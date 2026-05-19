package com.finance.portal.portfolio.service.performance;

import com.finance.portal.common.domain.AssetType;
import com.finance.portal.market.application.viop.ViopService;
import com.finance.portal.portfolio.application.performance.ExcludedPerformanceAsset;
import com.finance.portal.portfolio.application.performance.PortfolioPerformancePoint;
import com.finance.portal.portfolio.domain.PortfolioTransaction;
import com.finance.portal.portfolio.domain.TransactionType;
import com.finance.portal.portfolio.infrastructure.market.PortfolioHistoricalPriceAdapter;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;

/**
 * İşlem replay + günlük portföy değeri hesabı ({@link com.finance.portal.portfolio.service.PortfolioHoldingsBuilder} ile uyumlu maliyet mantığı).
 */
@Component
public class PortfolioPerformanceCalculator {

    private static final int MONEY_SCALE = 4;
    private static final int PCT_SCALE = 2;

    public List<PortfolioPerformancePoint> calculate(
            List<PortfolioTransaction> transactions,
            LocalDate startDate,
            LocalDate endDate,
            Map<String, NavigableMap<LocalDate, BigDecimal>> priceSeriesByPositionKey,
            Set<String> excludedPositionKeys,
            List<ExcludedPerformanceAsset> excludedAssetsOut) {

        List<PortfolioTransaction> sorted = new ArrayList<>(transactions != null ? transactions : List.of());
        sorted.sort(Comparator
                .comparing(PortfolioTransaction::getTransactionDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(PortfolioTransaction::getId, Comparator.nullsLast(Comparator.naturalOrder())));

        Map<String, HoldingAccumulator> openPositions = new LinkedHashMap<>();
        List<PortfolioPerformancePoint> points = new ArrayList<>();

        int txIndex = 0;
        for (LocalDate day = startDate; !day.isAfter(endDate); day = day.plusDays(1)) {
            while (txIndex < sorted.size()) {
                PortfolioTransaction tx = sorted.get(txIndex);
                LocalDateTime txDt = tx.getTransactionDate();
                if (txDt == null || txDt.toLocalDate().isAfter(day)) {
                    break;
                }
                String key = positionKey(tx);
                if (!excludedPositionKeys.contains(key)) {
                    openPositions.computeIfAbsent(key, k -> new HoldingAccumulator(
                            key, displaySymbol(tx), tx.getAssetType())).apply(tx);
                    HoldingAccumulator acc = openPositions.get(key);
                    if (acc.openQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                        openPositions.remove(key);
                    }
                }
                txIndex++;
            }

            BigDecimal totalMarketValue = BigDecimal.ZERO;
            BigDecimal totalCost = BigDecimal.ZERO;
            List<String> keysToRemoveAfterDay = new ArrayList<>();

            for (Map.Entry<String, HoldingAccumulator> e : openPositions.entrySet()) {
                String key = e.getKey();
                if (excludedPositionKeys.contains(key)) {
                    continue;
                }
                HoldingAccumulator acc = e.getValue();
                if (acc.openQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                NavigableMap<LocalDate, BigDecimal> series = priceSeriesByPositionKey.get(key);
                BigDecimal unitPrice = priceOnDay(series, day);
                if (unitPrice == null) {
                    if (excludedAssetsOut != null && markExcluded(excludedAssetsOut, excludedPositionKeys, acc)) {
                        keysToRemoveAfterDay.add(key);
                    }
                    continue;
                }
                BigDecimal mv = unitPrice.multiply(acc.openQuantity).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
                totalMarketValue = totalMarketValue.add(mv);
                totalCost = totalCost.add(acc.openCostBasis.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
            }

            for (String key : keysToRemoveAfterDay) {
                openPositions.remove(key);
            }

            BigDecimal profitLoss = totalMarketValue.subtract(totalCost).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            BigDecimal profitLossPercent = BigDecimal.ZERO.setScale(PCT_SCALE, RoundingMode.HALF_UP);
            if (totalCost.compareTo(BigDecimal.ZERO) > 0) {
                profitLossPercent = profitLoss
                        .divide(totalCost, 10, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(PCT_SCALE, RoundingMode.HALF_UP);
            }

            points.add(new PortfolioPerformancePoint(day, totalMarketValue, totalCost, profitLoss, profitLossPercent));
        }

        return points;
    }

    public static String positionKey(PortfolioTransaction tx) {
        String symPart = tx.getAssetType() == AssetType.FUTURE
                ? ViopService.portfolioFutureGroupKey(tx.getSymbol())
                : (tx.getSymbol() != null ? tx.getSymbol() : "");
        return symPart + "::" + tx.getAssetType().name();
    }

    public static String displaySymbol(PortfolioTransaction tx) {
        if (tx.getAssetType() == AssetType.FUTURE) {
            String n = ViopService.normalizeStoredFutureSymbol(tx.getSymbol());
            return n != null ? n : (tx.getSymbol() != null ? tx.getSymbol() : "");
        }
        return tx.getSymbol();
    }

    static BigDecimal priceOnDay(NavigableMap<LocalDate, BigDecimal> series, LocalDate day) {
        if (series == null || series.isEmpty()) {
            return null;
        }
        Map.Entry<LocalDate, BigDecimal> entry = series.floorEntry(day);
        if (entry == null || entry.getValue() == null) {
            return null;
        }
        BigDecimal price = entry.getValue();
        if (price.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return price;
    }

    private static boolean markExcluded(List<ExcludedPerformanceAsset> out,
                                      Set<String> excludedKeys,
                                      HoldingAccumulator acc) {
        if (excludedKeys.add(acc.positionKey)) {
            out.add(new ExcludedPerformanceAsset(
                    acc.symbol,
                    acc.assetType,
                    PortfolioHistoricalPriceAdapter.exclusionReason(acc.assetType, acc.symbol)));
            return true;
        }
        return false;
    }

    static class HoldingAccumulator {

        final String positionKey;
        final String symbol;
        final AssetType assetType;

        BigDecimal openQuantity = BigDecimal.ZERO;
        BigDecimal openCostBasis = BigDecimal.ZERO;

        HoldingAccumulator(String positionKey, String symbol, AssetType assetType) {
            this.positionKey = positionKey;
            this.symbol = symbol;
            this.assetType = assetType;
        }

        void apply(PortfolioTransaction tx) {
            BigDecimal qty = tx.getQuantity();
            BigDecimal price = tx.getPrice();
            BigDecimal commission = tx.getCommission() != null ? tx.getCommission() : BigDecimal.ZERO;

            if (tx.getTransactionType() == TransactionType.BUY) {
                BigDecimal cost = qty.multiply(price).add(commission);
                openCostBasis = openCostBasis.add(cost);
                openQuantity = openQuantity.add(qty);
            } else {
                if (openQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                    return;
                }
                BigDecimal soldCostBasis;
                if (qty.compareTo(openQuantity) >= 0) {
                    soldCostBasis = openCostBasis;
                } else {
                    BigDecimal unitCost = openCostBasis.divide(openQuantity, 12, RoundingMode.HALF_UP);
                    soldCostBasis = qty.multiply(unitCost);
                }
                openCostBasis = openCostBasis.subtract(soldCostBasis);
                openQuantity = openQuantity.subtract(qty);
            }
        }
    }
}
