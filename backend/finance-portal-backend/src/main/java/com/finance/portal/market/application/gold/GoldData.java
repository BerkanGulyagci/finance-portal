package com.finance.portal.market.application.gold;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

public class GoldData implements Serializable {

    // ONS fiyatı (USD)
    private BigDecimal priceUsd;
    // ONS fiyatı (TRY)
    private BigDecimal priceTry;
    // Gram altın (TRY)
    private BigDecimal gramTry;
    // Çeyrek altın (TRY)
    private BigDecimal ceyrekTry;
    // Yarım altın (TRY)
    private BigDecimal yarimTry;
    // Tam altın (TRY)
    private BigDecimal tamTry;

    private BigDecimal dayHigh;
    private BigDecimal dayLow;
    private BigDecimal previousClose;
    private BigDecimal change;
    private BigDecimal changePercent;
    private String currency;
    private String asOf;

    // Tarihsel kapanış fiyatları (USD) — grafik için
    private List<Long> timestamps;
    private List<BigDecimal> closePrices;

    public GoldData() {}

    public BigDecimal getPriceUsd() { return priceUsd; }
    public void setPriceUsd(BigDecimal priceUsd) { this.priceUsd = priceUsd; }
    public BigDecimal getPriceTry() { return priceTry; }
    public void setPriceTry(BigDecimal priceTry) { this.priceTry = priceTry; }
    public BigDecimal getGramTry() { return gramTry; }
    public void setGramTry(BigDecimal gramTry) { this.gramTry = gramTry; }
    public BigDecimal getCeyrekTry() { return ceyrekTry; }
    public void setCeyrekTry(BigDecimal ceyrekTry) { this.ceyrekTry = ceyrekTry; }
    public BigDecimal getYarimTry() { return yarimTry; }
    public void setYarimTry(BigDecimal yarimTry) { this.yarimTry = yarimTry; }
    public BigDecimal getTamTry() { return tamTry; }
    public void setTamTry(BigDecimal tamTry) { this.tamTry = tamTry; }
    public BigDecimal getDayHigh() { return dayHigh; }
    public void setDayHigh(BigDecimal dayHigh) { this.dayHigh = dayHigh; }
    public BigDecimal getDayLow() { return dayLow; }
    public void setDayLow(BigDecimal dayLow) { this.dayLow = dayLow; }
    public BigDecimal getPreviousClose() { return previousClose; }
    public void setPreviousClose(BigDecimal previousClose) { this.previousClose = previousClose; }
    public BigDecimal getChange() { return change; }
    public void setChange(BigDecimal change) { this.change = change; }
    public BigDecimal getChangePercent() { return changePercent; }
    public void setChangePercent(BigDecimal changePercent) { this.changePercent = changePercent; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getAsOf() { return asOf; }
    public void setAsOf(String asOf) { this.asOf = asOf; }
    public List<Long> getTimestamps() { return timestamps; }
    public void setTimestamps(List<Long> timestamps) { this.timestamps = timestamps; }
    public List<BigDecimal> getClosePrices() { return closePrices; }
    public void setClosePrices(List<BigDecimal> closePrices) { this.closePrices = closePrices; }
}
