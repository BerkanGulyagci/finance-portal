package com.finance.portal.market.infrastructure.external.fx.dto;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

public class TcmbCurrencyDto {

    @JacksonXmlProperty(isAttribute = true, localName = "CurrencyCode")
    private String currencyCode;

    @JacksonXmlProperty(localName = "Unit")
    private Integer unit;

    @JacksonXmlProperty(localName = "ForexBuying")
    private String forexBuying;

    @JacksonXmlProperty(localName = "ForexSelling")
    private String forexSelling;

    @JacksonXmlProperty(localName = "BanknoteBuying")
    private String banknoteBuying;

    @JacksonXmlProperty(localName = "BanknoteSelling")
    private String banknoteSelling;

    public TcmbCurrencyDto() {
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
