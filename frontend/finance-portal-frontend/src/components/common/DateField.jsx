import { useState, useRef, useEffect, useMemo, useCallback } from 'react';
import { createPortal } from 'react-dom';
import { Calendar, ChevronLeft, ChevronRight } from 'lucide-react';
import { useTranslation } from '../../context/LanguageContext';

const MONTHS_TR = [
  'Ocak', 'Şubat', 'Mart', 'Nisan', 'Mayıs', 'Haziran',
  'Temmuz', 'Ağustos', 'Eylül', 'Ekim', 'Kasım', 'Aralık',
];
const MONTHS_EN = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December',
];
const DOW_TR = ['Pt', 'Sa', 'Ça', 'Pe', 'Cu', 'Ct', 'Pz'];
const DOW_EN = ['Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa', 'Su'];

const POPOVER_W = 300;
const POPOVER_H = 340;

const pad = n => String(n).padStart(2, '0');
/** Date → "YYYY-MM-DD" */
const toIso = d => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
const dateOnly = d => new Date(d.getFullYear(), d.getMonth(), d.getDate());

function parseValue(v) {
  if (!v) return null;
  // Accept Date instance or "YYYY-MM-DD"
  if (v instanceof Date) return Number.isNaN(v.getTime()) ? null : v;
  const d = new Date(v.length === 10 ? `${v}T00:00` : v);
  return Number.isNaN(d.getTime()) ? null : d;
}

function parseBound(v) {
  if (!v) return null;
  if (v instanceof Date) return dateOnly(v);
  const d = new Date(v.length === 10 ? `${v}T00:00` : v);
  return Number.isNaN(d.getTime()) ? null : dateOnly(d);
}

/**
 * Material-3 stilinde tarih seçici (saat yok). Native <input type="date"> yerine
 * portal ile body'ye sabit konumlu açılan tek-tıkla takvim popover. Tarayıcı
 * farkı / tema farkı yaratmaz, dil değiştirilebilir.
 *
 * Props:
 *   value: Date | "YYYY-MM-DD" | null
 *   onChange: (newDate: Date) => void  — kullanıcı bir gün seçince Date verir
 *   min/max: aynı format kabul eder
 *   placeholder: trigger'da değer yoksa görünen metin
 *
 * value/onChange'in Date kullanması, EconomicCalendarPage gibi sayfaların
 * mevcut state şeması (Date) ile sıfır-friction çalışmasını sağlar.
 */
export default function DateField({ value, onChange, min, max, placeholder }) {
  const { t, language } = useTranslation();
  const isEn = language === 'en';
  const MONTHS = isEn ? MONTHS_EN : MONTHS_TR;
  const DOW = isEn ? DOW_EN : DOW_TR;

  const [open, setOpen] = useState(false);
  const [pos, setPos] = useState(null);
  const triggerRef = useRef(null);
  const popRef = useRef(null);

  const selected = useMemo(() => parseValue(value) ?? new Date(), [value]);
  const minDate = useMemo(() => parseBound(min), [min]);
  const maxDate = useMemo(() => parseBound(max), [max]);

  const [view, setView] = useState({ y: selected.getFullYear(), m: selected.getMonth() });
  const [mode, setMode] = useState('days');

  const computePos = useCallback(() => {
    const el = triggerRef.current;
    if (!el) return;
    const r = el.getBoundingClientRect();
    const left = Math.min(Math.max(r.left, 8), window.innerWidth - POPOVER_W - 8);
    const spaceBelow = window.innerHeight - r.bottom;
    const openUp = spaceBelow < POPOVER_H + 8 && r.top > spaceBelow;
    setPos(openUp
      ? { left, bottom: window.innerHeight - r.top + 6 }
      : { left, top: r.bottom + 6 });
  }, []);

  const openPicker = () => {
    setView({ y: selected.getFullYear(), m: selected.getMonth() });
    setMode('days');
    computePos();
    setOpen(true);
  };

  useEffect(() => {
    if (!open) return undefined;
    const onDown = e => {
      if (triggerRef.current?.contains(e.target)) return;
      if (popRef.current?.contains(e.target)) return;
      setOpen(false);
    };
    const onEsc = e => { if (e.key === 'Escape') setOpen(false); };
    const onReflow = () => computePos();
    document.addEventListener('mousedown', onDown);
    document.addEventListener('keydown', onEsc);
    window.addEventListener('resize', onReflow);
    window.addEventListener('scroll', onReflow, true);
    return () => {
      document.removeEventListener('mousedown', onDown);
      document.removeEventListener('keydown', onEsc);
      window.removeEventListener('resize', onReflow);
      window.removeEventListener('scroll', onReflow, true);
    };
  }, [open, computePos]);

  const grid = useMemo(() => {
    const first = new Date(view.y, view.m, 1);
    const lead = (first.getDay() + 6) % 7; // Monday-start
    const days = new Date(view.y, view.m + 1, 0).getDate();
    const cells = [];
    for (let i = 0; i < lead; i++) cells.push(null);
    for (let d = 1; d <= days; d++) cells.push(d);
    return cells;
  }, [view]);

  const isDisabled = d => {
    const day = new Date(view.y, view.m, d);
    if (minDate && day < minDate) return true;
    if (maxDate && day > maxDate) return true;
    return false;
  };

  const pickDay = d => {
    if (isDisabled(d)) return;
    const next = new Date(view.y, view.m, d);
    onChange(next);
    setOpen(false);
  };

  const goToday = () => {
    const now = new Date();
    if (minDate && dateOnly(now) < minDate) return;
    if (maxDate && dateOnly(now) > maxDate) return;
    onChange(dateOnly(now));
    setView({ y: now.getFullYear(), m: now.getMonth() });
    setOpen(false);
  };

  const minYear = minDate ? minDate.getFullYear() : null;
  const maxYear = maxDate ? maxDate.getFullYear() : null;
  const decadeStart = Math.floor(view.y / 12) * 12;

  const monthDisabled = m => {
    const start = new Date(view.y, m, 1);
    const end = new Date(view.y, m + 1, 0);
    if (minDate && end < minDate) return true;
    if (maxDate && start > maxDate) return true;
    return false;
  };
  const yearDisabled = y => (minYear != null && y < minYear) || (maxYear != null && y > maxYear);

  const handlePrev = () => {
    if (mode === 'days') setView(v => (v.m === 0 ? { y: v.y - 1, m: 11 } : { y: v.y, m: v.m - 1 }));
    else if (mode === 'months') setView(v => ({ ...v, y: v.y - 1 }));
    else setView(v => ({ ...v, y: v.y - 12 }));
  };
  const handleNext = () => {
    if (mode === 'days') setView(v => (v.m === 11 ? { y: v.y + 1, m: 0 } : { y: v.y, m: v.m + 1 }));
    else if (mode === 'months') setView(v => ({ ...v, y: v.y + 1 }));
    else setView(v => ({ ...v, y: v.y + 12 }));
  };
  const headerLabel = mode === 'days'
    ? `${MONTHS[view.m]} ${view.y}`
    : mode === 'months'
      ? `${view.y}`
      : `${decadeStart} - ${decadeStart + 11}`;
  const cycleMode = () => setMode(m => (m === 'days' ? 'months' : m === 'months' ? 'years' : 'days'));

  const display = parseValue(value);
  const displayText = display
    ? `${pad(display.getDate())}.${pad(display.getMonth() + 1)}.${display.getFullYear()}`
    : (placeholder || t('Tarih seçin'));
  const todayKey = (() => { const n = new Date(); return `${n.getFullYear()}-${n.getMonth()}-${n.getDate()}`; })();
  const selKey = `${selected.getFullYear()}-${selected.getMonth()}-${selected.getDate()}`;

  const popover = open && pos ? createPortal(
    <div
      ref={popRef}
      className="fixed z-[1000] rounded-2xl border border-[#e2e1eb] bg-white shadow-xl p-3"
      style={{ left: pos.left, top: pos.top, bottom: pos.bottom, width: POPOVER_W }}
    >
      <div className="flex items-center justify-between mb-2">
        <button type="button" onClick={handlePrev}
          className="p-1.5 rounded-lg hover:bg-[#f3f3fc] text-[#434653]">
          <ChevronLeft className="w-4 h-4" />
        </button>
        <button type="button" onClick={cycleMode}
          className="text-sm font-semibold text-[#1a1b22] px-3 py-1 rounded-lg hover:bg-[#f3f3fc]">
          {headerLabel}
        </button>
        <button type="button" onClick={handleNext}
          className="p-1.5 rounded-lg hover:bg-[#f3f3fc] text-[#434653]">
          <ChevronRight className="w-4 h-4" />
        </button>
      </div>

      {mode === 'days' && (
        <>
          <div className="grid grid-cols-7 gap-1 mb-1">
            {DOW.map(d => (
              <div key={d} className="text-center text-[11px] font-medium text-[#747684] py-1">{d}</div>
            ))}
          </div>
          <div className="grid grid-cols-7 gap-1">
            {grid.map((d, i) => {
              if (d == null) return <div key={`e${i}`} />;
              const key = `${view.y}-${view.m}-${d}`;
              const isSel = key === selKey;
              const isToday = key === todayKey;
              const disabled = isDisabled(d);
              return (
                <button
                  key={key}
                  type="button"
                  disabled={disabled}
                  onClick={() => pickDay(d)}
                  className={`h-8 rounded-lg text-sm transition-colors ${
                    disabled
                      ? 'text-[#c4c5d5] cursor-not-allowed'
                      : isSel
                        ? 'bg-[#093eaa] text-white font-semibold'
                        : isToday
                          ? 'text-[#093eaa] font-semibold hover:bg-[#f3f3fc]'
                          : 'text-[#1a1b22] hover:bg-[#f3f3fc]'
                  }`}
                >
                  {d}
                </button>
              );
            })}
          </div>
        </>
      )}

      {mode === 'months' && (
        <div className="grid grid-cols-3 gap-1">
          {MONTHS.map((mn, idx) => {
            const disabled = monthDisabled(idx);
            const isSel = idx === view.m && view.y === selected.getFullYear();
            return (
              <button
                key={mn}
                type="button"
                disabled={disabled}
                onClick={() => { setView(v => ({ ...v, m: idx })); setMode('days'); }}
                className={`h-10 rounded-lg text-sm transition-colors ${
                  disabled ? 'text-[#c4c5d5] cursor-not-allowed'
                    : isSel ? 'bg-[#093eaa] text-white font-semibold'
                      : 'text-[#1a1b22] hover:bg-[#f3f3fc]'
                }`}
              >
                {mn.slice(0, 3)}
              </button>
            );
          })}
        </div>
      )}

      {mode === 'years' && (
        <div className="grid grid-cols-3 gap-1">
          {Array.from({ length: 12 }, (_, i) => decadeStart + i).map(y => {
            const disabled = yearDisabled(y);
            const isSel = y === view.y;
            return (
              <button
                key={y}
                type="button"
                disabled={disabled}
                onClick={() => { setView(v => ({ ...v, y })); setMode('months'); }}
                className={`h-10 rounded-lg text-sm transition-colors ${
                  disabled ? 'text-[#c4c5d5] cursor-not-allowed'
                    : isSel ? 'bg-[#093eaa] text-white font-semibold'
                      : 'text-[#1a1b22] hover:bg-[#f3f3fc]'
                }`}
              >
                {y}
              </button>
            );
          })}
        </div>
      )}

      <div className="flex items-center justify-end gap-1 mt-3 pt-3 border-t border-[#eeedf7]">
        <button type="button" onClick={goToday}
          className="text-xs font-semibold text-[#093eaa] px-2 py-1 rounded-lg hover:bg-[#f3f3fc]">
          {t('Bugün')}
        </button>
        <button type="button" onClick={() => setOpen(false)}
          className="text-xs font-semibold text-white bg-[#093eaa] px-3 py-1.5 rounded-lg hover:bg-[#002a7d]">
          {t('Tamam')}
        </button>
      </div>
    </div>,
    document.body,
  ) : null;

  return (
    <div className="relative">
      <button
        ref={triggerRef}
        type="button"
        onClick={() => (open ? setOpen(false) : openPicker())}
        className="flex items-center justify-between gap-2 bg-[#f3f3fc] border border-[#e2e1eb] rounded-lg px-3 py-1.5 text-xs text-[#1a1b22] focus:outline-none focus:border-[#002a7d] focus:ring-1 focus:ring-[#002a7d] hover:border-[#c4c5d5] transition-colors min-w-[120px]"
      >
        <span className={display ? 'font-semibold' : 'text-[#747684]'}>{displayText}</span>
        <Calendar className="w-3.5 h-3.5 text-[#747684] shrink-0" />
      </button>
      {popover}
    </div>
  );
}
