package com.finance.portal.market.infrastructure.external.viop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.portal.common.application.logging.CentralIntegrationLogService;
import com.finance.portal.common.application.logging.IntegrationLogSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * İş Yatırım VİOP sözleşme spesifikasyon client'ı — sözleşme büyüklüğü (multiplier) ve
 * başlangıç teminatı (TL tutar) çeker.
 *
 * <p>Endpoint: GET {@code .../Common/Data.aspx/VadeliIslemler} — tüm aktif VİOP sözleşmelerini
 * tek JSON'da döndürür. İlgilenilen alanlar:
 * <pre>
 *   "Title": "F_USDTRY0626",          → kontrat sembolü (büyüklük/teminat bu prefix'e bağlı)
 *   "SOZLESME_BUYUKLUGU": "1000",      → multiplier (bir kontratın temsil ettiği birim)
 *   "BASLANGIC_TEMINATI": "5362,674",  → Takasbank başlangıç teminatı (TL tutar, virgül ondalık)
 *   "PARA_BIRIMI": "TRY",
 *   "UZLASMA_TIPI": "Cash Settlement"  → CASH / PHYSICAL
 * </pre>
 *
 * <p>{@link com.finance.portal.portfolio.application.viop.spec.ViopContractSpecRegistry}, YAML'da
 * bulunamayan sözleşmeler için bu client'tan büyüklük + teminat alır; başarısız olursa benzer-tür
 * fallback'e düşer (uygulama asla çökmez).
 *
 * <p>Türkçe sayı formatı: kaynak {@code "5362,674"} (binlik nokta + virgül ondalık) verir;
 * {@link #parseTrNumber} bunu BigDecimal'a çevirir.
 */
@Component
public class IsYatirimViopSpecClient {

    private static final Logger log = LoggerFactory.getLogger(IsYatirimViopSpecClient.class);

    private static final String URL =
            "https://www.isyatirim.com.tr/_Layouts/15/IsYatirim.Website/Common/Data.aspx/VadeliIslemler";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final CentralIntegrationLogService integrationLog;

    public IsYatirimViopSpecClient(RestTemplate restTemplate, ObjectMapper objectMapper,
                                   CentralIntegrationLogService integrationLog) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.integrationLog = integrationLog;
    }

    /**
     * Tüm VİOP sözleşmelerinin spec'lerini çeker.
     *
     * @return {@code Title (büyük harf) → spec} map'i; hata/boş durumda boş map (asla null).
     *         Çağıran taraf boş map'i "scrape başarısız → fallback" sinyali olarak ele alır.
     */
    public Map<String, IsYatirimViopSpec> fetchSpecs() {
        try {
            HttpEntity<Void> entity = new HttpEntity<>(buildHeaders());
            ResponseEntity<String> response = restTemplate.exchange(URL, HttpMethod.GET, entity, String.class);
            String body = response.getBody();
            String httpStatus = String.valueOf(response.getStatusCode().value());

            if (body == null || body.isBlank()) {
                logFailure(IntegrationLogSupport.EVENT_EXTERNAL_API_EMPTY_RESPONSE,
                        "İş Yatırım VadeliIslemler: empty response body", httpStatus, Map.of("url", URL));
                return Map.of();
            }

            Map<String, IsYatirimViopSpec> specs = parse(body, httpStatus);
            if (specs.isEmpty()) {
                logFailure(IntegrationLogSupport.EVENT_EXTERNAL_API_EMPTY_RESPONSE,
                        "İş Yatırım VadeliIslemler: response geldi ama 0 spec çıkarıldı (JSON yapısı değişmiş olabilir)",
                        httpStatus, Map.of("url", URL));
            } else {
                log.info("İş Yatırım VİOP spec çekildi: {} sözleşme", specs.size());
            }
            return specs;

        } catch (Exception e) {
            log.warn("İş Yatırım VadeliIslemler fetch/parse başarısız: {}", e.getMessage());
            logFailure(IntegrationLogSupport.EVENT_EXTERNAL_API_PARSE_FAILED,
                    "İş Yatırım VadeliIslemler fetch/parse başarısız: " + e.getMessage(),
                    null, Map.of("url", URL, "exceptionClass", e.getClass().getSimpleName()));
            return Map.of();
        }
    }

    private Map<String, IsYatirimViopSpec> parse(String body, String httpStatus) {
        Map<String, IsYatirimViopSpec> result = new HashMap<>();
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode arr = root.get("value");
            if (arr == null || !arr.isArray()) {
                logFailure(IntegrationLogSupport.EVENT_EXTERNAL_API_PARSE_FAILED,
                        "İş Yatırım VadeliIslemler: 'value' array yok (JSON yapısı değişmiş olabilir)",
                        httpStatus, Map.of("valueNull", arr == null));
                return Map.of();
            }

            for (JsonNode node : arr) {
                String title = text(node, "Title");
                if (title == null || title.isBlank()) continue;

                BigDecimal multiplier = parseTrNumber(text(node, "SOZLESME_BUYUKLUGU"));
                BigDecimal marginAmount = parseTrNumber(text(node, "BASLANGIC_TEMINATI"));
                // Büyüklük olmadan spec anlamsız; teminat opsiyonel (yoksa oran fallback'i devreye girer).
                if (multiplier == null || multiplier.signum() <= 0) continue;

                String currency = orDefault(text(node, "PARA_BIRIMI"), "TRY").trim().toUpperCase();
                boolean physical = containsIgnoreCase(text(node, "UZLASMA_TIPI"), "phys")
                        || containsIgnoreCase(text(node, "UZLASMA_TIPI"), "fiz");

                result.put(title.trim().toUpperCase(),
                        new IsYatirimViopSpec(title.trim().toUpperCase(), multiplier, marginAmount, currency, physical));
            }
        } catch (Exception e) {
            log.warn("İş Yatırım VadeliIslemler parse hatası: {}", e.getMessage());
            logFailure(IntegrationLogSupport.EVENT_EXTERNAL_API_PARSE_FAILED,
                    "İş Yatırım VadeliIslemler parse hatası: " + e.getMessage(),
                    httpStatus, Map.of("exceptionClass", e.getClass().getSimpleName()));
        }
        return result;
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static String orDefault(String v, String def) {
        return v == null || v.isBlank() ? def : v;
    }

    private static boolean containsIgnoreCase(String s, String sub) {
        return s != null && s.toLowerCase().contains(sub);
    }

    /**
     * Türkçe sayı string'ini ({@code "5.362,674"} veya {@code "1000"}) BigDecimal'a çevirir.
     * Binlik nokta atılır, virgül ondalık ayracı noktaya çevrilir. Parse edilemezse null.
     */
    static BigDecimal parseTrNumber(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String cleaned = raw.trim().replace(".", "").replace(",", ".");
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.ACCEPT, "application/json, text/javascript, */*; q=0.01");
        headers.set("X-Requested-With", "XMLHttpRequest");
        headers.set(HttpHeaders.REFERER, "https://www.isyatirim.com.tr/tr-tr/analiz/Sayfalar/viop.aspx");
        headers.set(HttpHeaders.USER_AGENT,
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");
        headers.set(HttpHeaders.ACCEPT_LANGUAGE, "tr-TR,tr;q=0.9,en;q=0.8");
        return headers;
    }

    private void logFailure(String eventType, String message, String httpStatus, Map<String, Object> metadata) {
        integrationLog.publish(
                eventType, "WARN", message,
                IntegrationLogSupport.PROVIDER_ISYATIRIM, "fetchViopSpecs",
                httpStatus, null, null, Boolean.TRUE, metadata,
                IsYatirimViopSpecClient.class.getName());
    }

    /**
     * Tek bir VİOP sözleşmesinin İş Yatırım'dan çekilen ham spec'i.
     *
     * @param title         kontrat sembolü (F_USDTRY0626)
     * @param multiplier    sözleşme büyüklüğü (SOZLESME_BUYUKLUGU)
     * @param marginAmount  başlangıç teminatı TL tutarı (BASLANGIC_TEMINATI); null olabilir
     * @param currency      TRY/USD/...
     * @param physical      true = fiziki teslimat, false = nakdi uzlaşma
     */
    public record IsYatirimViopSpec(
            String title,
            BigDecimal multiplier,
            BigDecimal marginAmount,
            String currency,
            boolean physical
    ) { }
}
