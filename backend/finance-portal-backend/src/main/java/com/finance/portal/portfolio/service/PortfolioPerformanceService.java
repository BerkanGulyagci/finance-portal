package com.finance.portal.portfolio.service;

import com.finance.portal.portfolio.application.performance.ExcludedPerformanceAsset;
import com.finance.portal.portfolio.application.performance.PortfolioPerformanceMetric;
import com.finance.portal.portfolio.application.performance.PortfolioPerformancePoint;
import com.finance.portal.portfolio.application.performance.PortfolioPerformanceRange;
import com.finance.portal.portfolio.application.performance.PortfolioPerformanceResult;
import com.finance.portal.portfolio.application.port.PortfolioHistoricalPricePort;
import com.finance.portal.portfolio.application.port.PortfolioPersistencePort;
import com.finance.portal.portfolio.infrastructure.market.PortfolioHistoricalPriceAdapter;
import com.finance.portal.portfolio.domain.Portfolio;
import com.finance.portal.portfolio.domain.PortfolioTransaction;
import com.finance.portal.portfolio.domain.PortfolioType;
import com.finance.portal.portfolio.presentation.dto.PortfolioHoldingResponse;
import com.finance.portal.portfolio.service.performance.PortfolioPerformanceCalculator;
import com.finance.portal.portfolio.service.performance.PortfolioPeriodGrowthCalculator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.UUID;

@Service
public class PortfolioPerformanceService {

    private static final int MONEY_SCALE = 4;
    private static final int PCT_SCALE = 2;

    private final PortfolioPersistencePort portfolioPersistence;
    private final PortfolioHistoricalPricePort historicalPricePort;
    private final PortfolioPerformanceCalculator calculator;
    private final PortfolioHoldingsBuilder holdingsBuilder;

    public PortfolioPerformanceService(PortfolioPersistencePort portfolioPersistence,
                                       PortfolioHistoricalPricePort historicalPricePort,
                                       PortfolioPerformanceCalculator calculator,
                                       PortfolioHoldingsBuilder holdingsBuilder) {
        this.portfolioPersistence = portfolioPersistence;
        this.historicalPricePort = historicalPricePort;
        this.calculator = calculator;
        this.holdingsBuilder = holdingsBuilder;
    }

    @Transactional(readOnly = true)
    public PortfolioPerformanceResult getPerformance(
            String userId,
            UUID portfolioId,
            String rangeParam,
            String metricParam) {

        Portfolio portfolio = portfolioPersistence.findByIdAndUserId(portfolioId, userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Portfolio not found: id=" + portfolioId + " userId=" + userId));

        if (portfolio.getPortfolioType() == PortfolioType.WATCHLIST) {
            throw new IllegalArgumentException("Performance is only available for holdings portfolios.");
        }

        PortfolioPerformanceRange range = PortfolioPerformanceRange.parse(rangeParam);
        PortfolioPerformanceMetric metric = PortfolioPerformanceMetric.parse(metricParam);

        LocalDate today = LocalDate.now(ZoneId.of("Europe/Istanbul"));
        LocalDate startDate = range.effectiveStart(today);
        LocalDate endDate = today;

        List<PortfolioTransaction> transactions = portfolio.getTransactions() != null
                ? portfolio.getTransactions()
                : List.of();

        LocalDate priceFetchFrom = earliestRelevantDate(transactions, startDate, today);

        Set<String> positionKeys = collectPositionKeys(transactions);
        List<ExcludedPerformanceAsset> excluded = new ArrayList<>();
        Set<String> excludedKeys = new HashSet<>();
        Map<String, NavigableMap<LocalDate, BigDecimal>> priceSeries = new LinkedHashMap<>();

        for (String key : positionKeys) {
            PortfolioTransaction sample = findSampleTransaction(transactions, key);
            if (sample == null) {
                continue;
            }
            var seriesOpt = historicalPricePort.fetchDailyClosePrices(
                    sample.getAssetType(),
                    sample.getSymbol(),
                    priceFetchFrom,
                    endDate);
            if (seriesOpt.isEmpty()) {
                excludedKeys.add(key);
                excluded.add(new ExcludedPerformanceAsset(
                        PortfolioPerformanceCalculator.displaySymbol(sample),
                        sample.getAssetType(),
                        PortfolioHistoricalPriceAdapter.exclusionReason(
                                sample.getAssetType(), sample.getSymbol())));
            } else {
                priceSeries.put(key, seriesOpt.get());
            }
        }

        List<PortfolioPerformancePoint> points = calculator.calculate(
                transactions,
                startDate,
                endDate,
                priceSeries,
                excludedKeys,
                excluded);

        alignLastPointWithLiveHoldings(points, endDate, transactions);
        PortfolioPeriodGrowthCalculator.applyPeriodGrowth(points, transactions, excludedKeys);

        PortfolioPerformanceResult result = new PortfolioPerformanceResult();
        result.setPortfolioId(portfolioId);
        result.setRange(range.getApiLabel());
        result.setMetric(metric.name());
        result.setCurrency("TRY");
        result.setStartDate(startDate);
        result.setEndDate(endDate);
        result.setPoints(points);
        result.setExcludedAssets(excluded);
        return result;
    }

    /**
     * Bugünkü son nokta: özet kartı ile aynı canlı piyasa değeri (tüm pozisyonlar).
     * Geçmiş günler yalnızca tarihsel serisi olan varlıklarla hesaplanmaya devam eder.
     */
    private void alignLastPointWithLiveHoldings(
            List<PortfolioPerformancePoint> points,
            LocalDate endDate,
            List<PortfolioTransaction> transactions) {
        if (points == null || points.isEmpty()) {
            return;
        }
        PortfolioPerformancePoint last = points.get(points.size() - 1);
        if (last.getDate() == null || !last.getDate().equals(endDate)) {
            return;
        }

        List<PortfolioHoldingResponse> holdings = holdingsBuilder.build(transactions);
        BigDecimal liveMv = holdings.stream()
                .map(PortfolioHoldingResponse::getMarketValue)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        if (liveMv.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        BigDecimal liveCost = holdings.stream()
                .map(h -> h.getTotalCost() != null ? h.getTotalCost() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        BigDecimal profitLoss = liveMv.subtract(liveCost).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal profitLossPercent = BigDecimal.ZERO.setScale(PCT_SCALE, RoundingMode.HALF_UP);
        if (liveCost.compareTo(BigDecimal.ZERO) > 0) {
            profitLossPercent = profitLoss
                    .divide(liveCost, 10, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(PCT_SCALE, RoundingMode.HALF_UP);
        }

        last.setMarketValue(liveMv);
        last.setTotalCost(liveCost);
        last.setProfitLoss(profitLoss);
        last.setProfitLossPercent(profitLossPercent);
    }

    private static LocalDate earliestRelevantDate(
            List<PortfolioTransaction> transactions,
            LocalDate startDate,
            LocalDate today) {
        LocalDate cap = today.minusYears(1);
        LocalDate earliest = startDate;
        for (PortfolioTransaction tx : transactions) {
            if (tx.getTransactionDate() != null) {
                LocalDate d = tx.getTransactionDate().toLocalDate();
                if (d.isBefore(earliest)) {
                    earliest = d;
                }
            }
        }
        return earliest.isBefore(cap) ? cap : earliest;
    }

    private static Set<String> collectPositionKeys(List<PortfolioTransaction> transactions) {
        Set<String> keys = new LinkedHashSet<>();
        for (PortfolioTransaction tx : transactions) {
            keys.add(PortfolioPerformanceCalculator.positionKey(tx));
        }
        return keys;
    }

    private static PortfolioTransaction findSampleTransaction(List<PortfolioTransaction> transactions, String key) {
        for (PortfolioTransaction tx : transactions) {
            if (PortfolioPerformanceCalculator.positionKey(tx).equals(key)) {
                return tx;
            }
        }
        return null;
    }
}
