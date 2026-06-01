import axios from 'axios';

const KEYCLOAK_URL = 'http://localhost:8081';
const REALM = 'finance-portal';
const CLIENT_ID = 'finance-portal-api';
const REDIRECT_URI = 'http://localhost:5173/auth/callback';

const AUTH_ENDPOINT = `${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/auth`;
const TOKEN_ENDPOINT = `${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/token`;
const LOGOUT_ENDPOINT = `${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/logout`;

const PKCE_VERIFIER_KEY = 'pkce_verifier';
const OAUTH_STATE_KEY = 'oauth_state';

export const OAUTH_ACTION_COMPLETE_MESSAGE =
  'Şifre sıfırlama işlemi tamamlandıysa lütfen yeni şifrenizle tekrar giriş yapın.';

/**
 * Keycloak forgot-password / required-action redirectleri PKCE login state'i taşımaz.
 * Normal login callback'inden ayırt etmek için kullanılır.
 */
export class OAuthActionCompleteError extends Error {
  constructor(message = OAUTH_ACTION_COMPLETE_MESSAGE) {
    super(message);
    this.name = 'OAuthActionCompleteError';
  }
}

export function isOAuthLoginPending() {
  return !!(
    sessionStorage.getItem(PKCE_VERIFIER_KEY) && sessionStorage.getItem(OAUTH_STATE_KEY)
  );
}

export function clearOAuthPkceSession() {
  sessionStorage.removeItem(PKCE_VERIFIER_KEY);
  sessionStorage.removeItem(OAUTH_STATE_KEY);
}

// ── PKCE helpers ──────────────────────────────────────────────────────────────
function bytesToBase64Url(bytes) {
  let binary = '';
  for (let i = 0; i < bytes.length; i += 1) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=/g, '');
}

function generateCodeVerifier() {
  const array = new Uint8Array(32);
  crypto.getRandomValues(array);
  return bytesToBase64Url(array);
}

async function generateCodeChallenge(verifier) {
  const data = new TextEncoder().encode(verifier);
  const digest = await crypto.subtle.digest('SHA-256', data);
  return btoa(String.fromCharCode(...new Uint8Array(digest)))
    .replace(/\+/g, '-').replace(/\//g, '_').replace(/=/g, '');
}

// ── Authorization Code Flow ───────────────────────────────────────────────────

/**
 * Redirects user to Keycloak login page (Authorization Code + PKCE).
 * Keycloak handles password + TOTP in its own UI.
 */
/**
 * PKCE login başlatır ve Keycloak authorize URL'ine yönlendirir.
 * @param {Event} [event] React click event (opsiyonel)
 */
export async function redirectToLogin(event) {
  event?.preventDefault?.();

  clearOAuthPkceSession();

  const verifier = generateCodeVerifier();
  const challenge = await generateCodeChallenge(verifier);
  const state = generateCodeVerifier();

  sessionStorage.setItem(PKCE_VERIFIER_KEY, verifier);
  sessionStorage.setItem(OAUTH_STATE_KEY, state);

  const params = new URLSearchParams({
    response_type: 'code',
    client_id: CLIENT_ID,
    redirect_uri: REDIRECT_URI,
    scope: 'openid profile email',
    state,
    code_challenge: challenge,
    code_challenge_method: 'S256',
  });

  // Keycloak temasını app ile senkronlamak için temayı URL'de taşı. Kaynak doğruluk
  // localStorage 'app_theme' (toggle bunu kesin yazar) — cross-port cookie'ye bağlı
  // kalmaz. Keycloak teması (template.ftl) bu paramı okuyup login ekranını boyar.
  let themePref = null;
  try {
    const raw = localStorage.getItem('app_theme');
    if (raw) themePref = JSON.parse(raw);
  } catch { /* yoksay */ }
  if (themePref === 'dark' || themePref === 'light') params.set('ui_theme', themePref);

  window.location.assign(`${AUTH_ENDPOINT}?${params}`);
}

/**
 * Exchanges authorization code for tokens (called on /auth/callback).
 */
export async function exchangeCodeForToken(code, state) {
  const savedState = sessionStorage.getItem(OAUTH_STATE_KEY);
  const verifier = sessionStorage.getItem(PKCE_VERIFIER_KEY);
  const loginPending = isOAuthLoginPending();

  if (state !== savedState) {
    if (!loginPending) {
      clearOAuthPkceSession();
      throw new OAuthActionCompleteError();
    }
    throw new Error('Güvenlik hatası: state uyuşmuyor.');
  }

  if (!verifier) {
    if (!loginPending) {
      clearOAuthPkceSession();
      throw new OAuthActionCompleteError();
    }
    throw new Error('PKCE verifier bulunamadı.');
  }

  clearOAuthPkceSession();

  const body = new URLSearchParams({
    grant_type: 'authorization_code',
    client_id: CLIENT_ID,
    redirect_uri: REDIRECT_URI,
    code,
    code_verifier: verifier,
  });

  try {
    const { data } = await axios.post(TOKEN_ENDPOINT, body.toString(), {
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    });
    return {
      access_token: data.access_token,
      refresh_token: data.refresh_token ?? null,
      id_token: data.id_token ?? null,
      expires_in: data.expires_in,
    };
  } catch (err) {
    throw new Error('Token alınamadı. Lütfen tekrar giriş yapın.');
  }
}

/**
 * Refresh token ile yeni access token alır (sessiz oturum yenileme).
 * Keycloak refresh token'ı rotasyona sokabilir; yeni refresh_token dönerse onu kullan,
 * dönmezse eldekini koru. Hata fırlatırsa (refresh token süresi dolmuş/iptal) çağıran
 * oturumu sonlandırır.
 */
export async function refreshAccessToken(refreshToken) {
  const body = new URLSearchParams({
    grant_type: 'refresh_token',
    client_id: CLIENT_ID,
    refresh_token: refreshToken,
  });
  const { data } = await axios.post(TOKEN_ENDPOINT, body.toString(), {
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
  });
  return {
    access_token: data.access_token,
    refresh_token: data.refresh_token ?? refreshToken,
    id_token: data.id_token ?? null,
    expires_in: data.expires_in,
  };
}

/**
 * Logout: clears local session and redirects to Keycloak logout.
 */
export function logoutRedirect(idToken) {
  const params = new URLSearchParams({
    client_id: CLIENT_ID,
    post_logout_redirect_uri: 'http://localhost:5173/',
  });
  // id_token_hint kasıtlı GÖNDERİLMİYOR: Keycloak görünür logout akışını göstersin ve oturumu
  // güvenle sonlandırsın. (id_token_hint, access token yenilendiğinde bayatlayıp Keycloak'ın
  // logout'u reddetmesine/atlamasına yol açabiliyordu — client_id + post_logout_redirect_uri yeterli.)
  window.location.href = `${LOGOUT_ENDPOINT}?${params}`;
}

/**
 * Registers a new user via backend → LDAP.
 */
export async function registerRequest({ username, email, password, firstName, lastName }) {
  try {
    const { data } = await axios.post('http://localhost:8080/api/auth/register', {
      username, email, password, firstName, lastName,
    });
    return data;
  } catch (err) {
    if (!err.response) throw new Error('Sunucuya ulaşılamıyor.');
    const body = err.response.data;
    if (body?.data && typeof body.data === 'object') {
      const firstMsg = Object.values(body.data)[0];
      throw new Error(firstMsg || body.message || 'Doğrulama hatası.');
    }
    throw new Error(body?.message || `Kayıt başarısız (${err.response.status}).`);
  }
}
