package com.finance.portal.market.presentation.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EconomicIndicatorsDto {

    private String policyRate;
    private String inflation;
    private String ppi;
    private String depositRate;
}
