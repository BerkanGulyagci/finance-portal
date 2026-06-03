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
 * Additional branch coverage for {@link CryptoYahooChartService}, targeting arms NOT exercised by
 * {@code CryptoYahooChartServiceTest} (static smoke) or {@code CryptoYahooChartServiceMoreTest}:
 * resolveYahooBase null-name / blank-name / null-item / null-symbol-list / direct-match arms,
 * the EUR->USD line-chart fallback + line exception path, getTryLineViaUsd zero/negative/null
 * guards, firstEntry FX fallback, all-rows-skipped null, and fetchUsdTryByDay null-fields /
 * filter-skip arms.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CryptoYahooChartServiceExtraTest {

    @Mock
    private YahooStockPort yahooStockPort;
    @Mock
    private CryptoMarketService cryptoMarketService;
    @Mock
    private LastKnownGoodCache lkg;

    /** LKG wrapper must be transparent here: run the underlying fetch (Supplier) directly. */
    @BeforeEach
    void stubLkgPassThrough() {
        when(lkg.resilient(any(), any(), any(), any()))
                .thenAnswer(inv -> inv.<Supplier<?>>getArgument(3).get());
    }

    private CryptoYahooChartService newService() {
        return new CryptoYahooChartService(yahooStockPort, cryptoMarketService, lkg);
    }

    private static CryptoMarketItem item(String symbol, String name) {
        return new CryptoMarketItem("id", symbol, name, null,
                BigDecimal.ONE, BigDecimal.ONE, 1, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, "x");
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

    /** Snapshot whose quote is non-null but with only close populated (for line/TRY paths). */
    private static YahooChartSnapshot lineSnapshot(List<Long> ts, List<BigDecimal> close) {
        return snapshot(ts, null, null, null, close, null);
    }

    // ================= resolveYahooBase — name-resolution arms =================

    @Test
    void resolveYahooBase_blankAfterSanitization_returnedAsIs() {
        CryptoYahooChartService service = newService();
        // "!!!" sanitizes to "" -> early return of original, no port interaction
        assertThat(service.resolveYahooBase("!!!")).isEqualTo("!!!");
        verifyNoInteractions(cryptoMarketService);
        verifyNoInteractions(yahooStockPort);
    }

    @Test
    void resolveYahooBase_nullItem_usesTickerAsQuery() {
        CryptoYahooChartService service = newService();
        when(cryptoMarketService.findBySymbol("BTC")).thenReturn(null);
        when(yahooStockPort.searchCryptoUsdSymbols("BTC")).thenReturn(List.of("OTHER-USD"));

        assertThat(service.resolveYahooBase("btc")).isEqualTo("BTC");
        verify(yahooStockPort).searchCryptoUsdSymbols("BTC");
    }

    @Test
    void resolveYahooBase_itemWithNullName_usesTickerAsQuery() {
        CryptoYahooChartService service = newService();
        when(cryptoMarketService.findBySymbol("ETH")).thenReturn(item("eth", null));
        when(yahooStockPort.searchCryptoUsdSymbols("ETH")).thenReturn(List.of("OTHER-USD"));

        assertThat(service.resolveYahooBase("eth")).isEqualTo("ETH");
        verify(yahooStockPort).searchCryptoUsdSymbols("ETH");
    }

    @Test
    void resolveYahooBase_itemWithBlankName_usesTickerAsQuery() {
        CryptoYahooChartService service = newService();
        when(cryptoMarketService.findBySymbol("XRP")).thenReturn(item("xrp", "   "));
        when(yahooStockPort.searchCryptoUsdSymbols("XRP")).thenReturn(List.of("OTHER-USD"));

        assertThat(service.resolveYahooBase("xrp")).isEqualTo("XRP");
        verify(yahooStockPort).searchCryptoUsdSymbols("XRP");
    }

    @Test
    void resolveYahooBase_nullSymbolList_fallsBackToTickerAndCaches() {
        CryptoYahooChartService service = newService();
        when(cryptoMarketService.findBySymbol("DAI")).thenReturn(item("dai", "Dai"));
        when(yahooStockPort.searchCryptoUsdSymbols("Dai")).thenReturn(null);

        assertThat(service.resolveYahooBase("dai")).isEqualTo("DAI");
        // cached -> second call does not re-query
        service.resolveYahooBase("dai");
        verify(yahooStockPort, times(1)).searchCryptoUsdSymbols("Dai");
    }

    @Test
    void resolveYahooBase_directTickerMatch_noNumericSuffix_resolvesWithoutLog() {
        CryptoYahooChartService service = newService();
        when(cryptoMarketService.findBySymbol("BTC")).thenReturn(item("btc", "Bitcoin"));
        // Yahoo returns exactly BTC-USD -> resolvedBase "BTC" equals key -> match arm, no rename log
        when(yahooStockPort.searchCryptoUsdSymbols("Bitcoin")).thenReturn(List.of("BTC-USD"));

        assertThat(service.resolveYahooBase("btc")).isEqualTo("BTC");
        service.resolveYahooBase("btc");
        verify(yahooStockPort, times(1)).searchCryptoUsdSymbols("Bitcoin"); // cached via match
    }

    @Test
    void resolveYahooBase_skipsNonUsdSuffixEntries() {
        CryptoYahooChartService service = newService();
        when(cryptoMarketService.findBySymbol("MNT")).thenReturn(item("mnt", "Mantle"));
        // First entry not -USD (continue), then matching numeric-suffix -USD
        when(yahooStockPort.searchCryptoUsdSymbols("Mantle"))
                .thenReturn(Arrays.asList("MNT-EUR", "MNT27075-USD"));

        assertThat(service.resolveYahooBase("mnt")).isEqualTo("MNT27075");
    }

    // ================= getLineChart — EUR fallback + exception arm =================

    @Test
    void getLineChart_eurNullChart_fallsBackToUsd() {
        CryptoYahooChartService service = newService();
        when(cryptoMarketService.findBySymbol("BTC")).thenReturn(null);
        when(yahooStockPort.searchCryptoUsdSymbols("BTC")).thenReturn(List.of());
        // EUR returns empty -> chart null -> fall to USD
        when(yahooStockPort.fetchChartWithParams("BTC-EUR", "5y", "1d"))
                .thenReturn(lineSnapshot(List.of(), List.of()));
        when(yahooStockPort.fetchChartWithParams("BTC-USD", "5y", "1d"))
                .thenReturn(lineSnapshot(List.of(1000L), List.of(new BigDecimal("12.34"))));

        StockChartResponse chart = service.getLineChart("btc", "5y", "eur");

        assertThat(chart).isNotNull();
        assertThat(chart.getSymbol()).isEqualTo("BTC-USD");
        verify(yahooStockPort).fetchChartWithParams("BTC-EUR", "5y", "1d");
        verify(yahooStockPort).fetchChartWithParams("BTC-USD", "5y", "1d");
    }

    @Test
    void getLineChart_yahooThrows_returnsNull() {
        CryptoYahooChartService service = newService();
        when(cryptoMarketService.findBySymbol("BTC")).thenReturn(null);
        when(yahooStockPort.searchCryptoUsdSymbols("BTC")).thenReturn(List.of());
        when(yahooStockPort.fetchChartWithParams("BTC-USD", "5y", "1d"))
                .thenThrow(new RuntimeException("yahoo line down"));

        assertThat(service.getLineChart("btc", "5y", "usd")).isNull();
    }

    // ================= getTryLineViaUsd — guard/skip arms =================

    @Test
    void getTryLineViaUsd_zeroAndNegativeUsdClose_skipped() {
        CryptoYahooChartService service = newService();
        when(cryptoMarketService.findBySymbol("BTC")).thenReturn(null);
        when(yahooStockPort.searchCryptoUsdSymbols("BTC")).thenReturn(List.of());
        long d1 = 1_577_836_800L; // 2020-01-01
        long d2 = d1 + 86_400L;
        long d3 = d2 + 86_400L;
        // first close zero (skip), second negative (skip), third valid
        when(yahooStockPort.fetchChartWithParams("BTC-USD", "5y", "1d")).thenReturn(lineSnapshot(
                List.of(d1, d2, d3),
                Arrays.asList(BigDecimal.ZERO, new BigDecimal("-5"), new BigDecimal("100"))));
        when(yahooStockPort.fetchChartWithParams("TRY=X", "5y", "1d")).thenReturn(lineSnapshot(
                List.of(d1), List.of(new BigDecimal("30"))));

        StockChartResponse chart = service.getTryLineViaUsd("btc", "5y");

        assertThat(chart).isNotNull();
        assertThat(chart.getTimestamps()).containsExactly(d3);
        assertThat(chart.getClosePrices().get(0)).isEqualByComparingTo("3000.000000");
    }

    @Test
    void getTryLineViaUsd_nullEpochAndNullClose_skipped() {
        CryptoYahooChartService service = newService();
        when(cryptoMarketService.findBySymbol("BTC")).thenReturn(null);
        when(yahooStockPort.searchCryptoUsdSymbols("BTC")).thenReturn(List.of());
        long d2 = 1_577_923_200L; // 2020-01-02
        long d3 = d2 + 86_400L;
        // timestamps: null (skip), valid d2 with null close (skip), valid d3 with close
        when(yahooStockPort.fetchChartWithParams("BTC-USD", "5y", "1d")).thenReturn(lineSnapshot(
                Arrays.asList(null, d2, d3),
                Arrays.asList(new BigDecimal("50"), null, new BigDecimal("100"))));
        when(yahooStockPort.fetchChartWithParams("TRY=X", "5y", "1d")).thenReturn(lineSnapshot(
                List.of(d2), List.of(new BigDecimal("30"))));

        StockChartResponse chart = service.getTryLineViaUsd("btc", "5y");

        assertThat(chart).isNotNull();
        assertThat(chart.getTimestamps()).containsExactly(d3);
    }

    @Test
    void getTryLineViaUsd_floorEntryNull_usesFirstEntryRate() {
        CryptoYahooChartService service = newService();
        when(cryptoMarketService.findBySymbol("BTC")).thenReturn(null);
        when(yahooStockPort.searchCryptoUsdSymbols("BTC")).thenReturn(List.of());
        long usdDay = 1_577_836_800L;      // 2020-01-01 (earlier than any FX day)
        long fxDay = usdDay + 86_400L;     // 2020-01-02
        when(yahooStockPort.fetchChartWithParams("BTC-USD", "5y", "1d")).thenReturn(lineSnapshot(
                List.of(usdDay), List.of(new BigDecimal("100"))));
        // FX only has a LATER day -> floorEntry(usdDay) == null -> firstEntry fallback (rate 40)
        when(yahooStockPort.fetchChartWithParams("TRY=X", "5y", "1d")).thenReturn(lineSnapshot(
                List.of(fxDay), List.of(new BigDecimal("40"))));

        StockChartResponse chart = service.getTryLineViaUsd("btc", "5y");

        assertThat(chart).isNotNull();
        assertThat(chart.getClosePrices().get(0)).isEqualByComparingTo("4000.000000");
    }

    @Test
    void getTryLineViaUsd_allRowsSkipped_returnsNull() {
        CryptoYahooChartService service = newService();
        when(cryptoMarketService.findBySymbol("BTC")).thenReturn(null);
        when(yahooStockPort.searchCryptoUsdSymbols("BTC")).thenReturn(List.of());
        long d1 = 1_577_836_800L;
        // all USD closes non-positive -> outTs ends empty -> null
        when(yahooStockPort.fetchChartWithParams("BTC-USD", "5y", "1d")).thenReturn(lineSnapshot(
                List.of(d1), List.of(BigDecimal.ZERO)));
        when(yahooStockPort.fetchChartWithParams("TRY=X", "5y", "1d")).thenReturn(lineSnapshot(
                List.of(d1), List.of(new BigDecimal("30"))));

        assertThat(service.getTryLineViaUsd("btc", "5y")).isNull();
    }

    @Test
    void getTryLineViaUsd_fxNullClosePrices_emptyMap_returnsNull() {
        CryptoYahooChartService service = newService();
        when(cryptoMarketService.findBySymbol("BTC")).thenReturn(null);
        when(yahooStockPort.searchCryptoUsdSymbols("BTC")).thenReturn(List.of());
        long d1 = 1_577_836_800L;
        when(yahooStockPort.fetchChartWithParams("BTC-USD", "5y", "1d")).thenReturn(lineSnapshot(
                List.of(d1), List.of(new BigDecimal("100"))));
        // FX snapshot has timestamps but null closes -> fetchUsdTryByDay returns empty map -> null
        when(yahooStockPort.fetchChartWithParams("TRY=X", "5y", "1d")).thenReturn(
                snapshot(List.of(d1), null, null, null, null, null));

        assertThat(service.getTryLineViaUsd("btc", "5y")).isNull();
    }

    @Test
    void getTryLineViaUsd_fxFilterSkipsZeroAndNullRates() {
        CryptoYahooChartService service = newService();
        when(cryptoMarketService.findBySymbol("BTC")).thenReturn(null);
        when(yahooStockPort.searchCryptoUsdSymbols("BTC")).thenReturn(List.of());
        long d1 = 1_577_836_800L;
        long d2 = d1 + 86_400L;
        long d3 = d2 + 86_400L;
        when(yahooStockPort.fetchChartWithParams("BTC-USD", "5y", "1d")).thenReturn(lineSnapshot(
                List.of(d3), List.of(new BigDecimal("100"))));
        // FX: d1 rate zero (skip), d2 rate null (skip), d3 valid -> only d3 mapped
        when(yahooStockPort.fetchChartWithParams("TRY=X", "5y", "1d")).thenReturn(lineSnapshot(
                Arrays.asList(d1, d2, d3),
                Arrays.asList(BigDecimal.ZERO, null, new BigDecimal("50"))));

        StockChartResponse chart = service.getTryLineViaUsd("btc", "5y");

        assertThat(chart).isNotNull();
        assertThat(chart.getClosePrices().get(0)).isEqualByComparingTo("5000.000000");
    }

    // ================= getOhlc — close-only quote (high/low/open null) skips all =================

    @Test
    void getOhlc_quoteWithoutOhlcComponents_emptyResult() {
        CryptoYahooChartService service = newService();
        when(cryptoMarketService.findBySymbol("BTC")).thenReturn(null);
        when(yahooStockPort.searchCryptoUsdSymbols("BTC")).thenReturn(List.of());
        // close populated but open/high/low null -> every row skipped -> empty -> empty result
        when(yahooStockPort.fetchChartWithParams("BTC-USD", "5y", "1d"))
                .thenReturn(lineSnapshot(List.of(1000L), List.of(new BigDecimal("10"))));

        assertThat(service.getOhlc("btc", "5y", "usd")).isEmpty();
    }

    @Test
    void getOhlc_nullQuote_emptyResult() {
        CryptoYahooChartService service = newService();
        when(cryptoMarketService.findBySymbol("BTC")).thenReturn(null);
        when(yahooStockPort.searchCryptoUsdSymbols("BTC")).thenReturn(List.of());
        YahooChartSnapshot s = new YahooChartSnapshot();
        s.setTimestamps(List.of(1000L));
        s.setQuote(null); // null quote -> fetchOhlcFromYahoo returns empty -> loop empties -> empty
        when(yahooStockPort.fetchChartWithParams("BTC-USD", "5y", "1d")).thenReturn(s);

        assertThat(service.getOhlc("btc", "5y", "usd")).isEmpty();
    }
}
