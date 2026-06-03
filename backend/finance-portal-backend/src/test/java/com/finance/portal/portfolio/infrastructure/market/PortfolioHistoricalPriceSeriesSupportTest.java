package com.finance.portal.portfolio.infrastructure.market;

import com.finance.portal.market.application.viop.model.ViopChartPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Branch-coverage focused tests for {@link PortfolioHistoricalPriceSeriesSupport}.
 * Same package so the package-private static helpers are reachable.
 */
class PortfolioHistoricalPriceSeriesSupportTest {

    // ------------------------------------------------------------------
    // emptyMap / notFoundReason
    // ------------------------------------------------------------------

    @Test
    @DisplayName("emptyMap: yeni boş NavigableMap döner")
    void emptyMap_isEmpty() {
        NavigableMap<LocalDate, BigDecimal> m = PortfolioHistoricalPriceSeriesSupport.emptyMap();
        assertThat(m).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("notFoundReason: sabit mesaj döner")
    void notFoundReason_constant() {
        assertThat(PortfolioHistoricalPriceSeriesSupport.notFoundReason())
                .isEqualTo("Historical price not found");
    }

    // ------------------------------------------------------------------
    // fromEpochCloses
    // ------------------------------------------------------------------

    @Test
    @DisplayName("fromEpochCloses: null timestamps -> boş map")
    void fromEpochCloses_nullTimestamps() {
        assertThat(PortfolioHistoricalPriceSeriesSupport.fromEpochCloses(
                null, List.of(BigDecimal.ONE))).isEmpty();
    }

    @Test
    @DisplayName("fromEpochCloses: null closes -> boş map")
    void fromEpochCloses_nullCloses() {
        assertThat(PortfolioHistoricalPriceSeriesSupport.fromEpochCloses(
                List.of(1_700_000_000L), null)).isEmpty();
    }

    @Test
    @DisplayName("fromEpochCloses: null ts ve null close elemanları atlanır, geçerli olan eklenir")
    void fromEpochCloses_skipsNullElements_andSizeMismatch() {
        // epoch seconds for 2023-11-14T... in Istanbul -> a concrete day
        long tsValid = Instant.parse("2024-01-15T08:00:00Z").getEpochSecond();
        List<Long> timestamps = new ArrayList<>(Arrays.asList(null, tsValid, 9_999_999L));
        // closes shorter by one -> Math.min truncates the trailing element (size mismatch branch)
        List<BigDecimal> closes = new ArrayList<>(Arrays.asList(new BigDecimal("100"), null));

        NavigableMap<LocalDate, BigDecimal> m =
                PortfolioHistoricalPriceSeriesSupport.fromEpochCloses(timestamps, closes);

        // index 0: ts null -> skip ; index 1: close null -> skip ; index 2 excluded by Math.min
        assertThat(m).isEmpty();
    }

    @Test
    @DisplayName("fromEpochCloses: geçerli ts+close günlük tarihe map'lenir")
    void fromEpochCloses_validEntry() {
        long ts = Instant.parse("2024-03-10T10:00:00Z").getEpochSecond();
        NavigableMap<LocalDate, BigDecimal> m = PortfolioHistoricalPriceSeriesSupport.fromEpochCloses(
                List.of(ts), List.of(new BigDecimal("42.5")));

        LocalDate expected = Instant.ofEpochSecond(ts)
                .atZone(PortfolioHistoricalPriceSeriesSupport.ISTANBUL).toLocalDate();
        assertThat(m).hasSize(1).containsKey(expected);
        assertThat(m.get(expected)).isEqualByComparingTo("42.5");
    }

    // ------------------------------------------------------------------
    // fromDateStringCloses
    // ------------------------------------------------------------------

    @Test
    @DisplayName("fromDateStringCloses: null dates -> boş map")
    void fromDateStringCloses_nullDates() {
        assertThat(PortfolioHistoricalPriceSeriesSupport.fromDateStringCloses(
                null, List.of(BigDecimal.ONE))).isEmpty();
    }

    @Test
    @DisplayName("fromDateStringCloses: null closes -> boş map")
    void fromDateStringCloses_nullCloses() {
        assertThat(PortfolioHistoricalPriceSeriesSupport.fromDateStringCloses(
                List.of("2024-01-01"), null)).isEmpty();
    }

    @Test
    @DisplayName("fromDateStringCloses: null ds / null close atlanır, geçersiz tarih catch ile atlanır")
    void fromDateStringCloses_skipsNullsAndMalformed() {
        List<String> dates = new ArrayList<>(Arrays.asList(
                null,             // ds null -> skip
                "2024-02-20",     // close null -> skip
                "not-a-date",     // parse fails -> catch skip
                "2024-02-22"));   // valid
        List<BigDecimal> closes = new ArrayList<>(Arrays.asList(
                new BigDecimal("1"),
                null,
                new BigDecimal("3"),
                new BigDecimal("4")));

        NavigableMap<LocalDate, BigDecimal> m =
                PortfolioHistoricalPriceSeriesSupport.fromDateStringCloses(dates, closes);

        assertThat(m).hasSize(1).containsKey(LocalDate.of(2024, 2, 22));
        assertThat(m.get(LocalDate.of(2024, 2, 22))).isEqualByComparingTo("4");
    }

    @Test
    @DisplayName("fromDateStringCloses: uzun ISO string substring(0,10) ile parse edilir")
    void fromDateStringCloses_longString_substring() {
        NavigableMap<LocalDate, BigDecimal> m = PortfolioHistoricalPriceSeriesSupport.fromDateStringCloses(
                List.of("2024-05-09T13:45:00Z"), List.of(new BigDecimal("9.9")));
        assertThat(m).hasSize(1).containsKey(LocalDate.of(2024, 5, 9));
        assertThat(m.get(LocalDate.of(2024, 5, 9))).isEqualByComparingTo("9.9");
    }

    @Test
    @DisplayName("fromDateStringCloses: tam 10 karakter ISO string doğrudan parse edilir")
    void fromDateStringCloses_exactTenChars() {
        NavigableMap<LocalDate, BigDecimal> m = PortfolioHistoricalPriceSeriesSupport.fromDateStringCloses(
                List.of("2024-06-30"), List.of(new BigDecimal("12")));
        assertThat(m).containsKey(LocalDate.of(2024, 6, 30));
    }

    @Test
    @DisplayName("fromDateStringCloses: 10'dan kısa string else dalı (full parse) ve uzunluk farkı Math.min")
    void fromDateStringCloses_shortString_elseBranch_andSizeMismatch() {
        // length < 10 -> takes the else arm (parse full string); "short" is not a valid date -> catch skip
        List<String> dates = List.of("short", "2024-07-01");
        // closes shorter -> Math.min drops the second valid entry (size mismatch branch)
        List<BigDecimal> closes = List.of(new BigDecimal("5"));

        NavigableMap<LocalDate, BigDecimal> m =
                PortfolioHistoricalPriceSeriesSupport.fromDateStringCloses(dates, closes);
        assertThat(m).isEmpty();
    }

    // ------------------------------------------------------------------
    // putIfInRange
    // ------------------------------------------------------------------

    @Test
    @DisplayName("putIfInRange: null day -> hiçbir şey eklenmez")
    void putIfInRange_nullDay() {
        NavigableMap<LocalDate, BigDecimal> t = new TreeMap<>();
        PortfolioHistoricalPriceSeriesSupport.putIfInRange(
                t, null, new BigDecimal("1"),
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));
        assertThat(t).isEmpty();
    }

    @Test
    @DisplayName("putIfInRange: null price -> hiçbir şey eklenmez")
    void putIfInRange_nullPrice() {
        NavigableMap<LocalDate, BigDecimal> t = new TreeMap<>();
        PortfolioHistoricalPriceSeriesSupport.putIfInRange(
                t, LocalDate.of(2024, 6, 1), null,
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));
        assertThat(t).isEmpty();
    }

    @Test
    @DisplayName("putIfInRange: from'dan önceki gün eklenmez")
    void putIfInRange_beforeFrom() {
        NavigableMap<LocalDate, BigDecimal> t = new TreeMap<>();
        PortfolioHistoricalPriceSeriesSupport.putIfInRange(
                t, LocalDate.of(2023, 12, 31), new BigDecimal("1"),
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));
        assertThat(t).isEmpty();
    }

    @Test
    @DisplayName("putIfInRange: to'dan sonraki gün eklenmez")
    void putIfInRange_afterTo() {
        NavigableMap<LocalDate, BigDecimal> t = new TreeMap<>();
        PortfolioHistoricalPriceSeriesSupport.putIfInRange(
                t, LocalDate.of(2025, 1, 1), new BigDecimal("1"),
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));
        assertThat(t).isEmpty();
    }

    @Test
    @DisplayName("putIfInRange: aralık içindeki gün (sınırlar dahil) eklenir")
    void putIfInRange_inRangeInclusiveBounds() {
        NavigableMap<LocalDate, BigDecimal> t = new TreeMap<>();
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to = LocalDate.of(2024, 12, 31);
        // boundary == from
        PortfolioHistoricalPriceSeriesSupport.putIfInRange(t, from, new BigDecimal("10"), from, to);
        // boundary == to
        PortfolioHistoricalPriceSeriesSupport.putIfInRange(t, to, new BigDecimal("20"), from, to);
        // middle
        PortfolioHistoricalPriceSeriesSupport.putIfInRange(
                t, LocalDate.of(2024, 6, 15), new BigDecimal("30"), from, to);

        assertThat(t).hasSize(3);
        assertThat(t.get(from)).isEqualByComparingTo("10");
        assertThat(t.get(to)).isEqualByComparingTo("20");
        assertThat(t.get(LocalDate.of(2024, 6, 15))).isEqualByComparingTo("30");
    }

    // ------------------------------------------------------------------
    // fromCoinGeckoChart
    // ------------------------------------------------------------------

    @Test
    @DisplayName("fromCoinGeckoChart: null chart -> boş map")
    void fromCoinGeckoChart_nullChart() {
        assertThat(PortfolioHistoricalPriceSeriesSupport.fromCoinGeckoChart(null)).isEmpty();
    }

    @Test
    @DisplayName("fromCoinGeckoChart: prices List değil -> boş map")
    void fromCoinGeckoChart_pricesNotList() {
        Map<String, Object> chart = new HashMap<>();
        chart.put("prices", "oops-not-a-list");
        assertThat(PortfolioHistoricalPriceSeriesSupport.fromCoinGeckoChart(chart)).isEmpty();
    }

    @Test
    @DisplayName("fromCoinGeckoChart: prices anahtarı yok (null) -> boş map")
    void fromCoinGeckoChart_pricesMissing() {
        Map<String, Object> chart = new HashMap<>();
        assertThat(PortfolioHistoricalPriceSeriesSupport.fromCoinGeckoChart(chart)).isEmpty();
    }

    @Test
    @DisplayName("fromCoinGeckoChart: row List değil / kısa / Number olmayan elemanlar atlanır, geçerli eklenir")
    void fromCoinGeckoChart_skipsInvalidRows_addsValid() {
        long ms = Instant.parse("2024-04-01T09:00:00Z").toEpochMilli();
        List<Object> rows = new ArrayList<>();
        rows.add("not-a-list");                                  // row not a List -> skip
        rows.add(List.of(1L));                                   // size < 2 -> skip
        rows.add(Arrays.asList("ts-string", 5.0));               // ts not Number -> skip
        rows.add(Arrays.asList(ms, "price-string"));             // price not Number -> skip
        rows.add(Arrays.asList(ms, 123.45));                     // valid

        Map<String, Object> chart = new HashMap<>();
        chart.put("prices", rows);

        NavigableMap<LocalDate, BigDecimal> m =
                PortfolioHistoricalPriceSeriesSupport.fromCoinGeckoChart(chart);

        LocalDate expected = Instant.ofEpochMilli(ms)
                .atZone(PortfolioHistoricalPriceSeriesSupport.ISTANBUL).toLocalDate();
        assertThat(m).hasSize(1).containsKey(expected);
        assertThat(m.get(expected)).isEqualByComparingTo("123.45");
    }

    // ------------------------------------------------------------------
    // lastClosePerDayFromViopPoints
    // ------------------------------------------------------------------

    @Test
    @DisplayName("lastClosePerDayFromViopPoints: null liste -> boş map")
    void viop_nullPoints() {
        assertThat(PortfolioHistoricalPriceSeriesSupport.lastClosePerDayFromViopPoints(null)).isEmpty();
    }

    @Test
    @DisplayName("lastClosePerDayFromViopPoints: value null atlanır")
    void viop_nullValueSkipped() {
        ViopChartPoint p = new ViopChartPoint(1_700_000_000_000L, "2024-01-01T00:00:00", null);
        assertThat(PortfolioHistoricalPriceSeriesSupport.lastClosePerDayFromViopPoints(List.of(p)))
                .isEmpty();
    }

    @Test
    @DisplayName("lastClosePerDayFromViopPoints: timestamp dalı ile gün hesaplanır")
    void viop_timestampBranch() {
        long ms = Instant.parse("2024-02-05T11:00:00Z").toEpochMilli();
        ViopChartPoint p = new ViopChartPoint(ms, null, new BigDecimal("55"));

        NavigableMap<LocalDate, BigDecimal> m =
                PortfolioHistoricalPriceSeriesSupport.lastClosePerDayFromViopPoints(List.of(p));

        LocalDate expected = Instant.ofEpochMilli(ms)
                .atZone(PortfolioHistoricalPriceSeriesSupport.ISTANBUL).toLocalDate();
        assertThat(m).hasSize(1).containsKey(expected);
        assertThat(m.get(expected)).isEqualByComparingTo("55");
    }

    @Test
    @DisplayName("lastClosePerDayFromViopPoints: dateTime dalı (timestamp null, len>=10) parse edilir")
    void viop_dateTimeBranch() {
        ViopChartPoint p = new ViopChartPoint(null, "2024-03-15T08:30:00", new BigDecimal("77"));

        NavigableMap<LocalDate, BigDecimal> m =
                PortfolioHistoricalPriceSeriesSupport.lastClosePerDayFromViopPoints(List.of(p));

        assertThat(m).hasSize(1).containsKey(LocalDate.of(2024, 3, 15));
        assertThat(m.get(LocalDate.of(2024, 3, 15))).isEqualByComparingTo("77");
    }

    @Test
    @DisplayName("lastClosePerDayFromViopPoints: dateTime parse hatası -> day null -> atlanır")
    void viop_dateTimeParseError_skipped() {
        // length >= 10 enters the parse branch, but content is not a valid date -> catch -> day stays null
        ViopChartPoint p = new ViopChartPoint(null, "BAD-DATE-XYZ", new BigDecimal("88"));
        assertThat(PortfolioHistoricalPriceSeriesSupport.lastClosePerDayFromViopPoints(List.of(p)))
                .isEmpty();
    }

    @Test
    @DisplayName("lastClosePerDayFromViopPoints: timestamp ve dateTime yok / dateTime kısa -> day null -> atlanır")
    void viop_noTimestamp_shortDateTime_skipped() {
        // timestamp null AND dateTime length < 10 -> neither branch sets day -> skip
        ViopChartPoint shortDt = new ViopChartPoint(null, "2024-01", new BigDecimal("99"));
        ViopChartPoint nullDt = new ViopChartPoint(null, null, new BigDecimal("100"));

        List<ViopChartPoint> pts = Arrays.asList(shortDt, nullDt);
        assertThat(PortfolioHistoricalPriceSeriesSupport.lastClosePerDayFromViopPoints(pts)).isEmpty();
    }

    @Test
    @DisplayName("lastClosePerDayFromViopPoints: aynı gün için son değer kazanır")
    void viop_lastValueWinsPerDay() {
        long msMorning = Instant.parse("2024-04-10T06:00:00Z").toEpochMilli();
        long msEvening = Instant.parse("2024-04-10T14:00:00Z").toEpochMilli();
        ViopChartPoint a = new ViopChartPoint(msMorning, null, new BigDecimal("10"));
        ViopChartPoint b = new ViopChartPoint(msEvening, null, new BigDecimal("20"));

        NavigableMap<LocalDate, BigDecimal> m =
                PortfolioHistoricalPriceSeriesSupport.lastClosePerDayFromViopPoints(Arrays.asList(a, b));

        LocalDate day = Instant.ofEpochMilli(msMorning)
                .atZone(PortfolioHistoricalPriceSeriesSupport.ISTANBUL).toLocalDate();
        assertThat(m).hasSize(1);
        assertThat(m.get(day)).isEqualByComparingTo("20");
    }
}
