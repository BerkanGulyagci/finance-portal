package com.finance.portal.portfolio.dto;

import com.finance.portal.common.domain.AssetType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class PortfolioHoldingResponse {

    private String symbol;
    private AssetType assetType;
    private BigDecimal totalQuantity;
    private BigDecimal averageCost;
    private BigDecimal totalCost;
    private BigDecimal currentPrice;
    private BigDecimal marketValue;
    private BigDecimal profitLoss;
    private String currency;
    private LocalDateTime asOf;
}
