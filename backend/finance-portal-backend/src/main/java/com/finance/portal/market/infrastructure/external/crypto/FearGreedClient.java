package com.finance.portal.market.infrastructure.external.crypto;

import com.finance.portal.common.application.exception.ExternalApiException;
import com.finance.portal.common.application.logging.CentralIntegrationLogService;
import com.finance.portal.common.application.logging.IntegrationLogSupport;
import com.finance.portal.market.application.crypto.model.FearGreedPoint;
import com.finance.portal.market.infrastructure.external.crypto.dto.FngEntry;
import com.finance.portal.market.infrastructure.external.crypto.dto.FngResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Crypto Fear &amp; Greed Index istemcisi — alternative.me {@code /fng} (anahtarsız, ücretsiz).
 *
 * <p>CoinGeckoClient desenini taklit eder: {@link RestClient} + {@code @Value} base-url +
 * {@link CentralIntegrationLogService} entegrasyon logu + hata → {@link ExternalApiException}.</p>
 *
 * <p>API unix SANİYE timestamp döner; parse sırasında ×1000 ile MİLİSANİYE'ye çevrilir
 * (frontend ms bekler). Endeks piyasa-geneli tek seridir (coin başına değil).</p>
 */
@Component
public class FearGreedClient {

    private static final Logger log = LoggerFactory.getLogger(FearGreedClient.class);
    private static final String PATH_FNG = "/fng/";

    private final RestClient restClient;
    private final CentralIntegrationLogService integrationLog;

    public FearGreedClient(
            @Value("${feargreed.base-url:https://api.alternative.me}") String baseUrl,
            CentralIntegrationLogService integrationLog
    ) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(30));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl != null && !baseUrl.isBlank() ? baseUrl.trim() : "https://api.alternative.me")
                .requestFactory(factory)
                .build();
        this.integrationLog = integrationLog;
    }

    private void logIntegrationFailure(String eventType, String message, String operation,
                                       String httpStatus, Map<String, Object> metadata) {
        integrationLog.publish(
                eventType,
                "WARN",
                message,
                IntegrationLogSupport.PROVIDER_ALTERNATIVE_ME,
                operation,
                httpStatus,
                null,
                null,
                Boolean.TRUE,
                metadata,
                FearGreedClient.class.getName()
        );
    }

    /**
     * Son {@code days} günlük Fear &amp; Greed serisini çeker.
     *
     * @param days kaç günlük geçmiş (alternative.me {@code limit}); &lt;=0 ise tüm seri (limit=0)
     * @return MİLİSANİYE timestamp'li, değer/sınıfı parse edilmiş noktalar (boş olabilir)
     */
    public List<FearGreedPoint> fetchFearGreed(int days) {
        int limit = Math.max(days, 0);
        String uri = PATH_FNG + "?limit=" + limit + "&format=json";
        log.info("Calling alternative.me {} limit={}", PATH_FNG, limit);

        try {
            FngResponse body = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        if (res.getStatusCode().value() == 429) {
                            logIntegrationFailure(
                                    IntegrationLogSupport.EVENT_EXTERNAL_API_RATE_LIMITED,
                                    "Fear & Greed: rate limited (HTTP 429)",
                                    "fetchFearGreed",
                                    String.valueOf(res.getStatusCode().value()),
                                    Map.of("limit", limit));
                        }
                        throw new ExternalApiException(
                                "Fear & Greed API client error: " + res.getStatusCode() + " " + res.getStatusText());
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new ExternalApiException(
                                "Fear & Greed API server error: " + res.getStatusCode() + " " + res.getStatusText());
                    })
                    .body(FngResponse.class);

            if (body == null || body.getData() == null) {
                logIntegrationFailure(
                        IntegrationLogSupport.EVENT_EXTERNAL_API_EMPTY_RESPONSE,
                        "Fear & Greed: empty/null response body",
                        "fetchFearGreed",
                        null,
                        Map.of("limit", limit, "bodyNull", body == null));
                throw new ExternalApiException("Fear & Greed API returned empty response");
            }

            List<FearGreedPoint> points = toPoints(body.getData());

            // 200 döndü ama 0 nokta parse edildi → muhtemelen JSON yapısı değişti (sessiz hata).
            if (points.isEmpty()) {
                logIntegrationFailure(
                        IntegrationLogSupport.EVENT_EXTERNAL_API_EMPTY_RESPONSE,
                        "Fear & Greed: 0 points parsed from response (JSON structure may have changed)",
                        "fetchFearGreed",
                        null,
                        Map.of("limit", limit, "rawCount", body.getData().size()));
            }
            return points;
        } catch (ExternalApiException e) {
            throw e;
        } catch (ResourceAccessException e) {
            throw new ExternalApiException("Failed to reach Fear & Greed API. Check network.", e);
        } catch (Exception e) {
            logIntegrationFailure(
                    IntegrationLogSupport.EVENT_EXTERNAL_API_PARSE_FAILED,
                    "Fear & Greed fetch/parse failed: " + e.getMessage() + " (JSON structure may have changed)",
                    "fetchFearGreed",
                    null,
                    Map.of("limit", limit, "exceptionClass", e.getClass().getSimpleName()));
            throw new ExternalApiException("Fear & Greed API error: " + e.getMessage(), e);
        }
    }

    /**
     * Ham DTO listesini parse edilmiş modele çevirir: timestamp SANİYE→MİLİSANİYE (×1000),
     * value String→int. Bozuk/eksik satırlar atlanır (best-effort).
     */
    private List<FearGreedPoint> toPoints(List<FngEntry> entries) {
        List<FearGreedPoint> points = new ArrayList<>(entries.size());
        for (FngEntry e : entries) {
            if (e == null || e.getValue() == null || e.getTimestamp() == null) {
                continue;
            }
            try {
                int value = Integer.parseInt(e.getValue().trim());
                long tsSeconds = Long.parseLong(e.getTimestamp().trim());
                points.add(new FearGreedPoint(tsSeconds * 1000L, value, e.getValueClassification()));
            } catch (NumberFormatException nfe) {
                log.debug("Fear & Greed satırı atlandı (value={}, ts={}): {}",
                        e.getValue(), e.getTimestamp(), nfe.getMessage());
            }
        }
        return points;
    }
}
