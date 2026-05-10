package com.finance.portal.market.infrastructure.external.precious;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Platin/Paladyum için günlük fiyat noktası.
 * Aynı tarihe ait MTL kayıtları gruplandırılarak üretilir.
 *
 * OHLC yok — sadece tek fiyat değeri (referans fiyat).
 * validPrice = usdOns > 0 OR tryKg > 0
 */
@Data
@NoArgsConstructor
public class BistMetalDailyPoint {

    private PreciousMetalType metalType;
    private String date;

    /** USD/Ons */
    private BigDecimal usdOns;

    /** TRY/Kg */
    private BigDecimal tryKg;

    /** TRY/Gram = tryKg / 1000 */
    private BigDecimal tryGram;

    /** EUR/Ons */
    private BigDecimal eurOns;

    /** Geçerli fiyat: usdOns > 0 OR tryKg > 0 */
    private boolean validPrice;

    /** Veri kaynağı tipi */
    private String dataSourceType = "BIST_METAL_FIYATLARI";
}
