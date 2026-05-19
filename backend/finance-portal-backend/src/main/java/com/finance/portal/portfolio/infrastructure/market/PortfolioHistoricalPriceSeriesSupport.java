package com.finance.portal.portfolio.infrastructure.market;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Tarihsel fiyat serilerini günlük {@link NavigableMap} formatına dönüştürür.
 */
final class PortfolioHistoricalPriceSeriesSupport {

    static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");
    private static final String REASON_NOT_FOUND = "Historical price not found";

    private PortfolioHistoricalPriceSeriesSupport() {
    }

    static NavigableMap<LocalDate, BigDecimal> emptyMap() {
        return new TreeMap<>();
    }

    static NavigableMap<LocalDate, BigDecimal> fromEpochCloses(List<Long> timestamps, List<BigDecimal> closes) {
        NavigableMap<LocalDate, BigDecimal> map = new TreeMap<>();
        if (timestamps == null || closes == null) {
            return map;
        }
        int n = Math.min(timestamps.size(), closes.size());
        for (int i = 0; i < n; i++) {
            Long ts = timestamps.get(i);
            BigDecimal close = closes.get(i);
            if (ts == null || close == null) {
                continue;
            }
            LocalDate day = Instant.ofEpochSecond(ts).atZone(ISTANBUL).toLocalDate();
            map.put(day, close);
        }
        return map;
    }

    static NavigableMap<LocalDate, BigDecimal> fromDateStringCloses(
            List<String> dates, List<BigDecimal> closes) {
        NavigableMap<LocalDate, BigDecimal> map = new TreeMap<>();
        if (dates == null || closes == null) {
            return map;
        }
        int n = Math.min(dates.size(), closes.size());
        for (int i = 0; i < n; i++) {
            String ds = dates.get(i);
            BigDecimal close = closes.get(i);
            if (ds == null || close == null) {
                continue;
            }
            try {
                LocalDate day = LocalDate.parse(ds.length() >= 10 ? ds.substring(0, 10) : ds);
                map.put(day, close);
            } catch (Exception ignored) {
                // skip malformed
            }
        }
        return map;
    }

    static void putIfInRange(NavigableMap<LocalDate, BigDecimal> target,
                             LocalDate day,
                             BigDecimal price,
                             LocalDate from,
                             LocalDate to) {
        if (day == null || price == null) {
            return;
        }
        if (!day.isBefore(from) && !day.isAfter(to)) {
            target.put(day, price);
        }
    }

    @SuppressWarnings("unchecked")
    static NavigableMap<LocalDate, BigDecimal> fromCoinGeckoChart(Map<String, Object> chart) {
        NavigableMap<LocalDate, BigDecimal> map = new TreeMap<>();
        if (chart == null) {
            return map;
        }
        Object raw = chart.get("prices");
        if (!(raw instanceof List<?> rows)) {
            return map;
        }
        for (Object rowObj : rows) {
            if (!(rowObj instanceof List<?> row) || row.size() < 2) {
                continue;
            }
            Number tsNum = row.get(0) instanceof Number n ? n : null;
            Number priceNum = row.get(1) instanceof Number n ? n : null;
            if (tsNum == null || priceNum == null) {
                continue;
            }
            LocalDate day = Instant.ofEpochMilli(tsNum.longValue()).atZone(ISTANBUL).toLocalDate();
            map.put(day, BigDecimal.valueOf(priceNum.doubleValue()));
        }
        return map;
    }

    static NavigableMap<LocalDate, BigDecimal> lastClosePerDayFromViopPoints(
            List<com.finance.portal.market.application.viop.model.ViopChartPoint> points) {
        NavigableMap<LocalDate, BigDecimal> map = new TreeMap<>();
        if (points == null) {
            return map;
        }
        for (var pt : points) {
            if (pt.getValue() == null) {
                continue;
            }
            LocalDate day = null;
            if (pt.getTimestamp() != null) {
                day = Instant.ofEpochMilli(pt.getTimestamp()).atZone(ISTANBUL).toLocalDate();
            } else if (pt.getDateTime() != null && pt.getDateTime().length() >= 10) {
                try {
                    day = LocalDate.parse(pt.getDateTime().substring(0, 10));
                } catch (Exception ignored) {
                    // skip
                }
            }
            if (day != null) {
                map.put(day, pt.getValue());
            }
        }
        return map;
    }

    static String notFoundReason() {
        return REASON_NOT_FOUND;
    }
}
