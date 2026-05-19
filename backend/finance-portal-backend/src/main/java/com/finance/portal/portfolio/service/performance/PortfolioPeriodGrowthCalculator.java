package com.finance.portal.portfolio.service.performance;

import com.finance.portal.portfolio.application.performance.PortfolioPerformancePoint;
import com.finance.portal.portfolio.domain.PortfolioTransaction;
import com.finance.portal.portfolio.domain.TransactionType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Seçilen aralıkta TWR-benzeri kümülatif dönem getirisi ({@code periodGrowthPercent}).
 * Nakit akışları (BUY/SELL) günlük getiriden arındırılır.
 */
public final class PortfolioPeriodGrowthCalculator {

    private static final int MONEY_SCALE = 4;
    private static final int RETURN_SCALE = 12;
    private static final int PCT_SCALE = 2;

    private PortfolioPeriodGrowthCalculator() {
    }

    public static void applyPeriodGrowth(
            List<PortfolioPerformancePoint> points,
            List<PortfolioTransaction> transactions,
            Set<String> excludedPositionKeys) {

        if (points == null || points.isEmpty()) {
            return;
        }

        Map<LocalDate, BigDecimal> cashFlowByDay = buildNetCashFlowByDay(transactions, excludedPositionKeys);

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
        return numerator.divide(previousMarketValue, RETURN_SCALE, RoundingMode.HALF_UP);
    }

    static BigDecimal netCashFlowForTransaction(PortfolioTransaction tx) {
        if (tx == null || tx.getQuantity() == null || tx.getPrice() == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal qty = tx.getQuantity();
        BigDecimal price = tx.getPrice();
        BigDecimal commission = tx.getCommission() != null ? tx.getCommission() : BigDecimal.ZERO;
        BigDecimal gross = qty.multiply(price).setScale(MONEY_SCALE, RoundingMode.HALF_UP);

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
            Set<String> excludedPositionKeys) {

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
            BigDecimal flow = netCashFlowForTransaction(tx);
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
