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
 * Ek {@link CoinGeckoClient} dal-kapsama testleri — {@code CoinGeckoClientTest}'in atladığı dallar.
 *
 * <p>Hedeflenen kaçırılmış dallar:
 * <ul>
 *   <li>fetchMarkets: {@code body == null} (boş gövde) → ExternalApiException;
 *       429 olmayan 4xx (429-kontrol if'inin false kolu); malformed JSON → PARSE_FAILED catch.</li>
 *   <li>fetchCoinDetail: 200 ama boş map / null gövde; 5xx server-error kolu; malformed JSON catch.</li>
 *   <li>fetchOhlc: 200 boş/null liste; 4xx client-error kolu; malformed JSON catch.</li>
 *   <li>fetchOhlcRange: 200 boş/null liste; 5xx server-error kolu; malformed JSON catch.</li>
 *   <li>fetchMarketChartRange: 200 boş/null map; 4xx client-error kolu; blank interval kolu; malformed catch.</li>
 *   <li>fetchMarketChart: 200 boş/null map; 5xx server-error kolu; blank/null interval kolları; malformed catch.</li>
 *   <li>resolveCurrency: null ve blank → "try" (varsayılan) kolları.</li>
 * </ul>
 * Entegrasyon log servisi mock (publish no-op). WireMock ile gerçek RestClient yolu.
 */
class CoinGeckoClientMoreTest {

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
    @DisplayName("fetchMarkets: 200 ama gövde yok (null) → ExternalApiException (empty response)")
    void fetchMarkets_nullBody() {
        // Boş 200 gövdesi → RestClient body() null döner → 'body == null' kolu.
        wm.stubFor(get(urlPathEqualTo("/coins/markets"))
                .willReturn(aResponse().withStatus(200)));
        assertThatThrownBy(() -> client.fetchMarkets(1, 10, "usd"))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("empty response");
    }

    @Test
    @DisplayName("fetchMarkets: 401 (429 olmayan 4xx) → ExternalApiException, 429-if'in false kolu")
    void fetchMarkets_clientError_not429() {
        wm.stubFor(get(urlPathEqualTo("/coins/markets")).willReturn(aResponse().withStatus(401)));
        assertThatThrownBy(() -> client.fetchMarkets(2, 25, "try"))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("client error");
    }

    @Test
    @DisplayName("fetchMarkets: bozuk JSON → catch(Exception) → PARSE_FAILED → ExternalApiException")
    void fetchMarkets_malformedJson() {
        wm.stubFor(get(urlPathEqualTo("/coins/markets"))
                .willReturn(okJson("{not-an-array")));
        assertThatThrownBy(() -> client.fetchMarkets(1, 10, "eur"))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("CoinGecko API error");
    }

    // ── /coins/{id} detail ──────────────────────────────────────────────────

    @Test
    @DisplayName("fetchCoinDetail: 200 ama boş map {} → boş map döner (sessiz hata loglanır)")
    void fetchCoinDetail_emptyMap() {
        wm.stubFor(get(urlPathEqualTo("/coins/ethereum")).willReturn(okJson("{}")));
        Map<String, Object> r = client.fetchCoinDetail("ethereum");
        assertThat(r).isEmpty();
    }

    @Test
    @DisplayName("fetchCoinDetail: 200 ama gövde yok (null) → Map.of() döner")
    void fetchCoinDetail_nullBody() {
        wm.stubFor(get(urlPathEqualTo("/coins/ethereum"))
                .willReturn(aResponse().withStatus(200)));
        assertThat(client.fetchCoinDetail("ethereum")).isEmpty();
    }

    @Test
    @DisplayName("fetchCoinDetail: 500 → ExternalApiException (server error kolu)")
    void fetchCoinDetail_serverError() {
        wm.stubFor(get(urlPathEqualTo("/coins/nope")).willReturn(aResponse().withStatus(500)));
        assertThatThrownBy(() -> client.fetchCoinDetail("nope")).isInstanceOf(ExternalApiException.class);
    }

    @Test
    @DisplayName("fetchCoinDetail: bozuk JSON → catch(Exception) → ExternalApiException")
    void fetchCoinDetail_malformedJson() {
        wm.stubFor(get(urlPathEqualTo("/coins/bitcoin")).willReturn(okJson("[1,2,3]")));
        // map beklenirken array → dönüşüm hatası
        assertThatThrownBy(() -> client.fetchCoinDetail("bitcoin"))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("detail error");
    }

    // ── /coins/{id}/ohlc ─────────────────────────────────────────────────────

    @Test
    @DisplayName("fetchOhlc: 200 ama boş liste [] → boş liste döner (empty body kolu)")
    void fetchOhlc_emptyList() {
        wm.stubFor(get(urlPathEqualTo("/coins/bitcoin/ohlc")).willReturn(okJson("[]")));
        assertThat(client.fetchOhlc("bitcoin", 30, "usd")).isEmpty();
    }

    @Test
    @DisplayName("fetchOhlc: 200 ama gövde yok (null) → boş liste döner")
    void fetchOhlc_nullBody() {
        wm.stubFor(get(urlPathEqualTo("/coins/bitcoin/ohlc")).willReturn(aResponse().withStatus(200)));
        assertThat(client.fetchOhlc("bitcoin", 30, "usd")).isEmpty();
    }

    @Test
    @DisplayName("fetchOhlc: 404 → ExternalApiException (4xx client-error kolu)")
    void fetchOhlc_clientError() {
        wm.stubFor(get(urlPathEqualTo("/coins/bitcoin/ohlc")).willReturn(aResponse().withStatus(404)));
        assertThatThrownBy(() -> client.fetchOhlc("bitcoin", 1, "usd"))
                .isInstanceOf(ExternalApiException.class);
    }

    @Test
    @DisplayName("fetchOhlc: bozuk JSON → catch(Exception) → ExternalApiException")
    void fetchOhlc_malformedJson() {
        wm.stubFor(get(urlPathEqualTo("/coins/bitcoin/ohlc")).willReturn(okJson("{broken")));
        assertThatThrownBy(() -> client.fetchOhlc("bitcoin", 7, "usd"))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("OHLC error");
    }

    // ── /coins/{id}/ohlc/range ──────────────────────────────────────────────

    @Test
    @DisplayName("fetchOhlcRange: 200 ama boş liste [] → boş liste döner")
    void fetchOhlcRange_emptyList() {
        wm.stubFor(get(urlPathEqualTo("/coins/bitcoin/ohlc/range")).willReturn(okJson("[]")));
        assertThat(client.fetchOhlcRange("bitcoin", "usd", 1L, 2L)).isEmpty();
    }

    @Test
    @DisplayName("fetchOhlcRange: 500 → ExternalApiException (5xx server-error kolu)")
    void fetchOhlcRange_serverError() {
        wm.stubFor(get(urlPathEqualTo("/coins/bitcoin/ohlc/range")).willReturn(aResponse().withStatus(500)));
        assertThatThrownBy(() -> client.fetchOhlcRange("bitcoin", "try", 1L, 2L))
                .isInstanceOf(ExternalApiException.class);
    }

    @Test
    @DisplayName("fetchOhlcRange: bozuk JSON → catch(Exception) → ExternalApiException")
    void fetchOhlcRange_malformedJson() {
        wm.stubFor(get(urlPathEqualTo("/coins/bitcoin/ohlc/range")).willReturn(okJson("{oops")));
        assertThatThrownBy(() -> client.fetchOhlcRange("bitcoin", "eur", 1L, 2L))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("OHLC range error");
    }

    // ── /coins/{id}/market_chart/range ──────────────────────────────────────

    @Test
    @DisplayName("fetchMarketChartRange: 200 ama boş map {} → boş map döner (interval blank kolu)")
    void fetchMarketChartRange_emptyMap_blankInterval() {
        wm.stubFor(get(urlPathEqualTo("/coins/bitcoin/market_chart/range")).willReturn(okJson("{}")));
        // blank interval "   " → if(false) kolu; sonuç boş map
        assertThat(client.fetchMarketChartRange("bitcoin", "usd", 1L, 2L, "   ")).isEmpty();
    }

    @Test
    @DisplayName("fetchMarketChartRange: 200 ama gövde yok (null) → boş map döner")
    void fetchMarketChartRange_nullBody() {
        wm.stubFor(get(urlPathEqualTo("/coins/bitcoin/market_chart/range"))
                .willReturn(aResponse().withStatus(200)));
        assertThat(client.fetchMarketChartRange("bitcoin", "usd", 1L, 2L)).isEmpty();
    }

    @Test
    @DisplayName("fetchMarketChartRange: 404 → ExternalApiException (4xx client-error kolu)")
    void fetchMarketChartRange_clientError() {
        wm.stubFor(get(urlPathEqualTo("/coins/bitcoin/market_chart/range")).willReturn(aResponse().withStatus(404)));
        assertThatThrownBy(() -> client.fetchMarketChartRange("bitcoin", "try", 1L, 2L, "daily"))
                .isInstanceOf(ExternalApiException.class);
    }

    @Test
    @DisplayName("fetchMarketChartRange: bozuk JSON → catch(Exception) → ExternalApiException")
    void fetchMarketChartRange_malformedJson() {
        wm.stubFor(get(urlPathEqualTo("/coins/bitcoin/market_chart/range")).willReturn(okJson("[1,2]")));
        assertThatThrownBy(() -> client.fetchMarketChartRange("bitcoin", "usd", 1L, 2L))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("chart range error");
    }

    // ── /coins/{id}/market_chart ────────────────────────────────────────────

    @Test
    @DisplayName("fetchMarketChart: 200 ama boş map {} → boş map döner (null interval kolu)")
    void fetchMarketChart_emptyMap_nullInterval() {
        wm.stubFor(get(urlPathEqualTo("/coins/bitcoin/market_chart")).willReturn(okJson("{}")));
        assertThat(client.fetchMarketChart("bitcoin", 90, "eur", null)).isEmpty();
    }

    @Test
    @DisplayName("fetchMarketChart: 200 ama gövde yok (null) → boş map döner (blank interval kolu)")
    void fetchMarketChart_nullBody_blankInterval() {
        wm.stubFor(get(urlPathEqualTo("/coins/bitcoin/market_chart"))
                .willReturn(aResponse().withStatus(200)));
        // blank interval "" → if(false) kolu
        assertThat(client.fetchMarketChart("bitcoin", 1, "try", "")).isEmpty();
    }

    @Test
    @DisplayName("fetchMarketChart: 500 → ExternalApiException (5xx server-error kolu)")
    void fetchMarketChart_serverError() {
        wm.stubFor(get(urlPathEqualTo("/coins/bitcoin/market_chart")).willReturn(aResponse().withStatus(500)));
        assertThatThrownBy(() -> client.fetchMarketChart("bitcoin", 1, "usd", "daily"))
                .isInstanceOf(ExternalApiException.class);
    }

    @Test
    @DisplayName("fetchMarketChart: bozuk JSON → catch(Exception) → ExternalApiException")
    void fetchMarketChart_malformedJson() {
        wm.stubFor(get(urlPathEqualTo("/coins/bitcoin/market_chart")).willReturn(okJson("[9]")));
        assertThatThrownBy(() -> client.fetchMarketChart("bitcoin", 30, "usd", "hourly"))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("chart error");
    }

    // ── resolveCurrency varsayılan kolları (null / blank) ────────────────────

    @Test
    @DisplayName("resolveCurrency: currency null → 'try' (varsayılan); başarı yolu çalışır")
    void resolveCurrency_nullCurrency_defaultsTry() {
        wm.stubFor(get(urlPathEqualTo("/coins/bitcoin/ohlc"))
                .willReturn(okJson("[[1700000000000,1,2,3,4]]")));
        // currency == null → "try" kolu
        List<List<Number>> r = client.fetchOhlc("bitcoin", 30, null);
        assertThat(r).hasSize(1);
    }

    @Test
    @DisplayName("resolveCurrency: currency blank '  ' → 'try' (varsayılan)")
    void resolveCurrency_blankCurrency_defaultsTry() {
        wm.stubFor(get(urlPathEqualTo("/coins/bitcoin/ohlc"))
                .willReturn(okJson("[[1700000000000,1,2,3,4]]")));
        // currency blank → "try" kolu
        List<List<Number>> r = client.fetchOhlc("bitcoin", 30, "   ");
        assertThat(r).hasSize(1);
    }
}
