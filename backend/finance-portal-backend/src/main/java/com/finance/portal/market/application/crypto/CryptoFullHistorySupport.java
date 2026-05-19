package com.finance.portal.market.application.crypto;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * CoinGecko days=max bazen yalnızca ~365 gün döndürür; genesis → bugün range ile tam geçmiş.
 */
final class CryptoFullHistorySupport {

    private static final long MIN_FULL_HISTORY_SPAN_DAYS = 500;
    private static final int MIN_FULL_HISTORY_POINTS = 400;
    private static final long DEFAULT_GENESIS_EPOCH_SEC = Instant.parse("2010-01-01T00:00:00Z").getEpochSecond();

    private CryptoFullHistorySupport() {
    }

    static boolean hasChartData(Map<String, Object> chart) {
        List<List<Number>> prices = extractPrices(chart);
        return prices != null && !prices.isEmpty();
    }

    static boolean isAdequateChartHistory(Map<String, Object> chart) {
        List<List<Number>> prices = extractPrices(chart);
        if (prices == null || prices.size() < MIN_FULL_HISTORY_POINTS) {
            return false;
        }
        long earliestMs = earliestPriceMs(prices);
        if (earliestMs <= 0) {
            return false;
        }
        long spanDays = (System.currentTimeMillis() - earliestMs) / 86_400_000L;
        return spanDays >= MIN_FULL_HISTORY_SPAN_DAYS;
    }

    static boolean hasOhlcData(List<List<Number>> rows) {
        return rows != null && !rows.isEmpty();
    }

    static boolean isAdequateOhlcHistory(List<List<Number>> rows) {
        if (rows == null || rows.size() < 80) {
            return false;
        }
        long earliestSec = earliestOhlcSec(rows);
        if (earliestSec <= 0) {
            return false;
        }
        long spanDays = (Instant.now().getEpochSecond() - earliestSec) / 86_400L;
        return spanDays >= MIN_FULL_HISTORY_SPAN_DAYS;
    }

    static long resolveGenesisEpochSeconds(Map<String, Object> detail) {
        if (detail == null || detail.isEmpty()) {
            return DEFAULT_GENESIS_EPOCH_SEC;
        }
        Object gd = detail.get("genesis_date");
        if (gd instanceof String s && !s.isBlank()) {
            try {
                return Instant.parse(s.trim()).getEpochSecond();
            } catch (Exception ignored) {
                /* varsayılan */
            }
        }
        return DEFAULT_GENESIS_EPOCH_SEC;
    }

    static Map<String, Object> mergeCharts(Map<String, Object> base, Map<String, Object> add) {
        if (!hasChartData(add)) {
            return base != null ? base : Map.of();
        }
        if (!hasChartData(base)) {
            return add;
        }

        TreeMap<Long, Double> prices = new TreeMap<>();
        TreeMap<Long, Double> volumes = new TreeMap<>();
        ingestChartSeries(base, prices, volumes);
        ingestChartSeries(add, prices, volumes);

        List<List<Number>> priceList = new ArrayList<>(prices.size());
        for (Map.Entry<Long, Double> e : prices.entrySet()) {
            priceList.add(List.of(e.getKey(), e.getValue()));
        }
        List<List<Number>> volList = new ArrayList<>(prices.size());
        for (Long ts : prices.keySet()) {
            volList.add(List.of(ts, volumes.getOrDefault(ts, 0.0)));
        }

        Map<String, Object> merged = new LinkedHashMap<>();
        merged.put("prices", priceList);
        merged.put("total_volumes", volList);
        return merged;
    }

    private static final ZoneId UTC = ZoneId.of("UTC");

    /**
     * Saatlik market_chart → günlük OHLC (3A / 6A mum grafikleri için gerçek fitil).
     * CoinGecko /ohlc formatı: [timestampMs, open, high, low, close].
     */
    static List<List<Number>> marketChartToDailyOhlc(Map<String, Object> chart) {
        List<List<Number>> prices = extractPrices(chart);
        if (prices == null || prices.isEmpty()) {
            return List.of();
        }

        TreeMap<Long, DailyOhlcBucket> buckets = new TreeMap<>();
        for (List<Number> point : prices) {
            if (point == null || point.size() < 2) {
                continue;
            }
            long tsMs = normalizeToMs(point.get(0).longValue());
            double price = point.get(1).doubleValue();
            if (price <= 0) {
                continue;
            }
            long dayKey = toUtcDayKey(tsMs);
            buckets.computeIfAbsent(dayKey, k -> new DailyOhlcBucket())
                    .add(tsMs, price);
        }

        List<List<Number>> rows = new ArrayList<>(buckets.size());
        for (DailyOhlcBucket bucket : buckets.values()) {
            rows.add(List.of(
                    (double) bucket.candleTimeMs(),
                    bucket.open,
                    bucket.high,
                    bucket.low,
                    bucket.close
            ));
        }
        return rows;
    }

    private static long normalizeToMs(long ts) {
        return ts < 1_000_000_000_000L ? ts * 1000L : ts;
    }

    private static long toUtcDayKey(long tsMs) {
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(tsMs), UTC).toLocalDate().toEpochDay();
    }

    private static final class DailyOhlcBucket {
        long firstTsMs = Long.MAX_VALUE;
        long lastTsMs = Long.MIN_VALUE;
        double open;
        double high = Double.NEGATIVE_INFINITY;
        double low = Double.POSITIVE_INFINITY;
        double close;

        void add(long tsMs, double price) {
            if (tsMs < firstTsMs) {
                firstTsMs = tsMs;
                open = price;
            }
            if (tsMs >= lastTsMs) {
                lastTsMs = tsMs;
                close = price;
            }
            high = Math.max(high, price);
            low = Math.min(low, price);
        }

        long candleTimeMs() {
            return firstTsMs;
        }
    }

    static List<List<Number>> mergeOhlc(List<List<Number>> base, List<List<Number>> add) {
        if (add == null || add.isEmpty()) {
            return base != null ? base : List.of();
        }
        if (base == null || base.isEmpty()) {
            return add;
        }
        TreeMap<Long, List<Number>> byTs = new TreeMap<>();
        for (List<Number> row : base) {
            putOhlcRow(byTs, row);
        }
        for (List<Number> row : add) {
            putOhlcRow(byTs, row);
        }
        return new ArrayList<>(byTs.values());
    }

    private static void putOhlcRow(TreeMap<Long, List<Number>> byTs, List<Number> row) {
        if (row == null || row.size() < 5) {
            return;
        }
        long ts = row.get(0).longValue();
        if (ts < 1_000_000_000_000L) {
            ts *= 1000L;
        }
        byTs.put(ts, List.of(ts, row.get(1), row.get(2), row.get(3), row.get(4)));
    }

    private static void ingestChartSeries(Map<String, Object> chart, TreeMap<Long, Double> prices, TreeMap<Long, Double> volumes) {
        List<List<Number>> priceRows = extractPrices(chart);
        if (priceRows == null) {
            return;
        }
        for (List<Number> p : priceRows) {
            if (p == null || p.size() < 2) {
                continue;
            }
            long ts = p.get(0).longValue();
            if (ts < 1_000_000_000_000L) {
                ts *= 1000L;
            }
            prices.put(ts, p.get(1).doubleValue());
        }
        List<List<Number>> volRows = extractVolumes(chart);
        if (volRows == null) {
            return;
        }
        for (List<Number> v : volRows) {
            if (v == null || v.size() < 2) {
                continue;
            }
            long ts = v.get(0).longValue();
            if (ts < 1_000_000_000_000L) {
                ts *= 1000L;
            }
            volumes.merge(ts, v.get(1).doubleValue(), Double::sum);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<List<Number>> extractPrices(Map<String, Object> chart) {
        if (chart == null) {
            return null;
        }
        Object raw = chart.get("prices");
        if (raw instanceof List<?> list) {
            return (List<List<Number>>) raw;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<List<Number>> extractVolumes(Map<String, Object> chart) {
        if (chart == null) {
            return null;
        }
        Object raw = chart.get("total_volumes");
        if (raw instanceof List<?> list) {
            return (List<List<Number>>) raw;
        }
        return null;
    }

    private static long earliestPriceMs(List<List<Number>> prices) {
        long min = Long.MAX_VALUE;
        for (List<Number> point : prices) {
            if (point == null || point.isEmpty()) {
                continue;
            }
            long ts = point.get(0).longValue();
            if (ts < 1_000_000_000_000L) {
                ts *= 1000L;
            }
            if (ts < min) {
                min = ts;
            }
        }
        return min == Long.MAX_VALUE ? 0 : min;
    }

    private static long earliestOhlcSec(List<List<Number>> rows) {
        long min = Long.MAX_VALUE;
        for (List<Number> row : rows) {
            if (row == null || row.isEmpty()) {
                continue;
            }
            long ts = row.get(0).longValue();
            long sec = ts < 1_000_000_000_000L ? ts : ts / 1000L;
            if (sec < min) {
                min = sec;
            }
        }
        return min == Long.MAX_VALUE ? 0 : min;
    }
}
