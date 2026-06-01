package com.finance.portal.market.application.economy;

import com.finance.portal.market.application.economy.model.EconomyChartPoint;
import com.finance.portal.market.application.economy.model.EconomyChartSeries;
import com.finance.portal.market.application.economy.model.EconomySeriesPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EconomyChartServiceTest {

    private static final ZoneId TR = ZoneId.of("Europe/Istanbul");
    /** Matches EconomyChartService.ONE_YEAR_SECONDS. */
    private static final long ONE_YEAR = 31_557_600L;
    private static final long ONE_MONTH = 2_592_000L;

    @Mock
    private EconomySeriesGateway seriesGateway;

    @InjectMocks
    private EconomyChartService service;

    // ── helpers ──────────────────────────────────────────────────────────────

    private static EconomySeriesPoint pt(String period, String value, long unix) {
        return new EconomySeriesPoint(period, new BigDecimal(value), unix);
    }

    /** Return {@code points} only for the target def's fetch; empty otherwise. */
    private void stubOnly(EconomyIndicatorDef target, List<EconomySeriesPoint> points) {
        when(seriesGateway.fetch(any(), any(), any())).thenAnswer(inv -> {
            EconomyIndicatorDef def = inv.getArgument(0);
            return def == target ? points : Collections.emptyList();
        });
    }

    // ── getAllChartSeries ────────────────────────────────────────────────────

    @Test
    @DisplayName("getAllChartSeries returns one series per catalog def")
    void getAllChartSeries_oneSeriesPerDef() {
        when(seriesGateway.fetch(any(), any(), any())).thenReturn(Collections.emptyList());

        List<EconomyChartSeries> all = service.getAllChartSeries();

        assertThat(all).hasSize(EconomyIndicatorDef.values().length);
        assertThat(all).extracting(EconomyChartSeries::getKey)
                .contains("tufe", "usdTry", "abdCpi");
    }

    // ── unknown key ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("getChartSeries: unknown key -> empty raw series echoing the key")
    void getChartSeries_unknownKey_emptySeries() {
        EconomyChartSeries s = service.getChartSeries("nope");

        assertThat(s.getKey()).isEqualTo("nope");
        assertThat(s.getLabel()).isEqualTo("nope");
        assertThat(s.getTransform()).isEqualTo("raw");
        assertThat(s.getSource()).isEqualTo("TCMB EVDS");
        assertThat(s.getPoints()).isEmpty();
    }

    @Test
    @DisplayName("getFullChartSeries: unknown key -> empty raw series")
    void getFullChartSeries_unknownKey_emptySeries() {
        EconomyChartSeries s = service.getFullChartSeries("missing");

        assertThat(s.getKey()).isEqualTo("missing");
        assertThat(s.getPoints()).isEmpty();
    }

    // ── empty upstream ───────────────────────────────────────────────────────

    @Test
    @DisplayName("empty raw series -> empty points but yoy transform/unit metadata for yoy def")
    void getChartSeries_emptyUpstream_yoyDef() {
        when(seriesGateway.fetch(any(), any(), any())).thenReturn(Collections.emptyList());

        EconomyChartSeries s = service.getChartSeries("tufe");

        assertThat(s.getKey()).isEqualTo("tufe");
        assertThat(s.getLabel()).isEqualTo(EconomyIndicatorDef.TUFE.getLabel());
        assertThat(s.getPoints()).isEmpty();
        assertThat(s.getTransform()).isEqualTo("yoy");
        assertThat(s.getUnit()).isEqualTo(EconomyIndicatorDef.TUFE.getUnit()); // unit kept from def on empty
        assertThat(s.getFrequency()).isEqualTo("MONTHLY");
    }

    // ── raw (non-yoy) series ─────────────────────────────────────────────────

    @Test
    @DisplayName("non-yoy monthly def -> raw transform, def unit, period/value passthrough")
    void getChartSeries_rawSeries() {
        stubOnly(EconomyIndicatorDef.CARI_DENGE, List.of(
                pt("2026-03", "-1000", 1_740_000_000L),
                pt("2026-04", "-1200", 1_742_592_000L),
                pt("2026-05", "-900", 1_745_184_000L)));

        EconomyChartSeries s = service.getChartSeries("cariDenge");

        assertThat(s.getTransform()).isEqualTo("raw");
        assertThat(s.getUnit()).isEqualTo(EconomyIndicatorDef.CARI_DENGE.getUnit());
        assertThat(s.getPoints()).extracting(EconomyChartPoint::getPeriod)
                .containsExactly("2026-03", "2026-04", "2026-05");
        assertThat(s.getPoints().get(0).getValue()).isEqualByComparingTo("-1000");
        assertThat(s.getPoints().get(2).getValue()).isEqualByComparingTo("-900");
    }

    // ── yoy series ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("yoy def: each point paired with ~1y-prior yields % change, capped to last N")
    void getChartSeries_yoySeries_computesPercent() {
        long base = 1_700_000_000L;
        List<EconomySeriesPoint> raw = new ArrayList<>();
        // 13 monthly points one year before, value 100
        // and 13 matching points one year later with rising values.
        // Build pairs so that the later points each find a ~1y-prior partner within tolerance.
        // Point at (base + i*ONE_MONTH) value 100  (the "prior" year)
        // Point at (base + ONE_YEAR + i*ONE_MONTH) value 150 (the "current" year)
        for (int i = 0; i < 13; i++) {
            raw.add(pt("y0-" + i, "100", base + i * ONE_MONTH));
        }
        for (int i = 0; i < 13; i++) {
            raw.add(pt("y1-" + i, "150", base + ONE_YEAR + i * ONE_MONTH));
        }
        stubOnly(EconomyIndicatorDef.TUFE, raw);

        EconomyChartSeries s = service.getChartSeries("tufe");

        assertThat(s.getTransform()).isEqualTo("yoy");
        assertThat(s.getUnit()).isEqualTo("%");
        // YoY points only exist for the later year (where a 1y-prior partner exists)
        assertThat(s.getPoints()).isNotEmpty();
        // (150/100 - 1) * 100 = 50.00
        assertThat(s.getPoints()).allSatisfy(p ->
                assertThat(p.getValue()).isEqualByComparingTo("50.00"));
    }

    @Test
    @DisplayName("yoy def: prior point out of tolerance -> that point produces no yoy value")
    void getChartSeries_yoyOutOfTolerance_skips() {
        long base = 1_700_000_000L;
        // Only two points, ~1 month apart. No partner within ~45d of (t - 1 year) -> no yoy points.
        stubOnly(EconomyIndicatorDef.TUFE, List.of(
                pt("2026-04", "115", base),
                pt("2026-05", "150", base + ONE_MONTH)));

        EconomyChartSeries s = service.getChartSeries("tufe");

        assertThat(s.getTransform()).isEqualTo("yoy");
        assertThat(s.getPoints()).isEmpty();
    }

    @Test
    @DisplayName("yoy def: non-positive prior value is skipped (no divide-by-zero)")
    void getChartSeries_yoyNonPositivePrior_skipped() {
        long base = 1_700_000_000L;
        stubOnly(EconomyIndicatorDef.TUFE, List.of(
                pt("y0", "0", base),                 // prior value 0 -> skipped as partner
                pt("y1", "150", base + ONE_YEAR)));  // would pair with the zero point

        EconomyChartSeries s = service.getChartSeries("tufe");

        assertThat(s.getPoints()).isEmpty();
    }

    // ── intraday downsampling ────────────────────────────────────────────────

    @Test
    @DisplayName("daily def (summary): multiple days in a month collapse to that month's last value")
    void getChartSeries_dailyDownsampledToMonthly() {
        // USD_TRY is DAILY + non-yoy. Three days in May 2026, two in June 2026.
        long may1 = epoch(2026, 5, 1);
        long may15 = epoch(2026, 5, 15);
        long may31 = epoch(2026, 5, 31);
        long jun1 = epoch(2026, 6, 1);
        long jun10 = epoch(2026, 6, 10);
        stubOnly(EconomyIndicatorDef.USD_TRY, List.of(
                pt("01-05-2026", "40.0", may1),
                pt("15-05-2026", "40.5", may15),
                pt("31-05-2026", "41.0", may31),   // last of May -> kept
                pt("01-06-2026", "41.2", jun1),
                pt("10-06-2026", "41.5", jun10))); // last of June -> kept

        EconomyChartSeries s = service.getChartSeries("usdTry");

        assertThat(s.getFrequency()).isEqualTo("DAILY");
        assertThat(s.getTransform()).isEqualTo("raw");
        // downsampled to one point per month, period rewritten "yyyy-M"
        assertThat(s.getPoints()).extracting(EconomyChartPoint::getPeriod)
                .containsExactly("2026-5", "2026-6");
        assertThat(s.getPoints().get(0).getValue()).isEqualByComparingTo("41.0"); // May last
        assertThat(s.getPoints().get(1).getValue()).isEqualByComparingTo("41.5"); // June last
    }

    @Test
    @DisplayName("daily def (full): NOT downsampled — day-by-day points preserved with original periods")
    void getFullChartSeries_dailyNotDownsampled() {
        long may1 = epoch(2026, 5, 1);
        long may15 = epoch(2026, 5, 15);
        long may31 = epoch(2026, 5, 31);
        stubOnly(EconomyIndicatorDef.USD_TRY, List.of(
                pt("01-05-2026", "40.0", may1),
                pt("15-05-2026", "40.5", may15),
                pt("31-05-2026", "41.0", may31)));

        EconomyChartSeries s = service.getFullChartSeries("usdTry");

        assertThat(s.getPoints()).hasSize(3);
        assertThat(s.getPoints()).extracting(EconomyChartPoint::getPeriod)
                .containsExactly("01-05-2026", "15-05-2026", "31-05-2026");
    }

    // ── lastN capping ────────────────────────────────────────────────────────

    @Test
    @DisplayName("summary view caps to last N points; full view keeps all history")
    void summaryCapsButFullKeepsAll() {
        // CARI_DENGE: MONTHLY non-yoy -> maxPoints = 14. Provide 20 monthly points.
        List<EconomySeriesPoint> raw = new ArrayList<>();
        long base = 1_600_000_000L;
        for (int i = 0; i < 20; i++) {
            raw.add(pt("p" + i, String.valueOf(100 + i), base + i * ONE_MONTH));
        }
        // stub responds the same for summary + full (both call the same def)
        when(seriesGateway.fetch(any(), any(), any())).thenAnswer(inv -> {
            EconomyIndicatorDef def = inv.getArgument(0);
            return def == EconomyIndicatorDef.CARI_DENGE ? raw : Collections.emptyList();
        });

        EconomyChartSeries summary = service.getChartSeries("cariDenge");
        EconomyChartSeries full = service.getFullChartSeries("cariDenge");

        assertThat(summary.getPoints()).hasSize(14);            // capped to last 14
        assertThat(summary.getPoints().get(0).getPeriod()).isEqualTo("p6"); // 20 - 14
        assertThat(summary.getPoints().get(13).getPeriod()).isEqualTo("p19");
        assertThat(full.getPoints()).hasSize(20);               // uncapped
    }

    // ── source labels ────────────────────────────────────────────────────────

    @Test
    @DisplayName("FRED-sourced def carries the FRED source label")
    void fredDef_sourceLabel() {
        when(seriesGateway.fetch(any(), any(), any())).thenReturn(Collections.emptyList());

        EconomyChartSeries s = service.getChartSeries("abdCpi");

        assertThat(s.getSource()).isEqualTo("FRED (St. Louis Fed)");
    }

    @Test
    @DisplayName("EVDS-sourced def carries the TCMB EVDS source label")
    void evdsDef_sourceLabel() {
        when(seriesGateway.fetch(any(), any(), any())).thenReturn(Collections.emptyList());

        EconomyChartSeries s = service.getChartSeries("usdTry");

        assertThat(s.getSource()).isEqualTo("TCMB EVDS");
    }

    private static long epoch(int year, int month, int day) {
        return Instant.from(
                YearMonth.of(year, month).atDay(day).atStartOfDay(TR)).getEpochSecond();
    }
}
