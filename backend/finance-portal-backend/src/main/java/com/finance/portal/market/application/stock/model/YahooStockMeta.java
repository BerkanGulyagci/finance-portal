package com.finance.portal.market.application.stock.model;

import java.math.BigDecimal;

public class YahooStockMeta {

    private String symbol;
    private String longName;
    private String currency;
    private String exchangeName;
    private BigDecimal regularMarketPrice;
    private BigDecimal previousClose;
    private BigDecimal regularMarketDayHigh;
    private BigDecimal regularMarketDayLow;
    private Long regularMarketVolume;
    private BigDecimal fiftyTwoWeekHigh;
    private BigDecimal fiftyTwoWeekLow;
    private Long regularMarketTime;

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getLongName() {
        return longName;
    }

    public void setLongName(String longName) {
        this.longName = longName;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getExchangeName() {
        return exchangeName;
    }

    public void setExchangeName(String exchangeName) {
        this.exchangeName = exchangeName;
    }

    public BigDecimal getRegularMarketPrice() {
        return regularMarketPrice;
    }

    public void setRegularMarketPrice(BigDecimal regularMarketPrice) {
        this.regularMarketPrice = regularMarketPrice;
    }

    public BigDecimal getPreviousClose() {
        return previousClose;
    }

    public void setPreviousClose(BigDecimal previousClose) {
        this.previousClose = previousClose;
    }

    public BigDecimal getRegularMarketDayHigh() {
        return regularMarketDayHigh;
    }

    public void setRegularMarketDayHigh(BigDecimal regularMarketDayHigh) {
        this.regularMarketDayHigh = regularMarketDayHigh;
    }

    public BigDecimal getRegularMarketDayLow() {
        return regularMarketDayLow;
    }

    public void setRegularMarketDayLow(BigDecimal regularMarketDayLow) {
        this.regularMarketDayLow = regularMarketDayLow;
    }

    public Long getRegularMarketVolume() {
        return regularMarketVolume;
    }

    public void setRegularMarketVolume(Long regularMarketVolume) {
        this.regularMarketVolume = regularMarketVolume;
    }

    public BigDecimal getFiftyTwoWeekHigh() {
        return fiftyTwoWeekHigh;
    }

    public void setFiftyTwoWeekHigh(BigDecimal fiftyTwoWeekHigh) {
        this.fiftyTwoWeekHigh = fiftyTwoWeekHigh;
    }

    public BigDecimal getFiftyTwoWeekLow() {
        return fiftyTwoWeekLow;
    }

    public void setFiftyTwoWeekLow(BigDecimal fiftyTwoWeekLow) {
        this.fiftyTwoWeekLow = fiftyTwoWeekLow;
    }

    public Long getRegularMarketTime() {
        return regularMarketTime;
    }

    public void setRegularMarketTime(Long regularMarketTime) {
        this.regularMarketTime = regularMarketTime;
    }
}
