package com.finance.portal.market.presentation.controller;

import com.finance.portal.common.presentation.dto.ApiResponse;
import com.finance.portal.market.application.bond.eurobond.EurobondService;
import com.finance.portal.market.presentation.dto.EurobondRefreshDto;
import com.finance.portal.market.presentation.dto.EurobondStatusDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Eurobond ISIN listesi yönetimi (yalnız ADMIN — /api/v1/admin/** ROLE_ADMIN korumalı).
 * Aylık HMB xlsx linkini buradan verince ISIN listesi indirilir/parse edilir ve cache boşaltılır.
 */
@RestController
@RequestMapping("/api/v1/admin/eurobonds")
public class EurobondAdminController {

    private final EurobondService eurobondService;

    public EurobondAdminController(EurobondService eurobondService) {
        this.eurobondService = eurobondService;
    }

    @GetMapping("/isins")
    public ResponseEntity<ApiResponse<EurobondStatusDto>> status() {
        var isins = eurobondService.currentIsins();
        return ResponseEntity.ok(ApiResponse.success(new EurobondStatusDto(isins.size(), isins), "Güncel Eurobond ISIN listesi"));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<EurobondRefreshDto>> refresh(
            @RequestParam String xlsxUrl,
            @RequestParam(required = false, defaultValue = "false") boolean force) {
        try {
            int count = eurobondService.refreshIsins(xlsxUrl, force);
            return ResponseEntity.ok(ApiResponse.success(new EurobondRefreshDto(count, xlsxUrl, force), count + " ISIN yüklendi ve cache boşaltıldı."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(502).body(ApiResponse.error("HMB xlsx işlenemedi: " + e.getMessage()));
        }
    }
}
