package com.finance.portal.market.application.crypto.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.math.BigDecimal;

/**
 * OHLC mum — frontend candle formatı: timestamp (saniye), open, high, low, close, volume.
 */
public class CryptoChartCandle {

    private long timestamp;
    private BigDecimal open;
    private BigDecimal high;
    private BigDecimal low;
    private BigDecimal close;
    private BigDecimal volume;

    /** Binance closeTime (ms) — pagination; API yanıtında yok. */
    @JsonIgnore
    private long closeTimeMs;

    public CryptoChartCandle() {
    }

    public CryptoChartCandle(long timestamp, BigDecimal open, BigDecimal high, BigDecimal low,
                           BigDecimal close, BigDecimal volume, long closeTimeMs) {
        this.timestamp = timestamp;
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.volume = volume;
        this.closeTimeMs = closeTimeMs;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public BigDecimal getOpen() {
        return open;
    }

    public void setOpen(BigDecimal open) {
        this.open = open;
    }

    public BigDecimal getHigh() {
        return high;
    }

    public void setHigh(BigDecimal high) {
        this.high = high;
    }

    public BigDecimal getLow() {
        return low;
    }

    public void setLow(BigDecimal low) {
        this.low = low;
    }

    public BigDecimal getClose() {
        return close;
    }

    public void setClose(BigDecimal close) {
        this.close = close;
    }

    public BigDecimal getVolume() {
        return volume;
    }

    public void setVolume(BigDecimal volume) {
        this.volume = volume;
    }

    public long getCloseTimeMs() {
        return closeTimeMs;
    }

    public void setCloseTimeMs(long closeTimeMs) {
        this.closeTimeMs = closeTimeMs;
    }
}
