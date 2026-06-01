import { createContext, useContext, useEffect, useState, useCallback } from 'react';
import { prefGet, prefSet } from '../api/prefs';

/**
 * Açık/Koyu mod (tema) yönetimi.
 *
 * <p>Tema {@code <html>} elementine {@code dark} class'ı ekley/çıkararak uygulanır;
 * index.css'teki {@code .dark} kuralları (CSS değişkenleri + m3-* class'ları + sık
 * kullanılan hardcoded palet override'ları) tüm uygulamayı buna göre boyar.</p>
 *
 * <p>Tercih {@code app_theme} anahtarıyla localStorage'a (anında, flash'sız) ve
 * giriş yapılmışsa sunucuya ({@code prefSet}) yazılır → cihazlar arası senkron.</p>
 */
const ThemeContext = createContext(null);

export const THEME_KEY = 'app_theme';

/** localStorage'tan tema oku (flash önleme için senkron). Geçersizse 'light'. */
export function readTheme() {
  const t = prefGet(THEME_KEY, 'light');
  return t === 'dark' ? 'dark' : 'light';
}

/** <html> üzerinde dark class'ını uygula. main.jsx erken çağırır (FOUC önleme). */
export function applyTheme(theme) {
  const root = document.documentElement;
  if (theme === 'dark') {
    root.classList.add('dark');
    root.style.colorScheme = 'dark';
  } else {
    root.classList.remove('dark');
    root.style.colorScheme = 'light';
  }
  // Keycloak senkronu: app (:5173) ve Keycloak (:8081) aynı host'ta (localhost);
  // cookie port'a değil host'a bağlıdır → Keycloak teması bu "ui_theme" cookie'sini
  // okuyup giriş/kayıt ekranlarını da koyu yapar. (Farklı origin olduğu için
  // localStorage paylaşılamaz; cookie paylaşılır.)
  try {
    document.cookie = `ui_theme=${theme}; path=/; max-age=31536000; SameSite=Lax`;
  } catch { /* yoksay */ }
}

export function ThemeProvider({ children }) {
  const [theme, setThemeState] = useState(readTheme);

  // Tema değişince DOM'a uygula + kalıcı yaz (localStorage + sunucu senkron).
  useEffect(() => {
    applyTheme(theme);
    prefSet(THEME_KEY, theme);
  }, [theme]);

  // Aynı tarayıcıda başka sekme tema değiştirirse senkronize ol.
  useEffect(() => {
    const onStorage = (e) => {
      if (e.key === THEME_KEY) setThemeState(readTheme());
    };
    window.addEventListener('storage', onStorage);
    return () => window.removeEventListener('storage', onStorage);
  }, []);

  const setTheme = useCallback((t) => setThemeState(t === 'dark' ? 'dark' : 'light'), []);
  const toggleTheme = useCallback(() => setThemeState((p) => (p === 'dark' ? 'light' : 'dark')), []);

  return (
    <ThemeContext.Provider value={{ theme, isDark: theme === 'dark', setTheme, toggleTheme }}>
      {children}
    </ThemeContext.Provider>
  );
}

export function useTheme() {
  const ctx = useContext(ThemeContext);
  if (!ctx) throw new Error('useTheme must be used within ThemeProvider');
  return ctx;
}
