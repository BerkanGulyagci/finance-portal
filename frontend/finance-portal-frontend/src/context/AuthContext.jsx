import { createContext, useContext, useState } from 'react';

const TOKEN_KEY = 'auth_token';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [token, setToken] = useState(() => localStorage.getItem(TOKEN_KEY));

  const isAuthenticated = !!token;

  function login(tokenValue) {
    localStorage.setItem(TOKEN_KEY, tokenValue);
    setToken(tokenValue);
  }

  function logout() {
    localStorage.removeItem(TOKEN_KEY);
    setToken(null);
  }

  return (
    <AuthContext.Provider value={{ token, isAuthenticated, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
