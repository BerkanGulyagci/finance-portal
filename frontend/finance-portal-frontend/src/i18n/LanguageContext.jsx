import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import translations from './translations';
import { prefGet, prefSet } from '../api/prefs';

const STORAGE_KEY = 'app_language';
const DEFAULT_LANGUAGE = 'tr';
const SUPPORTED_LANGUAGES = ['tr', 'en'];

const LanguageContext = createContext(null);

function detectInitialLanguage() {
  if (typeof window === 'undefined') return DEFAULT_LANGUAGE;
  const saved = prefGet(STORAGE_KEY, null);
  if (saved && SUPPORTED_LANGUAGES.includes(saved)) return saved;
  return DEFAULT_LANGUAGE;
}

export function LanguageProvider({ children }) {
  const [language, setLanguageState] = useState(detectInitialLanguage);

  useEffect(() => {
    if (typeof document !== 'undefined') {
      document.documentElement.lang = language;
    }
  }, [language]);

  const setLanguage = useCallback((lang) => {
    if (!SUPPORTED_LANGUAGES.includes(lang)) return;
    prefSet(STORAGE_KEY, lang);
    setLanguageState(lang);
  }, []);

  const toggleLanguage = useCallback(() => {
    setLanguage(language === 'tr' ? 'en' : 'tr');
  }, [language, setLanguage]);

  const t = useCallback(
    (key, vars) => {
      if (key == null) return '';
      const str = String(key);
      let out = str;
      if (language !== 'tr') {
        const dict = translations[language];
        if (dict && Object.prototype.hasOwnProperty.call(dict, str)) {
          out = dict[str];
        }
      }
      if (vars && typeof vars === 'object') {
        out = out.replace(/\{(\w+)\}/g, (m, name) =>
          Object.prototype.hasOwnProperty.call(vars, name) ? String(vars[name]) : m
        );
      }
      return out;
    },
    [language]
  );

  const value = useMemo(
    () => ({ language, setLanguage, toggleLanguage, t, supported: SUPPORTED_LANGUAGES }),
    [language, setLanguage, toggleLanguage, t]
  );

  return <LanguageContext.Provider value={value}>{children}</LanguageContext.Provider>;
}

export function useTranslation() {
  const ctx = useContext(LanguageContext);
  if (!ctx) throw new Error('useTranslation must be used within LanguageProvider');
  return ctx;
}

export function useLanguage() {
  return useTranslation();
}
