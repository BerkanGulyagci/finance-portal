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
 * Piyasanın hareketlileri — günün en çok yükselen/düşen enstrümanları (tüm piyasa, portföyden bağımsız).
 * {@code GET /api/market/movers?limit=5}
 */
@RestController
@RequestMapping("/api/market/movers")
public class MarketMoversController {

    private final MarketMoversService marketMoversService;

    public MarketMoversController(MarketMoversService marketMoversService) {
        this.marketMoversService = marketMoversService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<MoversCategory>>> getMovers(
            @RequestParam(name = "limit", defaultValue = "5") int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 20);
        List<MoversCategory> movers = marketMoversService.getMovers(safeLimit);
        return ResponseEntity.ok(ApiResponse.success(movers, "Piyasanın hareketlileri"));
    }
}
