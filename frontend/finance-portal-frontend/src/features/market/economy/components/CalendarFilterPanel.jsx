import { useState, useMemo } from 'react';
import { Check, Search, X } from 'lucide-react';
import { useTranslation } from '../../../../context/LanguageContext';
import { FX_META, FlagImg } from '../../fx/utils/fxMeta';

/**
 * Material 3 filter panel: importance row + currency search/chips + holiday toggle.
 * Each section uses its own colour palette so the screen is not a wall of navy.
 *  - Importance chips fill with the severity colour when active (rose / amber / emerald)
 *  - Currency chips use a neutral slate when active
 *  - Hide-holidays is a proper M3 toggle switch
 */
export default function CalendarFilterPanel({
  impactFilter, onToggleImpact,
  availableCurrencies, currencyFilter, onToggleCurrency,
  hideHolidays, onHideHolidaysChange,
  onClearAll,
}) {
  const { t } = useTranslation();
  const [search, setSearch] = useState('');

  const filteredCurrencies = useMemo(() => {
    if (!search.trim()) return availableCurrencies;
    const q = search.trim().toUpperCase();
    return availableCurrencies.filter(c => c.includes(q) || (FX_META[c]?.ad?.toUpperCase() ?? '').includes(q));
  }, [search, availableCurrencies]);

  const anyActive = impactFilter.size > 0 || currencyFilter.size > 0 || hideHolidays;

  // Importance chip styling per severity
  function impactChipClass(lvl, active) {
    if (!active) {
      return 'bg-white text-[#434653] border-[#e2e1eb] hover:border-[#c4c5d5]';
    }
    if (lvl === 'high')   return 'bg-rose-500 text-white border-rose-500';
    if (lvl === 'medium') return 'bg-amber-500 text-white border-amber-500';
    return 'bg-emerald-500 text-white border-emerald-500';
  }

  return (
    <div className="p-3 sm:p-4 border-b border-[#eeedf7] bg-[#f8f8fc] space-y-4">
      {/* Row 1 — Importance + Hide holidays toggle on the same row */}
      <div className="flex flex-col sm:flex-row sm:items-center gap-3 sm:gap-6">
        <div className="flex items-center gap-2 flex-wrap">
          <span className="text-[11px] font-bold text-[#747684] uppercase tracking-wider shrink-0">{t('Önem')}</span>
          {['high', 'medium', 'low'].map(lvl => {
            const active = impactFilter.has(lvl);
            const label = lvl === 'high' ? t('Yüksek') : lvl === 'medium' ? t('Orta') : t('Düşük');
            return (
              <button
                key={lvl}
                type="button"
                onClick={() => onToggleImpact(lvl)}
                className={`inline-flex items-center gap-1.5 px-2.5 h-7 rounded-md text-xs font-semibold border transition-colors ${impactChipClass(lvl, active)}`}
              >
                {active && <Check className="w-3 h-3" />}
                {label}
              </button>
            );
          })}
        </div>

        {/* M3 toggle switch */}
        <label className="inline-flex items-center gap-2 cursor-pointer select-none">
          <span className="text-xs font-semibold text-[#434653]">{t('Tatilleri gizle')}</span>
          <button
            type="button"
            role="switch"
            aria-checked={hideHolidays}
            onClick={() => onHideHolidaysChange(!hideHolidays)}
            className={`relative inline-flex w-9 h-5 rounded-full transition-colors ${
              hideHolidays ? 'bg-[#093eaa]' : 'bg-[#d1d5db]'
            }`}
          >
            <span
              className={`absolute top-0.5 inline-block w-4 h-4 bg-white rounded-full shadow transition-transform ${
                hideHolidays ? 'translate-x-4' : 'translate-x-0.5'
              }`}
            />
          </button>
        </label>

        {anyActive && (
          <button
            type="button"
            onClick={onClearAll}
            className="ml-auto inline-flex items-center gap-1 text-xs font-semibold text-[#5a6472] hover:text-[#093eaa]"
          >
            <X className="w-3.5 h-3.5" /> {t('Tümünü temizle')}
          </button>
        )}
      </div>

      {/* Row 2 — Currency section: search + chips */}
      {availableCurrencies.length > 0 && (
        <div>
          <div className="flex items-center gap-2 mb-2">
            <span className="text-[11px] font-bold text-[#747684] uppercase tracking-wider shrink-0">
              {t('Döviz')}
              {currencyFilter.size > 0 && (
                <span className="ml-1.5 text-[10px] font-bold text-[#093eaa] normal-case">
                  ({currencyFilter.size} {t('seçili')})
                </span>
              )}
            </span>
            <div className="relative flex-1 max-w-xs">
              <Search className="absolute left-2 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-[#9aa0a6] pointer-events-none" />
              <input
                type="text"
                value={search}
                onChange={e => setSearch(e.target.value)}
                placeholder={t('Döviz ara...')}
                className="w-full pl-7 pr-2 h-7 bg-white border border-[#e2e1eb] rounded-md text-xs text-[#1a1b22] placeholder-[#9aa0a6] focus:outline-none focus:border-[#093eaa]"
              />
            </div>
          </div>
          <div className="flex flex-wrap gap-1.5 max-h-28 overflow-y-auto">
            {filteredCurrencies.map(cur => {
              const active = currencyFilter.has(cur);
              const meta = FX_META[cur];
              return (
                <button
                  key={cur}
                  type="button"
                  onClick={() => onToggleCurrency(cur)}
                  className={`inline-flex items-center gap-1 px-2 h-7 rounded-md text-xs font-semibold border transition-colors ${
                    active
                      ? 'bg-[#1f2937] text-white border-[#1f2937]'
                      : 'bg-white text-[#434653] border-[#e2e1eb] hover:border-[#c4c5d5]'
                  }`}
                >
                  {active && <Check className="w-3 h-3" />}
                  <FlagImg cc={meta?.cc} size={14} />
                  {cur}
                </button>
              );
            })}
            {filteredCurrencies.length === 0 && (
              <span className="text-xs text-[#9aa0a6]">{t('Sonuç yok')}</span>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
