import axios from 'axios';
import { refreshAccessToken } from '../api/authApi';

const TOKEN_KEY = 'auth_token';
const ID_TOKEN_KEY = 'id_token';
const REFRESH_TOKEN_KEY = 'refresh_token';
/** Access token bitmeden bu kadar (ms) önce yenilemeyi dene. */
const REFRESH_SKEW_MS = 30_000;

const client = axios.create({
  // Boş baseURL → relative path kullanır.
  // Docker'da Nginx /api/v1/* isteklerini backend:8080'e proxy'ler.
  // Local dev'de vite.config.js proxy'si devreye girer.
  baseURL: '',
});

// Tek-uçuşan refresh: eşzamanlı istekler tek yenileme çağrısını paylaşır (refresh
// token rotasyonunda yarış/çift-yenileme olmasın). authApi.refreshAccessToken ham
// axios kullanır (bu client'ı DEĞİL) → interceptor özyinelemesi olmaz.
let refreshPromise = null;

function refreshTokens() {
  if (refreshPromise) return refreshPromise;
  const rt = localStorage.getItem(REFRESH_TOKEN_KEY);
  if (!rt) return Promise.resolve(null);
  refreshPromise = refreshAccessToken(rt)
    .then((t) => {
      localStorage.setItem(TOKEN_KEY, t.access_token);
      if (t.id_token) localStorage.setItem(ID_TOKEN_KEY, t.id_token);
      if (t.refresh_token) localStorage.setItem(REFRESH_TOKEN_KEY, t.refresh_token);
      // AuthContext React state'ini senkronlasın diye olay yayınla.
      window.dispatchEvent(new Event('auth:tokens-updated'));
      return t.access_token;
    })
    .catch(() => null)
    .finally(() => { refreshPromise = null; });
  return refreshPromise;
}

function isTokenExpired(token, skewMs = 0) {
  try {
    const payload = JSON.parse(atob(token.split('.')[1]));
    return payload.exp * 1000 - skewMs < Date.now();
  } catch {
    return true;
  }
}

function forceLogout(redirectPath = '/login') {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(ID_TOKEN_KEY);
  localStorage.removeItem(REFRESH_TOKEN_KEY);
  window.location.href = redirectPath;
}

// İstek: token süresi dolmuş/dolmak üzere → force-logout yerine ÖNCE sessiz yenile.
client.interceptors.request.use(async (config) => {
  let token = localStorage.getItem(TOKEN_KEY);
  if (token && isTokenExpired(token, REFRESH_SKEW_MS)) {
    token = await refreshTokens();
    if (!token) {
      forceLogout('/login');
      return Promise.reject(new Error('Oturum yenilenemedi'));
    }
  }
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

// Yanıt: 401 → bir kez sessiz yenile + isteği tekrarla (force-logout yerine);
// banlı (403) hesapları yine sonlandır.
client.interceptors.response.use(
  response => response,
  async (error) => {
    const status = error.response?.status;
    const message = error.response?.data?.message || '';
    const original = error.config;

    if (status === 401 && original && !original._retried) {
      const newToken = await refreshTokens();
      if (newToken) {
        original._retried = true;
        original.headers = original.headers || {};
        original.headers.Authorization = `Bearer ${newToken}`;
        return client(original);
      }
      forceLogout('/login');
    } else if (status === 403 && message.startsWith('ACCOUNT_DISABLED')) {
      forceLogout('/login?banned=1');
    } else if (status === 403 && message.startsWith('EMAIL_NOT_VERIFIED')) {
      window.location.href = '/verify-email';
    }

    return Promise.reject(error);
  },
);

export default client;
