package com.finance.portal.market.application.economy.model;

import java.math.BigDecimal;

/**
 * Hesaplanmış tek bir ekonomik gösterge (son değer + değişimler).
 *
 * <p>Redis'e cache'lendiği için no-arg constructor + getter/setter ile
 * Jackson (de)serializasyonuna uygun POJO olarak tasarlanmıştır.
 */
public class EconomyIndicator {

    private String key;
    private String label;
    private String category;
    private String categoryLabel;
    private String unit;
    private String frequency;
    private String seriesCode;

    /** Son (en güncel) değer. */
    private BigDecimal value;
    /** Son değerin dönemi (ham EVDS "Tarih": "2026-1", "2026-Q1", "2026", "22-05-2026"). */
    private String period;

    /** Bir önceki döneme ait değer. */
    private BigDecimal previousValue;
    /** Bir önceki döneme göre % değişim. */
    private BigDecimal changePercent;
    /** Bir önceki döneme göre mutlak (birim cinsinden) değişim. */
    private BigDecimal absoluteChange;
    /** Geçen yılın aynı dönemine göre % değişim (YoY) — anlamlı değilse null. */
    private BigDecimal yoyChangePercent;

    /** EVDS'den veri çekilebildi mi? false ise değer alanları null. */
    private boolean available;
    /** Negatif "akım" verisi mi (cari denge/bütçe)? Frontend % yerine mutlak değişim göstersin. */
    private boolean preferAbsolute;

    public EconomyIndicator() {
    }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getCategoryLabel() { return categoryLabel; }
    public void setCategoryLabel(String categoryLabel) { this.categoryLabel = categoryLabel; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public String getFrequency() { return frequency; }
    public void setFrequency(String frequency) { this.frequency = frequency; }

    public String getSeriesCode() { return seriesCode; }
    public void setSeriesCode(String seriesCode) { this.seriesCode = seriesCode; }

    public BigDecimal getValue() { return value; }
    public void setValue(BigDecimal value) { this.value = value; }

    public String getPeriod() { return period; }
    public void setPeriod(String period) { this.period = period; }

    public BigDecimal getPreviousValue() { return previousValue; }
    public void setPreviousValue(BigDecimal previousValue) { this.previousValue = previousValue; }

    public BigDecimal getChangePercent() { return changePercent; }
    public void setChangePercent(BigDecimal changePercent) { this.changePercent = changePercent; }

    public BigDecimal getAbsoluteChange() { return absoluteChange; }
    public void setAbsoluteChange(BigDecimal absoluteChange) { this.absoluteChange = absoluteChange; }

    public BigDecimal getYoyChangePercent() { return yoyChangePercent; }
    public void setYoyChangePercent(BigDecimal yoyChangePercent) { this.yoyChangePercent = yoyChangePercent; }

    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }

    public boolean isPreferAbsolute() { return preferAbsolute; }
    public void setPreferAbsolute(boolean preferAbsolute) { this.preferAbsolute = preferAbsolute; }
}
