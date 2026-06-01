package com.finance.portal.portfolio.service.watchlistenrich;

import com.finance.portal.market.application.gold.GoldHistoryPoint;
import com.finance.portal.market.application.gold.GoldHistoryResponse;
import com.finance.portal.market.application.gold.GoldMarketService;
import com.finance.portal.market.application.gold.GoldSpotResponse;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Characterization: {@link GoldWatchlistEnricher} eski
 * {@code PortfolioWatchlistMarketEnricher.enrichGold + latestFromGoldHistory + goldCloses1y +
 * watchlistGoldFactor} ile aynı davranmalı.
 */
@ExtendWith(MockitoExtension.class)
class GoldWatchlistEnricherTest {

    @Mock GoldMarketService goldMarketService;

    private GoldWatchlistEnricher enricher;

    @BeforeEach
    void setUp() {
        enricher = new GoldWatchlistEnricher(goldMarketService);
    }

    private static GoldSpotResponse spot() {
        GoldSpotResponse s = new GoldSpotResponse();
        s.setLastUpdated("2026-05-26T17:00:00");
        // gram has altın referansı
        s.setGramGoldTry(new BigDecimal("2800"));
        s.setOfficialPureGoldGramTry(new BigDecimal("2800"));
        s.setGramHighTry(new BigDecimal("2850"));
        s.setGramLowTry(new BigDecimal("2750"));
        // teorik türevler
        s.setQuarterGoldTry(new BigDecimal("4502"));
        s.setHalfGoldTry(new BigDecimal("9004"));
        s.setZiynetGoldTry(new BigDecimal("18008"));
        s.setRepublicGoldTry(new BigDecimal("18520"));
        s.setFourteenKBraceletTry(new BigDecimal("1638"));
        s.setTwentyTwoKBraceletTry(new BigDecimal("2566"));
        s.setAyar14Tl(new BigDecimal("1600"));
        s.setAyar22Tl(new BigDecimal("2500"));
        // ons
        s.setOnsUsd(new BigDecimal("2000"));
        s.setOnsTry(new BigDecimal("70000"));
        s.setUsdTry(new BigDecimal("35"));
        s.setOnsHigh(new BigDecimal("2010"));
        s.setOnsLow(new BigDecimal("1990"));
        s.setOnsChange(new BigDecimal("5"));
        s.setOnsChangePercent(new BigDecimal("0.25"));
        return s;
    }

    private static GoldHistoryResponse history(BigDecimal... closes) {
        GoldHistoryResponse h = new GoldHistoryResponse();
        List<GoldHistoryPoint> pts = new ArrayList<>();
        for (int i = 0; i < closes.length; i++) {
            pts.add(new GoldHistoryPoint("2025-01-" + (i + 1), closes[i]));
        }
        h.setPoints(pts);
        return h;
    }

    private static GoldHistoryPoint ohlc(BigDecimal open, BigDecimal high,
                                         BigDecimal low, BigDecimal close, Long volume) {
        GoldHistoryPoint p = new GoldHistoryPoint();
        p.setOpen(open);
        p.setHigh(high);
        p.setLow(low);
        p.setClose(close);
        p.setVolume(volume);
        return p;
    }

    @Test
    @DisplayName("enrich: GRAM → lastPrice/currency + 1W overlay close ile open/change/asOf")
    void enrich_gram_corePath() {
        when(goldMarketService.getSpotGold()).thenReturn(spot());
        GoldHistoryResponse weekly = new GoldHistoryResponse();
        weekly.setPoints(List.of(ohlc(new BigDecimal("2780"), new BigDecimal("2830"),
                new BigDecimal("2770"), new BigDecimal("2810"), 1234L)));
        when(goldMarketService.getGoldHistory("1W", "TRY")).thenReturn(weekly);
        when(goldMarketService.getGoldHistory("1Y", "TRY")).thenReturn(null);

        WatchlistItemResponse r = new WatchlistItemResponse();
        enricher.enrich(r, "gram");

        // 1W overlay GRAM → lastPrice = close
        assertThat(r.getLastPrice()).isEqualByComparingTo("2810");
        assertThat(r.getCurrency()).isEqualTo("TRY");
        assertThat(r.getOpen()).isEqualByComparingTo("2780");
        assertThat(r.getHigh()).isEqualByComparingTo("2830");
        assertThat(r.getLow()).isEqualByComparingTo("2770");
        assertThat(r.getVolume()).isEqualTo(1234L);
        // change = close - open = 2810 - 2780 = 30
        assertThat(r.getChange()).isEqualByComparingTo("30");
        assertThat(r.getAsOf()).isNotNull();
    }

    @Test
    @DisplayName("enrich: symbol küçük harf normalize edilir (toUpperCase)")
    void enrich_symbolNormalized() {
        when(goldMarketService.getSpotGold()).thenReturn(spot());
        when(goldMarketService.getGoldHistory(any(), any())).thenReturn(null);

        WatchlistItemResponse r = new WatchlistItemResponse();
        enricher.enrich(r, "ceyrek");

        assertThat(r.getLastPrice()).isEqualByComparingTo("4502");
        assertThat(r.getCurrency()).isEqualTo("TRY");
    }

    @Test
    @DisplayName("enrich: GOLD (ons) → onsTry lastPrice + change/percent ham spot'tan")
    void enrich_gold_onsPath() {
        when(goldMarketService.getSpotGold()).thenReturn(spot());
        // GOLD overlay → 1W USD; 1Y closes → USD
        when(goldMarketService.getGoldHistory("1W", "USD")).thenReturn(null);
        when(goldMarketService.getGoldHistory("1Y", "USD")).thenReturn(null);

        WatchlistItemResponse r = new WatchlistItemResponse();
        enricher.enrich(r, "GOLD");

        assertThat(r.getLastPrice()).isEqualByComparingTo("70000");
        assertThat(r.getCurrency()).isEqualTo("TRY");
        // onsChangePercent doğrudan kopyalanır
        assertThat(r.getChangePercent()).isEqualByComparingTo("0.25");
        // onsChange × usdTry = 5 × 35 = 175
        assertThat(r.getChange()).isEqualByComparingTo("175");
    }

    @Test
    @DisplayName("enrich: 14AYAR fourteenK null ise ayar14Tl fallback")
    void enrich_fourteenK_fallback() {
        GoldSpotResponse s = spot();
        s.setFourteenKBraceletTry(null);
        when(goldMarketService.getSpotGold()).thenReturn(s);
        when(goldMarketService.getGoldHistory(any(), any())).thenReturn(null);

        WatchlistItemResponse r = new WatchlistItemResponse();
        enricher.enrich(r, "14AYAR");

        assertThat(r.getLastPrice()).isEqualByComparingTo("1600");
    }

    @Test
    @DisplayName("enrich: 1Y history → 52w high/low + MA20 (gram, factor=null)")
    void enrich_gram_trendMetrics() {
        when(goldMarketService.getSpotGold()).thenReturn(spot());
        when(goldMarketService.getGoldHistory("1W", "TRY")).thenReturn(null);
        BigDecimal[] vals = new BigDecimal[20];
        for (int i = 0; i < 20; i++) {
            vals[i] = new BigDecimal(i + 1);
        }
        when(goldMarketService.getGoldHistory("1Y", "TRY")).thenReturn(history(vals));

        WatchlistItemResponse r = new WatchlistItemResponse();
        enricher.enrich(r, "GRAM");

        assertThat(r.getFiftyTwoWeekHigh()).isEqualByComparingTo("20");
        assertThat(r.getFiftyTwoWeekLow()).isEqualByComparingTo("1");
        assertThat(r.getMa20()).isEqualByComparingTo("10.5");
    }

    @Test
    @DisplayName("enrich: 1W overlay fırlatırsa spot fields korunur (silent skip)")
    void enrich_weeklyOverlayThrows_isSwallowed() {
        when(goldMarketService.getSpotGold()).thenReturn(spot());
        when(goldMarketService.getGoldHistory("1W", "TRY"))
                .thenThrow(new RuntimeException("BIST down"));
        lenient().when(goldMarketService.getGoldHistory("1Y", "TRY")).thenReturn(null);

        WatchlistItemResponse r = new WatchlistItemResponse();
        enricher.enrich(r, "GRAM");

        // spot field korundu (overlay fail)
        assertThat(r.getLastPrice()).isEqualByComparingTo("2800");
        assertThat(r.getHigh()).isEqualByComparingTo("2850");
        assertThat(r.getMa20()).isNull();
    }

    @Test
    @DisplayName("enrich: desteklenmeyen sembol UnsupportedOperationException")
    void enrich_unsupportedSymbol_throws() {
        when(goldMarketService.getSpotGold()).thenReturn(spot());

        WatchlistItemResponse r = new WatchlistItemResponse();
        assertThatThrownBy(() -> enricher.enrich(r, "PLATINUM"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Unsupported gold symbol");
    }
}
