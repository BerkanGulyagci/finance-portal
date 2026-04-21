package com.finance.portal.market.presentation.controller;

import com.finance.portal.common.presentation.dto.ApiResponse;
import com.finance.portal.market.application.gold.GoldHistoryResponse;
import com.finance.portal.market.application.gold.GoldMarketService;
import com.finance.portal.market.application.gold.GoldSpotResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/gold")
public class GoldController {

    private final GoldMarketService goldMarketService;

    public GoldController(GoldMarketService goldMarketService) {
        this.goldMarketService = goldMarketService;
    }

    /**
     * GET /api/gold/spot
     * Güncel altın fiyatı (ONS/USD + TRY türevleri)
     */
    @GetMapping("/spot")
    public ResponseEntity<ApiResponse<GoldSpotResponse>> getSpot() {
        GoldSpotResponse data = goldMarketService.getSpotGold();
        return ResponseEntity.ok(ApiResponse.success(data, "Gold spot data retrieved"));
    }

    /**
     * GET /api/gold/history?range=1D|1W|1M|3M|1Y|ALL&currency=USD|TRY
     * Tarihsel altın fiyatları
     */
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<GoldHistoryResponse>> getHistory(
            @RequestParam(defaultValue = "1M") String range,
            @RequestParam(defaultValue = "USD") String currency
    ) {
        GoldHistoryResponse data = goldMarketService.getGoldHistory(range, currency);
        return ResponseEntity.ok(ApiResponse.success(data, "Gold history retrieved"));
    }
}
