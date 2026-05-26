package com.finance.portal.market.infrastructure.external.rasyonet;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.portal.common.application.logging.CentralIntegrationLogService;
import com.finance.portal.common.application.logging.IntegrationLogSupport;
import com.finance.portal.market.application.funds.model.RasyonetFundDetailDto;
import com.finance.portal.market.application.funds.model.RasyonetFundDto;
import com.finance.portal.market.application.funds.model.RasyonetOsmanliFundBulletinDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rasyonet/YatırımDirekt fon API client.
 *
 * Fon listesi: POST /service-yatirimdirekt-home/web-fund/fund-filter
 * Fon detay:   GET  /service-yatirimdirekt-home/web-fund/card?fundUniqueCode={code}:TMF
 *
 * Encoding: Response byte[] olarak alınıp UTF-8 ile decode edilir.
 * Bu sayede Türkçe karakterler (ö, ü, ş, ğ, ı, İ, Ö, Ü, Ş, Ğ) doğru gelir.
 */
@Component
public class RasyonetFundClient {

    private static final Logger log = LoggerFactory.getLogger(RasyonetFundClient.class);

    private static final com.fasterxml.jackson.databind.ObjectMapper CARD_MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();
    private static final String ORIGIN  = "https://www.yatirimdirekt.com";
    private static final String REFERER = "https://www.yatirimdirekt.com/";
    private static final String UA      = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final RasyonetProperties props;
    private final CentralIntegrationLogService integrationLogService;

    public RasyonetFundClient(RestTemplate restTemplate,
                               ObjectMapper objectMapper,
                               RasyonetProperties props,
                               CentralIntegrationLogService integrationLogService) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.props        = props;
        this.integrationLogService = integrationLogService;
    }

    // ── Fon listesi ──────────────────────────────────────────────────────────

    /**
     * Belirtilen SourceCode ile Rasyonet fund-filter endpoint'inden fon listesi çeker.
     *
     * SourceCode mapping:
     *   TMF → Normal yatırım fonları (TEFAS) — ~719 fon
     *   TPF → BES (Bireysel Emeklilik) fonları
     *   TAF → OKS (Otomatik Katılım) fonları
     *
     * @param sourceCode TMF | TPF | TAF
     * @return Normalize edilmiş fon listesi; hata durumunda boş liste.
     */
    public List<RasyonetFundDto> fetchFundsBySourceCode(String sourceCode) {
        log.info("Fetching Rasyonet fund list: sourceCode={}, url={}", sourceCode, props.getFilterUrl());
        try {
            Map<String, Object> body = buildFilterPayload(sourceCode);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, buildHeaders());

            ResponseEntity<byte[]> response = restTemplate.exchange(
                    props.getFilterUrl(), HttpMethod.POST, entity, byte[].class);

            byte[] bytes = response.getBody();
            if (bytes == null || bytes.length == 0) {
                log.warn("Rasyonet fund-filter returned empty body for sourceCode={}", sourceCode);
                publishEmptyResponse("fund_filter", sourceCode, null);
                return List.of();
            }

            String json = new String(bytes, StandardCharsets.UTF_8);
            JsonNode root = objectMapper.readTree(json);

            if (!root.has("Data") || !root.get("Data").has("Funds")) {
                log.warn("Rasyonet fund-filter: Data.Funds not found for sourceCode={}", sourceCode);
                return List.of();
            }

            JsonNode fundsNode = root.get("Data").get("Funds");
            String fundCategory = resolveFundCategory(sourceCode);
            List<RasyonetFundDto> result = new ArrayList<>();
            Map<String, Boolean> seen = new LinkedHashMap<>();

            for (JsonNode f : fundsNode) {
                try {
                    RasyonetFundDto dto = mapFundNode(f);
                    if (dto.getCode() == null) continue;
                    dto.setFundCategory(fundCategory);
                    if (seen.putIfAbsent(dto.getCode(), true) == null) {
                        result.add(dto);
                    }
                } catch (Exception e) {
                    log.debug("Failed to map fund node for sourceCode={}: {}", sourceCode, e.getMessage());
                }
            }

            log.info("Rasyonet fund-filter: sourceCode={}, {} unique funds fetched", sourceCode, result.size());
            return result;

        } catch (HttpStatusCodeException e) {
            log.error("Rasyonet fund-filter HTTP error: sourceCode={}, status={}, body={}",
                    sourceCode, e.getStatusCode(),
                    e.getResponseBodyAsString().substring(0, Math.min(300, e.getResponseBodyAsString().length())));
            publishApiFailed("fund_filter", sourceCode, String.valueOf(e.getStatusCode().value()));
            return List.of();
        } catch (JsonProcessingException e) {
            log.error("Rasyonet fund-filter parse error: sourceCode={}, error={}", sourceCode, e.getMessage());
            publishParseFailed("fund_filter", sourceCode);
            return List.of();
        } catch (Exception e) {
            if (e.getCause() instanceof JsonProcessingException) {
                publishParseFailed("fund_filter", sourceCode);
            } else {
                log.error("Rasyonet fund-filter unexpected error: sourceCode={}, error={}", sourceCode, e.getMessage(), e);
            }
            return List.of();
        }
    }

    /**
     * Geriye dönük uyumluluk — mevcut TMF çağrılarını bozmaz.
     */
    public List<RasyonetFundDto> fetchFundList() {
        return fetchFundsBySourceCode(props.getSourceCode());
    }

    /**
     * Osmanlı Portföy fon bültenini çeker.
     * GET /service-yatirimdirekt-home/web-menu/osmanli-fund-bulletin
     * Response: {"Data": [...], "Status": "Ok", "Error": null}
     */
    public List<RasyonetOsmanliFundBulletinDto> fetchOsmanliFundBulletin() {
        log.info("Fetching Osmanlı fund bulletin from {}", props.getOsmanliBulletinUrl());
        try {
            HttpEntity<Void> entity = new HttpEntity<>(buildHeaders());
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    props.getOsmanliBulletinUrl(), HttpMethod.GET, entity, byte[].class);

            byte[] bytes = response.getBody();
            if (bytes == null || bytes.length == 0) {
                log.warn("Osmanlı fund bulletin returned empty body");
                return List.of();
            }

            String json = new String(bytes, StandardCharsets.UTF_8);
            log.debug("Osmanlı bulletin response length={}", json.length());

            JsonNode root = CARD_MAPPER.readTree(json);
            if (!root.has("Data") || root.get("Data").isNull()) {
                log.warn("Osmanlı bulletin: Data missing. Status={}, Error={}",
                        root.has("Status") ? root.get("Status").asText() : "N/A",
                        root.has("Error")  ? root.get("Error").asText()  : "N/A");
                return List.of();
            }

            JsonNode dataNode = root.get("Data");
            if (!dataNode.isArray()) {
                log.warn("Osmanlı bulletin: Data is not an array, type={}", dataNode.getNodeType());
                return List.of();
            }

            List<RasyonetOsmanliFundBulletinDto> result = new ArrayList<>();
            for (JsonNode item : dataNode) {
                try {
                    result.add(mapOsmanliBulletinItem(item));
                } catch (Exception e) {
                    log.debug("Failed to map Osmanlı bulletin item: {}", e.getMessage());
                }
            }

            log.info("Osmanlı fund bulletin: {} funds fetched", result.size());
            return result;

        } catch (HttpStatusCodeException e) {
            log.error("Osmanlı bulletin HTTP error: status={}, body={}",
                    e.getStatusCode(),
                    e.getResponseBodyAsString().substring(0, Math.min(300, e.getResponseBodyAsString().length())));
            return List.of();
        } catch (Exception e) {
            log.error("Osmanlı bulletin unexpected error: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /** SourceCode → fundCategory string mapping */
    private String resolveFundCategory(String sourceCode) {
        if (sourceCode == null) return "INVESTMENT_FUND";
        return switch (sourceCode.toUpperCase()) {
            case "TPF" -> "PENSION_FUND";
            case "TAF" -> "AUTO_ENROLLMENT_FUND";
            default    -> "INVESTMENT_FUND";
        };
    }

    /**
     * Tek bir fonun zengin detayını Rasyonet card endpoint'inden çeker.
     * sourceCode: TMF (TEFAS) | TPF (BES) | TAF (OKS) — default TMF
     */
    public RasyonetFundDetailDto fetchFundDetailRich(String code) {
        return fetchFundDetailRich(code, props.getSourceCode());
    }

    /**
     * Tek bir fonun zengin detayını Rasyonet card endpoint'inden çeker.
     * fundUniqueCode formatı: {code}:{sourceCode} — örn: AAJ:TPF, VKE:TAF, AAL:TMF
     */
    public RasyonetFundDetailDto fetchFundDetailRich(String code, String sourceCode) {
        String upperCode = code.toUpperCase();
        String sc = (sourceCode != null && !sourceCode.isBlank()) ? sourceCode.toUpperCase() : props.getSourceCode();
        String fundUniqueCode = upperCode + ":" + sc;

        // UriComponentsBuilder: build() sonra encode() — tek seferlik, double-encode yok
        java.net.URI uri = UriComponentsBuilder
                .fromHttpUrl(props.getCardUrl())
                .queryParam("fundUniqueCode", fundUniqueCode)
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUri();

        log.info("Rasyonet card request: code={}, fundUniqueCode={}, finalUri={}", upperCode, fundUniqueCode, uri);

        try {
            // 1. byte[] olarak al — Map.class veya JsonNode.class kullanma
            HttpEntity<Void> entity = new HttpEntity<>(buildHeaders());
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    uri, HttpMethod.GET, entity, byte[].class);

            int httpStatus = response.getStatusCode().value();
            var ct = response.getHeaders().getContentType();
            String contentType = ct != null ? ct.toString() : "unknown";

            byte[] bytes = response.getBody();
            if (bytes == null || bytes.length == 0) {
                log.warn("Rasyonet card: empty body — code={}, httpStatus={}", upperCode, httpStatus);
                publishEmptyResponse("fund_card", sc, upperCode);
                return null;
            }

            // 2. UTF-8 ile decode
            String body = new String(bytes, StandardCharsets.UTF_8);
            int logLen = Math.min(1000, body.length());
            log.info("Rasyonet card raw: code={}, httpStatus={}, contentType={}, bodyLen={}, body1000={}",
                    upperCode, httpStatus, contentType, body.length(), body.substring(0, logLen));

            // 3. CARD_MAPPER ile JsonNode parse — Spring bean değil, fresh ObjectMapper
            JsonNode root = CARD_MAPPER.readTree(body);

            // 4. root keys okunabilir şekilde logla
            List<String> rootKeys = new ArrayList<>();
            root.fieldNames().forEachRemaining(rootKeys::add);
            log.info("Rasyonet card parsed: code={}, nodeType={}, rootKeys={}, hasData={}",
                    upperCode, root.getNodeType(), rootKeys, root.has("Data"));

            // 5. Data kontrolü — büyük D, kesinlikle root.get("Data")
            if (!root.has("Data") || root.get("Data").isNull()) {
                String status = root.has("Status") ? root.get("Status").asText() : "N/A";
                String error  = root.has("Error")  ? root.get("Error").asText()  : "N/A";
                log.warn("Rasyonet card: Data null/missing — code={}, Status={}, Error={}, rootKeys={}",
                        upperCode, status, error, rootKeys);
                return null;
            }

            return mapCardNodeRich(root.get("Data"), upperCode);

        } catch (HttpStatusCodeException e) {
            String errBody = e.getResponseBodyAsString();
            log.error("Rasyonet card HTTP error: code={}, status={}, body={}",
                    upperCode, e.getStatusCode(),
                    errBody.substring(0, Math.min(500, errBody.length())));
            publishApiFailed("fund_card", sc, String.valueOf(e.getStatusCode().value()));
            return null;
        } catch (JsonProcessingException e) {
            log.error("Rasyonet card parse error: code={}, error={}", upperCode, e.getMessage());
            publishParseFailed("fund_card", upperCode);
            return null;
        } catch (Exception e) {
            if (e.getCause() instanceof JsonProcessingException) {
                publishParseFailed("fund_card", upperCode);
            } else {
                log.error("Rasyonet card error: code={}, error={}", upperCode, e.getMessage(), e);
            }
            return null;
        }
    }

    private void publishEmptyResponse(String operation, String sourceCode, String symbol) {
        integrationLogService.publish(
                IntegrationLogSupport.EVENT_EXTERNAL_API_EMPTY_RESPONSE,
                "WARN",
                "Rasyonet returned empty response",
                IntegrationLogSupport.PROVIDER_RASYONET,
                operation,
                null,
                null,
                null,
                null,
                buildRasyonetMetadata(sourceCode, symbol),
                RasyonetFundClient.class.getName()
        );
    }

    private void publishApiFailed(String operation, String sourceCode, String httpStatus) {
        integrationLogService.publish(
                IntegrationLogSupport.EVENT_EXTERNAL_API_FAILED,
                "ERROR",
                "Rasyonet API call failed",
                IntegrationLogSupport.PROVIDER_RASYONET,
                operation,
                httpStatus,
                null,
                null,
                null,
                buildRasyonetMetadata(sourceCode, null),
                RasyonetFundClient.class.getName()
        );
    }

    private void publishParseFailed(String operation, String symbolOrSource) {
        integrationLogService.publish(
                IntegrationLogSupport.EVENT_EXTERNAL_API_PARSE_FAILED,
                "ERROR",
                "Rasyonet response parse failed",
                IntegrationLogSupport.PROVIDER_RASYONET,
                operation,
                null,
                null,
                null,
                null,
                Map.of("sourceCode", symbolOrSource != null ? symbolOrSource : ""),
                RasyonetFundClient.class.getName()
        );
    }

    private static Map<String, Object> buildRasyonetMetadata(String sourceCode, String symbol) {
        Map<String, Object> meta = new LinkedHashMap<>();
        if (sourceCode != null && !sourceCode.isBlank()) {
            meta.put("sourceCode", sourceCode);
        }
        if (symbol != null && !symbol.isBlank()) {
            meta.put("symbol", symbol);
        }
        return meta;
    }

    private RasyonetFundDetailDto mapCardNodeRich(JsonNode d, String code) {
        RasyonetFundDetailDto dto = new RasyonetFundDetailDto();
        dto.setCode(text(d, "Code") != null ? text(d, "Code") : code.toUpperCase());
        dto.setName(text(d, "Name"));
        dto.setUniqueCode(text(d, "UniqueCode"));
        dto.setObjectId(text(d, "ObjectId"));
        dto.setPrice(decimal(d, "Price"));
        dto.setPriceUsd(decimal(d, "PriceUSD"));
        dto.setMarketCap(decimal(d, "Mcap"));
        dto.setMarketCapUsd(decimal(d, "McapUSD"));
        dto.setRiskLevel(intVal(d, "RiskLevel"));
        dto.setBenchmarkName(text(d, "BenchmarkName"));
        dto.setKapLink(text(d, "KapLink"));

        if (d.has("Manager") && !d.get("Manager").isNull())
            dto.setManagerName(text(d.get("Manager"), "Name"));
        if (d.has("Founder") && !d.get("Founder").isNull())
            dto.setFounderName(text(d.get("Founder"), "Name"));
        if (d.has("Source") && !d.get("Source").isNull())
            dto.setCurrencyCode(text(d.get("Source"), "Code"));
        if (d.has("Type") && !d.get("Type").isNull())
            dto.setFundType(text(d.get("Type"), "Name"));

        // Tag (fon bilgileri)
        JsonNode tag = d.path("Tag");
        if (!tag.isMissingNode()) {
            dto.setStrategy(text(tag, "Strategy"));
            dto.setCommission(text(tag, "Commission"));
            dto.setManagementFeeAnnual(text(tag, "ManagementFeeAnnual"));
            dto.setMinimumQuantitySales(text(tag, "MinimumQuantitySales"));
        }

        // Settlement
        JsonNode settlement = d.path("Settlement");
        if (!settlement.isMissingNode()) {
            dto.setBuySettlement(text(settlement, "Buy"));
            dto.setSellSettlement(text(settlement, "Sell"));
        }

        // Performance.Fund.Return
        JsonNode ret = d.path("Performance").path("Fund").path("Return");
        if (!ret.isMissingNode()) {
            dto.setReturnOneDay(returnValue(ret, "OneDay"));
            dto.setReturnOneWeek(returnValue(ret, "OneWeek"));
            dto.setReturnOneMonth(returnValue(ret, "OneMonth"));
            dto.setReturnThreeMonths(returnValue(ret, "ThreeMonths"));
            dto.setReturnSixMonths(returnValue(ret, "SixMonths"));
            dto.setReturnYearToDate(returnValue(ret, "YBG"));
            dto.setReturnOneYear(returnValue(ret, "OneYear"));
            dto.setReturnTwoYears(returnValue(ret, "TwoYears"));
            dto.setReturnThreeYears(returnValue(ret, "ThreeYears"));
            dto.setReturnFiveYears(returnValue(ret, "FiveYears"));
        }

        // Risk
        JsonNode risk = d.path("Risk").path("Fund");
        if (!risk.isMissingNode()) {
            dto.setRiskBest(text(risk, "Best"));
            dto.setRiskWorst(text(risk, "Worst"));
            dto.setRiskPositiveRateOfReturn(text(risk, "PositiveRateofReturn"));
        }

        // LastYearReturnPrice.Fund — fiyat geçmişi [{Date, Value}]
        JsonNode priceHistory = d.path("LastYearReturnPrice").path("Fund");
        if (priceHistory.isArray() && priceHistory.size() > 0) {
            java.util.List<RasyonetFundDetailDto.PricePoint> points = new java.util.ArrayList<>();
            for (JsonNode p : priceHistory) {
                String dateStr = text(p, "Date");
                BigDecimal val = decimal(p, "Value");
                if (dateStr != null && val != null) {
                    // ISO date → yyyy-MM-dd
                    String shortDate = dateStr.length() >= 10 ? dateStr.substring(0, 10) : dateStr;
                    points.add(new RasyonetFundDetailDto.PricePoint(shortDate, val));
                }
            }
            dto.setPriceHistory(points);
        }

        // MountlyReturn — aylık getiri
        // MountlyPerformance: {Return:[{Year, Return:{Ocak:..., Subat:...}, YBG}], Name:"..."}
        // NOT: MountlyPerformance bir OBJECT, array değil!
        JsonNode monthly = d.path("MountlyPerformance");
        if (!monthly.isMissingNode() && !monthly.isNull() && monthly.has("Return")) {
            try {
                JsonNode returnArray = monthly.get("Return");
                if (returnArray != null && returnArray.isArray()) {
                    java.util.List<RasyonetFundDetailDto.MonthlyReturn> monthlyList = new java.util.ArrayList<>();
                    String[] MONTH_KEYS = {"Ocak","Subat","Mart","Nisan","Mayis","Haziran","Temmuz","Agustos","Eylul","Ekim","Kasim","Aralik"};
                    for (JsonNode yearData : returnArray) {
                        String yr = text(yearData, "Year");
                        if (yr == null) continue;
                        JsonNode returnNode = yearData.get("Return");
                        if (returnNode == null || returnNode.isNull()) continue;
                        for (int mi = 0; mi < MONTH_KEYS.length; mi++) {
                            JsonNode mv = returnNode.get(MONTH_KEYS[mi]);
                            if (mv != null && !mv.isNull()) {
                                String valStr = mv.asText("");
                                if (!"NaN".equalsIgnoreCase(valStr) && !valStr.isEmpty()) {
                                    try {
                                        BigDecimal val = new BigDecimal(valStr);
                                        monthlyList.add(new RasyonetFundDetailDto.MonthlyReturn(yr, String.valueOf(mi + 1), val));
                                    } catch (Exception ignored) {}
                                }
                            }
                        }
                    }
                    if (!monthlyList.isEmpty()) dto.setMonthlyReturns(monthlyList);
                }
            } catch (Exception e) {
                log.warn("MountlyPerformance parse failed for code={}: {}", code, e.getMessage());
            }
        }

        // AssetAllocation — varlık dağılımı [{Name, Percentage, Items}]
        JsonNode assetAlloc = d.path("AssetAllocation");
        if (assetAlloc.isArray() && assetAlloc.size() > 0) {
            java.util.List<RasyonetFundDetailDto.AssetAllocationItem> items = new java.util.ArrayList<>();
            for (JsonNode a : assetAlloc) {
                String aName = text(a, "Name");
                BigDecimal aPct = decimal(a, "Percentage");
                if (aName != null && aPct != null) {
                    items.add(new RasyonetFundDetailDto.AssetAllocationItem(aName, aPct));
                }
            }
            if (!items.isEmpty()) dto.setAssetAllocation(items);
            log.debug("AssetAllocation count={} for code={}", items.size(), code);
        }

        log.info("mapCardNodeRich success: code={}, price={}, ret1m={}, priceHistory={}", 
                code, dto.getPrice(), dto.getReturnOneMonth(), 
                dto.getPriceHistory() != null ? dto.getPriceHistory().size() : 0);
        return dto;
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    /**
     * fund-filter Data.Funds[] içindeki tek bir fon node'unu DTO'ya map eder.
     */
    private RasyonetFundDto mapFundNode(JsonNode f) {
        RasyonetFundDto dto = new RasyonetFundDto();
        dto.setUniqueCode(text(f, "UniqueCode"));
        dto.setObjectId(text(f, "ObjectId"));
        dto.setCode(text(f, "Code"));
        dto.setName(text(f, "Name"));
        dto.setRiskLevel(intVal(f, "RiskLevel"));
        dto.setPrice(decimal(f, "Price"));
        dto.setMarketCapUsd(decimal(f, "McapUSD"));
        dto.setTradePlace(intVal(f, "TradePlace"));

        // Manager / Founder
        if (f.has("Manager") && !f.get("Manager").isNull()) {
            dto.setManagerName(text(f.get("Manager"), "Name"));
        }
        if (f.has("Founder") && !f.get("Founder").isNull()) {
            dto.setFounderName(text(f.get("Founder"), "Name"));
        }

        // Source
        if (f.has("Source") && !f.get("Source").isNull()) {
            dto.setSourceCode(text(f.get("Source"), "Code"));
        }

        // Type
        if (f.has("Type") && !f.get("Type").isNull()) {
            dto.setFundType(text(f.get("Type"), "Name"));
        }

        // Return
        JsonNode ret = f.path("Return");
        if (!ret.isMissingNode()) {
            dto.setReturnOneDay(returnValue(ret, "OneDay"));
            dto.setReturnOneWeek(returnValue(ret, "OneWeek"));
            dto.setReturnOneMonth(returnValue(ret, "OneMonth"));
            dto.setReturnThreeMonths(returnValue(ret, "ThreeMonths"));
            dto.setReturnSixMonths(returnValue(ret, "SixMonths"));
            dto.setReturnYearToDate(returnValue(ret, "YBG"));
            dto.setReturnOneYear(returnValue(ret, "OneYear"));
            dto.setReturnTwoYears(returnValue(ret, "TwoYears"));
            dto.setReturnThreeYears(returnValue(ret, "ThreeYears"));
            dto.setReturnFiveYears(returnValue(ret, "FiveYears"));
        }

        // Detail (Tag)
        JsonNode detail = f.path("Detail");
        if (!detail.isMissingNode()) {
            dto.setCommission(text(detail, "Commission"));
            dto.setBenchmark(text(detail, "Benchmark"));
            dto.setMinimumQuantitySales(text(detail, "MinimumQuantitySales"));
            dto.setBuySettlement(text(detail, "BuyBackSettlement"));
            dto.setSellSettlement(text(detail, "SellSettlement"));
            dto.setStrategy(text(detail, "Strategy"));
        }

        return dto;
    }

    /**
     * card endpoint Data node'unu DTO'ya map eder.
     * card response'u fund-filter'dan daha zengin veri içerir.
     */
    private RasyonetFundDto mapCardNode(JsonNode d) {
        RasyonetFundDto dto = new RasyonetFundDto();
        dto.setUniqueCode(text(d, "UniqueCode"));
        dto.setObjectId(text(d, "ObjectId"));
        dto.setCode(text(d, "Code"));
        dto.setName(text(d, "Name"));
        dto.setRiskLevel(intVal(d, "RiskLevel"));
        dto.setPrice(decimal(d, "Price"));
        dto.setMarketCapUsd(decimal(d, "McapUSD"));
        dto.setTradePlace(intVal(d, "TradePlace"));

        if (d.has("Manager") && !d.get("Manager").isNull()) {
            dto.setManagerName(text(d.get("Manager"), "Name"));
        }
        if (d.has("Founder") && !d.get("Founder").isNull()) {
            dto.setFounderName(text(d.get("Founder"), "Name"));
        }
        if (d.has("Source") && !d.get("Source").isNull()) {
            dto.setSourceCode(text(d.get("Source"), "Code"));
        }
        if (d.has("Type") && !d.get("Type").isNull()) {
            dto.setFundType(text(d.get("Type"), "Name"));
        }

        // Performance.Fund.Return
        JsonNode ret = d.path("Performance").path("Fund").path("Return");
        if (!ret.isMissingNode()) {
            dto.setReturnOneDay(returnValue(ret, "OneDay"));
            dto.setReturnOneWeek(returnValue(ret, "OneWeek"));
            dto.setReturnOneMonth(returnValue(ret, "OneMonth"));
            dto.setReturnThreeMonths(returnValue(ret, "ThreeMonths"));
            dto.setReturnSixMonths(returnValue(ret, "SixMonths"));
            dto.setReturnYearToDate(returnValue(ret, "YBG"));
            dto.setReturnOneYear(returnValue(ret, "OneYear"));
            dto.setReturnTwoYears(returnValue(ret, "TwoYears"));
            dto.setReturnThreeYears(returnValue(ret, "ThreeYears"));
            dto.setReturnFiveYears(returnValue(ret, "FiveYears"));
        }

        // Tag (detay alanları)
        JsonNode tag = d.path("Tag");
        if (!tag.isMissingNode()) {
            dto.setCommission(text(tag, "Commission"));
            dto.setMinimumQuantitySales(text(tag, "MinimumQuantitySales"));
            dto.setBuySettlement(text(tag, "BuyBackSettlement"));
            dto.setSellSettlement(text(tag, "SellSettlement"));
            dto.setStrategy(text(tag, "Strategy"));
        }

        // BenchmarkName
        dto.setBenchmark(text(d, "BenchmarkName"));

        return dto;
    }

    // ── Yardımcılar ───────────────────────────────────────────────────────────

    private Map<String, Object> buildFilterPayload(String sourceCode) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("SourceCode",    sourceCode);
        body.put("Managers",      List.of());
        body.put("Founders",      List.of());
        body.put("Types",         List.of());
        body.put("RiskLevels",    List.of());
        body.put("Limit",         -1);
        body.put("Mcap",          0);
        body.put("SecurityCode",  List.of());
        body.put("TradePlace",    0);
        return body;
    }

    /** Geriye dönük uyumluluk */
    private Map<String, Object> buildFilterPayload() {
        return buildFilterPayload(props.getSourceCode());
    }

    private RasyonetOsmanliFundBulletinDto mapOsmanliBulletinItem(JsonNode item) {
        RasyonetOsmanliFundBulletinDto dto = new RasyonetOsmanliFundBulletinDto();
        dto.setUniqueCode(text(item, "UniqueCode"));
        dto.setCode(text(item, "Code"));
        dto.setName(text(item, "Name"));
        dto.setGroup(text(item, "Group"));
        dto.setType(text(item, "Type"));
        // RiskLevel string olarak gelebiliyor
        String rl = text(item, "RiskLevel");
        if (rl != null) { try { dto.setRiskLevel(Integer.parseInt(rl.trim())); } catch (Exception ignored) {} }
        // Cosmission typo → commission
        dto.setCommission(text(item, "Cosmission"));
        // Kistas → benchmark
        dto.setBenchmark(text(item, "Kistas"));
        dto.setFundCurrency(text(item, "FundCurrency"));
        dto.setMarketCap(decimal(item, "Mcap"));
        dto.setMarketCapUsd(decimal(item, "McapUSD"));
        dto.setDailyReturn(decimal(item, "DailyReturn"));
        dto.setWeeklyReturn(decimal(item, "WeeklyReturn"));
        dto.setMonthlyReturn(decimal(item, "MonthlyReturn"));
        dto.setMonthlyReturnBenchmark(decimal(item, "MonthlyReturnBenchmark"));
        dto.setMonthlyRelatedIndex(text(item, "MonthlyRelatedIndex"));
        dto.setYearlyReturn(decimal(item, "YearlyReturn"));
        dto.setYearlyReturnBenchmark(decimal(item, "YearlyReturnBenchmark"));
        dto.setYearlyRelatedIndex(text(item, "YearlyRelatedIndex"));
        return dto;
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.ACCEPT,          "application/json");
        headers.set(HttpHeaders.USER_AGENT,      UA);
        headers.set(HttpHeaders.ORIGIN,          ORIGIN);
        headers.set(HttpHeaders.REFERER,         REFERER);
        headers.set(HttpHeaders.ACCEPT_LANGUAGE, "tr-TR,tr;q=0.9,en;q=0.8");
        return headers;
    }

    /** Güvenli text okuma */
    private String text(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) return null;
        String v = node.get(field).asText(null);
        return (v == null || v.isBlank()) ? null : v;
    }

    /** Güvenli int okuma */
    private Integer intVal(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) return null;
        try { return node.get(field).asInt(); } catch (Exception e) { return null; }
    }

    /**
     * Güvenli BigDecimal okuma.
     * "NaN", null, boş string → null döner.
     */
    private BigDecimal decimal(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) return null;
        String raw = node.get(field).asText(null);
        if (raw == null || raw.isBlank() || "NaN".equalsIgnoreCase(raw)) return null;
        try { return new BigDecimal(raw); } catch (Exception e) { return null; }
    }

    /**
     * Return.{period}.Value okuma.
     * Örn: Return.OneMonth.Value
     */
    private BigDecimal returnValue(JsonNode returnNode, String period) {
        if (returnNode == null || !returnNode.has(period)) return null;
        JsonNode periodNode = returnNode.get(period);
        if (periodNode.isNull()) return null;
        if (periodNode.has("Value")) {
            return decimal(periodNode, "Value");
        }
        // Bazı response'larda direkt sayı olabilir
        String raw = periodNode.asText(null);
        if (raw == null || raw.isBlank() || "NaN".equalsIgnoreCase(raw)) return null;
        try { return new BigDecimal(raw); } catch (Exception e) { return null; }
    }
}
