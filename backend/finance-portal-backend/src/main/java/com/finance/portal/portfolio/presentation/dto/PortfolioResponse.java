package com.finance.portal.portfolio.presentation.dto;

import com.finance.portal.portfolio.domain.PortfolioType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class PortfolioResponse {

    private UUID id;
    private String name;
    private String description;
    private String currency;
    private PortfolioType portfolioType;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<PortfolioTransactionResponse> transactions;
    private List<PortfolioHoldingResponse> holdings;
    private BigDecimal totalCost;
    private BigDecimal totalMarketValue;
    private BigDecimal totalProfitLoss;

    /**
     * Toplam gerçekleşmiş K/Z (TL): hem açık holding'lerin satışlarından gelen
     * realizedGainLoss hem de tamamen kapatılmış pozisyonların biriktirdiği realized
     * K/Z dahildir. Açık pozisyonun realizedGainLoss alanı holding satırında ayrıca
     * gösterilirken kapatılmış pozisyonlar yalnızca bu toplam içinde temsil edilir.
     */
    private BigDecimal totalRealizedProfitLoss;

    /** Enflasyona göre düzeltilmiş toplam reel K/Z (yalnızca TL pozisyonlar üzerinden). */
    private BigDecimal totalRealProfitLoss;
    /** Toplam reel getiri yüzdesi (reel K/Z / toplam reel maliyet × 100). */
    private BigDecimal totalRealProfitLossPercent;

    /** Sadece WATCHLIST portföyler için dolar; HOLDINGS'da null. */
    private Long watchlistItemCount;
}
