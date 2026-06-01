package com.finance.portal.market.application.commodity;

import com.finance.portal.market.application.fx.model.FxLatestRates;
import com.finance.portal.market.application.fx.model.FxRateItem;
import com.finance.portal.market.application.service.MarketFxService;
import com.finance.portal.market.application.stock.model.YahooChartSnapshot;
import com.finance.portal.market.application.stock.model.YahooQuoteSeries;
import com.finance.portal.market.application.stock.model.YahooStockMeta;
import com.finance.portal.market.application.stock.port.YahooStockPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class YahooCommodityServiceTest {

    @Mock
    private YahooStockPort yahooStockPort;

    @Mock
    private MarketFxService marketFxService;

    @InjectMocks
    private YahooCommodityService service;

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static YahooStockMeta meta(String currency, BigDecimal price, BigDecimal prevClose) {
        YahooStockMeta m = new YahooStockMeta();
        m.setCurrency(currency);
        m.setRegularMarketPrice(price);
        m.setPreviousClose(prevClose);
        m.setRegularMarketDayHigh(new BigDecimal("200"));
        m.setRegularMarketDayLow(new BigDecimal("90"));
        m.setFiftyTwoWeekHigh(new BigDecimal("300"));
        m.setFiftyTwoWeekLow(new BigDecimal("50"));
        m.setRegularMarketVolume(12345L);
        return m;
    }

    private static YahooChartSnapshot snapshotWithMeta(YahooStockMeta m) {
        YahooChartSnapshot s = new YahooChartSnapshot();
        s.setMeta(m);
        return s;
    }

    private static FxLatestRates usdTry(BigDecimal sell) {
        FxRateItem item = new FxRateItem("USD", new BigDecimal("32.0"), sell, 1);
        List<FxRateItem> rates = new ArrayList<>();
        rates.add(item);
        return new FxLatestRates("tcmb", "official", "TRY", "2026-01-01", rates);
    }

    private void noFx() {
        lenient().when(marketFxService.getTcmbLatestRates(anyString()))
                .thenReturn(new FxLatestRates("tcmb", "official", "TRY", "2026-01-01", null));
    }

    // ── listEnabledCommodities ───────────────────────────────────────────────

    @Test
    void listEnabledCommodities_returnsAllEnabledSymbols_withMetadataOnly() {
        List<CommodityDto> list = service.listEnabledCommodities();

        long enabledCount = java.util.Arrays.stream(CommoditySymbol.values())
                .filter(CommoditySymbol::isEnabled).count();
        assertThat(list).hasSize((int) enabledCount);
        assertThat(list).allSatisfy(dto -> {
            assertThat(dto.getSource()).isEqualTo("Yahoo Finance");
            assertThat(dto.getDataType()).isEqualTo("FUTURES_REFERENCE");
            assertThat(dto.isEnabled()).isTrue();
            assertThat(dto.getSymbol()).isNotBlank();
            assertThat(dto.getCategory()).isNotBlank();
            assertThat(dto.getCategoryDisplayTr()).isNotBlank();
        });
        assertThat(list).extracting(CommodityDto::getSymbol).contains("CL=F", "ZW=F");
    }

    @Test
    void listEnabledCommodities_centConversionFlag_matchesEnum() {
        List<CommodityDto> list = service.listEnabledCommodities();
        CommodityDto wheat = list.stream().filter(d -> d.getSymbol().equals("ZW=F")).findFirst().orElseThrow();
        CommodityDto wti = list.stream().filter(d -> d.getSymbol().equals("CL=F")).findFirst().orElseThrow();
        assertThat(wheat.isNeedsCentConversion()).isTrue();
        assertThat(wti.isNeedsCentConversion()).isFalse();
    }

    // ── getSpot ──────────────────────────────────────────────────────────────

    @Test
    void getSpot_disabledSymbol_throws() {
        // All enum values are enabled in current config; use an unknown symbol → fromSymbol throws.
        assertThatThrownBy(() -> service.getSpot("ZZ=F"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(yahooStockPort, never()).fetchChartWithParams(anyString(), anyString(), anyString());
    }

    @Test
    void getSpot_usdSymbol_mapsAndComputesChange_noCentConversion() {
        noFx();
        YahooStockMeta m = meta("USD", new BigDecimal("110"), new BigDecimal("100"));
        when(yahooStockPort.fetchChartWithParams("CL=F", "1d", "1m")).thenReturn(snapshotWithMeta(m));

        CommoditySpotDto dto = service.getSpot("CL=F");

        assertThat(dto.getSymbol()).isEqualTo("CL=F");
        assertThat(dto.getRawPrice()).isEqualByComparingTo("110");
        assertThat(dto.getRawCurrency()).isEqualTo("USD");
        assertThat(dto.getDisplayPrice()).isEqualByComparingTo("110");
        assertThat(dto.isCentConverted()).isFalse();
        // change = 110 - 100 = 10 ; pct = 10%
        assertThat(dto.getChange()).isEqualByComparingTo("10.0000");
        assertThat(dto.getChangePercent()).isEqualByComparingTo("10.00");
        assertThat(dto.isStale()).isFalse();
        assertThat(dto.getLastUpdated()).isNotNull();
        assertThat(dto.getDisplayCurrency()).isEqualTo("USD");
        assertThat(dto.getVolume()).isEqualTo(12345L);
    }

    @Test
    void getSpot_usxCurrency_appliesCentConversion() {
        noFx();
        YahooStockMeta m = meta("USX", new BigDecimal("250"), new BigDecimal("200"));
        when(yahooStockPort.fetchChartWithParams("ZW=F", "1d", "1m")).thenReturn(snapshotWithMeta(m));

        CommoditySpotDto dto = service.getSpot("ZW=F");

        assertThat(dto.isCentConverted()).isTrue();
        assertThat(dto.getDisplayPrice()).isEqualByComparingTo("2.50");
        assertThat(dto.getPreviousClose()).isEqualByComparingTo("2.00");
        // change in USD = 2.50 - 2.00 = 0.50
        assertThat(dto.getChange()).isEqualByComparingTo("0.5000");
    }

    @Test
    void getSpot_centConversionFromEnumFlag_evenWhenCurrencyUsd() {
        noFx();
        // ZC=F (Corn) has needsCentConversion=true in enum, currency reported as USD
        YahooStockMeta m = meta("USD", new BigDecimal("400"), new BigDecimal("400"));
        when(yahooStockPort.fetchChartWithParams("ZC=F", "1d", "1m")).thenReturn(snapshotWithMeta(m));

        CommoditySpotDto dto = service.getSpot("ZC=F");

        assertThat(dto.isCentConverted()).isTrue();
        assertThat(dto.getDisplayPrice()).isEqualByComparingTo("4");
    }

    @Test
    void getSpot_emptyMeta_marksStale() {
        YahooChartSnapshot empty = new YahooChartSnapshot(); // no meta
        when(yahooStockPort.fetchChartWithParams("CL=F", "1d", "1m")).thenReturn(empty);

        CommoditySpotDto dto = service.getSpot("CL=F");

        assertThat(dto.isStale()).isTrue();
        assertThat(dto.getRawPrice()).isNull();
        verify(marketFxService, never()).getTcmbLatestRates(anyString());
    }

    @Test
    void getSpot_upstreamThrows_marksStale() {
        when(yahooStockPort.fetchChartWithParams("CL=F", "1d", "1m"))
                .thenThrow(new RuntimeException("yahoo down"));

        CommoditySpotDto dto = service.getSpot("CL=F");

        assertThat(dto.isStale()).isTrue();
    }

    @Test
    void getSpot_nullPrevClose_skipsChange() {
        noFx();
        YahooStockMeta m = meta("USD", new BigDecimal("110"), null);
        when(yahooStockPort.fetchChartWithParams("CL=F", "1d", "1m")).thenReturn(snapshotWithMeta(m));

        CommoditySpotDto dto = service.getSpot("CL=F");

        assertThat(dto.getChange()).isNull();
        assertThat(dto.getChangePercent()).isNull();
        assertThat(dto.isStale()).isFalse();
    }

    @Test
    void getSpot_zeroPrevClose_skipsChange() {
        noFx();
        YahooStockMeta m = meta("USD", new BigDecimal("110"), BigDecimal.ZERO);
        when(yahooStockPort.fetchChartWithParams("CL=F", "1d", "1m")).thenReturn(snapshotWithMeta(m));

        CommoditySpotDto dto = service.getSpot("CL=F");

        assertThat(dto.getChange()).isNull();
        assertThat(dto.getChangePercent()).isNull();
    }

    @Test
    void getSpot_nullYahooCurrency_defaultsRawCurrencyUsd() {
        noFx();
        YahooStockMeta m = meta(null, new BigDecimal("110"), new BigDecimal("100"));
        when(yahooStockPort.fetchChartWithParams("CL=F", "1d", "1m")).thenReturn(snapshotWithMeta(m));

        CommoditySpotDto dto = service.getSpot("CL=F");

        assertThat(dto.getRawCurrency()).isEqualTo("USD");
        assertThat(dto.isCentConverted()).isFalse();
    }

    @Test
    void getSpot_appliesTcmbTryConversion_whenFxAvailable() {
        when(marketFxService.getTcmbLatestRates("USD")).thenReturn(usdTry(new BigDecimal("40")));
        YahooStockMeta m = meta("USD", new BigDecimal("110"), new BigDecimal("100"));
        when(yahooStockPort.fetchChartWithParams("CL=F", "1d", "1m")).thenReturn(snapshotWithMeta(m));

        CommoditySpotDto dto = service.getSpot("CL=F");

        assertThat(dto.getDisplayCurrency()).isEqualTo("TRY");
        // 110 USD * 40 = 4400
        assertThat(dto.getDisplayPrice()).isEqualByComparingTo("4400.0000");
        // prevClose 100 * 40 = 4000
        assertThat(dto.getPreviousClose()).isEqualByComparingTo("4000.0000");
        // change was 10 USD -> 400 TRY
        assertThat(dto.getChange()).isEqualByComparingTo("400.0000");
    }

    @Test
    void getSpot_fxLookupThrows_keepsUsdPrices() {
        when(marketFxService.getTcmbLatestRates("USD")).thenThrow(new RuntimeException("tcmb down"));
        YahooStockMeta m = meta("USD", new BigDecimal("110"), new BigDecimal("100"));
        when(yahooStockPort.fetchChartWithParams("CL=F", "1d", "1m")).thenReturn(snapshotWithMeta(m));

        CommoditySpotDto dto = service.getSpot("CL=F");

        // FX failed → stays USD
        assertThat(dto.getDisplayCurrency()).isEqualTo("USD");
        assertThat(dto.getDisplayPrice()).isEqualByComparingTo("110");
    }

    @Test
    void getSpot_fxRateMissingUsd_keepsUsdPrices() {
        FxLatestRates eurOnly = new FxLatestRates("tcmb", "official", "TRY", "2026-01-01",
                List.of(new FxRateItem("EUR", new BigDecimal("35"), new BigDecimal("36"), 1)));
        when(marketFxService.getTcmbLatestRates("USD")).thenReturn(eurOnly);
        YahooStockMeta m = meta("USD", new BigDecimal("110"), new BigDecimal("100"));
        when(yahooStockPort.fetchChartWithParams("CL=F", "1d", "1m")).thenReturn(snapshotWithMeta(m));

        CommoditySpotDto dto = service.getSpot("CL=F");

        assertThat(dto.getDisplayCurrency()).isEqualTo("USD");
        assertThat(dto.getDisplayPrice()).isEqualByComparingTo("110");
    }

    // ── getHistory ───────────────────────────────────────────────────────────

    @Test
    void getHistory_disabledSymbol_throws() {
        assertThatThrownBy(() -> service.getHistory("ZZ=F", "1M", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getHistory_mapsPoints_skipsNullTimestampsAndZeroCloses() {
        YahooChartSnapshot snap = new YahooChartSnapshot();
        YahooStockMeta m = new YahooStockMeta();
        m.setCurrency("USD");
        snap.setMeta(m);
        // ts[1] null → skipped ; close[2]=0 → skipped ; close[3]=null → skipped
        List<Long> ts = new ArrayList<>(java.util.Arrays.asList(1000L, null, 3000L, 4000L, 5000L));
        snap.setTimestamps(ts);
        YahooQuoteSeries q = new YahooQuoteSeries();
        q.setClose(new ArrayList<>(java.util.Arrays.asList(
                new BigDecimal("10"), new BigDecimal("11"), BigDecimal.ZERO, null, new BigDecimal("15"))));
        q.setOpen(new ArrayList<>(java.util.Arrays.asList(
                new BigDecimal("9"), new BigDecimal("9"), new BigDecimal("9"), new BigDecimal("9"), new BigDecimal("14"))));
        q.setHigh(new ArrayList<>(java.util.Arrays.asList(
                new BigDecimal("12"), new BigDecimal("12"), new BigDecimal("12"), new BigDecimal("12"), new BigDecimal("16"))));
        q.setLow(new ArrayList<>(java.util.Arrays.asList(
                new BigDecimal("8"), new BigDecimal("8"), new BigDecimal("8"), new BigDecimal("8"), new BigDecimal("13"))));
        q.setVolume(new ArrayList<>(java.util.Arrays.asList(100L, 100L, 100L, 100L, 500L)));
        snap.setQuote(q);

        when(yahooStockPort.fetchChartWithParams(eq("CL=F"), anyString(), anyString())).thenReturn(snap);

        CommodityHistoryResponse resp = service.getHistory("CL=F", "1M", null);

        // valid points: index 0 (ts1000 close10) and index 4 (ts5000 close15)
        assertThat(resp.getPoints()).hasSize(2);
        assertThat(resp.getPoints()).extracting(CommodityHistoryPointDto::getTimestamp)
                .containsExactly(1000L, 5000L);
        assertThat(resp.getPoints().get(0).getDisplayClose()).isEqualByComparingTo("10");
        assertThat(resp.getSymbol()).isEqualTo("CL=F");
        assertThat(resp.getDisplayCurrency()).isEqualTo("USD");
        assertThat(resp.getSource()).isEqualTo("Yahoo Finance");
        assertThat(resp.getDisclaimer()).isNotBlank();
        // 1M auto interval → 1d
        assertThat(resp.getInterval()).isEqualTo("1d");
    }

    @Test
    void getHistory_usxCurrency_convertsDisplayClose() {
        YahooChartSnapshot snap = new YahooChartSnapshot();
        YahooStockMeta m = new YahooStockMeta();
        m.setCurrency("USX");
        snap.setMeta(m);
        snap.setTimestamps(new ArrayList<>(List.of(1000L)));
        YahooQuoteSeries q = new YahooQuoteSeries();
        q.setClose(new ArrayList<>(List.of(new BigDecimal("250"))));
        snap.setQuote(q);
        when(yahooStockPort.fetchChartWithParams(eq("CL=F"), anyString(), anyString())).thenReturn(snap);

        CommodityHistoryResponse resp = service.getHistory("CL=F", "1M", null);

        assertThat(resp.getPoints()).hasSize(1);
        CommodityHistoryPointDto pt = resp.getPoints().get(0);
        assertThat(pt.isCentConverted()).isTrue();
        assertThat(pt.getDisplayClose()).isEqualByComparingTo("2.50");
        assertThat(pt.getRawClose()).isEqualByComparingTo("250");
        assertThat(pt.getRawCurrency()).isEqualTo("USX");
    }

    @Test
    void getHistory_emptyTimestamps_returnsEmptyPoints() {
        YahooChartSnapshot snap = new YahooChartSnapshot();
        snap.setTimestamps(new ArrayList<>());
        when(yahooStockPort.fetchChartWithParams(eq("CL=F"), anyString(), anyString())).thenReturn(snap);

        CommodityHistoryResponse resp = service.getHistory("CL=F", "1M", null);

        assertThat(resp.getPoints()).isEmpty();
    }

    @Test
    void getHistory_nullTimestamps_returnsEmptyPoints() {
        YahooChartSnapshot snap = new YahooChartSnapshot(); // timestamps null
        when(yahooStockPort.fetchChartWithParams(eq("CL=F"), anyString(), anyString())).thenReturn(snap);

        CommodityHistoryResponse resp = service.getHistory("CL=F", "1M", null);

        assertThat(resp.getPoints()).isEmpty();
    }

    @Test
    void getHistory_upstreamThrows_returnsResponseWithEmptyPoints() {
        when(yahooStockPort.fetchChartWithParams(eq("CL=F"), anyString(), anyString()))
                .thenThrow(new RuntimeException("boom"));

        CommodityHistoryResponse resp = service.getHistory("CL=F", "1M", null);

        assertThat(resp.getPoints()).isEmpty();
        assertThat(resp.getSymbol()).isEqualTo("CL=F");
    }

    @Test
    void getHistory_explicitInterval_usesProvidedInterval_andMappedRange() {
        YahooChartSnapshot snap = new YahooChartSnapshot();
        snap.setTimestamps(new ArrayList<>());
        when(yahooStockPort.fetchChartWithParams(eq("CL=F"), eq("5y"), eq("1d"))).thenReturn(snap);

        CommodityHistoryResponse resp = service.getHistory("CL=F", "5Y", "1d");

        assertThat(resp.getInterval()).isEqualTo("1d");
        verify(yahooStockPort).fetchChartWithParams("CL=F", "5y", "1d");
    }

    @Test
    void getHistory_autoInterval_perRangeMapping() {
        YahooChartSnapshot snap = new YahooChartSnapshot();
        snap.setTimestamps(new ArrayList<>());
        when(yahooStockPort.fetchChartWithParams(eq("CL=F"), anyString(), anyString())).thenReturn(snap);

        service.getHistory("CL=F", "1D", null);
        verify(yahooStockPort).fetchChartWithParams("CL=F", "1d", "5m");

        service.getHistory("CL=F", "1W", null);
        verify(yahooStockPort).fetchChartWithParams("CL=F", "5d", "1h");

        service.getHistory("CL=F", "5Y", null);
        verify(yahooStockPort).fetchChartWithParams("CL=F", "5y", "1wk");
    }

    @Test
    void getHistory_unknownRange_defaultsToOneMonthDaily() {
        YahooChartSnapshot snap = new YahooChartSnapshot();
        snap.setTimestamps(new ArrayList<>());
        when(yahooStockPort.fetchChartWithParams(eq("CL=F"), anyString(), anyString())).thenReturn(snap);

        service.getHistory("CL=F", "WEIRD", null);
        verify(yahooStockPort).fetchChartWithParams("CL=F", "1mo", "1d");
    }

    @Test
    void getHistory_nullRange_defaultsToOneMonthDaily() {
        YahooChartSnapshot snap = new YahooChartSnapshot();
        snap.setTimestamps(new ArrayList<>());
        when(yahooStockPort.fetchChartWithParams(eq("CL=F"), anyString(), anyString())).thenReturn(snap);

        service.getHistory("CL=F", null, null);
        verify(yahooStockPort).fetchChartWithParams("CL=F", "1mo", "1d");
    }

    // ── cache evict no-op methods (coverage) ──────────────────────────────────

    @Test
    void evictMethods_doNotThrow() {
        service.evictSpotCache();
        service.evictHistoryCache();
        verify(yahooStockPort, never()).fetchChart(any());
    }
}
