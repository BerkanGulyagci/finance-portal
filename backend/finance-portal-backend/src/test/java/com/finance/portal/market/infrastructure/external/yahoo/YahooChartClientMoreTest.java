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
 * {@link YahooChartClient} için EK dal-kapsamı testleri (branch coverage). {@link YahooChartClientTest}
 * çoktan kapsanan dalları tekrar etmez; sadece KAÇIRILAN dalları hedefler:
 *   - {@code exchangeChart} boş-cevap OR zincirinin ara koşulları (chart var/result null; result var/meta null),
 *   - 200 ama ayrıştırılamayan gövde → {@code RestClientException} dalı,
 *   - {@code normalizeYahooRange} switch'inin kalan case'leri (1D/5D/1M/3M/6M/2Y/5Y/YTD).
 * Chart taban URL'i {@code @Value market.stocks.yahoo.base-url} alanından gelir; reflection ile WireMock'a
 * yönlendirilir. 429 fallback dalı SABİT (hardcoded) query2 host'una gittiği için ağsız test edilemez,
 * o yüzden burada denenmez. Entegrasyon log servisi mock (publish no-op). Surefire'da koşsun diye {@code *Test}.
 */
class YahooChartClientMoreTest {

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
        ReflectionTestUtils.setField(client, "yahooBaseUrl", wm.baseUrl());
    }

    // ── exchangeChart boş-cevap OR zinciri: ara koşullar ────────────────────

    @Test
    @DisplayName("fetchChartWithParams: chart var ama result alanı null → ExternalApiException (getResult()==null dalı)")
    void fetchChartWithParams_chartPresentResultNull() {
        // chart != null geçer, ama getResult() == null dalı tetiklenir
        wm.stubFor(get(urlPathEqualTo("/AAPL"))
                .willReturn(okJson("{\"chart\":{\"result\":null,\"error\":null}}")));

        assertThatThrownBy(() -> client.fetchChartWithParams("AAPL", "1d", "1d"))
                .isInstanceOf(ExternalApiException.class);
    }

    @Test
    @DisplayName("fetchChartWithParams: result dolu ama meta yok → ExternalApiException (get(0).getMeta()==null dalı)")
    void fetchChartWithParams_resultPresentMetaNull() {
        // result boş DEĞİL (bir eleman var), ama eleman meta içermiyor → son OR koşulu tetiklenir
        wm.stubFor(get(urlPathEqualTo("/AAPL"))
                .willReturn(okJson("{\"chart\":{\"result\":[{\"timestamp\":[1700000000]}],\"error\":null}}")));

        assertThatThrownBy(() -> client.fetchChartWithParams("AAPL", "1d", "1d"))
                .isInstanceOf(ExternalApiException.class);
    }

    // ── RestClientException dalı (server/client/resource değil) ──────────────

    @Test
    @DisplayName("fetchChartWithParams: 200 ama ayrıştırılamayan gövde → ExternalApiException (RestClientException dalı)")
    void fetchChartWithParams_unparseableBody() {
        // 200 + bozuk JSON gövdesi → mesaj dönüştürücü RestClientException atar (4xx/5xx/ağ değil)
        wm.stubFor(get(urlPathEqualTo("/AAPL"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{ this-is-not-valid-json ")));

        assertThatThrownBy(() -> client.fetchChartWithParams("AAPL", "1d", "1d"))
                .isInstanceOf(ExternalApiException.class);
    }

    // ── normalizeYahooRange: kalan switch case'leri ─────────────────────────

    @Test
    @DisplayName("normalizeYahooRange: önceki testte kapsanmayan tüm switch case'leri")
    void normalizeYahooRange_remainingCases() {
        assertThat(YahooChartClient.normalizeYahooRange("1D")).isEqualTo("1d");
        assertThat(YahooChartClient.normalizeYahooRange("5D")).isEqualTo("5d");
        assertThat(YahooChartClient.normalizeYahooRange("1M")).isEqualTo("1mo");
        assertThat(YahooChartClient.normalizeYahooRange("3M")).isEqualTo("3mo");
        assertThat(YahooChartClient.normalizeYahooRange("6M")).isEqualTo("6mo");
        assertThat(YahooChartClient.normalizeYahooRange("2Y")).isEqualTo("2y");
        assertThat(YahooChartClient.normalizeYahooRange("5Y")).isEqualTo("5y");
        assertThat(YahooChartClient.normalizeYahooRange("YTD")).isEqualTo("ytd");
        // küçük harf girdi de aynı case'lere düşer (trim().toUpperCase normalize eder)
        assertThat(YahooChartClient.normalizeYahooRange("6m")).isEqualTo("6mo");
    }

    @Test
    @DisplayName("normalizeYahooInterval: boşluk içeren büyük harf → trim + lowercase")
    void normalizeYahooInterval_trimAndLower() {
        assertThat(YahooChartClient.normalizeYahooInterval("  1WK ")).isEqualTo("1wk");
        assertThat(YahooChartClient.normalizeYahooInterval("1MO")).isEqualTo("1mo");
    }
}
