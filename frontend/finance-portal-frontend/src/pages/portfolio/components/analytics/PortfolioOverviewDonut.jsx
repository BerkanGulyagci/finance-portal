import { useMemo, useState } from 'react';
import { ResponsiveContainer, PieChart, Pie, Cell } from 'recharts';
import { CHART_DONUT_COLORS, formatSharePercent } from '../../utils/portfolioAnalyticsHelpers';
import { useTranslation } from '../../../../i18n/LanguageContext';

function currencySymbol(cur) {
  return cur === 'USD' ? '$' : cur === 'EUR' ? '€' : '₺';
}

function convertFromTry(amount, displayCurrency, rates) {
  if (amount == null || !Number.isFinite(amount)) return amount;
  if (displayCurrency === 'TRY' || !rates) return amount;
  const r = displayCurrency === 'USD' ? rates.usd : displayCurrency === 'EUR' ? rates.eur : null;
  return r ? amount / r : amount;
}

function formatCompact(amount, displayCurrency, rates, valuesHidden) {
  if (valuesHidden) return '•••••';
  if (amount == null || !Number.isFinite(amount)) return '-';
  const value = convertFromTry(amount, displayCurrency, rates);
  const sym = currencySymbol(displayCurrency);
  const abs = Math.abs(value);
  let body;
  if (abs >= 1_000_000) {
    body = `${(value / 1_000_000).toLocaleString('tr-TR', { maximumFractionDigits: 2 })}M`;
  } else if (abs >= 1_000) {
    body = `${(value / 1_000).toLocaleString('tr-TR', { maximumFractionDigits: 1 })}K`;
  } else {
    body = value.toLocaleString('tr-TR', { maximumFractionDigits: 0 });
  }
  return `${sym}${body}`;
}

/**
 * Kompakt donut: portföy genel görünümü için.
 * Merkezde varsayılan olarak toplam değer (tıklanınca currency cycle);
 * hover/seçim sırasında dilim adı + yüzde.
 */
export default function PortfolioOverviewDonut({
  data,
  displayCurrency = 'TRY',
  rates = null,
  valuesHidden = false,
  onCurrencyClick,
  totalLabel,
  height = 170,
}) {
  const { t } = useTranslation();
  const [activeIndex, setActiveIndex] = useState(null);
  const [hoverIndex, setHoverIndex] = useState(null);

  const total = useMemo(() => data.reduce((s, d) => s + (d.value ?? 0), 0), [data]);

  const focusedIndex = hoverIndex ?? activeIndex;
  const focused = focusedIndex != null ? data[focusedIndex] : null;
  const focusedPct = focused
    ? (focused.sharePct ?? (total > 0 ? (focused.value / total) * 100 : 0))
    : null;
  const focusedColor =
    focusedIndex != null
      ? CHART_DONUT_COLORS[focusedIndex % CHART_DONUT_COLORS.length]
      : '#475569';

  function toggleActive(index) {
    setActiveIndex(prev => (prev === index ? null : index));
  }

  return (
    <div className="w-full min-w-0">
      <div className="relative w-full min-w-0" style={{ height }}>
        <ResponsiveContainer width="100%" height="100%">
          <PieChart margin={{ top: 2, right: 2, bottom: 2, left: 2 }}>
            <Pie
              data={data}
              dataKey="value"
              nameKey="name"
              cx="50%"
              cy="50%"
              innerRadius="66%"
              outerRadius="92%"
              paddingAngle={data.length > 1 ? 1.5 : 0}
              minAngle={3}
              label={false}
              labelLine={false}
              onClick={(_, index) => toggleActive(index)}
              onMouseEnter={(_, index) => setHoverIndex(index)}
              onMouseLeave={() => setHoverIndex(null)}
              isAnimationActive
              animationDuration={240}
              animationEasing="ease-out"
              style={{ cursor: 'pointer' }}
            >
              {data.map((entry, i) => (
                <Cell
                  key={entry.type ?? `${entry.name}-${i}`}
                  fill={CHART_DONUT_COLORS[i % CHART_DONUT_COLORS.length]}
                  stroke="transparent"
                  opacity={
                    activeIndex != null && activeIndex !== i
                      ? 0.32
                      : hoverIndex != null && hoverIndex !== i && activeIndex == null
                        ? 0.6
                        : 1
                  }
                  style={{ transition: 'opacity 0.18s ease' }}
                />
              ))}
            </Pie>
          </PieChart>
        </ResponsiveContainer>

        <div
          className="pointer-events-none absolute inset-0 flex items-center justify-center"
          aria-live="polite"
        >
          {focused ? (
            <div className="text-center px-2 max-w-[78%]">
              <div className="flex items-center justify-center gap-1">
                <span
                  className="h-1.5 w-1.5 rounded-full shrink-0"
                  style={{ backgroundColor: focusedColor }}
                  aria-hidden
                />
                <p
                  className="text-[10px] font-medium text-gray-500 truncate"
                  title={focused.name}
                >
                  {focused.name}
                </p>
              </div>
              <p
                className="mt-0.5 text-base font-semibold tabular-nums leading-none"
                style={{ color: focusedColor }}
              >
                {formatSharePercent(focusedPct, valuesHidden)}
              </p>
              <p className="mt-0.5 text-[9px] text-gray-400 tabular-nums">
                {formatCompact(focused.value, displayCurrency, rates, valuesHidden)}
              </p>
            </div>
          ) : (
            <div className="text-center px-2 max-w-[78%]">
              <p className="text-[9px] uppercase tracking-[0.16em] font-medium text-gray-400">
                {totalLabel ?? t('Toplam')}
              </p>
              {onCurrencyClick ? (
                <button
                  type="button"
                  onClick={onCurrencyClick}
                  className="pointer-events-auto mt-1 inline-flex items-baseline gap-1 hover:opacity-80 transition-opacity"
                  title={t('Para birimini değiştir')}
                >
                  <span className="text-[13px] tabular-nums font-semibold text-slate-700 leading-tight">
                    {formatCompact(total, displayCurrency, rates, valuesHidden)}
                  </span>
                  <span className="text-[9px] font-bold uppercase text-[#093eaa] bg-[#093eaa]/10 px-1 py-0.5 rounded tracking-wide">
                    {displayCurrency}
                  </span>
                </button>
              ) : (
                <div className="mt-1 inline-flex items-baseline gap-1">
                  <span className="text-[13px] tabular-nums font-semibold text-slate-700 leading-tight">
                    {formatCompact(total, displayCurrency, rates, valuesHidden)}
                  </span>
                  <span className="text-[9px] font-bold uppercase text-[#093eaa] bg-[#093eaa]/10 px-1 py-0.5 rounded tracking-wide">
                    {displayCurrency}
                  </span>
                </div>
              )}
              <p className="mt-1 text-[9px] text-gray-400 font-medium">
                {t('{count} kalem', { count: data.length })}
              </p>
            </div>
          )}
        </div>
      </div>

      <ul
        className="mt-2 flex flex-wrap gap-1 justify-center"
        role="listbox"
        aria-label={t('Dağılım kalemleri')}
      >
        {data.map((entry, i) => {
          const isActive = activeIndex === i;
          const isHover = hoverIndex === i;
          const color = CHART_DONUT_COLORS[i % CHART_DONUT_COLORS.length];
          const pct = entry.sharePct ?? (total > 0 ? (entry.value / total) * 100 : 0);
          return (
            <li key={entry.type ?? `${entry.name}-${i}`}>
              <button
                type="button"
                role="option"
                aria-selected={isActive}
                onClick={() => toggleActive(i)}
                onMouseEnter={() => setHoverIndex(i)}
                onMouseLeave={() => setHoverIndex(null)}
                className={`inline-flex items-center gap-1 rounded-md px-1.5 py-0.5 text-left text-[10px] transition-all max-w-[160px] ${
                  isActive
                    ? 'bg-sky-50 ring-1 ring-[#093eaa]/30'
                    : isHover
                      ? 'bg-gray-100'
                      : 'hover:bg-gray-50'
                }`}
              >
                <span
                  className="h-2 w-2 shrink-0 rounded-sm"
                  style={{ backgroundColor: color }}
                  aria-hidden
                />
                <span className="truncate font-medium text-gray-700" title={entry.name}>
                  {entry.name}
                </span>
                <span className="shrink-0 tabular-nums text-gray-500">
                  {formatSharePercent(pct, valuesHidden)}
                </span>
              </button>
            </li>
          );
        })}
      </ul>
    </div>
  );
}
