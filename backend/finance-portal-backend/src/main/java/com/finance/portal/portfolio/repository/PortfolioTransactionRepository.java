package com.finance.portal.portfolio.repository;

import com.finance.portal.portfolio.domain.PortfolioTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PortfolioTransactionRepository extends JpaRepository<PortfolioTransaction, UUID> {

    List<PortfolioTransaction> findByPortfolioId(UUID portfolioId);

    List<PortfolioTransaction> findByPortfolioIdOrderByTransactionDateDesc(UUID portfolioId);
}

