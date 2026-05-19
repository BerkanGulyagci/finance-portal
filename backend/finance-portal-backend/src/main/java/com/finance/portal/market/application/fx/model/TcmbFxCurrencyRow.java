package com.finance.portal.market.application.fx.model;

public class TcmbFxCurrencyRow {

    private String currencyCode;
    private Integer unit;
    private String forexBuying;
    private String forexSelling;

    public TcmbFxCurrencyRow() {
    }

    public TcmbFxCurrencyRow(String currencyCode, Integer unit, String forexBuying, String forexSelling) {
        this.currencyCode = currencyCode;
        this.unit = unit;
        this.forexBuying = forexBuying;
        this.forexSelling = forexSelling;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public void setCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
    }

    public Integer getUnit() {
        return unit;
    }

    public void setUnit(Integer unit) {
        this.unit = unit;
    }

    public String getForexBuying() {
        return forexBuying;
    }

    public void setForexBuying(String forexBuying) {
        this.forexBuying = forexBuying;
    }

    public String getForexSelling() {
        return forexSelling;
    }

    public void setForexSelling(String forexSelling) {
        this.forexSelling = forexSelling;
    }
}
