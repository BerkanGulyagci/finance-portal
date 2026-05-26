package com.finance.portal.portfolio.service.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioDateTimeParseTest {

    @Test
    @DisplayName("parseLenient: ISO LocalDateTime — direkt parse")
    void parseLenient_isoLocalDateTime() {
        LocalDateTime out = PortfolioDateTimeParse.parseLenient("2026-05-26T14:30:00");

        assertThat(out).isEqualTo(LocalDateTime.of(2026, Month.MAY, 26, 14, 30));
    }

    @Test
    @DisplayName("parseLenient: ISO OffsetDateTime — local'e çevrilir")
    void parseLenient_isoOffsetDateTime() {
        LocalDateTime out = PortfolioDateTimeParse.parseLenient("2026-05-26T14:30:00+03:00");

        assertThat(out).isEqualTo(LocalDateTime.of(2026, Month.MAY, 26, 14, 30));
    }

    @Test
    @DisplayName("parseLenient: ISO LocalDate — günün başına ata")
    void parseLenient_isoLocalDate_startsAtMidnight() {
        LocalDateTime out = PortfolioDateTimeParse.parseLenient("2026-05-26");

        assertThat(out).isEqualTo(LocalDate.of(2026, Month.MAY, 26).atStartOfDay());
        assertThat(out.toLocalTime()).isEqualTo(LocalTime.MIDNIGHT);
    }

    @Test
    @DisplayName("parseLenient: Türkçe tarih (dd.MM.yyyy) — günün başına ata")
    void parseLenient_turkishDate_startsAtMidnight() {
        LocalDateTime out = PortfolioDateTimeParse.parseLenient("26.05.2026");

        assertThat(out).isEqualTo(LocalDate.of(2026, Month.MAY, 26).atStartOfDay());
    }

    @Test
    @DisplayName("parseLenient: 'YYYY-MM-DD HH:MM:SS' (T yerine boşluk) — destekler")
    void parseLenient_spaceSeparator_replacesWithT() {
        LocalDateTime out = PortfolioDateTimeParse.parseLenient("2026-05-26 14:30:00");

        assertThat(out).isEqualTo(LocalDateTime.of(2026, Month.MAY, 26, 14, 30));
    }

    @Test
    @DisplayName("parseLenient: null — null döner")
    void parseLenient_null_returnsNull() {
        assertThat(PortfolioDateTimeParse.parseLenient(null)).isNull();
    }

    @Test
    @DisplayName("parseLenient: boş string — null döner")
    void parseLenient_blank_returnsNull() {
        assertThat(PortfolioDateTimeParse.parseLenient("")).isNull();
        assertThat(PortfolioDateTimeParse.parseLenient("   ")).isNull();
    }

    @Test
    @DisplayName("parseLenient: tanınmayan format — null döner (exception fırlatmaz)")
    void parseLenient_unrecognized_returnsNull() {
        assertThat(PortfolioDateTimeParse.parseLenient("not-a-date")).isNull();
        assertThat(PortfolioDateTimeParse.parseLenient("32/13/2026")).isNull();
    }
}
