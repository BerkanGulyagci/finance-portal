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
import java.time.Month;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PortfolioHoldingsBuilderTest {

    private HoldingMarketEnrichmentPort enrichmentPort;
    private PortfolioHoldingsBuilder builder;

    @BeforeEach
    void setUp() {
        enrichmentPort = mock(HoldingMarketEnrichmentPort.class);
        builder = new PortfolioHoldingsBuilder(enrichmentPort);
    }

    // ------------------------------ boş / null girdi ------------------------------

    @Test
    @DisplayName("build: null transactions → boş liste")
    void build_nullTransactions_returnsEmpty() {
        assertThat(builder.build(null)).isEmpty();
        verifyNoInteractions(enrichmentPort);
    }

    @Test
    @DisplayName("build: boş transactions → boş liste")
    void build_emptyTransactions_returnsEmpty() {
        assertThat(builder.build(List.of())).isEmpty();
        verifyNoInteractions(enrichmentPort);
    }

    // ------------------------------ BUY tek başına ------------------------------

    @Test
    @DisplayName("build: tek BUY → tek holding, qty/cost doğru, P&L yok")
    void build_singleBuy_singleHolding() {
        PortfolioTransaction buy = tx(BUY("THYAO", AssetType.STOCK,
                "10", "100", "0", "2026-05-20T10:00:00"));

        List<PortfolioHoldingResponse> out = builder.build(List.of(buy));

        assertThat(out).hasSize(1);
        PortfolioHoldingResponse h = out.get(0);
        assertThat(h.getSymbol()).isEqualTo("THYAO");
        assertThat(h.getAssetType()).isEqualTo(AssetType.STOCK);
        assertThat(h.getTotalQuantity()).isEqualByComparingTo("10");
        assertThat(h.getTotalCost()).isEqualByComparingTo("1000");          // 10 * 100
        assertThat(h.getAverageCost()).isEqualByComparingTo("100");
        assertThat(h.getRealizedGainLoss()).isNull();
        assertThat(h.getRealizedGainLossPercent()).isNull();
        assertThat(h.getFirstBuyDate()).isEqualTo(LocalDateTime.of(2026, Month.MAY, 20, 10, 0));
        verify(enrichmentPort).enrich(any(PortfolioHoldingResponse.class));
    }

    @Test
    @DisplayName("build: BUY komisyonu maliyete eklenir")
    void build_buyCommission_addedToCost() {
        PortfolioTransaction buy = tx(BUY("THYAO", AssetType.STOCK,
                "10", "100", "5.50", "2026-05-20T10:00:00"));

        PortfolioHoldingResponse h = builder.build(List.of(buy)).get(0);

        // 10*100 + 5.50 = 1005.50
        assertThat(h.getTotalCost()).isEqualByComparingTo("1005.50");
        assertThat(h.getAverageCost()).isEqualByComparingTo("100.55");      // 1005.50 / 10
    }

    // ------------------------------ Birden çok BUY ------------------------------

    @Test
    @DisplayName("build: aynı sembolden 2 BUY → quantity toplanır, ortalama maliyet ağırlıklı")
    void build_multipleBuys_averagesCost() {
        // BUY 10@100, BUY 10@200 → 20 adet, 3000 maliyet, avg 150
        PortfolioTransaction b1 = tx(BUY("THYAO", AssetType.STOCK,
                "10", "100", "0", "2026-05-20T10:00:00"));
        PortfolioTransaction b2 = tx(BUY("THYAO", AssetType.STOCK,
                "10", "200", "0", "2026-05-21T10:00:00"));

        PortfolioHoldingResponse h = builder.build(List.of(b1, b2)).get(0);

        assertThat(h.getTotalQuantity()).isEqualByComparingTo("20");
        assertThat(h.getTotalCost()).isEqualByComparingTo("3000");
        assertThat(h.getAverageCost()).isEqualByComparingTo("150");
        assertThat(h.getFirstBuyDate()).isEqualTo(LocalDateTime.of(2026, 5, 20, 10, 0));
    }

    // ------------------------------ Kısmi SELL ------------------------------

    @Test
    @DisplayName("build: BUY sonra kısmi SELL → qty düşer, realized P&L kaydedilir")
    void build_partialSell_recordsRealizedPnl() {
        // BUY 10@100, SELL 4@150 → kalan 6 adet, realized = 4*(150-100) = 200
        PortfolioTransaction b = tx(BUY("THYAO", AssetType.STOCK,
                "10", "100", "0", "2026-05-20T10:00:00"));
        PortfolioTransaction s = tx(SELL("THYAO", AssetType.STOCK,
                "4", "150", "0", "2026-05-22T10:00:00"));

        PortfolioHoldingResponse h = builder.build(List.of(b, s)).get(0);

        assertThat(h.getTotalQuantity()).isEqualByComparingTo("6");
        assertThat(h.getRealizedGainLoss()).isEqualByComparingTo("200");
        // % = (200 / (4*100)) * 100 = 50%
        assertThat(h.getRealizedGainLossPercent()).isEqualByComparingTo("50");
    }

    // ------------------------------ Tam SELL → holding kapalı ------------------------------

    @Test
    @DisplayName("build: BUY + tam SELL → açık pozisyon kalmaz, holding listede yok")
    void build_fullSell_holdingExcluded() {
        PortfolioTransaction b = tx(BUY("THYAO", AssetType.STOCK,
                "10", "100", "0", "2026-05-20T10:00:00"));
        PortfolioTransaction s = tx(SELL("THYAO", AssetType.STOCK,
                "10", "150", "0", "2026-05-22T10:00:00"));

        List<PortfolioHoldingResponse> out = builder.build(List.of(b, s));

        assertThat(out).isEmpty();
        // Holding listeye girmediği için enrichment de çağrılmaz
        verifyNoInteractions(enrichmentPort);
    }

    // ------------------------------ Aşırı satış (overflow) ------------------------------

    @Test
    @DisplayName("build: açık qty olmadan SELL → IllegalStateException")
    void build_sellWithoutPosition_throws() {
        PortfolioTransaction s = tx(SELL("THYAO", AssetType.STOCK,
                "5", "100", "0", "2026-05-22T10:00:00"));

        assertThatThrownBy(() -> builder.build(List.of(s)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SELL with non-positive open quantity");
    }

    // ------------------------------ Birden çok asset type ------------------------------

    @Test
    @DisplayName("build: farklı asset type'lar → ayrı holding'ler (aynı sembol bile olsa)")
    void build_differentAssetTypes_separateHoldings() {
        // Aynı sembol farklı tipte (örn. AKBNK stock + AKBNK fund) → 2 ayrı holding
        PortfolioTransaction stockBuy = tx(BUY("AKBNK", AssetType.STOCK,
                "10", "50", "0", "2026-05-20T10:00:00"));
        PortfolioTransaction goldBuy = tx(BUY("XAU", AssetType.GOLD,
                "5", "2000", "0", "2026-05-21T10:00:00"));

        List<PortfolioHoldingResponse> out = builder.build(List.of(stockBuy, goldBuy));

        assertThat(out).hasSize(2);
        assertThat(out).extracting(PortfolioHoldingResponse::getAssetType)
                .containsExactlyInAnyOrder(AssetType.STOCK, AssetType.GOLD);
    }

    // ------------------------------ FUND scale ------------------------------

    @Test
    @DisplayName("build: FUND → para alanları 8 ondalık scale (fonlar yüksek hassasiyetli)")
    void build_fund_usesEightDecimalScale() {
        PortfolioTransaction fundBuy = tx(BUY("TIE", AssetType.FUND,
                "1234.56789012", "1.23456789", "0", "2026-05-20T10:00:00"));

        PortfolioHoldingResponse h = builder.build(List.of(fundBuy)).get(0);

        assertThat(h.getAssetType()).isEqualTo(AssetType.FUND);
        // averageCost ve totalCost FUND için 8 ondalık scale
        assertThat(h.getAverageCost().scale()).isEqualTo(8);
        assertThat(h.getTotalCost().scale()).isEqualTo(8);
    }

    // ------------------------------ lastTransactionDate ------------------------------

    @Test
    @DisplayName("build: lastTransactionDate = en son işlemin tarihi (tarihe göre sıralı)")
    void build_lastTransactionDate_isMostRecent() {
        PortfolioTransaction early = tx(BUY("THYAO", AssetType.STOCK,
                "10", "100", "0", "2026-05-20T10:00:00"));
        PortfolioTransaction late = tx(BUY("THYAO", AssetType.STOCK,
                "5", "120", "0", "2026-05-25T15:30:00"));

        // Karışık sırayla geç ver, sıralama dahili
        PortfolioHoldingResponse h = builder.build(List.of(late, early)).get(0);

        assertThat(h.getLastTransactionDate())
                .isEqualTo(LocalDateTime.of(2026, 5, 25, 15, 30));
    }

    // ------------------------------ enrichment port ------------------------------

    @Test
    @DisplayName("build: her holding için enrichment port bir kez çağrılır")
    void build_callsEnrichment_perHolding() {
        PortfolioTransaction b1 = tx(BUY("THYAO", AssetType.STOCK,
                "10", "100", "0", "2026-05-20T10:00:00"));
        PortfolioTransaction b2 = tx(BUY("XAU", AssetType.GOLD,
                "5", "2000", "0", "2026-05-20T10:00:00"));

        builder.build(List.of(b1, b2));

        verify(enrichmentPort, times(2)).enrich(any(PortfolioHoldingResponse.class));
    }

    // ============================================================================
    // Helper'lar — readable test fixtures
    // ============================================================================

    private static PortfolioTransaction tx(PortfolioTransaction t) {
        return t;
    }

    private static PortfolioTransaction BUY(String symbol, AssetType type,
                                            String qty, String price, String commission,
                                            String date) {
        return makeTx(TransactionType.BUY, symbol, type, qty, price, commission, date);
    }

    private static PortfolioTransaction SELL(String symbol, AssetType type,
                                             String qty, String price, String commission,
                                             String date) {
        return makeTx(TransactionType.SELL, symbol, type, qty, price, commission, date);
    }

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
        t.setTransactionDate(LocalDateTime.parse(date));
        return t;
    }
}
