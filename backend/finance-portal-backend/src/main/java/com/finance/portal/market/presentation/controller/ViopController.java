package com.finance.portal.market.presentation.controller;

import com.finance.portal.common.presentation.dto.ApiResponse;
import com.finance.portal.market.application.viop.ViopContract;
import com.finance.portal.market.application.viop.ViopService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/market/viop")
public class ViopController {

    private static final Logger log = LoggerFactory.getLogger(ViopController.class);

    private final ViopService viopService;

    public ViopController(ViopService viopService) {
        this.viopService = viopService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ViopContract>>> getContracts() {
        List<ViopContract> contracts = viopService.getAllContracts();
        log.info("VIOP contracts count: {}", contracts.size());
        if (!contracts.isEmpty()) {
            log.info("First contract: {}", contracts.get(0).getName());
        }
        return ResponseEntity.ok(ApiResponse.success(contracts, "VIOP contracts retrieved successfully"));
    }
}
