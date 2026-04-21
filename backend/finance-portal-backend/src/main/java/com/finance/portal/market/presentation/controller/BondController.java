package com.finance.portal.market.presentation.controller;

import com.finance.portal.common.presentation.dto.ApiResponse;
import com.finance.portal.market.application.bond.BondItem;
import com.finance.portal.market.application.bond.BondService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/market/bonds")
public class BondController {

    private final BondService bondService;

    public BondController(BondService bondService) {
        this.bondService = bondService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<BondItem>>> getBonds() {
        List<BondItem> bonds = bondService.getBonds();
        return ResponseEntity.ok(ApiResponse.success(bonds, "Bond data retrieved successfully"));
    }
}
