package com.finance.portal.market.application.economy.model;

import java.util.List;

/**
 * Bir gösterge için grafik zaman serisi.
 *
 * @param transform "yoy" (yıllık % değişim) veya "raw" (ham değer)
 */
public class EconomyChartSeries {

    private String key;
    private String label;
    private String unit;
    private String frequency;
    private String transform;
    private String source;
    private List<EconomyChartPoint> points;

    public EconomyChartSeries() {
    }

    public EconomyChartSeries(String key, String label, String unit, String frequency,
                              String transform, String source, List<EconomyChartPoint> points) {
        this.key = key;
        this.label = label;
        this.unit = unit;
        this.frequency = frequency;
        this.transform = transform;
        this.source = source;
        this.points = points;
    }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public String getFrequency() { return frequency; }
    public void setFrequency(String frequency) { this.frequency = frequency; }

    public String getTransform() { return transform; }
    public void setTransform(String transform) { this.transform = transform; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public List<EconomyChartPoint> getPoints() { return points; }
    public void setPoints(List<EconomyChartPoint> points) { this.points = points; }
}
