package com.finance.portal.market.application.economy.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Grafik için tek bir veri noktası (dönem etiketi + değer).
 * Redis cache uyumu için no-arg constructor + getter/setter.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EconomyChartPoint {

    private String period;
    private BigDecimal value;
}
