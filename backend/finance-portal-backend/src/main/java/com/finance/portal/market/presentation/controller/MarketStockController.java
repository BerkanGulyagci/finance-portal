package com.finance.portal.market.presentation.controller;

import com.finance.portal.common.presentation.dto.ApiResponse;
import com.finance.portal.market.application.stock.MidasStockDetail;
import com.finance.portal.market.application.stock.StockChartResponse;
import com.finance.portal.market.application.stock.StockDetail;
import com.finance.portal.market.application.stock.StockPageResponse;
import com.finance.portal.market.application.stock.StockQueryService;
import com.finance.portal.market.application.stock.StockSummary;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/market/stocks")
public class MarketStockController {

    private static final String SYMBOL_REGEX = "^[A-Z0-9.]{1,15}$";

    private final StockQueryService stockQueryService;

    public MarketStockController(StockQueryService stockQueryService) {
        this.stockQueryService = stockQueryService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<StockPageResponse>> getStockPage(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        StockPageResponse stockPage = stockQueryService.getPagedStockSummaries(page, size);

        ApiResponse<StockPageResponse> response = ApiResponse.success(
                stockPage,
                "Stock page retrieved successfully"
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{symbol}")
    public ResponseEntity<ApiResponse<StockDetail>> getStockDetail(
            @PathVariable("symbol") String symbol
    ) {
        if (symbol == null || symbol.trim().isEmpty()) {
            throw new IllegalArgumentException("symbol must not be empty");
        }

        String normalizedSymbol = symbol.trim().toUpperCase();
        validateSymbol(normalizedSymbol);

        StockDetail detail = stockQueryService.getStockDetail(normalizedSymbol);

        ApiResponse<StockDetail> response = ApiResponse.success(
                detail,
                "Stock detail retrieved successfully"
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{symbol}/chart")
    public ResponseEntity<ApiResponse<StockChartResponse>> getStockChart(
            @PathVariable("symbol") String symbol,
            @RequestParam(defaultValue = "1d") String range,
            @RequestParam(defaultValue = "1m") String interval
    ) {
        if (symbol == null || symbol.trim().isEmpty()) {
            throw new IllegalArgumentException("symbol must not be empty");
        }

        String normalizedSymbol = symbol.trim().toUpperCase();
        validateSymbol(normalizedSymbol);

        StockChartResponse chart = stockQueryService.getStockChartWithParams(normalizedSymbol, range, interval);

        ApiResponse<StockChartResponse> response = ApiResponse.success(
                chart,
                "Stock chart retrieved successfully"
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{symbol}/midas")
    public ResponseEntity<ApiResponse<MidasStockDetail>> getMidasDetail(
            @PathVariable("symbol") String symbol
    ) {
        if (symbol == null || symbol.trim().isEmpty()) {
            throw new IllegalArgumentException("symbol must not be empty");
        }
        String normalizedSymbol = symbol.trim().toUpperCase();
        MidasStockDetail detail = stockQueryService.getMidasDetail(normalizedSymbol);
        return ResponseEntity.ok(ApiResponse.success(detail, "Midas stock detail retrieved successfully"));
    }

    private void validateSymbol(String symbol) {
        if (!symbol.matches(SYMBOL_REGEX)) {
            throw new IllegalArgumentException(
                    "Symbol must match pattern " + SYMBOL_REGEX + " (e.g. THYAO.IS)");
        }
    }
}

