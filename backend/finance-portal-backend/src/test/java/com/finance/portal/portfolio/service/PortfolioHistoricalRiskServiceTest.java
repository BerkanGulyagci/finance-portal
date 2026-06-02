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
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Risk metriklerinin varlıkların KENDİ fiyat geçmişinden (kullanıcı elde-tutma süresinden bağımsız)
 * hesaplandığını doğrular — yeni portföyde bile metrik üretmeli.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PortfolioHistoricalRiskServiceTest {

    @Mock private PortfolioHistoricalPricePort pricePort;
    @InjectMocks private PortfolioHistoricalRiskService service;

    @Test
    void compute_singleAsset_producesMetricsFromPriceHistory() {
        // 13 anchor fiyatı (12 ay önce → bugün), değişken aylık getiri → vol > 0, net yükseliş 100→150.
        stub(AssetType.COMMODITY, "CL=F",
                new double[]{100, 105, 103, 110, 108, 115, 120, 118, 125, 130, 128, 140, 150});

        RiskMetrics m = service.computeFromHoldings(
                List.of(new Position(AssetType.COMMODITY, "CL=F", 1.0)), 12);

        assertThat(m.available()).isTrue();
        assertThat(m.sampleMonths()).isEqualTo(12);
        assertThat(m.annualReturnPercent().doubleValue()).isGreaterThan(0);   // 100→150
        assertThat(m.annualVolatilityPercent().doubleValue()).isGreaterThan(0); // değişken getiri
        assertThat(m.maxDrawdownPercent().doubleValue()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void compute_noPriceData_unavailable() {
        // Hiçbir fiyat stub'lanmadı → Optional.empty → sıfır kapsama → metrik üretilemez.
        RiskMetrics m = service.computeFromHoldings(
                List.of(new Position(AssetType.STOCK, "ZZZ.IS", 1.0)), 12);

        assertThat(m.available()).isFalse();
        assertThat(m.annualVolatilityPercent()).isNull();
    }

    @Test
    void compute_partialCoverage_usesCoveredAsset() {
        // Ağırlığın %70'i fiyatı olan varlık, %30'u veri yok → kapsama 0.70 ≥ %50 → metrik üretilir.
        stub(AssetType.STOCK, "AAA.IS",
                new double[]{100, 102, 101, 104, 106, 105, 108, 110, 109, 112, 115, 117, 120});

        RiskMetrics m = service.computeFromHoldings(List.of(
                new Position(AssetType.STOCK, "AAA.IS", 0.70),
                new Position(AssetType.CRYPTO, "NODATA", 0.30)), 12);

        assertThat(m.available()).isTrue();
        assertThat(m.annualReturnPercent().doubleValue()).isGreaterThan(0);
    }

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
