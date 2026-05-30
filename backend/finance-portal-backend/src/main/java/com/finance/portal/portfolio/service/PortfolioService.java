package com.finance.portal.portfolio.service;

import com.finance.portal.portfolio.application.performance.PortfolioPerformanceResult;
import com.finance.portal.portfolio.application.whatif.PortfolioWhatIfResult;
import com.finance.portal.portfolio.application.whatif.WhatIfSeriesResult;
import com.finance.portal.portfolio.presentation.dto.AddCouponIncomeRequest;
import com.finance.portal.portfolio.presentation.dto.AddTransactionRequest;
import com.finance.portal.portfolio.presentation.dto.AddWatchlistItemRequest;
import com.finance.portal.portfolio.presentation.dto.CreatePortfolioRequest;
import com.finance.portal.portfolio.presentation.dto.PortfolioResponse;
import com.finance.portal.portfolio.presentation.dto.UpdatePortfolioRequest;
import com.finance.portal.portfolio.presentation.dto.WatchlistItemResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface PortfolioService {

    // ── HOLDINGS portföy işlemleri ────────────────────────────────────────────

    PortfolioResponse createPortfolio(String userId, CreatePortfolioRequest request);

    PortfolioResponse addTransaction(String userId, UUID portfolioId, AddTransactionRequest request);

    /**
     * Manuel DİBS/Sukuk kupon ödemesi kaydeder. Realized gelir olarak portföye yansır;
     * açık pozisyon (nominal/cost) etkilenmez.
     */
    PortfolioResponse addCouponIncome(String userId, UUID portfolioId, AddCouponIncomeRequest request);

    PortfolioResponse getPortfolioById(String userId, UUID portfolioId);

    PortfolioPerformanceResult getPortfolioPerformance(String userId, UUID portfolioId, String range, String metric);

    /** Portföy geneli "Ne Olurdu?" — yatırımların enflasyon/altın/dolar/mevduata gitseydi bugünkü karşılığı. */
    PortfolioWhatIfResult getPortfolioWhatIf(String userId, UUID portfolioId);

    /** "Ne Olurdu?" zaman serisi — seçilen varlık (assetType+symbol) ya da tüm portföy (null) + isteğe bağlı serbest kıyaslar. */
    WhatIfSeriesResult getPortfolioWhatIfSeries(String userId, UUID portfolioId, String assetType,
                                               String symbol, java.util.List<String> benchmarks);

    /** "Ne Olurdu?" özel/simülasyon modu — istenen tarihte X TL ile alınan istenen varlık + isteğe bağlı serbest kıyaslar. */
    WhatIfSeriesResult getPortfolioWhatIfSimSeries(String userId, UUID portfolioId, String assetType,
                                                  String symbol, BigDecimal amount, LocalDate date,
                                                  List<String> benchmarks);

    List<PortfolioResponse> getUserPortfolios(String userId);

    PortfolioResponse updatePortfolio(String userId, UUID portfolioId, UpdatePortfolioRequest request);

    void deletePortfolio(String userId, UUID portfolioId);

    PortfolioResponse deleteTransaction(String userId, UUID portfolioId, UUID transactionId);

    // ── WATCHLIST işlemleri ───────────────────────────────────────────────────

    List<WatchlistItemResponse> getWatchlistItems(String userId, UUID portfolioId);

    WatchlistItemResponse addWatchlistItem(String userId, UUID portfolioId, AddWatchlistItemRequest request);

    void deleteWatchlistItem(String userId, UUID portfolioId, UUID itemId);
}
