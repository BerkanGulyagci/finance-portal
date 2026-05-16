package com.finance.portal.portfolio.presentation.dto;

import com.finance.portal.common.domain.AssetType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class PortfolioHoldingResponse {

    private String symbol;
    private AssetType assetType;
    private BigDecimal totalQuantity;
    private BigDecimal averageCost;
    private BigDecimal totalCost;
    private BigDecimal currentPrice;
    private BigDecimal marketValue;
    private BigDecimal profitLoss;
    private String currency;
    private LocalDateTime asOf;

    // ── Enriched market fields (populated when live price is available) ─────────
    /** Instrument display name (e.g. "Türk Hava Yolları", "Bitcoin"). */
    private String name;
    /** Daily price change amount (current - previousClose). */
    private BigDecimal change;
    /** Daily price change percentage. */
    private BigDecimal changePercent;
    /** Daily trading volume. */
    private Long volume;
    /** Intraday high. */
    private BigDecimal dayHigh;
    /** Intraday low. */
    private BigDecimal dayLow;
    /** 52-week high (STOCK / FUTURE only). */
    private BigDecimal fiftyTwoWeekHigh;
    /** 52-week low (STOCK / FUTURE only). */
    private BigDecimal fiftyTwoWeekLow;
    /** Market capitalisation (CRYPTO only for now). */
    private BigDecimal marketCap;

    // ── Fund-specific return fields (FUND type — for trend computation) ─────────
    /** 1-day return % from Rasyonet (FUND). Used by computeTrend() fund-momentum branch. */
    private BigDecimal returnOneDay;
    /** 1-month return % from Rasyonet (FUND). */
    private BigDecimal returnOneMonth;
    /** 3-month return % from Rasyonet (FUND). */
    private BigDecimal returnThreeMonths;

    // ── Crypto-specific periodic change fields (CRYPTO — for trend computation) ─
    /** 7-day price change percentage (CRYPTO). Used by computeTrend() crypto-7d branch. */
    private BigDecimal priceChangePercentage7d;

    // ── Technical moving averages (STOCK / FUTURE / COMMODITY — computed from chart data) ──
    /** 20-period simple moving average of daily close prices. */
    private BigDecimal ma20;
    /** 50-period simple moving average of daily close prices. */
    private BigDecimal ma50;

    // ── Transaction date metadata ─────────────────────────────────────────────
    /** Date of the first BUY transaction for this holding. */
    private LocalDateTime firstBuyDate;
    /** Date of the most recent transaction (BUY or SELL) for this holding. */
    private LocalDateTime lastTransactionDate;

    /** Cumulative realized P/L from SELLs (null if no SELL for this symbol while position open). */
    private BigDecimal realizedGainLoss;
    /**
     * Aggregate realized P/L as % of total cost basis of sold shares:
     * (sum of realized $) / (sum of sold cost basis) × 100. Null if no SELL or zero basis.
     */
    private BigDecimal realizedGainLossPercent;
}
