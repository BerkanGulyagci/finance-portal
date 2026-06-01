package com.finance.portal.portfolio.service.performance;

import com.finance.portal.common.domain.AssetType;
import com.finance.portal.portfolio.application.performance.PortfolioPerformancePoint;
import com.finance.portal.portfolio.domain.PortfolioTransaction;
import com.finance.portal.portfolio.domain.TransactionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ek kapsam: {@link PortfolioPeriodGrowthCalculator} — mevcut
 * {@code PortfolioPeriodGrowthCalculatorTest}'in değinmediği guard/branch'ler
 * (null/empty noktalar, null MV, exclude key, null gold predicate, negatif denom,
 * COUPON_INCOME, eksik alanlar).
 */
class PortfolioPeriodGrowthCalculatorMoreTest {

    private static final BigDecimal ZERO_PCT = new BigDecimal("0.00");

    private static PortfolioPerformancePoint pt(LocalDate d, String mv) {
        PortfolioPerformancePoint p = new PortfolioPerformancePoint();
        p.setDate(d);
        p.setMarketValue(mv == null ? null : new BigDecimal(mv));
        return p;
    }

    private static PortfolioTransaction tx(LocalDate date, TransactionType type, AssetType asset,
                                           String symbol, String qty, String price, String commission) {
        PortfolioTransaction t = new PortfolioTransaction();
        t.setTransactionDate(date == null ? null : date.atStartOfDay());
        t.setTransactionType(type);
        t.setAssetType(asset);
        t.setSymbol(symbol);
        t.setQuantity(qty == null ? null : new BigDecimal(qty));
        t.setPrice(price == null ? null : new BigDecimal(price));
        t.setCommission(commission == null ? null : new BigDecimal(commission));
        return t;
    }

    // ── applyPeriodGrowth guards ─────────────────────────────────────────────

    @Test
    void applyPeriodGrowth_nullPoints_noThrow() {
        PortfolioPeriodGrowthCalculator.applyPeriodGrowth(null, List.of(), Set.of());
        // guard branch exercised; no exception
    }

    @Test
    void applyPeriodGrowth_emptyPoints_noThrow() {
        PortfolioPeriodGrowthCalculator.applyPeriodGrowth(new ArrayList<>(), List.of(), Set.of());
    }

    @Test
    void applyPeriodGrowth_nullTransactions_noThrow() {
        List<PortfolioPerformancePoint> points = new ArrayList<>(List.of(
                pt(LocalDate.of(2026, 1, 1), "1000"),
                pt(LocalDate.of(2026, 1, 2), "1100")));

        PortfolioPeriodGrowthCalculator.applyPeriodGrowth(points, null, Set.of());

        assertThat(points.get(1).getPeriodGrowthPercent()).isEqualByComparingTo(new BigDecimal("10.00"));
    }

    @Test
    void applyPeriodGrowth_intermediateZeroMv_resetsBaseline() {
        // i==0 zero (previousMv stays null) → next non-zero becomes baseline → growth resumes.
        List<PortfolioPerformancePoint> points = new ArrayList<>(List.of(
                pt(LocalDate.of(2026, 1, 1), "0"),
                pt(LocalDate.of(2026, 1, 2), "1000"),
                pt(LocalDate.of(2026, 1, 3), "1100")));

        PortfolioPeriodGrowthCalculator.applyPeriodGrowth(points, List.of(), Set.of());

        assertThat(points.get(0).getPeriodGrowthPercent()).isEqualByComparingTo(ZERO_PCT);
        assertThat(points.get(1).getPeriodGrowthPercent()).isEqualByComparingTo(ZERO_PCT);
        assertThat(points.get(2).getPeriodGrowthPercent()).isEqualByComparingTo(new BigDecimal("10.00"));
    }

    @Test
    void applyPeriodGrowth_nullCurrentMv_treatedAsZeroReturnMinus100() {
        List<PortfolioPerformancePoint> points = new ArrayList<>(List.of(
                pt(LocalDate.of(2026, 1, 1), "1000"),
                pt(LocalDate.of(2026, 1, 2), null)));   // normalized to 0 → -100%

        PortfolioPeriodGrowthCalculator.applyPeriodGrowth(points, List.of(), Set.of());

        assertThat(points.get(1).getPeriodGrowthPercent()).isEqualByComparingTo(new BigDecimal("-100.00"));
    }

    @Test
    void applyPeriodGrowth_negativeMv_normalizedToZero() {
        List<PortfolioPerformancePoint> points = new ArrayList<>(List.of(
                pt(LocalDate.of(2026, 1, 1), "1000"),
                pt(LocalDate.of(2026, 1, 2), "-50")));  // negative → normalized to 0 → -100%

        PortfolioPeriodGrowthCalculator.applyPeriodGrowth(points, List.of(), Set.of());

        assertThat(points.get(1).getPeriodGrowthPercent()).isEqualByComparingTo(new BigDecimal("-100.00"));
    }

    @Test
    void applyPeriodGrowth_excludedPositionKey_ignoresCashFlow() {
        // BUY 100 TL on day2; market also moved. With exclusion → flow ignored.
        // r = (1210 - 1000) / 1000 = 21%.
        List<PortfolioPerformancePoint> points = new ArrayList<>(List.of(
                pt(LocalDate.of(2026, 1, 1), "1000"),
                pt(LocalDate.of(2026, 1, 2), "1210")));

        List<PortfolioTransaction> txs = List.of(
                tx(LocalDate.of(2026, 1, 2), TransactionType.BUY, AssetType.STOCK, "AKBNK.IS", "100", "1.00", "0"));

        PortfolioPeriodGrowthCalculator.applyPeriodGrowth(points, txs, Set.of("AKBNK.IS::STOCK"));

        assertThat(points.get(1).getPeriodGrowthPercent()).isEqualByComparingTo(new BigDecimal("21.00"));
    }

    @Test
    void applyPeriodGrowth_txWithNullDate_skipped() {
        // A null-dated tx must be skipped in buildNetCashFlowByDay (no NPE, no flow).
        List<PortfolioPerformancePoint> points = new ArrayList<>(List.of(
                pt(LocalDate.of(2026, 1, 1), "1000"),
                pt(LocalDate.of(2026, 1, 2), "1100")));

        List<PortfolioTransaction> txs = new ArrayList<>();
        txs.add(tx(null, TransactionType.BUY, AssetType.STOCK, "X", "10", "5", "0"));

        PortfolioPeriodGrowthCalculator.applyPeriodGrowth(points, txs, Set.of());

        assertThat(points.get(1).getPeriodGrowthPercent()).isEqualByComparingTo(new BigDecimal("10.00"));
    }

    @Test
    void applyPeriodGrowth_nullGoldPredicate_usesNoGoldDefault() {
        List<PortfolioPerformancePoint> points = new ArrayList<>(List.of(
                pt(LocalDate.of(2026, 1, 1), "1000"),
                pt(LocalDate.of(2026, 1, 2), "1100")));

        PortfolioPeriodGrowthCalculator.applyPeriodGrowth(points, List.of(), Set.of(), null);

        assertThat(points.get(1).getPeriodGrowthPercent()).isEqualByComparingTo(new BigDecimal("10.00"));
    }

    @Test
    void applyPeriodGrowth_nullExcludedKeys_noThrow() {
        List<PortfolioPerformancePoint> points = new ArrayList<>(List.of(
                pt(LocalDate.of(2026, 1, 1), "1000"),
                pt(LocalDate.of(2026, 1, 2), "1100")));

        List<PortfolioTransaction> txs = List.of(
                tx(LocalDate.of(2026, 1, 1), TransactionType.BUY, AssetType.STOCK, "X", "10", "100", "0"));

        // excludedPositionKeys null → no exclusion path
        PortfolioPeriodGrowthCalculator.applyPeriodGrowth(points, txs, null);

        assertThat(points.get(1).getPeriodGrowthPercent()).isEqualByComparingTo(new BigDecimal("10.00"));
    }

    // ── computeDailyReturn branches ──────────────────────────────────────────

    @Test
    void computeDailyReturn_nullPrevious_zero() {
        assertThat(PortfolioPeriodGrowthCalculator.computeDailyReturn(null, BigDecimal.TEN, BigDecimal.ZERO))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void computeDailyReturn_negativePrevious_zero() {
        assertThat(PortfolioPeriodGrowthCalculator.computeDailyReturn(
                new BigDecimal("-5"), BigDecimal.TEN, BigDecimal.ZERO))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void computeDailyReturn_nullCurrentAndFlows_treatedAsZero() {
        BigDecimal r = PortfolioPeriodGrowthCalculator.computeDailyReturn(
                new BigDecimal("1000"), null, null);
        assertThat(r).isEqualByComparingTo(new BigDecimal("-1"));
    }

    @Test
    void computeDailyReturn_negativeDenominator_zero() {
        // prev=100, flows=-200 → denom = -100 ≤ 0 → 0
        BigDecimal r = PortfolioPeriodGrowthCalculator.computeDailyReturn(
                new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("-200"));
        assertThat(r).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void computeDailyReturn_zeroDenominator_zero() {
        // prev=100, flows=-100 → denom = 0 → 0
        BigDecimal r = PortfolioPeriodGrowthCalculator.computeDailyReturn(
                new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("-100"));
        assertThat(r).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void computeDailyReturn_standardFormula() {
        // (1210 - 1000 - 100) / (1000 + 100) = 110/1100 = 0.1
        BigDecimal r = PortfolioPeriodGrowthCalculator.computeDailyReturn(
                new BigDecimal("1000"), new BigDecimal("1210"), new BigDecimal("100"));
        assertThat(r).isEqualByComparingTo(new BigDecimal("0.1"));
    }

    // ── netCashFlowForTransaction branches ───────────────────────────────────

    @Test
    void netCashFlow_nullTx_zero() {
        assertThat(PortfolioPeriodGrowthCalculator.netCashFlowForTransaction(null))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void netCashFlow_nullQuantity_zero() {
        assertThat(PortfolioPeriodGrowthCalculator.netCashFlowForTransaction(
                tx(LocalDate.of(2026, 1, 1), TransactionType.BUY, AssetType.STOCK, "X", null, "1", "0")))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void netCashFlow_nullPrice_zero() {
        assertThat(PortfolioPeriodGrowthCalculator.netCashFlowForTransaction(
                tx(LocalDate.of(2026, 1, 1), TransactionType.BUY, AssetType.STOCK, "X", "1", null, "0")))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void netCashFlow_nullCommission_defaultsZero() {
        BigDecimal r = PortfolioPeriodGrowthCalculator.netCashFlowForTransaction(
                tx(LocalDate.of(2026, 1, 1), TransactionType.BUY, AssetType.STOCK, "X", "10", "5", null));
        assertThat(r).isEqualByComparingTo(new BigDecimal("50.0000"));
    }

    @Test
    void netCashFlow_couponIncome_zero() {
        BigDecimal r = PortfolioPeriodGrowthCalculator.netCashFlowForTransaction(
                tx(LocalDate.of(2026, 1, 1), TransactionType.COUPON_INCOME, AssetType.BOND, "X", "10", "1", "0"));
        assertThat(r).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void netCashFlow_bond_nullPredicate_divPar() {
        // isGoldBondSymbol null → treated as not gold → BOND price /100. 10*100/100 = 10.
        BigDecimal r = PortfolioPeriodGrowthCalculator.netCashFlowForTransaction(
                tx(LocalDate.of(2026, 1, 1), TransactionType.BUY, AssetType.BOND, "TRT", "10", "100", "0"),
                (Predicate<String>) null);
        assertThat(r).isEqualByComparingTo(new BigDecimal("10.0000"));
    }

    @Test
    void netCashFlow_bond_nullSymbol_divPar() {
        // symbol null on a BOND → not gold → /100 applied. 10*100/100 = 10.
        PortfolioTransaction t = tx(LocalDate.of(2026, 1, 1), TransactionType.BUY,
                AssetType.BOND, null, "10", "100", "0");
        BigDecimal r = PortfolioPeriodGrowthCalculator.netCashFlowForTransaction(t, "ALTING"::equals);
        assertThat(r).isEqualByComparingTo(new BigDecimal("10.0000"));
    }
}
