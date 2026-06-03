package com.finance.portal.market.application.commodity;

import com.finance.portal.common.infrastructure.cache.LastKnownGoodCache;
import com.finance.portal.market.application.fx.model.FxLatestRates;
import com.finance.portal.market.application.fx.model.FxRateItem;
import com.finance.portal.market.application.service.MarketFxService;
import com.finance.portal.market.application.stock.model.YahooChartSnapshot;
import com.finance.portal.market.application.stock.model.YahooQuoteSeries;
import com.finance.portal.market.application.stock.model.YahooStockMeta;
import com.finance.portal.market.application.stock.port.YahooStockPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Branch-coverage focused supplement to {@link YahooCommodityServiceTest}.
 * Targets branches the original misses:
 *  - fromSymbol null/blank/dash-format guards,
 *  - resolveRangeInterval explicit + auto switch arms not yet hit,
 *  - getHistory: null meta currency path, null quote, short quote lists (safeGet OOB),
 *  - resolveTcmbUsdTry sell==null / sell<=0 filter,
 *  - applyTryDisplayPrices per-field null guards (USX symbol so many highs/lows arrive),
 *  - per-metal arms (NG=F, HG=F, KC=F, CC=F, CT=F, BZ=F).
 */
@ExtendWith(MockitoExtension.class)
class YahooCommodityServiceMoreTest {

    @Mock
    private YahooStockPort yahooStockPort;

    @Mock
    private MarketFxService marketFxService;

    @Mock
    private LastKnownGoodCache lkg;

    @InjectMocks
    private YahooCommodityService service;

    @BeforeEach
    void stubLkgPassThrough() {
        lenient().when(lkg.resilient(any(), any(), any(), any()))
                .thenAnswer(inv -> ((java.util.function.Supplier<?>) inv.getArgument(3)).get());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static YahooChartSnapshot snapshotWithMeta(YahooStockMeta m) {
        YahooChartSnapshot s = new YahooChartSnapshot();
        s.setMeta(m);
        return s;
    }

    private void noFx() {
        lenient().when(marketFxService.getTcmbLatestRates(anyString()))
                .thenReturn(new FxLatestRates("tcmb", "official", "TRY", "2026-01-01", null));
    }

    private static FxLatestRates usdTrySell(BigDecimal sell) {
        FxRateItem item = new FxRateItem("USD", new BigDecimal("32.0"), sell, 1);
        List<FxRateItem> rates = new ArrayList<>();
        rates.add(item);
        return new FxLatestRates("tcmb", "official", "TRY", "2026-01-01", rates);
    }

    // ── fromSymbol guard branches (via getSpot) ──────────────────────────────

    @Test
    void getSpot_nullSymbol_throws() {
        assertThatThrownBy(() -> service.getSpot(null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(yahooStockPort, never()).fetchChartWithParams(anyString(), anyString(), anyString());
    }

    @Test
    void getSpot_blankSymbol_throws() {
        assertThatThrownBy(() -> service.getSpot("   "))
                .isInstanceOf(IllegalArgumentException.class);
        verify(yahooStockPort, never()).fetchChartWithParams(anyString(), anyString(), anyString());
    }

    @Test
    void getSpot_dashAndLowercaseFormat_normalizesToValidSymbol() {
        noFx();
        // "cl-f" → trim/upper/replace("-","=") → "CL=F"
        YahooStockMeta m = new YahooStockMeta();
        m.setCurrency("USD");
        m.setRegularMarketPrice(new BigDecimal("80"));
        m.setPreviousClose(new BigDecimal("80"));
        when(yahooStockPort.fetchChartWithParams("CL=F", "1d", "1m")).thenReturn(snapshotWithMeta(m));

        CommoditySpotDto dto = service.getSpot("cl-f");

        assertThat(dto.getSymbol()).isEqualTo("CL=F");
        assertThat(dto.isStale()).isFalse();
    }

    // ── per-metal arms (different enum symbols) ──────────────────────────────

    @Test
    void getSpot_naturalGas_usdNoCentConversion() {
        noFx();
        YahooStockMeta m = new YahooStockMeta();
        m.setCurrency("USD");
        m.setRegularMarketPrice(new BigDecimal("3.50"));
        m.setPreviousClose(new BigDecimal("3.00"));
        when(yahooStockPort.fetchChartWithParams("NG=F", "1d", "1m")).thenReturn(snapshotWithMeta(m));

        CommoditySpotDto dto = service.getSpot("NG=F");

        assertThat(dto.isCentConverted()).isFalse();
        assertThat(dto.getDisplayPrice()).isEqualByComparingTo("3.50");
        assertThat(dto.getUnit()).isEqualTo("MMBtu");
        // change = 0.50, pct = 16.67
        assertThat(dto.getChange()).isEqualByComparingTo("0.5000");
        assertThat(dto.getChangePercent()).isEqualByComparingTo("16.67");
    }

    @Test
    void getSpot_coffee_centConversionViaEnumFlag() {
        noFx();
        // KC=F (Coffee) needsCentConversion=true, currency reported USD
        YahooStockMeta m = new YahooStockMeta();
        m.setCurrency("USD");
        m.setRegularMarketPrice(new BigDecimal("180"));
        m.setPreviousClose(new BigDecimal("160"));
        when(yahooStockPort.fetchChartWithParams("KC=F", "1d", "1m")).thenReturn(snapshotWithMeta(m));

        CommoditySpotDto dto = service.getSpot("KC=F");

        assertThat(dto.isCentConverted()).isTrue();
        assertThat(dto.getDisplayPrice()).isEqualByComparingTo("1.80");
        assertThat(dto.getPreviousClose()).isEqualByComparingTo("1.60");
    }

    @Test
    void getSpot_cocoa_noCentConversion_isMetalDistinctArm() {
        noFx();
        // CC=F (Cocoa) needsCentConversion=false
        YahooStockMeta m = new YahooStockMeta();
        m.setCurrency("USD");
        m.setRegularMarketPrice(new BigDecimal("5000"));
        m.setPreviousClose(new BigDecimal("5000"));
        when(yahooStockPort.fetchChartWithParams("CC=F", "1d", "1m")).thenReturn(snapshotWithMeta(m));

        CommoditySpotDto dto = service.getSpot("CC=F");

        assertThat(dto.isCentConverted()).isFalse();
        assertThat(dto.getDisplayPrice()).isEqualByComparingTo("5000");
        assertThat(dto.getUnit()).isEqualTo("Ton");
    }

    // ── applyTryDisplayPrices: per-field null guards ─────────────────────────
    // displayPrice present + most optional fields null → each "if (x != null)" false-arm.

    @Test
    void getSpot_tryConversion_skipsNullOptionalFields() {
        when(marketFxService.getTcmbLatestRates("USD")).thenReturn(usdTrySell(new BigDecimal("40")));
        YahooStockMeta m = new YahooStockMeta();
        m.setCurrency("USD");
        m.setRegularMarketPrice(new BigDecimal("100"));
        // prevClose null → no change computed; highs/lows/52w all null
        m.setPreviousClose(null);
        // leave dayHigh/dayLow/52w/volume null
        when(yahooStockPort.fetchChartWithParams("CL=F", "1d", "1m")).thenReturn(snapshotWithMeta(m));

        CommoditySpotDto dto = service.getSpot("CL=F");

        // displayPrice converted to TRY: 100 * 40 = 4000
        assertThat(dto.getDisplayCurrency()).isEqualTo("TRY");
        assertThat(dto.getDisplayPrice()).isEqualByComparingTo("4000.0000");
        // null optional fields stay null (false-arm of each guard)
        assertThat(dto.getPreviousClose()).isNull();
        assertThat(dto.getDayHigh()).isNull();
        assertThat(dto.getDayLow()).isNull();
        assertThat(dto.getWeekHigh52()).isNull();
        assertThat(dto.getWeekLow52()).isNull();
        assertThat(dto.getChange()).isNull();
    }

    @Test
    void getSpot_tryConversion_convertsAllPresentFields() {
        when(marketFxService.getTcmbLatestRates("USD")).thenReturn(usdTrySell(new BigDecimal("10")));
        YahooStockMeta m = new YahooStockMeta();
        m.setCurrency("USD");
        m.setRegularMarketPrice(new BigDecimal("110"));
        m.setPreviousClose(new BigDecimal("100"));
        m.setRegularMarketDayHigh(new BigDecimal("120"));
        m.setRegularMarketDayLow(new BigDecimal("90"));
        m.setFiftyTwoWeekHigh(new BigDecimal("300"));
        m.setFiftyTwoWeekLow(new BigDecimal("50"));
        when(yahooStockPort.fetchChartWithParams("CL=F", "1d", "1m")).thenReturn(snapshotWithMeta(m));

        CommoditySpotDto dto = service.getSpot("CL=F");

        // every optional present-arm converted at rate 10
        assertThat(dto.getDayHigh()).isEqualByComparingTo("1200.0000");
        assertThat(dto.getDayLow()).isEqualByComparingTo("900.0000");
        assertThat(dto.getWeekHigh52()).isEqualByComparingTo("3000.0000");
        assertThat(dto.getWeekLow52()).isEqualByComparingTo("500.0000");
        assertThat(dto.getChange()).isEqualByComparingTo("100.0000"); // 10 USD * 10
    }

    // ── resolveTcmbUsdTry: sell null / sell<=0 filter ────────────────────────

    @Test
    void getSpot_fxSellNull_keepsUsd() {
        when(marketFxService.getTcmbLatestRates("USD")).thenReturn(usdTrySell(null));
        YahooStockMeta m = new YahooStockMeta();
        m.setCurrency("USD");
        m.setRegularMarketPrice(new BigDecimal("110"));
        m.setPreviousClose(new BigDecimal("100"));
        when(yahooStockPort.fetchChartWithParams("CL=F", "1d", "1m")).thenReturn(snapshotWithMeta(m));

        CommoditySpotDto dto = service.getSpot("CL=F");

        // sell==null filtered → orElse(null) → no TRY conversion
        assertThat(dto.getDisplayCurrency()).isEqualTo("USD");
        assertThat(dto.getDisplayPrice()).isEqualByComparingTo("110");
    }

    @Test
    void getSpot_fxSellZero_keepsUsd() {
        when(marketFxService.getTcmbLatestRates("USD")).thenReturn(usdTrySell(BigDecimal.ZERO));
        YahooStockMeta m = new YahooStockMeta();
        m.setCurrency("USD");
        m.setRegularMarketPrice(new BigDecimal("110"));
        m.setPreviousClose(new BigDecimal("100"));
        when(yahooStockPort.fetchChartWithParams("CL=F", "1d", "1m")).thenReturn(snapshotWithMeta(m));

        CommoditySpotDto dto = service.getSpot("CL=F");

        // sell<=0 filtered out
        assertThat(dto.getDisplayCurrency()).isEqualTo("USD");
        assertThat(dto.getDisplayPrice()).isEqualByComparingTo("110");
    }

    // ── getHistory: null meta → currency null branch ─────────────────────────

    @Test
    void getHistory_nullMeta_currencyDefaultsUsd_noCentConversion() {
        YahooChartSnapshot snap = new YahooChartSnapshot(); // meta null, timestamps present
        snap.setTimestamps(new ArrayList<>(List.of(1000L)));
        YahooQuoteSeries q = new YahooQuoteSeries();
        q.setClose(new ArrayList<>(List.of(new BigDecimal("75"))));
        snap.setQuote(q);
        when(yahooStockPort.fetchChartWithParams(eq("CL=F"), anyString(), anyString())).thenReturn(snap);

        CommodityHistoryResponse resp = service.getHistory("CL=F", "1M", null);

        assertThat(resp.getPoints()).hasSize(1);
        CommodityHistoryPointDto pt = resp.getPoints().get(0);
        // currency null → rawCurrency defaults USD, isCent false (CL=F not cent), no /100
        assertThat(pt.getRawCurrency()).isEqualTo("USD");
        assertThat(pt.isCentConverted()).isFalse();
        assertThat(pt.getDisplayClose()).isEqualByComparingTo("75");
    }

    // ── getHistory: null quote → every raw null → all rows skipped ───────────

    @Test
    void getHistory_nullQuote_skipsAllPoints() {
        YahooChartSnapshot snap = new YahooChartSnapshot();
        YahooStockMeta m = new YahooStockMeta();
        m.setCurrency("USD");
        snap.setMeta(m);
        snap.setTimestamps(new ArrayList<>(List.of(1000L, 2000L)));
        // quote == null → rawClose null → continue for each
        when(yahooStockPort.fetchChartWithParams(eq("CL=F"), anyString(), anyString())).thenReturn(snap);

        CommodityHistoryResponse resp = service.getHistory("CL=F", "1M", null);

        assertThat(resp.getPoints()).isEmpty();
    }

    // ── getHistory: short quote lists → safeGet/safeGetLong OOB null ──────────

    @Test
    void getHistory_shortQuoteLists_yieldNullOptionalFields() {
        YahooChartSnapshot snap = new YahooChartSnapshot();
        YahooStockMeta m = new YahooStockMeta();
        m.setCurrency("USD");
        snap.setMeta(m);
        // two timestamps; close has 2 entries, but open/high/low/volume only length 1 → index 1 OOB
        snap.setTimestamps(new ArrayList<>(Arrays.asList(1000L, 2000L)));
        YahooQuoteSeries q = new YahooQuoteSeries();
        q.setClose(new ArrayList<>(Arrays.asList(new BigDecimal("10"), new BigDecimal("20"))));
        q.setOpen(new ArrayList<>(List.of(new BigDecimal("9"))));
        q.setHigh(new ArrayList<>(List.of(new BigDecimal("12"))));
        q.setLow(new ArrayList<>(List.of(new BigDecimal("8"))));
        q.setVolume(new ArrayList<>(List.of(100L)));
        snap.setQuote(q);
        when(yahooStockPort.fetchChartWithParams(eq("CL=F"), anyString(), anyString())).thenReturn(snap);

        CommodityHistoryResponse resp = service.getHistory("CL=F", "1M", null);

        assertThat(resp.getPoints()).hasSize(2);
        CommodityHistoryPointDto p0 = resp.getPoints().get(0);
        assertThat(p0.getRawOpen()).isEqualByComparingTo("9");
        assertThat(p0.getVolume()).isEqualTo(100L);
        // index 1: optional fields OOB → null, but close present so point kept
        CommodityHistoryPointDto p1 = resp.getPoints().get(1);
        assertThat(p1.getRawClose()).isEqualByComparingTo("20");
        assertThat(p1.getRawOpen()).isNull();
        assertThat(p1.getRawHigh()).isNull();
        assertThat(p1.getRawLow()).isNull();
        assertThat(p1.getDisplayOpen()).isNull();
        assertThat(p1.getVolume()).isNull();
    }

    // ── resolveRangeInterval: explicit-interval switch arms ──────────────────
    // interval is non-blank → first switch; cover 1D/1W/3M/6M/1Y/default ranges.

    @Test
    void getHistory_explicitInterval_mapsEachRangeArm() {
        YahooChartSnapshot snap = new YahooChartSnapshot();
        snap.setTimestamps(new ArrayList<>());
        when(yahooStockPort.fetchChartWithParams(eq("CL=F"), anyString(), eq("15m"))).thenReturn(snap);

        service.getHistory("CL=F", "1D", "15m");
        verify(yahooStockPort).fetchChartWithParams("CL=F", "1d", "15m");

        service.getHistory("CL=F", "1W", "15m");
        verify(yahooStockPort).fetchChartWithParams("CL=F", "5d", "15m");

        service.getHistory("CL=F", "1M", "15m");
        verify(yahooStockPort).fetchChartWithParams("CL=F", "1mo", "15m");

        service.getHistory("CL=F", "3M", "15m");
        verify(yahooStockPort).fetchChartWithParams("CL=F", "3mo", "15m");

        service.getHistory("CL=F", "6M", "15m");
        verify(yahooStockPort).fetchChartWithParams("CL=F", "6mo", "15m");

        service.getHistory("CL=F", "1Y", "15m");
        verify(yahooStockPort).fetchChartWithParams("CL=F", "1y", "15m");

        service.getHistory("CL=F", "BADRANGE", "15m");
        // "1M" and unknown "BADRANGE" both map to the default "1mo" → 2 total invocations.
        verify(yahooStockPort, times(2)).fetchChartWithParams("CL=F", "1mo", "15m");
    }

    @Test
    void getHistory_explicitInterval_nullRange_defaultsToOneMonth() {
        YahooChartSnapshot snap = new YahooChartSnapshot();
        snap.setTimestamps(new ArrayList<>());
        when(yahooStockPort.fetchChartWithParams(eq("CL=F"), eq("1mo"), eq("30m"))).thenReturn(snap);

        service.getHistory("CL=F", null, "30m");

        verify(yahooStockPort).fetchChartWithParams("CL=F", "1mo", "30m");
    }

    @Test
    void getHistory_blankInterval_fallsThroughToAutoSelection() {
        // interval is blank → first if skipped → auto switch used
        YahooChartSnapshot snap = new YahooChartSnapshot();
        snap.setTimestamps(new ArrayList<>());
        when(yahooStockPort.fetchChartWithParams(eq("CL=F"), anyString(), anyString())).thenReturn(snap);

        service.getHistory("CL=F", "3M", "  ");
        verify(yahooStockPort).fetchChartWithParams("CL=F", "3mo", "1d");
    }

    // ── resolveRangeInterval: auto switch arms not yet covered ───────────────

    @Test
    void getHistory_autoInterval_3m6m1y_arms() {
        YahooChartSnapshot snap = new YahooChartSnapshot();
        snap.setTimestamps(new ArrayList<>());
        when(yahooStockPort.fetchChartWithParams(eq("CL=F"), anyString(), anyString())).thenReturn(snap);

        service.getHistory("CL=F", "3M", null);
        verify(yahooStockPort).fetchChartWithParams("CL=F", "3mo", "1d");

        service.getHistory("CL=F", "6M", null);
        verify(yahooStockPort).fetchChartWithParams("CL=F", "6mo", "1d");

        service.getHistory("CL=F", "1Y", null);
        verify(yahooStockPort).fetchChartWithParams("CL=F", "1y", "1d");
    }

    // ── getHistory empty meta with empty timestamps path on a cent metal ─────

    @Test
    void getHistory_usxMetal_centConvertsHistory() {
        // ZW=F (wheat) needsCentConversion=true → isCent true even if currency USD
        YahooChartSnapshot snap = new YahooChartSnapshot();
        YahooStockMeta m = new YahooStockMeta();
        m.setCurrency("USD");
        snap.setMeta(m);
        snap.setTimestamps(new ArrayList<>(List.of(1000L)));
        YahooQuoteSeries q = new YahooQuoteSeries();
        q.setClose(new ArrayList<>(List.of(new BigDecimal("600"))));
        snap.setQuote(q);
        when(yahooStockPort.fetchChartWithParams(eq("ZW=F"), anyString(), anyString())).thenReturn(snap);

        CommodityHistoryResponse resp = service.getHistory("ZW=F", "1M", null);

        assertThat(resp.getPoints()).hasSize(1);
        CommodityHistoryPointDto pt = resp.getPoints().get(0);
        assertThat(pt.isCentConverted()).isTrue();
        assertThat(pt.getDisplayClose()).isEqualByComparingTo("6.00");
    }
}
