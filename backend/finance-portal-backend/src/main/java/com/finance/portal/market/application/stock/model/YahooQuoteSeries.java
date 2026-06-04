package com.finance.portal.market.application.stock.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
public class YahooQuoteSeries {

    private List<BigDecimal> open;
    private List<BigDecimal> high;
    private List<BigDecimal> low;
    private List<BigDecimal> close;
    private List<Long> volume;
}
