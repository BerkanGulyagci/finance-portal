package com.finance.portal.portfolio.application.viop.spec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ViopContractSpec} statik fabrika + currency-tahmin testleri.
 *
 * <p>Odak: fallback'lerin USD-kote kontratı "TRY" sanma tuzağına karşı korunması.
 * Eskiden {@code fallback}/{@code similarTypeFallback} currency'yi koşulsuz "TRY" yazıyordu;
 * YAML'da olmayan bir USD-kote vade + scrape çökmesinde USD fiyat TL sanılıp FX atlanıyordu
 * (~kur katı eksik değer). {@link ViopContractSpec#guessCurrencyFromCode} bunu kapatır.
 */
class ViopContractSpecTest {

    @Test
    @DisplayName("guessCurrencyFromCode: …USD ile biten (ve TRY değil) → USD")
    void guessCurrency_usdQuoted() {
        assertThat(ViopContractSpec.guessCurrencyFromCode("XAUUSD")).isEqualTo("USD");
        assertThat(ViopContractSpec.guessCurrencyFromCode("EURUSD")).isEqualTo("USD");
        assertThat(ViopContractSpec.guessCurrencyFromCode("GBPUSD")).isEqualTo("USD");
        assertThat(ViopContractSpec.guessCurrencyFromCode("xagusd")).isEqualTo("USD"); // case-insensitive
    }

    @Test
    @DisplayName("guessCurrencyFromCode: TRY-kote (USDTRY dahil) ve bilinmeyen → TRY")
    void guessCurrency_tryQuoted() {
        assertThat(ViopContractSpec.guessCurrencyFromCode("USDTRY")).isEqualTo("TRY"); // …TRY → TL-kote!
        assertThat(ViopContractSpec.guessCurrencyFromCode("XAUTRY")).isEqualTo("TRY");
        assertThat(ViopContractSpec.guessCurrencyFromCode("AKBNK")).isEqualTo("TRY");
        assertThat(ViopContractSpec.guessCurrencyFromCode("XU030")).isEqualTo("TRY");
        assertThat(ViopContractSpec.guessCurrencyFromCode(null)).isEqualTo("TRY"); // güvenli varsayılan
    }

    @Test
    @DisplayName("fallback: USD-kote koddan currency=USD türetir (TRY sanmaz)")
    void fallback_usdQuoted_currencyUsd() {
        assertThat(ViopContractSpec.fallback("XAUUSD").currency()).isEqualTo("USD");
        assertThat(ViopContractSpec.fallback("AKBNK").currency()).isEqualTo("TRY");
    }

    @Test
    @DisplayName("similarTypeFallback: USD-kote koddan currency=USD türetir (TRY sanmaz)")
    void similarTypeFallback_usdQuoted_currencyUsd() {
        assertThat(ViopContractSpec.similarTypeFallback("EURUSD", ViopContractSpec.AssetClass.FX).currency())
                .isEqualTo("USD");
        assertThat(ViopContractSpec.similarTypeFallback("USDTRY", ViopContractSpec.AssetClass.FX).currency())
                .isEqualTo("TRY"); // USDTRY TL-kote
    }
}
