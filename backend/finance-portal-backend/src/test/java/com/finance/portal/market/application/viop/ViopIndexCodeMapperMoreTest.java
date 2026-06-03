package com.finance.portal.market.application.viop;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Branch-coverage focused complement to {@link ViopIndexCodeMapperTest}.
 *
 * Targets uncovered branches only (does NOT duplicate the existing suite):
 *  - monthCodeFromSkeleton: null / empty-after-strip / direct skeleton hit /
 *    "ub" substring fallback / "au" substring fallback / startsWith-a&endsWith-u
 *    fallback / unknown -> null
 *  - canonicalizeContractName: null / blank / no date-paren / unresolvable month /
 *    clean-passthrough / broken-encoding repair
 *  - resolveMonth arms reached via the public mapper:
 *    BROKEN_MONTH_MAP direct hit / normalize-then-broken / normalize-then-clean /
 *    capitalize arm / ASCII-skeleton fallback / final unresolved -> empty
 *  - tryElcbas monthly with unresolvable month -> empty
 *  - GAUTRY alias edge, Put option reject, isSupportedFuture false
 */
class ViopIndexCodeMapperMoreTest {

    private ViopIndexCodeMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ViopIndexCodeMapper();
    }

    // ── monthCodeFromSkeleton (package-private static) ─────────────────────────

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "1234", "!!!", "-- 99 --"})
    @DisplayName("monthCodeFromSkeleton: null / no-ASCII-letters -> null")
    void skeletonReturnsNullForNullOrNonLetters(String raw) {
        assertThat(ViopIndexCodeMapper.monthCodeFromSkeleton(raw)).isNull();
    }

    @ParameterizedTest(name = "skeleton ''{0}'' -> {1}")
    @CsvSource({
            // direct SKELETON_MONTH_MAP hits across the table
            "Oca, 01",
            "ub,  02",
            "Mar, 03",
            "Nis, 04",
            "May, 05",
            "Haz, 06",
            "Tem, 07",
            "au,  08",
            "Eyl, 09",
            "Eki, 10",
            "Kas, 11",
            "Ara, 12",
            // Turkish letters stripped to canonical skeleton
            "Şub, 02",
            "Ağu, 08",
    })
    @DisplayName("monthCodeFromSkeleton: direct skeleton-map hits")
    void skeletonDirectHits(String raw, String expected) {
        assertThat(ViopIndexCodeMapper.monthCodeFromSkeleton(raw)).isEqualTo(expected);
    }

    @Test
    @DisplayName("monthCodeFromSkeleton: 'ub' substring fallback (skeleton not in map)")
    void skeletonUbSubstringFallback() {
        // letters -> "Xub" -> "xub": not a map key, but contains "ub" -> 02
        assertThat(ViopIndexCodeMapper.monthCodeFromSkeleton("Xşub")).isEqualTo("02");
    }

    @Test
    @DisplayName("monthCodeFromSkeleton: 'au' substring fallback (skeleton not in map)")
    void skeletonAuSubstringFallback() {
        // letters -> "Xau" -> "xau": not a map key, but contains "au" -> 08
        assertThat(ViopIndexCodeMapper.monthCodeFromSkeleton("Xğau")).isEqualTo("08");
    }

    @Test
    @DisplayName("monthCodeFromSkeleton: startsWith-a & endsWith-u fallback (no 'au' substring)")
    void skeletonStartsAEndsUFallback() {
        // "Axu" -> "axu": not a key, no "au" substring, starts 'a' & ends 'u' -> 08
        assertThat(ViopIndexCodeMapper.monthCodeFromSkeleton("Axu")).isEqualTo("08");
    }

    @ParameterizedTest
    @ValueSource(strings = {"zzz", "qrt", "bob", "xyz"})
    @DisplayName("monthCodeFromSkeleton: unknown skeleton -> null")
    void skeletonUnknownReturnsNull(String raw) {
        assertThat(ViopIndexCodeMapper.monthCodeFromSkeleton(raw)).isNull();
    }

    // ── canonicalizeContractName (public static) ───────────────────────────────

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("canonicalizeContractName: null/blank returned unchanged")
    void canonicalizeNullOrBlankPassthrough(String name) {
        assertThat(ViopIndexCodeMapper.canonicalizeContractName(name)).isEqualTo(name);
    }

    @Test
    @DisplayName("canonicalizeContractName: no date paren -> unchanged")
    void canonicalizeNoDateParen() {
        String name = "THYAO Vadeli FIZ.";
        assertThat(ViopIndexCodeMapper.canonicalizeContractName(name)).isEqualTo(name);
    }

    @Test
    @DisplayName("canonicalizeContractName: paren present but month unresolvable -> unchanged")
    void canonicalizeUnresolvableMonth() {
        // "zzz" has a valid date-paren shape but no skeleton match -> code==null branch
        String name = "THYAO (30 zzz 26) Vadeli FIZ.";
        assertThat(ViopIndexCodeMapper.canonicalizeContractName(name)).isEqualTo(name);
    }

    @Test
    @DisplayName("canonicalizeContractName: already-clean month -> unchanged content")
    void canonicalizeAlreadyClean() {
        String name = "THYAO (30 Haz 26) Vadeli FIZ.";
        assertThat(ViopIndexCodeMapper.canonicalizeContractName(name)).isEqualTo(name);
    }

    @Test
    @DisplayName("canonicalizeContractName: broken Şubat token repaired to 'Şub'")
    void canonicalizeRepairsBrokenFebruary() {
        // U+FFFD-like garbled byte stripped to skeleton "ub" -> 02 -> abbrev "Şub"
        String repaired = ViopIndexCodeMapper.canonicalizeContractName("USDTRY (26 �ub 27) Vadeli");
        assertThat(repaired).isEqualTo("USDTRY (26 Şub 27) Vadeli");
    }

    @Test
    @DisplayName("canonicalizeContractName: broken Ağustos token repaired to 'Ağu'")
    void canonicalizeRepairsBrokenAugust() {
        String repaired = ViopIndexCodeMapper.canonicalizeContractName("X10XB (29 A�u 26) Vadeli");
        assertThat(repaired).isEqualTo("X10XB (29 Ağu 26) Vadeli");
    }

    // ── resolveMonth arms via public mapper ────────────────────────────────────

    @ParameterizedTest(name = "broken-token ''{0}'' -> ''{1}''")
    @CsvSource({
            // BROKEN_MONTH_MAP direct-hit branch (raw matches a static broken key)
            "THYAO (30 AĞYu 26) Vadeli FIZ., F_THYAO0826",
            "THYAO (30 Aşub 27) Vadeli FIZ., F_THYAO0227",
            "THYAO (30 Åub 27) Vadeli FIZ.,  F_THYAO0227",
    })
    @DisplayName("resolveMonth: BROKEN_MONTH_MAP direct-hit branch")
    void resolveMonthBrokenDirectHit(String contractName, String expected) {
        assertThat(mapper.toIsYatirimEndeksCode(contractName)).hasValue(expected);
    }

    @Test
    @DisplayName("resolveMonth: normalize-then-clean (capitalize arm) for lowercase 'haz'")
    void resolveMonthCapitalizeArm() {
        // "haz": not broken, not in MONTH_MAP, normalize no-op -> still miss,
        // capitalize -> "Haz" matches MONTH_MAP -> 06
        assertThat(mapper.toIsYatirimEndeksCode("THYAO (30 haz 26) Vadeli FIZ."))
                .hasValue("F_THYAO0626");
    }

    @Test
    @DisplayName("resolveMonth: ASCII-skeleton fallback resolves U+FFFD garbled month")
    void resolveMonthSkeletonFallback() {
        // "Ş" replaced by replacement char -> "�ub": not broken/clean/cap match,
        // skeleton "ub" -> 02
        assertThat(mapper.toIsYatirimEndeksCode("USDTRY (26 �ub 27) Vadeli FIZ."))
                .hasValue("F_USDTRY0227");
    }

    @Test
    @DisplayName("resolveMonth: ASCII-skeleton fallback for garbled Ağustos")
    void resolveMonthSkeletonFallbackAugust() {
        assertThat(mapper.toIsYatirimEndeksCode("X10XB (29 A�u 26) Vadeli FIZ."))
                .hasValue("F_X10XB0826");
    }

    @Test
    @DisplayName("resolveMonth: fully unknown month -> warn + empty")
    void resolveMonthFullyUnknownReturnsEmpty() {
        // valid futures shape, but month token "zzz" resolves nowhere -> monthCode==null
        assertThat(mapper.toIsYatirimEndeksCode("THYAO (30 zzz 26) Vadeli FIZ.")).isEmpty();
    }

    // ── ELCBAS monthly: unresolvable month -> tryElcbas empty, no generic fallback ─

    @Test
    @DisplayName("ELCBAS monthly with unresolvable paren month -> empty")
    void elcbasMonthlyUnresolvableMonthEmpty() {
        // ELCBAS_MONTHLY matches, but paren month "zzz" -> resolveMonth null -> empty;
        // ELCBAS{MM}.. is not a generic futures underlier, so overall empty.
        assertThat(mapper.toIsYatirimEndeksCode("ELCBAS05 (01 zzz 26) Vadeli")).isEmpty();
    }

    // ── Option Put reject + isSupportedFuture false arms ──────────────────────

    @Test
    @DisplayName("Put option rejected and isSupportedFuture false")
    void putOptionRejected() {
        String put = "GARAN Mayis 2026 Put 130.00 E";
        assertThat(mapper.toIsYatirimEndeksCode(put)).isEmpty();
        assertThat(mapper.isSupportedFuture(put)).isFalse();
    }

    @Test
    @DisplayName("isSupportedFuture false for null / blank / unknown format")
    void isSupportedFalseForNonFutures() {
        assertThat(mapper.isSupportedFuture(null)).isFalse();
        assertThat(mapper.isSupportedFuture("")).isFalse();
        assertThat(mapper.isSupportedFuture("   ")).isFalse();
        assertThat(mapper.isSupportedFuture("not a contract at all")).isFalse();
    }

    @Test
    @DisplayName("isSupportedFuture true for GAUTRY alias and ELCBAS quarterly/yearly")
    void isSupportedTrueForAliasAndElcbas() {
        assertThat(mapper.isSupportedFuture("GAUTRY (30 Haz 26) Vadeli")).isTrue();
        assertThat(mapper.isSupportedFuture("ELCBASQ3 (29 Haz 26) Vadeli")).isTrue();
        assertThat(mapper.isSupportedFuture("ELCBASY (29 Ara 26) Vadeli")).isTrue();
    }
}
