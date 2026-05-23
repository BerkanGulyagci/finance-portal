package com.finance.portal.market.infrastructure.external.economy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.portal.common.application.logging.CentralIntegrationLogService;
import com.finance.portal.common.application.logging.IntegrationLogSupport;
import com.finance.portal.market.application.economy.model.EconomySeriesPoint;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * TCMB EVDS API istemcisi — makro/ekonomi serileri (enflasyon, faiz, GSYİH, rezerv vb.).
 *
 * <p>{@code EvdsBondClient} ile aynı altyapıyı (base URL, header key, ISO-8859-1 decode)
 * kullanır; farkı, çoklu frekansı ({@code "2026-1"}, {@code "2026-Q1"}, {@code "2026"},
 * {@code "22-05-2026"}) desteklemesidir. Sıralama ham {@code Tarih} yerine
 * {@code UNIXTIME} alanı üzerinden yapılır — böylece tüm frekanslar tek tip işlenir.
 *
 * <p>URL formatı: {@code {base}/series={code}&startDate=dd-MM-yyyy&endDate=dd-MM-yyyy&type=json}
 */
@Component
public class EvdsEconomyClient {

    private static final Logger log = LoggerFactory.getLogger(EvdsEconomyClient.class);

    private static final DateTimeFormatter EVDS_DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final CentralIntegrationLogService integrationLog;

    @Value("${evds.base-url:https://evds3.tcmb.gov.tr/igmevdsms-dis}")
    private String baseUrl;

    @Value("${evds.api-key:}")
    private String apiKey;

    public EvdsEconomyClient(@Qualifier("evdsRestTemplate") RestTemplate restTemplate,
                             ObjectMapper objectMapper,
                             CentralIntegrationLogService integrationLog) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.integrationLog = integrationLog;
    }

    /**
     * Seri kodunun verilen aralıktaki noktalarını çeker (unixTime'a göre artan sıralı).
     * Hata/boş durumda boş liste döner — özet panelinin tek bir seri yüzünden çökmemesi için
     * exception fırlatılmaz.
     */
    public List<EconomySeriesPoint> fetchSeries(String seriesCode, LocalDate startDate, LocalDate endDate) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("[EVDS-Economy] API key yapılandırılmamış (evds.api-key / EVDS_API_KEY). seriesCode={}", seriesCode);
            return List.of();
        }

        String url = baseUrl.stripTrailing() + "/"
                + "series=" + seriesCode
                + "&startDate=" + startDate.format(EVDS_DATE_FMT)
                + "&endDate=" + endDate.format(EVDS_DATE_FMT)
                + "&type=json";

        log.debug("[EVDS-Economy] fetchSeries → {} ({} → {})", seriesCode,
                startDate.format(EVDS_DATE_FMT), endDate.format(EVDS_DATE_FMT));

        String body = executeGet(url, seriesCode);
        if (body == null) return List.of();

        return parseSeriesResponse(body, seriesCode);
    }

    private String executeGet(String url, String seriesCode) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("key", apiKey);
        headers.set(HttpHeaders.ACCEPT, "application/json");
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<byte[]> response = restTemplate.exchange(url, HttpMethod.GET, entity, byte[].class);
            byte[] bodyBytes = response.getBody();

            if (bodyBytes == null || bodyBytes.length == 0) {
                log.warn("[EVDS-Economy] Boş yanıt. seriesCode={}", seriesCode);
                logFailure(IntegrationLogSupport.EVENT_EXTERNAL_API_EMPTY_RESPONSE,
                        "EVDS economy: empty response body", seriesCode,
                        String.valueOf(response.getStatusCode().value()));
                return null;
            }
            // EVDS içeriği ISO-8859-1 encode (UTF-8 header'a rağmen) — Türkçe karakterler için
            return new String(bodyBytes, StandardCharsets.ISO_8859_1);

        } catch (HttpClientErrorException.NotFound e) {
            log.warn("[EVDS-Economy] 404 — seri bulunamadı. seriesCode={}", seriesCode);
            return null;
        } catch (HttpClientErrorException e) {
            // 400 = geçersiz seri kodu, 401 = key sorunu, 429 = rate limit
            log.warn("[EVDS-Economy] HTTP {} — seriesCode={} {}", e.getStatusCode(), seriesCode, e.getMessage());
            logFailure(IntegrationLogSupport.EVENT_EXTERNAL_API_FAILED,
                    "EVDS economy: client error " + e.getStatusCode(), seriesCode,
                    String.valueOf(e.getStatusCode().value()));
            return null;
        } catch (HttpServerErrorException e) {
            log.error("[EVDS-Economy] HTTP {} server error. seriesCode={}", e.getStatusCode(), seriesCode);
            logFailure(IntegrationLogSupport.EVENT_EXTERNAL_API_FAILED,
                    "EVDS economy: server error " + e.getStatusCode(), seriesCode,
                    String.valueOf(e.getStatusCode().value()));
            return null;
        } catch (ResourceAccessException e) {
            log.error("[EVDS-Economy] Bağlantı hatası (timeout/unreachable). seriesCode={} — {}", seriesCode, e.getMessage());
            logFailure(IntegrationLogSupport.EVENT_EXTERNAL_API_FAILED,
                    "EVDS economy: connection error", seriesCode, null);
            return null;
        } catch (Exception e) {
            log.error("[EVDS-Economy] Beklenmeyen hata. seriesCode={} — {}", seriesCode, e.getMessage(), e);
            return null;
        }
    }

    /**
     * EVDS seri response'unu parse eder. Beklenen format:
     * <pre>
     * {"totalCount":N,"items":[{"Tarih":"2026-1","TP_FG_J0":"3683.83","UNIXTIME":{"$numberLong":"..."}}, ...]}
     * </pre>
     * Seri kodundaki noktalar response alan adında alt çizgiye dönüşür
     * ({@code TP.FG.J0} → {@code TP_FG_J0}).
     */
    private List<EconomySeriesPoint> parseSeriesResponse(String body, String seriesCode) {
        String fieldName = seriesCode.replace('.', '_');
        List<EconomySeriesPoint> points = new ArrayList<>();

        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode items = root.path("items");

            if (items.isMissingNode() || !items.isArray()) {
                log.warn("[EVDS-Economy] 'items' yok veya dizi değil. seriesCode={}", seriesCode);
                logFailure(IntegrationLogSupport.EVENT_EXTERNAL_API_PARSE_FAILED,
                        "EVDS economy: 'items' missing or not an array", seriesCode, null);
                return List.of();
            }

            int itemCount = items.size();

            for (JsonNode item : items) {
                String periodStr = item.path("Tarih").asText(null);
                String valueStr = item.path(fieldName).asText(null);

                if (periodStr == null || valueStr == null || valueStr.isBlank()
                        || "null".equalsIgnoreCase(valueStr.trim())) {
                    continue; // tatil/eksik veri noktası
                }

                try {
                    BigDecimal value = new BigDecimal(valueStr.trim());
                    long unixTime = parseUnixTime(item.path("UNIXTIME"));
                    points.add(new EconomySeriesPoint(periodStr.trim(), value, unixTime));
                } catch (NumberFormatException e) {
                    log.debug("[EVDS-Economy] Değer parse edilemedi: field={} value='{}'", fieldName, valueStr);
                }
            }

            // items dolu ama 0 nokta → alan adı eşlemesi/yapısı değişmiş olabilir (sessiz hata)
            if (points.isEmpty() && itemCount > 0) {
                log.warn("[EVDS-Economy] {} item var ama 0 nokta çıktı (field='{}', seriesCode={})",
                        itemCount, fieldName, seriesCode);
                logFailure(IntegrationLogSupport.EVENT_EXTERNAL_API_PARSE_FAILED,
                        "EVDS economy: " + itemCount + " items but 0 points for field '" + fieldName + "'",
                        seriesCode, null);
            }

            points.sort(Comparator.comparingLong(EconomySeriesPoint::getUnixTime));

        } catch (Exception e) {
            log.error("[EVDS-Economy] Response parse hatası. seriesCode={} — {}", seriesCode, e.getMessage());
            logFailure(IntegrationLogSupport.EVENT_EXTERNAL_API_PARSE_FAILED,
                    "EVDS economy parse failed: " + e.getMessage(), seriesCode, null);
        }

        return points;
    }

    /**
     * UNIXTIME alanı genellikle {@code {"$numberLong":"1735678800"}} biçiminde gelir;
     * düz sayı geldiğinde de okunur.
     */
    private long parseUnixTime(JsonNode node) {
        if (node == null || node.isMissingNode()) return 0L;
        if (node.isObject()) {
            return node.path("$numberLong").asLong(0L);
        }
        return node.asLong(0L);
    }

    private void logFailure(String eventType, String message, String seriesCode, String httpStatus) {
        integrationLog.publish(
                eventType,
                "WARN",
                message,
                IntegrationLogSupport.PROVIDER_EVDS,
                "fetchEconomySeries",
                httpStatus,
                null,
                null,
                Boolean.TRUE,
                Map.of("seriesCode", seriesCode),
                EvdsEconomyClient.class.getName()
        );
    }
}
