package com.finance.portal.news.domain;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Farklı kaynakların tarih biçimlerini (RFC-2822 RSS, ISO-8601, "yyyy-MM-dd HH:mm:ss")
 * tek bir ISO-8601 (UTC, instant) biçimine normalize eder. Böylece hem frontend tutarlı
 * formatlar hem de aggregator tarihe göre sıralayabilir.
 */
public final class NewsDateUtil {

    private static final DateTimeFormatter SQL_LIKE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private NewsDateUtil() {
    }

    /** Ham tarihi ISO-8601 instant string'e çevirir; parse edilemezse orijinali döner. */
    public static String toIso(String raw) {
        Instant i = toInstant(raw);
        return i != null ? i.toString() : raw;
    }

    /** Sıralama için epoch-millis; parse edilemezse 0. */
    public static long toEpochMillis(String raw) {
        Instant i = toInstant(raw);
        return i != null ? i.toEpochMilli() : 0L;
    }

    /** Unix epoch saniyesinden ISO string. */
    public static String fromEpochSeconds(long seconds) {
        return Instant.ofEpochSecond(seconds).toString();
    }

    private static Instant toInstant(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim();
        // Bazı feed'ler (ör. Hürriyet) RFC-822 zonunu çıplak "Z" (Zulu/UTC) yazıyor;
        // RFC_1123_DATE_TIME bunu çözemez → " Z" sonekini " +0000" olarak normalize et.
        // (ISO instant'lar "...T..Z" boşluksuz bittiği için etkilenmez.)
        if (s.endsWith(" Z")) {
            s = s.substring(0, s.length() - 2) + " +0000";
        }
        // 1) RFC-1123 / RFC-2822 (RSS pubDate): "Sun, 24 May 2026 12:16:21 +0300"
        try {
            return OffsetDateTime.parse(s, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        } catch (Exception ignored) {
        }
        // 2) ISO-8601 offset/instant: "2026-05-24T12:15:00Z" / "...+03:00" / "...000000Z"
        for (DateTimeFormatter f : List.of(DateTimeFormatter.ISO_OFFSET_DATE_TIME,
                DateTimeFormatter.ISO_INSTANT, DateTimeFormatter.ISO_DATE_TIME)) {
            try {
                return OffsetDateTime.parse(s, f).toInstant();
            } catch (Exception ignored) {
            }
        }
        try {
            return Instant.parse(s);
        } catch (Exception ignored) {
        }
        // 3) "yyyy-MM-dd HH:mm:ss" (UTC kabul edilir)
        try {
            return LocalDateTime.parse(s, SQL_LIKE).toInstant(ZoneOffset.UTC);
        } catch (Exception ignored) {
        }
        return null;
    }
}
