package com.finance.portal.market.presentation.controller;

import com.finance.portal.common.presentation.dto.ApiResponse;
import com.finance.portal.market.application.futures.FuturesPageResponse;
import com.finance.portal.market.application.futures.FuturesQueryService;
import com.finance.portal.market.application.stock.StockChartResponse;
import com.finance.portal.market.application.stock.StockQueryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/market/futures")
public class MarketFuturesController {

    private static final String FUTURES_SYMBOL_REGEX = "^[A-Z0-9.=]{1,15}$";

    private final FuturesQueryService futuresQueryService;
    private final StockQueryService stockQueryService;

    public MarketFuturesController(FuturesQueryService futuresQueryService, StockQueryService stockQueryService) {
        this.futuresQueryService = futuresQueryService;
        this.stockQueryService = stockQueryService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<FuturesPageResponse>> getFuturesPage(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(20) int size
    ) {
        FuturesPageResponse futuresPage = futuresQueryService.getPagedFutures(page, size);

        ApiResponse<FuturesPageResponse> response = ApiResponse.success(
                futuresPage,
                "Futures page retrieved successfully"
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{symbol}/chart")
    public ResponseEntity<ApiResponse<StockChartResponse>> getFuturesChart(
            @PathVariable("symbol") String symbol
    ) {
        if (symbol == null || symbol.trim().isEmpty()) {
            throw new IllegalArgumentException("symbol must not be empty");
        }

        String normalizedSymbol = symbol.trim().toUpperCase();
        validateFuturesSymbol(normalizedSymbol);

        StockChartResponse chart = stockQueryService.getStockChart(normalizedSymbol);

        ApiResponse<StockChartResponse> response = ApiResponse.success(
                chart,
                "Futures chart retrieved successfully"
        );

        return ResponseEntity.ok(response);
    }

    private void validateFuturesSymbol(String symbol) {
        if (!symbol.matches(FUTURES_SYMBOL_REGEX)) {
            throw new IllegalArgumentException(
                    "Symbol must match pattern " + FUTURES_SYMBOL_REGEX + " (e.g. ES=F)");
        }
    }
}

