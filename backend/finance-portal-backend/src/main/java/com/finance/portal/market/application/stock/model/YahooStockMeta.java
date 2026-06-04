package com.finance.portal.market.application.stock.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class YahooStockMeta {

    private String symbol;
    private String longName;
    private String currency;
    private String exchangeName;
    private BigDecimal regularMarketPrice;
    private BigDecimal previousClose;
    private BigDecimal regularMarketDayHigh;
    private BigDecimal regularMarketDayLow;
    private Long regularMarketVolume;
    private BigDecimal fiftyTwoWeekHigh;
    private BigDecimal fiftyTwoWeekLow;
    private Long regularMarketTime;
}
