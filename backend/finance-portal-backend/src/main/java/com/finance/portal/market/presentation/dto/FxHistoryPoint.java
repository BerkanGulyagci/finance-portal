package com.finance.portal.market.presentation.dto;

import java.math.BigDecimal;

public class FxHistoryPoint {
    private String date;
    private BigDecimal close;

    public FxHistoryPoint() {}
    public FxHistoryPoint(String date, BigDecimal close) {
        this.date = date;
        this.close = close;
    }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
    public BigDecimal getClose() { return close; }
    public void setClose(BigDecimal close) { this.close = close; }
}
