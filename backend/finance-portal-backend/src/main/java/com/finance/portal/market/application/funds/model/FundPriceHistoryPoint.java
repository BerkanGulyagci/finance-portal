package com.finance.portal.market.application.funds.model;

import java.math.BigDecimal;

/**
 * Tek bir günün fon fiyat noktası (TEFAS tarihsel seri).
 * Redis cache + REST yanıtı için POJO (no-arg ctor + getter/setter).
 */
public class FundPriceHistoryPoint {

    private String date;            // yyyy-MM-dd
    private BigDecimal price;       // birim pay fiyatı (NAV)
    private BigDecimal portfolioSize; // portföy büyüklüğü (TL), opsiyonel

    public FundPriceHistoryPoint() {
    }

    public FundPriceHistoryPoint(String date, BigDecimal price, BigDecimal portfolioSize) {
        this.date = date;
        this.price = price;
        this.portfolioSize = portfolioSize;
    }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public BigDecimal getPortfolioSize() { return portfolioSize; }
    public void setPortfolioSize(BigDecimal portfolioSize) { this.portfolioSize = portfolioSize; }
}
