package com.finance.portal.portfolio.service.watchlistenrich;

import com.finance.portal.market.application.crypto.CryptoMarketService;
import com.finance.portal.market.application.crypto.model.CryptoMarketItem;
import com.finance.portal.portfolio.presentation.dto.WatchlistItemResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Characterization: {@link CryptoWatchlistEnricher} eski
 * {@code PortfolioWatchlistMarketEnricher.enrichCrypto + cryptoCloses1y} ile aynı davranmalı.
 */
@ExtendWith(MockitoExtension.class)
class CryptoWatchlistEnricherTest {

    @Mock CryptoMarketService cryptoMarketService;

    private CryptoWatchlistEnricher enricher;

    @BeforeEach
    void setUp() {
        enricher = new CryptoWatchlistEnricher(cryptoMarketService);
    }

    private static CryptoMarketItem item(String id, BigDecimal price) {
        return new CryptoMarketItem(
                id, "btc", "Bitcoin", "img",
                price,
                new BigDecimal("1000000000"),
                1,
                new BigDecimal("500000"),
                new BigDecimal("3100000"),
                new BigDecimal("2900000"),
                new BigDecimal("50000"),
                new BigDecimal("1.7"),
                new BigDecimal("0.2"),
                new BigDecimal("5.4"),
                "2026-05-26T10:15:00.000Z"
        );
    }

    private static Map<String, Object> chart(BigDecimal... values) {
        List<List<Number>> rows = new ArrayList<>();
        long ts = 1_700_000_000_000L;
        for (BigDecimal v : values) {
            rows.add(List.of(ts, v.doubleValue()));
            ts += 24 * 60 * 60 * 1000L;
        }
        return Map.of("prices", rows);
    }

    @Test
    @DisplayName("enrich: anlık fiyat + 24h değişim + hacim + open ham veriden gelir")
    void enrich_corePath() {
        when(cryptoMarketService.findBySymbol("btc"))
                .thenReturn(item("bitcoin", new BigDecimal("3000000")));
        when(cryptoMarketService.getMarketChart(any(), any(), any(), any(), any())).thenReturn(null);

        WatchlistItemResponse r = new WatchlistItemResponse();
        enricher.enrich(r, "btc");

        assertThat(r.getLastPrice()).isEqualByComparingTo("3000000");
        assertThat(r.getCurrency()).isEqualTo("TRY");
        assertThat(r.getHigh()).isEqualByComparingTo("3100000");
        assertThat(r.getLow()).isEqualByComparingTo("2900000");
        assertThat(r.getChange()).isEqualByComparingTo("50000");
        assertThat(r.getChangePercent()).isEqualByComparingTo("1.7");
        assertThat(r.getVolume()).isEqualTo(500_000L);
        // open = price − change24h = 3,000,000 − 50,000 = 2,950,000
        assertThat(r.getOpen()).isEqualByComparingTo("2950000");
        assertThat(r.getPriceChangePercentage7d()).isEqualByComparingTo("5.4");
    }

    @Test
    @DisplayName("enrich: chart varsa 52w + MA20 doldurulur")
    void enrich_withChart_fillsTrendMetrics() {
        when(cryptoMarketService.findBySymbol("btc"))
                .thenReturn(item("bitcoin", new BigDecimal("3000000")));

        BigDecimal[] vals = new BigDecimal[20];
        for (int i = 0; i < 20; i++) {
            vals[i] = new BigDecimal(i + 1);
        }
        when(cryptoMarketService.getMarketChart(any(), any(), any(), any(), any()))
                .thenReturn(chart(vals));

        WatchlistItemResponse r = new WatchlistItemResponse();
        enricher.enrich(r, "btc");

        assertThat(r.getFiftyTwoWeekHigh()).isEqualByComparingTo("20");
        assertThat(r.getFiftyTwoWeekLow()).isEqualByComparingTo("1");
        assertThat(r.getMa20()).isEqualByComparingTo("10.5");
    }

    @Test
    @DisplayName("enrich: chart null → trend metrikleri dolmaz, hata fırlatmaz")
    void enrich_nullChart_silentNoTrend() {
        when(cryptoMarketService.findBySymbol("btc"))
                .thenReturn(item("bitcoin", new BigDecimal("3000000")));
        when(cryptoMarketService.getMarketChart(any(), any(), any(), any(), any())).thenReturn(null);

        WatchlistItemResponse r = new WatchlistItemResponse();
        enricher.enrich(r, "btc");

        assertThat(r.getFiftyTwoWeekHigh()).isNull();
        assertThat(r.getMa20()).isNull();
    }

    @Test
    @DisplayName("enrich: chart fırlatırsa core fields yine doldurulur (silent skip)")
    void enrich_chartThrows_isSwallowed() {
        when(cryptoMarketService.findBySymbol("btc"))
                .thenReturn(item("bitcoin", new BigDecimal("3000000")));
        when(cryptoMarketService.getMarketChart(any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("CoinGecko down"));

        WatchlistItemResponse r = new WatchlistItemResponse();
        enricher.enrich(r, "btc");

        assertThat(r.getLastPrice()).isEqualByComparingTo("3000000");
        assertThat(r.getCurrency()).isEqualTo("TRY");
        assertThat(r.getMa20()).isNull();
    }
}
