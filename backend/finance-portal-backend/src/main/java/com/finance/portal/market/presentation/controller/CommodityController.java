package com.finance.portal.market.presentation.controller;

import com.finance.portal.common.presentation.dto.ApiResponse;
import com.finance.portal.market.application.commodity.CommodityDto;
import com.finance.portal.market.application.commodity.CommodityHistoryResponse;
import com.finance.portal.market.application.commodity.CommoditySpotDto;
import com.finance.portal.market.application.commodity.YahooCommodityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Global emtia futures endpoint'leri (Yahoo Finance kaynaklı).
 * Kıymetli madenler bu controller'da yer almaz.
 *
 * <pre>
 * GET /api/v1/commodities/list
 * GET /api/v1/commodities/spot?symbol=CL%3DF
 * GET /api/v1/commodities/history?symbol=CL%3DF&range=1M&interval=1d
 * </pre>
 *
 * Not: "=" içeren semboller (CL=F) path variable'da encoding sorununa yol açar.
 * Bu nedenle tüm endpoint'ler query param kullanır.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/commodities")
@RequiredArgsConstructor
public class CommodityController {

    private final YahooCommodityService commodityService;

    /**
     * Aktif emtia listesi — fiyat içermez, sadece metadata.
     */
    @GetMapping("/list")
    public ResponseEntity<ApiResponse<List<CommodityDto>>> list() {
        List<CommodityDto> data = commodityService.listEnabledCommodities();
        return ResponseEntity.ok(ApiResponse.success(data, "Commodity list retrieved"));
    }

    /**
     * Tek emtia spot fiyatı.
     * @param symbol Yahoo sembolü, örn: CL=F, BZ=F, ZW=F
     */
    @GetMapping("/spot")
    public ResponseEntity<ApiResponse<CommoditySpotDto>> spot(
            @RequestParam String symbol) {
        try {
            CommoditySpotDto data = commodityService.getSpot(symbol);
            return ResponseEntity.ok(ApiResponse.success(data, symbol + " spot retrieved"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Emtia tarihsel OHLC verisi.
     * @param symbol   Yahoo sembolü, örn: CL=F
     * @param range    1D | 1W | 1M | 3M | 6M | 1Y | 5Y (default: 1M)
     * @param interval Yahoo interval (opsiyonel — boş bırakılırsa range'e göre otomatik seçilir)
     */
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<CommodityHistoryResponse>> history(
            @RequestParam String symbol,
            @RequestParam(defaultValue = "1M") String range,
            @RequestParam(required = false) String interval) {
        try {
            CommodityHistoryResponse data = commodityService.getHistory(symbol, range, interval);
            return ResponseEntity.ok(ApiResponse.success(data, symbol + " history retrieved"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }
}
