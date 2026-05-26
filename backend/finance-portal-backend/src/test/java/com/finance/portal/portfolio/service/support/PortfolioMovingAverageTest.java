package com.finance.portal.portfolio.service.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioMovingAverageTest {

    @Test
    @DisplayName("simpleMa: tam pencere — son N elemanın aritmetik ortalaması")
    void simpleMa_exactWindow_returnsAverage() {
        List<BigDecimal> closes = List.of(
                new BigDecimal("10"), new BigDecimal("20"), new BigDecimal("30"));

        BigDecimal ma = PortfolioMovingAverage.simpleMa(closes, 3);

        assertThat(ma).isEqualByComparingTo("20");
    }

    @Test
    @DisplayName("simpleMa: pencereden daha uzun seri — yalnızca son N elemanı alır")
    void simpleMa_longerThanWindow_usesTail() {
        List<BigDecimal> closes = List.of(
                new BigDecimal("100"), new BigDecimal("200"),    // pencerede yok
                new BigDecimal("10"), new BigDecimal("20"), new BigDecimal("30"));

        BigDecimal ma = PortfolioMovingAverage.simpleMa(closes, 3);

        assertThat(ma).isEqualByComparingTo("20");
    }

    @Test
    @DisplayName("simpleMa: pencereden kısa seri — null döner")
    void simpleMa_shorterThanWindow_returnsNull() {
        List<BigDecimal> closes = List.of(new BigDecimal("10"), new BigDecimal("20"));

        assertThat(PortfolioMovingAverage.simpleMa(closes, 5)).isNull();
    }

    @Test
    @DisplayName("simpleMa: null listesi — null döner")
    void simpleMa_nullList_returnsNull() {
        assertThat(PortfolioMovingAverage.simpleMa(null, 5)).isNull();
    }

    @Test
    @DisplayName("simpleMa: boş liste — null döner")
    void simpleMa_emptyList_returnsNull() {
        assertThat(PortfolioMovingAverage.simpleMa(List.of(), 5)).isNull();
    }

    @Test
    @DisplayName("simpleMa: pencerede null fiyatlar — null'ları atlayıp kalanların ortalamasını alır")
    void simpleMa_someNullsInWindow_ignoresNulls() {
        List<BigDecimal> closes = Arrays.asList(
                new BigDecimal("10"), null, new BigDecimal("30"));

        BigDecimal ma = PortfolioMovingAverage.simpleMa(closes, 3);

        // (10 + 30) / 2 = 20
        assertThat(ma).isEqualByComparingTo("20");
    }

    @Test
    @DisplayName("simpleMa: pencere tamamen null — null döner")
    void simpleMa_allNullsInWindow_returnsNull() {
        List<BigDecimal> closes = Arrays.asList(null, null, null);

        assertThat(PortfolioMovingAverage.simpleMa(closes, 3)).isNull();
    }

    @Test
    @DisplayName("simpleMa: scale 4 + HALF_UP — bölünme presizyonu korunur")
    void simpleMa_division_keepsFourDecimals() {
        // (1 + 2) / 2 = 1.5000
        List<BigDecimal> closes = List.of(BigDecimal.ONE, new BigDecimal("2"));

        BigDecimal ma = PortfolioMovingAverage.simpleMa(closes, 2);

        assertThat(ma).isEqualByComparingTo("1.5");
        assertThat(ma.scale()).isEqualTo(4);
    }
}
