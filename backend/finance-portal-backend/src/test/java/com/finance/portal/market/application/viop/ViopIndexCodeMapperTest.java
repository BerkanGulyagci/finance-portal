package com.finance.portal.market.application.viop;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ViopIndexCodeMapper birim testleri.
 *
 * Doğrulanan mapping'ler (Network analizi ile kesinleşti):
 * - Pay vadeli:              THYAO (30 Haz 26) Vadeli FIZ.   → F_THYAO0626
 * - Endeks vadeli:           X10XB (29 Ağu 26) Vadeli FIZ.   → F_X10XB0826
 * - Döviz vadeli:            CNHTRY (31 Ara 26) Vadeli FIZ.  → F_CNHTRY1226
 * - Döviz/USD vadeli:        EURUSD (28 Ağu 26) Vadeli FIZ.  → F_EURUSD0826
 * - Kıymetli maden vadeli:   XAUTRYM (29 Ağu 26) Vadeli FIZ. → F_XAUTRYM0826
 * - Kıymetli maden/USD:      XAGUSD (29 Ağu 26) Vadeli FIZ.  → F_XAGUSD0826
 * - Metal vadeli:            XCUUSD (29 Ağu 26) Vadeli FIZ.  → F_XCUUSD0826
 */
class ViopIndexCodeMapperTest {

    private ViopIndexCodeMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ViopIndexCodeMapper();
    }

    // ── Pay vadeli ────────────────────────────────────────────────────────────

    @ParameterizedTest(name = "''{0}'' -> ''{1}''")
    @DisplayName("Pay vadeli sözleşmeler doğru endeks koduna dönüşmeli")
    @CsvSource({
            "THYAO (30 Haz 26) Vadeli FIZ.,   F_THYAO0626",
            "AKBNK (30 Haz 26) Vadeli FIZ.,   F_AKBNK0626",
            "ALARK (31 Tem 26) Vadeli FIZ.,   F_ALARK0726",
            "CIMSA (30 Haz 26) Vadeli FIZ.,   F_CIMSA0626",
            "SASA (25 May 26) Vadeli FIZ.,    F_SASA0526",
            "AEFES (30 Haz 26) Vadeli FIZ.,   F_AEFES0626",
            "EREGL (30 Haz 26) Vadeli FIZ.,   F_EREGL0626",
    })
    void shouldMapEquityFutures(String contractName, String expectedCode) {
        assertThat(mapper.toIsYatirimEndeksCode(contractName.trim()))
                .isPresent()
                .hasValue(expectedCode.trim());
    }

    // ── Endeks vadeli ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Endeks vadeli: X10XB → F_X10XB0826")
    void shouldMapIndexFuture() {
        assertThat(mapper.toIsYatirimEndeksCode("X10XB (29 Ağu 26) Vadeli FIZ."))
                .hasValue("F_X10XB0826");
    }

    // ── Döviz vadeli ──────────────────────────────────────────────────────────

    @ParameterizedTest(name = "''{0}'' -> ''{1}''")
    @DisplayName("Döviz vadeli sözleşmeler doğru endeks koduna dönüşmeli")
    @CsvSource({
            "CNHTRY (31 Ara 26) Vadeli FIZ.,  F_CNHTRY1226",
            "EURUSD (28 Ağu 26) Vadeli FIZ.,  F_EURUSD0826",
            "USDTRY (30 Haz 26) Vadeli FIZ.,  F_USDTRY0626",
            "EURTRY (30 Haz 26) Vadeli FIZ.,  F_EURTRY0626",
    })
    void shouldMapFxFutures(String contractName, String expectedCode) {
        assertThat(mapper.toIsYatirimEndeksCode(contractName.trim()))
                .isPresent()
                .hasValue(expectedCode.trim());
    }

    // ── Kıymetli maden vadeli ─────────────────────────────────────────────────

    @ParameterizedTest(name = "''{0}'' -> ''{1}''")
    @DisplayName("Kıymetli maden vadeli sözleşmeler doğru endeks koduna dönüşmeli")
    @CsvSource({
            "XAUTRYM (29 Ağu 26) Vadeli FIZ., F_XAUTRYM0826",
            "XAGUSD (29 Ağu 26) Vadeli FIZ.,  F_XAGUSD0826",
    })
    void shouldMapPreciousMetalFutures(String contractName, String expectedCode) {
        assertThat(mapper.toIsYatirimEndeksCode(contractName.trim()))
                .isPresent()
                .hasValue(expectedCode.trim());
    }

    // ── Metal vadeli ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("Metal vadeli: XCUUSD → F_XCUUSD0826")
    void shouldMapMetalFuture() {
        assertThat(mapper.toIsYatirimEndeksCode("XCUUSD (29 Ağu 26) Vadeli FIZ."))
                .hasValue("F_XCUUSD0826");
    }

    // ── Ay mapping'leri ───────────────────────────────────────────────────────

    @ParameterizedTest(name = "Ay: {0}")
    @DisplayName("Tüm Türkçe ay kısaltmaları doğru map edilmeli")
    @CsvSource({
            "Oca, F_THYAO0126",
            "Şub, F_THYAO0226",
            "Mar, F_THYAO0326",
            "Nis, F_THYAO0426",
            "May, F_THYAO0526",
            "Haz, F_THYAO0626",
            "Tem, F_THYAO0726",
            "Ağu, F_THYAO0826",
            "Eyl, F_THYAO0926",
            "Eki, F_THYAO1026",
            "Kas, F_THYAO1126",
            "Ara, F_THYAO1226",
    })
    void shouldMapAllMonths(String monthTr, String expectedCode) {
        String contractName = "THYAO (30 " + monthTr + " 26) Vadeli FIZ.";
        assertThat(mapper.toIsYatirimEndeksCode(contractName))
                .isPresent()
                .hasValue(expectedCode);
    }

    // ── Opsiyonlar desteklenmez ───────────────────────────────────────────────

    @ParameterizedTest(name = "''{0}'' opsiyon → desteklenmemeli")
    @DisplayName("Opsiyon sözleşmeleri empty dönmeli")
    @CsvSource({
            "AKBNK Mayis 2026 Call 72.00 E",
            "THYAO Mayis 2026 Put 330.00 E",
            "KCHOL Mayis 2026 Call 230.00 E",
            "XU030 Agustos 2026 Call 17250.00 E",
            "USDTRY Mayis 2026 Call 46500 E",
    })
    void shouldReturnEmptyForOptions(String contractName) {
        assertThat(mapper.toIsYatirimEndeksCode(contractName)).isEmpty();
    }

    // ── Edge case'ler ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("null input empty dönmeli")
    void shouldReturnEmptyForNull() {
        assertThat(mapper.toIsYatirimEndeksCode(null)).isEmpty();
    }

    @Test
    @DisplayName("Boş string empty dönmeli")
    void shouldReturnEmptyForBlank() {
        assertThat(mapper.toIsYatirimEndeksCode("")).isEmpty();
        assertThat(mapper.toIsYatirimEndeksCode("   ")).isEmpty();
    }

    @Test
    @DisplayName("Tanımsız format empty dönmeli")
    void shouldReturnEmptyForUnknownFormat() {
        assertThat(mapper.toIsYatirimEndeksCode("THYAO Haziran 2026 Vadeli")).isEmpty();
        assertThat(mapper.toIsYatirimEndeksCode("F_THYAO0626")).isEmpty();
    }

    // ── isSupportedFuture ─────────────────────────────────────────────────────

    @Test
    @DisplayName("isSupportedFuture vadeli sözleşme için true dönmeli")
    void isSupportedShouldReturnTrueForFutures() {
        assertThat(mapper.isSupportedFuture("THYAO (30 Haz 26) Vadeli FIZ.")).isTrue();
        assertThat(mapper.isSupportedFuture("X10XB (29 Ağu 26) Vadeli FIZ.")).isTrue();
        assertThat(mapper.isSupportedFuture("USDTRY (30 Haz 26) Vadeli FIZ.")).isTrue();
        assertThat(mapper.isSupportedFuture("XAUTRYM (29 Ağu 26) Vadeli FIZ.")).isTrue();
    }

    @Test
    @DisplayName("isSupportedFuture opsiyon için false dönmeli")
    void isSupportedShouldReturnFalseForOptions() {
        assertThat(mapper.isSupportedFuture("AKBNK Mayis 2026 Call 72.00 E")).isFalse();
        assertThat(mapper.isSupportedFuture("THYAO Mayis 2026 Put 330.00 E")).isFalse();
    }
}
