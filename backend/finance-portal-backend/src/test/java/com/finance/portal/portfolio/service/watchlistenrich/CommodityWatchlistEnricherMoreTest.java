package com.finance.portal.portfolio.service.watchlistenrich;

import com.finance.portal.market.application.commodity.CommodityHistoryPointDto;
import com.finance.portal.market.application.commodity.CommodityHistoryResponse;
import com.finance.portal.market.application.commodity.CommoditySpotDto;
import com.finance.portal.market.application.commodity.YahooCommodityService;
import com.finance.portal.market.application.precious.PreciousMetalHistoryPoint;
import com.finance.portal.market.application.precious.PreciousMetalHistoryResponse;
import com.finance.portal.market.application.precious.PreciousMetalService;
import com.finance.portal.market.application.precious.PreciousMetalSpotResponse;
import com.finance.portal.market.application.precious.model.PreciousMetalType;
import com.finance.portal.market.application.silver.SilverHistoryPoint;
import com.finance.portal.market.application.silver.SilverHistoryResponse;
import com.finance.portal.market.application.silver.SilverMarketService;
import com.finance.portal.market.application.silver.SilverSpotResponse;
import com.finance.portal.portfolio.presentation.dto.WatchlistItemResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Ek branch kapsamı: {@link CommodityWatchlistEnricherTest}'in DOKUNMADIĞI dallar.
 * Hedef: KG_TRY arm'ı, USD/EUR precious arm'ları, silver fallback'leri, null-history
 * guard'ları, change-percent sıfır-bölen koruması ve asOf fallback.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CommodityWatchlistEnricherMoreTest {

    @Mock SilverMarketService silverMarketService;
    @Mock PreciousMetalService preciousMetalService;
    @Mock YahooCommodityService yahooCommodityService;

    private CommodityWatchlistEnricher enricher;

    @BeforeEach
    void setUp() {
        enricher = new CommodityWatchlistEnricher(
                silverMarketService, preciousMetalService, yahooCommodityService);
    }

    // ── Builders ──────────────────────────────────────────────────────────────

    private static SilverSpotResponse silverSpot() {
        SilverSpotResponse s = new SilverSpotResponse();
        s.setLastUpdated("2026-05-26T17:00:00");
        s.setSilverGramTry(new BigDecimal("34.5"));
        s.setSilverGramCloseTry(new BigDecimal("34.2"));
        s.setSilverGramHighTry(new BigDecimal("35.0"));
        s.setSilverGramLowTry(new BigDecimal("33.8"));
        s.setSilverUsdOns(new BigDecimal("31.5"));
        s.setWeightedAverageTryKg(new BigDecimal("34500"));
        s.setHighTryKg(new BigDecimal("35000"));
        s.setLowTryKg(new BigDecimal("33800"));
        s.setVolumeTry(new BigDecimal("9999"));
        return s;
    }

    private static SilverHistoryPoint silverKgPoint(BigDecimal open, BigDecimal closeTryKg) {
        SilverHistoryPoint p = new SilverHistoryPoint();
        p.setOpen(open);
        p.setCloseTryKg(closeTryKg);
        return p;
    }

    private static SilverHistoryResponse silverHistory(SilverHistoryPoint... pts) {
        SilverHistoryResponse h = new SilverHistoryResponse();
        h.setPoints(new ArrayList<>(List.of(pts)));
        return h;
    }

    private static PreciousMetalSpotResponse preciousSpot() {
        PreciousMetalSpotResponse s = new PreciousMetalSpotResponse();
        s.setLastUpdated("2026-05-26T17:00:00");
        s.setUsdOns(new BigDecimal("950"));
        s.setEurOns(new BigDecimal("880"));
        s.setTryGram(new BigDecimal("1100"));
        s.setTryKg(new BigDecimal("1100000"));
        return s;
    }

    private static PreciousMetalHistoryPoint preciousPoint(BigDecimal tryGram, BigDecimal tryKg,
                                                           BigDecimal usdOns, BigDecimal eurOns) {
        PreciousMetalHistoryPoint p = new PreciousMetalHistoryPoint();
        p.setTryGram(tryGram);
        p.setTryKg(tryKg);
        p.setUsdOns(usdOns);
        p.setEurOns(eurOns);
        return p;
    }

    // ── SILVER:KG_TRY (existing test hiç dokunmuyor) ──────────────────────────

    @Test
    @DisplayName("enrich: SILVER:KG_TRY → spot kg fiyat/high/low/volume + 1W history change vs prev")
    void enrich_silverKgTry_corePath() {
        when(silverMarketService.getSpotSilver()).thenReturn(silverSpot());
        // latest.open != null && prev != null → open = prev.closeTryKg; change = latest-prev
        SilverHistoryResponse weekly = silverHistory(
                silverKgPoint(new BigDecimal("33000"), new BigDecimal("33200")),
                silverKgPoint(new BigDecimal("33200"), new BigDecimal("34000")));
        when(silverMarketService.getSilverHistory("1W", "TRY")).thenReturn(weekly);
        when(silverMarketService.getSilverHistory("1Y", "TRY")).thenReturn(null);

        WatchlistItemResponse r = new WatchlistItemResponse();
        enricher.enrich(r, "SILVER:KG_TRY");

        assertThat(r.getLastPrice()).isEqualByComparingTo("34500"); // weightedAverageTryKg
        assertThat(r.getCurrency()).isEqualTo("TRY");
        assertThat(r.getHigh()).isEqualByComparingTo("35000");
        assertThat(r.getLow()).isEqualByComparingTo("33800");
        // open = prev.closeTryKg = 33200
        assertThat(r.getOpen()).isEqualByComparingTo("33200");
        // change = latest.closeTryKg - prev.closeTryKg = 34000 - 33200 = 800
        assertThat(r.getChange()).isEqualByComparingTo("800");
        // changePct = 800 / 33200 * 100 = 2.41
        assertThat(r.getChangePercent()).isEqualByComparingTo("2.41");
        // gümüş emtia → hacim gösterilmez
        assertThat(r.getVolume()).isNull();
        assertThat(r.getAsOf()).isNotNull();
    }

    @Test
    @DisplayName("enrich: SILVER:KG_TRY latest.open=null → open null (ternary false dalı)")
    void enrich_silverKgTry_latestOpenNull_openStaysNull() {
        when(silverMarketService.getSpotSilver()).thenReturn(silverSpot());
        // latest.open == null → open ternary false → open = null; ama change yine hesaplanır
        SilverHistoryResponse weekly = silverHistory(
                silverKgPoint(null, new BigDecimal("33200")),
                silverKgPoint(null, new BigDecimal("34000")));
        when(silverMarketService.getSilverHistory("1W", "TRY")).thenReturn(weekly);
        when(silverMarketService.getSilverHistory("1Y", "TRY")).thenReturn(null);

        WatchlistItemResponse r = new WatchlistItemResponse();
        enricher.enrich(r, "SILVER:KG_TRY");

        assertThat(r.getOpen()).isNull();
        assertThat(r.getChange()).isEqualByComparingTo("800");
    }

    @Test
    @DisplayName("enrich: SILVER:KG_TRY history fırlatırsa (catch ignored) → spot kg değerleri korunur")
    void enrich_silverKgTry_historyThrows_swallowedKeepsSpot() {
        when(silverMarketService.getSpotSilver()).thenReturn(silverSpot());
        when(silverMarketService.getSilverHistory("1W", "TRY"))
                .thenThrow(new RuntimeException("silver 1W down"));
        when(silverMarketService.getSilverHistory("1Y", "TRY")).thenReturn(null);

        WatchlistItemResponse r = new WatchlistItemResponse();
        enricher.enrich(r, "SILVER:KG_TRY");

        // catch swallowed → spot doldurmuş; change set edilmez
        assertThat(r.getLastPrice()).isEqualByComparingTo("34500");
        assertThat(r.getHigh()).isEqualByComparingTo("35000");
        assertThat(r.getChange()).isNull();
        assertThat(r.getOpen()).isNull();
    }

    @Test
    @DisplayName("enrich: SILVER:KG_TRY volumeTry null → volume null (volume ternary false dalı)")
    void enrich_silverKgTry_nullVolumeTry() {
        SilverSpotResponse spot = silverSpot();
        spot.setVolumeTry(null);
        when(silverMarketService.getSpotSilver()).thenReturn(spot);
        // latest.closeTryKg == null → iç if'lere girmez (closeTryKg null guard false)
        when(silverMarketService.getSilverHistory("1W", "TRY"))
                .thenReturn(silverHistory(silverKgPoint(new BigDecimal("1"), null)));
        when(silverMarketService.getSilverHistory("1Y", "TRY")).thenReturn(null);

        WatchlistItemResponse r = new WatchlistItemResponse();
        enricher.enrich(r, "SILVER:KG_TRY");

        assertThat(r.getVolume()).isNull();
        assertThat(r.getChange()).isNull();
    }

    // ── SILVER:GRAM_TRY uncovered guard'lar ───────────────────────────────────

    @Test
    @DisplayName("enrich: SILVER:GRAM_TRY history boş + spot close null → silverGramTry son fallback")
    void enrich_silverGramTry_priceNull_fallsBackToGramTry() {
        SilverSpotResponse spot = silverSpot();
        spot.setSilverGramCloseTry(null); // history empty + close null → if(price==null) tetiklenir
        when(silverMarketService.getSpotSilver()).thenReturn(spot);
        when(silverMarketService.getSilverHistory("1W", "TRY")).thenReturn(silverHistory());
        when(silverMarketService.getSilverHistory("1Y", "TRY")).thenReturn(null);

        WatchlistItemResponse r = new WatchlistItemResponse();
        enricher.enrich(r, "SILVER:GRAM_TRY");

        // price == null → spot.getSilverGramTry()
        assertThat(r.getLastPrice()).isEqualByComparingTo("34.5");
        assertThat(r.getCurrency()).isEqualTo("TRY");
    }

    @Test
    @DisplayName("enrich: SILVER:GRAM_TRY prev.close=0 → changePct sıfır-bölen guard (set edilmez)")
    void enrich_silverGramTry_prevCloseZero_noChangePct() {
        when(silverMarketService.getSpotSilver()).thenReturn(silverSpot());
        SilverHistoryPoint prev = new SilverHistoryPoint();
        prev.setClose(BigDecimal.ZERO); // prev.close == 0 → changePct hesaplanmaz
        SilverHistoryPoint latest = new SilverHistoryPoint();
        latest.setOpen(new BigDecimal("33.0"));
        latest.setHigh(new BigDecimal("34.5"));
        latest.setLow(new BigDecimal("32.0"));
        latest.setClose(new BigDecimal("34.0"));
        when(silverMarketService.getSilverHistory("1W", "TRY"))
                .thenReturn(silverHistory(prev, latest));
        when(silverMarketService.getSilverHistory("1Y", "TRY")).thenReturn(null);

        WatchlistItemResponse r = new WatchlistItemResponse();
        enricher.enrich(r, "SILVER:GRAM_TRY");

        // change = 34.0 - 0 = 34.0 ama changePct null (sıfır bölen)
        assertThat(r.getChange()).isEqualByComparingTo("34.0");
        assertThat(r.getChangePercent()).isNull();
    }

    // ── SILVER:USD_ONS uncovered arm'lar ──────────────────────────────────────

    @Test
    @DisplayName("enrich: SILVER:USD_ONS latest yok (boş history) → spot.silverUsdOns fallback")
    void enrich_silverUsdOns_emptyHistory_spotFallback() {
        when(silverMarketService.getSpotSilver()).thenReturn(silverSpot());
        when(silverMarketService.getSilverHistory("1W", "USD")).thenReturn(silverHistory());
        when(silverMarketService.getSilverHistory("1Y", "USD")).thenReturn(null);

        WatchlistItemResponse r = new WatchlistItemResponse();
        enricher.enrich(r, "SILVER:USD_ONS");

        // lp.latest() == null → price = spot.getSilverUsdOns()
        assertThat(r.getLastPrice()).isEqualByComparingTo("31.5");
        assertThat(r.getCurrency()).isEqualTo("USD");
        assertThat(r.getChange()).isNull();
    }

    @Test
    @DisplayName("enrich: SILVER:USD_ONS latest var ama open=null → change set edilmez")
    void enrich_silverUsdOns_openNull_noChange() {
        when(silverMarketService.getSpotSilver()).thenReturn(silverSpot());
        SilverHistoryPoint latest = new SilverHistoryPoint();
        latest.setClose(new BigDecimal("31.5"));
        latest.setHigh(new BigDecimal("31.7"));
        latest.setLow(new BigDecimal("31.2"));
        latest.setOpen(null); // open null → if(price!=null && open!=null) false
        when(silverMarketService.getSilverHistory("1W", "USD"))
                .thenReturn(silverHistory(latest));
        when(silverMarketService.getSilverHistory("1Y", "USD")).thenReturn(null);

        WatchlistItemResponse r = new WatchlistItemResponse();
        enricher.enrich(r, "SILVER:USD_ONS");

        assertThat(r.getLastPrice()).isEqualByComparingTo("31.5");
        assertThat(r.getChange()).isNull();
    }

    @Test
    @DisplayName("enrich: SILVER:USD_ONS open=0 → change var, changePct sıfır-bölen guard")
    void enrich_silverUsdOns_openZero_noChangePct() {
        when(silverMarketService.getSpotSilver()).thenReturn(silverSpot());
        SilverHistoryPoint latest = new SilverHistoryPoint();
        latest.setClose(new BigDecimal("31.5"));
        latest.setOpen(BigDecimal.ZERO); // open == 0 → changePct guard
        when(silverMarketService.getSilverHistory("1W", "USD"))
                .thenReturn(silverHistory(latest));
        when(silverMarketService.getSilverHistory("1Y", "USD")).thenReturn(null);

        WatchlistItemResponse r = new WatchlistItemResponse();
        enricher.enrich(r, "SILVER:USD_ONS");

        assertThat(r.getChange()).isEqualByComparingTo("31.5");
        assertThat(r.getChangePercent()).isNull();
    }

    // ── PRECIOUS: EUR_ONS / KG_TRY arm'ları + window high/low + zero-div ──────

    @Test
    @DisplayName("enrich: PLATINUM:EUR_ONS → eurOns + EUR histCurrency + window high/low + change")
    void enrich_platinumEurOns_windowHighLowAndChange() {
        when(preciousMetalService.getSpot(PreciousMetalType.PLATINUM)).thenReturn(preciousSpot());
        PreciousMetalHistoryResponse hist = new PreciousMetalHistoryResponse();
        // EUR arm: cat USD_ONS->USD, EUR_ONS->EUR; window high/low eurOns üzerinden
        hist.setPoints(new ArrayList<>(List.of(
                preciousPoint(null, null, null, new BigDecimal("860")),
                preciousPoint(null, null, null, new BigDecimal("880")))));
        when(preciousMetalService.getHistory(PreciousMetalType.PLATINUM, "1W", "EUR"))
                .thenReturn(hist);

        WatchlistItemResponse r = new WatchlistItemResponse();
        enricher.enrich(r, "PLATINUM:EUR_ONS");

        assertThat(r.getLastPrice()).isEqualByComparingTo("880"); // eurOns spot
        assertThat(r.getCurrency()).isEqualTo("EUR");
        // window: max(860,880,spot880)=880, min(860,880,spot880)=860
        assertThat(r.getHigh()).isEqualByComparingTo("880.0000");
        assertThat(r.getLow()).isEqualByComparingTo("860.0000");
        // open=prev eurOns=860; change=880-860=20
        assertThat(r.getOpen()).isEqualByComparingTo("860");
        assertThat(r.getChange()).isEqualByComparingTo("20");
        assertThat(r.getAsOf()).isNotNull();
    }

    @Test
    @DisplayName("enrich: PALLADIUM:KG_TRY → tryKg + TRY currency (KG_TRY arm) + null value window skip")
    void enrich_palladiumKgTry_corePath() {
        when(preciousMetalService.getSpot(PreciousMetalType.PALLADIUM)).thenReturn(preciousSpot());
        PreciousMetalHistoryResponse hist = new PreciousMetalHistoryResponse();
        // tryKg null'lı nokta → window v==null continue dalı; sonra dolu nokta
        hist.setPoints(new ArrayList<>(List.of(
                preciousPoint(null, null, null, null),
                preciousPoint(null, new BigDecimal("1100000"), null, null))));
        when(preciousMetalService.getHistory(PreciousMetalType.PALLADIUM, "1W", "TRY"))
                .thenReturn(hist);

        WatchlistItemResponse r = new WatchlistItemResponse();
        enricher.enrich(r, "PALLADIUM:KG_TRY");

        assertThat(r.getLastPrice()).isEqualByComparingTo("1100000"); // tryKg
        assertThat(r.getCurrency()).isEqualTo("TRY");
        // window high/low spot(1100000) + 1100000 → 1100000
        assertThat(r.getHigh()).isEqualByComparingTo("1100000.0000");
        assertThat(r.getLow()).isEqualByComparingTo("1100000.0000");
    }

    @Test
    @DisplayName("enrich: PLATINUM:GRAM_TRY prev=0 → change var ama changePct sıfır-bölen guard")
    void enrich_platinumGramTry_prevZero_noChangePct() {
        when(preciousMetalService.getSpot(PreciousMetalType.PLATINUM)).thenReturn(preciousSpot());
        PreciousMetalHistoryResponse hist = new PreciousMetalHistoryResponse();
        hist.setPoints(new ArrayList<>(List.of(
                preciousPoint(BigDecimal.ZERO, null, null, null),
                preciousPoint(new BigDecimal("1100"), null, null, null))));
        when(preciousMetalService.getHistory(PreciousMetalType.PLATINUM, "1W", "TRY"))
                .thenReturn(hist);

        WatchlistItemResponse r = new WatchlistItemResponse();
        enricher.enrich(r, "PLATINUM:GRAM_TRY");

        assertThat(r.getLastPrice()).isEqualByComparingTo("1100"); // tryGram
        assertThat(r.getCurrency()).isEqualTo("TRY");
        // change = 1100 - 0 = 1100, ama prev==0 → changePct null
        assertThat(r.getChange()).isEqualByComparingTo("1100");
        assertThat(r.getChangePercent()).isNull();
    }

    @Test
    @DisplayName("enrich: PLATINUM:BOGUS → UnsupportedOperationException (precious kategori default)")
    void enrich_preciousUnknownCat_throws() {
        when(preciousMetalService.getSpot(PreciousMetalType.PLATINUM)).thenReturn(preciousSpot());
        // histCurrency default -> TRY; sonra cat switch default → throw
        when(preciousMetalService.getHistory(eq(PreciousMetalType.PLATINUM), eq("1W"), eq("TRY")))
                .thenReturn(null);

        WatchlistItemResponse r = new WatchlistItemResponse();
        assertThatThrownBy(() -> enricher.enrich(r, "PLATINUM:BOGUS"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Unsupported precious category");
    }

    @Test
    @DisplayName("enrich: PLATINUM:USD_ONS window'da prev value null → change set edilmez")
    void enrich_platinumUsdOns_prevValueNull_noChange() {
        when(preciousMetalService.getSpot(PreciousMetalType.PLATINUM)).thenReturn(preciousSpot());
        PreciousMetalHistoryResponse hist = new PreciousMetalHistoryResponse();
        // prev usdOns null → preciousPointValue(prev)==null → change atlanır (last!=null&&prev==null)
        hist.setPoints(new ArrayList<>(List.of(
                preciousPoint(null, null, null, null),
                preciousPoint(null, null, new BigDecimal("950"), null))));
        when(preciousMetalService.getHistory(PreciousMetalType.PLATINUM, "1W", "USD"))
                .thenReturn(hist);

        WatchlistItemResponse r = new WatchlistItemResponse();
        enricher.enrich(r, "PLATINUM:USD_ONS");

        assertThat(r.getLastPrice()).isEqualByComparingTo("950");
        assertThat(r.getChange()).isNull();
    }

    // ── enrich(): symbol == null → Yahoo dalı (":" guard false) ───────────────

    @Test
    @DisplayName("enrich: symbol null → Yahoo dalına düşer (contains guard kısa devre)")
    void enrich_nullSymbol_goesToYahoo() {
        CommoditySpotDto spot = new CommoditySpotDto();
        spot.setDisplayPrice(new BigDecimal("2.95"));
        spot.setDisplayCurrency("USD");
        spot.setLastUpdated(null); // asOf fallback: parseLenient(null) → null
        when(yahooCommodityService.getSpot(null)).thenReturn(spot);
        when(yahooCommodityService.getHistory(any(), any(), any())).thenReturn(null);

        WatchlistItemResponse r = new WatchlistItemResponse();
        enricher.enrich(r, null);

        assertThat(r.getLastPrice()).isEqualByComparingTo("2.95");
        assertThat(r.getCurrency()).isEqualTo("USD");
        // lastUpdated null → parseLenient → asOf null
        assertThat(r.getAsOf()).isNull();
    }

    // ── commodityCloses1y / silverCloses1y null-history guard'ları ────────────

    @Test
    @DisplayName("enrich: Yahoo history points==null → commodityCloses1y null → MA set edilmez")
    void enrich_yahoo_historyPointsNull_noMa() {
        CommoditySpotDto spot = new CommoditySpotDto();
        spot.setDisplayPrice(new BigDecimal("2.95"));
        spot.setDisplayCurrency("USD");
        spot.setLastUpdated("2026-05-26T17:00:00");
        when(yahooCommodityService.getSpot("CL=F")).thenReturn(spot);
        CommodityHistoryResponse hist = new CommodityHistoryResponse();
        hist.setPoints(null); // getPoints() == null → return null
        when(yahooCommodityService.getHistory("CL=F", "1Y", "1d")).thenReturn(hist);

        WatchlistItemResponse r = new WatchlistItemResponse();
        enricher.enrich(r, "CL=F");

        assertThat(r.getLastPrice()).isEqualByComparingTo("2.95");
        assertThat(r.getMa20()).isNull();
        assertThat(r.getFiftyTwoWeekHigh()).isNull();
    }

    @Test
    @DisplayName("enrich: Yahoo history dolu ama displayClose null'lar filtrelenir (<2 → MA yok)")
    void enrich_yahoo_nullClosesFiltered_noMa() {
        CommoditySpotDto spot = new CommoditySpotDto();
        spot.setDisplayPrice(new BigDecimal("2.95"));
        spot.setDisplayCurrency("USD");
        spot.setLastUpdated("2026-05-26T17:00:00");
        when(yahooCommodityService.getSpot("CL=F")).thenReturn(spot);
        CommodityHistoryResponse hist = new CommodityHistoryResponse();
        CommodityHistoryPointDto p1 = new CommodityHistoryPointDto();
        p1.setDisplayClose(null);
        CommodityHistoryPointDto p2 = new CommodityHistoryPointDto();
        p2.setDisplayClose(new BigDecimal("3.0"));
        hist.setPoints(new ArrayList<>(List.of(p1, p2)));
        when(yahooCommodityService.getHistory("CL=F", "1Y", "1d")).thenReturn(hist);

        WatchlistItemResponse r = new WatchlistItemResponse();
        enricher.enrich(r, "CL=F");

        // null filtrelendi → 1 eleman → applyMaAnd52w no-op
        assertThat(r.getMa20()).isNull();
        assertThat(r.getFiftyTwoWeekHigh()).isNull();
    }

    @Test
    @DisplayName("enrich: SILVER:GRAM_TRY 1Y history points==null → silverCloses1y null (TRY dalı)")
    void enrich_silver_closes1yPointsNull_noMa() {
        when(silverMarketService.getSpotSilver()).thenReturn(silverSpot());
        SilverHistoryPoint latest = new SilverHistoryPoint();
        latest.setClose(new BigDecimal("34.0"));
        when(silverMarketService.getSilverHistory("1W", "TRY"))
                .thenReturn(silverHistory(latest));
        SilverHistoryResponse yearly = new SilverHistoryResponse();
        yearly.setPoints(null); // cat.contains("USD")==false → "TRY"; getPoints null → null
        when(silverMarketService.getSilverHistory("1Y", "TRY")).thenReturn(yearly);

        WatchlistItemResponse r = new WatchlistItemResponse();
        enricher.enrich(r, "SILVER:GRAM_TRY");

        assertThat(r.getLastPrice()).isEqualByComparingTo("34.0");
        assertThat(r.getMa20()).isNull();
    }

    @Test
    @DisplayName("enrich: SILVER:USD_ONS 1Y history fırlatırsa silverCloses1y catch → null (USD dalı)")
    void enrich_silverUsd_closes1yThrows_noMa() {
        when(silverMarketService.getSpotSilver()).thenReturn(silverSpot());
        when(silverMarketService.getSilverHistory("1W", "USD")).thenReturn(silverHistory());
        when(silverMarketService.getSilverHistory("1Y", "USD"))
                .thenThrow(new RuntimeException("silver 1Y USD down"));

        WatchlistItemResponse r = new WatchlistItemResponse();
        enricher.enrich(r, "SILVER:USD_ONS");

        // empty 1W → price=spot usdOns; 1Y throws → silverCloses1y null → MA yok
        assertThat(r.getLastPrice()).isEqualByComparingTo("31.5");
        assertThat(r.getMa20()).isNull();
    }
}
