package com.finance.portal.market.infrastructure.external.gold;

import java.math.BigDecimal;

/**
 * canlialtinfiyatlari.com'dan parse edilen tek bir altın fiyat satırı.
 */
public class GoldPriceEntry {

    private final String name;
    private final BigDecimal buy;
    private final BigDecimal sell;
    private final BigDecimal changePercent; // nullable
    private final String time;              // nullable, "HH:mm:ss"

    public GoldPriceEntry(String name, BigDecimal buy, BigDecimal sell,
                          BigDecimal changePercent, String time) {
        this.name = name;
        this.buy = buy;
        this.sell = sell;
        this.changePercent = changePercent;
        this.time = time;
    }

    public String getName() { return name; }
    public BigDecimal getBuy() { return buy; }
    public BigDecimal getSell() { return sell; }
    public BigDecimal getChangePercent() { return changePercent; }
    public String getTime() { return time; }

    /** Orta fiyat (alış+satış)/2 */
    public BigDecimal getMid() {
        if (buy == null || sell == null) return buy != null ? buy : sell;
        return buy.add(sell).divide(java.math.BigDecimal.valueOf(2), 2, java.math.RoundingMode.HALF_UP);
    }
}
