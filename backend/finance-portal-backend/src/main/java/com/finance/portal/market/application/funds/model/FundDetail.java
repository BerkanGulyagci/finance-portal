package com.finance.portal.market.application.funds.model;

import java.math.BigDecimal;

public class FundDetail {

    private FundSummary summary;
    private BigDecimal fiftyTwoWeekHigh;
    private BigDecimal fiftyTwoWeekLow;
    private Long regularMarketTime;

    public FundDetail() {
    }

    public FundSummary getSummary() {
        return summary;
    }

    public void setSummary(FundSummary summary) {
        this.summary = summary;
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
