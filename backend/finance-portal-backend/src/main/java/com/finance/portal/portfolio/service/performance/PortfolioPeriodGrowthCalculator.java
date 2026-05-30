package com.finance.portal.portfolio.service.performance;

import com.finance.portal.common.domain.AssetType;
import com.finance.portal.portfolio.application.performance.PortfolioPerformancePoint;
import com.finance.portal.portfolio.domain.PortfolioTransaction;
import com.finance.portal.portfolio.domain.TransactionType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Seçilen aralıkta TWR-benzeri kümülatif dönem getirisi ({@code periodGrowthPercent}).
 * Nakit akışları (BUY/SELL) günlük getiriden arındırılır.
 */
public final class PortfolioPeriodGrowthCalculator {

    private static final int MONEY_SCALE = 4;
    private static final int RETURN_SCALE = 12;
    private static final int PCT_SCALE = 2;

    /** BOND için TCMB konvansiyonu: fiyat 100 TL nominal üzerinden — etkin = price/100. */
    private static final BigDecimal BOND_PAR_SCALE = new BigDecimal("100");

    private static final Predicate<String> NO_GOLD = s -> false;

    private PortfolioPeriodGrowthCalculator() {
    }

    public static void applyPeriodGrowth(
            List<PortfolioPerformancePoint> points,
            List<PortfolioTransaction> transactions,
            Set<String> excludedPositionKeys) {
        applyPeriodGrowth(points, transactions, excludedPositionKeys, NO_GOLD);
    }

    public static void applyPeriodGrowth(
            List<PortfolioPerformancePoint> points,
            List<PortfolioTransaction> transactions,
            Set<String> excludedPositionKeys,
            Predicate<String> isGoldBondSymbol) {

        if (points == null || points.isEmpty()) {
            return;
        }

        Predicate<String> goldCheck = isGoldBondSymbol != null ? isGoldBondSymbol : NO_GOLD;
        Map<LocalDate, BigDecimal> cashFlowByDay = buildNetCashFlowByDay(transactions, excludedPositionKeys, goldCheck);

        BigDecimal cumulativeFactor = BigDecimal.ONE;
        BigDecimal previousMv = null;

        for (int i = 0; i < points.size(); i++) {
            PortfolioPerformancePoint point = points.get(i);
            BigDecimal currentMv = normalizeMarketValue(point.getMarketValue());
            LocalDate day = point.getDate();

            if (i == 0 || previousMv == null || previousMv.compareTo(BigDecimal.ZERO) <= 0) {
                point.setPeriodGrowthPercent(BigDecimal.ZERO.setScale(PCT_SCALE, RoundingMode.HALF_UP));
                if (currentMv != null && currentMv.compareTo(BigDecimal.ZERO) > 0) {
                    previousMv = currentMv;
                }
                continue;
            }

            BigDecimal netCashFlow = cashFlowByDay.getOrDefault(day, BigDecimal.ZERO)
                    .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

            BigDecimal dailyReturn = computeDailyReturn(previousMv, currentMv, netCashFlow);
            cumulativeFactor = cumulativeFactor
                    .multiply(BigDecimal.ONE.add(dailyReturn))
                    .setScale(RETURN_SCALE, RoundingMode.HALF_UP);

            BigDecimal cumulativeGrowth = cumulativeFactor.subtract(BigDecimal.ONE);
            BigDecimal periodPct = cumulativeGrowth
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(PCT_SCALE, RoundingMode.HALF_UP);
            point.setPeriodGrowthPercent(periodPct);

            if (currentMv != null && currentMv.compareTo(BigDecimal.ZERO) > 0) {
                previousMv = currentMv;
            }
        }
    }

    /**
     * Günlük getiri (Modified Dietz):
     *   r = (MV_end − MV_start − netFlow) / (MV_start + netFlow_weighted)
     * Nakit akışları gün başında varsayılır (weight = 1) → büyük BUY günleri "küçük previousMV"e
     * bölünüp astronomik %'ye dönüşmez. Net flow=0 günlerde davranış aynı kalır.
     */
    static BigDecimal computeDailyReturn(
            BigDecimal previousMarketValue,
            BigDecimal currentMarketValue,
            BigDecimal netCashFlow) {

        if (previousMarketValue == null
                || previousMarketValue.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal current = currentMarketValue != null
                ? currentMarketValue
                : BigDecimal.ZERO;
        BigDecimal flows = netCashFlow != null ? netCashFlow : BigDecimal.ZERO;

        BigDecimal numerator = current.subtract(previousMarketValue).subtract(flows);
        BigDecimal denominator = previousMarketValue.add(flows);
        if (denominator.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return numerator.divide(denominator, RETURN_SCALE, RoundingMode.HALF_UP);
    }

    static BigDecimal netCashFlowForTransaction(PortfolioTransaction tx) {
        return netCashFlowForTransaction(tx, NO_GOLD);
    }

    static BigDecimal netCashFlowForTransaction(PortfolioTransaction tx, Predicate<String> isGoldBondSymbol) {
        if (tx == null || tx.getQuantity() == null || tx.getPrice() == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal qty = tx.getQuantity();
        BigDecimal price = tx.getPrice();
        BigDecimal commission = tx.getCommission() != null ? tx.getCommission() : BigDecimal.ZERO;

        // BOND: TCMB konvansiyonu — fiyat 100 TL nominal üzerinden, etkin = price/100.
        // Altın bond istisnası: birim adet/gram → /100 uygulanmaz.
        boolean isBond = tx.getAssetType() == AssetType.BOND;
        boolean isGoldBond = isBond
                && isGoldBondSymbol != null
                && tx.getSymbol() != null
                && isGoldBondSymbol.test(tx.getSymbol());
        BigDecimal effectivePrice = (isBond && !isGoldBond)
                ? price.divide(BOND_PAR_SCALE, 12, RoundingMode.HALF_UP)
                : price;
        BigDecimal gross = qty.multiply(effectivePrice).setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        if (tx.getTransactionType() == TransactionType.BUY) {
            return gross.add(commission);
        }
        if (tx.getTransactionType() == TransactionType.SELL) {
            return gross.subtract(commission).negate();
        }
        return BigDecimal.ZERO;
    }

    private static Map<LocalDate, BigDecimal> buildNetCashFlowByDay(
            List<PortfolioTransaction> transactions,
            Set<String> excludedPositionKeys,
            Predicate<String> isGoldBondSymbol) {

        Map<LocalDate, BigDecimal> byDay = new HashMap<>();
        if (transactions == null) {
            return byDay;
        }

        for (PortfolioTransaction tx : transactions) {
            if (tx.getTransactionDate() == null) {
                continue;
            }
            String key = PortfolioPerformanceCalculator.positionKey(tx);
            if (excludedPositionKeys != null && excludedPositionKeys.contains(key)) {
                continue;
            }
            LocalDate day = tx.getTransactionDate().toLocalDate();
            BigDecimal flow = netCashFlowForTransaction(tx, isGoldBondSymbol);
            byDay.merge(day, flow, (a, b) -> a.add(b).setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        }
        return byDay;
    }

    private static BigDecimal normalizeMarketValue(BigDecimal marketValue) {
        if (marketValue == null || marketValue.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }
        return marketValue;
    }
}
