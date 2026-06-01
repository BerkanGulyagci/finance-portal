import { createContext, useContext, useEffect, useMemo, useState } from 'react';
import { logoutRedirect, refreshAccessToken } from '../api/authApi';
import { extractAuthClaims, isTokenValid, parseJwtPayload } from '../utils/jwtUtils';

const TOKEN_KEY = 'auth_token';
const ID_TOKEN_KEY = 'id_token';
const REFRESH_TOKEN_KEY = 'refresh_token';
/** Access token bitmeden bu kadar (ms) önce proaktif yenile. */
const REFRESH_SKEW_MS = 60_000;

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [token, setToken] = useState(() => localStorage.getItem(TOKEN_KEY));
  const [idToken, setIdToken] = useState(() => localStorage.getItem(ID_TOKEN_KEY));
  // Açılışta access token süresi dolmuş AMA refresh token varsa oturumu sessizce
  // geri yükle. O kısa pencerede children'ı bloklayıp erken /login redirect'ini önler.
  const [initializing, setInitializing] = useState(() => {
    const tok = localStorage.getItem(TOKEN_KEY);
    return !!(tok && !isTokenValid(tok) && localStorage.getItem(REFRESH_TOKEN_KEY));
  });

  const claims = useMemo(() => extractAuthClaims(token), [token]);
  const isAuthenticated = isTokenValid(token);

  function persistTokens(accessToken, idTokenValue, refreshTokenValue) {
    if (accessToken) localStorage.setItem(TOKEN_KEY, accessToken);
    if (idTokenValue) localStorage.setItem(ID_TOKEN_KEY, idTokenValue);
    if (refreshTokenValue) localStorage.setItem(REFRESH_TOKEN_KEY, refreshTokenValue);
    setToken(accessToken ?? null);
    if (idTokenValue) setIdToken(idTokenValue);
  }

  function login(accessToken, idTokenValue, refreshTokenValue) {
    persistTokens(accessToken, idTokenValue, refreshTokenValue);
  }

  function clearLocalSession() {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(ID_TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
    setToken(null);
    setIdToken(null);
  }

  function logout() {
    const savedIdToken = localStorage.getItem(ID_TOKEN_KEY);
    clearLocalSession();
    logoutRedirect(savedIdToken);
  }

  // http.js reaktif yenileme (401 sonrası) yaptığında React state'ini senkronla.
  useEffect(() => {
    function syncFromStorage() {
      setToken(localStorage.getItem(TOKEN_KEY));
      setIdToken(localStorage.getItem(ID_TOKEN_KEY));
    }
    window.addEventListener('auth:tokens-updated', syncFromStorage);
    return () => window.removeEventListener('auth:tokens-updated', syncFromStorage);
  }, []);

  // Açılış rehydrate: süresi dolmuş access + geçerli refresh → sessizce yenile.
  useEffect(() => {
    if (!initializing) return undefined;
    const rt = localStorage.getItem(REFRESH_TOKEN_KEY);
    let cancelled = false;
    (async () => {
      try {
        const t = await refreshAccessToken(rt);
        if (!cancelled) persistTokens(t.access_token, t.id_token, t.refresh_token);
      } catch {
        if (!cancelled) clearLocalSession();
      } finally {
        if (!cancelled) setInitializing(false);
      }
    })();
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- yalnız açılışta bir kez
  }, [initializing]);

  // Proaktif yenileme: access token bitmeden ~60s önce sessizce yenile (token
  // her değişince yeniden zamanlanır). Refresh token rotasyona girerse yenisi kullanılır.
  useEffect(() => {
    if (!token) return undefined;
    const payload = parseJwtPayload(token);
    const rt = localStorage.getItem(REFRESH_TOKEN_KEY);
    if (!payload?.exp || !rt) return undefined;
    const delay = Math.max(payload.exp * 1000 - Date.now() - REFRESH_SKEW_MS, 0);
    const timerId = setTimeout(async () => {
      try {
        const t = await refreshAccessToken(localStorage.getItem(REFRESH_TOKEN_KEY));
        persistTokens(t.access_token, t.id_token, t.refresh_token);
      } catch {
        clearLocalSession();
      }
    }, delay);
    return () => clearTimeout(timerId);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- token değişince yeniden zamanla
  }, [token]);

  const value = {
    token,
    idToken,
    isAuthenticated,
    initializing,
    userId: claims.userId,
    username: claims.username,
    email: claims.email,
    emailVerified: claims.emailVerified,
    roles: claims.roles,
    isAdmin: claims.isAdmin,
    login,
    logout,
    clearLocalSession,
  };

  // Rehydrate penceresinde (sub-saniye) hiçbir şey render etme — erken redirect olmasın.
  if (initializing) return null;

  return (
    <AuthContext.Provider value={value}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
