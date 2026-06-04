import { describe, it, expect } from 'vitest';
import {
  BAN_STATUS,
  BAN_STATUS_FILTER,
  formatBanUntil,
  isActiveUser,
  isTemporaryBan,
  isPermanentBan,
  getBanStatusLabel,
} from '../banDisplay';

describe('sabitler', () => {
  it('BAN_STATUS doğru değerleri içerir', () => {
    expect(BAN_STATUS).toEqual({
      ACTIVE: 'ACTIVE',
      TEMPORARY_BANNED: 'TEMPORARY_BANNED',
      PERMANENT_BANNED: 'PERMANENT_BANNED',
    });
  });

  it('BAN_STATUS_FILTER doğru değerleri içerir', () => {
    expect(BAN_STATUS_FILTER).toEqual({
      ALL: 'ALL',
      ACTIVE: 'ACTIVE',
      BANNED: 'BANNED',
    });
  });
});

describe('formatBanUntil', () => {
  it('null/undefined/boş string → null (falsy erken-return)', () => {
    expect(formatBanUntil(null)).toBeNull();
    expect(formatBanUntil(undefined)).toBeNull();
    expect(formatBanUntil('')).toBeNull();
  });

  it('geçersiz tarih (parse edilemeyen) → null', () => {
    // new Date('saçma') → Invalid Date → getTime() NaN → null
    expect(formatBanUntil('saçma-tarih')).toBeNull();
    expect(formatBanUntil('not-a-date')).toBeNull();
  });

  it('geçerli ISO tarihi → null OLMAYAN biçimli string döner', () => {
    const out = formatBanUntil('2026-06-15T14:30:00Z');
    expect(out).not.toBeNull();
    expect(typeof out).toBe('string');
    // tr-TR locale çıktısı; yıl her ortamda görünmeli
    expect(out).toContain('2026');
  });

  it('Date nesnesini de kabul eder (new Date(date) idempotent)', () => {
    const out = formatBanUntil(new Date('2025-01-01T00:00:00Z'));
    expect(out).not.toBeNull();
    expect(out).toContain('2025');
  });

  it('epoch milisaniye (sayı) ile çağrılınca geçerli string döner', () => {
    // Number.isNaN(getTime()) false olduğu sürece biçimlenir
    const out = formatBanUntil(Date.UTC(2024, 11, 31, 12, 0, 0));
    expect(out).not.toBeNull();
    expect(out).toContain('2024');
  });
});

describe('isActiveUser', () => {
  it('banStatus ACTIVE → true', () => {
    expect(isActiveUser({ banStatus: BAN_STATUS.ACTIVE })).toBe(true);
  });

  it('enabled=true ve banStatus yok → true (ikinci dal)', () => {
    // banStatus undefined/null iken enabled true ise aktif sayılır
    expect(isActiveUser({ enabled: true })).toBe(true);
    expect(isActiveUser({ enabled: true, banStatus: null })).toBe(true);
    expect(isActiveUser({ enabled: true, banStatus: '' })).toBe(true);
  });

  it('enabled=true AMA banStatus dolu → false (banlı kullanıcı)', () => {
    // enabled olsa bile banStatus varsa ikinci dal kapanır
    expect(isActiveUser({ enabled: true, banStatus: BAN_STATUS.TEMPORARY_BANNED })).toBe(false);
    expect(isActiveUser({ enabled: true, banStatus: BAN_STATUS.PERMANENT_BANNED })).toBe(false);
  });

  it('enabled=false ve banStatus yok → falsy (aktif değil)', () => {
    // !user.banStatus true ama user.enabled false → ikinci dal undefined döner
    expect(isActiveUser({ enabled: false })).toBeFalsy();
  });

  it('null/undefined kullanıcı → falsy (optional chaining koruması)', () => {
    expect(isActiveUser(null)).toBeFalsy();
    expect(isActiveUser(undefined)).toBeFalsy();
  });

  it('boş nesne → falsy', () => {
    expect(isActiveUser({})).toBeFalsy();
  });
});

describe('isTemporaryBan', () => {
  it('banStatus TEMPORARY_BANNED → true', () => {
    expect(isTemporaryBan({ banStatus: BAN_STATUS.TEMPORARY_BANNED })).toBe(true);
  });

  it('farklı banStatus → false', () => {
    expect(isTemporaryBan({ banStatus: BAN_STATUS.PERMANENT_BANNED })).toBe(false);
    expect(isTemporaryBan({ banStatus: BAN_STATUS.ACTIVE })).toBe(false);
  });

  it('null/undefined/boş kullanıcı → false', () => {
    expect(isTemporaryBan(null)).toBe(false);
    expect(isTemporaryBan(undefined)).toBe(false);
    expect(isTemporaryBan({})).toBe(false);
  });
});

describe('isPermanentBan', () => {
  it('banStatus PERMANENT_BANNED → true', () => {
    expect(isPermanentBan({ banStatus: BAN_STATUS.PERMANENT_BANNED })).toBe(true);
  });

  it('farklı banStatus → false', () => {
    expect(isPermanentBan({ banStatus: BAN_STATUS.TEMPORARY_BANNED })).toBe(false);
    expect(isPermanentBan({ banStatus: BAN_STATUS.ACTIVE })).toBe(false);
  });

  it('null/undefined/boş kullanıcı → false', () => {
    expect(isPermanentBan(null)).toBe(false);
    expect(isPermanentBan(undefined)).toBe(false);
    expect(isPermanentBan({})).toBe(false);
  });
});

describe('getBanStatusLabel', () => {
  it('aktif kullanıcı → "Aktif"', () => {
    expect(getBanStatusLabel({ banStatus: BAN_STATUS.ACTIVE })).toBe('Aktif');
    expect(getBanStatusLabel({ enabled: true })).toBe('Aktif');
  });

  it('geçici ban → "Geçici ban"', () => {
    expect(getBanStatusLabel({ banStatus: BAN_STATUS.TEMPORARY_BANNED })).toBe('Geçici ban');
  });

  it('kalıcı ban → "Kalıcı ban"', () => {
    expect(getBanStatusLabel({ banStatus: BAN_STATUS.PERMANENT_BANNED })).toBe('Kalıcı ban');
  });

  it('hiçbir dala uymayan (enabled=false, banStatus yok) → "Bilinmiyor"', () => {
    // aktif değil, geçici değil, kalıcı değil → son fallback
    expect(getBanStatusLabel({ enabled: false })).toBe('Bilinmiyor');
  });

  it('bilinmeyen banStatus değeri → "Bilinmiyor"', () => {
    expect(getBanStatusLabel({ banStatus: 'FOO' })).toBe('Bilinmiyor');
  });

  it('null/undefined/boş kullanıcı → "Bilinmiyor"', () => {
    expect(getBanStatusLabel(null)).toBe('Bilinmiyor');
    expect(getBanStatusLabel(undefined)).toBe('Bilinmiyor');
    expect(getBanStatusLabel({})).toBe('Bilinmiyor');
  });
});
