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
import java.time.LocalDateTime;
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

    private static GoldHistoryResponse weekly(GoldHistoryPoint point) {
        GoldHistoryResponse h = new GoldHistoryResponse();
        h.setPoints(List.of(point));
        return h;
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
        // changePercent = 30 / 2780 * 100 = 1.08 (6dp div, 2dp scale)
        assertThat(r.getChangePercent()).isEqualByComparingTo("1.08");
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
        // onsHigh × usdTry = 2010 × 35 = 70350
        assertThat(r.getHigh()).isEqualByComparingTo("70350.00");
        // onsLow × usdTry = 1990 × 35 = 69650
        assertThat(r.getLow()).isEqualByComparingTo("69650.00");
    }

    @Test
    @DisplayName("enrich: GOLD onsTry null → onsUsd×usdTry; 1W USD overlay + 1Y USD closes×usdTry")
    void enrich_gold_onsComputedAndUsdHistory() {
        GoldSpotResponse s = spot();
        s.setOnsTry(null); // compute from onsUsd × usdTry
        when(goldMarketService.getSpotGold()).thenReturn(s);
        when(goldMarketService.getGoldHistory("1W", "USD"))
                .thenReturn(weekly(ohlc(new BigDecimal("1995"), new BigDecimal("2015"),
                        new BigDecimal("1985"), new BigDecimal("2005"), 4242L)));
        // 3 USD closes → ×35 = 70000, 70350, 69650
        when(goldMarketService.getGoldHistory("1Y", "USD"))
                .thenReturn(history(new BigDecimal("2000"), new BigDecimal("2010"), new BigDecimal("1990")));

        WatchlistItemResponse r = new WatchlistItemResponse();
        enricher.enrich(r, "GOLD");

        // onsTry computed = 2000 × 35 = 70000.00
        assertThat(r.getLastPrice()).isEqualByComparingTo("70000.00");
        // 1W USD overlay: open + volume; high/low from the 1W point (USD), not scaled
        assertThat(r.getOpen()).isEqualByComparingTo("1995");
        assertThat(r.getVolume()).isEqualTo(4242L);
        assertThat(r.getHigh()).isEqualByComparingTo("2015");
        assertThat(r.getLow()).isEqualByComparingTo("1985");
        // 1Y USD closes × usdTry: 70000, 70350, 69650
        assertThat(r.getFiftyTwoWeekHigh()).isEqualByComparingTo("70350.00");
        assertThat(r.getFiftyTwoWeekLow()).isEqualByComparingTo("69650.00");
    }

    @Test
    @DisplayName("enrich: GOLD usdTry null → ham ons high/low/change + 52w hesaplanmaz")
    void enrich_gold_noUsdTryRawFields() {
        GoldSpotResponse s = spot();
        s.setUsdTry(null); // high/low/change stay raw; closes1y returns null
        when(goldMarketService.getSpotGold()).thenReturn(s);
        when(goldMarketService.getGoldHistory("1W", "USD")).thenReturn(null);

        WatchlistItemResponse r = new WatchlistItemResponse();
        enricher.enrich(r, "GOLD");

        assertThat(r.getLastPrice()).isEqualByComparingTo("70000"); // onsTry used directly
        assertThat(r.getHigh()).isEqualByComparingTo("2010"); // raw onsHigh, not scaled
        assertThat(r.getLow()).isEqualByComparingTo("1990");
        assertThat(r.getChange()).isEqualByComparingTo("5");
        assertThat(r.getFiftyTwoWeekHigh()).isNull();
        assertThat(r.getFiftyTwoWeekLow()).isNull();
    }

    @Test
    @DisplayName("enrich: CEYREK 1W overlay → gram oranıyla change/open/high/low ölçeklenir")
    void enrich_ceyrek_weeklyScaled() {
        when(goldMarketService.getSpotGold()).thenReturn(spot());
        // gramRef = officialPureGoldGramTry = 2800
        when(goldMarketService.getGoldHistory("1W", "TRY"))
                .thenReturn(weekly(ohlc(new BigDecimal("2800"), new BigDecimal("2870"),
                        new BigDecimal("2730"), new BigDecimal("2828"), 777L)));
        when(goldMarketService.getGoldHistory("1Y", "TRY")).thenReturn(null);

        WatchlistItemResponse r = new WatchlistItemResponse();
        enricher.enrich(r, "CEYREK");

        // lastPrice = quarter spot = 4502
        assertThat(r.getLastPrice()).isEqualByComparingTo("4502");
        // ratio = 4502 / 2800 = 1.60785714 (8dp)
        // gramCh = 2828 - 2800 = 28 ; coinChange = 28 * 1.60785714 = 45.02 (2dp)
        assertThat(r.getChange()).isEqualByComparingTo("45.02");
        // gramPct = 28 / 2800 * 100 = 1.00
        assertThat(r.getChangePercent()).isEqualByComparingTo("1.00");
        // open = lastPrice - coinChange = 4502 - 45.02 = 4456.98
        assertThat(r.getOpen()).isEqualByComparingTo("4456.98");
        assertThat(r.getVolume()).isEqualTo(777L);
        // high = 2870 * ratio (2dp), low = 2730 * ratio (2dp) — non-null and scaled up
        assertThat(r.getHigh()).isNotNull();
        assertThat(r.getLow()).isNotNull();
        assertThat(r.getHigh()).isGreaterThan(r.getLow());
    }

    @Test
    @DisplayName("enrich: CEYREK 1W overlay open null → ölçekleme atlanır, sadece volume yazılır")
    void enrich_ceyrek_weeklyMissingOpen_onlyVolume() {
        when(goldMarketService.getSpotGold()).thenReturn(spot());
        // open null → coin-scaling guard fails → else branch sets only volume
        when(goldMarketService.getGoldHistory("1W", "TRY"))
                .thenReturn(weekly(ohlc(null, new BigDecimal("2870"),
                        new BigDecimal("2730"), new BigDecimal("2828"), 888L)));
        when(goldMarketService.getGoldHistory("1Y", "TRY")).thenReturn(null);

        WatchlistItemResponse r = new WatchlistItemResponse();
        enricher.enrich(r, "YARIM");

        assertThat(r.getLastPrice()).isEqualByComparingTo("9004"); // half spot kept
        assertThat(r.getVolume()).isEqualTo(888L);
        assertThat(r.getChange()).isNull();
        assertThat(r.getOpen()).isNull();
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
    @DisplayName("enrich: 22AYAR twentyTwoK null ise ayar22Tl fallback")
    void enrich_twentyTwoK_fallback() {
        GoldSpotResponse s = spot();
        s.setTwentyTwoKBraceletTry(null);
        when(goldMarketService.getSpotGold()).thenReturn(s);
        when(goldMarketService.getGoldHistory(any(), any())).thenReturn(null);

        WatchlistItemResponse r = new WatchlistItemResponse();
        enricher.enrich(r, "AYAR22");

        assertThat(r.getLastPrice()).isEqualByComparingTo("2500");
    }

    @Test
    @DisplayName("enrich: asOf lastUpdated parse edilemezse updatedAt'ten alınır")
    void enrich_asOf_fallbackToUpdatedAt() {
        GoldSpotResponse s = spot();
        s.setLastUpdated(null);
        s.setUpdatedAt("2026-05-01T08:00:00");
        when(goldMarketService.getSpotGold()).thenReturn(s);
        when(goldMarketService.getGoldHistory(any(), any())).thenReturn(null);

        WatchlistItemResponse r = new WatchlistItemResponse();
        enricher.enrich(r, "CUMHUR");

        assertThat(r.getAsOf()).isEqualTo(LocalDateTime.parse("2026-05-01T08:00:00"));
        assertThat(r.getLastPrice()).isEqualByComparingTo("18520");
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
    @DisplayName("enrich: CEYREK 1Y history → teorik çarpan ile 52w hesaplanır")
    void enrich_ceyrek_yearFactorApplied() {
        when(goldMarketService.getSpotGold()).thenReturn(spot());
        when(goldMarketService.getGoldHistory("1W", "TRY")).thenReturn(null);
        when(goldMarketService.getGoldHistory("1Y", "TRY"))
                .thenReturn(history(new BigDecimal("2800"), new BigDecimal("2900"), new BigDecimal("2700")));

        WatchlistItemResponse r = new WatchlistItemResponse();
        enricher.enrich(r, "CEYREK");

        // factor = 1.754 * 0.9166 ≈ 1.6077; closes scaled up → all > raw gram values
        assertThat(r.getFiftyTwoWeekHigh()).isNotNull();
        assertThat(r.getFiftyTwoWeekLow()).isNotNull();
        assertThat(r.getFiftyTwoWeekHigh()).isGreaterThan(new BigDecimal("2900"));
        assertThat(r.getFiftyTwoWeekLow()).isGreaterThan(new BigDecimal("2700"));
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
