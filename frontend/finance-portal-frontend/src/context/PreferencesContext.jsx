import { createContext, useContext, useEffect, useState } from 'react';
import { useAuth } from './AuthContext';
import { useTranslation } from '../context/LanguageContext';
import { hydratePrefs, prefGet } from '../api/prefs';

const MARGIN_ALERT_KEY = 'margin_alert_threshold_pct';
const MARGIN_ALERT_DEFAULT = 50;

function readMarginAlert() {
  const raw = prefGet(MARGIN_ALERT_KEY, MARGIN_ALERT_DEFAULT);
  const n = Number(raw);
  if (!Number.isFinite(n)) return MARGIN_ALERT_DEFAULT;
  const clamped = Math.max(0, Math.min(95, Math.round(n)));
  return clamped;
}

const PreferencesContext = createContext({ ready: true, marginAlertThresholdPct: MARGIN_ALERT_DEFAULT });

/**
 * Giriş yapan kullanıcının sunucudaki arayüz tercihlerini (dashboard düzeni, dil, ticker, grafik düzenleri…)
 * uygulama render edilmeden ÖNCE çeker (kısa yükleme kapısı) — böylece bileşenler mount'ta senkron değerleri okur.
 */
export function PreferencesProvider({ children }) {
  const { isAuthenticated } = useAuth();
  const { language, setLanguage } = useTranslation();
  const [ready, setReady] = useState(!isAuthenticated);
  const [marginAlertThresholdPct, setMarginAlertThresholdPct] = useState(readMarginAlert);

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
      setMarginAlertThresholdPct(readMarginAlert());
      setReady(true);
    });
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isAuthenticated]);

  // Aynı tarayıcıda MarginAlertSettings prefSet ile yazınca cross-tab storage event ile yenile.
  useEffect(() => {
    const onStorage = (e) => {
      if (e.key === MARGIN_ALERT_KEY) setMarginAlertThresholdPct(readMarginAlert());
    };
    window.addEventListener('storage', onStorage);
    return () => window.removeEventListener('storage', onStorage);
  }, []);

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

  return (
    <PreferencesContext.Provider value={{ ready, marginAlertThresholdPct }}>
      {children}
    </PreferencesContext.Provider>
  );
}

export function usePreferences() {
  return useContext(PreferencesContext);
}
