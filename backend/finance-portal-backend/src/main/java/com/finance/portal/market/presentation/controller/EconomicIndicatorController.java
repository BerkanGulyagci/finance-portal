package com.finance.portal.market.presentation.controller;

import com.finance.portal.common.presentation.dto.ApiResponse;
import com.finance.portal.market.application.indicator.EconomicIndicatorService;
import com.finance.portal.market.presentation.dto.EconomicIndicatorsDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/market/indicators")
public class EconomicIndicatorController {

    private final EconomicIndicatorService service;

    public EconomicIndicatorController(EconomicIndicatorService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<EconomicIndicatorsDto>> getIndicators() {
        var m = service.getIndicators();
        var dto = new EconomicIndicatorsDto(m.get("policyRate"), m.get("inflation"), m.get("ppi"), m.get("depositRate"));
        return ResponseEntity.ok(ApiResponse.success(dto, "Indicators retrieved"));
    }
}
