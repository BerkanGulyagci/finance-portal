package com.finance.portal.market.presentation.controller;

import com.finance.portal.common.presentation.dto.ApiResponse;
import com.finance.portal.market.application.funds.model.FundChartResponse;
import com.finance.portal.market.application.funds.model.FundDetail;
import com.finance.portal.market.application.funds.model.FundHistoryResponse;
import com.finance.portal.market.application.funds.model.FundPageResponse;
import com.finance.portal.market.application.funds.model.FundPeriod;
import com.finance.portal.market.application.funds.model.TefasFundHistoryResponse;
import com.finance.portal.market.application.funds.model.TefasFundItem;
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
        TefasFundItem item = tefasFundService.getFundDetail(code.toUpperCase());
        return ResponseEntity.ok(ApiResponse.success(item, "TEFAS fund detail retrieved"));
    }

    @GetMapping("/tefas/{code}/history")
    public ResponseEntity<ApiResponse<TefasFundHistoryResponse>> getTefasFundHistory(
            @PathVariable String code,
            @RequestParam(defaultValue = "1M") String range
    ) {
        TefasFundHistoryResponse result = fundQueryService.getTefasFundHistory(code, range);
        return ResponseEntity.ok(ApiResponse.success(result, "TEFAS fund history retrieved"));
    }

    /**
     * Fon grafik verisi — HangiKredi chart API üzerinden.
     * GET /api/market/funds/{fundCode}/history?period=THREE_YEARS
     */
    @GetMapping("/{fundCode}/history")
    public ResponseEntity<ApiResponse<FundHistoryResponse>> getFundHistory(
            @PathVariable String fundCode,
            @RequestParam(defaultValue = "ONE_MONTH") String period
    ) {
        FundPeriod fundPeriod;
        try {
            fundPeriod = FundPeriod.valueOf(period.toUpperCase());
        } catch (IllegalArgumentException e) {
            // Eski range formatını da destekle (1M, 3M vb.)
            fundPeriod = mapLegacyRange(period);
        }
        FundHistoryResponse result = tefasFundService.getFundHistory(fundCode.toUpperCase(), fundPeriod);
        return ResponseEntity.ok(ApiResponse.success(result, "Fund history retrieved"));
    }

    private FundPeriod mapLegacyRange(String range) {
        return switch (range.toUpperCase()) {
            case "1W"  -> FundPeriod.ONE_WEEK;
            case "1M"  -> FundPeriod.ONE_MONTH;
            case "3M"  -> FundPeriod.THREE_MONTHS;
            case "6M"  -> FundPeriod.SIX_MONTHS;
            case "1Y"  -> FundPeriod.ONE_YEAR;
            case "3Y"  -> FundPeriod.THREE_YEARS;
            case "5Y"  -> FundPeriod.FIVE_YEARS;
            default    -> FundPeriod.ONE_MONTH;
        };
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
