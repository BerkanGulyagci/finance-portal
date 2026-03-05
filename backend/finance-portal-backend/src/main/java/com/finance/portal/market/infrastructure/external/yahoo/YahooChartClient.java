package com.finance.portal.market.infrastructure.external.yahoo;

import com.finance.portal.common.infrastructure.exception.ExternalApiException;
import com.finance.portal.market.application.stock.port.YahooStockPort;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;
import java.nio.charset.StandardCharsets;

@Component
public class YahooChartClient implements YahooStockPort {

    private static final Logger log = LoggerFactory.getLogger(YahooChartClient.class);

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0 Safari/537.36";

    private final RestTemplate restTemplate;

    @Value("${market.stocks.yahoo.base-url}")
    private String yahooBaseUrl;

    public YahooChartClient(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    @CircuitBreaker(name = "yahooApi", fallbackMethod = "fallbackChart")
    @Retry(name = "yahooApi")
    public YahooChartResponseDto fetchChart(String symbol) {
        return fetchChartWithParams(symbol, "1d", "1m");
    }

    @Override
    @CircuitBreaker(name = "yahooApi", fallbackMethod = "fallbackChartWithParams")
    @Retry(name = "yahooApi")
    public YahooChartResponseDto fetchChartWithParams(String symbol, String range, String interval) {
        if (symbol == null || symbol.trim().isEmpty()) {
            throw new IllegalArgumentException("Symbol must not be empty");
        }

        String trimmedSymbol = symbol.trim();

        URI uri = UriComponentsBuilder
                .fromUriString(yahooBaseUrl)
                .pathSegment(encodePathSegment(trimmedSymbol))
                .queryParam("range", range != null ? range : "1mo")
                .queryParam("interval", interval != null ? interval : "1d")
                .build(true)
                .toUri();

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.USER_AGENT, USER_AGENT);

        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        log.debug("Fetching Yahoo chart data for symbol {} with range {} and interval {} from {}",
                trimmedSymbol, range, interval, uri);

        try {
            ResponseEntity<YahooChartResponseDto> response = restTemplate.exchange(
                    uri,
                    HttpMethod.GET,
                    requestEntity,
                    YahooChartResponseDto.class
            );

            YahooChartResponseDto body = response.getBody();
            if (body == null
                    || body.getChart() == null
                    || body.getChart().getResult() == null
                    || body.getChart().getResult().isEmpty()
                    || body.getChart().getResult().get(0).getMeta() == null) {
                throw new ExternalApiException(
                        "Yahoo Finance API returned empty chart result for symbol: " + trimmedSymbol);
            }

            return body;
        } catch (HttpClientErrorException ex) {
            throw new ExternalApiException(
                    "Yahoo Finance API returned a client error for symbol "
                            + trimmedSymbol + ": " + ex.getStatusCode(), ex);
        } catch (HttpServerErrorException ex) {
            throw new ExternalApiException(
                    "Yahoo Finance API is currently unavailable for symbol "
                            + trimmedSymbol + ": " + ex.getStatusCode(), ex);
        } catch (ResourceAccessException ex) {
            throw new ExternalApiException(
                    "Failed to access Yahoo Finance API for symbol "
                            + trimmedSymbol + ". Please check network connectivity.", ex);
        } catch (RestClientException ex) {
            throw new ExternalApiException(
                    "Unexpected error while calling Yahoo Finance API for symbol "
                            + trimmedSymbol + ": " + ex.getMessage(), ex);
        }
    }

    public YahooChartResponseDto fallbackChart(String symbol, Throwable t) {
        log.error("Yahoo Finance API fallback triggered for symbol {}: {}", symbol, t.getMessage());
        throw new ExternalApiException(
                "Yahoo Finance API is temporarily unavailable for symbol: " + symbol, t);
    }

    public YahooChartResponseDto fallbackChartWithParams(String symbol, String range, String interval, Throwable t) {
        log.error("Yahoo Finance API fallback triggered for symbol {} with range {} and interval {}: {}",
                symbol, range, interval, t.getMessage());
        throw new ExternalApiException(
                "Yahoo Finance API is temporarily unavailable for symbol: " + symbol, t);
    }

    private String encodePathSegment(String value) {
        // Encode only characters that are not allowed in path segments
        return UriComponentsBuilder.fromPath("/{segment}")
                .buildAndExpand(value)
                .encode(StandardCharsets.UTF_8)
                .getPath()
                .substring(1);
    }
}

