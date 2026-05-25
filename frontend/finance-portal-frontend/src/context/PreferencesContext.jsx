import { createContext, useContext, useEffect, useState } from 'react';
import { useAuth } from './AuthContext';
import { useTranslation } from '../i18n/LanguageContext';
import { hydratePrefs, prefGet } from '../api/prefs';

const PreferencesContext = createContext({ ready: true });

/**
 * Giriş yapan kullanıcının sunucudaki arayüz tercihlerini (dashboard düzeni, dil, ticker, grafik düzenleri…)
 * uygulama render edilmeden ÖNCE çeker (kısa yükleme kapısı) — böylece bileşenler mount'ta senkron değerleri okur.
 */
export function PreferencesProvider({ children }) {
  const { isAuthenticated } = useAuth();
  const { language, setLanguage } = useTranslation();
  const [ready, setReady] = useState(!isAuthenticated);

  useEffect(() => {
    let cancelled = false;
    if (!isAuthenticated) {
      setReady(true);
      return undefined;
    }
    setReady(false);
    hydratePrefs().finally(() => {
      if (cancelled) return;
      // Dil tercihini canlı state ile eşitle (LanguageContext localStorage'ı hydrate'ten önce okumuş olabilir).
      const saved = prefGet('app_language', null);
      if ((saved === 'tr' || saved === 'en') && saved !== language) {
        setLanguage(saved);
      }
      setReady(true);
    });
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isAuthenticated]);

  if (isAuthenticated && !ready) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-50">
        <div className="flex flex-col items-center gap-3">
          <div className="w-8 h-8 border-2 border-[#093eaa]/30 border-t-[#093eaa] rounded-full animate-spin" />
          <p className="text-sm text-gray-500">Tercihleriniz yükleniyor…</p>
        </div>
      </div>
    );
  }

  return <PreferencesContext.Provider value={{ ready }}>{children}</PreferencesContext.Provider>;
}

export function usePreferences() {
  return useContext(PreferencesContext);
}
