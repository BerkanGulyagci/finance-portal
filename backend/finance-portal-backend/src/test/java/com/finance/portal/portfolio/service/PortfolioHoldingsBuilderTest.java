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
    private com.finance.portal.market.application.bond.evds.EvdsBondService evdsBondService;
    private PortfolioHoldingsBuilder builder;

    @BeforeEach
    void setUp() {
        enrichmentPort = mock(HoldingMarketEnrichmentPort.class);
        evdsBondService = mock(com.finance.portal.market.application.bond.evds.EvdsBondService.class);
        var specRegistry = mock(com.finance.portal.portfolio.application.viop.spec.ViopContractSpecRegistry.class);
        // Mevcut testlerin hepsi STOCK/BOND/FUND için → FUTURE yolu vurulmaz, lenient stub yeter.
        org.mockito.Mockito.lenient()
                .when(specRegistry.resolveOrFallback(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(inv -> com.finance.portal.portfolio.application.viop.spec.ViopContractSpec
                        .fallback(inv.getArgument(0)));
        builder = new PortfolioHoldingsBuilder(enrichmentPort, evdsBondService, specRegistry);
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
    @DisplayName("build: açık qty olmadan SELL → throw ETMEZ, SELL atlanır (warn+skip sağlamlık)")
    void build_sellWithoutPosition_skips() {
        // Açık pozisyon yokken gelen SELL (yanlış-tarihli/tutarsız) tüm portföyü 500'lemek yerine
        // ATLANIR + uyarı loglanır (d453258 sağlamlaştırma). Bu tek hatalı işlem holding üretmez.
        PortfolioTransaction s = tx(SELL("THYAO", AssetType.STOCK,
                "5", "100", "0", "2026-05-22T10:00:00"));
        List<PortfolioTransaction> txs = List.of(s);

        List<PortfolioHoldingResponse> holdings = builder.build(txs);

        // Exception fırlamaz; over-sell SELL atlandığı için holding listesi boş.
        assertThat(holdings).isEmpty();
        // Holding oluşmadığı için enrichment de çağrılmaz.
        verifyNoInteractions(enrichmentPort);
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
    // Kapatılmış pozisyonların realized K/Z toplamı (buildWithClosed)
    // ============================================================================

    @Test
    @DisplayName("buildWithClosed: tam kapanış → holding yok ama closedRealized=5000")
    void buildWithClosed_fullClose_capturesRealizedInClosedList() {
        // BUY 100 × 250 + SELL 100 × 300 → realized = 100 × (300 - 250) = 5000
        PortfolioTransaction buy = tx(BUY("THYAO.IS", AssetType.STOCK,
                "100", "250", "0", "2025-01-01T10:00:00"));
        PortfolioTransaction sell = tx(SELL("THYAO.IS", AssetType.STOCK,
                "100", "300", "0", "2025-02-01T10:00:00"));

        PortfolioHoldingsBuilder.BuildResult res = builder.buildWithClosed(List.of(buy, sell));

        // Açık holding listesi davranışı bozulmadı: kapatılmış pozisyon listede yok.
        assertThat(res.holdings()).isEmpty();
        verifyNoInteractions(enrichmentPort);

        // Closed realized listesinde tam kapanan pozisyonun K/Z'si yakalanmış.
        assertThat(res.closedRealized()).hasSize(1);
        PortfolioHoldingsBuilder.ClosedPositionRealized closed = res.closedRealized().get(0);
        assertThat(closed.symbol()).isEqualTo("THYAO.IS");
        assertThat(closed.assetType()).isEqualTo(AssetType.STOCK);
        assertThat(closed.realizedGainLoss()).isEqualByComparingTo("5000");
        assertThat(closed.currency()).isEqualTo("TRY"); // .IS → TRY
    }

    @Test
    @DisplayName("buildWithClosed: kısmi SELL → açık holding kalır, closedRealized boş")
    void buildWithClosed_partialSell_holdingKeepsRealizedClosedListEmpty() {
        // BUY 100 × 250 + SELL 40 × 300 → kalan 60, realized = 40 × (300 - 250) = 2000
        PortfolioTransaction buy = tx(BUY("THYAO.IS", AssetType.STOCK,
                "100", "250", "0", "2025-01-01T10:00:00"));
        PortfolioTransaction sell = tx(SELL("THYAO.IS", AssetType.STOCK,
                "40", "300", "0", "2025-02-01T10:00:00"));

        PortfolioHoldingsBuilder.BuildResult res = builder.buildWithClosed(List.of(buy, sell));

        // Açık holding mevcut.
        assertThat(res.holdings()).hasSize(1);
        PortfolioHoldingResponse h = res.holdings().get(0);
        assertThat(h.getTotalQuantity()).isEqualByComparingTo("60");
        assertThat(h.getTotalCost()).isEqualByComparingTo("15000");      // 60 × 250
        assertThat(h.getAverageCost()).isEqualByComparingTo("250");
        assertThat(h.getRealizedGainLoss()).isEqualByComparingTo("2000"); // holding satırında korunur

        // Kapatılmış pozisyon yok.
        assertThat(res.closedRealized()).isEmpty();
    }

    @Test
    @DisplayName("buildWithClosed: komisyonlu tam kapanış → realized net (komisyon dahil)")
    void buildWithClosed_fullCloseWithCommission_realizedHonoursCommission() {
        // BUY 100 × 250 (comm 10) → cost = 25.010
        // SELL 100 × 300 (comm 8)  → proceeds = 30.000 - 8 = 29.992
        // realized = 29.992 - 25.010 = 4982
        PortfolioTransaction buy = tx(BUY("THYAO.IS", AssetType.STOCK,
                "100", "250", "10", "2025-01-01T10:00:00"));
        PortfolioTransaction sell = tx(SELL("THYAO.IS", AssetType.STOCK,
                "100", "300", "8", "2025-02-01T10:00:00"));

        PortfolioHoldingsBuilder.BuildResult res = builder.buildWithClosed(List.of(buy, sell));

        assertThat(res.holdings()).isEmpty();
        assertThat(res.closedRealized()).hasSize(1);
        assertThat(res.closedRealized().get(0).realizedGainLoss())
                .isEqualByComparingTo("4982");
    }

    // ============================================================================
    // Lot tracking — Reel K/Z + What-if için cost lot dağılımı
    // ============================================================================

    @Test
    @DisplayName("build: çoklu BUY → her lot kendi alış tarihi+cost ile holding'de saklanır")
    void build_multipleBuy_lotsTrackedSeparately() {
        // BUY 100×250 (2024-01-01) → cost 25.000
        // BUY  50×280 (2025-01-01) → cost 14.000
        PortfolioTransaction b1 = tx(BUY("THYAO.IS", AssetType.STOCK,
                "100", "250", "0", "2024-01-01T10:00:00"));
        PortfolioTransaction b2 = tx(BUY("THYAO.IS", AssetType.STOCK,
                "50", "280", "0", "2025-01-01T10:00:00"));

        PortfolioHoldingResponse h = builder.build(List.of(b1, b2)).get(0);

        // 2 lot olmalı
        assertThat(h.getOpenCostLots()).hasSize(2);
        // Lot 1: 2024-01-01, cost 25.000
        assertThat(h.getOpenCostLots().get(0).buyDate())
                .isEqualTo(java.time.LocalDate.of(2024, 1, 1));
        assertThat(h.getOpenCostLots().get(0).cost()).isEqualByComparingTo("25000");
        // Lot 2: 2025-01-01, cost 14.000
        assertThat(h.getOpenCostLots().get(1).buyDate())
                .isEqualTo(java.time.LocalDate.of(2025, 1, 1));
        assertThat(h.getOpenCostLots().get(1).cost()).isEqualByComparingTo("14000");
    }

    @Test
    @DisplayName("build: kısmi SELL → tüm lotlar pre-sell oranında orantısal küçülür")
    void build_partialSell_lotsShrinkProportionally() {
        // BUY 100×250 + BUY 50×280 → openQty=150, openCost=39.000, lots: [25.000, 14.000]
        // SELL 30 → remainingFraction = (150-30)/150 = 0.8
        //   Lot 1: 25.000 × 0.8 = 20.000
        //   Lot 2: 14.000 × 0.8 = 11.200
        //   Σ = 31.200 = openCost (39.000 × 0.8) ✓ invariant korunur
        PortfolioTransaction b1 = tx(BUY("THYAO.IS", AssetType.STOCK,
                "100", "250", "0", "2024-01-01T10:00:00"));
        PortfolioTransaction b2 = tx(BUY("THYAO.IS", AssetType.STOCK,
                "50", "280", "0", "2025-01-01T10:00:00"));
        PortfolioTransaction s = tx(SELL("THYAO.IS", AssetType.STOCK,
                "30", "300", "0", "2025-06-01T10:00:00"));

        PortfolioHoldingResponse h = builder.build(List.of(b1, b2, s)).get(0);

        assertThat(h.getTotalCost()).isEqualByComparingTo("31200"); // 39.000 × 0.8
        assertThat(h.getOpenCostLots()).hasSize(2);
        assertThat(h.getOpenCostLots().get(0).cost()).isEqualByComparingTo("20000");
        assertThat(h.getOpenCostLots().get(1).cost()).isEqualByComparingTo("11200");
        // average cost invariant: avgCost değişmedi
        assertThat(h.getAverageCost()).isEqualByComparingTo("260"); // 31.200/120 = 260
    }

    @Test
    @DisplayName("build: tam SELL → kapatılır, lot listesi boşalır (holding listesinde yok)")
    void build_fullSell_lotsCleared() {
        PortfolioTransaction b = tx(BUY("THYAO.IS", AssetType.STOCK,
                "100", "250", "0", "2024-01-01T10:00:00"));
        PortfolioTransaction s = tx(SELL("THYAO.IS", AssetType.STOCK,
                "100", "300", "0", "2025-06-01T10:00:00"));

        // Açık holding yok ama lot mantığı doğru çalıştığı için closedRealized kaydı var
        PortfolioHoldingsBuilder.BuildResult res = builder.buildWithClosed(List.of(b, s));
        assertThat(res.holdings()).isEmpty();
        assertThat(res.closedRealized()).hasSize(1);
    }

    @Test
    @DisplayName("build: SELL sonrası yeni BUY → eski lotlar küçülmüş, yeni lot tam fiyatla eklenir")
    void build_sellThenBuy_newLotAddedFreshOldShrunk() {
        // BUY 100×250 + SELL 40 + BUY 20×320
        // SELL 40 → remainingFraction = 60/100 = 0.6
        //   Lot 1: 25.000 × 0.6 = 15.000
        // BUY 20×320 → cost 6.400 → Lot 2 (yeni)
        // Toplam openCost = 15.000 + 6.400 = 21.400, qty=80, avg=267.5
        PortfolioTransaction b1 = tx(BUY("THYAO.IS", AssetType.STOCK,
                "100", "250", "0", "2024-01-01T10:00:00"));
        PortfolioTransaction s = tx(SELL("THYAO.IS", AssetType.STOCK,
                "40", "300", "0", "2025-01-01T10:00:00"));
        PortfolioTransaction b2 = tx(BUY("THYAO.IS", AssetType.STOCK,
                "20", "320", "0", "2025-06-01T10:00:00"));

        PortfolioHoldingResponse h = builder.build(List.of(b1, s, b2)).get(0);

        assertThat(h.getOpenCostLots()).hasSize(2);
        // Lot 1 küçülmüş 15.000
        assertThat(h.getOpenCostLots().get(0).buyDate())
                .isEqualTo(java.time.LocalDate.of(2024, 1, 1));
        assertThat(h.getOpenCostLots().get(0).cost()).isEqualByComparingTo("15000");
        // Lot 2 tam fiyatla eklenmiş
        assertThat(h.getOpenCostLots().get(1).buyDate())
                .isEqualTo(java.time.LocalDate.of(2025, 6, 1));
        assertThat(h.getOpenCostLots().get(1).cost()).isEqualByComparingTo("6400");
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
