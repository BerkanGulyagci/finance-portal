package com.finance.portal.market.application.bond.eurobond.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * {@link AccruedInterestCalculator} — gerçek Business Insider verisiyle (US900123AL40,
 * TURKEY 00/30) doğrulanmış birikmiş faiz / kirli fiyat hesabı.
 *
 * <p>Referans senaryo (canlı BI verisi): yıllık kupon %11.875, yılda 2 ödeme, kupon tarihleri
 * 15 Oca / 15 Tem, USD → 30/360. 15 Mayıs 2024 için son kupon 15 Oca; 30/360 ile 120 gün geçmiş,
 * dönem 180 gün → birikmiş faiz = 5.9375 × 120/180 = 3.9583 (100 nominal başına).
 */
class AccruedInterestCalculatorTest {

    private static EurobondDetail turkey0030() {
        EurobondDetail d = new EurobondDetail();
        d.setIsin("US900123AL40");
        d.setName("TURKEY 00/30");
        d.setCurrency("USD");
        d.setCouponRate("11.875%");
        d.setPaymentsPerYear("2,0");
        d.setCouponPaymentDate("7/15/2026"); // sıradaki ödeme (anchor)
        d.setCouponStartDate("7/15/2000");
        d.setFinalCouponDate("1/14/2030");
        return d;
    }

    // ── Gerçek-veri referans hesabı ─────────────────────────────────────────

    @Test
    @DisplayName("US900123AL40 — 15 May 2024, temiz 118.95 → birikmiş 3.9583, kirli 122.9083")
    void turkey0030_may2024() {
        AccruedInterestCalculator.AccruedResult r = AccruedInterestCalculator.compute(
                turkey0030(), new BigDecimal("118.95"), LocalDate.of(2024, 5, 15));

        assertThat(r.available()).isTrue();
        assertThat(r.dayCount()).isEqualTo(DayCountConvention.THIRTY_360);
        assertThat(r.periodStart()).isEqualTo(LocalDate.of(2024, 1, 15));
        assertThat(r.periodEnd()).isEqualTo(LocalDate.of(2024, 7, 15));
        assertThat(r.accruedInterest()).isCloseTo(new BigDecimal("3.9583"), within(new BigDecimal("0.001")));
        assertThat(r.dirtyPrice()).isCloseTo(new BigDecimal("122.9083"), within(new BigDecimal("0.001")));
    }

    @Test
    @DisplayName("US900123AL40 — 15 Kas 2024, temiz 121.50 → birikmiş 3.9583, kirli 125.4583")
    void turkey0030_nov2024() {
        AccruedInterestCalculator.AccruedResult r = AccruedInterestCalculator.compute(
                turkey0030(), new BigDecimal("121.50"), LocalDate.of(2024, 11, 15));

        assertThat(r.available()).isTrue();
        assertThat(r.periodStart()).isEqualTo(LocalDate.of(2024, 7, 15));
        assertThat(r.periodEnd()).isEqualTo(LocalDate.of(2025, 1, 15));
        assertThat(r.accruedInterest()).isCloseTo(new BigDecimal("3.9583"), within(new BigDecimal("0.001")));
        assertThat(r.dirtyPrice()).isCloseTo(new BigDecimal("125.4583"), within(new BigDecimal("0.001")));
    }

    @Test
    @DisplayName("Kupon ödeme günü → birikmiş faiz ~0 (yeni dönem başı)")
    void onCouponDate_accruedNearZero() {
        AccruedInterestCalculator.AccruedResult r = AccruedInterestCalculator.compute(
                turkey0030(), new BigDecimal("100"), LocalDate.of(2025, 1, 15));
        assertThat(r.available()).isTrue();
        // 15 Oca yeni dönemin başı → geçen gün 0 → birikmiş 0
        assertThat(r.accruedInterest()).isCloseTo(BigDecimal.ZERO, within(new BigDecimal("0.0001")));
        assertThat(r.dirtyPrice()).isCloseTo(new BigDecimal("100"), within(new BigDecimal("0.0001")));
    }

    // ── Konvansiyon seçimi ──────────────────────────────────────────────────

    @Test
    @DisplayName("EUR → ACT/ACT konvansiyonu seçilir")
    void eurUsesActAct() {
        EurobondDetail d = turkey0030();
        d.setCurrency("EUR");
        AccruedInterestCalculator.AccruedResult r = AccruedInterestCalculator.compute(
                d, new BigDecimal("100"), LocalDate.of(2024, 5, 15));
        assertThat(r.dayCount()).isEqualTo(DayCountConvention.ACT_ACT);
    }

    @Test
    @DisplayName("JPY → ACT/365 konvansiyonu seçilir")
    void jpyUsesAct365() {
        EurobondDetail d = turkey0030();
        d.setCurrency("JPY");
        AccruedInterestCalculator.AccruedResult r = AccruedInterestCalculator.compute(
                d, new BigDecimal("100"), LocalDate.of(2024, 5, 15));
        assertThat(r.dayCount()).isEqualTo(DayCountConvention.ACT_365);
    }

    // ── Eksik/bozuk veri → unavailable (asla exception atmaz) ────────────────

    @Test
    @DisplayName("Kupon oranı yok → unavailable")
    void noCouponRate_unavailable() {
        EurobondDetail d = turkey0030();
        d.setCouponRate(null);
        assertThat(AccruedInterestCalculator.compute(d, new BigDecimal("100"), LocalDate.now()).available())
                .isFalse();
    }

    @Test
    @DisplayName("Kupon tarihi yok → unavailable")
    void noCouponDate_unavailable() {
        EurobondDetail d = turkey0030();
        d.setCouponPaymentDate(null);
        d.setFinalCouponDate(null);
        d.setCouponStartDate(null);
        assertThat(AccruedInterestCalculator.compute(d, new BigDecimal("100"), LocalDate.now()).available())
                .isFalse();
    }

    @Test
    @DisplayName("null detail / null tarih → unavailable, exception yok")
    void nullInputs_unavailable() {
        assertThat(AccruedInterestCalculator.compute(null, new BigDecimal("100"), LocalDate.now()).available())
                .isFalse();
        assertThat(AccruedInterestCalculator.compute(turkey0030(), new BigDecimal("100"), null).available())
                .isFalse();
    }

    @Test
    @DisplayName("Temiz fiyat null → accrued döner ama dirty null")
    void nullCleanPrice_accruedOnlyNoDirty() {
        AccruedInterestCalculator.AccruedResult r = AccruedInterestCalculator.compute(
                turkey0030(), null, LocalDate.of(2024, 5, 15));
        assertThat(r.available()).isTrue();
        assertThat(r.accruedInterest()).isNotNull();
        assertThat(r.dirtyPrice()).isNull();
    }

    // ── Parsing birim testleri ──────────────────────────────────────────────

    @Nested
    @DisplayName("Parsing")
    class Parsing {
        @Test
        void percent() {
            assertThat(AccruedInterestCalculator.parsePercent("11.875%")).isEqualByComparingTo("11.875");
            assertThat(AccruedInterestCalculator.parsePercent("5,200%")).isEqualByComparingTo("5.200");
            assertThat(AccruedInterestCalculator.parsePercent("  7.5 % ")).isEqualByComparingTo("7.5");
            assertThat(AccruedInterestCalculator.parsePercent(null)).isNull();
            assertThat(AccruedInterestCalculator.parsePercent("abc")).isNull();
        }

        @Test
        void paymentsPerYear() {
            assertThat(AccruedInterestCalculator.parsePaymentsPerYear("2,0")).isEqualTo(2);
            assertThat(AccruedInterestCalculator.parsePaymentsPerYear("1")).isEqualTo(1);
            assertThat(AccruedInterestCalculator.parsePaymentsPerYear("4.0")).isEqualTo(4);
            assertThat(AccruedInterestCalculator.parsePaymentsPerYear(null)).isEqualTo(0);
            assertThat(AccruedInterestCalculator.parsePaymentsPerYear("xx")).isEqualTo(0);
        }

        @Test
        void date() {
            assertThat(AccruedInterestCalculator.parseDate("7/15/2026")).isEqualTo(LocalDate.of(2026, 7, 15));
            assertThat(AccruedInterestCalculator.parseDate("1/5/2000")).isEqualTo(LocalDate.of(2000, 1, 5));
            assertThat(AccruedInterestCalculator.parseDate(null)).isNull();
            assertThat(AccruedInterestCalculator.parseDate("not-a-date")).isNull();
        }
    }

    // ── 30/360 gün sayımı doğrudan ──────────────────────────────────────────

    @Test
    @DisplayName("30/360: 15 Oca → 15 May = 120 gün; tam yıl = 360")
    void thirty360_dayCounts() {
        assertThat(DayCountConvention.THIRTY_360.daysBetween(
                LocalDate.of(2024, 1, 15), LocalDate.of(2024, 5, 15))).isEqualTo(120);
        assertThat(DayCountConvention.THIRTY_360.daysBetween(
                LocalDate.of(2024, 1, 15), LocalDate.of(2025, 1, 15))).isEqualTo(360);
        assertThat(DayCountConvention.THIRTY_360.daysBetween(
                LocalDate.of(2024, 1, 15), LocalDate.of(2024, 7, 15))).isEqualTo(180);
    }
}
