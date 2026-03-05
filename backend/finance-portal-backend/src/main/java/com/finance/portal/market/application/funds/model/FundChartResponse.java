package com.finance.portal.market.application.funds.model;

import java.util.List;

public class FundChartResponse {

    private String symbol;
    private String range;
    private String interval;
    private List<FundChartPoint> candles;

    public FundChartResponse() {
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getRange() {
        return range;
    }

    public void setRange(String range) {
        this.range = range;
    }

    public String getInterval() {
        return interval;
    }

    public void setInterval(String interval) {
        this.interval = interval;
    }

    public List<FundChartPoint> getCandles() {
        return candles;
    }

    public void setCandles(List<FundChartPoint> candles) {
        this.candles = candles;
    }
}
