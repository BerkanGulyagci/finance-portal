package com.finance.portal.market.application.futures;

import com.finance.portal.market.application.stock.StockQueryService;
import com.finance.portal.market.application.stock.StockSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FuturesQueryServiceTest {

    @Mock
    private StockQueryService stockQueryService;

    @Mock
    private FuturesSymbolProvider futuresSymbolProvider;

    @InjectMocks
    private FuturesQueryService service;

    private static StockSummary summary(String symbol) {
        StockSummary s = new StockSummary();
        s.setSymbol(symbol);
        return s;
    }

    @Test
    void getPagedFutures_mapsSummaries_andComputesPaginationMetadata() {
        when(futuresSymbolProvider.getTotalElements()).thenReturn(15);
        when(futuresSymbolProvider.getPagedSymbols(0, 5)).thenReturn(List.of("ES=F", "NQ=F", "GC=F"));
        when(stockQueryService.getStockSummary("ES=F")).thenReturn(summary("ES=F"));
        when(stockQueryService.getStockSummary("NQ=F")).thenReturn(summary("NQ=F"));
        when(stockQueryService.getStockSummary("GC=F")).thenReturn(summary("GC=F"));

        FuturesPageResponse response = service.getPagedFutures(0, 5);

        assertThat(response.getContent())
                .extracting(StockSummary::getSymbol)
                .containsExactly("ES=F", "NQ=F", "GC=F");
        assertThat(response.getPage()).isEqualTo(0);
        assertThat(response.getSize()).isEqualTo(5);
        assertThat(response.getTotalElements()).isEqualTo(15);
        // ceil(15/5) = 3
        assertThat(response.getTotalPages()).isEqualTo(3);
        verify(stockQueryService, times(3)).getStockSummary(anyString());
    }

    @Test
    void emptySymbolPage_returnsEmptyContent_withoutCallingStockService() {
        when(futuresSymbolProvider.getTotalElements()).thenReturn(15);
        when(futuresSymbolProvider.getPagedSymbols(10, 5)).thenReturn(Collections.emptyList());

        FuturesPageResponse response = service.getPagedFutures(10, 5);

        assertThat(response.getContent()).isEmpty();
        assertThat(response.getTotalElements()).isEqualTo(15);
        assertThat(response.getTotalPages()).isEqualTo(3);
        verify(stockQueryService, never()).getStockSummary(anyString());
    }

    @Test
    void nullSummaries_areSkipped_inContent() {
        when(futuresSymbolProvider.getTotalElements()).thenReturn(2);
        when(futuresSymbolProvider.getPagedSymbols(0, 10)).thenReturn(List.of("ES=F", "NQ=F"));
        when(stockQueryService.getStockSummary("ES=F")).thenReturn(null);
        when(stockQueryService.getStockSummary("NQ=F")).thenReturn(summary("NQ=F"));

        FuturesPageResponse response = service.getPagedFutures(0, 10);

        assertThat(response.getContent())
                .extracting(StockSummary::getSymbol)
                .containsExactly("NQ=F");
    }

    @Test
    void perSymbolException_isSwallowed_remainingSymbolsStillFetched() {
        when(futuresSymbolProvider.getTotalElements()).thenReturn(2);
        when(futuresSymbolProvider.getPagedSymbols(0, 10)).thenReturn(List.of("BAD=F", "GC=F"));
        when(stockQueryService.getStockSummary("BAD=F"))
                .thenThrow(new RuntimeException("yahoo timeout"));
        when(stockQueryService.getStockSummary("GC=F")).thenReturn(summary("GC=F"));

        FuturesPageResponse response = service.getPagedFutures(0, 10);

        assertThat(response.getContent())
                .extracting(StockSummary::getSymbol)
                .containsExactly("GC=F");
    }

    @Test
    void allSummariesFail_returnsEmptyContent_butValidMetadata() {
        when(futuresSymbolProvider.getTotalElements()).thenReturn(15);
        when(futuresSymbolProvider.getPagedSymbols(0, 5)).thenReturn(List.of("A=F", "B=F"));
        lenient().when(stockQueryService.getStockSummary(anyString()))
                .thenThrow(new RuntimeException("down"));

        FuturesPageResponse response = service.getPagedFutures(0, 5);

        assertThat(response.getContent()).isEmpty();
        assertThat(response.getTotalElements()).isEqualTo(15);
        assertThat(response.getTotalPages()).isEqualTo(3);
    }

    @Test
    void zeroSize_yieldsZeroTotalPages_withoutDivisionByZero() {
        when(futuresSymbolProvider.getTotalElements()).thenReturn(15);
        when(futuresSymbolProvider.getPagedSymbols(0, 0)).thenReturn(Collections.emptyList());

        FuturesPageResponse response = service.getPagedFutures(0, 0);

        assertThat(response.getSize()).isZero();
        assertThat(response.getTotalPages()).isZero();
        assertThat(response.getContent()).isEmpty();
    }

    @Test
    void totalPages_roundsUp_forPartialLastPage() {
        when(futuresSymbolProvider.getTotalElements()).thenReturn(16);
        when(futuresSymbolProvider.getPagedSymbols(0, 5)).thenReturn(List.of("ES=F"));
        when(stockQueryService.getStockSummary("ES=F")).thenReturn(summary("ES=F"));

        FuturesPageResponse response = service.getPagedFutures(0, 5);

        // ceil(16/5) = 4
        assertThat(response.getTotalPages()).isEqualTo(4);
    }
}
