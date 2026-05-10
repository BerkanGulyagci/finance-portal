package com.finance.portal.market.infrastructure.external.precious;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * BIST Kıymetli Madenler Veri Sorgulama endpoint'inden gelen tek bir günlük kayıt.
 * Altın, Gümüş, Platin, Paladyum için ortak DTO.
 *
 * <p>Ham BIST alanları (lowRaw, highRaw, closeRaw, weightedAverageRaw) her zaman dolu.
 * Türetilmiş alanlar (gramXxx, onsXxx) client tarafından doldurulur.
 *
 * <p><b>validPrice kuralı:</b>
 * Gümüş gibi bazı metallerde kpo=0 ve sum_o=0 gelebilir (hacim var ama fiyat yok).
 * Bu kayıtlar grafik için kullanılmamalı. {@code validPrice=false} olanlar filtrelenir.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BistPreciousMetalPoint {

    // ── Metal / birim metadata ────────────────────────────────────────────────

    /** Hangi metal (client tarafından set edilir) */
    private PreciousMetalType metalType;

    /** Hangi birim (client tarafından set edilir) */
    private PriceUnit unit;

    // ── Ham BIST alanları ─────────────────────────────────────────────────────

    /** Tarih — "yyyy-MM-dd" */
    @JsonProperty("guntar")
    private String date;

    /** En düşük fiyat (TL/Kg veya USD/Ons) */
    @JsonProperty("min_f")
    private BigDecimal lowRaw;

    /** En yüksek fiyat */
    @JsonProperty("max_f")
    private BigDecimal highRaw;

    /** Kapanış fiyatı */
    @JsonProperty("kpo")
    private BigDecimal closeRaw;

    /** Ağırlıklı ortalama fiyat */
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

    // ── Geçerli fiyat kontrolü ────────────────────────────────────────────────

    /**
     * Geçerli fiyat kaydı mı?
     * validPrice = closeRaw > 0 OR weightedAverageRaw > 0
     * Gümüş gibi metallerde bazı günler 0 gelebilir — bu kayıtlar grafikte kullanılmaz.
     */
    private boolean validPrice;

    // ── TL/Kg modu — gram değerleri (/1000) ──────────────────────────────────

    /** closeRaw / 1000 */
    private BigDecimal gramClose;

    /** weightedAverageRaw / 1000 */
    private BigDecimal gramWeightedAverage;

    /** lowRaw / 1000 */
    private BigDecimal gramLow;

    /** highRaw / 1000 */
    private BigDecimal gramHigh;

    // ── USD/Ons modu — ham değerler kopyalanır ────────────────────────────────

    /** Kapanış USD/Ons */
    private BigDecimal closeUsdOns;

    /** Ağırlıklı ortalama USD/Ons */
    private BigDecimal weightedAverageUsdOns;

    /** En düşük USD/Ons */
    private BigDecimal lowUsdOns;

    /** En yüksek USD/Ons */
    private BigDecimal highUsdOns;

    /** İşlem hacmi USD */
    private BigDecimal volumeUsd;

    // ── Geriye dönük uyumluluk — BistGoldHistoricalPoint alias'ları ──────────

    public BigDecimal getLowTryKg()              { return lowRaw; }
    public BigDecimal getHighTryKg()             { return highRaw; }
    public BigDecimal getCloseTryKg()            { return closeRaw; }
    public BigDecimal getWeightedAverageTryKg()  { return weightedAverageRaw; }
    public BigDecimal getVolumeTry()             { return volumeRaw; }

    // Altın uyumluluğu için gram alias'ları
    public BigDecimal getGramCloseTry()           { return gramClose; }
    public BigDecimal getGramWeightedAverageTry() { return gramWeightedAverage; }
    public BigDecimal getGramLowTry()             { return gramLow; }
    public BigDecimal getGramHighTry()            { return gramHigh; }

    public void setGramCloseTry(BigDecimal v)           { this.gramClose = v; }
    public void setGramWeightedAverageTry(BigDecimal v) { this.gramWeightedAverage = v; }
    public void setGramLowTry(BigDecimal v)             { this.gramLow = v; }
    public void setGramHighTry(BigDecimal v)            { this.gramHigh = v; }
}
