package com.finance.portal.market.application.indicator;

import com.finance.portal.market.application.stock.model.YahooChartSnapshot;
import com.finance.portal.market.application.stock.model.YahooQuoteSeries;
import com.finance.portal.market.application.stock.port.YahooStockPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BistIndexServiceTest {

    @Mock
    private YahooStockPort yahooStockPort;

    @Mock
    private CacheManager cacheManager;

    @InjectMocks
    private BistIndexService service;

    // ── supports() ──────────────────────────────────────────────────────────

    @Test
    void supports_returnsTrueForKnownBistCodes() {
        assertThat(service.supports("XU100")).isTrue();
        assertThat(service.supports("XU030")).isTrue();
        assertThat(service.supports("XU050")).isTrue();
    }

    @Test
    void supports_isCaseInsensitiveAndTrims() {
        assertThat(service.supports("xu100")).isTrue();
        assertThat(service.supports("  Xu030  ")).isTrue();
    }

    @Test
    void supports_returnsFalseForUnknownOrNull() {
        assertThat(service.supports("AAPL")).isFalse();
        assertThat(service.supports("XU200")).isFalse();
        assertThat(service.supports(null)).isFalse();
        assertThat(service.supports("")).isFalse();
    }

    // ── pickYahooRange() static mapping ──────────────────────────────────────

    @Test
    void pickYahooRange_mapsDateSpansToYahooRanges() {
        LocalDate base = LocalDate.of(2026, 1, 1);
        assertThat(BistIndexService.pickYahooRange(base, base.plusDays(5))).isEqualTo("5d");
        assertThat(BistIndexService.pickYahooRange(base, base.plusDays(30))).isEqualTo("1mo");
        assertThat(BistIndexService.pickYahooRange(base, base.plusDays(90))).isEqualTo("3mo");
        assertThat(BistIndexService.pickYahooRange(base, base.plusDays(180))).isEqualTo("6mo");
        assertThat(BistIndexService.pickYahooRange(base, base.plusDays(365))).isEqualTo("1y");
        assertThat(BistIndexService.pickYahooRange(base, base.plusDays(750))).isEqualTo("2y");
        assertThat(BistIndexService.pickYahooRange(base, base.plusDays(1500))).isEqualTo("5y");
        // >~5.2 yıl (kıyas "Tüm" ~10 yıl) → Yahoo'dan tüm günlük geçmiş ("max") istenir.
        assertThat(BistIndexService.pickYahooRange(base, base.plusDays(2000))).isEqualTo("max");
    }

    @Test
    void pickYahooRange_defaultsToOneMonthWhenDatesNull() {
        assertThat(BistIndexService.pickYahooRange(null, null)).isEqualTo("1mo");
        assertThat(BistIndexService.pickYahooRange(LocalDate.now(), null)).isEqualTo("1mo");
    }

    // ── fetchChart() symbol mapping + close-series extraction ─────────────────

    @Test
    void fetchChart_mapsBistSymbolToIsSuffixAndReturnsSnapshot() {
        YahooChartSnapshot snap = snapshotWithCloses(List.of(
                new BigDecimal("9100.5"), new BigDecimal("9250.0")));
        when(yahooStockPort.fetchChartWithParams(eq("XU100.IS"), any(), eq("1d")))
                .thenReturn(snap);

        Optional<YahooChartSnapshot> result =
                service.fetchChart("xu100", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 20));

        assertThat(result).isPresent();
        assertThat(result.get().getQuote().getClose())
                .containsExactly(new BigDecimal("9100.5"), new BigDecimal("9250.0"));
        // 19 days span -> "1mo" range
        verify(yahooStockPort).fetchChartWithParams("XU100.IS", "1mo", "1d");
    }

    @Test
    void fetchChart_resolvesEachKnownSymbol() {
        when(yahooStockPort.fetchChartWithParams(any(), any(), any()))
                .thenReturn(snapshotWithCloses(List.of(new BigDecimal("1.0"))));

        service.fetchChart("XU030", null, null);
        service.fetchChart("XU050", null, null);

        verify(yahooStockPort).fetchChartWithParams(eq("XU030.IS"), any(), any());
        verify(yahooStockPort).fetchChartWithParams(eq("XU050.IS"), any(), any());
    }

    // ── empty / null upstream handling ───────────────────────────────────────

    @Test
    void fetchChart_returnsEmptyForNullSymbol() {
        assertThat(service.fetchChart(null, null, null)).isEmpty();
        verify(yahooStockPort, never()).fetchChartWithParams(any(), any(), any());
    }

    @Test
    void fetchChart_returnsEmptyForUnknownSymbol() {
        assertThat(service.fetchChart("AAPL", null, null)).isEmpty();
        verify(yahooStockPort, never()).fetchChartWithParams(any(), any(), any());
    }

    @Test
    void fetchChart_returnsEmptyWhenUpstreamReturnsNull() {
        when(yahooStockPort.fetchChartWithParams(any(), any(), any())).thenReturn(null);
        assertThat(service.fetchChart("XU100", null, null)).isEmpty();
    }

    @Test
    void fetchChart_returnsEmptyWhenTimestampsNull() {
        YahooChartSnapshot snap = new YahooChartSnapshot();
        snap.setTimestamps(null);
        snap.setQuote(quoteWithCloses(List.of(new BigDecimal("1.0"))));
        when(yahooStockPort.fetchChartWithParams(any(), any(), any())).thenReturn(snap);

        assertThat(service.fetchChart("XU100", null, null)).isEmpty();
    }

    @Test
    void fetchChart_returnsEmptyWhenTimestampsEmpty() {
        YahooChartSnapshot snap = new YahooChartSnapshot();
        snap.setTimestamps(Collections.emptyList());
        snap.setQuote(quoteWithCloses(List.of(new BigDecimal("1.0"))));
        when(yahooStockPort.fetchChartWithParams(any(), any(), any())).thenReturn(snap);

        assertThat(service.fetchChart("XU100", null, null)).isEmpty();
    }

    @Test
    void fetchChart_returnsEmptyWhenQuoteNull() {
        YahooChartSnapshot snap = new YahooChartSnapshot();
        snap.setTimestamps(List.of(1L, 2L));
        snap.setQuote(null);
        when(yahooStockPort.fetchChartWithParams(any(), any(), any())).thenReturn(snap);

        assertThat(service.fetchChart("XU100", null, null)).isEmpty();
    }

    @Test
    void fetchChart_returnsEmptyWhenCloseNullOrEmpty() {
        YahooChartSnapshot nullClose = new YahooChartSnapshot();
        nullClose.setTimestamps(List.of(1L));
        nullClose.setQuote(quoteWithCloses(null));

        YahooChartSnapshot emptyClose = new YahooChartSnapshot();
        emptyClose.setTimestamps(List.of(1L));
        emptyClose.setQuote(quoteWithCloses(Collections.emptyList()));

        when(yahooStockPort.fetchChartWithParams(any(), any(), any()))
                .thenReturn(nullClose, emptyClose);

        assertThat(service.fetchChart("XU100", null, null)).isEmpty();
        assertThat(service.fetchChart("XU100", null, null)).isEmpty();
    }

    // ── exception → graceful fallback ────────────────────────────────────────

    @Test
    void fetchChart_returnsEmptyWhenUpstreamThrows() {
        when(yahooStockPort.fetchChartWithParams(any(), any(), any()))
                .thenThrow(new RuntimeException("yahoo down"));

        assertThat(service.fetchChart("XU100", null, null)).isEmpty();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static YahooChartSnapshot snapshotWithCloses(List<BigDecimal> closes) {
        YahooChartSnapshot snap = new YahooChartSnapshot();
        snap.setTimestamps(List.of(1_700_000_000L, 1_700_086_400L));
        snap.setQuote(quoteWithCloses(closes));
        return snap;
    }

    private static YahooQuoteSeries quoteWithCloses(List<BigDecimal> closes) {
        YahooQuoteSeries quote = new YahooQuoteSeries();
        quote.setClose(closes);
        return quote;
    }
}
