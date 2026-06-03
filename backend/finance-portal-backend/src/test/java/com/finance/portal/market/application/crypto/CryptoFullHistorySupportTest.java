package com.finance.portal.market.application.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link CryptoFullHistorySupport}. The class is a package-private
 * static utility (private constructor, no dependencies) so we exercise each static
 * method directly through happy paths and the visible branches.
 */
class CryptoFullHistorySupportTest {

    private static final long DAY_MS = 86_400_000L;

    /** Build a market_chart map with the given [tsMs, price] rows (and matching volumes). */
    private static Map<String, Object> chartFromRows(List<List<Number>> priceRows,
                                                     List<List<Number>> volumeRows) {
        Map<String, Object> chart = new HashMap<>();
        if (priceRows != null) {
            chart.put("prices", priceRows);
        }
        if (volumeRows != null) {
            chart.put("total_volumes", volumeRows);
        }
        return chart;
    }

    private static List<Number> row(Number... values) {
        return List.of(values);
    }

    // ---- hasChartData -------------------------------------------------------

    @Test
    @DisplayName("hasChartData: null chart, missing/non-list prices, and empty list are all false; populated is true")
    void hasChartData_branches() {
        assertThat(CryptoFullHistorySupport.hasChartData(null)).isFalse();

        // "prices" not present at all
        assertThat(CryptoFullHistorySupport.hasChartData(new HashMap<>())).isFalse();

        // "prices" present but not a List -> extractPrices returns null
        Map<String, Object> notAList = new HashMap<>();
        notAList.put("prices", "oops");
        assertThat(CryptoFullHistorySupport.hasChartData(notAList)).isFalse();

        // empty price list
        assertThat(CryptoFullHistorySupport.hasChartData(
                chartFromRows(new ArrayList<>(), null))).isFalse();

        // populated
        long now = System.currentTimeMillis();
        List<List<Number>> prices = List.of(row(now, 1.0));
        assertThat(CryptoFullHistorySupport.hasChartData(chartFromRows(prices, null))).isTrue();
    }

    // ---- isAdequateChartHistory --------------------------------------------

    @Test
    @DisplayName("isAdequateChartHistory: too few points or null chart -> false")
    void isAdequateChartHistory_tooFewPoints() {
        List<List<Number>> prices = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            prices.add(row(System.currentTimeMillis() - i * DAY_MS, 1.0 + i));
        }
        assertThat(CryptoFullHistorySupport.isAdequateChartHistory(chartFromRows(prices, null)))
                .isFalse();
        assertThat(CryptoFullHistorySupport.isAdequateChartHistory(null)).isFalse();
    }

    @Test
    @DisplayName("isAdequateChartHistory: enough points but recent span -> false; old span -> true")
    void isAdequateChartHistory_spanBranches() {
        long now = System.currentTimeMillis();

        // 400 points, all recent (last few days) -> span < 500 days -> false
        List<List<Number>> recent = new ArrayList<>();
        for (int i = 0; i < 400; i++) {
            recent.add(row(now - (i % 5) * DAY_MS, 1.0 + i));
        }
        assertThat(CryptoFullHistorySupport.isAdequateChartHistory(chartFromRows(recent, null)))
                .isFalse();

        // 400 points, earliest ~600 days ago -> span >= 500 -> true
        List<List<Number>> old = new ArrayList<>();
        long earliest = now - 600L * DAY_MS;
        old.add(row(earliest, 1.0));
        for (int i = 1; i < 400; i++) {
            old.add(row(now - (i % 5) * DAY_MS, 1.0 + i));
        }
        assertThat(CryptoFullHistorySupport.isAdequateChartHistory(chartFromRows(old, null)))
                .isTrue();
    }

    @Test
    @DisplayName("isAdequateChartHistory: 400+ points but all timestamps blank -> earliestMs<=0 -> false")
    void isAdequateChartHistory_noUsableTimestamp() {
        // Empty rows are skipped in earliestPriceMs, leaving min == Long.MAX_VALUE -> 0
        List<List<Number>> prices = new ArrayList<>();
        for (int i = 0; i < 400; i++) {
            prices.add(new ArrayList<>());
        }
        assertThat(CryptoFullHistorySupport.isAdequateChartHistory(chartFromRows(prices, null)))
                .isFalse();
    }

    // ---- hasOhlcData --------------------------------------------------------

    @Test
    @DisplayName("hasOhlcData: null/empty -> false; populated -> true")
    void hasOhlcData_branches() {
        assertThat(CryptoFullHistorySupport.hasOhlcData(null)).isFalse();
        assertThat(CryptoFullHistorySupport.hasOhlcData(new ArrayList<>())).isFalse();
        assertThat(CryptoFullHistorySupport.hasOhlcData(
                List.of(row(System.currentTimeMillis(), 1, 2, 0.5, 1.5)))).isTrue();
    }

    // ---- isAdequateOhlcHistory ---------------------------------------------

    @Test
    @DisplayName("isAdequateOhlcHistory: <80 rows false; old enough true; recent false; empty-ts rows false")
    void isAdequateOhlcHistory_branches() {
        // null and too-few
        assertThat(CryptoFullHistorySupport.isAdequateOhlcHistory(null)).isFalse();
        assertThat(CryptoFullHistorySupport.isAdequateOhlcHistory(
                List.of(row(System.currentTimeMillis(), 1, 2, 0.5, 1.5)))).isFalse();

        long nowSec = Instant.now().getEpochSecond();

        // 80 rows, earliest ~600 days ago (seconds) -> span >= 500 -> true
        List<List<Number>> oldRows = new ArrayList<>();
        oldRows.add(row(nowSec - 600L * 86_400L, 1, 2, 0.5, 1.5));
        for (int i = 1; i < 80; i++) {
            oldRows.add(row(nowSec - (i % 3) * 86_400L, 1, 2, 0.5, 1.5));
        }
        assertThat(CryptoFullHistorySupport.isAdequateOhlcHistory(oldRows)).isTrue();

        // 80 rows but all recent -> span < 500 -> false
        List<List<Number>> recentRows = new ArrayList<>();
        for (int i = 0; i < 80; i++) {
            recentRows.add(row(nowSec - (i % 3) * 86_400L, 1, 2, 0.5, 1.5));
        }
        assertThat(CryptoFullHistorySupport.isAdequateOhlcHistory(recentRows)).isFalse();

        // 80 empty rows -> earliestOhlcSec returns 0 -> false
        List<List<Number>> blankRows = new ArrayList<>();
        for (int i = 0; i < 80; i++) {
            blankRows.add(new ArrayList<>());
        }
        assertThat(CryptoFullHistorySupport.isAdequateOhlcHistory(blankRows)).isFalse();
    }

    // ---- resolveGenesisEpochSeconds ----------------------------------------

    @Test
    @DisplayName("resolveGenesisEpochSeconds: null/empty/blank/invalid/non-string -> default; valid date -> parsed")
    void resolveGenesisEpochSeconds_branches() {
        long defaultSec = Instant.parse("2010-01-01T00:00:00Z").getEpochSecond();

        assertThat(CryptoFullHistorySupport.resolveGenesisEpochSeconds(null)).isEqualTo(defaultSec);
        assertThat(CryptoFullHistorySupport.resolveGenesisEpochSeconds(new HashMap<>()))
                .isEqualTo(defaultSec);

        // blank string -> default
        Map<String, Object> blank = new HashMap<>();
        blank.put("genesis_date", "   ");
        assertThat(CryptoFullHistorySupport.resolveGenesisEpochSeconds(blank)).isEqualTo(defaultSec);

        // unparseable string -> default
        Map<String, Object> bad = new HashMap<>();
        bad.put("genesis_date", "not-a-date");
        assertThat(CryptoFullHistorySupport.resolveGenesisEpochSeconds(bad)).isEqualTo(defaultSec);

        // non-string value -> default
        Map<String, Object> nonString = new HashMap<>();
        nonString.put("genesis_date", 12345);
        assertThat(CryptoFullHistorySupport.resolveGenesisEpochSeconds(nonString))
                .isEqualTo(defaultSec);

        // valid ISO instant -> parsed
        Map<String, Object> good = new HashMap<>();
        good.put("genesis_date", "2013-04-28T00:00:00Z");
        long expected = Instant.parse("2013-04-28T00:00:00Z").getEpochSecond();
        assertThat(CryptoFullHistorySupport.resolveGenesisEpochSeconds(good)).isEqualTo(expected);
    }

    // ---- mergeCharts --------------------------------------------------------

    @Test
    @DisplayName("mergeCharts: empty add returns base (or Map.of when base null); empty base returns add")
    void mergeCharts_emptyHandling() {
        long now = System.currentTimeMillis();
        Map<String, Object> base = chartFromRows(List.of(row(now, 10.0)), List.of(row(now, 100.0)));
        Map<String, Object> emptyAdd = chartFromRows(new ArrayList<>(), null);

        // add empty -> returns base unchanged
        assertThat(CryptoFullHistorySupport.mergeCharts(base, emptyAdd)).isSameAs(base);

        // add empty AND base null -> Map.of()
        assertThat(CryptoFullHistorySupport.mergeCharts(null, emptyAdd)).isEmpty();

        // base empty, add populated -> returns add
        Map<String, Object> add = chartFromRows(List.of(row(now, 5.0)), null);
        assertThat(CryptoFullHistorySupport.mergeCharts(null, add)).isSameAs(add);
    }

    @Test
    @DisplayName("mergeCharts: both populated -> de-duped, sorted prices + total_volumes")
    void mergeCharts_merge() {
        long t1 = 1_600_000_000_000L; // ms
        long t2 = 1_600_086_400_000L; // ms (one day later)

        Map<String, Object> base = chartFromRows(
                List.of(row(t2, 20.0)),
                List.of(row(t2, 200.0)));
        Map<String, Object> add = chartFromRows(
                List.of(row(t1, 10.0)),
                List.of(row(t1, 100.0)));

        Map<String, Object> merged = CryptoFullHistorySupport.mergeCharts(base, add);

        @SuppressWarnings("unchecked")
        List<List<Number>> prices = (List<List<Number>>) merged.get("prices");
        @SuppressWarnings("unchecked")
        List<List<Number>> vols = (List<List<Number>>) merged.get("total_volumes");

        assertThat(prices).hasSize(2);
        // sorted ascending by timestamp
        assertThat(prices.get(0).get(0).longValue()).isEqualTo(t1);
        assertThat(prices.get(0).get(1).doubleValue()).isEqualTo(10.0);
        assertThat(prices.get(1).get(0).longValue()).isEqualTo(t2);
        assertThat(prices.get(1).get(1).doubleValue()).isEqualTo(20.0);

        assertThat(vols).hasSize(2);
        assertThat(vols.get(0).get(1).doubleValue()).isEqualTo(100.0);
        assertThat(vols.get(1).get(1).doubleValue()).isEqualTo(200.0);
    }

    @Test
    @DisplayName("mergeCharts: second-based timestamps normalised to ms; price w/o matching volume defaults to 0")
    void mergeCharts_normalisesSecondsAndMissingVolume() {
        long secTs = 1_600_000_000L;     // seconds -> *1000
        long expectedMs = secTs * 1000L;

        // base has only prices (no volumes) so the merged volume defaults to 0.0
        Map<String, Object> base = chartFromRows(List.of(row(secTs, 7.0)), null);
        Map<String, Object> add = chartFromRows(List.of(row(secTs + 1, 8.0)),
                List.of(row((secTs + 1) * 1000L, 99.0)));

        Map<String, Object> merged = CryptoFullHistorySupport.mergeCharts(base, add);

        @SuppressWarnings("unchecked")
        List<List<Number>> prices = (List<List<Number>>) merged.get("prices");
        @SuppressWarnings("unchecked")
        List<List<Number>> vols = (List<List<Number>>) merged.get("total_volumes");

        assertThat(prices).hasSize(2);
        assertThat(prices.get(0).get(0).longValue()).isEqualTo(expectedMs);
        // the first price (secTs) has no matching volume -> 0.0
        assertThat(vols.get(0).get(1).doubleValue()).isEqualTo(0.0);
        // the second price (secTs+1) matches the ms volume row -> 99.0
        assertThat(vols.get(1).get(1).doubleValue()).isEqualTo(99.0);
    }

    // ---- marketChartToDailyOhlc --------------------------------------------

    @Test
    @DisplayName("marketChartToDailyOhlc: null/empty -> empty list")
    void marketChartToDailyOhlc_empty() {
        assertThat(CryptoFullHistorySupport.marketChartToDailyOhlc(null)).isEmpty();
        assertThat(CryptoFullHistorySupport.marketChartToDailyOhlc(
                chartFromRows(new ArrayList<>(), null))).isEmpty();
    }

    @Test
    @DisplayName("marketChartToDailyOhlc: aggregates hourly points into one daily OHLC bucket, skipping bad rows")
    void marketChartToDailyOhlc_aggregatesDay() {
        // Several points on the same UTC day: 2021-01-01
        long base = Instant.parse("2021-01-01T00:00:00Z").toEpochMilli();
        List<List<Number>> prices = new ArrayList<>();
        prices.add(row(base, 10.0));                 // open (earliest)
        prices.add(row(base + 3_600_000L, 15.0));    // high
        prices.add(row(base + 7_200_000L, 12.0));    // close (latest)
        // null / short / non-positive rows are skipped
        prices.add(null);
        prices.add(row(base + 1_000L));              // size < 2 -> skipped
        prices.add(row(base + 2_000L, 0.0));         // price <= 0 -> skipped

        List<List<Number>> ohlc = CryptoFullHistorySupport.marketChartToDailyOhlc(
                chartFromRows(prices, null));

        assertThat(ohlc).hasSize(1);
        List<Number> candle = ohlc.get(0);
        assertThat(candle).hasSize(5);
        // [candleTimeMs, open, high, low, close]
        assertThat(candle.get(0).doubleValue()).isEqualTo((double) base); // earliest ts
        assertThat(candle.get(1).doubleValue()).isEqualTo(10.0);          // open
        assertThat(candle.get(2).doubleValue()).isEqualTo(15.0);          // high
        assertThat(candle.get(3).doubleValue()).isEqualTo(10.0);          // low
        assertThat(candle.get(4).doubleValue()).isEqualTo(12.0);          // close
    }

    @Test
    @DisplayName("marketChartToDailyOhlc: seconds-based timestamps normalised before bucketing")
    void marketChartToDailyOhlc_normalisesSeconds() {
        long sec = Instant.parse("2022-06-15T10:00:00Z").getEpochSecond();
        List<List<Number>> prices = List.of(row(sec, 25.0)); // seconds -> *1000 inside
        List<List<Number>> ohlc = CryptoFullHistorySupport.marketChartToDailyOhlc(
                chartFromRows(prices, null));

        assertThat(ohlc).hasSize(1);
        assertThat(ohlc.get(0).get(0).doubleValue()).isEqualTo((double) (sec * 1000L));
        assertThat(ohlc.get(0).get(1).doubleValue()).isEqualTo(25.0);
    }

    // ---- mergeOhlc ----------------------------------------------------------

    @Test
    @DisplayName("mergeOhlc: null/empty add returns base; null base returns add; both null -> empty list")
    void mergeOhlc_emptyHandling() {
        List<List<Number>> base = List.of(row(1_600_000_000_000L, 1, 2, 0.5, 1.5));

        // add null -> base
        assertThat(CryptoFullHistorySupport.mergeOhlc(base, null)).isSameAs(base);
        // add empty -> base
        assertThat(CryptoFullHistorySupport.mergeOhlc(base, new ArrayList<>())).isSameAs(base);
        // both null -> List.of()
        assertThat(CryptoFullHistorySupport.mergeOhlc(null, null)).isEmpty();
        // base null, add present -> add
        List<List<Number>> add = List.of(row(1_600_000_000_000L, 1, 2, 0.5, 1.5));
        assertThat(CryptoFullHistorySupport.mergeOhlc(null, add)).isSameAs(add);
    }

    @Test
    @DisplayName("mergeOhlc: merges, normalises seconds->ms, sorts, drops malformed rows, dedups by ts")
    void mergeOhlc_merge() {
        long tsSec = 1_600_000_000L;       // seconds
        long tsMsExpected = tsSec * 1000L;
        long tsLater = 1_600_086_400_000L; // ms

        List<List<Number>> base = new ArrayList<>();
        base.add(row(tsLater, 5, 6, 4, 5.5)); // later
        base.add(null);                        // malformed -> dropped
        base.add(row(1L, 2L));                 // size < 5 -> dropped

        List<List<Number>> add = new ArrayList<>();
        add.add(row(tsSec, 1, 2, 0.5, 1.5));  // seconds -> normalised to ms
        add.add(row(tsLater, 9, 9, 9, 9));    // duplicate timestamp overwrites

        List<List<Number>> merged = CryptoFullHistorySupport.mergeOhlc(base, add);

        assertThat(merged).hasSize(2);
        // sorted ascending: normalised-second row first
        assertThat(merged.get(0).get(0).longValue()).isEqualTo(tsMsExpected);
        assertThat(merged.get(0).get(4).doubleValue()).isEqualTo(1.5);
        // later row -> overwritten by add's value (close = 9)
        assertThat(merged.get(1).get(0).longValue()).isEqualTo(tsLater);
        assertThat(merged.get(1).get(4).doubleValue()).isEqualTo(9.0);
    }
}
