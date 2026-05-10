package com.finance.portal.market.infrastructure.external.precious;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * BIST metal-fiyatlari.php endpoint'inden gelen tek bir fiyat kaydı.
 *
 * <pre>
 * GET https://www.borsaistanbul.com/metal-fiyatlari.php
 *     ?op=fetchMetalFiyatlari
 *     &startDate={startDate}
 *     &endDate={endDate}
 *     &priceType=PT|PD
 * </pre>
 *
 * Aynı tarih için birden fazla kayıt gelir (USD/OZ, TRY/KG, EUR/OZ).
 * Sadece priceRef=MTL kayıtları kullanılır.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BistMetalFiyatlariPoint {

    @JsonProperty("id")
    private Long id;

    /** Tarih — "yyyy-MM-dd" */
    @JsonProperty("priceDate")
    private String priceDate;

    /** MTL = ana metal fiyatı, REF = referans fiyat (kullanılmaz) */
    @JsonProperty("priceRef")
    private String priceRef;

    /** PT = Platin, PD = Paladyum */
    @JsonProperty("priceType")
    private String priceType;

    /** USD, TRY, EUR */
    @JsonProperty("priceCurrency")
    private String priceCurrency;

    /** OZ = ons, KG = kilogram */
    @JsonProperty("priceWeight")
    private String priceWeight;

    /** Fiyat değeri */
    @JsonProperty("priceValue")
    private BigDecimal priceValue;
}
