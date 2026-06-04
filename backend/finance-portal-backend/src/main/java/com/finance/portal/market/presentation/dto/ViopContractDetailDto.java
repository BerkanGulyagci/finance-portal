package com.finance.portal.market.presentation.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * VIOP kontrat detay bilgisi
 */
@Getter
@Setter
@NoArgsConstructor
public class ViopContractDetailDto {
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
    private String tradingViewSymbol; // TradingView sembolü (varsa)
}
