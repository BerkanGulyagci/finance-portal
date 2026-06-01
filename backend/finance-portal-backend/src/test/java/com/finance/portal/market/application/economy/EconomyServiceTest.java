package com.finance.portal.market.application.economy;

import com.finance.portal.market.application.economy.model.EconomyIndicator;
import com.finance.portal.market.application.economy.model.EconomySeriesPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EconomyServiceTest {

    /** Caller-runs executor so CompletableFuture.supplyAsync runs synchronously in-test. */
    private static final ExecutorService DIRECT = new AbstractExecutorService() {
        @Override public void execute(Runnable command) { command.run(); }
        @Override public void shutdown() { }
        @Override public List<Runnable> shutdownNow() { return Collections.emptyList(); }
        @Override public boolean isShutdown() { return true; }
        @Override public boolean isTerminated() { return true; }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return true; }
    };

    /** ~1 year in seconds — matches EconomyService.ONE_YEAR_SECONDS. */
    private static final long ONE_YEAR = 31_557_600L;

    @Mock
    private EconomySeriesGateway seriesGateway;

    private EconomyService service;

    @BeforeEach
    void setUp() {
        service = new EconomyService(seriesGateway, DIRECT);
    }

    private static EconomySeriesPoint pt(String period, String value, long unix) {
        return new EconomySeriesPoint(period, new BigDecimal(value), unix);
    }

    /** Stub the gateway to return {@code points} only for the given def's series code; empty otherwise. */
    private void stubOnly(EconomyIndicatorDef target, List<EconomySeriesPoint> points) {
        when(seriesGateway.fetch(any(), any(), any())).thenAnswer(inv -> {
            EconomyIndicatorDef def = inv.getArgument(0);
            return def == target ? points : Collections.emptyList();
        });
    }

    private static EconomyIndicator find(List<EconomyIndicator> list, String key) {
        return list.stream().filter(i -> key.equals(i.getKey())).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("getSummary returns one indicator per catalog def, carrying static metadata")
    void getSummary_oneIndicatorPerDef() {
        when(seriesGateway.fetch(any(), any(), any())).thenReturn(Collections.emptyList());

        List<EconomyIndicator> result = service.getSummary();

        assertThat(result).hasSize(EconomyIndicatorDef.values().length);
        EconomyIndicator tufe = find(result, "tufe");
        assertThat(tufe.getLabel()).isEqualTo(EconomyIndicatorDef.TUFE.getLabel());
        assertThat(tufe.getSeriesCode()).isEqualTo(EconomyIndicatorDef.TUFE.getSeriesCode());
        assertThat(tufe.getCategory()).isEqualTo(EconomyIndicatorDef.TUFE.getCategory().name());
        assertThat(tufe.getCategoryLabel()).isEqualTo(EconomyIndicatorDef.TUFE.getCategory().getLabel());
        assertThat(tufe.getUnit()).isEqualTo(EconomyIndicatorDef.TUFE.getUnit());
        assertThat(tufe.getFrequency()).isEqualTo(EconomyIndicatorDef.TUFE.getFrequency().name());
    }

    @Test
    @DisplayName("empty series -> indicator available=false with null value")
    void buildIndicator_emptySeries_unavailable() {
        when(seriesGateway.fetch(any(), any(), any())).thenReturn(Collections.emptyList());

        EconomyIndicator tufe = find(service.getSummary(), "tufe");

        assertThat(tufe.isAvailable()).isFalse();
        assertThat(tufe.getValue()).isNull();
        assertThat(tufe.getChangePercent()).isNull();
        assertThat(tufe.getYoyChangePercent()).isNull();
    }

    @Test
    @DisplayName("single point -> available with value/period but no previous/change")
    void buildIndicator_singlePoint_noChange() {
        long now = 1_750_000_000L;
        stubOnly(EconomyIndicatorDef.TUFE, List.of(pt("2026-05", "120", now)));

        EconomyIndicator tufe = find(service.getSummary(), "tufe");

        assertThat(tufe.isAvailable()).isTrue();
        assertThat(tufe.getValue()).isEqualByComparingTo("120");
        assertThat(tufe.getPeriod()).isEqualTo("2026-05");
        assertThat(tufe.getPreviousValue()).isNull();
        assertThat(tufe.getChangePercent()).isNull();
        assertThat(tufe.getAbsoluteChange()).isNull();
    }

    @Test
    @DisplayName("two points -> change% and absolute change computed from last two")
    void buildIndicator_twoPoints_periodChange() {
        long now = 1_750_000_000L;
        stubOnly(EconomyIndicatorDef.TUFE, List.of(
                pt("2026-04", "100", now - 2_592_000L),
                pt("2026-05", "110", now)));

        EconomyIndicator tufe = find(service.getSummary(), "tufe");

        assertThat(tufe.isAvailable()).isTrue();
        assertThat(tufe.getValue()).isEqualByComparingTo("110");
        assertThat(tufe.getPreviousValue()).isEqualByComparingTo("100");
        // (110/100 - 1) * 100 = 10.00
        assertThat(tufe.getChangePercent()).isEqualByComparingTo("10.00");
        assertThat(tufe.getAbsoluteChange()).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("YoY: point ~1 year before latest (within tolerance) yields yoyChangePercent")
    void buildIndicator_yoyWithinTolerance() {
        long now = 1_750_000_000L;
        long yearAgo = now - ONE_YEAR; // exactly one year prior -> within tolerance
        stubOnly(EconomyIndicatorDef.TUFE, List.of(
                pt("2025-05", "100", yearAgo),
                pt("2026-04", "115", now - 2_592_000L),
                pt("2026-05", "150", now)));

        EconomyIndicator tufe = find(service.getSummary(), "tufe");

        // (150/100 - 1) * 100 = 50.00
        assertThat(tufe.getYoyChangePercent()).isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("YoY: no point near one-year target (out of tolerance) -> yoyChangePercent null")
    void buildIndicator_yoyOutOfTolerance() {
        long now = 1_750_000_000L;
        // only recent points; nearest 'year ago' candidate is ~1 month back, far from target
        stubOnly(EconomyIndicatorDef.TUFE, List.of(
                pt("2026-04", "115", now - 2_592_000L),
                pt("2026-05", "150", now)));

        EconomyIndicator tufe = find(service.getSummary(), "tufe");

        assertThat(tufe.isAvailable()).isTrue();
        assertThat(tufe.getYoyChangePercent()).isNull();
    }

    @Test
    @DisplayName("YoY not meaningful def (e.g. USD/TRY) -> yoyChangePercent null even with year-ago point")
    void buildIndicator_yoyNotMeaningful() {
        long now = 1_750_000_000L;
        long yearAgo = now - ONE_YEAR;
        stubOnly(EconomyIndicatorDef.USD_TRY, List.of(
                pt("2025-05-30", "30", yearAgo),
                pt("2026-05-30", "40", now)));

        EconomyIndicator usd = find(service.getSummary(), "usdTry");

        assertThat(usd.isAvailable()).isTrue();
        // period change still computed
        assertThat(usd.getChangePercent()).isEqualByComparingTo("33.33");
        // but YoY suppressed because def.isYoyMeaningful() == false
        assertThat(usd.getYoyChangePercent()).isNull();
    }

    @Test
    @DisplayName("percentChange: zero base -> change% null (no divide-by-zero)")
    void buildIndicator_zeroBase_nullChange() {
        long now = 1_750_000_000L;
        stubOnly(EconomyIndicatorDef.TUFE, List.of(
                pt("2026-04", "0", now - 2_592_000L),
                pt("2026-05", "110", now)));

        EconomyIndicator tufe = find(service.getSummary(), "tufe");

        assertThat(tufe.getChangePercent()).isNull();
        // absolute change still computed
        assertThat(tufe.getAbsoluteChange()).isEqualByComparingTo("110");
    }

    @Test
    @DisplayName("preferAbsolute reflected for balance/flow indicators (cariDenge)")
    void buildIndicator_preferAbsoluteFlag() {
        long now = 1_750_000_000L;
        stubOnly(EconomyIndicatorDef.CARI_DENGE, List.of(pt("2026-05", "-1000", now)));

        EconomyIndicator cari = find(service.getSummary(), "cariDenge");

        assertThat(cari.isAvailable()).isTrue();
        assertThat(cari.isPreferAbsolute()).isTrue();
    }

    @Test
    @DisplayName("gateway throws for one series -> that indicator unavailable, others unaffected")
    void buildIndicator_gatewayThrows_isolatedFailure() {
        long now = 1_750_000_000L;
        when(seriesGateway.fetch(any(), any(), any())).thenAnswer(inv -> {
            EconomyIndicatorDef def = inv.getArgument(0);
            if (def == EconomyIndicatorDef.TUFE) {
                throw new RuntimeException("EVDS timeout");
            }
            if (def == EconomyIndicatorDef.UFE) {
                return List.of(pt("2026-05", "200", now));
            }
            return Collections.emptyList();
        });

        List<EconomyIndicator> result = service.getSummary();

        EconomyIndicator tufe = find(result, "tufe");
        EconomyIndicator ufe = find(result, "ufe");
        assertThat(tufe.isAvailable()).isFalse();
        assertThat(ufe.isAvailable()).isTrue();
        assertThat(ufe.getValue()).isEqualByComparingTo("200");
        // full catalog still returned despite one failure
        assertThat(result).hasSize(EconomyIndicatorDef.values().length);
    }

    @Test
    @DisplayName("keys are unique across the returned summary (catalog integrity)")
    void getSummary_uniqueKeys() {
        when(seriesGateway.fetch(any(), any(), any())).thenReturn(Collections.emptyList());

        Map<String, Long> counts = service.getSummary().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        EconomyIndicator::getKey, java.util.stream.Collectors.counting()));

        assertThat(counts.values()).allMatch(c -> c == 1L);
    }
}
