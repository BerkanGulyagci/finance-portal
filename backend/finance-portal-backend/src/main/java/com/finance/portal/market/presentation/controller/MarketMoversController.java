package com.finance.portal.market.presentation.controller;

import com.finance.portal.common.presentation.dto.ApiResponse;
import com.finance.portal.market.application.movers.MarketMoversService;
import com.finance.portal.market.application.movers.model.MoversCategory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Piyasanın hareketlileri + hacim liderleri (tüm piyasa, portföyden bağımsız).
 * {@code GET /api/v1/market/movers?limit=5} — günün en çok yükselen/düşen.
 * {@code GET /api/v1/market/volume-leaders?limit=5} — günlük işlem hacmi en yüksek.
 */
@RestController
@RequestMapping("/api/v1/market")
public class MarketMoversController {

    private final MarketMoversService marketMoversService;

    public MarketMoversController(MarketMoversService marketMoversService) {
        this.marketMoversService = marketMoversService;
    }

    @GetMapping("/movers")
    public ResponseEntity<ApiResponse<List<MoversCategory>>> getMovers(
            @RequestParam(name = "limit", defaultValue = "5") int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 20);
        List<MoversCategory> movers = marketMoversService.getMovers(safeLimit);
        return ResponseEntity.ok(ApiResponse.success(movers, "Piyasanın hareketlileri"));
    }

    @GetMapping("/volume-leaders")
    public ResponseEntity<ApiResponse<List<MoversCategory>>> getVolumeLeaders(
            @RequestParam(name = "limit", defaultValue = "5") int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 20);
        List<MoversCategory> leaders = marketMoversService.getVolumeLeaders(safeLimit);
        return ResponseEntity.ok(ApiResponse.success(leaders, "Hacim liderleri"));
    }
}
