import {
  ResponsiveContainer,
  LineChart, Line,
  AreaChart, Area,
  BarChart, Bar,
  XAxis, YAxis, CartesianGrid, Tooltip, ReferenceLine, Cell,
} from 'recharts';

const TR_MONTHS = ['Oca', 'Şub', 'Mar', 'Nis', 'May', 'Haz', 'Tem', 'Ağu', 'Eyl', 'Eki', 'Kas', 'Ara'];

/** EVDS dönem etiketini kısa Türkçe etikete çevirir: "2026-4"→"Nis 26", "2026-Q1"→"1Ç 26", "2026"→"2026". */
function formatPeriod(p) {
  if (!p) return '';
  const s = String(p);
  if (s.includes('-Q')) {
    const [y, q] = s.split('-Q');
    return `${q}Ç ${y.slice(2)}`;
  }
  const m = /^(\d{4})-(\d{1,2})$/.exec(s);
  if (m) {
    const mon = TR_MONTHS[Number(m[2]) - 1] || m[2];
    return `${mon} ${m[1].slice(2)}`;
  }
  return s;
}

function fmtNum(n, dec) {
  return Number(n).toLocaleString('tr-TR', { minimumFractionDigits: dec, maximumFractionDigits: dec });
}

/** Değeri birime göre formatlar (eksen + tooltip). compact: büyük tutarları mn/mr kısaltır. */
function fmtValue(v, unit, compact = false) {
  const n = Number(v);
  if (!Number.isFinite(n)) return '';
  switch (unit) {
    case '%': return `%${fmtNum(n, n % 1 === 0 ? 0 : 1)}`;
    case 'TL': return `${fmtNum(n, 2)} ₺`;
    case 'TL/gram': return `${fmtNum(n, 0)} ₺`;
    case 'USD': return `$${fmtNum(n, 0)}`;
    case 'milyon USD':
      if (compact) return Math.abs(n) >= 1000 ? `${fmtNum(n / 1000, 1)} mr$` : `${fmtNum(n, 0)} mn$`;
      return `${fmtNum(n, 0)} mn $`;
    case 'bin TL':
      if (compact) {
        const mr = n / 1e6;
        return Math.abs(mr) >= 1 ? `${fmtNum(mr, 1)} mr₺` : `${fmtNum(n / 1e3, 1)} mn₺`;
      }
      return `${fmtNum(n, 0)} bin ₺`;
    default: return fmtNum(n, 2);
  }
}

function CustomTooltip({ active, payload, unit }) {
  if (!active || !payload?.length) return null;
  const pt = payload[0].payload;
  return (
    <div className="bg-white border border-gray-200 rounded-lg shadow-md px-3 py-2 text-xs">
      <div className="font-semibold text-gray-700">{pt.fullPeriod}</div>
      <div className="font-bold text-[#093eaa]">{fmtValue(pt.y, unit)}</div>
    </div>
  );
}

const NAVY = '#093eaa';
const EMERALD = '#059669';
const ROSE = '#e11d48';
const AMBER = '#d97706';

/**
 * Genel ekonomi grafiği (çizgi / alan / bar).
 * Props: series = { unit, points:[{period,value}] }, type = 'line'|'area'|'bar', color, height
 */
export default function EconomyChart({ series, type = 'line', color = NAVY, height = 280 }) {
  const points = series?.points ?? [];
  const unit = series?.unit ?? '';

  if (!points.length) {
    return <div className="h-40 flex items-center justify-center text-sm text-gray-400">Grafik verisi yok</div>;
  }

  const data = points.map(p => ({
    x: formatPeriod(p.period),
    fullPeriod: p.period,
    y: Number(p.value),
  }));

  const hasNegative = data.some(d => d.y < 0);
  const axisTick = { fontSize: 11, fill: '#6b7280' };
  const yTickFmt = (v) => fmtValue(v, unit, true);

  const common = (
    <>
      <CartesianGrid strokeDasharray="3 3" stroke="#eef2f7" vertical={false} />
      <XAxis dataKey="x" tick={axisTick} tickLine={false} axisLine={{ stroke: '#e5e7eb' }} interval="preserveStartEnd" minTickGap={16} />
      <YAxis tick={axisTick} tickLine={false} axisLine={false} width={64} tickFormatter={yTickFmt} />
      <Tooltip content={<CustomTooltip unit={unit} />} cursor={{ stroke: '#cbd5e1', strokeWidth: 1 }} />
      {hasNegative && <ReferenceLine y={0} stroke="#9ca3af" strokeWidth={1} />}
    </>
  );

  return (
    <ResponsiveContainer width="100%" height={height}>
      {type === 'bar' ? (
        <BarChart data={data} margin={{ top: 8, right: 12, left: 0, bottom: 4 }}>
          {common}
          <Bar dataKey="y" radius={[4, 4, 0, 0]} maxBarSize={48}>
            {data.map((d, i) => (
              <Cell key={i} fill={d.y < 0 ? ROSE : color} />
            ))}
          </Bar>
        </BarChart>
      ) : type === 'area' ? (
        <AreaChart data={data} margin={{ top: 8, right: 12, left: 0, bottom: 4 }}>
          <defs>
            <linearGradient id={`grad-${color}`} x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor={color} stopOpacity={0.28} />
              <stop offset="100%" stopColor={color} stopOpacity={0.02} />
            </linearGradient>
          </defs>
          {common}
          <Area type="monotone" dataKey="y" stroke={color} strokeWidth={2.5} fill={`url(#grad-${color})`} dot={false} />
        </AreaChart>
      ) : (
        <LineChart data={data} margin={{ top: 8, right: 12, left: 0, bottom: 4 }}>
          {common}
          <Line type="monotone" dataKey="y" stroke={color} strokeWidth={2.5} dot={{ r: 2.5, fill: color }} activeDot={{ r: 4 }} />
        </LineChart>
      )}
    </ResponsiveContainer>
  );
}

export { NAVY, EMERALD, ROSE, AMBER };
