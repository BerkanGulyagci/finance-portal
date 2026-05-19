package com.finance.portal.market.application.fx.model;

import java.math.BigDecimal;

public class FxRateItem {

    private String symbol;
    private BigDecimal buy;
    private BigDecimal sell;
    private int unit;

    public FxRateItem() {
    }

    public FxRateItem(String symbol, BigDecimal buy, BigDecimal sell, int unit) {
        this.symbol = symbol;
        this.buy = buy;
        this.sell = sell;
        this.unit = unit;
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
}
