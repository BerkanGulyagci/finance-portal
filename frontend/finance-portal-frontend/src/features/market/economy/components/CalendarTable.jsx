import { ChevronUp, ChevronDown, ChevronsUpDown } from 'lucide-react';
import { useTranslation } from '../../../../context/LanguageContext';
import { FX_META, FlagImg } from '../../fx/utils/fxMeta';
import { parseEventTime, isAllDay, fmtTime, fmtDayHeading, fmtNumber } from '../utils/calendarHelpers';

/** Coloured-dot importance indicator (3 dots, gray for inactive). */
function ImpactIcon({ impact }) {
  const level = impact?.toLowerCase();
  const count = level === 'high' ? 3 : level === 'medium' ? 2 : level === 'low' ? 1 : 0;
  const color = level === 'high' ? 'bg-rose-500'
              : level === 'medium' ? 'bg-amber-500'
              : level === 'low' ? 'bg-emerald-500'
              : 'bg-gray-300';
  if (level === 'holiday') return <span className="text-xs font-semibold text-gray-500">—</span>;
  return (
    <span className="inline-flex items-center gap-0.5" title={impact || ''}>
      {[1, 2, 3].map(i => (
        <span key={i} className={`w-2 h-2 rounded-full ${i <= count ? color : 'bg-gray-200'}`} />
      ))}
    </span>
  );
}

/**
 * Sıralanabilir sütun başlığı. Tıklayınca onSort(key) çağırır; aktif sütunda yön okunu,
 * pasif sütunda soluk çift-ok gösterir. align: 'left' | 'right'.
 */
function SortableTh({ label, sortable, sortKey, colKey, sortDir, onSort, align = 'left', className = '' }) {
  const isActive = sortable && sortKey === colKey;
  const justify = align === 'right' ? 'justify-end' : 'justify-start';
  const base = `px-3 py-2.5 text-[11px] font-bold text-gray-500 uppercase tracking-wider select-none ${align === 'right' ? 'text-right' : 'text-left'} ${className}`;
  if (!sortable) {
    return <th className={base}>{label}</th>;
  }
  return (
    <th className={`${base} cursor-pointer hover:text-gray-700 transition-colors`}
        onClick={() => onSort(colKey)}
        aria-sort={isActive ? (sortDir === 'asc' ? 'ascending' : 'descending') : 'none'}>
      <span className={`inline-flex items-center gap-1 ${justify}`}>
        {label}
        {isActive
          ? (sortDir === 'asc'
              ? <ChevronUp className="w-3.5 h-3.5 text-[#093eaa]" />
              : <ChevronDown className="w-3.5 h-3.5 text-[#093eaa]" />)
          : <ChevronsUpDown className="w-3.5 h-3.5 text-gray-300" />}
      </span>
    </th>
  );
}

/**
 * Renders the calendar table — sticky header, date group rows, event rows.
 * Receives the sliced/grouped page rows + sort state from the parent.
 *
 * <p>Sütun genişlikleri içeriği dengeler: Olay esnek (geniş metinleri taşır) ama
 * sayı sütunları (Açıklanan/Beklenti/Önceki) merkeze yakın tutulur, böylece çoğu satırda
 * boş kalan sağ taraf daralır. Bayraklar 22px (önceki 16px) — satır daha dolu görünür.
 */
export default function CalendarTable({ groupedRows, sortKey, sortDir, onSort }) {
  const { t, language } = useTranslation();

  return (
    <table className="w-full min-w-[760px]">
      <thead className="bg-gray-50 sticky top-0 z-10 border-b border-gray-200">
        <tr>
          <SortableTh label={t('Zaman')} sortable colKey="time" sortKey={sortKey} sortDir={sortDir}
                      onSort={onSort} className="w-24" />
          <SortableTh label={t('Döviz')} sortable colKey="currency" sortKey={sortKey} sortDir={sortDir}
                      onSort={onSort} className="w-28" />
          <SortableTh label={t('Önem')} sortable colKey="impact" sortKey={sortKey} sortDir={sortDir}
                      onSort={onSort} className="w-24" />
          <SortableTh label={t('Olay')} sortable={false} className="" />
          <SortableTh label={t('Açıklanan')} sortable={false} align="right" className="w-28" />
          <SortableTh label={t('Beklenti')} sortable={false} align="right" className="w-28" />
          <SortableTh label={t('Önceki')} sortable={false} align="right" className="w-28" />
        </tr>
      </thead>
      <tbody className="divide-y divide-gray-100">
        {groupedRows.map((row, idx) => {
          if (row.type === 'header') {
            return (
              <tr key={`h-${idx}`} className="bg-gray-100/80">
                <td colSpan={7} className="px-3 py-2 text-xs font-bold text-gray-700">
                  {row.date ? fmtDayHeading(row.date, language) : '—'}
                </td>
              </tr>
            );
          }
          const e = row.event;
          const d = parseEventTime(e.time);
          const allDay = isAllDay(e.time);
          const meta = e.currency ? FX_META[e.currency] : null;
          // Flag resolution: currency-mapped country (FX_META) → fallback to raw country code.
          // flag-icons accepts any ISO 3166-1 alpha-2 directly, so unmapped currencies still get a flag.
          const flagCc = meta?.cc ?? (e.country ? e.country.toLowerCase() : null);
          const isHoliday = e.impact === 'holiday' || (allDay && e.actual == null && e.estimate == null && e.prev == null);
          const actualHigher = e.actual != null && e.estimate != null && e.actual > e.estimate;
          const actualLower  = e.actual != null && e.estimate != null && e.actual < e.estimate;
          return (
            <tr key={`e-${idx}`} className={isHoliday ? 'bg-gray-50/50 text-gray-500' : 'hover:bg-gray-50'}>
              <td className="px-3 py-2.5 text-xs whitespace-nowrap text-[#093eaa] font-semibold">
                {allDay ? t('Tüm Gün') : (d ? fmtTime(d, language) : '–')}
              </td>
              <td className="px-3 py-2.5 text-xs whitespace-nowrap">
                <span className="inline-flex items-center gap-2">
                  <span className="inline-flex rounded-sm overflow-hidden ring-1 ring-gray-200 shrink-0">
                    <FlagImg cc={flagCc} size={22} />
                  </span>
                  <span className="font-semibold text-gray-700 text-[13px]">{e.currency || e.country || ''}</span>
                </span>
              </td>
              <td className="px-3 py-2.5 text-xs">
                {isHoliday
                  ? <span className="text-[11px] font-semibold text-gray-500">{t('Tatil')}</span>
                  : <ImpactIcon impact={e.impact} />}
              </td>
              <td className="px-3 py-2.5 text-[13px] text-gray-800">{e.event || '—'}</td>
              <td className={`px-3 py-2.5 text-[13px] text-right tabular-nums font-semibold whitespace-nowrap ${actualHigher ? 'text-emerald-600' : actualLower ? 'text-rose-600' : 'text-gray-800'}`}>
                {fmtNumber(e.actual, e.unit)}
              </td>
              <td className="px-3 py-2.5 text-[13px] text-right tabular-nums text-gray-600 whitespace-nowrap">
                {fmtNumber(e.estimate, e.unit)}
              </td>
              <td className="px-3 py-2.5 text-[13px] text-right tabular-nums text-gray-600 whitespace-nowrap">
                {fmtNumber(e.prev, e.unit)}
              </td>
            </tr>
          );
        })}
      </tbody>
    </table>
  );
}
