package com.finance.portal.portfolio.service;

import com.finance.portal.common.domain.AssetType;
import com.finance.portal.portfolio.application.analysis.PortfolioAiAnalysisResult;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Ek kapsam: {@link PortfolioAnalysisService} içinde mevcut testin (PortfolioAnalysisServiceTest)
 * DOKUNMADIĞI dallar — rebalanceFor, boş/null portföy, tarihsel-risk MEVCUT yolu (what-if'i atlar),
 * stres testi degrade, korumacı sınıflandırma, varlık-sinyali MA trend (UP/DOWN/karışık) + 52h bandı,
 * null toTry atlama ve kapalı pozisyon atlama.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PortfolioAnalysisServiceMoreTest {

    @Mock private PortfolioService portfolioService;
    @Mock private PortfolioWhatIfService whatIfService;
    @Mock private PortfolioCurrencyConverter currencyConverter;
    @Mock private PortfolioStressTestService stressTestService;
    @Mock private PortfolioHistoricalRiskService historicalRiskService;
    @Mock private PortfolioAiNarrator narrator;

    // Gerçek servisler (dış bağımlılığı yok).
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
        when(narrator.generate(any(), any(), any(), any(), any())).thenReturn("Yorum.");
        when(stressTestService.compute(any())).thenReturn(List.of());
        when(whatIfService.computeSeries(any(), any(), any(), any())).thenReturn(sampleSeries());
        // Varsayılan tarihsel-risk "yok" (testler available metrik gerektiğinde override eder).
        when(historicalRiskService.computeFromHoldings(any(), anyInt())).thenReturn(unavailableMetrics());
    }

    // ── rebalanceFor ─────────────────────────────────────────────────────────────

    @Test
    void rebalanceFor_computesTypePercents_andUsesGivenProfile() {
        when(portfolioService.getPortfolioById(eq("u1"), eq(pid))).thenReturn(samplePortfolio());

        var rb = service.rebalanceFor("u1", pid, "CONSERVATIVE");

        assertThat(rb).isNotNull();
        assertThat(rb.basedOnProfile()).isEqualTo("CONSERVATIVE");
        assertThat(rb.items()).isNotEmpty();
        // STOCK mevcut (~52%) korumacı hedefin (10%) çok üstünde → AZALT.
        var stock = rb.items().stream().filter(i -> "STOCK".equals(i.assetType())).findFirst().orElseThrow();
        assertThat(stock.action()).isEqualTo("AZALT");
        assertThat(rb.driftPercent().doubleValue()).isGreaterThan(0);
    }

    @Test
    void rebalanceFor_nullPortfolio_returnsBalancedDefault_withEmptyCurrent() {
        when(portfolioService.getPortfolioById(eq("u1"), eq(pid))).thenReturn(null);

        // Bilinmeyen profil + boş holdings → rebalanceService BALANCED'a düşer.
        var rb = service.rebalanceFor("u1", pid, "WUT");

        assertThat(rb).isNotNull();
        assertThat(rb.basedOnProfile()).isEqualTo("BALANCED");
        // Mevcut hiç tip yok → hepsi hedefe doğru ARTIR; drift > 0.
        assertThat(rb.items()).isNotEmpty();
        assertThat(rb.driftPercent().doubleValue()).isGreaterThan(0);
    }

    @Test
    void rebalanceFor_skipsClosedAndNonPositivePositions() {
        PortfolioResponse p = new PortfolioResponse();
        p.setId(pid);
        PortfolioHoldingResponse closed = holding("OLD", "Kapalı", AssetType.BOND, "100", "100", "0", "0");
        closed.setClosed(true);
        PortfolioHoldingResponse zero = holding("ZERO", "Sıfır", AssetType.GOLD, "100", "0", "0", "0");
        PortfolioHoldingResponse ok = holding("THYAO", "Türk Hava Yolları", AssetType.STOCK, "100", "500", "400", "400");
        p.setHoldings(List.of(closed, zero, ok));
        when(portfolioService.getPortfolioById(eq("u1"), eq(pid))).thenReturn(p);

        var rb = service.rebalanceFor("u1", pid, "BALANCED");

        // Sadece STOCK (500 TL) sayılır → mevcut STOCK %100.
        var stock = rb.items().stream().filter(i -> "STOCK".equals(i.assetType())).findFirst().orElseThrow();
        assertThat(stock.currentPercent().doubleValue()).isEqualTo(100.0);
    }

    // ── analyze: boş / null portföy ──────────────────────────────────────────────

    @Test
    void analyze_emptyHoldings_returnsNoPositionResult() {
        PortfolioResponse p = new PortfolioResponse();
        p.setId(pid);
        p.setName("Boş");
        p.setHoldings(new ArrayList<>());
        when(portfolioService.getPortfolioById(eq("u1"), eq(pid))).thenReturn(p);

        PortfolioAiAnalysisResult r = service.analyze("u1", pid, "Berkan", "b@x.com");

        assertThat(r.getHoldingsCount()).isZero();
        assertThat(r.getTotalValueTry()).isEqualByComparingTo("0");
        assertThat(r.getRiskMetrics()).isNotNull();
        assertThat(r.getRiskMetrics().available()).isFalse();
        assertThat(r.getNotes()).anyMatch(n -> n.contains("açık pozisyon bulunamadı"));
        // Erken dönüş → skor/sınıflandırma hesaplanmaz.
        assertThat(r.getClassification()).isNull();
    }

    @Test
    void analyze_nullPortfolio_returnsNoPositionResult() {
        when(portfolioService.getPortfolioById(eq("u1"), eq(pid))).thenReturn(null);

        PortfolioAiAnalysisResult r = service.analyze("u1", pid, "Berkan", "b@x.com");

        assertThat(r.getName()).isNull();
        assertThat(r.getHoldingsCount()).isZero();
        assertThat(r.getRiskMetrics().available()).isFalse();
        assertThat(r.getTotalCostTry()).isNull();
    }

    @Test
    void analyze_skipsNullMarketValuePositions() {
        PortfolioResponse p = samplePortfolio();
        when(portfolioService.getPortfolioById(eq("u1"), eq(pid))).thenReturn(p);
        // marketValue → null TL (atlanır); diğer çağrılar (cost/pl) identity kalsın.
        when(currencyConverter.toTry(any(), any())).thenAnswer(inv -> {
            BigDecimal amt = inv.getArgument(0);
            if (amt != null && amt.compareTo(new BigDecimal("5000")) == 0) {
                return null; // THYAO mv → null → o pozisyon atlanır
            }
            return amt;
        });

        PortfolioAiAnalysisResult r = service.analyze("u1", pid, "Berkan", "b@x.com");

        // THYAO (mv 5000) atlandı → kalan 2 pozisyon (BTC mv 2500 + TRT mv 2100 = 4600).
        assertThat(r.getHoldingsCount()).isEqualTo(2);
        assertThat(r.getTotalValueTry()).isEqualByComparingTo("4600");
        assertThat(r.getAllocation()).extracting(PortfolioAiAnalysisResult.AllocationSlice::label)
                .doesNotContain("Türk Hava Yolları");
    }

    // ── analyze: tarihsel-risk MEVCUT yolu (what-if'i ATLAR) ─────────────────────

    @Test
    void analyze_historicalMetricsAvailable_skipsRatioSeries_butStillBuildsBenchmarks() {
        when(portfolioService.getPortfolioById(eq("u1"), eq(pid))).thenReturn(samplePortfolio());
        when(historicalRiskService.computeFromHoldings(any(), anyInt())).thenReturn(availableMetrics());

        PortfolioAiAnalysisResult r = service.analyze("u1", pid, "Berkan", "b@x.com");

        // computeFromHoldings AVAILABLE döndü → metrik onunla geldi (else-if dalı), what-if computeRiskMetrics atlandı.
        var m = r.getRiskMetrics();
        assertThat(m.available()).isTrue();
        assertThat(m.annualVolatilityPercent()).isEqualByComparingTo("20.00"); // availableMetrics değeri
        // Yine de TAM modda benchmark + değer serisi what-if'ten kurulur.
        assertThat(r.getBenchmarks()).extracting(PortfolioAiAnalysisResult.BenchmarkItem::key)
                .contains("bist100", "deposit");
        assertThat(r.getValueSeries()).isNotEmpty();
        // Metrik mevcut → forecast available.
        assertThat(r.getForecast().available()).isTrue();
    }

    @Test
    void analyze_lightMode_withHistoricalMetrics_noBenchmarksNoForecast() {
        when(portfolioService.getPortfolioById(eq("u1"), eq(pid))).thenReturn(samplePortfolio());
        when(historicalRiskService.computeFromHoldings(any(), anyInt())).thenReturn(availableMetrics());

        PortfolioAiAnalysisResult r = service.analyze("u1", pid, "Berkan", "b@x.com", false);

        // Hafif mod: historical metrik VAR → what-if hiç çağrılmaz; benchmark/değer serisi/forecast yok.
        assertThat(r.getRiskMetrics().available()).isTrue();
        assertThat(r.getBenchmarks()).isNull();
        assertThat(r.getValueSeries()).isNull();
        assertThat(r.getForecast()).isNull();
        assertThat(r.getMonteCarlo()).isNotNull();
        assertThat(r.getMonteCarlo().available()).isTrue(); // metrikler mevcut
    }

    // ── analyze: stres testi degrade ─────────────────────────────────────────────

    @Test
    void analyze_stressTestThrows_degradesToEmptyList() {
        when(portfolioService.getPortfolioById(eq("u1"), eq(pid))).thenReturn(samplePortfolio());
        when(stressTestService.compute(any())).thenThrow(new RuntimeException("stress boom"));

        PortfolioAiAnalysisResult r = service.analyze("u1", pid, "Berkan", "b@x.com");

        assertThat(r.getStressTests()).isNotNull().isEmpty();
        // Diğer her şey yine hesaplanır.
        assertThat(r.getRiskScore()).isBetween(0, 100);
    }

    // ── analyze: korumacı sınıflandırma + MA trend sinyalleri ────────────────────

    @Test
    void analyze_conservativePortfolio_withSignalTrends() {
        when(portfolioService.getPortfolioById(eq("u1"), eq(pid))).thenReturn(defensivePortfolio());

        PortfolioAiAnalysisResult r = service.analyze("u1", pid, "Berkan", "b@x.com");

        // BOND %60 + GOLD %40 → defensive baskın → CONSERVATIVE (riskScore düşük de olur).
        assertThat(r.getClassification().profile()).isEqualTo("CONSERVATIVE");
        assertThat(r.getClassification().defensiveWeightPercent().doubleValue())
                .isGreaterThan(r.getClassification().growthWeightPercent().doubleValue());

        // Varlık sinyalleri: BOND fiyat MA20/MA50 üstünde → UP; GOLD fiyat MA20/MA50 altında → DOWN.
        var signals = r.getAssetSignals();
        assertThat(signals).hasSize(2);
        var bond = signals.stream().filter(s -> "TRT".equals(s.symbol())).findFirst().orElseThrow();
        assertThat(bond.trend()).isEqualTo("UP");
        assertThat(bond.maState()).isEqualTo("Fiyat MA20 ve MA50 üstünde");
        assertThat(bond.range52wPercent()).isNotNull(); // 52h bandı dolduruldu (zirveye yakın)
        assertThat(bond.note()).contains("52-hafta zirvesine yakın");

        var gold = signals.stream().filter(s -> "XAU".equals(s.symbol())).findFirst().orElseThrow();
        assertThat(gold.trend()).isEqualTo("DOWN");
        assertThat(gold.maState()).isEqualTo("Fiyat MA20 ve MA50 altında");
    }

    @Test
    void analyze_mixedMaSignal_yieldsNeutralTrend() {
        PortfolioResponse p = new PortfolioResponse();
        p.setId(pid);
        // Fiyat MA50 üstünde ama MA20 altında → NEUTRAL ("MA50 üstü, MA20 altı").
        PortfolioHoldingResponse h = holding("ABC", "Karışık", AssetType.STOCK, "100", "500", "400", "400");
        h.setCurrentPrice(new BigDecimal("105"));
        h.setMa20(new BigDecimal("110")); // fiyat < MA20
        h.setMa50(new BigDecimal("100")); // fiyat >= MA50
        h.setFiftyTwoWeekHigh(new BigDecimal("200"));
        h.setFiftyTwoWeekLow(new BigDecimal("100"));
        h.setReturnOneMonth(new BigDecimal("3.21"));
        p.setHoldings(List.of(h));
        p.setTotalCost(new BigDecimal("100"));
        p.setTotalMarketValue(new BigDecimal("500"));
        when(portfolioService.getPortfolioById(eq("u1"), eq(pid))).thenReturn(p);

        PortfolioAiAnalysisResult r = service.analyze("u1", pid, "Berkan", "b@x.com");

        var sig = r.getAssetSignals().get(0);
        assertThat(sig.trend()).isEqualTo("NEUTRAL");
        assertThat(sig.maState()).isEqualTo("MA50 üstü, MA20 altı");
        // 52h: (105-100)/(200-100)=5% → dibe yakın; momentum1m notu var.
        assertThat(sig.note()).contains("son 1 ay %");
    }

    // ── Örnek veri ──────────────────────────────────────────────────────────────

    private PortfolioResponse samplePortfolio() {
        PortfolioResponse p = new PortfolioResponse();
        p.setId(pid);
        p.setName("Test Portföy");
        p.setHoldings(List.of(
                holding("THYAO", "Türk Hava Yolları", AssetType.STOCK, "4000", "5000", "1000", "25"),
                holding("BTC", "Bitcoin", AssetType.CRYPTO, "3000", "2500", "-500", "-16.67"),
                holding("TRT", "Devlet Tahvili", AssetType.BOND, "2000", "2100", "100", "5")));
        p.setTotalCost(new BigDecimal("9000"));
        p.setTotalMarketValue(new BigDecimal("9600"));
        p.setTotalProfitLoss(new BigDecimal("600"));
        p.setTotalRealProfitLossPercent(new BigDecimal("-4.5"));
        return p;
    }

    /** Korumacı dağılım: BOND %60 + GOLD %40 + MA/52h alanları (trend UP/DOWN üretsin). */
    private PortfolioResponse defensivePortfolio() {
        PortfolioResponse p = new PortfolioResponse();
        p.setId(pid);
        p.setName("Korumacı");

        PortfolioHoldingResponse bond = holding("TRT", "Devlet Tahvili", AssetType.BOND, "5000", "6000", "1000", "20");
        bond.setCurrentPrice(new BigDecimal("120"));
        bond.setMa20(new BigDecimal("110"));
        bond.setMa50(new BigDecimal("100")); // fiyat her ikisinin üstünde → UP
        bond.setFiftyTwoWeekHigh(new BigDecimal("125"));
        bond.setFiftyTwoWeekLow(new BigDecimal("80")); // (120-80)/(125-80)=~88% → zirveye yakın
        bond.setReturnOneMonth(new BigDecimal("2.5"));

        PortfolioHoldingResponse gold = holding("XAU", "Altın", AssetType.GOLD, "4000", "4000", "0", "0");
        gold.setCurrentPrice(new BigDecimal("90"));
        gold.setMa20(new BigDecimal("100"));
        gold.setMa50(new BigDecimal("110")); // fiyat her ikisinin altında → DOWN
        gold.setFiftyTwoWeekHigh(new BigDecimal("150"));
        gold.setFiftyTwoWeekLow(new BigDecimal("85")); // (90-85)/(150-85)=~8% → dibe yakın

        p.setHoldings(List.of(bond, gold));
        p.setTotalCost(new BigDecimal("9000"));
        p.setTotalMarketValue(new BigDecimal("10000"));
        p.setTotalProfitLoss(new BigDecimal("1000"));
        p.setTotalRealProfitLossPercent(new BigDecimal("3.0"));
        return p;
    }

    private PortfolioHoldingResponse holding(String sym, String name, AssetType type,
                                             String cost, String mv, String pl, String plPct) {
        PortfolioHoldingResponse h = new PortfolioHoldingResponse();
        h.setSymbol(sym);
        h.setName(name);
        h.setAssetType(type);
        h.setCurrency("TRY");
        h.setClosed(false);
        h.setTotalCost(new BigDecimal(cost));
        h.setMarketValue(new BigDecimal(mv));
        h.setProfitLoss(new BigDecimal(pl));
        h.setProfitLossPercent(new BigDecimal(plPct));
        return h;
    }

    private static RiskMetrics unavailableMetrics() {
        return new RiskMetrics(null, null, null, null, null, null, 0, false, "hist yok");
    }

    /** Tarihsel risk servisi AVAILABLE metrik (what-if oran serisi yolunu atlatmak için). */
    private static RiskMetrics availableMetrics() {
        return new RiskMetrics(new BigDecimal("18.00"), new BigDecimal("20.00"),
                new BigDecimal("0.80"), new BigDecimal("1.10"),
                new BigDecimal("12.00"), new BigDecimal("0.95"), 12, true, "hist var");
    }

    /** 8 aylık what-if serisi (benchmark + değer serisi kaynak). */
    private WhatIfSeriesResult sampleSeries() {
        double[] cost   = {1000, 1000, 2000, 2000, 2000, 2000, 2000, 2000};
        double[] actual = {1000, 1100, 2100, 1950, 2200, 2400, 2300, 2600};
        double[] bist   = {1000, 1050, 2050, 2000, 2150, 2250, 2200, 2400};
        double[] dep    = {1000, 1010, 2025, 2040, 2055, 2070, 2085, 2100};
        List<WhatIfSeriesPoint> pts = new ArrayList<>();
        java.time.LocalDate d = java.time.LocalDate.now().minusMonths(7);
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
}
