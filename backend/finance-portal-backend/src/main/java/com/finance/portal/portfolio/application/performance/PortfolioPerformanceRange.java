package com.finance.portal.portfolio.application.performance;

import java.time.LocalDate;
import java.util.Locale;

/**
 * Portföy performans grafiği zaman aralığı (maksimum 1 yıl).
 */
public enum PortfolioPerformanceRange {

    FIVE_DAYS("5D"),
    ONE_MONTH("1M"),
    SIX_MONTHS("6M"),
    YTD("YTD"),
    ONE_YEAR("1Y");

    private final String apiLabel;

    PortfolioPerformanceRange(String apiLabel) {
        this.apiLabel = apiLabel;
    }

    public String getApiLabel() {
        return apiLabel;
    }

    public static PortfolioPerformanceRange parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("range parameter is required");
        }
        String n = raw.trim().toUpperCase(Locale.ROOT);
        return switch (n) {
            case "5D", "FIVE_DAYS" -> FIVE_DAYS;
            case "1M", "ONE_MONTH" -> ONE_MONTH;
            case "6M", "SIX_MONTHS" -> SIX_MONTHS;
            case "YTD" -> YTD;
            case "1Y", "ONE_YEAR" -> ONE_YEAR;
            default -> throw new IllegalArgumentException(
                    "Unsupported range: " + raw + ". Allowed: 5D, 1M, 6M, YTD, 1Y");
        };
    }

    public LocalDate rangeStart(LocalDate today) {
        return switch (this) {
            case FIVE_DAYS -> today.minusDays(5);
            case ONE_MONTH -> today.minusMonths(1);
            case SIX_MONTHS -> today.minusMonths(6);
            case YTD -> LocalDate.of(today.getYear(), 1, 1);
            case ONE_YEAR -> today.minusYears(1);
        };
    }

    /**
     * İstenen aralık ile 1 yıllık üst sınırın kesişimi.
     */
    public LocalDate effectiveStart(LocalDate today) {
        LocalDate cap = today.minusYears(1);
        LocalDate start = rangeStart(today);
        return start.isBefore(cap) ? cap : start;
    }
}
