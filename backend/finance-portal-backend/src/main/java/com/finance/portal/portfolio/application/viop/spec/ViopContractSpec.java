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
 *
 * <p>{@code marginAmount} = başlangıç teminatının TL TUTARI (İş Yatırım'dan scrape edilen
 * sözleşmeler için doldurulur; YAML'dan gelen statik spec'lerde {@code null}). Var olduğunda
 * teminat hesabı fiyattan BAĞIMSIZ olur: {@code marginPosted = qty × marginAmount} (Takasbank'ın
 * verdiği gerçek tutar). Null ise eski oran yolu kullanılır: {@code qty × fiyat × mult × marginRate}.
 */
public record ViopContractSpec(
        String code,
        AssetClass assetClass,
        BigDecimal multiplier,
        BigDecimal marginRate,
        String currency,
        SettlementType settlementType,
        BigDecimal marginAmount
) {

    /**
     * Geriye uyumlu constructor — {@code marginAmount} olmadan (YAML statik spec'leri ve mevcut
     * çağrılar için). {@code marginAmount = null} → eski oran-bazlı teminat hesabı kullanılır.
     */
    public ViopContractSpec(String code, AssetClass assetClass, BigDecimal multiplier,
                            BigDecimal marginRate, String currency, SettlementType settlementType) {
        this(code, assetClass, multiplier, marginRate, currency, settlementType, null);
    }

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

    /**
     * Benzer-tür fallback: scrape de YAML de yokken, sözleşmenin tahmini varlık sınıfına göre
     * o sınıfın TİPİK (YAML medyanı) teminat oranını kullanır. Multiplier yine 1 (bilinmiyor),
     * ama oran sınıfa özgü olduğundan düz %15 fallback'ten daha isabetli.
     *
     * <p>Oranlar {@code viop-contract-specs.yml}'deki her assetClass'ın MEDYANINDAN türetildi
     * (47 hisse→%14.1, 5 endeks→%11.9, 6 döviz→%11.3, 7 maden→%14.0, 3 enerji→%11.0, ...).
     *
     * @param code         dayanak kodu (log/iz için)
     * @param assetClass   tahmini varlık sınıfı (sembol prefix'inden çıkarılır)
     */
    public static ViopContractSpec similarTypeFallback(String code, AssetClass assetClass) {
        AssetClass ac = assetClass != null ? assetClass : AssetClass.SINGLE_STOCK;
        return new ViopContractSpec(
                code,
                ac,
                BigDecimal.ONE,
                typicalMarginRate(ac),
                "TRY",
                ac == AssetClass.SINGLE_STOCK ? SettlementType.PHYSICAL : SettlementType.CASH
        );
    }

    /** Varlık sınıfının tipik başlangıç teminat oranı (YAML medyanı). */
    public static BigDecimal typicalMarginRate(AssetClass assetClass) {
        return switch (assetClass) {
            case SINGLE_STOCK   -> new BigDecimal("0.141");
            case INDEX          -> new BigDecimal("0.119");
            case FX             -> new BigDecimal("0.113");
            case PRECIOUS_METAL -> new BigDecimal("0.140");
            case ENERGY         -> new BigDecimal("0.110");
            case RATE           -> new BigDecimal("0.090");
            case BOND           -> new BigDecimal("0.150");
        };
    }
}
