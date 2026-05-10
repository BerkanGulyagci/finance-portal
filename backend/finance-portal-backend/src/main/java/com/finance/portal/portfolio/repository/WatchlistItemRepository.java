package com.finance.portal.portfolio.repository;

import com.finance.portal.common.domain.AssetType;
import com.finance.portal.portfolio.domain.WatchlistItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WatchlistItemRepository extends JpaRepository<WatchlistItem, UUID> {

    List<WatchlistItem> findByPortfolioId(UUID portfolioId);

    long countByPortfolioId(UUID portfolioId);

    boolean existsByPortfolioIdAndSymbolAndAssetType(UUID portfolioId, String symbol, AssetType assetType);

    Optional<WatchlistItem> findByIdAndPortfolioId(UUID id, UUID portfolioId);

    void deleteByIdAndPortfolioId(UUID id, UUID portfolioId);
}
