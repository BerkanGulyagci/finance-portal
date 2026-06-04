package com.finance.portal.market.application.stock;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class StockSummary {

    private String symbol;
    private String name;
    private String currency;
    private String exchange;
    private BigDecimal price;
    private BigDecimal change;
    private BigDecimal changePercent;
    private BigDecimal dayHigh;
    private BigDecimal dayLow;
    private Long volume;
    private String asOf;
}
