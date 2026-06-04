package com.finance.portal.market.presentation.controller;

import com.finance.portal.common.presentation.dto.ApiResponse;
import com.finance.portal.market.application.funds.model.FundPriceHistoryResponse;
import com.finance.portal.market.application.funds.model.RasyonetFundDetailDto;
import com.finance.portal.market.application.funds.model.RasyonetFundDto;
import com.finance.portal.market.application.funds.model.RasyonetOsmanliFundBulletinDto;
import com.finance.portal.market.application.funds.service.RasyonetFundService;
import com.finance.portal.market.application.funds.service.TefasFundService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Fon API controller.
 * Tüm veri kaynağı: Rasyonet / YatırımDirekt
 *
 * Endpoints:
 *   GET /api/v1/market/funds/tefas/all          → TMF (TEFAS yatırım fonları)
 *   GET /api/v1/market/funds/bes/all            → TPF (BES fonları)
 *   GET /api/v1/market/funds/oks/all            → TAF (OKS fonları)
 *   GET /api/v1/market/funds/osmanli/bulletin   → Osmanlı Portföy fon bülteni
 *   GET /api/v1/market/funds/tefas/{code}       → Fon detayı (Rasyonet card)
 */
@Validated
@RestController
@RequestMapping("/api/v1/market/funds")
public class MarketFundController {

    private final RasyonetFundService rasyonetFundService;
    private final TefasFundService tefasFundService;

    public MarketFundController(RasyonetFundService rasyonetFundService,
                                TefasFundService tefasFundService) {
        this.rasyonetFundService = rasyonetFundService;
        this.tefasFundService = tefasFundService;
    }

    /** GET /api/v1/market/funds/tefas/all — TMF yatırım fonları */
    @GetMapping("/tefas/all")
    public ResponseEntity<ApiResponse<List<RasyonetFundDto>>> getAllTefasFunds() {
        List<RasyonetFundDto> funds = rasyonetFundService.getAllFunds();
        return ResponseEntity.ok(ApiResponse.success(funds,
                "TEFAS funds retrieved via Rasyonet (" + funds.size() + " funds)"));
    }

    /** GET /api/v1/market/funds/bes/all — TPF BES fonları */
    @GetMapping("/bes/all")
    public ResponseEntity<ApiResponse<List<RasyonetFundDto>>> getAllBesFunds() {
        List<RasyonetFundDto> funds = rasyonetFundService.getAllBesFunds();
        return ResponseEntity.ok(ApiResponse.success(funds,
                "BES funds retrieved via Rasyonet (" + funds.size() + " funds)"));
    }

    /** GET /api/v1/market/funds/oks/all — TAF OKS fonları */
    @GetMapping("/oks/all")
    public ResponseEntity<ApiResponse<List<RasyonetFundDto>>> getAllOksFunds() {
        List<RasyonetFundDto> funds = rasyonetFundService.getAllOksFunds();
        return ResponseEntity.ok(ApiResponse.success(funds,
                "OKS funds retrieved via Rasyonet (" + funds.size() + " funds)"));
    }

    /** GET /api/v1/market/funds/osmanli/bulletin — Osmanlı Portföy fon bülteni */
    @GetMapping("/osmanli/bulletin")
    public ResponseEntity<ApiResponse<List<RasyonetOsmanliFundBulletinDto>>> getOsmanliFundBulletin() {
        List<RasyonetOsmanliFundBulletinDto> funds = rasyonetFundService.getOsmanliFundBulletin();
        return ResponseEntity.ok(ApiResponse.success(funds,
                "Osmanlı fund bulletin retrieved (" + funds.size() + " funds)"));
    }

    /**
     * GET /api/v1/market/funds/tefas/{code}?sourceCode=TMF — Fon detayı (Rasyonet card endpoint)
     * sourceCode: TMF (TEFAS/default) | TPF (BES) | TAF (OKS)
     * Fon kodu regex: büyük harf + rakam, 2-6 karakter. "all" bu pattern'e uymaz.
     */
    @GetMapping("/tefas/{code:[A-Z0-9]{2,6}}")
    public ResponseEntity<ApiResponse<RasyonetFundDetailDto>> getTefasFundDetail(
            @PathVariable String code,
            @RequestParam(defaultValue = "TMF") String sourceCode) {
        RasyonetFundDetailDto detail = rasyonetFundService.getFundDetailRich(code.toUpperCase(), sourceCode.toUpperCase());
        if (detail == null) {
            return ResponseEntity.ok(ApiResponse.success(null, "Fon bulunamadı: " + code));
        }
        return ResponseEntity.ok(ApiResponse.success(detail, "Fund detail retrieved via Rasyonet"));
    }

    /**
     * GET /api/v1/market/funds/{code}/price-history?range=1Y&type=YAT
     * Fon birim pay fiyatı tarihsel serisi — TEFAS resmi API'den (grafik için, 5 yıla kadar).
     * type: YAT (TEFAS yatırım fonu, default) | EMK (emeklilik). Veri yoksa points boş döner
     * (frontend Rasyonet'e fallback eder).
     */
    @GetMapping("/{code:[A-Z0-9]{2,6}}/price-history")
    public ResponseEntity<ApiResponse<FundPriceHistoryResponse>> getFundPriceHistory(
            @PathVariable String code,
            @RequestParam(defaultValue = "1Y") String range,
            @RequestParam(defaultValue = "YAT") String type) {
        FundPriceHistoryResponse resp = tefasFundService.getPriceHistory(
                code.toUpperCase(), range.toUpperCase(), type.toUpperCase());
        return ResponseEntity.ok(ApiResponse.success(resp,
                "Fund price history via TEFAS (" + resp.getPoints().size() + " points)"));
    }
}
