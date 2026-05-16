package com.finance.portal.portfolio.application.port;

import com.finance.portal.common.domain.AssetType;
import com.finance.portal.portfolio.domain.Portfolio;
import com.finance.portal.portfolio.domain.WatchlistItem;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Portföy, işlem ve izleme listesi kalıcılık portu.
 */
public interface PortfolioPersistencePort {

    boolean existsByUserIdAndName(String userId, String name);

    Portfolio savePortfolio(Portfolio portfolio);

    List<Portfolio> findByUserId(String userId);

    Optional<Portfolio> findByIdAndUserId(UUID portfolioId, String userId);

    void deletePortfolio(Portfolio portfolio);

    int deleteTransactionByIdForPortfolioAndUser(UUID transactionId, UUID portfolioId, String userId);

    List<WatchlistItem> findWatchlistByPortfolioId(UUID portfolioId);

    long countWatchlistByPortfolioId(UUID portfolioId);

    boolean existsWatchlistItem(UUID portfolioId, String symbol, AssetType assetType);

    WatchlistItem saveWatchlistItem(WatchlistItem item);

    Optional<WatchlistItem> findWatchlistItemByIdAndPortfolioId(UUID itemId, UUID portfolioId);

    void deleteWatchlistItemByIdAndPortfolioId(UUID itemId, UUID portfolioId);
}
