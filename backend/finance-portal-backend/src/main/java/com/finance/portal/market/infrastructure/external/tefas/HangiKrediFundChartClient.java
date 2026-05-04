package com.finance.portal.market.infrastructure.external.tefas;

import com.finance.portal.market.application.funds.model.FundHistoryPoint;
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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HangiKredi fon grafik API client.
 *
 * Endpoint: GET https://www.hangikredi.com/api/investment-services/v1/chart/fund
 * Params:   id={hangiKrediFundId}&day={days}
 *
 * Response format:
 * {
 *   "data": {
 *     "chart": [
 *       {
 *         "changePercent": 0,
 *         "date": "202308160000",
 *         "dateText": "16/08/2023",
 *         "last": 0.995943,
 *         "dailyChangeAmount": 0,
 *         "dailyChangePercent": 0,
 *         "dateTime": "2023-08-16T00:00:00"
 *       }
 *     ]
 *   }
 * }
 */
@Component
public class HangiKrediFundChartClient {

    private static final Logger log = LoggerFactory.getLogger(HangiKrediFundChartClient.class);

    private static final String HK_BASE_URL   = "https://www.hangikredi.com";
    private static final String CHART_API_URL = HK_BASE_URL + "/api/investment-services/v1/chart/fund";
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * fundCode -> hangiKrediId eşleşmesi.
     * Fon listesi veya detay sayfası scraping sırasında doldurulur.
     */
    private final ConcurrentHashMap<String, Long> fundIdCache = new ConcurrentHashMap<>();

    public HangiKrediFundChartClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * HangiKredi fund id'sini kaydet (liste/detay scraping sırasında çağrılır).
     */
    public void registerFundId(String code, Long hangiKrediId) {
        if (code != null && hangiKrediId != null && hangiKrediId > 0) {
            fundIdCache.put(code.toUpperCase(), hangiKrediId);
            log.debug("Registered HangiKredi id: {} -> {}", code.toUpperCase(), hangiKrediId);
        }
    }

    /**
     * Kayıtlı HangiKredi fund id'sini döndürür.
     */
    public Long getRegisteredId(String code) {
        if (code == null) return null;
        return fundIdCache.get(code.toUpperCase());
    }

    /**
     * Fon grafik verisini çeker ve normalize eder.
     *
     * @param code          Fon kodu (örn: "AEV")
     * @param hangiKrediId  HangiKredi internal id (örn: 4409) — null ise cache'den bakılır
     * @param days          Kaç günlük veri (7, 30, 90, 180, 365, 1095, 1825)
     * @return Normalize edilmiş fiyat noktaları listesi
     */
    public List<FundHistoryPoint> fetchChartData(String code, Long hangiKrediId, int days) {
        Long id = resolveId(code, hangiKrediId);
        if (id == null || id <= 0) {
            log.warn("HangiKredi fund id not found for code={} (provided={}, cached={})",
                    code, hangiKrediId, fundIdCache.get(code != null ? code.toUpperCase() : ""));
            return List.of();
        }

        String url = CHART_API_URL + "?id=" + id + "&day=" + days;
        log.info("Fetching HangiKredi chart: code={}, id={}, days={}, url={}", code, id, days, url);

        try {
            HttpHeaders headers = buildHeaders(code);
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            String body = response.getBody();
            if (body == null || body.isBlank()) {
                log.warn("Empty response from HangiKredi chart API for code={}", code);
                return List.of();
            }

            log.debug("HangiKredi chart response for code={}: {}", code, body.substring(0, Math.min(200, body.length())));
            return parseChartResponse(body, code);
        } catch (Exception e) {
            log.warn("Failed to fetch HangiKredi chart for code={}, id={}, days={}: {}", code, id, days, e.getMessage());
            return List.of();
        }
    }

    /**
     * HangiKredi chart API response'unu normalize eder.
     *
     * Beklenen format:
     * { "data": { "chart": [ { "dateTime": "2023-08-16T00:00:00", "dateText": "16/08/2023", "last": 0.995943, ... } ] } }
     */
    private List<FundHistoryPoint> parseChartResponse(String body, String code) {
        try {
            JsonNode root = objectMapper.readTree(body);

            // data.chart path'ini bul
            JsonNode chartArray = extractChartArray(root);

            if (chartArray == null || !chartArray.isArray() || chartArray.isEmpty()) {
                log.warn("No chart array found in HangiKredi response for code={}. Root keys: {}", code, root.fieldNames());
                return List.of();
            }

            List<FundHistoryPoint> points = new ArrayList<>();
            for (JsonNode node : chartArray) {
                FundHistoryPoint point = mapChartNode(node);
                if (point != null) {
                    points.add(point);
                }
            }

            // Tarihe göre sırala
            points.sort((a, b) -> a.getDate().compareTo(b.getDate()));
            log.info("Parsed {} chart points for code={}", points.size(), code);
            return points;
        } catch (Exception e) {
            log.warn("Failed to parse HangiKredi chart response for code={}: {}", code, e.getMessage());
            return List.of();
        }
    }

    /**
     * JSON içinde chart array'ini bulur.
     * Öncelik sırası: data.chart → data.fundChart → data (array) → chart → fundChart → root (array)
     */
    private JsonNode extractChartArray(JsonNode root) {
        // Format 1 (beklenen): { "data": { "chart": [...] } }
        if (root.has("data")) {
            JsonNode data = root.path("data");
            if (data.has("chart") && data.path("chart").isArray()) {
                return data.path("chart");
            }
            // Format 2: { "data": { "fundChart": [...] } }
            if (data.has("fundChart") && data.path("fundChart").isArray()) {
                return data.path("fundChart");
            }
            // Format 3: { "data": [...] }
            if (data.isArray()) return data;
        }
        // Format 4: { "chart": [...] }
        if (root.has("chart") && root.path("chart").isArray()) {
            return root.path("chart");
        }
        // Format 5: { "fundChart": [...] }
        if (root.has("fundChart") && root.path("fundChart").isArray()) {
            return root.path("fundChart");
        }
        // Format 6: düz array [...]
        if (root.isArray()) return root;
        return null;
    }

    /**
     * Tek bir chart node'unu FundHistoryPoint'e map eder.
     *
     * HangiKredi alanları:
     *   dateTime  → date       ("2023-08-16T00:00:00" → "2023-08-16")
     *   dateText  → dateText   ("16/08/2023")
     *   last      → price
     *   changePercent         → changePercent
     *   dailyChangeAmount     → dailyChangeAmount
     *   dailyChangePercent    → dailyChangePercent
     */
    private FundHistoryPoint mapChartNode(JsonNode node) {
        // Tarih — dateTime öncelikli
        String date = parseDateFromNode(node);
        if (date == null) return null;

        // Fiyat — last alanı
        double price = node.path("last").asDouble(0);
        if (price <= 0) return null;

        String dateText = node.path("dateText").asText(null);
        double changePercent      = node.path("changePercent").asDouble(0);
        double dailyChangeAmount  = node.path("dailyChangeAmount").asDouble(0);
        double dailyChangePercent = node.path("dailyChangePercent").asDouble(0);

        return new FundHistoryPoint(date, dateText, price, changePercent, dailyChangeAmount, dailyChangePercent);
    }

    /**
     * Node'dan tarih string'ini çıkarır.
     * Öncelik: dateTime → dateText → date
     */
    private String parseDateFromNode(JsonNode node) {
        // 1. dateTime: "2023-08-16T00:00:00" → "2023-08-16"
        String dateTime = node.path("dateTime").asText(null);
        if (dateTime != null && !dateTime.isBlank() && dateTime.length() >= 10) {
            return dateTime.substring(0, 10); // "2023-08-16"
        }

        // 2. dateText: "16/08/2023" → "2023-08-16"
        String dateText = node.path("dateText").asText(null);
        if (dateText != null && dateText.matches("\\d{2}/\\d{2}/\\d{4}")) {
            String[] parts = dateText.split("/");
            return parts[2] + "-" + parts[1] + "-" + parts[0];
        }

        // 3. date: "202308160000" → "2023-08-16"
        String date = node.path("date").asText(null);
        if (date != null) {
            if (date.matches("\\d{12}")) {
                return date.substring(0, 4) + "-" + date.substring(4, 6) + "-" + date.substring(6, 8);
            }
            if (date.matches("\\d{8}")) {
                return date.substring(0, 4) + "-" + date.substring(4, 6) + "-" + date.substring(6, 8);
            }
            if (date.matches("\\d{4}-\\d{2}-\\d{2}")) {
                return date;
            }
        }

        return null;
    }

    private Long resolveId(String code, Long provided) {
        if (provided != null && provided > 0) return provided;
        if (code != null) return fundIdCache.get(code.toUpperCase());
        return null;
    }

    private HttpHeaders buildHeaders(String code) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.USER_AGENT, UA);
        headers.set(HttpHeaders.ACCEPT, "application/json");
        headers.set("Content-Type", "application/json");
        headers.set(HttpHeaders.REFERER,
                HK_BASE_URL + "/yatirim-araclari/fon/" + (code != null ? code.toLowerCase() : ""));
        headers.set("x-production-mode", "true");
        headers.set("device", "0");
        headers.set(HttpHeaders.ACCEPT_LANGUAGE, "tr-TR,tr;q=0.9");
        return headers;
    }
}
