package com.finance.portal.market.infrastructure.external.fx;

import com.finance.portal.common.application.logging.CentralIntegrationLogService;
import com.finance.portal.market.application.fx.model.FxHistoryPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link TcmbFxHistoryClient} testleri.
 *
 * Bu istemcinin TCMB arşiv URL'i sabit ({@code https://www.tcmb.gov.tr/kurlar/...}) olduğundan
 * WireMock ile yönlendirilemez; bunun yerine {@link RestTemplate} mock'lanıp
 * {@code getForObject(url, String.class)} dönüşü (TCMB XML'i) stub'lanır. Böylece XML parse
 * ({@code <Currency CurrencyCode><ForexSelling>}), 404/hata dalı, boş gövde ve "0 veri noktası →
 * entegrasyon log" davranışları kapsanır.
 */
class TcmbFxHistoryClientTest {

    private RestTemplate restTemplate;
    private CentralIntegrationLogService integrationLog;
    private TcmbFxHistoryClient client;

    /** Tek bir iş günü (Çarşamba). */
    private static final LocalDate WORKDAY = LocalDate.of(2025, 4, 23);

    private static String xmlWith(String currencyCode, String forexSelling) {
        return "<?xml version=\"1.0\" encoding=\"ISO-8859-9\"?>"
                + "<Tarih_Date>"
                + "<Currency CurrencyCode=\"" + currencyCode + "\">"
                + "<ForexBuying>40.0000</ForexBuying>"
                + "<ForexSelling>" + forexSelling + "</ForexSelling>"
                + "</Currency>"
                + "</Tarih_Date>";
    }

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        integrationLog = mock(CentralIntegrationLogService.class);
        client = new TcmbFxHistoryClient(restTemplate, integrationLog);
    }

    @Test
    @DisplayName("fetchHistory: geçerli XML → ForexSelling parse edilip nokta döner")
    void fetchHistory_ok() {
        when(restTemplate.getForObject(anyString(), eq(String.class)))
                .thenReturn(xmlWith("USD", "38.5000"));

        List<FxHistoryPoint> points = client.fetchHistory("USD", WORKDAY, WORKDAY);

        assertThat(points).hasSize(1);
        FxHistoryPoint p = points.get(0);
        assertThat(p.getDate()).isEqualTo("2025-04-23");
        assertThat(p.getClose()).isEqualByComparingTo(new BigDecimal("38.5000"));
        // Veri noktası var → parse-failed entegrasyon log'u tetiklenmez.
        verify(integrationLog, never()).publish(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.<Map<String, Object>>any(), anyString());
    }

    @Test
    @DisplayName("fetchHistory: case-insensitive currency code eşleşir")
    void fetchHistory_caseInsensitiveCode() {
        when(restTemplate.getForObject(anyString(), eq(String.class)))
                .thenReturn(xmlWith("EUR", "44.1234"));

        List<FxHistoryPoint> points = client.fetchHistory("eur", WORKDAY, WORKDAY);

        assertThat(points).hasSize(1);
        assertThat(points.get(0).getClose()).isEqualByComparingTo(new BigDecimal("44.1234"));
    }

    @Test
    @DisplayName("fetchHistory: çok günlü aralık tarihe göre sıralı döner")
    void fetchHistory_multiDaySorted() {
        // Pzt-Çar (3 iş günü). Tüm günler için aynı USD değeri döndürülür.
        when(restTemplate.getForObject(anyString(), eq(String.class)))
                .thenReturn(xmlWith("USD", "38.0000"));

        LocalDate mon = LocalDate.of(2025, 4, 21);
        LocalDate wed = LocalDate.of(2025, 4, 23);
        List<FxHistoryPoint> points = client.fetchHistory("USD", mon, wed);

        assertThat(points).hasSize(3);
        assertThat(points).extracting(FxHistoryPoint::getDate)
                .containsExactly("2025-04-21", "2025-04-22", "2025-04-23");
    }

    @Test
    @DisplayName("fetchHistory: hafta sonu aralığı → iş günü yok, boş liste, log yok")
    void fetchHistory_weekendOnly() {
        LocalDate sat = LocalDate.of(2025, 4, 26);
        LocalDate sun = LocalDate.of(2025, 4, 27);

        List<FxHistoryPoint> points = client.fetchHistory("USD", sat, sun);

        assertThat(points).isEmpty();
        // Hiç iş günü yoksa HTTP çağrısı da yapılmaz.
        verify(restTemplate, never()).getForObject(anyString(), eq(String.class));
        verify(integrationLog, never()).publish(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.<Map<String, Object>>any(), anyString());
    }

    @Test
    @DisplayName("fetchHistory: 404 (NotFound) → o gün atlanır, 0 nokta → entegrasyon log tetiklenir")
    void fetchHistory_notFound_logsParseFailure() {
        HttpClientErrorException notFound =
                HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", null, null, null);
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenThrow(notFound);

        List<FxHistoryPoint> points = client.fetchHistory("USD", WORKDAY, WORKDAY);

        assertThat(points).isEmpty();
        // İş günü çekildi ama 0 nokta → parse-failed entegrasyon log'u tam 1 kez.
        verify(integrationLog, times(1)).publish(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.<Map<String, Object>>any(), anyString());
    }

    @Test
    @DisplayName("fetchHistory: boş/null gövde → nokta yok")
    void fetchHistory_blankBody() {
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn("   ");

        List<FxHistoryPoint> points = client.fetchHistory("USD", WORKDAY, WORKDAY);

        assertThat(points).isEmpty();
    }

    @Test
    @DisplayName("fetchHistory: 'Page Not Found' içeren gövde → nokta yok")
    void fetchHistory_pageNotFoundBody() {
        when(restTemplate.getForObject(anyString(), eq(String.class)))
                .thenReturn("<html>Page Not Found</html>");

        List<FxHistoryPoint> points = client.fetchHistory("USD", WORKDAY, WORKDAY);

        assertThat(points).isEmpty();
    }

    @Test
    @DisplayName("fetchHistory: bozuk XML → parse hatası yutulur, nokta yok")
    void fetchHistory_malformedXml() {
        when(restTemplate.getForObject(anyString(), eq(String.class)))
                .thenReturn("<Tarih_Date><Currency CurrencyCode=\"USD\"><ForexSelling>1.0");

        List<FxHistoryPoint> points = client.fetchHistory("USD", WORKDAY, WORKDAY);

        assertThat(points).isEmpty();
    }

    @Test
    @DisplayName("fetchHistory: istenen currency XML'de yok → nokta yok")
    void fetchHistory_currencyNotInXml() {
        when(restTemplate.getForObject(anyString(), eq(String.class)))
                .thenReturn(xmlWith("EUR", "44.0000"));

        List<FxHistoryPoint> points = client.fetchHistory("USD", WORKDAY, WORKDAY);

        assertThat(points).isEmpty();
    }

    @Test
    @DisplayName("fetchHistory: RestTemplate genel exception fırlatırsa → sessizce atlanır")
    void fetchHistory_genericException() {
        when(restTemplate.getForObject(anyString(), eq(String.class)))
                .thenThrow(new RuntimeException("connection reset"));

        List<FxHistoryPoint> points = client.fetchHistory("USD", WORKDAY, WORKDAY);

        assertThat(points).isEmpty();
        // En az bir kez çağrıldı (HTTP denendi).
        verify(restTemplate, times(1)).getForObject(anyString(), eq(String.class));
    }
}
