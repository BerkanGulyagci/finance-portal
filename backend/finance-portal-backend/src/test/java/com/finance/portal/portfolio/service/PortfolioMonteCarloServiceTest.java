package com.finance.portal.portfolio.service;

import com.finance.portal.portfolio.application.analysis.PortfolioAiAnalysisResult.MonteCarlo;
import com.finance.portal.portfolio.application.analysis.PortfolioAiAnalysisResult.ProjectionPoint;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

/**
 * Lognormal/GBM projeksiyonunun kapalı-form matematiğini doğrular (deterministik, RNG yok):
 * yüzdelik bantların sıralaması, medyan = S0·exp((μ−σ²/2)·T), kayıp olasılığı ve uç-durum davranışı.
 */
class PortfolioMonteCarloServiceTest {

    private final PortfolioMonteCarloService service = new PortfolioMonteCarloService();

    @Test
    void project_validInputs_producesOrderedBandsAndMedian() {
        // s0=10000, μ=%20, σ=%30, 12 ay (T=1). drift = 0.2 − 0.5·0.09 = 0.155.
        MonteCarlo mc = service.project(new BigDecimal("10000"),
                new BigDecimal("20"), new BigDecimal("30"), 12);

        assertThat(mc.available()).isTrue();
        assertThat(mc.bands()).hasSize(13); // 0..12
        // Ay 0: tüm yüzdelikler başlangıç değeri
        ProjectionPoint p0 = mc.bands().get(0);
        assertThat(p0.p5()).isEqualByComparingTo("10000");
        assertThat(p0.p50()).isEqualByComparingTo("10000");
        assertThat(p0.p95()).isEqualByComparingTo("10000");

        // Son ay: p5 < p25 < p50 < p75 < p95
        ProjectionPoint last = mc.bands().get(12);
        assertThat(last.p5().doubleValue()).isLessThan(last.p25().doubleValue());
        assertThat(last.p25().doubleValue()).isLessThan(last.p50().doubleValue());
        assertThat(last.p50().doubleValue()).isLessThan(last.p75().doubleValue());
        assertThat(last.p75().doubleValue()).isLessThan(last.p95().doubleValue());

        // Medyan ≈ 10000·exp(0.155) ≈ 11676.6
        assertThat(mc.medianEndValue().doubleValue()).isCloseTo(11676.6, offset(5.0));
        // Kayıp olasılığı ≈ Φ(−0.155/0.30) ≈ %30.3
        assertThat(mc.probLossPercent().doubleValue()).isCloseTo(30.3, offset(1.5));
        assertThat(mc.annualReturnPercent()).isEqualByComparingTo("20");
        assertThat(mc.annualVolatilityPercent()).isEqualByComparingTo("30");
    }

    @Test
    void project_bandsWidenWithHorizon() {
        MonteCarlo mc = service.project(new BigDecimal("10000"),
                new BigDecimal("10"), new BigDecimal("25"), 12);
        double width1 = mc.bands().get(1).p95().doubleValue() - mc.bands().get(1).p5().doubleValue();
        double width12 = mc.bands().get(12).p95().doubleValue() - mc.bands().get(12).p5().doubleValue();
        assertThat(width12).isGreaterThan(width1); // belirsizlik ufukla büyür
    }

    @Test
    void project_extremeDrift_isClampedWithNote() {
        // μ=%300 → makul banda (%100) kıstlanır, not düşülür.
        MonteCarlo mc = service.project(new BigDecimal("5000"),
                new BigDecimal("300"), new BigDecimal("40"), 12);
        assertThat(mc.available()).isTrue();
        assertThat(mc.annualReturnPercent()).isEqualByComparingTo("100");
        assertThat(mc.note()).contains("kıstlandı");
    }

    @Test
    void project_missingMetrics_unavailable() {
        MonteCarlo mc = service.project(new BigDecimal("10000"), null, new BigDecimal("30"), 12);
        assertThat(mc.available()).isFalse();
        assertThat(mc.bands()).isEmpty();
        assertThat(mc.medianEndValue()).isNull();
    }

    @Test
    void project_zeroStartValue_unavailable() {
        MonteCarlo mc = service.project(BigDecimal.ZERO, new BigDecimal("10"), new BigDecimal("20"), 12);
        assertThat(mc.available()).isFalse();
    }

    @Test
    void project_zeroVolatility_usesFloor_stillProducesBands() {
        MonteCarlo mc = service.project(new BigDecimal("10000"),
                new BigDecimal("10"), BigDecimal.ZERO, 12);
        assertThat(mc.available()).isTrue();
        // MIN_SIGMA tabanı → bant genişliği > 0 (dejenere değil)
        ProjectionPoint last = mc.bands().get(12);
        assertThat(last.p95().doubleValue()).isGreaterThan(last.p5().doubleValue());
    }
}
