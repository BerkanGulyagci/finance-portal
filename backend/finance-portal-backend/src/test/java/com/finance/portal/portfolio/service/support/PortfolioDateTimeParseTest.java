package com.finance.portal.portfolio.service.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioDateTimeParseTest {

    /**
     * 26 Mayıs 2026 14:30'un farklı serileştirilmiş formları — hepsi aynı LocalDateTime'a
     * çözülmeli. Sonar S5976: aynı asserti tekrar eden testler tek parametreli teste indirgenir.
     */
    @ParameterizedTest(name = "parseLenient: ''{0}'' → 2026-05-26T14:30")
    @ValueSource(strings = {
            "2026-05-26T14:30:00",            // ISO LocalDateTime
            "2026-05-26T14:30:00+03:00",      // ISO OffsetDateTime (local'e indirgenir)
            "2026-05-26 14:30:00"             // T yerine boşluk separator
    })
    void parseLenient_dateTimeFormats_returnSameLocalDateTime(String input) {
        LocalDateTime out = PortfolioDateTimeParse.parseLenient(input);

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
