package com.finance.portal.portfolio.service.watchlistenrich;

import com.finance.portal.market.application.viop.ViopContract;
import com.finance.portal.market.application.viop.ViopService;
import com.finance.portal.market.application.viop.model.ViopContractDetail;
import com.finance.portal.portfolio.presentation.dto.WatchlistItemResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FutureWatchlistEnricherTest {

    @Mock ViopService viopService;
    @Mock StockLikeWatchlistEnricher stockLikeWatchlistEnricher;

    private FutureWatchlistEnricher enricher;

    @BeforeEach
    void setUp() {
        enricher = new FutureWatchlistEnricher(viopService, stockLikeWatchlistEnricher);
    }

    private static ViopContractDetail detail(BigDecimal last, BigDecimal prevSet) {
        ViopContractDetail d = new ViopContractDetail();
        d.setLastPrice(last);
        d.setPrevSettlementPrice(prevSet);
        d.setHigh(new BigDecimal("105"));
        d.setLow(new BigDecimal("95"));
        d.setChangePercent(new BigDecimal("1"));
        d.setTime("2026-05-26T17:00:00");
        return d;
    }

    @Test
    void enrich_viopMatch_appliesDetailNoFallback() {
        ViopContract c = new ViopContract();
        when(viopService.findMatchingContract("F_USDTRY")).thenReturn(Optional.of(c));
        when(viopService.buildDetailDto(c))
                .thenReturn(detail(new BigDecimal("100"), new BigDecimal("98")));

        WatchlistItemResponse r = new WatchlistItemResponse();
        r.setSymbol("F_USDTRY");
        enricher.enrich(r, "F_USDTRY");

        assertThat(r.getLastPrice()).isEqualByComparingTo("100");
        assertThat(r.getChange()).isEqualByComparingTo("2.0000");   // 100 − 98
        assertThat(r.getOpen()).isEqualByComparingTo("98");
        verify(stockLikeWatchlistEnricher, never()).enrich(any(), any());
    }

    @Test
    void enrich_noViopMatch_fallsBackToStockLike() {
        when(viopService.findMatchingContract("ES=F")).thenReturn(Optional.empty());

        WatchlistItemResponse r = new WatchlistItemResponse();
        enricher.enrich(r, "ES=F");

        verify(stockLikeWatchlistEnricher).enrich(r, "ES=F");
    }

    @Test
    void enrich_blankSymbol_noOp() {
        WatchlistItemResponse r = new WatchlistItemResponse();
        enricher.enrich(r, "  ");

        verify(viopService, never()).findMatchingContract(any());
        verify(stockLikeWatchlistEnricher, never()).enrich(any(), any());
    }

    @Test
    void enrich_viopMatchNoPrice_throws() {
        ViopContract c = new ViopContract();
        when(viopService.findMatchingContract(any())).thenReturn(Optional.of(c));
        when(viopService.buildDetailDto(c)).thenReturn(detail(null, null));

        WatchlistItemResponse r = new WatchlistItemResponse();
        r.setSymbol("X");
        assertThatThrownBy(() -> enricher.enrich(r, "X"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("VIOP price not available");
    }
}
