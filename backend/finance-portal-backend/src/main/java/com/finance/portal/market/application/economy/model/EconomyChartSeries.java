package com.finance.portal.market.application.economy.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Bir gösterge için grafik zaman serisi.
 *
 * @param transform "yoy" (yıllık % değişim) veya "raw" (ham değer)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EconomyChartSeries {

    private String key;
    private String label;
    private String unit;
    private String frequency;
    private String transform;
    private String source;
    private List<EconomyChartPoint> points;
}
