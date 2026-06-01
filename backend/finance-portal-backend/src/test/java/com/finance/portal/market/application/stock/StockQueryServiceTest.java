package com.finance.portal.market.application.stock;

import com.finance.portal.common.application.exception.ResourceNotFoundException;
import com.finance.portal.market.application.stock.model.YahooChartSnapshot;
import com.finance.portal.market.application.stock.model.YahooQuoteSeries;
import com.finance.portal.market.application.stock.model.YahooStockMeta;
import com.finance.portal.market.application.stock.port.MidasStockPort;
import com.finance.portal.market.application.stock.port.YahooStockPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockQueryServiceTest {

    @Mock
    private YahooStockPort yahooStockPort;

    @Mock
    private StockSymbolProvider stockSymbolProvider;

    @Mock
    private MidasStockPort midasStockPort;

    @InjectMocks
    private StockQueryService service;

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static YahooStockMeta fullMeta(String symbol) {
        YahooStockMeta m = new YahooStockMeta();
        m.setSymbol(symbol);
        m.setLongName("Acme Corp");
        m.setCurrency("TRY");
        m.setExchangeName("IST");
        m.setRegularMarketPrice(new BigDecimal("110.00"));
        m.setPreviousClose(new BigDecimal("100.00"));
        m.setRegularMarketDayHigh(new BigDecimal("112.345"));
        m.setRegularMarketDayLow(new BigDecimal("99.111"));
        m.setRegularMarketVolume(9999L);
        m.setFiftyTwoWeekHigh(new BigDecimal("150"));
        m.setFiftyTwoWeekLow(new BigDecimal("70"));
        m.setRegularMarketTime(1_700_000_000L);
        return m;
    }

    private static YahooChartSnapshot snapshotWithMeta(YahooStockMeta m) {
        YahooChartSnapshot s = new YahooChartSnapshot();
        s.setMeta(m);
        return s;
    }

    // ── getStockSummary / mapToStockSummary ──────────────────────────────────

    @Test
    void getStockSummary_mapsAllFields_andComputesChange() {
        when(yahooStockPort.fetchChart("ACME.IS")).thenReturn(snapshotWithMeta(fullMeta("ACME.IS")));

        StockSummary s = service.getStockSummary("ACME.IS");

        assertThat(s.getSymbol()).isEqualTo("ACME.IS");
        assertThat(s.getName()).isEqualTo("Acme Corp");
        assertThat(s.getCurrency()).isEqualTo("TRY");
        assertThat(s.getExchange()).isEqualTo("IST");
        assertThat(s.getPrice()).isEqualByComparingTo("110.00");
        assertThat(s.getChange()).isEqualByComparingTo("10.00");
        // 10/100 *100 = 10.00%
        assertThat(s.getChangePercent()).isEqualByComparingTo("10.00");
        assertThat(s.getDayHigh()).isEqualByComparingTo("112.35"); // scaled 2 HALF_UP
        assertThat(s.getDayLow()).isEqualByComparingTo("99.11");
        assertThat(s.getVolume()).isEqualTo(9999L);
        assertThat(s.getAsOf()).isNotNull();
    }

    @Test
    void getStockSummary_nullPriceAndPrevClose_defaultToZero_noDivision() {
        YahooStockMeta m = new YahooStockMeta();
        m.setSymbol("X.IS");
        // price/prevClose null → default 0
        when(yahooStockPort.fetchChart("X.IS")).thenReturn(snapshotWithMeta(m));

        StockSummary s = service.getStockSummary("X.IS");

        assertThat(s.getPrice()).isEqualByComparingTo("0.00");
        assertThat(s.getChange()).isEqualByComparingTo("0.00");
        assertThat(s.getChangePercent()).isEqualByComparingTo("0");
        assertThat(s.getDayHigh()).isNull();
        assertThat(s.getDayLow()).isNull();
        assertThat(s.getAsOf()).isNull();
    }

    @Test
    void getStockSummary_notFound_throws() {
        when(yahooStockPort.fetchChart("MISSING.IS")).thenReturn(new YahooChartSnapshot());

        assertThatThrownBy(() -> service.getStockSummary("MISSING.IS"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("MISSING.IS");
    }

    // ── getStockDetail ───────────────────────────────────────────────────────

    @Test
    void getStockDetail_mapsMetaAndSummary() {
        when(yahooStockPort.fetchChart("ACME.IS")).thenReturn(snapshotWithMeta(fullMeta("ACME.IS")));

        StockDetail d = service.getStockDetail("ACME.IS");

        assertThat(d.getSymbol()).isEqualTo("ACME.IS");
        assertThat(d.getName()).isEqualTo("Acme Corp");
        assertThat(d.getCurrency()).isEqualTo("TRY");
        assertThat(d.getExchange()).isEqualTo("IST");
        assertThat(d.getFiftyTwoWeekHigh()).isEqualByComparingTo("150");
        assertThat(d.getFiftyTwoWeekLow()).isEqualByComparingTo("70");
        assertThat(d.getSummary()).isNotNull();
        assertThat(d.getSummary().getPrice()).isEqualByComparingTo("110.00");
    }

    @Test
    void getStockDetail_notFound_throws() {
        when(yahooStockPort.fetchChart("Z.IS")).thenReturn(new YahooChartSnapshot());
        assertThatThrownBy(() -> service.getStockDetail("Z.IS"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── getPagedStockSummaries ───────────────────────────────────────────────

    @Test
    void getPagedStockSummaries_buildsPage_withParallelSummaries() {
        when(stockSymbolProvider.getPagedSymbols(0, 2)).thenReturn(List.of("A.IS", "B.IS"));
        when(stockSymbolProvider.getTotalElements()).thenReturn(10);
        when(yahooStockPort.fetchChart("A.IS")).thenReturn(snapshotWithMeta(fullMeta("A.IS")));
        when(yahooStockPort.fetchChart("B.IS")).thenReturn(snapshotWithMeta(fullMeta("B.IS")));

        StockPageResponse resp = service.getPagedStockSummaries(0, 2);

        assertThat(resp.getContent()).extracting(StockSummary::getSymbol)
                .containsExactlyInAnyOrder("A.IS", "B.IS");
        assertThat(resp.getPage()).isZero();
        assertThat(resp.getSize()).isEqualTo(2);
        assertThat(resp.getTotalElements()).isEqualTo(10);
        // ceil(10/2)=5
        assertThat(resp.getTotalPages()).isEqualTo(5);
    }

    @Test
    void getPagedStockSummaries_emptySymbols_returnsEmptyContent() {
        when(stockSymbolProvider.getPagedSymbols(5, 5)).thenReturn(List.of());
        when(stockSymbolProvider.getTotalElements()).thenReturn(10);

        StockPageResponse resp = service.getPagedStockSummaries(5, 5);

        assertThat(resp.getContent()).isEmpty();
        assertThat(resp.getTotalPages()).isEqualTo(2);
    }

    @Test
    void getPagedStockSummaries_failingSymbol_isSkipped() {
        when(stockSymbolProvider.getPagedSymbols(0, 2)).thenReturn(List.of("OK.IS", "BAD.IS"));
        when(stockSymbolProvider.getTotalElements()).thenReturn(2);
        when(yahooStockPort.fetchChart("OK.IS")).thenReturn(snapshotWithMeta(fullMeta("OK.IS")));
        when(yahooStockPort.fetchChart("BAD.IS")).thenThrow(new RuntimeException("yahoo timeout"));

        StockPageResponse resp = service.getPagedStockSummaries(0, 2);

        assertThat(resp.getContent()).extracting(StockSummary::getSymbol).containsExactly("OK.IS");
    }

    @Test
    void getPagedStockSummaries_zeroSize_yieldsZeroTotalPages() {
        when(stockSymbolProvider.getPagedSymbols(0, 0)).thenReturn(List.of());
        when(stockSymbolProvider.getTotalElements()).thenReturn(10);

        StockPageResponse resp = service.getPagedStockSummaries(0, 0);

        assertThat(resp.getTotalPages()).isZero();
        assertThat(resp.getContent()).isEmpty();
    }

    // ── getPagedStockSummariesByIndex ────────────────────────────────────────

    @Test
    void getPagedStockSummariesByIndex_bist30_paginatesSublist() {
        when(stockSymbolProvider.getBist30Symbols()).thenReturn(List.of("A.IS", "B.IS", "C.IS", "D.IS"));
        when(yahooStockPort.fetchChart(anyString())).thenAnswer(inv ->
                snapshotWithMeta(fullMeta(inv.getArgument(0))));

        StockPageResponse resp = service.getPagedStockSummariesByIndex(0, 2, "BIST30");

        assertThat(resp.getTotalElements()).isEqualTo(4);
        assertThat(resp.getContent()).extracting(StockSummary::getSymbol)
                .containsExactlyInAnyOrder("A.IS", "B.IS");
        // ceil(4/2)=2
        assertThat(resp.getTotalPages()).isEqualTo(2);
    }

    @Test
    void getPagedStockSummariesByIndex_bist50_secondPage() {
        when(stockSymbolProvider.getBist50Symbols()).thenReturn(List.of("A.IS", "B.IS", "C.IS"));
        when(yahooStockPort.fetchChart(anyString())).thenAnswer(inv ->
                snapshotWithMeta(fullMeta(inv.getArgument(0))));

        StockPageResponse resp = service.getPagedStockSummariesByIndex(1, 2, "BIST50");

        assertThat(resp.getContent()).extracting(StockSummary::getSymbol).containsExactly("C.IS");
    }

    @Test
    void getPagedStockSummariesByIndex_bist100_used() {
        when(stockSymbolProvider.getBist100Symbols()).thenReturn(List.of("A.IS"));
        when(yahooStockPort.fetchChart("A.IS")).thenReturn(snapshotWithMeta(fullMeta("A.IS")));

        StockPageResponse resp = service.getPagedStockSummariesByIndex(0, 10, "BIST100");

        assertThat(resp.getContent()).hasSize(1);
        assertThat(resp.getTotalElements()).isEqualTo(1);
    }

    @Test
    void getPagedStockSummariesByIndex_unknownIndex_usesAllSymbols() {
        when(stockSymbolProvider.getAllSymbols()).thenReturn(List.of("A.IS", "B.IS"));
        when(yahooStockPort.fetchChart(anyString())).thenAnswer(inv ->
                snapshotWithMeta(fullMeta(inv.getArgument(0))));

        StockPageResponse resp = service.getPagedStockSummariesByIndex(0, 10, "XYZ");

        assertThat(resp.getTotalElements()).isEqualTo(2);
        assertThat(resp.getContent()).hasSize(2);
    }

    @Test
    void getPagedStockSummariesByIndex_startBeyondTotal_returnsEmpty() {
        when(stockSymbolProvider.getBist30Symbols()).thenReturn(List.of("A.IS", "B.IS"));

        StockPageResponse resp = service.getPagedStockSummariesByIndex(5, 10, "BIST30");

        assertThat(resp.getContent()).isEmpty();
        assertThat(resp.getTotalElements()).isEqualTo(2);
    }

    // ── getStockChart ────────────────────────────────────────────────────────

    @Test
    void getStockChart_filtersNullPairs() {
        YahooChartSnapshot snap = new YahooChartSnapshot();
        snap.setTimestamps(new ArrayList<>(Arrays.asList(1L, 2L, null, 4L)));
        YahooQuoteSeries q = new YahooQuoteSeries();
        q.setClose(new ArrayList<>(Arrays.asList(new BigDecimal("10"), null, new BigDecimal("12"), new BigDecimal("14"))));
        snap.setQuote(q);
        when(yahooStockPort.fetchChart("A.IS")).thenReturn(snap);

        StockChartResponse resp = service.getStockChart("A.IS");

        // index0 ok(1,10); index1 close null skip; index2 ts null skip; index3 ok(4,14)
        assertThat(resp.getSymbol()).isEqualTo("A.IS");
        assertThat(resp.getTimestamps()).containsExactly(1L, 4L);
        assertThat(resp.getClosePrices()).containsExactly(new BigDecimal("10"), new BigDecimal("14"));
    }

    @Test
    void getStockChart_missingData_throws() {
        YahooChartSnapshot snap = new YahooChartSnapshot();
        snap.setTimestamps(null);
        when(yahooStockPort.fetchChart("A.IS")).thenReturn(snap);

        assertThatThrownBy(() -> service.getStockChart("A.IS"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getStockChart_emptyCloses_throws() {
        YahooChartSnapshot snap = new YahooChartSnapshot();
        snap.setTimestamps(List.of(1L, 2L));
        YahooQuoteSeries q = new YahooQuoteSeries();
        q.setClose(new ArrayList<>());
        snap.setQuote(q);
        when(yahooStockPort.fetchChart("A.IS")).thenReturn(snap);

        assertThatThrownBy(() -> service.getStockChart("A.IS"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getStockChart_nullQuote_throws() {
        YahooChartSnapshot snap = new YahooChartSnapshot();
        snap.setTimestamps(List.of(1L));
        // quote null → closes null
        when(yahooStockPort.fetchChart("A.IS")).thenReturn(snap);

        assertThatThrownBy(() -> service.getStockChart("A.IS"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── getStockChartWithParams / range normalization ────────────────────────

    @Test
    void getStockChartWithParams_allAlias_normalizesTo30yDaily() {
        YahooChartSnapshot snap = new YahooChartSnapshot();
        snap.setTimestamps(List.of(1L));
        YahooQuoteSeries q = new YahooQuoteSeries();
        q.setClose(List.of(new BigDecimal("5")));
        snap.setQuote(q);
        when(yahooStockPort.fetchChartWithParams("A.IS", "30y", "1d")).thenReturn(snap);

        StockChartResponse resp = service.getStockChartWithParams("A.IS", "max", "1mo");

        assertThat(resp.getClosePrices()).containsExactly(new BigDecimal("5"));
        verify(yahooStockPort).fetchChartWithParams("A.IS", "30y", "1d");
    }

    @Test
    void getStockChartWithParams_turkishAllAliases_normalize() {
        YahooChartSnapshot snap = new YahooChartSnapshot();
        snap.setTimestamps(List.of(1L));
        YahooQuoteSeries q = new YahooQuoteSeries();
        q.setClose(List.of(new BigDecimal("5")));
        snap.setQuote(q);
        when(yahooStockPort.fetchChartWithParams(eq("A.IS"), eq("30y"), eq("1d"))).thenReturn(snap);

        service.getStockChartWithParams("A.IS", "tüm", "1d");
        service.getStockChartWithParams("A.IS", "tum", "1d");
        service.getStockChartWithParams("A.IS", "all", "1d");

        verify(yahooStockPort, org.mockito.Mockito.times(3))
                .fetchChartWithParams("A.IS", "30y", "1d");
    }

    @Test
    void getStockChartWithParams_passThroughRangeInterval() {
        YahooChartSnapshot snap = new YahooChartSnapshot();
        snap.setTimestamps(List.of(1L));
        YahooQuoteSeries q = new YahooQuoteSeries();
        q.setClose(List.of(new BigDecimal("5")));
        snap.setQuote(q);
        when(yahooStockPort.fetchChartWithParams("A.IS", "5d", "1h")).thenReturn(snap);

        service.getStockChartWithParams("A.IS", "5d", "1h");

        verify(yahooStockPort).fetchChartWithParams("A.IS", "5d", "1h");
    }

    @Test
    void getStockChartWithParams_nullRangeInterval_defaults() {
        YahooChartSnapshot snap = new YahooChartSnapshot();
        snap.setTimestamps(List.of(1L));
        YahooQuoteSeries q = new YahooQuoteSeries();
        q.setClose(List.of(new BigDecimal("5")));
        snap.setQuote(q);
        when(yahooStockPort.fetchChartWithParams("A.IS", "1d", "1m")).thenReturn(snap);

        service.getStockChartWithParams("A.IS", null, null);

        verify(yahooStockPort).fetchChartWithParams("A.IS", "1d", "1m");
    }

    @Test
    void getStockChartWithParams_blankRangeInterval_defaults() {
        YahooChartSnapshot snap = new YahooChartSnapshot();
        snap.setTimestamps(List.of(1L));
        YahooQuoteSeries q = new YahooQuoteSeries();
        q.setClose(List.of(new BigDecimal("5")));
        snap.setQuote(q);
        when(yahooStockPort.fetchChartWithParams("A.IS", "1d", "1m")).thenReturn(snap);

        service.getStockChartWithParams("A.IS", "  ", "  ");

        verify(yahooStockPort).fetchChartWithParams("A.IS", "1d", "1m");
    }

    // ── getStockOhlc ─────────────────────────────────────────────────────────

    @Test
    void getStockOhlc_buildsCandles_skipsIncompleteRows() {
        YahooChartSnapshot snap = new YahooChartSnapshot();
        snap.setTimestamps(List.of(100L, 200L));
        YahooQuoteSeries q = new YahooQuoteSeries();
        q.setOpen(Arrays.asList(new BigDecimal("1"), null));   // row1 incomplete
        q.setHigh(Arrays.asList(new BigDecimal("2"), new BigDecimal("2")));
        q.setLow(Arrays.asList(new BigDecimal("0.5"), new BigDecimal("0.5")));
        q.setClose(Arrays.asList(new BigDecimal("1.5"), new BigDecimal("1.5")));
        q.setVolume(Arrays.asList(1000L, 2000L));
        snap.setQuote(q);
        when(yahooStockPort.fetchChartWithParams("A.IS", "1d", "1m")).thenReturn(snap);

        List<Map<String, Object>> data = service.getStockOhlc("A.IS", null, null);

        assertThat(data).hasSize(1);
        Map<String, Object> candle = data.get(0);
        assertThat(candle.get("time")).isEqualTo(100L);
        assertThat(candle.get("open")).isEqualTo(new BigDecimal("1"));
        assertThat(candle.get("high")).isEqualTo(new BigDecimal("2"));
        assertThat(candle.get("low")).isEqualTo(new BigDecimal("0.5"));
        assertThat(candle.get("close")).isEqualTo(new BigDecimal("1.5"));
        assertThat(candle.get("volume")).isEqualTo(1000L);
    }

    @Test
    void getStockOhlc_nullTimestamps_throws() {
        YahooChartSnapshot snap = new YahooChartSnapshot();
        snap.setQuote(new YahooQuoteSeries());
        when(yahooStockPort.fetchChartWithParams("A.IS", "1d", "1m")).thenReturn(snap);

        assertThatThrownBy(() -> service.getStockOhlc("A.IS", null, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getStockOhlc_nullQuote_throws() {
        YahooChartSnapshot snap = new YahooChartSnapshot();
        snap.setTimestamps(List.of(1L));
        when(yahooStockPort.fetchChartWithParams("A.IS", "1d", "1m")).thenReturn(snap);

        assertThatThrownBy(() -> service.getStockOhlc("A.IS", null, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getStockOhlc_allRowsIncomplete_throws() {
        YahooChartSnapshot snap = new YahooChartSnapshot();
        snap.setTimestamps(List.of(1L));
        YahooQuoteSeries q = new YahooQuoteSeries();
        q.setOpen(Arrays.asList((BigDecimal) null));
        q.setHigh(Arrays.asList(new BigDecimal("2")));
        q.setLow(Arrays.asList(new BigDecimal("1")));
        q.setClose(Arrays.asList(new BigDecimal("1.5")));
        snap.setQuote(q);
        when(yahooStockPort.fetchChartWithParams("A.IS", "1d", "1m")).thenReturn(snap);

        assertThatThrownBy(() -> service.getStockOhlc("A.IS", null, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getStockOhlc_allAlias_usesNormalizedRange() {
        YahooChartSnapshot snap = new YahooChartSnapshot();
        snap.setTimestamps(List.of(1L));
        YahooQuoteSeries q = new YahooQuoteSeries();
        q.setOpen(List.of(new BigDecimal("1")));
        q.setHigh(List.of(new BigDecimal("2")));
        q.setLow(List.of(new BigDecimal("0.5")));
        q.setClose(List.of(new BigDecimal("1.5")));
        q.setVolume(List.of(10L));
        snap.setQuote(q);
        when(yahooStockPort.fetchChartWithParams("A.IS", "30y", "1d")).thenReturn(snap);

        List<Map<String, Object>> data = service.getStockOhlc("A.IS", "max", "1wk");

        assertThat(data).hasSize(1);
        verify(yahooStockPort).fetchChartWithParams("A.IS", "30y", "1d");
    }

    // ── getMidasDetail ───────────────────────────────────────────────────────

    @Test
    void getMidasDetail_delegatesToPort() {
        MidasStockDetail detail = new MidasStockDetail();
        detail.setSymbol("A.IS");
        detail.setName("Acme");
        when(midasStockPort.fetchDetail("A.IS")).thenReturn(detail);

        MidasStockDetail result = service.getMidasDetail("A.IS");

        assertThat(result).isSameAs(detail);
        verify(midasStockPort).fetchDetail("A.IS");
    }

    @Test
    void getMidasDetail_nullPassthrough() {
        when(midasStockPort.fetchDetail("Z.IS")).thenReturn(null);
        assertThat(service.getMidasDetail("Z.IS")).isNull();
    }
}
