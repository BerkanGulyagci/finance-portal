package com.finance.portal.market.application.economy.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * EVDS'den gelen tek bir veri noktası — frekanstan bağımsız.
 *
 * <p>EVDS serileri aylık ({@code "2026-1"}), çeyreklik ({@code "2026-Q1"}),
 * yıllık ({@code "2026"}) veya günlük/haftalık ({@code "22-05-2026"}) olabildiği için
 * {@code period} ham string olarak tutulur; sıralama ve yıllık karşılaştırma
 * {@code unixTime} (epoch saniye) üzerinden yapılır.
 */
public class EconomySeriesPoint {

    private final String period;
    private final BigDecimal value;
    private final long unixTime;

    @JsonCreator
    public EconomySeriesPoint(@JsonProperty("period") String period,
                             @JsonProperty("value") BigDecimal value,
                             @JsonProperty("unixTime") long unixTime) {
        this.period = period;
        this.value = value;
        this.unixTime = unixTime;
    }

    public String getPeriod() {
        return period;
    }

    public BigDecimal getValue() {
        return value;
    }

    public long getUnixTime() {
        return unixTime;
    }

    @Override
    public String toString() {
        return "EconomySeriesPoint{period=" + period + ", value=" + value + ", unixTime=" + unixTime + "}";
    }
}
