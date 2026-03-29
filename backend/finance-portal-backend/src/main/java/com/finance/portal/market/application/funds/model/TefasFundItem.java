package com.finance.portal.market.application.funds.model;

import java.io.Serializable;
import java.math.BigDecimal;

public class TefasFundItem implements Serializable {

    private String code;
    private String title;
    private BigDecimal price;
    private BigDecimal dailyReturnPercent;
    private BigDecimal marketCap;
    private Long numberOfInvestors;
    private String date;

    public TefasFundItem() {}

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public BigDecimal getDailyReturnPercent() { return dailyReturnPercent; }
    public void setDailyReturnPercent(BigDecimal dailyReturnPercent) { this.dailyReturnPercent = dailyReturnPercent; }
    public BigDecimal getMarketCap() { return marketCap; }
    public void setMarketCap(BigDecimal marketCap) { this.marketCap = marketCap; }
    public Long getNumberOfInvestors() { return numberOfInvestors; }
    public void setNumberOfInvestors(Long numberOfInvestors) { this.numberOfInvestors = numberOfInvestors; }
    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
}
