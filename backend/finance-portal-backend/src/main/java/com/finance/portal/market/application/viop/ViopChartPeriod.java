package com.finance.portal.market.application.viop;

/**
 * VİOP grafik zaman aralığı seçenekleri.
 */
public enum ViopChartPeriod {

    ONE_DAY(1),
    ONE_WEEK(7),
    ONE_MONTH(30),
    THREE_MONTHS(90),
    SIX_MONTHS(180),
    ONE_YEAR(365);

    private final int days;

    ViopChartPeriod(int days) {
        this.days = days;
    }

    public int getDays() {
        return days;
    }
}
