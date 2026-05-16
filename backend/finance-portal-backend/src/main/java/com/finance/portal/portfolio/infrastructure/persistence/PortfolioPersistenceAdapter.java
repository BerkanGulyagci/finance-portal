package com.finance.portal.portfolio.infrastructure.persistence;

import com.finance.portal.common.domain.AssetType;
import com.finance.portal.portfolio.application.port.PortfolioPersistencePort;
import com.finance.portal.portfolio.domain.Portfolio;
import com.finance.portal.portfolio.domain.WatchlistItem;
import com.finance.portal.portfolio.repository.PortfolioRepository;
import com.finance.portal.portfolio.repository.PortfolioTransactionRepository;
import com.finance.portal.portfolio.repository.WatchlistItemRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class PortfolioPersistenceAdapter implements PortfolioPersistencePort {

    private final PortfolioRepository portfolioRepository;
    private final PortfolioTransactionRepository portfolioTransactionRepository;
    private final WatchlistItemRepository watchlistItemRepository;

    public PortfolioPersistenceAdapter(PortfolioRepository portfolioRepository,
                                       PortfolioTransactionRepository portfolioTransactionRepository,
                                       WatchlistItemRepository watchlistItemRepository) {
        this.portfolioRepository = portfolioRepository;
        this.portfolioTransactionRepository = portfolioTransactionRepository;
        this.watchlistItemRepository = watchlistItemRepository;
    }

    @Override
    public boolean existsByUserIdAndName(String userId, String name) {
        return portfolioRepository.existsByUserIdAndName(userId, name);
    }

    @Override
    public Portfolio savePortfolio(Portfolio portfolio) {
        return portfolioRepository.save(portfolio);
    }

    @Override
    public List<Portfolio> findByUserId(String userId) {
        return portfolioRepository.findByUserId(userId);
    }

    @Override
    public Optional<Portfolio> findByIdAndUserId(UUID portfolioId, String userId) {
        return portfolioRepository.findByIdAndUserId(portfolioId, userId);
    }

    @Override
    public void deletePortfolio(Portfolio portfolio) {
        portfolioRepository.delete(portfolio);
    }

    @Override
    public int deleteTransactionByIdForPortfolioAndUser(UUID transactionId, UUID portfolioId, String userId) {
        return portfolioTransactionRepository.deleteByIdForPortfolioAndUser(transactionId, portfolioId, userId);
    }

    @Override
    public List<WatchlistItem> findWatchlistByPortfolioId(UUID portfolioId) {
        return watchlistItemRepository.findByPortfolioId(portfolioId);
    }

    @Override
    public long countWatchlistByPortfolioId(UUID portfolioId) {
        return watchlistItemRepository.countByPortfolioId(portfolioId);
    }

    @Override
    public boolean existsWatchlistItem(UUID portfolioId, String symbol, AssetType assetType) {
        return watchlistItemRepository.existsByPortfolioIdAndSymbolAndAssetType(portfolioId, symbol, assetType);
    }

    @Override
    public WatchlistItem saveWatchlistItem(WatchlistItem item) {
        return watchlistItemRepository.save(item);
    }

    @Override
    public Optional<WatchlistItem> findWatchlistItemByIdAndPortfolioId(UUID itemId, UUID portfolioId) {
        return watchlistItemRepository.findByIdAndPortfolioId(itemId, portfolioId);
    }

    @Override
    public void deleteWatchlistItemByIdAndPortfolioId(UUID itemId, UUID portfolioId) {
        watchlistItemRepository.deleteByIdAndPortfolioId(itemId, portfolioId);
    }
}
