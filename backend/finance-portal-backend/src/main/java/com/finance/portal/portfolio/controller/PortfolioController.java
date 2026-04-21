package com.finance.portal.portfolio.controller;

import com.finance.portal.common.presentation.dto.ApiResponse;
import com.finance.portal.portfolio.dto.AddTransactionRequest;
import com.finance.portal.portfolio.dto.CreatePortfolioRequest;
import com.finance.portal.portfolio.dto.PortfolioResponse;
import com.finance.portal.portfolio.service.PortfolioService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/portfolios")
public class PortfolioController {

    private final PortfolioService portfolioService;

    public PortfolioController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PortfolioResponse>> createPortfolio(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreatePortfolioRequest request
    ) {
        String userId = jwt.getSubject();
        PortfolioResponse portfolio = portfolioService.createPortfolio(userId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(portfolio, "Portfolio created successfully"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PortfolioResponse>>> getUserPortfolios(
            @AuthenticationPrincipal Jwt jwt
    ) {
        String userId = jwt.getSubject();
        List<PortfolioResponse> portfolios = portfolioService.getUserPortfolios(userId);
        return ResponseEntity.ok(ApiResponse.success(portfolios, "Portfolios retrieved successfully"));
    }

    @GetMapping("/{portfolioId}")
    public ResponseEntity<ApiResponse<PortfolioResponse>> getPortfolioById(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID portfolioId
    ) {
        String userId = jwt.getSubject();
        PortfolioResponse portfolio = portfolioService.getPortfolioById(userId, portfolioId);
        return ResponseEntity.ok(ApiResponse.success(portfolio, "Portfolio retrieved successfully"));
    }

    @PostMapping("/{portfolioId}/transactions")
    public ResponseEntity<ApiResponse<PortfolioResponse>> addTransaction(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID portfolioId,
            @Valid @RequestBody AddTransactionRequest request
    ) {
        String userId = jwt.getSubject();
        PortfolioResponse portfolio = portfolioService.addTransaction(userId, portfolioId, request);
        return ResponseEntity.ok(ApiResponse.success(portfolio, "Transaction added successfully"));
    }

    @DeleteMapping("/{portfolioId}")
    public ResponseEntity<ApiResponse<Void>> deletePortfolio(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID portfolioId
    ) {
        String userId = jwt.getSubject();
        portfolioService.deletePortfolio(userId, portfolioId);
        return ResponseEntity.ok(ApiResponse.success(null, "Portfolio deleted successfully"));
    }

    @DeleteMapping("/{portfolioId}/transactions/{transactionId}")
    public ResponseEntity<ApiResponse<PortfolioResponse>> deleteTransaction(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID portfolioId,
            @PathVariable UUID transactionId
    ) {
        String userId = jwt.getSubject();
        PortfolioResponse portfolio = portfolioService.deleteTransaction(userId, portfolioId, transactionId);
        return ResponseEntity.ok(ApiResponse.success(portfolio, "Transaction deleted successfully"));
    }
}
