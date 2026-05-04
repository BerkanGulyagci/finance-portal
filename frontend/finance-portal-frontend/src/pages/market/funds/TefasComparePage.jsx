import { useState, useCallback } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { X, Plus, TrendingUp, TrendingDown, BarChart2 } from 'lucide-react';
import {
  LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip,
  ResponsiveContainer, Legend,
} from 'recharts';
import { getTefasFundHistory, getTefasFundDetail } from '../../../api/marketApi';

const COLORS = ['#093eaa', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#06b6d4', '#f97316'];

const RANGES = ['1M', '3M', '6M', '1Y', '5Y'];
const RANGE_LABELS = { '1M': '1 Ay', '3M': '3 Ay', '6M': '6 Ay', '1Y': '1 Yıl', '5Y': '5 Yıl' };

function CompareTooltip({ active, payload, label }) {
  if (!active || !payload?.length) return null;
  return (
    <div className="bg-white border border-gray-200 rounded-xl shadow-lg px-4 py-3 text-sm min-w-[200px]">
      <p className="text-gray-500 text-xs font-semibold mb-2 border-b border-gray-100 pb-1.5">{label}</p>
      <div className="space-y-1">
        {payload.map((p, i) => (
          <div key={i} className="flex justify-between gap-4 items-center">
            <span className="flex items-center gap-1.5 text-xs text-gray-600">
              <span className="w-2 h-2 rounded-full flex-shrink-0" style={{ background: p.color }} />
              {p.name}
            </span>
            <span className="font-bold text-xs" style={{ color: p.color }}>
              {p.value >= 0 ? '+' : ''}{p.value?.toFixed(2)}%
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}

export default function TefasComparePage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();

  function parseUrlCodes() {
    const raw = searchParams.get('codes');
    if (!raw) return [
      { key: 'AAK', label: 'AAK' },
      { key: 'AAL', label: 'AAL' },
    ];
    return raw.split(',')
      .map(s => s.trim().toUpperCase())
      .filter(s => s.length > 0)
      .slice(0, 7)
      .map(key => ({ key, label: key }));
  }

  const [funds, setFunds]           = useState(parseUrlCodes);
  const [range, setRange]           = useState('1Y');
  const [search, setSearch]         = useState('');
  const [showAdd, setShowAdd]       = useState(false);
  const [seriesData, setSeriesData] = useState({});
  const [detailData, setDetailData] = useState({}); // fon detayları (scrape getirileri için)
  const [loading, setLoading]       = useState(false);
  const [loaded, setLoaded]         = useState(false);
  const [investment, setInvestment] = useState('1000');

  function syncUrl(newFunds) {
    const codes = newFunds.map(f => f.key).join(',');
    navigate(`/market/tefas/compare?codes=${codes}`, { replace: true });
  }

  function addFund() {
    const code = search.trim().toUpperCase();
    if (!code || funds.find(f => f.key === code) || funds.length >= 7) return;
    const updated = [...funds, { key: code, label: code }];
    setFunds(updated);
    syncUrl(updated);
    setSearch('');
    setShowAdd(false);
    setLoaded(false);
  }

  function removeFund(key) {
    const updated = funds.filter(f => f.key !== key);
    setFunds(updated);
    syncUrl(updated);
    setSeriesData(prev => { const n = { ...prev }; delete n[key]; return n; });
    setDetailData(prev => { const n = { ...prev }; delete n[key]; return n; });
  }

  const loadData = useCallback(async () => {
    if (funds.length === 0) return;
    setLoading(true);
    setLoaded(false);
    const results = {};
    const details = {};
    await Promise.all(funds.map(async fund => {
      try {
        const [histData, detData] = await Promise.all([
          getTefasFundHistory(fund.key, range),
          getTefasFundDetail(fund.key),
        ]);
        results[fund.key] = histData?.points ?? [];
        details[fund.key] = detData;
      } catch {
        results[fund.key] = [];
      }
    }));
    setSeriesData(results);
    setDetailData(details);
    setLoading(false);
    setLoaded(true);
  }, [funds, range]);

  // Rebased chart data
  function buildChartData() {
    if (!loaded) return [];
    const allDates = new Set();
    funds.forEach(f => (seriesData[f.key] ?? []).forEach(p => allDates.add(p.date)));
    const sortedDates = [...allDates].sort();

    const firstValues = {};
    funds.forEach(f => {
      const pts = seriesData[f.key] ?? [];
      if (pts.length > 0) firstValues[f.key] = parseFloat(pts[0].price);
    });

    return sortedDates.map(date => {
      const row = { date };
      funds.forEach(f => {
        const pts = seriesData[f.key] ?? [];
        const pt = pts.find(p => p.date === date);
        const first = firstValues[f.key];
        if (pt && first && first !== 0) {
          row[f.key] = parseFloat(((parseFloat(pt.price) - first) / first * 100).toFixed(2));
        }
      });
      return row;
    });
  }

  const chartData = buildChartData();

  // Scrape'den gelen dönem getirisi — range'e göre
  function getScrapedReturn(key) {
    const detail = detailData[key];
    const map = {
      '1M': detail?.return1M,
      '3M': detail?.return3M,
      '6M': detail?.return6M,
      '1Y': detail?.return1Y,
      // 5Y için scrape yok, null döner → fiyat hesabına düşer
    };
    return map[range] ?? null;
  }

  // Metrikler
  function getMetrics(key) {
    const raw = seriesData[key] ?? [];
    if (raw.length < 2) return null;
    const pts = [...raw].sort((a, b) => a.date.localeCompare(b.date));
    const prices = pts.map(p => parseFloat(p.price));
    const startPrice = prices[0];
    const endPrice   = prices[prices.length - 1];

    // Dönem getirisi — önce scrape'den gelen resmi değeri kullan
    const scrapedReturn = getScrapedReturn(key);
    const periodReturn = scrapedReturn != null
      ? scrapedReturn
      : ((endPrice - startPrice) / startPrice) * 100;

    // Drawdown
    let peak = prices[0], maxDrawdown = 0;
    for (const p of prices) {
      if (p > peak) peak = p;
      const dd = (p - peak) / peak;
      if (dd < maxDrawdown) maxDrawdown = dd;
    }

    // Volatilite
    const returns = [];
    for (let i = 1; i < prices.length; i++) {
      if (prices[i - 1] > 0) returns.push((prices[i] - prices[i - 1]) / prices[i - 1] * 100);
    }
    const mean = returns.reduce((a, b) => a + b, 0) / (returns.length || 1);
    const variance = returns.reduce((a, b) => a + (b - mean) ** 2, 0) / (returns.length || 1);

    return {
      startPrice, endPrice,
      maxPrice: Math.max(...prices),
      minPrice: Math.min(...prices),
      periodReturn,
      drawdown: maxDrawdown * 100,
      volatility: Math.sqrt(variance),
    };
  }

  // Risk başına getiri: periodReturn / volatility
  function getRiskAdjustedReturn(key) {
    const m = getMetrics(key);
    if (!m || m.volatility === 0) return null;
    return m.periodReturn / m.volatility;
  }

  // Risk değeri etiketi
  function riskLabel(rv) {
    if (rv == null) return { text: '-', color: 'text-gray-400' };
    if (rv <= 2) return { text: `${rv} — Düşük`, color: 'text-emerald-600' };
    if (rv <= 4) return { text: `${rv} — Orta`, color: 'text-amber-500' };
    return { text: `${rv} — Yüksek`, color: 'text-rose-600' };
  }

  function simulate(key, inv) {
    const m = getMetrics(key);
    if (!m || !inv || isNaN(inv) || m.startPrice === 0) return null;
    return (inv / m.startPrice) * m.endPrice;
  }

  function formatDate(d) {
    if (!d) return '';
    const parts = d.split('-');
    if (range === '1M') return `${parts[2]}/${parts[1]}`;
    return `${parts[1]}/${parts[0]?.slice(2)}`;
  }

  const fmt4 = v => v == null ? '-' : parseFloat(v).toLocaleString('tr-TR', { minimumFractionDigits: 4, maximumFractionDigits: 4 });
  const fmt2 = v => v == null ? '-' : parseFloat(v).toLocaleString('tr-TR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

  const topPerformer = loaded
    ? funds.map(f => ({ ...f, ret: getMetrics(f.key)?.periodReturn ?? -Infinity }))
           .sort((a, b) => b.ret - a.ret)[0]
    : null;

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-900 border-l-4 border-[#093eaa] pl-4">
          Fon Karşılaştırma
        </h1>
        <p className="text-sm text-gray-500 mt-1 pl-5">
          Birden fazla TEFAS fonunu aynı grafikte karşılaştır — başlangıca göre yüzde değişim
        </p>
      </div>

      {/* Fon seçici */}
      <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-5">
        <p className="text-xs font-bold text-gray-500 uppercase tracking-wider mb-3">Fon Ekle (Fon Kodu)</p>
        <div className="flex flex-wrap gap-2 mb-3">
          {funds.map((f, idx) => (
            <span key={f.key}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-sm font-semibold text-white"
              style={{ background: COLORS[idx % COLORS.length] }}>
              {f.label}
              <button onClick={() => removeFund(f.key)} className="ml-1 hover:opacity-70">
                <X className="w-3.5 h-3.5" />
              </button>
            </span>
          ))}
          {funds.length < 7 && (
            <div className="relative">
              {showAdd ? (
                <div className="flex items-center gap-2">
                  <input
                    autoFocus
                    value={search}
                    onChange={e => setSearch(e.target.value.toUpperCase())}
                    onKeyDown={e => e.key === 'Enter' && addFund()}
                    placeholder="Fon kodu (AAK...)"
                    className="px-3 py-1.5 border border-gray-200 rounded-full text-sm focus:outline-none focus:ring-2 focus:ring-[#093eaa]/30 w-36"
                  />
                  <button onClick={addFund}
                    className="px-3 py-1.5 bg-[#093eaa] text-white rounded-full text-xs font-bold hover:bg-[#0730a0]">
                    Ekle
                  </button>
                  <button onClick={() => { setShowAdd(false); setSearch(''); }}
                    className="text-gray-400 hover:text-gray-600 text-xs">İptal</button>
                </div>
              ) : (
                <button onClick={() => setShowAdd(true)}
                  className="flex items-center gap-1.5 px-3 py-1.5 rounded-full border-2 border-dashed border-gray-300 text-sm text-gray-400 hover:border-[#093eaa] hover:text-[#093eaa] transition-colors">
                  <Plus className="w-3.5 h-3.5" /> Ekle
                </button>
              )}
            </div>
          )}
        </div>

        <div className="flex items-center gap-3 flex-wrap">
          <div className="flex gap-1">
            {RANGES.map(r => (
              <button key={r} onClick={() => { setRange(r); setLoaded(false); }}
                className={`px-3 py-1.5 rounded-lg text-xs font-bold transition-all ${
                  range === r ? 'bg-[#093eaa] text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
                }`}>
                {RANGE_LABELS[r]}
              </button>
            ))}
          </div>
          <button onClick={loadData} disabled={loading || funds.length === 0}
            className="ml-auto px-5 py-2 bg-[#093eaa] text-white rounded-xl text-sm font-bold hover:bg-[#0730a0] transition-all disabled:opacity-50 flex items-center gap-2">
            <BarChart2 className="w-4 h-4" />
            {loading ? 'Yükleniyor...' : 'Karşılaştır'}
          </button>
        </div>
      </div>

      {/* Grafik */}
      {loaded && chartData.length > 0 && (
        <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">
          <div className="mb-4">
            <h2 className="font-bold text-gray-900">Göreceli Performans (%)</h2>
            <p className="text-xs text-gray-400 mt-0.5">Dönem başına göre yüzde değişim (rebased)</p>
            {/* Her fon için dönem getirisi — scrape'den gelen resmi değer */}
            <div className="flex flex-wrap gap-x-5 gap-y-1 mt-2">
              {funds.map((f, idx) => {
                const scraped = getScrapedReturn(f.key);
                const m = getMetrics(f.key);
                const ret = scraped ?? m?.periodReturn;
                if (ret == null) return null;
                const pos = ret >= 0;
                return (
                  <span key={f.key} className={`text-xs font-semibold flex items-center gap-1 ${pos ? 'text-emerald-600' : 'text-rose-600'}`}>
                    <span className="w-2 h-2 rounded-full flex-shrink-0" style={{ background: COLORS[idx % COLORS.length] }} />
                    {f.label}: {pos ? '+' : ''}{ret.toFixed(4)}%
                    {scraped != null && <span className="text-gray-400 font-normal">(TEFAS)</span>}
                  </span>
                );
              })}
            </div>
          </div>
          <div className="h-80">
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={chartData} margin={{ top: 5, right: 10, left: 10, bottom: 5 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
                <XAxis dataKey="date" tick={{ fontSize: 10, fill: '#9ca3af' }}
                  tickFormatter={formatDate} interval="preserveStartEnd" />
                <YAxis tick={{ fontSize: 10, fill: '#9ca3af' }}
                  tickFormatter={v => `${v >= 0 ? '+' : ''}${v.toFixed(1)}%`} width={60} />
                <Tooltip content={<CompareTooltip />} />
                <Legend formatter={value => funds.find(f => f.key === value)?.label ?? value} />
                {funds.map((f, idx) => (
                  <Line key={f.key} type="monotone" dataKey={f.key} name={f.key}
                    stroke={COLORS[idx % COLORS.length]} strokeWidth={2}
                    dot={false} activeDot={{ r: 4 }} connectNulls />
                ))}
              </LineChart>
            </ResponsiveContainer>
          </div>
        </div>
      )}

      {/* Performans Metrikleri */}
      {loaded && (
        <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden">
          <div className="px-6 py-4 border-b border-gray-100 flex items-center justify-between flex-wrap gap-3">
            <h2 className="font-bold text-gray-900">Performans Metrikleri</h2>
            {topPerformer && (
              <div className="flex items-center gap-2 text-sm">
                <span className="text-gray-500">En iyi:</span>
                <span className="font-bold text-emerald-600 flex items-center gap-1">
                  <TrendingUp className="w-4 h-4" />
                  {topPerformer.label} ({topPerformer.ret >= 0 ? '+' : ''}{topPerformer.ret.toFixed(2)}%)
                </span>
              </div>
            )}
          </div>
          <div className="overflow-x-auto">
            <table className="w-full min-w-[1000px]">
              <thead className="bg-gray-50">
                <tr>
                  {['Fon', 'Başlangıç', 'Bitiş', 'Dönem Max', 'Dönem Min', 'Dönem Getirisi', 'Drawdown', 'Volatilite', 'Risk Başına Getiri'].map(h => (
                    <th key={h} className="text-left px-4 py-3 text-xs font-bold text-gray-500 uppercase tracking-wider border-b border-gray-200 whitespace-nowrap">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {funds.map((f, idx) => {
                  const m = getMetrics(f.key);
                  const pos = (m?.periodReturn ?? 0) >= 0;
                  const rar = getRiskAdjustedReturn(f.key);
                  const rarPos = (rar ?? 0) >= 0;
                  return (
                    <tr key={f.key} className="border-t border-gray-100 hover:bg-gray-50 transition-colors">
                      <td className="px-4 py-3">
                        <div className="flex items-center gap-2">
                          <span className="w-3 h-3 rounded-full flex-shrink-0" style={{ background: COLORS[idx % COLORS.length] }} />
                          <span className="font-bold text-sm text-[#093eaa]">{f.label}</span>
                        </div>
                      </td>
                      <td className="px-4 py-3 text-sm font-mono text-gray-700">{m ? fmt4(m.startPrice) : '-'}</td>
                      <td className="px-4 py-3 text-sm font-mono text-gray-700">{m ? fmt4(m.endPrice) : '-'}</td>
                      <td className="px-4 py-3 text-sm font-semibold text-emerald-600 font-mono">{m ? fmt4(m.maxPrice) : '-'}</td>
                      <td className="px-4 py-3 text-sm font-semibold text-rose-600 font-mono">{m ? fmt4(m.minPrice) : '-'}</td>
                      <td className="px-4 py-3">
                        {m ? (
                          <span className={`flex items-center gap-1 text-sm font-bold ${pos ? 'text-emerald-600' : 'text-rose-600'}`}>
                            {pos ? <TrendingUp className="w-3.5 h-3.5" /> : <TrendingDown className="w-3.5 h-3.5" />}
                            {pos ? '+' : ''}{fmt2(m.periodReturn)}%
                          </span>
                        ) : '-'}
                      </td>
                      <td className="px-4 py-3 text-sm font-semibold text-rose-600">{m ? `${fmt2(m.drawdown)}%` : '-'}</td>
                      <td className="px-4 py-3 text-sm text-gray-600">{m ? `${fmt2(m.volatility)}%` : '-'}</td>
                      <td className="px-4 py-3">
                        {rar != null ? (
                          <span className={`text-sm font-bold ${rarPos ? 'text-emerald-600' : 'text-rose-600'}`}>
                            {rarPos ? '+' : ''}{rar.toFixed(2)}
                          </span>
                        ) : '-'}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* TL Yatırım Simülasyonu */}
      {loaded && (
        <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden">
          <div className="px-6 py-4 border-b border-gray-100 flex items-center gap-4 flex-wrap">
            <h2 className="font-bold text-gray-900">TL Yatırım Simülasyonu</h2>
            <div className="flex items-center gap-2 ml-auto">
              <span className="text-sm text-gray-500">Yatırım tutarı:</span>
              <input type="number" value={investment} onChange={e => setInvestment(e.target.value)}
                className="w-32 px-3 py-1.5 border border-gray-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-[#093eaa]/30 text-right"
                placeholder="1000" />
              <span className="text-sm font-semibold text-gray-600">₺</span>
            </div>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead className="bg-gray-50">
                <tr>
                  {['Fon', 'Başlangıç Fiyatı', 'Alınan Pay', 'Bitiş Fiyatı', 'Nihai Değer (₺)', 'Kâr/Zarar'].map(h => (
                    <th key={h} className="text-left px-5 py-3 text-xs font-bold text-gray-500 uppercase tracking-wider border-b border-gray-200 whitespace-nowrap">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {funds.map((f, idx) => {
                  const m = getMetrics(f.key);
                  const inv = parseFloat(investment || 0);
                  const finalVal = simulate(f.key, inv);
                  const profit = finalVal != null ? finalVal - inv : null;
                  const pos = (profit ?? 0) >= 0;
                  const units = m && m.startPrice > 0 ? inv / m.startPrice : null;
                  return (
                    <tr key={f.key} className="border-t border-gray-100 hover:bg-gray-50 transition-colors">
                      <td className="px-5 py-3">
                        <div className="flex items-center gap-2">
                          <span className="w-3 h-3 rounded-full flex-shrink-0" style={{ background: COLORS[idx % COLORS.length] }} />
                          <span className="font-bold text-sm text-[#093eaa]">{f.label}</span>
                        </div>
                      </td>
                      <td className="px-5 py-3 text-sm font-mono text-gray-700">{m ? fmt4(m.startPrice) : '-'}</td>
                      <td className="px-5 py-3 text-sm font-mono text-gray-700">
                        {units != null ? units.toLocaleString('tr-TR', { minimumFractionDigits: 4, maximumFractionDigits: 4 }) : '-'}
                      </td>
                      <td className="px-5 py-3 text-sm font-mono text-gray-700">{m ? fmt4(m.endPrice) : '-'}</td>
                      <td className="px-5 py-3 text-sm font-bold text-gray-900">
                        {finalVal != null ? `₺${finalVal.toLocaleString('tr-TR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}` : '-'}
                      </td>
                      <td className="px-5 py-3">
                        {profit != null ? (
                          <span className={`text-sm font-bold ${pos ? 'text-emerald-600' : 'text-rose-600'}`}>
                            {pos ? '+' : ''}₺{profit.toLocaleString('tr-TR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                          </span>
                        ) : '-'}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
          <div className="px-5 py-2 bg-gray-50 border-t border-gray-100 text-xs text-gray-400">
            * Simülasyon dönem başı ve sonu TEFAS fiyatları kullanılarak hesaplanmıştır. Gösterge niteliğindedir.
          </div>
        </div>
      )}

      {/* Fon Bilgileri */}
      {loaded && (
        <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden">
          <div className="px-6 py-4 border-b border-gray-100">
            <h2 className="font-bold text-gray-900">Fon Bilgileri</h2>
            <p className="text-xs text-gray-400 mt-0.5">Fon büyüklüğü ve yatırımcı sayısı</p>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead className="bg-gray-50">
                <tr>
                  {['Fon', 'Fon Adı', 'Fon Toplam Değer (₺)', 'Yatırımcı Sayısı'].map(h => (
                    <th key={h} className="text-left px-5 py-3 text-xs font-bold text-gray-500 uppercase tracking-wider border-b border-gray-200 whitespace-nowrap">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {funds.map((f, idx) => {
                  const d = detailData[f.key];
                  const mc = d?.marketCap != null
                    ? parseFloat(d.marketCap).toLocaleString('tr-TR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
                    : '-';
                  const inv = d?.numberOfInvestors != null
                    ? Number(d.numberOfInvestors).toLocaleString('tr-TR')
                    : '-';
                  return (
                    <tr key={f.key} className="border-t border-gray-100 hover:bg-gray-50 transition-colors">
                      <td className="px-5 py-3">
                        <div className="flex items-center gap-2">
                          <span className="w-3 h-3 rounded-full flex-shrink-0" style={{ background: COLORS[idx % COLORS.length] }} />
                          <span className="font-bold text-sm text-[#093eaa]">{f.label}</span>
                        </div>
                      </td>
                      <td className="px-5 py-3 text-sm text-gray-700 max-w-xs truncate">{d?.title ?? '-'}</td>
                      <td className="px-5 py-3 text-sm font-semibold text-gray-900">{mc !== '-' ? `₺${mc}` : '-'}</td>
                      <td className="px-5 py-3 text-sm text-gray-700">{inv}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {!loaded && !loading && (
        <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-12 text-center">
          <BarChart2 className="w-12 h-12 text-gray-300 mx-auto mb-3" />
          <p className="text-gray-500 font-semibold">Fon kodlarını gir ve "Karşılaştır" butonuna tıkla</p>
          <p className="text-gray-400 text-sm mt-1">En fazla 7 fon karşılaştırabilirsin</p>
        </div>
      )}
    </div>
  );
}
