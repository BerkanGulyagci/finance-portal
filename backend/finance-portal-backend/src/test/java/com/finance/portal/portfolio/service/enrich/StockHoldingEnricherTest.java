package com.finance.portal.portfolio.service.enrich;

import com.finance.portal.market.application.stock.StockChartResponse;
import com.finance.portal.market.application.stock.StockDetail;
import com.finance.portal.market.application.stock.StockQueryService;
import com.finance.portal.market.application.stock.StockSummary;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Characterization: {@link StockHoldingEnricher} eski
 * {@code PortfolioHoldingMarketEnricher.enrichStockOrFutureHolding(...)} ile aynı
 * davranmalı — mv/pl scale, summary pass-through, MA20/MA50, currency fallback,
 * name fallback ve symbol uppercase'leme.
 */
@ExtendWith(MockitoExtension.class)
class StockHoldingEnricherTest {

    @Mock StockQueryService stockQueryService;

    private StockHoldingEnricher enricher;

    @BeforeEach
    void setUp() {
        enricher = new StockHoldingEnricher(stockQueryService);
    }

    // ---- helpers ------------------------------------------------------------

    private static StockSummary summary(BigDecimal price, String currency, String name) {
        StockSummary s = new StockSummary();
        s.setPrice(price);
        s.setCurrency(currency);
        s.setName(name);
        s.setChange(new BigDecimal("1.5"));
        s.setChangePercent(new BigDecimal("0.5"));
        s.setVolume(123_456L);
        s.setDayHigh(new BigDecimal("310"));
        s.setDayLow(new BigDecimal("295"));
        s.setAsOf("2026-05-26T17:00:00");
        return s;
    }

    private static StockDetail detail(StockSummary s, String name, String currency,
                                      BigDecimal h52, BigDecimal l52) {
        StockDetail d = new StockDetail();
        d.setSummary(s);
        d.setName(name);
        d.setCurrency(currency);
        d.setFiftyTwoWeekHigh(h52);
        d.setFiftyTwoWeekLow(l52);
        return d;
    }

    private static StockChartResponse chart(BigDecimal... closes) {
        StockChartResponse c = new StockChartResponse();
        c.setClosePrices(List.of(closes));
        return c;
    }

    private static PortfolioHoldingResponse holding(String symbol, BigDecimal qty, BigDecimal cost) {
        PortfolioHoldingResponse h = new PortfolioHoldingResponse();
        h.setSymbol(symbol);
        h.setTotalQuantity(qty);
        h.setTotalCost(cost);
        return h;
    }

    // ---- core enrichment ---------------------------------------------------

    @Test
    @DisplayName("enrich: mv = price × qty (MONEY_SCALE 4); pl = mv − cost; symbol uppercase'leniyor")
    void enrich_marketValueProfitLossAndUppercase() {
        StockSummary s = summary(new BigDecimal("300"), "TRY", "Türk Hava Yolları");
        StockDetail d = detail(s, "THY", "TRY", new BigDecimal("400"), new BigDecimal("200"));
        when(stockQueryService.getStockDetail("THYAO.IS")).thenReturn(d);

        BigDecimal[] closes = new BigDecimal[20];
        for (int i = 0; i < 20; i++) {
            closes[i] = new BigDecimal(i + 1);
        }
        when(stockQueryService.getStockChartWithParams(eq("THYAO.IS"), any(), any()))
                .thenReturn(chart(closes));

        PortfolioHoldingResponse h = holding("thyao.is", new BigDecimal("10"), new BigDecimal("2500"));
        enricher.enrich(h);

        // 300 × 10 = 3000.0000; 3000.0000 − 2500 = 500.0000
        assertThat(h.getCurrentPrice()).isEqualByComparingTo("300");
        assertThat(h.getMarketValue()).isEqualByComparingTo("3000.0000");
        assertThat(h.getProfitLoss()).isEqualByComparingTo("500.0000");
        // symbol uppercase'le service'e gitti
        verify(stockQueryService).getStockDetail("THYAO.IS");
    }

    @Test
    @DisplayName("enrich: summary alanları (change/changePercent/volume/dayHigh/Low/52w) doğrudan kopyalanır")
    void enrich_passThroughSummaryFields() {
        StockSummary s = summary(new BigDecimal("100"), "TRY", "Foo");
        StockDetail d = detail(s, "Foo AŞ", "TRY", new BigDecimal("150"), new BigDecimal("80"));
        when(stockQueryService.getStockDetail("X")).thenReturn(d);
        when(stockQueryService.getStockChartWithParams(any(), any(), any())).thenReturn(chart());

        PortfolioHoldingResponse h = holding("X", BigDecimal.ONE, BigDecimal.ZERO);
        enricher.enrich(h);

        assertThat(h.getChange()).isEqualByComparingTo("1.5");
        assertThat(h.getChangePercent()).isEqualByComparingTo("0.5");
        assertThat(h.getVolume()).isEqualTo(123_456L);
        assertThat(h.getDayHigh()).isEqualByComparingTo("310");
        assertThat(h.getDayLow()).isEqualByComparingTo("295");
        assertThat(h.getFiftyTwoWeekHigh()).isEqualByComparingTo("150");
        assertThat(h.getFiftyTwoWeekLow()).isEqualByComparingTo("80");
        assertThat(h.getAsOf()).isNotNull();
    }

    @Test
    @DisplayName("enrich: currency önce summary.currency, yoksa detail.currency")
    void enrich_currencyFallback() {
        StockSummary s = summary(new BigDecimal("100"), null, "Foo");   // summary.currency null
        StockDetail d = detail(s, "Foo", "USD", null, null);
        when(stockQueryService.getStockDetail("X")).thenReturn(d);
        when(stockQueryService.getStockChartWithParams(any(), any(), any())).thenReturn(chart());

        PortfolioHoldingResponse h = holding("X", BigDecimal.ONE, BigDecimal.ZERO);
        enricher.enrich(h);

        assertThat(h.getCurrency()).isEqualTo("USD");
    }

    @Test
    @DisplayName("enrich: name önce detail.name, yoksa summary.name, yoksa holding.symbol")
    void enrich_nameFallback_holdingSymbol() {
        StockSummary s = summary(new BigDecimal("100"), "TRY", null);
        StockDetail d = detail(s, null, "TRY", null, null);          // detail.name null
        when(stockQueryService.getStockDetail("XYZ")).thenReturn(d);
        when(stockQueryService.getStockChartWithParams(any(), any(), any())).thenReturn(chart());

        PortfolioHoldingResponse h = holding("xyz", BigDecimal.ONE, BigDecimal.ZERO);
        enricher.enrich(h);

        // detail null + summary null → holding.symbol (lowercase haliyle korunmuş)
        assertThat(h.getName()).isEqualTo("xyz");
    }

    @Test
    @DisplayName("enrich: 20 günlük artan kapanış → MA20 = 10.5 (ortalama)")
    void enrich_ma20Calculation() {
        StockSummary s = summary(new BigDecimal("100"), "TRY", "Foo");
        StockDetail d = detail(s, "Foo", "TRY", null, null);
        when(stockQueryService.getStockDetail("X")).thenReturn(d);

        BigDecimal[] vals = new BigDecimal[20];
        for (int i = 0; i < 20; i++) {
            vals[i] = new BigDecimal(i + 1);
        }
        when(stockQueryService.getStockChartWithParams(eq("X"), eq("3mo"), eq("1d")))
                .thenReturn(chart(vals));

        PortfolioHoldingResponse h = holding("X", BigDecimal.ONE, BigDecimal.ZERO);
        enricher.enrich(h);

        assertThat(h.getMa20()).isEqualByComparingTo("10.5");
    }

    @Test
    @DisplayName("enrich: chart çağrısı patlarsa MA dolmaz, diğer alanlar yine set'lenir")
    void enrich_chartThrows_isSwallowed() {
        StockSummary s = summary(new BigDecimal("100"), "TRY", "Foo");
        StockDetail d = detail(s, "Foo", "TRY", null, null);
        when(stockQueryService.getStockDetail("X")).thenReturn(d);
        when(stockQueryService.getStockChartWithParams(any(), any(), any()))
                .thenThrow(new RuntimeException("Yahoo down"));

        PortfolioHoldingResponse h = holding("X", BigDecimal.ONE, BigDecimal.ZERO);
        enricher.enrich(h);

        assertThat(h.getCurrentPrice()).isEqualByComparingTo("100");
        assertThat(h.getMa20()).isNull();
        assertThat(h.getMa50()).isNull();
    }

    @Test
    @DisplayName("enrich: chart 3mo + 1d parametreleriyle çağrılır (kullanıcı SLA'sı)")
    void enrich_chartCalledWithExpectedParams() {
        StockSummary s = summary(new BigDecimal("100"), "TRY", "Foo");
        StockDetail d = detail(s, "Foo", "TRY", null, null);
        when(stockQueryService.getStockDetail("X")).thenReturn(d);
        when(stockQueryService.getStockChartWithParams(any(), any(), any())).thenReturn(chart());

        PortfolioHoldingResponse h = holding("X", BigDecimal.ONE, BigDecimal.ZERO);
        enricher.enrich(h);

        verify(stockQueryService).getStockChartWithParams("X", "3mo", "1d");
    }
}
