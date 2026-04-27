package com.finance.portal.market.crypto.infrastructure;

import com.finance.portal.common.infrastructure.exception.ExternalApiException;
import com.finance.portal.market.crypto.infrastructure.dto.CoinGeckoMarketItemDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.Duration;
import java.util.List;

@Component
public class CoinGeckoClient {

    private static final Logger log = LoggerFactory.getLogger(CoinGeckoClient.class);
    private static final String HEADER_API_KEY = "x-cg-demo-api-key";
    private static final String PATH_MARKETS = "/coins/markets";

    private final RestClient restClient;

    private static final java.util.Set<String> ALLOWED_CURRENCIES =
            java.util.Set.of("try", "usd", "eur");

    public CoinGeckoClient(
            @Value("${coingecko.base-url}") String baseUrl,
            @Value("${coingecko.api-key}") String apiKey
    ) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(3));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl != null && !baseUrl.isBlank() ? baseUrl.trim() : "https://api.coingecko.com/api/v3")
                .defaultHeader(HEADER_API_KEY, apiKey != null ? apiKey : "")
                .requestFactory(factory)
                .build();
    }

    /**
     * Fetches crypto market list from CoinGecko (Demo API).
     *
     * @param coingeckoPage 1-based page number for CoinGecko
     * @param perPage       number of items per page (1–250)
     * @param currency      vs_currency: try, usd or eur
     * @return list of market items in requested currency
     */
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

    /**
     * Fetches detailed coin info: description, links, ATH/ATL, supply, categories.
     */
    @SuppressWarnings("unchecked")
    public java.util.Map<String, Object> fetchCoinDetail(String coinId) {
        String uri = "/coins/" + coinId
                + "?localization=true&tickers=false&market_data=true&community_data=false&developer_data=false&sparkline=false";
        try {
            java.util.Map<String, Object> body = restClient.get().uri(uri).retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        throw new ExternalApiException("CoinGecko detail error: " + res.getStatusCode());
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new ExternalApiException("CoinGecko detail server error: " + res.getStatusCode());
                    })
                    .body(new ParameterizedTypeReference<java.util.Map<String, Object>>() {});
            return body != null ? body : java.util.Map.of();
        } catch (ExternalApiException e) { throw e; }
        catch (Exception e) { throw new ExternalApiException("CoinGecko detail error: " + e.getMessage(), e); }
    }

    /**
     * Fetches OHLC (candlestick) data for a coin.
     * days: 1, 7, 14, 30, 90, 180, 365
     */
    public List<List<Number>> fetchOhlc(String coinId, int days, String currency) {
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
        } catch (ExternalApiException e) { throw e; }
        catch (Exception e) { throw new ExternalApiException("CoinGecko OHLC error: " + e.getMessage(), e); }
    }

    /**
     * Fetches market chart (price history) for a coin.
     * days: 1, 7, 14, 30, 90, 180, 365
     */
    @SuppressWarnings("unchecked")
    public java.util.Map<String, Object> fetchMarketChart(String coinId, int days, String currency) {
        String cur = resolveCurrency(currency);
        String uri = "/coins/" + coinId + "/market_chart?vs_currency=" + cur + "&days=" + days;
        try {
            java.util.Map<String, Object> body = restClient.get().uri(uri).retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        throw new ExternalApiException("CoinGecko chart error: " + res.getStatusCode());
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new ExternalApiException("CoinGecko chart server error: " + res.getStatusCode());
                    })
                    .body(new ParameterizedTypeReference<java.util.Map<String, Object>>() {});
            return body != null ? body : java.util.Map.of();
        } catch (ExternalApiException e) { throw e; }
        catch (Exception e) { throw new ExternalApiException("CoinGecko chart error: " + e.getMessage(), e); }
    }

    private String resolveCurrency(String currency) {
        if (currency == null || currency.isBlank()) return "try";
        String lower = currency.trim().toLowerCase();
        return ALLOWED_CURRENCIES.contains(lower) ? lower : "try";
    }
}
