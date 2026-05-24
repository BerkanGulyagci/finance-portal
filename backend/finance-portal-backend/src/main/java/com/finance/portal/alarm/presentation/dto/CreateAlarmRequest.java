package com.finance.portal.alarm.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class CreateAlarmRequest {

    @NotBlank
    private String assetType;

    @NotBlank
    private String symbol;

    private String instrumentName;

    /** PRICE | CHANGE_PERCENT | VOLUME */
    @NotBlank
    private String metric;

    /** ABOVE | BELOW */
    @NotBlank
    private String direction;

    @NotNull
    private BigDecimal threshold;

    /** ONCE (varsayılan) | RECURRING */
    private String frequency;

    private String note;
}
