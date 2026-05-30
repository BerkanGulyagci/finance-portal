package com.finance.portal.portfolio.service;

import com.finance.portal.portfolio.application.performance.ExcludedPerformanceAsset;
import com.finance.portal.portfolio.application.performance.PortfolioPerformanceMetric;
import com.finance.portal.portfolio.application.performance.PortfolioPerformancePoint;
import com.finance.portal.portfolio.application.performance.PortfolioPerformanceRange;
import com.finance.portal.portfolio.application.performance.PortfolioPerformanceResult;
import com.finance.portal.market.application.economy.InflationDeflatorService;
import com.finance.portal.market.application.economy.model.EconomySeriesPoint;
import com.finance.portal.portfolio.application.port.PortfolioHistoricalPricePort;
import com.finance.portal.portfolio.application.port.PortfolioPersistencePort;
import com.finance.portal.portfolio.infrastructure.market.PortfolioHistoricalPriceAdapter;
import com.finance.portal.portfolio.domain.Portfolio;
import com.finance.portal.portfolio.domain.PortfolioTransaction;
import com.finance.portal.portfolio.domain.PortfolioType;
import com.finance.portal.portfolio.presentation.dto.PortfolioHoldingResponse;
import com.finance.portal.portfolio.service.performance.PortfolioPerformanceCalculator;
import com.finance.portal.portfolio.service.performance.PortfolioPeriodGrowthCalculator;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
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
    private final PortfolioCurrencyConverter currencyConverter;
    private final InflationDeflatorService deflator;

    public PortfolioPerformanceService(PortfolioPersistencePort portfolioPersistence,
                                       PortfolioHistoricalPricePort historicalPricePort,
                                       PortfolioPerformanceCalculator calculator,
                                       PortfolioHoldingsBuilder holdingsBuilder,
                                       PortfolioCurrencyConverter currencyConverter,
                                       InflationDeflatorService deflator) {
        this.portfolioPersistence = portfolioPersistence;
        this.historicalPricePort = historicalPricePort;
        this.calculator = calculator;
        this.holdingsBuilder = holdingsBuilder;
        this.currencyConverter = currencyConverter;
        this.deflator = deflator;
    }

    @Transactional(readOnly = true)
    @WithSpan("PortfolioPerformance.getPerformance")
    public PortfolioPerformanceResult getPerformance(
            @SpanAttribute("user.id") String userId,
            @SpanAttribute("portfolio.id") UUID portfolioId,
            @SpanAttribute("performance.range") String rangeParam,
            @SpanAttribute("performance.metric") String metricParam) {

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

        // Baştaki "değer = 0" günleri (henüz değerlenecek varlık/veri yok) çizilmesin — yoksa düz bir
        // 0 çizgisi + bugüne dik sıçrama oluşuyor. Hiç değerli gün yoksa (ör. tüm varlıklar VİOP olup
        // tarihsel verisi gelmiyorsa) seri boş kalır; frontend "veri yok" + hariç-tutulan açıklamasını gösterir.
        points = trimLeadingZeroValuePoints(points);

        alignLastPointWithLiveHoldings(points, endDate, transactions);
        PortfolioPeriodGrowthCalculator.applyPeriodGrowth(points, transactions, excludedKeys, calculator::isGoldBondSymbol);
        applyInflationBaseline(points);

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
                .map(h -> currencyConverter.toTry(h.getMarketValue(), h.getCurrency()))
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        if (liveMv.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        BigDecimal liveCost = holdings.stream()
                .map(h -> currencyConverter.toTry(h.getTotalCost(), h.getCurrency()))
                .filter(v -> v != null)
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

    /**
     * Enflasyon (TÜFE) referans çizgisini her noktaya yazar: ilk noktanın piyasa değeri, ilk gün
     * TÜFE'sine göre o günkü TÜFE'yle ölçeklenir → {@code infValue(t) = baseMv × TÜFE(t)/TÜFE(t0)}.
     * "Param sadece enflasyon kadar değerlenseydi" benchmark'ı. TÜFE verisi yoksa alan null kalır.
     */
    private void applyInflationBaseline(List<PortfolioPerformancePoint> points) {
        if (points == null || points.isEmpty()) {
            return;
        }
        final List<EconomySeriesPoint> tufe;
        try {
            tufe = deflator.tufeSeries();
        } catch (Exception e) {
            return;
        }
        if (tufe == null || tufe.isEmpty()) {
            return;
        }
        PortfolioPerformancePoint first = points.get(0);
        if (first.getDate() == null || first.getMarketValue() == null
                || first.getMarketValue().signum() <= 0) {
            return;
        }
        var baseIdxOpt = deflator.indexValueAt(tufe, first.getDate());
        if (baseIdxOpt.isEmpty()) {
            return;
        }
        BigDecimal baseMv = first.getMarketValue();
        BigDecimal baseIdx = baseIdxOpt.get();
        for (PortfolioPerformancePoint p : points) {
            if (p.getDate() == null) {
                continue;
            }
            deflator.indexValueAt(tufe, p.getDate()).ifPresent(idx -> {
                BigDecimal v = baseMv.multiply(idx)
                        .divide(baseIdx, MONEY_SCALE, RoundingMode.HALF_UP);
                p.setInflationValue(v);
            });
        }
    }

    /**
     * Serinin başındaki sıfır/negatif değerli (henüz değerlenecek varlık ya da tarihsel veri olmayan)
     * günleri atar; ilk gerçek değerli günden başlatır. Hiç değerli gün yoksa boş liste döner.
     */
    private static List<PortfolioPerformancePoint> trimLeadingZeroValuePoints(
            List<PortfolioPerformancePoint> points) {
        if (points == null || points.isEmpty()) {
            return points;
        }
        int firstValued = -1;
        for (int i = 0; i < points.size(); i++) {
            BigDecimal mv = points.get(i).getMarketValue();
            if (mv != null && mv.compareTo(BigDecimal.ZERO) > 0) {
                firstValued = i;
                break;
            }
        }
        if (firstValued < 0) {
            return new ArrayList<>(); // hiç değerli gün yok → grafik boş
        }
        if (firstValued == 0) {
            return points;
        }
        return new ArrayList<>(points.subList(firstValued, points.size()));
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
