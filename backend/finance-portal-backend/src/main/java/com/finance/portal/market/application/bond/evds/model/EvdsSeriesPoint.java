package com.finance.portal.market.application.bond.evds.model;


import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * EVDS'den gelen tek bir günlük veri noktasını temsil eder.
 * Hem "Değer" hem "Kupon Faiz Oranı" serileri için kullanılır.
 */
@Getter
@AllArgsConstructor
public class EvdsSeriesPoint {

    private final LocalDate date;
    private final BigDecimal value;

    @Override
    public String toString() {
        return "EvdsSeriesPoint{date=" + date + ", value=" + value + "}";
    }
}
