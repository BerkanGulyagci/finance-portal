package com.finance.portal.market.presentation.controller;

import com.finance.portal.common.presentation.dto.ApiResponse;
import com.finance.portal.market.application.service.MarketFxService;
import com.finance.portal.market.presentation.dto.FxLatestResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/market/fx")
public class MarketFxController {

    private final MarketFxService marketFxService;

    public MarketFxController(MarketFxService marketFxService) {
        this.marketFxService = marketFxService;
    }

    @GetMapping("/tcmb/latest")
    public ResponseEntity<ApiResponse<FxLatestResponse>> getTcmbLatestRates(
            @RequestParam(required = false) String symbols
    ) {
        FxLatestResponse fxResponse = marketFxService.getTcmbLatestRates(symbols);
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

        FxLatestResponse fxResponse = marketFxService.getOpenFxLatest(normalizedBase, normalizedSymbols);
        ApiResponse<FxLatestResponse> response = ApiResponse.success(
                fxResponse,
                "Open FX rates retrieved successfully"
        );
        return ResponseEntity.ok(response);
    }
}
