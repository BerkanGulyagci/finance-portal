package com.finance.portal.market.infrastructure.external.precious;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.portal.common.application.logging.CentralIntegrationLogService;
import com.finance.portal.market.application.precious.model.BistPreciousMetalPoint;
import com.finance.portal.market.application.precious.model.PreciousMetalType;
import com.finance.portal.market.application.precious.model.PriceUnit;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * {@link BistPreciousMetalsClient} testleri — WireMock ile gerçek HTTP yolu (RestTemplate) stub'lanır.
 * Base URL bir {@code @Value} alanı (bistBaseUrl) olduğundan ReflectionTestUtils ile WireMock'a yönlendirilir.
 * Endpoint sabit path'tir (veri-sorgulama.php), query param'lar değişir → urlPathEqualTo ile eşleştirilir.
 * Entegrasyon log servisi mock (publish no-op). Spring yok, hafif → Surefire'da {@code *Test} olarak koşar.
 */
class BistPreciousMetalsClientTest {

    private static final String PATH = "/veri-sorgulama.php";

    private static WireMockServer wm;
    private BistPreciousMetalsClient client;

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
        client = new BistPreciousMetalsClient(
                new RestTemplate(),
                new ObjectMapper(),
                mock(CentralIntegrationLogService.class));
        // @Value alanını WireMock base URL + endpoint path'ine yönlendir
        ReflectionTestUtils.setField(client, "bistBaseUrl", wm.baseUrl() + PATH);
    }

    // ── fetchHistory: happy path (TL/Kg → gram türetme + DESC→ASC) ─────────────

    @Test
    @DisplayName("fetchHistory TRY_KG: success JSON → ASC sıralı, gram değerleri türetilmiş liste")
    void fetchHistory_tryKg_ok() {
        // BIST DESC döndürür: yeni→eski. Client ASC'e çevirir.
        String json = "{\"status\":\"success\",\"source\":\"bist\",\"data\":["
                + "{\"guntar\":\"2026-01-02\",\"min_f\":2900000,\"max_f\":3100000,\"kpo\":3000000,\"sum_o\":2950000,\"sum_h\":1234},"
                + "{\"guntar\":\"2026-01-01\",\"min_f\":2800000,\"max_f\":3000000,\"kpo\":2900000,\"sum_o\":2850000,\"sum_h\":4321}"
                + "]}";
        wm.stubFor(get(urlPathEqualTo(PATH)).willReturn(okJson(json)));

        List<BistPreciousMetalPoint> points =
                client.fetchHistory(PreciousMetalType.GOLD, PriceUnit.TRY_KG, "2026-01-01", "2026-01-02");

        assertThat(points).hasSize(2);
        // DESC→ASC: ilk eleman en eski tarih olmalı
        assertThat(points.get(0).getDate()).isEqualTo("2026-01-01");
        assertThat(points.get(1).getDate()).isEqualTo("2026-01-02");
        // metal/unit set edildi
        assertThat(points.get(0).getMetalType()).isEqualTo(PreciousMetalType.GOLD);
        assertThat(points.get(0).getUnit()).isEqualTo(PriceUnit.TRY_KG);
        // validPrice (kpo > 0)
        assertThat(points.get(0).isValidPrice()).isTrue();
        // gram türetme: 2900000 / 1000 = 2900.0000
        assertThat(points.get(0).getGramClose())
                .isEqualByComparingTo(new BigDecimal("2900.0000"));
        // ons alanları TL/Kg modunda doldurulmaz
        assertThat(points.get(0).getCloseUsdOns()).isNull();
    }

    @Test
    @DisplayName("fetchHistory USD_ONS: success JSON → ons ham değerleri kopyalanır")
    void fetchHistory_usdOns_ok() {
        String json = "{\"status\":\"success\",\"data\":["
                + "{\"guntar\":\"2026-01-01\",\"min_f\":1900,\"max_f\":2100,\"kpo\":2000,\"sum_o\":1980,\"sum_h\":55}"
                + "]}";
        wm.stubFor(get(urlPathEqualTo(PATH)).willReturn(okJson(json)));

        List<BistPreciousMetalPoint> points =
                client.fetchHistory(PreciousMetalType.GOLD, PriceUnit.USD_ONS, "2026-01-01", "2026-01-01");

        assertThat(points).hasSize(1);
        BistPreciousMetalPoint p = points.get(0);
        // ons modunda ham değer kopyalanır
        assertThat(p.getCloseUsdOns()).isEqualByComparingTo(new BigDecimal("2000"));
        assertThat(p.getWeightedAverageUsdOns()).isEqualByComparingTo(new BigDecimal("1980"));
        // gram değerleri bu modda doldurulmaz
        assertThat(p.getGramClose()).isNull();
        assertThat(p.isValidPrice()).isTrue();
    }

    @Test
    @DisplayName("fetchHistory: validPrice=false (kpo=0 ve sum_o=0) kaydı dahil ama validPrice false")
    void fetchHistory_invalidPriceRecord() {
        String json = "{\"status\":\"success\",\"data\":["
                + "{\"guntar\":\"2026-01-01\",\"min_f\":0,\"max_f\":0,\"kpo\":0,\"sum_o\":0,\"sum_h\":0}"
                + "]}";
        wm.stubFor(get(urlPathEqualTo(PATH)).willReturn(okJson(json)));

        List<BistPreciousMetalPoint> points =
                client.fetchHistory(PreciousMetalType.SILVER, PriceUnit.TRY_KG, "2026-01-01", "2026-01-01");

        assertThat(points).hasSize(1);
        assertThat(points.get(0).isValidPrice()).isFalse();
    }

    // ── Error / fallback branches ─────────────────────────────────────────────

    @Test
    @DisplayName("fetchHistory: status != success → boş liste (sessiz hata)")
    void fetchHistory_nonSuccess() {
        wm.stubFor(get(urlPathEqualTo(PATH))
                .willReturn(okJson("{\"status\":\"error\",\"data\":[]}")));

        assertThat(client.fetchHistory(PreciousMetalType.GOLD, PriceUnit.TRY_KG, "a", "b")).isEmpty();
    }

    @Test
    @DisplayName("fetchHistory: success ama 0 data → boş liste")
    void fetchHistory_successEmptyData() {
        wm.stubFor(get(urlPathEqualTo(PATH))
                .willReturn(okJson("{\"status\":\"success\",\"data\":[]}")));

        assertThat(client.fetchHistory(PreciousMetalType.PLATINUM, PriceUnit.TRY_KG, "a", "b")).isEmpty();
    }

    @Test
    @DisplayName("fetchHistory: boş gövde → boş liste")
    void fetchHistory_emptyBody() {
        wm.stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(200).withBody("")));

        assertThat(client.fetchHistory(PreciousMetalType.GOLD, PriceUnit.TRY_KG, "a", "b")).isEmpty();
    }

    @Test
    @DisplayName("fetchHistory: malformed JSON → catch → boş liste")
    void fetchHistory_malformedJson() {
        wm.stubFor(get(urlPathEqualTo(PATH)).willReturn(okJson("{not-valid-json")));

        assertThat(client.fetchHistory(PreciousMetalType.GOLD, PriceUnit.TRY_KG, "a", "b")).isEmpty();
    }

    @Test
    @DisplayName("fetchHistory: HTTP 500 → exchange fırlatır → catch → boş liste")
    void fetchHistory_serverError() {
        wm.stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(500)));

        assertThat(client.fetchHistory(PreciousMetalType.GOLD, PriceUnit.USD_ONS, "a", "b")).isEmpty();
    }

    // ── fetchHistoryLastDays + fetchLatestValidPoint ──────────────────────────

    @Test
    @DisplayName("fetchHistoryLastDays: son N gün için success → liste döner")
    void fetchHistoryLastDays_ok() {
        String json = "{\"status\":\"success\",\"data\":["
                + "{\"guntar\":\"2026-01-01\",\"kpo\":3000000,\"sum_o\":2950000}"
                + "]}";
        wm.stubFor(get(urlPathEqualTo(PATH)).willReturn(okJson(json)));

        List<BistPreciousMetalPoint> points =
                client.fetchHistoryLastDays(PreciousMetalType.GOLD, PriceUnit.TRY_KG, 7);

        assertThat(points).hasSize(1);
        assertThat(points.get(0).isValidPrice()).isTrue();
    }

    @Test
    @DisplayName("fetchLatestValidPoint: son geçerli kaydı (ASC sondan) döndürür")
    void fetchLatestValidPoint_returnsLastValid() {
        // ASC çıktı: [eski geçerli, yeni GEÇERSİZ] → en yeni geçersiz olduğu için bir önceki geçerli dönmeli.
        // BIST DESC verir → client reverse eder. DESC: [yeni(geçersiz), eski(geçerli)]
        String json = "{\"status\":\"success\",\"data\":["
                + "{\"guntar\":\"2026-01-02\",\"kpo\":0,\"sum_o\":0},"
                + "{\"guntar\":\"2026-01-01\",\"kpo\":3000000,\"sum_o\":2950000}"
                + "]}";
        wm.stubFor(get(urlPathEqualTo(PATH)).willReturn(okJson(json)));

        BistPreciousMetalPoint latest =
                client.fetchLatestValidPoint(PreciousMetalType.GOLD, PriceUnit.TRY_KG);

        assertThat(latest).isNotNull();
        assertThat(latest.getDate()).isEqualTo("2026-01-01");
        assertThat(latest.isValidPrice()).isTrue();
    }

    @Test
    @DisplayName("fetchLatestValidPoint: hiç veri yok → null")
    void fetchLatestValidPoint_emptyReturnsNull() {
        wm.stubFor(get(urlPathEqualTo(PATH))
                .willReturn(okJson("{\"status\":\"success\",\"data\":[]}")));

        assertThat(client.fetchLatestValidPoint(PreciousMetalType.GOLD, PriceUnit.TRY_KG)).isNull();
    }
}
