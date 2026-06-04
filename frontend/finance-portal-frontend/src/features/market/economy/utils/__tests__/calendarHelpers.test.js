import { describe, it, expect } from 'vitest';
import {
  isoDate,
  startOfWeek,
  endOfWeek,
  parseEventTime,
  isAllDay,
  dateKey,
  fmtTime,
  fmtDayHeading,
  fmtNumber,
} from '../calendarHelpers';

describe('isoDate', () => {
  it('Date nesnesini UTC YYYY-MM-DD stringine çevirir', () => {
    const d = new Date(Date.UTC(2026, 5, 4, 13, 45, 0)); // 2026-06-04 UTC
    expect(isoDate(d)).toBe('2026-06-04');
  });

  it('UTC bazlı keser: gece yarısı sonrası bile aynı UTC günü verir', () => {
    // 23:59 UTC hâlâ aynı UTC günü.
    const d = new Date(Date.UTC(2026, 0, 1, 23, 59, 59));
    expect(isoDate(d)).toBe('2026-01-01');
  });
});

describe('dateKey', () => {
  it('isoDate ile aynı çıktıyı (UTC YYYY-MM-DD) verir', () => {
    const d = new Date(Date.UTC(2026, 11, 31, 10, 0, 0));
    expect(dateKey(d)).toBe('2026-12-31');
    expect(dateKey(d)).toBe(isoDate(d));
  });
});

describe('startOfWeek', () => {
  // Pazartesi-başlangıçlı hafta sınırı. Yerel TZ'ye duyarlı olduğundan
  // tam tarih yerine: dönen günün Pazartesi olduğunu ve saatin sıfırlandığını doğrularız.
  it('haftanın başını Pazartesiye (getDay()===1) çeker ve saati sıfırlar', () => {
    // Çarşamba bir tarih.
    const wed = new Date(2026, 5, 3, 15, 30, 0); // yerel
    const s = startOfWeek(wed);
    expect(s.getDay()).toBe(1); // Pazartesi
    expect(s.getHours()).toBe(0);
    expect(s.getMinutes()).toBe(0);
    expect(s.getSeconds()).toBe(0);
    expect(s.getMilliseconds()).toBe(0);
  });

  it('Pazar gününü bir önceki Pazartesiye çeker (offset -6)', () => {
    // Pazar için day===0 → offset -6, yani aynı haftanın Pazartesisi (6 gün geri).
    const sun = new Date(2026, 5, 7, 12, 0, 0); // 7 Haziran 2026 Pazar (yerel)
    const s = startOfWeek(sun);
    expect(s.getDay()).toBe(1);
    // 6 gün geriye gitmeli.
    expect(s.getDate()).toBe(1); // 1 Haziran Pazartesi
  });

  it('Pazartesi gününü aynı güne sabitler (sadece saati sıfırlar)', () => {
    const mon = new Date(2026, 5, 1, 18, 45, 0); // 1 Haziran 2026 Pazartesi
    const s = startOfWeek(mon);
    expect(s.getDay()).toBe(1);
    expect(s.getDate()).toBe(1);
    expect(s.getHours()).toBe(0);
  });

  it('orijinal Date nesnesini mutasyona uğratmaz (kopya üzerinde çalışır)', () => {
    const orig = new Date(2026, 5, 3, 15, 30, 0);
    const before = orig.getTime();
    startOfWeek(orig);
    expect(orig.getTime()).toBe(before);
  });
});

describe('endOfWeek', () => {
  it('hafta sonunu Pazara (başlangıç + 6 gün) çeker', () => {
    const wed = new Date(2026, 5, 3, 15, 30, 0);
    const e = endOfWeek(wed);
    expect(e.getDay()).toBe(0); // Pazar
  });

  it('startOfWeek ile tutarlı: tam 6 gün sonrasıdır', () => {
    const d = new Date(2026, 5, 3, 9, 0, 0);
    const s = startOfWeek(d);
    const e = endOfWeek(d);
    const diffDays = Math.round((e.getTime() - s.getTime()) / 86400000);
    expect(diffDays).toBe(6);
  });
});

describe('parseEventTime', () => {
  it('boş/null/undefined için null döner', () => {
    expect(parseEventTime(null)).toBeNull();
    expect(parseEventTime(undefined)).toBeNull();
    expect(parseEventTime('')).toBeNull();
  });

  it('"YYYY-MM-DD HH:mm:ss" stringini UTC Date olarak parse eder', () => {
    // Boşluk T ile değişir, sonuna Z eklenir → UTC kabul edilir.
    const d = parseEventTime('2026-06-04 13:30:00');
    expect(d).toBeInstanceOf(Date);
    expect(d.toISOString()).toBe('2026-06-04T13:30:00.000Z');
  });

  it('gece yarısı (00:00:00) değerini de geçerli Date olarak döner', () => {
    const d = parseEventTime('2026-06-04 00:00:00');
    expect(d.toISOString()).toBe('2026-06-04T00:00:00.000Z');
  });

  it('geçersiz tarih stringi için null döner', () => {
    expect(parseEventTime('not-a-date')).toBeNull();
  });
});

describe('isAllDay', () => {
  it('boş/null/undefined için true döner (tüm gün varsayılır)', () => {
    expect(isAllDay(null)).toBe(true);
    expect(isAllDay(undefined)).toBe(true);
    expect(isAllDay('')).toBe(true);
  });

  it('" 00:00:00" ile biten string için true döner', () => {
    expect(isAllDay('2026-06-04 00:00:00')).toBe(true);
  });

  it('"T00:00:00" ile biten string için true döner', () => {
    expect(isAllDay('2026-06-04T00:00:00')).toBe(true);
  });

  it('uzunluğu <= 10 olan (sadece tarih) string için true döner', () => {
    expect(isAllDay('2026-06-04')).toBe(true); // length 10
    expect(isAllDay('2026-6-4')).toBe(true);   // length < 10
  });

  it('belirli saatli (00:00 olmayan) string için false döner', () => {
    expect(isAllDay('2026-06-04 13:30:00')).toBe(false);
    expect(isAllDay('2026-06-04T09:15:00')).toBe(false);
  });
});

describe('fmtTime', () => {
  it('24 saat formatında (hour12:false) saat:dakika döner', () => {
    // Yerel TZ'den bağımsız doğrulamak için yerel bir Date kuruyoruz ve
    // saat/dakikanın string içinde göründüğünü kontrol ediyoruz.
    const d = new Date(2026, 5, 4, 9, 5, 0); // 09:05 yerel
    const tr = fmtTime(d, 'tr');
    // "09:05" benzeri; 2 haneli saat ve dakika içermeli.
    expect(tr).toMatch(/^\d{2}:\d{2}$/);
    expect(tr).toContain('09');
    expect(tr).toContain('05');
  });

  it('lang="en" ve diğer diller için de geçerli HH:mm üretir', () => {
    const d = new Date(2026, 5, 4, 14, 30, 0);
    const en = fmtTime(d, 'en');
    expect(en).toMatch(/^\d{2}:\d{2}$/);
    expect(en).toContain('30');
    // 'tr' dışındaki/locale verilmeyen değerler tr-TR'a düşse de format aynı kalır.
    const fallback = fmtTime(d, undefined);
    expect(fallback).toMatch(/^\d{2}:\d{2}$/);
  });
});

describe('fmtDayHeading', () => {
  it('tr için uzun tarih başlığı (ay adı + yıl) üretir', () => {
    const d = new Date(2026, 5, 4, 12, 0, 0); // Haziran 2026
    const tr = fmtDayHeading(d, 'tr');
    expect(typeof tr).toBe('string');
    expect(tr).toContain('2026');
    expect(tr.toLowerCase()).toContain('haziran');
  });

  it('en için İngilizce ay adı (June) ve yıl içerir', () => {
    const d = new Date(2026, 5, 4, 12, 0, 0);
    const en = fmtDayHeading(d, 'en');
    expect(en).toContain('2026');
    expect(en).toContain('June');
  });
});

describe('fmtNumber', () => {
  it('null/undefined/NaN için en-dash "–" döner', () => {
    expect(fmtNumber(null)).toBe('–');
    expect(fmtNumber(undefined)).toBe('–');
    expect(fmtNumber(NaN)).toBe('–');
  });

  it('abs >= 1000 için 0 ondalık kullanır (binlik ayraç nokta)', () => {
    // tr-TR: binlik nokta, ondalık virgül.
    expect(fmtNumber(12345)).toBe('12.345');
    expect(fmtNumber(1000)).toBe('1.000');
  });

  it('10 <= abs < 1000 için 1 ondalık kullanır', () => {
    expect(fmtNumber(12.3456)).toBe('12,3');
    expect(fmtNumber(999.95)).toBe('1.000,0'); // yuvarlama: 999.95 → 1000.0
  });

  it('0.01 <= abs < 10 için 2 ondalık kullanır', () => {
    expect(fmtNumber(1.5)).toBe('1,50');
    expect(fmtNumber(9.999)).toBe('10,00');
  });

  it('abs < 0.01 için 4 ondalık kullanır', () => {
    expect(fmtNumber(0.0012345)).toBe('0,0012');
  });

  it('0 değeri 2 ondalık ile gösterilir (abs<0.01 dalına düşer)', () => {
    // 0 -> abs 0 < 0.01 → dec 4.
    expect(fmtNumber(0)).toBe('0,0000');
  });

  it('negatif değerlerde mutlak büyüklüğe göre ondalık seçer', () => {
    expect(fmtNumber(-12345)).toBe('-12.345'); // abs>=1000 → 0 ondalık
    expect(fmtNumber(-1.5)).toBe('-1,50');     // 0.01<=abs<10 → 2 ondalık
  });

  it('birim "%" ise boşluksuz % ekler', () => {
    expect(fmtNumber(3.5, '%')).toBe('3,50%');
  });

  it('% dışı birimde araya boşluk koyarak ekler', () => {
    expect(fmtNumber(1500, 'TL')).toBe('1.500 TL');
  });

  it('birim verilmezse sadece sayıyı döner', () => {
    expect(fmtNumber(42.5)).toBe('42,5');
  });
});
