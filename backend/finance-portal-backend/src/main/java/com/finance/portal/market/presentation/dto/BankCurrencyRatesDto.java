package com.finance.portal.market.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.finance.portal.market.application.currency.BankCurrencyRateDto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BankCurrencyRatesDto {

    private boolean success;
    private List<BankCurrencyRateDto> data;
    private int count;
    private String error;
}
