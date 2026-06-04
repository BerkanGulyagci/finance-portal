package com.finance.portal.market.presentation.controller;

import com.finance.portal.common.presentation.dto.ApiResponse;
import com.finance.portal.market.application.bond.evds.EvdsBondService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * EVDS DİBS yönetim endpoint'leri (yalnız ADMIN — /api/v1/admin/** ROLE_ADMIN korumalı).
 *
 * <p>Maturity parser fix gibi tek seferlik deploy-sonrası temizlikler için:
 * <pre>
 *   curl -X POST -H "Authorization: Bearer &lt;admin-token&gt;" \
 *        http://localhost:8080/api/v1/admin/bonds/evds/cache-evict
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/admin/bonds/evds")
public class EvdsBondAdminController {

    private static final Logger log = LoggerFactory.getLogger(EvdsBondAdminController.class);

    private final EvdsBondService evdsBondService;

    public EvdsBondAdminController(EvdsBondService evdsBondService) {
        this.evdsBondService = evdsBondService;
    }

    /**
     * Tüm EVDS DİBS cache'lerini boşaltır (list / detail / history / active-series).
     * Maturity parser düzeltmesi gibi deploy-sonrası anlık tazeleme için.
     */
    @PostMapping("/cache-evict")
    public ResponseEntity<ApiResponse<Map<String, Object>>> evictCaches() {
        log.info("[EvdsBondAdminController] /cache-evict çağrıldı — tüm DİBS cache'leri boşaltılıyor.");
        evdsBondService.evictAllBondCaches();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("evicted", java.util.List.of(
                "market.evds.bonds.list",
                "market.evds.bonds.detail",
                "market.evds.bonds.history",
                "market.evds.bonds.active-series"
        ));
        return ResponseEntity.ok(ApiResponse.success(body, "EVDS DİBS cache'leri boşaltıldı."));
    }
}
