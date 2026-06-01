package com.finance.portal.portfolio.service;

import com.finance.portal.common.domain.AssetType;
import com.finance.portal.market.application.economy.InflationDeflatorService;
import com.finance.portal.market.application.economy.model.EconomySeriesPoint;
import com.finance.portal.portfolio.presentation.dto.PortfolioHoldingResponse;
import com.finance.portal.portfolio.presentation.dto.PortfolioResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Ek kapsam: {@link PortfolioRealReturnEnricher} — mevcut {@code PortfolioRealReturnEnricherTest}'in
 * değinmediği dallar: FUTURE (VİOP) teminat reel hesabı, BOND kupon step-up, ABD CPI serisi yokken
 * non-TRY native frame atlama, egzotik para birimi (TRY/USD değil) native frame yok ama TL frame'e
 * faktör=1 ile katılır.
 */
class PortfolioRealReturnEnricherMoreTest {

    private InflationDeflatorService deflator;
    private PortfolioCurrencyConverter currencyConverter;
    private PortfolioRealReturnEnricher enricher;

    @BeforeEach
    void setUp() {
        deflator = mock(InflationDeflatorService.class);
        currencyConverter = mock(PortfolioCurrencyConverter.class);
        lenient().when(currencyConverter.toTry(any(BigDecimal.class), anyString()))
                .thenAnswer(inv -> {
                    BigDecimal a = inv.getArgument(0);
                    String c = inv.getArgument(1);
                    if (a == null) return null;
                    if (c == null || "TRY".equalsIgnoreCase(c) || "TL".equalsIgnoreCase(c)) return a;
                    return a.multiply(new BigDecimal("30")); // sahte USD/TRY ve egzotik kur
                });
        lenient().when(currencyConverter.toTry(isNull(), anyString())).thenReturn(null);

        EconomySeriesPoint p = new EconomySeriesPoint("2026-01-01", new BigDecimal("100"), 0L);
        when(deflator.tufeSeries()).thenReturn(List.of(p));
        lenient().when(deflator.usCpiSeries()).thenReturn(List.of());

        enricher = new PortfolioRealReturnEnricher(deflator, currencyConverter);
    }

    // ── FUTURE (VİOP) ────────────────────────────────────────────────────────

    @Test
    void apply_futureWithMarginPosted_usesMarginAsRealCost() {
        // margin=1000, MV(M2M)=1500, factor=1.20 → realCost=1200 → realPL = 1500-1200 = 300.
        PortfolioHoldingResponse h = future("XU030", "5000", "1500", "TRY",
                LocalDate.of(2026, 1, 1), new BigDecimal("1000"));
        PortfolioResponse r = new PortfolioResponse();
        r.setHoldings(List.of(h));
        when(deflator.cumulativeFactor(any(), eq(LocalDate.of(2026, 1, 1)), any()))
                .thenReturn(Optional.of(new BigDecimal("1.20")));

        enricher.apply(r);

        assertThat(h.getRealProfitLoss()).isEqualByComparingTo("300.00");
        // pct = (1500/1200 - 1)*100 = 25
        assertThat(h.getRealProfitLossPercent()).isEqualByComparingTo("25.00");
        assertThat(h.getInflationSincePercent()).isEqualByComparingTo("20.00");
        assertThat(h.getInflationSource()).isEqualTo("TÜFE");
        // TL frame: costTl=1000, mvTl=1500, realCostTl=1200 → realPLTry 300
        assertThat(h.getRealProfitLossTry()).isEqualByComparingTo("300.00");
        assertThat(h.getInflationSinceTryPercent()).isEqualByComparingTo("20.00");
        assertThat(r.getTotalRealProfitLoss()).isEqualByComparingTo("300.00");
    }

    @Test
    void apply_futureNullMarginPosted_fallsBackToTotalCost() {
        // viopMarginPosted null → margin = totalCost (2000). factor empty → f=1 → realCost=2000.
        PortfolioHoldingResponse h = future("XU030", "2000", "2200", "TRY",
                LocalDate.of(2026, 1, 1), null);
        PortfolioResponse r = new PortfolioResponse();
        r.setHoldings(List.of(h));
        when(deflator.cumulativeFactor(any(), any(), any())).thenReturn(Optional.empty());

        enricher.apply(r);

        // f=1 (orElse): realCost=2000 → realPL = 2200-2000 = 200.
        assertThat(h.getRealProfitLoss()).isEqualByComparingTo("200.00");
        // fOpt empty → inflationSince NOT set
        assertThat(h.getInflationSincePercent()).isNull();
        assertThat(h.getInflationSource()).isNull();
        assertThat(r.getTotalRealProfitLoss()).isEqualByComparingTo("200.00");
    }

    @Test
    void apply_futureNonPositiveMargin_skipped() {
        PortfolioHoldingResponse h = future("XU030", "0", "100", "TRY",
                LocalDate.of(2026, 1, 1), new BigDecimal("0"));
        PortfolioResponse r = new PortfolioResponse();
        r.setHoldings(List.of(h));

        enricher.apply(r);

        assertThat(h.getRealProfitLoss()).isNull();
        assertThat(r.getTotalRealProfitLoss()).isNull();
    }

    @Test
    void apply_futureNullMarketValue_skipped() {
        PortfolioHoldingResponse h = future("XU030", "1000", null, "TRY",
                LocalDate.of(2026, 1, 1), new BigDecimal("1000"));
        PortfolioResponse r = new PortfolioResponse();
        r.setHoldings(List.of(h));

        enricher.apply(r);

        assertThat(h.getRealProfitLoss()).isNull();
        assertThat(r.getTotalRealProfitLoss()).isNull();
    }

    // ── BOND kupon step-up ───────────────────────────────────────────────────

    @Test
    void apply_bondWithCoupons_couponAddedToMarketValue() {
        // TRY bond: cost=1000, MV=1050, sumCoupon=200, factor=1.10 → realCost=1100.
        // Native frame (TRY): mvAdj = 1050 + 200 = 1250 → realPL = 1250 - 1100 = 150.
        PortfolioHoldingResponse h = holding("TRT", AssetType.BOND, "1000", "1050", "TRY",
                LocalDate.of(2026, 1, 1));
        h.setSumCouponIncome(new BigDecimal("200"));
        PortfolioResponse r = new PortfolioResponse();
        r.setHoldings(List.of(h));
        when(deflator.cumulativeFactor(any(), eq(LocalDate.of(2026, 1, 1)), any()))
                .thenReturn(Optional.of(new BigDecimal("1.10")));

        enricher.apply(r);

        assertThat(h.getRealProfitLoss()).isEqualByComparingTo("150.00");
        // TL frame: coupons (TL) eklenir → mvTlAdj = 1050+200=1250, realCostTl=1100 → 150
        assertThat(h.getRealProfitLossTry()).isEqualByComparingTo("150.00");
        assertThat(r.getTotalRealProfitLoss()).isEqualByComparingTo("150.00");
    }

    @Test
    void apply_usdBondCoupons_nativeFrameSkipsCouponButTlFrameAdds() {
        // USD bond: couponsNative = 0 (not TRY) → native mvAdj = marketValue only.
        // But TL frame adds coupons (always TL).
        when(deflator.usCpiSeries()).thenReturn(List.of(
                new EconomySeriesPoint("2026-01-01", new BigDecimal("250"), 0L)));
        when(deflator.cumulativeFactor(any(), any(), any())).thenReturn(Optional.of(new BigDecimal("1.05")));
        PortfolioHoldingResponse h = holding("US912", AssetType.BOND, "100", "120", "USD",
                LocalDate.of(2026, 1, 1));
        h.setSumCouponIncome(new BigDecimal("300")); // TL coupon
        PortfolioResponse r = new PortfolioResponse();
        r.setHoldings(List.of(h));

        enricher.apply(r);

        // Native frame uses US CPI source (USD)
        assertThat(h.getInflationSource()).isEqualTo("ABD TÜFE");
        // Native: mvAdj = 120 (no coupon), realCost = 100*1.05 = 105 → realPL = 15
        assertThat(h.getRealProfitLoss()).isEqualByComparingTo("15.00");
        // TL frame: costTl=3000, mvTl=3600, +coupon 300 = 3900; realCostTl=3000*1.05=3150 → 750
        assertThat(h.getRealProfitLossTry()).isEqualByComparingTo("750.00");
    }

    // ── currency / series edge cases ─────────────────────────────────────────

    @Test
    void apply_usdHoldingButNoUsCpiSeries_nativeFrameSkipped_tlFrameStillApplies() {
        // usCpiSeries empty (default) → nativeSeries null → native frame skipped, but TL frame runs.
        when(deflator.cumulativeFactor(any(), eq(LocalDate.of(2026, 1, 1)), any()))
                .thenReturn(Optional.of(new BigDecimal("1.20")));
        PortfolioHoldingResponse h = holding("AAPL", AssetType.STOCK, "100", "120", "USD",
                LocalDate.of(2026, 1, 1));
        PortfolioResponse r = new PortfolioResponse();
        r.setHoldings(List.of(h));

        enricher.apply(r);

        // native frame NOT set (no US CPI)
        assertThat(h.getRealProfitLoss()).isNull();
        assertThat(h.getInflationSource()).isNull();
        // TL frame: costTl=3000, mvTl=3600, realCostTl=3600... wait realCostTl=3000*1.20=3600 → realPL 0
        assertThat(h.getRealProfitLossTry()).isEqualByComparingTo("0.00");
        assertThat(r.getTotalRealProfitLoss()).isEqualByComparingTo("0.00");
    }

    @Test
    void apply_exoticCurrency_noNativeFrame_butContributesToTlTotal() {
        // EUR is neither TRY nor USD → nativeSeries null → no native frame columns.
        // TL frame still applies via converter (×30).
        when(deflator.cumulativeFactor(any(), eq(LocalDate.of(2026, 1, 1)), any()))
                .thenReturn(Optional.of(new BigDecimal("1.10")));
        PortfolioHoldingResponse h = holding("SAP", AssetType.STOCK, "100", "130", "EUR",
                LocalDate.of(2026, 1, 1));
        PortfolioResponse r = new PortfolioResponse();
        r.setHoldings(List.of(h));

        enricher.apply(r);

        assertThat(h.getRealProfitLoss()).isNull();           // no native frame
        assertThat(h.getInflationSource()).isNull();
        // TL frame: costTl=3000, mvTl=3900, realCostTl=3000*1.10=3300 → realPLTry = 600
        assertThat(h.getRealProfitLossTry()).isEqualByComparingTo("600.00");
        assertThat(r.getTotalRealProfitLoss()).isEqualByComparingTo("600.00");
    }

    @Test
    void apply_nullMarketValue_skipped() {
        PortfolioHoldingResponse h = holding("X", AssetType.STOCK, "1000", null, "TRY",
                LocalDate.of(2026, 1, 1));
        PortfolioResponse r = new PortfolioResponse();
        r.setHoldings(List.of(h));

        enricher.apply(r);

        assertThat(h.getRealProfitLoss()).isNull();
        assertThat(r.getTotalRealProfitLoss()).isNull();
    }

    @Test
    void apply_clearsStaleFieldsBeforeRecompute() {
        // Holding has stale cached values; cost ≤ 0 means it is skipped — fields must be cleared.
        PortfolioHoldingResponse h = holding("X", AssetType.STOCK, "0", "100", "TRY",
                LocalDate.of(2026, 1, 1));
        h.setRealProfitLoss(new BigDecimal("999"));
        h.setInflationSource("STALE");
        h.setRealProfitLossTry(new BigDecimal("777"));
        PortfolioResponse r = new PortfolioResponse();
        r.setHoldings(List.of(h));

        enricher.apply(r);

        assertThat(h.getRealProfitLoss()).isNull();
        assertThat(h.getInflationSource()).isNull();
        assertThat(h.getRealProfitLossTry()).isNull();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static PortfolioHoldingResponse holding(String symbol, AssetType type, String cost,
                                                    String mv, String currency, LocalDate buyDate) {
        PortfolioHoldingResponse h = new PortfolioHoldingResponse();
        h.setSymbol(symbol);
        h.setAssetType(type);
        h.setTotalCost(new BigDecimal(cost));
        h.setMarketValue(mv == null ? null : new BigDecimal(mv));
        h.setCurrency(currency);
        h.setFirstBuyDate(buyDate != null ? buyDate.atStartOfDay() : null);
        return h;
    }

    private static PortfolioHoldingResponse future(String symbol, String cost, String mv,
                                                   String currency, LocalDate buyDate,
                                                   BigDecimal marginPosted) {
        PortfolioHoldingResponse h = holding(symbol, AssetType.FUTURE, cost, mv, currency, buyDate);
        h.setViopMarginPosted(marginPosted);
        return h;
    }
}
