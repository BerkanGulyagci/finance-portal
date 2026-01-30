package com.finance.portal.application.service;

import com.finance.portal.presentation.dto.HealthResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class HealthService {

    @Value("${spring.application.name:finance-portal-backend}")
    private String applicationName;

    @Value("${application.version:1.0.0}")
    private String applicationVersion;

    public HealthResponse getHealthStatus() {
        return new HealthResponse(
            "UP", 
            applicationName, 
            applicationVersion
        );
    }
}
