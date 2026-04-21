package com.finance.portal.market.application.gold;

import java.io.Serializable;
import java.math.BigDecimal;

public class GoldHistoryPoint implements Serializable {
    private String date;
    private BigDecimal close;
    private BigDecimal open;
    private BigDecimal high;
    private BigDecimal low;
    private Long volume;

    public GoldHistoryPoint() {}
    public GoldHistoryPoint(String date, BigDecimal close) {
        this.date = date;
        this.close = close;
    }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
    public BigDecimal getClose() { return close; }
    public void setClose(BigDecimal close) { this.close = close; }
    public BigDecimal getOpen() { return open; }
    public void setOpen(BigDecimal open) { this.open = open; }
    public BigDecimal getHigh() { return high; }
    public void setHigh(BigDecimal high) { this.high = high; }
    public BigDecimal getLow() { return low; }
    public void setLow(BigDecimal low) { this.low = low; }
    public Long getVolume() { return volume; }
    public void setVolume(Long volume) { this.volume = volume; }
}
