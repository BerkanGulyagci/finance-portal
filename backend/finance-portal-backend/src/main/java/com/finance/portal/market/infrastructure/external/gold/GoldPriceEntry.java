package com.finance.portal.market.infrastructure.external.gold;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * canlialtinfiyatlari.com'dan parse edilen tek bir altın fiyat satırı.
 */
@Getter
@AllArgsConstructor
public class GoldPriceEntry {

    private final String name;
    private final BigDecimal buy;
    private final BigDecimal sell;
    private final BigDecimal changePercent; // nullable
    private final String time;              // nullable, "HH:mm:ss"

    /** Orta fiyat (alış+satış)/2 */
    public BigDecimal getMid() {
        if (buy == null || sell == null) return buy != null ? buy : sell;
        return buy.add(sell).divide(java.math.BigDecimal.valueOf(2), 2, java.math.RoundingMode.HALF_UP);
    }
}
