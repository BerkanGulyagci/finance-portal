import { Filter, Clock } from 'lucide-react';
import { useTranslation } from '../../../../context/LanguageContext';
import DateField from '../../../../components/common/DateField';

/**
 * Top toolbar for the Economic Calendar:
 *  - Filters toggle (with active-count badge)
 *  - Quick-range tab strip (Yesterday / Today / Tomorrow / This Week)
 *  - M3 custom date pickers (no native <input type="date">)
 *  - Current time indicator
 */
export default function CalendarToolbar({
  quickRange, onQuickRange,
  fromDate, toDate, onDateChange,
  showFilters, onToggleFilters,
  activeFilterCount,
}) {
  const { t, language } = useTranslation();

  return (
    <div className="p-3 sm:p-4 border-b border-[#eeedf7] flex flex-col sm:flex-row sm:items-center gap-2 sm:gap-3 flex-wrap">
      <button
        type="button"
        onClick={onToggleFilters}
        className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-bold border transition-colors ${
          showFilters || activeFilterCount > 0
            ? 'bg-[#093eaa] text-white border-[#093eaa]'
            : 'bg-white text-[#434653] border-[#e2e1eb] hover:bg-[#f3f3fc]'
        }`}
      >
        <Filter className="w-3.5 h-3.5" /> {t('Filtreler')}
        {activeFilterCount > 0 && (
          <span className="bg-white/30 rounded-md px-1 min-w-[16px] text-center">{activeFilterCount}</span>
        )}
      </button>

      {/* Quick range — M3 segmented buttons (less round) */}
      <div className="inline-flex rounded-lg border border-[#e2e1eb] overflow-hidden bg-white">
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
              quickRange === r.key ? 'bg-[#093eaa] text-white' : 'text-[#434653] hover:bg-[#f3f3fc]'
            }`}
          >
            {r.label}
          </button>
        ))}
      </div>

      {/* M3 date pickers — custom popover, no browser-native chrome */}
      <div className="inline-flex items-center gap-1.5 ml-auto">
        <DateField
          value={fromDate}
          onChange={d => onDateChange('from', d)}
          max={toDate}
        />
        <span className="text-xs text-[#747684]">–</span>
        <DateField
          value={toDate}
          onChange={d => onDateChange('to', d)}
          min={fromDate}
        />
      </div>

      <div className="inline-flex items-center gap-1 text-xs text-[#747684] ml-auto sm:ml-0">
        <Clock className="w-3.5 h-3.5" />
        {t('Şuanki Zaman:')} {new Date().toLocaleTimeString(language === 'en' ? 'en-US' : 'tr-TR', { hour: '2-digit', minute: '2-digit' })}
      </div>
    </div>
  );
}
