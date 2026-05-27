package com.finance.portal.portfolio.service.watchlistenrich;

import com.finance.portal.market.application.stock.StockChartResponse;
import com.finance.portal.market.application.stock.StockQueryService;
import com.finance.portal.market.application.stock.StockSummary;
import com.finance.portal.portfolio.presentation.dto.WatchlistItemResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockLikeWatchlistEnricherTest {

    @Mock StockQueryService stockQueryService;

    private StockLikeWatchlistEnricher enricher;

    @BeforeEach
    void setUp() {
        enricher = new StockLikeWatchlistEnricher(stockQueryService);
    }

    private static StockSummary summary() {
        StockSummary s = new StockSummary();
        s.setPrice(new BigDecimal("100"));
        s.setCurrency("TRY");
        s.setChange(new BigDecimal("2"));
        s.setChangePercent(new BigDecimal("2"));
        s.setVolume(1000L);
        s.setDayHigh(new BigDecimal("105"));
        s.setDayLow(new BigDecimal("95"));
        s.setAsOf("2026-05-26T17:00:00");
        return s;
    }

    @Test
    void enrich_corePath() {
        when(stockQueryService.getStockSummary("THYAO")).thenReturn(summary());
        when(stockQueryService.getStockChartWithParams(any(), any(), any())).thenReturn(null);

        WatchlistItemResponse r = new WatchlistItemResponse();
        enricher.enrich(r, "thyao");

        assertThat(r.getLastPrice()).isEqualByComparingTo("100");
        assertThat(r.getCurrency()).isEqualTo("TRY");
        assertThat(r.getChange()).isEqualByComparingTo("2");
        assertThat(r.getHigh()).isEqualByComparingTo("105");
        assertThat(r.getOpen()).isEqualByComparingTo("98");   // price - change
    }

    @Test
    void enrich_blankSymbol_noOp() {
        WatchlistItemResponse r = new WatchlistItemResponse();
        enricher.enrich(r, "  ");

        verify(stockQueryService, never()).getStockSummary(any());
        assertThat(r.getLastPrice()).isNull();
    }

    @Test
    void enrich_with1yChart_fillsTrendMetrics() {
        when(stockQueryService.getStockSummary("THYAO")).thenReturn(summary());
        StockChartResponse chart = new StockChartResponse();
        BigDecimal[] vals = new BigDecimal[20];
        for (int i = 0; i < 20; i++) {
            vals[i] = new BigDecimal(i + 1);
        }
        chart.setClosePrices(List.of(vals));
        when(stockQueryService.getStockChartWithParams("THYAO", "1y", "1d")).thenReturn(chart);

        WatchlistItemResponse r = new WatchlistItemResponse();
        enricher.enrich(r, "THYAO");

        assertThat(r.getFiftyTwoWeekHigh()).isEqualByComparingTo("20");
        assertThat(r.getMa20()).isEqualByComparingTo("10.5");
    }

    @Test
    void enrich_chartThrows_isSwallowed() {
        when(stockQueryService.getStockSummary("THYAO")).thenReturn(summary());
        when(stockQueryService.getStockChartWithParams(any(), any(), any()))
                .thenThrow(new RuntimeException("Yahoo down"));

        WatchlistItemResponse r = new WatchlistItemResponse();
        enricher.enrich(r, "THYAO");

        assertThat(r.getLastPrice()).isEqualByComparingTo("100");
        assertThat(r.getMa20()).isNull();
    }
}
