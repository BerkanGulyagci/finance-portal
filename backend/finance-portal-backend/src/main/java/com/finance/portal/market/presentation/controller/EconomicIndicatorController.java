package com.finance.portal.market.presentation.controller;

import com.finance.portal.common.presentation.dto.ApiResponse;
import com.finance.portal.market.application.indicator.EconomicIndicatorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/market/indicators")
public class EconomicIndicatorController {

    private final EconomicIndicatorService service;

    public EconomicIndicatorController(EconomicIndicatorService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, String>>> getIndicators() {
        return ResponseEntity.ok(ApiResponse.success(service.getIndicators(), "Indicators retrieved"));
    }
}
