package com.finance.portal.portfolio.presentation.dto;

import com.finance.portal.common.domain.AssetType;
import com.finance.portal.portfolio.domain.TransactionType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class PortfolioTransactionResponse {

    private UUID id;
    private String symbol;
    private AssetType assetType;
    private TransactionType transactionType;
    private BigDecimal quantity;
    private BigDecimal price;
    private BigDecimal commission;
    private LocalDateTime transactionDate;
    private LocalDateTime createdAt;

    /** VİOP pozisyon yönü: "LONG" veya "SHORT". Yalnızca FUTURE için doludur; diğerleri null. */
    private String direction;
    /** VİOP kontrat çarpanı (multiplier). Yalnızca FUTURE için doludur; diğerleri null. */
    private BigDecimal viopMultiplier;
    /** VİOP başlangıç teminat oranı (marginRate). Yalnızca FUTURE için doludur; diğerleri null. */
    private BigDecimal viopMarginRate;
}
