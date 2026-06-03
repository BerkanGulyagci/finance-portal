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
 * {@link RasyonetFundClient} — ek branch kapsamı. {@code RasyonetFundClientTest}'in atladığı
 * kenar dalları (null/blank sourceCode, JSON node {@code null} alanları, boş diziler, eksik
 * Status/Error anahtarları, NaN/parse-edilemeyen değerler, helper false-arm'ları) hedeflenir.
 *
 * Aynı seam: client URL'leri {@link RasyonetProperties}'ten okunur, WireMock baseUrl set edilir,
 * gerçek {@link RestTemplate} ile HTTP yolu çalışır. Entegrasyon log servisi mock'tur.
 * Her test {@link BeforeEach}'te taze client alır (rate-limit/sleep kapısı yoktur ama izole kalır).
 */
class RasyonetFundClientMoreTest {

    private static final String FILTER_PATH  = "/web-fund/fund-filter";
    private static final String CARD_PATH    = "/web-fund/card";
    private static final String OSMANLI_PATH = "/web-menu/osmanli-fund-bulletin";

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

    // ── fetchFundsBySourceCode kenar dalları ──────────────────────────────────

    @Test
    @DisplayName("fetchFundsBySourceCode(null): resolveFundCategory null dalı → INVESTMENT_FUND")
    void fetchFunds_nullSourceCode_categoryDefault() {
        // sourceCode null → buildFilterPayload null SourceCode koyar, resolveFundCategory(null) erken döner.
        wm.stubFor(post(urlPathEqualTo(FILTER_PATH))
                .willReturn(okJson("{\"Data\":{\"Funds\":[{\"Code\":\"NUL\"}]}}")));

        List<RasyonetFundDto> result = client.fetchFundsBySourceCode(null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFundCategory()).isEqualTo("INVESTMENT_FUND");
    }

    @Test
    @DisplayName("fetchFundsBySourceCode: TAF → AUTO_ENROLLMENT_FUND (switch TAF dalı)")
    void fetchFunds_autoEnrollmentCategory() {
        wm.stubFor(post(urlPathEqualTo(FILTER_PATH))
                .willReturn(okJson("{\"Data\":{\"Funds\":[{\"Code\":\"AEF\"}]}}")));

        List<RasyonetFundDto> result = client.fetchFundsBySourceCode("TAF");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFundCategory()).isEqualTo("AUTO_ENROLLMENT_FUND");
    }

    @Test
    @DisplayName("fetchFundsBySourceCode: hiç Data anahtarı yok → boş liste (!has(\"Data\") dalı)")
    void fetchFunds_noDataKey() {
        wm.stubFor(post(urlPathEqualTo(FILTER_PATH))
                .willReturn(okJson("{\"Foo\":1}")));

        assertThat(client.fetchFundsBySourceCode("TMF")).isEmpty();
    }

    @Test
    @DisplayName("fetchFundsBySourceCode: Funds içindeki Manager/Founder/Source JSON null → null-arm'lar")
    void fetchFunds_nullNestedObjects() {
        // Manager/Founder/Source/Type anahtarları VAR ama değerleri JSON null → isNull() true dalları
        String json = "{\"Data\":{\"Funds\":[{"
                + "\"Code\":\"NST\",\"Name\":\"NestNull\","
                + "\"Manager\":null,\"Founder\":null,\"Source\":null,\"Type\":null"
                + "}]}}";
        wm.stubFor(post(urlPathEqualTo(FILTER_PATH)).willReturn(okJson(json)));

        List<RasyonetFundDto> result = client.fetchFundsBySourceCode("TMF");

        assertThat(result).hasSize(1);
        RasyonetFundDto dto = result.get(0);
        assertThat(dto.getCode()).isEqualTo("NST");
        assertThat(dto.getManagerName()).isNull();
        assertThat(dto.getFounderName()).isNull();
        assertThat(dto.getSourceCode()).isNull();
        assertThat(dto.getFundType()).isNull();
    }

    @Test
    @DisplayName("fetchFundsBySourceCode: bozuk JSON + null sourceCode → publishParseFailed null-arm")
    void fetchFunds_malformedNullSource() {
        // JsonProcessing(parse) hatası + sourceCode null → publishParseFailed(op, null):
        //   Map.of("sourceCode", symbolOrSource != null ? ... : "")  false-arm.
        wm.stubFor(post(urlPathEqualTo(FILTER_PATH))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{not-json")));

        assertThat(client.fetchFundsBySourceCode(null)).isEmpty();
    }

    // ── fetchOsmanliFundBulletin kenar dalları ────────────────────────────────

    @Test
    @DisplayName("fetchOsmanliFundBulletin: boş gövde → boş liste (bytes.length==0 dalı)")
    void fetchOsmanli_emptyBody() {
        wm.stubFor(get(urlPathEqualTo(OSMANLI_PATH))
                .willReturn(aResponse().withStatus(200).withBody(new byte[0])));

        assertThat(client.fetchOsmanliFundBulletin()).isEmpty();
    }

    @Test
    @DisplayName("fetchOsmanliFundBulletin: Data yok, Status/Error de yok → \"N/A\" false-arm'lar")
    void fetchOsmanli_noDataNoStatusNoError() {
        // !root.has("Data") true + has("Status")/has("Error") false → ternary N/A dalları
        wm.stubFor(get(urlPathEqualTo(OSMANLI_PATH))
                .willReturn(okJson("{\"Foo\":1}")));

        assertThat(client.fetchOsmanliFundBulletin()).isEmpty();
    }

    @Test
    @DisplayName("fetchOsmanliFundBulletin: RiskLevel sayısal değil → parse catch yutulur")
    void fetchOsmanli_nonNumericRiskLevel() {
        // RiskLevel "abc" → Integer.parseInt fırlatır, catch(ignored) → riskLevel set edilmez
        String json = "{\"Data\":[{\"Code\":\"BAD\",\"Name\":\"BadRisk\",\"RiskLevel\":\"abc\"}]}";
        wm.stubFor(get(urlPathEqualTo(OSMANLI_PATH)).willReturn(okJson(json)));

        List<RasyonetOsmanliFundBulletinDto> result = client.fetchOsmanliFundBulletin();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCode()).isEqualTo("BAD");
        assertThat(result.get(0).getRiskLevel()).isNull();
    }

    // ── fetchFundDetailRich: sourceCode null/blank fallback ────────────────────

    @Test
    @DisplayName("fetchFundDetailRich(code, null): sourceCode null → props.sourceCode fallback")
    void fetchDetail_nullSourceCode_fallback() {
        wm.stubFor(get(urlPathEqualTo(CARD_PATH))
                .willReturn(okJson("{\"Data\":{\"Code\":\"AAA\",\"Name\":\"Fon\"}}")));

        RasyonetFundDetailDto dto = client.fetchFundDetailRich("aaa", null);

        assertThat(dto).isNotNull();
        assertThat(dto.getCode()).isEqualTo("AAA");
    }

    @Test
    @DisplayName("fetchFundDetailRich(code, \"  \"): sourceCode blank → props.sourceCode fallback")
    void fetchDetail_blankSourceCode_fallback() {
        wm.stubFor(get(urlPathEqualTo(CARD_PATH))
                .willReturn(okJson("{\"Data\":{\"Code\":\"BBB\",\"Name\":\"Fon\"}}")));

        RasyonetFundDetailDto dto = client.fetchFundDetailRich("bbb", "  ");

        assertThat(dto).isNotNull();
        assertThat(dto.getCode()).isEqualTo("BBB");
    }

    @Test
    @DisplayName("fetchFundDetailRich: Data anahtarı hiç yok, Status/Error de yok → null + N/A false-arm")
    void fetchDetail_noDataNoStatusNoError() {
        // !root.has("Data") true + has("Status")/has("Error") false (L278/L279/L280 dalları)
        wm.stubFor(get(urlPathEqualTo(CARD_PATH))
                .willReturn(okJson("{\"Foo\":1}")));

        assertThat(client.fetchFundDetailRich("aaa")).isNull();
    }

    // ── mapCardNodeRich: JSON null nested + boş diziler + NaN ──────────────────

    @Test
    @DisplayName("mapCardNodeRich: Manager/Founder/Source/Type JSON null → null-arm'lar")
    void fetchDetail_nestedNullObjects() {
        String json = "{\"Data\":{"
                + "\"Code\":\"CCC\",\"Name\":\"NestNull\","
                + "\"Manager\":null,\"Founder\":null,\"Source\":null,\"Type\":null"
                + "}}";
        wm.stubFor(get(urlPathEqualTo(CARD_PATH)).willReturn(okJson(json)));

        RasyonetFundDetailDto dto = client.fetchFundDetailRich("ccc");

        assertThat(dto).isNotNull();
        assertThat(dto.getCode()).isEqualTo("CCC");
        assertThat(dto.getManagerName()).isNull();
        assertThat(dto.getFounderName()).isNull();
        assertThat(dto.getCurrencyCode()).isNull();
        assertThat(dto.getFundType()).isNull();
    }

    @Test
    @DisplayName("mapCardNodeRich: priceHistory boş dizi + AssetAllocation boş dizi → set edilmez")
    void fetchDetail_emptyHistoryAndAlloc() {
        // LastYearReturnPrice.Fund = [] → isArray() true ama size()==0 false-arm
        // AssetAllocation = [] → aynı false-arm
        String json = "{\"Data\":{"
                + "\"Code\":\"EMP\",\"Name\":\"EmptyArrays\","
                + "\"LastYearReturnPrice\":{\"Fund\":[]},"
                + "\"AssetAllocation\":[]"
                + "}}";
        wm.stubFor(get(urlPathEqualTo(CARD_PATH)).willReturn(okJson(json)));

        RasyonetFundDetailDto dto = client.fetchFundDetailRich("emp");

        assertThat(dto).isNotNull();
        assertThat(dto.getPriceHistory()).isNull();
        assertThat(dto.getAssetAllocation()).isNull();
    }

    @Test
    @DisplayName("mapCardNodeRich: priceHistory kısa tarih + eksik Date/Value → dal kombinasyonları")
    void fetchDetail_priceHistoryEdgeItems() {
        // 1) Date var (kısa, <10) + Value var → shortDate else-dalı (dateStr kullanılır)
        // 2) Date yok, Value var → dateStr==null, item atlanır
        // 3) Date var, Value yok → val==null, item atlanır
        String json = "{\"Data\":{"
                + "\"Code\":\"PHE\",\"Name\":\"PriceHist\","
                + "\"LastYearReturnPrice\":{\"Fund\":["
                + "{\"Date\":\"2025\",\"Value\":\"9.5\"},"
                + "{\"Value\":\"10\"},"
                + "{\"Date\":\"2025-03-03T00:00:00\"}"
                + "]}"
                + "}}";
        wm.stubFor(get(urlPathEqualTo(CARD_PATH)).willReturn(okJson(json)));

        RasyonetFundDetailDto dto = client.fetchFundDetailRich("phe");

        assertThat(dto).isNotNull();
        assertThat(dto.getPriceHistory()).hasSize(1);
        assertThat(dto.getPriceHistory().get(0).getDate()).isEqualTo("2025"); // kısa tarih korunur
        assertThat(dto.getPriceHistory().get(0).getPrice()).isEqualByComparingTo("9.5");
    }

    @Test
    @DisplayName("mapCardNodeRich: AssetAllocation eksik Name/Percentage → tüm item geçersiz, set edilmez")
    void fetchDetail_assetAllocAllInvalid() {
        // {Name yok} ve {Percentage yok} → her ikisi de aName/aPct null → items boş → set edilmez
        String json = "{\"Data\":{"
                + "\"Code\":\"AAI\",\"Name\":\"AllocInvalid\","
                + "\"AssetAllocation\":[{\"Percentage\":\"60\"},{\"Name\":\"Hisse\"}]"
                + "}}";
        wm.stubFor(get(urlPathEqualTo(CARD_PATH)).willReturn(okJson(json)));

        RasyonetFundDetailDto dto = client.fetchFundDetailRich("aai");

        assertThat(dto).isNotNull();
        assertThat(dto.getAssetAllocation()).isNull();
    }

    @Test
    @DisplayName("mapCardNodeRich: MountlyPerformance Return anahtarı yok → blok atlanır")
    void fetchDetail_monthlyNoReturnKey() {
        // MountlyPerformance present, not null, ama "Return" yok → monthly.has("Return") false
        String json = "{\"Data\":{"
                + "\"Code\":\"MNR\",\"Name\":\"NoReturn\","
                + "\"MountlyPerformance\":{\"Name\":\"x\"}"
                + "}}";
        wm.stubFor(get(urlPathEqualTo(CARD_PATH)).willReturn(okJson(json)));

        RasyonetFundDetailDto dto = client.fetchFundDetailRich("mnr");

        assertThat(dto).isNotNull();
        assertThat(dto.getMonthlyReturns()).isNull();
    }

    @Test
    @DisplayName("mapCardNodeRich: MountlyPerformance.Return obje (dizi değil) → işlenmez")
    void fetchDetail_monthlyReturnNotArray() {
        // Return present ama isArray() false → iç blok atlanır
        String json = "{\"Data\":{"
                + "\"Code\":\"MNA\",\"Name\":\"ReturnObj\","
                + "\"MountlyPerformance\":{\"Return\":{\"k\":\"v\"}}"
                + "}}";
        wm.stubFor(get(urlPathEqualTo(CARD_PATH)).willReturn(okJson(json)));

        RasyonetFundDetailDto dto = client.fetchFundDetailRich("mna");

        assertThat(dto).isNotNull();
        assertThat(dto.getMonthlyReturns()).isNull();
    }

    @Test
    @DisplayName("mapCardNodeRich: aylık Year yok / inner Return null / ay null / hepsi NaN → set edilmez")
    void fetchDetail_monthlyEdgeAllSkipped() {
        // yearData #1: Year yok → continue (yr==null)
        // yearData #2: Year var ama inner Return null → continue (returnNode null)
        // yearData #3: Year var, Return var ama: bir ay JSON null, bir ay "NaN", bir ay "" → hepsi atlanır
        // → monthlyList boş → setMonthlyReturns çağrılmaz
        String json = "{\"Data\":{"
                + "\"Code\":\"MEG\",\"Name\":\"MonthlyEdge\","
                + "\"MountlyPerformance\":{\"Return\":["
                + "{\"Return\":{\"Ocak\":\"1.1\"}},"                       // Year yok → atlanır
                + "{\"Year\":\"2024\",\"Return\":null},"                   // inner Return null → atlanır
                + "{\"Year\":\"2025\",\"Return\":{\"Ocak\":null,\"Subat\":\"NaN\",\"Mart\":\"\"}}"
                + "]}"
                + "}}";
        wm.stubFor(get(urlPathEqualTo(CARD_PATH)).willReturn(okJson(json)));

        RasyonetFundDetailDto dto = client.fetchFundDetailRich("meg");

        assertThat(dto).isNotNull();
        assertThat(dto.getMonthlyReturns()).isNull();
    }

    @Test
    @DisplayName("mapCardNodeRich: returnValue period yok / null / NaN-düz-sayı dalları")
    void fetchDetail_returnValueEdges() {
        // Performance.Fund.Return:
        //   OneDay: yok (returnNode.has(period) false)
        //   OneWeek: null (periodNode.isNull true)
        //   OneMonth: düz string "NaN" (Value yok → asText NaN dalı → null)
        //   OneYear: düz sayı "12.5" (Value yok → asText parse → değer)
        String json = "{\"Data\":{"
                + "\"Code\":\"RVE\",\"Name\":\"RetVal\","
                + "\"Performance\":{\"Fund\":{\"Return\":{"
                + "\"OneWeek\":null,"
                + "\"OneMonth\":\"NaN\","
                + "\"OneYear\":\"12.5\""
                + "}}}"
                + "}}";
        wm.stubFor(get(urlPathEqualTo(CARD_PATH)).willReturn(okJson(json)));

        RasyonetFundDetailDto dto = client.fetchFundDetailRich("rve");

        assertThat(dto).isNotNull();
        assertThat(dto.getReturnOneDay()).isNull();   // period yok
        assertThat(dto.getReturnOneWeek()).isNull();  // null node
        assertThat(dto.getReturnOneMonth()).isNull(); // "NaN" düz sayı
        assertThat(dto.getReturnOneYear()).isEqualByComparingTo("12.5"); // düz sayı parse
    }

    @Test
    @DisplayName("mapCardNodeRich: text/intVal/decimal helper'ları — blank, null-node, geçersiz sayı")
    void fetchDetail_helperFalseArms() {
        // Name: "   " (blank) → text blank-arm → null
        // RiskLevel: JSON null → intVal isNull() true-arm → null
        // Price: "abc" (BigDecimal parse hatası → catch) → null (decimal catch arm)
        // PriceUSD: "NaN" → decimal NaN-arm → null
        // Mcap: yok (decimal !has(field) arm) → null
        String json = "{\"Data\":{"
                + "\"Code\":\"HLP\","
                + "\"Name\":\"   \","
                + "\"RiskLevel\":null,"
                + "\"Price\":\"abc\","
                + "\"PriceUSD\":\"NaN\""
                + "}}";
        wm.stubFor(get(urlPathEqualTo(CARD_PATH)).willReturn(okJson(json)));

        RasyonetFundDetailDto dto = client.fetchFundDetailRich("hlp");

        assertThat(dto).isNotNull();
        assertThat(dto.getCode()).isEqualTo("HLP"); // Code geldi
        assertThat(dto.getName()).isNull();         // blank → null
        assertThat(dto.getRiskLevel()).isNull();    // JSON null → null
        assertThat(dto.getPrice()).isNull();        // BigDecimal parse hatası → null
        assertThat(dto.getPriceUsd()).isNull();     // NaN → null
        assertThat(dto.getMarketCap()).isNull();    // alan yok → null
    }
}
