package com.finance.portal.market.infrastructure.external.fx;

import com.finance.portal.common.application.exception.ExternalApiException;
import com.finance.portal.common.application.logging.CentralIntegrationLogService;
import com.finance.portal.market.infrastructure.external.fx.dto.OpenErApiResponseDto;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * {@link OpenFxClient} testleri — WireMock ile Open ER API'nin gerçek HTTP yolu (RestTemplate)
 * stub'lanır. Tek public uç {@code fetchLatestRates(base)} için happy-path (JSON parse) + tüm
 * hata/savunma dalları: base null/blank → "USD" default, boş gövde, result null / non-success,
 * rates null / boş (sessiz hata, exception YOK), HTTP 429 rate-limit, 429-dışı client error,
 * 500 server error, ağ erişim hatası (ResourceAccessException), bozuk JSON parse hatası ve
 * {@code buildUrl} trailing-slash / null-baseUrl dalları.
 * Entegrasyon log servisi mock (publish no-op). Base URL @Value alanı olduğu için
 * {@link ReflectionTestUtils} ile WireMock URL'ine set edilir. Surefire'da koşsun diye {@code *Test}.
 */
class OpenFxClientTest {

    private static final String PATH = "/USD";

    private static WireMockServer wm;
    private OpenFxClient client;

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
        client = new OpenFxClient(new RestTemplate(), mock(CentralIntegrationLogService.class));
        // buildUrl: baseUrl trailing-slash YOK dalı + URL'i WireMock'a yönlendir.
        ReflectionTestUtils.setField(client, "openFxBaseUrl", wm.baseUrl());
    }

    private static final String VALID_JSON =
            "{\"result\":\"success\","
                    + "\"base_code\":\"USD\","
                    + "\"time_last_update_utc\":\"Tue, 03 Jun 2026 00:00:00 +0000\","
                    + "\"rates\":{\"USD\":1.0,\"TRY\":33.25,\"EUR\":0.92}}";

    // ── happy path ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("fetchLatestRates: geçerli JSON (result=success + rates dolu) → DTO parse edilir")
    void fetchLatestRates_ok() {
        wm.stubFor(get(urlPathEqualTo(PATH)).willReturn(okJson(VALID_JSON)));

        OpenErApiResponseDto dto = client.fetchLatestRates("usd");

        assertThat(dto).isNotNull();
        assertThat(dto.getResult()).isEqualTo("success");
        assertThat(dto.getBaseCode()).isEqualTo("USD");
        assertThat(dto.getTimeLastUpdateUtc()).isNotBlank();
        assertThat(dto.getRates()).containsEntry("TRY", 33.25);
        assertThat(dto.getRates()).hasSize(3);
    }

    @Test
    @DisplayName("fetchLatestRates: base null → 'USD' default (URL /USD'e gider, parse başarılı)")
    void fetchLatestRates_nullBaseDefaultsToUsd() {
        wm.stubFor(get(urlPathEqualTo(PATH)).willReturn(okJson(VALID_JSON)));

        OpenErApiResponseDto dto = client.fetchLatestRates(null);

        assertThat(dto).isNotNull();
        assertThat(dto.getResult()).isEqualTo("success");
    }

    @Test
    @DisplayName("fetchLatestRates: base boşluk → trim + 'USD' default (URL /USD)")
    void fetchLatestRates_blankBaseDefaultsToUsd() {
        wm.stubFor(get(urlPathEqualTo(PATH)).willReturn(okJson(VALID_JSON)));

        OpenErApiResponseDto dto = client.fetchLatestRates("   ");

        assertThat(dto).isNotNull();
        assertThat(dto.getResult()).isEqualTo("success");
    }

    @Test
    @DisplayName("fetchLatestRates: result=success ama rates null → exception YOK (sessiz hata, DTO döner)")
    void fetchLatestRates_ratesNull_silent() {
        wm.stubFor(get(urlPathEqualTo(PATH))
                .willReturn(okJson("{\"result\":\"success\",\"base_code\":\"USD\"}")));

        OpenErApiResponseDto dto = client.fetchLatestRates("USD");

        assertThat(dto).isNotNull();
        assertThat(dto.getResult()).isEqualTo("success");
        assertThat(dto.getRates()).isNull();
    }

    @Test
    @DisplayName("fetchLatestRates: result=success ama rates boş {} → exception YOK (sessiz hata, DTO döner)")
    void fetchLatestRates_ratesEmpty_silent() {
        wm.stubFor(get(urlPathEqualTo(PATH))
                .willReturn(okJson("{\"result\":\"success\",\"base_code\":\"USD\",\"rates\":{}}")));

        OpenErApiResponseDto dto = client.fetchLatestRates("USD");

        assertThat(dto).isNotNull();
        assertThat(dto.getRates()).isEmpty();
    }

    // ── error branches ──────────────────────────────────────────────────────

    @Test
    @DisplayName("fetchLatestRates: 200 ama boş gövde → response null → ExternalApiException (empty response)")
    void fetchLatestRates_emptyBody() {
        wm.stubFor(get(urlPathEqualTo(PATH))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("")));

        assertThatThrownBy(() -> client.fetchLatestRates("USD"))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("empty response");
    }

    @Test
    @DisplayName("fetchLatestRates: result alanı yok (null) → ExternalApiException (non-success result)")
    void fetchLatestRates_resultNull() {
        wm.stubFor(get(urlPathEqualTo(PATH))
                .willReturn(okJson("{\"base_code\":\"USD\",\"rates\":{\"TRY\":33.0}}")));

        assertThatThrownBy(() -> client.fetchLatestRates("USD"))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("non-success result");
    }

    @Test
    @DisplayName("fetchLatestRates: result=error (success değil) → ExternalApiException (non-success result)")
    void fetchLatestRates_resultNonSuccess() {
        wm.stubFor(get(urlPathEqualTo(PATH))
                .willReturn(okJson("{\"result\":\"error\",\"base_code\":\"USD\"}")));

        assertThatThrownBy(() -> client.fetchLatestRates("USD"))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("non-success result");
    }

    @Test
    @DisplayName("fetchLatestRates: HTTP 429 → rate-limit dalı → ExternalApiException (client error)")
    void fetchLatestRates_rateLimited() {
        wm.stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(429)));

        assertThatThrownBy(() -> client.fetchLatestRates("USD"))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("client error");
    }

    @Test
    @DisplayName("fetchLatestRates: HTTP 404 (429 dışı client error) → ExternalApiException (client error)")
    void fetchLatestRates_clientErrorNon429() {
        wm.stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(404)));

        assertThatThrownBy(() -> client.fetchLatestRates("USD"))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("client error");
    }

    @Test
    @DisplayName("fetchLatestRates: HTTP 500 server error → ExternalApiException (unavailable)")
    void fetchLatestRates_serverError() {
        wm.stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> client.fetchLatestRates("USD"))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("unavailable");
    }

    @Test
    @DisplayName("fetchLatestRates: bozuk JSON → genel catch → ExternalApiException (parse)")
    void fetchLatestRates_malformedJson() {
        wm.stubFor(get(urlPathEqualTo(PATH))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"result\":\"success\",\"rates\":{")));

        assertThatThrownBy(() -> client.fetchLatestRates("USD"))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("parse");
    }

    @Test
    @DisplayName("fetchLatestRates: ağ erişilemez (bağlantı reddi) → ResourceAccessException → ExternalApiException (network)")
    void fetchLatestRates_networkError() {
        // Dinlenmeyen port → connection refused → ResourceAccessException dalı.
        ReflectionTestUtils.setField(client, "openFxBaseUrl", "http://localhost:1");

        assertThatThrownBy(() -> client.fetchLatestRates("USD"))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("network connectivity");
    }

    // ── buildUrl dalları ─────────────────────────────────────────────────────

    @Test
    @DisplayName("buildUrl: baseUrl sonu '/' → çift slash olmadan birleşir (trailing-slash dalı, parse başarılı)")
    void buildUrl_trailingSlash() {
        // wm.baseUrl() sonuna '/' eklenince buildUrl 'baseUrl + baseCode' dalını kullanır → /USD.
        ReflectionTestUtils.setField(client, "openFxBaseUrl", wm.baseUrl() + "/");
        wm.stubFor(get(urlPathEqualTo(PATH)).willReturn(okJson(VALID_JSON)));

        OpenErApiResponseDto dto = client.fetchLatestRates("USD");

        assertThat(dto).isNotNull();
        assertThat(dto.getResult()).isEqualTo("success");
    }

    @Test
    @DisplayName("buildUrl: baseUrl null → '' ile birleşir ('/USD'), ağ yok → ExternalApiException (null-baseUrl dalı)")
    void buildUrl_nullBaseUrl() {
        ReflectionTestUtils.setField(client, "openFxBaseUrl", null);

        // baseUrl="" → "/USD" relative; RestTemplate geçersiz URI → exception (ağ değil ama hata yolu).
        assertThatThrownBy(() -> client.fetchLatestRates("USD"))
                .isInstanceOf(ExternalApiException.class);
    }
}
