import { useEffect, useMemo, useState } from 'react';
import {
  ResponsiveContainer,
  ComposedChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ReferenceArea,
} from 'recharts';
import { getCryptoFearGreedSummary, getCryptoChart } from '../../../../api/marketApi';
import { parseMarketChartPrices } from '../utils/cryptoChartRanges';
import { useTranslation } from '../../../../context/LanguageContext';

const PRICE_COLOR = '#093eaa';
const FNG_COLOR = '#f59e0b';

const CLASSIFICATION_KEY = {
  'Extreme Fear': 'Aşırı Korku',
  Fear: 'Korku',
  Neutral: 'Nötr',
  Greed: 'Hırs',
  'Extreme Greed': 'Aşırı Hırs',
};

function fngColor(v) {
  if (v == null) return '#9ca3af';
  if (v < 25) return '#ef4444';
  if (v < 45) return '#f97316';
  if (v < 55) return '#eab308';
  if (v < 75) return '#84cc16';
  return '#22c55e';
}

function dayKey(ms) {
  const d = new Date(Number(ms));
  if (Number.isNaN(d.getTime())) return null;
  return `${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, '0')}-${String(d.getUTCDate()).padStart(2, '0')}`;
}

function dateLabel(ms, locale) {
  return new Date(Number(ms)).toLocaleDateString(locale, { day: '2-digit', month: 'short' });
}

function clsLabel(classification, t, language) {
  if (!classification) return null;
  const key = CLASSIFICATION_KEY[classification];
  return language === 'tr' && key ? t(key) : classification;
}

function mergeByDay(series, priceData) {
  const priceByDay = new Map();
  for (const p of priceData ?? []) {
    const k = dayKey(p?.ts);
    if (k != null && p?.price != null) priceByDay.set(k, Number(p.price));
  }
  return (series ?? [])
    .map((r) => {
      const ts = Number(r?.timestamp);
      const k = dayKey(ts);
      return {
        ts,
        value: r?.value == null ? null : Number(r.value),
        classification: r?.classification ?? null,
        price: k != null && priceByDay.has(k) ? priceByDay.get(k) : null,
      };
    })
    .filter((row) => row.ts && !Number.isNaN(row.ts))
    .sort((a, b) => a.ts - b.ts);
}

function FngTooltip({ active, payload, t, language, locale }) {
  if (!active || !payload?.length) return null;
  const row = payload[0]?.payload;
  if (!row) return null;
  const label = clsLabel(row.classification, t, language);
  return (
    <div className="rounded-lg border border-gray-200 bg-white px-3 py-2 shadow-md text-xs">
      <p className="font-semibold text-gray-700 mb-1">{dateLabel(row.ts, locale)}</p>
      {row.price != null && (
        <p className="text-gray-600">
          <span className="font-medium" style={{ color: PRICE_COLOR }}>{t('Fiyat')}:</span>{' '}
          {Number(row.price).toLocaleString(locale, { maximumFractionDigits: 2 })}
        </p>
      )}
      {row.value != null && (
        <p className="text-gray-600">
          <span className="font-medium" style={{ color: FNG_COLOR }}>{t('Korku ve Hırs Endeksi')}:</span>{' '}
          <span style={{ color: fngColor(row.value), fontWeight: 600 }}>
            {row.value}{label ? ` (${label})` : ''}
          </span>
        </p>
      )}
    </div>
  );
}

const GAUGE_BANDS = [
  { from: 0, to: 25, color: '#ef4444' },
  { from: 25, to: 45, color: '#f97316' },
  { from: 45, to: 55, color: '#eab308' },
  { from: 55, to: 75, color: '#84cc16' },
  { from: 75, to: 100, color: '#22c55e' },
];

function polar(cx, cy, r, valuePct) {
  const angle = Math.PI * (1 - valuePct / 100);
  return { x: cx + r * Math.cos(angle), y: cy - r * Math.sin(angle) };
}

function arcPath(cx, cy, r, from, to) {
  const a = polar(cx, cy, r, from);
  const b = polar(cx, cy, r, to);
  const large = to - from > 50 ? 1 : 0;
  return `M ${a.x} ${a.y} A ${r} ${r} 0 ${large} 1 ${b.x} ${b.y}`;
}

function Gauge({ value, label, color }) {
  const W = 200;
  const H = 116;
  const cx = W / 2;
  const cy = 104;
  const r = 84;
  const v = Math.max(0, Math.min(100, Number(value) || 0));
  const needle = polar(cx, cy, r - 6, v);
  return (
    <svg viewBox={`0 0 ${W} ${H}`} className="w-full max-w-[220px]" role="img" aria-label={`${value}`}>
      {GAUGE_BANDS.map((b) => (
        <path
          key={b.from}
          d={arcPath(cx, cy, r, b.from, b.to)}
          fill="none"
          stroke={b.color}
          strokeWidth={13}
          strokeLinecap="butt"
        />
      ))}
      <line x1={cx} y1={cy} x2={needle.x} y2={needle.y} stroke="#374151" strokeWidth={3} strokeLinecap="round" />
      <circle cx={cx} cy={cy} r={6} fill="#374151" />
      <text x={cx} y={cy - 26} textAnchor="middle" fontSize="34" fontWeight="800" fill={color}>{value}</text>
      <text x={cx} y={cy - 8} textAnchor="middle" fontSize="11" fontWeight="700" fill="#6b7280">{label}</text>
    </svg>
  );
}

function HistoryBadge({ title, point, t, language }) {
  const v = point?.value;
  const color = fngColor(v);
  const label = clsLabel(point?.classification, t, language);
  return (
    <div className="flex items-center justify-between gap-2 py-1.5">
      <span className="text-xs text-gray-500">{title}</span>
      <span
        className="inline-flex items-center gap-1.5 rounded-full px-2 py-0.5 text-xs font-bold text-white"
        style={{ backgroundColor: color }}
      >
        {v ?? '—'}{label ? ` · ${label}` : ''}
      </span>
    </div>
  );
}

function ExtremeRow({ title, point, t, language, locale }) {
  const v = point?.value;
  const color = fngColor(v);
  const label = clsLabel(point?.classification, t, language);
  const date = point?.timestamp
    ? new Date(Number(point.timestamp)).toLocaleDateString(locale, { day: '2-digit', month: 'short', year: '2-digit' })
    : null;
  return (
    <div className="flex items-center justify-between gap-2 py-1.5">
      <div className="flex flex-col">
        <span className="text-xs text-gray-500">{title}</span>
        {date && <span className="text-[10px] text-gray-400">{date}</span>}
      </div>
      <span
        className="inline-flex items-center gap-1.5 rounded-full px-2 py-0.5 text-xs font-bold text-white"
        style={{ backgroundColor: color }}
      >
        {v ?? '—'}{label ? ` · ${label}` : ''}
      </span>
    </div>
  );
}

export default function FearGreedChart({ coinId, currency = 'try', days = 90 }) {
  const { t, language } = useTranslation();
  const locale = language === 'tr' ? 'tr-TR' : 'en-US';

  const fetchKey = `${coinId}:${days}:${currency}`;
  const [result, setResult] = useState({ key: null, summary: null, price: [], error: false });

  useEffect(() => {
    if (!coinId) return undefined;
    let alive = true;
    Promise.all([
      getCryptoFearGreedSummary(days),
      getCryptoChart(coinId, days, currency)
        .then(parseMarketChartPrices)
        .then((pairs) => (pairs ?? []).map((p) => ({ ts: p[0], price: p[1] })))
        .catch(() => []),
    ])
      .then(([summary, price]) => {
        if (alive) setResult({ key: fetchKey, summary: summary ?? null, price: price ?? [], error: false });
      })
      .catch(() => { if (alive) setResult({ key: fetchKey, summary: null, price: [], error: true }); });
    return () => { alive = false; };
  }, [coinId, days, currency, fetchKey]);

  const loaded = result.key === fetchKey;
  const loading = !loaded;
  const summary = loaded ? result.summary : null;
  const error = loaded && (result.error || !summary);

  const merged = useMemo(
    () => mergeByDay(summary?.series, loaded ? result.price : []),
    [summary, loaded, result.price],
  );

  const current = summary?.current;
  const currentColor = fngColor(current?.value);
  const currentLabel = clsLabel(current?.classification, t, language) ?? '';
  const hasData = merged.some((r) => r.value != null);

  return (
    <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-3 sm:p-5">
      {loading ? (
        <div className="flex items-center justify-center h-[300px]">
          <div className="flex gap-1.5">
            <div className="w-2 h-2 bg-[#093eaa] rounded-full animate-bounce" />
            <div className="w-2 h-2 bg-[#093eaa]/60 rounded-full animate-bounce [animation-delay:100ms]" />
            <div className="w-2 h-2 bg-[#093eaa]/30 rounded-full animate-bounce [animation-delay:200ms]" />
          </div>
        </div>
      ) : error ? (
        <p className="text-center text-sm text-gray-400 py-12">{t('Veri bulunamadı')}</p>
      ) : (
        <div className="flex flex-col sm:flex-row gap-5">
          {/* SOL PANEL */}
          <div className="sm:w-1/3 sm:border-r sm:border-gray-100 sm:pr-5">
            <p className="text-xs font-bold text-gray-500 uppercase tracking-wider mb-2">{t('Korku ve Hırs Endeksi')}</p>
            <div className="flex flex-col items-center">
              <Gauge value={current?.value ?? 0} label={currentLabel} color={currentColor} />
            </div>

            <div className="mt-3 border-t border-gray-100 pt-2">
              <p className="text-[11px] font-bold text-gray-400 uppercase tracking-wider mb-1">{t('Geçmiş Veriler')}</p>
              <HistoryBadge title={t('Dün')} point={summary?.yesterday} t={t} language={language} />
              <HistoryBadge title={t('Geçen Hafta')} point={summary?.lastWeek} t={t} language={language} />
              <HistoryBadge title={t('Geçen Ay')} point={summary?.lastMonth} t={t} language={language} />
            </div>

            <div className="mt-2 border-t border-gray-100 pt-2">
              <ExtremeRow title={t('Yıllık En Yüksek')} point={summary?.yearlyHigh} t={t} language={language} locale={locale} />
              <ExtremeRow title={t('Yıllık En Düşük')} point={summary?.yearlyLow} t={t} language={language} locale={locale} />
            </div>
          </div>

          {/* SAĞ PANEL */}
          <div className="sm:w-2/3 min-w-0">
            <p className="text-xs font-bold text-gray-500 uppercase tracking-wider mb-3">{t('Korku ve Hırs Endeksi Grafiği')}</p>
            {hasData ? (
              <>
                <div className="w-full h-[260px] sm:h-[300px]">
                  <ResponsiveContainer width="100%" height="100%">
                    <ComposedChart data={merged} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
                      <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" />
                      <XAxis
                        dataKey="ts"
                        type="number"
                        scale="time"
                        domain={['dataMin', 'dataMax']}
                        tick={{ fontSize: 11, fill: '#6b7280' }}
                        tickFormatter={(v) => dateLabel(v, locale)}
                        minTickGap={28}
                      />
                      <YAxis
                        yAxisId="price"
                        tick={{ fontSize: 11, fill: PRICE_COLOR }}
                        tickFormatter={(v) => Number(v).toLocaleString(locale, { notation: 'compact', maximumFractionDigits: 1 })}
                        width={52}
                        domain={['auto', 'auto']}
                      />
                      <YAxis
                        yAxisId="fng"
                        orientation="right"
                        domain={[0, 100]}
                        ticks={[0, 25, 50, 75, 100]}
                        tick={{ fontSize: 11, fill: FNG_COLOR }}
                        width={34}
                      />
                      <ReferenceArea yAxisId="fng" y1={75} y2={100} fill="#22c55e" fillOpacity={0.07} />
                      <ReferenceArea yAxisId="fng" y1={0} y2={25} fill="#ef4444" fillOpacity={0.07} />
                      <Tooltip content={<FngTooltip t={t} language={language} locale={locale} />} />
                      <Line
                        yAxisId="price"
                        type="monotone"
                        dataKey="price"
                        name={t('Fiyat')}
                        stroke={PRICE_COLOR}
                        strokeWidth={2}
                        dot={false}
                        connectNulls
                        isAnimationActive={false}
                      />
                      <Line
                        yAxisId="fng"
                        type="monotone"
                        dataKey="value"
                        name={t('Korku ve Hırs Endeksi')}
                        stroke={FNG_COLOR}
                        strokeWidth={2}
                        dot={false}
                        connectNulls
                        isAnimationActive={false}
                      />
                    </ComposedChart>
                  </ResponsiveContainer>
                </div>
                <div className="flex items-center justify-between gap-3 mt-2 flex-wrap">
                  <div className="flex flex-wrap gap-3">
                    <span className="flex items-center gap-1.5 text-xs font-semibold text-gray-600">
                      <span className="w-3 h-0.5 rounded" style={{ backgroundColor: PRICE_COLOR }} />
                      {t('Fiyat')}
                    </span>
                    <span className="flex items-center gap-1.5 text-xs font-semibold text-gray-600">
                      <span className="w-3 h-0.5 rounded" style={{ backgroundColor: FNG_COLOR }} />
                      {t('Korku ve Hırs Endeksi')} (0–100)
                    </span>
                  </div>
                  <p className="text-[11px] text-gray-400">{t('Piyasa geneli · son {n} gün', { n: days })}</p>
                </div>
              </>
            ) : (
              <p className="text-center text-sm text-gray-400 py-12">{t('Veri bulunamadı')}</p>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
