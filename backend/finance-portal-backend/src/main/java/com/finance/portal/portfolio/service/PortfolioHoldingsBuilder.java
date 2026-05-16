package com.finance.portal.portfolio.service;

import com.finance.portal.common.domain.AssetType;
import com.finance.portal.market.application.viop.ViopService;
import com.finance.portal.portfolio.domain.PortfolioTransaction;
import com.finance.portal.portfolio.domain.TransactionType;
import com.finance.portal.portfolio.application.port.HoldingMarketEnrichmentPort;
import com.finance.portal.portfolio.presentation.dto.PortfolioHoldingResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * İşlem listesinden açık pozisyonları (holdings) hesaplar ve canlı fiyat zenginleştirmesi uygular.
 */
@Component
public class PortfolioHoldingsBuilder {

    private static final int PRICE_SCALE = 8;
    private static final int MONEY_SCALE = 4;
    private static final int FUND_MONEY_SCALE = 8;

    private final HoldingMarketEnrichmentPort holdingMarketEnrichment;

    public PortfolioHoldingsBuilder(HoldingMarketEnrichmentPort holdingMarketEnrichment) {
        this.holdingMarketEnrichment = holdingMarketEnrichment;
    }

    public List<PortfolioHoldingResponse> build(List<PortfolioTransaction> transactions) {
        if (transactions == null || transactions.isEmpty()) {
            return new ArrayList<>();
        }

        Map<String, List<PortfolioTransaction>> byKey = new LinkedHashMap<>();
        for (PortfolioTransaction tx : transactions) {
            String keyPart = groupingKeyForTransaction(tx);
            String key = keyPart + "::" + tx.getAssetType().name();
            byKey.computeIfAbsent(key, k -> new ArrayList<>()).add(tx);
        }

        List<PortfolioHoldingResponse> result = new ArrayList<>();

        for (Map.Entry<String, List<PortfolioTransaction>> e : byKey.entrySet()) {
            List<PortfolioTransaction> txs = new ArrayList<>(e.getValue());
            txs.sort(Comparator
                    .comparing(PortfolioTransaction::getTransactionDate)
                    .thenComparing(PortfolioTransaction::getId));

            PortfolioTransaction sample = txs.get(0);
            AssetType assetType = sample.getAssetType();
            String accSymbol = assetType == AssetType.FUTURE
                    ? canonicalFutureDisplaySymbol(sample.getSymbol())
                    : sample.getSymbol();
            HoldingAccumulator acc = new HoldingAccumulator(accSymbol, assetType);
            for (PortfolioTransaction tx : txs) {
                acc.apply(tx);
            }

            if (acc.openQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            int moneyScale = assetType == AssetType.FUND ? FUND_MONEY_SCALE : MONEY_SCALE;
            int realizedPctScale = assetType == AssetType.FUND ? 8 : 2;

            PortfolioHoldingResponse holding = new PortfolioHoldingResponse();
            holding.setSymbol(acc.symbol);
            holding.setAssetType(acc.assetType);
            holding.setTotalQuantity(acc.openQuantity.setScale(PRICE_SCALE, RoundingMode.HALF_UP));
            holding.setAverageCost(acc.averageOpenCost().setScale(moneyScale, RoundingMode.HALF_UP));
            holding.setTotalCost(acc.openCostBasis.setScale(moneyScale, RoundingMode.HALF_UP));
            holding.setFirstBuyDate(acc.firstBuyDate);
            holding.setLastTransactionDate(txs.get(txs.size() - 1).getTransactionDate());

            if (acc.anySell) {
                holding.setRealizedGainLoss(acc.realizedGainLossSum.setScale(moneyScale, RoundingMode.HALF_UP));
                if (acc.totalSoldCostBasis.compareTo(BigDecimal.ZERO) > 0) {
                    holding.setRealizedGainLossPercent(
                            acc.realizedGainLossSum
                                    .divide(acc.totalSoldCostBasis, 10, RoundingMode.HALF_UP)
                                    .multiply(BigDecimal.valueOf(100))
                                    .setScale(realizedPctScale, RoundingMode.HALF_UP));
                }
            }

            holdingMarketEnrichment.enrich(holding);
            result.add(holding);
        }

        return result;
    }

    private static String groupingKeyForTransaction(PortfolioTransaction tx) {
        if (tx.getAssetType() == AssetType.FUTURE) {
            return ViopService.portfolioFutureGroupKey(tx.getSymbol());
        }
        return tx.getSymbol() != null ? tx.getSymbol() : "";
    }

    private static String canonicalFutureDisplaySymbol(String raw) {
        String n = ViopService.normalizeStoredFutureSymbol(raw);
        return n != null ? n : (raw != null ? raw : "");
    }

    private static class HoldingAccumulator {

        final String symbol;
        final AssetType assetType;

        BigDecimal openQuantity = BigDecimal.ZERO;
        BigDecimal openCostBasis = BigDecimal.ZERO;

        BigDecimal realizedGainLossSum = BigDecimal.ZERO;
        BigDecimal totalSoldCostBasis = BigDecimal.ZERO;
        boolean anySell = false;

        LocalDateTime firstBuyDate = null;

        HoldingAccumulator(String symbol, AssetType assetType) {
            this.symbol = symbol;
            this.assetType = assetType;
        }

        void apply(PortfolioTransaction tx) {
            BigDecimal qty = tx.getQuantity();
            BigDecimal price = tx.getPrice();
            BigDecimal commission = tx.getCommission() != null ? tx.getCommission() : BigDecimal.ZERO;
            LocalDateTime txDate = tx.getTransactionDate();

            if (tx.getTransactionType() == TransactionType.BUY) {
                BigDecimal cost = qty.multiply(price).add(commission);
                openCostBasis = openCostBasis.add(cost);
                openQuantity = openQuantity.add(qty);

                if (txDate != null && (firstBuyDate == null || txDate.isBefore(firstBuyDate))) {
                    firstBuyDate = txDate;
                }
            } else {
                if (openQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalStateException(
                            "SELL with non-positive open quantity for " + symbol + " " + assetType);
                }

                BigDecimal soldCostBasis;
                if (qty.compareTo(openQuantity) >= 0) {
                    soldCostBasis = openCostBasis;
                } else {
                    BigDecimal unitCost = openCostBasis.divide(openQuantity, 12, RoundingMode.HALF_UP);
                    soldCostBasis = qty.multiply(unitCost);
                }

                BigDecimal proceeds = qty.multiply(price).subtract(commission);
                BigDecimal pnlSlice = proceeds.subtract(soldCostBasis);

                realizedGainLossSum = realizedGainLossSum.add(pnlSlice);
                totalSoldCostBasis = totalSoldCostBasis.add(soldCostBasis);
                anySell = true;

                openCostBasis = openCostBasis.subtract(soldCostBasis);
                openQuantity = openQuantity.subtract(qty);
            }
        }

        BigDecimal averageOpenCost() {
            if (openQuantity.compareTo(BigDecimal.ZERO) == 0) {
                return BigDecimal.ZERO;
            }
            return openCostBasis.divide(openQuantity, 8, RoundingMode.HALF_UP);
        }
    }
}
