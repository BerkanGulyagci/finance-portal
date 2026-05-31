package com.finance.portal.portfolio.service.performance;

import com.finance.portal.common.domain.AssetType;
import com.finance.portal.portfolio.application.performance.ExcludedPerformanceAsset;
import com.finance.portal.portfolio.application.performance.PortfolioPerformancePoint;
import com.finance.portal.portfolio.application.performance.PortfolioPerformanceRange;
import com.finance.portal.portfolio.domain.PortfolioTransaction;
import com.finance.portal.portfolio.domain.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioPerformanceCalculatorTest {

    private PortfolioPerformanceCalculator calculator;

    @BeforeEach
    void setUp() {
        com.finance.portal.portfolio.application.viop.spec.ViopContractSpecRegistry specRegistry =
                org.mockito.Mockito.mock(com.finance.portal.portfolio.application.viop.spec.ViopContractSpecRegistry.class);
        org.mockito.Mockito.when(specRegistry.resolveOrFallback(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(com.finance.portal.portfolio.application.viop.spec.ViopContractSpec.fallback("TEST"));
        calculator = new PortfolioPerformanceCalculator(
                org.mockito.Mockito.mock(com.finance.portal.market.application.bond.evds.EvdsBondService.class),
                specRegistry,
                new com.finance.portal.portfolio.application.viop.valuation.ViopValuationService());
    }

    @Test
    @DisplayName("BUY/SELL replay ile günlük marketValue ve maliyet hesaplanır")
    void calculate_buySellReplay() {
        LocalDate d1 = LocalDate.of(2026, 4, 1);
        LocalDate d2 = LocalDate.of(2026, 4, 2);
        LocalDate d3 = LocalDate.of(2026, 4, 3);

        List<PortfolioTransaction> txs = List.of(
                buy("THYAO", AssetType.STOCK, d1.atStartOfDay(), "10", "100"),
                sell("THYAO", AssetType.STOCK, d2.atStartOfDay(), "4", "110")
        );

        NavigableMap<LocalDate, BigDecimal> prices = new TreeMap<>();
        prices.put(d1, new BigDecimal("100"));
        prices.put(d2, new BigDecimal("110"));
        prices.put(d3, new BigDecimal("105"));

        String key = PortfolioPerformanceCalculator.positionKey(txs.get(0));
        Map<String, NavigableMap<LocalDate, BigDecimal>> series = Map.of(key, prices);
        Set<String> excluded = new HashSet<>();
        List<ExcludedPerformanceAsset> excludedOut = new ArrayList<>();

        List<PortfolioPerformancePoint> points = calculator.calculate(
                txs, d1, d3, series, excluded, excludedOut);

        assertThat(excludedOut).isEmpty();
        assertThat(points).hasSize(3);

        // Day1: 10 * 100 = 1000 MV, cost 10*100 = 1000
        assertThat(points.get(0).getMarketValue()).isEqualByComparingTo("1000");
        assertThat(points.get(0).getTotalCost()).isEqualByComparingTo("1000");
        assertThat(points.get(0).getProfitLossPercent()).isEqualByComparingTo("0");

        // Day2: 6 * 110 = 660 MV, cost 600 (60% of 1000)
        assertThat(points.get(1).getMarketValue()).isEqualByComparingTo("660");
        assertThat(points.get(1).getTotalCost()).isEqualByComparingTo("600");
        assertThat(points.get(1).getProfitLoss()).isEqualByComparingTo("60");

        // Day3: 6 * 105 = 630
        assertThat(points.get(2).getMarketValue()).isEqualByComparingTo("630");
    }

    @Test
    @DisplayName("Forward-fill: eksik günde önceki kapanış kullanılır")
    void priceOnDay_forwardFill() {
        NavigableMap<LocalDate, BigDecimal> series = new TreeMap<>();
        series.put(LocalDate.of(2026, 4, 1), new BigDecimal("50"));
        series.put(LocalDate.of(2026, 4, 5), new BigDecimal("55"));

        assertThat(PortfolioPerformanceCalculator.priceOnDay(series, LocalDate.of(2026, 4, 3)))
                .isEqualByComparingTo("50");
        assertThat(PortfolioPerformanceCalculator.priceOnDay(series, LocalDate.of(2026, 4, 5)))
                .isEqualByComparingTo("55");
        assertThat(PortfolioPerformanceCalculator.priceOnDay(series, LocalDate.of(2026, 3, 31)))
                .isNull();
    }

    @Test
    @DisplayName("Sıfır veya negatif fiyat geçersiz sayılır")
    void priceOnDay_rejectsNonPositive() {
        NavigableMap<LocalDate, BigDecimal> series = new TreeMap<>();
        series.put(LocalDate.of(2026, 4, 1), new BigDecimal("-1"));
        series.put(LocalDate.of(2026, 4, 2), BigDecimal.ZERO);

        assertThat(PortfolioPerformanceCalculator.priceOnDay(series, LocalDate.of(2026, 4, 1)))
                .isNull();
        assertThat(PortfolioPerformanceCalculator.priceOnDay(series, LocalDate.of(2026, 4, 2)))
                .isNull();
    }

    @Test
    @DisplayName("Gün içinde fiyat yoksa pozisyon hariç tutulur (ConcurrentModification olmaz)")
    void calculate_missingPriceOnDay_excludesWithoutConcurrentModification() {
        LocalDate d1 = LocalDate.of(2026, 4, 1);
        LocalDate d2 = LocalDate.of(2026, 4, 2);

        List<PortfolioTransaction> txs = List.of(
                buy("GARAN", AssetType.STOCK, d1.atStartOfDay(), "5", "50"));

        // İlk günler için geçerli kapanış yok (sadece gelecekte fiyat var)
        NavigableMap<LocalDate, BigDecimal> garan = new TreeMap<>();
        garan.put(LocalDate.of(2026, 4, 10), new BigDecimal("55"));

        String keyGaran = PortfolioPerformanceCalculator.positionKey(txs.get(0));
        Map<String, NavigableMap<LocalDate, BigDecimal>> series = Map.of(keyGaran, garan);
        Set<String> excluded = new HashSet<>();
        List<ExcludedPerformanceAsset> excludedOut = new ArrayList<>();

        List<PortfolioPerformancePoint> points = calculator.calculate(
                txs, d1, d2, series, excluded, excludedOut);

        assertThat(points).hasSize(2);
        assertThat(excludedOut).hasSize(1);
        assertThat(excluded).contains(keyGaran);
        assertThat(points.get(0).getMarketValue()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("totalCost sıfırsa profitLossPercent 0")
    void zeroCost_percentIsZero() {
        LocalDate day = LocalDate.of(2026, 5, 1);
        List<PortfolioPerformancePoint> points = calculator.calculate(
                List.of(), day, day, Map.of(), Set.of(), new ArrayList<>());
        assertThat(points).hasSize(1);
        assertThat(points.get(0).getProfitLossPercent()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("YTD effectiveStart yıl başını keser (max 1 yıl)")
    void range_effectiveStartYtd() {
        LocalDate today = LocalDate.of(2026, 5, 17);
        LocalDate start = PortfolioPerformanceRange.YTD.effectiveStart(today);
        assertThat(start).isEqualTo(LocalDate.of(2026, 1, 1));
    }

    @Test
    @DisplayName("Range alias parse")
    void range_parseAliases() {
        assertThat(PortfolioPerformanceRange.parse("1M")).isEqualTo(PortfolioPerformanceRange.ONE_MONTH);
        assertThat(PortfolioPerformanceRange.parse("6M")).isEqualTo(PortfolioPerformanceRange.SIX_MONTHS);
        assertThat(PortfolioPerformanceRange.parse("ONE_YEAR").getApiLabel()).isEqualTo("1Y");
    }

    private static PortfolioTransaction buy(String symbol, AssetType type, LocalDateTime when,
                                            String qty, String price) {
        return tx(symbol, type, TransactionType.BUY, when, qty, price);
    }

    private static PortfolioTransaction sell(String symbol, AssetType type, LocalDateTime when,
                                             String qty, String price) {
        return tx(symbol, type, TransactionType.SELL, when, qty, price);
    }

    private static PortfolioTransaction tx(String symbol, AssetType type, TransactionType side,
                                           LocalDateTime when, String qty, String price) {
        PortfolioTransaction t = new PortfolioTransaction();
        t.setId(UUID.randomUUID());
        t.setSymbol(symbol);
        t.setAssetType(type);
        t.setTransactionType(side);
        t.setQuantity(new BigDecimal(qty));
        t.setPrice(new BigDecimal(price));
        t.setCommission(BigDecimal.ZERO);
        t.setTransactionDate(when);
        return t;
    }
}
