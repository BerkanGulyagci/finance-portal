package com.finance.portal.market.application.economy;

import com.finance.portal.market.application.economy.model.EconomyChartPoint;
import com.finance.portal.market.application.economy.model.EconomyChartSeries;
import com.finance.portal.market.application.economy.model.EconomySeriesPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Türkiye ekonomisi göstergeleri için grafik (zaman serisi) servisi.
 *
 * <p>Özet servisi son değeri verir; bu servis bülten/rapor sayfasındaki grafikler için
 * zaman serisini üretir. Endeks/GSYİH gibi {@code yoyMeaningful} göstergelerde yıllık
 * (% YoY) seri; oran/kur/akım göstergelerinde ham seri döner. Günlük/haftalık seriler
 * aylık son-değere indirgenir (temiz grafik için).
 */
@Service
public class EconomyChartService {

    private static final Logger log = LoggerFactory.getLogger(EconomyChartService.class);

    private static final String SOURCE = "TCMB EVDS";
    private static final long ONE_YEAR_SECONDS = 31_557_600L;
    private static final long YOY_TOLERANCE_SECONDS = 3_888_000L; // ~45 gün
    private static final ZoneId TR_ZONE = ZoneId.of("Europe/Istanbul");

    private final EconomySeriesGateway seriesGateway;

    public EconomyChartService(EconomySeriesGateway seriesGateway) {
        this.seriesGateway = seriesGateway;
    }

    /**
     * Tüm göstergelerin grafik serilerini tek seferde döndürür (rapor sayfası için).
     * Cache TTL: economy ile aynı (6 saat).
     */
    @Cacheable(cacheNames = "market.economy", key = "'charts.all'")
    public List<EconomyChartSeries> getAllChartSeries() {
        List<EconomyChartSeries> all = new ArrayList<>();
        for (EconomyIndicatorDef def : EconomyIndicatorDef.values()) {
            all.add(buildChartSeries(def));
        }
        long ok = all.stream().filter(s -> s.getPoints() != null && !s.getPoints().isEmpty()).count();
        log.info("[EconomyChart] getAllChartSeries → {}/{} seri dolu", ok, all.size());
        return all;
    }

    /**
     * Tek göstergenin grafik serisi. Bilinmeyen anahtar → boş seri.
     */
    @Cacheable(cacheNames = "market.economy", key = "'chart.' + #key")
    public EconomyChartSeries getChartSeries(String key) {
        EconomyIndicatorDef def = findByKey(key);
        if (def == null) {
            log.warn("[EconomyChart] bilinmeyen gösterge anahtarı: {}", key);
            return new EconomyChartSeries(key, key, "", "", "raw", SOURCE, List.of());
        }
        return buildChartSeries(def);
    }

    // ── Çekirdek hesap ──────────────────────────────────────────────────────

    private EconomyChartSeries buildChartSeries(EconomyIndicatorDef def) {
        EconomyIndicatorDef.Frequency freq = def.getFrequency();
        boolean intraday = freq == EconomyIndicatorDef.Frequency.DAILY
                || freq == EconomyIndicatorDef.Frequency.WEEKLY;
        boolean yoy = def.isYoyMeaningful();

        LocalDate end = LocalDate.now();
        LocalDate start = end.minusMonths(fetchMonths(freq, yoy));
        List<EconomySeriesPoint> raw = seriesGateway.fetch(def, start, end);
        String source = sourceLabel(def);
        if (raw.isEmpty()) {
            return new EconomyChartSeries(def.getKey(), def.getLabel(), def.getUnit(),
                    freq.name(), yoy ? "yoy" : "raw", source, List.of());
        }

        List<EconomySeriesPoint> base = intraday ? downsampleMonthly(raw) : raw;

        String transform;
        String unit;
        List<EconomyChartPoint> points;
        if (yoy) {
            transform = "yoy";
            unit = "%";
            points = yoySeries(base);
        } else {
            transform = "raw";
            unit = def.getUnit();
            points = new ArrayList<>(base.size());
            for (EconomySeriesPoint p : base) {
                points.add(new EconomyChartPoint(p.getPeriod(), p.getValue()));
            }
        }

        points = lastN(points, maxPoints(freq, yoy));
        return new EconomyChartSeries(def.getKey(), def.getLabel(), unit, freq.name(),
                transform, source, points);
    }

    // ── Yardımcılar ───────────────────────────────────────────────────────────

    /** Göstergenin kaynağına göre etiket — grafik altında gösterilir. */
    private static String sourceLabel(EconomyIndicatorDef def) {
        return def.getSource() == EconomyIndicatorDef.Source.FRED ? "FRED (St. Louis Fed)" : SOURCE;
    }

    private EconomyIndicatorDef findByKey(String key) {
        for (EconomyIndicatorDef d : EconomyIndicatorDef.values()) {
            if (d.getKey().equals(key)) return d;
        }
        return null;
    }

    private int fetchMonths(EconomyIndicatorDef.Frequency freq, boolean yoy) {
        return switch (freq) {
            case DAILY, WEEKLY -> 15;
            case MONTHLY -> yoy ? 30 : 15;
            case QUARTERLY -> yoy ? 39 : 30;
            case YEARLY -> 160;
        };
    }

    private int maxPoints(EconomyIndicatorDef.Frequency freq, boolean yoy) {
        return switch (freq) {
            case DAILY, WEEKLY -> 13;
            case MONTHLY -> yoy ? 13 : 14;
            case QUARTERLY -> yoy ? 6 : 8;
            case YEARLY -> 11;
        };
    }

    /** Yıllık (% YoY) seri: her nokta için ~1 yıl önceki noktayla oran. */
    private List<EconomyChartPoint> yoySeries(List<EconomySeriesPoint> points) {
        List<EconomyChartPoint> result = new ArrayList<>();
        for (EconomySeriesPoint p : points) {
            EconomySeriesPoint prior = nearestTo(points, p.getUnixTime() - ONE_YEAR_SECONDS, p);
            if (prior == null || prior.getValue() == null || prior.getValue().signum() <= 0) continue;
            long diff = Math.abs(prior.getUnixTime() - (p.getUnixTime() - ONE_YEAR_SECONDS));
            if (diff > YOY_TOLERANCE_SECONDS) continue;
            BigDecimal yoy = p.getValue()
                    .divide(prior.getValue(), MathContext.DECIMAL64)
                    .subtract(BigDecimal.ONE)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);
            result.add(new EconomyChartPoint(p.getPeriod(), yoy));
        }
        return result;
    }

    private EconomySeriesPoint nearestTo(List<EconomySeriesPoint> points, long target, EconomySeriesPoint exclude) {
        EconomySeriesPoint best = null;
        long bestDiff = Long.MAX_VALUE;
        for (EconomySeriesPoint p : points) {
            if (p == exclude) continue;
            long diff = Math.abs(p.getUnixTime() - target);
            if (diff < bestDiff) {
                bestDiff = diff;
                best = p;
            }
        }
        return best;
    }

    /** Günlük/haftalık seriyi her ayın son değerine indirger (period → "yyyy-M"). */
    private List<EconomySeriesPoint> downsampleMonthly(List<EconomySeriesPoint> points) {
        Map<YearMonth, EconomySeriesPoint> byMonth = new LinkedHashMap<>();
        for (EconomySeriesPoint p : points) {
            YearMonth ym = YearMonth.from(Instant.ofEpochSecond(p.getUnixTime()).atZone(TR_ZONE));
            byMonth.put(ym, p);
        }
        List<EconomySeriesPoint> result = new ArrayList<>(byMonth.size());
        for (Map.Entry<YearMonth, EconomySeriesPoint> e : byMonth.entrySet()) {
            EconomySeriesPoint p = e.getValue();
            String period = e.getKey().getYear() + "-" + e.getKey().getMonthValue();
            result.add(new EconomySeriesPoint(period, p.getValue(), p.getUnixTime()));
        }
        return result;
    }

    private <T> List<T> lastN(List<T> list, int n) {
        if (list.size() <= n) return list;
        return new ArrayList<>(list.subList(list.size() - n, list.size()));
    }
}
