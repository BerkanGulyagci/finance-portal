import { useCallback, useMemo, useState } from 'react';
import {
  ResponsiveContainer,
  PieChart,
  Pie,
  Cell,
  Sector,
} from 'recharts';
import PortfolioAnalyticsTooltip from './PortfolioAnalyticsTooltip';
import { formatMoney } from '../../utils/portfolioFormatUtils';
import { CHART_DONUT_COLORS, formatSharePercent } from '../../utils/portfolioAnalyticsHelpers';

const EXPAND_PX = 10;

function renderActiveShape(props) {
  const {
    cx,
    cy,
    innerRadius,
    outerRadius,
    startAngle,
    endAngle,
    fill,
  } = props;
  return (
    <Sector
      cx={cx}
      cy={cy}
      innerRadius={innerRadius}
      outerRadius={outerRadius + EXPAND_PX}
      startAngle={startAngle}
      endAngle={endAngle}
      fill={fill}
      stroke="#fff"
      strokeWidth={2}
      style={{ filter: 'drop-shadow(0 2px 6px rgba(15,23,42,0.12))' }}
    />
  );
}

function CenterDetail({ item, total, valuesHidden, currency, color, hint }) {
  if (!item) {
    return (
      <div className="text-center px-2">
        <p className="text-[10px] font-medium text-gray-400">{hint}</p>
        <p className="text-xs font-semibold text-gray-600 mt-0.5">Dilime tıklayın</p>
      </div>
    );
  }

  const pct = item.sharePct ?? (total > 0 ? (item.value / total) * 100 : null);

  return (
    <div className="text-center px-2 max-w-full">
      <span
        className="inline-block h-2 w-2 rounded-full mb-1"
        style={{ backgroundColor: color }}
        aria-hidden
      />
      <p className="text-[11px] font-semibold text-gray-800 truncate" title={item.name}>
        {item.name}
      </p>
      <p className="text-lg font-bold text-gray-900 tabular-nums leading-tight">
        {formatSharePercent(pct, valuesHidden)}
      </p>
      <p className="text-[10px] text-gray-500 tabular-nums mt-0.5">
        {formatMoney(item.value, currency, valuesHidden)}
      </p>
    </div>
  );
}

function hoverPayload(item) {
  if (!item) return [];
  return [{ name: item.name, value: item.value, payload: item }];
}

/**
 * Etkileşimli donut: dilim tıklanınca büyür; merkez + legend ile detay.
 * Hover ipucu grafik sağında (merkez panelinin arkasında kalmaz).
 */
export default function PortfolioDonutChart({
  data,
  valuesHidden,
  currency,
  height = 248,
  getCellKey = (entry, i) => entry.type ?? `${entry.name}-${i}`,
  onSelect,
}) {
  const [activeIndex, setActiveIndex] = useState(null);
  const [hoverIndex, setHoverIndex] = useState(null);

  const total = useMemo(
    () => data.reduce((s, d) => s + (d.value ?? 0), 0),
    [data],
  );

  const defaultIndex = 0;
  const hoverItem = hoverIndex != null ? data[hoverIndex] : null;

  const selectIndex = useCallback(
    index => {
      setActiveIndex(prev => {
        const next = prev === index ? null : index;
        onSelect?.(next != null ? data[next] : null, next);
        return next;
      });
    },
    [data, onSelect],
  );

  const defaultItem = data[defaultIndex];
  const defaultPct =
    defaultItem?.sharePct ??
    (total > 0 && defaultItem ? (defaultItem.value / total) * 100 : 0);
  const showDefaultCenter =
    activeIndex == null && hoverIndex == null && defaultPct >= 40;

  const centerItem =
    activeIndex != null
      ? data[activeIndex]
      : hoverIndex != null
        ? data[hoverIndex]
        : showDefaultCenter
          ? data[defaultIndex]
          : null;

  const centerColor =
    CHART_DONUT_COLORS[
      (activeIndex ?? hoverIndex ?? (showDefaultCenter ? defaultIndex : 0)) %
        CHART_DONUT_COLORS.length
    ];

  return (
    <div className="w-full min-w-0">
      <div className="relative w-full min-w-0" style={{ height }}>
        <ResponsiveContainer width="100%" height="100%" className="relative z-0">
          <PieChart margin={{ top: 4, right: 4, bottom: 4, left: 4 }}>
            <Pie
              data={data}
              dataKey="value"
              nameKey="name"
              cx="50%"
              cy="50%"
              innerRadius="56%"
              outerRadius="78%"
              paddingAngle={data.length > 1 ? 2 : 0}
              minAngle={3}
              label={false}
              labelLine={false}
              activeIndex={activeIndex ?? undefined}
              activeShape={renderActiveShape}
              onClick={(_, index) => selectIndex(index)}
              onMouseEnter={(_, index) => setHoverIndex(index)}
              onMouseLeave={() => setHoverIndex(null)}
              isAnimationActive
              animationDuration={280}
              animationEasing="ease-out"
              style={{ cursor: 'pointer' }}
            >
              {data.map((entry, i) => (
                <Cell
                  key={getCellKey(entry, i)}
                  fill={CHART_DONUT_COLORS[i % CHART_DONUT_COLORS.length]}
                  stroke="#fff"
                  strokeWidth={2}
                  opacity={
                    activeIndex != null && activeIndex !== i
                      ? 0.45
                      : hoverIndex != null && hoverIndex !== i && activeIndex == null
                        ? 0.72
                        : 1
                  }
                  style={{ transition: 'opacity 0.2s ease' }}
                />
              ))}
            </Pie>
          </PieChart>
        </ResponsiveContainer>

        {/* Hover tooltip — sabit sağda, merkez panelinin üstünde */}
        {hoverItem && (
          <div
            className="absolute right-1 top-1/2 z-30 max-w-[min(160px,42%)] -translate-y-1/2 pointer-events-none"
            role="tooltip"
          >
            <PortfolioAnalyticsTooltip
              active
              payload={hoverPayload(hoverItem)}
              label={hoverItem.name}
              valuesHidden={valuesHidden}
              currency={currency}
              valueIsMoney
            />
          </div>
        )}

        <div
          className="pointer-events-none absolute inset-0 z-10 flex items-center justify-center"
          aria-live="polite"
        >
          <div className="w-[44%] max-w-[140px] rounded-full bg-white/90 shadow-sm ring-1 ring-gray-100/80 py-2.5 px-1 backdrop-blur-[2px]">
            {centerItem ? (
              <CenterDetail
                item={centerItem}
                total={total}
                valuesHidden={valuesHidden}
                currency={currency}
                color={centerColor}
                hint={null}
              />
            ) : (
              <CenterDetail
                item={null}
                total={total}
                valuesHidden={valuesHidden}
                currency={currency}
                color={centerColor}
                hint="Dağılım"
              />
            )}
          </div>
        </div>
      </div>

      <p className="mt-1 text-center text-[10px] text-gray-400">
        Dilim veya etikete tıklayarak detayı görün
      </p>

      <ul className="mt-2 flex flex-wrap gap-1.5 justify-center" role="listbox" aria-label="Dağılım kalemleri">
        {data.map((entry, i) => {
          const isActive = activeIndex === i;
          const isHover = hoverIndex === i;
          const color = CHART_DONUT_COLORS[i % CHART_DONUT_COLORS.length];
          const pct = entry.sharePct ?? (total > 0 ? (entry.value / total) * 100 : 0);

          return (
            <li key={getCellKey(entry, i)}>
              <button
                type="button"
                role="option"
                aria-selected={isActive}
                onClick={() => selectIndex(i)}
                onMouseEnter={() => setHoverIndex(i)}
                onMouseLeave={() => setHoverIndex(null)}
                className={`inline-flex items-center gap-1.5 rounded-lg border px-2 py-1 text-left text-[11px] transition-all max-w-[200px] ${
                  isActive
                    ? 'border-[#093eaa]/40 bg-sky-50/90 shadow-sm ring-1 ring-[#093eaa]/20'
                    : isHover
                      ? 'border-gray-300 bg-gray-50'
                      : 'border-gray-200 bg-white hover:border-gray-300 hover:bg-gray-50'
                }`}
              >
                <span
                  className="h-2.5 w-2.5 shrink-0 rounded-sm"
                  style={{ backgroundColor: color }}
                  aria-hidden
                />
                <span className="truncate font-medium text-gray-800" title={entry.name}>
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
