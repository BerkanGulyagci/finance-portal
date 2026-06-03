package com.finance.portal.market.application.gold;

import com.finance.portal.common.infrastructure.cache.LastKnownGoodCache;
import com.finance.portal.market.application.fx.model.FxLatestRates;
import com.finance.portal.market.application.fx.model.FxRateItem;
import com.finance.portal.market.application.precious.model.BistMetalDailyPoint;
import com.finance.portal.market.application.precious.model.BistPreciousMetalPoint;
import com.finance.portal.market.application.precious.model.PreciousMetalType;
import com.finance.portal.market.application.precious.model.PriceUnit;
import com.finance.portal.market.application.precious.port.BistMetalFiyatlariPort;
import com.finance.portal.market.application.precious.port.BistPreciousMetalsPort;
import com.finance.portal.market.application.service.MarketFxService;
import com.finance.portal.market.application.stock.model.YahooChartSnapshot;
import com.finance.portal.market.application.stock.model.YahooQuoteSeries;
import com.finance.portal.market.application.stock.model.YahooStockMeta;
import com.finance.portal.market.application.stock.port.YahooStockPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Branch-coverage companion to {@link GoldMarketServiceTest}.
 *
 * <p>Targets the residual uncovered arms the primary suite misses:
 * <ul>
 *   <li>{@code buildSpotFromYahooFallback}: prevClose == 0 → changePercent stays ZERO.</li>
 *   <li>{@code enrichWithYahooOns}: prevClose == 0 → changePercent stays ZERO.</li>
 *   <li>{@code finalizeGoldSpotForTry}: usdTry resolved but onsUsd == null (Yahoo failed) →
 *       conversion block skipped, currency still flipped to TRY.</li>
 *   <li>{@code buildHistoryFromBistGram} / {@code buildHistoryFromBistOns}:
 *       quantityKg == null → volume not derived.</li>
 *   <li>{@code getGoldHistoryFromYahoo}: 3M range arm + non-null OHLC lists carrying null
 *       elements and a short volume list (per-element / boundary guards).</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GoldMarketServiceMoreTest {

    private static final String GOLD_SYMBOL = "GC=F";

    @Mock
    private BistPreciousMetalsPort bistClient;
    @Mock
    private BistMetalFiyatlariPort metalClient;
    @Mock
    private YahooStockPort yahooStockPort;
    @Mock
    private MarketFxService marketFxService;
    @Mock
    private LastKnownGoodCache lkg;

    @InjectMocks
    private GoldMarketService service;

    @BeforeEach
    void stubLkgPassThrough() {
        // LKG wrapper is transparent in tests: resilient(...) just runs the 4th-arg supplier.
        when(lkg.resilient(any(), any(), any(), any()))
                .thenAnswer(inv -> ((java.util.function.Supplier<?>) inv.getArgument(3)).get());
    }

    // ── helpers (NOTE: never name a helper eq/any/etc.) ───────────────────────

    private static BigDecimal bd(String v) {
        return v == null ? null : new BigDecimal(v);
    }

    private static BistPreciousMetalPoint gram(String date, String gramWa, String gramClose,
                                               String gramLow, String gramHigh, String quantityKg,
                                               boolean valid) {
        BistPreciousMetalPoint p = new BistPreciousMetalPoint();
        p.setDate(date);
        p.setGramWeightedAverage(bd(gramWa));
        p.setGramClose(bd(gramClose));
        p.setGramLow(bd(gramLow));
        p.setGramHigh(bd(gramHigh));
        p.setCloseRaw(gramClose == null ? null : bd(gramClose).multiply(BigDecimal.valueOf(1000)));
        p.setWeightedAverageRaw(gramWa == null ? null : bd(gramWa).multiply(BigDecimal.valueOf(1000)));
        p.setVolumeRaw(bd("123456"));
        p.setQuantityKg(bd(quantityKg)); // may be null → exercises volume guard false arm
        p.setTransactionCount(7);
        p.setValidPrice(valid);
        return p;
    }

    private static BistPreciousMetalPoint ons(String date, String close, String high,
                                              String low, String wa, String quantityKg,
                                              boolean valid) {
        BistPreciousMetalPoint p = new BistPreciousMetalPoint();
        p.setDate(date);
        p.setCloseUsdOns(bd(close));
        p.setHighUsdOns(bd(high));
        p.setLowUsdOns(bd(low));
        p.setWeightedAverageUsdOns(bd(wa));
        p.setVolumeUsd(bd("99999"));
        p.setQuantityKg(bd(quantityKg)); // may be null → exercises volume guard false arm
        p.setValidPrice(valid);
        return p;
    }

    private static BistMetalDailyPoint metal(String date, String usdOns, String tryKg,
                                             String tryGram, boolean valid) {
        BistMetalDailyPoint p = new BistMetalDailyPoint();
        p.setDate(date);
        p.setUsdOns(bd(usdOns));
        p.setTryKg(bd(tryKg));
        p.setTryGram(bd(tryGram));
        p.setValidPrice(valid);
        return p;
    }

    private static YahooChartSnapshot metaSnap(String price, String prevClose, String high, String low) {
        YahooStockMeta meta = new YahooStockMeta();
        meta.setRegularMarketPrice(bd(price));
        meta.setPreviousClose(bd(prevClose));
        meta.setRegularMarketDayHigh(bd(high));
        meta.setRegularMarketDayLow(bd(low));
        YahooChartSnapshot snap = new YahooChartSnapshot();
        snap.setMeta(meta);
        return snap;
    }

    private void stubTcmb(String sell) {
        FxRateItem usd = new FxRateItem("USD", bd("40.00"), bd(sell), 1);
        FxLatestRates rates = new FxLatestRates("tcmb", "official", "TRY", "2026-05-30", List.of(usd));
        when(marketFxService.getTcmbLatestRates("USD")).thenReturn(rates);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Spot — Yahoo fallback prevClose == 0 (changePercent ZERO arm)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("spot: BIST null → Yahoo fallback with prevClose=0 → changePercent stays ZERO")
    void getSpotGold_yahooFallback_zeroPrevClose() {
        when(bistClient.fetchLatestValidPoint(PreciousMetalType.GOLD, PriceUnit.TRY_KG)).thenReturn(null);
        // prevClose = 0 → compareTo(ZERO)==0 → skip changePercent computation (false arm)
        when(yahooStockPort.fetchChartWithParams(GOLD_SYMBOL, "1d", "1m"))
                .thenReturn(metaSnap("2300", "0", "2310", "2290"));
        stubTcmb("42.00");

        GoldSpotResponse resp = service.getSpotGold();

        assertThat(resp.getSource()).isEqualTo("Yahoo Finance Fallback");
        assertThat(resp.isFallback()).isTrue();
        assertThat(resp.getOnsChangePercent()).isEqualByComparingTo("0");
        // onsChange = onsUsd - prevClose = 2300 - 0
        assertThat(resp.getOnsChange()).isEqualByComparingTo("2300.00");
        assertThat(resp.getOnsUsd()).isEqualByComparingTo("2300.00");
        // TL conversion still applies (onsUsd non-null, usdTry non-null)
        assertThat(resp.getUsdTry()).isEqualByComparingTo("42.00");
        assertThat(resp.getOnsTry()).isEqualByComparingTo(
                new BigDecimal("2300").multiply(new BigDecimal("42.00"))
                        .setScale(2, java.math.RoundingMode.HALF_UP));
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Spot — enrichWithYahooOns prevClose == 0 (changePercent ZERO arm)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("spot: BIST gram present, BIST ons null, Yahoo prevClose=0 → enrichWithYahooOns ZERO change arm")
    void getSpotGold_yahooOns_zeroPrevClose() {
        BistPreciousMetalPoint g = gram("2026-05-30", "3000", "3001", "2990", "3010", "12.5", true);
        when(bistClient.fetchLatestValidPoint(PreciousMetalType.GOLD, PriceUnit.TRY_KG)).thenReturn(g);
        when(bistClient.fetchLatestValidPoint(PreciousMetalType.GOLD, PriceUnit.USD_ONS)).thenReturn(null);
        // prevClose = 0 → enrichWithYahooOns hits the false arm of compareTo(ZERO)!=0
        when(yahooStockPort.fetchChartWithParams(GOLD_SYMBOL, "1d", "1m"))
                .thenReturn(metaSnap("2350", "0", "2360", "2340"));
        stubTcmb("41.00");

        GoldSpotResponse resp = service.getSpotGold();

        assertThat(resp.isOfficial()).isTrue();
        assertThat(resp.getOnsUsd()).isEqualByComparingTo("2350.00");
        assertThat(resp.getOnsChangePercent()).isEqualByComparingTo("0");
        // onsChange = 2350 - 0
        assertThat(resp.getOnsChange()).isEqualByComparingTo("2350.00");
        assertThat(resp.getOnsHigh()).isEqualByComparingTo("2360.00");
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Spot — finalizeGoldSpotForTry: usdTry resolved but onsUsd null
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("spot: Yahoo fallback throws (onsUsd null) but TCMB ok → currency=TRY, usdTry set, no onsTry")
    void getSpotGold_yahooFallbackThrows_butTcmbResolves() {
        when(bistClient.fetchLatestValidPoint(PreciousMetalType.GOLD, PriceUnit.TRY_KG)).thenReturn(null);
        // Yahoo fallback chart fetch blows up → onsUsd never set (stays null)
        when(yahooStockPort.fetchChartWithParams(GOLD_SYMBOL, "1d", "1m"))
                .thenThrow(new RuntimeException("yahoo down"));
        // TCMB resolves → usdTry non-null → finalize proceeds past the usdTry==null guard,
        // then onsUsd==null skips the conversion block (false arm of getOnsUsd()!=null)
        stubTcmb("43.25");

        GoldSpotResponse resp = service.getSpotGold();

        assertThat(resp.getSource()).isEqualTo("Yahoo Finance Fallback");
        assertThat(resp.isFallback()).isTrue();
        assertThat(resp.getOnsUsd()).isNull();
        assertThat(resp.getUsdTry()).isEqualByComparingTo("43.25");
        assertThat(resp.getCurrency()).isEqualTo("TRY");
        assertThat(resp.getOnsTry()).isNull();
        assertThat(resp.getPriceTl()).isNull();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  History — BIST gram, quantityKg null → volume guard false arm
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("history 1M/TRY: BIST gram point with null quantityKg → volume left unset")
    void getGoldHistory_gram_nullQuantity_noVolume() {
        BistPreciousMetalPoint p = gram("2026-05-30", "2500", "2501", "2490", "2510", null, true);
        when(bistClient.fetchHistory(eq(PreciousMetalType.GOLD), eq(PriceUnit.TRY_KG), anyString(), anyString()))
                .thenReturn(List.of(p));

        GoldHistoryResponse resp = service.getGoldHistory("1M", "TRY");

        assertThat(resp.getSymbol()).isEqualTo("BIST/ALTIN");
        assertThat(resp.getPoints()).hasSize(1);
        GoldHistoryPoint pt = resp.getPoints().get(0);
        assertThat(pt.getClose()).isEqualByComparingTo("2501");
        assertThat(pt.getOpen()).isEqualByComparingTo("2501"); // first → own close
        assertThat(pt.getVolume()).isNull();                   // quantityKg null → guard false
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  History — BIST ons, quantityKg null → volume guard false arm
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("history 3M/USD: BIST ons point with null quantityKg → volume left unset, quantityKg null")
    void getGoldHistory_ons_nullQuantity_noVolume() {
        BistPreciousMetalPoint p = ons("2026-05-30", "2400", "2410", "2390", "2405", null, true);
        when(bistClient.fetchHistory(eq(PreciousMetalType.GOLD), eq(PriceUnit.USD_ONS), anyString(), anyString()))
                .thenReturn(List.of(p));

        GoldHistoryResponse resp = service.getGoldHistory("3M", "USD");

        assertThat(resp.getSymbol()).isEqualTo("BIST/ALTIN-ONS");
        assertThat(resp.getPoints()).hasSize(1);
        GoldHistoryPoint pt = resp.getPoints().get(0);
        assertThat(pt.getClose()).isEqualByComparingTo("2400");
        assertThat(pt.getOpen()).isEqualByComparingTo("2400");
        assertThat(pt.getVolume()).isNull();        // quantityKg null → guard false
        assertThat(pt.getQuantityKg()).isNull();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  History — Yahoo fallback 3M arm + per-element / short-list OHLC guards
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("history 3M/USD: BIST empty → Yahoo '3mo' arm; non-null OHLC lists with null elements & short volume")
    void getGoldHistory_yahoo3M_ohlcElementAndBoundaryGuards() {
        // BIST ons empty → fall through to Yahoo with range 3M (rangeToYahooParams '3M' → 3mo/1d)
        when(bistClient.fetchHistory(eq(PreciousMetalType.GOLD), eq(PriceUnit.USD_ONS), anyString(), anyString()))
                .thenReturn(List.of());

        // 2 timestamps, 2 closes.
        // opens: non-null list but element[0] is null → (opens.get(0)!=null) false arm.
        // highs: non-null list, element[1] null → high set only for i=0.
        // lows : SHORT list (size 1) → at i=1, (i < lows.size()) false arm.
        // volumes: SHORT list (size 1) → at i=1, (i < volumes.size()) false arm.
        YahooQuoteSeries q = new YahooQuoteSeries();
        q.setClose(Arrays.asList(new BigDecimal("2300"), new BigDecimal("2310")));
        q.setOpen(Arrays.asList(null, new BigDecimal("2299")));
        q.setHigh(Arrays.asList(new BigDecimal("2305"), null));
        q.setLow(Arrays.asList(new BigDecimal("2290")));
        q.setVolume(Arrays.asList(150L));
        YahooChartSnapshot snap = new YahooChartSnapshot();
        snap.setTimestamps(Arrays.asList(1_716_000_000L, 1_716_086_400L));
        snap.setQuote(q);

        when(yahooStockPort.fetchChartWithParams(eq(GOLD_SYMBOL), eq("3mo"), eq("1d"))).thenReturn(snap);

        GoldHistoryResponse resp = service.getGoldHistory("3M", "USD");

        assertThat(resp.getSource()).isEqualTo("Yahoo Finance Fallback");
        assertThat(resp.isFallback()).isTrue();
        assertThat(resp.getCurrency()).isEqualTo("USD");
        assertThat(resp.getPoints()).hasSize(2);

        GoldHistoryPoint p0 = resp.getPoints().get(0);
        assertThat(p0.getClose()).isEqualByComparingTo("2300.00");
        assertThat(p0.getOpen()).isNull();                 // opens[0] null → not set
        assertThat(p0.getHigh()).isEqualByComparingTo("2305.00");
        assertThat(p0.getLow()).isEqualByComparingTo("2290.00");
        assertThat(p0.getVolume()).isEqualTo(150L);

        GoldHistoryPoint p1 = resp.getPoints().get(1);
        assertThat(p1.getClose()).isEqualByComparingTo("2310.00");
        assertThat(p1.getOpen()).isEqualByComparingTo("2299.00"); // opens[1] present
        assertThat(p1.getHigh()).isNull();                  // highs[1] null → not set
        assertThat(p1.getLow()).isNull();                   // lows short → boundary false
        assertThat(p1.getVolume()).isNull();                // volumes short → boundary false
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  History — metal-ref (ALL) with ALL points filtered out → empty official resp
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("history ALL/TRY: every metal point invalid → filtered to empty, still official no-fallback resp")
    void getGoldHistory_metalRef_allFilteredEmpty() {
        when(metalClient.fetchMetalPrices(eq(PreciousMetalType.GOLD), anyString(), anyString()))
                .thenReturn(List.of(metal("2026-05-29", "0", "0", "0", false)));

        GoldHistoryResponse resp = service.getGoldHistory("ALL", "TRY");

        assertThat(resp.getSymbol()).isEqualTo("BIST/ALTIN-REF");
        assertThat(resp.isOfficial()).isTrue();
        assertThat(resp.isFallback()).isFalse();
        assertThat(resp.getPoints()).isEmpty(); // valid stream filtered everything out
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Range — 3M BIST date window (start = today - 3 months) via gram path
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("range 3M → BIST start date three months ago (gram path)")
    void rangeToBistDates_threeMonths() {
        when(bistClient.fetchHistory(eq(PreciousMetalType.GOLD), eq(PriceUnit.TRY_KG), anyString(), anyString()))
                .thenReturn(List.of(gram("2026-05-30", "2500", "2501", "2490", "2510", "12.5", true)));

        service.getGoldHistory("3M", "TRY");

        ArgumentCaptor<String> start = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(bistClient)
                .fetchHistory(eq(PreciousMetalType.GOLD), eq(PriceUnit.TRY_KG), start.capture(), anyString());
        assertThat(start.getValue())
                .isEqualTo(java.time.LocalDate.now(java.time.ZoneId.of("Europe/Istanbul"))
                        .minusMonths(3)
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")));
    }
}
