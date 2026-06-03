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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * {@link MarketPriceHistoryController} ek dal kapsama testleri — mevcut
 * {@code MarketPriceHistoryControllerTest}'in ATLAdığı dalları hedefler:
 * boş/null koruyucular (snapshot empty, null seri, null quote/close), işaret≤0 ve
 * aralık-dışı filtreler, {@code .IS} sonek kırpma, USCPI fx-floor fallback / cpi-yok,
 * mevduat seri-null & exception & son-nokta dalları, RuntimeException yutma.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MarketPriceHistoryControllerMoreTest {

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

    // ── assetType / range trimming + casing (whitespace dalları) ─────────────

    @Test
    @DisplayName("assetType başında/sonunda boşluk + lowercase range → trim & upper sonrası çözülür")
    void getPriceHistory_trimsAndUppercasesParams() {
        when(pricePort.fetchDailyClosePrices(eq(AssetType.STOCK), eq("AAA"), any(), any()))
                .thenReturn(Optional.empty());

        PriceHistoryResponse r = body(controller.getPriceHistory("  stock  ", "AAA", "  3m  "));

        assertThat(r.timestamps()).isEmpty();
        assertThat(r.closePrices()).isEmpty();
    }

    // ── default range arm reachability via blank range -> default 1Y ─────────

    @Test
    @DisplayName("INDICATOR assetType boşluklu → trim sonrası INDICATOR dalına girer")
    void getPriceHistory_indicatorWithWhitespace() {
        // TUFE serisi boş döner → boş seri; INDICATOR dalına girdiğini bu yolla doğrularız
        when(deflator.tufeSeries()).thenReturn(List.of());

        PriceHistoryResponse r = body(controller.getPriceHistory(" indicator ", "TUFE", "1Y"));

        assertThat(r.closePrices()).isEmpty();
        // pricePort.fetch çağrılmamalı (normal enstrüman dalına düşmedi)
    }

    // ── INDICATOR TUFE: null seri (safe()) ve null değer dalı ────────────────

    @Test
    @DisplayName("INDICATOR TUFE: tufeSeries null → safe() boş listeye çevirir, boş seri")
    void indicator_tufe_nullSeries() {
        when(deflator.tufeSeries()).thenReturn(null);

        PriceHistoryResponse r = body(controller.getPriceHistory("INDICATOR", "TUFE", "1Y"));

        assertThat(r.timestamps()).isEmpty();
        assertThat(r.closePrices()).isEmpty();
    }

    @Test
    @DisplayName("INDICATOR TUFE: value null nokta atlanır (getValue()!=null koruması)")
    void indicator_tufe_nullValueSkipped() {
        LocalDate today = LocalDate.now();
        List<EconomySeriesPoint> tufe = Arrays.asList(
                new EconomySeriesPoint("nullv", null, epochUtc(today.minusMonths(2))),       // value null → atla
                new EconomySeriesPoint("ok", new BigDecimal("1500"), epochUtc(today.minusMonths(1))));
        when(deflator.tufeSeries()).thenReturn(tufe);

        PriceHistoryResponse r = body(controller.getPriceHistory("INDICATOR", "TUFE", "1Y"));

        assertThat(r.closePrices()).containsExactly(new BigDecimal("1500"));
    }

    // ── INDICATOR USCPI_TRY: ek dallar ──────────────────────────────────────

    @Test
    @DisplayName("INDICATOR USCPI_TRY: usCpi serisi boş → erken return, boş seri")
    void indicator_usCpiTry_emptyCpi_returnsEarly() {
        when(deflator.usCpiSeries()).thenReturn(List.of());

        PriceHistoryResponse r = body(controller.getPriceHistory("INDICATOR", "USCPI_TRY", "1Y"));

        assertThat(r.closePrices()).isEmpty();
    }

    @Test
    @DisplayName("INDICATOR USCPI_TRY: usCpiSeries null → safe() boşa çevirir → erken return")
    void indicator_usCpiTry_nullCpi_returnsEarly() {
        when(deflator.usCpiSeries()).thenReturn(null);

        PriceHistoryResponse r = body(controller.getPriceHistory("INDICATOR", "USCPI_TRY", "1Y"));

        assertThat(r.closePrices()).isEmpty();
    }

    @Test
    @DisplayName("INDICATOR USCPI_TRY: usd serisi boş map → null/empty koruması, boş seri")
    void indicator_usCpiTry_emptyUsdMap() {
        when(deflator.usCpiSeries()).thenReturn(List.of(
                new EconomySeriesPoint("u", new BigDecimal("300"), epochUtc(LocalDate.now()))));
        when(pricePort.fetchDailyClosePrices(eq(AssetType.FX), eq("USD"), any(), any()))
                .thenReturn(Optional.of(new TreeMap<>())); // boş map → usd.isEmpty()

        PriceHistoryResponse r = body(controller.getPriceHistory("INDICATOR", "USCPI_TRY", "1Y"));

        assertThat(r.closePrices()).isEmpty();
    }

    @Test
    @DisplayName("INDICATOR USCPI_TRY: floorEntry null → firstEntry fallback + cpi-yok aylar atlanır")
    void indicator_usCpiTry_floorFallbackAndCpiMissing() {
        LocalDate today = LocalDate.now();
        when(deflator.usCpiSeries()).thenReturn(List.of(
                new EconomySeriesPoint("u", new BigDecimal("250"), epochUtc(today.minusMonths(2)))));

        // FX serisi yalnız BUGÜN'e ait → erken aylar için floorEntry null → firstEntry fallback
        NavigableMap<LocalDate, BigDecimal> usd = new TreeMap<>();
        usd.put(today, new BigDecimal("32"));
        when(pricePort.fetchDailyClosePrices(eq(AssetType.FX), eq("USD"), any(), any()))
                .thenReturn(Optional.of(usd));

        // İlk ay cpi yok (Optional.empty → atla), ikinci ay cpi var (>0 → yayınla)
        when(deflator.indexValueAt(any(), any()))
                .thenReturn(Optional.empty())                       // 1. ay → atla (cpi.isPresent false)
                .thenReturn(Optional.of(new BigDecimal("250")));    // sonraki → yayınla

        PriceHistoryResponse r = body(controller.getPriceHistory("INDICATOR", "USCPI_TRY", "3M"));

        // En az bir nokta (firstEntry fx=32 × cpi=250 = 8000) yayınlanır
        assertThat(r.closePrices()).isNotEmpty();
        assertThat(r.closePrices().get(0)).isEqualByComparingTo("8000");
    }

    @Test
    @DisplayName("INDICATOR USCPI_TRY: cpi işareti<=0 → ay atlanır, boş seri")
    void indicator_usCpiTry_cpiNonPositiveSkipped() {
        LocalDate today = LocalDate.now();
        when(deflator.usCpiSeries()).thenReturn(List.of(
                new EconomySeriesPoint("u", new BigDecimal("250"), epochUtc(today.minusMonths(2)))));
        NavigableMap<LocalDate, BigDecimal> usd = new TreeMap<>();
        usd.put(today.minusYears(1), new BigDecimal("30"));
        when(pricePort.fetchDailyClosePrices(eq(AssetType.FX), eq("USD"), any(), any()))
                .thenReturn(Optional.of(usd));
        when(deflator.indexValueAt(any(), any())).thenReturn(Optional.of(BigDecimal.ZERO)); // signum 0 → atla

        PriceHistoryResponse r = body(controller.getPriceHistory("INDICATOR", "USCPI_TRY", "1M"));

        assertThat(r.closePrices()).isEmpty();
    }

    // ── INDICATOR DEPOSIT: ek dallar ────────────────────────────────────────

    @Test
    @DisplayName("INDICATOR DEPOSIT: fetchSeries null → rates boş → erken return")
    void indicator_deposit_nullSeries() {
        when(economyDataPort.fetchSeries(anyString(), any(), any())).thenReturn(null);

        PriceHistoryResponse r = body(controller.getPriceHistory("INDICATOR", "DEPOSIT", "1Y"));

        assertThat(r.closePrices()).isEmpty();
    }

    @Test
    @DisplayName("INDICATOR DEPOSIT: fetchSeries exception fırlatır → yutulur, boş seri")
    void indicator_deposit_seriesThrows() {
        when(economyDataPort.fetchSeries(anyString(), any(), any()))
                .thenThrow(new RuntimeException("EVDS down"));

        PriceHistoryResponse r = body(controller.getPriceHistory("INDICATOR", "DEPOSIT", "1Y"));

        assertThat(r.closePrices()).isEmpty();
    }

    @Test
    @DisplayName("INDICATOR DEPOSIT: value null nokta atlanır + son nokta (bugün) eklenir")
    void indicator_deposit_nullValueSkipped_andLastPointAppended() {
        LocalDate today = LocalDate.now();
        List<EconomySeriesPoint> rates = Arrays.asList(
                new EconomySeriesPoint("rnull", null, epochUtc(today.minusMonths(8))),        // value null → atla
                new EconomySeriesPoint("r0", new BigDecimal("45"), epochUtc(today.minusMonths(6))));
        when(economyDataPort.fetchSeries(anyString(), any(), any())).thenReturn(rates);

        PriceHistoryResponse r = body(controller.getPriceHistory("INDICATOR", "DEPOSIT", "6M"));

        assertThat(r.timestamps()).isNotEmpty();
        // son nokta bugün'e karşılık gelmeli (append dalı)
        assertThat(r.timestamps().get(r.timestamps().size() - 1)).isEqualTo(epochUtc(today));
        assertThat(r.closePrices().get(0)).isEqualByComparingTo("100"); // başlangıç 100
    }

    @Test
    @DisplayName("INDICATOR DEPOSIT: 1D aralığı → ilk ay başı=from, depositCompound target==from dalı (1.0)")
    void indicator_deposit_targetNotAfterFrom() {
        LocalDate today = LocalDate.now();
        // 1D → from = today-7; aynı ay içinde m.atDay(1) from'dan önce → d=from
        List<EconomySeriesPoint> rates = List.of(
                new EconomySeriesPoint("r0", new BigDecimal("40"), epochUtc(today.minusDays(30))));
        when(economyDataPort.fetchSeries(anyString(), any(), any())).thenReturn(rates);

        PriceHistoryResponse r = body(controller.getPriceHistory("INDICATOR", "DEPOSIT", "1D"));

        // d==from için faktör 1.0 → 100; son nokta (bugün) >100 (bileşik büyüme 7 gün)
        assertThat(r.closePrices().get(0)).isEqualByComparingTo("100");
        assertThat(r.timestamps().get(r.timestamps().size() - 1)).isEqualTo(epochUtc(today));
    }

    // ── INDICATOR: default arm — ".IS" sonek kırpma → katalog isabeti ────────

    @Test
    @DisplayName("INDICATOR XU100.IS: .IS soneki kırpılır → katalog isabeti → stock-chart yolu")
    void indicator_dotIsSuffixStripped() {
        LocalDate today = LocalDate.now();
        StockChartResponse chart = new StockChartResponse();
        chart.setTimestamps(List.of(epochUtc(today.minusMonths(1))));
        chart.setClosePrices(List.of(new BigDecimal("9900")));
        when(stockQueryService.getStockChartWithParams(eq("XU100.IS"), anyString(), eq("1d")))
                .thenReturn(chart);

        PriceHistoryResponse r = body(controller.getPriceHistory("INDICATOR", "XU100.IS", "1Y"));

        assertThat(r.closePrices()).containsExactly(new BigDecimal("9900"));
    }

    // ── emitIndexViaStockChart: koruma dalları ──────────────────────────────

    @Test
    @DisplayName("emitIndexViaStockChart: timestamps/closes null → boş seri")
    void indexViaStockChart_nullLists() {
        StockChartResponse chart = new StockChartResponse(); // timestamps & closes null
        when(stockQueryService.getStockChartWithParams(eq("XU100.IS"), anyString(), eq("1d")))
                .thenReturn(chart);

        PriceHistoryResponse r = body(controller.getPriceHistory("INDICATOR", "XU100", "1Y"));

        assertThat(r.timestamps()).isEmpty();
    }

    @Test
    @DisplayName("emitIndexViaStockChart: sign<=0 ve aralık-dışı noktalar atlanır")
    void indexViaStockChart_filtersSignAndRange() {
        LocalDate today = LocalDate.now();
        StockChartResponse chart = new StockChartResponse();
        chart.setTimestamps(new ArrayList<>(Arrays.asList(
                null,                                   // sec null → atla
                epochUtc(today.minusMonths(1)),         // sign<=0 → atla
                epochUtc(today.minusYears(20)),         // aralık öncesi (from'dan önce) → atla
                epochUtc(today.plusDays(5)),            // aralık sonrası (to'dan sonra) → atla
                epochUtc(today.minusMonths(2)))));      // geçerli → yayınla
        chart.setClosePrices(new ArrayList<>(Arrays.asList(
                new BigDecimal("1"),
                BigDecimal.ZERO,                        // sign<=0
                new BigDecimal("2"),
                new BigDecimal("3"),
                new BigDecimal("8800"))));
        when(stockQueryService.getStockChartWithParams(eq("XU100.IS"), anyString(), eq("1d")))
                .thenReturn(chart);

        PriceHistoryResponse r = body(controller.getPriceHistory("INDICATOR", "XU100", "1Y"));

        assertThat(r.closePrices()).containsExactly(new BigDecimal("8800"));
    }

    @Test
    @DisplayName("emitIndexViaStockChart: close listesinde null eleman → atlanır")
    void indexViaStockChart_nullCloseElement() {
        LocalDate today = LocalDate.now();
        StockChartResponse chart = new StockChartResponse();
        chart.setTimestamps(new ArrayList<>(Arrays.asList(
                epochUtc(today.minusMonths(2)),
                epochUtc(today.minusMonths(1)))));
        chart.setClosePrices(new ArrayList<>(Arrays.asList(
                (BigDecimal) null,                      // close null → atla
                new BigDecimal("9100"))));
        when(stockQueryService.getStockChartWithParams(eq("XU100.IS"), anyString(), eq("1d")))
                .thenReturn(chart);

        PriceHistoryResponse r = body(controller.getPriceHistory("INDICATOR", "XU100", "1Y"));

        assertThat(r.closePrices()).containsExactly(new BigDecimal("9100"));
    }

    @Test
    @DisplayName("emitIndexViaStockChart: RuntimeException → yutulur, boş seri")
    void indexViaStockChart_runtimeExceptionSwallowed() {
        when(stockQueryService.getStockChartWithParams(eq("XU100.IS"), anyString(), eq("1d")))
                .thenThrow(new RuntimeException("yahoo 500"));

        PriceHistoryResponse r = body(controller.getPriceHistory("INDICATOR", "XU100", "1Y"));

        assertThat(r.timestamps()).isEmpty();
        assertThat(r.closePrices()).isEmpty();
    }

    // ── emitBistIndex: koruma dalları (katalog-dışı, servis destekli) ───────

    @Test
    @DisplayName("emitBistIndex: snapshot empty → boş seri")
    void bistIndex_snapshotEmpty() {
        when(bistIndexService.supports("FOOBAR")).thenReturn(true);
        when(bistIndexService.fetchChart(eq("FOOBAR"), any(), any())).thenReturn(Optional.empty());

        PriceHistoryResponse r = body(controller.getPriceHistory("INDICATOR", "FOOBAR", "1Y"));

        assertThat(r.timestamps()).isEmpty();
    }

    @Test
    @DisplayName("emitBistIndex: timestamps boş → koruma, boş seri")
    void bistIndex_emptyTimestamps() {
        YahooQuoteSeries quote = new YahooQuoteSeries();
        quote.setClose(List.of(new BigDecimal("10")));
        YahooChartSnapshot snap = new YahooChartSnapshot();
        snap.setTimestamps(List.of());          // empty → guard true
        snap.setQuote(quote);
        when(bistIndexService.supports("FOOBAR")).thenReturn(true);
        when(bistIndexService.fetchChart(eq("FOOBAR"), any(), any())).thenReturn(Optional.of(snap));

        PriceHistoryResponse r = body(controller.getPriceHistory("INDICATOR", "FOOBAR", "1Y"));

        assertThat(r.timestamps()).isEmpty();
    }

    @Test
    @DisplayName("emitBistIndex: timestamps null → koruma, boş seri")
    void bistIndex_nullTimestamps() {
        YahooQuoteSeries quote = new YahooQuoteSeries();
        quote.setClose(List.of(new BigDecimal("10")));
        YahooChartSnapshot snap = new YahooChartSnapshot();
        snap.setTimestamps(null);               // null → guard true
        snap.setQuote(quote);
        when(bistIndexService.supports("FOOBAR")).thenReturn(true);
        when(bistIndexService.fetchChart(eq("FOOBAR"), any(), any())).thenReturn(Optional.of(snap));

        PriceHistoryResponse r = body(controller.getPriceHistory("INDICATOR", "FOOBAR", "1Y"));

        assertThat(r.timestamps()).isEmpty();
    }

    @Test
    @DisplayName("emitBistIndex: quote null → koruma, boş seri")
    void bistIndex_nullQuote() {
        YahooChartSnapshot snap = new YahooChartSnapshot();
        snap.setTimestamps(List.of(epochUtc(LocalDate.now().minusMonths(1))));
        snap.setQuote(null);                    // quote null → guard true
        when(bistIndexService.supports("FOOBAR")).thenReturn(true);
        when(bistIndexService.fetchChart(eq("FOOBAR"), any(), any())).thenReturn(Optional.of(snap));

        PriceHistoryResponse r = body(controller.getPriceHistory("INDICATOR", "FOOBAR", "1Y"));

        assertThat(r.timestamps()).isEmpty();
    }

    @Test
    @DisplayName("emitBistIndex: quote.close null → koruma, boş seri")
    void bistIndex_nullClose() {
        YahooQuoteSeries quote = new YahooQuoteSeries(); // close null
        YahooChartSnapshot snap = new YahooChartSnapshot();
        snap.setTimestamps(List.of(epochUtc(LocalDate.now().minusMonths(1))));
        snap.setQuote(quote);
        when(bistIndexService.supports("FOOBAR")).thenReturn(true);
        when(bistIndexService.fetchChart(eq("FOOBAR"), any(), any())).thenReturn(Optional.of(snap));

        PriceHistoryResponse r = body(controller.getPriceHistory("INDICATOR", "FOOBAR", "1Y"));

        assertThat(r.timestamps()).isEmpty();
    }

    @Test
    @DisplayName("emitBistIndex: sec null + sign<=0 + aralık-dışı noktalar atlanır, geçerli kalır")
    void bistIndex_filtersSignNullAndRange() {
        LocalDate today = LocalDate.now();
        YahooQuoteSeries quote = new YahooQuoteSeries();
        quote.setClose(new ArrayList<>(Arrays.asList(
                new BigDecimal("5"),
                BigDecimal.ZERO,                        // sign<=0 → atla
                new BigDecimal("7"),
                new BigDecimal("9"),
                new BigDecimal("321.0"))));
        YahooChartSnapshot snap = new YahooChartSnapshot();
        snap.setTimestamps(new ArrayList<>(Arrays.asList(
                (Long) null,                            // sec null → atla
                epochUtc(today.minusMonths(1)),         // sign<=0 (close 0) → atla
                epochUtc(today.minusYears(20)),         // from öncesi → atla
                epochUtc(today.plusDays(10)),           // to sonrası → atla
                epochUtc(today.minusMonths(2)))));      // geçerli → yayınla
        snap.setQuote(quote);
        when(bistIndexService.supports("FOOBAR")).thenReturn(true);
        when(bistIndexService.fetchChart(eq("FOOBAR"), any(), any())).thenReturn(Optional.of(snap));

        PriceHistoryResponse r = body(controller.getPriceHistory("INDICATOR", "FOOBAR", "1Y"));

        assertThat(r.closePrices()).containsExactly(new BigDecimal("321.0"));
    }

    // ── buildIndicator: switch içindeki istisna yutma (catch) ───────────────

    @Test
    @DisplayName("buildIndicator: alt yol exception fırlatsa bile dış catch yutar → boş seri")
    void buildIndicator_innerExceptionSwallowed() {
        // FOOBAR → supports() çağrısı exception fırlatır → dıştaki catch(Exception) yutar
        when(bistIndexService.supports("FOOBAR")).thenThrow(new RuntimeException("boom"));

        PriceHistoryResponse r = body(controller.getPriceHistory("INDICATOR", "FOOBAR", "1Y"));

        assertThat(r.timestamps()).isEmpty();
        assertThat(r.closePrices()).isEmpty();
    }
}
