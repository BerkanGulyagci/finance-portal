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

    @Value("${coingecko.vs-currency:try}")
    private String vsCurrency;

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
     * @return list of market items in TRY (or configured vs_currency)
     */
    public List<CoinGeckoMarketItemDto> fetchMarkets(int coingeckoPage, int perPage) {
        String currency = vsCurrency != null && !vsCurrency.isBlank() ? vsCurrency.trim().toLowerCase() : "try";
        log.info("Calling CoinGecko /coins/markets vs_currency={} page={} per_page={}", currency, coingeckoPage, perPage);

        String uri = PATH_MARKETS + "?vs_currency=" + currency
                + "&order=market_cap_desc"
                + "&per_page=" + perPage
                + "&page=" + coingeckoPage
                + "&sparkline=false"
                + "&price_change_percentage=24h";

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
}
