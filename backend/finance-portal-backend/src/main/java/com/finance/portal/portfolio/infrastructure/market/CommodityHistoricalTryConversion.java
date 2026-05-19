package com.finance.portal.portfolio.infrastructure.market;

import com.finance.portal.market.application.fx.model.FxHistoryPoint;
import com.finance.portal.market.application.fx.port.TcmbFxHistoryPort;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Yahoo emtia tarihsel USD kapanışlarını portföy para birimi (TRY) ile uyumlu hale getirir.
 * Spot taraftaki {@code applyTryDisplayPrices} ile aynı TCMB satış (ForexSelling) mantığı.
 */
public final class CommodityHistoricalTryConversion {

    private static final int TRY_PRICE_SCALE = 6;

    private CommodityHistoricalTryConversion() {
    }

    /**
     * TCMB günlük USD/TRY satış kurları (iş günleri). Boş dönebilir.
     */
    public static NavigableMap<LocalDate, BigDecimal> loadUsdTryHistory(
            TcmbFxHistoryPort port,
            LocalDate from,
            LocalDate to) {

        NavigableMap<LocalDate, BigDecimal> map = new TreeMap<>();
        if (port == null || from == null || to == null || from.isAfter(to)) {
            return map;
        }

        List<FxHistoryPoint> points = port.fetchHistory("USD", from, to);
        if (points == null) {
            return map;
        }

        for (FxHistoryPoint p : points) {
            if (p.getDate() == null || p.getClose() == null) {
                continue;
            }
            if (p.getClose().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            try {
                LocalDate day = LocalDate.parse(p.getDate().substring(0, Math.min(10, p.getDate().length())));
                map.put(day, p.getClose());
            } catch (Exception ignored) {
                // skip malformed date
            }
        }
        return map;
    }

    /**
     * USD kapanışı TRY'ye çevirir.
     * <p>
     * Önce {@code usdTryByDay} içinde o güne kadar forward-fill (floorEntry);
     * yoksa {@code latestUsdTryFallback} (MVP: güncel TCMB satış).
     */
    public static BigDecimal convertUsdCloseToTry(
            BigDecimal usdClose,
            LocalDate day,
            NavigableMap<LocalDate, BigDecimal> usdTryByDay,
            BigDecimal latestUsdTryFallback) {

        if (usdClose == null || usdClose.compareTo(BigDecimal.ZERO) <= 0 || day == null) {
            return null;
        }

        BigDecimal rate = resolveUsdTryRate(day, usdTryByDay, latestUsdTryFallback);
        if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        return usdClose.multiply(rate).setScale(TRY_PRICE_SCALE, RoundingMode.HALF_UP);
    }

    static BigDecimal resolveUsdTryRate(
            LocalDate day,
            NavigableMap<LocalDate, BigDecimal> usdTryByDay,
            BigDecimal latestUsdTryFallback) {

        if (usdTryByDay != null && !usdTryByDay.isEmpty()) {
            var entry = usdTryByDay.floorEntry(day);
            if (entry != null
                    && entry.getValue() != null
                    && entry.getValue().compareTo(BigDecimal.ZERO) > 0) {
                return entry.getValue();
            }
        }
        return latestUsdTryFallback;
    }
}
