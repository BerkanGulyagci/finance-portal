package com.finance.portal.market.presentation.controller;

import com.finance.portal.common.presentation.dto.ApiResponse;
import com.finance.portal.market.application.ipo.IpoItem;
import com.finance.portal.market.application.ipo.IpoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/market/ipo")
public class IpoController {

    private final IpoService ipoService;

    public IpoController(IpoService ipoService) {
        this.ipoService = ipoService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<IpoItem>>> getIpos() {
        List<IpoItem> ipos = ipoService.getIpos();
        return ResponseEntity.ok(ApiResponse.success(ipos, "IPO calendar retrieved successfully"));
    }
}
