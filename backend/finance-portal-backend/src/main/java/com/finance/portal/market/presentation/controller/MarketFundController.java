package com.finance.portal.market.presentation.controller;

import com.finance.portal.common.presentation.dto.ApiResponse;
import com.finance.portal.market.application.funds.model.RasyonetFundDetailDto;
import com.finance.portal.market.application.funds.model.RasyonetFundDto;
import com.finance.portal.market.application.funds.model.RasyonetOsmanliFundBulletinDto;
import com.finance.portal.market.application.funds.service.RasyonetFundService;
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
 *   GET /api/market/funds/tefas/all          → TMF (TEFAS yatırım fonları)
 *   GET /api/market/funds/bes/all            → TPF (BES fonları)
 *   GET /api/market/funds/oks/all            → TAF (OKS fonları)
 *   GET /api/market/funds/osmanli/bulletin   → Osmanlı Portföy fon bülteni
 *   GET /api/market/funds/tefas/{code}       → Fon detayı (Rasyonet card)
 */
@Validated
@RestController
@RequestMapping("/api/market/funds")
public class MarketFundController {

    private final RasyonetFundService rasyonetFundService;

    public MarketFundController(RasyonetFundService rasyonetFundService) {
        this.rasyonetFundService = rasyonetFundService;
    }

    /** GET /api/market/funds/tefas/all — TMF yatırım fonları */
    @GetMapping("/tefas/all")
    public ResponseEntity<ApiResponse<List<RasyonetFundDto>>> getAllTefasFunds() {
        List<RasyonetFundDto> funds = rasyonetFundService.getAllFunds();
        return ResponseEntity.ok(ApiResponse.success(funds,
                "TEFAS funds retrieved via Rasyonet (" + funds.size() + " funds)"));
    }

    /** GET /api/market/funds/bes/all — TPF BES fonları */
    @GetMapping("/bes/all")
    public ResponseEntity<ApiResponse<List<RasyonetFundDto>>> getAllBesFunds() {
        List<RasyonetFundDto> funds = rasyonetFundService.getAllBesFunds();
        return ResponseEntity.ok(ApiResponse.success(funds,
                "BES funds retrieved via Rasyonet (" + funds.size() + " funds)"));
    }

    /** GET /api/market/funds/oks/all — TAF OKS fonları */
    @GetMapping("/oks/all")
    public ResponseEntity<ApiResponse<List<RasyonetFundDto>>> getAllOksFunds() {
        List<RasyonetFundDto> funds = rasyonetFundService.getAllOksFunds();
        return ResponseEntity.ok(ApiResponse.success(funds,
                "OKS funds retrieved via Rasyonet (" + funds.size() + " funds)"));
    }

    /** GET /api/market/funds/osmanli/bulletin — Osmanlı Portföy fon bülteni */
    @GetMapping("/osmanli/bulletin")
    public ResponseEntity<ApiResponse<List<RasyonetOsmanliFundBulletinDto>>> getOsmanliFundBulletin() {
        List<RasyonetOsmanliFundBulletinDto> funds = rasyonetFundService.getOsmanliFundBulletin();
        return ResponseEntity.ok(ApiResponse.success(funds,
                "Osmanlı fund bulletin retrieved (" + funds.size() + " funds)"));
    }

    /**
     * GET /api/market/funds/tefas/{code}?sourceCode=TMF — Fon detayı (Rasyonet card endpoint)
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
}
