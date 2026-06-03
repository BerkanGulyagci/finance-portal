package com.finance.portal.market.application.bond.evds.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link BondClassifier} dal-kapsamı tamamlayıcı testleri.
 *
 * <p>{@link BondClassifierTest} CBRT-kodlu {@code classify(...)} mutlu yollarını kapsıyor; bu sınıf
 * KAPSANMAYAN dalları hedefler: Kira Sertifikası alt-kategorileri + datagroup varyantları, ISIN
 * F-suffix erken dönüş, TÜFE alternatif CBRT kalıpları, altın (\\d+TA\\d+), {@code classifyFromFamily}
 * için her {@link DibsSectionFamily} + strip işareti (CBRT öncelik / ISIN fallback), null-guard'lar,
 * {@code stripMarkerFromCbrt} ve {@code lastIsinLetterBeforeDigits} sınır durumları ve
 * {@code currencyFor} null/EUR dalları.
 */
class BondClassifierMoreTest {

    // ── Kira Sertifikası (datagroup ve ISIN sinyalleri) ──────────────────────

    @Test
    @DisplayName("Kira (datagroup bie_pyks) + ALT → Altın endeksli kira sertifikası")
    void kiraDg_gold_alt() {
        assertThat(BondClassifier.classify("TRT010101T11", "12TALT3", "bie_pyks"))
                .isEqualTo(BondCategory.GOLD_INDEXED_LEASE_CERTIFICATE);
    }

    @Test
    @DisplayName("Kira (datagroup contains 'kira') + AU → Altın endeksli kira sertifikası")
    void kiraDg_gold_au() {
        assertThat(BondClassifier.classify("TRT010101T11", "10AU2", "Some-KIRA-group"))
                .isEqualTo(BondCategory.GOLD_INDEXED_LEASE_CERTIFICATE);
    }

    @Test
    @DisplayName("Kira (datagroup contains 'pyks') + USD → FX kira sertifikası")
    void kiraDg_fx_usdCode() {
        assertThat(BondClassifier.classify("TRT010101T11", "5USD9", "x_pyks_y"))
                .isEqualTo(BondCategory.FX_LEASE_CERTIFICATE);
    }

    @Test
    @DisplayName("Kira ISIN (TRD prefix) + ISIN F-kalıbı → FX kira sertifikası")
    void kiraIsin_fx_isinPattern() {
        // ISIN .*[A-Z]F\\d+ : 'A' harfi + 'F' + rakamlar → eşleşir, code USD içermese de.
        assertThat(BondClassifier.classify("TRD250101AF12", "999", null))
                .isEqualTo(BondCategory.FX_LEASE_CERTIFICATE);
    }

    @Test
    @DisplayName("Kira ISIN (TRD prefix) + DK kodu → TÜFE-endeksli kira sertifikası")
    void kiraIsin_inflation_dk() {
        assertThat(BondClassifier.classify("TRD030626T26", "61T4DK1", null))
                .isEqualTo(BondCategory.INFLATION_INDEXED_LEASE_CERTIFICATE);
    }

    @Test
    @DisplayName("Kira ISIN (TRD prefix) + TF kodu → TÜFE-endeksli kira sertifikası")
    void kiraIsin_inflation_tf() {
        assertThat(BondClassifier.classify("TRD030626T26", "ABTF99", null))
                .isEqualTo(BondCategory.INFLATION_INDEXED_LEASE_CERTIFICATE);
    }

    @Test
    @DisplayName("Kira ISIN (TRD prefix) + sade kod → standart kira sertifikası")
    void kiraIsin_plain() {
        assertThat(BondClassifier.classify("TRD030626T26", "121T2", null))
                .isEqualTo(BondCategory.LEASE_CERTIFICATE);
    }

    // ── ISIN F-suffix erken dönüş (TRT + F) ──────────────────────────────────

    @Test
    @DisplayName("TRT + ISIN F-suffix → FX cinsli DT (CBRT 121T2 olsa bile öncelikli)")
    void trtF_earlyReturn_overridesCbrt() {
        // CBRT kodu nominal DT'ye benziyor ama ISIN F-suffix önceliklidir.
        assertThat(BondClassifier.classify("TRT220726F11", "121T2"))
                .isEqualTo(BondCategory.FX_DENOMINATED_BOND);
    }

    // ── TÜFE alternatif CBRT kalıpları ───────────────────────────────────────

    @Test
    @DisplayName("CBRT 121T2D... → TÜFE-endeksli tam DT (alternate kuponlu)")
    void cbrt121T2D_inflation() {
        assertThat(BondClassifier.classify("TRT070727T13", "121T2D01070727"))
                .isEqualTo(BondCategory.INFLATION_INDEXED_BOND);
    }

    @Test
    @DisplayName("CBRT 121D2... → TÜFE-endeksli tam DT (D2 prefix)")
    void cbrt121D2_inflation() {
        assertThat(BondClassifier.classify("TRT070727T13", "121D2X"))
                .isEqualTo(BondCategory.INFLATION_INDEXED_BOND);
    }

    @Test
    @DisplayName("CBRT 121T2A + D → TÜFE-endeksli ANA PARA stripi (edge: A-strip TÜFE)")
    void cbrt121T2A_withD_inflationPrincipalStrip() {
        // 121T2A && contains('D') → inflation; ISIN A-suffix → INFLATION_PRINCIPAL_STRIP.
        assertThat(BondClassifier.classify("TRT110827A17", "121T2AD110827"))
                .isEqualTo(BondCategory.INFLATION_PRINCIPAL_STRIP);
    }

    @Test
    @DisplayName("CBRT 12...DK → TÜFE-endeksli (12 prefix + DK içerir), ISIN K → kupon stripi")
    void cbrt12_dk_inflationCouponStrip() {
        assertThat(BondClassifier.classify("TRT030626K26", "12XDK5"))
                .isEqualTo(BondCategory.INFLATION_COUPON_STRIP);
    }

    // ── Altın endeksli senet (\\d+TA\\d+) ────────────────────────────────────

    @Test
    @DisplayName("CBRT 12TA2 → Altın endeksli senet")
    void cbrt12TA2_gold() {
        assertThat(BondClassifier.classify("TRT010101T11", "12TA2"))
                .isEqualTo(BondCategory.GOLD_INDEXED_BOND);
    }

    // ── classifyFromFamily: null guard ───────────────────────────────────────

    @Test
    @DisplayName("classifyFromFamily(null, ...) → UNKNOWN")
    void family_null_unknown() {
        assertThat(BondClassifier.classifyFromFamily(null, "TRT010101T11"))
                .isEqualTo(BondCategory.UNKNOWN);
    }

    // ── classifyFromFamily: ZERO_COUPON (TRB vs diğer) ───────────────────────

    @Test
    @DisplayName("ZERO_COUPON + TRB ISIN → Hazine Bonosu")
    void family_zeroCoupon_trb_bill() {
        assertThat(BondClassifier.classifyFromFamily(DibsSectionFamily.ZERO_COUPON, "TRB170626T13"))
                .isEqualTo(BondCategory.ZERO_COUPON_BILL);
    }

    @Test
    @DisplayName("ZERO_COUPON + TRT ISIN → Kuponsuz Devlet Tahvili")
    void family_zeroCoupon_trt_bond() {
        assertThat(BondClassifier.classifyFromFamily(DibsSectionFamily.ZERO_COUPON, "TRT060127T10"))
                .isEqualTo(BondCategory.ZERO_COUPON_BOND);
    }

    // ── classifyFromFamily: FIXED_COUPON strip dalları (ISIN fallback) ───────

    @Test
    @DisplayName("FIXED_COUPON + ISIN A-suffix (CBRT yok) → Ana Para Stripi")
    void family_fixed_isinA_principalStrip() {
        assertThat(BondClassifier.classifyFromFamily(DibsSectionFamily.FIXED_COUPON, "TRT110827A17"))
                .isEqualTo(BondCategory.PRINCIPAL_STRIP);
    }

    @Test
    @DisplayName("FIXED_COUPON + ISIN K-suffix (CBRT yok) → Kupon Stripi")
    void family_fixed_isinK_couponStrip() {
        assertThat(BondClassifier.classifyFromFamily(DibsSectionFamily.FIXED_COUPON, "TRT080328K14"))
                .isEqualTo(BondCategory.COUPON_STRIP);
    }

    @Test
    @DisplayName("FIXED_COUPON + ISIN T-suffix (CBRT yok) → tam Sabit Kuponlu DT")
    void family_fixed_isinT_fullBond() {
        assertThat(BondClassifier.classifyFromFamily(DibsSectionFamily.FIXED_COUPON, "TRT240227T17"))
                .isEqualTo(BondCategory.FIXED_COUPON_BOND);
    }

    // ── classifyFromFamily: CBRT strip işareti ISIN'i geçersiz kılar ─────────

    @Test
    @DisplayName("FIXED_COUPON + CBRT '...A' önceliği → Ana Para Stripi (ISIN KA tuzağına rağmen)")
    void family_fixed_cbrtA_beatsIsinTrap() {
        // ISIN "...KA0" basit kuralla 'A' okur; ama burada CBRT 'A' ile biter → A doğru.
        assertThat(BondClassifier.classifyFromFamily(DibsSectionFamily.FIXED_COUPON, "121T2A110827", "TRT160926KA0"))
                .isEqualTo(BondCategory.PRINCIPAL_STRIP);
    }

    @Test
    @DisplayName("FIXED_COUPON + CBRT '...K' önceliği → Kupon Stripi")
    void family_fixed_cbrtK_couponStrip() {
        assertThat(BondClassifier.classifyFromFamily(DibsSectionFamily.FIXED_COUPON, "121T2K20080328", "TRT080328T14"))
                .isEqualTo(BondCategory.COUPON_STRIP);
    }

    // ── classifyFromFamily: INFLATION strip dalları ──────────────────────────

    @Test
    @DisplayName("INFLATION + ISIN A → TÜFE Ana Para Stripi")
    void family_inflation_isinA() {
        assertThat(BondClassifier.classifyFromFamily(DibsSectionFamily.INFLATION, "TRT131130A15"))
                .isEqualTo(BondCategory.INFLATION_PRINCIPAL_STRIP);
    }

    @Test
    @DisplayName("INFLATION + ISIN K → TÜFE Kupon Stripi")
    void family_inflation_isinK() {
        assertThat(BondClassifier.classifyFromFamily(DibsSectionFamily.INFLATION, "TRT030626K26"))
                .isEqualTo(BondCategory.INFLATION_COUPON_STRIP);
    }

    @Test
    @DisplayName("INFLATION + ISIN T → TÜFE tam DT")
    void family_inflation_isinT() {
        assertThat(BondClassifier.classifyFromFamily(DibsSectionFamily.INFLATION, "TRT070727T13"))
                .isEqualTo(BondCategory.INFLATION_INDEXED_BOND);
    }

    // ── classifyFromFamily: kalan basit aile dalları ─────────────────────────

    @Test
    @DisplayName("GOLD ailesi → Altın endeksli senet")
    void family_gold() {
        assertThat(BondClassifier.classifyFromFamily(DibsSectionFamily.GOLD, "TRT010101T11"))
                .isEqualTo(BondCategory.GOLD_INDEXED_BOND);
    }

    @Test
    @DisplayName("FX ailesi → FX cinsli DT")
    void family_fx() {
        assertThat(BondClassifier.classifyFromFamily(DibsSectionFamily.FX, "TRT220726F11"))
                .isEqualTo(BondCategory.FX_DENOMINATED_BOND);
    }

    @Test
    @DisplayName("TLREF ailesi → TLREF endeksli DT")
    void family_tlref() {
        assertThat(BondClassifier.classifyFromFamily(DibsSectionFamily.TLREF, "TRT010101T11"))
                .isEqualTo(BondCategory.TLREF_INDEXED_BOND);
    }

    @Test
    @DisplayName("LIQUIDITY ailesi → Hazine Bonosu (kuponsuz benzeri)")
    void family_liquidity() {
        assertThat(BondClassifier.classifyFromFamily(DibsSectionFamily.LIQUIDITY, "TRT010101T11"))
                .isEqualTo(BondCategory.ZERO_COUPON_BILL);
    }

    @Test
    @DisplayName("LEASE ailesi → standart kira sertifikası")
    void family_lease() {
        assertThat(BondClassifier.classifyFromFamily(DibsSectionFamily.LEASE, "TRD010101T11"))
                .isEqualTo(BondCategory.LEASE_CERTIFICATE);
    }

    @Test
    @DisplayName("LEASE_INFLATION ailesi → TÜFE kira sertifikası")
    void family_leaseInflation() {
        assertThat(BondClassifier.classifyFromFamily(DibsSectionFamily.LEASE_INFLATION, "TRD010101T11"))
                .isEqualTo(BondCategory.INFLATION_INDEXED_LEASE_CERTIFICATE);
    }

    @Test
    @DisplayName("LEASE_GOLD ailesi → Altın kira sertifikası")
    void family_leaseGold() {
        assertThat(BondClassifier.classifyFromFamily(DibsSectionFamily.LEASE_GOLD, "TRD010101T11"))
                .isEqualTo(BondCategory.GOLD_INDEXED_LEASE_CERTIFICATE);
    }

    @Test
    @DisplayName("LEASE_FX ailesi → FX kira sertifikası")
    void family_leaseFx() {
        assertThat(BondClassifier.classifyFromFamily(DibsSectionFamily.LEASE_FX, "TRD010101T11"))
                .isEqualTo(BondCategory.FX_LEASE_CERTIFICATE);
    }

    @Test
    @DisplayName("classifyFromFamily 2-arg + null ISIN → tam DT (sym boş, strip default)")
    void family_twoArg_nullIsin() {
        assertThat(BondClassifier.classifyFromFamily(DibsSectionFamily.FIXED_COUPON, null))
                .isEqualTo(BondCategory.FIXED_COUPON_BOND);
    }

    // ── stripMarkerFromCbrt sınır durumları (classifyFromFamily üzerinden) ───

    @Test
    @DisplayName("CBRT null → strip ISIN'e düşer (FIXED_COUPON + ISIN A → ana para)")
    void stripMarker_nullCbrt_fallsBackToIsin() {
        assertThat(BondClassifier.classifyFromFamily(DibsSectionFamily.FIXED_COUPON, null, "TRT110827A17"))
                .isEqualTo(BondCategory.PRINCIPAL_STRIP);
    }

    @Test
    @DisplayName("CBRT sadece rakam (end==0) → işaret yok, ISIN'e düşer → tam DT")
    void stripMarker_allDigits_fallsBackToIsin() {
        assertThat(BondClassifier.classifyFromFamily(DibsSectionFamily.FIXED_COUPON, "123456", "TRT240227T17"))
                .isEqualTo(BondCategory.FIXED_COUPON_BOND);
    }

    @Test
    @DisplayName("CBRT son harf ne A ne K (örn ...D) → işaret yok, ISIN'e düşer → tam DT")
    void stripMarker_nonAK_fallsBackToIsin() {
        // "121T2D" → rakam yok, son harf 'D' → '\0' → ISIN T-suffix → tam DT.
        assertThat(BondClassifier.classifyFromFamily(DibsSectionFamily.FIXED_COUPON, "121T2D", "TRT240227T17"))
                .isEqualTo(BondCategory.FIXED_COUPON_BOND);
    }

    // ── lastIsinLetterBeforeDigits sınır durumları (doğrudan) ────────────────

    @Test
    @DisplayName("lastIsinLetterBeforeDigits: null → boşluk")
    void lastLetter_null() {
        assertThat(BondClassifier.lastIsinLetterBeforeDigits(null)).isEqualTo(' ');
    }

    @Test
    @DisplayName("lastIsinLetterBeforeDigits: çok kısa (<5) → boşluk")
    void lastLetter_tooShort() {
        assertThat(BondClassifier.lastIsinLetterBeforeDigits("TR12")).isEqualTo(' ');
    }

    @Test
    @DisplayName("lastIsinLetterBeforeDigits: tamamı rakam → boşluk (i < 0)")
    void lastLetter_allDigits() {
        assertThat(BondClassifier.lastIsinLetterBeforeDigits("1234567890")).isEqualTo(' ');
    }

    @Test
    @DisplayName("lastIsinLetterBeforeDigits: tipik K-suffix → 'K'")
    void lastLetter_typicalK() {
        assertThat(BondClassifier.lastIsinLetterBeforeDigits("TRT030626K26")).isEqualTo('K');
    }

    // ── currencyFor null / EUR-yok default dalları ───────────────────────────

    @Test
    @DisplayName("currencyFor(null) → TRY")
    void currency_null_try() {
        assertThat(BondClassifier.currencyFor(null)).isEqualTo(BondCurrency.TRY);
    }

    @Test
    @DisplayName("currencyFor(UNKNOWN) → default TRY")
    void currency_unknown_default_try() {
        assertThat(BondClassifier.currencyFor(BondCategory.UNKNOWN)).isEqualTo(BondCurrency.TRY);
    }

    // ── classify null girişler / UNKNOWN kalan dallar ────────────────────────

    @Test
    @DisplayName("classify(null, null) → UNKNOWN (tüm guard'lar boş string'e düşer)")
    void classify_allNull_unknown() {
        assertThat(BondClassifier.classify(null, null)).isEqualTo(BondCategory.UNKNOWN);
    }

    @Test
    @DisplayName("classify: TRT prefix + tanınmayan suffix (Z) fallback → tam Sabit Kuponlu DT")
    void classify_trt_unknownSuffix_default() {
        // CBRT yok, TÜFE/altın/strip eşleşmez; TRT fallback switch default → FIXED_COUPON_BOND.
        assertThat(BondClassifier.classify("TRT999999Z99", ""))
                .isEqualTo(BondCategory.FIXED_COUPON_BOND);
    }
}
