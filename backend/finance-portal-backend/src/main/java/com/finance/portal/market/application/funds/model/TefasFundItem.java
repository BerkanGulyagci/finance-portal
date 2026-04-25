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
    private BigDecimal sharesInCirculation; // Tedavüldeki Pay Sayısı
    private BigDecimal borsaBultenFiyat;    // BYF için Borsa Bülten Fiyatı
    private String date;
    // Dönem getirileri (scrape'den)
    private Double return1M;
    private Double return3M;
    private Double return6M;
    private Double return1Y;
    private Double dailyReturn;
    private Integer riskValue; // TEFAS risk değeri 1-7
    private String kind;       // Fon tipi: YAT, BYF, EMK, GYF, GSYF

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
    public BigDecimal getSharesInCirculation() { return sharesInCirculation; }
    public void setSharesInCirculation(BigDecimal sharesInCirculation) { this.sharesInCirculation = sharesInCirculation; }
    public BigDecimal getBorsaBultenFiyat() { return borsaBultenFiyat; }
    public void setBorsaBultenFiyat(BigDecimal borsaBultenFiyat) { this.borsaBultenFiyat = borsaBultenFiyat; }
    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
    public Double getReturn1M() { return return1M; }
    public void setReturn1M(Double return1M) { this.return1M = return1M; }
    public Double getReturn3M() { return return3M; }
    public void setReturn3M(Double return3M) { this.return3M = return3M; }
    public Double getReturn6M() { return return6M; }
    public void setReturn6M(Double return6M) { this.return6M = return6M; }
    public Double getReturn1Y() { return return1Y; }
    public void setReturn1Y(Double return1Y) { this.return1Y = return1Y; }
    public Double getDailyReturn() { return dailyReturn; }
    public void setDailyReturn(Double dailyReturn) { this.dailyReturn = dailyReturn; }
    public Integer getRiskValue() { return riskValue; }
    public void setRiskValue(Integer riskValue) { this.riskValue = riskValue; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
}
