package com.finance.portal.market.application.stock.model;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class YahooChartSnapshot {

    private YahooStockMeta meta;
    private List<Long> timestamps;
    private YahooQuoteSeries quote;

    public boolean hasMeta() {
        return meta != null;
    }
}
