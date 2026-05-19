package com.finance.portal.market.application.crypto;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * CoinGecko günlük market_chart serisini haftalık / aylık örnekler.
 */
public final class CryptoChartAggregator {

    private static final ZoneId UTC = ZoneId.of("UTC");

    private CryptoChartAggregator() {
    }

 	@SuppressWarnings("unchecked")
    public static Map<String, Object> aggregate(Map<String, Object> chart, String mode) {
        if (chart == null || chart.isEmpty()) {
            return chart;
        }
        List<List<Number>> prices = (List<List<Number>>) chart.get("prices");
        List<List<Number>> volumes = (List<List<Number>>) chart.get("total_volumes");
        if (prices == null || prices.isEmpty()) {
            return chart;
        }

        boolean monthly = "monthly".equalsIgnoreCase(mode);
        List<List<Number>> aggPrices = bucketSeries(prices, monthly);
        List<List<Number>> aggVolumes = volumes != null && !volumes.isEmpty()
                ? bucketSeries(volumes, monthly, true)
                : List.of();

        Map<String, Object> result = new LinkedHashMap<>(chart);
        result.put("prices", aggPrices);
        if (!aggVolumes.isEmpty()) {
            result.put("total_volumes", aggVolumes);
        }
        return result;
    }

    private static List<List<Number>> bucketSeries(List<List<Number>> series, boolean monthly) {
        return bucketSeries(series, monthly, false);
    }

    private static List<List<Number>> bucketSeries(List<List<Number>> series, boolean monthly, boolean sumValues) {
        Map<String, List<Number>> buckets = new LinkedHashMap<>();
        WeekFields weekFields = WeekFields.of(Locale.ROOT);

        for (List<Number> point : series) {
            if (point == null || point.size() < 2) {
                continue;
            }
            long ts = point.get(0).longValue();
            double val = point.get(1).doubleValue();
            ZonedDateTime zdt = Instant.ofEpochMilli(ts).atZone(UTC);
            String key = monthly
                    ? zdt.getYear() + "-" + zdt.getMonthValue()
                    : zdt.getYear() + "-W" + zdt.get(weekFields.weekOfWeekBasedYear());

            buckets.merge(key, List.of((double) ts, val), (prev, cur) -> {
                if (sumValues) {
                    double sum = prev.get(1).doubleValue() + cur.get(1).doubleValue();
                    return List.of(cur.get(0), sum);
                }
                return cur.get(0).longValue() >= prev.get(0).longValue() ? cur : prev;
            });
        }

        List<List<Number>> out = new ArrayList<>();
        for (List<Number> v : buckets.values()) {
            out.add(List.of(v.get(0).longValue(), v.get(1)));
        }
        return out;
    }
}
