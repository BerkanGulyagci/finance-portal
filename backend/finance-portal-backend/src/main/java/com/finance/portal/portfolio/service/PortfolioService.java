package com.finance.portal.portfolio.service;

import com.finance.portal.portfolio.dto.AddTransactionRequest;
import com.finance.portal.portfolio.dto.AddWatchlistItemRequest;
import com.finance.portal.portfolio.dto.CreatePortfolioRequest;
import com.finance.portal.portfolio.dto.PortfolioResponse;
import com.finance.portal.portfolio.dto.UpdatePortfolioRequest;
import com.finance.portal.portfolio.dto.WatchlistItemResponse;

import java.util.List;
import java.util.UUID;

public interface PortfolioService {

    // ── HOLDINGS portföy işlemleri ────────────────────────────────────────────

    PortfolioResponse createPortfolio(String userId, CreatePortfolioRequest request);

    PortfolioResponse addTransaction(String userId, UUID portfolioId, AddTransactionRequest request);

    PortfolioResponse getPortfolioById(String userId, UUID portfolioId);

    List<PortfolioResponse> getUserPortfolios(String userId);

    PortfolioResponse updatePortfolio(String userId, UUID portfolioId, UpdatePortfolioRequest request);

    void deletePortfolio(String userId, UUID portfolioId);

    PortfolioResponse deleteTransaction(String userId, UUID portfolioId, UUID transactionId);

    // ── WATCHLIST işlemleri ───────────────────────────────────────────────────

    List<WatchlistItemResponse> getWatchlistItems(String userId, UUID portfolioId);

    WatchlistItemResponse addWatchlistItem(String userId, UUID portfolioId, AddWatchlistItemRequest request);

    void deleteWatchlistItem(String userId, UUID portfolioId, UUID itemId);
}
