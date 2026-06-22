package com.finance.portal.market.application.economy;

import com.finance.portal.common.infrastructure.cache.LastKnownGoodCache;
import com.finance.portal.market.application.economy.model.EconomySeriesPoint;
import com.finance.portal.market.application.economy.port.EconomyDataPort;
import com.finance.portal.market.application.economy.port.FredDataPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Yeni interpolasyon/ekstrapolasyon yardımcılarını kapsar.
 *
 * <p>Aylık TÜFE serisi, performance ve what-if grafiklerinin günlük noktalarına ham {@code
 * indexValueAt}/{@code nearest} ile snap'lendiğinde 32 günlük pencere içinde tüm günler tek bir
 * aylık değere yapışıyor ve referans çizgi düz kalıyor (bug 8 ve 9). Yeni
 * {@link InflationDeflatorService#indexValueInterpolated} iki aylık noktayı doğrusal birleştirir;
 * son aylık noktanın ötesi (TÜFE yayım gecikmesi) son ≤3 ay geometrik ortalamasıyla ekstrapole
 * edilir. {@link InflationDeflatorService#cumulativeFactor(List, LocalDate, LocalDate)} bu değerler
 * üzerinden iki tarih arası bileşik enflasyon faktörünü verir.
 */
class InflationDeflatorServiceTest {

    private static final ZoneId TR = ZoneId.of("Europe/Istanbul");

    private InflationDeflatorService service;

    @BeforeEach
    void setUp() {
        service = new InflationDeflatorService(mock(EconomyDataPort.class), mock(FredDataPort.class),
                mock(LastKnownGoodCache.class));
    }

    @Test
    @DisplayName("indexValueInterpolated: iki ay arasındaki tarih iki noktayı doğrusal birleştirir")
    void indexValueInterpolated_betweenMonths_linearBlend() {
        // CPI: Mart=100, Nisan=110. Mart 16 ortada → ≈ 105
        List<EconomySeriesPoint> series = List.of(
                point("2026-03-01", "100"),
                point("2026-04-01", "110"));

        Optional<BigDecimal> mid = service.indexValueInterpolated(series, LocalDate.of(2026, 3, 16));

        assertThat(mid).isPresent();
        // ±%2 tolerans (UTC/saat ofseti küçük dakika kayması yapabilir).
        double v = mid.get().doubleValue();
        assertThat(v).isBetween(103.0, 107.0);
    }

    @Test
    @DisplayName("indexValueInterpolated: seri sonrası tarihler son aylık büyüme ile ekstrapole edilir")
    void indexValueInterpolated_afterLastPoint_extrapolates() {
        // CPI son 3 ay: %2/ay ortalamada → 100 → 102 → 104.04
        List<EconomySeriesPoint> series = List.of(
                point("2026-02-01", "100"),
                point("2026-03-01", "102"),
                point("2026-04-01", "104.04"));

        // Son noktadan ~1 ay sonra → ≈ 104.04 × 1.02 = ~106.12
        Optional<BigDecimal> after = service.indexValueInterpolated(series, LocalDate.of(2026, 5, 1));

        assertThat(after).isPresent();
        double v = after.get().doubleValue();
        assertThat(v).isBetween(105.0, 107.5);
    }

    @Test
    @DisplayName("cumulativeFactor(from,to): aynı ay içinde bile farklı günler için farklı faktör")
    void cumulativeFactor_dailySensitivity_notFlat() {
        // CPI 100 → 110 (10% aylık). Mart başı vs Mart sonu → fark görünür olmalı.
        List<EconomySeriesPoint> series = List.of(
                point("2026-03-01", "100"),
                point("2026-04-01", "110"));

        LocalDate from = LocalDate.of(2026, 3, 1);
        BigDecimal f1 = service.cumulativeFactor(series, from, LocalDate.of(2026, 3, 10)).orElseThrow();
        BigDecimal f2 = service.cumulativeFactor(series, from, LocalDate.of(2026, 3, 25)).orElseThrow();

        // Daha sonraki gün → daha büyük faktör (gün bazlı duyarlılık var).
        assertThat(f2).isGreaterThan(f1);
        // Her iki faktör de 1.00 (cost-eşit) değil — bug 8/9'un düzeltildiğinin ispatı.
        assertThat(f1.doubleValue()).isGreaterThan(1.0);
        assertThat(f2.doubleValue()).isGreaterThan(1.0);
    }

    @Test
    @DisplayName("cumulativeFactor(from,to): seri sonrası 'to' tarihleri pozitif birikimli enflasyon verir")
    void cumulativeFactor_pastTufeLatest_extrapolatedAboveOne() {
        // Son aylık CPI 110; aylık büyüme ~%5. from = son ay, to = 30 gün sonrası → ~1.05
        List<EconomySeriesPoint> series = List.of(
                point("2026-02-01", "100"),
                point("2026-03-01", "105"),
                point("2026-04-01", "110.25"));

        LocalDate from = LocalDate.of(2026, 4, 1);
        LocalDate to = LocalDate.of(2026, 5, 1);
        BigDecimal f = service.cumulativeFactor(series, from, to).orElseThrow();

        // %5 ± tolerans (30g vs ay-başı approximasyonu)
        assertThat(f.doubleValue()).isBetween(1.03, 1.08);
    }

    @Test
    @DisplayName("cumulativeFactor(from) [reel K/Z]: SON yayınlanan endeksi kullanır, EKSTRAPOLE ETMEZ")
    void cumulativeFactorToLatest_usesLastPublished_noExtrapolation() {
        // TIPS/enflasyon-endeksli enstrüman standardı: reel getiri yayınlanmamış günler için
        // endeksi ileri taşımaz; son bilinen (lag'li) endeksi esas alır.
        List<EconomySeriesPoint> series = List.of(
                point("2025-02-01", "90"),
                point("2025-03-01", "93"),
                point("2026-05-01", "128"));   // son yayınlanan

        LocalDate from = LocalDate.of(2025, 2, 6);

        // 2-param (reel K/Z): latest(128) / base(~90) — ekstrapolasyon YOK.
        BigDecimal latestFactor = service.cumulativeFactor(series, from).orElseThrow();

        // 3-param "bugün" (grafik): son aydan çok sonrasına EKSTRAPOLE eder → daha büyük faktör.
        BigDecimal extrapolatedFactor = service.cumulativeFactor(
                series, from, LocalDate.of(2026, 7, 1)).orElseThrow();

        // Reel K/Z faktörü, ekstrapole edilen "bugün" faktöründen KÜÇÜK olmalı (ileri taşımıyor).
        assertThat(latestFactor).isLessThan(extrapolatedFactor);
        // latest = 128 / ~90 ≈ 1.42 (son yayınlanan ile sınırlı; >1.45'e çıkmaz).
        assertThat(latestFactor.doubleValue()).isBetween(1.40, 1.45);
    }

    private static EconomySeriesPoint point(String period, String value) {
        long unix = LocalDate.parse(period).atStartOfDay(TR).toEpochSecond();
        return new EconomySeriesPoint(period, new BigDecimal(value), unix);
    }
}
