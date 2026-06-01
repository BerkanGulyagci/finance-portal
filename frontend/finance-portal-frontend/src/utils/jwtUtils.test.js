import { describe, it, expect } from 'vitest';
import { parseJwtPayload, isTokenValid, extractAuthClaims } from './jwtUtils';

// ── Test yardımcısı: payload nesnesini base64url JWT'ye çevirir ────────────────
// JWT = header.payload.signature; modül yalnız payload'u (index 1) decode eder.
function makeToken(payload, { header = { alg: 'HS256', typ: 'JWT' }, signature = 'sig' } = {}) {
  const b64url = (obj) =>
    btoa(JSON.stringify(obj))
      .replace(/\+/g, '-')
      .replace(/\//g, '_')
      .replace(/=+$/, ''); // base64url: padding yok
  return `${b64url(header)}.${b64url(payload)}.${signature}`;
}

describe('parseJwtPayload', () => {
  it('null/boş/undefined için null döner', () => {
    expect(parseJwtPayload(null)).toBeNull();
    expect(parseJwtPayload('')).toBeNull();
    expect(parseJwtPayload(undefined)).toBeNull();
  });

  it('geçerli token payload nesnesini döner', () => {
    const token = makeToken({ sub: '42', email: 'a@b.com' });
    expect(parseJwtPayload(token)).toEqual({ sub: '42', email: 'a@b.com' });
  });

  it('base64url özel karakterli (- ve _) payload doğru decode edilir', () => {
    // İçinde -/_ üretecek değerler — replace(/-/g,"+") ve replace(/_/g,"/") yolunu test eder.
    const payload = { note: 'a>?>?b', list: [1, 2, 3], roles: ['ADMIN'] };
    const token = makeToken(payload);
    expect(parseJwtPayload(token)).toEqual(payload);
  });

  it('payload kısmı (index 1) olmayan token → null', () => {
    expect(parseJwtPayload('onlyonepart')).toBeNull();
  });

  it('bozuk base64 payload → null (catch)', () => {
    expect(parseJwtPayload('aaa.!!!notbase64!!!.bbb')).toBeNull();
  });

  it('geçerli base64 ama JSON olmayan payload → null', () => {
    const notJson = btoa('hello world').replace(/=+$/, '');
    expect(parseJwtPayload(`h.${notJson}.s`)).toBeNull();
  });
});

describe('isTokenValid', () => {
  it('exp gelecekte ise true', () => {
    const future = Math.floor(Date.now() / 1000) + 3600;
    expect(isTokenValid(makeToken({ exp: future }))).toBe(true);
  });

  it('exp geçmişte ise false', () => {
    const past = Math.floor(Date.now() / 1000) - 3600;
    expect(isTokenValid(makeToken({ exp: past }))).toBe(false);
  });

  it('exp claim yoksa false', () => {
    expect(isTokenValid(makeToken({ sub: '1' }))).toBe(false);
  });

  it('geçersiz/null token false', () => {
    expect(isTokenValid(null)).toBe(false);
    expect(isTokenValid('garbage')).toBe(false);
  });
});

describe('extractAuthClaims', () => {
  it('geçersiz token için boş/varsayılan claim seti döner', () => {
    expect(extractAuthClaims(null)).toEqual({
      userId: null,
      username: null,
      email: null,
      emailVerified: false,
      roles: [],
      isAdmin: false,
    });
  });

  it('tüm claim alanlarını çıkarır + ADMIN rolü isAdmin true yapar', () => {
    const token = makeToken({
      sub: 'user-1',
      preferred_username: 'berkan',
      email: 'b@x.com',
      email_verified: true,
      realm_access: { roles: ['USER', 'ADMIN'] },
    });
    expect(extractAuthClaims(token)).toEqual({
      userId: 'user-1',
      username: 'berkan',
      email: 'b@x.com',
      emailVerified: true,
      roles: ['USER', 'ADMIN'],
      isAdmin: true,
    });
  });

  it('email_verified "true" string de kabul edilir', () => {
    const token = makeToken({ sub: '1', email_verified: 'true' });
    expect(extractAuthClaims(token).emailVerified).toBe(true);
  });

  it('preferred_username yoksa username fallback', () => {
    const token = makeToken({ sub: '1', username: 'fallback' });
    expect(extractAuthClaims(token).username).toBe('fallback');
  });

  it('realm_access.roles yoksa boş dizi ve isAdmin false', () => {
    const token = makeToken({ sub: '1' });
    const claims = extractAuthClaims(token);
    expect(claims.roles).toEqual([]);
    expect(claims.isAdmin).toBe(false);
    expect(claims.emailVerified).toBe(false);
  });

  it('roles dizi değilse boş diziye normalize edilir', () => {
    const token = makeToken({ sub: '1', realm_access: { roles: 'ADMIN' } });
    expect(extractAuthClaims(token).roles).toEqual([]);
  });
});
