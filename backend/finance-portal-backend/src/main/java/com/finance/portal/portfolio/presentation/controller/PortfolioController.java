package com.finance.portal.portfolio.presentation.controller;

import com.finance.portal.common.domain.AssetType;
import com.finance.portal.common.presentation.dto.ApiResponse;
import com.finance.portal.portfolio.application.performance.PortfolioPerformanceResult;
import com.finance.portal.portfolio.application.port.PortfolioHistoricalPricePort;
import com.finance.portal.portfolio.application.whatif.PortfolioWhatIfResult;
import com.finance.portal.portfolio.application.whatif.WhatIfSeriesResult;
import com.finance.portal.portfolio.presentation.dto.AddCouponIncomeRequest;
import com.finance.portal.portfolio.presentation.dto.AddTransactionRequest;
import com.finance.portal.portfolio.presentation.dto.AddWatchlistItemRequest;
import com.finance.portal.portfolio.presentation.dto.CreatePortfolioRequest;
import com.finance.portal.portfolio.presentation.dto.PortfolioResponse;
import com.finance.portal.portfolio.presentation.dto.PriceAtDateResponse;
import com.finance.portal.portfolio.presentation.dto.UpdatePortfolioRequest;
import com.finance.portal.portfolio.presentation.dto.WatchlistItemResponse;
import com.finance.portal.portfolio.service.PortfolioService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/portfolios")
public class PortfolioController {

    private final PortfolioService portfolioService;
    private final PortfolioHistoricalPricePort historicalPricePort;

    public PortfolioController(PortfolioService portfolioService,
                              PortfolioHistoricalPricePort historicalPricePort) {
        this.portfolioService = portfolioService;
        this.historicalPricePort = historicalPricePort;
    }

    /**
     * Bir varlığın belirli bir tarihteki kapanış fiyatı (işlem ekleme modalında otomatik
     * doldurma için). İstenen tarihte veri yoksa o tarihten önceki en yakın işlem günü
     * kullanılır; hiç veri yoksa (örn. tipin o kadar eski geçmişi yok) {@code found=false}.
     */
    @GetMapping("/price-at")
    public ResponseEntity<ApiResponse<PriceAtDateResponse>> getPriceAtDate(
            @RequestParam String assetType,
            @RequestParam String symbol,
            @RequestParam String date) {
        PriceAtDateResponse body;
        try {
            AssetType type = AssetType.valueOf(assetType.trim().toUpperCase());
            LocalDate d = LocalDate.parse(date.trim());
            Optional<NavigableMap<LocalDate, BigDecimal>> series =
                    historicalPricePort.fetchDailyClosePrices(type, symbol, d.minusDays(10), d);
            BigDecimal price = null;
            LocalDate priceDate = null;
            if (series.isPresent()) {
                Map.Entry<LocalDate, BigDecimal> entry = series.get().floorEntry(d);
                if (entry != null) {
                    price = entry.getValue();
                    priceDate = entry.getKey();
                }
            }
            body = new PriceAtDateResponse(price, priceDate, price != null);
        } catch (Exception e) {
            body = new PriceAtDateResponse(null, null, false);
        }
        return ResponseEntity.ok(ApiResponse.success(body));
    }

    // ── HOLDINGS portföy endpointleri ─────────────────────────────────────────

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

    @GetMapping("/{portfolioId}/performance")
    public ResponseEntity<ApiResponse<PortfolioPerformanceResult>> getPortfolioPerformance(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID portfolioId,
            @RequestParam String range,
            @RequestParam(defaultValue = "VALUE") String metric
    ) {
        String userId = jwt.getSubject();
        PortfolioPerformanceResult performance =
                portfolioService.getPortfolioPerformance(userId, portfolioId, range, metric);
        return ResponseEntity.ok(ApiResponse.success(performance, "Portfolio performance retrieved successfully"));
    }

    @GetMapping("/{portfolioId}/what-if")
    public ResponseEntity<ApiResponse<PortfolioWhatIfResult>> getPortfolioWhatIf(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID portfolioId
    ) {
        String userId = jwt.getSubject();
        PortfolioWhatIfResult whatIf = portfolioService.getPortfolioWhatIf(userId, portfolioId);
        return ResponseEntity.ok(ApiResponse.success(whatIf, "Portfolio what-if retrieved successfully"));
    }

    @GetMapping("/{portfolioId}/what-if-series")
    public ResponseEntity<ApiResponse<WhatIfSeriesResult>> getPortfolioWhatIfSeries(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID portfolioId,
            @RequestParam(required = false) String assetType,
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) List<String> benchmark,
            @RequestParam(required = false) String simAssetType,
            @RequestParam(required = false) String simSymbol,
            @RequestParam(required = false) BigDecimal simAmount,
            @RequestParam(required = false) String simDate
    ) {
        String userId = jwt.getSubject();
        WhatIfSeriesResult series;
        // Özel/simülasyon modu: sim* parametreleri varsa portföy yerine sentetik pozisyon kullanılır.
        if (simSymbol != null && !simSymbol.isBlank() && simAmount != null && simDate != null && !simDate.isBlank()) {
            LocalDate date;
            try {
                date = LocalDate.parse(simDate.trim());
            } catch (Exception e) {
                date = null;
            }
            series = portfolioService.getPortfolioWhatIfSimSeries(
                    userId, portfolioId, simAssetType, simSymbol, simAmount, date, benchmark);
        } else {
            series = portfolioService.getPortfolioWhatIfSeries(userId, portfolioId, assetType, symbol, benchmark);
        }
        return ResponseEntity.ok(ApiResponse.success(series, "Portfolio what-if series retrieved successfully"));
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

    /**
     * Manuel DİBS/Kira Sertifikası kupon ödeme kaydı. Realized gelir olarak yansır.
     * Açık pozisyon (nominal/cost) etkilenmez.
     */
    @PostMapping("/{portfolioId}/coupon-income")
    public ResponseEntity<ApiResponse<PortfolioResponse>> addCouponIncome(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID portfolioId,
            @Valid @RequestBody AddCouponIncomeRequest request
    ) {
        String userId = jwt.getSubject();
        PortfolioResponse portfolio = portfolioService.addCouponIncome(userId, portfolioId, request);
        return ResponseEntity.ok(ApiResponse.success(portfolio, "Coupon income added successfully"));
    }

    @PatchMapping("/{portfolioId}")
    public ResponseEntity<ApiResponse<PortfolioResponse>> updatePortfolio(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID portfolioId,
            @Valid @RequestBody UpdatePortfolioRequest request
    ) {
        String userId = jwt.getSubject();
        PortfolioResponse portfolio = portfolioService.updatePortfolio(userId, portfolioId, request);
        return ResponseEntity.ok(ApiResponse.success(portfolio, "Portfolio updated successfully"));
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

    // ── WATCHLIST endpointleri ────────────────────────────────────────────────

    /**
     * GET /api/portfolios/{portfolioId}/watchlist
     * İzleme listesindeki tüm sembolleri döner.
     * Şu an sadece DB alanları (id, symbol, assetType, notes, addedAt).
     * Canlı fiyat bilgisi ileride eklenecek.
     */
    @GetMapping("/{portfolioId}/watchlist")
    public ResponseEntity<ApiResponse<List<WatchlistItemResponse>>> getWatchlistItems(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID portfolioId
    ) {
        String userId = jwt.getSubject();
        List<WatchlistItemResponse> items = portfolioService.getWatchlistItems(userId, portfolioId);
        return ResponseEntity.ok(ApiResponse.success(items, "Watchlist items retrieved successfully"));
    }

    /**
     * POST /api/portfolios/{portfolioId}/watchlist
     * İzleme listesine sembol ekler.
     */
    @PostMapping("/{portfolioId}/watchlist")
    public ResponseEntity<ApiResponse<WatchlistItemResponse>> addWatchlistItem(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID portfolioId,
            @Valid @RequestBody AddWatchlistItemRequest request
    ) {
        String userId = jwt.getSubject();
        WatchlistItemResponse item = portfolioService.addWatchlistItem(userId, portfolioId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(item, "Watchlist item added successfully"));
    }

    /**
     * DELETE /api/portfolios/{portfolioId}/watchlist/{itemId}
     * İzleme listesinden sembol siler.
     */
    @DeleteMapping("/{portfolioId}/watchlist/{itemId}")
    public ResponseEntity<ApiResponse<Void>> deleteWatchlistItem(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID portfolioId,
            @PathVariable UUID itemId
    ) {
        String userId = jwt.getSubject();
        portfolioService.deleteWatchlistItem(userId, portfolioId, itemId);
        return ResponseEntity.ok(ApiResponse.success(null, "Watchlist item deleted successfully"));
    }
}
