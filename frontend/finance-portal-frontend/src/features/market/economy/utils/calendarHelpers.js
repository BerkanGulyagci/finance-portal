/**
 * Pure helpers for the Economic Calendar page.
 * No React, no I/O — easy to unit-test.
 */

/**
 * YYYY-MM-DD — YEREL takvim gününden (UTC DEĞİL).
 * toISOString() tarihi UTC'ye çevirir → TR (UTC+3) gibi pozitif ofsette, gece yarısından
 * sonraki yerel gün UTC'de hâlâ önceki gün olduğu için tarih BİR GÜN GERİ kayardı
 * (örn. 21 Haziran Pazar 00:00 TR → "2026-06-20"). Yerel yıl/ay/gün bileşenlerinden üreterek
 * quick-filtreler (bugün/yarın/hafta) doğru günü gönderir.
 */
export function isoDate(d) {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

/** Monday-start week boundary. */
export function startOfWeek(d) {
  const x = new Date(d);
  const day = x.getDay(); // 0=Sun
  const offset = (day === 0 ? -6 : 1 - day);
  x.setDate(x.getDate() + offset);
  x.setHours(0, 0, 0, 0);
  return x;
}

export function endOfWeek(d) {
  const s = startOfWeek(d);
  s.setDate(s.getDate() + 6);
  return s;
}

/** "YYYY-MM-DD HH:mm:ss" (Finnhub UTC) → Date | null */
export function parseEventTime(timeStr) {
  if (!timeStr) return null;
  const iso = timeStr.replace(' ', 'T') + 'Z';
  const d = new Date(iso);
  return isNaN(d.getTime()) ? null : d;
}

export function isAllDay(timeStr) {
  if (!timeStr) return true;
  return timeStr.endsWith(' 00:00:00') || timeStr.endsWith('T00:00:00') || timeStr.length <= 10;
}

/**
 * Olayın ait olduğu YEREL gün (YYYY-MM-DD). isoDate ile aynı — UTC kayması olmadan,
 * kullanıcının gördüğü yerel günle eşleşir (olay zamanı parseEventTime ile yerel Date'tir).
 * Eskiden toISOString() (UTC) kullanıyordu → gün gruplaması/filtresi TR'de bir gün kayabiliyordu.
 */
export function dateKey(d) {
  return isoDate(d);
}

export function fmtTime(d, lang) {
  return d.toLocaleTimeString(lang === 'en' ? 'en-US' : 'tr-TR', {
    hour: '2-digit', minute: '2-digit', hour12: false,
  });
}

export function fmtDayHeading(d, lang) {
  return d.toLocaleDateString(lang === 'en' ? 'en-US' : 'tr-TR', {
    day: 'numeric', month: 'long', year: 'numeric', weekday: 'long',
  });
}

export function fmtNumber(v, unit) {
  if (v == null || isNaN(v)) return '–';
  const abs = Math.abs(v);
  let dec = 2;
  if (abs >= 1000) dec = 0;
  else if (abs >= 10) dec = 1;
  else if (abs < 0.01) dec = 4;
  const formatted = v.toLocaleString('tr-TR', { minimumFractionDigits: dec, maximumFractionDigits: dec });
  return unit ? `${formatted}${unit === '%' ? '%' : ' ' + unit}` : formatted;
}
