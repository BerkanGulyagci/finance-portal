import { Sun, Moon } from 'lucide-react';
import { useTheme } from '../../context/ThemeContext';
import { useTranslation } from '../../context/LanguageContext';

/**
 * Segmentli açık/koyu mod geçişi (Güneş | Ay).
 *
 * <p>İki ikon her zaman yan yana görünür; geçerli tema vurgulu "knob" kazanır.
 * Stiller index.css'teki {@code .theme-toggle*} class'larından gelir (global
 * {@code .dark} override'larından etkilenmemesi için özel class'lar).</p>
 *
 * <p>{@code display} class'ta set EDİLMEZ; görünürlük {@code className} ile
 * (örn. "hidden sm:inline-flex") dışarıdan kontrol edilir.</p>
 */
export default function ThemeToggle({ className = '' }) {
  const { isDark, setTheme } = useTheme();
  const { t } = useTranslation();

  return (
    <div role="group" aria-label={t('Tema')} className={`theme-toggle ${className}`}>
      <button
        type="button"
        onClick={() => setTheme('light')}
        aria-pressed={!isDark}
        aria-label={t('Açık mod')}
        title={t('Açık mod')}
        className={`theme-toggle__btn${!isDark ? ' theme-toggle__btn--active' : ''}`}
      >
        <Sun size={15} strokeWidth={2.2} />
      </button>
      <button
        type="button"
        onClick={() => setTheme('dark')}
        aria-pressed={isDark}
        aria-label={t('Koyu mod')}
        title={t('Koyu mod')}
        className={`theme-toggle__btn${isDark ? ' theme-toggle__btn--active' : ''}`}
      >
        <Moon size={15} strokeWidth={2.2} />
      </button>
    </div>
  );
}
