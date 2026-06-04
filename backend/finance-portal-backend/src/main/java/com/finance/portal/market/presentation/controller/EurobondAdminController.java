package com.finance.portal.market.presentation.controller;

import com.finance.portal.common.presentation.dto.ApiResponse;
import com.finance.portal.market.application.bond.eurobond.EurobondService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

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
    public ResponseEntity<ApiResponse<Map<String, Object>>> status() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("count", eurobondService.currentIsins().size());
        body.put("isins", eurobondService.currentIsins());
        return ResponseEntity.ok(ApiResponse.success(body, "Güncel Eurobond ISIN listesi"));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<Map<String, Object>>> refresh(
            @RequestParam String xlsxUrl,
            @RequestParam(required = false, defaultValue = "false") boolean force) {
        try {
            int count = eurobondService.refreshIsins(xlsxUrl, force);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("count", count);
            body.put("xlsxUrl", xlsxUrl);
            body.put("force", force);
            return ResponseEntity.ok(ApiResponse.success(body, count + " ISIN yüklendi ve cache boşaltıldı."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(502).body(ApiResponse.error("HMB xlsx işlenemedi: " + e.getMessage()));
        }
    }
}
