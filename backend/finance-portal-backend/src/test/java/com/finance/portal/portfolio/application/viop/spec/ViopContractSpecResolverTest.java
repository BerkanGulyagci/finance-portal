package com.finance.portal.portfolio.application.viop.spec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ViopContractSpecResolver#resolveUnderlier} sembol-parse testleri — tamamen statik, mock yok.
 */
class ViopContractSpecResolverTest {

    @Test
    @DisplayName("Düz hisse sembolü: F_AKBNK0626 → AKBNK")
    void standardStock() {
        assertThat(ViopContractSpecResolver.resolveUnderlier("F_AKBNK0626")).isEqualTo("AKBNK");
    }

    @Test
    @DisplayName("Bedelli N1 variant: F_BIMAS0626N1 → BIMAS (N-suffix atılır)")
    void n1Variant() {
        assertThat(ViopContractSpecResolver.resolveUnderlier("F_BIMAS0626N1")).isEqualTo("BIMAS");
    }

    @Test
    @DisplayName("Mini gold: F_XAUTRYM0626 → XAUTRY (M dayanağa dahil değil)")
    void xautryMini() {
        assertThat(ViopContractSpecResolver.resolveUnderlier("F_XAUTRYM0626")).isEqualTo("XAUTRY");
        assertThat(ViopContractSpecResolver.resolveUnderlier("F_XAUTRYM0626N1")).isEqualTo("XAUTRY");
    }

    @Test
    @DisplayName("Elektrik quarterly: F_ELCBASQ326 → ELCBASQ")
    void elcbasQuarterly() {
        assertThat(ViopContractSpecResolver.resolveUnderlier("F_ELCBASQ326")).isEqualTo("ELCBASQ");
    }

    @Test
    @DisplayName("Elektrik yearly: F_ELCBASY26 → ELCBASY")
    void elcbasYearly() {
        assertThat(ViopContractSpecResolver.resolveUnderlier("F_ELCBASY26")).isEqualTo("ELCBASY");
    }

    @Test
    @DisplayName("Elektrik monthly (default): F_ELCBAS0626 → ELCBASM")
    void elcbasMonthly() {
        assertThat(ViopContractSpecResolver.resolveUnderlier("F_ELCBAS0626")).isEqualTo("ELCBASM");
    }

    @Test
    @DisplayName("TLREF: F_TLREF1M0526 → TLREF_VADE_ICI (default)")
    void tlref() {
        assertThat(ViopContractSpecResolver.resolveUnderlier("F_TLREF1M0526")).isEqualTo("TLREF_VADE_ICI");
    }

    @Test
    @DisplayName("DİBS: F_TRT020926T17_KESN_T1_0626 → TRT020926T17")
    void dibs() {
        assertThat(ViopContractSpecResolver.resolveUnderlier("F_TRT020926T17_KESN_T1_0626"))
                .isEqualTo("TRT020926T17");
    }

    @Test
    @DisplayName("FX/index düz semboller: USDTRY, XU030 doğru çözülür")
    void fxAndIndex() {
        assertThat(ViopContractSpecResolver.resolveUnderlier("F_USDTRY0626")).isEqualTo("USDTRY");
        assertThat(ViopContractSpecResolver.resolveUnderlier("F_XU0300626")).isEqualTo("XU030");
    }

    @Test
    @DisplayName("Küçük harf ve boşluk normalize edilir → büyük harf kod")
    void caseAndWhitespaceNormalized() {
        assertThat(ViopContractSpecResolver.resolveUnderlier("  f_akbnk0626  ")).isEqualTo("AKBNK");
    }

    @Test
    @DisplayName("F_ prefix'siz sembol → null")
    void noPrefix() {
        assertThat(ViopContractSpecResolver.resolveUnderlier("AKBNK0626")).isNull();
    }

    @Test
    @DisplayName("null ve boş/whitespace girdi → null")
    void nullAndBlank() {
        assertThat(ViopContractSpecResolver.resolveUnderlier(null)).isNull();
        assertThat(ViopContractSpecResolver.resolveUnderlier("")).isNull();
        assertThat(ViopContractSpecResolver.resolveUnderlier("   ")).isNull();
    }

    @Test
    @DisplayName("Hiçbir kalıba uymayan sembol → null")
    void unparseable() {
        // 4 rakamlı tarih bloğu yok, formata uymuyor
        assertThat(ViopContractSpecResolver.resolveUnderlier("F_AKBNK")).isNull();
        assertThat(ViopContractSpecResolver.resolveUnderlier("F_")).isNull();
    }
}
