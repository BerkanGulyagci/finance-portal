package com.finance.portal.market.application.stock;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class StockDetail {

    private String symbol;
    private String name;
    private String currency;
    private String exchange;
    private StockSummary summary;
    private BigDecimal fiftyTwoWeekHigh;
    private BigDecimal fiftyTwoWeekLow;
}
