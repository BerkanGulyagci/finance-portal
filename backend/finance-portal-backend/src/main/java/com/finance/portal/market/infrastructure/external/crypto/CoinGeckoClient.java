package com.finance.portal.market.infrastructure.external.crypto;

import com.finance.portal.common.application.exception.ExternalApiException;
import com.finance.portal.common.application.logging.CentralIntegrationLogService;
import com.finance.portal.common.application.logging.IntegrationLogSupport;
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
    private final CentralIntegrationLogService integrationLog;

    public CoinGeckoClient(
            @Value("${coingecko.base-url}") String baseUrl,
            @Value("${coingecko.api-key}") String apiKey,
            CentralIntegrationLogService integrationLog
    ) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(45));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl != null && !baseUrl.isBlank() ? baseUrl.trim() : "https://api.coingecko.com/api/v3")
                .defaultHeader(HEADER_API_KEY, apiKey != null ? apiKey : "")
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
                IntegrationLogSupport.PROVIDER_COINGECKO,
                operation,
                httpStatus,
                null,
                null,
                Boolean.TRUE,
                metadata,
                CoinGeckoClient.class.getName()
        );
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
                        if (res.getStatusCode().value() == 429) {
                            logIntegrationFailure(
                                    IntegrationLogSupport.EVENT_EXTERNAL_API_RATE_LIMITED,
                                    "CoinGecko markets: rate limited (HTTP 429)",
                                    "fetchMarkets",
                                    String.valueOf(res.getStatusCode().value()),
                                    Map.of("currency", cur, "page", coingeckoPage, "perPage", perPage));
                        }
                        throw new ExternalApiException(
                                "CoinGecko API client error: " + res.getStatusCode() + " " + res.getStatusText());
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new ExternalApiException(
                                "CoinGecko API server error: " + res.getStatusCode() + " " + res.getStatusText());
                    })
                    .body(new ParameterizedTypeReference<>() {});

            if (body == null) {
                logIntegrationFailure(
                        IntegrationLogSupport.EVENT_EXTERNAL_API_EMPTY_RESPONSE,
                        "CoinGecko markets: empty response body",
                        "fetchMarkets",
                        null,
                        Map.of("currency", cur, "page", coingeckoPage, "perPage", perPage));
                throw new ExternalApiException("CoinGecko API returned empty response");
            }

            // 200 döndü ama parse sonrası 0 kayıt —
            // muhtemelen JSON yapısı değişti (sessiz hata).
            if (body.isEmpty()) {
                logIntegrationFailure(
                        IntegrationLogSupport.EVENT_EXTERNAL_API_EMPTY_RESPONSE,
                        "CoinGecko markets: 0 items parsed from response (JSON structure may have changed)",
                        "fetchMarkets",
                        null,
                        Map.of("currency", cur, "page", coingeckoPage, "perPage", perPage));
            }
            return body;
        } catch (ExternalApiException e) {
            throw e;
        } catch (ResourceAccessException e) {
            throw new ExternalApiException("Failed to reach CoinGecko API. Check network.", e);
        } catch (Exception e) {
            logIntegrationFailure(
                    IntegrationLogSupport.EVENT_EXTERNAL_API_PARSE_FAILED,
                    "CoinGecko markets fetch/parse failed: " + e.getMessage() + " (JSON structure may have changed)",
                    "fetchMarkets",
                    null,
                    Map.of("currency", cur, "page", coingeckoPage, "perPage", perPage,
                            "exceptionClass", e.getClass().getSimpleName()));
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
            if (body == null || body.isEmpty()) {
                logIntegrationFailure(
                        IntegrationLogSupport.EVENT_EXTERNAL_API_EMPTY_RESPONSE,
                        "CoinGecko detail: empty/null response (JSON structure may have changed)",
                        "fetchCoinDetail",
                        null,
                        Map.of("coinId", coinId, "bodyNull", body == null));
            }
            return body != null ? body : Map.of();
        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            logIntegrationFailure(
                    IntegrationLogSupport.EVENT_EXTERNAL_API_PARSE_FAILED,
                    "CoinGecko detail fetch/parse failed: " + e.getMessage() + " (JSON structure may have changed)",
                    "fetchCoinDetail",
                    null,
                    Map.of("coinId", coinId, "exceptionClass", e.getClass().getSimpleName()));
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
            if (body == null || body.isEmpty()) {
                logIntegrationFailure(
                        IntegrationLogSupport.EVENT_EXTERNAL_API_EMPTY_RESPONSE,
                        "CoinGecko OHLC: empty/null response (JSON structure may have changed)",
                        "fetchOhlc",
                        null,
                        Map.of("coinId", coinId, "currency", cur, "days", String.valueOf(days),
                                "bodyNull", body == null));
            }
            return body != null ? body : List.of();
        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            logIntegrationFailure(
                    IntegrationLogSupport.EVENT_EXTERNAL_API_PARSE_FAILED,
                    "CoinGecko OHLC fetch/parse failed: " + e.getMessage() + " (JSON structure may have changed)",
                    "fetchOhlc",
                    null,
                    Map.of("coinId", coinId, "currency", cur, "exceptionClass", e.getClass().getSimpleName()));
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
            if (body == null || body.isEmpty()) {
                logIntegrationFailure(
                        IntegrationLogSupport.EVENT_EXTERNAL_API_EMPTY_RESPONSE,
                        "CoinGecko OHLC range: empty/null response (JSON structure may have changed)",
                        "fetchOhlcRange",
                        null,
                        Map.of("coinId", coinId, "currency", cur,
                                "from", fromEpochSec, "to", toEpochSec, "bodyNull", body == null));
            }
            return body != null ? body : List.of();
        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            logIntegrationFailure(
                    IntegrationLogSupport.EVENT_EXTERNAL_API_PARSE_FAILED,
                    "CoinGecko OHLC range fetch/parse failed: " + e.getMessage() + " (JSON structure may have changed)",
                    "fetchOhlcRange",
                    null,
                    Map.of("coinId", coinId, "currency", cur, "exceptionClass", e.getClass().getSimpleName()));
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
            if (body == null || body.isEmpty()) {
                logIntegrationFailure(
                        IntegrationLogSupport.EVENT_EXTERNAL_API_EMPTY_RESPONSE,
                        "CoinGecko chart range: empty/null response (JSON structure may have changed)",
                        "fetchMarketChartRange",
                        null,
                        Map.of("coinId", coinId, "currency", cur,
                                "from", fromEpochSec, "to", toEpochSec, "bodyNull", body == null));
            }
            return body != null ? body : Map.of();
        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            logIntegrationFailure(
                    IntegrationLogSupport.EVENT_EXTERNAL_API_PARSE_FAILED,
                    "CoinGecko chart range fetch/parse failed: " + e.getMessage() + " (JSON structure may have changed)",
                    "fetchMarketChartRange",
                    null,
                    Map.of("coinId", coinId, "currency", cur, "exceptionClass", e.getClass().getSimpleName()));
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
            if (body == null || body.isEmpty()) {
                logIntegrationFailure(
                        IntegrationLogSupport.EVENT_EXTERNAL_API_EMPTY_RESPONSE,
                        "CoinGecko chart: empty/null response (JSON structure may have changed)",
                        "fetchMarketChart",
                        null,
                        Map.of("coinId", coinId, "currency", cur,
                                "days", String.valueOf(days), "bodyNull", body == null));
            }
            return body != null ? body : Map.of();
        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            logIntegrationFailure(
                    IntegrationLogSupport.EVENT_EXTERNAL_API_PARSE_FAILED,
                    "CoinGecko chart fetch/parse failed: " + e.getMessage() + " (JSON structure may have changed)",
                    "fetchMarketChart",
                    null,
                    Map.of("coinId", coinId, "currency", cur, "exceptionClass", e.getClass().getSimpleName()));
            throw new ExternalApiException("CoinGecko chart error: " + e.getMessage(), e);
        }
    }

    private String resolveCurrency(String currency) {
        if (currency == null || currency.isBlank()) return "try";
        String lower = currency.trim().toLowerCase();
        return ALLOWED_CURRENCIES.contains(lower) ? lower : "try";
    }
}
