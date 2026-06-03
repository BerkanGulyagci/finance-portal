package com.finance.portal.market.application.economy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.finance.portal.common.infrastructure.cache.LastKnownGoodCache;
import com.finance.portal.market.application.economy.model.EconomySeriesPoint;
import com.finance.portal.market.application.economy.port.EconomyDataPort;
import com.finance.portal.market.application.economy.port.FredDataPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Ek dal kapsamı: {@link InflationDeflatorServiceTest} interpolasyon "happy path"lerini test eder;
 * bu sınıf null/boş/sınır/hata dallarını hedefler:
 * <ul>
 *   <li>{@code tufeSeries}/{@code usCpiSeries}/{@code refresh*} — LKG sarmalayıcı + port çağrısı</li>
 *   <li>{@code cumulativeFactor(series, from)} — null/boş/zero-guard/floor & "alış son aydan sonra"</li>
 *   <li>{@code indexValueAt} — null/boş guard ve geçersiz değer</li>
 *   <li>{@code indexValueInterpolated} — tüm noktaları atlama, seri öncesi clamp, span<=0</li>
 *   <li>{@code cumulativeFactor(series, from, to)} — null/empty/zero base dalları</li>
 *   <li>ekstrapolasyon: tek nokta (büyüme 0) & geçersiz komşular</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InflationDeflatorServiceMoreTest {

    private static final ZoneId TR = ZoneId.of("Europe/Istanbul");

    @Mock
    private EconomyDataPort economyDataPort;
    @Mock
    private FredDataPort fredDataPort;
    @Mock
    private LastKnownGoodCache lkg;

    private InflationDeflatorService service;

    @BeforeEach
    void setUp() {
        service = new InflationDeflatorService(economyDataPort, fredDataPort, lkg);
    }

    /** LKG'yi pass-through yapar: gerçek supplier'ı çağırıp sonucunu döndürür. */
    @SuppressWarnings("unchecked")
    private <T> void passThroughLkg() {
        when(lkg.resilient(anyString(), any(Duration.class), any(TypeReference.class), any(Supplier.class)))
                .thenAnswer(inv -> ((Supplier<Object>) inv.getArgument(3)).get());
    }

    // ---------- tufeSeries / usCpiSeries / refresh* ----------

    @Test
    @DisplayName("tufeSeries: LKG sarmalayıcı EVDS portunu çağırır ve seriyi döner")
    void tufeSeries_delegatesToEconomyPort() {
        passThroughLkg();
        List<EconomySeriesPoint> data = List.of(point("2026-01-01", "100"));
        when(economyDataPort.fetchSeries(anyString(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(data);

        List<EconomySeriesPoint> out = service.tufeSeries();

        assertThat(out).isSameAs(data);
    }

    @Test
    @DisplayName("usCpiSeries: LKG sarmalayıcı FRED portunu çağırır (anahtar yoksa boş liste)")
    void usCpiSeries_delegatesToFredPort_empty() {
        passThroughLkg();
        when(fredDataPort.fetchSeries(anyString(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(Collections.emptyList());

        List<EconomySeriesPoint> out = service.usCpiSeries();

        assertThat(out).isEmpty();
    }

    @Test
    @DisplayName("refreshTufeSeries / refreshUsCpiSeries: self-invoke ile read metoduna delege eder")
    void refreshMethods_delegate() {
        passThroughLkg();
        List<EconomySeriesPoint> tufe = List.of(point("2026-02-01", "110"));
        when(economyDataPort.fetchSeries(eq("TP.TUKFIY2025.GENEL"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(tufe);
        when(fredDataPort.fetchSeries(eq("CPIAUCNS"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(Collections.emptyList());

        assertThat(service.refreshTufeSeries()).isSameAs(tufe);
        assertThat(service.refreshUsCpiSeries()).isEmpty();
    }

    // ---------- cumulativeFactor(series, from) ----------

    @Test
    @DisplayName("cumulativeFactor(from): null from / null seri / boş seri → empty")
    void cumulativeFactor2_nullAndEmptyGuards() {
        List<EconomySeriesPoint> series = List.of(point("2026-01-01", "100"), point("2026-02-01", "110"));

        assertThat(service.cumulativeFactor(series, null)).isEmpty();
        assertThat(service.cumulativeFactor(null, LocalDate.of(2026, 1, 1))).isEmpty();
        assertThat(service.cumulativeFactor(Collections.emptyList(), LocalDate.of(2026, 1, 1))).isEmpty();
    }

    @Test
    @DisplayName("cumulativeFactor(from): base değeri null → empty (zero/null-guard)")
    void cumulativeFactor2_baseNullValue() {
        // nearest 'from' → null değerli noktaya snap olur.
        List<EconomySeriesPoint> series = List.of(
                point("2026-01-01", null),
                point("2026-06-01", "120"));

        Optional<BigDecimal> r = service.cumulativeFactor(series, LocalDate.of(2026, 1, 1));

        assertThat(r).isEmpty();
    }

    @Test
    @DisplayName("cumulativeFactor(from): base değeri sıfır → empty (signum<=0)")
    void cumulativeFactor2_baseZeroValue() {
        List<EconomySeriesPoint> series = List.of(
                point("2026-01-01", "0"),
                point("2026-06-01", "120"));

        Optional<BigDecimal> r = service.cumulativeFactor(series, LocalDate.of(2026, 1, 1));

        assertThat(r).isEmpty();
    }

    @Test
    @DisplayName("cumulativeFactor(from): base == latest (alış son TÜFE ayında/sonrasında) → empty")
    void cumulativeFactor2_baseAtOrAfterLatest() {
        List<EconomySeriesPoint> series = List.of(
                point("2026-01-01", "100"),
                point("2026-02-01", "110"));

        // from son aya snap olur → base.unixTime >= latest.unixTime → empty
        Optional<BigDecimal> r = service.cumulativeFactor(series, LocalDate.of(2026, 2, 15));

        assertThat(r).isEmpty();
    }

    @Test
    @DisplayName("cumulativeFactor(from): geçerli geçmiş tarih → latest/base oranı (>1)")
    void cumulativeFactor2_happyPath() {
        List<EconomySeriesPoint> series = List.of(
                point("2026-01-01", "100"),
                point("2026-06-01", "150"));

        Optional<BigDecimal> r = service.cumulativeFactor(series, LocalDate.of(2026, 1, 1));

        assertThat(r).isPresent();
        assertThat(r.get()).isEqualByComparingTo("1.5");
    }

    // ---------- indexValueAt ----------

    @Test
    @DisplayName("indexValueAt: null seri / boş seri / null tarih → empty")
    void indexValueAt_guards() {
        List<EconomySeriesPoint> series = List.of(point("2026-01-01", "100"));

        assertThat(service.indexValueAt(null, LocalDate.of(2026, 1, 1))).isEmpty();
        assertThat(service.indexValueAt(Collections.emptyList(), LocalDate.of(2026, 1, 1))).isEmpty();
        assertThat(service.indexValueAt(series, null)).isEmpty();
    }

    @Test
    @DisplayName("indexValueAt: en yakın noktanın değeri null/sıfır → empty")
    void indexValueAt_invalidNearestValue() {
        List<EconomySeriesPoint> nullVal = List.of(point("2026-01-01", null));
        List<EconomySeriesPoint> zeroVal = List.of(point("2026-01-01", "0"));

        assertThat(service.indexValueAt(nullVal, LocalDate.of(2026, 1, 1))).isEmpty();
        assertThat(service.indexValueAt(zeroVal, LocalDate.of(2026, 1, 1))).isEmpty();
    }

    @Test
    @DisplayName("indexValueAt: geçerli en yakın değer → o değer")
    void indexValueAt_happyPath() {
        List<EconomySeriesPoint> series = List.of(
                point("2026-01-01", "100"),
                point("2026-02-01", "110"));

        Optional<BigDecimal> r = service.indexValueAt(series, LocalDate.of(2026, 2, 3));

        assertThat(r).isPresent();
        assertThat(r.get()).isEqualByComparingTo("110");
    }

    // ---------- indexValueInterpolated ----------

    @Test
    @DisplayName("indexValueInterpolated: null/boş seri / null tarih → empty")
    void indexValueInterpolated_guards() {
        List<EconomySeriesPoint> series = List.of(point("2026-01-01", "100"));

        assertThat(service.indexValueInterpolated(null, LocalDate.of(2026, 1, 1))).isEmpty();
        assertThat(service.indexValueInterpolated(Collections.emptyList(), LocalDate.of(2026, 1, 1))).isEmpty();
        assertThat(service.indexValueInterpolated(series, null)).isEmpty();
    }

    @Test
    @DisplayName("indexValueInterpolated: tüm noktalar geçersiz (null/sıfır) → empty")
    void indexValueInterpolated_allPointsSkipped() {
        List<EconomySeriesPoint> series = List.of(
                point("2026-01-01", null),
                point("2026-02-01", "0"),
                point("2026-03-01", "-5"));

        Optional<BigDecimal> r = service.indexValueInterpolated(series, LocalDate.of(2026, 2, 15));

        assertThat(r).isEmpty();
    }

    @Test
    @DisplayName("indexValueInterpolated: seri öncesi tarih → ilk geçerli değere clamp (before==null)")
    void indexValueInterpolated_beforeSeriesClampsToFirst() {
        List<EconomySeriesPoint> series = List.of(
                point("2026-05-01", "200"),
                point("2026-06-01", "210"));

        Optional<BigDecimal> r = service.indexValueInterpolated(series, LocalDate.of(2026, 1, 1));

        assertThat(r).isPresent();
        assertThat(r.get()).isEqualByComparingTo("200");
    }

    @Test
    @DisplayName("indexValueInterpolated: target tam bir noktaya eşit → o aylık değer (span hesabı doğrudan)")
    void indexValueInterpolated_exactPointMatch() {
        List<EconomySeriesPoint> series = List.of(
                point("2026-03-01", "100"),
                point("2026-04-01", "110"));

        // target == before.unixTime → frac=0 → before değeri
        Optional<BigDecimal> r = service.indexValueInterpolated(series, LocalDate.of(2026, 3, 1));

        assertThat(r).isPresent();
        assertThat(r.get()).isEqualByComparingTo("100");
    }

    // ---------- cumulativeFactor(series, from, to) ----------

    @Test
    @DisplayName("cumulativeFactor(from,to): null from veya null to → empty")
    void cumulativeFactor3_nullGuards() {
        List<EconomySeriesPoint> series = List.of(point("2026-01-01", "100"), point("2026-02-01", "110"));

        assertThat(service.cumulativeFactor(series, null, LocalDate.of(2026, 2, 1))).isEmpty();
        assertThat(service.cumulativeFactor(series, LocalDate.of(2026, 1, 1), null)).isEmpty();
    }

    @Test
    @DisplayName("cumulativeFactor(from,to): base/at hesaplanamıyor (boş seri) → empty")
    void cumulativeFactor3_baseOrAtEmpty() {
        // boş seri → indexValueInterpolated her ikisinde de empty → empty
        Optional<BigDecimal> r = service.cumulativeFactor(
                Collections.emptyList(), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1));

        assertThat(r).isEmpty();
    }

    @Test
    @DisplayName("cumulativeFactor(from,to): geçerli base/at (tam nokta eşleşmesi) → at/base oranı")
    void cumulativeFactor3_exactPointsRatio() {
        // from→Mart başı (=100), to→Nisan başı (=120) → 120/100 = 1.2 (base signum>0 dalı geçilir).
        long u1 = LocalDate.of(2026, 3, 1).atStartOfDay(TR).toEpochSecond();
        long u2 = LocalDate.of(2026, 4, 1).atStartOfDay(TR).toEpochSecond();
        List<EconomySeriesPoint> series = new ArrayList<>();
        series.add(new EconomySeriesPoint("2026-03-01", new BigDecimal("100"), u1));
        series.add(new EconomySeriesPoint("2026-04-01", new BigDecimal("120"), u2));

        Optional<BigDecimal> r = service.cumulativeFactor(
                series, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 4, 1));

        assertThat(r).isPresent();
        assertThat(r.get()).isEqualByComparingTo("1.2");
    }

    // ---------- extrapolation edge: single point => growth 0 ----------

    @Test
    @DisplayName("indexValueInterpolated: tek nokta + seri sonrası tarih → büyüme 0, değer sabit kalır")
    void indexValueInterpolated_singlePointExtrapolatesFlat() {
        // Tek geçerli nokta → trailingMonthlyGrowth idx<=0 → 0.0 → factor = value × 1.0
        List<EconomySeriesPoint> series = List.of(point("2026-01-01", "100"));

        Optional<BigDecimal> r = service.indexValueInterpolated(series, LocalDate.of(2026, 6, 1));

        assertThat(r).isPresent();
        // büyüme 0 → 100 (pow(1,...)=1)
        assertThat(r.get().doubleValue()).isCloseTo(100.0, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    @DisplayName("indexValueInterpolated: ekstrapolasyonda geçersiz komşular atlanır (n=0 → büyüme 0)")
    void indexValueInterpolated_extrapolateWithInvalidNeighbors() {
        // 'last' geçerli ama önceki komşu null değerli → trailingMonthlyGrowth'ta o adım skip,
        // başka geçerli çift yoksa n=0 → büyüme 0 → düz ekstrapolasyon.
        List<EconomySeriesPoint> series = List.of(
                point("2026-03-01", null),
                point("2026-04-01", "108"));

        Optional<BigDecimal> r = service.indexValueInterpolated(series, LocalDate.of(2026, 7, 1));

        assertThat(r).isPresent();
        // n=0 → büyüme 0 → son değer 108 sabit
        assertThat(r.get().doubleValue()).isCloseTo(108.0, org.assertj.core.data.Offset.offset(0.001));
    }

    private static EconomySeriesPoint point(String period, String value) {
        long unix = LocalDate.parse(periodDate(period)).atStartOfDay(TR).toEpochSecond();
        BigDecimal v = value == null ? null : new BigDecimal(value);
        return new EconomySeriesPoint(period, v, unix);
    }

    /** "2026-03-01b" gibi sonek taşıyan period etiketlerinden gerçek tarihi ayıklar (dup-unix testi için). */
    private static String periodDate(String period) {
        return period.length() > 10 ? period.substring(0, 10) : period;
    }
}
