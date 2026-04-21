package com.finance.portal.market.application.gold;

import java.io.Serializable;
import java.math.BigDecimal;

public class GoldSpotResponse implements Serializable {

    private String symbol;
    private String name;
    private String currency;

    // ONS fiyatı (USD)
    private BigDecimal price;
    private BigDecimal change;
    private BigDecimal changePercent;
    private BigDecimal high;
    private BigDecimal low;
    private BigDecimal previousClose;
    private BigDecimal bid;
    private BigDecimal ask;

    // TRY karşılıkları
    private BigDecimal priceTl;       // ONS/TRY
    private BigDecimal gramTl;        // Gram altın TRY
    private BigDecimal ceyrekTl;      // Çeyrek altın TRY
    private BigDecimal yarimTl;       // Yarım altın TRY
    private BigDecimal tamTl;         // Tam altın TRY

    // USD/TRY kuru
    private BigDecimal usdTry;

    private String updatedAt;

    public GoldSpotResponse() {}

    // Getters & Setters
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public BigDecimal getChange() { return change; }
    public void setChange(BigDecimal change) { this.change = change; }
    public BigDecimal getChangePercent() { return changePercent; }
    public void setChangePercent(BigDecimal changePercent) { this.changePercent = changePercent; }
    public BigDecimal getHigh() { return high; }
    public void setHigh(BigDecimal high) { this.high = high; }
    public BigDecimal getLow() { return low; }
    public void setLow(BigDecimal low) { this.low = low; }
    public BigDecimal getPreviousClose() { return previousClose; }
    public void setPreviousClose(BigDecimal previousClose) { this.previousClose = previousClose; }
    public BigDecimal getBid() { return bid; }
    public void setBid(BigDecimal bid) { this.bid = bid; }
    public BigDecimal getAsk() { return ask; }
    public void setAsk(BigDecimal ask) { this.ask = ask; }
    public BigDecimal getPriceTl() { return priceTl; }
    public void setPriceTl(BigDecimal priceTl) { this.priceTl = priceTl; }
    public BigDecimal getGramTl() { return gramTl; }
    public void setGramTl(BigDecimal gramTl) { this.gramTl = gramTl; }
    public BigDecimal getCeyrekTl() { return ceyrekTl; }
    public void setCeyrekTl(BigDecimal ceyrekTl) { this.ceyrekTl = ceyrekTl; }
    public BigDecimal getYarimTl() { return yarimTl; }
    public void setYarimTl(BigDecimal yarimTl) { this.yarimTl = yarimTl; }
    public BigDecimal getTamTl() { return tamTl; }
    public void setTamTl(BigDecimal tamTl) { this.tamTl = tamTl; }
    public BigDecimal getUsdTry() { return usdTry; }
    public void setUsdTry(BigDecimal usdTry) { this.usdTry = usdTry; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
