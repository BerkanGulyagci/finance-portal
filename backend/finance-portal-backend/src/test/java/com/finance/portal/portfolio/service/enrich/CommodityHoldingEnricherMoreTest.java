package com.finance.portal.portfolio.service.enrich;

import com.finance.portal.market.application.commodity.CommodityHistoryPointDto;
import com.finance.portal.market.application.commodity.CommodityHistoryResponse;
import com.finance.portal.market.application.commodity.CommoditySpotDto;
import com.finance.portal.market.application.commodity.YahooCommodityService;
import com.finance.portal.market.application.fx.model.FxLatestRates;
import com.finance.portal.market.application.fx.model.FxRateItem;
import com.finance.portal.market.application.precious.PreciousMetalHistoryPoint;
import com.finance.portal.market.application.precious.PreciousMetalHistoryResponse;
import com.finance.portal.market.application.precious.PreciousMetalService;
import com.finance.portal.market.application.precious.PreciousMetalSpotResponse;
import com.finance.portal.market.application.precious.model.PreciousMetalType;
import com.finance.portal.market.application.service.MarketFxService;
import com.finance.portal.market.application.silver.SilverHistoryPoint;
import com.finance.portal.market.application.silver.SilverHistoryResponse;
import com.finance.portal.market.application.silver.SilverMarketService;
import com.finance.portal.market.application.silver.SilverSpotResponse;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Ek kapsama: {@link CommodityHoldingEnricher} için mevcut {@code CommodityHoldingEnricherTest}'in
 * dokunmadığı dallar — SILVER:GRAM_TRY (1W history pencere + change/%) ve SILVER:USD_ONS,
 * PLATINUM/PALLADIUM precious metal yolu, Yahoo 1Y history MA (USD→TRY çarpan), 52w doldurma
 * ve null-symbol → Yahoo dispatch.
 */
@ExtendWith(MockitoExtension.class)
class CommodityHoldingEnricherMoreTest {

    @Mock SilverMarketService silverMarketService;
    @Mock YahooCommodityService yahooCommodityService;
    @Mock MarketFxService marketFxService;
    @Mock PreciousMetalService preciousMetalService;

    private CommodityHoldingEnricher enricher;

    @BeforeEach
    void setUp() {
        enricher = new CommodityHoldingEnricher(silverMarketService, yahooCommodityService,
                marketFxService, preciousMetalService);
    }

    private static PortfolioHoldingResponse holding(String symbol, BigDecimal qty, BigDecimal cost) {
        PortfolioHoldingResponse h = new PortfolioHoldingResponse();
        h.setSymbol(symbol);
        h.setTotalQuantity(qty);
        h.setTotalCost(cost);
        return h;
    }

    private static SilverHistoryPoint silverPoint(BigDecimal close, BigDecimal high, BigDecimal low) {
        SilverHistoryPoint p = new SilverHistoryPoint();
        p.setClose(close);
        p.setHigh(high);
        p.setLow(low);
        return p;
    }

    private static SilverHistoryResponse silverHistory(SilverHistoryPoint... pts) {
        SilverHistoryResponse r = new SilverHistoryResponse();
        List<SilverHistoryPoint> list = new ArrayList<>(List.of(pts));
        r.setPoints(list);
        return r;
    }

    private static SilverHistoryResponse silverCloses(BigDecimal... closes) {
        List<SilverHistoryPoint> pts = new ArrayList<>();
        for (BigDecimal c : closes) {
            SilverHistoryPoint p = new SilverHistoryPoint();
            p.setClose(c);
            pts.add(p);
        }
        SilverHistoryResponse r = new SilverHistoryResponse();
        r.setPoints(pts);
        return r;
    }

    private static PreciousMetalHistoryPoint preciousPoint(BigDecimal gram, BigDecimal usdOns) {
        PreciousMetalHistoryPoint p = new PreciousMetalHistoryPoint();
        p.setTryGram(gram);
        p.setUsdOns(usdOns);
        return p;
    }

    // =========================================================================
    // SILVER:GRAM_TRY — 1W window price + change derivation
    // =========================================================================

    @Test
    @DisplayName("SILVER:GRAM_TRY: 1W history son nokta fiyat/high/low; prev'den change & %")
    void silverGram_fromHistoryWindow() {
        SilverSpotResponse spot = new SilverSpotResponse();
        spot.setLastUpdated("2026-05-26T15:00:00");
        when(silverMarketService.getSpotSilver()).thenReturn(spot);

        SilverHistoryResponse hist1w = silverHistory(
                silverPoint(new BigDecimal("48"), null, null),     // prev
                silverPoint(new BigDecimal("50"), new BigDecimal("51"), new BigDecimal("49")) // latest
        );
        when(silverMarketService.getSilverHistory("1W", "TRY")).thenReturn(hist1w);
        when(silverMarketService.getSilverHistory("1Y", "TRY")).thenReturn(null);

        PortfolioHoldingResponse h = holding("SILVER:GRAM_TRY", new BigDecimal("10"), new BigDecimal("450"));
        enricher.enrich(h);

        assertThat(h.getCurrentPrice()).isEqualByComparingTo("50");
        assertThat(h.getDayHigh()).isEqualByComparingTo("51");
        assertThat(h.getDayLow()).isEqualByComparingTo("49");
        assertThat(h.getCurrency()).isEqualTo("TRY");
        assertThat(h.getName()).isEqualTo("Gümüş");
        // change = 50 - 48 = 2 ; % = 2/48*100 = 4.17
        assertThat(h.getChange()).isEqualByComparingTo("2");
        assertThat(h.getChangePercent()).isEqualByComparingTo("4.17");
        assertThat(h.getMarketValue()).isEqualByComparingTo("500.0000");
    }

    @Test
    @DisplayName("SILVER blank cat → GRAM_TRY default; history yoksa spot.silverGramCloseTry fallback")
    void silverBlankCat_spotCloseFallback() {
        SilverSpotResponse spot = new SilverSpotResponse();
        spot.setSilverGramCloseTry(new BigDecimal("52"));
        spot.setSilverGramHighTry(new BigDecimal("53"));
        spot.setSilverGramLowTry(new BigDecimal("51"));
        spot.setLastUpdated("2026-05-26T15:00:00");
        when(silverMarketService.getSpotSilver()).thenReturn(spot);
        when(silverMarketService.getSilverHistory("1W", "TRY")).thenReturn(null);
        when(silverMarketService.getSilverHistory("1Y", "TRY")).thenReturn(null);

        // symbol "SILVER:" → cat blank → defaults to GRAM_TRY
        PortfolioHoldingResponse h = holding("SILVER:", BigDecimal.ONE, BigDecimal.ZERO);
        enricher.enrich(h);

        assertThat(h.getCurrentPrice()).isEqualByComparingTo("52");
        assertThat(h.getDayHigh()).isEqualByComparingTo("53");
        assertThat(h.getDayLow()).isEqualByComparingTo("51");
    }

    @Test
    @DisplayName("SILVER:GRAM_TRY: history ve close yok ama silverGramTry var → son fallback")
    void silverGram_silverGramTryLastResort() {
        SilverSpotResponse spot = new SilverSpotResponse();
        spot.setSilverGramTry(new BigDecimal("54"));
        spot.setLastUpdated("2026-05-26T15:00:00");
        when(silverMarketService.getSpotSilver()).thenReturn(spot);
        when(silverMarketService.getSilverHistory("1W", "TRY")).thenReturn(null);
        when(silverMarketService.getSilverHistory("1Y", "TRY")).thenReturn(null);

        PortfolioHoldingResponse h = holding("SILVER:GRAM_TRY", BigDecimal.ONE, BigDecimal.ZERO);
        enricher.enrich(h);

        assertThat(h.getCurrentPrice()).isEqualByComparingTo("54");
    }

    @Test
    @DisplayName("SILVER:GRAM_TRY: 1Y history → 52w min/max + MA")
    void silverGram_52wFromYearHistory() {
        SilverSpotResponse spot = new SilverSpotResponse();
        spot.setSilverGramCloseTry(new BigDecimal("50"));
        spot.setLastUpdated("2026-05-26T15:00:00");
        when(silverMarketService.getSpotSilver()).thenReturn(spot);
        when(silverMarketService.getSilverHistory("1W", "TRY")).thenReturn(null);

        BigDecimal[] closes = new BigDecimal[20];
        for (int i = 0; i < 20; i++) {
            closes[i] = new BigDecimal(i + 1); // 1..20
        }
        when(silverMarketService.getSilverHistory("1Y", "TRY")).thenReturn(silverCloses(closes));

        PortfolioHoldingResponse h = holding("SILVER:GRAM_TRY", BigDecimal.ONE, BigDecimal.ZERO);
        enricher.enrich(h);

        assertThat(h.getFiftyTwoWeekLow()).isEqualByComparingTo("1");
        assertThat(h.getFiftyTwoWeekHigh()).isEqualByComparingTo("20");
        assertThat(h.getMa20()).isEqualByComparingTo("10.5");
    }

    // =========================================================================
    // SILVER:USD_ONS
    // =========================================================================

    @Test
    @DisplayName("SILVER:USD_ONS: 1W history son nokta → fiyat; currency stays TRY when latest present")
    void silverUsdOns_fromHistory() {
        SilverSpotResponse spot = new SilverSpotResponse();
        spot.setLastUpdated("2026-05-26T15:00:00");
        when(silverMarketService.getSpotSilver()).thenReturn(spot);

        SilverHistoryResponse hist1w = silverHistory(
                silverPoint(new BigDecimal("30"), null, null),
                silverPoint(new BigDecimal("33"), new BigDecimal("34"), new BigDecimal("32"))
        );
        when(silverMarketService.getSilverHistory("1W", "USD")).thenReturn(hist1w);
        // currency stays "TRY" because latest != null → 52w fetched as TRY
        when(silverMarketService.getSilverHistory("1Y", "TRY")).thenReturn(null);

        PortfolioHoldingResponse h = holding("SILVER:USD_ONS", BigDecimal.ONE, BigDecimal.ZERO);
        enricher.enrich(h);

        assertThat(h.getCurrentPrice()).isEqualByComparingTo("33");
        // change = 33 - 30 = 3 ; % = 3/30*100 = 10.00
        assertThat(h.getChange()).isEqualByComparingTo("3");
        assertThat(h.getChangePercent()).isEqualByComparingTo("10.00");
    }

    @Test
    @DisplayName("SILVER:USD_ONS: history boş → spot.silverUsdOns, currency=USD, 52w USD history")
    void silverUsdOns_spotFallbackCurrencyUsd() {
        SilverSpotResponse spot = new SilverSpotResponse();
        spot.setSilverUsdOns(new BigDecimal("31.5"));
        spot.setLastUpdated("2026-05-26T15:00:00");
        when(silverMarketService.getSpotSilver()).thenReturn(spot);
        when(silverMarketService.getSilverHistory("1W", "USD")).thenReturn(null);
        // currency becomes USD because latest == null → 52w fetched as USD
        when(silverMarketService.getSilverHistory("1Y", "USD")).thenReturn(null);

        PortfolioHoldingResponse h = holding("SILVER:USD_ONS", BigDecimal.ONE, BigDecimal.ZERO);
        enricher.enrich(h);

        assertThat(h.getCurrentPrice()).isEqualByComparingTo("31.5");
        assertThat(h.getCurrency()).isEqualTo("USD");
    }

    @Test
    @DisplayName("SILVER 52w: 1Y history exception fırlatırsa fail-soft (çekirdek alanlar korunur)")
    void silver_52wThrows_failSoft() {
        SilverSpotResponse spot = new SilverSpotResponse();
        spot.setWeightedAverageTryKg(new BigDecimal("100000"));
        spot.setLastUpdated("2026-05-26T15:00:00");
        when(silverMarketService.getSpotSilver()).thenReturn(spot);
        when(silverMarketService.getSilverHistory("1Y", "TRY")).thenThrow(new RuntimeException("boom"));

        PortfolioHoldingResponse h = holding("SILVER:KG_TRY", BigDecimal.ONE, BigDecimal.ZERO);
        enricher.enrich(h);

        assertThat(h.getCurrentPrice()).isEqualByComparingTo("100000");
        assertThat(h.getFiftyTwoWeekHigh()).isNull();
    }

    // =========================================================================
    // PLATINUM / PALLADIUM (precious metal branch)
    // =========================================================================

    @Test
    @DisplayName("PLATINUM:GRAM_TRY: tryGram → fiyat; isim Platin; 1W change + 1Y 52w")
    void platinum_gramTry_full() {
        PreciousMetalSpotResponse spot = new PreciousMetalSpotResponse();
        spot.setTryGram(new BigDecimal("1200"));
        spot.setLastUpdated("2026-05-26T15:00:00");
        when(preciousMetalService.getSpot(PreciousMetalType.PLATINUM)).thenReturn(spot);

        // 1W change: prev gram 1180, latest gram 1200 → change 20, % = 20/1180*100 = 1.69
        PreciousMetalHistoryResponse hist1w = new PreciousMetalHistoryResponse();
        hist1w.setPoints(List.of(
                preciousPoint(new BigDecimal("1180"), null),
                preciousPoint(new BigDecimal("1200"), null)
        ));
        when(preciousMetalService.getHistory(eq(PreciousMetalType.PLATINUM), eq("1W"), eq("TRY")))
                .thenReturn(hist1w);

        // 1Y 52w via tryGram values 1000..1019
        List<PreciousMetalHistoryPoint> yearPts = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            yearPts.add(preciousPoint(new BigDecimal(1000 + i), null));
        }
        PreciousMetalHistoryResponse hist1y = new PreciousMetalHistoryResponse();
        hist1y.setPoints(yearPts);
        when(preciousMetalService.getHistory(eq(PreciousMetalType.PLATINUM), eq("1Y"), eq("TRY")))
                .thenReturn(hist1y);

        PortfolioHoldingResponse h = holding("PLATINUM:GRAM_TRY", new BigDecimal("2"), new BigDecimal("2000"));
        enricher.enrich(h);

        assertThat(h.getCurrentPrice()).isEqualByComparingTo("1200");
        assertThat(h.getCurrency()).isEqualTo("TRY");
        assertThat(h.getName()).isEqualTo("Platin");
        assertThat(h.getMarketValue()).isEqualByComparingTo("2400.0000");
        assertThat(h.getProfitLoss()).isEqualByComparingTo("400.0000");
        assertThat(h.getChange()).isEqualByComparingTo("20");
        assertThat(h.getChangePercent()).isEqualByComparingTo("1.69");
        assertThat(h.getFiftyTwoWeekLow()).isEqualByComparingTo("1000");
        assertThat(h.getFiftyTwoWeekHigh()).isEqualByComparingTo("1019");
    }

    @Test
    @DisplayName("PALLADIUM:USD_ONS: usdOns → fiyat; currency USD; isim Paladyum")
    void palladium_usdOns() {
        PreciousMetalSpotResponse spot = new PreciousMetalSpotResponse();
        spot.setUsdOns(new BigDecimal("950"));
        spot.setLastUpdated("2026-05-26T15:00:00");
        when(preciousMetalService.getSpot(PreciousMetalType.PALLADIUM)).thenReturn(spot);
        lenient().when(preciousMetalService.getHistory(any(), any(), any())).thenReturn(null);

        PortfolioHoldingResponse h = holding("PALLADIUM:USD_ONS", BigDecimal.ONE, BigDecimal.ZERO);
        enricher.enrich(h);

        assertThat(h.getCurrentPrice()).isEqualByComparingTo("950");
        assertThat(h.getCurrency()).isEqualTo("USD");
        assertThat(h.getName()).isEqualTo("Paladyum");
    }

    @Test
    @DisplayName("PLATINUM:EUR_ONS: eurOns → fiyat; currency EUR")
    void platinum_eurOns() {
        PreciousMetalSpotResponse spot = new PreciousMetalSpotResponse();
        spot.setEurOns(new BigDecimal("880"));
        spot.setLastUpdated("2026-05-26T15:00:00");
        when(preciousMetalService.getSpot(PreciousMetalType.PLATINUM)).thenReturn(spot);
        lenient().when(preciousMetalService.getHistory(any(), any(), any())).thenReturn(null);

        PortfolioHoldingResponse h = holding("PLATINUM:EUR_ONS", BigDecimal.ONE, BigDecimal.ZERO);
        enricher.enrich(h);

        assertThat(h.getCurrentPrice()).isEqualByComparingTo("880");
        assertThat(h.getCurrency()).isEqualTo("EUR");
    }

    @Test
    @DisplayName("PLATINUM:BOGUS → UnsupportedOperationException")
    void platinum_unknownCat_throws() {
        when(preciousMetalService.getSpot(PreciousMetalType.PLATINUM))
                .thenReturn(new PreciousMetalSpotResponse());

        PortfolioHoldingResponse h = holding("PLATINUM:BOGUS", BigDecimal.ONE, BigDecimal.ZERO);
        assertThatThrownBy(() -> enricher.enrich(h))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Unsupported precious category");
    }

    @Test
    @DisplayName("PALLADIUM:GRAM_TRY: tryGram null → IllegalStateException")
    void palladium_priceNull_throws() {
        when(preciousMetalService.getSpot(PreciousMetalType.PALLADIUM))
                .thenReturn(new PreciousMetalSpotResponse());

        PortfolioHoldingResponse h = holding("PALLADIUM:GRAM_TRY", BigDecimal.ONE, BigDecimal.ZERO);
        assertThatThrownBy(() -> enricher.enrich(h))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Precious metal price unavailable");
    }

    @Test
    @DisplayName("PLATINUM blank cat → GRAM_TRY default")
    void platinum_blankCatDefaultsGram() {
        PreciousMetalSpotResponse spot = new PreciousMetalSpotResponse();
        spot.setTryGram(new BigDecimal("1234"));
        spot.setLastUpdated("2026-05-26T15:00:00");
        when(preciousMetalService.getSpot(PreciousMetalType.PLATINUM)).thenReturn(spot);
        lenient().when(preciousMetalService.getHistory(any(), any(), any())).thenReturn(null);

        PortfolioHoldingResponse h = holding("PLATINUM:", BigDecimal.ONE, BigDecimal.ZERO);
        enricher.enrich(h);

        assertThat(h.getCurrentPrice()).isEqualByComparingTo("1234");
        assertThat(h.getName()).isEqualTo("Platin");
    }

    // =========================================================================
    // Yahoo 1Y history MA (USD → TRY multiplication) + null symbol dispatch
    // =========================================================================

    @Test
    @DisplayName("Yahoo: currency TRY iken 1Y history closes USD/TRY ile çarpılıp MA hesaplanır")
    void yahoo_historyMaUsdTryScaled() {
        CommoditySpotDto spot = new CommoditySpotDto();
        spot.setDisplayPrice(new BigDecimal("85"));
        spot.setLastUpdated("2026-05-26T17:00:00");
        when(yahooCommodityService.getSpot("NG=F")).thenReturn(spot);

        // 20 closes each 100 ; usdTry 40 → MA20 = 4000.00
        List<CommodityHistoryPointDto> pts = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            CommodityHistoryPointDto p = new CommodityHistoryPointDto();
            p.setDisplayClose(new BigDecimal("100"));
            pts.add(p);
        }
        CommodityHistoryResponse hist = new CommodityHistoryResponse();
        hist.setPoints(pts);
        when(yahooCommodityService.getHistory("NG=F", "1Y", "1d")).thenReturn(hist);

        FxRateItem usd = new FxRateItem("USD", new BigDecimal("39.5"), new BigDecimal("40"), 1);
        FxLatestRates rates = new FxLatestRates("tcmb", "live", "TRY", "now", List.of(usd));
        when(marketFxService.getTcmbLatestRates("USD")).thenReturn(rates);

        // enrichYahoo sets currency="TRY" before applyYahooHistoryMa → USD×usdTry path taken
        PortfolioHoldingResponse h = holding("NG=F", BigDecimal.ONE, BigDecimal.ZERO);
        enricher.enrich(h);

        // 100 × 40 = 4000.00
        assertThat(h.getMa20()).isEqualByComparingTo("4000.00");
    }

    @Test
    @DisplayName("Yahoo history exception → MA atlanır (fail-soft)")
    void yahoo_historyThrows_maSkipped() {
        CommoditySpotDto spot = new CommoditySpotDto();
        spot.setDisplayPrice(new BigDecimal("85"));
        spot.setLastUpdated("2026-05-26T17:00:00");
        when(yahooCommodityService.getSpot("NG=F")).thenReturn(spot);
        when(yahooCommodityService.getHistory(any(), any(), any())).thenThrow(new RuntimeException("yahoo down"));

        PortfolioHoldingResponse h = holding("NG=F", BigDecimal.ONE, BigDecimal.ZERO);
        enricher.enrich(h);

        assertThat(h.getCurrentPrice()).isEqualByComparingTo("85");
        assertThat(h.getMa20()).isNull();
    }

    @Test
    @DisplayName("Dispatch: null symbol → Yahoo branch (getSpot(null) çağrılır)")
    void dispatch_nullSymbol_goesToYahoo() {
        CommoditySpotDto spot = new CommoditySpotDto();
        spot.setDisplayPrice(new BigDecimal("70"));
        when(yahooCommodityService.getSpot(null)).thenReturn(spot);
        when(yahooCommodityService.getHistory(any(), any(), any())).thenReturn(null);

        PortfolioHoldingResponse h = holding(null, BigDecimal.ONE, BigDecimal.ZERO);
        enricher.enrich(h);

        assertThat(h.getCurrentPrice()).isEqualByComparingTo("70");
    }
}
