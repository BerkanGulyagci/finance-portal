package com.finance.portal.market.application.bond.evds.model;


import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * EVDS'den gelen tek bir günlük veri noktasını temsil eder.
 * Hem "Değer" hem "Kupon Faiz Oranı" serileri için kullanılır.
 */
public class EvdsSeriesPoint {

    private final LocalDate date;
    private final BigDecimal value;

    public EvdsSeriesPoint(LocalDate date, BigDecimal value) {
        this.date = date;
        this.value = value;
    }

    public LocalDate getDate() {
        return date;
    }

    public BigDecimal getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "EvdsSeriesPoint{date=" + date + ", value=" + value + "}";
    }
}
