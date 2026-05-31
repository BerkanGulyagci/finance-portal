package com.finance.portal.portfolio.service;

import com.finance.portal.market.application.economy.InflationDeflatorService;
import com.finance.portal.market.application.economy.model.EconomySeriesPoint;
import com.finance.portal.portfolio.presentation.dto.PortfolioHoldingResponse;
import com.finance.portal.portfolio.presentation.dto.PortfolioResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class PortfolioRealReturnEnricherTest {

    private InflationDeflatorService deflator;
    private PortfolioCurrencyConverter currencyConverter;
    private PortfolioRealReturnEnricher enricher;

    @BeforeEach
    void setUp() {
        deflator = mock(InflationDeflatorService.class);
        currencyConverter = mock(PortfolioCurrencyConverter.class);
        // currencyConverter — TRY identity; USD için sahte 30 kur
        when(currencyConverter.toTry(any(BigDecimal.class), anyString()))
                .thenAnswer(inv -> {
                    BigDecimal a = inv.getArgument(0);
                    String c = inv.getArgument(1);
                    if (a == null) return null;
                    if (c == null || "TRY".equalsIgnoreCase(c) || "TL".equalsIgnoreCase(c)) return a;
                    return a.multiply(new BigDecimal("30"));
                });
        when(currencyConverter.toTry(isNull(), anyString())).thenReturn(null);

        // Default: deflator TÜFE serisi var, US CPI yok
        EconomySeriesPoint p = new EconomySeriesPoint("2026-01-01", new BigDecimal("100"), 0L);
        when(deflator.tufeSeries()).thenReturn(List.of(p));
        when(deflator.usCpiSeries()).thenReturn(List.of());

        enricher = new PortfolioRealReturnEnricher(deflator, currencyConverter);
    }

    // ------------------------------ no-op paths ------------------------------

    @Test
    @DisplayName("apply: null response → sessizce çıkar")
    void apply_nullResponse_noop() {
        enricher.apply(null);
        verifyNoInteractions(deflator);
    }

    @Test
    @DisplayName("apply: null holdings → sessizce çıkar")
    void apply_nullHoldings_noop() {
        PortfolioResponse r = new PortfolioResponse();
        r.setHoldings(null);

        enricher.apply(r);

        verifyNoInteractions(deflator);
    }

    @Test
    @DisplayName("apply: boş holdings → sessizce çıkar")
    void apply_emptyHoldings_noop() {
        PortfolioResponse r = new PortfolioResponse();
        r.setHoldings(List.of());

        enricher.apply(r);

        verifyNoInteractions(deflator);
    }

    @Test
    @DisplayName("apply: TÜFE serisi boş → tüm reel alanlar temizlenir, totals null")
    void apply_emptyTufeSeries_clearsEverything() {
        when(deflator.tufeSeries()).thenReturn(List.of());
        PortfolioHoldingResponse h = holding("THYAO", "1000", "1500", "TRY",
                LocalDate.of(2026, 1, 1));
        // Önceden set edilmiş cache değerleri olduğunu varsay
        h.setRealProfitLoss(new BigDecimal("999"));
        h.setRealProfitLossPercent(new BigDecimal("50"));
        PortfolioResponse r = new PortfolioResponse();
        r.setHoldings(List.of(h));
        r.setTotalRealProfitLoss(new BigDecimal("999"));

        enricher.apply(r);

        assertThat(h.getRealProfitLoss()).isNull();
        assertThat(h.getRealProfitLossPercent()).isNull();
        assertThat(r.getTotalRealProfitLoss()).isNull();
        assertThat(r.getTotalRealProfitLossPercent()).isNull();
    }

    @Test
    @DisplayName("apply: deflator.tufeSeries() exception → temizlik yapılır (graceful)")
    void apply_tufeSeriesThrows_clears() {
        when(deflator.tufeSeries()).thenThrow(new RuntimeException("EVDS down"));
        PortfolioHoldingResponse h = holding("THYAO", "1000", "1500", "TRY",
                LocalDate.of(2026, 1, 1));
        PortfolioResponse r = new PortfolioResponse();
        r.setHoldings(List.of(h));

        enricher.apply(r);  // exception fırlatmamalı

        assertThat(h.getRealProfitLoss()).isNull();
        assertThat(r.getTotalRealProfitLoss()).isNull();
    }

    // ------------------------------ TL holding ------------------------------

    @Test
    @DisplayName("apply: TL holding, %20 enflasyon faktörü → reel kâr/zarar hesaplanır")
    void apply_tlHolding_appliesRealReturn() {
        PortfolioHoldingResponse h = holding("THYAO", "1000", "1500", "TRY",
                LocalDate.of(2026, 1, 1));
        PortfolioResponse r = new PortfolioResponse();
        r.setHoldings(List.of(h));
        // Faktör 1.20 (%20 enflasyon) → reel cost 1200, marketValue 1500 → real PL 300
        when(deflator.cumulativeFactor(any(), eq(LocalDate.of(2026, 1, 1)), any()))
                .thenReturn(Optional.of(new BigDecimal("1.20")));

        enricher.apply(r);

        assertThat(h.getRealProfitLoss()).isEqualByComparingTo("300");
        // pct = (1500/1200 - 1) * 100 = 25%
        assertThat(h.getRealProfitLossPercent()).isEqualByComparingTo("25.00");
        // inflationSincePercent = (1.20 - 1) * 100 = 20%
        assertThat(h.getInflationSincePercent()).isEqualByComparingTo("20.00");
        assertThat(h.getInflationSource()).isEqualTo("TÜFE");
        // TL alım gücü çerçevesi de aynı
        assertThat(h.getRealProfitLossTry()).isEqualByComparingTo("300");
        // Portföy toplamı: realPL = 300
        assertThat(r.getTotalRealProfitLoss()).isEqualByComparingTo("300.0000");
    }

    @Test
    @DisplayName("apply: TL alias ('TL') de TRY ile aynı çerçeveye gider")
    void apply_tlAlias_treatedAsTry() {
        PortfolioHoldingResponse h = holding("X", "100", "150", "TL",
                LocalDate.of(2026, 1, 1));
        PortfolioResponse r = new PortfolioResponse();
        r.setHoldings(List.of(h));
        when(deflator.cumulativeFactor(any(), any(), any())).thenReturn(Optional.of(new BigDecimal("1.10")));

        enricher.apply(r);

        assertThat(h.getInflationSource()).isEqualTo("TÜFE");
    }

    @Test
    @DisplayName("apply: USD holding + ABD CPI serisi var → kendi para çerçevesi 'ABD TÜFE' kaynağı")
    void apply_usdHolding_usesUsCpi() {
        when(deflator.usCpiSeries()).thenReturn(List.of(
                new EconomySeriesPoint("2026-01-01", new BigDecimal("250"), 0L)));
        // Native (USD): ABD CPI; TL çerçeve için TÜFE
        when(deflator.cumulativeFactor(any(), any(), any())).thenReturn(Optional.of(new BigDecimal("1.03")));
        PortfolioHoldingResponse h = holding("AAPL", "100", "120", "USD",
                LocalDate.of(2026, 1, 1));
        PortfolioResponse r = new PortfolioResponse();
        r.setHoldings(List.of(h));

        enricher.apply(r);

        assertThat(h.getInflationSource()).isEqualTo("ABD TÜFE");
        // costTl = 100×30 = 3000, mvTl = 120×30 = 3600 → realCostTl = 3000×1.03 = 3090, realPlTl = 510
        assertThat(h.getRealProfitLossTry()).isEqualByComparingTo("510");
    }

    @Test
    @DisplayName("apply: USDT (USD alias) → ABD CPI kullanılır")
    void apply_usdtAlias_treatedAsUsd() {
        when(deflator.usCpiSeries()).thenReturn(List.of(
                new EconomySeriesPoint("2026-01-01", new BigDecimal("250"), 0L)));
        when(deflator.cumulativeFactor(any(), any(), any())).thenReturn(Optional.of(new BigDecimal("1.02")));
        PortfolioHoldingResponse h = holding("BTC", "1000", "1200", "USDT",
                LocalDate.of(2026, 1, 1));
        PortfolioResponse r = new PortfolioResponse();
        r.setHoldings(List.of(h));

        enricher.apply(r);

        assertThat(h.getInflationSource()).isEqualTo("ABD TÜFE");
    }

    // ------------------------------ edge cases ------------------------------

    @Test
    @DisplayName("apply: firstBuyDate yok → kendi çerçevesi atlanır, ama hala TL toplam'a (factor=1) katılır")
    void apply_noFirstBuyDate_skipsNativeFrame() {
        PortfolioHoldingResponse h = holding("THYAO", "1000", "1100", "TRY", null);
        PortfolioResponse r = new PortfolioResponse();
        r.setHoldings(List.of(h));

        enricher.apply(r);

        assertThat(h.getRealProfitLoss()).isNull();         // kendi çerçeve eklenmedi (date yok)
        assertThat(h.getInflationSource()).isNull();
        // Ama TL toplam'a faktör=1 ile katıldı: realPL = 1100-1000 = 100
        assertThat(r.getTotalRealProfitLoss()).isEqualByComparingTo("100.0000");
    }

    @Test
    @DisplayName("apply: enflasyon faktörü Optional.empty → singleLot fallback ile factor=1, satır da reel kolonu alır")
    void apply_factorEmpty_singleLotFallback() {
        PortfolioHoldingResponse h = holding("THYAO", "1000", "1100", "TRY",
                LocalDate.of(2026, 1, 1));
        PortfolioResponse r = new PortfolioResponse();
        r.setHoldings(List.of(h));
        when(deflator.cumulativeFactor(any(), any(), any())).thenReturn(Optional.empty());

        enricher.apply(r);

        // P1 Bug 4 fix: faktör boş olsa bile fallbackBuyDate varsa factor=1 kullanılıp
        // satır reel kolonu hesaplanır (kısa vadeli/yeni pozisyonların boş kalmaması için).
        assertThat(h.getRealProfitLoss()).isEqualByComparingTo("100.0000");
        assertThat(h.getRealProfitLossTry()).isEqualByComparingTo("100.0000");
        // Toplama da factor=1 ile katılır → totalRealPL = 100
        assertThat(r.getTotalRealProfitLoss()).isEqualByComparingTo("100.0000");
    }

    @Test
    @DisplayName("apply: maliyet ≤ 0 → o holding tamamen atlanır")
    void apply_zeroOrNegativeCost_skipped() {
        PortfolioHoldingResponse zero = holding("A", "0", "100", "TRY", LocalDate.of(2026, 1, 1));
        PortfolioHoldingResponse neg = holding("B", "-5", "10", "TRY", LocalDate.of(2026, 1, 1));
        PortfolioResponse r = new PortfolioResponse();
        r.setHoldings(List.of(zero, neg));

        enricher.apply(r);

        assertThat(zero.getRealProfitLoss()).isNull();
        assertThat(neg.getRealProfitLoss()).isNull();
        assertThat(r.getTotalRealProfitLoss()).isNull();
    }

    @Test
    @DisplayName("apply: birden çok holding → toplam reel K/Z ağırlıklı %'si doğru")
    void apply_multipleHoldings_aggregatesTotal() {
        when(deflator.cumulativeFactor(any(), any(), any())).thenReturn(Optional.of(new BigDecimal("1.10")));
        PortfolioHoldingResponse a = holding("A", "1000", "1300", "TRY", LocalDate.of(2026, 1, 1));
        PortfolioHoldingResponse b = holding("B", "2000", "2400", "TRY", LocalDate.of(2026, 1, 1));
        PortfolioResponse r = new PortfolioResponse();
        r.setHoldings(List.of(a, b));

        enricher.apply(r);

        // a: realCost 1100, mv 1300 → realPL 200
        // b: realCost 2200, mv 2400 → realPL 200
        // toplam realPL 400, realCost 3300 → % = 400/3300 = 12.12
        assertThat(r.getTotalRealProfitLoss()).isEqualByComparingTo("400.0000");
        assertThat(r.getTotalRealProfitLossPercent()).isEqualByComparingTo("12.12");
    }

    // ============================================================================
    // Çoklu BUY (lot-bazlı) enflasyon hesabı — her lot kendi alış tarihinden
    // ============================================================================

    @Test
    @DisplayName("apply: çoklu BUY — her lot kendi tarihinden enflasyonla şişer (firstBuyDate kullanmaz)")
    void apply_multipleBuyLots_useEachLotDate() {
        // Cost 1000 = lot1 (eski tarih, %50) + lot2 (yeni tarih, %10)
        LocalDate oldDate = LocalDate.of(2024, 1, 1);
        LocalDate newDate = LocalDate.of(2025, 6, 1);
        PortfolioHoldingResponse h = holding("THYAO.IS", "1000", "1500", "TRY", oldDate);
        h.setOpenCostLots(List.of(
                new PortfolioHoldingResponse.CostLot(oldDate, new BigDecimal("400")),
                new PortfolioHoldingResponse.CostLot(newDate, new BigDecimal("600"))
        ));
        PortfolioResponse r = new PortfolioResponse();
        r.setHoldings(List.of(h));

        when(deflator.cumulativeFactor(any(), eq(oldDate), any()))
                .thenReturn(Optional.of(new BigDecimal("1.50"))); // eski lot: %50 enflasyon
        when(deflator.cumulativeFactor(any(), eq(newDate), any()))
                .thenReturn(Optional.of(new BigDecimal("1.10"))); // yeni lot: %10 enflasyon

        enricher.apply(r);

        // Beklenen realCost = 400×1.50 + 600×1.10 = 600 + 660 = 1260
        // realPL = MV - realCost = 1500 - 1260 = 240
        assertThat(h.getRealProfitLoss()).isEqualByComparingTo("240");
        // realPL% = (1500/1260 - 1) × 100 ≈ 19.05
        assertThat(h.getRealProfitLossPercent()).isEqualByComparingTo("19.05");
        // weightedFactor = 1260/1000 = 1.26 → inflationSincePercent = 26.00
        assertThat(h.getInflationSincePercent()).isEqualByComparingTo("26.00");
    }

    @Test
    @DisplayName("apply: lots boş ise eski firstBuyDate davranışı korunur (geri uyumlu)")
    void apply_noLots_fallbackToFirstBuyDate() {
        PortfolioHoldingResponse h = holding("THYAO.IS", "1000", "1500", "TRY",
                LocalDate.of(2026, 1, 1));
        h.setOpenCostLots(null); // veya boş — eski tx data
        PortfolioResponse r = new PortfolioResponse();
        r.setHoldings(List.of(h));
        when(deflator.cumulativeFactor(any(), eq(LocalDate.of(2026, 1, 1)), any()))
                .thenReturn(Optional.of(new BigDecimal("1.20")));

        enricher.apply(r);

        assertThat(h.getRealProfitLoss()).isEqualByComparingTo("300"); // 1500 − 1000×1.20
        assertThat(h.getInflationSincePercent()).isEqualByComparingTo("20.00");
    }

    @Test
    @DisplayName("apply: tek lot çoklu lot ile aynı sonuç verir (tek BUY senaryosu bozulmaz)")
    void apply_singleLot_matchesNoLotsBehavior() {
        LocalDate buyDate = LocalDate.of(2025, 6, 1);
        PortfolioHoldingResponse h = holding("THYAO.IS", "1000", "1500", "TRY", buyDate);
        h.setOpenCostLots(List.of(
                new PortfolioHoldingResponse.CostLot(buyDate, new BigDecimal("1000"))
        ));
        PortfolioResponse r = new PortfolioResponse();
        r.setHoldings(List.of(h));
        when(deflator.cumulativeFactor(any(), eq(buyDate), any()))
                .thenReturn(Optional.of(new BigDecimal("1.20")));

        enricher.apply(r);

        // Tek lot: realCost = 1000×1.20 = 1200, realPL = 1500−1200 = 300
        assertThat(h.getRealProfitLoss()).isEqualByComparingTo("300");
        assertThat(h.getInflationSincePercent()).isEqualByComparingTo("20.00");
    }

    @Test
    @DisplayName("apply: lots içinden biri için faktör yoksa, o lot reel=nominal kabul edilir")
    void apply_lotWithoutFactor_treatedAsNominal() {
        LocalDate oldDate = LocalDate.of(2024, 1, 1);
        LocalDate newDate = LocalDate.of(2025, 6, 1);
        PortfolioHoldingResponse h = holding("THYAO.IS", "1000", "1500", "TRY", oldDate);
        h.setOpenCostLots(List.of(
                new PortfolioHoldingResponse.CostLot(oldDate, new BigDecimal("400")),
                new PortfolioHoldingResponse.CostLot(newDate, new BigDecimal("600"))
        ));
        PortfolioResponse r = new PortfolioResponse();
        r.setHoldings(List.of(h));

        when(deflator.cumulativeFactor(any(), eq(oldDate), any()))
                .thenReturn(Optional.of(new BigDecimal("1.50")));
        when(deflator.cumulativeFactor(any(), eq(newDate), any()))
                .thenReturn(Optional.empty()); // newDate için seri yok

        enricher.apply(r);

        // realCost = 400×1.50 + 600×1.00 (nominal fallback) = 1200
        assertThat(h.getRealProfitLoss()).isEqualByComparingTo("300"); // 1500-1200
    }

    // ------------------------------ helper ------------------------------

    private static PortfolioHoldingResponse holding(String symbol, String cost,
                                                    String mv, String currency,
                                                    LocalDate buyDate) {
        PortfolioHoldingResponse h = new PortfolioHoldingResponse();
        h.setSymbol(symbol);
        h.setTotalCost(new BigDecimal(cost));
        h.setMarketValue(new BigDecimal(mv));
        h.setCurrency(currency);
        h.setFirstBuyDate(buyDate != null ? buyDate.atStartOfDay() : null);
        return h;
    }
}
