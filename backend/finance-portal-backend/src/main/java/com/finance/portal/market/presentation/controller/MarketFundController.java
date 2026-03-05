package com.finance.portal.market.presentation.controller;

import com.finance.portal.common.presentation.dto.ApiResponse;
import com.finance.portal.market.application.funds.model.FundChartResponse;
import com.finance.portal.market.application.funds.model.FundDetail;
import com.finance.portal.market.application.funds.service.FundQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/market/funds")
public class MarketFundController {

    private static final String SYMBOL_REGEX = "^[A-Z.]{1,10}$";

    private final FundQueryService fundQueryService;

    public MarketFundController(FundQueryService fundQueryService) {
        this.fundQueryService = fundQueryService;
    }

    @GetMapping("/{symbol}")
    public ResponseEntity<ApiResponse<FundDetail>> getFundDetail(
            @PathVariable("symbol") String symbol
    ) {
        if (symbol == null || symbol.trim().isEmpty()) {
            throw new IllegalArgumentException("symbol must not be empty");
        }

        String normalizedSymbol = symbol.trim().toUpperCase();
        validateSymbol(normalizedSymbol);

        FundDetail detail = fundQueryService.getFundDetail(normalizedSymbol);

        ApiResponse<FundDetail> response = ApiResponse.success(
                detail,
                "Fund detail retrieved successfully"
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{symbol}/chart")
    public ResponseEntity<ApiResponse<FundChartResponse>> getFundChart(
            @PathVariable("symbol") String symbol,
            @RequestParam(defaultValue = "1mo") String range,
            @RequestParam(defaultValue = "1d") String interval
    ) {
        if (symbol == null || symbol.trim().isEmpty()) {
            throw new IllegalArgumentException("symbol must not be empty");
        }

        String normalizedSymbol = symbol.trim().toUpperCase();
        validateSymbol(normalizedSymbol);

        FundChartResponse chart = fundQueryService.getFundChart(normalizedSymbol, range, interval);

        ApiResponse<FundChartResponse> response = ApiResponse.success(
                chart,
                "Fund chart retrieved successfully"
        );

        return ResponseEntity.ok(response);
    }

    private void validateSymbol(String symbol) {
        if (!symbol.matches(SYMBOL_REGEX)) {
            throw new IllegalArgumentException(
                    "Symbol must match pattern " + SYMBOL_REGEX + " (e.g. SPY, QQQ)");
        }
    }
}
