package com.finance.portal.common.presentation.controller;

import com.finance.portal.common.application.health.HealthService;
import com.finance.portal.common.application.health.model.HealthStatus;
import com.finance.portal.common.presentation.dto.ApiResponse;
import com.finance.portal.common.presentation.dto.HealthResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class HealthController {

    private final HealthService healthService;

    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping("/health")
    public ResponseEntity<ApiResponse<HealthResponse>> health() {
        HealthStatus status = healthService.getHealthStatus();
        HealthResponse healthResponse = new HealthResponse(
                status.status(),
                status.applicationName(),
                status.applicationVersion()
        );
        return ResponseEntity.ok(ApiResponse.success(healthResponse, "Health check completed successfully"));
    }
}
