import { useTranslation } from '../../../../context/LanguageContext';

function toFloat(v) {
  if (v == null) return null;
  const n = parseFloat(v);
  return isNaN(n) ? null : n;
}

export default function MonthlyReturnChart({ monthlyReturns }) {
  const [hovered, setHovered] = useState(null);

  if (!monthlyReturns || monthlyReturns.length === 0) return null;

  const sorted = [...monthlyReturns].sort((a, b) => {
    const ay = parseInt(a.year) * 12 + parseInt(a.month);
    const by = parseInt(b.year) * 12 + parseInt(b.month);
    return ay - by;
  });
  const recent = sorted.slice(-24);

  const vals = recent.map(m => toFloat(m.value)).filter(v => v != null);
  if (vals.length === 0) return null;

  const maxPos = Math.max(...vals.filter(v => v >= 0), 0.01);
  const maxNeg = Math.max(...vals.filter(v => v < 0).map(Math.abs), 0.01);
  const posHeight = 80;
  const negHeight = vals.some(v => v < 0) ? 40 : 0;

  const MONTH_TR = {
    '1': 'Oca', '2': 'Şub', '3': 'Mar', '4': 'Nis', '5': 'May', '6': 'Haz',
    '7': 'Tem', '8': 'Ağu', '9': 'Eyl', '10': 'Eki', '11': 'Kas', '12': 'Ara'
  };

  return (
    <div className="relative select-none">
      {/* Custom tooltip */}
      {hovered && (
        <div className="absolute z-20 bg-gray-900 text-white text-xs rounded-lg px-3 py-2 pointer-events-none shadow-lg whitespace-nowrap"
          style={{ left: hovered.x, top: hovered.y, transform: 'translate(-50%, -110%)' }}>
          <p className="font-semibold">{hovered.label}</p>
          <p className={hovered.value >= 0 ? 'text-emerald-400' : 'text-rose-400'}>
            {hovered.value >= 0 ? '+' : ''}{hovered.value.toFixed(2)}%
          </p>
        </div>
      )}

      <div className="flex items-end gap-0.5" style={{ height: `${posHeight + negHeight}px` }}>
        {recent.map((m, i) => {
          const v = toFloat(m.value);
          if (v == null) return <div key={i} className="flex-1" />;
          const isPos = v >= 0;
          const barH = isPos
            ? Math.max(3, (Math.abs(v) / maxPos) * posHeight)
            : Math.max(3, (Math.abs(v) / maxNeg) * negHeight);
          const monthLabel = `${MONTH_TR[m.month] ?? m.month} ${m.year}`;

          return (
            <div key={i} className="flex-1 flex flex-col cursor-pointer"
              style={{ height: `${posHeight + negHeight}px` }}
              onMouseEnter={e => {
                const rect = e.currentTarget.getBoundingClientRect();
                const parent = e.currentTarget.closest('.relative').getBoundingClientRect();
                setHovered({ x: rect.left - parent.left + rect.width / 2, y: rect.top - parent.top, label: monthLabel, value: v });
              }}
              onMouseLeave={() => setHovered(null)}>
              <div className="flex flex-col justify-end" style={{ height: `${posHeight}px` }}>
                {isPos && (
                  <div className="w-full rounded-t-sm transition-opacity hover:opacity-75"
                    style={{ height: `${barH}px`, backgroundColor: '#10b981' }} />
                )}
              </div>
              {negHeight > 0 && (
                <div className="flex flex-col justify-start" style={{ height: `${negHeight}px` }}>
                  {!isPos && (
                    <div className="w-full rounded-b-sm transition-opacity hover:opacity-75"
                      style={{ height: `${barH}px`, backgroundColor: '#ef4444' }} />
                  )}
                </div>
              )}
            </div>
          );
        })}
      </div>

      <div className="border-t border-gray-300" />
      <div className="flex items-center gap-0.5 mt-1">
        {recent.map((m, i) => (
          <div key={i} className="flex-1 text-center overflow-hidden">
            {i % 3 === 0 && (
              <span className="text-[9px] text-gray-400 whitespace-nowrap">
                {MONTH_TR[m.month] ?? m.month} {m.year?.slice(-2)}
              </span>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}

// ── Varlık dağılımı (pie + tablo) ────────────────────────────────────────────

const PIE_COLORS = ['#3b82f6','#10b981','#f59e0b','#ef4444','#8b5cf6','#06b6d4','#f97316','#84cc16','#ec4899','#6366f1'];

