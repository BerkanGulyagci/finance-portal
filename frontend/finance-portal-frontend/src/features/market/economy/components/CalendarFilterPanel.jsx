import { useTranslation } from '../../../../context/LanguageContext';
import { FX_META, FlagImg } from '../../fx/utils/fxMeta';

/**
 * Collapsible filter panel — Impact / Currency / Hide-holidays toggles.
 * Pure controlled component: receives sets + setters from parent.
 */
export default function CalendarFilterPanel({
  impactFilter, onToggleImpact,
  availableCurrencies, currencyFilter, onToggleCurrency,
  hideHolidays, onHideHolidaysChange,
  onClearAll,
}) {
  const { t } = useTranslation();
  const anyActive = impactFilter.size > 0 || currencyFilter.size > 0 || hideHolidays;

  return (
    <div className="p-3 sm:p-4 border-b border-[#eeedf7] bg-[#f3f3fc]/40 space-y-3">
      <div>
        <p className="text-[11px] font-bold text-[#747684] uppercase tracking-wider mb-1.5">{t('Önem')}</p>
        <div className="flex flex-wrap gap-1.5">
          {['high', 'medium', 'low'].map(lvl => {
            const active = impactFilter.has(lvl);
            const color = lvl === 'high' ? 'bg-rose-500' : lvl === 'medium' ? 'bg-amber-500' : 'bg-emerald-500';
            const label = lvl === 'high' ? t('Yüksek') : lvl === 'medium' ? t('Orta') : t('Düşük');
            return (
              <button
                key={lvl}
                onClick={() => onToggleImpact(lvl)}
                className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md text-xs font-bold border transition-colors ${
                  active ? 'bg-[#093eaa] text-white border-[#093eaa]' : 'bg-white text-[#434653] border-[#e2e1eb] hover:bg-[#f3f3fc]'
                }`}
              >
                <span className={`w-1.5 h-1.5 rounded-full ${color}`} />
                {label}
              </button>
            );
          })}
        </div>
      </div>

      {availableCurrencies.length > 0 && (
        <div>
          <p className="text-[11px] font-bold text-[#747684] uppercase tracking-wider mb-1.5">{t('Döviz')}</p>
          <div className="flex flex-wrap gap-1.5 max-h-32 overflow-y-auto">
            {availableCurrencies.map(cur => {
              const active = currencyFilter.has(cur);
              const meta = FX_META[cur];
              return (
                <button
                  key={cur}
                  onClick={() => onToggleCurrency(cur)}
                  className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-md text-xs font-bold border transition-colors ${
                    active ? 'bg-[#093eaa] text-white border-[#093eaa]' : 'bg-white text-[#434653] border-[#e2e1eb] hover:bg-[#f3f3fc]'
                  }`}
                >
                  <FlagImg cc={meta?.cc} size={14} />
                  {cur}
                </button>
              );
            })}
          </div>
        </div>
      )}

      <label className="inline-flex items-center gap-2 text-xs font-semibold text-[#434653] cursor-pointer">
        <input
          type="checkbox"
          checked={hideHolidays}
          onChange={e => onHideHolidaysChange(e.target.checked)}
          className="rounded text-[#093eaa] focus:ring-[#093eaa]"
        />
        {t('Tatilleri gizle')}
      </label>

      {anyActive && (
        <button
          onClick={onClearAll}
          className="text-xs font-semibold text-[#093eaa] hover:underline"
        >
          {t('Tümünü temizle')}
        </button>
      )}
    </div>
  );
}
