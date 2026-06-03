package com.finance.portal.market.infrastructure.external.bond;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.portal.common.application.logging.CentralIntegrationLogService;
import com.finance.portal.market.application.bond.evds.model.EvdsSeriesInfo;
import com.finance.portal.market.application.bond.evds.model.EvdsSeriesPoint;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * {@link EvdsBondClient} testleri — WireMock ile gerçek HTTP yolu (RestTemplate) stub'lanır.
 *
 * <p>EVDS URL'i standart query string kullanmaz (path'e {@code =} ve {@code &} ile eklenir),
 * bu yüzden stub eşleşmesi {@code any(anyUrl())} ile yapılır (URL-encode kırılganlığından kaçınılır).
 *
 * <p>{@code baseUrl}/{@code apiKey}/{@code dataGroup} {@code @Value} alanları olduğu için
 * {@link ReflectionTestUtils} ile set edilir. Entegrasyon log servisi mock (publish no-op).
 * Tüm hata dalları null/boş-liste döndürür (exception fırlatmaz); yalnızca eksik API key
 * {@link IllegalStateException} fırlatır.
 */
class EvdsBondClientTest {

    private static WireMockServer wm;
    private EvdsBondClient client;

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
        client = new EvdsBondClient(new RestTemplate(), new ObjectMapper(),
                mock(CentralIntegrationLogService.class));
        ReflectionTestUtils.setField(client, "baseUrl", wm.baseUrl());
        ReflectionTestUtils.setField(client, "apiKey", "test-key");
        ReflectionTestUtils.setField(client, "dataGroup", "bie_pydibs");
    }

    private static final LocalDate START = LocalDate.of(2026, 5, 1);
    private static final LocalDate END = LocalDate.of(2026, 5, 4);

    // ── validateApiKey ────────────────────────────────────────────────────────

    @Test
    @DisplayName("fetchSeries: API key boş → IllegalStateException")
    void fetchSeries_missingApiKey_throws() {
        ReflectionTestUtils.setField(client, "apiKey", "  ");
        assertThatThrownBy(() -> client.fetchSeries("TP.TRD070727K10", START, END))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("fetchSeriesList: API key null → IllegalStateException")
    void fetchSeriesList_nullApiKey_throws() {
        ReflectionTestUtils.setField(client, "apiKey", null);
        assertThatThrownBy(() -> client.fetchSeriesList("bie_pydibs"))
                .isInstanceOf(IllegalStateException.class);
    }

    // ── fetchSeries happy path & parse branches ───────────────────────────────

    @Test
    @DisplayName("fetchSeries: geçerli items → tarih sıralı noktalar parse edilir")
    void fetchSeries_ok() {
        String json = "{\"totalCount\":2,\"items\":["
                + "{\"Tarih\":\"01-05-2026\",\"TP_TRD070727K10\":\"10.98700000\"},"
                + "{\"Tarih\":\"02-05-2026\",\"TP_TRD070727K10\":\"11.10000000\"}"
                + "]}";
        wm.stubFor(any(anyUrl()).willReturn(okJson(json)));

        List<EvdsSeriesPoint> points = client.fetchSeries("TP.TRD070727K10", START, END);

        assertThat(points).hasSize(2);
        assertThat(points.get(0).getDate()).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(points.get(0).getValue()).isEqualByComparingTo("10.98700000");
        assertThat(points.get(1).getDate()).isEqualTo(LocalDate.of(2026, 5, 2));
    }

    @Test
    @DisplayName("fetchSeries: 'null' string + eksik + bozuk-tarih satırları atlanır")
    void fetchSeries_skipsNullBlankAndBadRows() {
        String json = "{\"items\":["
                + "{\"Tarih\":\"01-05-2026\",\"TP_TRD070727K10\":\"null\"},"   // null değer → atla
                + "{\"Tarih\":\"02-05-2026\",\"TP_TRD070727K10\":\"\"},"        // boş değer → atla
                + "{\"Tarih\":\"bozuk\",\"TP_TRD070727K10\":\"5.0\"},"          // bozuk tarih → atla
                + "{\"Tarih\":\"04-05-2026\",\"TP_TRD070727K10\":\"7.25\"}"     // geçerli
                + "]}";
        wm.stubFor(any(anyUrl()).willReturn(okJson(json)));

        List<EvdsSeriesPoint> points = client.fetchSeries("TP.TRD070727K10", START, END);

        assertThat(points).hasSize(1);
        assertThat(points.get(0).getDate()).isEqualTo(LocalDate.of(2026, 5, 4));
        assertThat(points.get(0).getValue()).isEqualByComparingTo("7.25");
    }

    @Test
    @DisplayName("fetchSeries: items var ama beklenen field yok → boş liste (sessiz hata)")
    void fetchSeries_itemsPresentButWrongField() {
        String json = "{\"items\":[{\"Tarih\":\"01-05-2026\",\"WRONG_FIELD\":\"1.0\"}]}";
        wm.stubFor(any(anyUrl()).willReturn(okJson(json)));

        assertThat(client.fetchSeries("TP.TRD070727K10", START, END)).isEmpty();
    }

    @Test
    @DisplayName("fetchSeries: 'items' alanı yok → boş liste")
    void fetchSeries_itemsMissing() {
        wm.stubFor(any(anyUrl()).willReturn(okJson("{\"totalCount\":0}")));
        assertThat(client.fetchSeries("TP.TRD070727K10", START, END)).isEmpty();
    }

    @Test
    @DisplayName("fetchSeries: bozuk JSON → boş liste (parse hatası yakalanır)")
    void fetchSeries_malformedJson() {
        wm.stubFor(any(anyUrl()).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{ this is not json")));
        assertThat(client.fetchSeries("TP.TRD070727K10", START, END)).isEmpty();
    }

    @Test
    @DisplayName("fetchSeries: boş gövde → boş liste (executeGet null döner)")
    void fetchSeries_emptyBody() {
        wm.stubFor(any(anyUrl()).willReturn(aResponse().withStatus(200)));
        assertThat(client.fetchSeries("TP.TRD070727K10", START, END)).isEmpty();
    }

    @Test
    @DisplayName("fetchSeries: HTTP 500 → boş liste (sunucu hatası yakalanır)")
    void fetchSeries_serverError() {
        wm.stubFor(any(anyUrl()).willReturn(aResponse().withStatus(500)));
        assertThat(client.fetchSeries("TP.TRD070727K10", START, END)).isEmpty();
    }

    @Test
    @DisplayName("fetchSeries: HTTP 404 → boş liste (client hatası yakalanır)")
    void fetchSeries_notFound() {
        wm.stubFor(any(anyUrl()).willReturn(aResponse().withStatus(404)));
        assertThat(client.fetchSeries("TP.TRD070727K10", START, END)).isEmpty();
    }

    // ── fetchIndicatorValues / fetchCouponRates delegations ───────────────────

    @Test
    @DisplayName("fetchIndicatorValues: TP. prefix'li seriyi çeker (Değer serisi)")
    void fetchIndicatorValues_ok() {
        String json = "{\"items\":[{\"Tarih\":\"01-05-2026\",\"TP_TRD070727K10\":\"10.98\"}]}";
        wm.stubFor(any(anyUrl()).willReturn(okJson(json)));

        List<EvdsSeriesPoint> points = client.fetchIndicatorValues("TRD070727K10", START, END);

        assertThat(points).hasSize(1);
        assertThat(points.get(0).getValue()).isEqualByComparingTo("10.98");
    }

    @Test
    @DisplayName("fetchCouponRates: .ORAN suffix → field TP_..._ORAN olarak parse edilir")
    void fetchCouponRates_ok() {
        // seriesCode = TP.TRD070727K10.ORAN → fieldName = TP_TRD070727K10_ORAN
        String json = "{\"items\":[{\"Tarih\":\"01-05-2026\",\"TP_TRD070727K10_ORAN\":\"4.50\"}]}";
        wm.stubFor(any(anyUrl()).willReturn(okJson(json)));

        List<EvdsSeriesPoint> points = client.fetchCouponRates("TRD070727K10", START, END);

        assertThat(points).hasSize(1);
        assertThat(points.get(0).getValue()).isEqualByComparingTo("4.50");
    }

    // ── fetchSeriesList / fetchBondSeriesList ─────────────────────────────────

    @Test
    @DisplayName("fetchBondSeriesList: geçerli JSON array → meta liste parse edilir")
    void fetchBondSeriesList_ok() {
        String json = "["
                + "{\"SERIE_CODE\":\"TP.TRD070727K10\",\"DATAGROUP_CODE\":\"bie_pydibs\","
                + "\"SERIE_NAME\":\"TRD070727K10 ( 07.01.2026 07.07.2027 ) Deger\","
                + "\"SERIE_NAME_ENG\":\"TRD070727K10 Value\",\"FREQUENCY_STR\":\"GUNLUK\","
                + "\"START_DATE\":\"07-01-2026\",\"END_DATE\":\"04-05-2026\"},"
                + "{\"SERIE_CODE\":\"TP.TRD070727K10.ORAN\",\"DATAGROUP_CODE\":\"bie_pydibs\","
                + "\"SERIE_NAME\":\"Kupon\",\"START_DATE\":\"bad-date\",\"END_DATE\":\"\"}"
                + "]";
        wm.stubFor(any(anyUrl()).willReturn(okJson(json)));

        List<EvdsSeriesInfo> series = client.fetchBondSeriesList();

        assertThat(series).hasSize(2);
        assertThat(series.get(0).getSeriesCode()).isEqualTo("TP.TRD070727K10");
        assertThat(series.get(0).getDatagroupCode()).isEqualTo("bie_pydibs");
        assertThat(series.get(0).getStartDate()).isEqualTo(LocalDate.of(2026, 1, 7));
        assertThat(series.get(0).getEndDate()).isEqualTo(LocalDate.of(2026, 5, 4));
        // ikinci satır: bozuk/boş tarih → null (parseEvdsDate)
        assertThat(series.get(1).getSeriesCode()).isEqualTo("TP.TRD070727K10.ORAN");
        assertThat(series.get(1).getStartDate()).isNull();
        assertThat(series.get(1).getEndDate()).isNull();
    }

    @Test
    @DisplayName("fetchSeriesList: SERIE_CODE eksik satır atlanır → boş liste (sessiz hata)")
    void fetchSeriesList_missingSerieCodeSkipped() {
        String json = "[{\"DATAGROUP_CODE\":\"bie_pydibs\",\"SERIE_NAME\":\"x\"}]";
        wm.stubFor(any(anyUrl()).willReturn(okJson(json)));

        assertThat(client.fetchSeriesList("bie_pydibs")).isEmpty();
    }

    @Test
    @DisplayName("fetchSeriesList: response array değil → boş liste")
    void fetchSeriesList_notArray() {
        wm.stubFor(any(anyUrl()).willReturn(okJson("{\"error\":\"not-an-array\"}")));
        assertThat(client.fetchSeriesList("bie_pydibs")).isEmpty();
    }

    @Test
    @DisplayName("fetchSeriesList: HTTP 500 → boş liste")
    void fetchSeriesList_serverError() {
        wm.stubFor(any(anyUrl()).willReturn(aResponse().withStatus(500)));
        assertThat(client.fetchSeriesList("bie_pydibs")).isEmpty();
    }
}
