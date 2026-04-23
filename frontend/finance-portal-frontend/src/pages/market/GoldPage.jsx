import { useEffect, useState, useCallback } from 'react';
import { TrendingUp, TrendingDown, RefreshCw, Calculator } from 'lucide-react';
import {
  XAxis, YAxis, CartesianGrid, Tooltip,
  ResponsiveContainer, Area, AreaChart,
} from 'recharts';
import { getGoldSpot, getGoldHistory } from '../../api/marketApi';
import { getGoldNews } from '../../api/newsApi';

// ── Helpers ───────────────────────────────────────────────────────────────────
function fmt(v, dec = 2) {
  if (v == null) return '-';
  return parseFloat(v).toLocaleString('tr-TR', {
    minimumFractionDigits: dec,
    maximumFractionDigits: dec,
  });
}

// ── Tabs ──────────────────────────────────────────────────────────────────────
const GOLD_TABS = [
  { key: 'ons',        label: 'Ons Altın',        gramRatio: 1,                                currency: 'USD' },
  { key: 'gram',       label: 'Gram Altın',        gramRatio: 1 / 31.1035,                      currency: 'TRY' },
  { key: 'ceyrek',     label: 'Çeyrek Altın',      gramRatio: (1.75 / 31.1035) * (22 / 24),     currency: 'TRY' },
  { key: 'cumhuriyet', label: 'Cumhuriyet Altını', gramRatio: (7.216 / 31.1035) * (22 / 24),   currency: 'TRY' },
  { key: 'ziynet',     label: 'Ziynet Altını',     gramRatio: (7.00 / 31.1035) * (22 / 24),     currency: 'TRY' },
  { key: '14ayar',     label: '14 Ayar Bilezik',   gramRatio: (1 / 31.1035) * (14 / 24),        currency: 'TRY' },
  { key: '22ayar',     label: '22 Ayar Bilezik',   gramRatio: (1 / 31.1035) * (22 / 24),        currency: 'TRY' },
];

const RANGES = ['1D', '1W', '1M', '3M', '1Y', 'ALL'];
const RANGE_LABELS = { '1D': '1G', '1W': '1H', '1M': '1A', '3M': '3A', '1Y': '1Y', 'ALL': 'Tümü' };

// ── Custom Tooltip ────────────────────────────────────────────────────────────
function ChartTooltip({ active, payload, label, currency }) {
  if (!active || !payload?.length) return null;
  const d = payload[0]?.payload;
  const sym = currency === 'TRY' ? '₺' : '$';
  return (
    <div className="bg-white border border-gray-200 rounded-xl shadow-lg px-4 py-3 text-sm min-w-[180px]">
      <p className="text-gray-500 text-xs font-semibold mb-2 border-b border-gray-100 pb-1.5">
        {label}
      </p>
      <div className="space-y-1">
        <div className="flex justify-between gap-4">
          <span className="text-gray-500 text-xs">Close:</span>
          <span className="font-bold text-gray-900 text-xs">
            {sym}{d?.close != null ? parseFloat(d.close).toLocaleString('tr-TR', { minimumFractionDigits: 2 }) : '-'}
          </span>
        </div>
        {d?.open != null && (
          <div className="flex justify-between gap-4">
            <span className="text-gray-500 text-xs">Open:</span>
            <span className="font-semibold text-gray-700 text-xs">
              {sym}{parseFloat(d.open).toLocaleString('tr-TR', { minimumFractionDigits: 2 })}
            </span>
          </div>
        )}
        {d?.high != null && (
          <div className="flex justify-between gap-4">
            <span className="text-gray-500 text-xs">High:</span>
            <span className="font-semibold text-emerald-600 text-xs">
              {sym}{parseFloat(d.high).toLocaleString('tr-TR', { minimumFractionDigits: 2 })}
            </span>
          </div>
        )}
        {d?.low != null && (
          <div className="flex justify-between gap-4">
            <span className="text-gray-500 text-xs">Low:</span>
            <span className="font-semibold text-rose-600 text-xs">
              {sym}{parseFloat(d.low).toLocaleString('tr-TR', { minimumFractionDigits: 2 })}
            </span>
          </div>
        )}
        {d?.volume != null && (
          <div className="flex justify-between gap-4">
            <span className="text-gray-500 text-xs">Volume:</span>
            <span className="font-semibold text-gray-600 text-xs">
              {d.volume?.toLocaleString('tr-TR') ?? 0}
            </span>
          </div>
        )}
      </div>
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
  const [news, setNews] = useState([]);
  const [newsLabel, setNewsLabel] = useState('İlgili Haberler');

  // Spot + news
  useEffect(() => {
    setLoadingSpot(true);
    getGoldSpot()
      .then(setSpot)
      .catch(() => setError('Altın verisi alınamadı.'))
      .finally(() => setLoadingSpot(false));
    getGoldNews()
      .then(result => {
        setNews(result?.items ?? []);
        setNewsLabel(result?.label ?? 'İlgili Haberler');
      })
      .catch(() => {});
  }, []);

  // History — TRY sekmelerinde her zaman TRY (gram altın) verisi çek, USD sekmesinde USD
  const loadHistory = useCallback(() => {
    setLoadingChart(true);
    const tab = GOLD_TABS.find(t => t.key === activeTab) ?? GOLD_TABS[0];
    const tabIsUsd = tab.currency === 'USD';
    // TRY sekmelerinde gram altın TRY verisi çek (ratio ile dönüştüreceğiz)
    // ONS sekmesinde currency state'ini kullan (USD veya TRY toggle)
    const fetchCurrency = tabIsUsd ? currency : 'TRY';
    getGoldHistory(range, fetchCurrency)
      .then(setHistory)
      .catch(() => setHistory(null))
      .finally(() => setLoadingChart(false));
  }, [range, currency, activeTab]);

  useEffect(() => { loadHistory(); }, [loadHistory]);

  // Tab change
  function handleTabChange(tabKey) {
    setActiveTab(tabKey);
    const tab = GOLD_TABS.find(t => t.key === tabKey);
    setCurrency(tab?.currency === 'USD' ? 'USD' : 'TRY');
  }

  // Active tab config
  const activeTabConfig = GOLD_TABS.find(t => t.key === activeTab) ?? GOLD_TABS[0];
  const isUsd = activeTabConfig.currency === 'USD';
  const ratio = activeTabConfig.gramRatio;
  const usdTry = parseFloat(spot?.usdTry ?? 44.5);

  // Display price / change — computed once
  const displayPrice = spot
    ? (isUsd ? parseFloat(spot.price) : parseFloat(spot.price) * ratio * usdTry)
    : null;
  const displayChange = spot
    ? (isUsd ? parseFloat(spot.change) : parseFloat(spot.change) * ratio * usdTry)
    : null;
  const isDown = (displayChange ?? 0) < 0;
  const priceColor = isDown ? 'text-rose-600' : 'text-emerald-600';

  // Chart data — TRY sekmelerinde backend gram altın TRY döndürür,
  // sekmenin gramRatio'sunu gram altın oranına (1/31.1035) bölerek çarpan bulunur
  const GRAM_RATIO = 1 / 31.1035;
  const tabMultiplier = isUsd ? 1 : (ratio / GRAM_RATIO);

  const chartPoints = history?.points?.map(p => {
    const mult = isUsd ? 1 : tabMultiplier;
    return {
      date: p.date,
      value: parseFloat(p.close) * mult,
      close: (parseFloat(p.close) * mult).toFixed(2),
      open:  p.open  != null ? (parseFloat(p.open)  * mult).toFixed(2) : null,
      high:  p.high  != null ? (parseFloat(p.high)  * mult).toFixed(2) : null,
      low:   p.low   != null ? (parseFloat(p.low)   * mult).toFixed(2) : null,
      volume: p.volume,
    };
  }) ?? [];

  const chartMin = chartPoints.length > 0 ? Math.min(...chartPoints.map(p => p.value)) * 0.998 : 0;
  const chartMax = chartPoints.length > 0 ? Math.max(...chartPoints.map(p => p.value)) * 1.002 : 0;

  // Info grid helper
  function displayVal(usdVal) {
    if (usdVal == null) return '-';
    if (isUsd) return fmt(usdVal);
    return '₺' + fmt(parseFloat(usdVal) * ratio * usdTry);
  }

  // Serbest piyasa table rows
  const gramTl = parseFloat(spot?.gramTl ?? 0);
  const AYAR_22 = 22 / 24;
  const AYAR_14 = 14 / 24;

  const serbestRows = spot ? [
    {
      name: 'ALTIN/ONS',
      buy: parseFloat(spot.bid),
      sell: parseFloat(spot.ask),
      pct: spot.changePercent,
      sym: '$',
    },
    {
      name: 'GRAM ALTIN',
      buy: gramTl * 0.999,
      sell: gramTl * 1.001,
      pct: spot.changePercent,
      sym: '₺',
    },
    {
      name: 'ÇEYREK ALTIN',
      buy: parseFloat(spot.ceyrekTl) * 0.999,
      sell: parseFloat(spot.ceyrekTl) * 1.001,
      pct: spot.changePercent,
      sym: '₺',
    },
    {
      name: 'YARIM ALTIN',
      buy: parseFloat(spot.yarimTl) * 0.999,
      sell: parseFloat(spot.yarimTl) * 1.001,
      pct: spot.changePercent,
      sym: '₺',
    },
    {
      name: 'TAM ALTIN',
      buy: parseFloat(spot.tamTl) * 0.999,
      sell: parseFloat(spot.tamTl) * 1.001,
      pct: spot.changePercent,
      sym: '₺',
    },
    {
      name: 'CUMHURİYET ALTINI',
      buy: parseFloat(spot.cumhuriyetTl) * 0.999,
      sell: parseFloat(spot.cumhuriyetTl) * 1.001,
      pct: spot.changePercent,
      sym: '₺',
    },
    {
      name: '14 AYAR BİLEZİK/gr',
      buy: parseFloat(spot.ayar14Tl ?? (gramTl * AYAR_14)) * 0.999,
      sell: parseFloat(spot.ayar14Tl ?? (gramTl * AYAR_14)) * 1.001,
      pct: spot.changePercent,
      sym: '₺',
    },
    {
      name: '22 AYAR BİLEZİK/gr',
      buy: parseFloat(spot.ayar22Tl ?? (gramTl * AYAR_22)) * 0.999,
      sell: parseFloat(spot.ayar22Tl ?? (gramTl * AYAR_22)) * 1.001,
      pct: spot.changePercent,
      sym: '₺',
    },
  ] : [];

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-gray-900 border-l-4 border-[#093eaa] pl-4">Altın</h1>

      {/* Main card */}
      <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden">

        {/* Tab bar */}
        <div className="flex overflow-x-auto border-b border-gray-200">
          {GOLD_TABS.map(tab => (
            <button
              key={tab.key}
              onClick={() => handleTabChange(tab.key)}
              className={`px-5 py-3 text-sm font-semibold whitespace-nowrap transition-all border-b-2 ${
                activeTab === tab.key
                  ? 'border-[#093eaa] text-[#093eaa] bg-blue-50'
                  : 'border-transparent text-gray-600 hover:text-[#093eaa] hover:bg-gray-50'
              }`}
            >
              {tab.label}
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
              {/* Price header */}
              <div className="mb-6">
                <h2 className="text-lg font-bold text-gray-700 mb-1">
                  {activeTabConfig.label.toUpperCase()} ({isUsd ? '$' : '₺'})
                </h2>
                <div className="flex items-baseline gap-3 flex-wrap">
                  <span className={`text-4xl font-black ${priceColor}`}>
                    {isUsd ? '$' : '₺'}{fmt(displayPrice)}
                  </span>
                  <span className={`flex items-center gap-1 text-sm font-semibold ${priceColor}`}>
                    {isDown
                      ? <TrendingDown className="w-4 h-4" />
                      : <TrendingUp className="w-4 h-4" />}
                    {displayChange >= 0 ? '+' : ''}{fmt(displayChange)}
                    {' '}({parseFloat(spot.changePercent) >= 0 ? '+' : ''}{fmt(spot.changePercent)}%)
                  </span>
                </div>
                {!isUsd && (
                  <p className="text-sm text-gray-500 mt-1">
                    ONS: <span className="font-semibold">${fmt(spot.price)}</span>
                    <span className="ml-2 text-xs text-gray-400">(1 USD = ₺{fmt(usdTry, 4)})</span>
                  </p>
                )}
                {isUsd && spot.priceTl && (
                  <p className="text-sm text-gray-500 mt-1">
                    TL karşılığı: <span className="font-semibold text-gray-700">₺{fmt(spot.priceTl)}</span>
                    <span className="ml-2 text-xs text-gray-400">(1 USD = ₺{fmt(usdTry, 4)})</span>
                  </p>
                )}
                {spot.updatedAt && (
                  <p className="text-xs text-gray-400 mt-1">
                    Güncelleme:{' '}
                    {(() => {
                      try {
                        return new Date(spot.updatedAt.replace(/\[.*\]$/, '')).toLocaleString('tr-TR');
                      } catch {
                        return spot.updatedAt;
                      }
                    })()}
                  </p>
                )}
              </div>

              {/* Info grid */}
              <div className="grid grid-cols-2 gap-x-12 gap-y-4 mb-6 border-t border-gray-100 pt-5">
                {[
                  { label: 'ALIŞ',            value: displayVal(spot.bid) },
                  { label: 'SATIŞ',           value: displayVal(spot.ask) },
                  { label: 'FARK',            value: isUsd ? fmt(spot.change) : '₺' + fmt(parseFloat(spot.change ?? 0) * ratio * usdTry) },
                  { label: 'ÖNCEKİ KAPANIŞ', value: displayVal(spot.previousClose) },
                  { label: 'EN YÜKSEK',       value: displayVal(spot.high) },
                  { label: 'EN DÜŞÜK',        value: displayVal(spot.low) },
                ].map(item => (
                  <div key={item.label} className="flex justify-between items-center py-2 border-b border-gray-50">
                    <span className="text-xs font-bold text-gray-500 tracking-wider">{item.label}</span>
                    <span className="text-sm font-semibold text-gray-900">{item.value}</span>
                  </div>
                ))}
              </div>

              {/* Derived values — only for ons tab */}
              {activeTab === 'ons' && (
                <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mb-6">
                  {[
                    { label: 'Gram Altın',   value: spot.gramTl },
                    { label: 'Çeyrek Altın', value: spot.ceyrekTl },
                    { label: 'Yarım Altın',  value: spot.yarimTl },
                    { label: 'Tam Altın',    value: spot.tamTl },
                  ].map(item => (
                    <div key={item.label} className="bg-gray-50 rounded-xl p-3 text-center">
                      <p className="text-xs text-gray-500 font-semibold mb-1">{item.label}</p>
                      <p className="text-sm font-bold text-gray-900">₺{fmt(item.value)}</p>
                    </div>
                  ))}
                </div>
              )}

              {/* Chart controls */}
              <div className="flex items-center justify-between mb-4 flex-wrap gap-3">
                <div className="flex gap-1">
                  {RANGES.map(r => (
                    <button
                      key={r}
                      onClick={() => setRange(r)}
                      className={`px-3 py-1.5 rounded-lg text-xs font-bold transition-all ${
                        range === r
                          ? 'bg-[#093eaa] text-white'
                          : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
                      }`}
                    >
                      {RANGE_LABELS[r]}
                    </button>
                  ))}
                </div>
                <div className="flex items-center gap-2">
                  {/* USD/TRY toggle sadece ONS sekmesinde göster */}
                  {isUsd && (
                    <div className="flex gap-1">
                      {['USD', 'TRY'].map(c => (
                        <button
                          key={c}
                          onClick={() => setCurrency(c)}
                          className={`px-3 py-1.5 rounded-lg text-xs font-bold transition-all ${
                            currency === c
                              ? 'bg-[#093eaa] text-white'
                              : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
                          }`}
                        >
                          {c === 'USD' ? '$ ONS' : '₺ GRAM'}
                        </button>
                      ))}
                    </div>
                  )}
                  <button
                    onClick={loadHistory}
                    className="p-1.5 rounded-lg bg-gray-100 hover:bg-gray-200 transition-all"
                  >
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
                <p className="text-xs text-gray-400 mb-2">
                  {isUsd && currency === 'USD'
                    ? 'Altın/Ons (USD)'
                    : `${activeTabConfig.label} (₺) — Günlük USD/TRY kuru ile hesaplanmıştır`}
                </p>
                {chartPoints.length > 0 ? (
                  <ResponsiveContainer width="100%" height="100%">
                    <AreaChart data={chartPoints} margin={{ top: 5, right: 10, left: 10, bottom: 5 }}>
                      <defs>
                        <linearGradient id="goldGrad" x1="0" y1="0" x2="0" y2="1">
                          <stop offset="5%"  stopColor={isDown ? '#ef4444' : '#10b981'} stopOpacity={0.15} />
                          <stop offset="95%" stopColor={isDown ? '#ef4444' : '#10b981'} stopOpacity={0} />
                        </linearGradient>
                      </defs>
                      <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
                      <XAxis
                        dataKey="date"
                        tick={{ fontSize: 10, fill: '#9ca3af' }}
                        tickFormatter={d => {
                          if (!d) return '';
                          const parts = d.split('-');
                          if (range === '1D') return d;
                          if (range === '1W' || range === '1M') return `${parts[2]}/${parts[1]}`;
                          return `${parts[1]}/${parts[0]?.slice(2)}`;
                        }}
                        interval="preserveStartEnd"
                      />
                      <YAxis
                        domain={[chartMin, chartMax]}
                        tick={{ fontSize: 10, fill: '#9ca3af' }}
                        tickFormatter={v => fmt(v, 0)}
                        width={70}
                      />
                      <Tooltip content={<ChartTooltip currency={currency} />} />
                      <Area
                        type="monotone"
                        dataKey="value"
                        stroke={isDown ? '#ef4444' : '#10b981'}
                        strokeWidth={1.5}
                        fill="url(#goldGrad)"
                        dot={false}
                        activeDot={{ r: 4 }}
                      />
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

      {/* Serbest Piyasa table */}
      {spot && (
        <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden">
          <div className="px-6 py-4 border-b border-gray-100">
            <h2 className="font-bold text-gray-900">Serbest Piyasada Altın</h2>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full min-w-[560px]">
              <thead className="bg-gray-50">
                <tr>
                  {['Enstrüman', 'Alış', 'Satış', 'Değişim %'].map(h => (
                    <th
                      key={h}
                      className="text-left px-5 py-3 text-xs font-bold text-gray-500 uppercase tracking-wider border-b border-gray-200"
                    >
                      {h}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {serbestRows.map((row, i) => {
                  const pct = parseFloat(row.pct ?? 0);
                  const pos = pct >= 0;
                  return (
                    <tr key={i} className="border-t border-gray-100 hover:bg-gray-50 transition-colors">
                      <td className="px-5 py-3 text-sm font-bold text-gray-800">
                        <span className={`mr-2 ${pos ? 'text-emerald-500' : 'text-rose-500'}`}>
                          {pos ? '↑' : '↓'}
                        </span>
                        {row.name}
                      </td>
                      <td className={`px-5 py-3 text-sm font-semibold ${pos ? 'text-emerald-600' : 'text-rose-600'}`}>
                        {row.buy != null && !isNaN(row.buy) ? row.sym + fmt(row.buy) : '-'}
                      </td>
                      <td className={`px-5 py-3 text-sm font-semibold ${pos ? 'text-emerald-600' : 'text-rose-600'}`}>
                        {row.sell != null && !isNaN(row.sell) ? row.sym + fmt(row.sell) : '-'}
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
            * Fiyatlar Yahoo Finance (GC=F vadeli kontrat) ve TCMB günlük USD/TRY kuru kullanılarak
            hesaplanmıştır. Anlık piyasa fiyatlarından küçük sapmalar olabilir. Gösterge niteliğindedir.
          </div>
        </div>
      )}

      {/* Calculator + News */}
      {spot && (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <GoldCalculator spot={spot} />
          <GoldNews news={news} label={newsLabel} />
        </div>
      )}
    </div>
  );
}

// ── Gold Calculator ───────────────────────────────────────────────────────────
function GoldCalculator({ spot }) {
  const usdTry = parseFloat(spot.usdTry ?? 44.5);

  const AYAR_22 = 22 / 24;
  const AYAR_14 = 14 / 24;
  const gramTl = parseFloat(spot.gramTl ?? 0);

  const GOLD_TYPES = [
    { key: 'ons',        label: 'ALTIN/ONS ($)',          priceUsd: parseFloat(spot.price) },
    { key: 'gram',       label: 'GRAM ALTIN (₺)',          priceTl: gramTl },
    { key: 'ceyrek',     label: 'ÇEYREK ALTIN (₺)',        priceTl: parseFloat(spot.ceyrekTl) },
    { key: 'yarim',      label: 'YARIM ALTIN (₺)',         priceTl: parseFloat(spot.yarimTl) },
    { key: 'tam',        label: 'TAM ALTIN (₺)',           priceTl: parseFloat(spot.tamTl) },
    { key: 'cumhuriyet', label: 'CUMHURİYET ALTINI (₺)',   priceTl: parseFloat(spot.cumhuriyetTl) },
    { key: 'ziynet',     label: 'ZİYNET ALTINI (₺)',       priceTl: parseFloat(spot.tamTl) },
    { key: '14ayar',     label: '14 AYAR BİLEZİK (₺/gr)', priceTl: parseFloat(spot.ayar14Tl ?? (gramTl * AYAR_14)) },
    { key: '22ayar',     label: '22 AYAR BİLEZİK (₺/gr)', priceTl: parseFloat(spot.ayar22Tl ?? (gramTl * AYAR_22)) },
    { key: 'try',        label: 'TÜRK LİRASI (₺)',         priceTl: 1 },
    { key: 'usd',        label: 'AMERİKAN DOLARI ($)',      priceUsd: 1 },
  ];

  const [fromKey, setFromKey] = useState('ons');
  const [toKey, setToKey] = useState('try');
  const [amount, setAmount] = useState('1');

  function getPrice(key) {
    const t = GOLD_TYPES.find(x => x.key === key);
    if (!t) return 1;
    if (t.priceTl != null && !isNaN(t.priceTl)) return t.priceTl;
    if (t.priceUsd != null && !isNaN(t.priceUsd)) return t.priceUsd * usdTry;
    return 1;
  }

  function calculate() {
    const a = parseFloat(amount || 0);
    if (!a || isNaN(a)) return '-';
    const fromPrice = getPrice(fromKey);
    const toPrice = getPrice(toKey);
    if (!toPrice) return '-';
    const result = (a * fromPrice) / toPrice;
    return result.toLocaleString('tr-TR', { minimumFractionDigits: 2, maximumFractionDigits: 6 });
  }

  return (
    <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">
      <h2 className="font-bold text-gray-900 mb-4 flex items-center gap-2">
        <Calculator className="w-5 h-5 text-[#093eaa]" /> Altın Hesaplama
      </h2>
      <div className="space-y-4">
        <div>
          <label className="block text-xs font-bold text-gray-500 mb-1.5">KAYNAK</label>
          <select
            value={fromKey}
            onChange={e => setFromKey(e.target.value)}
            className="w-full px-4 py-2.5 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-[#093eaa]/30 bg-white"
          >
            {GOLD_TYPES.map(t => (
              <option key={t.key} value={t.key}>{t.label}</option>
            ))}
          </select>
        </div>
        <div>
          <label className="block text-xs font-bold text-gray-500 mb-1.5">HEDEF</label>
          <select
            value={toKey}
            onChange={e => setToKey(e.target.value)}
            className="w-full px-4 py-2.5 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-[#093eaa]/30 bg-white"
          >
            {GOLD_TYPES.map(t => (
              <option key={t.key} value={t.key}>{t.label}</option>
            ))}
          </select>
        </div>
        <div>
          <label className="block text-xs font-bold text-gray-500 mb-1.5">TUTAR</label>
          <input
            type="number"
            value={amount}
            onChange={e => setAmount(e.target.value)}
            placeholder="1"
            className="w-full px-4 py-2.5 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-[#093eaa]/30"
          />
        </div>
        <div className="bg-[#093eaa] rounded-xl p-4 text-center">
          <p className="text-white/70 text-xs mb-1">
            {amount || '0'} {GOLD_TYPES.find(t => t.key === fromKey)?.label} =
          </p>
          <p className="text-white text-2xl font-black">{calculate()}</p>
          <p className="text-white/70 text-xs mt-1">
            {GOLD_TYPES.find(t => t.key === toKey)?.label}
          </p>
        </div>
        <p className="text-xs text-gray-400 text-center">
          Gösterge niteliğindedir. Anlık fiyatlar değişebilir.
        </p>
      </div>
    </div>
  );
}

// ── Gold News ─────────────────────────────────────────────────────────────────
function GoldNews({ news, label = 'İlgili Haberler' }) {
  return (
    <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">
      <h2 className="font-bold text-gray-900 mb-4 flex items-center gap-2">
        📰 {label}
      </h2>
      {news.length === 0 ? (
        <p className="text-gray-400 text-sm">Haber yükleniyor...</p>
      ) : (
        <div className="space-y-4">
          {news.slice(0, 6).map((item, i) => (
            <a
              key={i}
              href={item.url}
              target="_blank"
              rel="noopener noreferrer"
              className="flex gap-3 group hover:bg-gray-50 rounded-xl p-2 -mx-2 transition-colors"
            >
              {item.imageUrl && (
                <img
                  src={item.imageUrl}
                  alt=""
                  className="w-16 h-16 object-cover rounded-lg flex-shrink-0"
                />
              )}
              <div className="flex-1 min-w-0">
                <p className="text-sm font-semibold text-gray-800 group-hover:text-[#093eaa] transition-colors line-clamp-2 leading-snug">
                  {item.title}
                </p>
                <p className="text-xs text-gray-400 mt-1">
                  {item.source && <span className="font-semibold">{item.source} · </span>}
                  {item.publishedAt
                    ? new Date(item.publishedAt).toLocaleDateString('tr-TR')
                    : ''}
                </p>
              </div>
            </a>
          ))}
        </div>
      )}
    </div>
  );
}
