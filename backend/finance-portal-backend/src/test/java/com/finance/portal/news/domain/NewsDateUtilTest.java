package com.finance.portal.news.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * NewsDateUtil saf yardımcı sınıf testleri: farklı kaynak tarih biçimlerinin
 * (RFC-2822, ISO-8601 offset/instant, "yyyy-MM-dd HH:mm:ss", çıplak " Z")
 * tek bir UTC ISO-8601 instant'a normalize edilmesi.
 */
class NewsDateUtilTest {

    // =========================================================================
    // toIso
    // =========================================================================

    @Test
    @DisplayName("toIso: null → null")
    void toIso_null_returnsNull() {
        assertThat(NewsDateUtil.toIso(null)).isNull();
    }

    @Test
    @DisplayName("toIso: boş/blank → blank parse edilemez, orijinal döner")
    void toIso_blank_returnsRaw() {
        assertThat(NewsDateUtil.toIso("")).isEqualTo("");
        assertThat(NewsDateUtil.toIso("   ")).isEqualTo("   ");
    }

    @Test
    @DisplayName("toIso: parse edilemeyen ham metin olduğu gibi döner")
    void toIso_unparsable_returnsRaw() {
        assertThat(NewsDateUtil.toIso("not a date")).isEqualTo("not a date");
    }

    @Test
    @DisplayName("toIso: RFC-2822 RSS pubDate (+0300 offset) → UTC instant")
    void toIso_rfc2822_withOffset() {
        // 12:16:21 +0300 → 09:16:21Z
        assertThat(NewsDateUtil.toIso("Sun, 24 May 2026 12:16:21 +0300"))
                .isEqualTo("2026-05-24T09:16:21Z");
    }

    @Test
    @DisplayName("toIso: RFC-2822 GMT zonu → UTC instant")
    void toIso_rfc2822_gmt() {
        assertThat(NewsDateUtil.toIso("Sun, 24 May 2026 09:16:21 GMT"))
                .isEqualTo("2026-05-24T09:16:21Z");
    }

    @Test
    @DisplayName("toIso: çıplak ' Z' (Zulu) soneki +0000 olarak normalize edilir")
    void toIso_bareSpaceZ_normalizedToUtc() {
        // "Sun, 24 May 2026 12:16:21 Z" → " Z" → " +0000"
        assertThat(NewsDateUtil.toIso("Sun, 24 May 2026 12:16:21 Z"))
                .isEqualTo("2026-05-24T12:16:21Z");
    }

    @Test
    @DisplayName("toIso: ISO-8601 offset (+03:00) → UTC instant")
    void toIso_isoOffset() {
        assertThat(NewsDateUtil.toIso("2026-05-24T12:15:00+03:00"))
                .isEqualTo("2026-05-24T09:15:00Z");
    }

    @Test
    @DisplayName("toIso: ISO-8601 instant (Z soneki) korunur")
    void toIso_isoInstant() {
        assertThat(NewsDateUtil.toIso("2026-05-24T12:15:00Z"))
                .isEqualTo("2026-05-24T12:15:00Z");
    }

    @Test
    @DisplayName("toIso: ISO instant fraksiyonel saniye ile")
    void toIso_isoInstant_fractional() {
        assertThat(NewsDateUtil.toIso("2026-05-24T12:15:00.500Z"))
                .isEqualTo("2026-05-24T12:15:00.500Z");
    }

    @Test
    @DisplayName("toIso: 'yyyy-MM-dd HH:mm:ss' (boşluklu, UTC kabul) → UTC instant")
    void toIso_sqlLike() {
        assertThat(NewsDateUtil.toIso("2026-05-24 12:15:00"))
                .isEqualTo("2026-05-24T12:15:00Z");
    }

    @Test
    @DisplayName("toIso: zaten ISO instant olan değer idempotent kalır")
    void toIso_idempotentOnIso() {
        String iso = "2026-01-01T00:00:00Z";
        assertThat(NewsDateUtil.toIso(iso)).isEqualTo(iso);
    }

    // =========================================================================
    // toEpochMillis
    // =========================================================================

    @Test
    @DisplayName("toEpochMillis: null → 0")
    void toEpochMillis_null_returnsZero() {
        assertThat(NewsDateUtil.toEpochMillis(null)).isZero();
    }

    @Test
    @DisplayName("toEpochMillis: parse edilemez → 0")
    void toEpochMillis_unparsable_returnsZero() {
        assertThat(NewsDateUtil.toEpochMillis("garbage")).isZero();
    }

    @Test
    @DisplayName("toEpochMillis: geçerli ISO instant → doğru epoch millis")
    void toEpochMillis_valid() {
        long expected = Instant.parse("2026-05-24T09:16:21Z").toEpochMilli();
        assertThat(NewsDateUtil.toEpochMillis("Sun, 24 May 2026 12:16:21 +0300"))
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("toEpochMillis: epoch (1970) sınır değeri")
    void toEpochMillis_epochZero() {
        assertThat(NewsDateUtil.toEpochMillis("1970-01-01T00:00:00Z")).isZero();
    }

    // =========================================================================
    // fromEpochSeconds
    // =========================================================================

    @Test
    @DisplayName("fromEpochSeconds: 0 → 1970 epoch ISO")
    void fromEpochSeconds_zero() {
        assertThat(NewsDateUtil.fromEpochSeconds(0L)).isEqualTo("1970-01-01T00:00:00Z");
    }

    @Test
    @DisplayName("fromEpochSeconds: pozitif saniye → doğru ISO instant")
    void fromEpochSeconds_positive() {
        long secs = Instant.parse("2026-05-24T12:15:00Z").getEpochSecond();
        assertThat(NewsDateUtil.fromEpochSeconds(secs)).isEqualTo("2026-05-24T12:15:00Z");
    }

    @Test
    @DisplayName("fromEpochSeconds: negatif saniye (1970 öncesi)")
    void fromEpochSeconds_negative() {
        assertThat(NewsDateUtil.fromEpochSeconds(-1L)).isEqualTo("1969-12-31T23:59:59Z");
    }

    @Test
    @DisplayName("round-trip: fromEpochSeconds → toEpochMillis tutarlı")
    void roundTrip_fromSecondsToMillis() {
        long secs = 1_700_000_000L;
        String iso = NewsDateUtil.fromEpochSeconds(secs);
        assertThat(NewsDateUtil.toEpochMillis(iso)).isEqualTo(secs * 1000L);
    }
}
