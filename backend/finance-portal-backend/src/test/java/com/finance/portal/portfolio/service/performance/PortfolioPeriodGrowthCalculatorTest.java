package com.finance.portal.portfolio.service.performance;

import com.finance.portal.common.domain.AssetType;
import com.finance.portal.portfolio.application.performance.PortfolioPerformancePoint;
import com.finance.portal.portfolio.domain.PortfolioTransaction;
import com.finance.portal.portfolio.domain.TransactionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioPeriodGrowthCalculatorTest {

    @Test
    @DisplayName("Senaryo 1: İlk gün alış — periodGrowthPercent = 0")
    void scenario1_firstDayPurchase_zeroGrowth() {
        LocalDate d1 = LocalDate.of(2026, 1, 1);
        List<PortfolioPerformancePoint> points = List.of(point(d1, "1000"));
        List<PortfolioTransaction> txs = List.of(
                buy("THYAO", d1.atStartOfDay(), "10", "100"));

        apply(points, txs);

        assertThat(points.get(0).getPeriodGrowthPercent()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Senaryo 2: Ertesi gün +10% piyasa — periodGrowthPercent = +10")
    void scenario2_nextDayMarketUp_tenPercent() {
        LocalDate d1 = LocalDate.of(2026, 1, 1);
        LocalDate d2 = LocalDate.of(2026, 1, 2);
        List<PortfolioPerformancePoint> points = new ArrayList<>(List.of(
                point(d1, "1000"),
                point(d2, "1100")));
        List<PortfolioTransaction> txs = List.of(
                buy("THYAO", d1.atStartOfDay(), "10", "100"));

        apply(points, txs);

        assertThat(points.get(0).getPeriodGrowthPercent()).isEqualByComparingTo("0");
        assertThat(points.get(1).getPeriodGrowthPercent()).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("Senaryo 3: Yeni 500 TL alım — getiri ~0 (nakit akışı arındırılmış)")
    void scenario3_newPurchase_neutralizesCashFlow() {
        LocalDate d1 = LocalDate.of(2026, 1, 1);
        LocalDate d2 = LocalDate.of(2026, 1, 2);
        LocalDate d3 = LocalDate.of(2026, 1, 3);
        List<PortfolioPerformancePoint> points = new ArrayList<>(List.of(
                point(d1, "1000"),
                point(d2, "1100"),
                point(d3, "1600")));
        List<PortfolioTransaction> txs = List.of(
                buy("THYAO", d1.atStartOfDay(), "10", "100"),
                buy("THYAO", d3.atStartOfDay(), "5", "100"));

        apply(points, txs);

        assertThat(points.get(2).getPeriodGrowthPercent()).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("Senaryo 4: 1600→1680 (+5%) — kümülatif zincirlenir")
    void scenario4_marketMoveFivePercent_chainedCumulative() {
        LocalDate d1 = LocalDate.of(2026, 1, 1);
        LocalDate d2 = LocalDate.of(2026, 1, 2);
        LocalDate d3 = LocalDate.of(2026, 1, 3);
        LocalDate d4 = LocalDate.of(2026, 1, 4);
        List<PortfolioPerformancePoint> points = new ArrayList<>(List.of(
                point(d1, "1000"),
                point(d2, "1100"),
                point(d3, "1600"),
                point(d4, "1680")));
        List<PortfolioTransaction> txs = List.of(
                buy("THYAO", d1.atStartOfDay(), "10", "100"),
                buy("THYAO", d3.atStartOfDay(), "5", "100"));

        apply(points, txs);

        // (1.10 * 1.00 * 1.05) - 1 = 15.50%
        assertThat(points.get(3).getPeriodGrowthPercent()).isEqualByComparingTo("15.50");
    }

    @Test
    @DisplayName("Senaryo 5: SELL nakit çıkışı — satış zarar gibi sayılmaz")
    void scenario5_sellCashOut_notCountedAsLoss() {
        LocalDate d1 = LocalDate.of(2026, 1, 1);
        LocalDate d2 = LocalDate.of(2026, 1, 2);
        List<PortfolioPerformancePoint> points = new ArrayList<>(List.of(
                point(d1, "1000"),
                point(d2, "600")));
        List<PortfolioTransaction> txs = List.of(
                buy("THYAO", d1.atStartOfDay(), "10", "100"),
                sell("THYAO", d2.atStartOfDay(), "4", "100"));

        apply(points, txs);

        assertThat(points.get(1).getPeriodGrowthPercent()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("netCashFlow: BUY + komisyon, SELL − (tutar − komisyon)")
    void netCashFlow_buyAndSell() {
        PortfolioTransaction buy = tx(TransactionType.BUY, "5", "100", "2");
        PortfolioTransaction sell = tx(TransactionType.SELL, "2", "110", "1");

        assertThat(PortfolioPeriodGrowthCalculator.netCashFlowForTransaction(buy))
                .isEqualByComparingTo("502");
        assertThat(PortfolioPeriodGrowthCalculator.netCashFlowForTransaction(sell))
                .isEqualByComparingTo("-219");
    }

    @Test
    @DisplayName("netCashFlow BOND: qty*price/100 (TCMB konvansiyonu) — altın hariç")
    void netCashFlow_bondParScaling() {
        PortfolioTransaction bondBuy = bondTx(TransactionType.BUY, "TRT060127T10", "10000", "78.50");
        PortfolioTransaction bondSell = bondTx(TransactionType.SELL, "TRT060127T10", "5000", "80.00");

        assertThat(PortfolioPeriodGrowthCalculator.netCashFlowForTransaction(bondBuy))
                .as("10000 * 78.50 / 100 = 7850")
                .isEqualByComparingTo("7850");
        assertThat(PortfolioPeriodGrowthCalculator.netCashFlowForTransaction(bondSell))
                .as("-(5000 * 80.00 / 100) = -4000")
                .isEqualByComparingTo("-4000");
    }

    @Test
    @DisplayName("netCashFlow GOLD BOND: /100 uygulanmaz")
    void netCashFlow_goldBondNoScaling() {
        PortfolioTransaction goldBuy = bondTx(TransactionType.BUY, "TRT270127T15", "100", "6500.00");
        java.util.function.Predicate<String> isGold = "TRT270127T15"::equals;

        assertThat(PortfolioPeriodGrowthCalculator.netCashFlowForTransaction(goldBuy, isGold))
                .as("Altın bond: 100 * 6500 = 650000 (NO /100)")
                .isEqualByComparingTo("650000");
    }

    private static PortfolioTransaction bondTx(TransactionType side, String symbol, String qty, String price) {
        PortfolioTransaction t = new PortfolioTransaction();
        t.setId(UUID.randomUUID());
        t.setSymbol(symbol);
        t.setAssetType(AssetType.BOND);
        t.setTransactionType(side);
        t.setQuantity(new BigDecimal(qty));
        t.setPrice(new BigDecimal(price));
        t.setCommission(BigDecimal.ZERO);
        t.setTransactionDate(LocalDate.of(2026, 1, 1).atStartOfDay());
        return t;
    }

    @Test
    @DisplayName("dailyReturn: önceki MV 0 ise 0")
    void dailyReturn_zeroPrevious_isZero() {
        assertThat(PortfolioPeriodGrowthCalculator.computeDailyReturn(
                BigDecimal.ZERO, new BigDecimal("1000"), BigDecimal.ZERO))
                .isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Entegrasyon: calculator + period growth senaryo 2")
    void integration_calculatorWithPeriodGrowth() {
        LocalDate d1 = LocalDate.of(2026, 4, 1);
        LocalDate d2 = LocalDate.of(2026, 4, 2);
        com.finance.portal.portfolio.application.viop.spec.ViopContractSpecRegistry specRegistry =
                org.mockito.Mockito.mock(com.finance.portal.portfolio.application.viop.spec.ViopContractSpecRegistry.class);
        org.mockito.Mockito.when(specRegistry.resolveOrFallback(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(com.finance.portal.portfolio.application.viop.spec.ViopContractSpec.fallback("TEST"));
        PortfolioPerformanceCalculator calc = new PortfolioPerformanceCalculator(
                org.mockito.Mockito.mock(com.finance.portal.market.application.bond.evds.EvdsBondService.class),
                specRegistry,
                new com.finance.portal.portfolio.application.viop.valuation.ViopValuationService(),
                org.mockito.Mockito.mock(com.finance.portal.market.application.fx.port.TcmbFxHistoryPort.class));

        List<PortfolioTransaction> txs = List.of(
                buyTx("THYAO", d1.atStartOfDay(), "10", "100"));

        NavigableMap<LocalDate, BigDecimal> prices = new TreeMap<>();
        prices.put(d1, new BigDecimal("100"));
        prices.put(d2, new BigDecimal("110"));

        String key = PortfolioPerformanceCalculator.positionKey(txs.get(0));
        Map<String, NavigableMap<LocalDate, BigDecimal>> priceSeries = Map.of(key, prices);

        List<PortfolioPerformancePoint> points = calc.calculate(
                txs, d1, d2, priceSeries, Set.of(), new ArrayList<>());
        PortfolioPeriodGrowthCalculator.applyPeriodGrowth(points, txs, Set.of());

        assertThat(points.get(1).getPeriodGrowthPercent()).isEqualByComparingTo("10");
    }

    private static void apply(List<PortfolioPerformancePoint> points, List<PortfolioTransaction> txs) {
        PortfolioPeriodGrowthCalculator.applyPeriodGrowth(points, txs, Set.of());
    }

    private static PortfolioPerformancePoint point(LocalDate date, String mv) {
        BigDecimal marketValue = new BigDecimal(mv);
        return new PortfolioPerformancePoint(
                date,
                marketValue,
                marketValue,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO);
    }

    private static PortfolioTransaction buy(String symbol, LocalDateTime when, String qty, String price) {
        return tx(TransactionType.BUY, symbol, when, qty, price);
    }

    private static PortfolioTransaction sell(String symbol, LocalDateTime when, String qty, String price) {
        return tx(TransactionType.SELL, symbol, when, qty, price);
    }

    private static PortfolioTransaction buyTx(String symbol, LocalDateTime when, String qty, String price) {
        PortfolioTransaction t = new PortfolioTransaction();
        t.setId(UUID.randomUUID());
        t.setSymbol(symbol);
        t.setAssetType(AssetType.STOCK);
        t.setTransactionType(TransactionType.BUY);
        t.setQuantity(new BigDecimal(qty));
        t.setPrice(new BigDecimal(price));
        t.setCommission(BigDecimal.ZERO);
        t.setTransactionDate(when);
        return t;
    }

    private static PortfolioTransaction tx(TransactionType side, String qty, String price, String commission) {
        PortfolioTransaction t = new PortfolioTransaction();
        t.setSymbol("X");
        t.setAssetType(AssetType.STOCK);
        t.setTransactionType(side);
        t.setQuantity(new BigDecimal(qty));
        t.setPrice(new BigDecimal(price));
        t.setCommission(new BigDecimal(commission));
        t.setTransactionDate(LocalDate.of(2026, 1, 1).atStartOfDay());
        return t;
    }

    private static PortfolioTransaction tx(TransactionType side, String symbol,
                                           LocalDateTime when, String qty, String price) {
        PortfolioTransaction t = new PortfolioTransaction();
        t.setId(UUID.randomUUID());
        t.setSymbol(symbol);
        t.setAssetType(AssetType.STOCK);
        t.setTransactionType(side);
        t.setQuantity(new BigDecimal(qty));
        t.setPrice(new BigDecimal(price));
        t.setCommission(BigDecimal.ZERO);
        t.setTransactionDate(when);
        return t;
    }
}
