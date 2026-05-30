import { useState, useEffect, useCallback, useMemo } from 'react';
import {
  LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip,
  Legend, ResponsiveContainer, ReferenceLine,
} from 'recharts';
import { getPreciousMetalHistory } from '../../../api/marketApi';
import { useTranslation } from '../../../context/LanguageContext';

// ── Sabitler ──────────────────────────────────────────────────────────────────

const METALS = [
  { key: 'gold',      label: 'Altın',    color: '#f59e0b', defaultOn: true  },
  { key: 'silver',    label: 'Gümüş',    color: '#6b7280', defaultOn: true  },
  { key: 'platinum',  label: 'Platin',   color: '#3b82f6', defaultOn: false },
  { key: 'palladium', label: 'Paladyum', color: '#8b5cf6', defaultOn: false },
];

const RANGES = [
  { key: '1W',  label: '1H'    },
  { key: '1M',  label: '1A'    },
  { key: '3M',  label: '3A'    },
  { key: '6M',  label: '6A'    },
  { key: '1Y',  label: '1Y'    },
  { key: 'ALL', label: 'Tümü'  },
];

const UNITS = [
  { key: 'USD', label: 'USD/Ons' },
  { key: 'TRY', label: 'TL/Gram' },
];

const DISCLAIMER =
  'Bu değerler Borsa İstanbul resmi referans verileridir. ' +
  'Serbest piyasa alış/satış, işçilik ve makas dahil değildir.';

// ── Yardımcılar ───────────────────────────────────────────────────────────────

function fmt(v, dec = 2) {
  if (v == null || isNaN(parseFloat(v))) return '-';
  return parseFloat(v).toLocaleString('tr-TR', {
    minimumFractionDigits: dec,
    maximumFractionDigits: dec,
  });
}

function fmtPct(v) {
  if (v == null || isNaN(v)) return '-';
  return `${v >= 0 ? '+' : ''}${fmt(v)}%`;
}

/** Ham history noktasından birime göre fiyat çek */
function extractPrice(point, unit) {
  if (!point) return null;
  if (unit === 'USD') {
    const v = point.usdOns ?? point.close ?? point.value;
    return v != null && parseFloat(v) > 0 ? parseFloat(v) : null;
  }
  // TRY gram
  const v = point.tryGram ?? point.weightedAverage ?? point.close ?? point.value;
  return v != null && parseFloat(v) > 0 ? parseFloat(v) : null;
}

/** Normalize getiri serisi üret: ilk geçerli fiyat = 0% */
function normalizeHistory(points, unit) {
  const valid = points
    .map(p => ({ date: p.date, price: extractPrice(p, unit) }))
    .filter(p => p.price != null && p.price > 0);
  if (!valid.length) return [];
  const first = valid[0].price;
  return valid.map(p => ({
    date: p.date,
    ret: ((p.price - first) / first) * 100,
    price: p.price,
  }));
}

function dailyReturns(series) {
  const rets = [];
  for (let i = 1; i < series.length; i++) {
    const prev = series[i - 1].price;
    const curr = series[i].price;
    if (prev > 0) rets.push((curr - prev) / prev * 100);
  }
  return rets;
}

function stdDev(arr) {
  if (!arr.length) return null;
  const mean = arr.reduce((a, b) => a + b, 0) / arr.length;
  const variance = arr.reduce((a, b) => a + (b - mean) ** 2, 0) / arr.length;
  return Math.sqrt(variance);
}

function maxDrawdown(series) {
  let peak = -Infinity, maxDD = 0;
  for (const p of series) {
    if (p.price > peak) peak = p.price;
    const dd = (peak - p.price) / peak * 100;
    if (dd > maxDD) maxDD = dd;
  }
  return maxDD;
}

function periodReturn(series, days) {
  const valid = series.filter(p => p.price > 0);
  if (valid.length < 2) return null;
  const last = valid[valid.length - 1];
  const cutoff = new Date(last.date);
  cutoff.setDate(cutoff.getDate() - days);
  const from = valid.find(p => new Date(p.date) >= cutoff);
  if (!from || from === last) return null;
  return (last.price - from.price) / from.price * 100;
}

// ── Custom Tooltip ────────────────────────────────────────────────────────────

function CustomTooltip({ active, payload, label }) {
  const { t } = useTranslation();
  if (!active || !payload?.length) return null;
  return (
    <div className="bg-gray-900/95 text-white rounded-xl px-4 py-3 shadow-xl text-xs min-w-[180px]">
      <p className="text-gray-400 mb-2 font-medium">{label}</p>
      {payload.map(entry => (
        <div key={entry.dataKey} className="flex justify-between gap-4 mb-1">
          <span style={{ color: entry.color }} className="font-semibold">
            {(() => { const m = METALS.find(mt => mt.key === entry.dataKey); return m ? t(m.label) : entry.dataKey; })()}
          </span>
          <span className={`font-bold ${entry.value >= 0 ? 'text-emerald-400' : 'text-rose-400'}`}>
            {fmtPct(entry.value)}
          </span>
        </div>
      ))}
    </div>
  );
}

// ── Veri çekme — tüm metaller için /api/precious-metals/{metal}/history ───────

async function fetchMetalHistory(metalKey, range, unit) {
  const currency = unit === 'USD' ? 'USD' : 'TRY';
  try {
    const d = await getPreciousMetalHistory(metalKey, range, currency);
    return d?.points ?? [];
  } catch {
    return [];
  }
}

// ── Ana sayfa ─────────────────────────────────────────────────────────────────

export default function CommodityComparePage() {
  const { t } = useTranslation();
  const [selectedMetals, setSelectedMetals] = useState(
    METALS.filter(m => m.defaultOn).map(m => m.key)
  );
  const [range,   setRange]   = useState('1M');
  const [unit,    setUnit]    = useState('USD');
  const [loading, setLoading] = useState(false);
  const [rawData, setRawData] = useState({});

  const loadAll = useCallback(async () => {
    setLoading(true);
    const results = await Promise.all(
      selectedMetals.map(async key => ({
        key,
        points: await fetchMetalHistory(key, range, unit),
      }))
    );
    const map = {};
    results.forEach(r => { map[r.key] = r.points; });
    setRawData(map);
    setLoading(false);
  }, [selectedMetals, range, unit]);

  useEffect(() => { loadAll(); }, [loadAll]);

  const normalizedSeries = useMemo(() => {
    const out = {};
    selectedMetals.forEach(key => {
      out[key] = normalizeHistory(rawData[key] ?? [], unit);
    });
    return out;
  }, [rawData, selectedMetals, unit]);

  const chartData = useMemo(() => {
    const dateSet = new Set();
    selectedMetals.forEach(key => {
      (normalizedSeries[key] ?? []).forEach(p => dateSet.add(p.date));
    });
    const dates = Array.from(dateSet).sort();
    return dates.map(date => {
      const row = { date };
      selectedMetals.forEach(key => {
        const pt = (normalizedSeries[key] ?? []).find(p => p.date === date);
        row[key] = pt ? parseFloat(pt.ret.toFixed(2)) : null;
      });
      return row;
    });
  }, [normalizedSeries, selectedMetals]);

  const stats = useMemo(() => {
    const out = {};
    selectedMetals.forEach(key => {
      const series = normalizedSeries[key] ?? [];
      const rawSeries = (rawData[key] ?? [])
        .map(p => ({ date: p.date, price: extractPrice(p, unit) }))
        .filter(p => p.price != null && p.price > 0);

      const rets  = dailyReturns(rawSeries);
      const vol   = stdDev(rets);
      const mdd   = maxDrawdown(rawSeries);
      const best  = rets.length ? Math.max(...rets) : null;
      const worst = rets.length ? Math.min(...rets) : null;
      const totalRet = series.length ? series[series.length - 1]?.ret : null;

      out[key] = {
        totalRet,
        ret1d:  periodReturn(rawSeries, 1),
        ret1w:  periodReturn(rawSeries, 7),
        ret1m:  periodReturn(rawSeries, 30),
        ret3m:  periodReturn(rawSeries, 90),
        ret6m:  periodReturn(rawSeries, 180),
        ret1y:  periodReturn(rawSeries, 365),
        vol, mdd, best, worst,
        lastPrice: rawSeries.length ? rawSeries[rawSeries.length - 1].price : null,
        lastDate:  rawSeries.length ? rawSeries[rawSeries.length - 1].date  : null,
      };
    });
    return out;
  }, [normalizedSeries, rawData, selectedMetals, unit]);

  function toggleMetal(key) {
    setSelectedMetals(prev =>
      prev.includes(key)
        ? prev.length > 1 ? prev.filter(k => k !== key) : prev
        : [...prev, key]
    );
  }

  const sym = unit === 'USD' ? '$' : '₺';

  function fmtDate(d) {
    if (!d) return '';
    try { return new Date(d).toLocaleDateString('tr-TR', { month: 'short', day: 'numeric' }); }
    catch { return d; }
  }

  return (
    <div className="space-y-6">
      <h1 className="text-xl sm:text-2xl font-bold text-gray-900 border-l-4 border-[#093eaa] pl-4">
        {t('Emtia Karşılaştırma')}
      </h1>

      {/* Seçim kartı */}
      <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-3 sm:p-5">
        <div className="flex flex-wrap gap-3 sm:gap-6 items-start">
          {/* Metal seçimi */}
          <div>
            <p className="text-xs font-bold text-gray-500 uppercase tracking-wider mb-2">{t('Emtia')}</p>
            <div className="flex gap-2 flex-wrap">
              {METALS.map(m => {
                const active = selectedMetals.includes(m.key);
                return (
                  <button key={m.key} onClick={() => toggleMetal(m.key)}
                    className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-bold border transition-all ${
                      active ? 'text-white border-transparent' : 'bg-gray-50 text-gray-500 border-gray-200 hover:bg-gray-100'
                    }`}
                    style={active ? { backgroundColor: m.color } : {}}>
                    <span className="inline-block w-2.5 h-2.5 rounded-full"
                      style={{ backgroundColor: active ? 'rgba(255,255,255,0.7)' : m.color }} />
                    {t(m.label)}
                  </button>
                );
              })}
            </div>
          </div>

          {/* Dönem */}
          <div>
            <p className="text-xs font-bold text-gray-500 uppercase tracking-wider mb-2">{t('Dönem')}</p>
            <div className="flex gap-1">
              {RANGES.map(r => (
                <button key={r.key} onClick={() => setRange(r.key)}
                  className={`px-3 py-1.5 rounded-lg text-xs font-bold transition-all ${
                    range === r.key ? 'bg-[#093eaa] text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
                  }`}>
                  {t(r.label)}
                </button>
              ))}
            </div>
          </div>

          {/* Birim */}
          <div>
            <p className="text-xs font-bold text-gray-500 uppercase tracking-wider mb-2">{t('Birim')}</p>
            <div className="flex gap-1">
              {UNITS.map(u => (
                <button key={u.key} onClick={() => setUnit(u.key)}
                  className={`px-3 py-1.5 rounded-lg text-xs font-bold transition-all ${
                    unit === u.key ? 'bg-[#093eaa] text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
                  }`}>
                  {t(u.label)}
                </button>
              ))}
            </div>
          </div>
        </div>
      </div>

      {/* Özet kartlar */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
        {selectedMetals.map(key => {
          const m = METALS.find(x => x.key === key);
          const s = stats[key];
          const ret = s?.totalRet;
          const isPos = ret != null && ret >= 0;
          return (
            <div key={key} className="bg-white rounded-2xl border border-gray-200 shadow-sm p-4">
              <div className="flex items-center gap-2 mb-2">
                <span className="inline-block w-3 h-3 rounded-full" style={{ backgroundColor: m.color }} />
                <span className="text-sm font-bold text-gray-800">{t(m.label)}</span>
              </div>
              <p className={`text-xl font-black ${isPos ? 'text-emerald-600' : 'text-rose-600'}`}>
                {fmtPct(ret)}
              </p>
              <p className="text-xs text-gray-400 mt-1">
                {s?.lastPrice != null ? `${sym}${fmt(s.lastPrice)}` : '-'}
                {s?.lastDate ? ` · ${s.lastDate}` : ''}
              </p>
            </div>
          );
        })}
      </div>

      {/* Normalize performans grafiği */}
      <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-3 sm:p-6">
        <div className="flex items-center justify-between mb-4">
          <div>
            <h2 className="font-bold text-gray-900">{t('Normalize Performans')}</h2>
            <p className="text-xs text-gray-400 mt-0.5">
              {t('Başlangıç tarihi = 0% kabul edilir. Y ekseni: % getiri.')}
            </p>
          </div>
          {loading && (
            <div className="flex gap-1">
              <div className="w-2 h-2 bg-[#093eaa] rounded-full animate-bounce" />
              <div className="w-2 h-2 bg-[#093eaa]/60 rounded-full animate-bounce [animation-delay:100ms]" />
              <div className="w-2 h-2 bg-[#093eaa]/30 rounded-full animate-bounce [animation-delay:200ms]" />
            </div>
          )}
        </div>

        {chartData.length > 0 ? (
          <ResponsiveContainer width="100%" height={360}>
            <LineChart data={chartData} margin={{ top: 5, right: 20, left: 0, bottom: 5 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" vertical={false} />
              <XAxis dataKey="date" tickFormatter={fmtDate}
                tick={{ fontSize: 10, fill: '#94a3b8' }} tickLine={false} axisLine={false}
                interval={Math.max(1, Math.floor(chartData.length / 8))} />
              <YAxis tickFormatter={v => `${v >= 0 ? '+' : ''}${v.toFixed(1)}%`}
                tick={{ fontSize: 10, fill: '#94a3b8' }} tickLine={false} axisLine={false}
                width={60} />
              <ReferenceLine y={0} stroke="#e2e8f0" strokeDasharray="4 4" />
              <Tooltip content={<CustomTooltip />} />
              <Legend
                formatter={value => { const m = METALS.find(mt => mt.key === value); return m ? t(m.label) : value; }}
                wrapperStyle={{ fontSize: 12, paddingTop: 12 }} />
              {selectedMetals.map(key => {
                const m = METALS.find(x => x.key === key);
                return (
                  <Line key={key} type="monotone" dataKey={key} name={key}
                    stroke={m.color} strokeWidth={2} dot={false}
                    connectNulls activeDot={{ r: 4, fill: m.color }} />
                );
              })}
            </LineChart>
          </ResponsiveContainer>
        ) : (
          <div className="flex items-center justify-center h-64 text-gray-400 text-sm">
            {loading ? t('Veri yükleniyor...') : t('Grafik verisi bulunamadı.')}
          </div>
        )}
      </div>

      {/* Tablo 1: Dönem Getirileri */}
      <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden">
        <div className="px-3 sm:px-6 py-4 border-b border-gray-100">
          <h2 className="font-bold text-gray-900">{t('Dönem Getirileri')}</h2>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full min-w-[600px]">
            <thead className="bg-gray-50">
              <tr>
                {['Emtia', 'Günlük', 'Haftalık', '1 Ay', '3 Ay', '6 Ay', '1 Yıl'].map(h => (
                  <th key={h} className="text-left px-4 py-3 text-xs font-bold text-gray-500 uppercase tracking-wider border-b border-gray-200">
                    {t(h)}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {selectedMetals.map(key => {
                const m = METALS.find(x => x.key === key);
                const s = stats[key];
                const cell = v => (
                  <span className={`font-semibold ${v == null ? 'text-gray-400' : v >= 0 ? 'text-emerald-600' : 'text-rose-600'}`}>
                    {fmtPct(v)}
                  </span>
                );
                return (
                  <tr key={key} className="border-t border-gray-100 hover:bg-gray-50 transition-colors">
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-2">
                        <span className="inline-block w-2.5 h-2.5 rounded-full" style={{ backgroundColor: m.color }} />
                        <span className="text-sm font-bold text-gray-800">{t(m.label)}</span>
                      </div>
                    </td>
                    <td className="px-4 py-3 text-sm">{cell(s?.ret1d)}</td>
                    <td className="px-4 py-3 text-sm">{cell(s?.ret1w)}</td>
                    <td className="px-4 py-3 text-sm">{cell(s?.ret1m)}</td>
                    <td className="px-4 py-3 text-sm">{cell(s?.ret3m)}</td>
                    <td className="px-4 py-3 text-sm">{cell(s?.ret6m)}</td>
                    <td className="px-4 py-3 text-sm">{cell(s?.ret1y)}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>

      {/* Tablo 2: Risk / Dalgalanma */}
      <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden">
        <div className="px-3 sm:px-6 py-4 border-b border-gray-100">
          <h2 className="font-bold text-gray-900">{t('Risk / Dalgalanma')}</h2>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full min-w-[560px]">
            <thead className="bg-gray-50">
              <tr>
                {['Emtia', 'Volatilite', 'Max Drawdown', 'En İyi Gün', 'En Kötü Gün'].map(h => (
                  <th key={h} className="text-left px-4 py-3 text-xs font-bold text-gray-500 uppercase tracking-wider border-b border-gray-200">
                    {t(h)}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {selectedMetals.map(key => {
                const m = METALS.find(x => x.key === key);
                const s = stats[key];
                return (
                  <tr key={key} className="border-t border-gray-100 hover:bg-gray-50 transition-colors">
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-2">
                        <span className="inline-block w-2.5 h-2.5 rounded-full" style={{ backgroundColor: m.color }} />
                        <span className="text-sm font-bold text-gray-800">{t(m.label)}</span>
                      </div>
                    </td>
                    <td className="px-4 py-3 text-sm text-gray-700 font-semibold">
                      {s?.vol != null ? `${fmt(s.vol)}%` : '-'}
                    </td>
                    <td className="px-4 py-3 text-sm font-semibold text-rose-600">
                      {s?.mdd != null ? `-${fmt(s.mdd)}%` : '-'}
                    </td>
                    <td className="px-4 py-3 text-sm font-semibold text-emerald-600">
                      {s?.best != null ? `+${fmt(s.best)}%` : '-'}
                    </td>
                    <td className="px-4 py-3 text-sm font-semibold text-rose-600">
                      {s?.worst != null ? `${fmt(s.worst)}%` : '-'}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>

      {/* Tablo 3: Güncel Fiyatlar */}
      <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden">
        <div className="px-3 sm:px-6 py-4 border-b border-gray-100 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2 sm:gap-0">
          <h2 className="font-bold text-gray-900">{t('Güncel Fiyatlar')}</h2>
          <span className="text-xs text-gray-400 bg-gray-50 border border-gray-200 px-2 py-0.5 rounded-full">
            {t('Kaynak: Borsa İstanbul')}
          </span>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full min-w-[560px]">
            <thead className="bg-gray-50">
              <tr>
                {['Emtia', 'USD/Ons', 'TL/Gram', 'Son Veri Tarihi', 'Kaynak'].map(h => (
                  <th key={h} className="text-left px-4 py-3 text-xs font-bold text-gray-500 uppercase tracking-wider border-b border-gray-200">
                    {t(h)}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {selectedMetals.map(key => {
                const m = METALS.find(x => x.key === key);
                const s = stats[key];
                const usdSeries = (rawData[key] ?? [])
                  .map(p => extractPrice(p, 'USD'))
                  .filter(v => v != null && v > 0);
                const trySeries = (rawData[key] ?? [])
                  .map(p => extractPrice(p, 'TRY'))
                  .filter(v => v != null && v > 0);
                const usdLast = usdSeries.length ? usdSeries[usdSeries.length - 1] : null;
                const tryLast = trySeries.length ? trySeries[trySeries.length - 1] : null;
                return (
                  <tr key={key} className="border-t border-gray-100 hover:bg-gray-50 transition-colors">
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-2">
                        <span className="inline-block w-2.5 h-2.5 rounded-full" style={{ backgroundColor: m.color }} />
                        <span className="text-sm font-bold text-gray-800">{t(m.label)}</span>
                      </div>
                    </td>
                    <td className="px-4 py-3 text-sm font-semibold text-gray-900">
                      {usdLast != null ? `$${fmt(usdLast)}` : '-'}
                    </td>
                    <td className="px-4 py-3 text-sm font-semibold text-gray-900">
                      {tryLast != null ? `₺${fmt(tryLast)}` : '-'}
                    </td>
                    <td className="px-4 py-3 text-sm text-gray-500">
                      {s?.lastDate ?? '-'}
                    </td>
                    <td className="px-4 py-3 text-xs text-emerald-700 font-semibold">
                      {t('Borsa İstanbul')}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
        <div className="px-5 py-2.5 bg-gray-50 border-t border-gray-100 text-xs text-gray-400">
          {t(DISCLAIMER)}
        </div>
      </div>
    </div>
  );
}
