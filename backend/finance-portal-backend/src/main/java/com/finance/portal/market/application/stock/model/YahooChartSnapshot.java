package com.finance.portal.market.application.stock.model;

import java.util.List;

public class YahooChartSnapshot {

    private YahooStockMeta meta;
    private List<Long> timestamps;
    private YahooQuoteSeries quote;

    public YahooStockMeta getMeta() {
        return meta;
    }

    public void setMeta(YahooStockMeta meta) {
        this.meta = meta;
    }

    public List<Long> getTimestamps() {
        return timestamps;
    }

    public void setTimestamps(List<Long> timestamps) {
        this.timestamps = timestamps;
    }

    public YahooQuoteSeries getQuote() {
        return quote;
    }

    public void setQuote(YahooQuoteSeries quote) {
        this.quote = quote;
    }

    public boolean hasMeta() {
        return meta != null;
    }
}
