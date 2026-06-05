import { useEffect, useMemo, useState } from 'react';
import { getEconomicCalendar } from '../../../api/marketApi.js';
import { useTranslation } from '../../../context/LanguageContext';
import Pagination from '../../../components/common/Pagination';
import CalendarToolbar from './components/CalendarToolbar';
import CalendarFilterPanel from './components/CalendarFilterPanel';
import CalendarTable from './components/CalendarTable';
import { SkeletonTable } from '../../../components/common/Skeleton';
import {
  isoDate, startOfWeek, endOfWeek,
  parseEventTime, isAllDay, dateKey,
} from './utils/calendarHelpers';

const PAGE_SIZE = 50;

/**
 * Economic Calendar page — global macro releases from Finnhub.
 * Owns state + data fetching; UI composed from sub-components.
 */
export default function EconomicCalendarPage() {
  const { t } = useTranslation();

  // Default range: today ± 30 days. Finnhub free tier returns ~20 days of future
  // events at most (verified empirically), so widening the future window doesn't
  // help. Past window kept short for fast initial load (cached 1h on backend).
  // Users can widen the range via the custom date pickers; backend chunks
  // requests >30 days into 30-day windows transparently.
  const today = useMemo(() => { const d = new Date(); d.setHours(0, 0, 0, 0); return d; }, []);
  const thirtyDaysPast  = useMemo(() => { const d = new Date(today); d.setDate(d.getDate() - 30); return d; }, [today]);
  const thirtyDaysAhead = useMemo(() => { const d = new Date(today); d.setDate(d.getDate() + 30); return d; }, [today]);

  const [fromDate, setFromDate] = useState(thirtyDaysPast);
  const [toDate,   setToDate]   = useState(thirtyDaysAhead);
  const [events,   setEvents]   = useState([]);
  const [loading,  setLoading]  = useState(true);
  const [error,    setError]    = useState('');

  const [showFilters,    setShowFilters]    = useState(false);
  const [impactFilter,   setImpactFilter]   = useState(new Set());
  const [currencyFilter, setCurrencyFilter] = useState(new Set());
  const [hideHolidays,   setHideHolidays]   = useState(false);

  const [quickRange, setQuickRange] = useState('week'); // 'yesterday'|'today'|'tomorrow'|'week'|'custom'
  const [page, setPage] = useState(0);

  // ── Data fetching ──────────────────────────────────────────────────────────
  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError('');
    getEconomicCalendar(isoDate(fromDate), isoDate(toDate))
      .then(list => {
        if (cancelled) return;
        setEvents(Array.isArray(list) ? list : []);
        setLoading(false);
      })
      .catch(e => {
        if (cancelled) return;
        setError(e?.response?.data?.message || t('Veriler yüklenemedi.'));
        setLoading(false);
      });
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [fromDate, toDate]);

  // ── Derived state ──────────────────────────────────────────────────────────
  const quickWindow = useMemo(() => {
    const now = new Date(); now.setHours(0, 0, 0, 0);
    if (quickRange === 'yesterday') { const y = new Date(now); y.setDate(y.getDate() - 1); return { start: y, end: y }; }
    if (quickRange === 'today')     return { start: now, end: now };
    if (quickRange === 'tomorrow')  { const tm = new Date(now); tm.setDate(tm.getDate() + 1); return { start: tm, end: tm }; }
    if (quickRange === 'week')      return { start: startOfWeek(now), end: endOfWeek(now) };
    return null;
  }, [quickRange]);

  const availableCurrencies = useMemo(() => {
    const set = new Set();
    events.forEach(e => { if (e.currency) set.add(e.currency); });
    return [...set].sort((a, b) => a.localeCompare(b, 'tr'));
  }, [events]);

  const filtered = useMemo(() => events.filter(e => {
    const d = parseEventTime(e.time);
    if (!d) return false;
    if (quickWindow) {
      const dk = dateKey(d), sk = dateKey(quickWindow.start), ek = dateKey(quickWindow.end);
      if (dk < sk || dk > ek) return false;
    }
    if (hideHolidays && (e.impact === 'holiday' || (isAllDay(e.time) && e.actual == null && e.estimate == null && e.prev == null))) return false;
    if (impactFilter.size > 0 && !impactFilter.has(e.impact?.toLowerCase())) return false;
    if (currencyFilter.size > 0 && (!e.currency || !currencyFilter.has(e.currency))) return false;
    return true;
  }), [events, quickWindow, impactFilter, currencyFilter, hideHolidays]);

  const sorted = useMemo(() => [...filtered].sort((a, b) => {
    const da = parseEventTime(a.time)?.getTime() ?? 0;
    const db = parseEventTime(b.time)?.getTime() ?? 0;
    return da - db;
  }), [filtered]);

  const totalPages = Math.max(1, Math.ceil(sorted.length / PAGE_SIZE));
  const pageRows   = sorted.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE);

  // Group page rows by date — single pass
  const groupedRows = useMemo(() => {
    const groups = [];
    let currentKey = null;
    pageRows.forEach(e => {
      const d = parseEventTime(e.time);
      const dk = d ? dateKey(d) : '0';
      if (dk !== currentKey) { currentKey = dk; groups.push({ type: 'header', date: d }); }
      groups.push({ type: 'event', event: e });
    });
    return groups;
  }, [pageRows]);

  // Reset page on filter/range changes
  useEffect(() => { setPage(0); }, [quickRange, impactFilter, currencyFilter, hideHolidays, fromDate, toDate]);

  // ── Handlers ───────────────────────────────────────────────────────────────
  function toggleSet(setter) {
    return (val) => setter(prev => {
      const next = new Set(prev);
      if (next.has(val)) next.delete(val); else next.add(val);
      return next;
    });
  }
  const toggleImpact   = toggleSet(setImpactFilter);
  const toggleCurrency = toggleSet(setCurrencyFilter);

  function handleDateChange(field, d) {
    setQuickRange('custom');
    if (field === 'from') setFromDate(d); else setToDate(d);
  }

  function clearAllFilters() {
    setImpactFilter(new Set());
    setCurrencyFilter(new Set());
    setHideHolidays(false);
  }

  const activeFilterCount = impactFilter.size + currencyFilter.size + (hideHolidays ? 1 : 0);

  return (
    <div>
      <h1 className="text-xl sm:text-2xl font-bold text-gray-900 mb-2 border-l-4 border-[#093eaa] pl-4">
        {t('Ekonomik Takvim')}
      </h1>
      <p className="text-sm text-gray-500 mb-6 pl-5">
        {t('Küresel makro veri açıklamaları — enflasyon, faiz kararları, istihdam, GSYİH ve daha fazlası.')}
      </p>

      <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden">
        <CalendarToolbar
          quickRange={quickRange}
          onQuickRange={setQuickRange}
          fromDate={fromDate}
          toDate={toDate}
          onDateChange={handleDateChange}
          showFilters={showFilters}
          onToggleFilters={() => setShowFilters(o => !o)}
          activeFilterCount={activeFilterCount}
        />

        {showFilters && (
          <CalendarFilterPanel
            impactFilter={impactFilter}
            onToggleImpact={toggleImpact}
            availableCurrencies={availableCurrencies}
            currencyFilter={currencyFilter}
            onToggleCurrency={toggleCurrency}
            hideHolidays={hideHolidays}
            onHideHolidaysChange={setHideHolidays}
            onClearAll={clearAllFilters}
          />
        )}

        <div className="overflow-x-auto">
          {loading ? (
            <SkeletonTable rows={12} cols={6} />
          ) : error ? (
            <div className="p-6 text-rose-500 text-sm">{error}</div>
          ) : sorted.length === 0 ? (
            <div className="p-8 text-center text-gray-400 text-sm">{t('Bu kriterler için sonuç yok.')}</div>
          ) : (
            <CalendarTable groupedRows={groupedRows} />
          )}
        </div>

        {!loading && !error && sorted.length > 0 && (
          <Pagination
            page={page}
            totalPages={totalPages}
            totalElements={sorted.length}
            unitLabel={t('olay')}
            onChange={(p) => {
              setPage(p);
              if (typeof window !== 'undefined') window.scrollTo(0, 0);
            }}
          />
        )}
      </div>
    </div>
  );
}
