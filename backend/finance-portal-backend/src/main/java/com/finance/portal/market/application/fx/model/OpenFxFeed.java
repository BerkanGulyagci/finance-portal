package com.finance.portal.market.application.fx.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OpenFxFeed {

    private String baseCode;
    private String timeLastUpdateUtc;
    private Map<String, Double> rates;
}
