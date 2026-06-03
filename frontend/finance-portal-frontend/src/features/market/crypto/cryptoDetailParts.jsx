/* eslint-disable react-refresh/only-export-components -- saf formatter/calc helper'lar + küçük sunum component'leri (RSIBadge/pct) bir arada; fast-refresh dev uyarısı, davranışı etkilemez */
// CryptoDetailPage'den çıkarılan saf yardımcılar (fiyat/yüzde format, MA/RSI hesabı) + küçük sunum
// parçaları (pct rozeti, RSIBadge gauge). Davranış orijinaliyle birebir aynı.

import { TrendingUp, TrendingDown } from 'lucide-react';
import { useTranslation } from '../../../context/LanguageContext';

export const CURRENCIES = ['TRY', 'USD', 'EUR'];
const CURRENCY_SYMBOLS = { TRY: '₺', USD: '$', EUR: '€' };

export function fmtPrice(v, currency) {
  if (v == null) return '-';
  const sym = CURRENCY_SYMBOLS[currency] ?? currency;
  const n = parseFloat(v);
  let dec;
  if (n >= 1000)        dec = 2;
  else if (n >= 1)      dec = 4;
  else if (n >= 0.01)   dec = 6;
  else if (n >= 0.0001) dec = 8;
  else                  dec = 10;
  return `${sym}${n.toLocaleString('tr-TR', { minimumFractionDigits: dec, maximumFractionDigits: dec })}`;
}

export function fmt(v, dec = 2) {
  if (v == null) return '-';
  return parseFloat(v).toLocaleString('tr-TR', { minimumFractionDigits: dec, maximumFractionDigits: dec });
}

export function pct(v) {
  if (v == null) return <span className="text-gray-400">-</span>;
  const pos = v >= 0;
  return (
    <span className={`flex items-center gap-0.5 font-bold text-xs ${pos ? 'text-emerald-600' : 'text-rose-600'}`}>
      {pos ? <TrendingUp className="w-3 h-3" /> : <TrendingDown className="w-3 h-3" />}
      {pos ? '+' : ''}{fmt(v)}%
    </span>
  );
}

// MA hesaplama yardımcısı
export function calcMA(data, period) {
  return data.map((_, i) => {
    if (i < period - 1) return null;
    const slice = data.slice(i - period + 1, i + 1);
    return slice.reduce((s, d) => s + (d.price ?? 0), 0) / period;
  });
}

// RSI hesaplama (14 periyot)
export function calcRSI(data, period = 14) {
  if (data.length < period + 1) return null;
  const prices = data.map(d => d.price ?? 0);
  let gains = 0, losses = 0;
  for (let i = 1; i <= period; i++) {
    const diff = prices[i] - prices[i - 1];
    if (diff >= 0) gains += diff; else losses -= diff;
  }
  let avgGain = gains / period;
  let avgLoss = losses / period;
  for (let i = period + 1; i < prices.length; i++) {
    const diff = prices[i] - prices[i - 1];
    avgGain = (avgGain * (period - 1) + Math.max(diff, 0)) / period;
    avgLoss = (avgLoss * (period - 1) + Math.max(-diff, 0)) / period;
  }
  if (avgLoss === 0) return 100;
  const rs = avgGain / avgLoss;
  return 100 - (100 / (1 + rs));
}

export function RSIBadge({ rsi }) {
  const { t } = useTranslation();
  if (rsi == null) return null;
  const val = parseFloat(rsi.toFixed(1));
  let label, bg, text;
  if (val >= 70)      { label = t('Aşırı Alım'); bg = 'bg-rose-100';    text = 'text-rose-700'; }
  else if (val <= 30) { label = t('Aşırı Satım'); bg = 'bg-emerald-100'; text = 'text-emerald-700'; }
  else                { label = t('Normal');       bg = 'bg-gray-100';    text = 'text-gray-600'; }

  // Gauge yüzdesi (0-100 → 0%-100%)
  const pct = Math.min(100, Math.max(0, val));

  return (
    <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-5">
      <div className="flex items-center justify-between mb-3">
        <p className="text-xs font-bold text-gray-500 uppercase tracking-wider">RSI (14)</p>
        <span className={`text-xs font-bold px-2 py-0.5 rounded-full ${bg} ${text}`}>{label}</span>
      </div>
      <p className="text-2xl font-black text-gray-900 mb-3">{val}</p>
      {/* Gauge bar */}
      <div className="relative h-2 rounded-full overflow-hidden mb-1" style={{
        background: 'linear-gradient(to right, #10b981 0%, #10b981 30%, #f59e0b 30%, #f59e0b 70%, #ef4444 70%, #ef4444 100%)'
      }}>
        <div className="absolute top-1/2 -translate-y-1/2 w-3 h-3 rounded-full bg-white border-2 border-gray-700 shadow"
          style={{ left: `calc(${pct}% - 6px)` }} />
      </div>
      <div className="flex justify-between text-xs text-gray-400 mt-1">
        <span>0</span><span className="text-emerald-600 font-semibold">30</span>
        <span className="text-rose-600 font-semibold">70</span><span>100</span>
      </div>
    </div>
  );
}
