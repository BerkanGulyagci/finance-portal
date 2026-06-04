package com.finance.portal.portfolio.application.viop.spec;

import com.finance.portal.portfolio.application.viop.spec.ViopContractSpec.AssetClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ViopAssetClassGuesser} — sembol/koddan varlık sınıfı tahmini.
 * Desenler {@code viop-contract-specs.yml}'deki gerçek kodlarla doğrulanır.
 */
class ViopAssetClassGuesserTest {

    @Test
    @DisplayName("FX: döviz çiftleri (USDTRY, EURUSD, GBPUSD)")
    void fxPairs() {
        assertThat(ViopAssetClassGuesser.guess("F_USDTRY0626", "USDTRY")).isEqualTo(AssetClass.FX);
        assertThat(ViopAssetClassGuesser.guess("F_EURUSD0626", "EURUSD")).isEqualTo(AssetClass.FX);
        assertThat(ViopAssetClassGuesser.guess("F_GBPUSD0626", "GBPUSD")).isEqualTo(AssetClass.FX);
        assertThat(ViopAssetClassGuesser.guess("F_CNHTRY0626", "CNHTRY")).isEqualTo(AssetClass.FX);
    }

    @Test
    @DisplayName("PRECIOUS_METAL: XAU/XAG/XPT/XPD/XCU/GAU prefix")
    void preciousMetals() {
        assertThat(ViopAssetClassGuesser.guess("F_XAUTRY0626", "XAUTRY")).isEqualTo(AssetClass.PRECIOUS_METAL);
        assertThat(ViopAssetClassGuesser.guess("F_XAGUSD0626", "XAGUSD")).isEqualTo(AssetClass.PRECIOUS_METAL);
        assertThat(ViopAssetClassGuesser.guess("F_XPTUSD0626", "XPTUSD")).isEqualTo(AssetClass.PRECIOUS_METAL);
        assertThat(ViopAssetClassGuesser.guess("F_GAUTRY0626", "GAUTRY")).isEqualTo(AssetClass.PRECIOUS_METAL);
    }

    @Test
    @DisplayName("INDEX: endeks kodları (XU030, XLBNK, SASX10)")
    void indices() {
        assertThat(ViopAssetClassGuesser.guess("F_XU0300626", "XU030")).isEqualTo(AssetClass.INDEX);
        assertThat(ViopAssetClassGuesser.guess("F_XLBNK0626", "XLBNK")).isEqualTo(AssetClass.INDEX);
        assertThat(ViopAssetClassGuesser.guess("F_SASX100626", "SASX10")).isEqualTo(AssetClass.INDEX);
    }

    @Test
    @DisplayName("ENERGY: ELCBAS* (elektrik)")
    void energy() {
        assertThat(ViopAssetClassGuesser.guess("F_ELCBASM0626", "ELCBASM")).isEqualTo(AssetClass.ENERGY);
        assertThat(ViopAssetClassGuesser.guess("F_ELCBASQ326", "ELCBASQ")).isEqualTo(AssetClass.ENERGY);
    }

    @Test
    @DisplayName("RATE: TLREF*")
    void rate() {
        assertThat(ViopAssetClassGuesser.guess("F_TLREF1M0526", "TLREF_VADE_ICI")).isEqualTo(AssetClass.RATE);
    }

    @Test
    @DisplayName("BOND: TRT* (DİBS)")
    void bond() {
        assertThat(ViopAssetClassGuesser.guess("F_TRT020926T17_KESN_T1_0626", "TRT020926T17"))
                .isEqualTo(AssetClass.BOND);
    }

    @Test
    @DisplayName("SINGLE_STOCK: hisse kodları (AKBNK, THYAO, ASELS)")
    void singleStock() {
        assertThat(ViopAssetClassGuesser.guess("F_AKBNK0626", "AKBNK")).isEqualTo(AssetClass.SINGLE_STOCK);
        assertThat(ViopAssetClassGuesser.guess("F_THYAO0626", "THYAO")).isEqualTo(AssetClass.SINGLE_STOCK);
        assertThat(ViopAssetClassGuesser.guess("F_ASELS0626", "ASELS")).isEqualTo(AssetClass.SINGLE_STOCK);
    }

    @Test
    @DisplayName("code null ise sembolden türetir (F_ + vade soyma)")
    void derivesFromSymbolWhenCodeNull() {
        assertThat(ViopAssetClassGuesser.guess("F_AKBNK0626", null)).isEqualTo(AssetClass.SINGLE_STOCK);
        assertThat(ViopAssetClassGuesser.guess("F_USDTRY0626", null)).isEqualTo(AssetClass.FX);
        assertThat(ViopAssetClassGuesser.guess("F_XAUTRY0626", null)).isEqualTo(AssetClass.PRECIOUS_METAL);
    }

    @Test
    @DisplayName("tahmin edilemeyen/boş → SINGLE_STOCK (en güvenli varsayılan) veya null")
    void edgeCases() {
        // tamamen boş → null
        assertThat(ViopAssetClassGuesser.guess(null, null)).isNull();
        assertThat(ViopAssetClassGuesser.guess("", "")).isNull();
        // tanınmayan ama kod var → hisse (en yaygın sınıf)
        assertThat(ViopAssetClassGuesser.guess("ANYTHING", "ANYTHING")).isEqualTo(AssetClass.SINGLE_STOCK);
    }
}
