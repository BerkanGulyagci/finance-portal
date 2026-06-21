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
import java.time.LocalDate;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * {@link TradingViewCalendarClient} testleri — WireMock ile gerçek HTTP yolu (RestClient) stub'lanır.
 * Base URL ctor parametresi olduğundan {@code wm.baseUrl()} doğrudan geçirilir; query param (from/to)
 * client tarafından eklenir, bu yüzden stub {@code urlPathEqualTo("/")} ile eşleşir.
 * Hata/boş durumlarda client exception fırlatmaz — daima boş liste döner.
 */
class TradingViewCalendarClientTest {

    private static WireMockServer wm;
    private TradingViewCalendarClient client;

    private static final LocalDate FROM = LocalDate.of(2026, 6, 1);
    private static final LocalDate TO = LocalDate.of(2026, 6, 30);

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
        client = new TradingViewCalendarClient(
                mock(CentralIntegrationLogService.class),
                wm.baseUrl(),
                "https://www.tradingview.com");
    }

    // ── isEnabled ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("isEnabled: base-url varsa true (API anahtarı gerektirmez)")
    void isEnabled_true() {
        assertThat(client.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("isEnabled: boş base-url'de false ve fetch boş liste (HTTP çağrısı yapmaz)")
    void isEnabled_falseAndFetchEmpty() {
        TradingViewCalendarClient noUrl = new TradingViewCalendarClient(
                mock(CentralIntegrationLogService.class), "", "https://www.tradingview.com");
        assertThat(noUrl.isEnabled()).isFalse();
        assertThat(noUrl.fetch(FROM, TO)).isEmpty();
    }

    // ── fetch happy path ──────────────────────────────────────────────────────

    @Test
    @DisplayName("fetch: 200 geçerli JSON → parse + alan eşleme (ISO date→time, importance→impact, currency doğrudan)")
    void fetch_ok() {
        String json = "{\"status\":\"ok\",\"result\":["
                + "{\"title\":\"CPI YoY\",\"country\":\"US\",\"currency\":\"USD\",\"importance\":1,"
                + "\"actual\":3.4,\"forecast\":3.2,\"previous\":3.1,\"unit\":\"%\","
                + "\"date\":\"2026-06-15T07:00:00.000Z\"},"
                + "{\"title\":\"Sanayi Üretimi\",\"country\":\"TR\",\"currency\":\"TRY\",\"importance\":-1,"
                + "\"actual\":6.0,\"previous\":-1.1,\"unit\":\"%\",\"date\":\"2026-06-16T08:00:00.000Z\"}"
                + "]}";
        wm.stubFor(get(urlPathEqualTo("/")).willReturn(okJson(json)));

        List<EconomicCalendarEvent> events = client.fetch(FROM, TO);

        assertThat(events).hasSize(2);
        EconomicCalendarEvent first = events.get(0);
        assertThat(first.getEvent()).isEqualTo("CPI YoY");
        assertThat(first.getCountry()).isEqualTo("US");
        assertThat(first.getCurrency()).isEqualTo("USD");
        // ISO 8601 UTC → "yyyy-MM-dd HH:mm:ss" (Finnhub format konvansiyonu)
        assertThat(first.getTime()).isEqualTo("2026-06-15 07:00:00");
        // importance 1 → high
        assertThat(first.getImpact()).isEqualTo("high");
        assertThat(first.getActual()).isEqualTo(3.4);
        assertThat(first.getEstimate()).isEqualTo(3.2);
        assertThat(first.getPrev()).isEqualTo(3.1);
        assertThat(first.getUnit()).isEqualTo("%");
        // Türkiye olayı + importance -1 → low
        EconomicCalendarEvent tr = events.get(1);
        assertThat(tr.getCountry()).isEqualTo("TR");
        assertThat(tr.getCurrency()).isEqualTo("TRY");
        assertThat(tr.getImpact()).isEqualTo("low");
        assertThat(tr.getEstimate()).isNull(); // forecast yok
    }

    @Test
    @DisplayName("fetch: importance 0 → medium")
    void fetch_importanceMedium() {
        String json = "{\"status\":\"ok\",\"result\":["
                + "{\"title\":\"Retail Sales\",\"country\":\"GB\",\"currency\":\"GBP\",\"importance\":0,"
                + "\"date\":\"2026-06-15T06:00:00.000Z\"}"
                + "]}";
        wm.stubFor(get(urlPathEqualTo("/")).willReturn(okJson(json)));

        List<EconomicCalendarEvent> events = client.fetch(FROM, TO);

        assertThat(events).hasSize(1);
        assertThat(events.get(0).getImpact()).isEqualTo("medium");
    }

    @Test
    @DisplayName("fetch: tarayıcı User-Agent header'ı gönderilir (nginx 403 önlemi)")
    void fetch_sendsUserAgentHeader() {
        wm.stubFor(get(urlPathEqualTo("/")).willReturn(okJson("{\"status\":\"ok\",\"result\":[]}")));

        client.fetch(FROM, TO);

        // User-Agent tarayıcı benzeri olmalı (Mozilla içermeli) — UA'sız istek 403 alır.
        wm.verify(com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor(urlPathEqualTo("/"))
                .withHeader("User-Agent",
                        com.github.tomakehurst.wiremock.client.WireMock.containing("Mozilla")));
        // NOT: Origin header'ı da gönderilir AMA HttpURLConnection onu "restricted" sayıp düşürür;
        // ancak JVM argümanı -Dsun.net.http.allowRestrictedHeaders=true verildiğinde geçer (Dockerfile
        // ENTRYPOINT'inde set edilir, canlı kanıtlandı). Test JVM'inde bu argüman olmadığından
        // Origin'i burada doğrulamayız — birim test JVM-arg'a bağlanmamalı.
    }

    @Test
    @DisplayName("fetch: title ve date ikisi de boş olan olay atlanır (parseOne null)")
    void fetch_skipsEventWithoutTitleAndDate() {
        String json = "{\"status\":\"ok\",\"result\":["
                + "{\"country\":\"DE\",\"currency\":\"EUR\"},"  // ne title ne date → atlanır
                + "{\"title\":\"PMI\",\"country\":\"DE\",\"currency\":\"EUR\",\"date\":\"2026-06-05T08:00:00.000Z\"}"
                + "]}";
        wm.stubFor(get(urlPathEqualTo("/")).willReturn(okJson(json)));

        List<EconomicCalendarEvent> events = client.fetch(FROM, TO);

        assertThat(events).hasSize(1);
        assertThat(events.get(0).getEvent()).isEqualTo("PMI");
        assertThat(events.get(0).getActual()).isNull();
    }

    @Test
    @DisplayName("fetch: result boş dizi → boş liste")
    void fetch_emptyArray() {
        wm.stubFor(get(urlPathEqualTo("/")).willReturn(okJson("{\"status\":\"ok\",\"result\":[]}")));
        assertThat(client.fetch(FROM, TO)).isEmpty();
    }

    @Test
    @DisplayName("fetch: result alanı yok / dizi değil → boş liste")
    void fetch_noResultField() {
        wm.stubFor(get(urlPathEqualTo("/")).willReturn(okJson("{\"status\":\"error\"}")));
        assertThat(client.fetch(FROM, TO)).isEmpty();
    }

    // ── error branches → daima boş liste, exception yok ─────────────────────────

    @Test
    @DisplayName("fetch: 403 (Origin reddi / 4xx) → boş liste (HttpClientErrorException yutulur)")
    void fetch_clientError() {
        wm.stubFor(get(urlPathEqualTo("/")).willReturn(aResponse().withStatus(403)));
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
        wm.stubFor(get(urlPathEqualTo("/")).willReturn(okJson("{not-valid-json")));
        assertThat(client.fetch(FROM, TO)).isEmpty();
    }
}
