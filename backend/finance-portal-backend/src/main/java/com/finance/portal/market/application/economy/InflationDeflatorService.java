package com.finance.portal.market.application.economy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.finance.portal.common.infrastructure.cache.LastKnownGoodCache;
import com.finance.portal.market.application.economy.model.EconomySeriesPoint;
import com.finance.portal.market.application.economy.port.EconomyDataPort;
import com.finance.portal.market.application.economy.port.FredDataPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * TÜFE bazlı enflasyon deflatörü — bir geçmiş tarihten bugüne birikimli enflasyon faktörünü verir.
 *
 * <p>Reel getiri hesabında kullanılır: nominal bir TL tutarın bugünkü "reel" karşılığı için
 * {@code tutar × faktör} ile şişirilir. Faktör = TÜFE(bugün) / TÜFE(geçmiş ay).
 *
 * <p>Self-invocation cache tuzağından kaçınmak için seri çekimi ({@link #tufeSeries()}) ile
 * hesap ({@link #cumulativeFactor}) ayrılmıştır: çağıran önce {@code tufeSeries()}'i (cache'li)
 * bir kez alır, sonra her tarih için saf {@code cumulativeFactor(...)}'ı çağırır.
 */
@Service
public class InflationDeflatorService {

    private static final Logger log = LoggerFactory.getLogger(InflationDeflatorService.class);

    /** TÜFE genel endeks — aylık, güncel seri (2025=100). Eski TP.FG.J0 (2003=100) Oca 2026'da donduruldu. */
    private static final String TUFE_CODE = "TP.TUKFIY2025.GENEL";
    /** ABD TÜFE (CPI, NSA, aylık endeks; baz 1982-84=100). FRED üzerinden. */
    private static final String US_CPI_CODE = "CPIAUCNS";
    /** Geçmiş alışları kapsaması için seriyi bu tarihten itibaren çekeriz. */
    private static final LocalDate SERIES_START = LocalDate.of(2005, 1, 1);
    /** EVDS UNIXTIME alanları İstanbul saatiyle ay başına denk gelir. */
    private static final ZoneId TR_ZONE = ZoneId.of("Europe/Istanbul");

    /** Yavaş (aylık ekonomi) veri için LKG ömrü — kaynak çökerse en fazla bu kadar eski seri gösterilir. */
    private static final Duration ECONOMY_LKG_TTL = Duration.ofDays(14);

    private final EconomyDataPort economyDataPort;
    private final FredDataPort fredDataPort;
    private final LastKnownGoodCache lkg;

    public InflationDeflatorService(EconomyDataPort economyDataPort, FredDataPort fredDataPort,
                                    LastKnownGoodCache lkg) {
        this.economyDataPort = economyDataPort;
        this.fredDataPort = fredDataPort;
        this.lkg = lkg;
    }

    /**
     * Aylık TÜFE endeks serisini döndürür (unixTime'a göre artan). Cache TTL: economy ile aynı.
     * <p>Çağıran bunu bir kez alıp {@link #cumulativeFactor} ile tekrar tekrar kullanmalıdır.
     */
    @Cacheable(cacheNames = "market.economy", key = "'tufe.series'")
    public List<EconomySeriesPoint> tufeSeries() {
        // EVDS çökerse/boş dönerse son başarılı TÜFE serisini servis et (yavaş veri, uzun LKG ömrü).
        List<EconomySeriesPoint> pts = lkg.resilient("economy.tufe.series", ECONOMY_LKG_TTL,
                new TypeReference<List<EconomySeriesPoint>>() {},
                () -> economyDataPort.fetchSeries(TUFE_CODE, SERIES_START, LocalDate.now()));
        log.info("[InflationDeflator] TÜFE serisi yüklendi — {} nokta", pts.size());
        return pts;
    }

    /**
     * Aylık ABD TÜFE (CPI) endeks serisini döndürür (unixTime'a göre artan). FRED üzerinden.
     * <p>USD cinsi pozisyonların reel getirisinde kullanılır. FRED anahtarı yoksa boş liste döner
     * (çağıran reel alanları null bırakır). {@link #cumulativeFactor} ile birlikte kullanılır.
     */
    @Cacheable(cacheNames = "market.economy", key = "'uscpi.series'")
    public List<EconomySeriesPoint> usCpiSeries() {
        // FRED çökerse/boş dönerse son başarılı ABD CPI serisini servis et (yavaş veri, uzun LKG ömrü).
        // NOT: FRED anahtarı yoksa adapter boş liste döner → LKG de yoksa boş kalır (mevcut degrade korunur).
        List<EconomySeriesPoint> pts = lkg.resilient("economy.uscpi.series", ECONOMY_LKG_TTL,
                new TypeReference<List<EconomySeriesPoint>>() {},
                () -> fredDataPort.fetchSeries(US_CPI_CODE, SERIES_START, LocalDate.now()));
        log.info("[InflationDeflator] ABD CPI serisi yüklendi — {} nokta", pts.size());
        return pts;
    }

    /**
     * Warm-up için: TÜFE serisi cache'ini ('tufe.series') atomik tazeler.
     * {@code MarketListCacheWarmupService} periyodik çağırır → reel-getiri/what-if soğuk EVDS
     * çekimine düşmez (TÜFE serisi her portföy reel-getiri + what-if hesabının temeli). Self-invoke
     * ile read metodunun @Cacheable'ı bypass; @CachePut taze seriyi read ile AYNI anahtara yazar.
     */
    @CachePut(cacheNames = "market.economy", key = "'tufe.series'")
    public List<EconomySeriesPoint> refreshTufeSeries() {
        return tufeSeries();
    }

    /** Warm-up için: ABD CPI serisi cache'ini ('uscpi.series') atomik tazeler ({@link #refreshTufeSeries} eşi). */
    @CachePut(cacheNames = "market.economy", key = "'uscpi.series'")
    public List<EconomySeriesPoint> refreshUsCpiSeries() {
        return usCpiSeries();
    }

    /**
     * {@code from} tarihinden (o ayın TÜFE'sinden) bugüne birikimli enflasyon faktörü.
     * Örn. 1.30 → %30 birikimli enflasyon. Hesaplanamıyorsa boş.
     *
     * @param series {@link #tufeSeries()} sonucu (cache'li, dışarıdan bir kez alınır)
     * @param from   referans tarih (genelde ilk alış tarihi)
     */
    public Optional<BigDecimal> cumulativeFactor(List<EconomySeriesPoint> series, LocalDate from) {
        if (from == null || series == null || series.isEmpty()) {
            return Optional.empty();
        }
        EconomySeriesPoint latest = series.get(series.size() - 1);
        EconomySeriesPoint base = nearest(series, from);
        if (base == null || base.getValue() == null || base.getValue().signum() <= 0) {
            return Optional.empty();
        }
        // Alış, en güncel TÜFE ayından sonraysa o döneme ait TÜFE henüz yayınlanmamıştır
        // → enflasyon ölçülemez (boş). Çağıran reel alanları null bırakır; frontend "—" gösterir
        // (yanıltıcı "%0" yerine "henüz hesaplanamıyor").
        if (base.getUnixTime() >= latest.getUnixTime()) {
            return Optional.empty();
        }
        return Optional.of(latest.getValue().divide(base.getValue(), MathContext.DECIMAL64));
    }

    /**
     * Verilen tarihe en yakın aylık endeks değerini döndürür (ör. enflasyon referans çizgisi için).
     * Seri boşsa / değer geçersizse boş.
     */
    public Optional<BigDecimal> indexValueAt(List<EconomySeriesPoint> series, LocalDate date) {
        if (series == null || series.isEmpty() || date == null) {
            return Optional.empty();
        }
        EconomySeriesPoint p = nearest(series, date);
        if (p == null || p.getValue() == null || p.getValue().signum() <= 0) {
            return Optional.empty();
        }
        return Optional.of(p.getValue());
    }

    /** Saniye/30g ≈ aylık genişletme periyodu (extrapolasyonda kullanılır). */
    private static final long MONTH_SECONDS = 30L * 24L * 3600L;

    /**
     * Verilen tarih için aylık seri üzerinde DOĞRUSAL İNTERPOLASYONLU endeks değeri.
     * Aynı ay içindeki günlük tarihler {@link #indexValueAt} ile aynı (en yakın) aylık değere
     * snap'lendiğinden günlük seriler düzleşir; bu helper iki komşu aylık nokta arasında
     * unixTime kesriyle interpolasyon yapar → günlük performance/what-if noktaları için
     * pürüzsüz değişen değer üretir.
     *
     * <p>Seri sonrasına düşen tarihler (TÜFE yayım gecikmesi nedeniyle bugün/son ay genelde
     * yayınlanmamıştır) için son 3 aylık geometrik ortalama büyüme oranı (CAGR) ile
     * EKSTRAPOLE edilir — aksi halde 32-günlük performans penceresinde son baz aylık değere
     * snap'lenip çizgi düz kalır (bug 8/9'un kök nedeni). Seri öncesi tarihler için ilk değere
     * clamp.
     */
    public Optional<BigDecimal> indexValueInterpolated(List<EconomySeriesPoint> series, LocalDate date) {
        if (series == null || series.isEmpty() || date == null) {
            return Optional.empty();
        }
        long target = date.atStartOfDay(TR_ZONE).toEpochSecond();
        EconomySeriesPoint before = null;
        EconomySeriesPoint after = null;
        EconomySeriesPoint last = null;
        for (EconomySeriesPoint p : series) {
            if (p.getValue() == null || p.getValue().signum() <= 0) {
                continue;
            }
            if (last == null || p.getUnixTime() > last.getUnixTime()) {
                last = p;
            }
            if (p.getUnixTime() <= target) {
                if (before == null || p.getUnixTime() > before.getUnixTime()) {
                    before = p;
                }
            } else {
                if (after == null || p.getUnixTime() < after.getUnixTime()) {
                    after = p;
                }
            }
        }
        if (before == null && after == null) {
            return Optional.empty();
        }
        if (before == null) {
            return Optional.of(after.getValue()); // seri öncesi → ilk değer
        }
        if (after != null) {
            long span = after.getUnixTime() - before.getUnixTime();
            if (span <= 0) {
                return Optional.of(before.getValue());
            }
            BigDecimal frac = BigDecimal.valueOf(target - before.getUnixTime())
                    .divide(BigDecimal.valueOf(span), MathContext.DECIMAL64);
            BigDecimal delta = after.getValue().subtract(before.getValue());
            return Optional.of(before.getValue().add(delta.multiply(frac)));
        }
        // Seri sonrası: son birkaç aylık geometrik ortalama büyüme oranını uygula.
        return Optional.of(extrapolateBeyond(series, last, target));
    }

    /**
     * Son aylık nokta + sonraki TÜFE yayınına kadar olan gün/ayları, son 3 aylık (varsa)
     * geometrik aylık büyüme oranı ile ileri taşır. Veri tek noktaya düşerse büyüme 0.
     */
    private static BigDecimal extrapolateBeyond(List<EconomySeriesPoint> series,
                                                EconomySeriesPoint last, long targetSeconds) {
        double monthlyGrowth = trailingMonthlyGrowth(series, last);
        long deltaSeconds = Math.max(0L, targetSeconds - last.getUnixTime());
        double months = (double) deltaSeconds / (double) MONTH_SECONDS;
        double factor = Math.pow(1.0 + monthlyGrowth, months);
        return last.getValue().multiply(BigDecimal.valueOf(factor));
    }

    /**
     * Son ≤3 ay için ortalama aylık büyüme: g_n = (CPI_n / CPI_{n-1}) − 1.
     * Yeterli geçmiş yoksa son iki aylık değişim; o da yoksa 0.
     */
    private static double trailingMonthlyGrowth(List<EconomySeriesPoint> series,
                                                EconomySeriesPoint last) {
        List<EconomySeriesPoint> ordered = new java.util.ArrayList<>(series);
        ordered.sort(java.util.Comparator.comparingLong(EconomySeriesPoint::getUnixTime));
        int idx = ordered.indexOf(last);
        if (idx <= 0) return 0.0;
        int from = Math.max(0, idx - 3);
        double prod = 1.0;
        int n = 0;
        for (int i = from + 1; i <= idx; i++) {
            EconomySeriesPoint a = ordered.get(i - 1);
            EconomySeriesPoint b = ordered.get(i);
            if (a.getValue() == null || a.getValue().signum() <= 0
                    || b.getValue() == null || b.getValue().signum() <= 0) {
                continue;
            }
            prod *= b.getValue().doubleValue() / a.getValue().doubleValue();
            n++;
        }
        if (n <= 0) return 0.0;
        return Math.pow(prod, 1.0 / n) - 1.0;
    }

    /**
     * İki tarih arasındaki birikimli enflasyon faktörü: {@code CPI(to) / CPI(from)}.
     * İnterpolasyonlu — aynı ay içinde bile farklı günler için farklı değer döner;
     * günlük/haftalık performance & what-if çizgilerinde gerekli (aksi halde aylık seri
     * dailyleri tek bir noktaya snap'leyip çizgiyi düzleştirir).
     */
    public Optional<BigDecimal> cumulativeFactor(List<EconomySeriesPoint> series,
                                                 LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            return Optional.empty();
        }
        Optional<BigDecimal> base = indexValueInterpolated(series, from);
        Optional<BigDecimal> at = indexValueInterpolated(series, to);
        if (base.isEmpty() || at.isEmpty() || base.get().signum() <= 0) {
            return Optional.empty();
        }
        return Optional.of(at.get().divide(base.get(), MathContext.DECIMAL64));
    }

    /** unixTime'a göre verilen tarihe en yakın noktayı bulur. */
    private EconomySeriesPoint nearest(List<EconomySeriesPoint> series, LocalDate date) {
        long target = date.atStartOfDay(TR_ZONE).toEpochSecond();
        EconomySeriesPoint best = null;
        long bestDiff = Long.MAX_VALUE;
        for (EconomySeriesPoint p : series) {
            long diff = Math.abs(p.getUnixTime() - target);
            if (diff < bestDiff) {
                bestDiff = diff;
                best = p;
            }
        }
        return best;
    }
}
