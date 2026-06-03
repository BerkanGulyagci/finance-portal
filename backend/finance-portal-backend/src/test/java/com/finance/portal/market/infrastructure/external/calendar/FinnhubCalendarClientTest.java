package com.finance.portal.market.infrastructure.external.calendar;

import com.finance.portal.common.application.logging.CentralIntegrationLogService;
import com.finance.portal.market.application.calendar.model.EconomicCalendarEvent;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * {@link FinnhubCalendarClient} testleri — WireMock ile gerçek HTTP yolu (RestTemplate) stub'lanır.
 * Base URL ctor parametresi olduğundan {@code wm.baseUrl()} doğrudan geçirilir; query param (from/to/token)
 * client tarafından eklenir, bu yüzden stub {@code urlPathEqualTo("/")} ile eşleşir.
 * Hata/boş durumlarda client exception fırlatmaz — daima boş liste döner.
 */
class FinnhubCalendarClientTest {

    private static WireMockServer wm;
    private FinnhubCalendarClient client;

    private static final LocalDate FROM = LocalDate.of(2024, 1, 1);
    private static final LocalDate TO = LocalDate.of(2024, 1, 31);

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
        client = new FinnhubCalendarClient(
                new RestTemplate(),
                mock(CentralIntegrationLogService.class),
                wm.baseUrl(),
                "test-key");
    }

    // ── isEnabled ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("isEnabled: anahtar varsa true")
    void isEnabled_true() {
        assertThat(client.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("isEnabled: boş anahtarda false ve fetch boş liste döner (HTTP çağrısı yapmaz)")
    void isEnabled_falseAndFetchEmpty() {
        FinnhubCalendarClient noKey = new FinnhubCalendarClient(
                new RestTemplate(), mock(CentralIntegrationLogService.class), wm.baseUrl(), "");
        assertThat(noKey.isEnabled()).isFalse();
        assertThat(noKey.fetch(FROM, TO)).isEmpty();
    }

    // ── fetch happy path ──────────────────────────────────────────────────────

    @Test
    @DisplayName("fetch: 200 geçerli JSON → parse edilen olay listesi döner (currency country'den türetilir)")
    void fetch_ok() {
        String json = "{\"economicCalendar\":["
                + "{\"time\":\"2024-01-02 13:30:00\",\"country\":\"US\",\"event\":\"CPI\",\"impact\":\"high\","
                + "\"actual\":3.4,\"estimate\":3.2,\"prev\":3.1,\"unit\":\"%\"},"
                + "{\"time\":\"2024-01-03 07:00:00\",\"country\":\"tr\",\"event\":\"Faiz Kararı\",\"impact\":\"high\"}"
                + "]}";
        wm.stubFor(get(urlPathEqualTo("/")).willReturn(okJson(json)));

        List<EconomicCalendarEvent> events = client.fetch(FROM, TO);

        assertThat(events).hasSize(2);
        EconomicCalendarEvent first = events.get(0);
        assertThat(first.getCountry()).isEqualTo("US");
        assertThat(first.getCurrency()).isEqualTo("USD");
        assertThat(first.getEvent()).isEqualTo("CPI");
        assertThat(first.getImpact()).isEqualTo("high");
        assertThat(first.getActual()).isEqualTo(3.4);
        assertThat(first.getEstimate()).isEqualTo(3.2);
        assertThat(first.getPrev()).isEqualTo(3.1);
        assertThat(first.getUnit()).isEqualTo("%");
        // country lowercase "tr" → uppercase'e çevrilip TRY bulunur
        assertThat(events.get(1).getCurrency()).isEqualTo("TRY");
    }

    @Test
    @DisplayName("fetch: event ve time ikisi de boş olan olay atlanır (parseOne null döner)")
    void fetch_skipsEventWithoutEventAndTime() {
        String json = "{\"economicCalendar\":["
                + "{\"country\":\"DE\"},"  // ne time ne event → atlanır
                + "{\"time\":\"2024-01-05 10:00:00\",\"country\":\"DE\",\"event\":\"PMI\"}"
                + "]}";
        wm.stubFor(get(urlPathEqualTo("/")).willReturn(okJson(json)));

        List<EconomicCalendarEvent> events = client.fetch(FROM, TO);

        assertThat(events).hasSize(1);
        assertThat(events.get(0).getEvent()).isEqualTo("PMI");
        // DE eurozone → EUR
        assertThat(events.get(0).getCurrency()).isEqualTo("EUR");
        // sayısal alanlar yoksa null
        assertThat(events.get(0).getActual()).isNull();
    }

    @Test
    @DisplayName("fetch: economicCalendar boş dizi → boş liste döner")
    void fetch_emptyArray() {
        wm.stubFor(get(urlPathEqualTo("/")).willReturn(okJson("{\"economicCalendar\":[]}")));
        assertThat(client.fetch(FROM, TO)).isEmpty();
    }

    @Test
    @DisplayName("fetch: economicCalendar alanı yok / dizi değil → boş liste döner")
    void fetch_noCalendarField() {
        wm.stubFor(get(urlPathEqualTo("/")).willReturn(okJson("{\"foo\":\"bar\"}")));
        assertThat(client.fetch(FROM, TO)).isEmpty();
    }

    // ── error branches → daima boş liste, exception yok ─────────────────────────

    @Test
    @DisplayName("fetch: 429 rate-limit (4xx) → boş liste (HttpClientErrorException yutulur)")
    void fetch_clientError() {
        wm.stubFor(get(urlPathEqualTo("/")).willReturn(aResponse().withStatus(429)));
        assertThat(client.fetch(FROM, TO)).isEmpty();
    }

    @Test
    @DisplayName("fetch: 500 server error → boş liste (Exception dalı yutulur)")
    void fetch_serverError() {
        wm.stubFor(get(urlPathEqualTo("/")).willReturn(aResponse().withStatus(500)));
        assertThat(client.fetch(FROM, TO)).isEmpty();
    }

    @Test
    @DisplayName("fetch: bozuk JSON → boş liste (parse hatası yutulur)")
    void fetch_malformedJson() {
        wm.stubFor(get(urlPathEqualTo("/"))
                .willReturn(okJson("{not-valid-json")));
        assertThat(client.fetch(FROM, TO)).isEmpty();
    }
}
