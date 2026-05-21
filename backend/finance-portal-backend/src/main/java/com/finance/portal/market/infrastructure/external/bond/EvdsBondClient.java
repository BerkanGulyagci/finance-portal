package com.finance.portal.market.infrastructure.external.bond;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.portal.common.application.logging.CentralIntegrationLogService;
import com.finance.portal.common.application.logging.IntegrationLogSupport;
import com.finance.portal.market.application.bond.evds.model.EvdsSeriesInfo;
import com.finance.portal.market.application.bond.evds.model.EvdsSeriesPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * TCMB EVDS API istemcisi — DİBS (Devlet İç Borçlanma Senetleri) verileri.
 *
 * <p>Base URL: https://evds3.tcmb.gov.tr/igmevdsms-dis
 *
 * <p>EVDS API'nin standart query string (?key=val) kullanmadığına dikkat:
 * parametreler path'e & ile eklenir:
 * <pre>
 *   {base}/series=TP.TRD070727K10&startDate=01-05-2026&endDate=04-05-2026&type=json
 *   {base}/serieList/code=bie_pydibs&type=json
 * </pre>
 *
 * <p>API key HTTP header olarak gönderilir: {@code key: <api_key>}
 *
 * <p>Data group: bie_pydibs (Piyasa Verileri → DİBS Gösterge Değerleri)
 */
@Component
public class EvdsBondClient {

    private static final Logger log = LoggerFactory.getLogger(EvdsBondClient.class);

    /** EVDS tarih formatı: dd-MM-yyyy */
    private static final DateTimeFormatter EVDS_DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final CentralIntegrationLogService integrationLog;

    @Value("${evds.base-url:https://evds3.tcmb.gov.tr/igmevdsms-dis}")
    private String baseUrl;

    @Value("${evds.api-key:}")
    private String apiKey;

    @Value("${evds.data-group:bie_pydibs}")
    private String dataGroup;

    public EvdsBondClient(@Qualifier("evdsRestTemplate") RestTemplate restTemplate,
                          ObjectMapper objectMapper,
                          CentralIntegrationLogService integrationLog) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.integrationLog = integrationLog;
    }

    private void logIntegrationFailure(String eventType, String message, String operation,
                                       String httpStatus, Map<String, Object> metadata) {
        integrationLog.publish(
                eventType,
                "WARN",
                message,
                IntegrationLogSupport.PROVIDER_EVDS,
                operation,
                httpStatus,
                null,
                null,
                Boolean.TRUE,
                metadata,
                EvdsBondClient.class.getName()
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Belirtilen seri kodunun günlük değerlerini çeker.
     *
     * <p>Seri kodu örnekleri:
     * <ul>
     *   <li>{@code TP.TRD070727K10}      — Değer serisi
     *   <li>{@code TP.TRD070727K10.ORAN} — Kupon Faiz Oranı serisi
     * </ul>
     *
     * @param seriesCode EVDS seri kodu (TP. prefix dahil)
     * @param startDate  başlangıç tarihi (dahil)
     * @param endDate    bitiş tarihi (dahil)
     * @return tarih sıralı veri noktaları listesi; hata durumunda boş liste
     */
    public List<EvdsSeriesPoint> fetchSeries(String seriesCode, LocalDate startDate, LocalDate endDate) {
        validateApiKey();

        String start = startDate.format(EVDS_DATE_FMT);
        String end   = endDate.format(EVDS_DATE_FMT);

        // EVDS URL formatı: {base}/series={code}&startDate={dd-MM-yyyy}&endDate={dd-MM-yyyy}&type=json
        String url = baseUrl.stripTrailing() + "/"
                + "series=" + seriesCode
                + "&startDate=" + start
                + "&endDate=" + end
                + "&type=json";

        log.info("[EVDS] fetchSeries → seriesCode={} startDate={} endDate={}", seriesCode, start, end);
        log.debug("[EVDS] Request URL: {}", url);

        String body = executeGet(url);
        if (body == null) return List.of();

        List<EvdsSeriesPoint> points = parseSeriesResponse(body, seriesCode);

        log.info("[EVDS] fetchSeries ← seriesCode={} itemCount={}", seriesCode, points.size());
        if (!points.isEmpty()) {
            log.debug("[EVDS] first={} last={}", points.get(0), points.get(points.size() - 1));
        }

        return points;
    }

    /**
     * Kıymetin TCMB EVDS Gösterge Değeri (Değer) serisini çeker.
     *
     * <p>Seri kodu: {@code TP.{instrumentCode}}
     *
     * @param instrumentCode kıymet kodu (örn. {@code TRD070727K10})
     * @param startDate      başlangıç tarihi
     * @param endDate        bitiş tarihi
     * @return tarih sıralı gösterge değerleri; hata durumunda boş liste
     */
    public List<EvdsSeriesPoint> fetchIndicatorValues(
            String instrumentCode, LocalDate startDate, LocalDate endDate) {
        String seriesCode = "TP." + instrumentCode;
        log.debug("[EVDS] fetchIndicatorValues → instrumentCode={} seriesCode={}", instrumentCode, seriesCode);
        return fetchSeries(seriesCode, startDate, endDate);
    }

    /**
     * Kıymetin Kupon Faiz Oranı serisini çeker.
     *
     * <p>Seri kodu: {@code TP.{instrumentCode}.ORAN}
     *
     * @param instrumentCode kıymet kodu (örn. {@code TRD070727K10})
     * @param startDate      başlangıç tarihi
     * @param endDate        bitiş tarihi
     * @return tarih sıralı kupon faiz oranları; hata durumunda boş liste
     */
    public List<EvdsSeriesPoint> fetchCouponRates(
            String instrumentCode, LocalDate startDate, LocalDate endDate) {
        String seriesCode = "TP." + instrumentCode + ".ORAN";
        log.debug("[EVDS] fetchCouponRates → instrumentCode={} seriesCode={}", instrumentCode, seriesCode);
        return fetchSeries(seriesCode, startDate, endDate);
    }

    /**
     * bie_pydibs data group'undaki tüm DİBS serilerinin meta bilgisini çeker.
     *
     * <p>URL formatı: {@code {base}/serieList/code={dataGroup}&type=json}
     *
     * @return seri meta bilgileri listesi; hata durumunda boş liste
     */
    public List<EvdsSeriesInfo> fetchBondSeriesList() {
        validateApiKey();

        String url = baseUrl.stripTrailing() + "/serieList/code=" + dataGroup + "&type=json";

        log.info("[EVDS] fetchBondSeriesList → dataGroup={}", dataGroup);
        log.debug("[EVDS] Request URL: {}", url);

        String body = executeGet(url);
        if (body == null) return List.of();

        List<EvdsSeriesInfo> series = parseSeriesListResponse(body);

        log.info("[EVDS] fetchBondSeriesList ← itemCount={}", series.size());
        return series;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HTTP
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * EVDS API'ye GET isteği atar.
     * API key {@code key} header'ı olarak gönderilir.
     *
     * @return response body string; hata durumunda null
     */
    private String executeGet(String url) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("key", apiKey);
        headers.set(HttpHeaders.ACCEPT, "application/json");
        headers.set(HttpHeaders.ACCEPT_CHARSET, "UTF-8");
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<byte[]> response = restTemplate.exchange(url, HttpMethod.GET, entity, byte[].class);
            byte[] bodyBytes = response.getBody();

            if (bodyBytes == null || bodyBytes.length == 0) {
                log.warn("[EVDS] Empty response body for URL: {}", url);
                logIntegrationFailure(
                        IntegrationLogSupport.EVENT_EXTERNAL_API_EMPTY_RESPONSE,
                        "EVDS: empty response body (provider may have changed)",
                        "executeGet",
                        String.valueOf(response.getStatusCode().value()),
                        Map.of("url", url));
                return null;
            }
            // EVDS response Content-Type UTF-8 bildirse de içerik ISO-8859-1 encode
            // Türkçe karakterler için ISO-8859-1 → UTF-8 dönüşümü gerekli
            String decoded = new String(bodyBytes, StandardCharsets.ISO_8859_1);
            return decoded;

        } catch (HttpClientErrorException.Unauthorized e) {
            log.error("[EVDS] 401 Unauthorized — API key geçersiz veya eksik. URL: {}", url);
            return null;
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("[EVDS] 404 Not Found — Seri bulunamadı. URL: {}", url);
            return null;
        } catch (HttpClientErrorException e) {
            log.error("[EVDS] HTTP {} client error. URL: {} — {}", e.getStatusCode(), url, e.getMessage());
            return null;
        } catch (HttpServerErrorException e) {
            log.error("[EVDS] HTTP {} server error. URL: {} — {}", e.getStatusCode(), url, e.getMessage());
            return null;
        } catch (ResourceAccessException e) {
            log.error("[EVDS] Connection error (timeout/unreachable). URL: {} — {}", url, e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("[EVDS] Unexpected error. URL: {} — {}", url, e.getMessage(), e);
            return null;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Parsing
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * EVDS seri veri response'unu parse eder.
     *
     * <p>Beklenen format:
     * <pre>
     * {
     *   "totalCount": 4,
     *   "items": [
     *     {"Tarih": "01-05-2026", "TP_TRD070727K10": "10.98700000", "UNIXTIME": {...}},
     *     ...
     *   ]
     * }
     * </pre>
     *
     * <p>Seri kodu → response field dönüşümü:
     * {@code TP.TRD070727K10}      → {@code TP_TRD070727K10}
     * {@code TP.TRD070727K10.ORAN} → {@code TP_TRD070727K10_ORAN}
     *
     * @param body       raw JSON string
     * @param seriesCode EVDS seri kodu (nokta → alt çizgi dönüşümü için)
     * @return parse edilmiş veri noktaları
     */
    private List<EvdsSeriesPoint> parseSeriesResponse(String body, String seriesCode) {
        // "TP.TRD070727K10.ORAN" → "TP_TRD070727K10_ORAN"
        String fieldName = seriesCode.replace('.', '_');

        List<EvdsSeriesPoint> points = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode items = root.path("items");

            if (items.isMissingNode() || !items.isArray()) {
                log.warn("[EVDS] 'items' alanı bulunamadı veya dizi değil. seriesCode={}", seriesCode);
                logIntegrationFailure(
                        IntegrationLogSupport.EVENT_EXTERNAL_API_PARSE_FAILED,
                        "EVDS series: 'items' field missing or not an array (JSON structure may have changed)",
                        "fetchSeries",
                        null,
                        Map.of("seriesCode", seriesCode));
                return List.of();
            }

            int itemCount = items.size();

            for (JsonNode item : items) {
                String dateStr  = item.path("Tarih").asText(null);
                String valueStr = item.path(fieldName).asText(null);

                if (dateStr == null || valueStr == null || valueStr.isBlank()) {
                    log.debug("[EVDS] Eksik alan, satır atlandı: Tarih={} field={} value={}", dateStr, fieldName, valueStr);
                    continue;
                }

                // Değer "null" string olarak gelebilir
                if ("null".equalsIgnoreCase(valueStr.trim())) {
                    log.debug("[EVDS] Null değer, satır atlandı: Tarih={} seriesCode={}", dateStr, seriesCode);
                    continue;
                }

                try {
                    LocalDate date  = LocalDate.parse(dateStr.trim(), EVDS_DATE_FMT);
                    BigDecimal value = new BigDecimal(valueStr.trim());
                    points.add(new EvdsSeriesPoint(date, value));
                } catch (DateTimeParseException e) {
                    log.warn("[EVDS] Tarih parse hatası, satır atlandı: Tarih='{}' — {}", dateStr, e.getMessage());
                } catch (NumberFormatException e) {
                    log.warn("[EVDS] Değer parse hatası, satır atlandı: field='{}' value='{}' — {}", fieldName, valueStr, e.getMessage());
                }
            }

            // items var ama beklenen alandan 0 nokta çıktı —
            // muhtemelen seri kodu → field ('TP_...') eşlemesi/yapısı değişti (sessiz hata).
            if (points.isEmpty() && itemCount > 0) {
                log.warn("[EVDS] {} items parsed but 0 points for field '{}' (seriesCode={})", itemCount, fieldName, seriesCode);
                logIntegrationFailure(
                        IntegrationLogSupport.EVENT_EXTERNAL_API_PARSE_FAILED,
                        "EVDS series: " + itemCount + " items present but 0 points extracted for field '" + fieldName
                                + "' (response field naming may have changed)",
                        "fetchSeries",
                        null,
                        Map.of("seriesCode", seriesCode, "fieldName", fieldName, "itemCount", itemCount));
            }

        } catch (Exception e) {
            log.error("[EVDS] Response parse hatası. seriesCode={} — {}", seriesCode, e.getMessage(), e);
            logIntegrationFailure(
                    IntegrationLogSupport.EVENT_EXTERNAL_API_PARSE_FAILED,
                    "EVDS series parse failed: " + e.getMessage(),
                    "fetchSeries",
                    null,
                    Map.of("seriesCode", seriesCode, "exceptionClass", e.getClass().getSimpleName()));
        }

        return points;
    }

    /**
     * EVDS serieList response'unu parse eder.
     *
     * <p>Beklenen format (JSON array):
     * <pre>
     * [
     *   {
     *     "SERIE_CODE": "TP.TRD070727K10",
     *     "DATAGROUP_CODE": "bie_pydibs",
     *     "SERIE_NAME": "TRD070727K10 ( 07.01.2026 07.07.2027 )  Değer ...",
     *     "SERIE_NAME_ENG": "TRD070727K10 ( 07.01.2026 07.07.2027 )  Value ...",
     *     "FREQUENCY_STR": "GÜNLÜK",
     *     "START_DATE": "07-01-2026",
     *     "END_DATE": "04-05-2026"
     *   },
     *   ...
     * ]
     * </pre>
     */
    private List<EvdsSeriesInfo> parseSeriesListResponse(String body) {
        List<EvdsSeriesInfo> result = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(body);

            if (!root.isArray()) {
                log.warn("[EVDS] serieList response dizi değil. dataGroup={}", dataGroup);
                logIntegrationFailure(
                        IntegrationLogSupport.EVENT_EXTERNAL_API_PARSE_FAILED,
                        "EVDS serieList: response is not a JSON array (structure may have changed)",
                        "fetchBondSeriesList",
                        null,
                        Map.of("dataGroup", dataGroup));
                return List.of();
            }

            int nodeCount = root.size();

            for (JsonNode node : root) {
                String seriesCode    = node.path("SERIE_CODE").asText(null);
                String datagroupCode = node.path("DATAGROUP_CODE").asText(null);
                String seriesName    = node.path("SERIE_NAME").asText(null);
                String seriesNameEng = node.path("SERIE_NAME_ENG").asText(null);
                String frequency     = node.path("FREQUENCY_STR").asText(null);
                String startDateStr  = node.path("START_DATE").asText(null);
                String endDateStr    = node.path("END_DATE").asText(null);

                if (seriesCode == null || seriesCode.isBlank()) {
                    log.debug("[EVDS] SERIE_CODE eksik, satır atlandı");
                    continue;
                }

                LocalDate startDate = parseEvdsDate(startDateStr, seriesCode, "START_DATE");
                LocalDate endDate   = parseEvdsDate(endDateStr,   seriesCode, "END_DATE");

                result.add(new EvdsSeriesInfo(
                        seriesCode, datagroupCode, seriesName, seriesNameEng,
                        frequency, startDate, endDate));
            }

            // Dizi dolu ama 0 seri çıktı —
            // muhtemelen 'SERIE_CODE' alan adı/yapısı değişti (sessiz hata).
            if (result.isEmpty() && nodeCount > 0) {
                log.warn("[EVDS] serieList had {} nodes but 0 series extracted. dataGroup={}", nodeCount, dataGroup);
                logIntegrationFailure(
                        IntegrationLogSupport.EVENT_EXTERNAL_API_PARSE_FAILED,
                        "EVDS serieList: " + nodeCount + " nodes present but 0 series extracted ('SERIE_CODE' field may have changed)",
                        "fetchBondSeriesList",
                        null,
                        Map.of("dataGroup", dataGroup, "nodeCount", nodeCount));
            }

        } catch (Exception e) {
            log.error("[EVDS] serieList parse hatası. dataGroup={} — {}", dataGroup, e.getMessage(), e);
            logIntegrationFailure(
                    IntegrationLogSupport.EVENT_EXTERNAL_API_PARSE_FAILED,
                    "EVDS serieList parse failed: " + e.getMessage(),
                    "fetchBondSeriesList",
                    null,
                    Map.of("dataGroup", dataGroup, "exceptionClass", e.getClass().getSimpleName()));
        }

        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private LocalDate parseEvdsDate(String dateStr, String context, String fieldName) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
            return LocalDate.parse(dateStr.trim(), EVDS_DATE_FMT);
        } catch (DateTimeParseException e) {
            log.warn("[EVDS] {} parse hatası: context={} value='{}' — {}", fieldName, context, dateStr, e.getMessage());
            return null;
        }
    }

    private void validateApiKey() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "[EVDS] API key yapılandırılmamış. " +
                    "Lütfen 'evds.api-key' veya EVDS_API_KEY environment variable'ını ayarlayın.");
        }
    }
}
