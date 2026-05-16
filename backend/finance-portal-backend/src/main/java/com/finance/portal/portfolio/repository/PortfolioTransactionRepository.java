package com.finance.portal.portfolio.repository;

import com.finance.portal.portfolio.domain.PortfolioTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PortfolioTransactionRepository extends JpaRepository<PortfolioTransaction, UUID> {

    List<PortfolioTransaction> findByPortfolioId(UUID portfolioId);

    List<PortfolioTransaction> findByPortfolioIdOrderByTransactionDateDesc(UUID portfolioId);

    /**
     * Koleksiyon üzerinden aramak yerine doğrudan DB'den siler;
     * lazy OneToMany ile senkron olmayan bellek durumlarında "Transaction not found" hatasını önler.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            DELETE FROM PortfolioTransaction t
            WHERE t.id = :transactionId
              AND t.portfolio.id = :portfolioId
              AND t.portfolio.userId = :userId
            """)
    int deleteByIdForPortfolioAndUser(
            @Param("transactionId") UUID transactionId,
            @Param("portfolioId") UUID portfolioId,
            @Param("userId") String userId);
}

