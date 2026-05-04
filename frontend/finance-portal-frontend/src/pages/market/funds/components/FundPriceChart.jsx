import { useState, useEffect, useCallback } from 'react';
import { RefreshCw, TrendingUp, TrendingDown, BarChart2 } from 'lucide-react';
import {
  AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
} from 'recharts';
import { getFundHistory } from '../../../../api/marketApi';

// Periyot tanımları — backend FundPeriod enum ile eşleşmeli
const PERIODS = [
  { key: 'ONE_WEEK',     label: '1 Hafta' },
  { key: 'ONE_MONTH',    label: '1 Ay'    },
  { key: 'THREE_MONTHS', label: '3 Ay'    },
  { key: 'SIX_MONTHS',   label: '6 Ay'    },
  { key: 'ONE_YEAR',     label: '1 Yıl'   },
  { key: 'THREE_YEARS',  label: '3 Yıl'   },
  { key: 'FIVE_YEARS',   label: '5 Yıl'   },
];

function fmt(val, decimals = 4) {
  if (val == null || isNaN(val)) return '-';
  return parseFloat(val).toLocaleString('tr-TR', {
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  });
}

function ChartTooltip({ active, payload, label }) {
  if (!active || !payload?.length) return null;
  const d = payload[0]?.payload ?? {};
  const isPos = (d.dailyChangePercent ?? 0) >= 0;

  return (
    <div className="bg-white border border-gray-200 rounded-xl shadow-lg px-4 py-3 text-sm min-w-[160px]">
      <p className="text-gray-400 text-xs mb-2">{d.dateText ?? label}</p>
      <p className="font-bold text-gray-900 text-base mb-1">
        {fmt(d.price, 6)} TL
      </p>
      {d.dailyChangeAmount !== 0 && (
        <p className={`text-xs font-medium ${isPos ? 'text-emerald-600' : 'text-rose-600'}`}>
          Günlük: {isPos ? '+' : ''}{fmt(d.dailyChangeAmount, 4)} TL
          {' '}({isPos ? '+' : ''}{fmt(d.dailyChangePercent, 2)}%)
        </p>
      )}
    </div>
  );
}

function EmptyChart({ message }) {
  return (
    <div className="flex flex-col items-center justify-center h-72 gap-3 text-gray-400">
      <BarChart2 className="w-10 h-10 opacity-30" />
      <p className="text-sm text-center px-4">{message ?? 'Grafik verisi yüklenemedi.'}</p>
    </div>
  );
}

export default function FundPriceChart({ code }) {
  const [period, setPeriod]   = useState('ONE_MONTH');
  const [points, setPoints]   = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError]     = useState(false);

  const loadHistory = useCallback(() => {
    if (!code) return;
    setLoading(true);
    setError(false);
    getFundHistory(code, period)
      .then(resp => {
        // Backend: { code, period, points: [{ date, dateText, price, changePercent, dailyChangeAmount, dailyChangePercent }] }
        const pts = (resp?.points ?? []).filter(p => p.price > 0);
        setPoints(pts);
      })
      .catch(() => {
        setPoints([]);
        setError(true);
      })
      .finally(() => setLoading(false));
  }, [code, period]);

  useEffect(() => { loadHistory(); }, [loadHistory]);

  // Grafik için x=dateText, y=price
  const chartData = points.map(p => ({
    date:               p.date,
    dateText:           p.dateText ?? p.date,
    price:              p.price,
    dailyChangeAmount:  p.dailyChangeAmount,
    dailyChangePercent: p.dailyChangePercent,
    changePercent:      p.changePercent,
  }));

  const isUp = chartData.length > 1 &&
    chartData[chartData.length - 1].price >= chartData[0].price;
  const strokeColor = isUp ? '#10b981' : '#ef4444';

  const prices = chartData.map(p => p.price);
  const minVal = prices.length > 0 ? Math.min(...prices) * 0.998 : 0;
  const maxVal = prices.length > 0 ? Math.max(...prices) * 1.002 : 0;

  const firstPrice = chartData.length > 1 ? chartData[0].price : null;
  const lastPrice  = chartData.length > 1 ? chartData[chartData.length - 1].price : null;
  const periodReturn = firstPrice && lastPrice
    ? ((lastPrice - firstPrice) / firstPrice) * 100
    : null;
  const periodPos = (periodReturn ?? 0) >= 0;

  const currentPeriod = PERIODS.find(p => p.key === period) ?? PERIODS[1];

  return (
    <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">
      {/* Başlık + dönem seçici */}
      <div className="flex items-center justify-between mb-4 flex-wrap gap-3">
        <div>
          <h2 className="font-bold text-gray-900">Fiyat Grafiği</h2>
          {periodReturn != null && (
            <span className={`text-sm font-semibold flex items-center gap-1 mt-0.5 ${periodPos ? 'text-emerald-600' : 'text-rose-600'}`}>
              {periodPos ? <TrendingUp className="w-4 h-4" /> : <TrendingDown className="w-4 h-4" />}
              {currentPeriod.label} getiri:{' '}
              {periodPos ? '+' : ''}
              {periodReturn.toLocaleString('tr-TR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}%
            </span>
          )}
        </div>
        <div className="flex items-center gap-2">
          <div className="flex gap-1 flex-wrap">
            {PERIODS.map(p => (
              <button key={p.key} onClick={() => setPeriod(p.key)}
                className={`px-3 py-1.5 rounded-lg text-xs font-bold transition-all ${
                  period === p.key
                    ? 'bg-[#093eaa] text-white'
                    : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
                }`}>
                {p.label}
              </button>
            ))}
          </div>
          <button onClick={loadHistory}
            className="p-1.5 rounded-lg bg-gray-100 hover:bg-gray-200 transition-all"
            title="Yenile">
            <RefreshCw className={`w-3.5 h-3.5 text-gray-500 ${loading ? 'animate-spin' : ''}`} />
          </button>
        </div>
      </div>

      {/* Grafik alanı */}
      <div className="h-72 relative">
        {loading && (
          <div className="absolute inset-0 flex items-center justify-center bg-white/70 z-10 rounded-xl">
            <div className="flex gap-1.5">
              <div className="w-2 h-2 bg-[#093eaa] rounded-full animate-bounce" />
              <div className="w-2 h-2 bg-[#093eaa]/60 rounded-full animate-bounce [animation-delay:100ms]" />
              <div className="w-2 h-2 bg-[#093eaa]/30 rounded-full animate-bounce [animation-delay:200ms]" />
            </div>
          </div>
        )}

        {!loading && error && (
          <EmptyChart message="Grafik verisi şu an alınamadı. Lütfen daha sonra tekrar deneyin." />
        )}

        {!loading && !error && chartData.length === 0 && (
          <EmptyChart message="Bu dönem için grafik verisi bulunamadı." />
        )}

        {!loading && !error && chartData.length > 0 && (
          <ResponsiveContainer width="100%" height="100%">
            <AreaChart data={chartData} margin={{ top: 5, right: 10, left: 10, bottom: 5 }}>
              <defs>
                <linearGradient id="fundAreaGrad" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%"  stopColor={strokeColor} stopOpacity={0.15} />
                  <stop offset="95%" stopColor={strokeColor} stopOpacity={0} />
                </linearGradient>
              </defs>
              <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" vertical={false} />
              <XAxis
                dataKey="dateText"
                tick={{ fontSize: 10, fill: '#9ca3af' }}
                tickLine={false}
                axisLine={false}
                interval="preserveStartEnd"
              />
              <YAxis
                domain={[minVal, maxVal]}
                tick={{ fontSize: 10, fill: '#9ca3af' }}
                tickLine={false}
                axisLine={false}
                tickFormatter={v =>
                  parseFloat(v).toLocaleString('tr-TR', {
                    minimumFractionDigits: 2,
                    maximumFractionDigits: 4,
                  })
                }
                width={80}
              />
              <Tooltip
                content={<ChartTooltip />}
                cursor={{ stroke: '#093eaa', strokeWidth: 1, strokeDasharray: '4 4' }}
              />
              <Area
                type="monotone"
                dataKey="price"
                stroke={strokeColor}
                strokeWidth={2}
                fill="url(#fundAreaGrad)"
                dot={false}
                activeDot={{ r: 4, fill: strokeColor, stroke: '#fff', strokeWidth: 2 }}
              />
            </AreaChart>
          </ResponsiveContainer>
        )}
      </div>

      <p className="text-xs text-gray-400 mt-2">
        Kaynak: HangiKredi · TEFAS · Günlük kapanış fiyatları
      </p>
    </div>
  );
}
