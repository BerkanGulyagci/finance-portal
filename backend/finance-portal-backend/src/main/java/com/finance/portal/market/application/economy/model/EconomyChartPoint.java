package com.finance.portal.market.application.economy.model;

import java.math.BigDecimal;

/**
 * Grafik için tek bir veri noktası (dönem etiketi + değer).
 * Redis cache uyumu için no-arg constructor + getter/setter.
 */
public class EconomyChartPoint {

    private String period;
    private BigDecimal value;

    public EconomyChartPoint() {
    }

    public EconomyChartPoint(String period, BigDecimal value) {
        this.period = period;
        this.value = value;
    }

    public String getPeriod() { return period; }
    public void setPeriod(String period) { this.period = period; }

    public BigDecimal getValue() { return value; }
    public void setValue(BigDecimal value) { this.value = value; }
}
