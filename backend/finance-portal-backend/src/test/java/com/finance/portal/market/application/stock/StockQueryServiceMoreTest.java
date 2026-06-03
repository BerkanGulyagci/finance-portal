package com.finance.portal.market.application.stock;

import com.finance.portal.common.application.exception.ResourceNotFoundException;
import com.finance.portal.common.infrastructure.cache.LastKnownGoodCache;
import com.finance.portal.market.application.stock.model.YahooChartSnapshot;
import com.finance.portal.market.application.stock.model.YahooQuoteSeries;
import com.finance.portal.market.application.stock.port.MidasStockPort;
import com.finance.portal.market.application.stock.port.YahooStockPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Branch-coverage complement to {@link StockQueryServiceTest}. Targets branches the original test
 * misses: the {@code fetchChartSnapshotResilient} hourly-fallback machinery (all arms), the
 * {@code toChartResponse} null-snapshot guard, the ≥2-close daily short-circuit, and the
 * non-long-range fallback range arm.
 */
@ExtendWith(MockitoExtension.class)
class StockQueryServiceMoreTest {

    @Mock
    private YahooStockPort yahooStockPort;

    @Mock
    private StockSymbolProvider stockSymbolProvider;

    @Mock
    private MidasStockPort midasStockPort;

    @Mock
    private LastKnownGoodCache lkg;

    @InjectMocks
    private StockQueryService service;

    @BeforeEach
    void stubLkgPassThrough() {
        // LKG wrapper is transparent in tests: resilient(...) invokes the 4th arg (the fetch supplier).
        lenient().when(lkg.resilient(any(), any(), any(), any()))
                .thenAnswer(inv -> ((java.util.function.Supplier<?>) inv.getArgument(3)).get());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static YahooChartSnapshot snapshotWith(List<Long> timestamps, List<BigDecimal> closes) {
        YahooChartSnapshot s = new YahooChartSnapshot();
        s.setTimestamps(timestamps);
        YahooQuoteSeries q = new YahooQuoteSeries();
        q.setClose(closes);
        s.setQuote(q);
        return s;
    }

    // ── toChartResponse: null-snapshot guard (getStockChart path) ────────────

    @Test
    void getStockChart_nullSnapshot_throws() {
        // fetchChart returns null → toChartResponse hits the `snapshot == null` guard.
        when(yahooStockPort.fetchChart("NULL.IS")).thenReturn(null);

        assertThatThrownBy(() -> service.getStockChart("NULL.IS"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("NULL.IS");
    }

    // ── fetchChartSnapshotResilient: ≥2 closes on a DAILY interval → no fallback ──

    @Test
    void getStockChartWithParams_dailyWithTwoCloses_noHourlyFallback() {
        // interval "1d" is non-intraday, but nonNullCloseCount==2 → early return, fallback NOT attempted.
        YahooChartSnapshot snap = snapshotWith(List.of(1L, 2L),
                Arrays.asList(new BigDecimal("10"), new BigDecimal("11")));
        when(yahooStockPort.fetchChartWithParams("A.IS", "5d", "1d")).thenReturn(snap);

        StockChartResponse resp = service.getStockChartWithParams("A.IS", "5d", "1d");

        assertThat(resp.getClosePrices())
                .extracting(BigDecimal::toPlainString)
                .containsExactly("10", "11");
        // No hourly fallback should ever be requested.
        verify(yahooStockPort, never()).fetchChartWithParams("A.IS", "5d", "1h");
    }

    // ── fetchChartSnapshotResilient: degenerate daily → hourly fallback SUCCEEDS ──

    @Test
    void getStockChartWithParams_shortRangeDegenerateDaily_usesHourlyFallback() {
        // interval "1d", only 1 close → degenerate. range "5d" is NOT long → fbRange stays "5d".
        YahooChartSnapshot degenerate = snapshotWith(List.of(1L), List.of(new BigDecimal("10")));
        YahooChartSnapshot hourly = snapshotWith(List.of(100L, 200L),
                Arrays.asList(new BigDecimal("20"), new BigDecimal("21")));
        when(yahooStockPort.fetchChartWithParams("A.IS", "5d", "1d")).thenReturn(degenerate);
        when(yahooStockPort.fetchChartWithParams("A.IS", "5d", "1h")).thenReturn(hourly);

        StockChartResponse resp = service.getStockChartWithParams("A.IS", "5d", "1d");

        // hourly has ≥2 closes → it wins.
        assertThat(resp.getClosePrices())
                .extracting(BigDecimal::toPlainString)
                .containsExactly("20", "21");
        verify(yahooStockPort).fetchChartWithParams("A.IS", "5d", "1h");
    }

    // ── fetchChartSnapshotResilient: LONG range degenerate → fbRange clamped to 2y ──

    @Test
    void getStockChartWithParams_longRangeDegenerateDaily_clampsFallbackTo2y() {
        // "5y" is a long range → fbRange becomes "2y" for the hourly fallback.
        YahooChartSnapshot degenerate = snapshotWith(List.of(1L), List.of(new BigDecimal("10")));
        YahooChartSnapshot hourly = snapshotWith(List.of(100L, 200L, 300L),
                Arrays.asList(new BigDecimal("30"), new BigDecimal("31"), new BigDecimal("32")));
        when(yahooStockPort.fetchChartWithParams("A.IS", "5y", "1d")).thenReturn(degenerate);
        when(yahooStockPort.fetchChartWithParams("A.IS", "2y", "1h")).thenReturn(hourly);

        StockChartResponse resp = service.getStockChartWithParams("A.IS", "5y", "1d");

        assertThat(resp.getClosePrices()).hasSize(3);
        verify(yahooStockPort).fetchChartWithParams("A.IS", "2y", "1h");
    }

    // ── fetchChartSnapshotResilient: hourly fallback ALSO degenerate → original kept ──

    @Test
    void getStockChartWithParams_hourlyFallbackAlsoDegenerate_keepsOriginal() {
        // degenerate daily (1 close) → fallback; hourly ALSO has <2 closes → original snapshot kept.
        YahooChartSnapshot degenerate = snapshotWith(List.of(1L, 2L),
                Arrays.asList(new BigDecimal("10"), (BigDecimal) null)); // ts=2 but close null → 1 usable close
        YahooChartSnapshot hourlyWeak = snapshotWith(List.of(100L), List.of(new BigDecimal("20")));
        when(yahooStockPort.fetchChartWithParams("A.IS", "1mo", "1d")).thenReturn(degenerate);
        when(yahooStockPort.fetchChartWithParams("A.IS", "1mo", "1h")).thenReturn(hourlyWeak);

        StockChartResponse resp = service.getStockChartWithParams("A.IS", "1mo", "1d");

        // Original kept → only the single valid (ts,close) pair survives toChartResponse filtering.
        assertThat(resp.getTimestamps()).containsExactly(1L);
        assertThat(resp.getClosePrices())
                .extracting(BigDecimal::toPlainString)
                .containsExactly("10");
        verify(yahooStockPort).fetchChartWithParams("A.IS", "1mo", "1h");
    }

    // ── fetchChartSnapshotResilient: hourly fallback THROWS → catch arm keeps original ──

    @Test
    void getStockChartWithParams_hourlyFallbackThrows_keepsOriginal() {
        YahooChartSnapshot degenerate = snapshotWith(List.of(1L), List.of(new BigDecimal("10")));
        when(yahooStockPort.fetchChartWithParams("A.IS", "1mo", "1d")).thenReturn(degenerate);
        when(yahooStockPort.fetchChartWithParams("A.IS", "1mo", "1h"))
                .thenThrow(new RuntimeException("yahoo hourly 500"));

        StockChartResponse resp = service.getStockChartWithParams("A.IS", "1mo", "1d");

        // RuntimeException from the fallback is swallowed → original snapshot served.
        assertThat(resp.getClosePrices())
                .extracting(BigDecimal::toPlainString)
                .containsExactly("10");
        verify(yahooStockPort).fetchChartWithParams("A.IS", "1mo", "1h");
    }

    // ── fetchChartSnapshotResilient: snapshot with null quote → nonNullCloseCount==0 path ──

    @Test
    void getStockChartWithParams_nullQuoteDaily_triggersFallbackThenThrowsMissing() {
        // snapshot non-null but quote null → nonNullCloseCount==0 → degenerate → fallback.
        // fallback also yields a quote-less snapshot → original kept → toChartResponse throws (closes null).
        YahooChartSnapshot noQuote = new YahooChartSnapshot();
        noQuote.setTimestamps(List.of(1L));
        // quote left null
        when(yahooStockPort.fetchChartWithParams("A.IS", "1d", "1d")).thenReturn(noQuote);
        when(yahooStockPort.fetchChartWithParams("A.IS", "1d", "1h")).thenReturn(noQuote);

        assertThatThrownBy(() -> service.getStockChartWithParams("A.IS", "1d", "1d"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("A.IS");
        verify(yahooStockPort).fetchChartWithParams("A.IS", "1d", "1h");
    }

    // ── getStockOhlc: degenerate daily exercises fallback in the OHLC path too ──

    @Test
    void getStockOhlc_degenerateDaily_usesHourlyFallbackData() {
        YahooChartSnapshot degenerate = snapshotWith(List.of(1L), List.of(new BigDecimal("10")));
        YahooChartSnapshot hourly = new YahooChartSnapshot();
        hourly.setTimestamps(List.of(100L, 200L));
        YahooQuoteSeries q = new YahooQuoteSeries();
        q.setOpen(Arrays.asList(new BigDecimal("1"), new BigDecimal("2")));
        q.setHigh(Arrays.asList(new BigDecimal("3"), new BigDecimal("4")));
        q.setLow(Arrays.asList(new BigDecimal("0.5"), new BigDecimal("0.6")));
        q.setClose(Arrays.asList(new BigDecimal("1.5"), new BigDecimal("2.5")));
        q.setVolume(Arrays.asList(10L, 20L));
        hourly.setQuote(q);
        when(yahooStockPort.fetchChartWithParams("A.IS", "6mo", "1d")).thenReturn(degenerate);
        when(yahooStockPort.fetchChartWithParams("A.IS", "6mo", "1h")).thenReturn(hourly);

        var data = service.getStockOhlc("A.IS", "6mo", "1d");

        assertThat(data).hasSize(2);
        assertThat(data.get(0).get("time")).isEqualTo(100L);
        assertThat((BigDecimal) data.get(0).get("close")).isEqualByComparingTo("1.5");
        verify(yahooStockPort).fetchChartWithParams("A.IS", "6mo", "1h");
    }
}
