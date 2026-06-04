package com.finance.portal.market.presentation.controller;

import com.finance.portal.common.presentation.dto.ApiResponse;
import com.finance.portal.market.application.index.IndexQueryService;
import com.finance.portal.market.application.index.IndexSummary;
import com.finance.portal.market.application.stock.StockSummary;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * BIST endeksleri — liste, tekil endeks ve endeksle ilgili hisseler.
 * Fiyat/grafik verisi Yahoo'dan gelir (endeks sembolü {@code {KOD}.IS}); grafik için mevcut
 * {@code /api/v1/market/stocks/{symbol}/chart} ve {@code /ohlc} uçları doğrudan kullanılabilir.
 */
@RestController
@RequestMapping("/api/v1/market/indices")
public class MarketIndexController {

    private final IndexQueryService indexQueryService;

    public MarketIndexController(IndexQueryService indexQueryService) {
        this.indexQueryService = indexQueryService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<IndexSummary>>> getIndices() {
        List<IndexSummary> indices = indexQueryService.getIndices();
        return ResponseEntity.ok(ApiResponse.success(indices, "Indices retrieved successfully"));
    }

    @GetMapping("/{code}")
    public ResponseEntity<ApiResponse<IndexSummary>> getIndex(@PathVariable("code") String code) {
        IndexSummary index = indexQueryService.getIndex(code);
        return ResponseEntity.ok(ApiResponse.success(index, "Index retrieved successfully"));
    }

    @GetMapping("/{code}/constituents")
    public ResponseEntity<ApiResponse<List<StockSummary>>> getConstituents(@PathVariable("code") String code) {
        List<StockSummary> stocks = indexQueryService.getConstituents(code);
        return ResponseEntity.ok(ApiResponse.success(stocks, "Index constituents retrieved successfully"));
    }
}
