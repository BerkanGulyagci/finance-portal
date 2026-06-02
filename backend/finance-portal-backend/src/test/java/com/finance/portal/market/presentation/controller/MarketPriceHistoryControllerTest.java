package com.finance.portal.market.presentation.controller;

import com.finance.portal.common.domain.AssetType;
import com.finance.portal.common.presentation.dto.ApiResponse;
import com.finance.portal.market.application.economy.InflationDeflatorService;
import com.finance.portal.market.application.economy.model.EconomySeriesPoint;
import com.finance.portal.market.application.economy.port.EconomyDataPort;
import com.finance.portal.market.application.indicator.BistIndexService;
import com.finance.portal.market.application.stock.StockChartResponse;
import com.finance.portal.market.application.stock.StockQueryService;
import com.finance.portal.market.application.stock.model.YahooChartSnapshot;
import com.finance.portal.market.application.stock.model.YahooQuoteSeries;
import com.finance.portal.market.presentation.controller.MarketPriceHistoryController.PriceHistoryResponse;
import com.finance.portal.portfolio.application.port.PortfolioHistoricalPricePort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MarketPriceHistoryController} birim testleri — 5 bağımlılık mock'lanır, Spring context yok.
 * Aralık ayrıştırma, normal enstrüman serisi ve INDICATOR benchmark dalları (TÜFE/ABD/Mevduat/BIST endeks)
 * kapsanır.
 */
@ExtendWith(MockitoExtension.class)
class MarketPriceHistoryControllerTest {

    @Mock private PortfolioHistoricalPricePort pricePort;
    @Mock private InflationDeflatorService deflator;
    @Mock private EconomyDataPort economyDataPort;
    @Mock private BistIndexService bistIndexService;
    @Mock private StockQueryService stockQueryService;

    @InjectMocks private MarketPriceHistoryController controller;

    private static long epochUtc(LocalDate d) {
        return d.atStartOfDay(ZoneOffset.UTC).toEpochSecond();
    }

    @SuppressWarnings("unchecked")
    private static PriceHistoryResponse body(Object responseEntity) {
        var re = (org.springframework.http.ResponseEntity<ApiResponse<PriceHistoryResponse>>) responseEntity;
        return re.getBody().getData();
    }

    // ── Normal enstrüman serisi ─────────────────────────────────────────────

    @Test
    @DisplayName("Normal enstrüman: seri map'lenir, null kapanışlar atlanır")
    void getPriceHistory_mapsSeriesAndSkipsNulls() {
        LocalDate today = LocalDate.now();
        NavigableMap<LocalDate, BigDecimal> series = new TreeMap<>();
        series.put(today.minusDays(3), new BigDecimal("100.5"));
        series.put(today.minusDays(2), null);                 // atlanmalı
        series.put(today.minusDays(1), new BigDecimal("101.0"));
        when(pricePort.fetchDailyClosePrices(eq(AssetType.STOCK), eq("THYAO"), any(), any()))
                .thenReturn(Optional.of(series));

        PriceHistoryResponse r = body(controller.getPriceHistory("STOCK", "THYAO", "1Y"));

        assertThat(r.timestamps()).hasSize(2);
        assertThat(r.closePrices()).containsExactly(new BigDecimal("100.5"), new BigDecimal("101.0"));
    }

    @Test
    @DisplayName("Boş seri (Optional.empty) → boş listeler döner")
    void getPriceHistory_emptySeries() {
        when(pricePort.fetchDailyClosePrices(any(), any(), any(), any())).thenReturn(Optional.empty());
        PriceHistoryResponse r = body(controller.getPriceHistory("CRYPTO", "BTC", "6M"));
        assertThat(r.timestamps()).isEmpty();
        assertThat(r.closePrices()).isEmpty();
    }

    @Test
    @DisplayName("Geçersiz assetType → exception yutulur, boş seri")
    void getPriceHistory_invalidAssetType_returnsEmpty() {
        PriceHistoryResponse r = body(controller.getPriceHistory("NOT_A_TYPE", "X", "1Y"));
        assertThat(r.timestamps()).isEmpty();
        assertThat(r.closePrices()).isEmpty();
    }

    @Test
    @DisplayName("Aralık ayrıştırma: her etiket doğru 'from' tarihine çevrilir (tüm switch dalları)")
    void getPriceHistory_rangeParsing_allBranches() {
        when(pricePort.fetchDailyClosePrices(any(), any(), any(), any())).thenReturn(Optional.empty());
        LocalDate today = LocalDate.now();

        record Case(String range, LocalDate expectedFrom) {}
        List<Case> cases = List.of(
                new Case("1D", today.minusDays(7)),
                new Case("5D", today.minusDays(7)),
                new Case("1W", today.minusDays(7)),
                new Case("1M", today.minusMonths(1)),
                new Case("3M", today.minusMonths(3)),
                new Case("6M", today.minusMonths(6)),
                new Case("YTD", LocalDate.of(today.getYear(), 1, 1)),
                new Case("1Y", today.minusYears(1)),
                new Case("5Y", today.minusYears(5)),
                new Case("ALL", today.minusYears(10)),
                new Case("MAX", today.minusYears(10)),
                new Case("weird", today.minusYears(1)));
        for (Case c : cases) {
            controller.getPriceHistory("STOCK", "AAA", c.range());
        }
        controller.getPriceHistory("STOCK", "AAA", null); // null → default 1Y

        ArgumentCaptor<LocalDate> fromCap = ArgumentCaptor.forClass(LocalDate.class);
        verify(pricePort, times(cases.size() + 1))
                .fetchDailyClosePrices(any(), any(), fromCap.capture(), any());
        List<LocalDate> froms = fromCap.getAllValues();
        for (int i = 0; i < cases.size(); i++) {
            assertThat(froms.get(i)).as(cases.get(i).range()).isEqualTo(cases.get(i).expectedFrom());
        }
        assertThat(froms.get(cases.size())).isEqualTo(today.minusYears(1)); // null → 1Y
    }

    // ── INDICATOR: TÜFE ─────────────────────────────────────────────────────

    @Test
    @DisplayName("INDICATOR TUFE: pozitif & aralıktaki noktalar yayınlanır")
    void indicator_tufe_filtersByDateAndSign() {
        LocalDate today = LocalDate.now();
        List<EconomySeriesPoint> tufe = Arrays.asList(
                new EconomySeriesPoint("2020-1", new BigDecimal("500"), epochUtc(today.minusYears(5))), // aralık dışı
                new EconomySeriesPoint("m1", new BigDecimal("1200"), epochUtc(today.minusMonths(6))),
                new EconomySeriesPoint("m2", BigDecimal.ZERO, epochUtc(today.minusMonths(3))),          // sign<=0 atlanır
                new EconomySeriesPoint("m3", new BigDecimal("1300"), epochUtc(today.minusMonths(1))));
        when(deflator.tufeSeries()).thenReturn(tufe);

        PriceHistoryResponse r = body(controller.getPriceHistory("INDICATOR", "TUFE", "1Y"));

        assertThat(r.closePrices()).containsExactly(new BigDecimal("1200"), new BigDecimal("1300"));
    }

    // ── INDICATOR: ABD CPI × USD/TRY ────────────────────────────────────────

    @Test
    @DisplayName("INDICATOR USCPI_TRY: CPI × USD/TRY aylık seri")
    void indicator_usCpiTry_emitsMonthly() {
        LocalDate today = LocalDate.now();
        when(deflator.usCpiSeries()).thenReturn(List.of(
                new EconomySeriesPoint("u1", new BigDecimal("300"), epochUtc(today.minusMonths(2)))));
        NavigableMap<LocalDate, BigDecimal> usd = new TreeMap<>();
        usd.put(today.minusYears(1), new BigDecimal("30"));
        when(pricePort.fetchDailyClosePrices(eq(AssetType.FX), eq("USD"), any(), any()))
                .thenReturn(Optional.of(usd));
        when(deflator.indexValueAt(any(), any())).thenReturn(Optional.of(new BigDecimal("300")));

        PriceHistoryResponse r = body(controller.getPriceHistory("INDICATOR", "USCPI_TRY", "3M"));

        assertThat(r.closePrices()).isNotEmpty();
        assertThat(r.closePrices().get(0)).isEqualByComparingTo("9000"); // 300 × 30
    }

    @Test
    @DisplayName("INDICATOR USCPI_TRY: USD serisi yoksa boş")
    void indicator_usCpiTry_noFx_empty() {
        when(deflator.usCpiSeries()).thenReturn(List.of(
                new EconomySeriesPoint("u1", new BigDecimal("300"), epochUtc(LocalDate.now()))));
        when(pricePort.fetchDailyClosePrices(eq(AssetType.FX), eq("USD"), any(), any()))
                .thenReturn(Optional.empty());

        PriceHistoryResponse r = body(controller.getPriceHistory("INDICATOR", "USCPI_TRY", "1Y"));
        assertThat(r.closePrices()).isEmpty();
    }

    // ── INDICATOR: Mevduat faizi bileşik endeks ─────────────────────────────

    @Test
    @DisplayName("INDICATOR DEPOSIT: faiz serisinden 100'den başlayan bileşik endeks")
    void indicator_deposit_compoundIndex() {
        LocalDate today = LocalDate.now();
        List<EconomySeriesPoint> rates = List.of(
                new EconomySeriesPoint("r0", new BigDecimal("50"), epochUtc(today.minusMonths(6))),
                new EconomySeriesPoint("r1", new BigDecimal("48"), epochUtc(today.minusMonths(3))));
        when(economyDataPort.fetchSeries(anyString(), any(), any())).thenReturn(rates);

        PriceHistoryResponse r = body(controller.getPriceHistory("INDICATOR", "DEPOSIT", "6M"));

        assertThat(r.closePrices()).isNotEmpty();
        assertThat(r.closePrices().get(0)).isEqualByComparingTo("100"); // başlangıç = 100
        // bileşik büyüme → son nokta ilk noktadan büyük
        assertThat(r.closePrices().get(r.closePrices().size() - 1).doubleValue()).isGreaterThan(100.0);
    }

    @Test
    @DisplayName("INDICATOR DEPOSIT: faiz serisi boşsa boş")
    void indicator_deposit_emptyRates() {
        when(economyDataPort.fetchSeries(anyString(), any(), any())).thenReturn(List.of());
        PriceHistoryResponse r = body(controller.getPriceHistory("INDICATOR", "DEPOSIT", "1Y"));
        assertThat(r.closePrices()).isEmpty();
    }

    // ── INDICATOR: BIST endeksleri ──────────────────────────────────────────

    @Test
    @DisplayName("INDICATOR XU100: katalog endeksi hisse-grafik yolundan yayınlanır")
    void indicator_bistIndex_viaStockChart() {
        LocalDate today = LocalDate.now();
        StockChartResponse chart = new StockChartResponse();
        chart.setTimestamps(List.of(epochUtc(today.minusMonths(2)), epochUtc(today.minusMonths(1))));
        chart.setClosePrices(List.of(new BigDecimal("9500"), new BigDecimal("9800")));
        when(stockQueryService.getStockChartWithParams(eq("XU100.IS"), anyString(), eq("1d")))
                .thenReturn(chart);

        PriceHistoryResponse r = body(controller.getPriceHistory("INDICATOR", "XU100", "1Y"));

        assertThat(r.closePrices()).containsExactly(new BigDecimal("9500"), new BigDecimal("9800"));
    }

    @Test
    @DisplayName("INDICATOR: katalogda olmayan ama servis destekleyen sembol → emitBistIndex")
    void indicator_bistIndex_viaService() {
        LocalDate today = LocalDate.now();
        YahooQuoteSeries quote = new YahooQuoteSeries();
        quote.setClose(List.of(new BigDecimal("123.4"), new BigDecimal("125.6")));
        YahooChartSnapshot snap = new YahooChartSnapshot();
        snap.setTimestamps(List.of(epochUtc(today.minusMonths(2)), epochUtc(today.minusMonths(1))));
        snap.setQuote(quote);
        when(bistIndexService.supports("FOOBAR")).thenReturn(true);
        when(bistIndexService.fetchChart(eq("FOOBAR"), any(), any())).thenReturn(Optional.of(snap));

        PriceHistoryResponse r = body(controller.getPriceHistory("INDICATOR", "FOOBAR", "1Y"));

        assertThat(r.closePrices()).containsExactly(new BigDecimal("123.4"), new BigDecimal("125.6"));
    }

    @Test
    @DisplayName("INDICATOR: tanınmayan sembol → boş seri")
    void indicator_unknownSymbol_empty() {
        when(bistIndexService.supports("NOPE")).thenReturn(false);
        PriceHistoryResponse r = body(controller.getPriceHistory("INDICATOR", "NOPE", "1Y"));
        assertThat(r.timestamps()).isEmpty();
    }
}
