package com.finance.portal.portfolio.service.enrich;

import com.finance.portal.market.application.fx.model.FxHistory;
import com.finance.portal.market.application.fx.model.FxHistoryPoint;
import com.finance.portal.market.application.fx.model.FxLatestRates;
import com.finance.portal.market.application.fx.model.FxRateItem;
import com.finance.portal.market.application.service.MarketFxService;
import com.finance.portal.portfolio.presentation.dto.PortfolioHoldingResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Characterization: {@link FxHoldingEnricher} eski
 * {@code PortfolioHoldingMarketEnricher.enrichFxHolding(...)} ile aynı davranmalı.
 * Mocklanan: {@link MarketFxService}.
 */
@ExtendWith(MockitoExtension.class)
class FxHoldingEnricherTest {

    @Mock MarketFxService marketFxService;

    private FxHoldingEnricher enricher;

    @BeforeEach
    void setUp() {
        enricher = new FxHoldingEnricher(marketFxService);
    }

    // ---- helpers ------------------------------------------------------------

    private static FxLatestRates latest(String symbol, BigDecimal sell, BigDecimal buy, int unit) {
        FxRateItem item = new FxRateItem(symbol, buy, sell, unit);
        return new FxLatestRates("TCMB", "interbank", "TRY",
                "2026-05-26T17:00:00", List.of(item));
    }

    private static FxHistory history(BigDecimal... closes) {
        List<FxHistoryPoint> pts = new ArrayList<>();
        for (int i = 0; i < closes.length; i++) {
            pts.add(new FxHistoryPoint(String.format("2025-%02d-%02d", 1 + i / 28, 1 + (i % 28)), closes[i]));
        }
        FxHistory h = new FxHistory();
        h.setPoints(pts);
        return h;
    }

    private static PortfolioHoldingResponse holding(String symbol, BigDecimal qty, BigDecimal cost) {
        PortfolioHoldingResponse h = new PortfolioHoldingResponse();
        h.setSymbol(symbol);
        h.setTotalQuantity(qty);
        h.setTotalCost(cost);
        return h;
    }

    // ---- core path ---------------------------------------------------------

    @Test
    @DisplayName("enrich: mv = price × qty, currency = TRY, name = SYMBOL/TRY")
    void enrich_corePath() {
        when(marketFxService.getTcmbLatestRates("USD")).thenReturn(
                latest("USD", new BigDecimal("35.5"), new BigDecimal("35.4"), 1));
        when(marketFxService.getFxHistory(any(), any())).thenReturn(null);

        PortfolioHoldingResponse h = holding("usd", new BigDecimal("100"), new BigDecimal("3000"));
        enricher.enrich(h);

        assertThat(h.getCurrentPrice()).isEqualByComparingTo("35.5");
        // 35.5 × 100 = 3550.0000; 3550 − 3000 = 550.0000
        assertThat(h.getMarketValue()).isEqualByComparingTo("3550.0000");
        assertThat(h.getProfitLoss()).isEqualByComparingTo("550.0000");
        assertThat(h.getCurrency()).isEqualTo("TRY");
        assertThat(h.getName()).isEqualTo("USD/TRY");
        assertThat(h.getAsOf()).isNotNull();
    }

    @Test
    @DisplayName("enrich: sell yoksa buy kullanılır")
    void enrich_sellNull_usesBuy() {
        when(marketFxService.getTcmbLatestRates("EUR")).thenReturn(
                latest("EUR", null, new BigDecimal("40"), 1));
        when(marketFxService.getFxHistory(any(), any())).thenReturn(null);

        PortfolioHoldingResponse h = holding("EUR", BigDecimal.ONE, BigDecimal.ZERO);
        enricher.enrich(h);

        assertThat(h.getCurrentPrice()).isEqualByComparingTo("40");
    }

    @Test
    @DisplayName("enrich: hem sell hem buy null → IllegalStateException")
    void enrich_noPrice_throws() {
        when(marketFxService.getTcmbLatestRates("USD")).thenReturn(latest("USD", null, null, 1));

        PortfolioHoldingResponse h = holding("USD", BigDecimal.ONE, BigDecimal.ZERO);
        assertThatThrownBy(() -> enricher.enrich(h))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FX price unavailable");
    }

    @Test
    @DisplayName("enrich: symbol latest rates listesinde yoksa IllegalStateException")
    void enrich_symbolNotFound_throws() {
        when(marketFxService.getTcmbLatestRates("XXX")).thenReturn(
                new FxLatestRates("TCMB", "interbank", "TRY", "2026-05-26", List.of()));

        PortfolioHoldingResponse h = holding("XXX", BigDecimal.ONE, BigDecimal.ZERO);
        assertThatThrownBy(() -> enricher.enrich(h))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FX rate not found");
    }

    @Test
    @DisplayName("enrich: unit > 1 (JPY gibi) — fiyat unit'e bölünür")
    void enrich_unitBigger_dividedToOneUnit() {
        when(marketFxService.getTcmbLatestRates("JPY")).thenReturn(
                latest("JPY", new BigDecimal("23.45"), null, 100));   // 100 JPY = 23.45 TRY
        when(marketFxService.getFxHistory(any(), any())).thenReturn(null);

        PortfolioHoldingResponse h = holding("JPY", new BigDecimal("1000"), BigDecimal.ZERO);
        enricher.enrich(h);

        // 23.45 / 100 = 0.2345 (6 ondalık) — 1 JPY için TRY karşılığı
        assertThat(h.getCurrentPrice()).isEqualByComparingTo("0.234500");
        // 0.2345 × 1000 = 234.5000
        assertThat(h.getMarketValue()).isEqualByComparingTo("234.5000");
    }

    // ---- history-based metrics --------------------------------------------

    @Test
    @DisplayName("enrich: history varsa change/changePercent son iki kapanıştan, 52w min/max, MA20/MA50")
    void enrich_historyFillsChangeAndRangeAndMa() {
        when(marketFxService.getTcmbLatestRates("USD")).thenReturn(
                latest("USD", new BigDecimal("35"), null, 1));

        BigDecimal[] closes = new BigDecimal[30];
        for (int i = 0; i < 30; i++) {
            closes[i] = new BigDecimal(i + 1);   // 1..30
        }
        when(marketFxService.getFxHistory("USD", "1Y")).thenReturn(history(closes));

        PortfolioHoldingResponse h = holding("USD", BigDecimal.ONE, BigDecimal.ZERO);
        enricher.enrich(h);

        // son iki 30 ve 29 → change 1.0000, %change 100/29 = 3.45
        assertThat(h.getChange()).isEqualByComparingTo("1.0000");
        assertThat(h.getChangePercent()).isEqualByComparingTo("3.45");
        assertThat(h.getFiftyTwoWeekLow()).isEqualByComparingTo("1");
        assertThat(h.getFiftyTwoWeekHigh()).isEqualByComparingTo("30");
        // MA20 = ortalama(closes[10..29]) = ortalama(11..30) = 20.5
        assertThat(h.getMa20()).isEqualByComparingTo("20.5");
    }

    @Test
    @DisplayName("enrich: 22 günlük seri → 1M getiri hesaplanmaz (>= daysBack şartı), 3M yok")
    void enrich_returnPeriods_notEnoughData() {
        when(marketFxService.getTcmbLatestRates("USD")).thenReturn(
                latest("USD", new BigDecimal("35"), null, 1));
        BigDecimal[] closes = new BigDecimal[22];
        for (int i = 0; i < 22; i++) {
            closes[i] = new BigDecimal(i + 1);
        }
        when(marketFxService.getFxHistory(any(), any())).thenReturn(history(closes));

        PortfolioHoldingResponse h = holding("USD", BigDecimal.ONE, BigDecimal.ZERO);
        enricher.enrich(h);

        // n=22, daysBack=22 → n <= daysBack → null
        assertThat(h.getReturnOneMonth()).isNull();
        assertThat(h.getReturnThreeMonths()).isNull();
    }

    @Test
    @DisplayName("enrich: history fırlatırsa core fields yine doldurulur (silent skip)")
    void enrich_historyThrows_isSwallowed() {
        when(marketFxService.getTcmbLatestRates("USD")).thenReturn(
                latest("USD", new BigDecimal("35"), null, 1));
        when(marketFxService.getFxHistory(any(), any())).thenThrow(new RuntimeException("TCMB down"));

        PortfolioHoldingResponse h = holding("USD", BigDecimal.ONE, BigDecimal.ZERO);
        enricher.enrich(h);

        assertThat(h.getCurrentPrice()).isEqualByComparingTo("35");
        assertThat(h.getMa20()).isNull();
        assertThat(h.getChange()).isNull();
    }

    @Test
    @DisplayName("enrich: 23+ günlük seri → 1M getiri hesaplanır")
    void enrich_returnOneMonth_computed() {
        when(marketFxService.getTcmbLatestRates("USD")).thenReturn(
                latest("USD", new BigDecimal("35"), null, 1));
        BigDecimal[] closes = new BigDecimal[24];
        // 22 işlem günü önceki close[1] = 2; son close[23] = 24 → değişim = (24-2)/2 * 100 = 1100.00
        for (int i = 0; i < 24; i++) {
            closes[i] = new BigDecimal(i + 1);
        }
        when(marketFxService.getFxHistory(any(), any())).thenReturn(history(closes));

        PortfolioHoldingResponse h = holding("USD", BigDecimal.ONE, BigDecimal.ZERO);
        enricher.enrich(h);

        assertThat(h.getReturnOneMonth()).isEqualByComparingTo("1100.00");
    }
}
