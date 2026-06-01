package com.finance.portal.market.application.bond.eurobond;

import com.finance.portal.market.application.bond.eurobond.model.EurobondChartPoint;
import com.finance.portal.market.application.bond.eurobond.model.EurobondDetail;
import com.finance.portal.market.application.bond.eurobond.model.EurobondRef;
import com.finance.portal.market.application.bond.eurobond.model.EurobondSummary;
import com.finance.portal.market.application.bond.eurobond.model.HmbBond;
import com.finance.portal.market.application.bond.eurobond.port.BusinessInsiderBondPort;
import com.finance.portal.market.application.bond.eurobond.port.HmbIsinSource;
import com.finance.portal.market.application.fx.model.FxLatestRates;
import com.finance.portal.market.application.fx.model.FxRateItem;
import com.finance.portal.market.application.service.MarketFxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EurobondServiceTest {

    @Mock
    private BusinessInsiderBondPort bi;
    @Mock
    private HmbIsinSource hmb;
    @Mock
    private CacheManager cacheManager;
    @Mock
    private MarketFxService marketFxService;

    private EurobondService service;

    @BeforeEach
    void setUp() {
        service = new EurobondService(bi, hmb, cacheManager, marketFxService);
    }

    // ── helpers ─────────────────────────────────────────────────────────────────

    private EurobondDetail detail(String isin, String currency, BigDecimal lastPrice) {
        EurobondDetail d = new EurobondDetail();
        d.setIsin(isin);
        d.setName("US TREASURY " + isin);
        d.setIssuer("U.S. Treasury");
        d.setCurrency(currency);
        d.setCouponRate("5.200%");
        d.setMaturityDate("8/17/2031");
        d.setLastPrice(lastPrice);
        d.setChangePercent(new BigDecimal("0.35"));
        d.setInstrumentId(12345L);
        d.setTkData("1,627799832,1330,333");
        return d;
    }

    private FxLatestRates rates(String symbol, BigDecimal sell, BigDecimal buy, int unit) {
        FxRateItem item = new FxRateItem(symbol, buy, sell, unit);
        return new FxLatestRates("TCMB", "official", "TRY", "2026-05-30", List.of(item));
    }

    // ── fxRateToTry ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("fxRateToTry: TRY / null / blank → 1")
    void fxRateTryOne() {
        assertThat(service.fxRateToTry("TRY")).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(service.fxRateToTry("try")).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(service.fxRateToTry(null)).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(service.fxRateToTry("  ")).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    @DisplayName("fxRateToTry: USD unit=1 → satış kuru döner")
    void fxRateUsd() {
        when(marketFxService.getTcmbLatestRates("USD"))
                .thenReturn(rates("USD", new BigDecimal("32.50"), new BigDecimal("32.40"), 1));

        assertThat(service.fxRateToTry("USD")).isEqualByComparingTo(new BigDecimal("32.50"));
    }

    @Test
    @DisplayName("fxRateToTry: JPY unit=100 → birim başına bölünür")
    void fxRateJpyUnit() {
        when(marketFxService.getTcmbLatestRates("JPY"))
                .thenReturn(rates("JPY", new BigDecimal("21.00"), new BigDecimal("20.90"), 100));

        // 21.00 / 100 = 0.21
        assertThat(service.fxRateToTry("JPY")).isEqualByComparingTo(new BigDecimal("0.21"));
    }

    @Test
    @DisplayName("fxRateToTry: satış null ise alış kuru kullanılır")
    void fxRateSellNullUsesBuy() {
        when(marketFxService.getTcmbLatestRates("EUR"))
                .thenReturn(rates("EUR", null, new BigDecimal("35.10"), 1));

        assertThat(service.fxRateToTry("EUR")).isEqualByComparingTo(new BigDecimal("35.10"));
    }

    @Test
    @DisplayName("fxRateToTry: sembol listede yok → null")
    void fxRateSymbolMissing() {
        when(marketFxService.getTcmbLatestRates("GBP"))
                .thenReturn(rates("USD", new BigDecimal("32"), new BigDecimal("31"), 1));

        assertThat(service.fxRateToTry("GBP")).isNull();
    }

    @Test
    @DisplayName("fxRateToTry: hem satış hem alış null → null")
    void fxRateBothNull() {
        when(marketFxService.getTcmbLatestRates("EUR"))
                .thenReturn(rates("EUR", null, null, 1));

        assertThat(service.fxRateToTry("EUR")).isNull();
    }

    @Test
    @DisplayName("fxRateToTry: FX servisi exception → null (yutulur)")
    void fxRateException() {
        when(marketFxService.getTcmbLatestRates(anyString()))
                .thenThrow(new RuntimeException("tcmb down"));

        assertThat(service.fxRateToTry("USD")).isNull();
    }

    // ── detail / loadDetail ──────────────────────────────────────────────────────

    @Test
    @DisplayName("detail: BI çözülür → detay + TL fiyatı (lastPrice × fxRate) eklenir")
    void detailWithFx() {
        EurobondDetail d = detail("US123", "USD", new BigDecimal("99.50"));
        EurobondRef ref = new EurobondRef("US123", "slug", "name", "issuer", 1L);
        when(bi.resolve("US123")).thenReturn(Optional.of(ref));
        when(bi.fetchDetail(ref)).thenReturn(Optional.of(d));
        when(marketFxService.getTcmbLatestRates("USD"))
                .thenReturn(rates("USD", new BigDecimal("32.00"), new BigDecimal("31.90"), 1));

        EurobondDetail out = service.detail("US123");

        assertThat(out).isNotNull();
        assertThat(out.getFxRate()).isEqualByComparingTo(new BigDecimal("32.00"));
        // 99.50 × 32.00 = 3184.0000
        assertThat(out.getLastPriceTry()).isEqualByComparingTo(new BigDecimal("3184.0000"));
    }

    @Test
    @DisplayName("detail: BI çözülemez → null")
    void detailUnresolved() {
        when(bi.resolve("X")).thenReturn(Optional.empty());

        assertThat(service.detail("X")).isNull();
    }

    @Test
    @DisplayName("detail: lastPrice null → TL fiyatı eklenmez")
    void detailNoLastPrice() {
        EurobondDetail d = detail("US1", "USD", null);
        EurobondRef ref = new EurobondRef("US1", "slug", "n", "i", 1L);
        when(bi.resolve("US1")).thenReturn(Optional.of(ref));
        when(bi.fetchDetail(ref)).thenReturn(Optional.of(d));

        EurobondDetail out = service.detail("US1");

        assertThat(out).isNotNull();
        assertThat(out.getLastPriceTry()).isNull();
        verify(marketFxService, never()).getTcmbLatestRates(anyString());
    }

    @Test
    @DisplayName("detail: fxRate null (kur yok) → lastPriceTry set edilmez")
    void detailFxRateNull() {
        EurobondDetail d = detail("US1", "USD", new BigDecimal("100"));
        EurobondRef ref = new EurobondRef("US1", "slug", "n", "i", 1L);
        when(bi.resolve("US1")).thenReturn(Optional.of(ref));
        when(bi.fetchDetail(ref)).thenReturn(Optional.of(d));
        when(marketFxService.getTcmbLatestRates("USD"))
                .thenReturn(rates("USD", null, null, 1)); // → null rate

        EurobondDetail out = service.detail("US1");

        assertThat(out.getLastPriceTry()).isNull();
        assertThat(out.getFxRate()).isNull();
    }

    // ── currentPrice ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("currentPrice: detaydan lastPrice döner")
    void currentPrice() {
        EurobondDetail d = detail("US1", "USD", new BigDecimal("101.25"));
        EurobondRef ref = new EurobondRef("US1", "slug", "n", "i", 1L);
        when(bi.resolve("US1")).thenReturn(Optional.of(ref));
        when(bi.fetchDetail(ref)).thenReturn(Optional.of(d));
        when(marketFxService.getTcmbLatestRates("USD"))
                .thenReturn(rates("USD", new BigDecimal("30"), new BigDecimal("29"), 1));

        assertThat(service.currentPrice("US1")).isEqualByComparingTo(new BigDecimal("101.25"));
    }

    @Test
    @DisplayName("currentPrice: detay yok → null")
    void currentPriceNull() {
        when(bi.resolve("X")).thenReturn(Optional.empty());
        assertThat(service.currentPrice("X")).isNull();
    }

    // ── list / merge ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("list: BI canlı satır → detay alanları HMB künyesiyle birleşir (hasDetail=true)")
    void listWithBiDetail() {
        HmbBond hb = new HmbBond("US123", null, null, null, "Tahvil");
        when(hmb.bonds()).thenReturn(List.of(hb));
        EurobondDetail d = detail("US123", "USD", new BigDecimal("99.50"));
        EurobondRef ref = new EurobondRef("US123", "slug", "n", "i", 1L);
        when(bi.resolve("US123")).thenReturn(Optional.of(ref));
        when(bi.fetchDetail(ref)).thenReturn(Optional.of(d));
        when(marketFxService.getTcmbLatestRates("USD"))
                .thenReturn(rates("USD", new BigDecimal("32"), new BigDecimal("31"), 1));

        List<EurobondSummary> out = service.list();

        assertThat(out).hasSize(1);
        EurobondSummary s = out.get(0);
        assertThat(s.isin()).isEqualTo("US123");
        assertThat(s.hasDetail()).isTrue();
        assertThat(s.name()).isEqualTo("US TREASURY US123");
        assertThat(s.issuer()).isEqualTo("U.S. Treasury");
        assertThat(s.currency()).isEqualTo("USD");
        assertThat(s.couponRate()).isEqualTo("5.200%");
        assertThat(s.maturityDate()).isEqualTo("8/17/2031");
        assertThat(s.issueType()).isEqualTo("Tahvil");
        assertThat(s.lastPrice()).isEqualByComparingTo(new BigDecimal("99.50"));
        assertThat(s.instrumentId()).isEqualTo(12345L);
    }

    @Test
    @DisplayName("list: BI çözülemez → HMB yedek satırı (hasDetail=false, issuer='T.C. Hazine')")
    void listFallbackToHmb() {
        HmbBond hb = new HmbBond("TR999", "EUR", "4.500", "17.08.2031", "Kira Sertifikası");
        when(hmb.bonds()).thenReturn(List.of(hb));
        when(bi.resolve("TR999")).thenReturn(Optional.empty());

        List<EurobondSummary> out = service.list();

        assertThat(out).hasSize(1);
        EurobondSummary s = out.get(0);
        assertThat(s.hasDetail()).isFalse();
        assertThat(s.name()).isNull();
        assertThat(s.issuer()).isEqualTo("T.C. Hazine");
        assertThat(s.currency()).isEqualTo("EUR");
        // HMB couponRate "4.500" → "4.500%"
        assertThat(s.couponRate()).isEqualTo("4.500%");
        assertThat(s.maturityDate()).isEqualTo("17.08.2031");
        assertThat(s.lastPrice()).isNull();
        assertThat(s.instrumentId()).isNull();
    }

    @Test
    @DisplayName("list: HMB künyesi BI detayına göre önceliklidir (currency/coupon/maturity)")
    void listHmbPrecedence() {
        HmbBond hb = new HmbBond("US1", "USD", "6.000", "01.01.2030", "Tahvil");
        when(hmb.bonds()).thenReturn(List.of(hb));
        EurobondDetail d = detail("US1", "EUR", new BigDecimal("100")); // BI says EUR/5.200%/8-17-2031
        EurobondRef ref = new EurobondRef("US1", "slug", "n", "i", 1L);
        when(bi.resolve("US1")).thenReturn(Optional.of(ref));
        when(bi.fetchDetail(ref)).thenReturn(Optional.of(d));
        when(marketFxService.getTcmbLatestRates(anyString()))
                .thenReturn(rates("USD", new BigDecimal("32"), new BigDecimal("31"), 1));

        EurobondSummary s = service.list().get(0);

        // HMB values win
        assertThat(s.currency()).isEqualTo("USD");
        assertThat(s.couponRate()).isEqualTo("6.000%");
        assertThat(s.maturityDate()).isEqualTo("01.01.2030");
    }

    @Test
    @DisplayName("list: boş HMB listesi → boş çıktı")
    void listEmpty() {
        when(hmb.bonds()).thenReturn(List.of());
        assertThat(service.list()).isEmpty();
    }

    // ── chart ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("chart: tkData varsa BI.fetchChart sonucu döner")
    void chartWithData() {
        EurobondDetail d = detail("US1", "USD", new BigDecimal("100"));
        EurobondRef ref = new EurobondRef("US1", "slug", "n", "i", 1L);
        when(bi.resolve("US1")).thenReturn(Optional.of(ref));
        when(bi.fetchDetail(ref)).thenReturn(Optional.of(d));
        when(marketFxService.getTcmbLatestRates(anyString()))
                .thenReturn(rates("USD", new BigDecimal("32"), new BigDecimal("31"), 1));
        List<EurobondChartPoint> pts = List.of(
                new EurobondChartPoint("2026-01-01", new BigDecimal("100"), new BigDecimal("99"),
                        new BigDecimal("101"), new BigDecimal("98")));
        when(bi.fetchChart(eq("1,627799832,1330,333"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(pts);

        List<EurobondChartPoint> out = service.chart("US1", "1Y");

        assertThat(out).hasSize(1);
        assertThat(out.get(0).close()).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("chart: detay yok → boş liste, BI.fetchChart çağrılmaz")
    void chartNoDetail() {
        when(bi.resolve("X")).thenReturn(Optional.empty());

        assertThat(service.chart("X", "1Y")).isEmpty();
        verify(bi, never()).fetchChart(anyString(), any(), any());
    }

    @Test
    @DisplayName("chart: tkData boş → boş liste")
    void chartBlankTkData() {
        EurobondDetail d = detail("US1", "USD", new BigDecimal("100"));
        d.setTkData("  ");
        EurobondRef ref = new EurobondRef("US1", "slug", "n", "i", 1L);
        when(bi.resolve("US1")).thenReturn(Optional.of(ref));
        when(bi.fetchDetail(ref)).thenReturn(Optional.of(d));
        when(marketFxService.getTcmbLatestRates(anyString()))
                .thenReturn(rates("USD", new BigDecimal("32"), new BigDecimal("31"), 1));

        assertThat(service.chart("US1", "1Y")).isEmpty();
        verify(bi, never()).fetchChart(anyString(), any(), any());
    }

    @Test
    @DisplayName("chart: range varyantları fromDate'i etkiler (1M/YTD/MAX/null/bilinmeyen)")
    void chartRangeVariants() {
        EurobondDetail d = detail("US1", "USD", new BigDecimal("100"));
        EurobondRef ref = new EurobondRef("US1", "slug", "n", "i", 1L);
        when(bi.resolve("US1")).thenReturn(Optional.of(ref));
        when(bi.fetchDetail(ref)).thenReturn(Optional.of(d));
        when(marketFxService.getTcmbLatestRates(anyString()))
                .thenReturn(rates("USD", new BigDecimal("32"), new BigDecimal("31"), 1));
        when(bi.fetchChart(anyString(), any(), any())).thenReturn(List.of());

        org.mockito.ArgumentCaptor<LocalDate> fromCap = org.mockito.ArgumentCaptor.forClass(LocalDate.class);

        service.chart("US1", "1M");
        service.chart("US1", "YTD");
        service.chart("US1", "MAX");
        service.chart("US1", null);
        service.chart("US1", "WTF"); // unknown → default 1Y

        verify(bi, times(5)).fetchChart(anyString(), fromCap.capture(), any());
        List<LocalDate> froms = fromCap.getAllValues();
        LocalDate now = LocalDate.now();
        assertThat(froms.get(0)).isEqualTo(now.minusMonths(1));               // 1M
        assertThat(froms.get(1)).isEqualTo(LocalDate.of(now.getYear(), 1, 1)); // YTD
        assertThat(froms.get(2)).isEqualTo(now.minusYears(30));               // MAX
        assertThat(froms.get(3)).isEqualTo(now.minusYears(1));                // null → 1Y
        assertThat(froms.get(4)).isEqualTo(now.minusYears(1));                // unknown → 1Y
    }

    // ── refreshIsins / currentIsins ──────────────────────────────────────────────

    @Test
    @DisplayName("refreshIsins: HMB tazelenir ve list+detail cache'leri temizlenir")
    void refreshIsinsEvicts() {
        Cache listCache = org.mockito.Mockito.mock(Cache.class);
        Cache detailCache = org.mockito.Mockito.mock(Cache.class);
        when(hmb.refreshFromXlsx("http://xlsx", false)).thenReturn(42);
        when(cacheManager.getCache("market.eurobond.list")).thenReturn(listCache);
        when(cacheManager.getCache("market.eurobond.detail")).thenReturn(detailCache);

        int n = service.refreshIsins("http://xlsx");

        assertThat(n).isEqualTo(42);
        verify(hmb).refreshFromXlsx("http://xlsx", false);
        verify(listCache).clear();
        verify(detailCache).clear();
    }

    @Test
    @DisplayName("refreshIsins(force=true): force HMB'ye geçirilir")
    void refreshIsinsForce() {
        when(hmb.refreshFromXlsx("http://xlsx", true)).thenReturn(10);
        when(cacheManager.getCache(anyString())).thenReturn(null); // best-effort evict

        int n = service.refreshIsins("http://xlsx", true);

        assertThat(n).isEqualTo(10);
        verify(hmb).refreshFromXlsx("http://xlsx", true);
    }

    @Test
    @DisplayName("refreshIsins: cache null (Redis yok) → evict best-effort, hata atılmaz")
    void refreshIsinsNullCache() {
        when(hmb.refreshFromXlsx(anyString(), anyBoolean())).thenReturn(5);
        when(cacheManager.getCache(anyString())).thenReturn(null);

        assertThat(service.refreshIsins("http://xlsx")).isEqualTo(5);
    }

    @Test
    @DisplayName("refreshIsins: cache.clear() exception → yutulur (best-effort)")
    void refreshIsinsClearThrows() {
        Cache throwing = org.mockito.Mockito.mock(Cache.class);
        org.mockito.Mockito.doThrow(new RuntimeException("redis down")).when(throwing).clear();
        when(hmb.refreshFromXlsx(anyString(), anyBoolean())).thenReturn(7);
        when(cacheManager.getCache(anyString())).thenReturn(throwing);

        assertThat(service.refreshIsins("http://xlsx")).isEqualTo(7);
    }

    @Test
    @DisplayName("currentIsins: HMB.isins() delegasyonu")
    void currentIsins() {
        when(hmb.isins()).thenReturn(List.of("US1", "US2"));
        assertThat(service.currentIsins()).containsExactly("US1", "US2");
    }
}
