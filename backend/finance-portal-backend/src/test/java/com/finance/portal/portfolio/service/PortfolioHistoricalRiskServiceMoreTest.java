package com.finance.portal.portfolio.service;

import com.finance.portal.common.domain.AssetType;
import com.finance.portal.portfolio.application.analysis.PortfolioAiAnalysisResult.RiskMetrics;
import com.finance.portal.portfolio.application.port.PortfolioHistoricalPricePort;
import com.finance.portal.portfolio.service.PortfolioHistoricalRiskService.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Branş-kapsamını artıran ek testler — {@link PortfolioHistoricalRiskServiceTest}'in DEĞMEDİĞİ kollar:
 * giriş guard'ları (null/boş pozisyon, months&lt;2), yetersiz örnek (MIN_SAMPLES altı), ay kapsama atlama
 * (MIN_COVERAGE altı), sıfır-volatilite Sharpe/Sortino kolları, beta null (varB≤0 ve pPairs&lt;MIN_SAMPLES)
 * ile beta hesaplanan (BIST stub'lı) yol ve null/boş fiyat serisi atlama.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PortfolioHistoricalRiskServiceMoreTest {

    @Mock private PortfolioHistoricalPricePort pricePort;
    @InjectMocks private PortfolioHistoricalRiskService service;

    // ── Giriş guard'ları → unavailable(0, "Pozisyon yok") ────────────────────────

    @Test
    void compute_nullPositions_unavailable() {
        RiskMetrics m = service.computeFromHoldings(null, 12);

        assertThat(m.available()).isFalse();
        assertThat(m.sampleMonths()).isZero();
        assertThat(m.annualReturnPercent()).isNull();
        assertThat(m.beta()).isNull();
        assertThat(m.note()).isEqualTo("Pozisyon yok");
    }

    @Test
    void compute_emptyPositions_unavailable() {
        RiskMetrics m = service.computeFromHoldings(Collections.emptyList(), 12);

        assertThat(m.available()).isFalse();
        assertThat(m.note()).isEqualTo("Pozisyon yok");
    }

    @Test
    void compute_monthsBelowTwo_unavailable() {
        // months < 2 → pencere kurulamaz → guard.
        RiskMetrics m = service.computeFromHoldings(
                List.of(new Position(AssetType.STOCK, "AAA.IS", 1.0)), 1);

        assertThat(m.available()).isFalse();
        assertThat(m.sampleMonths()).isZero();
        assertThat(m.note()).isEqualTo("Pozisyon yok");
    }

    // ── Örnek/kapsama yetersizliği ───────────────────────────────────────────────

    @Test
    void compute_emptySeries_returnsZeroArray_unavailable() {
        // Boş NavigableMap → sampleAt isEmpty kolu → hepsi 0 → her ay kapsamasız → 0 örnek.
        when(pricePort.fetchDailyClosePrices(eq(AssetType.GOLD), eq("XAU"), any(), any()))
                .thenReturn(Optional.of(new TreeMap<>()));

        RiskMetrics m = service.computeFromHoldings(
                List.of(new Position(AssetType.GOLD, "XAU", 1.0)), 12);

        assertThat(m.available()).isFalse();
        assertThat(m.sampleMonths()).isZero();
        assertThat(m.note()).contains("yetersiz");
    }

    @Test
    void compute_fewValidMonths_coverageSkip_unavailableWithSampleCount() {
        // Yalnız ilk 4 ay pozitif (3 geçerli getiri), kalan aylar 0 → o aylar MIN_COVERAGE altı atlanır.
        // portRet.size()=3 < MIN_SAMPLES(5) → unavailable ama sampleMonths bu sayıyı taşır.
        double[] prices = {100, 105, 110, 108, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        stub(AssetType.STOCK, "PART.IS", prices);

        RiskMetrics m = service.computeFromHoldings(
                List.of(new Position(AssetType.STOCK, "PART.IS", 1.0)), 12);

        assertThat(m.available()).isFalse();
        assertThat(m.sampleMonths()).isEqualTo(3);
        assertThat(m.note()).contains("yetersiz");
    }

    @Test
    void compute_mixedCoverageMonths_skipsLowCoverageMonth() {
        // İki eşit-ağırlıklı varlık. B varlığı sadece tek ayda 0 fiyatlanır → o ayda kapsama 0.5'e düşer
        // (sadece A → 0.5 ≥ MIN_COVERAGE, sınırda dahil). Geri kalan aylarda her ikisi de geçerli.
        // Asıl amaç: bazı aylarda tek-varlık kapsaması, bazılarında çift → covered>=MIN_COVERAGE her arm.
        stub(AssetType.STOCK, "AAA.IS",
                new double[]{100, 102, 104, 103, 106, 108, 107, 110, 112, 111, 114, 116, 120});
        // BBB ortada bir ay 0 (kapsama kaybı) ama diğer aylarda mevcut.
        stub(AssetType.CRYPTO, "BBB",
                new double[]{200, 204, 0, 210, 214, 212, 218, 222, 220, 226, 230, 228, 240});

        RiskMetrics m = service.computeFromHoldings(List.of(
                new Position(AssetType.STOCK, "AAA.IS", 0.5),
                new Position(AssetType.CRYPTO, "BBB", 0.5)), 12);

        assertThat(m.available()).isTrue();
        assertThat(m.sampleMonths()).isEqualTo(12); // tüm aylar ≥ MIN_COVERAGE kaldı
    }

    // ── Sıfır volatilite: Sharpe & Sortino zero arm + beta null (BIST yok) ───────

    @Test
    void compute_flatReturns_zeroVolatility_sharpeSortinoZero_betaNull() {
        // Tüm fiyatlar eşit → her aylık getiri 0 → stddev 0 → annualVol≈0 → sharpe=0 kolu;
        // negatif getiri yok → downside 0 → sortino=0 kolu. BIST stub'lı değil → beta null.
        stub(AssetType.FUND, "FLAT.FN",
                new double[]{100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100});

        RiskMetrics m = service.computeFromHoldings(
                List.of(new Position(AssetType.FUND, "FLAT.FN", 1.0)), 12);

        assertThat(m.available()).isTrue();
        assertThat(m.sampleMonths()).isEqualTo(12);
        assertThat(m.sharpe()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(m.sortino()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(m.annualVolatilityPercent()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(m.maxDrawdownPercent()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(m.beta()).isNull();
    }

    // ── beta null: pPairs ≥ MIN_SAMPLES ama varB ≤ 0 (sabit BIST getirisi) ───────

    @Test
    void compute_constantBistReturns_zeroVariance_betaNull() {
        // Portföy değişken (vol>0) ama BIST düz → bPairs varyans 0 → varB ≤ 1e-12 → beta null.
        stub(AssetType.COMMODITY, "CL=F",
                new double[]{100, 106, 103, 112, 109, 118, 124, 121, 130, 137, 133, 145, 156});
        stub(AssetType.STOCK, "XU100.IS",
                new double[]{1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000});

        RiskMetrics m = service.computeFromHoldings(
                List.of(new Position(AssetType.COMMODITY, "CL=F", 1.0)), 12);

        assertThat(m.available()).isTrue();
        assertThat(m.annualVolatilityPercent().doubleValue()).isGreaterThan(0);
        assertThat(m.beta()).isNull(); // varB ≤ 0 → beta atanmadı
    }

    // ── beta hesaplanan yol: BIST değişken → varB > 0 → beta non-null ────────────

    @Test
    void compute_variableBist_betaComputed() {
        // Hem portföy hem BIST değişken & yeterli geçerli ay → beta hesaplanır (non-null).
        stub(AssetType.STOCK, "AAA.IS",
                new double[]{100, 110, 105, 120, 112, 130, 125, 140, 133, 150, 145, 160, 170});
        stub(AssetType.STOCK, "XU100.IS",
                new double[]{1000, 1080, 1040, 1150, 1090, 1220, 1180, 1300, 1250, 1380, 1330, 1450, 1520});

        RiskMetrics m = service.computeFromHoldings(
                List.of(new Position(AssetType.STOCK, "AAA.IS", 1.0)), 12);

        assertThat(m.available()).isTrue();
        assertThat(m.beta()).isNotNull();
    }

    /** 13 elemanlı {@code prices} dizisini 12..0 ay önceki anchor tarihlerine eşler (0 fiyat → o ay dışlanır). */
    private void stub(AssetType type, String symbol, double[] prices) {
        when(pricePort.fetchDailyClosePrices(eq(type), eq(symbol), any(), any())).thenAnswer(inv -> {
            TreeMap<LocalDate, BigDecimal> m = new TreeMap<>();
            LocalDate today = LocalDate.now();
            for (int i = 0; i <= 12; i++) {
                m.put(today.minusMonths(12 - i), BigDecimal.valueOf(prices[i]));
            }
            return Optional.of(m);
        });
    }
}
