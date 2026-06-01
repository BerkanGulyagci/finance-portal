package com.finance.portal.market.application.crypto;

import com.finance.portal.common.infrastructure.cache.LastKnownGoodCache;
import com.finance.portal.market.application.crypto.model.CryptoMarketItem;
import com.finance.portal.market.application.stock.StockChartResponse;
import com.finance.portal.market.application.stock.model.YahooChartSnapshot;
import com.finance.portal.market.application.stock.model.YahooQuoteSeries;
import com.finance.portal.market.application.stock.port.YahooStockPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Extended coverage for {@link CryptoYahooChartService} — resolveYahooBase, OHLC/line/TRY paths,
 * currency fallback and exception handling. Complements the existing minimal
 * {@code CryptoYahooChartServiceTest} (static helper smoke tests).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CryptoYahooChartServiceMoreTest {

    @Mock
    private YahooStockPort yahooStockPort;
    @Mock
    private CryptoMarketService cryptoMarketService;
    @Mock
    private LastKnownGoodCache lkg;

    /** LKG sarmalayıcı bu birim testlerde şeffaf olmalı: doğrudan asıl çekimi (Supplier) çalıştır. */
    @BeforeEach
    void stubLkgPassThrough() {
        when(lkg.resilient(any(), any(), any(), any()))
                .thenAnswer(inv -> inv.<Supplier<?>>getArgument(3).get());
    }

    private CryptoYahooChartService newService() {
        return new CryptoYahooChartService(yahooStockPort, cryptoMarketService, lkg);
    }

    private static YahooChartSnapshot snapshot(List<Long> ts,
                                               List<BigDecimal> open,
                                               List<BigDecimal> high,
                                               List<BigDecimal> low,
                                               List<BigDecimal> close,
                                               List<Long> volume) {
        YahooQuoteSeries q = new YahooQuoteSeries();
        q.setOpen(open);
        q.setHigh(high);
        q.setLow(low);
        q.setClose(close);
        q.setVolume(volume);
        YahooChartSnapshot s = new YahooChartSnapshot();
        s.setTimestamps(ts);
        s.setQuote(q);
        return s;
    }

    private void stubResolvesToTicker(String ticker) {
        when(cryptoMarketService.findBySymbol(ticker.toUpperCase())).thenThrow(new RuntimeException("skip"));
        when(yahooStockPort.searchCryptoUsdSymbols(ticker.toUpperCase())).thenReturn(List.of());
    }

    // ================= shouldUseYahoo extra =================

    @Test
    void shouldUseYahoo_nullArgs_false() {
        assertThat(CryptoYahooChartService.shouldUseYahoo(null, "5y")).isFalse();
        assertThat(CryptoYahooChartService.shouldUseYahoo("usd", null)).isFalse();
    }

    // ================= toYahooSymbol extra =================

    @Test
    void toYahooSymbol_unsupportedCurrencyDefaultsUsd_andBlankNull() {
        assertThat(CryptoYahooChartService.toYahooSymbol("btc", "try")).isEqualTo("BTC-USD");
        assertThat(CryptoYahooChartService.toYahooSymbol("btc", null)).isEqualTo("BTC-USD");
        assertThat(CryptoYahooChartService.toYahooSymbol("  ", "usd")).isNull();
        assertThat(CryptoYahooChartService.toYahooSymbol("!!!", "usd")).isNull();
    }

    @Test
    void resolveYahooRangeInterval_unknown_null() {
        assertThat(CryptoYahooChartService.resolveYahooRangeInterval("1y")).isNull();
    }

    // ================= resolveYahooBase =================

    @Test
    void resolveYahooBase_blank_returnedAsIs() {
        CryptoYahooChartService service = newService();
        assertThat(service.resolveYahooBase(null)).isNull();
        assertThat(service.resolveYahooBase("   ")).isEqualTo("   ");
        verifyNoInteractions(yahooStockPort);
    }

    @Test
    void resolveYahooBase_matchingNumericSuffix_resolvesAndCaches() {
        CryptoYahooChartService service = newService();
        CryptoMarketItem usdg = new CryptoMarketItem("usdg", "usdg", "Global Dollar", null,
                BigDecimal.ONE, BigDecimal.ONE, 1, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, "x");
        when(cryptoMarketService.findBySymbol("USDG")).thenReturn(usdg);
        when(yahooStockPort.searchCryptoUsdSymbols("Global Dollar"))
                .thenReturn(List.of("SOMETHING-EUR", "USDG33793-USD"));

        assertThat(service.resolveYahooBase("usdg")).isEqualTo("USDG33793");
        // cached — second call does not hit the port again
        assertThat(service.resolveYahooBase("usdg")).isEqualTo("USDG33793");
        verify(yahooStockPort, times(1)).searchCryptoUsdSymbols(anyString());
    }

    @Test
    void resolveYahooBase_noNumericMatch_fallsBackToTickerAndCaches() {
        CryptoYahooChartService service = newService();
        when(cryptoMarketService.findBySymbol("BTC")).thenThrow(new RuntimeException("not found"));
        when(yahooStockPort.searchCryptoUsdSymbols("BTC")).thenReturn(List.of("OTHER-USD"));

        assertThat(service.resolveYahooBase("btc")).isEqualTo("BTC");
        service.resolveYahooBase("btc");
        verify(yahooStockPort, times(1)).searchCryptoUsdSymbols("BTC"); // cached
    }

    @Test
    void resolveYahooBase_searchThrows_returnsTickerWithoutCaching() {
        CryptoYahooChartService service = newService();
        when(cryptoMarketService.findBySymbol("BTC")).thenThrow(new RuntimeException("x"));
        when(yahooStockPort.searchCryptoUsdSymbols("BTC")).thenThrow(new RuntimeException("yahoo down"));

        assertThat(service.resolveYahooBase("btc")).isEqualTo("BTC");
        service.resolveYahooBase("btc");
        verify(yahooStockPort, times(2)).searchCryptoUsdSymbols("BTC"); // not cached -> retried
    }

    // ================= getOhlc =================

    @Test
    void getOhlc_notEligible_returnsEmpty() {
        CryptoYahooChartService service = newService();
        assertThat(service.getOhlc("btc", "1y", "usd")).isEmpty();
        assertThat(service.getOhlc("btc", "5y", "try")).isEmpty();
        verifyNoInteractions(yahooStockPort);
    }

    @Test
    void getOhlc_buildsCandlesFromSnapshot() {
        CryptoYahooChartService service = newService();
        stubResolvesToTicker("btc");
        YahooChartSnapshot snap = snapshot(
                List.of(1000L, 2000L),
                List.of(new BigDecimal("10"), new BigDecimal("20")),
                List.of(new BigDecimal("11"), new BigDecimal("21")),
                List.of(new BigDecimal("9"), new BigDecimal("19")),
                List.of(new BigDecimal("10.5"), new BigDecimal("20.5")),
                List.of(100L, 200L));
        when(yahooStockPort.fetchChartWithParams("BTC-USD", "5y", "1d")).thenReturn(snap);

        List<Map<String, Object>> rows = service.getOhlc("btc", "5y", "usd");

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0))
                .containsEntry("time", 1000L)
                .containsEntry("open", new BigDecimal("10"))
                .containsEntry("close", new BigDecimal("10.5"))
                .containsEntry("volume", 100L);
    }

    @Test
    void getOhlc_skipsRowsWithNullComponents() {
        CryptoYahooChartService service = newService();
        stubResolvesToTicker("btc");
        YahooChartSnapshot snap = snapshot(
                List.of(1000L, 2000L),
                Arrays.asList(new BigDecimal("10"), null), // second open missing
                List.of(new BigDecimal("11"), new BigDecimal("21")),
                List.of(new BigDecimal("9"), new BigDecimal("19")),
                List.of(new BigDecimal("10.5"), new BigDecimal("20.5")),
                List.of(100L, 200L));
        when(yahooStockPort.fetchChartWithParams("BTC-USD", "5y", "1d")).thenReturn(snap);

        assertThat(service.getOhlc("btc", "5y", "usd")).hasSize(1);
    }

    @Test
    void getOhlc_eurEmpty_fallsBackToUsd() {
        CryptoYahooChartService service = newService();
        stubResolvesToTicker("btc");
        when(yahooStockPort.fetchChartWithParams("BTC-EUR", "5y", "1d"))
                .thenReturn(snapshot(List.of(), null, null, null, null, null));
        when(yahooStockPort.fetchChartWithParams("BTC-USD", "5y", "1d")).thenReturn(snapshot(
                List.of(1000L),
                List.of(new BigDecimal("10")), List.of(new BigDecimal("11")),
                List.of(new BigDecimal("9")), List.of(new BigDecimal("10.5")),
                List.of(100L)));

        assertThat(service.getOhlc("btc", "5y", "eur")).hasSize(1);
        verify(yahooStockPort).fetchChartWithParams("BTC-EUR", "5y", "1d");
        verify(yahooStockPort).fetchChartWithParams("BTC-USD", "5y", "1d");
    }

    @Test
    void getOhlc_yahooThrows_returnsEmpty() {
        CryptoYahooChartService service = newService();
        stubResolvesToTicker("btc");
        when(yahooStockPort.fetchChartWithParams("BTC-USD", "5y", "1d"))
                .thenThrow(new RuntimeException("yahoo error"));

        assertThat(service.getOhlc("btc", "5y", "usd")).isEmpty();
    }

    // ================= getLineChart =================

    @Test
    void getLineChart_notEligible_null() {
        CryptoYahooChartService service = newService();
        assertThat(service.getLineChart("btc", "1y", "usd")).isNull();
    }

    @Test
    void getLineChart_filtersNullCloses_setsSymbol() {
        CryptoYahooChartService service = newService();
        stubResolvesToTicker("btc");
        YahooChartSnapshot snap = snapshot(
                List.of(1000L, 2000L), null, null, null,
                Arrays.asList(new BigDecimal("10.5"), null), null);
        when(yahooStockPort.fetchChartWithParams("BTC-USD", "max", "1d")).thenReturn(snap);

        StockChartResponse chart = service.getLineChart("btc", "max", "usd");

        assertThat(chart).isNotNull();
        assertThat(chart.getSymbol()).isEqualTo("BTC-USD");
        assertThat(chart.getTimestamps()).containsExactly(1000L);
        assertThat(chart.getClosePrices()).containsExactly(new BigDecimal("10.5"));
    }

    @Test
    void getLineChart_emptySnapshot_null() {
        CryptoYahooChartService service = newService();
        stubResolvesToTicker("btc");
        when(yahooStockPort.fetchChartWithParams("BTC-USD", "5y", "1d"))
                .thenReturn(snapshot(List.of(), null, null, null, List.of(), null));

        assertThat(service.getLineChart("btc", "5y", "usd")).isNull();
    }

    // ================= getTryLineViaUsd =================

    @Test
    void getTryLineViaUsd_notEligibleRange_null() {
        CryptoYahooChartService service = newService();
        assertThat(service.getTryLineViaUsd("btc", "1y")).isNull();
        verifyNoInteractions(yahooStockPort);
    }

    @Test
    void getTryLineViaUsd_multipliesUsdCloseByFxRate() {
        CryptoYahooChartService service = newService();
        stubResolvesToTicker("btc");
        long day1 = 1_577_836_800L; // 2020-01-01 UTC
        long day2 = day1 + 86_400L;

        when(yahooStockPort.fetchChartWithParams("BTC-USD", "5y", "1d")).thenReturn(snapshot(
                List.of(day1, day2), null, null, null,
                List.of(new BigDecimal("100"), new BigDecimal("200")), null));
        when(yahooStockPort.fetchChartWithParams("TRY=X", "5y", "1d")).thenReturn(snapshot(
                List.of(day1, day2), null, null, null,
                List.of(new BigDecimal("30"), new BigDecimal("32")), null));

        StockChartResponse chart = service.getTryLineViaUsd("btc", "5y");

        assertThat(chart).isNotNull();
        assertThat(chart.getSymbol()).isEqualTo("BTC-USD·TRY");
        assertThat(chart.getTimestamps()).containsExactly(day1, day2);
        assertThat(chart.getClosePrices().get(0)).isEqualByComparingTo("3000.000000");
        assertThat(chart.getClosePrices().get(1)).isEqualByComparingTo("6400.000000");
    }

    @Test
    void getTryLineViaUsd_forwardFillsFxWithFloorEntry() {
        CryptoYahooChartService service = newService();
        stubResolvesToTicker("btc");
        long day1 = 1_577_836_800L;
        long day2 = day1 + 86_400L;
        // USD has two days; FX only has day1 -> day2 forward-filled from day1 rate
        when(yahooStockPort.fetchChartWithParams("BTC-USD", "5y", "1d")).thenReturn(snapshot(
                List.of(day1, day2), null, null, null,
                List.of(new BigDecimal("100"), new BigDecimal("100")), null));
        when(yahooStockPort.fetchChartWithParams("TRY=X", "5y", "1d")).thenReturn(snapshot(
                List.of(day1), null, null, null,
                List.of(new BigDecimal("30")), null));

        StockChartResponse chart = service.getTryLineViaUsd("btc", "5y");

        assertThat(chart).isNotNull();
        assertThat(chart.getClosePrices()).hasSize(2);
        assertThat(chart.getClosePrices().get(1)).isEqualByComparingTo("3000.000000");
    }

    @Test
    void getTryLineViaUsd_noUsdLine_null() {
        CryptoYahooChartService service = newService();
        stubResolvesToTicker("btc");
        when(yahooStockPort.fetchChartWithParams("BTC-USD", "5y", "1d"))
                .thenReturn(snapshot(List.of(), null, null, null, List.of(), null));

        assertThat(service.getTryLineViaUsd("btc", "5y")).isNull();
    }

    @Test
    void getTryLineViaUsd_fxFetchThrows_null() {
        CryptoYahooChartService service = newService();
        stubResolvesToTicker("btc");
        long day1 = 1_577_836_800L;
        when(yahooStockPort.fetchChartWithParams("BTC-USD", "5y", "1d")).thenReturn(snapshot(
                List.of(day1), null, null, null, List.of(new BigDecimal("100")), null));
        when(yahooStockPort.fetchChartWithParams("TRY=X", "5y", "1d"))
                .thenThrow(new RuntimeException("fx down"));

        assertThat(service.getTryLineViaUsd("btc", "5y")).isNull();
    }
}
