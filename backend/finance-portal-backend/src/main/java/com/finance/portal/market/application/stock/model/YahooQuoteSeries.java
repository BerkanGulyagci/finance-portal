package com.finance.portal.market.application.stock.model;

import java.math.BigDecimal;
import java.util.List;

public class YahooQuoteSeries {

    private List<BigDecimal> open;
    private List<BigDecimal> high;
    private List<BigDecimal> low;
    private List<BigDecimal> close;
    private List<Long> volume;

    public List<BigDecimal> getOpen() {
        return open;
    }

    public void setOpen(List<BigDecimal> open) {
        this.open = open;
    }

    public List<BigDecimal> getHigh() {
        return high;
    }

    public void setHigh(List<BigDecimal> high) {
        this.high = high;
    }

    public List<BigDecimal> getLow() {
        return low;
    }

    public void setLow(List<BigDecimal> low) {
        this.low = low;
    }

    public List<BigDecimal> getClose() {
        return close;
    }

    public void setClose(List<BigDecimal> close) {
        this.close = close;
    }

    public List<Long> getVolume() {
        return volume;
    }

    public void setVolume(List<Long> volume) {
        this.volume = volume;
    }
}
