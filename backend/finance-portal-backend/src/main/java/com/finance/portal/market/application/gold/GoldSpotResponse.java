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
    private BigDecimal tamTl;         // Ziynet tam altın TRY (7.00g brüt)
    private BigDecimal cumhuriyetTl;  // Cumhuriyet (Ata) altın TRY mid
    private BigDecimal ayar14Tl;      // 14 Ayar bilezik TRY/gram mid
    private BigDecimal ayar22Tl;      // 22 Ayar bilezik TRY/gram mid

    // Scrape'den gelen gerçek alış/satış fiyatları (tablo için)
    private BigDecimal gramBuy;
    private BigDecimal gramSell;
    private BigDecimal ceyrekBuy;
    private BigDecimal ceyrekSell;
    private BigDecimal yarimBuy;
    private BigDecimal yarimSell;
    private BigDecimal tamBuy;
    private BigDecimal tamSell;
    private BigDecimal cumhuriyetBuy;
    private BigDecimal cumhuriyetSell;
    private BigDecimal ayar14Buy;
    private BigDecimal ayar14Sell;
    private BigDecimal ayar22Buy;
    private BigDecimal ayar22Sell;

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
    public BigDecimal getCumhuriyetTl() { return cumhuriyetTl; }
    public void setCumhuriyetTl(BigDecimal cumhuriyetTl) { this.cumhuriyetTl = cumhuriyetTl; }
    public BigDecimal getAyar14Tl() { return ayar14Tl; }
    public void setAyar14Tl(BigDecimal ayar14Tl) { this.ayar14Tl = ayar14Tl; }
    public BigDecimal getAyar22Tl() { return ayar22Tl; }
    public void setAyar22Tl(BigDecimal ayar22Tl) { this.ayar22Tl = ayar22Tl; }
    public BigDecimal getGramBuy() { return gramBuy; }
    public void setGramBuy(BigDecimal gramBuy) { this.gramBuy = gramBuy; }
    public BigDecimal getGramSell() { return gramSell; }
    public void setGramSell(BigDecimal gramSell) { this.gramSell = gramSell; }
    public BigDecimal getCeyrekBuy() { return ceyrekBuy; }
    public void setCeyrekBuy(BigDecimal ceyrekBuy) { this.ceyrekBuy = ceyrekBuy; }
    public BigDecimal getCeyrekSell() { return ceyrekSell; }
    public void setCeyrekSell(BigDecimal ceyrekSell) { this.ceyrekSell = ceyrekSell; }
    public BigDecimal getYarimBuy() { return yarimBuy; }
    public void setYarimBuy(BigDecimal yarimBuy) { this.yarimBuy = yarimBuy; }
    public BigDecimal getYarimSell() { return yarimSell; }
    public void setYarimSell(BigDecimal yarimSell) { this.yarimSell = yarimSell; }
    public BigDecimal getTamBuy() { return tamBuy; }
    public void setTamBuy(BigDecimal tamBuy) { this.tamBuy = tamBuy; }
    public BigDecimal getTamSell() { return tamSell; }
    public void setTamSell(BigDecimal tamSell) { this.tamSell = tamSell; }
    public BigDecimal getCumhuriyetBuy() { return cumhuriyetBuy; }
    public void setCumhuriyetBuy(BigDecimal cumhuriyetBuy) { this.cumhuriyetBuy = cumhuriyetBuy; }
    public BigDecimal getCumhuriyetSell() { return cumhuriyetSell; }
    public void setCumhuriyetSell(BigDecimal cumhuriyetSell) { this.cumhuriyetSell = cumhuriyetSell; }
    public BigDecimal getAyar14Buy() { return ayar14Buy; }
    public void setAyar14Buy(BigDecimal ayar14Buy) { this.ayar14Buy = ayar14Buy; }
    public BigDecimal getAyar14Sell() { return ayar14Sell; }
    public void setAyar14Sell(BigDecimal ayar14Sell) { this.ayar14Sell = ayar14Sell; }
    public BigDecimal getAyar22Buy() { return ayar22Buy; }
    public void setAyar22Buy(BigDecimal ayar22Buy) { this.ayar22Buy = ayar22Buy; }
    public BigDecimal getAyar22Sell() { return ayar22Sell; }
    public void setAyar22Sell(BigDecimal ayar22Sell) { this.ayar22Sell = ayar22Sell; }
    public BigDecimal getUsdTry() { return usdTry; }
    public void setUsdTry(BigDecimal usdTry) { this.usdTry = usdTry; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
