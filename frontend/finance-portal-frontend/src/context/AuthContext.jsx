import { createContext, useContext, useMemo, useState } from 'react';
import { logoutRedirect } from '../api/authApi';
import { extractAuthClaims, isTokenValid } from '../utils/jwtUtils';

const TOKEN_KEY = 'auth_token';
const ID_TOKEN_KEY = 'id_token';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [token, setToken] = useState(() => localStorage.getItem(TOKEN_KEY));
  const [idToken, setIdToken] = useState(() => localStorage.getItem(ID_TOKEN_KEY));

  const claims = useMemo(() => extractAuthClaims(token), [token]);

  const isAuthenticated = isTokenValid(token);

  function login(accessToken, idTokenValue) {
    localStorage.setItem(TOKEN_KEY, accessToken);
    if (idTokenValue) localStorage.setItem(ID_TOKEN_KEY, idTokenValue);
    setToken(accessToken);
    setIdToken(idTokenValue ?? null);
  }

  function clearLocalSession() {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(ID_TOKEN_KEY);
    setToken(null);
    setIdToken(null);
  }

  function logout() {
    const savedIdToken = localStorage.getItem(ID_TOKEN_KEY);
    clearLocalSession();
    logoutRedirect(savedIdToken);
  }

  const value = {
    token,
    idToken,
    isAuthenticated,
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
