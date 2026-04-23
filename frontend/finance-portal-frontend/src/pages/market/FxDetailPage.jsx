import { useEffect, useState, useCallback } from 'react';
import { useParams, Link } from 'react-router-dom';
import { ArrowLeft, RefreshCw, TrendingUp, TrendingDown, Star, ArrowLeftRight, Newspaper, BarChart2 } from 'lucide-react';
import {
  AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
} from 'recharts';
import { getFxHistory, getFxTcmb } from '../../api/marketApi';
import { getBloombergHtNews } from '../../api/newsApi';
import { FX_META, FlagImg } from '../../utils/fxMeta.jsx';

// ── Helpers ───────────────────────────────────────────────────────────────────
function fmt(v, dec = 4) {
  if (v == null) return '-';
  return parseFloat(v).toLocaleString('tr-TR', { minimumFractionDigits: dec, maximumFractionDigits: dec });
}

const RANGES = ['1W', '1M', '3M', '6M', '1Y', 'ALL'];
const RANGE_LABELS = { '1W': '1H', '1M': '1A', '3M': '3A', '6M': '6A', '1Y': '1Y', 'ALL': 'Tümü' };

// ── Custom Tooltip ────────────────────────────────────────────────────────────
function ChartTooltip({ active, payload, label }) {
  if (!active || !payload?.length) return null;
  const val = payload[0]?.value;
  return (
    <div className="bg-white border border-gray-200 rounded-xl shadow-lg px-4 py-3 text-sm min-w-[160px]">
      <p className="text-gray-500 text-xs font-semibold mb-1.5 border-b border-gray-100 pb-1.5">{label}</p>
      <div className="flex justify-between gap-4">
        <span className="text-gray-500 text-xs">Değer:</span>
        <span className="font-bold text-gray-900 text-xs">
          {val != null ? parseFloat(val).toLocaleString('tr-TR', { minimumFractionDigits: 4, maximumFractionDigits: 4 }) : '-'}
        </span>
      </div>
    </div>
  );
}

// ── Döviz Çevirici ────────────────────────────────────────────────────────────
function FxConverter({ symbol, allRates }) {
  const sym = symbol?.toUpperCase();
  // Tüm dövizler + TRY
  const currencies = ['TRY', ...Object.keys(FX_META).filter(k => k !== 'TRY')];

  const [fromCur, setFromCur] = useState(sym ?? 'USD');
  const [toCur, setToCur]     = useState('TRY');
  const [amount, setAmount]   = useState('1');

  // Kur map: symbol → sell fiyatı (TRY bazlı)
  // TRY → TRY = 1
  function getRateTry(cur) {
    if (cur === 'TRY') return 1;
    const r = allRates.find(x => x.symbol === cur);
    return r?.sell ? parseFloat(r.sell) : null;
  }

  function calculate() {
    const a = parseFloat(amount || 0);
    if (!a || isNaN(a)) return '-';
    const fromRate = getRateTry(fromCur); // 1 fromCur = ? TRY
    const toRate   = getRateTry(toCur);   // 1 toCur   = ? TRY
    if (!fromRate || !toRate) return '-';
    const result = (a * fromRate) / toRate;
    return result.toLocaleString('tr-TR', { minimumFractionDigits: 4, maximumFractionDigits: 6 });
  }

  function swap() {
    setFromCur(toCur);
    setToCur(fromCur);
  }

  return (
    <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">
      <h2 className="font-bold text-gray-900 mb-4 flex items-center gap-2">
        <ArrowLeftRight className="w-5 h-5 text-[#093eaa]" /> Döviz Çevirici
      </h2>
      <div className="space-y-3">
        {/* Kaynak */}
        <div>
          <label className="block text-xs font-bold text-gray-500 mb-1.5">KAYNAK</label>
          <div className="flex gap-2">
            <select
              value={fromCur}
              onChange={e => setFromCur(e.target.value)}
              className="flex-1 px-3 py-2.5 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-[#093eaa]/30 bg-white"
            >
              {currencies.map(c => (
                <option key={c} value={c}>{c} — {FX_META[c]?.ad ?? c}</option>
              ))}
            </select>
            <input
              type="number"
              value={amount}
              onChange={e => setAmount(e.target.value)}
              placeholder="1"
              className="w-28 px-3 py-2.5 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-[#093eaa]/30 text-right"
            />
          </div>
        </div>

        {/* Swap butonu */}
        <div className="flex justify-center">
          <button
            onClick={swap}
            className="p-2 rounded-full bg-gray-100 hover:bg-[#093eaa] hover:text-white transition-all group"
            title="Ters çevir"
          >
            <ArrowLeftRight className="w-4 h-4 text-gray-500 group-hover:text-white" />
          </button>
        </div>

        {/* Hedef */}
        <div>
          <label className="block text-xs font-bold text-gray-500 mb-1.5">HEDEF</label>
          <select
            value={toCur}
            onChange={e => setToCur(e.target.value)}
            className="w-full px-3 py-2.5 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-[#093eaa]/30 bg-white"
          >
            {currencies.map(c => (
              <option key={c} value={c}>{c} — {FX_META[c]?.ad ?? c}</option>
            ))}
          </select>
        </div>

        {/* Sonuç */}
        <div className="bg-[#093eaa] rounded-xl p-4 text-center">
          <p className="text-white/70 text-xs mb-1">
            {amount || '0'} {fromCur} =
          </p>
          <p className="text-white text-2xl font-black">{calculate()}</p>
          <p className="text-white/70 text-xs mt-1">{toCur}</p>
        </div>

        {/* Kur bilgisi */}
        {getRateTry(fromCur) && getRateTry(toCur) && (
          <p className="text-xs text-gray-400 text-center">
            1 {fromCur} = {((getRateTry(fromCur) ?? 1) / (getRateTry(toCur) ?? 1)).toLocaleString('tr-TR', { minimumFractionDigits: 4, maximumFractionDigits: 6 })} {toCur}
          </p>
        )}
        <p className="text-xs text-gray-400 text-center">TCMB resmi kurları kullanılmaktadır.</p>
      </div>
    </div>
  );
}

// ── İlgili Haberler ───────────────────────────────────────────────────────────
function FxNews({ symbol }) {
  const [news, setNews] = useState([]);
  const [label, setLabel] = useState('İlgili Haberler');
  const meta = FX_META[symbol?.toUpperCase()];

  useEffect(() => {
    getBloombergHtNews()
      .then(items => {
        const keywords = [
          symbol?.toLowerCase(),
          meta?.ad?.toLowerCase(),
          'döviz', 'kur', 'merkez bankası', 'tcmb', 'faiz',
        ].filter(Boolean);

        const filtered = items.filter(item => {
          const text = ((item.title ?? '') + ' ' + (item.description ?? '')).toLowerCase();
          return keywords.some(k => text.includes(k));
        });

        if (filtered.length >= 3) {
          setNews(filtered.slice(0, 6));
          setLabel(`${symbol?.toUpperCase()} Haberleri`);
        } else {
          setNews(items.slice(0, 6));
          setLabel('Son Haberler');
        }
      })
      .catch(() => {});
  }, [symbol]);

  return (
    <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">
      <h2 className="font-bold text-gray-900 mb-4 flex items-center gap-2">
        <Newspaper className="w-5 h-5 text-[#093eaa]" /> {label}
      </h2>
      {news.length === 0 ? (
        <p className="text-gray-400 text-sm">Haberler yükleniyor...</p>
      ) : (
        <div className="space-y-4">
          {news.map((item, i) => (
            <a
              key={i}
              href={item.url}
              target="_blank"
              rel="noopener noreferrer"
              className="flex gap-3 group hover:bg-gray-50 rounded-xl p-2 -mx-2 transition-colors"
            >
              {item.imageUrl && (
                <img src={item.imageUrl} alt="" className="w-16 h-16 object-cover rounded-lg flex-shrink-0" />
              )}
              <div className="flex-1 min-w-0">
                <p className="text-sm font-semibold text-gray-800 group-hover:text-[#093eaa] transition-colors line-clamp-2 leading-snug">
                  {item.title}
                </p>
                <p className="text-xs text-gray-400 mt-1">
                  {item.source && <span className="font-semibold">{item.source} · </span>}
                  {item.publishedAt ? new Date(item.publishedAt).toLocaleDateString('tr-TR') : ''}
                </p>
              </div>
            </a>
          ))}
        </div>
      )}
    </div>
  );
}

// ── Main Page ─────────────────────────────────────────────────────────────────
export default function FxDetailPage() {
  const { symbol } = useParams();
  const sym = symbol?.toUpperCase();

  const [history, setHistory]       = useState(null);
  const [loading, setLoading]       = useState(true);
  const [loadingChart, setLoadingChart] = useState(false);
  const [range, setRange]           = useState('1M');
  const [currentRate, setCurrentRate] = useState(null);
  const [allRates, setAllRates]     = useState([]);

  // Watchlist — localStorage
  const [inWatchlist, setInWatchlist] = useState(() => {
    try {
      const wl = JSON.parse(localStorage.getItem('fx_watchlist') ?? '[]');
      return wl.includes(sym);
    } catch { return false; }
  });

  function toggleWatchlist() {
    try {
      const wl = JSON.parse(localStorage.getItem('fx_watchlist') ?? '[]');
      const updated = inWatchlist ? wl.filter(x => x !== sym) : [...wl, sym];
      localStorage.setItem('fx_watchlist', JSON.stringify(updated));
      setInWatchlist(!inWatchlist);
    } catch {}
  }

  // TCMB rates
  useEffect(() => {
    getFxTcmb()
      .then(data => {
        const rates = data?.rates ?? [];
        setAllRates(rates);
        const found = rates.find(r => r.symbol === sym);
        setCurrentRate(found ?? null);
      })
      .catch(() => {});
  }, [sym]);

  // History
  const loadHistory = useCallback(() => {
    setLoadingChart(true);
    getFxHistory(symbol, range)
      .then(data => { setHistory(data); setLoading(false); })
      .catch(() => { setHistory(null); setLoading(false); })
      .finally(() => setLoadingChart(false));
  }, [symbol, range]);

  useEffect(() => { setLoading(true); loadHistory(); }, [loadHistory]);

  // Chart
  const chartPoints = history?.points?.map(p => ({ date: p.date, value: parseFloat(p.close) })) ?? [];
  const isUp = chartPoints.length > 1 && chartPoints[chartPoints.length - 1].value >= chartPoints[0].value;
  const strokeColor = isUp ? '#10b981' : '#ef4444';
  const chartMin = chartPoints.length > 0 ? Math.min(...chartPoints.map(p => p.value)) * 0.998 : 0;
  const chartMax = chartPoints.length > 0 ? Math.max(...chartPoints.map(p => p.value)) * 1.002 : 0;

  function formatDate(d) {
    if (!d) return '';
    const parts = d.split('-');
    if (range === '1W' || range === '1M') return `${parts[2]}/${parts[1]}`;
    return `${parts[1]}/${parts[0]?.slice(2)}`;
  }

  const buy  = currentRate?.buy;
  const sell = currentRate?.sell;
  const unit = currentRate?.unit;

  const firstVal = chartPoints.length > 1 ? chartPoints[0].value : null;
  const lastVal  = chartPoints.length > 1 ? chartPoints[chartPoints.length - 1].value : null;
  const change   = firstVal && lastVal ? lastVal - firstVal : null;
  const changePct = firstVal && change != null ? (change / firstVal) * 100 : null;
  const changePositive = (change ?? 0) >= 0;

  return (
    <div className="space-y-6">
      {/* Back */}
      <div className="flex items-center justify-between">
        <Link to="/market/fx" className="inline-flex items-center gap-1.5 text-sm text-[#093eaa] font-semibold hover:underline">
          <ArrowLeft className="w-4 h-4" /> Döviz Kurları
        </Link>
        {/* Watchlist + Karşılaştır butonları */}
        <button
          onClick={toggleWatchlist}
          className={`inline-flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-semibold border transition-all ${
            inWatchlist
              ? 'bg-amber-50 border-amber-300 text-amber-700 hover:bg-amber-100'
              : 'bg-white border-gray-200 text-gray-600 hover:bg-gray-50'
          }`}
        >
          <Star className={`w-4 h-4 ${inWatchlist ? 'fill-amber-400 text-amber-400' : ''}`} />
          {inWatchlist ? 'İzleme Listesinde' : 'İzleme Listesine Ekle'}
        </button>
        <Link
          to={`/market/compare?symbols=${sym}`}
          className="inline-flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-semibold border border-[#093eaa] text-[#093eaa] bg-white hover:bg-blue-50 transition-all"
        >
          <BarChart2 className="w-4 h-4" />
          Karşılaştır
        </Link>
      </div>

      {/* Main card */}
      <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden">
        <div className="p-6">
          {/* Header */}
          <div className="flex items-start justify-between flex-wrap gap-4 mb-6">
            <div>
              <div className="flex items-center gap-3 mb-1">
                <FlagImg cc={FX_META[sym]?.cc} size={40} />
                <div>
                  <h1 className="text-2xl font-black text-gray-900 leading-tight">{sym}/TRY</h1>
                  <p className="text-sm text-gray-500">{FX_META[sym]?.ad ?? sym}</p>
                </div>
              </div>
              {loading ? (
                <div className="flex items-center gap-1.5 py-2">
                  <div className="w-2 h-2 bg-[#093eaa] rounded-full animate-bounce" />
                  <div className="w-2 h-2 bg-[#093eaa]/60 rounded-full animate-bounce [animation-delay:100ms]" />
                  <div className="w-2 h-2 bg-[#093eaa]/30 rounded-full animate-bounce [animation-delay:200ms]" />
                </div>
              ) : (
                <div className="flex items-baseline gap-3 flex-wrap mt-2">
                  {sell != null && (
                    <span className={`text-3xl font-black ${changePositive ? 'text-emerald-600' : 'text-rose-600'}`}>
                      {fmt(sell)}
                    </span>
                  )}
                  {change != null && (
                    <span className={`flex items-center gap-1 text-sm font-semibold ${changePositive ? 'text-emerald-600' : 'text-rose-600'}`}>
                      {changePositive ? <TrendingUp className="w-4 h-4" /> : <TrendingDown className="w-4 h-4" />}
                      {changePositive ? '+' : ''}{fmt(change)}
                      {' '}({changePositive ? '+' : ''}{fmt(changePct, 2)}%)
                    </span>
                  )}
                </div>
              )}
            </div>
            <button onClick={loadHistory} className="p-2 rounded-xl bg-gray-100 hover:bg-gray-200 transition-all" title="Yenile">
              <RefreshCw className={`w-4 h-4 text-gray-500 ${loadingChart ? 'animate-spin' : ''}`} />
            </button>
          </div>

          {/* Range buttons */}
          <div className="flex gap-1 mb-5 flex-wrap">
            {RANGES.map(r => (
              <button key={r} onClick={() => setRange(r)}
                className={`px-3 py-1.5 rounded-lg text-xs font-bold transition-all ${
                  range === r ? 'bg-[#093eaa] text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
                }`}>
                {RANGE_LABELS[r]}
              </button>
            ))}
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
            {!loading && chartPoints.length > 0 ? (
              <ResponsiveContainer width="100%" height="100%">
                <AreaChart data={chartPoints} margin={{ top: 5, right: 10, left: 10, bottom: 5 }}>
                  <defs>
                    <linearGradient id="fxGrad" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%"  stopColor={strokeColor} stopOpacity={0.15} />
                      <stop offset="95%" stopColor={strokeColor} stopOpacity={0} />
                    </linearGradient>
                  </defs>
                  <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
                  <XAxis dataKey="date" tick={{ fontSize: 10, fill: '#9ca3af' }} tickFormatter={formatDate} interval="preserveStartEnd" />
                  <YAxis domain={[chartMin, chartMax]} tick={{ fontSize: 10, fill: '#9ca3af' }}
                    tickFormatter={v => parseFloat(v).toLocaleString('tr-TR', { minimumFractionDigits: 4, maximumFractionDigits: 4 })}
                    width={80} />
                  <Tooltip content={<ChartTooltip />} />
                  <Area type="monotone" dataKey="value" stroke={strokeColor} strokeWidth={1.5}
                    fill="url(#fxGrad)" dot={false} activeDot={{ r: 4 }} />
                </AreaChart>
              </ResponsiveContainer>
            ) : !loading && (
              <div className="flex items-center justify-center h-full text-gray-400 text-sm">
                Grafik verisi yüklenemedi.
              </div>
            )}
          </div>
        </div>

        {/* Info cards */}
        {currentRate && (
          <div className="border-t border-gray-100 px-6 py-5 grid grid-cols-3 gap-4">
            {[
              { label: 'Alış',   value: fmt(buy) },
              { label: 'Satış',  value: fmt(sell) },
              { label: 'Birim',  value: unit ?? '-' },
            ].map(item => (
              <div key={item.label} className="bg-gray-50 rounded-xl p-4 text-center">
                <p className="text-xs text-gray-500 font-semibold mb-1">{item.label}</p>
                <p className="text-sm font-bold text-gray-900">{item.value}</p>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Çevirici + Haberler */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <FxConverter symbol={sym} allRates={allRates} />
        <FxNews symbol={sym} />
      </div>
    </div>
  );
}
