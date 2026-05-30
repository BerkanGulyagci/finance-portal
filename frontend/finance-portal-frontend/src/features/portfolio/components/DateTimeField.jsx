import { useState, useRef, useEffect, useMemo, useCallback } from 'react';
import { createPortal } from 'react-dom';
import { Calendar, ChevronLeft, ChevronRight, Clock } from 'lucide-react';
import { useTranslation } from '../../../context/LanguageContext';

const MONTHS_TR = [
  'Ocak', 'Şubat', 'Mart', 'Nisan', 'Mayıs', 'Haziran',
  'Temmuz', 'Ağustos', 'Eylül', 'Ekim', 'Kasım', 'Aralık',
];
const DOW_TR = ['Pt', 'Sa', 'Ça', 'Pe', 'Cu', 'Ct', 'Pz'];

const POPOVER_W = 300;
const POPOVER_H = 360;

const pad = n => String(n).padStart(2, '0');
const toValue = d => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
const dateOnly = d => new Date(d.getFullYear(), d.getMonth(), d.getDate());

function parseValue(v) {
  if (!v) return null;
  const d = new Date(v);
  return Number.isNaN(d.getTime()) ? null : d;
}

/** "YYYY-MM-DD" veya "YYYY-MM-DDTHH:mm" → gün başlangıcı Date */
function parseBound(v) {
  if (!v) return null;
  const d = new Date(v.length === 10 ? `${v}T00:00` : v);
  return Number.isNaN(d.getTime()) ? null : dateOnly(d);
}

/**
 * Özel tarih + saat seçici — native datetime-local yerine temiz M3 takvim.
 * Takvim, modal taşmasından etkilenmemek için portal ile body'ye fixed konumla açılır
 * (altta yer yoksa yukarı doğru). value/onChange formatı "YYYY-MM-DDTHH:mm".
 */
export default function DateTimeField({ value, onChange, min, max }) {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);
  const [pos, setPos] = useState(null); // { left, top?, bottom? }
  const triggerRef = useRef(null);
  const popRef = useRef(null);

  const selected = useMemo(() => parseValue(value) ?? new Date(), [value]);
  const minDate = useMemo(() => parseBound(min), [min]);
  const maxDate = useMemo(() => parseBound(max), [max]);

  const [view, setView] = useState({ y: selected.getFullYear(), m: selected.getMonth() });
  const [mode, setMode] = useState('days'); // 'days' | 'months' | 'years'

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
    const lead = (first.getDay() + 6) % 7; // Pazartesi başlangıç
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
    const next = new Date(view.y, view.m, d, selected.getHours(), selected.getMinutes());
    onChange(toValue(next));
  };

  const setTime = e => {
    const [h, mn] = (e.target.value || '00:00').split(':').map(Number);
    const next = new Date(selected.getFullYear(), selected.getMonth(), selected.getDate(),
      Number.isFinite(h) ? h : 0, Number.isFinite(mn) ? mn : 0);
    onChange(toValue(next));
  };

  const goToday = () => {
    const now = new Date();
    if (minDate && dateOnly(now) < minDate) return;
    onChange(toValue(now));
    setView({ y: now.getFullYear(), m: now.getMonth() });
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
    ? `${MONTHS_TR[view.m]} ${view.y}`
    : mode === 'months'
      ? `${view.y}`
      : `${decadeStart} - ${decadeStart + 11}`;
  const cycleMode = () => setMode(m => (m === 'days' ? 'months' : m === 'months' ? 'years' : 'days'));

  const display = parseValue(value);
  const displayText = display
    ? `${pad(display.getDate())}.${pad(display.getMonth() + 1)}.${display.getFullYear()} · ${pad(display.getHours())}:${pad(display.getMinutes())}`
    : t('Tarih seçin');
  const timeValue = `${pad(selected.getHours())}:${pad(selected.getMinutes())}`;

  const todayKey = (() => { const n = new Date(); return `${n.getFullYear()}-${n.getMonth()}-${n.getDate()}`; })();
  const selKey = `${selected.getFullYear()}-${selected.getMonth()}-${selected.getDate()}`;

  const popover = open && pos ? createPortal(
    <div
      ref={popRef}
      className="fixed z-[1000] rounded-2xl border border-[#e2e1eb] bg-white shadow-xl p-3"
      style={{ left: pos.left, top: pos.top, bottom: pos.bottom, width: POPOVER_W }}
    >
      {/* Navigasyon — başlığa tıklayınca ay → yıl seçimine geçer */}
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

      {/* Gün ızgarası */}
      {mode === 'days' && (
        <>
          <div className="grid grid-cols-7 gap-1 mb-1">
            {DOW_TR.map(d => (
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

      {/* Ay ızgarası */}
      {mode === 'months' && (
        <div className="grid grid-cols-3 gap-1">
          {MONTHS_TR.map((mn, idx) => {
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

      {/* Yıl ızgarası */}
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

      {/* Saat + aksiyonlar */}
      <div className="flex items-center justify-between gap-2 mt-3 pt-3 border-t border-[#eeedf7]">
        <div className="flex items-center gap-1.5">
          <Clock className="w-4 h-4 text-[#747684]" />
          <input
            type="time"
            value={timeValue}
            onChange={setTime}
            className="bg-[#f3f3fc] border border-[#e2e1eb] rounded-lg px-2 py-1 text-sm text-[#1a1b22] focus:outline-none focus:border-[#002a7d]"
          />
        </div>
        <div className="flex items-center gap-1">
          <button type="button" onClick={goToday}
            className="text-xs font-semibold text-[#093eaa] px-2 py-1 rounded-lg hover:bg-[#f3f3fc]">
            {t('Bugün')}
          </button>
          <button type="button" onClick={() => setOpen(false)}
            className="text-xs font-semibold text-white bg-[#093eaa] px-3 py-1.5 rounded-lg hover:bg-[#002a7d]">
            {t('Tamam')}
          </button>
        </div>
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
        className="w-full flex items-center justify-between gap-2 bg-[#f3f3fc] border border-[#e2e1eb] rounded-xl px-4 py-2.5 text-sm text-[#1a1b22] focus:outline-none focus:border-[#002a7d] focus:ring-1 focus:ring-[#002a7d]"
      >
        <span className={display ? '' : 'text-[#747684]'}>{displayText}</span>
        <Calendar className="w-4 h-4 text-[#747684] shrink-0" />
      </button>
      {popover}
    </div>
  );
}
