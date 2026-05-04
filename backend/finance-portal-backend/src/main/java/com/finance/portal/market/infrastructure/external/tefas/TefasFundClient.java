package com.finance.portal.market.infrastructure.external.tefas;

import com.finance.portal.market.application.funds.model.TefasFundHistoryPoint;
import com.finance.portal.market.application.funds.model.TefasFundItem;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HangiKredi üzerinden TEFAS fon verilerini çeker.
 * Sayfa Next.js SSR ile render edilir ve fon verileri HTML içine gömülü JSON olarak gelir.
 */
@Component
public class TefasFundClient {

    private static final Logger log = LoggerFactory.getLogger(TefasFundClient.class);

    private static final String HK_BASE_URL  = "https://www.hangikredi.com";
    private static final String HK_LIST_URL  = HK_BASE_URL + "/yatirim-araclari/fon/tefas";
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TefasFundClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    // ── Fon listesi ──────────────────────────────────────────────────────────

    public List<TefasFundItem> fetchFunds(String kind) {
        log.info("Fetching TEFAS funds from HangiKredi (kind={})", kind);
        try {
            String html = fetchHtml(HK_LIST_URL);
            if (html == null || html.isBlank()) {
                log.warn("HangiKredi returned empty HTML");
                return List.of();
            }
            List<TefasFundItem> funds = parseHangiKrediHtml(html);
            log.info("Parsed {} TEFAS funds from HangiKredi", funds.size());
            return funds;
        } catch (Exception e) {
            log.warn("Failed to fetch TEFAS funds from HangiKredi: {}", e.getMessage());
            return List.of();
        }
    }

    public List<TefasFundItem> fetchFundByCode(String code) {
        // Önce detay sayfasından çek (daha zengin veri)
        TefasFundItem detail = fetchFundDetail(code);
        if (detail != null) return List.of(detail);
        // Fallback: liste sayfasından filtrele
        List<TefasFundItem> all = fetchFunds("YAT");
        return all.stream()
                .filter(f -> code.equalsIgnoreCase(f.getCode()))
                .toList();
    }

    /**
     * HangiKredi fon detay sayfasından zengin veri çeker.
     * Fiyat, risk değeri, fon içeriği, geçmiş performans, grafik verisi.
     */
    public TefasFundItem fetchFundDetail(String code) {
        try {
            String url = HK_BASE_URL + "/yatirim-araclari/fon/" + code.toLowerCase();
            String html = fetchHtml(url);
            if (html == null || html.isBlank()) return null;
            return parseDetailHtml(html, code);
        } catch (Exception e) {
            log.warn("Failed to fetch fund detail for {}: {}", code, e.getMessage());
            return null;
        }
    }

    private TefasFundItem parseDetailHtml(String html, String code) {
        try {
            int idx = html.indexOf("initialData");
            if (idx < 0) return null;

            // initialData'dan sonraki JSON bloğunu çıkar
            // Önce escaped format dene (Next.js SSR genellikle escaped gönderir)
            int dataStart = html.indexOf("{\\\"data\\\":{", idx);
            if (dataStart >= 0) {
                String escaped = html.substring(dataStart);
                String unescaped = escaped.replace("\\\"", "\"").replace("\\/", "/");
                int objEnd = findMatchingBracket(unescaped, 0, '{', '}');
                if (objEnd >= 0) {
                    TefasFundItem item = mapDetailJson(
                            objectMapper.readTree(unescaped.substring(0, objEnd + 1)).path("data"), code);
                    if (item != null) return item;
                }
            }

            // Normal (unescaped) format dene
            dataStart = html.indexOf("{\"data\":{", idx);
            if (dataStart >= 0) {
                int objEnd = findMatchingBracket(html, dataStart, '{', '}');
                if (objEnd >= 0) {
                    String json = html.substring(dataStart, objEnd + 1).replace("\\/", "/");
                    TefasFundItem item = mapDetailJson(
                            objectMapper.readTree(json).path("data"), code);
                    if (item != null) return item;
                }
            }

            log.warn("Could not find data block in HangiKredi HTML for code={}", code);
            return null;
        } catch (Exception e) {
            log.warn("Failed to parse detail HTML for {}: {}", code, e.getMessage());
            return null;
        }
    }

    private TefasFundItem mapDetailJson(JsonNode data, String code) {
        TefasFundItem item = new TefasFundItem();
        item.setCode(data.path("code").asText(code));
        item.setTitle(data.path("name").asText(null));
        item.setLogoUrl(data.path("logoPath").asText(null));
        item.setLastPrice(data.path("last").asText(null));
        item.setChangePercent(data.path("changePercent").asText(null));
        item.setChangeAmount(data.path("changeAmount").asText(null));
        item.setUpdateDate(data.path("updateDate").asText(null));
        item.setDate(data.path("updateShortDate").asText(null));
        item.setKind("YAT");
        // HangiKredi internal id — grafik API için
        // id alanı int veya long olabilir
        JsonNode idNode = data.path("id");
        if (!idNode.isMissingNode() && !idNode.isNull()) {
            long idVal = idNode.asLong(0);
            if (idVal > 0) {
                item.setHangiKrediId(idVal);
                log.debug("Parsed hangiKrediId={} for code={}", idVal, code);
            }
        }

        // Risk değeri
        JsonNode risky = data.path("riskySection");
        if (!risky.isMissingNode()) {
            item.setRiskValue(risky.path("riskValue").asInt(0));
        }

        // Fon bilgisi etiketleri
        JsonNode infoSection = data.path("infoSection");
        if (!infoSection.isMissingNode()) {
            List<TefasFundItem.FundInfoLabel> labels = new ArrayList<>();
            for (JsonNode label : infoSection.path("labels")) {
                labels.add(new TefasFundItem.FundInfoLabel(
                    label.path("title").asText(),
                    label.path("text").asText(),
                    label.path("order").asInt()
                ));
            }
            labels.sort(java.util.Comparator.comparingInt(TefasFundItem.FundInfoLabel::getOrder));
            item.setInfoLabels(labels);
        }

        // Fon içeriği (dağılım)
        JsonNode dist = data.path("fundDistribution");
        if (!dist.isMissingNode()) {
            List<TefasFundItem.FundDistributionItem> distItems = new ArrayList<>();
            for (JsonNode d : dist.path("items")) {
                distItems.add(new TefasFundItem.FundDistributionItem(
                    d.path("title").asText(),
                    d.path("rate").asDouble(),
                    d.path("rateText").asText()
                ));
            }
            item.setDistribution(distItems);
        }

        // Geçmiş performans karşılaştırması
        JsonNode perf = data.path("performanceSection");
        if (!perf.isMissingNode()) {
            List<TefasFundItem.FundPerformanceItem> perfItems = new ArrayList<>();
            for (JsonNode p : perf.path("items")) {
                TefasFundItem.FundPerformanceItem pi = new TefasFundItem.FundPerformanceItem();
                pi.setCode(p.path("code").asText());
                pi.setName(p.path("name").asText());
                pi.setChangePercent(p.path("changePercent").asDouble());
                pi.setChangePercentFormated(p.path("changePercentFormated").asText());
                perfItems.add(pi);
            }
            item.setPerformanceComparison(perfItems);
        }

        // Grafik verisi
        JsonNode chart = data.path("chart");
        if (chart.isArray()) {
            List<TefasFundItem.FundChartPoint> chartPoints = new ArrayList<>();
            for (JsonNode c : chart) {
                TefasFundItem.FundChartPoint cp = new TefasFundItem.FundChartPoint();
                cp.setDate(c.path("date").asText());
                cp.setDateText(c.path("dateText").asText());
                cp.setChangePercent(c.path("changePercent").asDouble());
                cp.setLast(c.path("last").asDouble());
                chartPoints.add(cp);
            }
            item.setChartData(chartPoints);
        }

        // Dönem getirileri — liste sayfasından gelmiyor, infoLabels'dan çıkar
        return item;
    }

    public String detectKind(String code) {
        return fundKindCache.computeIfAbsent(code.toUpperCase(), k -> "YAT");
    }

    private final ConcurrentHashMap<String, String> fundKindCache = new ConcurrentHashMap<>();

    // ── Tarihsel veri ────────────────────────────────────────────────────────

    private static final ExecutorService HISTORY_EXECUTOR = Executors.newFixedThreadPool(4);

    public List<TefasFundHistoryPoint> fetchHistory(String code, LocalDate from, LocalDate to) {
        // HangiKredi'den tarihsel veri çekmek için fon detay sayfasını kullan
        try {
            String url = HK_BASE_URL + "/yatirim-araclari/fon/" + code.toLowerCase();
            String html = fetchHtml(url);
            if (html == null || html.isBlank()) return List.of();
            return parseHistoryFromHtml(html, code);
        } catch (Exception e) {
            log.warn("Failed to fetch history for {}: {}", code, e.getMessage());
            return List.of();
        }
    }

    // ── HTML Parse ───────────────────────────────────────────────────────────

    /**
     * HangiKredi sayfasındaki Next.js SSR verisinden fon listesini parse eder.
     * Veri "filteredFunds" key'i altında JSON olarak HTML içine gömülüdür.
     */
    private List<TefasFundItem> parseHangiKrediHtml(String html) {
        try {
            // "filteredFunds" JSON bloğunu bul — hem normal hem escaped formatta
            int idx = html.indexOf("filteredFunds");
            if (idx < 0) {
                log.warn("filteredFunds not found in HangiKredi HTML (length={})", html.length());
                return List.of();
            }

            // "funds":[ array'ini bul — filteredFunds'tan sonra
            // Escaped formatta: \"funds\":[ veya normal: "funds":[
            int fundsIdx = html.indexOf("\"funds\":[", idx);
            if (fundsIdx < 0) {
                fundsIdx = html.indexOf("\\\"funds\\\":[", idx);
            }
            if (fundsIdx < 0) return List.of();

            // Array'in başlangıcını bul
            int arrayStart = html.indexOf("[", fundsIdx + 8);
            if (arrayStart < 0) return List.of();

            // Matching bracket'i bul
            int arrayEnd = findMatchingBracket(html, arrayStart, '[', ']');
            if (arrayEnd < 0) return List.of();

            String fundsJson = html.substring(arrayStart, arrayEnd + 1);
            // Escaped karakterleri düzelt
            fundsJson = fundsJson.replace("\\/", "/").replace("\\\"", "\"");

            JsonNode fundsArray = objectMapper.readTree(fundsJson);
            List<TefasFundItem> result = new ArrayList<>();

            for (JsonNode fund : fundsArray) {
                TefasFundItem item = mapHangiKrediFund(fund);
                if (item != null) result.add(item);
            }

            log.info("Parsed {} funds from HangiKredi HTML", result.size());
            return result;
        } catch (Exception e) {
            log.warn("Failed to parse HangiKredi HTML: {}", e.getMessage());
            return List.of();
        }
    }

    private TefasFundItem mapHangiKrediFund(JsonNode node) {
        try {
            TefasFundItem item = new TefasFundItem();
            item.setCode(node.path("code").asText(null));
            item.setTitle(node.path("name").asText(null));
            item.setKind("YAT");
            // HangiKredi internal id — grafik API için gerekli
            if (!node.path("symbolDefinitionId").isMissingNode()) {
                item.setHangiKrediId(node.path("symbolDefinitionId").asLong(0));
            } else if (!node.path("id").isMissingNode()) {
                item.setHangiKrediId(node.path("id").asLong(0));
            }

            // Tarih bilgisi
            item.setDate(node.path("date").asText(null));

            // Yatırımcı sayısı
            String personCountStr = node.path("personCount").asText(null);
            if (personCountStr != null) {
                try {
                    // "4,8 bin" → 4800, "27,0 bin" → 27000, "755" → 755
                    personCountStr = personCountStr.trim();
                    if (personCountStr.endsWith(" bin")) {
                        double val = Double.parseDouble(personCountStr.replace(" bin", "").replace(",", "."));
                        item.setNumberOfInvestors((long)(val * 1000));
                    } else {
                        item.setNumberOfInvestors(Long.parseLong(personCountStr.replace(",", "").replace(".", "")));
                    }
                } catch (Exception ignored) {}
            }

            // Dönem getirileri
            JsonNode periods = node.path("periods");
            if (periods.isArray()) {
                for (JsonNode period : periods) {
                    int type = period.path("type").asInt(0);
                    double changePercent = period.path("changePercent").asDouble(0);
                    switch (type) {
                        case 30   -> item.setReturn1M(changePercent);
                        case 90   -> item.setReturn3M(changePercent);
                        case 180  -> item.setReturn6M(changePercent);
                        case 360  -> item.setReturn1Y(changePercent);
                        case 1080 -> item.setReturn3Y(changePercent);
                        case 1800 -> item.setReturn5Y(changePercent);
                    }
                }
            }

            // Logo URL
            String imagePath = node.path("imagePath").asText(null);
            if (imagePath != null && !imagePath.isBlank()) {
                item.setLogoUrl(imagePath);
            }

            return item;
        } catch (Exception e) {
            log.debug("Failed to map fund: {}", e.getMessage());
            return null;
        }
    }

    private List<TefasFundHistoryPoint> parseHistoryFromHtml(String html, String code) {
        // Basit implementasyon — tarihsel veri için ayrı bir endpoint gerekebilir
        return List.of();
    }

    /**
     * Matching bracket'i bulur (JSON array/object sınırını tespit eder)
     */
    private int findMatchingBracket(String text, int start, char open, char close) {
        int depth = 0;
        boolean inString = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"' && (i == 0 || text.charAt(i-1) != '\\')) {
                inString = !inString;
            }
            if (!inString) {
                if (c == open) depth++;
                else if (c == close) {
                    depth--;
                    if (depth == 0) return i;
                }
            }
        }
        return -1;
    }

    // ── HTTP ─────────────────────────────────────────────────────────────────

    private String fetchHtml(String url) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.USER_AGENT, UA);
            headers.set(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            headers.set(HttpHeaders.ACCEPT_LANGUAGE, "tr-TR,tr;q=0.9");
            headers.set(HttpHeaders.REFERER, HK_BASE_URL + "/");
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            return response.getBody();
        } catch (Exception e) {
            log.warn("HTTP GET failed for {}: {}", url, e.getMessage());
            return null;
        }
    }

    // ── Unused legacy methods (kept for interface compatibility) ─────────────

    private BigDecimal parseBD(Object value) {
        if (value == null) return null;
        try { return new BigDecimal(value.toString()); }
        catch (Exception e) { return null; }
    }

    private Long parseLong(Object value) {
        if (value == null) return null;
        try { return (long) Double.parseDouble(value.toString()); }
        catch (Exception e) { return null; }
    }
}
