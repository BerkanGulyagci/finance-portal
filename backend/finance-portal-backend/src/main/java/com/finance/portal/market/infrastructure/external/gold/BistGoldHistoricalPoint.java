package com.finance.portal.market.infrastructure.external.gold;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * BIST Kıymetli Madenler Veri Sorgulama endpoint'inden gelen tek bir günlük kayıt.
 *
 * İki farklı endpoint için kullanılır:
 *   f_tipit=L/K  → TL/Kg  (gram altın)
 *   f_tipit=$/O  → USD/Ons (ons altın)
 *
 * TL/Kg modunda gramXxx alanları /1000 ile doldurulur.
 * USD/Ons modunda onsXxx alanları doğrudan ham değerlerdir.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BistGoldHistoricalPoint {

    /** Tarih — "yyyy-MM-dd" */
    @JsonProperty("guntar")
    private String date;

    /** En düşük fiyat (TL/Kg veya USD/Ons — endpoint'e göre) */
    @JsonProperty("min_f")
    private BigDecimal lowRaw;

    /** En yüksek fiyat (TL/Kg veya USD/Ons) */
    @JsonProperty("max_f")
    private BigDecimal highRaw;

    /** Kapanış fiyatı (TL/Kg veya USD/Ons) */
    @JsonProperty("kpo")
    private BigDecimal closeRaw;

    /** Ağırlıklı ortalama fiyat (TL/Kg veya USD/Ons) */
    @JsonProperty("sum_o")
    private BigDecimal weightedAverageRaw;

    /** Ağırlıklı ortalama fiyat 2 */
    @JsonProperty("sum_o2")
    private BigDecimal weightedAverage2Raw;

    /** İşlem hacmi (TL veya USD) */
    @JsonProperty("sum_h")
    private BigDecimal volumeRaw;

    /** İşlem miktarı Kg */
    @JsonProperty("sum_m")
    private BigDecimal quantityKg;

    /** İşlem sayısı */
    @JsonProperty("cnt_i")
    private Integer transactionCount;

    /** Ağırlıklı ortalama Kg/USD */
    @JsonProperty("kgusd")
    private BigDecimal weightedAverageKgUsd;

    /** T+0 fiyat */
    @JsonProperty("t0fiyat")
    private BigDecimal t0PriceRaw;

    // ── TL/Kg modu — gram değerleri (client tarafından doldurulur) ────────────

    /** closeRaw / 1000  (TL/Kg modunda) */
    private BigDecimal gramCloseTry;

    /** weightedAverageRaw / 1000  ← officialPureGoldGramTry referansı */
    private BigDecimal gramWeightedAverageTry;

    /** lowRaw / 1000 */
    private BigDecimal gramLowTry;

    /** highRaw / 1000 */
    private BigDecimal gramHighTry;

    // ── USD/Ons modu — ons değerleri (ham değerler, dönüşüm gerekmez) ─────────

    /** Kapanış USD/Ons  = closeRaw  ($/O modunda) */
    private BigDecimal closeUsdOns;

    /** Ağırlıklı ortalama USD/Ons = weightedAverageRaw */
    private BigDecimal weightedAverageUsdOns;

    /** En düşük USD/Ons = lowRaw */
    private BigDecimal lowUsdOns;

    /** En yüksek USD/Ons = highRaw */
    private BigDecimal highUsdOns;

    /** İşlem hacmi USD = volumeRaw */
    private BigDecimal volumeUsd;

    // ── Geriye dönük uyumluluk (TL/Kg modu için eski alan adları) ─────────────

    /** @deprecated lowRaw kullan */
    public BigDecimal getLowTryKg()  { return lowRaw; }
    /** @deprecated highRaw kullan */
    public BigDecimal getHighTryKg() { return highRaw; }
    /** @deprecated closeRaw kullan */
    public BigDecimal getCloseTryKg() { return closeRaw; }
    /** @deprecated weightedAverageRaw kullan */
    public BigDecimal getWeightedAverageTryKg() { return weightedAverageRaw; }
    /** @deprecated volumeRaw kullan */
    public BigDecimal getVolumeTry() { return volumeRaw; }
}
