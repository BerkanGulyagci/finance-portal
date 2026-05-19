package com.finance.portal.admin.application.service;

import com.finance.portal.admin.presentation.dto.DurationUnit;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public final class BanDurationCalculator {

    private BanDurationCalculator() {
    }

    public static Instant calculateBanUntil(int durationValue, DurationUnit durationUnit) {
        Instant now = Instant.now();
        return switch (durationUnit) {
            case MINUTES -> now.plus(durationValue, ChronoUnit.MINUTES);
            case HOURS -> now.plus(durationValue, ChronoUnit.HOURS);
            case DAYS -> now.plus(durationValue, ChronoUnit.DAYS);
        };
    }

    public static String formatDuration(int durationValue, DurationUnit durationUnit) {
        return switch (durationUnit) {
            case MINUTES -> durationValue + " dakika";
            case HOURS -> durationValue + " saat";
            case DAYS -> durationValue + " gün";
        };
    }
}
