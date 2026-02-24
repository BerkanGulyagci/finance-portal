package com.finance.portal.common.presentation.controller;

import com.finance.portal.common.application.service.HealthService;
import com.finance.portal.common.presentation.dto.ApiResponse;
import com.finance.portal.common.presentation.dto.HealthResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HealthController {

    private final HealthService healthService;

    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping("/health")
    public ResponseEntity<ApiResponse<HealthResponse>> health() {
        HealthResponse healthStatus = healthService.getHealthStatus();
        ApiResponse<HealthResponse> response = ApiResponse.success(
            healthStatus, 
            "Health check completed successfully"
        );
        return ResponseEntity.ok(response);
    }
}
