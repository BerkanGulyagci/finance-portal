package com.finance.portal.portfolio.application.port;

import com.finance.portal.portfolio.presentation.dto.PortfolioResponse;
import com.finance.portal.portfolio.presentation.dto.WatchlistItemResponse;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Portföy okuma yanıtları için cache-aside portu (Redis implementasyonu).
 */
public interface PortfolioCachePort {

    Optional<List<PortfolioResponse>> getPortfolioList(String userId);

    void putPortfolioList(String userId, List<PortfolioResponse> value);

    Optional<PortfolioResponse> getPortfolioDetail(String userId, UUID portfolioId);

    void putPortfolioDetail(String userId, UUID portfolioId, PortfolioResponse value);

    Optional<List<WatchlistItemResponse>> getWatchlist(String userId, UUID portfolioId);

    void putWatchlist(String userId, UUID portfolioId, List<WatchlistItemResponse> value);

    void evictList(String userId);

    void evictListDetailAndWatchlist(String userId, UUID portfolioId);

    void evictListAndDetail(String userId, UUID portfolioId);
}
