package com.finance.portal.portfolio.infrastructure.market;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

class CommodityHistoricalTryConversionTest {

    @Test
    @DisplayName("NG=F benzeri: ~2.96 USD × ~45 TRY ≈ 133 TRY (güncel kur fallback)")
    void convertUsdCloseToTry_withLatestFallback() {
        BigDecimal usdClose = new BigDecimal("2.96");
        BigDecimal latestTry = new BigDecimal("45.27");

        BigDecimal tryClose = CommodityHistoricalTryConversion.convertUsdCloseToTry(
                usdClose,
                LocalDate.of(2026, 5, 17),
                new TreeMap<>(),
                latestTry);

        assertThat(tryClose).isNotNull();
        assertThat(tryClose).isEqualByComparingTo("134.001120");
    }

    @Test
    @DisplayName("4 lot × ~134 TRY ≈ 536 TRY market value seviyesi")
    void fourLots_marketValueScale() {
        BigDecimal unitTry = CommodityHistoricalTryConversion.convertUsdCloseToTry(
                new BigDecimal("2.96"),
                LocalDate.of(2026, 5, 17),
                new TreeMap<>(),
                new BigDecimal("45.27"));
        assertThat(unitTry).isNotNull();

        BigDecimal mv = unitTry.multiply(new BigDecimal("4"));
        assertThat(mv).isGreaterThan(new BigDecimal("500"));
        assertThat(mv).isLessThan(new BigDecimal("560"));
    }

    @Test
    @DisplayName("Günlük kur: floorEntry ile hafta sonu önceki iş günü kuru")
    void resolveUsdTryRate_forwardFill() {
        var usdTry = new TreeMap<LocalDate, BigDecimal>();
        usdTry.put(LocalDate.of(2026, 5, 15), new BigDecimal("40.00"));
        usdTry.put(LocalDate.of(2026, 5, 16), new BigDecimal("41.00"));

        BigDecimal sat = CommodityHistoricalTryConversion.resolveUsdTryRate(
                LocalDate.of(2026, 5, 17),
                usdTry,
                new BigDecimal("99"));

        assertThat(sat).isEqualByComparingTo("41.00");
    }

    @Test
    @DisplayName("Sıfır veya negatif USD kapanış geçersiz")
    void convertUsdCloseToTry_rejectsNonPositiveUsd() {
        assertThat(CommodityHistoricalTryConversion.convertUsdCloseToTry(
                BigDecimal.ZERO,
                LocalDate.of(2026, 5, 1),
                new TreeMap<>(),
                new BigDecimal("40"))).isNull();
    }
}
