package com.finance.portal.market.infrastructure.external.fx.dto;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.List;

@JacksonXmlRootElement(localName = "Tarih_Date")
public class TcmbExchangeRatesDto {

    @JacksonXmlProperty(isAttribute = true, localName = "Date")
    private String date;

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "Currency")
    private List<TcmbCurrencyDto> currencies;

    public TcmbExchangeRatesDto() {
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public List<TcmbCurrencyDto> getCurrencies() {
        return currencies;
    }

    public void setCurrencies(List<TcmbCurrencyDto> currencies) {
        this.currencies = currencies;
    }
}
