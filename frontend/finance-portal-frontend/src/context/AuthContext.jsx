import { createContext, useContext, useState } from 'react';
import { logoutRedirect } from '../api/authApi';

const TOKEN_KEY = 'auth_token';
const ID_TOKEN_KEY = 'id_token';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [token, setToken] = useState(() => localStorage.getItem(TOKEN_KEY));
  const [idToken, setIdToken] = useState(() => localStorage.getItem(ID_TOKEN_KEY));

  const isAuthenticated = !!token;

  function login(accessToken, idTokenValue) {
    localStorage.setItem(TOKEN_KEY, accessToken);
    if (idTokenValue) localStorage.setItem(ID_TOKEN_KEY, idTokenValue);
    setToken(accessToken);
    setIdToken(idTokenValue ?? null);
  }

  function logout() {
    const savedIdToken = localStorage.getItem(ID_TOKEN_KEY);
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(ID_TOKEN_KEY);
    setToken(null);
    setIdToken(null);
    logoutRedirect(savedIdToken);
  }

  return (
    <AuthContext.Provider value={{ token, idToken, isAuthenticated, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
