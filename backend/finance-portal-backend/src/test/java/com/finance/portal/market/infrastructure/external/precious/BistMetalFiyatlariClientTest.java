package com.finance.portal.market.infrastructure.external.precious;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.portal.common.application.logging.CentralIntegrationLogService;
import com.finance.portal.market.application.precious.model.BistMetalDailyPoint;
import com.finance.portal.market.application.precious.model.PreciousMetalType;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * {@link BistMetalFiyatlariClient} testleri — WireMock ile gerçek HTTP yolu (RestTemplate) stub'lanır.
 * Base URL bir {@code @Value} alanı ({@code metalBaseUrl}) olduğu için ReflectionTestUtils ile set edilir.
 * Client tüm hataları yakalar (catch Exception) → hata dallarında boş liste döner, exception fırlatmaz.
 * Entegrasyon log servisi mock (publish no-op). Surefire'da koşsun diye {@code *Test} (WireMock hafif, Spring yok).
 */
class BistMetalFiyatlariClientTest {

    private static WireMockServer wm;
    private BistMetalFiyatlariClient client;

    /** UriComponentsBuilder.fromHttpUrl(baseUrl) — baseUrl path'siz, istek "/" yoluna gider. */
    private static final String PATH = "/";

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
        client = new BistMetalFiyatlariClient(
                new RestTemplate(),
                new ObjectMapper(),
                mock(CentralIntegrationLogService.class));
        ReflectionTestUtils.setField(client, "metalBaseUrl", wm.baseUrl());
    }

    // ── fetchMetalPrices: happy path ──────────────────────────────────────────

    @Test
    @DisplayName("fetchMetalPrices: success + MTL kayıtları → gruplanmış nokta döner (usd/try/eur set edilir)")
    void fetchMetalPrices_ok() {
        String json = "{"
                + "\"status\":\"success\","
                + "\"source\":\"BIST\","
                + "\"data\":["
                + "  {\"priceDate\":\"2026-01-02\",\"priceRef\":\"MTL\",\"priceCurrency\":\"USD\",\"priceWeight\":\"OZ\",\"priceValue\":2050.50},"
                + "  {\"priceDate\":\"2026-01-02\",\"priceRef\":\"MTL\",\"priceCurrency\":\"TRY\",\"priceWeight\":\"KG\",\"priceValue\":2400000},"
                + "  {\"priceDate\":\"2026-01-02\",\"priceRef\":\"MTL\",\"priceCurrency\":\"EUR\",\"priceWeight\":\"OZ\",\"priceValue\":1900.25},"
                + "  {\"priceDate\":\"2026-01-02\",\"priceRef\":\"REF\",\"priceCurrency\":\"USD\",\"priceWeight\":\"OZ\",\"priceValue\":9999}"
                + "]}";
        wm.stubFor(get(urlPathEqualTo(PATH)).willReturn(okJson(json)));

        List<BistMetalDailyPoint> r = client.fetchMetalPrices(PreciousMetalType.GOLD, "2026-01-01", "2026-01-03");

        assertThat(r).hasSize(1);
        BistMetalDailyPoint p = r.get(0);
        assertThat(p.getDate()).isEqualTo("2026-01-02");
        assertThat(p.getMetalType()).isEqualTo(PreciousMetalType.GOLD);
        assertThat(p.getUsdOns()).isEqualByComparingTo("2050.50");
        assertThat(p.getTryKg()).isEqualByComparingTo("2400000");
        // tryGram = tryKg / 1000
        assertThat(p.getTryGram()).isEqualByComparingTo("2400.0000");
        assertThat(p.getEurOns()).isEqualByComparingTo("1900.25");
        assertThat(p.isValidPrice()).isTrue();
    }

    @Test
    @DisplayName("fetchMetalPrices: birden çok tarih → ASC sıralı döner")
    void fetchMetalPrices_multipleDatesSortedAsc() {
        String json = "{"
                + "\"status\":\"success\",\"data\":["
                + "  {\"priceDate\":\"2026-01-03\",\"priceRef\":\"MTL\",\"priceCurrency\":\"USD\",\"priceWeight\":\"OZ\",\"priceValue\":31.0},"
                + "  {\"priceDate\":\"2026-01-01\",\"priceRef\":\"MTL\",\"priceCurrency\":\"USD\",\"priceWeight\":\"OZ\",\"priceValue\":30.0}"
                + "]}";
        wm.stubFor(get(urlPathEqualTo(PATH)).willReturn(okJson(json)));

        List<BistMetalDailyPoint> r = client.fetchMetalPrices(PreciousMetalType.SILVER, "2026-01-01", "2026-01-03");

        assertThat(r).hasSize(2);
        assertThat(r.get(0).getDate()).isEqualTo("2026-01-01");
        assertThat(r.get(1).getDate()).isEqualTo("2026-01-03");
    }

    // ── fetchMetalPrices: error / silent-failure branches ─────────────────────

    @Test
    @DisplayName("fetchMetalPrices: boş gövde → boş liste döner")
    void fetchMetalPrices_emptyBody() {
        wm.stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(200).withBody("")));

        assertThat(client.fetchMetalPrices(PreciousMetalType.PLATINUM, "2026-01-01", "2026-01-03"))
                .isEmpty();
    }

    @Test
    @DisplayName("fetchMetalPrices: status != success → boş liste döner")
    void fetchMetalPrices_nonSuccess() {
        wm.stubFor(get(urlPathEqualTo(PATH))
                .willReturn(okJson("{\"status\":\"error\",\"data\":[]}")));

        assertThat(client.fetchMetalPrices(PreciousMetalType.PALLADIUM, "2026-01-01", "2026-01-03"))
                .isEmpty();
    }

    @Test
    @DisplayName("fetchMetalPrices: success ama tüm kayıtlar REF (0 gruplanmış nokta) → boş liste döner")
    void fetchMetalPrices_rawRowsButNoGroupedPoints() {
        String json = "{"
                + "\"status\":\"success\",\"data\":["
                + "  {\"priceDate\":\"2026-01-02\",\"priceRef\":\"REF\",\"priceCurrency\":\"USD\",\"priceWeight\":\"OZ\",\"priceValue\":2050}"
                + "]}";
        wm.stubFor(get(urlPathEqualTo(PATH)).willReturn(okJson(json)));

        assertThat(client.fetchMetalPrices(PreciousMetalType.GOLD, "2026-01-01", "2026-01-03"))
                .isEmpty();
    }

    @Test
    @DisplayName("fetchMetalPrices: 500 sunucu hatası → exception yakalanır, boş liste döner")
    void fetchMetalPrices_serverError() {
        wm.stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(500)));

        assertThat(client.fetchMetalPrices(PreciousMetalType.GOLD, "2026-01-01", "2026-01-03"))
                .isEmpty();
    }

    @Test
    @DisplayName("fetchMetalPrices: bozuk JSON → exception yakalanır, boş liste döner")
    void fetchMetalPrices_malformedJson() {
        wm.stubFor(get(urlPathEqualTo(PATH)).willReturn(okJson("{not-valid-json")));

        assertThat(client.fetchMetalPrices(PreciousMetalType.SILVER, "2026-01-01", "2026-01-03"))
                .isEmpty();
    }

    // ── fetchMetalPricesLastDays + fetchLatestValidPoint ──────────────────────

    @Test
    @DisplayName("fetchMetalPricesLastDays: tarih aralığını hesaplar, parse edilen liste döner")
    void fetchMetalPricesLastDays_ok() {
        String json = "{"
                + "\"status\":\"success\",\"data\":["
                + "  {\"priceDate\":\"2026-06-01\",\"priceRef\":\"MTL\",\"priceCurrency\":\"USD\",\"priceWeight\":\"OZ\",\"priceValue\":1000}"
                + "]}";
        wm.stubFor(get(urlPathEqualTo(PATH)).willReturn(okJson(json)));

        List<BistMetalDailyPoint> r = client.fetchMetalPricesLastDays(PreciousMetalType.PLATINUM, 10);

        assertThat(r).hasSize(1);
        assertThat(r.get(0).isValidPrice()).isTrue();
    }

    @Test
    @DisplayName("fetchLatestValidPoint: en son geçerli noktayı döner")
    void fetchLatestValidPoint_returnsLastValid() {
        String json = "{"
                + "\"status\":\"success\",\"data\":["
                + "  {\"priceDate\":\"2026-06-01\",\"priceRef\":\"MTL\",\"priceCurrency\":\"USD\",\"priceWeight\":\"OZ\",\"priceValue\":1000},"
                + "  {\"priceDate\":\"2026-06-02\",\"priceRef\":\"MTL\",\"priceCurrency\":\"USD\",\"priceWeight\":\"OZ\",\"priceValue\":1100}"
                + "]}";
        wm.stubFor(get(urlPathEqualTo(PATH)).willReturn(okJson(json)));

        BistMetalDailyPoint latest = client.fetchLatestValidPoint(PreciousMetalType.GOLD);

        assertThat(latest).isNotNull();
        assertThat(latest.getDate()).isEqualTo("2026-06-02");
        assertThat(latest.isValidPrice()).isTrue();
    }

    @Test
    @DisplayName("fetchLatestValidPoint: hiç geçerli/veri yoksa → null döner")
    void fetchLatestValidPoint_null() {
        wm.stubFor(get(urlPathEqualTo(PATH))
                .willReturn(okJson("{\"status\":\"success\",\"data\":[]}")));

        assertThat(client.fetchLatestValidPoint(PreciousMetalType.SILVER)).isNull();
    }
}
