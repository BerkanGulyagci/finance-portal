package com.finance.portal.market.application.silver;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Gümüş tarihsel fiyat noktası.
 * validPrice=false kayıtlar servis katmanında filtrelenir.
 */
@Data
@NoArgsConstructor
public class SilverHistoryPoint implements Serializable {

    private String date;

    /** TRY modunda: gramClose, USD modunda: closeUsdOns */
    private BigDecimal close;
    private BigDecimal open;   // sentetik: önceki gün close
    private BigDecimal high;
    private BigDecimal low;
    private Long volume;

    /** TRY modu: ağırlıklı ortalama gram TL */
    private BigDecimal weightedAverage;

    /** USD modu: ağırlıklı ortalama USD/Ons */
    private BigDecimal weightedAverageUsdOns;

    /** Ham TL/Kg değerleri */
    private BigDecimal closeTryKg;
    private BigDecimal weightedAverageTryKg;
}
