import { useEffect, useState, useCallback } from 'react';
import { TrendingUp, TrendingDown, RefreshCw } from 'lucide-react';
import {
  LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip,
  ResponsiveContainer, Area, AreaChart
} from 'recharts';
import { getGoldSpot, getGoldHistory } from '../../api/marketApi';

// ── Helpers ───────────────────────────────────────────────────────────────────
function fmt(v, dec = 2) {
  if (v == null) return '-';
  return parseFloat(v).toLocaleString('tr-TR', { minimumFractionDigits: dec, maximumFractionDigits: dec });
}

function PriceChange({ value, percent }) {
  const n = parseFloat(value ?? 0);
  const p = parseFloat(percent ?? 0);
  const pos = n >= 0;
  return (
    <span className={`flex items-center gap-1 text-sm font-semibold ${pos ? 'text-emerald-600' : 'text-rose-600'}`}>
      {pos ? <TrendingUp className="w-4 h-4" /> : <TrendingDown className="w-4 h-4" />}
      {pos ? '+' : ''}{fmt(n)} ({pos ? '+' : ''}{fmt(p)}%)
    </span>
  );
}

// ── Tabs ──────────────────────────────────────────────────────────────────────
const GOLD_TABS = [
  { key: 'ons', label: 'Ons Altın', active: true },
  { key: 'gram', label: 'Gram Altın', active: true },
  { key: 'ceyrek', label: 'Çeyrek Altın', active: false },
  { key: 'cumhuriyet', label: 'Cumhuriyet Altını', active: false },
  { key: 'ziynet', label: 'Ziynet Altını', active: false },
  { key: '14ayar', label: '14 Ayar Bilezik', active: false },
  { key: '22ayar', label: '22 Ayar Bilezik', active: false },
];

const RANGES = ['1D', '1W', '1M', '3M', '1Y', 'ALL'];
const RANGE_LABELS = { '1D': '1G', '1W': '1H', '1M': '1A', '3M': '3A', '1Y': '1Y', 'ALL': 'Tümü' };

// ── Custom Tooltip ────────────────────────────────────────────────────────────
function ChartTooltip({ active, payload, label, currency }) {
  if (!active || !payload?.length) return null;
  return (
    <div className="bg-white border border-gray-200 rounded-xl shadow-lg px-4 py-3 text-sm">
      <p className="text-gray-500 text-xs mb-1">{label}</p>
      <p className="font-bold text-gray-900">
        {fmt(payload[0].value)} {currency === 'TRY' ? '₺' : '$'}
      </p>
    </div>
  );
}

// ── Main Page ─────────────────────────────────────────────────────────────────
export default function GoldPage() {
  const [spot, setSpot] = useState(null);
  const [history, setHistory] = useState(null);
  const [activeTab, setActiveTab] = useState('ons');
  const [range, setRange] = useState('1M');
  const [currency, setCurrency] = useState('USD');
  const [loadingSpot, setLoadingSpot] = useState(true);
  const [loadingChart, setLoadingChart] = useState(false);
  const [error, setError] = useState('');

  // Spot data
  useEffect(() => {
    setLoadingSpot(true);
    getGoldSpot()
      .then(setSpot)
      .catch(e => setError('Altın verisi alınamadı.'))
      .finally(() => setLoadingSpot(false));
  }, []);

  // History data
  const loadHistory = useCallback(() => {
    setLoadingChart(true);
    getGoldHistory(range, currency)
      .then(setHistory)
      .catch(() => setHistory(null))
      .finally(() => setLoadingChart(false));
  }, [range, currency]);

  useEffect(() => { loadHistory(); }, [loadHistory]);

  // Aktif tab'a göre fiyat
  function getTabPrice() {
    if (!spot) return null;
    switch (activeTab) {
      case 'ons': return { price: spot.price, priceTl: spot.priceTl, unit: 'ONS', symbol: '$' };
      case 'gram': return { price: spot.gramTl / (spot.usdTry || 1), priceTl: spot.gramTl, unit: 'GRAM', symbol: '₺' };
      case 'ceyrek': return { price: null, priceTl: spot.ceyrekTl, unit: 'ÇEYREK', symbol: '₺' };
      default: return null;
    }
  }

  const tabData = getTabPrice();
  const isDown = parseFloat(spot?.changePercent ?? 0) < 0;
  const priceColor = isDown ? 'text-rose-600' : 'text-emerald-600';

  // Chart data
  const chartPoints = history?.points?.map(p => ({
    date: p.date,
    value: parseFloat(p.close),
  })) ?? [];

  const chartMin = chartPoints.length > 0 ? Math.min(...chartPoints.map(p => p.value)) * 0.998 : 0;
  const chartMax = chartPoints.length > 0 ? Math.max(...chartPoints.map(p => p.value)) * 1.002 : 0;

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-gray-900 border-l-4 border-[#093eaa] pl-4">Altın</h1>

      {/* Tab bar */}
      <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden">
        <div className="flex overflow-x-auto border-b border-gray-200">
          {GOLD_TABS.map(tab => (
            <button key={tab.key}
              onClick={() => tab.active && setActiveTab(tab.key)}
              className={`px-5 py-3 text-sm font-semibold whitespace-nowrap transition-all border-b-2 ${
                activeTab === tab.key
                  ? 'border-[#093eaa] text-[#093eaa] bg-blue-50'
                  : tab.active
                    ? 'border-transparent text-gray-600 hover:text-[#093eaa] hover:bg-gray-50'
                    : 'border-transparent text-gray-300 cursor-not-allowed'
              }`}>
              {tab.label}
              {!tab.active && <span className="ml-1 text-[10px] text-gray-300">yakında</span>}
            </button>
          ))}
        </div>

        <div className="p-6">
          {loadingSpot ? (
            <div className="flex items-center gap-2 py-8 justify-center">
              <div className="w-2 h-2 bg-[#093eaa] rounded-full animate-bounce" />
              <div className="w-2 h-2 bg-[#093eaa]/60 rounded-full animate-bounce [animation-delay:100ms]" />
              <div className="w-2 h-2 bg-[#093eaa]/30 rounded-full animate-bounce [animation-delay:200ms]" />
            </div>
          ) : error ? (
            <p className="text-rose-500 text-sm py-4">{error}</p>
          ) : spot && (
            <>
              {/* Başlık + Fiyat */}
              <div className="mb-6">
                <h2 className="text-lg font-bold text-gray-700 mb-1">
                  {activeTab === 'ons' ? 'ALTIN/ONS ($)' : activeTab === 'gram' ? 'GRAM ALTIN (₺)' : 'ÇEYREK ALTIN (₺)'}
                </h2>
                <div className="flex items-baseline gap-3 flex-wrap">
                  <span className={`text-4xl font-black ${priceColor}`}>
                    {activeTab === 'ons'
                      ? fmt(spot.price)
                      : activeTab === 'gram'
                        ? fmt(spot.gramTl)
                        : fmt(spot.ceyrekTl)
                    }
                  </span>
                  <PriceChange value={spot.change} percent={spot.changePercent} />
                </div>
                {activeTab === 'ons' && spot.priceTl && (
                  <p className="text-sm text-gray-500 mt-1">
                    TL karşılığı: <span className="font-semibold text-gray-700">₺{fmt(spot.priceTl)}</span>
                    <span className="ml-2 text-xs text-gray-400">(1 USD = ₺{fmt(spot.usdTry, 4)})</span>
                  </p>
                )}
                {spot.updatedAt && (
                  <p className="text-xs text-gray-400 mt-1">
                    Güncelleme: {new Date(spot.updatedAt).toLocaleString('tr-TR')}
                  </p>
                )}
              </div>

              {/* Info grid */}
              <div className="grid grid-cols-2 gap-x-12 gap-y-4 mb-6 border-t border-gray-100 pt-5">
                {[
                  { label: 'ALIŞ', value: fmt(spot.bid) },
                  { label: 'SATIŞ', value: fmt(spot.ask) },
                  { label: 'FARK', value: fmt(spot.change) },
                  { label: 'ÖNCEKİ KAPANIŞ', value: fmt(spot.previousClose) },
                  { label: 'EN YÜKSEK', value: fmt(spot.high) },
                  { label: 'EN DÜŞÜK', value: fmt(spot.low) },
                ].map(item => (
                  <div key={item.label} className="flex justify-between items-center py-2 border-b border-gray-50">
                    <span className="text-xs font-bold text-gray-500 tracking-wider">{item.label}</span>
                    <span className="text-sm font-semibold text-gray-900">{item.value}</span>
                  </div>
                ))}
              </div>

              {/* Türetilmiş değerler */}
              {activeTab === 'ons' && (
                <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mb-6">
                  {[
                    { label: 'Gram Altın', value: fmt(spot.gramTl), unit: '₺' },
                    { label: 'Çeyrek Altın', value: fmt(spot.ceyrekTl), unit: '₺' },
                    { label: 'Yarım Altın', value: fmt(spot.yarimTl), unit: '₺' },
                    { label: 'Tam Altın', value: fmt(spot.tamTl), unit: '₺' },
                  ].map(item => (
                    <div key={item.label} className="bg-gray-50 rounded-xl p-3 text-center">
                      <p className="text-xs text-gray-500 font-semibold mb-1">{item.label}</p>
                      <p className="text-sm font-bold text-gray-900">{item.unit}{item.value}</p>
                    </div>
                  ))}
                </div>
              )}

              {/* Chart controls */}
              <div className="flex items-center justify-between mb-4 flex-wrap gap-3">
                <div className="flex gap-1">
                  {RANGES.map(r => (
                    <button key={r} onClick={() => setRange(r)}
                      className={`px-3 py-1.5 rounded-lg text-xs font-bold transition-all ${
                        range === r ? 'bg-[#093eaa] text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
                      }`}>
                      {RANGE_LABELS[r]}
                    </button>
                  ))}
                </div>
                <div className="flex items-center gap-2">
                  <div className="flex gap-1">
                    {['USD', 'TRY'].map(c => (
                      <button key={c} onClick={() => setCurrency(c)}
                        className={`px-3 py-1.5 rounded-lg text-xs font-bold transition-all ${
                          currency === c ? 'bg-[#093eaa] text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
                        }`}>
                        {c === 'USD' ? '$' : '₺'} {c}
                      </button>
                    ))}
                  </div>
                  <button onClick={loadHistory} className="p-1.5 rounded-lg bg-gray-100 hover:bg-gray-200 transition-all">
                    <RefreshCw className={`w-3.5 h-3.5 text-gray-500 ${loadingChart ? 'animate-spin' : ''}`} />
                  </button>
                </div>
              </div>

              {/* Chart */}
              <div className="h-72 relative">
                {loadingChart && (
                  <div className="absolute inset-0 flex items-center justify-center bg-white/70 z-10 rounded-xl">
                    <div className="flex gap-1.5">
                      <div className="w-2 h-2 bg-[#093eaa] rounded-full animate-bounce" />
                      <div className="w-2 h-2 bg-[#093eaa]/60 rounded-full animate-bounce [animation-delay:100ms]" />
                      <div className="w-2 h-2 bg-[#093eaa]/30 rounded-full animate-bounce [animation-delay:200ms]" />
                    </div>
                  </div>
                )}
                {chartPoints.length > 0 ? (
                  <ResponsiveContainer width="100%" height="100%">
                    <AreaChart data={chartPoints} margin={{ top: 5, right: 10, left: 10, bottom: 5 }}>
                      <defs>
                        <linearGradient id="goldGrad" x1="0" y1="0" x2="0" y2="1">
                          <stop offset="5%" stopColor={isDown ? '#ef4444' : '#10b981'} stopOpacity={0.15} />
                          <stop offset="95%" stopColor={isDown ? '#ef4444' : '#10b981'} stopOpacity={0} />
                        </linearGradient>
                      </defs>
                      <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
                      <XAxis dataKey="date" tick={{ fontSize: 10, fill: '#9ca3af' }}
                        tickFormatter={d => {
                          if (!d) return '';
                          const parts = d.split('-');
                          if (range === '1D') return d;
                          if (range === '1W' || range === '1M') return `${parts[2]}/${parts[1]}`;
                          return `${parts[1]}/${parts[0]?.slice(2)}`;
                        }}
                        interval="preserveStartEnd" />
                      <YAxis domain={[chartMin, chartMax]} tick={{ fontSize: 10, fill: '#9ca3af' }}
                        tickFormatter={v => fmt(v, 0)} width={70} />
                      <Tooltip content={<ChartTooltip currency={currency} />} />
                      <Area type="monotone" dataKey="value"
                        stroke={isDown ? '#ef4444' : '#10b981'} strokeWidth={1.5}
                        fill="url(#goldGrad)" dot={false} activeDot={{ r: 4 }} />
                    </AreaChart>
                  </ResponsiveContainer>
                ) : (
                  <div className="flex items-center justify-center h-full text-gray-400 text-sm">
                    Grafik verisi yüklenemedi.
                  </div>
                )}
              </div>
            </>
          )}
        </div>
      </div>

      {/* Serbest Piyasa Tablosu */}
      {spot && (
        <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden">
          <div className="px-6 py-4 border-b border-gray-100">
            <h2 className="font-bold text-gray-900">Serbest Piyasada Altın</h2>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead className="bg-gray-50">
                <tr>
                  {['Enstrüman', 'Alış (₺)', 'Satış (₺)', 'Değişim %'].map(h => (
                    <th key={h} className="text-left px-5 py-3 text-xs font-bold text-gray-500 uppercase tracking-wider border-b border-gray-200">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {[
                  { name: 'ALTIN/ONS', buy: spot.bid, sell: spot.ask, pct: spot.changePercent },
                  { name: 'GRAM ALTIN', buy: spot.gramTl * 0.999, sell: spot.gramTl * 1.001, pct: spot.changePercent },
                  { name: 'ÇEYREK ALTIN', buy: spot.ceyrekTl * 0.999, sell: spot.ceyrekTl * 1.001, pct: spot.changePercent },
                  { name: 'YARIM ALTIN', buy: spot.yarimTl * 0.999, sell: spot.yarimTl * 1.001, pct: spot.changePercent },
                  { name: 'TAM ALTIN', buy: spot.tamTl * 0.999, sell: spot.tamTl * 1.001, pct: spot.changePercent },
                ].map((row, i) => {
                  const pct = parseFloat(row.pct ?? 0);
                  const pos = pct >= 0;
                  return (
                    <tr key={i} className="border-t border-gray-100 hover:bg-gray-50 transition-colors">
                      <td className="px-5 py-3 text-sm font-bold text-gray-800">
                        <span className={`mr-2 ${pos ? 'text-emerald-500' : 'text-rose-500'}`}>{pos ? '↑' : '↓'}</span>
                        {row.name}
                      </td>
                      <td className={`px-5 py-3 text-sm font-semibold ${pos ? 'text-emerald-600' : 'text-rose-600'}`}>
                        {fmt(row.buy)}
                      </td>
                      <td className={`px-5 py-3 text-sm font-semibold ${pos ? 'text-emerald-600' : 'text-rose-600'}`}>
                        {fmt(row.sell)}
                      </td>
                      <td className={`px-5 py-3 text-sm font-bold ${pos ? 'text-emerald-600' : 'text-rose-600'}`}>
                        {pos ? '+' : ''}{fmt(pct)}%
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
          <div className="px-5 py-2 bg-gray-50 border-t border-gray-100 text-xs text-gray-400">
            * Fiyatlar Yahoo Finance (GC=F) ve TCMB USD/TRY kuru kullanılarak hesaplanmıştır. Gösterge niteliğindedir.
          </div>
        </div>
      )}
    </div>
  );
}
