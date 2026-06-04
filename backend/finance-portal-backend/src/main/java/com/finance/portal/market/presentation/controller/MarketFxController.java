package com.finance.portal.market.presentation.controller;

import com.finance.portal.common.presentation.dto.ApiResponse;
import com.finance.portal.market.application.fx.model.FxHistory;
import com.finance.portal.market.application.fx.model.FxLatestRates;
import com.finance.portal.market.application.service.MarketFxService;
import com.finance.portal.market.presentation.dto.FxHistoryResponse;
import com.finance.portal.market.presentation.dto.FxLatestResponse;
import com.finance.portal.market.presentation.mapper.MarketFxPresentationMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/market/fx")
public class MarketFxController {

    private final MarketFxService marketFxService;
    private final MarketFxPresentationMapper fxMapper;

    public MarketFxController(MarketFxService marketFxService, MarketFxPresentationMapper fxMapper) {
        this.marketFxService = marketFxService;
        this.fxMapper = fxMapper;
    }

    @GetMapping("/tcmb/latest")
    public ResponseEntity<ApiResponse<FxLatestResponse>> getTcmbLatestRates(
            @RequestParam(required = false) String symbols
    ) {
        FxLatestRates rates = marketFxService.getTcmbLatestRates(symbols);
        FxLatestResponse fxResponse = fxMapper.toFxLatestResponse(rates);
        ApiResponse<FxLatestResponse> response = ApiResponse.success(
                fxResponse,
                "TCMB FX rates retrieved successfully"
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/open/latest")
    public ResponseEntity<ApiResponse<FxLatestResponse>> getOpenFxLatest(
            @RequestParam(required = false) String base,
            @RequestParam(required = false) String symbols
    ) {
        String normalizedBase = null;
        if (base != null && !base.trim().isEmpty()) {
            String candidate = base.trim().toUpperCase();
            if (!candidate.matches("^[A-Z]{3}$")) {
                throw new IllegalArgumentException("Base currency must be a 3-letter uppercase code, e.g. USD");
            }
            normalizedBase = candidate;
        }

        String normalizedSymbols = symbols;
        if (symbols != null && !symbols.trim().isEmpty()) {
            normalizedSymbols = Arrays.stream(symbols.split(","))
                    .map(String::trim)
                    .map(String::toUpperCase)
                    .peek(code -> {
                        if (!code.matches("^[A-Z]{3}$")) {
                            throw new IllegalArgumentException("Symbols must be comma-separated 3-letter currency codes, e.g. USD,EUR");
                        }
                    })
                    .collect(Collectors.joining(","));
        }

        FxLatestRates rates = marketFxService.getOpenFxLatest(normalizedBase, normalizedSymbols);
        FxLatestResponse fxResponse = fxMapper.toFxLatestResponse(rates);
        ApiResponse<FxLatestResponse> response = ApiResponse.success(
                fxResponse,
                "Open FX rates retrieved successfully"
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<FxHistoryResponse>> getFxHistory(
            @RequestParam String symbol,
            @RequestParam(defaultValue = "1M") String range
    ) {
        FxHistory history = marketFxService.getFxHistory(symbol, range);
        FxHistoryResponse data = fxMapper.toFxHistoryResponse(history);
        return ResponseEntity.ok(ApiResponse.success(data, "FX history retrieved"));
    }
}
