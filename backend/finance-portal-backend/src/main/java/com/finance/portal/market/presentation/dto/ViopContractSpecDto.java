package com.finance.portal.market.presentation.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ViopContractSpecDto {

    private String code;
    private String assetClass;
    private BigDecimal multiplier;
    private BigDecimal marginRate;
    private String currency;
    private String settlementType;
    private boolean found;
    private String isYatirimCode;
}
