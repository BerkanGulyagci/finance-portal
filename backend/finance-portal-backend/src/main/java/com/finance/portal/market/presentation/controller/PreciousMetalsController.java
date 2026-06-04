package com.finance.portal.market.presentation.controller;

import com.finance.portal.common.presentation.dto.ApiResponse;
import com.finance.portal.market.application.precious.PreciousMetalHistoryResponse;
import com.finance.portal.market.application.precious.PreciousMetalService;
import com.finance.portal.market.application.precious.PreciousMetalSpotResponse;
import com.finance.portal.market.application.precious.model.PreciousMetalType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Kıymetli Madenler — Platin ve Paladyum.
 *
 * <pre>
 * GET /api/v1/precious-metals/platinum/spot
 * GET /api/v1/precious-metals/platinum/history?range=1M&currency=TRY
 * GET /api/v1/precious-metals/palladium/spot
 * GET /api/v1/precious-metals/palladium/history?range=1M&currency=USD
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/precious-metals")
@RequiredArgsConstructor
public class PreciousMetalsController {

    private final PreciousMetalService preciousMetalService;

    @GetMapping("/{metal}/spot")
    public ResponseEntity<ApiResponse<PreciousMetalSpotResponse>> getSpot(
            @PathVariable String metal) {
        PreciousMetalType type = resolveType(metal);
        PreciousMetalSpotResponse data = preciousMetalService.getSpot(type);
        return ResponseEntity.ok(ApiResponse.success(data, type.getDisplayName() + " spot retrieved"));
    }

    @GetMapping("/{metal}/history")
    public ResponseEntity<ApiResponse<PreciousMetalHistoryResponse>> getHistory(
            @PathVariable String metal,
            @RequestParam(defaultValue = "1M") String range,
            @RequestParam(defaultValue = "TRY") String currency) {
        PreciousMetalType type = resolveType(metal);
        PreciousMetalHistoryResponse data = preciousMetalService.getHistory(type, range, currency);
        return ResponseEntity.ok(ApiResponse.success(data, type.getDisplayName() + " history retrieved"));
    }

    private PreciousMetalType resolveType(String metal) {
        return switch (metal.toLowerCase()) {
            case "platinum", "platin", "pt"   -> PreciousMetalType.PLATINUM;
            case "palladium", "paladyum", "pd" -> PreciousMetalType.PALLADIUM;
            case "gold", "altin", "au"         -> PreciousMetalType.GOLD;
            case "silver", "gumus", "ag"       -> PreciousMetalType.SILVER;
            default -> throw new IllegalArgumentException("Desteklenmeyen metal: " + metal);
        };
    }
}
