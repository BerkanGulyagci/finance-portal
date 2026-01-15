package com.finance.portal.application.service;

import org.springframework.stereotype.Service;

@Service
public class HealthService {

    public String getHealthStatus() {
        return "Finance Portal Backend is running";
    }
}
