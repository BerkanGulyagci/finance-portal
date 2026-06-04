import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';

// '../../lib/http' default export'u (axios client) MOCK'lanır — gerçek ağ isteği atılmasın.
// prefs.js sadece bu client'ın put/get metodlarını kullanır; gerisi localStorage (jsdom) üzerinde saf mantıktır.
vi.mock('../../lib/http', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
    patch: vi.fn(),
  },
}));

import client from '../../lib/http';
import { prefGet, prefSet, prefSetSync, hydratePrefs } from '../prefs';

const TOKEN_KEY = 'auth_token';
// Kaynaktaki sabitlerle birebir aynı (prefs.js içi private):
const DEBOUNCE_MS = 600;
const JSON_HEADERS = { headers: { 'Content-Type': 'application/json' } };

/** Giriş yapılmış (authed) durumu: auth_token mevcut. */
function signIn() {
  localStorage.setItem(TOKEN_KEY, 'jwt-token');
}

beforeEach(() => {
  localStorage.clear();
  vi.clearAllMocks();
});

afterEach(() => {
  // prefSet sahte zamanlayıcı kullanabilir; her testten sonra gerçek zamana dön.
  vi.useRealTimers();
});

// ─────────────────────────────────────────────────────────────────────────────
// prefGet
// ─────────────────────────────────────────────────────────────────────────────
describe('prefGet', () => {
  it('kayıtlı JSON değeri parse edip döndürür', () => {
    localStorage.setItem('theme', JSON.stringify('dark'));
    expect(prefGet('theme')).toBe('dark');
  });

  it('obje/dizi gibi karmaşık JSON değerleri de doğru parse eder', () => {
    localStorage.setItem('grid', JSON.stringify({ cols: 3, items: [1, 2] }));
    expect(prefGet('grid')).toEqual({ cols: 3, items: [1, 2] });
  });

  it('anahtar yoksa varsayılan null döner (fallback verilmediğinde)', () => {
    expect(prefGet('yok')).toBeNull();
  });

  it('anahtar yoksa verilen fallback değerini döner', () => {
    expect(prefGet('yok', 'varsayilan')).toBe('varsayilan');
  });

  it('değer "null" string\'i (JSON null) olarak kayıtlıysa fallback DEĞİL parse edilen null döner', () => {
    // localStorage.getItem != null (string "null" mevcut) → JSON.parse('null') === null döner.
    localStorage.setItem('k', JSON.stringify(null));
    expect(prefGet('k', 'fb')).toBeNull();
  });

  it('bozuk JSON kayıtlıysa parse hatasında fallback\'e düşer', () => {
    localStorage.setItem('bozuk', '{gecersiz json');
    expect(prefGet('bozuk', 42)).toBe(42);
  });

  it('falsy ama geçerli değerleri (0, false, boş string) olduğu gibi döndürür (fallback\'e düşmez)', () => {
    localStorage.setItem('zero', JSON.stringify(0));
    localStorage.setItem('flag', JSON.stringify(false));
    localStorage.setItem('empty', JSON.stringify(''));
    expect(prefGet('zero', 99)).toBe(0);
    expect(prefGet('flag', true)).toBe(false);
    expect(prefGet('empty', 'x')).toBe('');
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// prefSet (localStorage'a hemen + authed ise debounce'lu PUT)
// ─────────────────────────────────────────────────────────────────────────────
describe('prefSet', () => {
  it('her zaman localStorage\'a JSON olarak hemen yazar', () => {
    prefSet('lang', 'tr');
    expect(JSON.parse(localStorage.getItem('lang'))).toBe('tr');
  });

  it('anonim kullanıcıda (auth_token yok) sunucuya PUT GÖNDERMEZ', () => {
    vi.useFakeTimers();
    prefSet('lang', 'en');
    vi.advanceTimersByTime(DEBOUNCE_MS + 100);
    expect(client.put).not.toHaveBeenCalled();
  });

  it('authed kullanıcıda debounce süresi DOLMADAN PUT atmaz', () => {
    vi.useFakeTimers();
    signIn();
    prefSet('lang', 'en');
    vi.advanceTimersByTime(DEBOUNCE_MS - 1);
    expect(client.put).not.toHaveBeenCalled();
  });

  it('authed kullanıcıda debounce dolunca doğru URL + JSON gövde + JSON header ile PUT eder', () => {
    vi.useFakeTimers();
    signIn();
    client.put.mockResolvedValue({ data: {} });
    prefSet('lang', 'en');
    vi.advanceTimersByTime(DEBOUNCE_MS);
    expect(client.put).toHaveBeenCalledTimes(1);
    expect(client.put).toHaveBeenCalledWith(
      '/api/me/preferences/lang',
      JSON.stringify('en'),
      JSON_HEADERS,
    );
  });

  it('anahtardaki özel karakterleri URL\'de encode eder (encodeURIComponent)', () => {
    vi.useFakeTimers();
    signIn();
    client.put.mockResolvedValue({ data: {} });
    prefSet('grid layout/v2', { a: 1 });
    vi.advanceTimersByTime(DEBOUNCE_MS);
    expect(client.put).toHaveBeenCalledWith(
      '/api/me/preferences/grid%20layout%2Fv2',
      JSON.stringify({ a: 1 }),
      JSON_HEADERS,
    );
  });

  it('aynı anahtara hızlı ardışık çağrılarda debounce uygular: sadece SON değer için tek PUT', () => {
    vi.useFakeTimers();
    signIn();
    client.put.mockResolvedValue({ data: {} });
    prefSet('lang', 'a');
    vi.advanceTimersByTime(100);
    prefSet('lang', 'b');
    vi.advanceTimersByTime(100);
    prefSet('lang', 'c');
    vi.advanceTimersByTime(DEBOUNCE_MS);
    expect(client.put).toHaveBeenCalledTimes(1);
    expect(client.put).toHaveBeenCalledWith(
      '/api/me/preferences/lang',
      JSON.stringify('c'),
      JSON_HEADERS,
    );
    // Son yazılan değer localStorage'da da güncel olmalı.
    expect(JSON.parse(localStorage.getItem('lang'))).toBe('c');
  });

  it('PUT reject olsa bile (best-effort .catch) hata fırlatmaz / unhandled rejection olmaz', async () => {
    vi.useFakeTimers();
    signIn();
    client.put.mockRejectedValue(new Error('network'));
    expect(() => {
      prefSet('lang', 'en');
      vi.advanceTimersByTime(DEBOUNCE_MS);
    }).not.toThrow();
    // .catch zincirinin çözülmesine izin ver (mikrotask kuyruğu).
    await Promise.resolve();
    expect(client.put).toHaveBeenCalledTimes(1);
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// prefSetSync (localStorage + hemen PUT, promise döner)
// ─────────────────────────────────────────────────────────────────────────────
describe('prefSetSync', () => {
  it('her zaman localStorage\'a hemen yazar', () => {
    prefSetSync('lang', 'tr');
    expect(JSON.parse(localStorage.getItem('lang'))).toBe('tr');
  });

  it('anonim kullanıcıda PUT göndermez ve çözümlenmiş (resolved) bir Promise döner', async () => {
    const ret = prefSetSync('lang', 'en');
    expect(ret).toBeInstanceOf(Promise);
    await expect(ret).resolves.toBeUndefined();
    expect(client.put).not.toHaveBeenCalled();
  });

  it('authed kullanıcıda HEMEN doğru URL + JSON gövde + header ile PUT eder ve client promise\'ini döner', async () => {
    signIn();
    const resp = { data: { ok: true } };
    client.put.mockResolvedValue(resp);
    const ret = prefSetSync('lang', 'en');
    expect(client.put).toHaveBeenCalledTimes(1);
    expect(client.put).toHaveBeenCalledWith(
      '/api/me/preferences/lang',
      JSON.stringify('en'),
      JSON_HEADERS,
    );
    // Dönen promise client.put'un promise'idir → aynı response'a çözülür.
    await expect(ret).resolves.toBe(resp);
  });

  it('authed kullanıcıda PUT reject olursa dönen promise de REJECT olur (UI feedback için)', async () => {
    signIn();
    const err = new Error('500');
    client.put.mockRejectedValue(err);
    await expect(prefSetSync('lang', 'en')).rejects.toBe(err);
  });

  it('bekleyen prefSet debounce\'unu iptal eder: prefSetSync sonrası fazladan (debounce) PUT atılmaz', () => {
    vi.useFakeTimers();
    signIn();
    client.put.mockResolvedValue({ data: {} });
    prefSet('lang', 'eski');        // debounce zamanlayıcısı kurulur
    vi.advanceTimersByTime(100);    // henüz dolmadı
    prefSetSync('lang', 'yeni');    // bekleyen timer iptal + hemen PUT
    expect(client.put).toHaveBeenCalledTimes(1);
    vi.advanceTimersByTime(DEBOUNCE_MS * 2); // iptal edilen timer ateşlememeli
    expect(client.put).toHaveBeenCalledTimes(1);
    expect(client.put).toHaveBeenLastCalledWith(
      '/api/me/preferences/lang',
      JSON.stringify('yeni'),
      JSON_HEADERS,
    );
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// hydratePrefs (sunucudan çek → localStorage'a yaz)
// ─────────────────────────────────────────────────────────────────────────────
describe('hydratePrefs', () => {
  it('anonim kullanıcıda sunucuya GET göndermez ve sessizce döner', async () => {
    await hydratePrefs();
    expect(client.get).not.toHaveBeenCalled();
  });

  it('authed kullanıcıda /api/me/preferences GET eder', async () => {
    signIn();
    client.get.mockResolvedValue({ data: { data: {} } });
    await hydratePrefs();
    expect(client.get).toHaveBeenCalledTimes(1);
    expect(client.get).toHaveBeenCalledWith('/api/me/preferences');
  });

  it('wrapper.data içindeki her tercihi JSON olarak localStorage\'a yazar', async () => {
    signIn();
    client.get.mockResolvedValue({
      data: { data: { lang: 'tr', theme: 'dark', grid: { cols: 2 } } },
    });
    await hydratePrefs();
    expect(JSON.parse(localStorage.getItem('lang'))).toBe('tr');
    expect(JSON.parse(localStorage.getItem('theme'))).toBe('dark');
    expect(JSON.parse(localStorage.getItem('grid'))).toEqual({ cols: 2 });
  });

  it('wrapper.data yoksa (null/undefined) hata vermez, hiçbir tercih yazmaz', async () => {
    signIn();
    client.get.mockResolvedValue({ data: { data: null } });
    await expect(hydratePrefs()).resolves.toBeUndefined();
    // auth_token dışında localStorage'a bir şey eklenmemeli.
    expect(localStorage.length).toBe(1);
  });

  it('wrapper tamamen yoksa (data alanı undefined) boş objeye düşer, hata vermez', async () => {
    signIn();
    client.get.mockResolvedValue({});
    await expect(hydratePrefs()).resolves.toBeUndefined();
    expect(localStorage.length).toBe(1); // sadece auth_token
  });

  it('GET reject olursa (best-effort) hata yutulur, yerel değerler korunur', async () => {
    signIn();
    localStorage.setItem('lang', JSON.stringify('mevcut'));
    client.get.mockRejectedValue(new Error('network'));
    await expect(hydratePrefs()).resolves.toBeUndefined();
    // Yerel değer GET hatasına rağmen olduğu gibi kalmalı.
    expect(JSON.parse(localStorage.getItem('lang'))).toBe('mevcut');
  });
});
