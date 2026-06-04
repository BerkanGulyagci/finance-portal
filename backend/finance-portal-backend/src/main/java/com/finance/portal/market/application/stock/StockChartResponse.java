package com.finance.portal.market.application.stock;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class StockChartResponse {

    private String symbol;
    private List<Long> timestamps;
    private List<BigDecimal> closePrices;
}
