package com.finance.portal.market.application.stock;

import java.math.BigDecimal;
import java.util.List;

public class StockChartResponse {

    private String symbol;
    private List<Long> timestamps;
    private List<BigDecimal> closePrices;

    public StockChartResponse() {
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public List<Long> getTimestamps() {
        return timestamps;
    }

    public void setTimestamps(List<Long> timestamps) {
        this.timestamps = timestamps;
    }

    public List<BigDecimal> getClosePrices() {
        return closePrices;
    }

    public void setClosePrices(List<BigDecimal> closePrices) {
        this.closePrices = closePrices;
    }
}

