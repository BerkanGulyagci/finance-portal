import { describe, it, expect } from 'vitest';
import { parseBackendDate } from './dateUtils';

describe('parseBackendDate', () => {
  it('null/boş için null döner', () => {
    expect(parseBackendDate(null)).toBeNull();
    expect(parseBackendDate('')).toBeNull();
    expect(parseBackendDate(undefined)).toBeNull();
  });

  it('TZ-belirteçsiz ISO string UTC olarak parse edilir (Z eklenir)', () => {
    // Backend "2026-06-01T00:49:52" (TZ yok) → UTC kabul edilmeli.
    const d = parseBackendDate('2026-06-01T00:49:52');
    expect(d).toBeInstanceOf(Date);
    expect(d.toISOString()).toBe('2026-06-01T00:49:52.000Z');
  });

  it('zaten Z taşıyan string olduğu gibi parse edilir', () => {
    const d = parseBackendDate('2026-06-01T00:49:52Z');
    expect(d.toISOString()).toBe('2026-06-01T00:49:52.000Z');
  });

  it('offsetli string (+03:00) doğru parse edilir, çift Z eklenmez', () => {
    const d = parseBackendDate('2026-06-01T03:49:52+03:00');
    expect(d.toISOString()).toBe('2026-06-01T00:49:52.000Z');
  });

  it('milisaniyeli TZ-siz string UTC parse edilir', () => {
    const d = parseBackendDate('2026-06-01T00:49:52.123');
    expect(d.toISOString()).toBe('2026-06-01T00:49:52.123Z');
  });

  it('geçersiz string için null döner', () => {
    expect(parseBackendDate('not-a-date')).toBeNull();
  });

  it('Date/number girdiyi new Date ile işler', () => {
    const ms = Date.UTC(2026, 5, 1, 0, 0, 0);
    const d = parseBackendDate(ms);
    expect(d).toBeInstanceOf(Date);
    expect(d.getTime()).toBe(ms);
  });
});
