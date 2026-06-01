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
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GoldMarketServiceTest {

    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
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
        // LKG sarmalayıcı testte şeffaf: resilient(...) doğrudan asıl çekimi (4. arg) çağırır.
        when(lkg.resilient(any(), any(), any(), any()))
                .thenAnswer(inv -> ((java.util.function.Supplier<?>) inv.getArgument(3)).get());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static BigDecimal bd(String v) {
        return v == null ? null : new BigDecimal(v);
    }

    /** TL/Kg gram-mode BIST point (gram fields populated). */
    private static BistPreciousMetalPoint gramPoint(String date, String gramWa, String gramClose,
                                                    String gramLow, String gramHigh, boolean valid) {
        BistPreciousMetalPoint p = new BistPreciousMetalPoint();
        p.setDate(date);
        p.setGramWeightedAverage(bd(gramWa));
        p.setGramClose(bd(gramClose));
        p.setGramLow(bd(gramLow));
        p.setGramHigh(bd(gramHigh));
        p.setCloseRaw(gramClose == null ? null : bd(gramClose).multiply(BigDecimal.valueOf(1000)));
        p.setWeightedAverageRaw(gramWa == null ? null : bd(gramWa).multiply(BigDecimal.valueOf(1000)));
        p.setVolumeRaw(bd("123456"));
        p.setQuantityKg(bd("12.5"));
        p.setTransactionCount(42);
        p.setValidPrice(valid);
        return p;
    }

    /** USD/Ons mode BIST point. */
    private static BistPreciousMetalPoint onsPoint(String date, String close, String high,
                                                   String low, String wa, boolean valid) {
        BistPreciousMetalPoint p = new BistPreciousMetalPoint();
        p.setDate(date);
        p.setCloseUsdOns(bd(close));
        p.setHighUsdOns(bd(high));
        p.setLowUsdOns(bd(low));
        p.setWeightedAverageUsdOns(bd(wa));
        p.setVolumeUsd(bd("99999"));
        p.setQuantityKg(bd("3.0"));
        p.setValidPrice(valid);
        return p;
    }

    private static BistMetalDailyPoint metalPoint(String date, String usdOns, String tryKg,
                                                  String tryGram, boolean valid) {
        BistMetalDailyPoint p = new BistMetalDailyPoint();
        p.setDate(date);
        p.setUsdOns(bd(usdOns));
        p.setTryKg(bd(tryKg));
        p.setTryGram(bd(tryGram));
        p.setValidPrice(valid);
        return p;
    }

    private static YahooChartSnapshot metaSnapshot(String price, String prevClose, String high, String low) {
        YahooStockMeta meta = new YahooStockMeta();
        meta.setRegularMarketPrice(bd(price));
        meta.setPreviousClose(bd(prevClose));
        meta.setRegularMarketDayHigh(bd(high));
        meta.setRegularMarketDayLow(bd(low));
        YahooChartSnapshot snap = new YahooChartSnapshot();
        snap.setMeta(meta);
        return snap;
    }

    private void stubTcmbUsdTry(String sell) {
        FxRateItem usd = new FxRateItem("USD", bd("40.00"), bd(sell), 1);
        FxLatestRates rates = new FxLatestRates("tcmb", "official", "TRY", "2026-05-30", List.of(usd));
        when(marketFxService.getTcmbLatestRates("USD")).thenReturn(rates);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  getSpotGold
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("spot: BIST gram + BIST ons → official, derived prices, ons enriched from BIST, TL conversion")
    void getSpotGold_bistGramAndBistOns() {
        BistPreciousMetalPoint gram = gramPoint("2026-05-30", "3000", "3001", "2990", "3010", true);
        BistPreciousMetalPoint ons = onsPoint("2026-05-30", "2400", "2410", "2390", "2405", true);
        when(bistClient.fetchLatestValidPoint(PreciousMetalType.GOLD, PriceUnit.TRY_KG)).thenReturn(gram);
        when(bistClient.fetchLatestValidPoint(PreciousMetalType.GOLD, PriceUnit.USD_ONS)).thenReturn(ons);
        // enrichWithBistOns + buildSpotFromBist's enrichWithYahooOns both call Yahoo for prevClose
        when(yahooStockPort.fetchChartWithParams(GOLD_SYMBOL, "1d", "1m"))
                .thenReturn(metaSnapshot("2400", "2380", "2415", "2385"));
        stubTcmbUsdTry("40.50");

        GoldSpotResponse resp = service.getSpotGold();

        assertThat(resp.getSource()).isEqualTo("Borsa İstanbul");
        assertThat(resp.isOfficial()).isTrue();
        assertThat(resp.isFallback()).isFalse();
        assertThat(resp.isStale()).isFalse();
        assertThat(resp.getBistDate()).isEqualTo("2026-05-30");

        // derived theoretical prices off gram WA = 3000
        assertThat(resp.getOfficialPureGoldGramTry()).isEqualByComparingTo("3000");
        assertThat(resp.getGramGoldTry()).isEqualByComparingTo("3000.00");
        assertThat(resp.getQuarterGoldTry())
                .isEqualByComparingTo(new BigDecimal("3000").multiply(new BigDecimal("1.754"))
                        .multiply(new BigDecimal("0.9166")).setScale(2, java.math.RoundingMode.HALF_UP));
        assertThat(resp.getTwentyTwoKBraceletTry())
                .isEqualByComparingTo(new BigDecimal("3000").multiply(new BigDecimal("0.9166"))
                        .setScale(2, java.math.RoundingMode.HALF_UP));
        // legacy aliases mirror new fields
        assertThat(resp.getGramTl()).isEqualByComparingTo(resp.getGramGoldTry());
        assertThat(resp.getCeyrekTl()).isEqualByComparingTo(resp.getQuarterGoldTry());

        // ons from BIST close (2400), change from Yahoo prevClose 2380
        assertThat(resp.getOnsUsd()).isEqualByComparingTo("2400");
        assertThat(resp.getOnsHigh()).isEqualByComparingTo("2410");
        assertThat(resp.getOnsLow()).isEqualByComparingTo("2390");
        assertThat(resp.getOnsChange()).isEqualByComparingTo("20.00");
        assertThat(resp.getOnsPreviousClose()).isEqualByComparingTo("2380.00");

        // TL conversion: onsTry = onsUsd * usdTry
        assertThat(resp.getUsdTry()).isEqualByComparingTo("40.50");
        assertThat(resp.getCurrency()).isEqualTo("TRY");
        assertThat(resp.getOnsTry()).isEqualByComparingTo(new BigDecimal("2400").multiply(new BigDecimal("40.50"))
                .setScale(2, java.math.RoundingMode.HALF_UP));
        assertThat(resp.getPriceTl()).isEqualByComparingTo(resp.getOnsTry());
    }

    @Test
    @DisplayName("spot: BIST gram present but BIST ons null → Yahoo ons fallback")
    void getSpotGold_bistGramYahooOnsFallback() {
        BistPreciousMetalPoint gram = gramPoint("2026-05-29", "2500", "2501", "2490", "2510", true);
        when(bistClient.fetchLatestValidPoint(PreciousMetalType.GOLD, PriceUnit.TRY_KG)).thenReturn(gram);
        when(bistClient.fetchLatestValidPoint(PreciousMetalType.GOLD, PriceUnit.USD_ONS)).thenReturn(null);
        when(yahooStockPort.fetchChartWithParams(GOLD_SYMBOL, "1d", "1m"))
                .thenReturn(metaSnapshot("2350", "2300", "2360", "2340"));
        stubTcmbUsdTry("41.00");

        GoldSpotResponse resp = service.getSpotGold();

        assertThat(resp.isOfficial()).isTrue();
        assertThat(resp.getOnsUsd()).isEqualByComparingTo("2350.00");
        assertThat(resp.getOnsHigh()).isEqualByComparingTo("2360.00");
        assertThat(resp.getOnsChange()).isEqualByComparingTo("50.00");
        // changePercent = (2350-2300)/2300*100
        assertThat(resp.getOnsChangePercent()).isEqualByComparingTo(
                new BigDecimal("2350").subtract(new BigDecimal("2300"))
                        .divide(new BigDecimal("2300"), 4, java.math.RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).setScale(2, java.math.RoundingMode.HALF_UP));
        assertThat(resp.getOnsTry()).isNotNull();
    }

    @Test
    @DisplayName("spot: BIST ons close null → enrichWithBistOns skipped, falls to Yahoo ons")
    void getSpotGold_bistOnsCloseNull_usesYahoo() {
        BistPreciousMetalPoint gram = gramPoint("2026-05-29", "2500", "2501", "2490", "2510", true);
        BistPreciousMetalPoint ons = onsPoint("2026-05-29", null, null, null, null, true); // close null
        when(bistClient.fetchLatestValidPoint(PreciousMetalType.GOLD, PriceUnit.TRY_KG)).thenReturn(gram);
        when(bistClient.fetchLatestValidPoint(PreciousMetalType.GOLD, PriceUnit.USD_ONS)).thenReturn(ons);
        when(yahooStockPort.fetchChartWithParams(GOLD_SYMBOL, "1d", "1m"))
                .thenReturn(metaSnapshot("2350", "2300", "2360", "2340"));
        stubTcmbUsdTry("41.00");

        GoldSpotResponse resp = service.getSpotGold();

        // ons from Yahoo, not BIST (which had null close)
        assertThat(resp.getOnsUsd()).isEqualByComparingTo("2350.00");
    }

    @Test
    @DisplayName("spot: BIST ons prevClose=0 → changePercent zero branch")
    void getSpotGold_bistOnsZeroPrevClose() {
        BistPreciousMetalPoint gram = gramPoint("2026-05-30", "3000", "3001", "2990", "3010", true);
        BistPreciousMetalPoint ons = onsPoint("2026-05-30", "2400", "2410", "2390", "2405", true);
        when(bistClient.fetchLatestValidPoint(PreciousMetalType.GOLD, PriceUnit.TRY_KG)).thenReturn(gram);
        when(bistClient.fetchLatestValidPoint(PreciousMetalType.GOLD, PriceUnit.USD_ONS)).thenReturn(ons);
        when(yahooStockPort.fetchChartWithParams(GOLD_SYMBOL, "1d", "1m"))
                .thenReturn(metaSnapshot("2400", "0", "2415", "2385")); // prevClose 0
        stubTcmbUsdTry("40.00");

        GoldSpotResponse resp = service.getSpotGold();

        assertThat(resp.getOnsChangePercent()).isEqualByComparingTo("0");
        assertThat(resp.getOnsChange()).isEqualByComparingTo("2400.00");
    }

    @Test
    @DisplayName("spot: BIST ons present but Yahoo prevClose lookup throws → ons change zeroed")
    void getSpotGold_bistOnsYahooThrows() {
        BistPreciousMetalPoint gram = gramPoint("2026-05-30", "3000", "3001", "2990", "3010", true);
        BistPreciousMetalPoint ons = onsPoint("2026-05-30", "2400", "2410", "2390", "2405", true);
        when(bistClient.fetchLatestValidPoint(PreciousMetalType.GOLD, PriceUnit.TRY_KG)).thenReturn(gram);
        when(bistClient.fetchLatestValidPoint(PreciousMetalType.GOLD, PriceUnit.USD_ONS)).thenReturn(ons);
        when(yahooStockPort.fetchChartWithParams(GOLD_SYMBOL, "1d", "1m"))
                .thenThrow(new RuntimeException("yahoo down"));
        stubTcmbUsdTry("40.00");

        GoldSpotResponse resp = service.getSpotGold();

        assertThat(resp.getOnsUsd()).isEqualByComparingTo("2400");
        assertThat(resp.getOnsChange()).isEqualByComparingTo("0");
        assertThat(resp.getOnsChangePercent()).isEqualByComparingTo("0");
        // bid/ask still computed from close
        assertThat(resp.getBid()).isEqualByComparingTo("2399.50");
        assertThat(resp.getAsk()).isEqualByComparingTo("2400.50");
    }

    @Test
    @DisplayName("spot: BIST gram null → Yahoo GC=F fallback path")
    void getSpotGold_bistNull_yahooFallback() {
        when(bistClient.fetchLatestValidPoint(PreciousMetalType.GOLD, PriceUnit.TRY_KG)).thenReturn(null);
        when(yahooStockPort.fetchChartWithParams(GOLD_SYMBOL, "1d", "1m"))
                .thenReturn(metaSnapshot("2300", "2250", "2310", "2290"));
        stubTcmbUsdTry("42.00");

        GoldSpotResponse resp = service.getSpotGold();

        assertThat(resp.getSource()).isEqualTo("Yahoo Finance Fallback");
        assertThat(resp.isOfficial()).isFalse();
        assertThat(resp.isFallback()).isTrue();
        assertThat(resp.getOnsUsd()).isEqualByComparingTo("2300.00");
        assertThat(resp.getPrice()).isEqualByComparingTo("2300.00");
        assertThat(resp.getBid()).isEqualByComparingTo("2299.50");
        assertThat(resp.getAsk()).isEqualByComparingTo("2300.50");
        assertThat(resp.getOnsChange()).isEqualByComparingTo("50.00");
        assertThat(resp.getOnsTry()).isEqualByComparingTo(new BigDecimal("2300").multiply(new BigDecimal("42.00"))
                .setScale(2, java.math.RoundingMode.HALF_UP));
        // never asks BIST for ons in this path
        verify(bistClient, never()).fetchLatestValidPoint(PreciousMetalType.GOLD, PriceUnit.USD_ONS);
    }

    @Test
    @DisplayName("spot: BIST gram null AND Yahoo fallback throws → graceful empty-ish response")
    void getSpotGold_bistNull_yahooAlsoThrows() {
        when(bistClient.fetchLatestValidPoint(PreciousMetalType.GOLD, PriceUnit.TRY_KG)).thenReturn(null);
        when(yahooStockPort.fetchChartWithParams(GOLD_SYMBOL, "1d", "1m"))
                .thenThrow(new RuntimeException("yahoo down"));
        when(marketFxService.getTcmbLatestRates("USD")).thenThrow(new RuntimeException("tcmb down"));

        GoldSpotResponse resp = service.getSpotGold();

        assertThat(resp.getSource()).isEqualTo("Yahoo Finance Fallback");
        assertThat(resp.isFallback()).isTrue();
        assertThat(resp.getOnsUsd()).isNull();
        assertThat(resp.getLastUpdated()).isNotNull();
        // tcmb failed → no conversion
        assertThat(resp.getUsdTry()).isNull();
    }

    @Test
    @DisplayName("spot: gram present but gramWeightedAverage null → treated as BIST-unavailable, Yahoo fallback")
    void getSpotGold_gramWaNull_fallback() {
        BistPreciousMetalPoint gram = gramPoint("2026-05-30", null, "3001", "2990", "3010", true);
        when(bistClient.fetchLatestValidPoint(PreciousMetalType.GOLD, PriceUnit.TRY_KG)).thenReturn(gram);
        when(yahooStockPort.fetchChartWithParams(GOLD_SYMBOL, "1d", "1m"))
                .thenReturn(metaSnapshot("2300", "2250", "2310", "2290"));
        stubTcmbUsdTry("42.00");

        GoldSpotResponse resp = service.getSpotGold();

        assertThat(resp.isFallback()).isTrue();
        assertThat(resp.getSource()).isEqualTo("Yahoo Finance Fallback");
    }

    @Test
    @DisplayName("spot: TCMB rates null list → no TL conversion (usdTry stays null)")
    void getSpotGold_tcmbRatesNull() {
        BistPreciousMetalPoint gram = gramPoint("2026-05-30", "3000", "3001", "2990", "3010", true);
        BistPreciousMetalPoint ons = onsPoint("2026-05-30", "2400", "2410", "2390", "2405", true);
        when(bistClient.fetchLatestValidPoint(PreciousMetalType.GOLD, PriceUnit.TRY_KG)).thenReturn(gram);
        when(bistClient.fetchLatestValidPoint(PreciousMetalType.GOLD, PriceUnit.USD_ONS)).thenReturn(ons);
        when(yahooStockPort.fetchChartWithParams(GOLD_SYMBOL, "1d", "1m"))
                .thenReturn(metaSnapshot("2400", "2380", "2415", "2385"));
        FxLatestRates rates = new FxLatestRates("tcmb", "official", "TRY", "2026-05-30", null);
        when(marketFxService.getTcmbLatestRates("USD")).thenReturn(rates);

        GoldSpotResponse resp = service.getSpotGold();

        assertThat(resp.getUsdTry()).isNull();
        assertThat(resp.getOnsTry()).isNull();
    }

    @Test
    @DisplayName("spot: TCMB has USD but sell is zero → filtered out, no usdTry")
    void getSpotGold_tcmbUsdSellZero() {
        BistPreciousMetalPoint gram = gramPoint("2026-05-30", "3000", "3001", "2990", "3010", true);
        BistPreciousMetalPoint ons = onsPoint("2026-05-30", "2400", "2410", "2390", "2405", true);
        when(bistClient.fetchLatestValidPoint(PreciousMetalType.GOLD, PriceUnit.TRY_KG)).thenReturn(gram);
        when(bistClient.fetchLatestValidPoint(PreciousMetalType.GOLD, PriceUnit.USD_ONS)).thenReturn(ons);
        when(yahooStockPort.fetchChartWithParams(GOLD_SYMBOL, "1d", "1m"))
                .thenReturn(metaSnapshot("2400", "2380", "2415", "2385"));
        FxRateItem usd = new FxRateItem("USD", bd("40"), bd("0"), 1);
        when(marketFxService.getTcmbLatestRates("USD"))
                .thenReturn(new FxLatestRates("tcmb", "official", "TRY", "x", List.of(usd)));

        GoldSpotResponse resp = service.getSpotGold();

        assertThat(resp.getUsdTry()).isNull();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  getGoldHistory — routing
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("history ALL/TRY → metal-ref AU endpoint, gram fields populated, synthetic open")
    void getGoldHistory_allTry_metalRef() {
        List<BistMetalDailyPoint> raw = List.of(
                metalPoint("2026-05-28", "900", "1800000", "1800", true),
                metalPoint("2026-05-29", "0", "0", "0", false),       // filtered
                metalPoint("2026-05-30", "910", "1810000", "1810", true));
        when(metalClient.fetchMetalPrices(eq(PreciousMetalType.GOLD), anyString(), anyString())).thenReturn(raw);

        GoldHistoryResponse resp = service.getGoldHistory("ALL", "TRY");

        assertThat(resp.getSymbol()).isEqualTo("BIST/ALTIN-REF");
        assertThat(resp.getCurrency()).isEqualTo("TRY");
        assertThat(resp.isOfficial()).isTrue();
        assertThat(resp.getPoints()).hasSize(2);
        GoldHistoryPoint first = resp.getPoints().get(0);
        assertThat(first.getClose()).isEqualByComparingTo("1800");
        assertThat(first.getOpen()).isEqualByComparingTo("1800");   // prevClose null → own close
        assertThat(first.getCloseTryKg()).isEqualByComparingTo("1800000");
        GoldHistoryPoint second = resp.getPoints().get(1);
        assertThat(second.getOpen()).isEqualByComparingTo("1800");  // prev close carried over
        assertThat(second.getClose()).isEqualByComparingTo("1810");
    }

    @Test
    @DisplayName("history 5Y/USD → metal-ref AU endpoint, usd ons fields")
    void getGoldHistory_fiveYearUsd_metalRef() {
        when(metalClient.fetchMetalPrices(eq(PreciousMetalType.GOLD), anyString(), anyString()))
                .thenReturn(List.of(metalPoint("2026-05-30", "910", "1810000", "1810", true)));

        GoldHistoryResponse resp = service.getGoldHistory("5Y", "USD");

        assertThat(resp.getCurrency()).isEqualTo("USD");
        assertThat(resp.getPoints()).hasSize(1);
        assertThat(resp.getPoints().get(0).getClose()).isEqualByComparingTo("910");
        assertThat(resp.getPoints().get(0).getWeightedAverageUsdOns()).isEqualByComparingTo("910");
    }

    @Test
    @DisplayName("history 1M/TRY → BIST gram endpoint, OHLC + volume")
    void getGoldHistory_oneMonthTry_bistGram() {
        BistPreciousMetalPoint p1 = gramPoint("2026-05-29", "2500", "2501", "2490", "2510", true);
        BistPreciousMetalPoint p2 = gramPoint("2026-05-30", "2520", "2521", "2510", "2530", true);
        when(bistClient.fetchHistory(eq(PreciousMetalType.GOLD), eq(PriceUnit.TRY_KG), anyString(), anyString()))
                .thenReturn(List.of(p1, p2));

        GoldHistoryResponse resp = service.getGoldHistory("1M", "TRY");

        assertThat(resp.getSymbol()).isEqualTo("BIST/ALTIN");
        assertThat(resp.getCurrency()).isEqualTo("TRY");
        assertThat(resp.isOfficial()).isTrue();
        assertThat(resp.getPoints()).hasSize(2);
        GoldHistoryPoint pt = resp.getPoints().get(0);
        assertThat(pt.getClose()).isEqualByComparingTo("2501");
        assertThat(pt.getHigh()).isEqualByComparingTo("2510");
        assertThat(pt.getLow()).isEqualByComparingTo("2490");
        assertThat(pt.getOpen()).isEqualByComparingTo("2501");  // first → own close
        assertThat(pt.getVolume()).isEqualTo(12500L);           // 12.5 kg * 1000
        assertThat(resp.getPoints().get(1).getOpen()).isEqualByComparingTo("2501"); // prev close
    }

    @Test
    @DisplayName("history 1M/TRY filters validPrice=false then falls back to Yahoo when empty")
    void getGoldHistory_bistGramEmpty_yahooFallback() {
        BistPreciousMetalPoint invalid = gramPoint("2026-05-30", "2500", "2501", "2490", "2510", false);
        when(bistClient.fetchHistory(eq(PreciousMetalType.GOLD), eq(PriceUnit.TRY_KG), anyString(), anyString()))
                .thenReturn(List.of(invalid));
        YahooChartSnapshot snap = chartSnapshotStr(
                List.of(1_716_000_000L, 1_716_086_400L),
                List.of("2300", "2310"), List.of("2295", "2305"),
                List.of("2305", "2315"), List.of("2290", "2300"),
                List.of(100L, 200L));
        when(yahooStockPort.fetchChartWithParams(eq(GOLD_SYMBOL), anyString(), anyString())).thenReturn(snap);

        GoldHistoryResponse resp = service.getGoldHistory("1M", "TRY");

        assertThat(resp.isFallback()).isTrue();
        assertThat(resp.isOfficial()).isFalse();
        assertThat(resp.getSource()).isEqualTo("Yahoo Finance Fallback");
        assertThat(resp.getSymbol()).isEqualTo(GOLD_SYMBOL);
        assertThat(resp.getPoints()).hasSize(2);
        assertThat(resp.getPoints().get(0).getClose()).isEqualByComparingTo("2300.00");
    }

    @Test
    @DisplayName("history 3M/USD → BIST ons endpoint, ons OHLC + synthetic open")
    void getGoldHistory_threeMonthUsd_bistOns() {
        BistPreciousMetalPoint o1 = onsPoint("2026-05-29", "2400", "2410", "2390", "2405", true);
        BistPreciousMetalPoint o2 = onsPoint("2026-05-30", "2420", "2430", "2410", "2425", true);
        when(bistClient.fetchHistory(eq(PreciousMetalType.GOLD), eq(PriceUnit.USD_ONS), anyString(), anyString()))
                .thenReturn(List.of(o1, o2));

        GoldHistoryResponse resp = service.getGoldHistory("3M", "USD");

        assertThat(resp.getSymbol()).isEqualTo("BIST/ALTIN-ONS");
        assertThat(resp.getCurrency()).isEqualTo("USD");
        GoldHistoryPoint pt = resp.getPoints().get(0);
        assertThat(pt.getClose()).isEqualByComparingTo("2400");
        assertThat(pt.getHigh()).isEqualByComparingTo("2410");
        assertThat(pt.getOpen()).isEqualByComparingTo("2400");
        assertThat(pt.getVolume()).isEqualTo(3000L); // 3.0 kg * 1000
        assertThat(resp.getPoints().get(1).getOpen()).isEqualByComparingTo("2400");
    }

    @Test
    @DisplayName("history USD/BIST ons empty → Yahoo fallback (USD currency tagged)")
    void getGoldHistory_bistOnsEmpty_yahooFallback() {
        when(bistClient.fetchHistory(eq(PreciousMetalType.GOLD), eq(PriceUnit.USD_ONS), anyString(), anyString()))
                .thenReturn(List.of());
        YahooChartSnapshot snap = chartSnapshotStr(
                List.of(1_716_000_000L), List.of("2300"), List.of("2295"),
                List.of("2305"), List.of("2290"), List.of(100L));
        when(yahooStockPort.fetchChartWithParams(eq(GOLD_SYMBOL), anyString(), anyString())).thenReturn(snap);

        GoldHistoryResponse resp = service.getGoldHistory("1Y", "USD");

        assertThat(resp.getCurrency()).isEqualTo("USD");
        assertThat(resp.isFallback()).isTrue();
        assertThat(resp.getPoints()).hasSize(1);
    }

    @Test
    @DisplayName("history Yahoo fallback: null timestamp/close entries skipped; null quote series tolerated")
    void getGoldHistory_yahooNullsSkipped() {
        when(bistClient.fetchHistory(eq(PreciousMetalType.GOLD), eq(PriceUnit.USD_ONS), anyString(), anyString()))
                .thenReturn(List.of());
        YahooChartSnapshot snap = chartSnapshotBd(
                Arrays.asList(1_716_000_000L, null, 1_716_172_800L),
                Arrays.asList(new BigDecimal("2300"), new BigDecimal("2305"), null),
                null, null, null, null); // no opens/highs/lows/volumes
        when(yahooStockPort.fetchChartWithParams(eq(GOLD_SYMBOL), anyString(), anyString())).thenReturn(snap);

        GoldHistoryResponse resp = service.getGoldHistory("1W", "USD");

        // index0 valid, index1 null ts → skip, index2 null close → skip
        assertThat(resp.getPoints()).hasSize(1);
        assertThat(resp.getPoints().get(0).getClose()).isEqualByComparingTo("2300.00");
    }

    @Test
    @DisplayName("history Yahoo fallback: quote=null & timestamps=null → empty points, no error")
    void getGoldHistory_yahooEmptyChart() {
        when(bistClient.fetchHistory(eq(PreciousMetalType.GOLD), eq(PriceUnit.USD_ONS), anyString(), anyString()))
                .thenReturn(List.of());
        YahooChartSnapshot snap = new YahooChartSnapshot(); // timestamps null, quote null
        when(yahooStockPort.fetchChartWithParams(eq(GOLD_SYMBOL), anyString(), anyString())).thenReturn(snap);

        GoldHistoryResponse resp = service.getGoldHistory("1W", "USD");

        assertThat(resp.getPoints()).isEmpty();
        assertThat(resp.isFallback()).isTrue();
    }

    @Test
    @DisplayName("history Yahoo fallback throws → empty graceful response")
    void getGoldHistory_yahooThrows() {
        when(bistClient.fetchHistory(eq(PreciousMetalType.GOLD), eq(PriceUnit.USD_ONS), anyString(), anyString()))
                .thenReturn(List.of());
        when(yahooStockPort.fetchChartWithParams(eq(GOLD_SYMBOL), anyString(), anyString()))
                .thenThrow(new RuntimeException("network"));

        GoldHistoryResponse resp = service.getGoldHistory("1D", "USD");

        assertThat(resp.getPoints()).isEmpty();
        assertThat(resp.isFallback()).isTrue();
        assertThat(resp.getSource()).isEqualTo("Yahoo Finance Fallback");
        assertThat(resp.getCurrency()).isEqualTo("USD");
    }

    @Test
    @DisplayName("history null range defaults to 1M (TRY gram path)")
    void getGoldHistory_nullRangeDefaults() {
        when(bistClient.fetchHistory(eq(PreciousMetalType.GOLD), eq(PriceUnit.TRY_KG), anyString(), anyString()))
                .thenReturn(List.of(gramPoint("2026-05-30", "2500", "2501", "2490", "2510", true)));

        GoldHistoryResponse resp = service.getGoldHistory(null, "TRY");

        assertThat(resp.getRange()).isEqualTo("1M");
        assertThat(resp.getPoints()).hasSize(1);
    }

    @Test
    @DisplayName("history lowercase currency 'usd' routes to USD ons path")
    void getGoldHistory_lowercaseUsd() {
        when(bistClient.fetchHistory(eq(PreciousMetalType.GOLD), eq(PriceUnit.USD_ONS), anyString(), anyString()))
                .thenReturn(List.of(onsPoint("2026-05-30", "2400", "2410", "2390", "2405", true)));

        GoldHistoryResponse resp = service.getGoldHistory("3M", "usd");

        assertThat(resp.getSymbol()).isEqualTo("BIST/ALTIN-ONS");
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  range → BIST dates (verified via metal-ref ALL path)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("range ALL → BIST start date 2011-01-01")
    void rangeToBistDates_all() {
        when(metalClient.fetchMetalPrices(eq(PreciousMetalType.GOLD), anyString(), anyString()))
                .thenReturn(List.of());

        service.getGoldHistory("ALL", "TRY");

        ArgumentCaptor<String> start = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> end = ArgumentCaptor.forClass(String.class);
        verify(metalClient).fetchMetalPrices(eq(PreciousMetalType.GOLD), start.capture(), end.capture());
        assertThat(start.getValue()).isEqualTo("2011-01-01");
        assertThat(end.getValue()).isEqualTo(LocalDate.now(ISTANBUL).format(FMT));
    }

    @Test
    @DisplayName("range 5Y → BIST start date five years ago")
    void rangeToBistDates_fiveYears() {
        when(metalClient.fetchMetalPrices(eq(PreciousMetalType.GOLD), anyString(), anyString()))
                .thenReturn(List.of());

        service.getGoldHistory("5Y", "TRY");

        ArgumentCaptor<String> start = ArgumentCaptor.forClass(String.class);
        verify(metalClient).fetchMetalPrices(eq(PreciousMetalType.GOLD), start.capture(), anyString());
        assertThat(start.getValue()).isEqualTo(LocalDate.now(ISTANBUL).minusYears(5).format(FMT));
    }

    @Test
    @DisplayName("range 1Y → BIST start date one year ago (gram path)")
    void rangeToBistDates_oneYear() {
        when(bistClient.fetchHistory(eq(PreciousMetalType.GOLD), eq(PriceUnit.TRY_KG), anyString(), anyString()))
                .thenReturn(List.of(gramPoint("2026-05-30", "2500", "2501", "2490", "2510", true)));

        service.getGoldHistory("1Y", "TRY");

        ArgumentCaptor<String> start = ArgumentCaptor.forClass(String.class);
        verify(bistClient).fetchHistory(eq(PreciousMetalType.GOLD), eq(PriceUnit.TRY_KG), start.capture(), anyString());
        assertThat(start.getValue()).isEqualTo(LocalDate.now(ISTANBUL).minusYears(1).format(FMT));
    }

    @Test
    @DisplayName("range 1D → BIST start date 5 days ago (gram path)")
    void rangeToBistDates_oneDay() {
        when(bistClient.fetchHistory(eq(PreciousMetalType.GOLD), eq(PriceUnit.TRY_KG), anyString(), anyString()))
                .thenReturn(List.of(gramPoint("2026-05-30", "2500", "2501", "2490", "2510", true)));

        service.getGoldHistory("1D", "TRY");

        ArgumentCaptor<String> start = ArgumentCaptor.forClass(String.class);
        verify(bistClient).fetchHistory(eq(PreciousMetalType.GOLD), eq(PriceUnit.TRY_KG), start.capture(), anyString());
        assertThat(start.getValue()).isEqualTo(LocalDate.now(ISTANBUL).minusDays(5).format(FMT));
    }

    @Test
    @DisplayName("range 1W → BIST start date 10 days ago (gram path)")
    void rangeToBistDates_oneWeek() {
        when(bistClient.fetchHistory(eq(PreciousMetalType.GOLD), eq(PriceUnit.TRY_KG), anyString(), anyString()))
                .thenReturn(List.of(gramPoint("2026-05-30", "2500", "2501", "2490", "2510", true)));

        service.getGoldHistory("1W", "TRY");

        ArgumentCaptor<String> start = ArgumentCaptor.forClass(String.class);
        verify(bistClient).fetchHistory(eq(PreciousMetalType.GOLD), eq(PriceUnit.TRY_KG), start.capture(), anyString());
        assertThat(start.getValue()).isEqualTo(LocalDate.now(ISTANBUL).minusDays(10).format(FMT));
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  cache evict
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("evictGoldCaches runs without touching any client")
    void evictGoldCaches_noInteraction() {
        service.evictGoldCaches();
        verify(bistClient, never()).fetchLatestValidPoint(any(), any());
        verify(metalClient, never()).fetchMetalPrices(any(), anyString(), anyString());
    }

    // ── chart helpers ────────────────────────────────────────────────────────

    private static YahooChartSnapshot chartSnapshotStr(List<Long> timestamps,
                                                       List<String> closes, List<String> opens,
                                                       List<String> highs, List<String> lows,
                                                       List<Long> volumes) {
        return chartSnapshotBd(timestamps, toBd(closes), toBd(opens), toBd(highs), toBd(lows), volumes);
    }

    private static YahooChartSnapshot chartSnapshotBd(List<Long> timestamps,
                                                      List<BigDecimal> closes, List<BigDecimal> opens,
                                                      List<BigDecimal> highs, List<BigDecimal> lows,
                                                      List<Long> volumes) {
        YahooQuoteSeries q = new YahooQuoteSeries();
        q.setClose(closes);
        q.setOpen(opens);
        q.setHigh(highs);
        q.setLow(lows);
        q.setVolume(volumes);
        YahooChartSnapshot snap = new YahooChartSnapshot();
        snap.setTimestamps(timestamps);
        snap.setQuote(q);
        return snap;
    }

    private static List<BigDecimal> toBd(List<String> values) {
        if (values == null) return null;
        return values.stream().map(v -> v == null ? null : new BigDecimal(v)).toList();
    }
}
