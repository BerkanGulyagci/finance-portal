package com.finance.portal.market.application.fx.model;

import java.math.BigDecimal;

public class FxRateItem {

    private String symbol;
    private BigDecimal buy;
    private BigDecimal sell;
    private int unit;
    /** Efektif (banknot) alış — yalnızca TCMB kaynağında doludur, aksi halde null. */
    private BigDecimal effectiveBuy;
    /** Efektif (banknot) satış — yalnızca TCMB kaynağında doludur, aksi halde null. */
    private BigDecimal effectiveSell;

    public FxRateItem() {
    }

    public FxRateItem(String symbol, BigDecimal buy, BigDecimal sell, int unit) {
        this.symbol = symbol;
        this.buy = buy;
        this.sell = sell;
        this.unit = unit;
    }

    public FxRateItem(String symbol, BigDecimal buy, BigDecimal sell, int unit,
                      BigDecimal effectiveBuy, BigDecimal effectiveSell) {
        this.symbol = symbol;
        this.buy = buy;
        this.sell = sell;
        this.unit = unit;
        this.effectiveBuy = effectiveBuy;
        this.effectiveSell = effectiveSell;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public BigDecimal getBuy() {
        return buy;
    }

    public void setBuy(BigDecimal buy) {
        this.buy = buy;
    }

    public BigDecimal getSell() {
        return sell;
    }

    public void setSell(BigDecimal sell) {
        this.sell = sell;
    }

    public int getUnit() {
        return unit;
    }

    public void setUnit(int unit) {
        this.unit = unit;
    }

    public BigDecimal getEffectiveBuy() {
        return effectiveBuy;
    }

    public void setEffectiveBuy(BigDecimal effectiveBuy) {
        this.effectiveBuy = effectiveBuy;
    }

    public BigDecimal getEffectiveSell() {
        return effectiveSell;
    }

    public void setEffectiveSell(BigDecimal effectiveSell) {
        this.effectiveSell = effectiveSell;
    }
}
