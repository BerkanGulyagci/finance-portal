package com.finance.portal.market.infrastructure.external.bond.businessinsider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.portal.common.application.logging.CentralIntegrationLogService;
import com.finance.portal.market.application.bond.eurobond.model.EurobondChartPoint;
import com.finance.portal.market.application.bond.eurobond.model.EurobondDetail;
import com.finance.portal.market.application.bond.eurobond.model.EurobondRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link BusinessInsiderBondClient} testleri.
 *
 * <p>Bu istemcinin temel URL'i sabit ({@code https://markets.businessinsider.com}) olduğundan
 * WireMock ile yönlendirilemez; bunun yerine constructor'a verilen {@link RestTemplate} mock'lanıp
 * {@code exchange(url, GET, HttpEntity, String.class)} dönüşü stub'lanır. Böylece suggest-regex
 * çözümü (resolve), Jsoup tablo + regex parse (fetchDetail), JSON OHLC parse (fetchChart) ve
 * ban-korumalı GET'in tüm hata dalları (403/429 degrade, diğer HTTP hatası, genel istisna,
 * boş/null gövde) kapsanır. Entegrasyon log servisi mock (publish no-op).</p>
 */
class BusinessInsiderBondClientTest {

    private RestTemplate restTemplate;
    private ObjectMapper objectMapper;
    private CentralIntegrationLogService integrationLog;
    private BusinessInsiderBondClient client;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        objectMapper = new ObjectMapper();
        integrationLog = mock(CentralIntegrationLogService.class);
        client = new BusinessInsiderBondClient(restTemplate, objectMapper, integrationLog);
    }

    /** {@code restTemplate.exchange(...)} için bir 200 OK String gövdesi stub'lar. */
    private void stubBody(String body) {
        ResponseEntity<String> resp = new ResponseEntity<>(body, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(resp);
    }

    // ── resolve ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("resolve: null/boş ISIN → Optional.empty (HTTP'ye hiç gidilmez)")
    void resolve_nullOrBlank() {
        assertThat(client.resolve(null)).isEmpty();
        assertThat(client.resolve("   ")).isEmpty();
    }

    @Test
    @DisplayName("resolve: suggest gövdesinde eşleşen ISIN → EurobondRef alanları çözülür")
    void resolve_ok() {
        // SUGGEST regex: "slug|ISIN(12)|issuer|name|instrumentId"
        String body = "{\"results\":[\"some-bond-slug|XS1234567890|IssuerCo|Full Bond Name|98765\"]}";
        stubBody(body);

        Optional<EurobondRef> ref = client.resolve("xs1234567890");

        assertThat(ref).isPresent();
        EurobondRef r = ref.get();
        assertThat(r.isin()).isEqualTo("XS1234567890");
        assertThat(r.slug()).isEqualTo("some-bond-slug");
        assertThat(r.name()).isEqualTo("Full Bond Name");
        assertThat(r.issuer()).isEqualTo("IssuerCo");
        assertThat(r.instrumentId()).isEqualTo(98765L);
    }

    @Test
    @DisplayName("resolve: gövdede eşleşen ISIN yok → Optional.empty")
    void resolve_noMatch() {
        stubBody("{\"results\":[\"other-slug|XS9999999999|IssuerCo|Other Name|11111\"]}");
        assertThat(client.resolve("XS1234567890")).isEmpty();
    }

    @Test
    @DisplayName("resolve: gövdesi null yanıt → Optional.empty (body == null dalı)")
    void resolve_nullBody() {
        ResponseEntity<String> resp = new ResponseEntity<>((String) null, HttpStatus.NO_CONTENT);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(resp);
        assertThat(client.resolve("XS1234567890")).isEmpty();
    }

    // ── fetchDetail ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("fetchDetail: null ref veya slug'ı null → Optional.empty")
    void fetchDetail_nullRef() {
        assertThat(client.fetchDetail(null)).isEmpty();
        assertThat(client.fetchDetail(new EurobondRef("XS1234567890", null, "n", "i", 5L))).isEmpty();
    }

    @Test
    @DisplayName("fetchDetail: tablo + fiyat/değişim/tkData/InstrumentID parse edilir")
    void fetchDetail_ok() {
        String html = "<html><body>"
                + "<table>"
                + "<tr><td>Country</td><td>Germany</td></tr>"
                + "<tr><td>Issuer</td><td>Deutschland</td></tr>"
                + "<tr><td>Currency</td><td>EUR</td></tr>"
                + "<tr><td>Coupon</td><td>5.200%</td></tr>"
                + "<tr><td>Maturity</td><td>8/17/2031</td></tr>"
                + "<tr><td>Denomination</td><td>1000</td></tr>"
                + "</table>"
                + "<span class=\"price-section__current-value\">101,37</span>"
                + "<span class=\"price-section__relative-value\">(+0,25%</span>"
                + "<script>var x = {\"tkData\":\"1,627799832,1330,333\"};</script>"
                + "<script>{\"InstrumentID\": 424242}</script>"
                + "</body></html>";
        stubBody(html);

        EurobondRef ref = new EurobondRef("XS1234567890", "some-slug", "Full Name", "RefIssuer", 0L);
        Optional<EurobondDetail> det = client.fetchDetail(ref);

        assertThat(det).isPresent();
        EurobondDetail d = det.get();
        assertThat(d.getIsin()).isEqualTo("XS1234567890");
        assertThat(d.getName()).isEqualTo("Full Name");
        assertThat(d.getDetailUrl()).contains("/bonds/some-slug");
        assertThat(d.getCountry()).isEqualTo("Germany");
        assertThat(d.getCurrency()).isEqualTo("EUR");
        assertThat(d.getCouponRate()).isEqualTo("5.200%");
        assertThat(d.getMaturityDate()).isEqualTo("8/17/2031");
        assertThat(d.getDenomination()).isEqualTo("1000");
        assertThat(d.getLastPrice()).isEqualByComparingTo(new BigDecimal("101.37"));
        assertThat(d.getChangePercent()).isEqualByComparingTo(new BigDecimal("0.25"));
        assertThat(d.getTkData()).isEqualTo("1,627799832,1330,333");
        // ref.instrumentId() == 0 olduğu için HTML'deki InstrumentID kullanılır.
        assertThat(d.getInstrumentId()).isEqualTo(424242L);
    }

    @Test
    @DisplayName("fetchDetail: boş gövde → Optional.empty")
    void fetchDetail_blankBody() {
        stubBody("   ");
        EurobondRef ref = new EurobondRef("XS1234567890", "some-slug", "n", "i", 1L);
        assertThat(client.fetchDetail(ref)).isEmpty();
    }

    // ── fetchChart ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("fetchChart: null/boş tkData veya null tarih → boş liste")
    void fetchChart_invalidArgs() {
        LocalDate d = LocalDate.of(2025, 1, 1);
        assertThat(client.fetchChart(null, d, d)).isEmpty();
        assertThat(client.fetchChart("  ", d, d)).isEmpty();
        assertThat(client.fetchChart("tk", null, d)).isEmpty();
        assertThat(client.fetchChart("tk", d, null)).isEmpty();
    }

    @Test
    @DisplayName("fetchChart: geçerli JSON dizisi → OHLC noktaları (tarih ilk 10 karaktere kırpılır)")
    void fetchChart_ok() {
        String json = "["
                + "{\"Date\":\"2025-01-02T00:00:00\",\"Close\":101.5,\"Open\":100.0,\"High\":102.0,\"Low\":99.5},"
                + "{\"Date\":\"2025-01-03\",\"Close\":102.25,\"Open\":101.5,\"High\":103.0,\"Low\":101.0},"
                + "{\"Open\":1.0,\"High\":2.0}"  // Date/Close yok → atlanır
                + "]";
        stubBody(json);

        List<EurobondChartPoint> pts = client.fetchChart("1,627799832,1330,333",
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31));

        assertThat(pts).hasSize(2);
        EurobondChartPoint p0 = pts.get(0);
        assertThat(p0.date()).isEqualTo("2025-01-02");
        assertThat(p0.close()).isEqualByComparingTo(new BigDecimal("101.5"));
        assertThat(p0.open()).isEqualByComparingTo(new BigDecimal("100.0"));
        assertThat(p0.high()).isEqualByComparingTo(new BigDecimal("102.0"));
        assertThat(p0.low()).isEqualByComparingTo(new BigDecimal("99.5"));
        assertThat(pts.get(1).date()).isEqualTo("2025-01-03");
        assertThat(pts.get(1).close()).isEqualByComparingTo(new BigDecimal("102.25"));
    }

    @Test
    @DisplayName("fetchChart: bozuk JSON → exception yutulur, boş liste döner")
    void fetchChart_malformedJson() {
        stubBody("not-a-json{");
        List<EurobondChartPoint> pts = client.fetchChart("tk",
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31));
        assertThat(pts).isEmpty();
    }

    @Test
    @DisplayName("fetchChart: JSON ama dizi değil (obje) → boş liste")
    void fetchChart_notArray() {
        stubBody("{\"error\":\"nope\"}");
        assertThat(client.fetchChart("tk", LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31))).isEmpty();
    }

    // ── GET hata dalları (ban-koruması) ──────────────────────────────────────

    @Test
    @DisplayName("get(): 429 → degrade penceresi açılır, sonraki istek HTTP'ye gitmeden null döner")
    void get_rateLimited_thenDegraded() {
        HttpClientErrorException tooMany =
                HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests", null, null, null);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenThrow(tooMany);

        // 1. çağrı: 429 → boş + RATE_LIMITED log publish edilir, degradedUntil ileriye alınır.
        assertThat(client.resolve("XS1234567890")).isEmpty();
        // 2. çağrı: degrade penceresinde → restTemplate'e hiç gidilmez, yine boş.
        assertThat(client.fetchDetail(new EurobondRef("XS1234567890", "slug", "n", "i", 1L))).isEmpty();
        assertThat(client.fetchChart("tk", LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31))).isEmpty();

        // exchange sadece ilk çağrıda kullanıldı; degrade penceresi sonrakileri kısa devre yaptı.
        verify(restTemplate, times(1))
                .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
        // RATE_LIMITED entegrasyon logu yayımlandı.
        verify(integrationLog, times(1)).publish(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), any(), any(), any(), any(), anyString());
    }

    @Test
    @DisplayName("get(): 403 → degrade dalı (RATE_LIMITED) → resolve boş döner")
    void get_forbidden() {
        HttpClientErrorException forbidden =
                HttpClientErrorException.create(HttpStatus.FORBIDDEN, "Forbidden", null, null, null);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenThrow(forbidden);
        assertThat(client.resolve("XS1234567890")).isEmpty();
    }

    @Test
    @DisplayName("get(): 500 (403/429 dışı HTTP hatası) → FAILED dalı → boş döner")
    void get_serverError() {
        HttpServerErrorException serverErr =
                HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR, "Boom", null, null, null);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenThrow(serverErr);
        assertThat(client.resolve("XS1234567890")).isEmpty();
    }

    @Test
    @DisplayName("get(): genel istisna (HttpStatusCodeException değil) → FAILED dalı → boş döner")
    void get_genericException() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RestClientException("connection reset"));
        assertThat(client.resolve("XS1234567890")).isEmpty();
    }
}
