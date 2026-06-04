package com.finance.portal.market.application.viop.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class ViopContractDetail {

    private String name;
    private String symbol;
    private BigDecimal lastPrice;
    private BigDecimal changePercent;
    private BigDecimal high;
    private BigDecimal low;
    private Long openPositionCount;
    private Long openPositionChange;
    private BigDecimal settlementPrice;
    private BigDecimal prevSettlementPrice;
    private String time;
    private String tradingViewSymbol;
}
