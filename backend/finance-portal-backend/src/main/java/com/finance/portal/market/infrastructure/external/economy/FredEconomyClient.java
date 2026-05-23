package com.finance.portal.market.infrastructure.external.economy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.portal.market.application.economy.model.EconomySeriesPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * FRED (Federal Reserve Economic Data, St. Louis Fed) API istemcisi — küresel makro
 * serileri (örn. ABD TÜFE/CPI). EVDS istemcisinden ayrıdır: kimlik doğrulama query
 * param ({@code api_key}), yanıt JSON ve {@code observations[].date}="yyyy-MM-dd".
 *
 * <p>URL: {@code {base}/series/observations?series_id={id}&api_key={key}&file_type=json
 * &observation_start=yyyy-MM-dd&observation_end=yyyy-MM-dd}
 *
 * <p>API anahtarı yoksa (yapılandırılmamışsa) boş liste döner — panel/grafik tek bir
 * küresel seri yüzünden çökmez. Anahtar ücretsizdir: https://fred.stlouisfed.org/docs/api/api_key.html
 */
@Component
public class FredEconomyClient {

    private static final Logger log = LoggerFactory.getLogger(FredEconomyClient.class);

    private static final DateTimeFormatter FRED_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${fred.base-url:https://api.stlouisfed.org/fred}")
    private String baseUrl;

    @Value("${fred.api-key:}")
    private String apiKey;

    public FredEconomyClient(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Seri kimliğinin verilen aralıktaki gözlemlerini çeker (unixTime'a göre artan sıralı).
     * Hata/boş/anahtar yok durumunda boş liste döner — exception fırlatılmaz.
     */
    public List<EconomySeriesPoint> fetchSeries(String seriesId, LocalDate startDate, LocalDate endDate) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("[FRED] API anahtarı yapılandırılmamış (fred.api-key / FRED_API_KEY). seriesId={}", seriesId);
            return List.of();
        }

        String url = UriComponentsBuilder.fromHttpUrl(baseUrl.stripTrailing() + "/series/observations")
                .queryParam("series_id", seriesId)
                .queryParam("api_key", apiKey)
                .queryParam("file_type", "json")
                .queryParam("observation_start", startDate.format(FRED_DATE_FMT))
                .queryParam("observation_end", endDate.format(FRED_DATE_FMT))
                .toUriString();

        try {
            String body = restTemplate.getForObject(url, String.class);
            if (body == null || body.isBlank()) {
                log.warn("[FRED] Boş yanıt. seriesId={}", seriesId);
                return List.of();
            }
            return parse(body, seriesId);
        } catch (HttpClientErrorException e) {
            // 400 = geçersiz seri/anahtar, 429 = rate limit
            log.warn("[FRED] HTTP {} — seriesId={} {}", e.getStatusCode(), seriesId, e.getMessage());
            return List.of();
        } catch (ResourceAccessException e) {
            log.error("[FRED] Bağlantı hatası (timeout/unreachable). seriesId={} — {}", seriesId, e.getMessage());
            return List.of();
        } catch (Exception e) {
            log.error("[FRED] Beklenmeyen hata. seriesId={} — {}", seriesId, e.getMessage());
            return List.of();
        }
    }

    /**
     * FRED response'unu parse eder. Beklenen format:
     * <pre>{"observations":[{"date":"2026-04-01","value":"320.321"}, ...]}</pre>
     * Eksik gözlem değeri "." olarak gelir ve atlanır. {@code period} EVDS aylık
     * formatına ("yyyy-M") eşlenir; {@code unixTime} tarihten (UTC gün başı) hesaplanır.
     */
    private List<EconomySeriesPoint> parse(String body, String seriesId) {
        List<EconomySeriesPoint> points = new ArrayList<>();
        try {
            JsonNode obs = objectMapper.readTree(body).path("observations");
            if (obs.isMissingNode() || !obs.isArray()) {
                log.warn("[FRED] 'observations' yok veya dizi değil. seriesId={}", seriesId);
                return List.of();
            }
            for (JsonNode o : obs) {
                String dateStr = o.path("date").asText(null);
                String valueStr = o.path("value").asText(null);
                if (dateStr == null || valueStr == null || valueStr.isBlank() || ".".equals(valueStr.trim())) {
                    continue;
                }
                try {
                    LocalDate date = LocalDate.parse(dateStr.trim(), FRED_DATE_FMT);
                    BigDecimal value = new BigDecimal(valueStr.trim());
                    String period = date.getYear() + "-" + date.getMonthValue();
                    long unixTime = date.atStartOfDay(ZoneOffset.UTC).toEpochSecond();
                    points.add(new EconomySeriesPoint(period, value, unixTime));
                } catch (Exception e) {
                    log.debug("[FRED] Gözlem parse edilemedi: date='{}' value='{}'", dateStr, valueStr);
                }
            }
            points.sort(Comparator.comparingLong(EconomySeriesPoint::getUnixTime));
        } catch (Exception e) {
            log.error("[FRED] Response parse hatası. seriesId={} — {}", seriesId, e.getMessage());
        }
        return points;
    }
}
