package com.finance.portal.market.application.economy;

import com.finance.portal.market.application.economy.model.EconomyIndicator;
import com.finance.portal.market.application.economy.model.EconomySeriesPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Türkiye ekonomisi özet servisi — {@link EconomyIndicatorDef} kataloğundaki tüm
 * göstergeleri TCMB EVDS'ten çeker, son değer + dönemsel/yıllık değişimleri hesaplar.
 *
 * <p>Göstergeler EVDS'ten paralel çekilir (EVDS executor'ı paylaşılır). Tek bir serinin
 * hatası diğerlerini etkilemez; başarısız gösterge {@code available=false} döner.
 *
 * <p>Sonuç {@code market.economy} cache'inde tutulur (varsayılan 6 saat) — veri günlük/aylık
 * yayınlandığı için sık yenilemeye gerek yoktur.
 */
@Service
public class EconomyService {

    private static final Logger log = LoggerFactory.getLogger(EconomyService.class);

    public static final String SOURCE = "TCMB EVDS";

    /** ~1 yıl (saniye) — yıllık (YoY) karşılaştırmada hedef nokta için. */
    private static final long ONE_YEAR_SECONDS = 31_557_600L;
    /** YoY hedef noktasına kabul edilebilir maksimum sapma (~31 gün). */
    private static final long YOY_TOLERANCE_SECONDS = 2_678_400L;

    private final EconomySeriesGateway seriesGateway;
    private final ExecutorService evdsFetchExecutor;

    public EconomyService(EconomySeriesGateway seriesGateway,
                          @Qualifier("evdsBondFetchExecutor") ExecutorService evdsFetchExecutor) {
        this.seriesGateway = seriesGateway;
        this.evdsFetchExecutor = evdsFetchExecutor;
    }

    /**
     * Tüm ekonomi göstergelerini katalog sırasında döndürür (panelde gruplanır).
     * Cache TTL: 6 saat (yapılandırılabilir).
     */
    @Cacheable(cacheNames = "market.economy", key = "'summary'")
    public List<EconomyIndicator> getSummary() {
        log.info("[EconomyService] getSummary başlatıldı — {} gösterge", EconomyIndicatorDef.values().length);

        List<CompletableFuture<EconomyIndicator>> futures = new ArrayList<>();
        for (EconomyIndicatorDef def : EconomyIndicatorDef.values()) {
            futures.add(CompletableFuture.supplyAsync(() -> buildIndicator(def), evdsFetchExecutor));
        }

        List<EconomyIndicator> result = new ArrayList<>(futures.size());
        for (CompletableFuture<EconomyIndicator> f : futures) {
            result.add(f.join());
        }

        long ok = result.stream().filter(EconomyIndicator::isAvailable).count();
        log.info("[EconomyService] getSummary tamamlandı — {}/{} gösterge dolu", ok, result.size());
        return result;
    }

    private EconomyIndicator buildIndicator(EconomyIndicatorDef def) {
        EconomyIndicator ind = new EconomyIndicator();
        ind.setKey(def.getKey());
        ind.setLabel(def.getLabel());
        ind.setCategory(def.getCategory().name());
        ind.setCategoryLabel(def.getCategory().getLabel());
        ind.setUnit(def.getUnit());
        ind.setFrequency(def.getFrequency().name());
        ind.setSeriesCode(def.getSeriesCode());
        ind.setAvailable(false);

        try {
            LocalDate end = LocalDate.now();
            LocalDate start = end.minusMonths(def.getFrequency().getLookbackMonths());
            List<EconomySeriesPoint> points = seriesGateway.fetch(def, start, end);

            if (points.isEmpty()) {
                log.debug("[EconomyService] {} için veri yok", def.getSeriesCode());
                return ind;
            }

            EconomySeriesPoint latest = points.get(points.size() - 1);
            ind.setValue(latest.getValue());
            ind.setPeriod(latest.getPeriod());
            ind.setPreferAbsolute(def.isPreferAbsolute());
            ind.setAvailable(true);

            if (points.size() >= 2) {
                EconomySeriesPoint prev = points.get(points.size() - 2);
                ind.setPreviousValue(prev.getValue());
                ind.setChangePercent(percentChange(latest.getValue(), prev.getValue()));
                ind.setAbsoluteChange(latest.getValue().subtract(prev.getValue()));
            }

            if (def.isYoyMeaningful()) {
                EconomySeriesPoint yearAgo = findYearAgo(points, latest);
                if (yearAgo != null) {
                    ind.setYoyChangePercent(percentChange(latest.getValue(), yearAgo.getValue()));
                }
            }
        } catch (Exception e) {
            log.warn("[EconomyService] {} hesaplanırken hata: {}", def.getKey(), e.getMessage());
            ind.setAvailable(false);
        }
        return ind;
    }

    /** Latest'ten ~1 yıl önceki noktayı (unixTime'a göre en yakın) bulur; tolerans dışıysa null. */
    private EconomySeriesPoint findYearAgo(List<EconomySeriesPoint> points, EconomySeriesPoint latest) {
        long target = latest.getUnixTime() - ONE_YEAR_SECONDS;
        EconomySeriesPoint best = null;
        long bestDiff = Long.MAX_VALUE;
        for (EconomySeriesPoint p : points) {
            if (p == latest) continue;
            long diff = Math.abs(p.getUnixTime() - target);
            if (diff < bestDiff) {
                bestDiff = diff;
                best = p;
            }
        }
        return (best != null && bestDiff <= YOY_TOLERANCE_SECONDS) ? best : null;
    }

    /** (current / base - 1) * 100, 2 ondalık. base sıfır/null ise null. */
    private BigDecimal percentChange(BigDecimal current, BigDecimal base) {
        if (current == null || base == null || base.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return current.subtract(base)
                .divide(base, MathContext.DECIMAL64)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
