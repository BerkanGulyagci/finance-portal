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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Characterization: {@link CommodityWatchlistEnricher} eski
 * {@code PortfolioWatchlistMarketEnricher.enrichCommodity + applyPreciousHighLowFromHistoryWindow
 * + commodityCloses1y + silverCloses1y} ile aynı davranmalı.
 */
@ExtendWith(MockitoExtension.class)
class CommodityWatchlistEnricherTest {

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

    private static CommoditySpotDto yahooSpot() {
        CommoditySpotDto s = new CommoditySpotDto();
        s.setDisplayPrice(new BigDecimal("2.95"));
        s.setDisplayCurrency("USD");
        s.setPreviousClose(new BigDecimal("2.90"));
        s.setDayHigh(new BigDecimal("3.00"));
        s.setDayLow(new BigDecimal("2.85"));
        s.setChange(new BigDecimal("0.05"));
        s.setChangePercent(new BigDecimal("1.72"));
        s.setLastUpdated("2026-05-26T17:00:00");
        return s;
    }

    private static CommodityHistoryResponse commodityHistory(BigDecimal... closes) {
        CommodityHistoryResponse h = new CommodityHistoryResponse();
        List<CommodityHistoryPointDto> pts = new ArrayList<>();
        for (BigDecimal c : closes) {
            CommodityHistoryPointDto p = new CommodityHistoryPointDto();
            p.setDisplayClose(c);
            pts.add(p);
        }
        h.setPoints(pts);
        return h;
    }

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

    private static SilverHistoryPoint silverPoint(BigDecimal open, BigDecimal high,
                                                  BigDecimal low, BigDecimal close, Long volume) {
        SilverHistoryPoint p = new SilverHistoryPoint();
        p.setOpen(open);
        p.setHigh(high);
        p.setLow(low);
        p.setClose(close);
        p.setVolume(volume);
        return p;
    }

    private static SilverHistoryResponse silverHistory(SilverHistoryPoint... pts) {
        SilverHistoryResponse h = new SilverHistoryResponse();
        h.setPoints(new ArrayList<>(List.of(pts)));
        return h;
    }

    private static SilverHistoryResponse silverHistoryCloses(BigDecimal... closes) {
        List<SilverHistoryPoint> pts = new ArrayList<>();
        for (BigDecimal c : closes) {
            SilverHistoryPoint p = new SilverHistoryPoint();
            p.setClose(c);
            pts.add(p);
        }
        SilverHistoryResponse h = new SilverHistoryResponse();
        h.setPoints(pts);
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

    // ── Yahoo (sembolde ":" yok) ──────────────────────────────────────────────

    @Test
    @DisplayName("enrich: Yahoo emtia (NG=F) → displayPrice/currency + open=prevClose + change")
    void enrich_yahoo_corePath() {
        when(yahooCommodityService.getSpot("NG=F")).thenReturn(yahooSpot());
        when(yahooCommodityService.getHistory(any(), any(), any())).thenReturn(null);

        WatchlistItemResponse r = new WatchlistItemResponse();
        enricher.enrich(r, "NG=F");

        assertThat(r.getLastPrice()).isEqualByComparingTo("2.95");
        assertThat(r.getCurrency()).isEqualTo("USD");
        assertThat(r.getOpen()).isEqualByComparingTo("2.90");
        assertThat(r.getHigh()).isEqualByComparingTo("3.00");
        assertThat(r.getLow()).isEqualByComparingTo("2.85");
        assertThat(r.getChange()).isEqualByComparingTo("0.05");
        assertThat(r.getChangePercent()).isEqualByComparingTo("1.72");
        assertThat(r.getVolume()).isNull(); // emtia için hacim doldurulmaz
        assertThat(r.getAsOf()).isNotNull();
    }

    @Test
    @DisplayName("enrich: Yahoo 1Y history → 52w high/low + MA20")
    void enrich_yahoo_trendMetrics() {
        when(yahooCommodityService.getSpot("NG=F")).thenReturn(yahooSpot());
        BigDecimal[] vals = new BigDecimal[20];
        for (int i = 0; i < 20; i++) {
            vals[i] = new BigDecimal(i + 1);
        }
        when(yahooCommodityService.getHistory("NG=F", "1Y", "1d"))
                .thenReturn(commodityHistory(vals));

        WatchlistItemResponse r = new WatchlistItemResponse();
        enricher.enrich(r, "NG=F");

        assertThat(r.getFiftyTwoWeekHigh()).isEqualByComparingTo("20");
        assertThat(r.getFiftyTwoWeekLow()).isEqualByComparingTo("1");
        assertThat(r.getMa20()).isEqualByComparingTo("10.5");
    }

    @Test
    @DisplayName("enrich: Yahoo 1Y history fırlatırsa core fields korunur")
    void enrich_yahoo_historyThrows_isSwallowed() {
        when(yahooCommodityService.getSpot("NG=F")).thenReturn(yahooSpot());
        when(yahooCommodityService.getHistory(any(), any(), any()))
                .thenThrow(new RuntimeException("Yahoo down"));

        WatchlistItemResponse r = new WatchlistItemResponse();
        enricher.enrich(r, "NG=F");

        assertThat(r.getLastPrice()).isEqualByComparingTo("2.95");
        assertThat(r.getMa20()).isNull();
    }

    // ── Silver (SILVER:cat) ───────────────────────────────────────────────────

    @Test
    @DisplayName("enrich: SILVER:GRAM_TRY → 1W latest close + open/high/low + change vs prev")
    void enrich_silverGramTry_corePath() {
        when(silverMarketService.getSpotSilver()).thenReturn(silverSpot());
        SilverHistoryResponse weekly = silverHistory(
                silverPoint(new BigDecimal("33.0"), new BigDecimal("33.5"),
                        new BigDecimal("32.8"), new BigDecimal("33.2"), 100L),
                silverPoint(new BigDecimal("33.2"), new BigDecimal("34.5"),
                        new BigDecimal("33.0"), new BigDecimal("34.0"), 200L));
        when(silverMarketService.getSilverHistory("1W", "TRY")).thenReturn(weekly);
        when(silverMarketService.getSilverHistory("1Y", "TRY")).thenReturn(null);

        WatchlistItemResponse r = new WatchlistItemResponse();
        enricher.enrich(r, "SILVER:GRAM_TRY");

        assertThat(r.getLastPrice()).isEqualByComparingTo("34.0"); // latest close
        assertThat(r.getCurrency()).isEqualTo("TRY");
        assertThat(r.getOpen()).isEqualByComparingTo("33.2"); // latest.open
        assertThat(r.getHigh()).isEqualByComparingTo("34.5");
        assertThat(r.getLow()).isEqualByComparingTo("33.0");
        // Gümüş emtia için "Hacim" gösterilmez → volume hep null kalır
        assertThat(r.getVolume()).isNull();
        // change = latestClose - prevClose = 34.0 - 33.2 = 0.8
        assertThat(r.getChange()).isEqualByComparingTo("0.8");
        assertThat(r.getAsOf()).isNotNull();
    }

    @Test
    @DisplayName("enrich: SILVER:GRAM_TRY history boş → spot gram close fallback")
    void enrich_silverGramTry_emptyHistory_spotFallback() {
        when(silverMarketService.getSpotSilver()).thenReturn(silverSpot());
        when(silverMarketService.getSilverHistory("1W", "TRY"))
                .thenReturn(silverHistory()); // boş points
        when(silverMarketService.getSilverHistory("1Y", "TRY")).thenReturn(null);

        WatchlistItemResponse r = new WatchlistItemResponse();
        enricher.enrich(r, "SILVER:GRAM_TRY");

        // latest yok → spot.silverGramCloseTry
        assertThat(r.getLastPrice()).isEqualByComparingTo("34.2");
        assertThat(r.getHigh()).isEqualByComparingTo("35.0");
        assertThat(r.getLow()).isEqualByComparingTo("33.8");
        assertThat(r.getCurrency()).isEqualTo("TRY");
    }

    @Test
    @DisplayName("enrich: SILVER:USD_ONS → 1W USD latest + change vs open + 1Y USD trend")
    void enrich_silverUsdOns_corePathAndTrend() {
        when(silverMarketService.getSpotSilver()).thenReturn(silverSpot());
        SilverHistoryResponse weekly = silverHistory(
                silverPoint(new BigDecimal("31.0"), new BigDecimal("31.6"),
                        new BigDecimal("30.9"), new BigDecimal("31.5"), 50L));
        when(silverMarketService.getSilverHistory("1W", "USD")).thenReturn(weekly);
        BigDecimal[] vals = new BigDecimal[20];
        for (int i = 0; i < 20; i++) {
            vals[i] = new BigDecimal(i + 1);
        }
        when(silverMarketService.getSilverHistory("1Y", "USD"))
                .thenReturn(silverHistoryCloses(vals));

        WatchlistItemResponse r = new WatchlistItemResponse();
        enricher.enrich(r, "SILVER:USD_ONS");

        assertThat(r.getLastPrice()).isEqualByComparingTo("31.5");
        assertThat(r.getCurrency()).isEqualTo("USD");
        // change = close - open = 31.5 - 31.0 = 0.5
        assertThat(r.getChange()).isEqualByComparingTo("0.5");
        assertThat(r.getFiftyTwoWeekHigh()).isEqualByComparingTo("20");
        assertThat(r.getMa20()).isEqualByComparingTo("10.5");
    }

    @Test
    @DisplayName("enrich: SILVER:UNKNOWN → UnsupportedOperationException (silver kategori)")
    void enrich_silverUnknownCat_throws() {
        when(silverMarketService.getSpotSilver()).thenReturn(silverSpot());

        WatchlistItemResponse r = new WatchlistItemResponse();
        assertThatThrownBy(() -> enricher.enrich(r, "SILVER:BOGUS"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Unsupported silver category");
    }

    // ── Precious (PLATINUM/PALLADIUM:cat) ─────────────────────────────────────

    @Test
    @DisplayName("enrich: PLATINUM:USD_ONS → usdOns price + USD currency + change vs prev")
    void enrich_platinumUsdOns_corePath() {
        when(preciousMetalService.getSpot(PreciousMetalType.PLATINUM)).thenReturn(preciousSpot());
        PreciousMetalHistoryResponse hist = new PreciousMetalHistoryResponse();
        PreciousMetalHistoryPoint prev = new PreciousMetalHistoryPoint();
        prev.setUsdOns(new BigDecimal("940"));
        PreciousMetalHistoryPoint last = new PreciousMetalHistoryPoint();
        last.setUsdOns(new BigDecimal("950"));
        hist.setPoints(new ArrayList<>(List.of(prev, last)));
        when(preciousMetalService.getHistory(PreciousMetalType.PLATINUM, "1W", "USD"))
                .thenReturn(hist);

        WatchlistItemResponse r = new WatchlistItemResponse();
        enricher.enrich(r, "PLATINUM:USD_ONS");

        assertThat(r.getLastPrice()).isEqualByComparingTo("950");
        assertThat(r.getCurrency()).isEqualTo("USD");
        // open=prev=940, change=950-940=10
        assertThat(r.getOpen()).isEqualByComparingTo("940");
        assertThat(r.getChange()).isEqualByComparingTo("10");
        assertThat(r.getAsOf()).isNotNull();
    }

    @Test
    @DisplayName("enrich: PALLADIUM:GRAM_TRY → tryGram price + TRY currency (TRY histCurrency)")
    void enrich_palladiumGramTry_corePath() {
        when(preciousMetalService.getSpot(PreciousMetalType.PALLADIUM)).thenReturn(preciousSpot());
        when(preciousMetalService.getHistory(eq(PreciousMetalType.PALLADIUM), eq("1W"), eq("TRY")))
                .thenReturn(null);

        WatchlistItemResponse r = new WatchlistItemResponse();
        enricher.enrich(r, "PALLADIUM:GRAM_TRY");

        assertThat(r.getLastPrice()).isEqualByComparingTo("1100");
        assertThat(r.getCurrency()).isEqualTo("TRY");
    }

    @Test
    @DisplayName("enrich: precious getHistory çağrısı (spot'tan ÖNCE değil sonra, try-dışı) → propagate")
    void enrich_preciousHistoryThrows_propagates() {
        // enrichPrecious'ta getHistory çağrısı try/catch DIŞINDA (sadece sonraki
        // window hesabı sarılı) → bu noktadaki hata yukarı taşınır (eski davranış).
        when(preciousMetalService.getSpot(PreciousMetalType.PLATINUM)).thenReturn(preciousSpot());
        when(preciousMetalService.getHistory(any(), any(), any()))
                .thenThrow(new RuntimeException("BIST down"));

        WatchlistItemResponse r = new WatchlistItemResponse();
        assertThatThrownBy(() -> enricher.enrich(r, "PLATINUM:EUR_ONS"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("BIST down");
    }

    @Test
    @DisplayName("enrich: precious window hesabı (try-içi) hata → spot fiyat/currency korunur")
    void enrich_preciousWindowError_isSwallowed() {
        // getHistory başarılı ama window noktasında null değerler → try-catch yutar,
        // spot'tan set edilen lastPrice/currency korunur.
        when(preciousMetalService.getSpot(PreciousMetalType.PLATINUM)).thenReturn(preciousSpot());
        PreciousMetalHistoryResponse hist = new PreciousMetalHistoryResponse();
        hist.setPoints(new ArrayList<>()); // boş → window null, change set edilmez
        when(preciousMetalService.getHistory(PreciousMetalType.PLATINUM, "1W", "EUR"))
                .thenReturn(hist);

        WatchlistItemResponse r = new WatchlistItemResponse();
        enricher.enrich(r, "PLATINUM:EUR_ONS");

        assertThat(r.getLastPrice()).isEqualByComparingTo("880"); // eurOns
        assertThat(r.getCurrency()).isEqualTo("EUR");
        assertThat(r.getChange()).isNull();
    }

    @Test
    @DisplayName("enrich: tanınmayan metal sembolü → UnsupportedOperationException")
    void enrich_unknownMetal_throws() {
        WatchlistItemResponse r = new WatchlistItemResponse();
        assertThatThrownBy(() -> enricher.enrich(r, "COPPER:USD_ONS"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Unsupported precious metal symbol");
    }
}
