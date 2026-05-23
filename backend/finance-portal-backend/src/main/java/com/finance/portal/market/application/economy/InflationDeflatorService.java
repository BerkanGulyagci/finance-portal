package com.finance.portal.market.application.economy;

import com.finance.portal.market.application.economy.model.EconomySeriesPoint;
import com.finance.portal.market.application.economy.port.EconomyDataPort;
import com.finance.portal.market.application.economy.port.FredDataPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
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

    private final EconomyDataPort economyDataPort;
    private final FredDataPort fredDataPort;

    public InflationDeflatorService(EconomyDataPort economyDataPort, FredDataPort fredDataPort) {
        this.economyDataPort = economyDataPort;
        this.fredDataPort = fredDataPort;
    }

    /**
     * Aylık TÜFE endeks serisini döndürür (unixTime'a göre artan). Cache TTL: economy ile aynı.
     * <p>Çağıran bunu bir kez alıp {@link #cumulativeFactor} ile tekrar tekrar kullanmalıdır.
     */
    @Cacheable(cacheNames = "market.economy", key = "'tufe.series'")
    public List<EconomySeriesPoint> tufeSeries() {
        List<EconomySeriesPoint> pts = economyDataPort.fetchSeries(TUFE_CODE, SERIES_START, LocalDate.now());
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
        List<EconomySeriesPoint> pts = fredDataPort.fetchSeries(US_CPI_CODE, SERIES_START, LocalDate.now());
        log.info("[InflationDeflator] ABD CPI serisi yüklendi — {} nokta", pts.size());
        return pts;
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
