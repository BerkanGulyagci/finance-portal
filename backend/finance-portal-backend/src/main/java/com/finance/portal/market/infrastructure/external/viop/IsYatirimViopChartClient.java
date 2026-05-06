package com.finance.portal.market.infrastructure.external.viop;

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
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * İş Yatırım VİOP grafik verisi client'ı.
 * <p>
 * Endpoint: GET https://www.isyatirim.com.tr/_Layouts/15/IsYatirim.Website/Common/ChartData.aspx/IndexHistoricalAll
 * Query params: period, from (yyyyMMddHHmmss), to (yyyyMMddHHmmss), endeks (örn: F_THYAO0626)
 * <p>
 * Response örneği:
 * {"data": [[1777269600000, 344.29999], [1777273200000, 343.64999]], "timestamp": "..."}
 */
@Component
public class IsYatirimViopChartClient {

    private static final Logger log = LoggerFactory.getLogger(IsYatirimViopChartClient.class);

    private static final String BASE_URL = "https://www.isyatirim.com.tr";
    private static final String PATH = "/_Layouts/15/IsYatirim.Website/Common/ChartData.aspx/IndexHistoricalAll";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public IsYatirimViopChartClient(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * İş Yatırım'dan VİOP sözleşme grafik verisi çeker.
     *
     * @param isYatirimEndeksCode İş Yatırım endeks kodu (örn: F_THYAO0626)
     * @param from                Başlangıç tarihi
     * @param to                  Bitiş tarihi
     * @param period              Periyot (dakika cinsinden, örn: 60)
     * @return Grafik noktaları listesi; hata durumunda boş liste
     */
    public List<IsYatirimChartPoint> fetchChart(String isYatirimEndeksCode,
                                                LocalDateTime from,
                                                LocalDateTime to,
                                                int period) {
        String fromStr = from.format(DATE_FORMAT);
        String toStr = to.format(DATE_FORMAT);

        String url = UriComponentsBuilder
                .fromHttpUrl(BASE_URL + PATH)
                .queryParam("period", period)
                .queryParam("from", fromStr)
                .queryParam("to", toStr)
                .queryParam("endeks", isYatirimEndeksCode)
                .build(false)
                .toUriString();

        log.info("Fetching İş Yatırım VIOP chart: endeks={}, from={}, to={}, period={}",
                isYatirimEndeksCode, fromStr, toStr, period);
        log.debug("Request URL: {}", url);

        try {
            HttpHeaders headers = buildHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            String body = response.getBody();

            if (body == null || body.isBlank()) {
                log.warn("İş Yatırım returned empty body for endeks={}", isYatirimEndeksCode);
                return List.of();
            }

            List<IsYatirimChartPoint> points = parseResponse(body, isYatirimEndeksCode);
            log.info("Fetched {} chart points for endeks={}", points.size(), isYatirimEndeksCode);
            return points;

        } catch (Exception e) {
            log.error("Failed to fetch İş Yatırım chart for endeks={}: {}", isYatirimEndeksCode, e.getMessage(), e);
            return List.of();
        }
    }

    private List<IsYatirimChartPoint> parseResponse(String body, String endeksCode) {
        List<IsYatirimChartPoint> result = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode dataNode = root.get("data");

            if (dataNode == null || !dataNode.isArray()) {
                log.warn("No 'data' array in İş Yatırım response for endeks={}", endeksCode);
                return List.of();
            }

            for (JsonNode point : dataNode) {
                if (!point.isArray() || point.size() < 2) {
                    log.warn("Unexpected point format: {}", point);
                    continue;
                }
                long timestamp = point.get(0).asLong();
                BigDecimal value = new BigDecimal(point.get(1).asText());
                result.add(new IsYatirimChartPoint(timestamp, value));
            }

        } catch (Exception e) {
            log.error("Failed to parse İş Yatırım response for endeks={}: {}", endeksCode, e.getMessage(), e);
        }
        return result;
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
}
