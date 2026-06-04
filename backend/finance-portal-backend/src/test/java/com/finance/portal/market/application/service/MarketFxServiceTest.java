package com.finance.portal.market.application.service;

import com.finance.portal.common.infrastructure.cache.LastKnownGoodCache;
import com.finance.portal.market.application.fx.model.FxHistory;
import com.finance.portal.market.application.fx.model.FxHistoryPoint;
import com.finance.portal.market.application.fx.model.FxLatestRates;
import com.finance.portal.market.application.fx.model.FxRateItem;
import com.finance.portal.market.application.fx.model.OpenFxFeed;
import com.finance.portal.market.application.fx.model.TcmbFxCurrencyRow;
import com.finance.portal.market.application.fx.model.TcmbFxFeed;
import com.finance.portal.market.application.fx.port.OpenFxPort;
import com.finance.portal.market.application.fx.port.TcmbFxHistoryPort;
import com.finance.portal.market.application.fx.port.TcmbFxPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketFxServiceTest {

    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");

    @Mock
    private TcmbFxPort tcmbFxPort;
    @Mock
    private OpenFxPort openFxPort;
    @Mock
    private TcmbFxHistoryPort tcmbFxHistoryPort;
    @Mock
    private LastKnownGoodCache lkg;

    @InjectMocks
    private MarketFxService service;

    /** LKG sarmalayıcı bu birim testlerde şeffaf olmalı: doğrudan asıl çekimi (Supplier) çalıştır. */
    @BeforeEach
    void stubLkgPassThrough() {
        lenient().when(lkg.resilient(any(), any(), any(), any()))
                .thenAnswer(inv -> inv.<Supplier<?>>getArgument(3).get());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static TcmbFxCurrencyRow row(String code, Integer unit, String buy, String sell) {
        return new TcmbFxCurrencyRow(code, unit, buy, sell);
    }

    private static TcmbFxCurrencyRow rowFull(String code, Integer unit, String buy, String sell,
                                             String bankBuy, String bankSell) {
        return new TcmbFxCurrencyRow(code, unit, buy, sell, bankBuy, bankSell);
    }

    private static FxRateItem bySymbol(FxLatestRates rates, String symbol) {
        return rates.getRates().stream()
                .filter(r -> symbol.equals(r.getSymbol()))
                .findFirst().orElse(null);
    }

    // ── getTcmbLatestRates ───────────────────────────────────────────────────

    @Test
    @DisplayName("TCMB latest: maps buy/sell/unit + optional banknote, sets official metadata")
    void getTcmbLatestRates_mapsAndCarriesMetadata() {
        TcmbFxFeed feed = new TcmbFxFeed("2026-05-30", List.of(
                rowFull("USD", 1, "40.10", "40.30", "40.05", "40.40"),
                rowFull("JPY", 100, "0.25", "0.26", null, "")));
        when(tcmbFxPort.fetchLatestRates()).thenReturn(feed);

        FxLatestRates result = service.getTcmbLatestRates(null);

        assertThat(result.getProvider()).isEqualTo("tcmb");
        assertThat(result.getSourceType()).isEqualTo("official");
        assertThat(result.getBase()).isEqualTo("TRY");
        assertThat(result.getAsOf()).isEqualTo("2026-05-30");
        assertThat(result.getRates()).hasSize(2);

        FxRateItem usd = bySymbol(result, "USD");
        assertThat(usd.getBuy()).isEqualByComparingTo("40.10");
        assertThat(usd.getSell()).isEqualByComparingTo("40.30");
        assertThat(usd.getUnit()).isEqualTo(1);
        assertThat(usd.getEffectiveBuy()).isEqualByComparingTo("40.05");
        assertThat(usd.getEffectiveSell()).isEqualByComparingTo("40.40");

        // JPY: unit 100, banknote null/blank -> effective values null
        FxRateItem jpy = bySymbol(result, "JPY");
        assertThat(jpy.getUnit()).isEqualTo(100);
        assertThat(jpy.getEffectiveBuy()).isNull();
        assertThat(jpy.getEffectiveSell()).isNull();
    }

    @Test
    @DisplayName("TCMB latest: null unit defaults to 1")
    void getTcmbLatestRates_nullUnitDefaultsToOne() {
        when(tcmbFxPort.fetchLatestRates()).thenReturn(
                new TcmbFxFeed("2026-05-30", List.of(row("GBP", null, "53.10", "53.40"))));

        FxLatestRates result = service.getTcmbLatestRates(null);

        assertThat(bySymbol(result, "GBP").getUnit()).isEqualTo(1);
    }

    @Test
    @DisplayName("TCMB latest: symbols filter is case-insensitive and trimmed")
    void getTcmbLatestRates_filtersBySymbolsCaseInsensitive() {
        when(tcmbFxPort.fetchLatestRates()).thenReturn(new TcmbFxFeed("2026-05-30", List.of(
                row("USD", 1, "40.10", "40.30"),
                row("EUR", 1, "43.10", "43.40"),
                row("GBP", 1, "53.10", "53.40"))));

        FxLatestRates result = service.getTcmbLatestRates(" usd , eur ");

        assertThat(result.getRates()).extracting(FxRateItem::getSymbol)
                .containsExactlyInAnyOrder("USD", "EUR");
    }

    @Test
    @DisplayName("TCMB latest: rows with blank forexBuying/forexSelling are skipped")
    void getTcmbLatestRates_skipsBlankForexRows() {
        when(tcmbFxPort.fetchLatestRates()).thenReturn(new TcmbFxFeed("2026-05-30", List.of(
                row("USD", 1, "40.10", "40.30"),
                row("XDR", 1, "", "50.0"),     // blank buy
                row("AZN", 1, "23.5", "   "),  // blank sell
                row("RUB", 1, null, "0.5"))));  // null buy

        FxLatestRates result = service.getTcmbLatestRates(null);

        assertThat(result.getRates()).extracting(FxRateItem::getSymbol).containsExactly("USD");
    }

    @Test
    @DisplayName("TCMB latest: unparseable numeric value is dropped (NumberFormatException -> null)")
    void getTcmbLatestRates_dropsUnparseableRow() {
        when(tcmbFxPort.fetchLatestRates()).thenReturn(new TcmbFxFeed("2026-05-30", List.of(
                row("USD", 1, "40.10", "40.30"),
                row("BAD", 1, "not-a-number", "1.0"))));

        FxLatestRates result = service.getTcmbLatestRates(null);

        assertThat(result.getRates()).extracting(FxRateItem::getSymbol).containsExactly("USD");
    }

    @Test
    @DisplayName("TCMB latest: banknote with garbage value parses to null, core rate kept")
    void getTcmbLatestRates_garbageBanknoteParsesToNull() {
        when(tcmbFxPort.fetchLatestRates()).thenReturn(new TcmbFxFeed("2026-05-30", List.of(
                rowFull("USD", 1, "40.10", "40.30", "abc", "40.40"))));

        FxRateItem usd = bySymbol(service.getTcmbLatestRates(null), "USD");

        assertThat(usd.getBuy()).isEqualByComparingTo("40.10");
        assertThat(usd.getEffectiveBuy()).isNull();
        assertThat(usd.getEffectiveSell()).isEqualByComparingTo("40.40");
    }

    @Test
    @DisplayName("TCMB latest: empty currency list yields empty rates, metadata still set")
    void getTcmbLatestRates_emptyCurrencyList() {
        when(tcmbFxPort.fetchLatestRates()).thenReturn(new TcmbFxFeed("2026-05-30", List.of()));

        FxLatestRates result = service.getTcmbLatestRates("USD");

        assertThat(result.getRates()).isEmpty();
        assertThat(result.getBase()).isEqualTo("TRY");
        assertThat(result.getAsOf()).isEqualTo("2026-05-30");
    }

    // ── getOpenFxLatest ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Open FX: maps rates (buy==sell, unit 1), uses provider baseCode")
    void getOpenFxLatest_mapsRatesAndBase() {
        Map<String, Double> rates = new LinkedHashMap<>();
        rates.put("TRY", 40.5);
        rates.put("EUR", 0.92);
        when(openFxPort.fetchLatestRates("USD"))
                .thenReturn(new OpenFxFeed("USD", "2026-05-30T00:00:00Z", rates));

        FxLatestRates result = service.getOpenFxLatest(null, null);

        assertThat(result.getProvider()).isEqualTo("openfx");
        assertThat(result.getSourceType()).isEqualTo("global");
        assertThat(result.getBase()).isEqualTo("USD");
        assertThat(result.getAsOf()).isEqualTo("2026-05-30T00:00:00Z");
        assertThat(result.getRates()).hasSize(2);

        FxRateItem tryItem = bySymbol(result, "TRY");
        assertThat(tryItem.getBuy()).isEqualByComparingTo("40.5");
        assertThat(tryItem.getSell()).isEqualByComparingTo("40.5");
        assertThat(tryItem.getUnit()).isEqualTo(1);
    }

    @Test
    @DisplayName("Open FX: blank base falls back to USD when querying the port")
    void getOpenFxLatest_blankBaseDefaultsToUsd() {
        when(openFxPort.fetchLatestRates("USD"))
                .thenReturn(new OpenFxFeed("USD", "t", Map.of("EUR", 0.92)));

        service.getOpenFxLatest("   ", null);

        verify(openFxPort).fetchLatestRates("USD");
    }

    @Test
    @DisplayName("Open FX: base is trimmed + uppercased before the call")
    void getOpenFxLatest_baseTrimmedUppercased() {
        when(openFxPort.fetchLatestRates("EUR"))
                .thenReturn(new OpenFxFeed("EUR", "t", Map.of("USD", 1.08)));

        FxLatestRates result = service.getOpenFxLatest(" eur ", null);

        verify(openFxPort).fetchLatestRates("EUR");
        assertThat(result.getBase()).isEqualTo("EUR");
    }

    @Test
    @DisplayName("Open FX: null baseCode in feed falls back to the effective base")
    void getOpenFxLatest_nullBaseCodeFallsBackToEffective() {
        when(openFxPort.fetchLatestRates("USD"))
                .thenReturn(new OpenFxFeed(null, "t", Map.of("EUR", 0.92)));

        FxLatestRates result = service.getOpenFxLatest("usd", null);

        assertThat(result.getBase()).isEqualTo("USD");
    }

    @Test
    @DisplayName("Open FX: symbols filter applied case-insensitively")
    void getOpenFxLatest_filtersBySymbols() {
        Map<String, Double> rates = new LinkedHashMap<>();
        rates.put("TRY", 40.5);
        rates.put("EUR", 0.92);
        rates.put("GBP", 0.79);
        when(openFxPort.fetchLatestRates("USD")).thenReturn(new OpenFxFeed("USD", "t", rates));

        FxLatestRates result = service.getOpenFxLatest("USD", " try , gbp ");

        assertThat(result.getRates()).extracting(FxRateItem::getSymbol)
                .containsExactlyInAnyOrder("TRY", "GBP");
    }

    @Test
    @DisplayName("Open FX: null rate value entries are filtered out")
    void getOpenFxLatest_skipsNullRateValues() {
        Map<String, Double> rates = new LinkedHashMap<>();
        rates.put("TRY", 40.5);
        rates.put("EUR", null);
        when(openFxPort.fetchLatestRates("USD")).thenReturn(new OpenFxFeed("USD", "t", rates));

        FxLatestRates result = service.getOpenFxLatest("USD", null);

        assertThat(result.getRates()).extracting(FxRateItem::getSymbol).containsExactly("TRY");
    }

    @Test
    @DisplayName("Open FX: null rates map throws IllegalStateException")
    void getOpenFxLatest_nullRatesThrows() {
        when(openFxPort.fetchLatestRates("USD")).thenReturn(new OpenFxFeed("USD", "t", null));

        assertThatThrownBy(() -> service.getOpenFxLatest("USD", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no conversion rates");
    }

    @Test
    @DisplayName("Open FX: empty rates map throws IllegalStateException")
    void getOpenFxLatest_emptyRatesThrows() {
        when(openFxPort.fetchLatestRates("USD")).thenReturn(new OpenFxFeed("USD", "t", Map.of()));

        assertThatThrownBy(() -> service.getOpenFxLatest("USD", null))
                .isInstanceOf(IllegalStateException.class);
    }

    // ── getFxHistory ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("FX history: symbol uppercased, quote TRY, range echoed, points passed through")
    void getFxHistory_mapsResponse() {
        List<FxHistoryPoint> points = List.of(
                new FxHistoryPoint("2026-05-29", new BigDecimal("40.10")),
                new FxHistoryPoint("2026-05-30", new BigDecimal("40.20")));
        when(tcmbFxHistoryPort.fetchHistory(eq("USD"), any(), any())).thenReturn(points);

        FxHistory result = service.getFxHistory("usd", "1M");

        assertThat(result.getSymbol()).isEqualTo("USD");
        assertThat(result.getQuoteCurrency()).isEqualTo("TRY");
        assertThat(result.getRange()).isEqualTo("1M");
        assertThat(result.getPoints()).hasSize(2);
    }

    @Test
    @DisplayName("FX history: '1W' resolves to today minus one week as the 'from' bound")
    void getFxHistory_1W_fromDate() {
        when(tcmbFxHistoryPort.fetchHistory(eq("EUR"), any(), any())).thenReturn(List.of());

        service.getFxHistory("EUR", "1W");

        LocalDate today = LocalDate.now(ISTANBUL);
        ArgumentCaptor<LocalDate> from = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> to = ArgumentCaptor.forClass(LocalDate.class);
        verify(tcmbFxHistoryPort).fetchHistory(eq("EUR"), from.capture(), to.capture());
        assertThat(from.getValue()).isEqualTo(today.minusWeeks(1));
        assertThat(to.getValue()).isEqualTo(today);
    }

    @Test
    @DisplayName("FX history: 'ALL' resolves to today minus five years")
    void getFxHistory_ALL_fromDate() {
        when(tcmbFxHistoryPort.fetchHistory(eq("USD"), any(), any())).thenReturn(List.of());

        service.getFxHistory("USD", "ALL");

        ArgumentCaptor<LocalDate> from = ArgumentCaptor.forClass(LocalDate.class);
        verify(tcmbFxHistoryPort).fetchHistory(eq("USD"), from.capture(), any());
        assertThat(from.getValue()).isEqualTo(LocalDate.now(ISTANBUL).minusYears(5));
    }

    @Test
    @DisplayName("FX history: null/unknown range falls back to one month")
    void getFxHistory_defaultRange_oneMonth() {
        when(tcmbFxHistoryPort.fetchHistory(eq("USD"), any(), any())).thenReturn(List.of());

        service.getFxHistory("USD", null);
        service.getFxHistory("USD", "BOGUS");

        LocalDate expected = LocalDate.now(ISTANBUL).minusMonths(1);
        ArgumentCaptor<LocalDate> from = ArgumentCaptor.forClass(LocalDate.class);
        verify(tcmbFxHistoryPort, org.mockito.Mockito.times(2))
                .fetchHistory(eq("USD"), from.capture(), any());
        assertThat(from.getAllValues()).containsExactly(expected, expected);
    }

    @Test
    @DisplayName("FX history: each named range maps to the expected lookback")
    void getFxHistory_rangeMappings() {
        when(tcmbFxHistoryPort.fetchHistory(eq("USD"), any(), any())).thenReturn(List.of());
        LocalDate today = LocalDate.now(ISTANBUL);

        service.getFxHistory("USD", "3M");
        service.getFxHistory("USD", "6M");
        service.getFxHistory("USD", "1Y");

        ArgumentCaptor<LocalDate> from = ArgumentCaptor.forClass(LocalDate.class);
        verify(tcmbFxHistoryPort, org.mockito.Mockito.times(3))
                .fetchHistory(eq("USD"), from.capture(), any());
        assertThat(from.getAllValues()).containsExactly(
                today.minusMonths(3), today.minusMonths(6), today.minusYears(1));
    }

    @Test
    @DisplayName("FX history: '5Y'/'10Y' resolve to today minus five/ten years")
    void getFxHistory_5Y_10Y_fromDates() {
        when(tcmbFxHistoryPort.fetchHistory(eq("USD"), any(), any())).thenReturn(List.of());
        LocalDate today = LocalDate.now(ISTANBUL);

        service.getFxHistory("USD", "5Y");
        service.getFxHistory("USD", "10Y");

        ArgumentCaptor<LocalDate> from = ArgumentCaptor.forClass(LocalDate.class);
        verify(tcmbFxHistoryPort, org.mockito.Mockito.times(2))
                .fetchHistory(eq("USD"), from.capture(), any());
        assertThat(from.getAllValues()).containsExactly(
                today.minusYears(5), today.minusYears(10));
    }
}
