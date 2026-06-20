package com.finance.portal.portfolio.service;

import com.finance.portal.common.domain.AssetType;
import com.finance.portal.portfolio.application.port.HoldingMarketEnrichmentPort;
import com.finance.portal.portfolio.domain.PortfolioTransaction;
import com.finance.portal.portfolio.domain.TransactionType;
import com.finance.portal.portfolio.presentation.dto.PortfolioHoldingResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link TransactionType#COUPON_INCOME} davranışı için {@link PortfolioHoldingsBuilder}
 * characterization testleri.
 */
class PortfolioHoldingsBuilderCouponIncomeTest {

    private PortfolioHoldingsBuilder builder;

    @BeforeEach
    void setUp() {
        HoldingMarketEnrichmentPort noopEnrichment = h -> { /* no-op */ };
        com.finance.portal.market.application.bond.evds.EvdsBondService evdsMock =
                org.mockito.Mockito.mock(com.finance.portal.market.application.bond.evds.EvdsBondService.class);
        var specMock = org.mockito.Mockito.mock(
                com.finance.portal.portfolio.application.viop.spec.ViopContractSpecRegistry.class);
        var ccMock = org.mockito.Mockito.mock(
                com.finance.portal.portfolio.service.PortfolioCurrencyConverter.class);
        builder = new PortfolioHoldingsBuilder(noopEnrichment, evdsMock, specMock, ccMock);
    }

    private static PortfolioTransaction tx(TransactionType type, BigDecimal qty, BigDecimal price,
                                            LocalDateTime when) {
        PortfolioTransaction t = new PortfolioTransaction();
        t.setId(UUID.randomUUID());
        t.setSymbol("TRT240227T17");
        t.setAssetType(AssetType.BOND);
        t.setTransactionType(type);
        t.setQuantity(qty);
        t.setPrice(price);
        t.setCommission(BigDecimal.ZERO);
        t.setTransactionDate(when);
        return t;
    }

    @Test
    @DisplayName("COUPON_INCOME tek başına: realized = amount, openQty=0 → açık holding yok")
    void couponIncome_only_noOpenPosition() {
        // 300 TL kupon geliri kayıt edilmiş, ama BUY yok → açık pozisyon yok
        PortfolioTransaction coupon = tx(TransactionType.COUPON_INCOME,
                new BigDecimal("300"), BigDecimal.ONE, LocalDateTime.now().minusDays(1));

        List<PortfolioHoldingResponse> holdings = builder.build(List.of(coupon));

        // openQty=0 olduğu için holding listesinde gözükmez (eski davranış aynı kalır)
        assertThat(holdings).isEmpty();
    }

    @Test
    @DisplayName("BUY + COUPON_INCOME: açık pozisyon değişmez, realized = kupon tutarı")
    void buyPlusCoupon_realizedIsCouponAmount() {
        // 10.000 TL nominal BUY @ 90 → cost = 9.000
        PortfolioTransaction buy = tx(TransactionType.BUY,
                new BigDecimal("10000"), new BigDecimal("90"), LocalDateTime.now().minusMonths(6));
        // 300 TL kupon geliri
        PortfolioTransaction coupon = tx(TransactionType.COUPON_INCOME,
                new BigDecimal("300"), BigDecimal.ONE, LocalDateTime.now().minusDays(1));

        List<PortfolioHoldingResponse> holdings = builder.build(List.of(buy, coupon));

        assertThat(holdings).hasSize(1);
        PortfolioHoldingResponse h = holdings.get(0);
        // Açık nominal = 10.000 (kupon dokunmuyor)
        assertThat(h.getTotalQuantity()).isEqualByComparingTo("10000");
        // Cost = 10.000 × 90 / 100 = 9.000 (kupon eklemiyor)
        assertThat(h.getTotalCost()).isEqualByComparingTo("9000");
        // Realized = 300 (kupon tutarı)
        assertThat(h.getRealizedGainLoss()).isEqualByComparingTo("300");
    }

    @Test
    @DisplayName("Çoklu kupon: realized cumulative toplanır")
    void multipleCoupons_cumulativeRealized() {
        PortfolioTransaction buy = tx(TransactionType.BUY,
                new BigDecimal("10000"), new BigDecimal("90"), LocalDateTime.now().minusYears(1));
        PortfolioTransaction coupon1 = tx(TransactionType.COUPON_INCOME,
                new BigDecimal("300"), BigDecimal.ONE, LocalDateTime.now().minusMonths(6));
        PortfolioTransaction coupon2 = tx(TransactionType.COUPON_INCOME,
                new BigDecimal("310.50"), BigDecimal.ONE, LocalDateTime.now().minusDays(7));

        List<PortfolioHoldingResponse> holdings = builder.build(List.of(buy, coupon1, coupon2));

        assertThat(holdings).hasSize(1);
        // Realized = 300 + 310.50 = 610.50
        assertThat(holdings.get(0).getRealizedGainLoss()).isEqualByComparingTo("610.50");
        // Cost ve openQty değişmez
        assertThat(holdings.get(0).getTotalQuantity()).isEqualByComparingTo("10000");
        assertThat(holdings.get(0).getTotalCost()).isEqualByComparingTo("9000");
    }

    @Test
    @DisplayName("BOND vade itfası: tam SELL sonrası kapalı satır holding listesinde kalır")
    void bond_fullyClosed_keepsRowWithClosedFlag() {
        // 10.000 nominal BUY @ 90 → cost = 9.000
        PortfolioTransaction buy = tx(TransactionType.BUY,
                new BigDecimal("10000"), new BigDecimal("90"), LocalDateTime.now().minusYears(1));
        // Vade itfası: 10.000 nominal SELL @ 100 (par) → proceeds = 10.000, sold_cost = 9.000, pnl = +1.000
        PortfolioTransaction itfa = tx(TransactionType.SELL,
                new BigDecimal("10000"), new BigDecimal("100"), LocalDateTime.now().minusDays(1));
        // Vade öncesi 300 TL kupon alınmış
        PortfolioTransaction coupon = tx(TransactionType.COUPON_INCOME,
                new BigDecimal("300"), BigDecimal.ONE, LocalDateTime.now().minusMonths(6));

        List<PortfolioHoldingResponse> holdings = builder.build(List.of(buy, coupon, itfa));

        // Kapalı satır kalmalı (BOND için özel davranış)
        assertThat(holdings).hasSize(1);
        PortfolioHoldingResponse h = holdings.get(0);
        assertThat(h.isClosed()).isTrue();
        assertThat(h.getTotalQuantity()).isEqualByComparingTo("0");
        assertThat(h.getInitialCost()).isEqualByComparingTo("9000");
        // Realized = itfa pnl (1.000) + kupon (300) = 1.300
        assertThat(h.getRealizedGainLoss()).isEqualByComparingTo("1300");
        // Realized % = 1300 / 9000 × 100 = 14.44
        assertThat(h.getRealizedGainLossPercent()).isEqualByComparingTo("14.44");
    }

    @Test
    @DisplayName("STOCK tam SELL sonrası eski davranış: satır listede gözükmez")
    void stock_fullyClosed_dropsRow() {
        PortfolioTransaction buy = new PortfolioTransaction();
        buy.setId(UUID.randomUUID());
        buy.setSymbol("ASELS.IS");
        buy.setAssetType(AssetType.STOCK);
        buy.setTransactionType(TransactionType.BUY);
        buy.setQuantity(new BigDecimal("100"));
        buy.setPrice(new BigDecimal("50"));
        buy.setCommission(BigDecimal.ZERO);
        buy.setTransactionDate(LocalDateTime.now().minusMonths(6));

        PortfolioTransaction sell = new PortfolioTransaction();
        sell.setId(UUID.randomUUID());
        sell.setSymbol("ASELS.IS");
        sell.setAssetType(AssetType.STOCK);
        sell.setTransactionType(TransactionType.SELL);
        sell.setQuantity(new BigDecimal("100"));
        sell.setPrice(new BigDecimal("60"));
        sell.setCommission(BigDecimal.ZERO);
        sell.setTransactionDate(LocalDateTime.now().minusDays(1));

        List<PortfolioHoldingResponse> holdings = builder.build(List.of(buy, sell));
        // BOND dışı kapalı pozisyon listede gözükmez (eski davranış)
        assertThat(holdings).isEmpty();
    }

    @Test
    @DisplayName("BUY + SELL kısmi + COUPON: realized = sell pnl + kupon")
    void buyPartialSellPlusCoupon_realizedSums() {
        // 10.000 nominal BUY @ 90 → cost = 9.000
        PortfolioTransaction buy = tx(TransactionType.BUY,
                new BigDecimal("10000"), new BigDecimal("90"), LocalDateTime.now().minusYears(1));
        // 4.000 nominal SELL @ 95 → proceeds = 3.800, sold_cost = 3.600, pnl = +200
        PortfolioTransaction sell = tx(TransactionType.SELL,
                new BigDecimal("4000"), new BigDecimal("95"), LocalDateTime.now().minusMonths(3));
        // 300 TL kupon
        PortfolioTransaction coupon = tx(TransactionType.COUPON_INCOME,
                new BigDecimal("300"), BigDecimal.ONE, LocalDateTime.now().minusDays(1));

        List<PortfolioHoldingResponse> holdings = builder.build(List.of(buy, sell, coupon));

        assertThat(holdings).hasSize(1);
        PortfolioHoldingResponse h = holdings.get(0);
        // Açık nominal = 6.000
        assertThat(h.getTotalQuantity()).isEqualByComparingTo("6000");
        // Cost (kalan) = 9.000 − 3.600 = 5.400
        assertThat(h.getTotalCost()).isEqualByComparingTo("5400");
        // Realized = sell_pnl (200) + kupon (300) = 500
        assertThat(h.getRealizedGainLoss()).isEqualByComparingTo("500");
    }
}
