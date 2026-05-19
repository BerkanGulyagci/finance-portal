package com.finance.portal.market.application.fx.model;

import java.util.List;

public class FxHistory {

    private String symbol;
    private String quoteCurrency;
    private String range;
    private List<FxHistoryPoint> points;

    public FxHistory() {
    }

    public FxHistory(String symbol, String quoteCurrency, String range, List<FxHistoryPoint> points) {
        this.symbol = symbol;
        this.quoteCurrency = quoteCurrency;
        this.range = range;
        this.points = points;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getQuoteCurrency() {
        return quoteCurrency;
    }

    public void setQuoteCurrency(String quoteCurrency) {
        this.quoteCurrency = quoteCurrency;
    }

    public String getRange() {
        return range;
    }

    public void setRange(String range) {
        this.range = range;
    }

    public List<FxHistoryPoint> getPoints() {
        return points;
    }

    public void setPoints(List<FxHistoryPoint> points) {
        this.points = points;
    }
}
