package com.finance.portal.portfolio.service;

import com.finance.portal.common.domain.AssetType;
import com.finance.portal.portfolio.application.analysis.PortfolioAiAnalysisResult.StressTest;
import com.finance.portal.portfolio.application.port.PortfolioHistoricalPricePort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Stres testi proxy-ağırlıklı matematiğini doğrular (mock fiyat port'u ile):
 * her krizde portföy etkisi = Σ tip-ağırlığı × (proxy faktörü − 1).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PortfolioStressTestServiceTest {

    @Mock private PortfolioHistoricalPricePort pricePort;
    @InjectMocks private PortfolioStressTestService service;

    @Test
    void compute_weightsProxyFactorsPerCrisis() {
        // Hisse proxy (BIST100): 100 → 70 (faktör 0.7, %30 düştü)
        stub(AssetType.STOCK, "XU100.IS", 100, 70);
        // Döviz proxy (USD): 100 → 140 (faktör 1.4, TL düşünce USD kazandırır)
        stub(AssetType.FX, "USD", 100, 140);

        Map<String, Double> weights = Map.of("STOCK", 0.6, "FX", 0.4);
        var out = service.compute(weights);

        assertThat(out).hasSize(4); // 2008 / 2018 / 2020 / 2022
        // Her kriz: 0.6×(0.7−1) + 0.4×(1.4−1) = −0.18 + 0.16 = −0.02 → %−2
        for (StressTest st : out) {
            assertThat(st.available()).isTrue();
            assertThat(st.impactPercent().doubleValue()).isCloseTo(-2.0, offset(0.1));
        }
    }

    @Test
    void compute_cryptoOnly_2008Excluded_recentCrisesCoveredByCuratedTable() {
        // Kripto canlı verisi yok → küratörlü tabloya düşer. 2008'de kripto analoğu yok (hariç),
        // 2018 & 2020'de tarihsel faktör var (kapsanır).
        var out = service.compute(Map.of("CRYPTO", 1.0));
        var byKey = out.stream().collect(java.util.stream.Collectors.toMap(StressTest::key, st -> st));
        assertThat(byKey.get("crisis2008").available()).isFalse();
        assertThat(byKey.get("crisis2018").available()).isTrue();
        assertThat(byKey.get("covid2020").available()).isTrue();
        // Covid kripto faktörü 0.53 → tek-varlık etki ≈ %-47
        assertThat(byKey.get("covid2020").impactPercent().doubleValue()).isCloseTo(-47.0, offset(1.0));
    }

    @Test
    void compute_commodityOnly_allCrisesCoveredByCuratedTable() {
        // Emtia (petrol) için canlı derin-geçmiş temsilci yok → tarihsel tablo. 3 kriz de kapsanır
        // ("o tarihte yoktu" diye dışlanmaz). Covid emtia faktörü 0.45 → ≈ %-55.
        var out = service.compute(Map.of("COMMODITY", 1.0));
        assertThat(out).hasSize(4).allSatisfy(st -> assertThat(st.available()).isTrue());
        var byKey = out.stream().collect(java.util.stream.Collectors.toMap(StressTest::key, st -> st));
        assertThat(byKey.get("covid2020").impactPercent().doubleValue()).isCloseTo(-55.0, offset(1.0));
    }

    private void stub(AssetType type, String symbol, double startPrice, double endPrice) {
        when(pricePort.fetchDailyClosePrices(eq(type), eq(symbol), any(), any())).thenAnswer(inv -> {
            LocalDate from = inv.getArgument(2);
            LocalDate to = inv.getArgument(3);
            TreeMap<LocalDate, BigDecimal> m = new TreeMap<>();
            m.put(from.plusDays(15), BigDecimal.valueOf(startPrice)); // ≈ kriz başlangıcı
            m.put(to.minusDays(7), BigDecimal.valueOf(endPrice));     // ≈ kriz bitişi
            return Optional.of(m);
        });
    }
}
