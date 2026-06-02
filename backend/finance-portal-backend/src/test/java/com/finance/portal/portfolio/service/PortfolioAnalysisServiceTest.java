package com.finance.portal.portfolio.service;

import com.finance.portal.common.domain.AssetType;
import com.finance.portal.portfolio.application.analysis.PortfolioAiAnalysisResult;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Metrik motorunun DETERMİNİSTİK hesaplarını doğrular (mock'larla, canlı veri/LLM olmadan):
 * allocation/yoğunlaşma/getiri + actual/cost oran serisinden volatilite/Sharpe/drawdown + skorlar.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PortfolioAnalysisServiceTest {

    @Mock private PortfolioService portfolioService;
    @Mock private PortfolioWhatIfService whatIfService;
    @Mock private PortfolioCurrencyConverter currencyConverter;
    @Mock private PortfolioStressTestService stressTestService;
    @Mock private PortfolioAiNarrator narrator;

    // Gerçek MC servisi (dış bağımlılığı yok) — projeksiyon entegrasyonunu da doğrular.
    private final PortfolioMonteCarloService monteCarloService = new PortfolioMonteCarloService();

    private PortfolioAnalysisService service;

    private final UUID pid = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new PortfolioAnalysisService(portfolioService, whatIfService, currencyConverter,
                stressTestService, monteCarloService, narrator);
        // toTry: TRY varsayımı (identity).
        when(currencyConverter.toTry(any(), any())).thenAnswer(inv -> inv.getArgument(0));
        when(narrator.generate(any(), any(), any(), any())).thenReturn("Test yorum raporu.");
        when(stressTestService.compute(any())).thenReturn(java.util.List.of());
        when(portfolioService.getPortfolioById(eq("u1"), eq(pid))).thenReturn(samplePortfolio());
        when(whatIfService.computeSeries(any(), any(), any(), any())).thenReturn(sampleSeries());
    }

    @Test
    void analyze_computesAllocationConcentrationReturns() {
        PortfolioAiAnalysisResult r = service.analyze("u1", pid, "Berkan", "b@x.com");

        // 3 açık pozisyon, toplam değer 9600 (5000+2500+2100)
        assertThat(r.getHoldingsCount()).isEqualTo(3);
        assertThat(r.getTotalValueTry()).isEqualByComparingTo("9600");

        // Allocation ağırlıkları ~100'e toplanır, en büyük THYAO (~52%)
        double sumW = r.getAllocation().stream().mapToDouble(a -> a.weightPercent().doubleValue()).sum();
        assertThat(sumW).isCloseTo(100.0, org.assertj.core.data.Offset.offset(0.5));
        assertThat(r.getAllocation().get(0).label()).isEqualTo("Türk Hava Yolları");
        assertThat(r.getConcentration().topHoldingPercent().doubleValue()).isBetween(50.0, 54.0);

        // En çok kazandıran THYAO (+25), kaybettiren BTC (-16.67)
        assertThat(r.getTopGainers()).isNotEmpty();
        assertThat(r.getTopGainers().get(0).symbol()).isEqualTo("THYAO");
        assertThat(r.getTopLosers().get(0).symbol()).isEqualTo("BTC");
    }

    @Test
    void analyze_computesRiskMetricsFromRatioSeries() {
        PortfolioAiAnalysisResult r = service.analyze("u1", pid, "Berkan", "b@x.com");
        var m = r.getRiskMetrics();

        assertThat(m.available()).isTrue();
        assertThat(m.annualVolatilityPercent()).isNotNull();
        assertThat(m.annualVolatilityPercent().doubleValue()).isGreaterThan(0);
        assertThat(m.maxDrawdownPercent().doubleValue()).isGreaterThan(0); // seride düşüş var
        assertThat(m.sharpe()).isNotNull();
        assertThat(m.sampleMonths()).isGreaterThanOrEqualTo(5);
    }

    @Test
    void analyze_scoresInRange_andBenchmarksAndAiReportPresent() {
        PortfolioAiAnalysisResult r = service.analyze("u1", pid, "Berkan", "b@x.com");

        assertThat(r.getRiskScore()).isBetween(0, 100);
        assertThat(r.getHealthScore()).isBetween(0, 100);
        assertThat(r.getRiskLabel()).isNotBlank();
        assertThat(r.getRiskFactors()).isNotEmpty();

        // Benchmark: en az BIST100 + Mevduat (sample seride var)
        assertThat(r.getBenchmarks()).extracting(PortfolioAiAnalysisResult.BenchmarkItem::key)
                .contains("bist100", "deposit");

        // AI rapor mock'tan geldi
        assertThat(r.isAiReportAvailable()).isTrue();
        assertThat(r.getAiReport()).isEqualTo("Test yorum raporu.");
    }

    @Test
    void analyze_classifiesProfile_buildsAssetSignals_andMonteCarlo() {
        PortfolioAiAnalysisResult r = service.analyze("u1", pid, "Berkan", "b@x.com");

        // Sınıflandırma: STOCK ~52% + CRYPTO ~26% = ~78% büyüme → AGRESİF
        assertThat(r.getClassification()).isNotNull();
        assertThat(r.getClassification().profile()).isEqualTo("AGGRESSIVE");
        assertThat(r.getClassification().growthWeightPercent().doubleValue())
                .isGreaterThan(r.getClassification().defensiveWeightPercent().doubleValue());

        // Varlık sinyalleri: 3 varlık, ağırlığa göre sıralı (THYAO ilk), her birinin notu dolu
        assertThat(r.getAssetSignals()).hasSize(3);
        assertThat(r.getAssetSignals().get(0).symbol()).isEqualTo("THYAO");
        assertThat(r.getAssetSignals().get(0).note()).isNotBlank();

        // Monte Carlo: risk metrikleri mevcut → projeksiyon mevcut, %95 > %5 ve bantlar dolu
        var mc = r.getMonteCarlo();
        assertThat(mc.available()).isTrue();
        assertThat(mc.bands()).isNotEmpty();
        assertThat(mc.medianEndValue()).isNotNull();
        assertThat(mc.p95EndValue().doubleValue()).isGreaterThan(mc.p5EndValue().doubleValue());
    }

    @Test
    void analyze_aiNarratorFailure_degradesGracefully() {
        when(narrator.generate(any(), any(), any(), any())).thenThrow(new RuntimeException("LLM down"));
        PortfolioAiAnalysisResult r = service.analyze("u1", pid, "Berkan", "b@x.com");

        assertThat(r.isAiReportAvailable()).isFalse();
        assertThat(r.getAiReport()).isNull();
        // Metrikler yine hesaplanmış olmalı
        assertThat(r.getRiskScore()).isBetween(0, 100);
        assertThat(r.getRiskMetrics().available()).isTrue();
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

    /** 8 aylık seri: cost ayda artar (katkı), actual dalgalı (volatilite + düşüş), bist + mevduat. */
    private WhatIfSeriesResult sampleSeries() {
        double[] cost   = {1000, 1000, 2000, 2000, 2000, 2000, 2000, 2000};
        double[] actual = {1000, 1100, 2100, 1950, 2200, 2400, 2300, 2600};
        double[] bist   = {1000, 1050, 2050, 2000, 2150, 2250, 2200, 2400};
        double[] dep    = {1000, 1010, 2025, 2040, 2055, 2070, 2085, 2100};
        List<WhatIfSeriesPoint> pts = new ArrayList<>();
        LocalDate d = LocalDate.now().minusMonths(7);
        for (int i = 0; i < cost.length; i++) {
            WhatIfSeriesPoint p = new WhatIfSeriesPoint(d.plusMonths(i));
            p.setCost(BigDecimal.valueOf(cost[i]));
            p.setActual(BigDecimal.valueOf(actual[i]));
            p.setBist100(BigDecimal.valueOf(bist[i]));
            p.setDeposit(BigDecimal.valueOf(dep[i]));
            pts.add(p);
        }
        WhatIfSeriesResult r = new WhatIfSeriesResult();
        r.setPoints(pts);
        return r;
    }
}
