package com.finance.portal.market.infrastructure.external.yahoo;

import com.finance.portal.common.application.exception.ExternalApiException;
import com.finance.portal.common.application.logging.CentralIntegrationLogService;
import com.finance.portal.common.application.logging.IntegrationLogSupport;
import com.finance.portal.market.application.stock.StockSummary;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class YahooChartClient {

    private static final Logger log = LoggerFactory.getLogger(YahooChartClient.class);

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0 Safari/537.36";
    private static final String FALLBACK_BASE_URL = "https://query2.finance.yahoo.com/v8/finance/chart";

    private final RestTemplate restTemplate;
    private final CentralIntegrationLogService integrationLogService;

    @Value("${market.stocks.yahoo.base-url}")
    private String yahooBaseUrl;

    public YahooChartClient(RestTemplateBuilder restTemplateBuilder,
                            CentralIntegrationLogService integrationLogService) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(5))
                .build();
        this.integrationLogService = integrationLogService;
    }

    @CircuitBreaker(name = "yahooApi", fallbackMethod = "fallbackChart")
    @Retry(name = "yahooApi")
    public YahooChartResponseDto fetchChart(String symbol) {
        return fetchChartWithParams(symbol, "1d", "1m");
    }

    @CircuitBreaker(name = "yahooApi", fallbackMethod = "fallbackChartWithParams")
    @Retry(name = "yahooApi")
    public YahooChartResponseDto fetchChartWithParams(String symbol, String range, String interval) {
        if (symbol == null || symbol.trim().isEmpty()) {
            throw new IllegalArgumentException("Symbol must not be empty");
        }

        String trimmedSymbol = symbol.trim();
        String yahooRange = normalizeYahooRange(range);
        String yahooInterval = normalizeYahooInterval(interval);

        URI uri = UriComponentsBuilder
                .fromUriString(yahooBaseUrl)
                .pathSegment(encodePathSegment(trimmedSymbol))
                .queryParam("range", yahooRange)
                .queryParam("interval", yahooInterval)
                .build(true)
                .toUri();

        URI fallbackUri = UriComponentsBuilder
                .fromUriString(FALLBACK_BASE_URL)
                .pathSegment(encodePathSegment(trimmedSymbol))
                .queryParam("range", yahooRange)
                .queryParam("interval", yahooInterval)
                .build(true)
                .toUri();

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.USER_AGENT, USER_AGENT);
        headers.add(HttpHeaders.ACCEPT, "application/json,text/plain,*/*");
        headers.add(HttpHeaders.ACCEPT_LANGUAGE, "en-US,en;q=0.9,tr-TR;q=0.8,tr;q=0.7");

        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        log.debug("Fetching Yahoo chart data for symbol {} with range {} and interval {} from {}",
                trimmedSymbol, range, interval, uri);

        try {
            return exchangeChart(uri, requestEntity, trimmedSymbol);
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                log.warn("Yahoo rate limited query1 for {}. Trying query2 fallback.", trimmedSymbol);
                integrationLogService.publish(
                        IntegrationLogSupport.EVENT_EXTERNAL_API_RATE_LIMITED,
                        "WARN",
                        "Yahoo Finance rate limited",
                        IntegrationLogSupport.PROVIDER_YAHOO,
                        "chart",
                        "429",
                        null,
                        null,
                        null,
                        Map.of("symbol", trimmedSymbol, "host", "query1"),
                        YahooChartClient.class.getName()
                );
                try {
                    YahooChartResponseDto recovered = exchangeChart(fallbackUri, requestEntity, trimmedSymbol);
                    integrationLogService.publish(
                            IntegrationLogSupport.EVENT_EXTERNAL_API_FALLBACK_USED,
                            "WARN",
                            "Yahoo Finance host fallback used",
                            IntegrationLogSupport.PROVIDER_YAHOO,
                            "chart",
                            "200",
                            null,
                            true,
                            null,
                            Map.of("symbol", trimmedSymbol, "host", "query2"),
                            YahooChartClient.class.getName()
                    );
                    return recovered;
                } catch (Exception fallbackEx) {
                    throw new ExternalApiException(
                            "Yahoo Finance API rate limit reached for symbol "
                                    + trimmedSymbol + " (query1 and query2)", fallbackEx);
                }
            }
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

    private YahooChartResponseDto exchangeChart(URI uri, HttpEntity<Void> requestEntity, String symbol) {
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
            integrationLogService.publish(
                    IntegrationLogSupport.EVENT_EXTERNAL_API_EMPTY_RESPONSE,
                    "ERROR",
                    "Yahoo Finance returned empty chart",
                    IntegrationLogSupport.PROVIDER_YAHOO,
                    "chart",
                    null,
                    null,
                    null,
                    null,
                    Map.of("symbol", symbol),
                    YahooChartClient.class.getName()
            );
            throw new ExternalApiException(
                    "Yahoo Finance API returned empty chart result for symbol: " + symbol);
        }
        return body;
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

    /**
     * Yahoo chart API küçük harf aralık bekler ({@code 1y}); {@code 1Y} gönderilirse tek günlük veri dönebilir.
     */
    static String normalizeYahooRange(String range) {
        if (range == null || range.isBlank()) {
            return "1mo";
        }
        return switch (range.trim().toUpperCase(Locale.ROOT)) {
            case "1D" -> "1d";
            case "5D", "1W" -> "5d";
            case "1M" -> "1mo";
            case "3M" -> "3mo";
            case "6M" -> "6mo";
            case "1Y" -> "1y";
            case "2Y" -> "2y";
            case "5Y" -> "5y";
            case "YTD" -> "ytd";
            case "MAX" -> "max";
            default -> range.trim().toLowerCase(Locale.ROOT);
        };
    }

    static String normalizeYahooInterval(String interval) {
        if (interval == null || interval.isBlank()) {
            return "1d";
        }
        return interval.trim().toLowerCase(Locale.ROOT);
    }

    private String encodePathSegment(String value) {
        // Encode only characters that are not allowed in path segments
        String encoded = UriComponentsBuilder.fromPath("/{segment}")
                .buildAndExpand(value)
                .encode(StandardCharsets.UTF_8)
                .getPath();
        return encoded != null && encoded.length() > 1 ? encoded.substring(1) : "";
    }

    public List<StockSummary> fetchQuoteBatch(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) return List.of();

        String symbolsCsv = String.join(",", symbols);
        URI uri = UriComponentsBuilder
                .fromUriString("https://query1.finance.yahoo.com/v7/finance/quote")
                .queryParam("symbols", symbolsCsv)
                .build(true)
                .toUri();

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.USER_AGENT, USER_AGENT);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        log.debug("Fetching batch quotes for {} symbols", symbols.size());

        try {
            ResponseEntity<Map> response = restTemplate.exchange(uri, HttpMethod.GET, entity, Map.class);
            return parseQuoteResponse(response.getBody());
        } catch (Exception ex) {
            log.warn("Batch quote fetch failed: {}", ex.getMessage());
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<StockSummary> parseQuoteResponse(Map<?, ?> body) {
        List<StockSummary> result = new ArrayList<>();
        if (body == null) return result;

        try {
            Map<?, ?> quoteResponse = (Map<?, ?>) body.get("quoteResponse");
            if (quoteResponse == null) return result;
            List<?> quotes = (List<?>) quoteResponse.get("result");
            if (quotes == null) return result;

            for (Object q : quotes) {
                Map<?, ?> quote = (Map<?, ?>) q;
                StockSummary s = new StockSummary();
                s.setSymbol(str(quote, "symbol"));
                s.setName(str(quote, "longName") != null ? str(quote, "longName") : str(quote, "shortName"));
                s.setCurrency(str(quote, "currency"));
                s.setExchange(str(quote, "fullExchangeName"));
                s.setPrice(bd(quote, "regularMarketPrice"));
                s.setChange(bd(quote, "regularMarketChange"));
                s.setChangePercent(bd(quote, "regularMarketChangePercent"));
                s.setDayHigh(bd(quote, "regularMarketDayHigh"));
                s.setDayLow(bd(quote, "regularMarketDayLow"));
                Object vol = quote.get("regularMarketVolume");
                if (vol instanceof Number) s.setVolume(((Number) vol).longValue());
                result.add(s);
            }
        } catch (Exception ex) {
            log.warn("Failed to parse batch quote response: {}", ex.getMessage());
        }
        return result;
    }

    private String str(Map<?, ?> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }

    private BigDecimal bd(Map<?, ?> map, String key) {
        Object v = map.get(key);
        if (v == null) return null;
        try {
            return new BigDecimal(v.toString()).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            return null;
        }
    }
}

