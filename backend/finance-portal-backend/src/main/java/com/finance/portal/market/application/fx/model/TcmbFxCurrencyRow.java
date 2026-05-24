package com.finance.portal.market.application.fx.model;

public class TcmbFxCurrencyRow {

    private String currencyCode;
    private Integer unit;
    private String forexBuying;
    private String forexSelling;
    private String banknoteBuying;
    private String banknoteSelling;

    public TcmbFxCurrencyRow() {
    }

    public TcmbFxCurrencyRow(String currencyCode, Integer unit, String forexBuying, String forexSelling) {
        this(currencyCode, unit, forexBuying, forexSelling, null, null);
    }

    public TcmbFxCurrencyRow(String currencyCode, Integer unit, String forexBuying, String forexSelling,
                             String banknoteBuying, String banknoteSelling) {
        this.currencyCode = currencyCode;
        this.unit = unit;
        this.forexBuying = forexBuying;
        this.forexSelling = forexSelling;
        this.banknoteBuying = banknoteBuying;
        this.banknoteSelling = banknoteSelling;
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

    public String getBanknoteBuying() {
        return banknoteBuying;
    }

    public void setBanknoteBuying(String banknoteBuying) {
        this.banknoteBuying = banknoteBuying;
    }

    public String getBanknoteSelling() {
        return banknoteSelling;
    }

    public void setBanknoteSelling(String banknoteSelling) {
        this.banknoteSelling = banknoteSelling;
    }
}
