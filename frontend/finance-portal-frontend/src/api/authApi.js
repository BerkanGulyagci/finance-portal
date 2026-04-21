import axios from 'axios';

const KEYCLOAK_URL = 'http://localhost:8081';
const REALM = 'finance-portal';
const CLIENT_ID = 'finance-portal-api';
const REDIRECT_URI = 'http://localhost:5173/auth/callback';

const AUTH_ENDPOINT = `${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/auth`;
const TOKEN_ENDPOINT = `${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/token`;
const LOGOUT_ENDPOINT = `${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/logout`;

// ── PKCE helpers ──────────────────────────────────────────────────────────────
function generateCodeVerifier() {
  const array = new Uint8Array(32);
  crypto.getRandomValues(array);
  return btoa(String.fromCharCode(...array))
    .replace(/\+/g, '-').replace(/\//g, '_').replace(/=/g, '');
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
export async function redirectToLogin() {
  const verifier = generateCodeVerifier();
  const challenge = await generateCodeChallenge(verifier);
  const state = generateCodeVerifier(); // random state for CSRF

  sessionStorage.setItem('pkce_verifier', verifier);
  sessionStorage.setItem('oauth_state', state);

  const params = new URLSearchParams({
    response_type: 'code',
    client_id: CLIENT_ID,
    redirect_uri: REDIRECT_URI,
    scope: 'openid profile email',
    state,
    code_challenge: challenge,
    code_challenge_method: 'S256',
  });

  window.location.href = `${AUTH_ENDPOINT}?${params}`;
}

/**
 * Exchanges authorization code for tokens (called on /auth/callback).
 */
export async function exchangeCodeForToken(code, state) {
  const savedState = sessionStorage.getItem('oauth_state');
  const verifier = sessionStorage.getItem('pkce_verifier');

  if (state !== savedState) throw new Error('Güvenlik hatası: state uyuşmuyor.');
  if (!verifier) throw new Error('PKCE verifier bulunamadı.');

  sessionStorage.removeItem('pkce_verifier');
  sessionStorage.removeItem('oauth_state');

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
      expires_in: data.expires_in,
    };
  } catch (err) {
    throw new Error('Token alınamadı. Lütfen tekrar giriş yapın.');
  }
}

/**
 * Logout: clears local session and redirects to Keycloak logout.
 */
export function logoutRedirect(idToken) {
  const params = new URLSearchParams({
    client_id: CLIENT_ID,
    post_logout_redirect_uri: 'http://localhost:5173/',
  });
  if (idToken) params.set('id_token_hint', idToken);
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
