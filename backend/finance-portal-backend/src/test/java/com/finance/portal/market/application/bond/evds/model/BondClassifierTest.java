package com.finance.portal.market.application.bond.evds.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link BondClassifier} kategorizasyon kararları — TCMB dibs1.txt'in 7 alt-bölümünden
 * gözlemlenmiş gerçek ISIN + CBRT kalıpları ile.
 */
class BondClassifierTest {

    // ── CBRT-kodlu kararlar (öncelikli yol) ─────────────────────────────────

    @Test
    @DisplayName("CBRT 10B → Kuponsuz Hazine Bonosu")
    void cbrt10B_zeroCouponBill() {
        assertThat(BondClassifier.classify("TRB170626T13", "10B"))
                .isEqualTo(BondCategory.ZERO_COUPON_BILL);
    }

    @Test
    @DisplayName("CBRT 11B → Kuponsuz Hazine Bonosu")
    void cbrt11B_zeroCouponBill() {
        assertThat(BondClassifier.classify("TRB170327T15", "11B"))
                .isEqualTo(BondCategory.ZERO_COUPON_BILL);
    }

    @Test
    @DisplayName("CBRT 12T → Kuponsuz Devlet Tahvili")
    void cbrt12T_zeroCouponBond() {
        assertThat(BondClassifier.classify("TRT060127T10", "12T"))
                .isEqualTo(BondCategory.ZERO_COUPON_BOND);
    }

    @Test
    @DisplayName("CBRT 121T2 (suffix yok) → Sabit kuponlu tam DT")
    void cbrt121T2_fixedCouponBond() {
        assertThat(BondClassifier.classify("TRT240227T17", "121T2"))
                .isEqualTo(BondCategory.FIXED_COUPON_BOND);
    }

    @Test
    @DisplayName("CBRT 121T2A... → Ana Para Stripi")
    void cbrt121T2A_principalStrip() {
        assertThat(BondClassifier.classify("TRT110827A17", "121T2A110827"))
                .isEqualTo(BondCategory.PRINCIPAL_STRIP);
    }

    @Test
    @DisplayName("CBRT 121T2K... → Kupon Stripi")
    void cbrt121T2K_couponStrip() {
        assertThat(BondClassifier.classify("TRT080328K14", "121T2K20080328"))
                .isEqualTo(BondCategory.COUPON_STRIP);
    }

    @Test
    @DisplayName("CBRT 61T... + ISIN T → TÜFE-Endeksli tam DT")
    void cbrt61T_inflationFullBond() {
        assertThat(BondClassifier.classify("TRT070727T13", "61T2DK01070727"))
                .isEqualTo(BondCategory.INFLATION_INDEXED_BOND);
    }

    @Test
    @DisplayName("CBRT 61T... + ISIN K → TÜFE-Endeksli Kupon Stripi (TRT030626K26 case)")
    void cbrt61T_K_inflationCouponStrip() {
        assertThat(BondClassifier.classify("TRT030626K26", "61T4DK13010328"))
                .isEqualTo(BondCategory.INFLATION_COUPON_STRIP);
    }

    @Test
    @DisplayName("CBRT 61T... + ISIN A → TÜFE-Endeksli Ana Para Stripi")
    void cbrt61T_A_inflationPrincipalStrip() {
        assertThat(BondClassifier.classify("TRT131130A15", "61T2DA01131130"))
                .isEqualTo(BondCategory.INFLATION_PRINCIPAL_STRIP);
    }

    // ── Fallback: CBRT yoksa ISIN'e bak ──────────────────────────────────────

    @Test
    @DisplayName("Fallback: TRB prefix + T suffix → Hazine Bonosu")
    void fallback_trb_zeroCouponBill() {
        assertThat(BondClassifier.classify("TRB170626T13", null))
                .isEqualTo(BondCategory.ZERO_COUPON_BILL);
    }

    @Test
    @DisplayName("Fallback: TRT + T suffix → Sabit kuponlu DT")
    void fallback_trtT_fixedCouponBond() {
        assertThat(BondClassifier.classify("TRT240227T17", null))
                .isEqualTo(BondCategory.FIXED_COUPON_BOND);
    }

    @Test
    @DisplayName("Fallback: TRT + K suffix → Kupon Stripi")
    void fallback_trtK_couponStrip() {
        assertThat(BondClassifier.classify("TRT080328K14", ""))
                .isEqualTo(BondCategory.COUPON_STRIP);
    }

    @Test
    @DisplayName("Fallback: TRT + A suffix → Ana Para Stripi")
    void fallback_trtA_principalStrip() {
        assertThat(BondClassifier.classify("TRT110827A17", null))
                .isEqualTo(BondCategory.PRINCIPAL_STRIP);
    }

    @Test
    @DisplayName("Fallback: TRT + F suffix → FX cinsli")
    void fallback_trtF_fxBond() {
        assertThat(BondClassifier.classify("TRT220726F11", null))
                .isEqualTo(BondCategory.FX_DENOMINATED_BOND);
    }

    @Test
    @DisplayName("Tanınmayan ISIN + boş CBRT → UNKNOWN")
    void unknown() {
        assertThat(BondClassifier.classify("XX999999Z99", null))
                .isEqualTo(BondCategory.UNKNOWN);
    }

    // ── Para birimi kararları ────────────────────────────────────────────────

    @Test
    @DisplayName("currencyFor: Tier 1/2 TL")
    void currency_tier1AndInflationAreTRY() {
        assertThat(BondClassifier.currencyFor(BondCategory.ZERO_COUPON_BILL)).isEqualTo(BondCurrency.TRY);
        assertThat(BondClassifier.currencyFor(BondCategory.FIXED_COUPON_BOND)).isEqualTo(BondCurrency.TRY);
        assertThat(BondClassifier.currencyFor(BondCategory.COUPON_STRIP)).isEqualTo(BondCurrency.TRY);
        assertThat(BondClassifier.currencyFor(BondCategory.INFLATION_INDEXED_BOND)).isEqualTo(BondCurrency.TRY);
        assertThat(BondClassifier.currencyFor(BondCategory.TLREF_INDEXED_BOND)).isEqualTo(BondCurrency.TRY);
        assertThat(BondClassifier.currencyFor(BondCategory.LEASE_CERTIFICATE)).isEqualTo(BondCurrency.TRY);
    }

    @Test
    @DisplayName("currencyFor: FX → USD, GOLD → GOLD")
    void currency_fxAndGold() {
        assertThat(BondClassifier.currencyFor(BondCategory.FX_DENOMINATED_BOND)).isEqualTo(BondCurrency.USD);
        assertThat(BondClassifier.currencyFor(BondCategory.FX_LEASE_CERTIFICATE)).isEqualTo(BondCurrency.USD);
        assertThat(BondClassifier.currencyFor(BondCategory.GOLD_INDEXED_BOND)).isEqualTo(BondCurrency.GOLD);
        assertThat(BondClassifier.currencyFor(BondCategory.GOLD_INDEXED_LEASE_CERTIFICATE)).isEqualTo(BondCurrency.GOLD);
    }

    // ── BondCategory helper'ları ────────────────────────────────────────────

    @Test
    @DisplayName("BondCategory.isTier1 — TL/Tier1 ailesi")
    void category_isTier1() {
        assertThat(BondCategory.FIXED_COUPON_BOND.isTier1()).isTrue();
        assertThat(BondCategory.COUPON_STRIP.isTier1()).isTrue();
        assertThat(BondCategory.INFLATION_INDEXED_BOND.isTier1()).isFalse();
        assertThat(BondCategory.GOLD_INDEXED_BOND.isTier1()).isFalse();
    }

    @Test
    @DisplayName("BondCategory.isStrip — A/K varyantları")
    void category_isStrip() {
        assertThat(BondCategory.PRINCIPAL_STRIP.isStrip()).isTrue();
        assertThat(BondCategory.COUPON_STRIP.isStrip()).isTrue();
        assertThat(BondCategory.INFLATION_PRINCIPAL_STRIP.isStrip()).isTrue();
        assertThat(BondCategory.FIXED_COUPON_BOND.isStrip()).isFalse();
    }

    @Test
    @DisplayName("BondCategory.paysCoupon — strip ve kuponsuz ödeme yapmaz")
    void category_paysCoupon() {
        assertThat(BondCategory.FIXED_COUPON_BOND.paysCoupon()).isTrue();
        assertThat(BondCategory.INFLATION_INDEXED_BOND.paysCoupon()).isTrue();
        assertThat(BondCategory.ZERO_COUPON_BILL.paysCoupon()).isFalse();
        assertThat(BondCategory.PRINCIPAL_STRIP.paysCoupon()).isFalse();
        assertThat(BondCategory.COUPON_STRIP.paysCoupon()).isFalse();
    }
}
