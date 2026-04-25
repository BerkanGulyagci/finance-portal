import { useEffect, useState, useCallback } from 'react';
import { useParams, Link } from 'react-router-dom';
import { ArrowLeft, RefreshCw, TrendingUp, TrendingDown, BarChart2 } from 'lucide-react';
import {
  AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
} from 'recharts';
import { getTefasFundDetail, getTefasFundHistory } from '../../api/marketApi';

function fmt(v, dec = 6) {
  if (v == null) return '-';
  return parseFloat(v).toLocaleString('tr-TR', { minimumFractionDigits: dec, maximumFractionDigits: dec });
}
function fmtInt(v) {
  if (v == null) return '-';
  return Number(v).toLocaleString('tr-TR');
}

const RANGES = ['1W', '1M', '3M', '6M', '1Y', '5Y'];
const RANGE_LABELS = { '1W': 'Haftalık', '1M': 'Aylık', '3M': '3 Aylık', '6M': '6 Aylık', '1Y': 'Son 1 Yıl', '5Y': 'Son 5 Yıl' };

function ChartTooltip({ active, payload, label }) {
  if (!active || !payload?.length) return null;
  const val = payload[0]?.value;
  return (
    <div className="bg-white border border-gray-200 rounded-xl shadow-lg px-4 py-3 text-sm min-w-[160px]">
      <p className="text-gray-500 text-xs font-semibold mb-1.5 border-b border-gray-100 pb-1.5">{label}</p>
      <div className="flex justify-between gap-4">
        <span className="text-gray-500 text-xs">Fiyat:</span>
        <span className="font-bold text-gray-900 text-xs">
          {val != null ? parseFloat(val).toLocaleString('tr-TR', { minimumFractionDigits: 6, maximumFractionDigits: 6 }) : '-'}
        </span>
      </div>
    </div>
  );
}

export default function TefasFundDetailPage() {
  const { code } = useParams();

  const [detail, setDetail]         = useState(null);
  const [history, setHistory]       = useState(null);
  const [historyFull, setHistoryFull] = useState(null); // 1Y — dönem getirileri için
  const [range, setRange]           = useState('1Y');
  const [loading, setLoading]       = useState(true);
  const [loadingChart, setLoadingChart] = useState(false);

  // Detay
  useEffect(() => {
    setLoading(true);
    getTefasFundDetail(code)
      .then(setDetail)
      .catch(() => setDetail(null))
      .finally(() => setLoading(false));
  }, [code]);

  // 1Y history — grafik için (artık dönem getirileri backend'den geliyor)
  useEffect(() => {
    getTefasFundHistory(code, '1Y')
      .then(setHistoryFull)
      .catch(() => setHistoryFull(null));
  }, [code]);

  // Grafik
  const loadHistory = useCallback(() => {
    setLoadingChart(true);
    getTefasFundHistory(code, range)
      .then(setHistory)
      .catch(() => setHistory(null))
      .finally(() => setLoadingChart(false));
  }, [code, range]);

  useEffect(() => { loadHistory(); }, [loadHistory]);

  // Chart data
  const chartPoints = history?.points
    ?.slice()
    .sort((a, b) => a.date.localeCompare(b.date))
    .map(p => ({ date: p.date, value: parseFloat(p.price) })) ?? [];

  const isUp = chartPoints.length > 1 &&
    chartPoints[chartPoints.length - 1].value >= chartPoints[0].value;
  const strokeColor = isUp ? '#10b981' : '#ef4444';
  const chartMin = chartPoints.length > 0 ? Math.min(...chartPoints.map(p => p.value)) * 0.998 : 0;
  const chartMax = chartPoints.length > 0 ? Math.max(...chartPoints.map(p => p.value)) * 1.002 : 0;

  // Dönem getirisi — seçili range'e göre scrape'den gelen resmi değer öncelikli
  const scrapedReturnMap = {
    '1M': detail?.return1M,
    '3M': detail?.return3M,
    '6M': detail?.return6M,
    '1Y': detail?.return1Y,
    // 1W ve 5Y için scrape yok → fiyat hesabına düşer
  };
  const scrapedReturn = scrapedReturnMap[range] ?? null;
  // Scrape'de yoksa (1W, 5Y) grafik verisinden hesapla
  const firstPrice = chartPoints.length > 1 ? chartPoints[0].value : null;
  const lastPrice  = chartPoints.length > 1 ? chartPoints[chartPoints.length - 1].value : null;
  const calcPeriodReturn = firstPrice && lastPrice ? ((lastPrice - firstPrice) / firstPrice) * 100 : null;
  // detail yüklenmediyse scrape değeri bekleniyor olabilir — sadece scrape varsa ya da 1W/5Y ise göster
  const periodReturn = scrapedReturn != null ? scrapedReturn : (loading ? null : calcPeriodReturn);
  const periodPos = (periodReturn ?? 0) >= 0;

  // 1Y history'den dönem getirileri hesapla
  const fullPoints = historyFull?.points
    ?.slice()
    .sort((a, b) => a.date.localeCompare(b.date))
    .map(p => ({ date: p.date, value: parseFloat(p.price) })) ?? [];

  function calcReturn(daysAgo) {
    if (fullPoints.length < 2) return null;
    const last = fullPoints[fullPoints.length - 1].value;
    const lastDate = new Date(fullPoints[fullPoints.length - 1].date);
    const cutoff = new Date(lastDate);
    cutoff.setDate(cutoff.getDate() - daysAgo);
    const cutoffStr = cutoff.toISOString().slice(0, 10);

    // cutoff tarihine en yakın (önceki veya eşit) noktayı bul
    let closest = fullPoints[0];
    for (const pt of fullPoints) {
      if (pt.date <= cutoffStr) closest = pt;
      else break;
    }
    if (!closest || closest.value === 0) return null;
    return ((last - closest.value) / closest.value) * 100;
  }

  // Günlük getiri: son iki günün fiyatından
  const dailyReturn = fullPoints.length >= 2
    ? ((fullPoints[fullPoints.length - 1].value - fullPoints[fullPoints.length - 2].value) /
       fullPoints[fullPoints.length - 2].value) * 100
    : null;

  function formatDate(d) {
    if (!d) return '';
    const parts = d.split('-');
    if (range === '1W' || range === '1M') return `${parts[2]}/${parts[1]}`;
    return `${parts[1]}/${parts[0]?.slice(2)}`;
  }

  return (
    <div className="space-y-6">
      {/* Back + Karşılaştır */}
      <div className="flex items-center justify-between">
        <Link to="/market/tefas" className="inline-flex items-center gap-1.5 text-sm text-[#093eaa] font-semibold hover:underline">
          <ArrowLeft className="w-4 h-4" /> TEFAS Fonları
        </Link>
        <Link
          to={`/market/tefas/compare?codes=${code}`}
          className="inline-flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-semibold border border-[#093eaa] text-[#093eaa] bg-white hover:bg-blue-50 transition-all"
        >
          <BarChart2 className="w-4 h-4" />
          Karşılaştır
        </Link>
      </div>

      {/* Özet kart */}
      <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">
        {loading ? (
          <div className="flex gap-1.5 py-4">
            <div className="w-2 h-2 bg-[#093eaa] rounded-full animate-bounce" />
            <div className="w-2 h-2 bg-[#093eaa]/60 rounded-full animate-bounce [animation-delay:100ms]" />
            <div className="w-2 h-2 bg-[#093eaa]/30 rounded-full animate-bounce [animation-delay:200ms]" />
          </div>
        ) : detail ? (
          <>
            <div className="text-center mb-5">
              <h1 className="text-xl font-black text-gray-900 mb-3">{detail.title}</h1>
              {/* Üst bilgi satırı */}
              <div className="grid grid-cols-2 sm:grid-cols-5 gap-4 mb-4">
                {[
                  { label: 'Son Fiyat (TL)', value: fmt(detail.price, 6), color: 'text-[#093eaa]' },
                  { label: 'Günlük Getiri (%)',
                    value: detail.dailyReturn != null ? `%${detail.dailyReturn.toFixed(4)}` : '-',
                    color: detail.dailyReturn == null ? 'text-gray-700' : detail.dailyReturn >= 0 ? 'text-emerald-600' : 'text-rose-600' },
                  { label: 'Pay (Adet)', value: fmtInt(detail.sharesInCirculation), color: 'text-[#093eaa]' },
                  { label: 'Fon Toplam Değer (TL)',
                    value: detail.marketCap != null ? parseFloat(detail.marketCap).toLocaleString('tr-TR', { minimumFractionDigits: 2 }) : '-',
                    color: 'text-[#093eaa]' },
                  { label: 'Yatırımcı Sayısı (Kişi)', value: fmtInt(detail.numberOfInvestors), color: 'text-[#093eaa]' },
                ].map(item => (
                  <div key={item.label} className="text-center">
                    <p className="text-xs text-gray-500 mb-1">{item.label}</p>
                    <p className={`text-sm font-bold ${item.color}`}>{item.value}</p>
                  </div>
                ))}
              </div>
            </div>

            {/* Dönem getirileri — backend scrape'den */}
            <div className="grid grid-cols-2 sm:grid-cols-4 border border-gray-200 rounded-xl overflow-hidden">
              {[
                { label: 'Son 1 Ay Getirisi', value: detail.return1M },
                { label: 'Son 3 Ay Getirisi', value: detail.return3M },
                { label: 'Son 6 Ay Getirisi', value: detail.return6M },
                { label: 'Son 1 Yıl Getirisi', value: detail.return1Y },
              ].map((item, i) => {
                const pos = (item.value ?? 0) >= 0;
                return (
                  <div key={item.label}
                    className={`p-4 text-center ${i % 2 === 0 ? 'border-r border-gray-200' : ''} ${i >= 2 ? 'border-t border-gray-200 sm:border-t-0' : ''} ${i < 3 ? 'sm:border-r sm:border-gray-200' : ''}`}>
                    <p className="text-xs text-gray-500 mb-1">{item.label}</p>
                    <p className={`text-base font-black ${item.value == null ? 'text-gray-400' : pos ? 'text-emerald-600' : 'text-rose-600'}`}>
                      {item.value == null ? '-' : `%${item.value.toFixed(6)}`}
                    </p>
                  </div>
                );
              })}
            </div>
          </>
        ) : (
          <p className="text-gray-400 text-sm">Fon bilgisi yüklenemedi.</p>
        )}
      </div>

      {/* Grafik */}
      <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">
        <div className="flex items-center justify-between mb-4 flex-wrap gap-3">
          <div>
            <h2 className="font-bold text-gray-900">Fiyat Grafiği</h2>
            {periodReturn != null && (
              <span className={`text-sm font-semibold flex items-center gap-1 mt-0.5 ${periodPos ? 'text-emerald-600' : 'text-rose-600'}`}>
                {periodPos ? <TrendingUp className="w-4 h-4" /> : <TrendingDown className="w-4 h-4" />}
                Dönem getirisi: {periodPos ? '+' : ''}{periodReturn.toFixed(4)}%
              </span>
            )}
          </div>
          <div className="flex items-center gap-2">
            <div className="flex gap-1 flex-wrap">
              {RANGES.map(r => (
                <button key={r} onClick={() => setRange(r)}
                  className={`px-3 py-1.5 rounded-lg text-xs font-bold transition-all ${
                    range === r ? 'bg-[#093eaa] text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
                  }`}>
                  {RANGE_LABELS[r]}
                </button>
              ))}
            </div>
            <button onClick={loadHistory} className="p-1.5 rounded-lg bg-gray-100 hover:bg-gray-200 transition-all">
              <RefreshCw className={`w-3.5 h-3.5 text-gray-500 ${loadingChart ? 'animate-spin' : ''}`} />
            </button>
          </div>
        </div>

        <div className="h-80 relative">
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
                  <linearGradient id="fundGrad" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%"  stopColor={strokeColor} stopOpacity={0.15} />
                    <stop offset="95%" stopColor={strokeColor} stopOpacity={0} />
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
                <XAxis dataKey="date" tick={{ fontSize: 10, fill: '#9ca3af' }}
                  tickFormatter={formatDate} interval="preserveStartEnd" />
                <YAxis domain={[chartMin, chartMax]} tick={{ fontSize: 10, fill: '#9ca3af' }}
                  tickFormatter={v => parseFloat(v).toLocaleString('tr-TR', { minimumFractionDigits: 4, maximumFractionDigits: 4 })}
                  width={80} />
                <Tooltip content={<ChartTooltip />} />
                <Area type="monotone" dataKey="value" stroke={strokeColor} strokeWidth={1.5}
                  fill="url(#fundGrad)" dot={false} activeDot={{ r: 4 }} />
              </AreaChart>
            </ResponsiveContainer>
          ) : !loadingChart && (
            <div className="flex items-center justify-center h-full text-gray-400 text-sm">
              Grafik verisi yüklenemedi.
            </div>
          )}
        </div>
        <p className="text-xs text-gray-400 mt-2">Kaynak: TEFAS (tefas.gov.tr) · Günlük kapanış fiyatları</p>
      </div>
    </div>
  );
}
