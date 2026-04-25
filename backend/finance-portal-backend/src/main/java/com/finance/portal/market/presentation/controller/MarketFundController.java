package com.finance.portal.market.presentation.controller;

import com.finance.portal.common.presentation.dto.ApiResponse;
import com.finance.portal.market.application.funds.model.FundChartResponse;
import com.finance.portal.market.application.funds.model.FundDetail;
import com.finance.portal.market.application.funds.model.FundPageResponse;
import com.finance.portal.market.application.funds.model.TefasFundHistoryResponse;
import com.finance.portal.market.application.funds.model.TefasFundPageResponse;
import com.finance.portal.market.application.funds.service.FundQueryService;
import com.finance.portal.market.application.funds.service.TefasFundService;
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
@RequestMapping("/api/market/funds")
public class MarketFundController {

    private static final String SYMBOL_REGEX = "^[A-Z.]{1,10}$";

    private final FundQueryService fundQueryService;
    private final TefasFundService tefasFundService;

    public MarketFundController(FundQueryService fundQueryService, TefasFundService tefasFundService) {
        this.fundQueryService = fundQueryService;
        this.tefasFundService = tefasFundService;
    }

    @GetMapping("/tefas")
    public ResponseEntity<ApiResponse<TefasFundPageResponse>> getTefasFunds(
            @RequestParam(defaultValue = "YAT") String kind,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(2000) int size
    ) {
        TefasFundPageResponse result = tefasFundService.getPagedFunds(kind, page, size);
        return ResponseEntity.ok(ApiResponse.success(result, "TEFAS funds retrieved successfully"));
    }

    @GetMapping("/tefas/{code}")
    public ResponseEntity<ApiResponse<Object>> getTefasFundDetail(@PathVariable String code) {
        var items = tefasFundService.getFundByCode(code.toUpperCase());
        return ResponseEntity.ok(ApiResponse.success(items.isEmpty() ? null : items.get(0), "TEFAS fund detail retrieved"));
    }

    @GetMapping("/tefas/{code}/history")
    public ResponseEntity<ApiResponse<TefasFundHistoryResponse>> getTefasFundHistory(
            @PathVariable String code,
            @RequestParam(defaultValue = "1M") String range
    ) {
        TefasFundHistoryResponse result = fundQueryService.getTefasFundHistory(code, range);
        return ResponseEntity.ok(ApiResponse.success(result, "TEFAS fund history retrieved"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<FundPageResponse>> getFundPage(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size
    ) {
        FundPageResponse fundPage = fundQueryService.getPagedFunds(page, size);
        return ResponseEntity.ok(ApiResponse.success(fundPage, "Fund page retrieved successfully"));
    }

    @GetMapping("/{symbol}")
    public ResponseEntity<ApiResponse<FundDetail>> getFundDetail(@PathVariable String symbol) {
        String s = normalize(symbol);
        return ResponseEntity.ok(ApiResponse.success(fundQueryService.getFundDetail(s), "Fund detail retrieved successfully"));
    }

    @GetMapping("/{symbol}/chart")
    public ResponseEntity<ApiResponse<FundChartResponse>> getFundChart(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "1mo") String range,
            @RequestParam(defaultValue = "1d") String interval
    ) {
        String s = normalize(symbol);
        return ResponseEntity.ok(ApiResponse.success(fundQueryService.getFundChart(s, range, interval), "Fund chart retrieved successfully"));
    }

    private String normalize(String symbol) {
        if (symbol == null || symbol.trim().isEmpty()) throw new IllegalArgumentException("symbol must not be empty");
        String s = symbol.trim().toUpperCase();
        if (!s.matches(SYMBOL_REGEX)) throw new IllegalArgumentException("Symbol must match pattern " + SYMBOL_REGEX);
        return s;
    }
}
