package com.finance.portal.market.infrastructure.external.crypto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.portal.common.application.logging.CentralIntegrationLogService;
import com.finance.portal.common.application.logging.IntegrationLogSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class BinanceKlineClient {

    private static final Logger log = LoggerFactory.getLogger(BinanceKlineClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final CentralIntegrationLogService integrationLog;

    public BinanceKlineClient(
            @Value("${binance.base-url:https://api.binance.com}") String baseUrl,
            ObjectMapper objectMapper,
            CentralIntegrationLogService integrationLog
    ) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(30));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl != null && !baseUrl.isBlank() ? baseUrl.trim() : "https://api.binance.com")
                .requestFactory(factory)
                .build();
        this.objectMapper = objectMapper;
        this.integrationLog = integrationLog;
    }

    private void logIntegrationFailure(String eventType, String message, String httpStatus, Map<String, Object> metadata) {
        integrationLog.publish(
                eventType,
                "WARN",
                message,
                IntegrationLogSupport.PROVIDER_BINANCE,
                "fetchKlinesRaw",
                httpStatus,
                null,
                null,
                Boolean.TRUE,
                metadata,
                BinanceKlineClient.class.getName()
        );
    }

    /**
     * Ham Binance kline satırları — her satır [openTime, open, high, low, close, volume, closeTime, ...].
     */
    public List<List<Object>> fetchKlinesRaw(String symbol, String interval, int limit, long startTimeMs) {
        try {
            byte[] body = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/v3/klines")
                            .queryParam("symbol", symbol)
                            .queryParam("interval", interval)
                            .queryParam("limit", limit)
                            .queryParam("startTime", startTimeMs)
                            .build())
                    .retrieve()
                    .body(byte[].class);

            if (body == null || body.length == 0) {
                logIntegrationFailure(
                        IntegrationLogSupport.EVENT_EXTERNAL_API_EMPTY_RESPONSE,
                        "Binance klines: empty response body",
                        null,
                        Map.of("symbol", symbol, "interval", interval, "limit", limit));
                return List.of();
            }

            List<List<Object>> klines = objectMapper.readValue(body, new TypeReference<>() {});

            // 200 döndü ama parse sonrası 0 satır —
            // muhtemelen JSON yapısı değişti (sessiz hata).
            if (klines == null || klines.isEmpty()) {
                logIntegrationFailure(
                        IntegrationLogSupport.EVENT_EXTERNAL_API_EMPTY_RESPONSE,
                        "Binance klines: 0 rows parsed from response (JSON structure may have changed)",
                        null,
                        Map.of("symbol", symbol, "interval", interval, "limit", limit,
                                "klinesNull", klines == null));
                return klines == null ? List.of() : klines;
            }

            return klines;
        } catch (HttpClientErrorException ex) {
            log.warn("Binance klines client error symbol={} interval={} status={}: {}",
                    symbol, interval, ex.getStatusCode().value(), ex.getResponseBodyAsString());
            if (ex.getStatusCode().value() == 429) {
                logIntegrationFailure(
                        IntegrationLogSupport.EVENT_EXTERNAL_API_RATE_LIMITED,
                        "Binance klines: rate limited (HTTP 429)",
                        String.valueOf(ex.getStatusCode().value()),
                        Map.of("symbol", symbol, "interval", interval, "limit", limit));
            } else {
                logIntegrationFailure(
                        IntegrationLogSupport.EVENT_EXTERNAL_API_PARSE_FAILED,
                        "Binance klines client error: " + ex.getStatusCode(),
                        String.valueOf(ex.getStatusCode().value()),
                        Map.of("symbol", symbol, "interval", interval, "limit", limit));
            }
            return List.of();
        } catch (ResourceAccessException ex) {
            log.warn("Binance klines network error symbol={}: {}", symbol, ex.getMessage());
            return List.of();
        } catch (Exception ex) {
            log.warn("Binance klines parse error symbol={}: {}", symbol, ex.getMessage());
            logIntegrationFailure(
                    IntegrationLogSupport.EVENT_EXTERNAL_API_PARSE_FAILED,
                    "Binance klines fetch/parse failed: " + ex.getMessage() + " (JSON structure may have changed)",
                    null,
                    Map.of("symbol", symbol, "interval", interval, "limit", limit,
                            "exceptionClass", ex.getClass().getSimpleName()));
            return List.of();
        }
    }
}
