package com.finance.portal.market.application.crypto;

import com.finance.portal.common.application.exception.ExternalApiException;
import com.finance.portal.common.application.logging.CentralIntegrationLogService;
import com.finance.portal.common.infrastructure.cache.LastKnownGoodCache;
import com.finance.portal.market.application.crypto.model.CryptoMarketItem;
import com.finance.portal.market.application.crypto.port.CoinGeckoPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Final branch-coverage pass for {@link CryptoMarketService}: targets only the
 * conditionals JaCoCo still reports as missed (nc/pc) that neither
 * {@code CryptoMarketServiceTest} nor {@code CryptoMarketServiceMoreTest} reach.
 *
 * Specifically:
 *  - getAllCoins: non-null blank currency (L81), present-but-null cache wrapper (L87),
 *    full 4-page loop with no early break (L96/L101), and partial-result break in the
 *    catch arm (L107).
 *  - getOhlc days=180: the hourly two-range merge path inside fetchHourlyMarketChart
 *    (L218-false plus L222-253) and the d==180 arm of fetchOhlcSeries (L154).
 *  - fetchMarketChartData catch: the hourly-false short-circuit (L206) -> rethrow.
 *  - fetchFullHistoryChart / fetchFullHistoryOhlc: the "range present but inadequate,
 *    yet has data" partial-return arms (L269 / L365).
 *
 * Resilience4j annotations are inert under direct construction; the LKG wrapper is
 * stubbed to run the underlying supplier so the real method bodies execute.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CryptoMarketServiceMoreTest2 {

    @Mock
    private CoinGeckoPort coinGeckoPort;
    @Mock
    private CacheManager cacheManager;
    @Mock
    private CentralIntegrationLogService integrationLogService;
    @Mock
    private LastKnownGoodCache lkg;
    @Mock
    private CryptoDescriptionTranslationService descriptionTranslationService;

    /** LKG sarmalayıcı bu birim testlerde şeffaf olmalı: doğrudan asıl çekimi (Supplier) çalıştır. */
    @BeforeEach
    void stubLkgPassThrough() {
        when(lkg.resilient(any(), any(), any(), any()))
                .thenAnswer(inv -> inv.<Supplier<?>>getArgument(3).get());
    }

    private CryptoMarketService newService() {
        return new CryptoMarketService(coinGeckoPort, cacheManager, integrationLogService, lkg,
                descriptionTranslationService);
    }

    private static CryptoMarketItem item(String id) {
        return new CryptoMarketItem(id, "sym", "Name", null,
                BigDecimal.ONE, BigDecimal.ONE, 1, BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.ONE, "x");
    }

    /** "prices" + "total_volumes" chart map with {@code count} synthetic daily points ending now. */
    private static Map<String, Object> chartWithDailyPoints(int count) {
        List<List<Number>> prices = new ArrayList<>();
        List<List<Number>> vols = new ArrayList<>();
        long nowMs = System.currentTimeMillis();
        for (int i = count - 1; i >= 0; i--) {
            long ts = nowMs - (long) i * 86_400_000L;
            prices.add(List.of(ts, 100.0 + i));
            vols.add(List.of(ts, 5_000.0 + i));
        }
        Map<String, Object> chart = new java.util.LinkedHashMap<>();
        chart.put("prices", prices);
        chart.put("total_volumes", vols);
        return chart;
    }

    private static List<List<Number>> ohlcRows(int count) {
        List<List<Number>> rows = new ArrayList<>();
        long nowSec = System.currentTimeMillis() / 1000L;
        for (int i = count - 1; i >= 0; i--) {
            long sec = nowSec - (long) i * 86_400L;
            rows.add(List.of(sec, 100.0, 110.0, 90.0, 105.0));
        }
        return rows;
    }

    // ===================== getAllCoins remaining branches =====================

    /** L81: non-null but blank currency -> isBlank() true arm -> normalized to "try". */
    @Test
    void getAllCoins_blankNonNullCurrency_normalizesToTry() {
        CryptoMarketService service = newService();
        when(cacheManager.getCache("cryptoMarketsCache")).thenReturn(null);
        List<CryptoMarketItem> page1 = List.of(item("bitcoin"));
        when(coinGeckoPort.fetchMarkets(1, 250, "try")).thenReturn(page1);
        when(coinGeckoPort.fetchMarkets(2, 250, "try")).thenReturn(List.of());

        List<CryptoMarketItem> result = service.getAllCoins("   ");

        assertThat(result).containsExactlyElementsOf(page1);
        // proves the blank (not null) currency was coerced to the "try" market key
        verify(coinGeckoPort).fetchMarkets(1, 250, "try");
    }

    /** L87: cache wrapper present but its value is null -> falls through to live fetch. */
    @Test
    void getAllCoins_wrapperPresentButNullValue_fetchesLive() {
        CryptoMarketService service = newService();
        Cache cache = mock(Cache.class);
        when(cacheManager.getCache("cryptoMarketsCache")).thenReturn(cache);
        // wrapper is non-null, but wrapper.get() == null -> right side of && is false
        when(cache.get("try:all")).thenReturn(() -> null);
        List<CryptoMarketItem> page1 = List.of(item("bitcoin"));
        when(coinGeckoPort.fetchMarkets(1, 250, "try")).thenReturn(page1);
        when(coinGeckoPort.fetchMarkets(2, 250, "try")).thenReturn(List.of());

        List<CryptoMarketItem> result = service.getAllCoins("try");

        assertThat(result).containsExactlyElementsOf(page1);
        verify(coinGeckoPort).fetchMarkets(1, 250, "try");
    }

    /** L96 loop-exit-by-condition + L101 (page<4) false at page 4: all four pages non-empty. */
    @Test
    void getAllCoins_allFourPagesNonEmpty_loopRunsToCompletion() {
        CryptoMarketService service = newService();
        Cache cache = mock(Cache.class);
        when(cacheManager.getCache("cryptoMarketsCache")).thenReturn(cache);
        when(cache.get("try:all")).thenReturn(null);
        List<CryptoMarketItem> p1 = List.of(item("c1"));
        List<CryptoMarketItem> p2 = List.of(item("c2"));
        List<CryptoMarketItem> p3 = List.of(item("c3"));
        List<CryptoMarketItem> p4 = List.of(item("c4"));
        when(coinGeckoPort.fetchMarkets(1, 250, "try")).thenReturn(p1);
        when(coinGeckoPort.fetchMarkets(2, 250, "try")).thenReturn(p2);
        when(coinGeckoPort.fetchMarkets(3, 250, "try")).thenReturn(p3);
        when(coinGeckoPort.fetchMarkets(4, 250, "try")).thenReturn(p4);

        List<CryptoMarketItem> result = service.getAllCoins("try");

        // 4 pages aggregated; loop ended because page became 5 (condition false), not via break
        assertThat(result).hasSize(4);
        verify(coinGeckoPort).fetchMarkets(4, 250, "try");
        verify(cache).put("try:all", result);
    }

    /** L107: a later page fails AFTER earlier success -> result non-empty -> break, return partial. */
    @Test
    void getAllCoins_laterPageFailsWithPartialResult_breaksAndCaches() {
        CryptoMarketService service = newService();
        Cache cache = mock(Cache.class);
        when(cacheManager.getCache("cryptoMarketsCache")).thenReturn(cache);
        when(cache.get("try:all")).thenReturn(null);
        List<CryptoMarketItem> p1 = List.of(item("c1"));
        when(coinGeckoPort.fetchMarkets(1, 250, "try")).thenReturn(p1);
        when(coinGeckoPort.fetchMarkets(2, 250, "try"))
                .thenThrow(new RuntimeException("page2 boom"));

        List<CryptoMarketItem> result = service.getAllCoins("try");

        // partial result kept (no rethrow because result was non-empty), and cached
        assertThat(result).containsExactlyElementsOf(p1);
        verify(cache).put("try:all", result);
        verify(coinGeckoPort, never()).fetchMarkets(3, 250, "try");
    }

    // ===================== getOhlc days=180 hourly two-range merge =====================

    /**
     * L154 (d==180 arm) + fetchHourlyMarketChart days>90 path (L218-false, L222-253):
     * two hourly range chunks merged into a chart, then converted to daily OHLC.
     */
    @Test
    void getOhlc_days180_mergesTwoHourlyRangesIntoDailyOhlc() {
        CryptoMarketService service = newService();
        Map<String, Object> older = chartWithDailyPoints(40);
        Map<String, Object> recent = chartWithDailyPoints(40);
        // 5-arg range overload (with interval) is what the >90 hourly path uses
        when(coinGeckoPort.fetchMarketChartRange(eq("bitcoin"), eq("usd"), anyLong(), anyLong(), eq("hourly")))
                .thenReturn(older, recent);

        List<List<Number>> result = service.getOhlc("bitcoin", "180", "usd");

        // merged chart had price data -> marketChartToDailyOhlc produced rows
        assertThat(result).isNotEmpty();
        // the two-range hourly path was used; the plain /ohlc endpoint was never hit
        verify(coinGeckoPort, times(2))
                .fetchMarketChartRange(eq("bitcoin"), eq("usd"), anyLong(), anyLong(), eq("hourly"));
        verify(coinGeckoPort, never()).fetchOhlc(anyString(), any(), anyString());
    }

    // ===================== fetchMarketChartData catch: hourly-false rethrow (L206) =====================

    /**
     * L206 hourly-false short-circuit: a non-hourly request whose live fetch throws is
     * NOT eligible for the chunked-hourly retry, so the exception is rethrown unchanged.
     */
    @Test
    void getMarketChart_dailyFetchThrows_rethrowsWithoutHourlyRetry() {
        CryptoMarketService service = newService();
        when(coinGeckoPort.fetchMarketChart("bitcoin", 120, "usd", "daily"))
                .thenThrow(new ExternalApiException("daily rate limited"));

        assertThatThrownBy(() -> service.getMarketChart("bitcoin", "120", "usd", "daily", null))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("daily rate limited");

        // interval was "daily" (not hourly) -> no fallback to the hourly two-range path
        verify(coinGeckoPort, never())
                .fetchMarketChartRange(anyString(), anyString(), anyLong(), anyLong(), anyString());
    }

    // ===================== partial-return arms (range present but inadequate) =====================

    /**
     * L269: days=max chart is short, and the range best-effort returns data that is
     * present yet still inadequate -> service returns that partial ranged chart.
     */
    @Test
    void getMarketChart_max_shortMaxAndShortRange_returnsPartialRanged() {
        CryptoMarketService service = newService();
        // max returns immediately (non-null) but short -> inadequate, avoids retry sleeps
        Map<String, Object> shortMax = chartWithDailyPoints(10);
        when(coinGeckoPort.fetchMarketChart("bitcoin", "max", "usd", null)).thenReturn(shortMax);
        when(coinGeckoPort.fetchCoinDetail("bitcoin"))
                .thenReturn(Map.of("genesis_date", "2013-04-28"));
        // single full range returns data but short -> hasChartData true, isAdequate false
        Map<String, Object> shortRange = chartWithDailyPoints(12);
        when(coinGeckoPort.fetchMarketChartRange(eq("bitcoin"), eq("usd"), anyLong(), anyLong()))
                .thenReturn(shortRange);

        Map<String, Object> result = service.getMarketChart("bitcoin", "max", "usd", null, null);

        assertThat(result).isSameAs(shortRange);
    }

    /**
     * L365: days=max OHLC fails entirely, and the OHLC range best-effort returns rows
     * that are present yet inadequate -> service returns that partial ranged OHLC.
     */
    @Test
    void getOhlc_max_maxFailsAndShortRange_returnsPartialRanged() {
        CryptoMarketService service = newService();
        when(coinGeckoPort.fetchOhlc("bitcoin", "max", "usd"))
                .thenThrow(new ExternalApiException("max down")); // fromMax stays null
        when(coinGeckoPort.fetchCoinDetail("bitcoin"))
                .thenReturn(Map.of("genesis_date", "2013-04-28"));
        // single full ohlc range returns short rows -> hasOhlcData true, isAdequate false
        List<List<Number>> shortRange = ohlcRows(12);
        when(coinGeckoPort.fetchOhlcRange(eq("bitcoin"), eq("usd"), anyLong(), anyLong()))
                .thenReturn(shortRange);

        List<List<Number>> result = service.getOhlc("bitcoin", "max", "usd");

        assertThat(result).isSameAs(shortRange);
        verify(coinGeckoPort).fetchCoinDetail("bitcoin");
    }
}
