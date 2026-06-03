package com.finance.portal.market.infrastructure.external.rasyonet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.portal.common.application.logging.CentralIntegrationLogService;
import com.finance.portal.market.application.funds.model.RasyonetFundDetailDto;
import com.finance.portal.market.application.funds.model.RasyonetFundDto;
import com.finance.portal.market.application.funds.model.RasyonetOsmanliFundBulletinDto;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * {@link RasyonetFundClient} testleri — WireMock ile gerçek HTTP yolu (RestTemplate) stub'lanır.
 *
 * Seam: client URL'leri {@link RasyonetProperties}'ten okur; props'a WireMock baseUrl'i set ederek
 * fund-filter (POST), card (GET), osmanli-bulletin (GET) uçlarını yakalarız. İntegrasyon log servisi
 * mock (publish no-op). RestTemplate 4xx/5xx'te HttpStatusCodeException atar → client catch eder ve
 * boş liste / null döner. Surefire'da koşsun diye {@code *Test} (WireMock hafif, Spring context yok).
 */
class RasyonetFundClientTest {

    private static final String FILTER_PATH   = "/web-fund/fund-filter";
    private static final String CARD_PATH     = "/web-fund/card";
    private static final String OSMANLI_PATH  = "/web-menu/osmanli-fund-bulletin";

    private static WireMockServer wm;
    private RasyonetFundClient client;
    private RasyonetProperties props;

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
        props = new RasyonetProperties();
        props.setFilterUrl(wm.baseUrl() + FILTER_PATH);
        props.setCardUrl(wm.baseUrl() + CARD_PATH);
        props.setOsmanliBulletinUrl(wm.baseUrl() + OSMANLI_PATH);
        props.setSourceCode("TMF");
        client = new RasyonetFundClient(
                new RestTemplate(),
                new ObjectMapper(),
                props,
                mock(CentralIntegrationLogService.class));
    }

    // ── fetchFundsBySourceCode / fetchFundList ────────────────────────────────

    @Test
    @DisplayName("fetchFundsBySourceCode: 200 + Data.Funds → parse edilen benzersiz fonlar")
    void fetchFunds_ok() {
        String json = "{\"Data\":{\"Funds\":["
                + "{\"Code\":\"AAA\",\"Name\":\"Fon A\",\"Price\":\"12.34\",\"RiskLevel\":3,"
                + "\"Manager\":{\"Name\":\"Mgr\"},\"Source\":{\"Code\":\"TMF\"},\"Type\":{\"Name\":\"Hisse\"},"
                + "\"Return\":{\"OneMonth\":{\"Value\":\"1.5\"},\"OneDay\":\"NaN\"},"
                + "\"Detail\":{\"Commission\":\"%2\",\"Strategy\":\"x\"}},"
                + "{\"Code\":\"AAA\",\"Name\":\"Dup\"},"          // duplicate code → tek sayılır
                + "{\"Code\":\"BBB\",\"Name\":\"Fon B\"},"
                + "{\"Name\":\"NoCode\"}"                          // code null → atlanır
                + "]}}";
        wm.stubFor(post(urlPathEqualTo(FILTER_PATH)).willReturn(okJson(json)));

        List<RasyonetFundDto> result = client.fetchFundsBySourceCode("TMF");

        assertThat(result).hasSize(2);
        RasyonetFundDto first = result.get(0);
        assertThat(first.getCode()).isEqualTo("AAA");
        assertThat(first.getName()).isEqualTo("Fon A");
        assertThat(first.getFundCategory()).isEqualTo("INVESTMENT_FUND");
        assertThat(first.getManagerName()).isEqualTo("Mgr");
        assertThat(first.getReturnOneMonth()).isNotNull();
        assertThat(first.getReturnOneDay()).isNull(); // "NaN" → null
        assertThat(result.get(1).getCode()).isEqualTo("BBB");
    }

    @Test
    @DisplayName("fetchFundsBySourceCode: TPF → PENSION_FUND kategorisi")
    void fetchFunds_pensionCategory() {
        wm.stubFor(post(urlPathEqualTo(FILTER_PATH))
                .willReturn(okJson("{\"Data\":{\"Funds\":[{\"Code\":\"CCC\"}]}}")));

        List<RasyonetFundDto> result = client.fetchFundsBySourceCode("TPF");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFundCategory()).isEqualTo("PENSION_FUND");
    }

    @Test
    @DisplayName("fetchFundsBySourceCode: Data.Funds yok → boş liste")
    void fetchFunds_missingFunds() {
        wm.stubFor(post(urlPathEqualTo(FILTER_PATH))
                .willReturn(okJson("{\"Data\":{\"Other\":1}}")));

        assertThat(client.fetchFundsBySourceCode("TMF")).isEmpty();
    }

    @Test
    @DisplayName("fetchFundsBySourceCode: boş gövde → boş liste")
    void fetchFunds_emptyBody() {
        wm.stubFor(post(urlPathEqualTo(FILTER_PATH))
                .willReturn(aResponse().withStatus(200).withBody(new byte[0])));

        assertThat(client.fetchFundsBySourceCode("TMF")).isEmpty();
    }

    @Test
    @DisplayName("fetchFundsBySourceCode: 500 → boş liste (HttpStatusCodeException yakalanır)")
    void fetchFunds_serverError() {
        wm.stubFor(post(urlPathEqualTo(FILTER_PATH)).willReturn(aResponse().withStatus(500)));

        assertThat(client.fetchFundsBySourceCode("TMF")).isEmpty();
    }

    @Test
    @DisplayName("fetchFundsBySourceCode: 429 → boş liste")
    void fetchFunds_rateLimited() {
        wm.stubFor(post(urlPathEqualTo(FILTER_PATH)).willReturn(aResponse().withStatus(429)));

        assertThat(client.fetchFundsBySourceCode("TMF")).isEmpty();
    }

    @Test
    @DisplayName("fetchFundsBySourceCode: bozuk JSON → boş liste (parse hatası yakalanır)")
    void fetchFunds_malformed() {
        wm.stubFor(post(urlPathEqualTo(FILTER_PATH))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{not-json")));

        assertThat(client.fetchFundsBySourceCode("TMF")).isEmpty();
    }

    @Test
    @DisplayName("fetchFundList: props.sourceCode'a delege eder")
    void fetchFundList_delegates() {
        wm.stubFor(post(urlPathEqualTo(FILTER_PATH))
                .willReturn(okJson("{\"Data\":{\"Funds\":[{\"Code\":\"DDD\"}]}}")));

        List<RasyonetFundDto> result = client.fetchFundList();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFundCategory()).isEqualTo("INVESTMENT_FUND");
    }

    // ── fetchOsmanliFundBulletin ──────────────────────────────────────────────

    @Test
    @DisplayName("fetchOsmanliFundBulletin: 200 + Data dizisi → parse edilen liste")
    void fetchOsmanli_ok() {
        String json = "{\"Status\":\"Ok\",\"Error\":null,\"Data\":["
                + "{\"Code\":\"OSM\",\"Name\":\"Osmanli Fon\",\"RiskLevel\":\"4\","
                + "\"Cosmission\":\"%1\",\"Kistas\":\"BIST\",\"Mcap\":\"1000\",\"DailyReturn\":\"0.5\"},"
                + "{\"Code\":\"OS2\",\"Name\":\"Ikinci\"}"
                + "]}";
        wm.stubFor(get(urlPathEqualTo(OSMANLI_PATH)).willReturn(okJson(json)));

        List<RasyonetOsmanliFundBulletinDto> result = client.fetchOsmanliFundBulletin();

        assertThat(result).hasSize(2);
        RasyonetOsmanliFundBulletinDto first = result.get(0);
        assertThat(first.getCode()).isEqualTo("OSM");
        assertThat(first.getRiskLevel()).isEqualTo(4);
        assertThat(first.getCommission()).isEqualTo("%1");   // Cosmission → commission
        assertThat(first.getBenchmark()).isEqualTo("BIST");  // Kistas → benchmark
        assertThat(first.getMarketCap()).isNotNull();
    }

    @Test
    @DisplayName("fetchOsmanliFundBulletin: Data null → boş liste")
    void fetchOsmanli_dataNull() {
        wm.stubFor(get(urlPathEqualTo(OSMANLI_PATH))
                .willReturn(okJson("{\"Status\":\"Err\",\"Error\":\"x\",\"Data\":null}")));

        assertThat(client.fetchOsmanliFundBulletin()).isEmpty();
    }

    @Test
    @DisplayName("fetchOsmanliFundBulletin: Data dizi değil → boş liste")
    void fetchOsmanli_dataNotArray() {
        wm.stubFor(get(urlPathEqualTo(OSMANLI_PATH))
                .willReturn(okJson("{\"Data\":{\"k\":\"v\"}}")));

        assertThat(client.fetchOsmanliFundBulletin()).isEmpty();
    }

    @Test
    @DisplayName("fetchOsmanliFundBulletin: 500 → boş liste")
    void fetchOsmanli_serverError() {
        wm.stubFor(get(urlPathEqualTo(OSMANLI_PATH)).willReturn(aResponse().withStatus(500)));

        assertThat(client.fetchOsmanliFundBulletin()).isEmpty();
    }

    // ── fetchFundDetailRich ───────────────────────────────────────────────────

    @Test
    @DisplayName("fetchFundDetailRich: 200 + zengin Data → tüm alanlar map edilir")
    void fetchDetail_ok() {
        String json = "{\"Status\":\"Ok\",\"Data\":{"
                + "\"Code\":\"AAA\",\"Name\":\"Fon A\",\"Price\":\"15.50\",\"PriceUSD\":\"0.50\","
                + "\"RiskLevel\":5,\"BenchmarkName\":\"BIST100\",\"KapLink\":\"http://kap\","
                + "\"Manager\":{\"Name\":\"Mgr\"},\"Founder\":{\"Name\":\"Fnd\"},"
                + "\"Source\":{\"Code\":\"TRY\"},\"Type\":{\"Name\":\"Hisse\"},"
                + "\"Tag\":{\"Strategy\":\"strat\",\"Commission\":\"%2\",\"ManagementFeeAnnual\":\"%1\",\"MinimumQuantitySales\":\"1\"},"
                + "\"Settlement\":{\"Buy\":\"T+1\",\"Sell\":\"T+2\"},"
                + "\"Performance\":{\"Fund\":{\"Return\":{\"OneMonth\":{\"Value\":\"2.5\"},\"OneYear\":{\"Value\":\"30\"}}}},"
                + "\"Risk\":{\"Fund\":{\"Best\":\"5\",\"Worst\":\"-3\",\"PositiveRateofReturn\":\"60\"}},"
                + "\"LastYearReturnPrice\":{\"Fund\":[{\"Date\":\"2025-01-01T00:00:00\",\"Value\":\"10\"},{\"Date\":\"2025-01-02T00:00:00\",\"Value\":\"11\"}]},"
                + "\"MountlyPerformance\":{\"Name\":\"m\",\"Return\":[{\"Year\":\"2025\",\"Return\":{\"Ocak\":\"1.1\",\"Subat\":\"NaN\",\"Mart\":\"2.2\"}}]},"
                + "\"AssetAllocation\":[{\"Name\":\"Hisse\",\"Percentage\":\"60\"},{\"Name\":\"Tahvil\",\"Percentage\":\"40\"}]"
                + "}}";
        wm.stubFor(get(urlPathEqualTo(CARD_PATH)).willReturn(okJson(json)));

        RasyonetFundDetailDto dto = client.fetchFundDetailRich("aaa");

        assertThat(dto).isNotNull();
        assertThat(dto.getCode()).isEqualTo("AAA");
        assertThat(dto.getName()).isEqualTo("Fon A");
        assertThat(dto.getPrice()).isNotNull();
        assertThat(dto.getManagerName()).isEqualTo("Mgr");
        assertThat(dto.getFounderName()).isEqualTo("Fnd");
        assertThat(dto.getCurrencyCode()).isEqualTo("TRY");
        assertThat(dto.getFundType()).isEqualTo("Hisse");
        assertThat(dto.getStrategy()).isEqualTo("strat");
        assertThat(dto.getBuySettlement()).isEqualTo("T+1");
        assertThat(dto.getReturnOneMonth()).isNotNull();
        assertThat(dto.getRiskBest()).isEqualTo("5");
        assertThat(dto.getPriceHistory()).hasSize(2);
        assertThat(dto.getPriceHistory().get(0).getDate()).isEqualTo("2025-01-01");
        assertThat(dto.getMonthlyReturns()).hasSize(2); // Ocak + Mart (Subat NaN atlanır)
        assertThat(dto.getAssetAllocation()).hasSize(2);
    }

    @Test
    @DisplayName("fetchFundDetailRich(code, sourceCode): 200 minimal Data → Code fallback olarak param kullanılır")
    void fetchDetail_okWithSourceCode_codeFallback() {
        wm.stubFor(get(urlPathEqualTo(CARD_PATH))
                .willReturn(okJson("{\"Data\":{\"Name\":\"Sadece Isim\"}}")));

        RasyonetFundDetailDto dto = client.fetchFundDetailRich("xyz", "TPF");

        assertThat(dto).isNotNull();
        assertThat(dto.getCode()).isEqualTo("XYZ"); // Data.Code yok → upperCode fallback
        assertThat(dto.getName()).isEqualTo("Sadece Isim");
    }

    @Test
    @DisplayName("fetchFundDetailRich: Data null/yok → null")
    void fetchDetail_dataMissing() {
        wm.stubFor(get(urlPathEqualTo(CARD_PATH))
                .willReturn(okJson("{\"Status\":\"NotFound\",\"Error\":\"yok\",\"Data\":null}")));

        assertThat(client.fetchFundDetailRich("aaa")).isNull();
    }

    @Test
    @DisplayName("fetchFundDetailRich: boş gövde → null")
    void fetchDetail_emptyBody() {
        wm.stubFor(get(urlPathEqualTo(CARD_PATH))
                .willReturn(aResponse().withStatus(200).withBody(new byte[0])));

        assertThat(client.fetchFundDetailRich("aaa")).isNull();
    }

    @Test
    @DisplayName("fetchFundDetailRich: 500 → null")
    void fetchDetail_serverError() {
        wm.stubFor(get(urlPathEqualTo(CARD_PATH)).willReturn(aResponse().withStatus(500)));

        assertThat(client.fetchFundDetailRich("aaa")).isNull();
    }

    @Test
    @DisplayName("fetchFundDetailRich: bozuk JSON → null (parse hatası yakalanır)")
    void fetchDetail_malformed() {
        wm.stubFor(get(urlPathEqualTo(CARD_PATH))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{broken")));

        assertThat(client.fetchFundDetailRich("aaa")).isNull();
    }
}
