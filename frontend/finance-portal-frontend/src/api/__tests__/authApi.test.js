import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';

// authApi, ağ çağrıları için axios'u DOĞRUDAN import eder (paylaşılan ../lib/http DEĞİL).
// Bu yüzden mock hedefi 'axios'tur. Gerçek ağ isteği YAPILMAZ.
vi.mock('axios', () => ({
  default: {
    post: vi.fn(),
    get: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
    patch: vi.fn(),
  },
}));

import axios from 'axios';
import {
  OAUTH_ACTION_COMPLETE_MESSAGE,
  OAuthActionCompleteError,
  isOAuthLoginPending,
  clearOAuthPkceSession,
  redirectToLogin,
  exchangeCodeForToken,
  refreshAccessToken,
  logoutRedirect,
  registerRequest,
} from '../authApi';

// Kaynaktaki sabitler (private) — beklenen URL/parametreleri doğrulamak için tekrar tanımlandı.
const KEYCLOAK_URL = 'http://localhost:8081';
const REALM = 'finance-portal';
const CLIENT_ID = 'finance-portal-api';
const REDIRECT_URI = 'http://localhost:5173/auth/callback';
const TOKEN_ENDPOINT = `${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/token`;
const LOGOUT_ENDPOINT = `${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/logout`;
const AUTH_ENDPOINT = `${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/auth`;

const PKCE_VERIFIER_KEY = 'pkce_verifier';
const OAUTH_STATE_KEY = 'oauth_state';

const FORM_HEADER = { headers: { 'Content-Type': 'application/x-www-form-urlencoded' } };

// jsdom window.location'a gerçek navigasyon yaptırmaz ("Not implemented: navigation").
// redirectToLogin/logoutRedirect assign/href kullandığından location'ı stub'larız.
let locationStub;
let originalLocation;

beforeEach(() => {
  vi.clearAllMocks();
  sessionStorage.clear();
  localStorage.clear();

  originalLocation = window.location;
  locationStub = { assign: vi.fn(), href: '', replace: vi.fn() };
  delete window.location;
  window.location = locationStub;
});

afterEach(() => {
  window.location = originalLocation;
});

// ── Sabitler / hata sınıfı ───────────────────────────────────────────────────
describe('OAUTH_ACTION_COMPLETE_MESSAGE & OAuthActionCompleteError', () => {
  it('mesaj sabiti beklenen Türkçe metni içerir', () => {
    expect(typeof OAUTH_ACTION_COMPLETE_MESSAGE).toBe('string');
    expect(OAUTH_ACTION_COMPLETE_MESSAGE).toContain('Şifre sıfırlama');
  });

  it('hata sınıfı varsayılan mesajla kurulur ve adı OAuthActionCompleteError olur', () => {
    const err = new OAuthActionCompleteError();
    expect(err).toBeInstanceOf(Error);
    expect(err.name).toBe('OAuthActionCompleteError');
    expect(err.message).toBe(OAUTH_ACTION_COMPLETE_MESSAGE);
  });

  it('özel mesaj geçilebilir', () => {
    const err = new OAuthActionCompleteError('özel mesaj');
    expect(err.message).toBe('özel mesaj');
  });
});

// ── isOAuthLoginPending ──────────────────────────────────────────────────────
describe('isOAuthLoginPending', () => {
  it('her iki sessionStorage anahtarı varsa true', () => {
    sessionStorage.setItem(PKCE_VERIFIER_KEY, 'v');
    sessionStorage.setItem(OAUTH_STATE_KEY, 's');
    expect(isOAuthLoginPending()).toBe(true);
  });

  it('anahtarlardan biri eksikse false', () => {
    sessionStorage.setItem(PKCE_VERIFIER_KEY, 'v');
    expect(isOAuthLoginPending()).toBe(false);
  });

  it('hiçbir anahtar yoksa false', () => {
    expect(isOAuthLoginPending()).toBe(false);
  });
});

// ── clearOAuthPkceSession ────────────────────────────────────────────────────
describe('clearOAuthPkceSession', () => {
  it('PKCE verifier ve state anahtarlarını siler', () => {
    sessionStorage.setItem(PKCE_VERIFIER_KEY, 'v');
    sessionStorage.setItem(OAUTH_STATE_KEY, 's');
    clearOAuthPkceSession();
    expect(sessionStorage.getItem(PKCE_VERIFIER_KEY)).toBeNull();
    expect(sessionStorage.getItem(OAUTH_STATE_KEY)).toBeNull();
  });

  it('anahtar yokken hata vermeden çalışır', () => {
    expect(() => clearOAuthPkceSession()).not.toThrow();
  });
});

// ── redirectToLogin ──────────────────────────────────────────────────────────
describe('redirectToLogin', () => {
  it('PKCE verifier+state yazar ve Keycloak authorize URL’ine yönlendirir', async () => {
    await redirectToLogin();

    // sessionStorage'a verifier ve state yazılmalı
    expect(sessionStorage.getItem(PKCE_VERIFIER_KEY)).toBeTruthy();
    expect(sessionStorage.getItem(OAUTH_STATE_KEY)).toBeTruthy();

    // window.location.assign tek argümanla, authorize endpoint'i ile çağrılmalı
    expect(locationStub.assign).toHaveBeenCalledTimes(1);
    const url = locationStub.assign.mock.calls[0][0];
    expect(url.startsWith(`${AUTH_ENDPOINT}?`)).toBe(true);

    const qs = new URLSearchParams(url.split('?')[1]);
    expect(qs.get('response_type')).toBe('code');
    expect(qs.get('client_id')).toBe(CLIENT_ID);
    expect(qs.get('redirect_uri')).toBe(REDIRECT_URI);
    expect(qs.get('scope')).toBe('openid profile email');
    expect(qs.get('code_challenge_method')).toBe('S256');
    expect(qs.get('code_challenge')).toBeTruthy();
    // state URL'deki ile sessionStorage'daki aynı olmalı
    expect(qs.get('state')).toBe(sessionStorage.getItem(OAUTH_STATE_KEY));
    // tema yokken ui_theme parametresi eklenmemeli
    expect(qs.get('ui_theme')).toBeNull();
  });

  it('event.preventDefault çağrılır (opsiyonel event)', async () => {
    const preventDefault = vi.fn();
    await redirectToLogin({ preventDefault });
    expect(preventDefault).toHaveBeenCalledTimes(1);
  });

  it('app_theme=dark ise ui_theme=dark parametresi eklenir', async () => {
    localStorage.setItem('app_theme', JSON.stringify('dark'));
    await redirectToLogin();
    const qs = new URLSearchParams(locationStub.assign.mock.calls[0][0].split('?')[1]);
    expect(qs.get('ui_theme')).toBe('dark');
  });

  it('app_theme=light ise ui_theme=light parametresi eklenir', async () => {
    localStorage.setItem('app_theme', JSON.stringify('light'));
    await redirectToLogin();
    const qs = new URLSearchParams(locationStub.assign.mock.calls[0][0].split('?')[1]);
    expect(qs.get('ui_theme')).toBe('light');
  });

  it('bozuk app_theme JSON’u sessizce yutulur, ui_theme eklenmez', async () => {
    localStorage.setItem('app_theme', '{bozuk-json');
    await redirectToLogin();
    const qs = new URLSearchParams(locationStub.assign.mock.calls[0][0].split('?')[1]);
    expect(qs.get('ui_theme')).toBeNull();
  });

  it('geçersiz tema değeri (örn "blue") ui_theme eklemez', async () => {
    localStorage.setItem('app_theme', JSON.stringify('blue'));
    await redirectToLogin();
    const qs = new URLSearchParams(locationStub.assign.mock.calls[0][0].split('?')[1]);
    expect(qs.get('ui_theme')).toBeNull();
  });

  it('önceki oturum anahtarlarını temizleyip yenisini yazar', async () => {
    sessionStorage.setItem(PKCE_VERIFIER_KEY, 'ESKI');
    sessionStorage.setItem(OAUTH_STATE_KEY, 'ESKI');
    await redirectToLogin();
    expect(sessionStorage.getItem(PKCE_VERIFIER_KEY)).not.toBe('ESKI');
    expect(sessionStorage.getItem(OAUTH_STATE_KEY)).not.toBe('ESKI');
  });
});

// ── exchangeCodeForToken ─────────────────────────────────────────────────────
describe('exchangeCodeForToken', () => {
  function primeValidSession(state = 'STATE', verifier = 'VERIFIER') {
    sessionStorage.setItem(OAUTH_STATE_KEY, state);
    sessionStorage.setItem(PKCE_VERIFIER_KEY, verifier);
  }

  it('geçerli state+verifier ile token endpoint’ine doğru body ile POST atar ve token döner', async () => {
    primeValidSession('STATE', 'VERIFIER');
    axios.post.mockResolvedValue({
      data: {
        access_token: 'AT',
        refresh_token: 'RT',
        id_token: 'IT',
        expires_in: 300,
      },
    });

    const result = await exchangeCodeForToken('THE_CODE', 'STATE');

    expect(axios.post).toHaveBeenCalledTimes(1);
    const [url, body, config] = axios.post.mock.calls[0];
    expect(url).toBe(TOKEN_ENDPOINT);
    expect(config).toEqual(FORM_HEADER);

    const params = new URLSearchParams(body);
    expect(params.get('grant_type')).toBe('authorization_code');
    expect(params.get('client_id')).toBe(CLIENT_ID);
    expect(params.get('redirect_uri')).toBe(REDIRECT_URI);
    expect(params.get('code')).toBe('THE_CODE');
    expect(params.get('code_verifier')).toBe('VERIFIER');

    expect(result).toEqual({
      access_token: 'AT',
      refresh_token: 'RT',
      id_token: 'IT',
      expires_in: 300,
    });

    // başarıyla takas edildikten sonra PKCE oturumu temizlenmiş olmalı
    expect(sessionStorage.getItem(PKCE_VERIFIER_KEY)).toBeNull();
    expect(sessionStorage.getItem(OAUTH_STATE_KEY)).toBeNull();
  });

  it('refresh_token/id_token yoksa null’a normalize edilir', async () => {
    primeValidSession();
    axios.post.mockResolvedValue({ data: { access_token: 'AT', expires_in: 60 } });
    const result = await exchangeCodeForToken('C', 'STATE');
    expect(result).toEqual({
      access_token: 'AT',
      refresh_token: null,
      id_token: null,
      expires_in: 60,
    });
  });

  it('state uyuşmazlığı + login beklemiyorsa OAuthActionCompleteError fırlatır ve oturumu temizler', async () => {
    // verifier var ama state farklı → isOAuthLoginPending true OLUR; ama uyuşmazlık dalı
    // login pending DEĞİLKEN OAuthActionCompleteError atar. Bu yüzden state anahtarını boş bırak.
    sessionStorage.setItem(PKCE_VERIFIER_KEY, 'VERIFIER'); // sadece verifier → pending değil (state yok)
    await expect(exchangeCodeForToken('C', 'GELEN_STATE')).rejects.toBeInstanceOf(
      OAuthActionCompleteError,
    );
    expect(axios.post).not.toHaveBeenCalled();
    expect(sessionStorage.getItem(PKCE_VERIFIER_KEY)).toBeNull();
  });

  it('state uyuşmazlığı + login beklerken "state uyuşmuyor" hatası fırlatır', async () => {
    // her iki anahtar da dolu → pending true; gelen state kayıtlı state’ten farklı
    primeValidSession('KAYITLI', 'VERIFIER');
    await expect(exchangeCodeForToken('C', 'FARKLI')).rejects.toThrow('state uyuşmuyor');
    expect(axios.post).not.toHaveBeenCalled();
  });

  it('verifier yok + login beklemiyorsa OAuthActionCompleteError fırlatır', async () => {
    // state eşleşiyor ama verifier yok; sadece state var → pending değil
    sessionStorage.setItem(OAUTH_STATE_KEY, 'STATE');
    await expect(exchangeCodeForToken('C', 'STATE')).rejects.toBeInstanceOf(
      OAuthActionCompleteError,
    );
    expect(axios.post).not.toHaveBeenCalled();
  });

  it('axios reddederse genel "Token alınamadı" hatasına çevrilir', async () => {
    primeValidSession();
    axios.post.mockRejectedValue(new Error('network down'));
    await expect(exchangeCodeForToken('C', 'STATE')).rejects.toThrow(
      'Token alınamadı. Lütfen tekrar giriş yapın.',
    );
  });
});

// ── refreshAccessToken ───────────────────────────────────────────────────────
describe('refreshAccessToken', () => {
  it('refresh_token grant ile token endpoint’ine POST atar ve yeni token döner', async () => {
    axios.post.mockResolvedValue({
      data: {
        access_token: 'NEW_AT',
        refresh_token: 'NEW_RT',
        id_token: 'NEW_IT',
        expires_in: 600,
      },
    });

    const result = await refreshAccessToken('OLD_RT');

    expect(axios.post).toHaveBeenCalledTimes(1);
    const [url, body, config] = axios.post.mock.calls[0];
    expect(url).toBe(TOKEN_ENDPOINT);
    expect(config).toEqual(FORM_HEADER);
    const params = new URLSearchParams(body);
    expect(params.get('grant_type')).toBe('refresh_token');
    expect(params.get('client_id')).toBe(CLIENT_ID);
    expect(params.get('refresh_token')).toBe('OLD_RT');

    expect(result).toEqual({
      access_token: 'NEW_AT',
      refresh_token: 'NEW_RT',
      id_token: 'NEW_IT',
      expires_in: 600,
    });
  });

  it('yanıtta refresh_token yoksa eldeki refresh token korunur (rotasyon yok)', async () => {
    axios.post.mockResolvedValue({ data: { access_token: 'AT', expires_in: 100 } });
    const result = await refreshAccessToken('KEEP_ME');
    expect(result.refresh_token).toBe('KEEP_ME');
    expect(result.id_token).toBeNull();
  });

  it('axios reddederse hata yukarı fırlatılır (çağıran oturumu sonlandırır)', async () => {
    axios.post.mockRejectedValue(new Error('refresh expired'));
    await expect(refreshAccessToken('RT')).rejects.toThrow('refresh expired');
  });
});

// ── logoutRedirect ───────────────────────────────────────────────────────────
describe('logoutRedirect', () => {
  it('Keycloak logout URL’ine client_id ve post_logout_redirect_uri ile yönlendirir', () => {
    logoutRedirect('IGNORED_ID_TOKEN');
    expect(locationStub.href.startsWith(`${LOGOUT_ENDPOINT}?`)).toBe(true);
    const qs = new URLSearchParams(locationStub.href.split('?')[1]);
    expect(qs.get('client_id')).toBe(CLIENT_ID);
    expect(qs.get('post_logout_redirect_uri')).toBe('http://localhost:5173/');
  });

  it('id_token_hint KASITLI gönderilmez', () => {
    logoutRedirect('SOME_ID_TOKEN');
    const qs = new URLSearchParams(locationStub.href.split('?')[1]);
    expect(qs.get('id_token_hint')).toBeNull();
  });

  it('idToken parametresiz çağrılsa da çalışır', () => {
    expect(() => logoutRedirect()).not.toThrow();
    expect(locationStub.href).toContain(LOGOUT_ENDPOINT);
  });
});

// ── registerRequest ──────────────────────────────────────────────────────────
describe('registerRequest', () => {
  const payload = {
    username: 'berkan',
    email: 'b@x.com',
    password: 'secret',
    firstName: 'Berkan',
    lastName: 'G',
  };

  it('backend register endpoint’ine alanları POST eder ve data döner', async () => {
    axios.post.mockResolvedValue({ data: { id: 1, username: 'berkan' } });

    const result = await registerRequest(payload);

    expect(axios.post).toHaveBeenCalledTimes(1);
    expect(axios.post).toHaveBeenCalledWith('http://localhost:8080/api/auth/register', {
      username: 'berkan',
      email: 'b@x.com',
      password: 'secret',
      firstName: 'Berkan',
      lastName: 'G',
    });
    expect(result).toEqual({ id: 1, username: 'berkan' });
  });

  it('yanıt (err.response) yoksa "Sunucuya ulaşılamıyor." fırlatır', async () => {
    axios.post.mockRejectedValue(new Error('boom')); // response alanı yok
    await expect(registerRequest(payload)).rejects.toThrow('Sunucuya ulaşılamıyor.');
  });

  it('body.data alan-bazlı doğrulama hatası varsa ilk mesajı fırlatır', async () => {
    axios.post.mockRejectedValue({
      response: {
        status: 400,
        data: { data: { email: 'E-posta zaten kayıtlı', password: 'çok kısa' } },
      },
    });
    await expect(registerRequest(payload)).rejects.toThrow('E-posta zaten kayıtlı');
  });

  it('body.data var ama ilk değer boş/falsy ise body.message’a düşer', async () => {
    axios.post.mockRejectedValue({
      response: { status: 400, data: { data: { field: '' }, message: 'Doğrulama mesajı' } },
    });
    await expect(registerRequest(payload)).rejects.toThrow('Doğrulama mesajı');
  });

  it('body.data yoksa body.message fırlatılır', async () => {
    axios.post.mockRejectedValue({
      response: { status: 409, data: { message: 'Kullanıcı zaten var' } },
    });
    await expect(registerRequest(payload)).rejects.toThrow('Kullanıcı zaten var');
  });

  it('body.message de yoksa status kodlu genel mesaj fırlatılır', async () => {
    axios.post.mockRejectedValue({ response: { status: 500, data: {} } });
    await expect(registerRequest(payload)).rejects.toThrow('Kayıt başarısız (500).');
  });
});
