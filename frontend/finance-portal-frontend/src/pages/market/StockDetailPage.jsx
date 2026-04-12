import { useEffect, useState, useCallback } from 'react';
import { useParams, Link } from 'react-router-dom';
import { ArrowLeft, TrendingUp, TrendingDown } from 'lucide-react';
import {
  AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip,
  ResponsiveContainer
} from 'recharts';
import { getStockMidasDetail, getStockChart } from '../../api/marketApi';

const RANGES = [
  { label: '1G', range: '1d',  interval: '5m'  },
  { label: '1H', range: '5d',  interval: '1h'  },
  { label: '1A', range: '1mo', interval: '1d'  },
  { label: '3A', range: '3mo', interval: '1d'  },
  { label: '1Y', range: '1y',  interval: '1wk' },
  { label: '5Y', range: '5y',  interval: '1mo' },
];

/* ─── Custom Tooltip ─── */
function ChartTooltip({ active, payload, label }) {
  if (!active || !payload?.length) return null;
  return (
    <div className="bg-[#093eaa] text-white text-xs px-3 py-2 rounded-lg shadow-lg pointer-events-none">
      <p className="font-bold">₺{parseFloat(payload[0].value).toFixed(2)}</p>
      <p className="opacity-80 mt-0.5">{label}</p>
    </div>
  );
}

/* ─── Interactive Chart ─── */
function StockChart({ timestamps, prices }) {
  if (!timestamps?.length || !prices?.length) {
    return (
      <div className="h-64 flex items-center justify-center text-gray-300 text-sm bg-gray-50 rounded-xl">
        Grafik verisi yok
      </div>
    );
  }

  const data = timestamps
    .map((t, i) => {
      if (prices[i] == null) return null;
      const d = new Date(t * 1000);
      const label = d.toLocaleString('tr-TR', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' });
      return { time: label, price: parseFloat(prices[i]) };
    })
    .filter(Boolean);

  const vals = data.map(d => d.price);
  const minVal = Math.min(...vals) * 0.9995;
  const maxVal = Math.max(...vals) * 1.0005;
  const isPos = vals[vals.length - 1] >= vals[0];
  const color = isPos ? '#10b981' : '#ef4444';

  return (
    <ResponsiveContainer width="100%" height={280}>
      <AreaChart data={data} margin={{ top: 10, right: 20, left: 0, bottom: 0 }}>
        <defs>
          <linearGradient id="grad" x1="0" y1="0" x2="0" y2="1">
            <stop offset="5%"  stopColor={color} stopOpacity={0.2} />
            <stop offset="95%" stopColor={color} stopOpacity={0} />
          </linearGradient>
        </defs>
        <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" vertical={false} />
        <XAxis dataKey="time" tick={{ fontSize: 10, fill: '#94a3b8' }} tickLine={false} axisLine={false}
          interval={Math.floor(data.length / 5)} />
        <YAxis domain={[minVal, maxVal]} tick={{ fontSize: 10, fill: '#94a3b8' }}
          tickLine={false} axisLine={false} tickFormatter={v => v.toFixed(2)} width={60} />
        <Tooltip content={<ChartTooltip />} cursor={{ stroke: '#093eaa', strokeWidth: 1, strokeDasharray: '4 4' }} />
        <Area type="monotone" dataKey="price" stroke={color} strokeWidth={2.5}
          fill="url(#grad)" dot={false} activeDot={{ r: 5, fill: color, stroke: '#fff', strokeWidth: 2 }} />
      </AreaChart>
    </ResponsiveContainer>
  );
}

/* ─── Metric Card ─── */
function MetricCard({ label, value, highlight }) {
  if (!value) return null;
  return (
    <div className="bg-gray-50 rounded-xl p-4">
      <p className="text-xs text-gray-400 mb-1">{label}</p>
      <p className={`text-sm font-bold ${highlight ? 'text-[#093eaa]' : 'text-gray-900'}`}>{value}</p>
    </div>
  );
}

/* ─── Info Row ─── */
function InfoRow({ label, value }) {
  if (!value) return null;
  return (
    <div className="flex justify-between items-start py-3.5 border-b border-gray-100 last:border-0">
      <span className="text-sm text-gray-500 w-44 shrink-0">{label}</span>
      <span className="text-sm text-gray-900 font-medium text-right max-w-xs">{value}</span>
    </div>
  );
}

/* ─── Main Page ─── */
export default function StockDetailPage() {
  const { symbol } = useParams();
  const [midas, setMidas] = useState(null);
  const [chart, setChart] = useState(null);
  const [loading, setLoading] = useState(true);
  const [chartLoading, setChartLoading] = useState(false);
  const [error, setError] = useState('');
  const [activeRange, setActiveRange] = useState(0); // index into RANGES

  useEffect(() => {
    setLoading(true);
    const r = RANGES[0];
    Promise.all([
      getStockMidasDetail(symbol).catch(() => null),
      getStockChart(symbol, r.range, r.interval).catch(() => null),
    ]).then(([m, c]) => { setMidas(m); setChart(c); })
      .catch(e => setError(!e.response ? 'Sunucuya ulaşılamıyor.' : `Hata (${e.response.status})`))
      .finally(() => setLoading(false));
  }, [symbol]);

  function handleRange(idx) {
    setActiveRange(idx);
    setChartLoading(true);
    const r = RANGES[idx];
    getStockChart(symbol, r.range, r.interval)
      .then(setChart)
      .catch(() => {})
      .finally(() => setChartLoading(false));
  }

  const ticker = symbol?.replace('.IS', '').replace('.is', '').toUpperCase();
  const isPos = midas?.dailyChangePercent && !midas.dailyChangePercent.startsWith('-');

  return (
    <div className="max-w-4xl mx-auto space-y-6">
      {/* Back + Title */}
      <div className="flex items-center gap-3">
        <Link to="/market/stocks" className="text-gray-400 hover:text-[#093eaa] transition-colors">
          <ArrowLeft className="w-5 h-5" />
        </Link>
        <div>
          <h1 className="text-2xl font-bold text-gray-900">
            {ticker} Hisse {midas?.name ? `- ${midas.name}` : ''}
          </h1>
          <p className="text-xs text-gray-400 mt-0.5">Borsa İstanbul · Veriler 15 dk gecikmeli · Kaynak: Midas</p>
        </div>
      </div>

      {loading && (
        <div className="flex items-center justify-center py-20">
          <div className="flex gap-2">
            <div className="w-2 h-2 bg-[#093eaa] rounded-full animate-bounce" />
            <div className="w-2 h-2 bg-[#093eaa]/60 rounded-full animate-bounce [animation-delay:100ms]" />
            <div className="w-2 h-2 bg-[#093eaa]/30 rounded-full animate-bounce [animation-delay:200ms]" />
          </div>
        </div>
      )}

      {error && <div className="bg-red-50 border border-red-200 text-red-600 text-sm px-6 py-4 rounded-2xl">{error}</div>}

      {!loading && !error && (
        <>
          {/* ── Price + Volume + Chart ── */}
          <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden">
            <div className="grid grid-cols-2 divide-x divide-gray-100">
              <div className="p-6">
                <p className="text-4xl font-bold text-gray-900">
                  {midas?.currentPrice ?? '-'}
                </p>
                <p className="text-sm text-gray-400 mt-1">Güncel Fiyat</p>
              </div>
              <div className="p-6">
                <p className="text-2xl font-bold text-gray-900 truncate">
                  {midas?.dailyVolume ?? '-'}
                </p>
                <p className="text-sm text-gray-400 mt-1">Günlük İşlem Hacmi</p>
              </div>
            </div>

            <div className="px-4 pt-2 pb-2">
              <StockChart timestamps={chart?.timestamps} prices={chart?.closePrices} />
              {/* Range buttons */}
              <div className="flex gap-2 mt-3 justify-center">
                {RANGES.map((r, i) => (
                  <button key={r.label} onClick={() => handleRange(i)}
                    className={`px-3 py-1.5 rounded-full text-xs font-bold transition-all ${
                      activeRange === i
                        ? 'bg-[#093eaa] text-white'
                        : 'text-gray-400 hover:text-gray-700'
                    }`}>
                    {r.label}
                  </button>
                ))}
              </div>
              {chartLoading && <p className="text-center text-xs text-gray-400 mt-2">Grafik yükleniyor...</p>}
            </div>

            {midas?.dailyChangePercent && (
              <div className="px-6 pb-5">
                <span className={`text-sm font-bold flex items-center gap-1.5 ${isPos ? 'text-emerald-600' : 'text-rose-600'}`}>
                  {isPos ? <TrendingUp className="w-4 h-4" /> : <TrendingDown className="w-4 h-4" />}
                  Günlük Değişim: {midas.dailyChange ?? ''} ({midas.dailyChangePercent})
                </span>
              </div>
            )}
          </div>

          {/* ── Financial Metrics Table ── */}
          <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">
            <h2 className="text-base font-bold text-gray-900 mb-4">{ticker} Hisse ve Finansal Bilgileri</h2>
            <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
              <MetricCard label="Son İşlem Fiyatı"     value={midas?.currentPrice} highlight />
              <MetricCard label="Alış"                 value={midas?.bid} />
              <MetricCard label="Satış"                value={midas?.ask} />
              <MetricCard label="Açılış Fiyatı"        value={midas?.openPrice} />
              <MetricCard label="Günlük Değişim %"     value={midas?.dailyChangePercent} />
              <MetricCard label="Günlük Değişim (TL)"  value={midas?.dailyChange} />
              <MetricCard label="Günlük Hacim (TL)"    value={midas?.dailyVolume} />
              <MetricCard label="Günlük Hacim (Lot)"   value={midas?.volumeLot} />
              <MetricCard label="Tavan"                value={midas?.upperLimit} />
              <MetricCard label="Taban"                value={midas?.lowerLimit} />
              <MetricCard label="Haftalık En Yüksek"   value={midas?.weeklyHigh} />
              <MetricCard label="Haftalık En Düşük"    value={midas?.weeklyLow} />
              <MetricCard label="Aylık En Yüksek"      value={midas?.monthlyHigh} />
              <MetricCard label="Aylık En Düşük"       value={midas?.monthlyLow} />
              <MetricCard label="Piyasa Değeri"        value={midas?.marketCap} />
              <MetricCard label="Sermaye"              value={midas?.capital} />
              <MetricCard label="F/K"                  value={midas?.peRatio} />
              <MetricCard label="PD/DD"                value={midas?.pbRatio} />
              <MetricCard label="Halka Açıklık (%)"    value={midas?.freeFloat} />
              <MetricCard label="Yabancı Oranı (%)"    value={midas?.foreignRatio} />
              <MetricCard label="Volatilite"           value={midas?.volatility} />
              <MetricCard label="Net Kâr"              value={midas?.netProfit} />
            </div>
          </div>

          {/* ── Ortaklık Yapısı ── */}
          {midas?.shareholders?.length > 0 && (
            <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">
              <h2 className="text-base font-bold text-gray-900 mb-6">{ticker} Ortaklık Yapısı</h2>
              <table className="w-full">
                <thead className="bg-gray-50">
                  <tr>
                    <th className="text-left px-4 py-3 text-xs font-bold text-gray-500 uppercase tracking-wider">Ticari Ünvan</th>
                    <th className="text-right px-4 py-3 text-xs font-bold text-gray-500 uppercase tracking-wider">Pay Oranı (%)</th>
                  </tr>
                </thead>
                <tbody>
                  {midas.shareholders.map((s, i) => (
                    <tr key={i} className="border-t border-gray-100 hover:bg-gray-50 transition-colors">
                      <td className="px-4 py-3 text-sm text-gray-800">{s.name}</td>
                      <td className="px-4 py-3 text-sm font-semibold text-gray-900 text-right">{s.sharePercent}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {/* ── Şirket Hakkında ── */}
          <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">
            <h2 className="text-base font-bold text-gray-900 mb-1">{ticker} - Şirket Hakkında</h2>
            {midas?.name && (
              <p className="text-sm text-gray-400 mb-6">
                {midas.name} şirketiyle ilgili bilmen gerekenleri aşağıda bulabilirsin.
              </p>
            )}
            <InfoRow label="CEO"              value={midas?.ceo} />
            <InfoRow label="Kuruluş Tarihi"   value={midas?.foundedDate} />
            <InfoRow label="Halka Arz Tarihi" value={midas?.ipoDate} />
            <InfoRow label="Sektör"           value={midas?.sector} />
            <InfoRow label="Çalışan Sayısı"   value={midas?.employeeCount} />
            <InfoRow label="Adres"            value={midas?.address} />
            <InfoRow label="Merkez"           value={midas?.country} />
          </div>

          {/* ── Şirket Açıklaması ── */}
          {midas?.description && (
            <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">
              {/* Logo + Name header like Midas */}
              <div className="flex items-center gap-4 mb-6">
                {midas.logoUrl && (
                  <img
                    src={`http://localhost:8080/api/proxy/image?url=${encodeURIComponent(midas.logoUrl)}`}
                    alt={midas.name}
                    className="w-16 h-16 rounded-xl object-contain border border-gray-100 p-1"
                    onError={e => { e.target.style.display = 'none'; }}
                  />
                )}
                <h2 className="text-xl font-bold text-gray-900">{midas.name}</h2>
              </div>
              {midas.description.split('\n\n').map((para, i) => (
                <p key={i} className="text-sm text-gray-600 leading-relaxed mb-4 last:mb-0">{para}</p>
              ))}
            </div>
          )}

          {!midas && !chart && (
            <div className="bg-white rounded-2xl border border-gray-200 p-12 text-center">
              <p className="text-gray-400">Bu hisse için detay verisi bulunamadı.</p>
            </div>
          )}
        </>
      )}
    </div>
  );
}
