package com.finance.portal.portfolio.service.support;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Harici API'lerden gelen tarih/saat string varyantlarını tek biçime indirger.
 */
public final class PortfolioDateTimeParse {

    private PortfolioDateTimeParse() {
    }

    public static LocalDateTime parseLenient(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value);
        } catch (Exception ignored) {
        }
        try {
            return OffsetDateTime.parse(value).toLocalDateTime();
        } catch (Exception ignored) {
        }
        try {
            return LocalDate.parse(value).atStartOfDay();
        } catch (Exception ignored) {
        }
        try {
            return LocalDate.parse(value, DateTimeFormatter.ofPattern("dd.MM.yyyy")).atStartOfDay();
        } catch (Exception ignored) {
        }
        try {
            return LocalDateTime.parse(value.replace(' ', 'T'));
        } catch (Exception ignored) {
        }
        return null;
    }
}
