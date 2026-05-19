package com.finance.portal.market.infrastructure.external.crypto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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

@Component
public class BinanceKlineClient {

    private static final Logger log = LoggerFactory.getLogger(BinanceKlineClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public BinanceKlineClient(
            @Value("${binance.base-url:https://api.binance.com}") String baseUrl,
            ObjectMapper objectMapper
    ) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(30));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl != null && !baseUrl.isBlank() ? baseUrl.trim() : "https://api.binance.com")
                .requestFactory(factory)
                .build();
        this.objectMapper = objectMapper;
    }

    /**
     * Ham Binance kline satırları — her satır [openTime, open, high, low, close, volume, closeTime, ...].
     */
    public List<List<Object>> fetchKlinesRaw(String symbol, String interval, int limit, long startTimeMs) {
        try {
            byte[] body = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v3/klines")
                            .queryParam("symbol", symbol)
                            .queryParam("interval", interval)
                            .queryParam("limit", limit)
                            .queryParam("startTime", startTimeMs)
                            .build())
                    .retrieve()
                    .body(byte[].class);

            if (body == null || body.length == 0) {
                return List.of();
            }
            return objectMapper.readValue(body, new TypeReference<>() {});
        } catch (HttpClientErrorException ex) {
            log.warn("Binance klines client error symbol={} interval={} status={}: {}",
                    symbol, interval, ex.getStatusCode().value(), ex.getResponseBodyAsString());
            return List.of();
        } catch (ResourceAccessException ex) {
            log.warn("Binance klines network error symbol={}: {}", symbol, ex.getMessage());
            return List.of();
        } catch (Exception ex) {
            log.warn("Binance klines parse error symbol={}: {}", symbol, ex.getMessage());
            return List.of();
        }
    }
}
