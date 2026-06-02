package com.finance.portal.market.infrastructure.external.crypto;

import com.finance.portal.common.application.exception.ExternalApiException;
import com.finance.portal.common.application.logging.CentralIntegrationLogService;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * {@link CoinGeckoClient} testleri — WireMock ile gerçek HTTP yolu (RestClient) stub'lanır.
 * Tüm public uçlar (markets/detail/ohlc/ohlc-range/market_chart[/range]) için happy-path + hata dalları.
 * Entegrasyon log servisi mock (publish no-op). Surefire'da koşsun diye {@code *Test} (WireMock hafif, Spring yok).
 */
class CoinGeckoClientTest {

    private static WireMockServer wm;
    private CoinGeckoClient client;

    @BeforeAll
    static void startWireMock() {
        wm = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wm.start();
    }

    @AfterAll
    static void stopWireMock() {
        if (wm != null) wm.stop();
    }

    @BeforeEach
    void setUp() {
        wm.resetAll();
        client = new CoinGeckoClient(wm.baseUrl(), "test-key", mock(CentralIntegrationLogService.class));
    }

    // ── /coins/markets ──────────────────────────────────────────────────────

    @Test
    @DisplayName("fetchMarkets: 200 → parse edilen liste döner")
    void fetchMarkets_ok() {
        wm.stubFor(get(urlPathEqualTo("/coins/markets")).willReturn(okJson("[{},{}]")));
        List<?> r = client.fetchMarkets(1, 2, "usd");
        assertThat(r).hasSize(2);
    }

    @Test
    @DisplayName("fetchMarkets: 200 ama boş liste → boş döner (sessiz hata loglanır)")
    void fetchMarkets_emptyList() {
        wm.stubFor(get(urlPathEqualTo("/coins/markets")).willReturn(okJson("[]")));
        assertThat(client.fetchMarkets(1, 50, "eur")).isEmpty();
    }

    @Test
    @DisplayName("fetchMarkets: 429 rate-limit → ExternalApiException")
    void fetchMarkets_rateLimited() {
        wm.stubFor(get(urlPathEqualTo("/coins/markets")).willReturn(aResponse().withStatus(429)));
        assertThatThrownBy(() -> client.fetchMarkets(1, 10, "try"))
                .isInstanceOf(ExternalApiException.class);
    }

    @Test
    @DisplayName("fetchMarkets: 500 → ExternalApiException (server error)")
    void fetchMarkets_serverError() {
        wm.stubFor(get(urlPathEqualTo("/coins/markets")).willReturn(aResponse().withStatus(500)));
        assertThatThrownBy(() -> client.fetchMarkets(1, 10, "invalid-cur-defaults-try"))
                .isInstanceOf(ExternalApiException.class);
    }

    // ── /coins/{id} detail ──────────────────────────────────────────────────

    @Test
    @DisplayName("fetchCoinDetail: 200 → map döner")
    void fetchCoinDetail_ok() {
        wm.stubFor(get(urlPathEqualTo("/coins/bitcoin")).willReturn(okJson("{\"id\":\"bitcoin\",\"symbol\":\"btc\"}")));
        Map<String, Object> r = client.fetchCoinDetail("bitcoin");
        assertThat(r).containsEntry("id", "bitcoin");
    }

    @Test
    @DisplayName("fetchCoinDetail: 404 → ExternalApiException")
    void fetchCoinDetail_notFound() {
        wm.stubFor(get(urlPathEqualTo("/coins/nope")).willReturn(aResponse().withStatus(404)));
        assertThatThrownBy(() -> client.fetchCoinDetail("nope")).isInstanceOf(ExternalApiException.class);
    }

    // ── /coins/{id}/ohlc + /ohlc/range ──────────────────────────────────────

    @Test
    @DisplayName("fetchOhlc: 200 → [[..]] liste döner")
    void fetchOhlc_ok() {
        wm.stubFor(get(urlPathEqualTo("/coins/bitcoin/ohlc"))
                .willReturn(okJson("[[1700000000000,1,2,3,4]]")));
        List<List<Number>> r = client.fetchOhlc("bitcoin", 30, "usd");
        assertThat(r).hasSize(1);
    }

    @Test
    @DisplayName("fetchOhlc: 500 → ExternalApiException")
    void fetchOhlc_serverError() {
        wm.stubFor(get(urlPathEqualTo("/coins/bitcoin/ohlc")).willReturn(aResponse().withStatus(503)));
        assertThatThrownBy(() -> client.fetchOhlc("bitcoin", "max", "try"))
                .isInstanceOf(ExternalApiException.class);
    }

    @Test
    @DisplayName("fetchOhlcRange: 200 → liste döner")
    void fetchOhlcRange_ok() {
        wm.stubFor(get(urlPathEqualTo("/coins/bitcoin/ohlc/range"))
                .willReturn(okJson("[[1700000000000,1,2,3,4],[1700086400000,2,3,4,5]]")));
        List<List<Number>> r = client.fetchOhlcRange("bitcoin", "usd", 1_700_000_000L, 1_700_100_000L);
        assertThat(r).hasSize(2);
    }

    @Test
    @DisplayName("fetchOhlcRange: 400 → ExternalApiException")
    void fetchOhlcRange_clientError() {
        wm.stubFor(get(urlPathEqualTo("/coins/bitcoin/ohlc/range")).willReturn(aResponse().withStatus(400)));
        assertThatThrownBy(() -> client.fetchOhlcRange("bitcoin", "try", 1L, 2L))
                .isInstanceOf(ExternalApiException.class);
    }

    // ── /coins/{id}/market_chart + /market_chart/range ──────────────────────

    @Test
    @DisplayName("fetchMarketChartRange: 200 (interval'siz ve interval'li) → map döner")
    void fetchMarketChartRange_ok() {
        wm.stubFor(get(urlPathEqualTo("/coins/bitcoin/market_chart/range"))
                .willReturn(okJson("{\"prices\":[[1,2]]}")));
        assertThat(client.fetchMarketChartRange("bitcoin", "usd", 1L, 2L)).containsKey("prices");
        assertThat(client.fetchMarketChartRange("bitcoin", "usd", 1L, 2L, "daily")).containsKey("prices");
    }

    @Test
    @DisplayName("fetchMarketChartRange: 500 → ExternalApiException")
    void fetchMarketChartRange_serverError() {
        wm.stubFor(get(urlPathEqualTo("/coins/bitcoin/market_chart/range")).willReturn(aResponse().withStatus(502)));
        assertThatThrownBy(() -> client.fetchMarketChartRange("bitcoin", "try", 1L, 2L, "hourly"))
                .isInstanceOf(ExternalApiException.class);
    }

    @Test
    @DisplayName("fetchMarketChart: 200 → map döner")
    void fetchMarketChart_ok() {
        wm.stubFor(get(urlPathEqualTo("/coins/bitcoin/market_chart"))
                .willReturn(okJson("{\"prices\":[[1,2]],\"total_volumes\":[]}")));
        assertThat(client.fetchMarketChart("bitcoin", 90, "eur", "daily")).containsKey("prices");
    }

    @Test
    @DisplayName("fetchMarketChart: 404 → ExternalApiException")
    void fetchMarketChart_notFound() {
        wm.stubFor(get(urlPathEqualTo("/coins/bitcoin/market_chart")).willReturn(aResponse().withStatus(404)));
        assertThatThrownBy(() -> client.fetchMarketChart("bitcoin", 1, "try", null))
                .isInstanceOf(ExternalApiException.class);
    }
}
