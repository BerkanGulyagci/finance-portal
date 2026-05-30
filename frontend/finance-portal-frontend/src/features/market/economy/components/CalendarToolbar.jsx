import { Filter, Clock } from 'lucide-react';
import { useTranslation } from '../../../../context/LanguageContext';
import { isoDate } from '../utils/calendarHelpers';

/**
 * Top toolbar for the Economic Calendar:
 *  - Filters toggle (with active-count badge)
 *  - Quick-range tab strip (Yesterday / Today / Tomorrow / This Week)
 *  - Custom date range pickers
 *  - Current time indicator
 */
export default function CalendarToolbar({
  quickRange, onQuickRange,
  fromDate, toDate, onDateChange,
  showFilters, onToggleFilters,
  activeFilterCount,
}) {
  const { t, language } = useTranslation();

  function handleCustomDate(field, value) {
    if (!value) return;
    const d = new Date(value);
    if (isNaN(d.getTime())) return;
    onDateChange(field, d);
  }

  return (
    <div className="p-3 sm:p-4 border-b border-gray-100 flex flex-col sm:flex-row sm:items-center gap-2 sm:gap-3 flex-wrap">
      <button
        type="button"
        onClick={onToggleFilters}
        className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-bold border transition-colors ${
          showFilters || activeFilterCount > 0
            ? 'bg-[#093eaa] text-white border-[#093eaa]'
            : 'bg-white text-gray-700 border-gray-200 hover:bg-gray-50'
        }`}
      >
        <Filter className="w-3.5 h-3.5" /> {t('Filtreler')}
        {activeFilterCount > 0 && (
          <span className="bg-white/30 rounded-full px-1 min-w-[16px] text-center">{activeFilterCount}</span>
        )}
      </button>

      <div className="inline-flex rounded-lg border border-gray-200 overflow-hidden">
        {[
          { key: 'yesterday', label: t('Dün') },
          { key: 'today',     label: t('Bugün') },
          { key: 'tomorrow',  label: t('Yarın') },
          { key: 'week',      label: t('Bu Hafta') },
        ].map(r => (
          <button
            key={r.key}
            onClick={() => onQuickRange(r.key)}
            className={`px-3 py-1.5 text-xs font-bold transition-colors ${
              quickRange === r.key ? 'bg-[#093eaa] text-white' : 'bg-white text-gray-700 hover:bg-gray-50'
            }`}
          >
            {r.label}
          </button>
        ))}
      </div>

      <div className="inline-flex items-center gap-1.5 ml-auto">
        <input
          type="date"
          value={isoDate(fromDate)}
          onChange={e => handleCustomDate('from', e.target.value)}
          className="px-2.5 py-1.5 bg-white border border-gray-200 rounded-lg text-xs text-gray-700"
        />
        <span className="text-xs text-gray-400">–</span>
        <input
          type="date"
          value={isoDate(toDate)}
          onChange={e => handleCustomDate('to', e.target.value)}
          className="px-2.5 py-1.5 bg-white border border-gray-200 rounded-lg text-xs text-gray-700"
        />
      </div>

      <div className="inline-flex items-center gap-1 text-xs text-gray-500 ml-auto sm:ml-0">
        <Clock className="w-3.5 h-3.5" />
        {t('Şuanki Zaman:')} {new Date().toLocaleTimeString(language === 'en' ? 'en-US' : 'tr-TR', { hour: '2-digit', minute: '2-digit' })}
      </div>
    </div>
  );
}
