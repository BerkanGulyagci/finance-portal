package com.finance.portal.portfolio.infrastructure.market;

import com.finance.portal.common.domain.AssetType;
import com.finance.portal.market.application.bond.eurobond.EurobondService;
import com.finance.portal.market.application.bond.eurobond.model.EurobondChartPoint;
import com.finance.portal.market.application.bond.eurobond.model.EurobondDetail;
import com.finance.portal.market.application.bond.evds.port.EvdsBondPort;
import com.finance.portal.market.application.commodity.CommodityHistoryPointDto;
import com.finance.portal.market.application.commodity.CommodityHistoryResponse;
import com.finance.portal.market.application.commodity.YahooCommodityService;
import com.finance.portal.market.application.crypto.CryptoBinanceChartService;
import com.finance.portal.market.application.crypto.CryptoMarketService;
import com.finance.portal.market.application.crypto.CryptoYahooChartService;
import com.finance.portal.market.application.crypto.model.CryptoChartCandle;
import com.finance.portal.market.application.crypto.model.CryptoMarketItem;
import com.finance.portal.market.application.funds.model.FundPriceHistoryPoint;
import com.finance.portal.market.application.funds.model.RasyonetFundDetailDto;
import com.finance.portal.market.application.funds.service.RasyonetFundService;
import com.finance.portal.market.application.funds.service.TefasFundService;
import com.finance.portal.market.application.fx.model.FxHistoryPoint;
import com.finance.portal.market.application.fx.model.FxLatestRates;
import com.finance.portal.market.application.fx.model.FxRateItem;
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
import com.finance.portal.market.application.viop.UnsupportedViopContractException;
import com.finance.portal.market.application.viop.ViopChartPeriod;
import com.finance.portal.market.application.viop.ViopChartService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.NavigableMap;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link PortfolioHistoricalPriceAdapter} ek birim testleri — mevcut {@code ...Test} tarafından
 * kapsanmayan dallar: FX birim>1 bölme, FUND Rasyonet fallback, altın teorik faktör (çeyrek/ayar),
 * COMMODITY kur-yok, kripto eski-tarih Yahoo fallback / ratio-red / Binance pencere kaçırma,
 * gümüş & platin/paladyum kategori-çözüm dalları, eurobond detay-currency + latestFx fallback,
 * VİOP UnsupportedContract dalı ve statik {@code isPlatinumOrPalladiumSymbol}.
 */
@ExtendWith(MockitoExtension.class)
class PortfolioHistoricalPriceAdapterMoreTest {

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

    private static FxLatestRates ratesOf(FxRateItem... items) {
        return new FxLatestRates("TCMB", "OFFICIAL", "TRY", "now", List.of(items));
    }

    // ── FX: birim > 1 (JPY tarzı) kapanış unit'e bölünür ─────────────────────

    @Test
    @DisplayName("FX: birim>1 (JPY) — TCMB kapanışı unit'e bölünür")
    void fx_unitGreaterThanOne_dividesClose() {
        when(marketFxService.getTcmbLatestRates("JPY"))
                .thenReturn(ratesOf(new FxRateItem("JPY", new BigDecimal("0.22"), new BigDecimal("0.23"), 100)));
        when(tcmbFxHistoryPort.fetchHistory(eq("JPY"), any(), any()))
                .thenReturn(List.of(new FxHistoryPoint("2026-05-01", new BigDecimal("22.0"))));

        Optional<NavigableMap<LocalDate, BigDecimal>> r =
                adapter.fetchDailyClosePrices(AssetType.FX, "jpy", from, to);

        assertThat(r).isPresent();
        // 22.0 / 100 = 0.22 (scale 6)
        assertThat(r.get().get(LocalDate.parse("2026-05-01"))).isEqualByComparingTo("0.22");
    }

    @Test
    @DisplayName("FX: latest rates null olsa da unit=1 ile geçmiş seri döner")
    void fx_latestRatesNull_unitDefaultsToOne() {
        when(marketFxService.getTcmbLatestRates("EUR")).thenReturn(null);
        when(tcmbFxHistoryPort.fetchHistory(eq("EUR"), any(), any()))
                .thenReturn(List.of(new FxHistoryPoint("2026-05-01", new BigDecimal("36.50"))));

        Optional<NavigableMap<LocalDate, BigDecimal>> r =
                adapter.fetchDailyClosePrices(AssetType.FX, "EUR", from, to);

        assertThat(r).isPresent();
        assertThat(r.get().get(LocalDate.parse("2026-05-01"))).isEqualByComparingTo("36.50");
    }

    // ── FUND: TEFAS boş → Rasyonet fallback serisi ───────────────────────────

    @Test
    @DisplayName("FUND: TEFAS boş → Rasyonet priceHistory aralık-içi serisine düşer")
    void fund_rasyonetFallback_ok() {
        when(tefasFundService.getPriceHistoryRange(eq("BES1"), eq("YAT"), any(), any()))
                .thenReturn(List.of());
        RasyonetFundDetailDto detail = new RasyonetFundDetailDto();
        // biri aralık-içi, biri null fiyat (atlanır) → kapsam dalları
        detail.setPriceHistory(List.of(
                new RasyonetFundDetailDto.PricePoint(to.minusDays(5).toString(), new BigDecimal("1.23")),
                new RasyonetFundDetailDto.PricePoint(to.minusDays(4).toString(), null)));
        when(rasyonetFundService.getFundDetailRich("BES1")).thenReturn(detail);

        Optional<NavigableMap<LocalDate, BigDecimal>> r =
                adapter.fetchDailyClosePrices(AssetType.FUND, "BES1", from, to);

        assertThat(r).isPresent();
        assertThat(r.get()).containsEntry(to.minusDays(5), new BigDecimal("1.23"));
        assertThat(r.get()).hasSize(1);
    }

    @Test
    @DisplayName("FUND: TEFAS throw + Rasyonet priceHistory null → Optional.empty")
    void fund_tefasThrows_rasyonetNullHistory_empty() {
        when(tefasFundService.getPriceHistoryRange(eq("ERR"), eq("YAT"), any(), any()))
                .thenThrow(new RuntimeException("tefas down"));
        RasyonetFundDetailDto detail = new RasyonetFundDetailDto();
        detail.setPriceHistory(null);
        when(rasyonetFundService.getFundDetailRich("ERR")).thenReturn(detail);

        assertThat(adapter.fetchDailyClosePrices(AssetType.FUND, "ERR", from, to)).isEmpty();
    }

    // ── GOLD: teorik faktör dalları (çeyrek / 22 ayar) ───────────────────────

    @Test
    @DisplayName("GOLD CEYREK: gram TL × çeyrek brüt × milyem 22K faktörü uygulanır")
    void gold_ceyrek_factorApplied() {
        GoldHistoryResponse resp = new GoldHistoryResponse();
        resp.setPoints(List.of(new GoldHistoryPoint(to.minusDays(3).toString(), new BigDecimal("1000"))));
        when(goldMarketService.getGoldHistory(anyString(), eq("TRY"))).thenReturn(resp);

        Optional<NavigableMap<LocalDate, BigDecimal>> r =
                adapter.fetchDailyClosePrices(AssetType.GOLD, "CEYREK", from, to);

        assertThat(r).isPresent();
        // 1000 × (1.754 × 0.9166) = 1607.7164
        assertThat(r.get().get(to.minusDays(3))).isEqualByComparingTo("1607.716400");
    }

    @Test
    @DisplayName("GOLD 22AYAR: milyem 22K faktörü uygulanır")
    void gold_22ayar_factorApplied() {
        GoldHistoryResponse resp = new GoldHistoryResponse();
        resp.setPoints(List.of(new GoldHistoryPoint(to.minusDays(2).toString(), new BigDecimal("2000"))));
        when(goldMarketService.getGoldHistory(anyString(), eq("TRY"))).thenReturn(resp);

        Optional<NavigableMap<LocalDate, BigDecimal>> r =
                adapter.fetchDailyClosePrices(AssetType.GOLD, "22AYAR", from, to);

        assertThat(r).isPresent();
        // 2000 × 0.9166 = 1833.2
        assertThat(r.get().get(to.minusDays(2))).isEqualByComparingTo("1833.200000");
    }

    @Test
    @DisplayName("GOLD: getGoldHistory null → boş seri → Optional.empty")
    void gold_nullHistory_empty() {
        when(goldMarketService.getGoldHistory(anyString(), eq("TRY"))).thenReturn(null);

        assertThat(adapter.fetchDailyClosePrices(AssetType.GOLD, "GRAM", from, to)).isEmpty();
    }

    // ── COMMODITY: USD/TRY kuru hiç yoksa seri atlanır ───────────────────────

    @Test
    @DisplayName("COMMODITY: TCMB geçmiş yok + güncel USD/TRY yok → boş seri")
    void commodity_noFxRate_empty() {
        CommodityHistoryPointDto pt = new CommodityHistoryPointDto();
        pt.setDate("2026-05-01");
        pt.setDisplayClose(new BigDecimal("80"));
        CommodityHistoryResponse resp = new CommodityHistoryResponse();
        resp.setPoints(List.of(pt));
        when(yahooCommodityService.getHistory(eq("NG"), anyString(), eq("1d"))).thenReturn(resp);
        when(tcmbFxHistoryPort.fetchHistory(eq("USD"), any(), any())).thenReturn(List.of());
        when(marketFxService.getTcmbLatestRates("USD")).thenReturn(null); // fetchUsdTryRate → null

        assertThat(adapter.fetchDailyClosePrices(AssetType.COMMODITY, "NG", from, to)).isEmpty();
    }

    // ── CRYPTO: eski tarih, Yahoo fallback ratio reddi (yanlış token) ────────

    @Test
    @DisplayName("CRYPTO eski: Binance boş, Yahoo türetilen TL coin fiyatından çok uzak → reddedilir")
    void crypto_old_yahooRatioRejected() {
        LocalDate oldFrom = to.minusYears(2);
        CryptoMarketItem item = mock(CryptoMarketItem.class);
        lenient().when(item.getSymbol()).thenReturn("HYPE");
        when(item.getCurrentPrice()).thenReturn(new BigDecimal("1000")); // gerçek güncel TRY
        when(cryptoMarketService.findBySymbol("HYPE")).thenReturn(item);
        // Binance pair yok → boş
        when(cryptoBinanceChartService.getChartCandles(eq("HYPE"), anyString(), eq("try")))
                .thenReturn(List.of());
        // Yahoo USD serisi var
        long ts = to.minusMonths(1).atStartOfDay(ZoneOffset.UTC).toEpochSecond();
        StockChartResponse usdChart = new StockChartResponse();
        usdChart.setTimestamps(List.of(ts));
        usdChart.setClosePrices(List.of(new BigDecimal("5"))); // 5 USD
        when(cryptoYahooChartService.getLineChart(eq("HYPE"), anyString(), eq("USD"))).thenReturn(usdChart);
        when(tcmbFxHistoryPort.fetchHistory(eq("USD"), any(), any()))
                .thenReturn(List.of(new FxHistoryPoint(to.minusMonths(1).toString(), new BigDecimal("32"))));
        // 5 × 32 = 160 TL; ratio 160/1000 = 0.16 < 0.2 → reddedilir → boş

        assertThat(adapter.fetchDailyClosePrices(AssetType.CRYPTO, "HYPE", oldFrom, to)).isEmpty();
    }

    @Test
    @DisplayName("CRYPTO eski: Binance from'u kapsamıyor (geniş chart) → Yahoo fallback kabul")
    void crypto_old_binanceMissesFrom_yahooAccepted() {
        LocalDate oldFrom = to.minusYears(3); // window > 60 gün ve oldest > from
        CryptoMarketItem item = mock(CryptoMarketItem.class);
        lenient().when(item.getSymbol()).thenReturn("ETH");
        when(item.getCurrentPrice()).thenReturn(new BigDecimal("3200"));
        when(cryptoMarketService.findBySymbol("ETH")).thenReturn(item);
        // Binance serisi var ama oldest = bugün-1ay (from'dan çok sonra) → coversFrom=false,
        // priceAtLikeWindow=false (window büyük) → Yahoo'ya düşer
        CryptoChartCandle candle = mock(CryptoChartCandle.class);
        when(candle.getClose()).thenReturn(new BigDecimal("100000"));
        when(candle.getTimestamp())
                .thenReturn(to.minusMonths(1).atStartOfDay(ZoneOffset.UTC).toEpochSecond());
        when(cryptoBinanceChartService.getChartCandles(eq("ETH"), anyString(), eq("try")))
                .thenReturn(List.of(candle));
        // Yahoo USD serisi → kabul edilen ratio
        long ts = to.minusMonths(1).atStartOfDay(ZoneOffset.UTC).toEpochSecond();
        StockChartResponse usdChart = new StockChartResponse();
        usdChart.setTimestamps(List.of(ts));
        usdChart.setClosePrices(List.of(new BigDecimal("100"))); // 100 USD
        when(cryptoYahooChartService.getLineChart(eq("ETH"), anyString(), eq("USD"))).thenReturn(usdChart);
        when(tcmbFxHistoryPort.fetchHistory(eq("USD"), any(), any()))
                .thenReturn(List.of(new FxHistoryPoint(to.minusMonths(1).toString(), new BigDecimal("32"))));
        // 100 × 32 = 3200; ratio 3200/3200 = 1.0 → kabul

        Optional<NavigableMap<LocalDate, BigDecimal>> r =
                adapter.fetchDailyClosePrices(AssetType.CRYPTO, "ETH", oldFrom, to);

        assertThat(r).isPresent();
        assertThat(r.get().values()).contains(new BigDecimal("3200.000000"));
    }

    // ── SILVER: kategori çözüm dalları (KG_TRY ağırlıklı / USD_ONS) ──────────

    @Test
    @DisplayName("SILVER:KG_TRY → ağırlıklı ortalama TL/Kg kullanılır")
    void silver_kgTry_weightedAverage() {
        SilverHistoryPoint pt = new SilverHistoryPoint();
        pt.setDate(to.minusDays(3).toString());
        pt.setWeightedAverageTryKg(new BigDecimal("34500"));
        pt.setCloseTryKg(new BigDecimal("34000"));
        SilverHistoryResponse resp = new SilverHistoryResponse();
        resp.setPoints(List.of(pt));
        when(silverMarketService.getSilverHistory(anyString(), eq("TRY"))).thenReturn(resp);

        Optional<NavigableMap<LocalDate, BigDecimal>> r =
                adapter.fetchDailyClosePrices(AssetType.COMMODITY, "SILVER:KG_TRY", from, to);

        assertThat(r).isPresent();
        assertThat(r.get().get(to.minusDays(3))).isEqualByComparingTo("34500");
    }

    @Test
    @DisplayName("SILVER:USD_ONS → ağırlıklı USD/Ons; USD currency seçilir")
    void silver_usdOns_weighted() {
        SilverHistoryPoint pt = new SilverHistoryPoint();
        pt.setDate(to.minusDays(2).toString());
        pt.setWeightedAverageUsdOns(new BigDecimal("31.5"));
        pt.setClose(new BigDecimal("31.0"));
        SilverHistoryResponse resp = new SilverHistoryResponse();
        resp.setPoints(List.of(pt));
        when(silverMarketService.getSilverHistory(anyString(), eq("USD"))).thenReturn(resp);

        Optional<NavigableMap<LocalDate, BigDecimal>> r =
                adapter.fetchDailyClosePrices(AssetType.COMMODITY, "SILVER:USD_ONS", from, to);

        assertThat(r).isPresent();
        assertThat(r.get().get(to.minusDays(2))).isEqualByComparingTo("31.5");
    }

    @Test
    @DisplayName("SILVER: servis throw → Optional.empty")
    void silver_serviceThrows_empty() {
        when(silverMarketService.getSilverHistory(anyString(), eq("TRY")))
                .thenThrow(new RuntimeException("silver down"));

        assertThat(adapter.fetchDailyClosePrices(AssetType.COMMODITY, "SILVER:GRAM_TRY", from, to)).isEmpty();
    }

    // ── PRECIOUS (Palladyum + kategori dalları) ──────────────────────────────

    @Test
    @DisplayName("PALLADIUM:USD_ONS → usdOns değeri ile USD currency")
    void palladium_usdOns() {
        PreciousMetalHistoryPoint pt = new PreciousMetalHistoryPoint();
        pt.setDate(to.minusDays(4).toString());
        pt.setUsdOns(new BigDecimal("950"));
        PreciousMetalHistoryResponse resp = new PreciousMetalHistoryResponse();
        resp.setPoints(List.of(pt));
        when(preciousMetalService.getHistory(eq(PreciousMetalType.PALLADIUM), anyString(), eq("USD")))
                .thenReturn(resp);

        Optional<NavigableMap<LocalDate, BigDecimal>> r =
                adapter.fetchDailyClosePrices(AssetType.COMMODITY, "PALLADIUM:USD_ONS", from, to);

        assertThat(r).isPresent();
        assertThat(r.get().get(to.minusDays(4))).isEqualByComparingTo("950");
    }

    @Test
    @DisplayName("PLATINUM:EUR_ONS → eurOns yoksa value fallback; EUR currency")
    void platinum_eurOns_valueFallback() {
        PreciousMetalHistoryPoint pt = new PreciousMetalHistoryPoint();
        pt.setDate(to.minusDays(6).toString());
        pt.setEurOns(null);           // kategori değeri yok
        pt.setValue(new BigDecimal("888")); // → fallback value
        PreciousMetalHistoryResponse resp = new PreciousMetalHistoryResponse();
        resp.setPoints(List.of(pt));
        when(preciousMetalService.getHistory(eq(PreciousMetalType.PLATINUM), anyString(), eq("EUR")))
                .thenReturn(resp);

        Optional<NavigableMap<LocalDate, BigDecimal>> r =
                adapter.fetchDailyClosePrices(AssetType.COMMODITY, "PLATINUM:EUR_ONS", from, to);

        assertThat(r).isPresent();
        assertThat(r.get().get(to.minusDays(6))).isEqualByComparingTo("888");
    }

    @Test
    @DisplayName("PRECIOUS: servis null response → Optional.empty")
    void precious_nullResponse_empty() {
        when(preciousMetalService.getHistory(eq(PreciousMetalType.PLATINUM), anyString(), eq("TRY")))
                .thenReturn(null);

        assertThat(adapter.fetchDailyClosePrices(AssetType.COMMODITY, "PLATINUM:GRAM_TRY", from, to)).isEmpty();
    }

    // ── BOND eurobond: detay-currency + tarihsel kur yok, latestFx fallback ──

    @Test
    @DisplayName("BOND eurobond: FX geçmişi boş ama detail.fxRate var → güncel kur ile TL")
    void eurobond_emptyFxHistory_usesDetailFxRate() {
        EurobondDetail d = new EurobondDetail();
        d.setCurrency("EUR");
        d.setFxRate(new BigDecimal("36"));
        when(eurobondService.currentIsins()).thenReturn(List.of("XS9990001112"));
        when(eurobondService.detail("XS9990001112")).thenReturn(d);
        when(eurobondService.chart(eq("XS9990001112"), anyString()))
                .thenReturn(List.of(new EurobondChartPoint(to.minusDays(3).toString(),
                        new BigDecimal("101.0"), null, null, null)));
        // EUR tarihsel kur yok → fxByDay boş, latestFx=detail.fxRate kullanılır
        when(tcmbFxHistoryPort.fetchHistory(eq("EUR"), any(), any())).thenReturn(List.of());
        lenient().when(marketFxService.getTcmbLatestRates("EUR")).thenReturn(null);

        Optional<NavigableMap<LocalDate, BigDecimal>> r =
                adapter.fetchDailyClosePrices(AssetType.BOND, "XS9990001112", from, to);

        assertThat(r).isPresent();
        // 101.0 × 36 = 3636 (scale 6)
        assertThat(r.get().get(to.minusDays(3))).isEqualByComparingTo("3636.000000");
    }

    @Test
    @DisplayName("BOND eurobond: chart boş → boş seri → Optional.empty")
    void eurobond_emptyChart_empty() {
        when(eurobondService.currentIsins()).thenReturn(List.of("XS0000000001"));
        when(eurobondService.detail("XS0000000001")).thenReturn(null);
        when(eurobondService.chart(eq("XS0000000001"), anyString())).thenReturn(List.of());

        assertThat(adapter.fetchDailyClosePrices(AssetType.BOND, "XS0000000001", from, to)).isEmpty();
    }

    // ── FUTURE: UnsupportedViopContractException → boş (kısa periyot denenmez) ─

    @Test
    @DisplayName("FUTURE: UnsupportedViopContractException → kısa periyot denenmeden boş")
    void future_unsupportedContract_empty() {
        when(viopChartService.getChart(eq("F_XUSD"), any(ViopChartPeriod.class)))
                .thenThrow(new UnsupportedViopContractException("desteklenmiyor"));

        assertThat(adapter.fetchDailyClosePrices(AssetType.FUTURE, "F_XUSD", from, to)).isEmpty();
    }

    // ── Statik: isPlatinumOrPalladiumSymbol ──────────────────────────────────

    @Test
    @DisplayName("isPlatinumOrPalladiumSymbol: PLATINUM/PALLADIUM true; null/blank/diğer false")
    void isPlatinumOrPalladium_branches() {
        assertThat(PortfolioHistoricalPriceAdapter.isPlatinumOrPalladiumSymbol("PLATINUM:GRAM_TRY")).isTrue();
        assertThat(PortfolioHistoricalPriceAdapter.isPlatinumOrPalladiumSymbol("palladium")).isTrue();
        assertThat(PortfolioHistoricalPriceAdapter.isPlatinumOrPalladiumSymbol("GOLD")).isFalse();
        assertThat(PortfolioHistoricalPriceAdapter.isPlatinumOrPalladiumSymbol(null)).isFalse();
        assertThat(PortfolioHistoricalPriceAdapter.isPlatinumOrPalladiumSymbol("   ")).isFalse();
    }
}
