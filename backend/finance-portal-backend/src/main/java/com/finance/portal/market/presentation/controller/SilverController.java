package com.finance.portal.market.presentation.controller;

import com.finance.portal.common.presentation.dto.ApiResponse;
import com.finance.portal.market.application.silver.SilverHistoryResponse;
import com.finance.portal.market.application.silver.SilverMarketService;
import com.finance.portal.market.application.silver.SilverSpotResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/silver")
@RequiredArgsConstructor
public class SilverController {

    private final SilverMarketService silverMarketService;

    /**
     * GET /api/v1/silver/spot
     * Güncel gümüş fiyatı (TL/Kg, TL/Gram, USD/Ons)
     */
    @GetMapping("/spot")
    public ResponseEntity<ApiResponse<SilverSpotResponse>> getSpot() {
        SilverSpotResponse data = silverMarketService.getSpotSilver();
        return ResponseEntity.ok(ApiResponse.success(data, "Silver spot data retrieved"));
    }

    /**
     * GET /api/v1/silver/history?range=1M&currency=TRY|USD
     * Tarihsel gümüş fiyatları
     */
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<SilverHistoryResponse>> getHistory(
            @RequestParam(defaultValue = "1M") String range,
            @RequestParam(defaultValue = "TRY") String currency
    ) {
        SilverHistoryResponse data = silverMarketService.getSilverHistory(range, currency);
        return ResponseEntity.ok(ApiResponse.success(data, "Silver history retrieved"));
    }
}
