package com.finance.portal.market.application.viop.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ViopChartPoint {

    private Long timestamp;
    private String dateTime;
    private BigDecimal value;
}
