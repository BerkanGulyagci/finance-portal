package com.finance.portal.market.application.precious;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Platin/Paladyum tarihsel fiyat noktası.
 * OHLC yok — sadece tek referans fiyat değeri.
 * Grafik için seçilen currency'ye göre value alanı doldurulur.
 */
@Data
@NoArgsConstructor
public class PreciousMetalHistoryPoint implements Serializable {

    private String date;

    /** Seçilen currency'ye göre grafik değeri (TRY gram, USD ons veya EUR ons) */
    private BigDecimal value;

    /** TRY/Gram */
    private BigDecimal tryGram;

    /** TRY/Kg */
    private BigDecimal tryKg;

    /** USD/Ons */
    private BigDecimal usdOns;

    /** EUR/Ons */
    private BigDecimal eurOns;
}
