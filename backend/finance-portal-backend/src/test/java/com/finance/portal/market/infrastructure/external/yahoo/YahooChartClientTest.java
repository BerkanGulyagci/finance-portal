package com.finance.portal.market.infrastructure.external.yahoo;

import com.finance.portal.common.application.exception.ExternalApiException;
import com.finance.portal.common.application.logging.CentralIntegrationLogService;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.test.util.ReflectionTestUtils;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * {@link YahooChartClient} testleri — WireMock ile chart ucunun gerçek HTTP yolu stub'lanır.
 * Chart taban URL'i {@code @Value market.stocks.yahoo.base-url} alanından gelir; reflection ile
 * WireMock'a yönlendirilir. {@code fetchQuoteBatch}/{@code searchCryptoUsdSymbols} sabit (hardcoded)
 * Yahoo host'ları kullandığı için sadece null/empty erken-dönüş dalları (ağsız) test edilir.
 * Entegrasyon log servisi mock (publish no-op). Surefire'da koşsun diye {@code *Test} (WireMock hafif, Spring yok).
 */
class YahooChartClientTest {

    private static WireMockServer wm;
    private YahooChartClient client;

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
        client = new YahooChartClient(new RestTemplateBuilder(), mock(CentralIntegrationLogService.class));
        // Chart taban URL'ini WireMock'a yönlendir (FALLBACK_BASE_URL ve quote/search sabittir, etkilenmez)
        ReflectionTestUtils.setField(client, "yahooBaseUrl", wm.baseUrl());
    }

    // Geçerli, meta içeren chart cevabı (exchangeChart null/empty kontrollerini geçer)
    private static final String VALID_CHART_JSON =
            "{\"chart\":{\"result\":[{"
                    + "\"meta\":{\"symbol\":\"AAPL\",\"currency\":\"USD\",\"regularMarketPrice\":190.50},"
                    + "\"timestamp\":[1700000000],"
                    + "\"indicators\":{\"quote\":[{"
                    + "\"open\":[189.0],\"high\":[191.0],\"low\":[188.0],\"close\":[190.5],\"volume\":[1234]"
                    + "}]}"
                    + "}],\"error\":null}}";

    // ── fetchChartWithParams ────────────────────────────────────────────────

    @Test
    @DisplayName("fetchChartWithParams: 200 + geçerli chart → parse edilmiş DTO (meta dolu) döner")
    void fetchChartWithParams_ok() {
        wm.stubFor(get(urlPathEqualTo("/AAPL")).willReturn(okJson(VALID_CHART_JSON)));

        YahooChartResponseDto dto = client.fetchChartWithParams("AAPL", "1Y", "1d");

        assertThat(dto).isNotNull();
        assertThat(dto.getChart()).isNotNull();
        assertThat(dto.getChart().getResult()).hasSize(1);
        YahooChartResponseDto.Meta meta = dto.getChart().getResult().get(0).getMeta();
        assertThat(meta).isNotNull();
        assertThat(meta.getSymbol()).isEqualTo("AAPL");
        assertThat(meta.getCurrency()).isEqualTo("USD");
    }

    @Test
    @DisplayName("fetchChartWithParams: symbol boşsa → IllegalArgumentException (ağ çağrısı yok)")
    void fetchChartWithParams_blankSymbol() {
        assertThatThrownBy(() -> client.fetchChartWithParams("   ", "1d", "1d"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.fetchChartWithParams(null, "1d", "1d"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("fetchChartWithParams: 200 ama meta yok (boş result) → ExternalApiException")
    void fetchChartWithParams_emptyResult() {
        wm.stubFor(get(urlPathEqualTo("/AAPL"))
                .willReturn(okJson("{\"chart\":{\"result\":[],\"error\":null}}")));

        assertThatThrownBy(() -> client.fetchChartWithParams("AAPL", "1d", "1d"))
                .isInstanceOf(ExternalApiException.class);
    }

    @Test
    @DisplayName("fetchChartWithParams: 200 ama gövde null/chart yok → ExternalApiException (empty branch)")
    void fetchChartWithParams_nullChart() {
        wm.stubFor(get(urlPathEqualTo("/AAPL")).willReturn(okJson("{}")));

        assertThatThrownBy(() -> client.fetchChartWithParams("AAPL", "5d", "1d"))
                .isInstanceOf(ExternalApiException.class);
    }

    @Test
    @DisplayName("fetchChartWithParams: 404 client error (429 değil) → ExternalApiException")
    void fetchChartWithParams_clientError() {
        wm.stubFor(get(urlPathEqualTo("/AAPL")).willReturn(aResponse().withStatus(404)));

        assertThatThrownBy(() -> client.fetchChartWithParams("AAPL", "1d", "1d"))
                .isInstanceOf(ExternalApiException.class);
    }

    @Test
    @DisplayName("fetchChartWithParams: 500 server error → ExternalApiException")
    void fetchChartWithParams_serverError() {
        wm.stubFor(get(urlPathEqualTo("/AAPL")).willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> client.fetchChartWithParams("AAPL", "1d", "1d"))
                .isInstanceOf(ExternalApiException.class);
    }

    // ── fetchChart (1d/1m'e delege eder) ────────────────────────────────────

    @Test
    @DisplayName("fetchChart: 200 + geçerli chart → DTO döner (default 1d/1m)")
    void fetchChart_ok() {
        wm.stubFor(get(urlPathEqualTo("/MSFT")).willReturn(okJson(
                VALID_CHART_JSON.replace("AAPL", "MSFT"))));

        YahooChartResponseDto dto = client.fetchChart("MSFT");

        assertThat(dto).isNotNull();
        assertThat(dto.getChart().getResult().get(0).getMeta().getSymbol()).isEqualTo("MSFT");
    }

    @Test
    @DisplayName("fetchChart: 503 → ExternalApiException (server error sarmalanır)")
    void fetchChart_serverError() {
        wm.stubFor(get(urlPathEqualTo("/MSFT")).willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(() -> client.fetchChart("MSFT"))
                .isInstanceOf(ExternalApiException.class);
    }

    // ── fetchQuoteBatch / searchCryptoUsdSymbols: null/empty erken dönüş ─────

    @Test
    @DisplayName("fetchQuoteBatch: null/boş liste → boş liste (ağ çağrısı yok)")
    void fetchQuoteBatch_emptyInput() {
        assertThat(client.fetchQuoteBatch(null)).isEmpty();
        assertThat(client.fetchQuoteBatch(java.util.List.of())).isEmpty();
    }

    @Test
    @DisplayName("searchCryptoUsdSymbols: null/boş sorgu → boş liste (ağ çağrısı yok)")
    void searchCryptoUsdSymbols_emptyInput() {
        assertThat(client.searchCryptoUsdSymbols(null)).isEmpty();
        assertThat(client.searchCryptoUsdSymbols("   ")).isEmpty();
    }

    // ── static normalize yardımcıları (aynı paket → package-private erişim) ──

    @Test
    @DisplayName("normalizeYahooRange / normalizeYahooInterval: eşleme ve varsayılanlar")
    void normalizeHelpers() {
        assertThat(YahooChartClient.normalizeYahooRange("1Y")).isEqualTo("1y");
        assertThat(YahooChartClient.normalizeYahooRange("1W")).isEqualTo("5d");
        assertThat(YahooChartClient.normalizeYahooRange("MAX")).isEqualTo("max");
        assertThat(YahooChartClient.normalizeYahooRange(null)).isEqualTo("1mo");
        assertThat(YahooChartClient.normalizeYahooRange("  ")).isEqualTo("1mo");
        // bilinmeyen → lowercase passthrough
        assertThat(YahooChartClient.normalizeYahooRange("7D")).isEqualTo("7d");

        assertThat(YahooChartClient.normalizeYahooInterval("1D")).isEqualTo("1d");
        assertThat(YahooChartClient.normalizeYahooInterval(null)).isEqualTo("1d");
        assertThat(YahooChartClient.normalizeYahooInterval(" ")).isEqualTo("1d");
    }
}
