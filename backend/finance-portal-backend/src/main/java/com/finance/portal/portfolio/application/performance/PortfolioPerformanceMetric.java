package com.finance.portal.portfolio.application.performance;

import java.util.Locale;

/**
 * Grafikte gösterilecek metrik.
 * <p>
 * VALUE → piyasa değeri serisi; GROWTH → TWR-benzeri kümülatif dönem getirisi ({@code periodGrowthPercent}).
 * Her iki durumda da noktalar tam alan setiyle döner.
 */
public enum PortfolioPerformanceMetric {

    VALUE,
    GROWTH;

    public static PortfolioPerformanceMetric parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return VALUE;
        }
        String n = raw.trim().toUpperCase(Locale.ROOT);
        return switch (n) {
            case "VALUE" -> VALUE;
            case "GROWTH" -> GROWTH;
            default -> throw new IllegalArgumentException(
                    "Unsupported metric: " + raw + ". Allowed: VALUE, GROWTH");
        };
    }
}
