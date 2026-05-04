package com.finance.portal.market.application.funds.model;

/**
 * Fon grafik periyotları.
 * day değeri HangiKredi API'sine gönderilir.
 */
public enum FundPeriod {
    ONE_WEEK(7),
    ONE_MONTH(30),
    THREE_MONTHS(90),
    SIX_MONTHS(180),
    ONE_YEAR(365),
    THREE_YEARS(1095),
    FIVE_YEARS(1825);

    private final int days;

    FundPeriod(int days) {
        this.days = days;
    }

    public int getDays() {
        return days;
    }

    /**
     * Cache TTL saniye cinsinden.
     * Kısa dönemler daha sık güncellenir.
     */
    public long getCacheTtlSeconds() {
        return switch (this) {
            case ONE_WEEK, ONE_MONTH, THREE_MONTHS -> 1800;   // 30 dakika
            case SIX_MONTHS, ONE_YEAR              -> 3600;   // 1 saat
            case THREE_YEARS, FIVE_YEARS           -> 21600;  // 6 saat
        };
    }
}
