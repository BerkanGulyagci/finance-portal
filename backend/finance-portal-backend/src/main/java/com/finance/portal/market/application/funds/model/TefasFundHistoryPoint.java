package com.finance.portal.market.application.funds.model;

import java.io.Serializable;
import java.math.BigDecimal;

public class TefasFundHistoryPoint implements Serializable {
    private String date;          // yyyy-MM-dd
    private BigDecimal price;
    private Long numberOfInvestors;
    private BigDecimal marketCap;
    private BigDecimal sharesInCirculation;

    public TefasFundHistoryPoint() {}

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public Long getNumberOfInvestors() { return numberOfInvestors; }
    public void setNumberOfInvestors(Long numberOfInvestors) { this.numberOfInvestors = numberOfInvestors; }
    public BigDecimal getMarketCap() { return marketCap; }
    public void setMarketCap(BigDecimal marketCap) { this.marketCap = marketCap; }
    public BigDecimal getSharesInCirculation() { return sharesInCirculation; }
    public void setSharesInCirculation(BigDecimal sharesInCirculation) { this.sharesInCirculation = sharesInCirculation; }
}
