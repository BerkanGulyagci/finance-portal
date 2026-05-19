package com.finance.portal.market.infrastructure.external.crypto;

import com.finance.portal.common.application.exception.ExternalApiException;
import com.finance.portal.market.infrastructure.external.crypto.dto.CoinGeckoMarketItemDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class CoinGeckoClient {

    private static final Logger log = LoggerFactory.getLogger(CoinGeckoClient.class);
    private static final String HEADER_API_KEY = "x-cg-demo-api-key";
    private static final String PATH_MARKETS = "/coins/markets";

    private static final Set<String> ALLOWED_CURRENCIES = Set.of("try", "usd", "eur");

    private final RestClient restClient;

    public CoinGeckoClient(
            @Value("${coingecko.base-url}") String baseUrl,
            @Value("${coingecko.api-key}") String apiKey
    ) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(45));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl != null && !baseUrl.isBlank() ? baseUrl.trim() : "https://api.coingecko.com/api/v3")
                .defaultHeader(HEADER_API_KEY, apiKey != null ? apiKey : "")
                .requestFactory(factory)
                .build();
    }

    public List<CoinGeckoMarketItemDto> fetchMarkets(int coingeckoPage, int perPage, String currency) {
        String cur = resolveCurrency(currency);
        log.info("Calling CoinGecko /coins/markets vs_currency={} page={} per_page={}", cur, coingeckoPage, perPage);

        String uri = PATH_MARKETS + "?vs_currency=" + cur
                + "&order=market_cap_desc"
                + "&per_page=" + perPage
                + "&page=" + coingeckoPage
                + "&sparkline=false"
                + "&price_change_percentage=1h,7d";

        try {
            List<CoinGeckoMarketItemDto> body = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        throw new ExternalApiException(
                                "CoinGecko API client error: " + res.getStatusCode() + " " + res.getStatusText());
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new ExternalApiException(
                                "CoinGecko API server error: " + res.getStatusCode() + " " + res.getStatusText());
                    })
                    .body(new ParameterizedTypeReference<>() {});

            if (body == null) {
                throw new ExternalApiException("CoinGecko API returned empty response");
            }
            return body;
        } catch (ExternalApiException e) {
            throw e;
        } catch (ResourceAccessException e) {
            throw new ExternalApiException("Failed to reach CoinGecko API. Check network.", e);
        } catch (Exception e) {
            throw new ExternalApiException("CoinGecko API error: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> fetchCoinDetail(String coinId) {
        String uri = "/coins/" + coinId
                + "?localization=true&tickers=false&market_data=true&community_data=false&developer_data=false&sparkline=false";
        try {
            Map<String, Object> body = restClient.get().uri(uri).retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        throw new ExternalApiException("CoinGecko detail error: " + res.getStatusCode());
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new ExternalApiException("CoinGecko detail server error: " + res.getStatusCode());
                    })
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            return body != null ? body : Map.of();
        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalApiException("CoinGecko detail error: " + e.getMessage(), e);
        }
    }

    public List<List<Number>> fetchOhlc(String coinId, Object days, String currency) {
        String cur = resolveCurrency(currency);
        String uri = "/coins/" + coinId + "/ohlc?vs_currency=" + cur + "&days=" + days;
        try {
            List<List<Number>> body = restClient.get().uri(uri).retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        throw new ExternalApiException("CoinGecko OHLC error: " + res.getStatusCode());
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new ExternalApiException("CoinGecko OHLC server error: " + res.getStatusCode());
                    })
                    .body(new ParameterizedTypeReference<>() {});
            return body != null ? body : List.of();
        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalApiException("CoinGecko OHLC error: " + e.getMessage(), e);
        }
    }

    public List<List<Number>> fetchOhlcRange(String coinId, String currency, long fromEpochSec, long toEpochSec) {
        String cur = resolveCurrency(currency);
        String uri = "/coins/" + coinId + "/ohlc/range?vs_currency=" + cur
                + "&from=" + fromEpochSec + "&to=" + toEpochSec;
        try {
            List<List<Number>> body = restClient.get().uri(uri).retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        throw new ExternalApiException("CoinGecko OHLC range error: " + res.getStatusCode());
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new ExternalApiException("CoinGecko OHLC range server error: " + res.getStatusCode());
                    })
                    .body(new ParameterizedTypeReference<>() {});
            return body != null ? body : List.of();
        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalApiException("CoinGecko OHLC range error: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> fetchMarketChartRange(String coinId, String currency, long fromEpochSec, long toEpochSec) {
        return fetchMarketChartRange(coinId, currency, fromEpochSec, toEpochSec, null);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> fetchMarketChartRange(String coinId, String currency, long fromEpochSec, long toEpochSec,
                                                     String interval) {
        String cur = resolveCurrency(currency);
        StringBuilder uri = new StringBuilder("/coins/")
                .append(coinId)
                .append("/market_chart/range?vs_currency=")
                .append(cur)
                .append("&from=")
                .append(fromEpochSec)
                .append("&to=")
                .append(toEpochSec);
        if (interval != null && !interval.isBlank()) {
            uri.append("&interval=").append(interval.trim().toLowerCase());
        }
        log.info("CoinGecko market_chart/range {} {} -> {} interval={}", coinId, fromEpochSec, toEpochSec, interval);
        try {
            Map<String, Object> body = restClient.get().uri(uri.toString()).retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        throw new ExternalApiException("CoinGecko chart range error: " + res.getStatusCode());
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new ExternalApiException("CoinGecko chart range server error: " + res.getStatusCode());
                    })
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            return body != null ? body : Map.of();
        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalApiException("CoinGecko chart range error: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> fetchMarketChart(String coinId, Object days, String currency, String interval) {
        String cur = resolveCurrency(currency);
        StringBuilder uri = new StringBuilder("/coins/")
                .append(coinId)
                .append("/market_chart?vs_currency=")
                .append(cur)
                .append("&days=")
                .append(days);
        if (interval != null && !interval.isBlank()) {
            uri.append("&interval=").append(interval.trim().toLowerCase());
        }
        try {
            Map<String, Object> body = restClient.get().uri(uri.toString()).retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        throw new ExternalApiException("CoinGecko chart error: " + res.getStatusCode());
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new ExternalApiException("CoinGecko chart server error: " + res.getStatusCode());
                    })
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            return body != null ? body : Map.of();
        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalApiException("CoinGecko chart error: " + e.getMessage(), e);
        }
    }

    private String resolveCurrency(String currency) {
        if (currency == null || currency.isBlank()) return "try";
        String lower = currency.trim().toLowerCase();
        return ALLOWED_CURRENCIES.contains(lower) ? lower : "try";
    }
}
