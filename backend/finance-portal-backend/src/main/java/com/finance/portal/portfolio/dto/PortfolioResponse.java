package com.finance.portal.portfolio.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class PortfolioResponse {

    private UUID id;
    private String name;
    private String description;
    private String currency;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<PortfolioTransactionResponse> transactions;
    private List<PortfolioHoldingResponse> holdings;
    private BigDecimal totalCost;
    private BigDecimal totalMarketValue;
    private BigDecimal totalProfitLoss;
}
