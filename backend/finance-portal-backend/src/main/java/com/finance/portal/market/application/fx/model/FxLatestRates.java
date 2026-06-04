package com.finance.portal.market.application.fx.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FxLatestRates {

    private String provider;
    private String sourceType;
    private String base;
    private String asOf;
    private List<FxRateItem> rates;
}
