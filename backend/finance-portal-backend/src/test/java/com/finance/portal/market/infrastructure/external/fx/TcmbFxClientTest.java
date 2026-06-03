package com.finance.portal.market.infrastructure.external.fx;

import com.finance.portal.common.application.exception.ExternalApiException;
import com.finance.portal.common.application.logging.CentralIntegrationLogService;
import com.finance.portal.market.infrastructure.external.fx.dto.TcmbExchangeRatesDto;
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
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * {@link TcmbFxClient} testleri — WireMock ile gerçek HTTP yolu (RestTemplate) stub'lanır.
 * Tek public uç {@code fetchLatestRates()} için happy-path (XML parse) + hata dalları
 * (boş gövde, 429 rate-limit, 500 server error, bozuk XML, 0 Currency sessiz-hata).
 * Entegrasyon log servisi mock (publish no-op). Base URL @Value alanı olduğu için
 * {@link ReflectionTestUtils} ile WireMock URL'ine set edilir.
 */
class TcmbFxClientTest {

    private static final String PATH = "/today.xml";

    private static WireMockServer wm;
    private TcmbFxClient client;

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
        client = new TcmbFxClient(new RestTemplate(), mock(CentralIntegrationLogService.class));
        ReflectionTestUtils.setField(client, "tcmbUrl", wm.baseUrl() + PATH);
    }

    // ── happy path ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("fetchLatestRates: geçerli XML → DTO tarih + currency listesi parse edilir")
    void fetchLatestRates_ok() {
        String xml = "<Tarih_Date Date=\"06.03.2026\">"
                + "<Currency CurrencyCode=\"USD\">"
                + "<Unit>1</Unit>"
                + "<ForexBuying>33.1000</ForexBuying>"
                + "<ForexSelling>33.2000</ForexSelling>"
                + "<BanknoteBuying>33.0500</BanknoteBuying>"
                + "<BanknoteSelling>33.2500</BanknoteSelling>"
                + "</Currency>"
                + "<Currency CurrencyCode=\"EUR\">"
                + "<Unit>1</Unit>"
                + "<ForexBuying>36.1000</ForexBuying>"
                + "<ForexSelling>36.2000</ForexSelling>"
                + "</Currency>"
                + "</Tarih_Date>";

        wm.stubFor(get(urlPathEqualTo(PATH))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/xml")
                        .withBody(xml)));

        TcmbExchangeRatesDto dto = client.fetchLatestRates();

        assertThat(dto).isNotNull();
        assertThat(dto.getDate()).isEqualTo("06.03.2026");
        assertThat(dto.getCurrencies()).hasSize(2);
        assertThat(dto.getCurrencies().get(0).getCurrencyCode()).isEqualTo("USD");
        assertThat(dto.getCurrencies().get(0).getUnit()).isEqualTo(1);
        assertThat(dto.getCurrencies().get(0).getForexBuying()).isEqualTo("33.1000");
        assertThat(dto.getCurrencies().get(0).getForexSelling()).isEqualTo("33.2000");
        assertThat(dto.getCurrencies().get(0).getBanknoteBuying()).isEqualTo("33.0500");
        assertThat(dto.getCurrencies().get(1).getCurrencyCode()).isEqualTo("EUR");
    }

    @Test
    @DisplayName("fetchLatestRates: CurrencyCode boş → Kod attribute fallback + geçersiz Unit → null")
    void fetchLatestRates_kodFallbackAndInvalidUnit() {
        String xml = "<Tarih_Date Date=\"06.03.2026\">"
                + "<Currency Kod=\"GBP\">"
                + "<Unit>not-a-number</Unit>"
                + "<ForexBuying>42.0000</ForexBuying>"
                + "</Currency>"
                + "</Tarih_Date>";

        wm.stubFor(get(urlPathEqualTo(PATH))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/xml")
                        .withBody(xml)));

        TcmbExchangeRatesDto dto = client.fetchLatestRates();

        assertThat(dto.getCurrencies()).hasSize(1);
        assertThat(dto.getCurrencies().get(0).getCurrencyCode()).isEqualTo("GBP");
        assertThat(dto.getCurrencies().get(0).getUnit()).isNull();
        assertThat(dto.getCurrencies().get(0).getForexBuying()).isEqualTo("42.0000");
    }

    @Test
    @DisplayName("fetchLatestRates: geçerli XML ama hiç <Currency> yok → boş liste (sessiz hata, exception YOK)")
    void fetchLatestRates_noCurrencies() {
        String xml = "<Tarih_Date Date=\"06.03.2026\"></Tarih_Date>";

        wm.stubFor(get(urlPathEqualTo(PATH))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/xml")
                        .withBody(xml)));

        TcmbExchangeRatesDto dto = client.fetchLatestRates();

        assertThat(dto).isNotNull();
        assertThat(dto.getCurrencies()).isEmpty();
    }

    // ── error branches ──────────────────────────────────────────────────────

    @Test
    @DisplayName("fetchLatestRates: boş gövde → ExternalApiException")
    void fetchLatestRates_emptyBody() {
        wm.stubFor(get(urlPathEqualTo(PATH))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/xml")
                        .withBody("   ")));

        assertThatThrownBy(() -> client.fetchLatestRates())
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("empty response");
    }

    @Test
    @DisplayName("fetchLatestRates: HTTP 429 rate-limit → ExternalApiException")
    void fetchLatestRates_rateLimited() {
        wm.stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(429)));

        assertThatThrownBy(() -> client.fetchLatestRates())
                .isInstanceOf(ExternalApiException.class);
    }

    @Test
    @DisplayName("fetchLatestRates: HTTP 404 client error (429 dışı) → ExternalApiException")
    void fetchLatestRates_clientError() {
        wm.stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(404)));

        assertThatThrownBy(() -> client.fetchLatestRates())
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("client error");
    }

    @Test
    @DisplayName("fetchLatestRates: HTTP 500 server error → ExternalApiException")
    void fetchLatestRates_serverError() {
        wm.stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> client.fetchLatestRates())
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("unavailable");
    }

    @Test
    @DisplayName("fetchLatestRates: bozuk XML → ExternalApiException (parse hatası)")
    void fetchLatestRates_malformedXml() {
        wm.stubFor(get(urlPathEqualTo(PATH))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/xml")
                        .withBody("<Tarih_Date Date=\"x\"><Currency>")));

        assertThatThrownBy(() -> client.fetchLatestRates())
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("parse");
    }
}
