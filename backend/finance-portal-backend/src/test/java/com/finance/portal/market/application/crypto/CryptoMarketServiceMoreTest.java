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
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Coverage for {@link CryptoMarketService} OHLC / market-chart / fallback paths.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CryptoMarketServiceMoreTest {

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

    /** Build a "prices" chart map with the given number of synthetic daily points ending now. */
    private static Map<String, Object> chartWithDailyPoints(int count) {
        List<List<Number>> prices = new ArrayList<>();
        long nowMs = System.currentTimeMillis();
        for (int i = count - 1; i >= 0; i--) {
            prices.add(List.of(nowMs - (long) i * 86_400_000L, 100.0 + i));
        }
        return Map.of("prices", prices);
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

    // ================= getOhlc =================

    @Test
    void getOhlc_numericDays_delegatesToPortWithParsedInt() {
        CryptoMarketService service = newService();
        List<List<Number>> rows = ohlcRows(3);
        when(coinGeckoPort.fetchOhlc("bitcoin", 30, "usd")).thenReturn(rows);

        List<List<Number>> result = service.getOhlc("bitcoin", "30", "usd");

        assertThat(result).isEqualTo(rows);
        verify(coinGeckoPort).fetchOhlc("bitcoin", 30, "usd");
    }

    @Test
    void getOhlc_blankDays_defaultsTo7() {
        CryptoMarketService service = newService();
        when(coinGeckoPort.fetchOhlc("bitcoin", 7, "try")).thenReturn(List.of());

        service.getOhlc("bitcoin", "", "try");

        verify(coinGeckoPort).fetchOhlc("bitcoin", 7, "try");
    }

    @Test
    void getOhlc_invalidDays_defaultsTo7() {
        CryptoMarketService service = newService();
        when(coinGeckoPort.fetchOhlc("bitcoin", 7, "try")).thenReturn(List.of());

        service.getOhlc("bitcoin", "abc", "try");

        verify(coinGeckoPort).fetchOhlc("bitcoin", 7, "try");
    }

    @Test
    void getOhlc_daysBelowOne_clampedToOne() {
        CryptoMarketService service = newService();
        when(coinGeckoPort.fetchOhlc("bitcoin", 1, "try")).thenReturn(List.of());

        service.getOhlc("bitcoin", "0", "try");

        verify(coinGeckoPort).fetchOhlc("bitcoin", 1, "try");
    }

    @Test
    void getOhlc_days90_buildsDailyFromHourlyChart() {
        CryptoMarketService service = newService();
        // 90 days hourly -> single hourly request; marketChartToDailyOhlc yields non-empty
        Map<String, Object> hourly = chartWithDailyPoints(5);
        when(coinGeckoPort.fetchMarketChart("bitcoin", 90, "usd", "hourly")).thenReturn(hourly);

        List<List<Number>> result = service.getOhlc("bitcoin", "90", "usd");

        assertThat(result).isNotEmpty();
        // should NOT call the plain /ohlc endpoint when hourly-derived daily is present
        verify(coinGeckoPort, never()).fetchOhlc(anyString(), any(), anyString());
    }

    @Test
    void getOhlc_days90_emptyHourly_fallsBackToOhlcEndpoint() {
        CryptoMarketService service = newService();
        when(coinGeckoPort.fetchMarketChart("bitcoin", 90, "usd", "hourly"))
                .thenReturn(Map.of()); // no prices -> empty daily
        List<List<Number>> rows = ohlcRows(2);
        when(coinGeckoPort.fetchOhlc("bitcoin", 90, "usd")).thenReturn(rows);

        List<List<Number>> result = service.getOhlc("bitcoin", "90", "usd");

        assertThat(result).isEqualTo(rows);
        verify(coinGeckoPort).fetchOhlc("bitcoin", 90, "usd");
    }

    @Test
    void getOhlc_max_adequateHistory_returnedDirectly() {
        CryptoMarketService service = newService();
        List<List<Number>> longHist = ohlcRows(600); // >=80 rows spanning ~600 days
        when(coinGeckoPort.fetchOhlc("bitcoin", "max", "usd")).thenReturn(longHist);

        List<List<Number>> result = service.getOhlc("bitcoin", "max", "usd");

        assertThat(result).isEqualTo(longHist);
        // adequate -> range not attempted
        verify(coinGeckoPort, never()).fetchOhlcRange(anyString(), anyString(), anyLong(), anyLong());
    }

    @Test
    void getOhlc_max_shortHistory_fallsBackToRangeBestEffort() {
        CryptoMarketService service = newService();
        // short max history (inadequate) but has data
        List<List<Number>> shortHist = ohlcRows(10);
        when(coinGeckoPort.fetchOhlc("bitcoin", "max", "usd")).thenReturn(shortHist);
        when(coinGeckoPort.fetchCoinDetail("bitcoin")).thenReturn(Map.of("genesis_date", "2013-04-28"));
        List<List<Number>> rangeHist = ohlcRows(700);
        when(coinGeckoPort.fetchOhlcRange(eq("bitcoin"), eq("usd"), anyLong(), anyLong()))
                .thenReturn(rangeHist);

        List<List<Number>> result = service.getOhlc("bitcoin", "max", "usd");

        assertThat(result).isEqualTo(rangeHist);
        verify(coinGeckoPort).fetchCoinDetail("bitcoin");
    }

    @Test
    void getOhlc_max_allEmpty_throwsExternalApiException() {
        CryptoMarketService service = newService();
        when(coinGeckoPort.fetchOhlc("bitcoin", "max", "usd"))
                .thenThrow(new ExternalApiException("max down"));
        when(coinGeckoPort.fetchCoinDetail("bitcoin"))
                .thenThrow(new ExternalApiException("detail down"));

        assertThatThrownBy(() -> service.getOhlc("bitcoin", "max", "usd"))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("OHLC unavailable");
    }

    // ================= getMarketChart =================

    @Test
    void getMarketChart_numericDaily_delegatesAndReturns() {
        CryptoMarketService service = newService();
        Map<String, Object> chart = chartWithDailyPoints(3);
        when(coinGeckoPort.fetchMarketChart("bitcoin", 7, "try", "daily")).thenReturn(chart);

        Map<String, Object> result = service.getMarketChart("bitcoin", "7", "try", "daily", null);

        assertThat(result).isEqualTo(chart);
        verify(coinGeckoPort).fetchMarketChart("bitcoin", 7, "try", "daily");
    }

    @Test
    void getMarketChart_unknownInterval_normalizedToNull() {
        CryptoMarketService service = newService();
        Map<String, Object> chart = chartWithDailyPoints(3);
        when(coinGeckoPort.fetchMarketChart(eq("bitcoin"), eq(7), eq("try"), isNull())).thenReturn(chart);

        service.getMarketChart("bitcoin", "7", "try", "weird", null);

        verify(coinGeckoPort).fetchMarketChart(eq("bitcoin"), eq(7), eq("try"), isNull());
    }

    @Test
    void getMarketChart_hourly90_usesHourlySinglePage() {
        CryptoMarketService service = newService();
        Map<String, Object> chart = chartWithDailyPoints(3);
        when(coinGeckoPort.fetchMarketChart("bitcoin", 90, "usd", "hourly")).thenReturn(chart);

        Map<String, Object> result = service.getMarketChart("bitcoin", "90", "usd", "hourly", null);

        assertThat(result).isEqualTo(chart);
        verify(coinGeckoPort).fetchMarketChart("bitcoin", 90, "usd", "hourly");
    }

    @Test
    void getMarketChart_dailyHourlyFails_andLowDays_propagatesException() {
        CryptoMarketService service = newService();
        // hourly but days < 90 -> goes to the else branch, fetch throws, not eligible for chunked retry
        when(coinGeckoPort.fetchMarketChart("bitcoin", 30, "usd", "hourly"))
                .thenThrow(new ExternalApiException("rate limited"));

        assertThatThrownBy(() -> service.getMarketChart("bitcoin", "30", "usd", "hourly", null))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("rate limited");
    }

    @Test
    void getMarketChart_weeklyAggregate_invokesAggregator() {
        CryptoMarketService service = newService();
        Map<String, Object> chart = chartWithDailyPoints(20);
        when(coinGeckoPort.fetchMarketChart("bitcoin", 30, "try", "daily")).thenReturn(chart);

        Map<String, Object> result = service.getMarketChart("bitcoin", "30", "try", "daily", "weekly");

        // aggregator returns a map containing prices (possibly aggregated); just assert non-null structure
        assertThat(result).isNotNull();
        assertThat(result).containsKey("prices");
    }

    @Test
    void getMarketChart_monthlyAggregate_invokesAggregator() {
        CryptoMarketService service = newService();
        Map<String, Object> chart = chartWithDailyPoints(60);
        when(coinGeckoPort.fetchMarketChart("bitcoin", 90, "try", "daily")).thenReturn(chart);

        Map<String, Object> result = service.getMarketChart("bitcoin", "90", "try", "daily", "MONTHLY");

        assertThat(result).isNotNull();
        assertThat(result).containsKey("prices");
    }

    @Test
    void getMarketChart_max_adequate_returnedDirectly() {
        CryptoMarketService service = newService();
        Map<String, Object> longChart = chartWithDailyPoints(600); // >=400 pts, >=500 days span
        when(coinGeckoPort.fetchMarketChart("bitcoin", "max", "usd", null)).thenReturn(longChart);

        Map<String, Object> result = service.getMarketChart("bitcoin", "max", "usd", null, null);

        assertThat(result).isEqualTo(longChart);
        verify(coinGeckoPort, never()).fetchMarketChartRange(anyString(), anyString(), anyLong(), anyLong());
    }

    @Test
    void getMarketChart_max_shortThenRangeAdequate() {
        CryptoMarketService service = newService();
        Map<String, Object> shortChart = chartWithDailyPoints(10);
        when(coinGeckoPort.fetchMarketChart("bitcoin", "max", "usd", null)).thenReturn(shortChart);
        when(coinGeckoPort.fetchCoinDetail("bitcoin")).thenReturn(Map.of("genesis_date", "2013-04-28"));
        Map<String, Object> rangeChart = chartWithDailyPoints(700);
        when(coinGeckoPort.fetchMarketChartRange(eq("bitcoin"), eq("usd"), anyLong(), anyLong()))
                .thenReturn(rangeChart);

        Map<String, Object> result = service.getMarketChart("bitcoin", "max", "usd", null, null);

        assertThat(result).isEqualTo(rangeChart);
    }

    @Test
    void getMarketChart_max_everythingEmpty_throws() {
        CryptoMarketService service = newService();
        // all 3 max attempts throw -> fromMax null; detail throws -> range best effort returns empty map
        when(coinGeckoPort.fetchMarketChart("bitcoin", "max", "usd", null))
                .thenThrow(new ExternalApiException("down"));
        when(coinGeckoPort.fetchCoinDetail("bitcoin"))
                .thenThrow(new ExternalApiException("detail down"));

        assertThatThrownBy(() -> service.getMarketChart("bitcoin", "max", "usd", null, null))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("chart unavailable");
        // retried 3 times for days=max
        verify(coinGeckoPort, times(3)).fetchMarketChart("bitcoin", "max", "usd", null);
    }

    // ================= fallback methods =================

    @Test
    void getCryptosFallback_cacheHit_returnsCachedAndPublishesLog() {
        CryptoMarketService service = newService();
        Cache cache = mock(Cache.class);
        when(cacheManager.getCache("cryptoMarketsCache")).thenReturn(cache);
        List<CryptoMarketItem> cached = List.of(item("bitcoin"));
        when(cache.get("try:p0:s20")).thenReturn(() -> cached);

        List<CryptoMarketItem> result = service.getCryptosFallback(0, 20, new RuntimeException("cb open"));

        assertThat(result).isSameAs(cached);
        verify(integrationLogService).publish(
                anyString(), eq("WARN"), anyString(), anyString(), anyString(),
                isNull(), isNull(), eq(true), isNull(), any(Map.class), anyString());
    }

    @Test
    void getCryptosFallback_noCache_throws() {
        CryptoMarketService service = newService();
        when(cacheManager.getCache("cryptoMarketsCache")).thenReturn(null);

        assertThatThrownBy(() -> service.getCryptosFallback(0, 20, new RuntimeException("cb open")))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("temporarily unavailable");
    }

    @Test
    void getCryptosFallback_cacheMiss_throws() {
        CryptoMarketService service = newService();
        Cache cache = mock(Cache.class);
        when(cacheManager.getCache("cryptoMarketsCache")).thenReturn(cache);
        when(cache.get("try:p0:s20")).thenReturn(null);

        assertThatThrownBy(() -> service.getCryptosFallback(0, 20, new RuntimeException("cb open")))
                .isInstanceOf(ExternalApiException.class);
    }

    @Test
    void getCryptosFallback_cacheMiss_butLkgHit_servesStaleData() {
        CryptoMarketService service = newService();
        // Spring cache boş (devre uzun süredir açık → TTL düştü).
        Cache cache = mock(Cache.class);
        when(cacheManager.getCache("cryptoMarketsCache")).thenReturn(cache);
        when(cache.get("try:p0:s20")).thenReturn(null);
        // LKG'de son geçerli sayfa var (key: getCryptos ile aynı format → page+1).
        List<CryptoMarketItem> stale = List.of(item("bitcoin"));
        when(lkg.peek(eq("crypto.coingecko.markets:try:p1:s20"), any()))
                .thenReturn(Optional.of(stale));

        List<CryptoMarketItem> result = service.getCryptosFallback(0, 20, new RuntimeException("cb open"));

        assertThat(result).isSameAs(stale);
        verify(integrationLogService).publish(
                anyString(), eq("WARN"), anyString(), anyString(), anyString(),
                isNull(), isNull(), eq(true), isNull(), any(Map.class), anyString());
    }

    @Test
    void getCryptosByCurrencyFallback_cacheMiss_butLkgHit_servesStaleData() {
        CryptoMarketService service = newService();
        Cache cache = mock(Cache.class);
        when(cacheManager.getCache("cryptoMarketsCache")).thenReturn(cache);
        when(cache.get("usd:p1:s50")).thenReturn(null);
        List<CryptoMarketItem> stale = List.of(item("ethereum"));
        when(lkg.peek(eq("crypto.coingecko.markets:usd:p2:s50"), any()))
                .thenReturn(Optional.of(stale));

        List<CryptoMarketItem> result =
                service.getCryptosByCurrencyFallback(1, 50, "USD", new RuntimeException("cb"));

        assertThat(result).isSameAs(stale);
    }

    @Test
    void getCryptosByCurrencyFallback_cacheHit_normalizesCurrencyKey() {
        CryptoMarketService service = newService();
        Cache cache = mock(Cache.class);
        when(cacheManager.getCache("cryptoMarketsCache")).thenReturn(cache);
        List<CryptoMarketItem> cached = List.of(item("bitcoin"));
        when(cache.get("usd:p1:s50")).thenReturn(() -> cached);

        List<CryptoMarketItem> result =
                service.getCryptosByCurrencyFallback(1, 50, "  USD ", new RuntimeException("cb"));

        assertThat(result).isSameAs(cached);
        verify(integrationLogService).publish(
                anyString(), eq("WARN"), anyString(), anyString(), anyString(),
                isNull(), isNull(), eq(true), isNull(), any(Map.class), anyString());
    }

    @Test
    void getCryptosByCurrencyFallback_blankCurrency_usesTryKey() {
        CryptoMarketService service = newService();
        Cache cache = mock(Cache.class);
        when(cacheManager.getCache("cryptoMarketsCache")).thenReturn(cache);
        List<CryptoMarketItem> cached = List.of(item("bitcoin"));
        when(cache.get("try:p0:s10")).thenReturn(() -> cached);

        List<CryptoMarketItem> result =
                service.getCryptosByCurrencyFallback(0, 10, "  ", new RuntimeException("cb"));

        assertThat(result).isSameAs(cached);
    }

    @Test
    void getCryptosByCurrencyFallback_noCacheHit_throws() {
        CryptoMarketService service = newService();
        Cache cache = mock(Cache.class);
        when(cacheManager.getCache("cryptoMarketsCache")).thenReturn(cache);
        when(cache.get(anyString())).thenReturn(null);

        assertThatThrownBy(() ->
                service.getCryptosByCurrencyFallback(0, 10, "usd", new RuntimeException("cb")))
                .isInstanceOf(ExternalApiException.class);
    }
}
