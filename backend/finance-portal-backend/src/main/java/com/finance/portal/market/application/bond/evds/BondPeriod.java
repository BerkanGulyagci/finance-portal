package com.finance.portal.market.application.bond.evds;

/**
 * Tarihsel grafik için zaman periyodu.
 * Her enum değeri kaç günlük veri çekileceğini belirtir.
 */
public enum BondPeriod {

    ONE_WEEK(7),
    ONE_MONTH(30),
    THREE_MONTHS(90),
    SIX_MONTHS(180),
    ONE_YEAR(365);

    private final int days;

    BondPeriod(int days) {
        this.days = days;
    }

    public int getDays() {
        return days;
    }
}
