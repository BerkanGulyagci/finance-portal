package com.finance.portal.portfolio.infrastructure.market;

import com.finance.portal.common.domain.AssetType;
import com.finance.portal.market.application.bond.eurobond.model.EurobondChartPoint;
import com.finance.portal.market.application.bond.evds.model.EvdsSeriesPoint;
import com.finance.portal.market.application.bond.evds.port.EvdsBondPort;
import com.finance.portal.market.application.bond.eurobond.EurobondService;
import com.finance.portal.market.application.commodity.CommodityHistoryPointDto;
import com.finance.portal.market.application.commodity.CommodityHistoryResponse;
import com.finance.portal.market.application.commodity.YahooCommodityService;
import com.finance.portal.market.application.crypto.CryptoBinanceChartService;
import com.finance.portal.market.application.crypto.CryptoMarketService;
import com.finance.portal.market.application.crypto.CryptoYahooChartService;
import com.finance.portal.market.application.crypto.model.CryptoChartCandle;
import com.finance.portal.market.application.crypto.model.CryptoMarketItem;
import com.finance.portal.market.application.funds.model.FundPriceHistoryPoint;
import com.finance.portal.market.application.funds.service.RasyonetFundService;
import com.finance.portal.market.application.funds.service.TefasFundService;
import com.finance.portal.market.application.fx.model.FxHistoryPoint;
import com.finance.portal.market.application.fx.port.TcmbFxHistoryPort;
import com.finance.portal.market.application.gold.GoldHistoryPoint;
import com.finance.portal.market.application.gold.GoldHistoryResponse;
import com.finance.portal.market.application.gold.GoldMarketService;
import com.finance.portal.market.application.precious.PreciousMetalHistoryPoint;
import com.finance.portal.market.application.precious.PreciousMetalHistoryResponse;
import com.finance.portal.market.application.precious.PreciousMetalService;
import com.finance.portal.market.application.precious.model.PreciousMetalType;
import com.finance.portal.market.application.service.MarketFxService;
import com.finance.portal.market.application.silver.SilverHistoryPoint;
import com.finance.portal.market.application.silver.SilverHistoryResponse;
import com.finance.portal.market.application.silver.SilverMarketService;
import com.finance.portal.market.application.stock.StockChartResponse;
import com.finance.portal.market.application.stock.StockQueryService;
import com.finance.portal.market.application.viop.ViopChartPeriod;
import com.finance.portal.market.application.viop.ViopChartService;
import com.finance.portal.market.application.viop.model.ViopChartPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link PortfolioHistoricalPriceAdapter} birim testleri — tüm servisler mock'lanır, tür-bazlı
 * tarihsel TL serisi köprüsü doğrulanır (dispatch + STOCK/FUND/FX/FUTURE happy-path + hata/boş dalları
 * + statik {@code exclusionReason}/{@code isSilverCommoditySymbol}).
 */
@ExtendWith(MockitoExtension.class)
class PortfolioHistoricalPriceAdapterTest {

    @Mock private StockQueryService stockQueryService;
    @Mock private RasyonetFundService rasyonetFundService;
    @Mock private TefasFundService tefasFundService;
    @Mock private TcmbFxHistoryPort tcmbFxHistoryPort;
    @Mock private MarketFxService marketFxService;
    @Mock private EvdsBondPort evdsBondPort;
    @Mock private EurobondService eurobondService;
    @Mock private GoldMarketService goldMarketService;
    @Mock private SilverMarketService silverMarketService;
    @Mock private PreciousMetalService preciousMetalService;
    @Mock private YahooCommodityService yahooCommodityService;
    @Mock private CryptoMarketService cryptoMarketService;
    @Mock private CryptoYahooChartService cryptoYahooChartService;
    @Mock private CryptoBinanceChartService cryptoBinanceChartService;
    @Mock private ViopChartService viopChartService;

    @InjectMocks private PortfolioHistoricalPriceAdapter adapter;

    private final LocalDate to = LocalDate.now();
    private final LocalDate from = to.minusYears(1);

    private static long epochSec(LocalDate d) {
        return d.atStartOfDay(ZoneOffset.UTC).toEpochSecond();
    }

    // ── STOCK ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("STOCK: hisse grafik kapanışları TL serisine map'lenir")
    void stock_ok() {
        StockChartResponse chart = new StockChartResponse();
        chart.setTimestamps(List.of(epochSec(to.minusDays(3)), epochSec(to.minusDays(2))));
        chart.setClosePrices(List.of(new BigDecimal("300"), new BigDecimal("305")));
        when(stockQueryService.getStockChartWithParams(eq("THYAO"), anyString(), eq("1d"))).thenReturn(chart);

        Optional<NavigableMap<LocalDate, BigDecimal>> r =
                adapter.fetchDailyClosePrices(AssetType.STOCK, "THYAO", from, to);

        assertThat(r).isPresent();
        assertThat(r.get()).hasSize(2);
    }

    @Test
    @DisplayName("STOCK: boş grafik → Optional.empty")
    void stock_empty() {
        StockChartResponse chart = new StockChartResponse();
        chart.setTimestamps(List.of());
        chart.setClosePrices(List.of());
        when(stockQueryService.getStockChartWithParams(eq("XXXX"), anyString(), eq("1d"))).thenReturn(chart);

        assertThat(adapter.fetchDailyClosePrices(AssetType.STOCK, "XXXX", from, to)).isEmpty();
    }

    @Test
    @DisplayName("Dispatch: servis exception fırlatırsa yutulur → Optional.empty")
    void dispatch_catchesException() {
        when(stockQueryService.getStockChartWithParams(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("yahoo down"));

        assertThat(adapter.fetchDailyClosePrices(AssetType.STOCK, "ANY", from, to)).isEmpty();
    }

    // ── FUND (TEFAS) ────────────────────────────────────────────────────────

    @Test
    @DisplayName("FUND: TEFAS price-at serisi TL map'e dönüşür")
    void fund_tefas_ok() {
        when(tefasFundService.getPriceHistoryRange(eq("AAK"), eq("YAT"), any(), any()))
                .thenReturn(List.of(
                        new FundPriceHistoryPoint("2026-05-01", new BigDecimal("12.34"), null),
                        new FundPriceHistoryPoint("2026-05-02", new BigDecimal("12.55"), null)));

        Optional<NavigableMap<LocalDate, BigDecimal>> r =
                adapter.fetchDailyClosePrices(AssetType.FUND, "AAK", from, to);

        assertThat(r).isPresent();
        assertThat(r.get()).containsEntry(LocalDate.parse("2026-05-01"), new BigDecimal("12.34"));
    }

    @Test
    @DisplayName("FUND: TEFAS boş + Rasyonet null → Optional.empty")
    void fund_bothEmpty() {
        when(tefasFundService.getPriceHistoryRange(eq("ZZZ"), eq("YAT"), any(), any())).thenReturn(List.of());
        when(rasyonetFundService.getFundDetailRich("ZZZ")).thenReturn(null);

        assertThat(adapter.fetchDailyClosePrices(AssetType.FUND, "ZZZ", from, to)).isEmpty();
    }

    // ── FX ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("FX: TCMB geçmiş kapanışları TL serisine map'lenir")
    void fx_ok() {
        when(tcmbFxHistoryPort.fetchHistory(eq("USD"), any(), any()))
                .thenReturn(List.of(
                        new FxHistoryPoint("2026-05-01", new BigDecimal("32.10")),
                        new FxHistoryPoint("2026-05-02", new BigDecimal("32.40"))));

        Optional<NavigableMap<LocalDate, BigDecimal>> r =
                adapter.fetchDailyClosePrices(AssetType.FX, "USD", from, to);

        assertThat(r).isPresent();
        assertThat(r.get()).containsEntry(LocalDate.parse("2026-05-01"), new BigDecimal("32.10"));
    }

    // ── FUTURE (VİOP) ───────────────────────────────────────────────────────

    @Test
    @DisplayName("FUTURE: VİOP grafik noktaları günlük son-kapanış TL serisine dönüşür")
    void future_ok() {
        long millis = to.minusMonths(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        when(viopChartService.getChart(eq("F_XU030"), any(ViopChartPeriod.class)))
                .thenReturn(List.of(new ViopChartPoint(millis, "2026-05-01T18:00", new BigDecimal("9876.5"))));

        Optional<NavigableMap<LocalDate, BigDecimal>> r =
                adapter.fetchDailyClosePrices(AssetType.FUTURE, "F_XU030", from, to);

        assertThat(r).isPresent();
        assertThat(r.get().values()).contains(new BigDecimal("9876.5"));
    }

    // ── GOLD ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GOLD: gram/teorik altın (TRY) — getGoldHistory(TRY) serisi TL'ye map'lenir")
    void gold_try_ok() {
        GoldHistoryResponse resp = new GoldHistoryResponse();
        resp.setPoints(List.of(
                new GoldHistoryPoint("2026-05-01", new BigDecimal("2500")),
                new GoldHistoryPoint("2026-05-02", new BigDecimal("2520"))));
        when(goldMarketService.getGoldHistory(anyString(), eq("TRY"))).thenReturn(resp);

        Optional<NavigableMap<LocalDate, BigDecimal>> r =
                adapter.fetchDailyClosePrices(AssetType.GOLD, "GRAMALTIN", from, to);

        assertThat(r).isPresent();
        assertThat(r.get()).isNotEmpty();
    }

    @Test
    @DisplayName("GOLD: ONS altın (GOLD) — USD kapanış × tarihe uygun TCMB USD/TRY")
    void gold_usd_ok() {
        GoldHistoryResponse resp = new GoldHistoryResponse();
        resp.setPoints(List.of(new GoldHistoryPoint("2026-05-01", new BigDecimal("2000"))));
        when(goldMarketService.getGoldHistory(anyString(), eq("USD"))).thenReturn(resp);
        when(tcmbFxHistoryPort.fetchHistory(anyString(), any(), any()))
                .thenReturn(List.of(new FxHistoryPoint("2026-05-01", new BigDecimal("32"))));

        Optional<NavigableMap<LocalDate, BigDecimal>> r =
                adapter.fetchDailyClosePrices(AssetType.GOLD, "GOLD", from, to);

        assertThat(r).isPresent();
    }

    // ── COMMODITY ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("COMMODITY: Yahoo USD kapanış → tarihe uygun TCMB kuru ile TL")
    void commodity_ok() {
        CommodityHistoryPointDto pt = new CommodityHistoryPointDto();
        pt.setDate("2026-05-01");
        pt.setDisplayClose(new BigDecimal("75"));
        CommodityHistoryResponse resp = new CommodityHistoryResponse();
        resp.setPoints(List.of(pt));
        when(yahooCommodityService.getHistory(eq("CL"), anyString(), eq("1d"))).thenReturn(resp);
        when(tcmbFxHistoryPort.fetchHistory(anyString(), any(), any()))
                .thenReturn(List.of(new FxHistoryPoint("2026-05-01", new BigDecimal("32"))));

        Optional<NavigableMap<LocalDate, BigDecimal>> r =
                adapter.fetchDailyClosePrices(AssetType.COMMODITY, "CL", from, to);

        assertThat(r).isPresent();
    }

    // ── CRYPTO ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("CRYPTO: yakın tarih → CoinGecko TRY market_chart serisi")
    void crypto_coinGecko_ok() {
        CryptoMarketItem item = mock(CryptoMarketItem.class);
        when(item.getId()).thenReturn("bitcoin");
        when(cryptoMarketService.findBySymbol("BTC")).thenReturn(item);
        long ms = to.minusMonths(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        Map<String, Object> chart = new HashMap<>();
        chart.put("prices", List.of(List.of(ms, 50000.0)));
        when(cryptoMarketService.getMarketChart(eq("bitcoin"), any(), eq("try"), any(), any()))
                .thenReturn(chart);

        Optional<NavigableMap<LocalDate, BigDecimal>> r =
                adapter.fetchDailyClosePrices(AssetType.CRYPTO, "BTC", to.minusMonths(3), to);

        assertThat(r).isPresent();
        assertThat(r.get().values()).contains(new BigDecimal("50000.0"));
    }

    @Test
    @DisplayName("CRYPTO: yakın tarih CoinGecko boş → Yahoo USD × TCMB kuru fallback")
    void crypto_yahooFallback_ok() {
        LocalDate nearFrom = to.minusMonths(3);
        CryptoMarketItem item = mock(CryptoMarketItem.class);
        when(item.getId()).thenReturn("ethereum");
        when(item.getSymbol()).thenReturn("ETH");
        when(item.getCurrentPrice()).thenReturn(new BigDecimal("3200"));
        when(cryptoMarketService.findBySymbol("ETH")).thenReturn(item);
        when(cryptoMarketService.getMarketChart(eq("ethereum"), any(), eq("try"), any(), any()))
                .thenReturn(new HashMap<>()); // CoinGecko boş → fallback'e düş
        long ts = to.minusMonths(1).atStartOfDay(ZoneOffset.UTC).toEpochSecond();
        StockChartResponse usdChart = new StockChartResponse();
        usdChart.setTimestamps(List.of(ts));
        usdChart.setClosePrices(List.of(new BigDecimal("100"))); // USD kapanış
        when(cryptoYahooChartService.getLineChart(eq("ETH"), anyString(), eq("USD"))).thenReturn(usdChart);
        when(tcmbFxHistoryPort.fetchHistory(eq("USD"), any(), any()))
                .thenReturn(List.of(new FxHistoryPoint(to.minusMonths(1).toString(), new BigDecimal("32"))));

        Optional<NavigableMap<LocalDate, BigDecimal>> r =
                adapter.fetchDailyClosePrices(AssetType.CRYPTO, "ETH", nearFrom, to);

        assertThat(r).isPresent(); // 100 USD × 32 = 3200 TL (ratio ~1, kabul)
    }

    @Test
    @DisplayName("CRYPTO: eski tarih (>1yıl) → Binance {BASE}TRY günlük serisi")
    void crypto_binanceOld_ok() {
        LocalDate oldFrom = to.minusYears(2);
        when(cryptoMarketService.findBySymbol("BTC")).thenReturn(null);
        long sec = oldFrom.minusDays(10).atStartOfDay(ZoneOffset.UTC).toEpochSecond();
        CryptoChartCandle candle = mock(CryptoChartCandle.class);
        when(candle.getClose()).thenReturn(new BigDecimal("1500000"));
        when(candle.getTimestamp()).thenReturn(sec);
        when(cryptoBinanceChartService.getChartCandles(eq("BTC"), anyString(), eq("try")))
                .thenReturn(List.of(candle));

        Optional<NavigableMap<LocalDate, BigDecimal>> r =
                adapter.fetchDailyClosePrices(AssetType.CRYPTO, "BTC", oldFrom, to);

        assertThat(r).isPresent();
        assertThat(r.get().values()).contains(new BigDecimal("1500000"));
    }

    // ── BOND (EVDS DİBS) ────────────────────────────────────────────────────

    @Test
    @DisplayName("BOND: EVDS gösterge değerleri doğrudan TL serisi (eurobond değil)")
    void bond_evds_ok() {
        when(eurobondService.currentIsins()).thenReturn(List.of());
        when(evdsBondPort.fetchIndicatorValues(eq("TRT080528T16"), any(), any()))
                .thenReturn(List.of(
                        new EvdsSeriesPoint(LocalDate.parse("2026-05-01"), new BigDecimal("98.5")),
                        new EvdsSeriesPoint(LocalDate.parse("2026-05-02"), new BigDecimal("98.7"))));

        Optional<NavigableMap<LocalDate, BigDecimal>> r =
                adapter.fetchDailyClosePrices(AssetType.BOND, "TRT080528T16", from, to);

        assertThat(r).isPresent();
        assertThat(r.get()).containsEntry(LocalDate.parse("2026-05-01"), new BigDecimal("98.5"));
    }

    @Test
    @DisplayName("BOND: eurobond ISIN → BI kote × tarihe uygun TCMB kuru (TL)")
    void bond_eurobond_ok() {
        when(eurobondService.currentIsins()).thenReturn(List.of("XS1234567890"));
        when(eurobondService.detail("XS1234567890")).thenReturn(null); // currency → varsayılan USD
        when(eurobondService.chart(eq("XS1234567890"), anyString()))
                .thenReturn(List.of(new EurobondChartPoint("2026-05-01", new BigDecimal("99.5"), null, null, null)));
        when(tcmbFxHistoryPort.fetchHistory(eq("USD"), any(), any()))
                .thenReturn(List.of(new FxHistoryPoint("2026-05-01", new BigDecimal("32"))));

        Optional<NavigableMap<LocalDate, BigDecimal>> r =
                adapter.fetchDailyClosePrices(AssetType.BOND, "XS1234567890", from, to);

        assertThat(r).isPresent();
        assertThat(r.get().get(LocalDate.parse("2026-05-01"))).isEqualByComparingTo("3184"); // 99.5 × 32
    }

    // ── COMMODITY → gümüş / platin yönlendirmesi ────────────────────────────

    @Test
    @DisplayName("COMMODITY SILVER:GRAM_TRY → fetchSilver TL serisi")
    void commodity_silver_ok() {
        SilverHistoryPoint pt = new SilverHistoryPoint();
        pt.setDate("2026-05-01");
        pt.setClose(new BigDecimal("34.5"));
        SilverHistoryResponse resp = new SilverHistoryResponse();
        resp.setPoints(List.of(pt));
        when(silverMarketService.getSilverHistory(anyString(), eq("TRY"))).thenReturn(resp);

        Optional<NavigableMap<LocalDate, BigDecimal>> r =
                adapter.fetchDailyClosePrices(AssetType.COMMODITY, "SILVER:GRAM_TRY", from, to);

        assertThat(r).isPresent();
        assertThat(r.get()).containsEntry(LocalDate.parse("2026-05-01"), new BigDecimal("34.5"));
    }

    @Test
    @DisplayName("COMMODITY PLATINUM:GRAM_TRY → fetchPreciousMetal TL serisi")
    void commodity_platinum_ok() {
        PreciousMetalHistoryPoint pt = new PreciousMetalHistoryPoint();
        pt.setDate("2026-05-01");
        pt.setTryGram(new BigDecimal("1250"));
        PreciousMetalHistoryResponse resp = new PreciousMetalHistoryResponse();
        resp.setPoints(List.of(pt));
        when(preciousMetalService.getHistory(eq(PreciousMetalType.PLATINUM), anyString(), eq("TRY")))
                .thenReturn(resp);

        Optional<NavigableMap<LocalDate, BigDecimal>> r =
                adapter.fetchDailyClosePrices(AssetType.COMMODITY, "PLATINUM:GRAM_TRY", from, to);

        assertThat(r).isPresent();
        assertThat(r.get()).containsEntry(LocalDate.parse("2026-05-01"), new BigDecimal("1250"));
    }

    // ── Statik yardımcılar ──────────────────────────────────────────────────

    @Test
    @DisplayName("exclusionReason: FUTURE / gümüş COMMODITY / varsayılan mesajları")
    void exclusionReason_branches() {
        assertThat(PortfolioHistoricalPriceAdapter.exclusionReason(AssetType.FUTURE, "F_X"))
                .contains("VİOP");
        assertThat(PortfolioHistoricalPriceAdapter.exclusionReason(AssetType.COMMODITY, "SILVER:XAG"))
                .contains("Gümüş");
        assertThat(PortfolioHistoricalPriceAdapter.exclusionReason(AssetType.STOCK, "THYAO"))
                .isEqualTo("Tarihsel fiyat bulunamadı.");
    }

    @Test
    @DisplayName("isSilverCommoditySymbol: SILVER/GUMUS/GÜMÜŞ true, diğerleri false")
    void isSilver_branches() {
        assertThat(PortfolioHistoricalPriceAdapter.isSilverCommoditySymbol("SILVER")).isTrue();
        assertThat(PortfolioHistoricalPriceAdapter.isSilverCommoditySymbol("silver:xag")).isTrue();
        assertThat(PortfolioHistoricalPriceAdapter.isSilverCommoditySymbol("GUMUS")).isTrue();
        assertThat(PortfolioHistoricalPriceAdapter.isSilverCommoditySymbol("GÜMÜŞ")).isTrue();
        assertThat(PortfolioHistoricalPriceAdapter.isSilverCommoditySymbol("XAU")).isFalse();
        assertThat(PortfolioHistoricalPriceAdapter.isSilverCommoditySymbol(null)).isFalse();
        assertThat(PortfolioHistoricalPriceAdapter.isSilverCommoditySymbol("  ")).isFalse();
    }
}
