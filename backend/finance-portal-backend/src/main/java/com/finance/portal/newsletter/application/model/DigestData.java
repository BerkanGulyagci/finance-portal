package com.finance.portal.newsletter.application.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Bülten e-postasında gösterilecek "dashboard görünümü" verisi — portföy toplamları,
 * varlık türü dağılımı, portföy listesi, en çok kazandıran/kaybettiren, son işlemler
 * ve genel piyasa anlık görünümü.
 */
public record DigestData(
        int portfolioCount,
        BigDecimal totalValue,
        BigDecimal totalProfitLoss,
        BigDecimal totalProfitLossPercent,
        BigDecimal dailyChangePercent,
        List<Slice> allocation,
        List<PortfolioLine> portfolios,
        List<Mover> gainers,
        List<Mover> losers,
        List<TxLine> recentTransactions,
        List<Fav> favorites,
        Market market
) {
    public record Slice(String label, BigDecimal percent) {}

    public record PortfolioLine(String name, BigDecimal value,
                                BigDecimal profitLoss, BigDecimal profitLossPercent) {}

    public record Mover(String symbol, String name, BigDecimal changePercent) {}

    public record Fav(String symbol, String typeLabel, BigDecimal lastPrice, BigDecimal changePercent) {}

    public record TxLine(String symbol, String type, BigDecimal total,
                         LocalDateTime date, String portfolioName) {}

    public record Market(
            BigDecimal usdTry,
            BigDecimal bist100,
            BigDecimal gramAltin,
            BigDecimal inflationYoy,
            BigDecimal policyRate
    ) {}
}
