package com.finance.portal.portfolio.dto;

import com.finance.portal.common.domain.AssetType;
import com.finance.portal.portfolio.domain.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class AddTransactionRequest {

    @NotBlank(message = "symbol must not be blank")
    private String symbol;

    @NotNull(message = "assetType must not be null")
    private AssetType assetType;

    @NotNull(message = "transactionType must not be null")
    private TransactionType transactionType;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false, message = "quantity must be greater than 0")
    private BigDecimal quantity;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false, message = "price must be greater than 0")
    private BigDecimal price;

    @DecimalMin(value = "0.0", inclusive = true, message = "commission must be greater than or equal to 0")
    private BigDecimal commission;

    @NotNull(message = "transactionDate must not be null")
    private LocalDateTime transactionDate;
}
