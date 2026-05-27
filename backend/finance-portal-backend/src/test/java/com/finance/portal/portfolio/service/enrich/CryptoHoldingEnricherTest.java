package com.finance.portal.portfolio.service.enrich;

import com.finance.portal.market.application.crypto.CryptoMarketService;
import com.finance.portal.market.application.crypto.model.CryptoMarketItem;
import com.finance.portal.portfolio.presentation.dto.PortfolioHoldingResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Characterization test: {@link CryptoHoldingEnricher} davranışı
 * {@code PortfolioHoldingMarketEnricher}'dan ayrıştırılmadan önceki ile aynen olmalı.
 * — fiyat × adet = marketValue (MONEY_SCALE=4, HALF_UP)
 * — profitLoss = marketValue − totalCost
 * — currency hep "TRY"
 * — name / change / 24h hacim / dayHigh / dayLow / marketCap ham veriden doğrudan kopyalanır
 * — chart varsa 52w min/max + MA20 doldurulur; yoksa dokunulmaz
 */
@ExtendWith(MockitoExtension.class)
class CryptoHoldingEnricherTest {

    @Mock CryptoMarketService cryptoMarketService;

    private CryptoHoldingEnricher enricher;

    @BeforeEach
    void setUp() {
        enricher = new CryptoHoldingEnricher(cryptoMarketService);
    }

    // ---- helpers ------------------------------------------------------------

    private static CryptoMarketItem item(String id, String symbol, String name, BigDecimal price) {
        return new CryptoMarketItem(
                id, symbol, name, "img",
                price,                          // currentPrice
                new BigDecimal("1000000000"),   // marketCap
                1,                              // marketCapRank
                new BigDecimal("500000"),       // totalVolume
                new BigDecimal("3100000"),      // high24h
                new BigDecimal("2900000"),      // low24h
                new BigDecimal("50000"),        // priceChange24h
                new BigDecimal("1.7"),          // priceChangePercentage24h
                new BigDecimal("0.2"),          // priceChangePercentage1h
                new BigDecimal("5.4"),          // priceChangePercentage7d
                "2026-05-26T10:15:00.000Z"      // lastUpdated
        );
    }

    private static PortfolioHoldingResponse holding(BigDecimal qty, BigDecimal cost, String symbol) {
        PortfolioHoldingResponse h = new PortfolioHoldingResponse();
        h.setSymbol(symbol);
        h.setTotalQuantity(qty);
        h.setTotalCost(cost);
        return h;
    }

    /** "prices" çift listesi formatında ([ms, price]) — CoinGecko market_chart taklidi. */
    private static Map<String, Object> chartWithPrices(BigDecimal... values) {
        List<List<Number>> rows = new java.util.ArrayList<>();
        long ts = 1_700_000_000_000L;
        for (BigDecimal v : values) {
            rows.add(List.of(ts, v.doubleValue()));
            ts += 24 * 60 * 60 * 1000L;
        }
        return Map.of("prices", rows);
    }

    // ---- core enrichment ---------------------------------------------------

    @Test
    @DisplayName("enrich: marketValue = price × qty (MONEY_SCALE 4, HALF_UP); profitLoss = mv − cost")
    void enrich_setsMarketValueAndProfitLoss() {
        when(cryptoMarketService.findBySymbol("btc")).thenReturn(
                item("bitcoin", "btc", "Bitcoin", new BigDecimal("3000000")));
        when(cryptoMarketService.getMarketChart(any(), any(), any(), any(), any())).thenReturn(null);

        PortfolioHoldingResponse h = holding(new BigDecimal("0.5"), new BigDecimal("1000000"), "btc");
        enricher.enrich(h);

        assertThat(h.getCurrentPrice()).isEqualByComparingTo("3000000");
        // 3,000,000 × 0.5 = 1,500,000.0000
        assertThat(h.getMarketValue()).isEqualByComparingTo("1500000.0000");
        // 1,500,000.0000 − 1,000,000 = 500,000.0000
        assertThat(h.getProfitLoss()).isEqualByComparingTo("500000.0000");
        assertThat(h.getCurrency()).isEqualTo("TRY");
    }

    @Test
    @DisplayName("enrich: 24h değişim / hacim / dayHigh / dayLow / marketCap / 7d hep ham veriden kopyalanır")
    void enrich_passThroughCryptoFields() {
        when(cryptoMarketService.findBySymbol("eth")).thenReturn(
                item("ethereum", "eth", "Ethereum", new BigDecimal("100000")));
        when(cryptoMarketService.getMarketChart(any(), any(), any(), any(), any())).thenReturn(null);

        PortfolioHoldingResponse h = holding(BigDecimal.ONE, BigDecimal.ZERO, "eth");
        enricher.enrich(h);

        assertThat(h.getName()).isEqualTo("Ethereum");
        assertThat(h.getChange()).isEqualByComparingTo("50000");
        assertThat(h.getChangePercent()).isEqualByComparingTo("1.7");
        assertThat(h.getPriceChangePercentage7d()).isEqualByComparingTo("5.4");
        assertThat(h.getVolume()).isEqualTo(500_000L);   // BigDecimal → long
        assertThat(h.getDayHigh()).isEqualByComparingTo("3100000");
        assertThat(h.getDayLow()).isEqualByComparingTo("2900000");
        assertThat(h.getMarketCap()).isEqualByComparingTo("1000000000");
    }

    @Test
    @DisplayName("enrich: lastUpdated string → LocalDateTime (asOf)")
    void enrich_parsesLastUpdated() {
        when(cryptoMarketService.findBySymbol("btc")).thenReturn(
                item("bitcoin", "btc", "Bitcoin", new BigDecimal("3000000")));
        when(cryptoMarketService.getMarketChart(any(), any(), any(), any(), any())).thenReturn(null);

        PortfolioHoldingResponse h = holding(BigDecimal.ONE, BigDecimal.ZERO, "btc");
        enricher.enrich(h);

        assertThat(h.getAsOf()).isNotNull();
    }

    // ---- chart / 52w / MA branches -----------------------------------------

    @Test
    @DisplayName("enrich: chart varsa 52 hafta min/max ve MA20 doldurulur")
    void enrich_withChart_setsFiftyTwoWeekAndMa() {
        when(cryptoMarketService.findBySymbol("btc")).thenReturn(
                item("bitcoin", "btc", "Bitcoin", new BigDecimal("3000000")));
        // 20 günlük artan fiyat — MA20 = ortalama, max = 20, min = 1
        BigDecimal[] vals = new BigDecimal[20];
        for (int i = 0; i < 20; i++) {
            vals[i] = new BigDecimal(i + 1);
        }
        when(cryptoMarketService.getMarketChart(any(), any(), any(), any(), any()))
                .thenReturn(chartWithPrices(vals));

        PortfolioHoldingResponse h = holding(BigDecimal.ONE, BigDecimal.ZERO, "btc");
        enricher.enrich(h);

        assertThat(h.getFiftyTwoWeekLow()).isEqualByComparingTo("1");
        assertThat(h.getFiftyTwoWeekHigh()).isEqualByComparingTo("20");
        // 1..20 ortalaması = 10.5
        assertThat(h.getMa20()).isEqualByComparingTo("10.5");
    }

    @Test
    @DisplayName("enrich: chart null → 52w/MA dokunulmaz, hata fırlatmaz")
    void enrich_nullChart_doesNotThrow() {
        when(cryptoMarketService.findBySymbol("btc")).thenReturn(
                item("bitcoin", "btc", "Bitcoin", new BigDecimal("3000000")));
        when(cryptoMarketService.getMarketChart(any(), any(), any(), any(), any())).thenReturn(null);

        PortfolioHoldingResponse h = holding(BigDecimal.ONE, BigDecimal.ZERO, "btc");
        enricher.enrich(h);

        assertThat(h.getFiftyTwoWeekHigh()).isNull();
        assertThat(h.getFiftyTwoWeekLow()).isNull();
        assertThat(h.getMa20()).isNull();
    }

    @Test
    @DisplayName("enrich: chart prices boş liste → 52w/MA dokunulmaz")
    void enrich_emptyChart_doesNotSetMetrics() {
        when(cryptoMarketService.findBySymbol("btc")).thenReturn(
                item("bitcoin", "btc", "Bitcoin", new BigDecimal("3000000")));
        when(cryptoMarketService.getMarketChart(any(), any(), any(), any(), any()))
                .thenReturn(Map.of("prices", List.of()));

        PortfolioHoldingResponse h = holding(BigDecimal.ONE, BigDecimal.ZERO, "btc");
        enricher.enrich(h);

        assertThat(h.getFiftyTwoWeekHigh()).isNull();
        assertThat(h.getMa20()).isNull();
    }

    @Test
    @DisplayName("enrich: getMarketChart fırlatırsa enrich kalan alanları yine doldurur")
    void enrich_chartThrows_isSwallowedSilently() {
        when(cryptoMarketService.findBySymbol("btc")).thenReturn(
                item("bitcoin", "btc", "Bitcoin", new BigDecimal("3000000")));
        when(cryptoMarketService.getMarketChart(any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("CoinGecko down"));

        PortfolioHoldingResponse h = holding(BigDecimal.ONE, BigDecimal.ZERO, "btc");
        enricher.enrich(h);

        // Core fields hâlâ set edilmiş olmalı.
        assertThat(h.getCurrentPrice()).isEqualByComparingTo("3000000");
        assertThat(h.getCurrency()).isEqualTo("TRY");
        assertThat(h.getMa20()).isNull();   // chart fail → no MA
    }
}
