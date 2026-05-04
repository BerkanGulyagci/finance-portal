package com.finance.portal.market.presentation.controller;

import com.finance.portal.common.presentation.dto.ApiResponse;
import com.finance.portal.market.application.futures.FuturesPageResponse;
import com.finance.portal.market.application.futures.FuturesQueryService;
import com.finance.portal.market.application.stock.StockChartResponse;
import com.finance.portal.market.application.stock.StockQueryService;
import com.finance.portal.market.application.viop.ViopService;
import com.finance.portal.market.presentation.dto.ViopContractDetailDto;
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
    private final ViopService viopService;

    public MarketFuturesController(FuturesQueryService futuresQueryService, 
                                   StockQueryService stockQueryService,
                                   ViopService viopService) {
        this.futuresQueryService = futuresQueryService;
        this.stockQueryService = stockQueryService;
        this.viopService = viopService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<FuturesPageResponse>> getFuturesPage(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
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

    /**
     * VIOP kontrat listesi (Akbank'tan)
     */
    @GetMapping("/viop/contracts")
    public ResponseEntity<ApiResponse<?>> getViopContracts() {
        var contracts = viopService.getAllContracts();

        ApiResponse<?> response = ApiResponse.success(
                contracts,
                "VIOP contracts retrieved successfully"
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Belirli bir hisse senedine ait VİOP kontratlarını getir
     */
    @GetMapping("/viop/contracts/by-underlying/{symbol}")
    public ResponseEntity<ApiResponse<?>> getViopContractsByUnderlying(@PathVariable String symbol) {
        var contracts = viopService.getContractsByUnderlyingAsset(symbol.toUpperCase());

        ApiResponse<?> response = ApiResponse.success(
                contracts,
                "Related VIOP contracts retrieved successfully"
        );

        return ResponseEntity.ok(response);
    }

    /**
     * VIOP kontrat detayı (Akbank'tan)
     * Query parameter ile kontrat adı alınır
     */
    @GetMapping("/viop")
    public ResponseEntity<ApiResponse<ViopContractDetailDto>> getViopContractDetail(
            @RequestParam("name") String contractName
    ) {
        if (contractName == null || contractName.trim().isEmpty()) {
            throw new IllegalArgumentException("Contract name must not be empty");
        }

        ViopContractDetailDto detail = viopService.getContractDetail(contractName);

        ApiResponse<ViopContractDetailDto> response = ApiResponse.success(
                detail,
                "VIOP contract detail retrieved successfully"
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

