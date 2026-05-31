package com.finance.portal.portfolio.application.viop.spec;

import java.math.BigDecimal;

/**
 * VİOP sözleşme spesifikasyonu (dayanak varlık başına sabit metadata).
 *
 * <p>Veri kaynağı: {@code resources/viop-contract-specs.yml}. Yılda 1-2 kez review edilir.
 * Multiplier ve marginRate, sembol prefix eşlemesiyle her vade için aynı dayanağa düşer
 * (örn. F_AKBNK0626, F_AKBNK0726, F_AKBNK0826 hepsi AKBNK spec'ine bakar).
 *
 * <p>{@code code} = dayanak varlık kodu (AKBNK, USDTRY, XU030, XAUTRY, ELCBASQ, …).
 * {@code multiplier} = bir kontratın temsil ettiği birim sayısı (100 hisse, 10 endeks
 * puanı, 1000 USD, 1 gram, 220.8 MWh, vb.). {@code marginRate} = başlangıç teminat
 * oranı (decimal, örn. 0.146 = %14.6).
 */
public record ViopContractSpec(
        String code,
        AssetClass assetClass,
        BigDecimal multiplier,
        BigDecimal marginRate,
        String currency,
        SettlementType settlementType
) {

    public enum AssetClass {
        SINGLE_STOCK,
        INDEX,
        FX,
        PRECIOUS_METAL,
        ENERGY,
        RATE,
        BOND
    }

    public enum SettlementType {
        CASH,
        PHYSICAL
    }

    /**
     * Sembol için bulunamayan spec yerine güvenli fallback — multiplier=1, margin=%15
     * (single-stock orta tahmini). Hesap "yanlış" değil "yaklaşık" olur; loglardan
     * fark edilebilir.
     */
    public static ViopContractSpec fallback(String code) {
        return new ViopContractSpec(
                code,
                AssetClass.SINGLE_STOCK,
                BigDecimal.ONE,
                new BigDecimal("0.15"),
                "TRY",
                SettlementType.PHYSICAL
        );
    }
}
