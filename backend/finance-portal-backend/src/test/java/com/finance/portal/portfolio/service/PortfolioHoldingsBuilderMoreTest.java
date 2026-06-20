package com.finance.portal.portfolio.service;

import com.finance.portal.common.domain.AssetType;
import com.finance.portal.market.application.bond.evds.EvdsBondInstrument;
import com.finance.portal.market.application.bond.evds.EvdsBondService;
import com.finance.portal.market.application.bond.evds.model.BondCategory;
import com.finance.portal.portfolio.application.port.HoldingMarketEnrichmentPort;
import com.finance.portal.portfolio.application.viop.spec.ViopContractSpec;
import com.finance.portal.portfolio.application.viop.spec.ViopContractSpecRegistry;
import com.finance.portal.portfolio.domain.PortfolioTransaction;
import com.finance.portal.portfolio.domain.TransactionType;
import com.finance.portal.portfolio.presentation.dto.PortfolioHoldingResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Branch-coverage focused complement to {@link PortfolioHoldingsBuilderTest}.
 * Targets per-AssetType arms (BOND /100 scaling + parUnscaled, FUTURE multiplier/direction),
 * COUPON_INCOME, missing-price fail-soft (EVDS lookup throws), zero-quantity skip,
 * currency-guess branches, BOND closed-row emit, and skipInlineEnrich.
 */
class PortfolioHoldingsBuilderMoreTest {

    private HoldingMarketEnrichmentPort enrichmentPort;
    private EvdsBondService evdsBondService;
    private ViopContractSpecRegistry specRegistry;
    private PortfolioCurrencyConverter currencyConverter;
    private PortfolioHoldingsBuilder builder;

    @BeforeEach
    void setUp() {
        enrichmentPort = mock(HoldingMarketEnrichmentPort.class);
        evdsBondService = mock(EvdsBondService.class);
        specRegistry = mock(ViopContractSpecRegistry.class);
        currencyConverter = mock(PortfolioCurrencyConverter.class);
        builder = new PortfolioHoldingsBuilder(enrichmentPort, evdsBondService, specRegistry, currencyConverter);
    }

    // ----------------------------------------------------------------------
    // BOND: par-unscaled (GOLD_INDEXED_BOND) vs standard /100 quote
    // ----------------------------------------------------------------------

    @Test
    @DisplayName("BOND standart (TÜFE/Tier1): effectivePrice = price/100 uygulanır")
    void bond_standardQuote_appliesParScaleDivision() {
        // EVDS detail FIXED_COUPON_BOND (usesPerUnitNominalQuote=false) → /100 yolu
        EvdsBondInstrument inst = new EvdsBondInstrument();
        inst.setCategory(BondCategory.FIXED_COUPON_BOND);
        when(evdsBondService.getEvdsBondDetail("TRT150927T11")).thenReturn(inst);

        // BUY 100 nominal @ price 80 (100 TL nominal üzerinden) → effective 0.80 → cost 80
        PortfolioTransaction buy = makeTx(TransactionType.BUY, "TRT150927T11", AssetType.BOND,
                "100", "80", "0", "2026-01-01T10:00:00");

        PortfolioHoldingResponse h = builder.build(List.of(buy)).get(0);

        assertThat(h.getAssetType()).isEqualTo(AssetType.BOND);
        assertThat(h.getTotalQuantity()).isEqualByComparingTo("100");
        assertThat(h.getTotalCost()).isEqualByComparingTo("80"); // 100 * (80/100)
        // BOND open holding → sumCouponIncome + couponEvents alanları doldurulur
        assertThat(h.getSumCouponIncome()).isEqualByComparingTo("0");
        assertThat(h.getCouponEvents()).isEmpty();
    }

    @Test
    @DisplayName("BOND par-unscaled (GOLD_INDEXED_BOND): /100 atlanır, fiyat birim üzerinden")
    void bond_perUnitNominalQuote_skipsParScale() {
        EvdsBondInstrument inst = new EvdsBondInstrument();
        inst.setCategory(BondCategory.GOLD_INDEXED_BOND); // usesPerUnitNominalQuote=true
        when(evdsBondService.getEvdsBondDetail("ALTINBOND")).thenReturn(inst);

        // BUY 3 gram @ 2500 TL/gram → /100 UYGULANMAZ → cost 7500
        PortfolioTransaction buy = makeTx(TransactionType.BUY, "ALTINBOND", AssetType.BOND,
                "3", "2500", "0", "2026-01-01T10:00:00");

        PortfolioHoldingResponse h = builder.build(List.of(buy)).get(0);

        assertThat(h.getTotalCost()).isEqualByComparingTo("7500"); // 3 * 2500, no /100
    }

    @Test
    @DisplayName("isParUnscaledBondSymbol: EVDS lookup fırlatırsa fail-soft → klasik /100")
    void bond_evdsLookupThrows_failsSoftToParScale() {
        when(evdsBondService.getEvdsBondDetail(anyString()))
                .thenThrow(new RuntimeException("EVDS down"));

        PortfolioTransaction buy = makeTx(TransactionType.BUY, "TRT_FAIL", AssetType.BOND,
                "100", "90", "0", "2026-01-01T10:00:00");

        PortfolioHoldingResponse h = builder.build(List.of(buy)).get(0);

        // catch → false → /100 uygulanır → 100 * 0.90 = 90
        assertThat(h.getTotalCost()).isEqualByComparingTo("90");
    }

    @Test
    @DisplayName("isParUnscaledBondSymbol: EVDS null/category null → klasik /100")
    void bond_evdsNullDetail_appliesParScale() {
        when(evdsBondService.getEvdsBondDetail(anyString())).thenReturn(null);

        PortfolioTransaction buy = makeTx(TransactionType.BUY, "TRT_NULL", AssetType.BOND,
                "100", "70", "0", "2026-01-01T10:00:00");

        PortfolioHoldingResponse h = builder.build(List.of(buy)).get(0);

        assertThat(h.getTotalCost()).isEqualByComparingTo("70"); // null → false → /100
    }

    // ----------------------------------------------------------------------
    // COUPON_INCOME branch (+ couponEvents with/without txDate)
    // ----------------------------------------------------------------------

    @Test
    @DisplayName("BOND COUPON_INCOME: realized + sumCouponIncome + couponEvent kaydı (açık pozisyon)")
    void bond_couponIncome_recordsCouponAndEvent() {
        EvdsBondInstrument inst = new EvdsBondInstrument();
        inst.setCategory(BondCategory.FIXED_COUPON_BOND);
        lenient().when(evdsBondService.getEvdsBondDetail(anyString())).thenReturn(inst);

        // BUY 100 @ 100 → effective 1.0 → cost 100 ; COUPON_INCOME qty=50 (TL), price=1
        PortfolioTransaction buy = makeTx(TransactionType.BUY, "TRT_CPN", AssetType.BOND,
                "100", "100", "0", "2026-01-01T10:00:00");
        PortfolioTransaction coupon = makeTx(TransactionType.COUPON_INCOME, "TRT_CPN", AssetType.BOND,
                "50", "1", "0", "2026-06-01T10:00:00");

        PortfolioHoldingResponse h = builder.build(List.of(buy, coupon)).get(0);

        // pozisyon hala açık (qty 100), kupon openQty'ye dokunmaz
        assertThat(h.getTotalQuantity()).isEqualByComparingTo("100");
        assertThat(h.getSumCouponIncome()).isEqualByComparingTo("50");
        assertThat(h.getCouponEvents()).hasSize(1);
        assertThat(h.getCouponEvents().get(0).date()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(h.getCouponEvents().get(0).amountTl()).isEqualByComparingTo("50");
        // anySell=true (kupon) ama totalSoldCostBasis=0 → pct null
        assertThat(h.getRealizedGainLoss()).isEqualByComparingTo("50");
        assertThat(h.getRealizedGainLossPercent()).isNull();
    }

    @Test
    @DisplayName("COUPON_INCOME: txDate null → couponEvents'e eklenmez (sadece sum)")
    void couponIncome_nullDate_noEventButSum() {
        EvdsBondInstrument inst = new EvdsBondInstrument();
        inst.setCategory(BondCategory.FIXED_COUPON_BOND);
        lenient().when(evdsBondService.getEvdsBondDetail(anyString())).thenReturn(inst);

        PortfolioTransaction buy = makeTx(TransactionType.BUY, "TRT_CPN2", AssetType.BOND,
                "100", "100", "0", "2026-01-01T10:00:00");
        PortfolioTransaction coupon = makeTx(TransactionType.COUPON_INCOME, "TRT_CPN2", AssetType.BOND,
                "30", "1", "0", null); // null date

        PortfolioHoldingResponse h = builder.build(List.of(buy, coupon)).get(0);

        assertThat(h.getSumCouponIncome()).isEqualByComparingTo("30");
        assertThat(h.getCouponEvents()).isEmpty(); // date null → no event
    }

    // ----------------------------------------------------------------------
    // BOND closed row emit (anySell + totalBuyCostBasis > 0) vs coupon-only (no BUY)
    // ----------------------------------------------------------------------

    @Test
    @DisplayName("BOND tam kapanış: closed=true satır emit edilir (initialCost/realized% dolu)")
    void bond_fullClose_emitsClosedRow() {
        EvdsBondInstrument inst = new EvdsBondInstrument();
        inst.setCategory(BondCategory.FIXED_COUPON_BOND);
        lenient().when(evdsBondService.getEvdsBondDetail(anyString())).thenReturn(inst);

        // BUY 100 @ 90 → cost 90 ; SELL 100 @ 110 → proceeds 110 ; realized = 20
        PortfolioTransaction buy = makeTx(TransactionType.BUY, "TRT_CLOSE", AssetType.BOND,
                "100", "90", "0", "2026-01-01T10:00:00");
        PortfolioTransaction sell = makeTx(TransactionType.SELL, "TRT_CLOSE", AssetType.BOND,
                "100", "110", "0", "2026-06-01T10:00:00");

        PortfolioHoldingsBuilder.BuildResult res = builder.buildWithClosed(List.of(buy, sell));

        // Açık holding yok ama BOND için kapalı satır listede emit edilir.
        assertThat(res.holdings()).hasSize(1);
        PortfolioHoldingResponse closedRow = res.holdings().get(0);
        assertThat(closedRow.isClosed()).isTrue();
        assertThat(closedRow.getAssetType()).isEqualTo(AssetType.BOND);
        assertThat(closedRow.getTotalQuantity()).isEqualByComparingTo("0");
        assertThat(closedRow.getCurrency()).isEqualTo("TRY");
        assertThat(closedRow.getInitialCost()).isEqualByComparingTo("90");
        assertThat(closedRow.getRealizedGainLoss()).isEqualByComparingTo("20"); // 110 - 90
        // realized% = 20 / 90 * 100 = 22.22
        assertThat(closedRow.getRealizedGainLossPercent()).isEqualByComparingTo("22.22");
        assertThat(closedRow.getLastTransactionDate())
                .isEqualTo(LocalDateTime.of(2026, 6, 1, 10, 0));

        // closedRealized listesi de doldu (anySell + realized != 0)
        assertThat(res.closedRealized()).hasSize(1);
        assertThat(res.closedRealized().get(0).currency()).isEqualTo("TRY");
    }

    @Test
    @DisplayName("BOND kupon-only (BUY yok): kapalı satır emit edilmez (totalBuyCostBasis=0)")
    void bond_couponOnlyNoBuy_doesNotEmitClosedRow() {
        EvdsBondInstrument inst = new EvdsBondInstrument();
        inst.setCategory(BondCategory.FIXED_COUPON_BOND);
        lenient().when(evdsBondService.getEvdsBondDetail(anyString())).thenReturn(inst);

        // Sadece COUPON_INCOME → openQty 0, anySell true (kupon), totalBuyCostBasis=0
        PortfolioTransaction coupon = makeTx(TransactionType.COUPON_INCOME, "TRT_CPNONLY", AssetType.BOND,
                "40", "1", "0", "2026-06-01T10:00:00");

        PortfolioHoldingsBuilder.BuildResult res = builder.buildWithClosed(List.of(coupon));

        // BUY geçmişi yok → kapalı satır listeyi kirletmez.
        assertThat(res.holdings()).isEmpty();
        // closedRealized: anySell + realized(40) != 0 → yakalanır
        assertThat(res.closedRealized()).hasSize(1);
        assertThat(res.closedRealized().get(0).realizedGainLoss()).isEqualByComparingTo("40");
        assertThat(res.closedRealized().get(0).assetType()).isEqualTo(AssetType.BOND);
        verifyNoInteractions(enrichmentPort);
    }

    // ----------------------------------------------------------------------
    // Zero-quantity-only BUY → openQty 0, no sell → fully skipped (no closed, no holding)
    // ----------------------------------------------------------------------

    @Test
    @DisplayName("Tek BUY qty=0: openQty 0 + anySell false → tamamen atlanır (boş sonuç)")
    void zeroQuantityBuy_skippedEntirely() {
        PortfolioTransaction buy = makeTx(TransactionType.BUY, "THYAO", AssetType.STOCK,
                "0", "100", "0", "2026-01-01T10:00:00");

        PortfolioHoldingsBuilder.BuildResult res = builder.buildWithClosed(List.of(buy));

        assertThat(res.holdings()).isEmpty();
        assertThat(res.closedRealized()).isEmpty(); // anySell false → not captured
        verifyNoInteractions(enrichmentPort);
    }

    @Test
    @DisplayName("Tam kapanış realized=0 (alış=satış fiyatı): closedRealized'a girmez (signum 0)")
    void fullClose_zeroRealized_notCaptured() {
        // BUY 10 @ 100, SELL 10 @ 100 → realized 0 → signum 0 → atlanır
        PortfolioTransaction buy = makeTx(TransactionType.BUY, "THYAO.IS", AssetType.STOCK,
                "10", "100", "0", "2026-01-01T10:00:00");
        PortfolioTransaction sell = makeTx(TransactionType.SELL, "THYAO.IS", AssetType.STOCK,
                "10", "100", "0", "2026-02-01T10:00:00");

        PortfolioHoldingsBuilder.BuildResult res = builder.buildWithClosed(List.of(buy, sell));

        assertThat(res.holdings()).isEmpty();
        assertThat(res.closedRealized()).isEmpty(); // realized signum 0
    }

    // ----------------------------------------------------------------------
    // guessNativeCurrency: STOCK non-.IS → USD ; non-STOCK closed → TRY
    // ----------------------------------------------------------------------

    @Test
    @DisplayName("guessNativeCurrency: STOCK non-.IS kapanış → USD")
    void closedRealized_nonIsStock_currencyUsd() {
        PortfolioTransaction buy = makeTx(TransactionType.BUY, "AAPL", AssetType.STOCK,
                "10", "100", "0", "2026-01-01T10:00:00");
        PortfolioTransaction sell = makeTx(TransactionType.SELL, "AAPL", AssetType.STOCK,
                "10", "150", "0", "2026-02-01T10:00:00");

        PortfolioHoldingsBuilder.BuildResult res = builder.buildWithClosed(List.of(buy, sell));

        assertThat(res.closedRealized()).hasSize(1);
        assertThat(res.closedRealized().get(0).currency()).isEqualTo("USD"); // no .IS
        assertThat(res.closedRealized().get(0).realizedGainLoss()).isEqualByComparingTo("500");
    }

    @Test
    @DisplayName("guessNativeCurrency: non-STOCK (GOLD) kapanış → TRY")
    void closedRealized_gold_currencyTry() {
        PortfolioTransaction buy = makeTx(TransactionType.BUY, "XAU", AssetType.GOLD,
                "5", "2000", "0", "2026-01-01T10:00:00");
        PortfolioTransaction sell = makeTx(TransactionType.SELL, "XAU", AssetType.GOLD,
                "5", "2200", "0", "2026-02-01T10:00:00");

        PortfolioHoldingsBuilder.BuildResult res = builder.buildWithClosed(List.of(buy, sell));

        assertThat(res.closedRealized()).hasSize(1);
        assertThat(res.closedRealized().get(0).currency()).isEqualTo("TRY");
    }

    // ----------------------------------------------------------------------
    // FUTURE: direction + multiplier/dirSign on realized (open & closed)
    // ----------------------------------------------------------------------

    @Test
    @DisplayName("FUTURE USD-kote (XAUUSD) kapalı: realized × multiplier × dir × USD/TRY kuru ile TL'ye çevrilir")
    void future_usdQuoted_closedRealized_convertedToTry() {
        // XAUUSD: USD-kote, multiplier=1. SELL ile tam kapanır.
        ViopContractSpec spec = new ViopContractSpec("XAUUSD",
                ViopContractSpec.AssetClass.PRECIOUS_METAL,
                new BigDecimal("1"), new BigDecimal("0.10"),
                "USD", ViopContractSpec.SettlementType.CASH);
        lenient().when(specRegistry.resolveOrFallback(anyString())).thenReturn(spec);
        // Canlı USD/TRY satış kuru = 40 (sabit, deterministik test).
        lenient().when(currencyConverter.rateToTry("USD")).thenReturn(new BigDecimal("40"));

        // BUY 1 @ 2000, SELL 1 @ 2100 → spot realized 1*(2100-2000) = 100 (USD)
        PortfolioTransaction buy = makeFuture(TransactionType.BUY, "F_XAUUSD0625", "LONG",
                "1", "2000", "0", "2026-01-01T10:00:00");
        PortfolioTransaction sell = makeFuture(TransactionType.SELL, "F_XAUUSD0625", "LONG",
                "1", "2100", "0", "2026-02-01T10:00:00");

        PortfolioHoldingsBuilder.BuildResult res = builder.buildWithClosed(List.of(buy, sell));

        assertThat(res.closedRealized()).hasSize(1);
        // realized = 100 (USD spot) × multiplier 1 × dirSign +1 × fx 40 = 4000 TL
        assertThat(res.closedRealized().get(0).realizedGainLoss()).isEqualByComparingTo("4000");
    }

    @Test
    @DisplayName("FUTURE TL-kote (XU030) kapalı: USD/TRY kuru UYGULANMAZ (fx=1, davranış değişmez)")
    void future_tryQuoted_closedRealized_noFxApplied() {
        ViopContractSpec spec = new ViopContractSpec("XU030",
                ViopContractSpec.AssetClass.INDEX,
                new BigDecimal("10"), new BigDecimal("0.20"),
                "TRY", ViopContractSpec.SettlementType.CASH);
        lenient().when(specRegistry.resolveOrFallback(anyString())).thenReturn(spec);
        // Kur stub'lansa bile TL-kote'de çağrılmamalı; çağrılsa bile sonuç değişmemeli.
        lenient().when(currencyConverter.rateToTry("USD")).thenReturn(new BigDecimal("40"));

        // BUY 10 @ 100, SELL 10 @ 110 → spot realized 10*(110-100)=100
        PortfolioTransaction buy = makeFuture(TransactionType.BUY, "F_XU0300625", "LONG",
                "10", "100", "0", "2026-01-01T10:00:00");
        PortfolioTransaction sell = makeFuture(TransactionType.SELL, "F_XU0300625", "LONG",
                "10", "110", "0", "2026-02-01T10:00:00");

        PortfolioHoldingsBuilder.BuildResult res = builder.buildWithClosed(List.of(buy, sell));

        assertThat(res.closedRealized()).hasSize(1);
        // realized = 100 × multiplier 10 × dirSign +1 × fx 1 (TL-kote) = 1000 (40 UYGULANMAZ)
        assertThat(res.closedRealized().get(0).realizedGainLoss()).isEqualByComparingTo("1000");
    }

    @Test
    @DisplayName("FUTURE LONG açık + kısmi SELL: realized × multiplier, pct ÷ marginRate, viopDirection=LONG")
    void future_longPartialSell_scalesByMultiplierAndMargin() {
        // multiplier=10, marginRate=0.20
        ViopContractSpec spec = new ViopContractSpec("XU030",
                ViopContractSpec.AssetClass.INDEX,
                new BigDecimal("10"), new BigDecimal("0.20"),
                "TRY", ViopContractSpec.SettlementType.CASH);
        lenient().when(specRegistry.resolveOrFallback(anyString())).thenReturn(spec);

        // BUY 10 @ 100 → spot cost 1000 ; SELL 4 @ 150 → spot realized 4*(150-100)=200
        PortfolioTransaction buy = makeFuture(TransactionType.BUY, "F_XU0300625", "LONG",
                "10", "100", "0", "2026-01-01T10:00:00");
        PortfolioTransaction sell = makeFuture(TransactionType.SELL, "F_XU0300625", "LONG",
                "4", "150", "0", "2026-02-01T10:00:00");

        PortfolioHoldingResponse h = builder.build(List.of(buy, sell)).get(0);

        assertThat(h.getAssetType()).isEqualTo(AssetType.FUTURE);
        assertThat(h.getViopDirection()).isEqualTo("LONG");
        // realized = spotRealized 200 × multiplier 10 × dirSign +1 = 2000
        assertThat(h.getRealizedGainLoss()).isEqualByComparingTo("2000");
        // pctSpot = 200 / soldCostBasis(400) * 100 = 50 ; pctFinal = 50 × +1 / 0.20 = 250
        assertThat(h.getRealizedGainLossPercent()).isEqualByComparingTo("250");
    }

    @Test
    @DisplayName("FUTURE SHORT açık + kısmi SELL: dirSign=-1 → realized işareti döner")
    void future_shortPartialSell_dirSignNegates() {
        ViopContractSpec spec = new ViopContractSpec("USDTRY",
                ViopContractSpec.AssetClass.FX,
                new BigDecimal("1000"), new BigDecimal("0.10"),
                "TRY", ViopContractSpec.SettlementType.CASH);
        lenient().when(specRegistry.resolveOrFallback(anyString())).thenReturn(spec);

        // SHORT: spot realized 4*(150-100)=200, dirSign -1 → realized = 200 * 1000 * -1 = -200000
        PortfolioTransaction buy = makeFuture(TransactionType.BUY, "F_USDTRY0625", "short",
                "10", "100", "0", "2026-01-01T10:00:00");
        PortfolioTransaction sell = makeFuture(TransactionType.SELL, "F_USDTRY0625", "short",
                "4", "150", "0", "2026-02-01T10:00:00");

        PortfolioHoldingResponse h = builder.build(List.of(buy, sell)).get(0);

        assertThat(h.getViopDirection()).isEqualTo("SHORT");
        assertThat(h.getRealizedGainLoss()).isEqualByComparingTo("-200000");
        // pct = 50 × -1 / 0.10 = -500
        assertThat(h.getRealizedGainLossPercent()).isEqualByComparingTo("-500");
    }

    @Test
    @DisplayName("FUTURE direction null/blank → LONG default + viopDirection=LONG")
    void future_nullDirection_defaultsLong() {
        ViopContractSpec spec = new ViopContractSpec("AKBNK",
                ViopContractSpec.AssetClass.SINGLE_STOCK,
                new BigDecimal("100"), new BigDecimal("0.15"),
                "TRY", ViopContractSpec.SettlementType.PHYSICAL);
        lenient().when(specRegistry.resolveOrFallback(anyString())).thenReturn(spec);

        // direction null → LONG; tek BUY açık (anySell false → realized null)
        PortfolioTransaction buy = makeFuture(TransactionType.BUY, "F_AKBNK0625", null,
                "5", "100", "0", "2026-01-01T10:00:00");

        PortfolioHoldingResponse h = builder.build(List.of(buy)).get(0);

        assertThat(h.getViopDirection()).isEqualTo("LONG");
        assertThat(h.getRealizedGainLoss()).isNull(); // anySell false
    }

    @Test
    @DisplayName("FUTURE SHORT tam kapanış: closedRealized realized × multiplier × -1")
    void future_shortFullClose_closedRealizedConverted() {
        ViopContractSpec spec = new ViopContractSpec("XU030",
                ViopContractSpec.AssetClass.INDEX,
                new BigDecimal("10"), new BigDecimal("0.20"),
                "TRY", ViopContractSpec.SettlementType.CASH);
        lenient().when(specRegistry.resolveOrFallback(anyString())).thenReturn(spec);

        // BUY 10 @ 100, SELL 10 @ 90 → spot realized 10*(90-100) = -100
        PortfolioTransaction buy = makeFuture(TransactionType.BUY, "F_XU0300625", "SHORT",
                "10", "100", "0", "2026-01-01T10:00:00");
        PortfolioTransaction sell = makeFuture(TransactionType.SELL, "F_XU0300625", "SHORT",
                "10", "90", "0", "2026-02-01T10:00:00");

        PortfolioHoldingsBuilder.BuildResult res = builder.buildWithClosed(List.of(buy, sell));

        assertThat(res.holdings()).isEmpty(); // FUTURE kapalı satır emit etmez
        assertThat(res.closedRealized()).hasSize(1);
        // realized = -100 × 10 × -1 = 1000 (SHORT düşüşten kazanç)
        assertThat(res.closedRealized().get(0).realizedGainLoss()).isEqualByComparingTo("1000");
        assertThat(res.closedRealized().get(0).assetType()).isEqualTo(AssetType.FUTURE);
        assertThat(res.closedRealized().get(0).currency()).isEqualTo("TRY");
    }

    // ----------------------------------------------------------------------
    // skipInlineEnrich = true → enrich() NOT called inline
    // ----------------------------------------------------------------------

    @Test
    @DisplayName("buildWithClosed(skipInlineEnrich=true): inline enrich çağrılmaz")
    void buildWithClosed_skipInlineEnrich_noEnrich() {
        PortfolioTransaction buy = makeTx(TransactionType.BUY, "THYAO", AssetType.STOCK,
                "10", "100", "0", "2026-01-01T10:00:00");

        PortfolioHoldingsBuilder.BuildResult res = builder.buildWithClosed(List.of(buy), true);

        assertThat(res.holdings()).hasSize(1);
        verify(enrichmentPort, never()).enrich(any());
    }

    @Test
    @DisplayName("enrichHolding: porta delege eder")
    void enrichHolding_delegatesToPort() {
        PortfolioHoldingResponse h = new PortfolioHoldingResponse();
        builder.enrichHolding(h);
        verify(enrichmentPort, times(1)).enrich(h);
    }

    // ----------------------------------------------------------------------
    // snapshotLots: BUY with null txDate → lot.buyDate null → atılır (lot listesi boş)
    // ----------------------------------------------------------------------

    @Test
    @DisplayName("snapshotLots: null txDate'li BUY → lot null-buyDate olduğu için snapshot'tan atılır")
    void build_nullDateBuy_lotSkippedInSnapshot() {
        // txDate null → firstBuyDate null, lot.buyDate null → snapshotLots filtreler
        PortfolioTransaction buy = makeTx(TransactionType.BUY, "THYAO", AssetType.STOCK,
                "10", "100", "0", null);

        PortfolioHoldingResponse h = builder.build(List.of(buy)).get(0);

        assertThat(h.getTotalQuantity()).isEqualByComparingTo("10");
        assertThat(h.getOpenCostLots()).isEmpty(); // null buyDate → atıldı
        assertThat(h.getFirstBuyDate()).isNull();
    }

    // ----------------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------------

    private static PortfolioTransaction makeTx(TransactionType type, String symbol,
                                               AssetType assetType, String qty, String price,
                                               String commission, String date) {
        PortfolioTransaction t = new PortfolioTransaction();
        t.setId(UUID.randomUUID());
        t.setSymbol(symbol);
        t.setAssetType(assetType);
        t.setTransactionType(type);
        t.setQuantity(new BigDecimal(qty));
        t.setPrice(new BigDecimal(price));
        t.setCommission(new BigDecimal(commission));
        if (date != null) {
            t.setTransactionDate(LocalDateTime.parse(date));
        }
        return t;
    }

    private static PortfolioTransaction makeFuture(TransactionType type, String symbol, String direction,
                                                   String qty, String price, String commission, String date) {
        PortfolioTransaction t = makeTx(type, symbol, AssetType.FUTURE, qty, price, commission, date);
        t.setDirection(direction);
        return t;
    }
}
