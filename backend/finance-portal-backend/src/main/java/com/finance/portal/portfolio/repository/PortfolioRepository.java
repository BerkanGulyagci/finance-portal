package com.finance.portal.portfolio.repository;

import com.finance.portal.portfolio.domain.Portfolio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PortfolioRepository extends JpaRepository<Portfolio, UUID> {

    List<Portfolio> findByUserId(String userId);

    Optional<Portfolio> findByIdAndUserId(UUID id, String userId);

    boolean existsByUserIdAndName(String userId, String name);
}

