package com.finance.portal.portfolio.service;

import com.finance.portal.portfolio.application.analysis.PortfolioAiAnalysisResult.Rebalance;
import com.finance.portal.portfolio.application.analysis.PortfolioAiAnalysisResult.RebalanceItem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

/**
 * Yeniden dengeleme: profil hedef şablonu vs mevcut dağılım sapması ve yön (ARTIR/AZALT/KORU).
 */
class PortfolioRebalanceServiceTest {

    private final PortfolioRebalanceService service = new PortfolioRebalanceService();

    @Test
    void compute_conservative_targetsAndDrift() {
        // Mevcut: %100 hisse → Korumacı hedefe çok uzak.
        Rebalance rb = service.compute(Map.of("STOCK", new BigDecimal("100")), "CONSERVATIVE");

        assertThat(rb.basedOnProfile()).isEqualTo("CONSERVATIVE");
        assertThat(rb.profileLabel()).isEqualTo("Korumacı");

        RebalanceItem stock = item(rb, "STOCK");
        assertThat(stock.targetPercent()).isEqualByComparingTo("10.0");
        assertThat(stock.action()).isEqualTo("AZALT"); // 100 → 10

        RebalanceItem bond = item(rb, "BOND");
        assertThat(bond.action()).isEqualTo("ARTIR"); // 0 → 40

        // drift = (|10-100| + 40 + 20 + 15 + 15) / 2 = 180/2 = 90
        assertThat(rb.driftPercent().doubleValue()).isCloseTo(90.0, offset(0.5));
    }

    @Test
    void compute_unknownProfile_defaultsToBalanced() {
        Rebalance rb = service.compute(Map.of("STOCK", new BigDecimal("100")), "ZZZ");
        assertThat(rb.basedOnProfile()).isEqualTo("BALANCED");
    }

    @Test
    void compute_withinKeepBand_marksKoru() {
        // Dengeli hedef hisse %35; mevcut %36 → |sapma| 1 ≤ 2 → KORU.
        Rebalance rb = service.compute(
                Map.of("STOCK", new BigDecimal("36"), "BOND", new BigDecimal("64")), "BALANCED");
        assertThat(item(rb, "STOCK").action()).isEqualTo("KORU");
    }

    private static RebalanceItem item(Rebalance rb, String type) {
        return rb.items().stream().filter(i -> type.equals(i.assetType())).findFirst().orElseThrow();
    }
}
