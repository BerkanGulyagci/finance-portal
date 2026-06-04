import { describe, it, expect } from 'vitest';
import {
  getAccountConsoleUrl,
  getAccountSigningInUrl,
  keycloakAccountUrls,
} from '../keycloakAccount';

// NOT: Test ortamında VITE_KEYCLOAK_URL / VITE_KEYCLOAK_REALM tanımlı değil,
// bu yüzden modül fallback varsayılanlarını kullanır:
//   keycloakUrl -> 'http://localhost:8081'
//   realm       -> 'finance-portal'
//   accountPath -> 'http://localhost:8081/realms/finance-portal/account'
const EXPECTED_BASE = 'http://localhost:8081';
const EXPECTED_REALM = 'finance-portal';
const EXPECTED_ACCOUNT_PATH = `${EXPECTED_BASE}/realms/${EXPECTED_REALM}/account`;

describe('getAccountConsoleUrl', () => {
  it('account console ana sayfa URL’ini (sonunda tek "/" ile) döner', () => {
    expect(getAccountConsoleUrl()).toBe(`${EXPECTED_ACCOUNT_PATH}/`);
  });

  it('string döndürür ve tam olarak bir "/" ile biter', () => {
    const url = getAccountConsoleUrl();
    expect(typeof url).toBe('string');
    expect(url.endsWith('/')).toBe(true);
    // çift slash ile bitmemeli (".../account//" değil)
    expect(url.endsWith('//')).toBe(false);
  });

  it('realm ve account segmentlerini içerir, host’tan sonra çift slash yoktur', () => {
    const url = getAccountConsoleUrl();
    expect(url).toContain(`/realms/${EXPECTED_REALM}/account`);
    // http:// dışında "//" geçmemeli (env URL trailing-slash temizliği)
    expect(url.replace('http://', '')).not.toContain('//');
  });

  it('saf fonksiyon — ardışık çağrılar aynı değeri döner', () => {
    expect(getAccountConsoleUrl()).toBe(getAccountConsoleUrl());
  });
});

describe('getAccountSigningInUrl', () => {
  it('Keycloak 26 "signing-in" (şifre değiştirme) URL’ini döner', () => {
    expect(getAccountSigningInUrl()).toBe(
      `${EXPECTED_ACCOUNT_PATH}/account-security/signing-in`,
    );
  });

  it('string döndürür ve account-security/signing-in ile biter', () => {
    const url = getAccountSigningInUrl();
    expect(typeof url).toBe('string');
    expect(url.endsWith('/account-security/signing-in')).toBe(true);
  });

  it('console URL’i ile aynı account taban yolunu paylaşır', () => {
    // signing-in URL'i, console URL'inin (sondaki "/" hariç) altında yer alır
    const base = getAccountConsoleUrl().replace(/\/$/, '');
    expect(getAccountSigningInUrl().startsWith(base)).toBe(true);
  });

  it('saf fonksiyon — ardışık çağrılar aynı değeri döner', () => {
    expect(getAccountSigningInUrl()).toBe(getAccountSigningInUrl());
  });
});

describe('keycloakAccountUrls', () => {
  it('beklenen anahtarlara (home, changePassword) sahip nesnedir', () => {
    expect(Object.keys(keycloakAccountUrls).sort()).toEqual(
      ['changePassword', 'home'].sort(),
    );
  });

  it('home değeri getAccountConsoleUrl() ile aynıdır', () => {
    expect(keycloakAccountUrls.home).toBe(getAccountConsoleUrl());
  });

  it('changePassword değeri getAccountSigningInUrl() ile aynıdır', () => {
    expect(keycloakAccountUrls.changePassword).toBe(getAccountSigningInUrl());
  });

  it('her iki değer de mutlak http(s) URL’dir', () => {
    expect(keycloakAccountUrls.home).toMatch(/^https?:\/\//);
    expect(keycloakAccountUrls.changePassword).toMatch(/^https?:\/\//);
  });
});
