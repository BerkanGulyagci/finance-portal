package com.finance.portal.portfolio.infrastructure.viop.config;

import com.finance.portal.portfolio.application.viop.spec.ViopContractSpec;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code viop-contract-specs.yml} dosyasını Spring Boot @ConfigurationProperties ile yükler.
 * YAML kökü {@code viop.contract-specs[]} altında her satır {@link Entry}'e bağlanır.
 *
 * <p>Lombok kullanılmadı; bean validation veya constructor-binding'e gerek yok — alanlar
 * setter ile doldurulur. Spring Boot YAML loader'ı camelCase ↔ kebab-case eşleşmesini
 * otomatik yapar.
 */
@ConfigurationProperties(prefix = "viop")
public class ViopContractSpecProperties {

    private List<Entry> contractSpecs = new ArrayList<>();

    public List<Entry> getContractSpecs() {
        return contractSpecs;
    }

    public void setContractSpecs(List<Entry> contractSpecs) {
        this.contractSpecs = contractSpecs != null ? contractSpecs : new ArrayList<>();
    }

    /**
     * YAML satırı için DTO. {@link #toSpec()} ile domain record'a çevrilir.
     */
    public static class Entry {
        private String code;
        private String assetClass;
        private BigDecimal multiplier;
        private BigDecimal marginRate;
        private String currency;
        private String settlementType;

        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getAssetClass() { return assetClass; }
        public void setAssetClass(String assetClass) { this.assetClass = assetClass; }
        public BigDecimal getMultiplier() { return multiplier; }
        public void setMultiplier(BigDecimal multiplier) { this.multiplier = multiplier; }
        public BigDecimal getMarginRate() { return marginRate; }
        public void setMarginRate(BigDecimal marginRate) { this.marginRate = marginRate; }
        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }
        public String getSettlementType() { return settlementType; }
        public void setSettlementType(String settlementType) { this.settlementType = settlementType; }

        public ViopContractSpec toSpec() {
            return new ViopContractSpec(
                    code,
                    ViopContractSpec.AssetClass.valueOf(assetClass),
                    multiplier != null ? multiplier : BigDecimal.ONE,
                    marginRate != null ? marginRate : new BigDecimal("0.15"),
                    currency != null ? currency : "TRY",
                    ViopContractSpec.SettlementType.valueOf(settlementType != null ? settlementType : "PHYSICAL")
            );
        }
    }
}
