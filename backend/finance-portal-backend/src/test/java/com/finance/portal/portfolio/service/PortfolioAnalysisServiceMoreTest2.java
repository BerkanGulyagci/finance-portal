package com.finance.portal.portfolio.service;

import com.finance.portal.common.domain.AssetType;
import com.finance.portal.portfolio.application.analysis.PortfolioAiAnalysisResult;
import com.finance.portal.portfolio.application.analysis.PortfolioAiAnalysisResult.AllocationSlice;
import com.finance.portal.portfolio.application.analysis.PortfolioAiAnalysisResult.AssetSignal;
import com.finance.portal.portfolio.application.analysis.PortfolioAiAnalysisResult.RiskMetrics;
import com.finance.portal.portfolio.application.whatif.WhatIfSeriesPoint;
import com.finance.portal.portfolio.application.whatif.WhatIfSeriesResult;
import com.finance.portal.portfolio.presentation.dto.PortfolioHoldingResponse;
import com.finance.portal.portfolio.presentation.dto.PortfolioResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 3. tur ek kapsam: {@link PortfolioAnalysisService} içinde PortfolioAnalysisServiceTest ve
 * PortfolioAnalysisServiceMoreTest'in İKİSİNİN de DOKUNMADIĞI dallar. JaCoCo raporundaki
 * kırmızı/sarı (nc/pc) satırları hedefler:
 * <ul>
 *   <li>typeLabel + assetTypeRisk switch'lerinin az kullanılan tipleri (FUTURE/COMMODITY/FUND/FX + null tip)</li>
 *   <li>yoğunlaşma etiketleri "İyi çeşitlendirilmiş" + "Orta yoğunlaşma" (yalnız "Yüksek" kapsanmıştı)</li>
 *   <li>MA durumu "MA20 üstü, MA50 altı" (fiyat MA20 üstünde ama MA50 altında) + 52-hafta orta bant (üst/alt yarı)</li>
 *   <li>computeRiskMetrics kısa-geçmiş erken dönüşü (&lt; MIN_SAMPLES) → forecast erken dönüşü</li>
 *   <li>beta hesaplanamaz (benchmark serisi hizasız) + rf=0 (mevduat &lt; 2 nokta)</li>
 *   <li>forecast TOP_ASSETS sınırı (&gt;6 pozisyon) + null-tip pozisyon forecast'ta atlanır</li>
 *   <li>rebalanceFor null-TL atlama, analyze sıfır-TL atlama, profitPercent null piyasa-değeri, holdings=null</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PortfolioAnalysisServiceMoreTest2 {

    @Mock private PortfolioService portfolioService;
    @Mock private PortfolioWhatIfService whatIfService;
    @Mock private PortfolioCurrencyConverter currencyConverter;
    @Mock private PortfolioStressTestService stressTestService;
    @Mock private PortfolioHistoricalRiskService historicalRiskService;
    @Mock private PortfolioAiNarrator narrator;

    // Gerçek servisler (dış bağımlılığı yok) — deterministik.
    private final PortfolioMonteCarloService monteCarloService = new PortfolioMonteCarloService();
    private final PortfolioRebalanceService rebalanceService = new PortfolioRebalanceService();

    private PortfolioAnalysisService service;

    private final UUID pid = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new PortfolioAnalysisService(portfolioService, whatIfService, currencyConverter,
                stressTestService, historicalRiskService, monteCarloService, rebalanceService, narrator);
        // toTry: TRY varsayımı (identity) — gerektiğinde testler override eder.
        when(currencyConverter.toTry(any(), any())).thenAnswer(inv -> inv.getArgument(0));
        when(narrator.generate(any(), any(), any(), any())).thenReturn("Yorum.");
        when(stressTestService.compute(any())).thenReturn(List.of());
        when(whatIfService.computeSeries(any(), any(), any(), any())).thenReturn(eightPointSeries());
        // Varsayılan tarihsel-risk "yok" → what-if oran serisi yoluna düşer (computeRiskMetrics).
        when(historicalRiskService.computeFromHoldings(any(), anyInt())).thenReturn(unavailableMetrics());
    }

    // ── 1) Az kullanılan varlık tipleri + null tip (typeLabel/assetTypeRisk/typeName switch dalları) ──

    @Test
    void analyze_mixedAssetTypes_coversTypeSwitchesAndNullType() {
        PortfolioResponse p = new PortfolioResponse();
        p.setId(pid);
        p.setName("Karışık");
        // FUTURE/COMMODITY/FUND/FX + isimsiz (label sembolden) + null-tip (typeName→OTHER, assetTypeRisk→50).
        // profitLossPercent NULL bırakılır → maliyetten hesaplama dalı (L299/L409) tetiklenir.
        PortfolioHoldingResponse fut = bare("VIOPX", null, AssetType.FUTURE, "1000", "1500");
        PortfolioHoldingResponse com = bare("XCOM", "Bakır", AssetType.COMMODITY, "1000", "1200");
        PortfolioHoldingResponse fund = bare("FONX", "Fon A", AssetType.FUND, "1000", "1100");
        PortfolioHoldingResponse fx = bare("USDX", "Dolar", AssetType.FX, "1000", "1050");
        PortfolioHoldingResponse unknown = bare("UNK", null, null, "1000", "1000"); // assetType NULL
        p.setHoldings(List.of(fut, com, fund, fx, unknown));
        p.setTotalCost(new BigDecimal("5000"));
        p.setTotalMarketValue(new BigDecimal("5850"));
        p.setTotalProfitLoss(new BigDecimal("850"));
        p.setTotalRealProfitLossPercent(new BigDecimal("1.0"));
        when(portfolioService.getPortfolioById(eq("u1"), eq(pid))).thenReturn(p);

        PortfolioAiAnalysisResult r = service.analyze("u1", pid, "Berkan", "b@x.com");

        assertThat(r.getHoldingsCount()).isEqualTo(5);
        // Tip-dağılımı etiketleri switch'in az kullanılan dallarını kapsar.
        var typeLabels = r.getAssetTypeAllocation().stream().map(AllocationSlice::label).toList();
        assertThat(typeLabels).contains("Vadeli (VİOP)", "Emtia", "Fon", "Döviz");
        // null tip → typeName "OTHER" → typeLabel default (type adıyla aynı) → "OTHER".
        assertThat(r.getAssetTypeAllocation()).extracting(AllocationSlice::assetType).contains("OTHER");
        assertThat(typeLabels).contains("OTHER");
        // İsimsiz FUTURE pozisyonu label'ı sembolden aldı.
        assertThat(r.getAllocation()).extracting(AllocationSlice::label).contains("VIOPX");
        // profitLossPercent null'dı → maliyetten türetildi (FUTURE +50%).
        var futReturn = r.getAssetReturns().stream().filter(a -> "VIOPX".equals(a.symbol())).findFirst().orElseThrow();
        assertThat(futReturn.profitLossPercent()).isEqualByComparingTo("50.00");
        // Risk skoru hesaplanmış (assetTypeRisk FUTURE=100/.../null=50 dalları çalıştı).
        assertThat(r.getRiskScore()).isBetween(0, 100);
        // null-tip pozisyon forecast varlık listesinde ATLANIR (sembolü var ama tipi yok).
        assertThat(r.getForecast()).isNotNull();
        assertThat(r.getForecast().assetForecasts()).extracting(
                PortfolioAiAnalysisResult.AssetForecast::symbol).doesNotContain("UNK");
    }

    // ── 2) "İyi çeşitlendirilmiş" yoğunlaşma + >6 pozisyon (forecast TOP_ASSETS sınırı) ──

    @Test
    void analyze_wellDiversified_lowConcentration_andForecastTopAssetsCap() {
        PortfolioResponse p = new PortfolioResponse();
        p.setId(pid);
        p.setName("Çeşitli");
        // 7 eşit-ağırlık pozisyon (her biri ~%14.3) → top<25 ve HHI~0.143<0.25 → "İyi çeşitlendirilmiş".
        // Ayrıca 7 > FORECAST_TOP_ASSETS(6) → forecast döngüsünde n>=6 break dalı (L645) çalışır.
        p.setHoldings(List.of(
                pos("A", AssetType.STOCK), pos("B", AssetType.CRYPTO), pos("C", AssetType.BOND),
                pos("D", AssetType.GOLD), pos("E", AssetType.FUND), pos("F", AssetType.FX),
                pos("G", AssetType.COMMODITY)));
        p.setTotalCost(new BigDecimal("7000"));
        p.setTotalMarketValue(new BigDecimal("7000"));
        p.setTotalProfitLoss(BigDecimal.ZERO);
        p.setTotalRealProfitLossPercent(new BigDecimal("0.0"));
        when(portfolioService.getPortfolioById(eq("u1"), eq(pid))).thenReturn(p);

        PortfolioAiAnalysisResult r = service.analyze("u1", pid, "Berkan", "b@x.com");

        assertThat(r.getHoldingsCount()).isEqualTo(7);
        assertThat(r.getConcentration().topHoldingPercent().doubleValue()).isLessThan(25.0);
        assertThat(r.getConcentration().label()).isEqualTo("İyi çeşitlendirilmiş");
        // Forecast yalnız ilk 6 varlığı işler (7. break ile atlanır).
        assertThat(r.getForecast().assetForecasts()).hasSize(6);
    }

    // ── 3) "Orta yoğunlaşma" etiketi (top %25–40 arası) ──

    @Test
    void analyze_mediumConcentrationLabel() {
        PortfolioResponse p = new PortfolioResponse();
        p.setId(pid);
        p.setName("Orta");
        // 30/30/20/20 → en büyük %30 (>=25, <40) → "Orta yoğunlaşma".
        p.setHoldings(List.of(
                bare("A", "A", AssetType.STOCK, "100", "3000"),
                bare("B", "B", AssetType.FUND, "100", "3000"),
                bare("C", "C", AssetType.BOND, "100", "2000"),
                bare("D", "D", AssetType.GOLD, "100", "2000")));
        p.setTotalCost(new BigDecimal("400"));
        p.setTotalMarketValue(new BigDecimal("10000"));
        p.setTotalProfitLoss(new BigDecimal("9600"));
        p.setTotalRealProfitLossPercent(new BigDecimal("0.0"));
        when(portfolioService.getPortfolioById(eq("u1"), eq(pid))).thenReturn(p);

        PortfolioAiAnalysisResult r = service.analyze("u1", pid, "Berkan", "b@x.com");

        assertThat(r.getConcentration().topHoldingPercent().doubleValue()).isBetween(25.0, 40.0);
        assertThat(r.getConcentration().label()).isEqualTo("Orta yoğunlaşma");
    }

    // ── 4) MA durumu "MA20 üstü, MA50 altı" + 52-hafta orta bant (üst yarı / alt yarı) ──

    @Test
    void analyze_priceAboveMa20BelowMa50_andMidBand52w() {
        PortfolioResponse p = new PortfolioResponse();
        p.setId(pid);
        p.setName("MA testi");

        // Stock1: fiyat MA20 üstünde ama MA50 ALTINDA → NEUTRAL "MA20 üstü, MA50 altı".
        // 52h konumu (160-100)/(200-100)=%60 → "bandının üst yarısında" (55<=pos<80).
        PortfolioHoldingResponse up20 = bare("S1", "Üst20", AssetType.STOCK, "1000", "5000");
        up20.setCurrentPrice(new BigDecimal("160"));
        up20.setMa20(new BigDecimal("150")); // fiyat >= MA20
        up20.setMa50(new BigDecimal("170")); // fiyat < MA50
        up20.setFiftyTwoWeekHigh(new BigDecimal("200"));
        up20.setFiftyTwoWeekLow(new BigDecimal("100"));
        up20.setReturnOneMonth(new BigDecimal("1.5"));

        // Stock2: 52h konumu (140-100)/(200-100)=%40 → "bandının alt yarısında" (20<pos<55). MA alanları yok.
        PortfolioHoldingResponse low = bare("S2", "Alt", AssetType.STOCK, "1000", "3000");
        low.setCurrentPrice(new BigDecimal("140"));
        low.setFiftyTwoWeekHigh(new BigDecimal("200"));
        low.setFiftyTwoWeekLow(new BigDecimal("100"));

        p.setHoldings(List.of(up20, low));
        p.setTotalCost(new BigDecimal("2000"));
        p.setTotalMarketValue(new BigDecimal("8000"));
        p.setTotalProfitLoss(new BigDecimal("6000"));
        p.setTotalRealProfitLossPercent(new BigDecimal("2.0"));
        when(portfolioService.getPortfolioById(eq("u1"), eq(pid))).thenReturn(p);

        PortfolioAiAnalysisResult r = service.analyze("u1", pid, "Berkan", "b@x.com");

        var s1 = signal(r, "S1");
        assertThat(s1.trend()).isEqualTo("NEUTRAL");
        assertThat(s1.maState()).isEqualTo("MA20 üstü, MA50 altı");
        assertThat(s1.note()).contains("52-hafta bandının üst yarısında");

        var s2 = signal(r, "S2");
        assertThat(s2.note()).contains("52-hafta bandının alt yarısında");
    }

    // ── 5) Kısa what-if geçmişi → risk metrikleri "yok" → forecast erken dönüş ──

    @Test
    void analyze_shortSeries_riskMetricsUnavailable_andForecastEarlyReturn() {
        PortfolioResponse p = samplePortfolio();
        when(portfolioService.getPortfolioById(eq("u1"), eq(pid))).thenReturn(p);
        // Yalnız 3 geçerli nokta → portRatio.size() < MIN_SAMPLES(5) → computeRiskMetrics erken döner.
        when(whatIfService.computeSeries(any(), any(), any(), any())).thenReturn(shortSeries());

        PortfolioAiAnalysisResult r = service.analyze("u1", pid, "Berkan", "b@x.com");

        // Risk metrikleri geçmiş kısa → available=false.
        assertThat(r.getRiskMetrics()).isNotNull();
        assertThat(r.getRiskMetrics().available()).isFalse();
        assertThat(r.getNotes()).anyMatch(n -> n.contains("geçmiş çok kısa"));
        // Metrik yok → forecast erken dönüş (available=false, ufuk/varlık listeleri boş).
        assertThat(r.getForecast()).isNotNull();
        assertThat(r.getForecast().available()).isFalse();
        assertThat(r.getForecast().portfolioHorizons()).isEmpty();
        assertThat(r.getForecast().assetForecasts()).isEmpty();
        // Skorlar yine hesaplanır (hasMetrics=false dalı).
        assertThat(r.getRiskScore()).isBetween(0, 100);
        assertThat(r.getHealthScore()).isBetween(0, 100);
    }

    // ── 6) Beta hesaplanamaz (benchmark serisi hizasız) + risksiz oran 0 (mevduat tek nokta) ──

    @Test
    void analyze_betaUnavailable_whenBenchmarkSeriesMisaligned() {
        PortfolioResponse p = samplePortfolio();
        when(portfolioService.getPortfolioById(eq("u1"), eq(pid))).thenReturn(p);
        // actual: 6 geçerli nokta (>=5) → metrik üretilir; bist: yalnız 2 nokta (hizasız → beta null);
        // deposit: yalnız 1 nokta (<2 → rf=0).
        when(whatIfService.computeSeries(any(), any(), any(), any())).thenReturn(misalignedSeries());

        PortfolioAiAnalysisResult r = service.analyze("u1", pid, "Berkan", "b@x.com");

        var m = r.getRiskMetrics();
        assertThat(m.available()).isTrue();
        assertThat(m.sampleMonths()).isGreaterThanOrEqualTo(5);
        // Beta hesaplanamadı (bist serisi port serisiyle aynı uzunlukta değil).
        assertThat(m.beta()).isNull();
        // Volatilite yine hesaplandı.
        assertThat(m.annualVolatilityPercent()).isNotNull();
    }

    // ── 7) rebalanceFor: TL'ye çevrilince null olan pozisyonu atlar ──

    @Test
    void rebalanceFor_skipsNullConvertedMarketValue() {
        PortfolioResponse p = new PortfolioResponse();
        p.setId(pid);
        PortfolioHoldingResponse nullMv = bare("NULLX", "Çevrilemez", AssetType.BOND, "100", "777");
        PortfolioHoldingResponse ok = bare("THYAO", "Türk Hava Yolları", AssetType.STOCK, "100", "500");
        p.setHoldings(List.of(nullMv, ok));
        when(portfolioService.getPortfolioById(eq("u1"), eq(pid))).thenReturn(p);
        // 777 → null TL (atlanır); diğerleri identity.
        when(currencyConverter.toTry(any(), any())).thenAnswer(inv -> {
            BigDecimal amt = inv.getArgument(0);
            if (amt != null && amt.compareTo(new BigDecimal("777")) == 0) {
                return null;
            }
            return amt;
        });

        var rb = service.rebalanceFor("u1", pid, "BALANCED");

        // Yalnız STOCK (500 TL) sayılır → BOND mevcut %0 kalır (null-TL pozisyonu atlandı).
        var stock = rb.items().stream().filter(i -> "STOCK".equals(i.assetType())).findFirst().orElseThrow();
        assertThat(stock.currentPercent().doubleValue()).isEqualTo(100.0);
    }

    // ── 8) analyze: sıfır piyasa-değerli pozisyonu atlar (signum<=0) ──

    @Test
    void analyze_skipsZeroMarketValuePosition() {
        PortfolioResponse p = new PortfolioResponse();
        p.setId(pid);
        p.setName("Sıfır dahil");
        PortfolioHoldingResponse zero = bare("ZERO", "Sıfır", AssetType.GOLD, "100", "0"); // mv=0 → signum<=0
        PortfolioHoldingResponse ok = bare("THYAO", "Türk Hava Yolları", AssetType.STOCK, "1000", "5000");
        p.setHoldings(List.of(zero, ok));
        p.setTotalCost(new BigDecimal("1100"));
        p.setTotalMarketValue(new BigDecimal("5000"));
        p.setTotalProfitLoss(new BigDecimal("3900"));
        p.setTotalRealProfitLossPercent(new BigDecimal("0.0"));
        when(portfolioService.getPortfolioById(eq("u1"), eq(pid))).thenReturn(p);

        PortfolioAiAnalysisResult r = service.analyze("u1", pid, "Berkan", "b@x.com");

        // Sıfır-değerli atlandı → yalnız 1 açık pozisyon.
        assertThat(r.getHoldingsCount()).isEqualTo(1);
        assertThat(r.getTotalValueTry()).isEqualByComparingTo("5000");
        assertThat(r.getAllocation()).extracting(AllocationSlice::label).doesNotContain("Sıfır");
    }

    // ── 9) profitPercent: piyasa değeri null → toplam getiri % null + holdings=null erken dönüş ──

    @Test
    void analyze_nullMarketValue_andNullHoldings_yieldsNullProfitPercent() {
        PortfolioResponse p = new PortfolioResponse();
        p.setId(pid);
        p.setName("Eksik");
        p.setHoldings(null); // holdings NULL → List.of()'a düşer (L137 dalı)
        p.setTotalCost(new BigDecimal("1000")); // > 0
        p.setTotalMarketValue(null);            // null → profitPercent null döner
        when(portfolioService.getPortfolioById(eq("u1"), eq(pid))).thenReturn(p);

        PortfolioAiAnalysisResult r = service.analyze("u1", pid, "Berkan", "b@x.com");

        // totalCost>0 ama piyasa değeri null → nominal getiri % hesaplanamadı.
        assertThat(r.getTotalProfitLossPercent()).isNull();
        // Pozisyon yok → erken dönüş.
        assertThat(r.getHoldingsCount()).isZero();
        assertThat(r.getRiskMetrics().available()).isFalse();
        assertThat(r.getClassification()).isNull();
    }

    // ── Yardımcılar ──────────────────────────────────────────────────────────────

    private static AssetSignal signal(PortfolioAiAnalysisResult r, String symbol) {
        return r.getAssetSignals().stream().filter(s -> symbol.equals(s.symbol())).findFirst().orElseThrow();
    }

    private PortfolioResponse samplePortfolio() {
        PortfolioResponse p = new PortfolioResponse();
        p.setId(pid);
        p.setName("Test Portföy");
        p.setHoldings(List.of(
                bare("THYAO", "Türk Hava Yolları", AssetType.STOCK, "4000", "5000"),
                bare("BTC", "Bitcoin", AssetType.CRYPTO, "3000", "2500"),
                bare("TRT", "Devlet Tahvili", AssetType.BOND, "2000", "2100")));
        p.setTotalCost(new BigDecimal("9000"));
        p.setTotalMarketValue(new BigDecimal("9600"));
        p.setTotalProfitLoss(new BigDecimal("600"));
        p.setTotalRealProfitLossPercent(new BigDecimal("-4.5"));
        return p;
    }

    /** profitLossPercent NULL bırakılmış basit holding (maliyetten türetme dalını açar). */
    private PortfolioHoldingResponse bare(String sym, String name, AssetType type, String cost, String mv) {
        PortfolioHoldingResponse h = new PortfolioHoldingResponse();
        h.setSymbol(sym);
        h.setName(name);
        h.setAssetType(type);
        h.setCurrency("TRY");
        h.setClosed(false);
        h.setTotalCost(new BigDecimal(cost));
        h.setMarketValue(new BigDecimal(mv));
        h.setProfitLoss(new BigDecimal(mv).subtract(new BigDecimal(cost)));
        // profitLossPercent KASITLI null.
        return h;
    }

    /** Eşit-ağırlık (1000 TL) pozisyon — 7'li çeşitlendirme testi için. */
    private PortfolioHoldingResponse pos(String sym, AssetType type) {
        return bare(sym, sym, type, "1000", "1000");
    }

    private static RiskMetrics unavailableMetrics() {
        return new RiskMetrics(null, null, null, null, null, null, 0, false, "hist yok");
    }

    /** 8 aylık seri (cost katkıyla artar, actual dalgalı) — varsayılan (metrik üretir). */
    private WhatIfSeriesResult eightPointSeries() {
        double[] cost   = {1000, 1000, 2000, 2000, 2000, 2000, 2000, 2000};
        double[] actual = {1000, 1100, 2100, 1950, 2200, 2400, 2300, 2600};
        double[] bist   = {1000, 1050, 2050, 2000, 2150, 2250, 2200, 2400};
        double[] dep    = {1000, 1010, 2025, 2040, 2055, 2070, 2085, 2100};
        List<WhatIfSeriesPoint> pts = new ArrayList<>();
        LocalDate d = LocalDate.now().minusMonths(7);
        for (int i = 0; i < cost.length; i++) {
            WhatIfSeriesPoint pt = new WhatIfSeriesPoint(d.plusMonths(i));
            pt.setCost(BigDecimal.valueOf(cost[i]));
            pt.setActual(BigDecimal.valueOf(actual[i]));
            pt.setBist100(BigDecimal.valueOf(bist[i]));
            pt.setDeposit(BigDecimal.valueOf(dep[i]));
            pts.add(pt);
        }
        WhatIfSeriesResult r = new WhatIfSeriesResult();
        r.setPoints(pts);
        return r;
    }

    /** Yalnız 3 geçerli (cost>0 & actual>0) nokta → MIN_SAMPLES(5) altında. */
    private WhatIfSeriesResult shortSeries() {
        List<WhatIfSeriesPoint> pts = new ArrayList<>();
        LocalDate d = LocalDate.now().minusMonths(2);
        double[] cost   = {1000, 1000, 1000};
        double[] actual = {1000, 1100, 1050};
        for (int i = 0; i < cost.length; i++) {
            WhatIfSeriesPoint pt = new WhatIfSeriesPoint(d.plusMonths(i));
            pt.setCost(BigDecimal.valueOf(cost[i]));
            pt.setActual(BigDecimal.valueOf(actual[i]));
            pts.add(pt);
        }
        WhatIfSeriesResult r = new WhatIfSeriesResult();
        r.setPoints(pts);
        return r;
    }

    /**
     * actual: 6 geçerli nokta (metrik üretilir); bist: yalnız ilk 2 noktada dolu (port serisiyle hizasız
     * → beta null); deposit: yalnız 1 noktada dolu (&lt;2 → risksiz oran 0).
     */
    private WhatIfSeriesResult misalignedSeries() {
        double[] cost   = {1000, 1000, 1000, 1000, 1000, 1000};
        double[] actual = {1000, 1100, 1050, 1200, 1150, 1300};
        List<WhatIfSeriesPoint> pts = new ArrayList<>();
        LocalDate d = LocalDate.now().minusMonths(5);
        for (int i = 0; i < cost.length; i++) {
            WhatIfSeriesPoint pt = new WhatIfSeriesPoint(d.plusMonths(i));
            pt.setCost(BigDecimal.valueOf(cost[i]));
            pt.setActual(BigDecimal.valueOf(actual[i]));
            if (i < 2) {
                pt.setBist100(BigDecimal.valueOf(1000 + i * 50)); // yalnız 2 bist noktası
            }
            if (i == 0) {
                pt.setDeposit(BigDecimal.valueOf(1000)); // yalnız 1 mevduat noktası
            }
            pts.add(pt);
        }
        WhatIfSeriesResult r = new WhatIfSeriesResult();
        r.setPoints(pts);
        return r;
    }
}
