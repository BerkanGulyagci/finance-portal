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
        <span key={i} className={`w-1.5 h-1.5 rounded-full ${i <= count ? color : 'bg-gray-200'}`} />
      ))}
    </span>
  );
}

/**
 * Renders the calendar table — sticky header, date group rows, event rows.
 * Receives the sliced/grouped page rows from the parent.
 */
export default function CalendarTable({ groupedRows }) {
  const { t, language } = useTranslation();

  return (
    <table className="w-full min-w-[700px]">
      <thead className="bg-gray-50 sticky top-0">
        <tr>
          <th className="text-left  px-3 py-2 text-[11px] font-bold text-gray-500 uppercase tracking-wider w-20">{t('Zaman')}</th>
          <th className="text-left  px-3 py-2 text-[11px] font-bold text-gray-500 uppercase tracking-wider w-24">{t('Döviz')}</th>
          <th className="text-left  px-3 py-2 text-[11px] font-bold text-gray-500 uppercase tracking-wider w-20">{t('Önem')}</th>
          <th className="text-left  px-3 py-2 text-[11px] font-bold text-gray-500 uppercase tracking-wider">{t('Olay')}</th>
          <th className="text-right px-3 py-2 text-[11px] font-bold text-gray-500 uppercase tracking-wider w-24">{t('Açıklanan')}</th>
          <th className="text-right px-3 py-2 text-[11px] font-bold text-gray-500 uppercase tracking-wider w-24">{t('Beklenti')}</th>
          <th className="text-right px-3 py-2 text-[11px] font-bold text-gray-500 uppercase tracking-wider w-24">{t('Önceki')}</th>
        </tr>
      </thead>
      <tbody className="divide-y divide-gray-100">
        {groupedRows.map((row, idx) => {
          if (row.type === 'header') {
            return (
              <tr key={`h-${idx}`} className="bg-gray-100/80">
                <td colSpan={7} className="px-3 py-1.5 text-xs font-bold text-gray-700">
                  {row.date ? fmtDayHeading(row.date, language) : '—'}
                </td>
              </tr>
            );
          }
          const e = row.event;
          const d = parseEventTime(e.time);
          const allDay = isAllDay(e.time);
          const meta = e.currency ? FX_META[e.currency] : null;
          const isHoliday = e.impact === 'holiday' || (allDay && e.actual == null && e.estimate == null && e.prev == null);
          const actualHigher = e.actual != null && e.estimate != null && e.actual > e.estimate;
          const actualLower  = e.actual != null && e.estimate != null && e.actual < e.estimate;
          return (
            <tr key={`e-${idx}`} className={isHoliday ? 'bg-gray-50/50 text-gray-500' : 'hover:bg-gray-50'}>
              <td className="px-3 py-2 text-xs whitespace-nowrap text-[#093eaa] font-semibold">
                {allDay ? t('Tüm Gün') : (d ? fmtTime(d, language) : '–')}
              </td>
              <td className="px-3 py-2 text-xs whitespace-nowrap">
                <span className="inline-flex items-center gap-1.5">
                  <span className="inline-flex rounded-sm overflow-hidden ring-1 ring-gray-200">
                    <FlagImg cc={meta?.cc} size={16} />
                  </span>
                  <span className="font-semibold text-gray-700">{e.currency || e.country || ''}</span>
                </span>
              </td>
              <td className="px-3 py-2 text-xs">
                {isHoliday
                  ? <span className="text-[11px] font-semibold text-gray-500">{t('Tatil')}</span>
                  : <ImpactIcon impact={e.impact} />}
              </td>
              <td className="px-3 py-2 text-xs text-gray-800">{e.event || '—'}</td>
              <td className={`px-3 py-2 text-xs text-right tabular-nums font-semibold whitespace-nowrap ${actualHigher ? 'text-emerald-600' : actualLower ? 'text-rose-600' : 'text-gray-800'}`}>
                {fmtNumber(e.actual, e.unit)}
              </td>
              <td className="px-3 py-2 text-xs text-right tabular-nums text-gray-600 whitespace-nowrap">
                {fmtNumber(e.estimate, e.unit)}
              </td>
              <td className="px-3 py-2 text-xs text-right tabular-nums text-gray-600 whitespace-nowrap">
                {fmtNumber(e.prev, e.unit)}
              </td>
            </tr>
          );
        })}
      </tbody>
    </table>
  );
}
