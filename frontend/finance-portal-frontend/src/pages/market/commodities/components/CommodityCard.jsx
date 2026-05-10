import { useState } from 'react';
import { TrendingUp, TrendingDown, AlertCircle } from 'lucide-react';
import { CATEGORY_META, fmt, fmtPct } from './commodityConstants';

export default function CommodityCard({ meta, spot, loading, usdTryRate, onClick }) {
  const catMeta = CATEGORY_META[meta.category] ?? CATEGORY_META.ENERGY;
  const pct     = spot ? fmtPct(spot.changePercent) : null;
  const isPos   = pct ? pct.value >= 0 : null;

  // USD ↔ TL toggle — sadece fiyat alanına tıklanınca değişir, kart tıklaması detaya gider
  const [showTry, setShowTry] = useState(false);
  const canToggle = usdTryRate != null && spot?.displayPrice != null;
  const tryPrice  = canToggle ? parseFloat(spot.displayPrice) * usdTryRate : null;

  function handlePriceClick(e) {
    e.stopPropagation(); // kart onClick'ini tetikleme
    if (canToggle) setShowTry(v => !v);
  }

  return (
    <button
      onClick={onClick}
      className="w-full text-left bg-white rounded-2xl border border-gray-200 shadow-sm hover:shadow-md hover:border-gray-300 transition-all p-5 group min-h-[160px] flex flex-col justify-between"
    >
      {/* Üst satır: isim + badge'ler */}
      <div className="flex items-start justify-between gap-2 mb-3">
        <div>
          <p className="font-bold text-gray-900 text-sm group-hover:text-[#093eaa] transition-colors">
            {meta.displayNameTr}
          </p>
          <p className="text-xs text-gray-400 mt-0.5">{meta.displayNameEn}</p>
        </div>
        <div className="flex flex-col items-end gap-1 shrink-0">
          <span className={`text-[10px] font-bold px-2 py-0.5 rounded-full ${catMeta.bg} ${catMeta.color} border ${catMeta.border}`}>
            {catMeta.label}
          </span>
          <span className="text-[10px] font-semibold px-2 py-0.5 rounded-full bg-amber-50 text-amber-700 border border-amber-200">
            Vadeli
          </span>
        </div>
      </div>

      {/* Fiyat alanı */}
      {loading ? (
        <div className="space-y-2">
          <div className="h-6 bg-gray-100 rounded animate-pulse w-28" />
          <div className="h-4 bg-gray-100 rounded animate-pulse w-20" />
        </div>
      ) : spot && !spot.stale ? (
        <div>
          {/* Tıklanabilir fiyat bloğu */}
          <div
            onClick={handlePriceClick}
            title={canToggle ? (showTry ? 'USD göster' : 'TL göster') : undefined}
            className={`inline-flex items-baseline gap-1.5 rounded-lg px-1 -mx-1 transition-colors ${
              canToggle ? 'cursor-pointer hover:bg-gray-50 active:bg-gray-100' : ''
            }`}
          >
            {showTry && tryPrice != null ? (
              <>
                <span className="text-xl font-black text-gray-900">
                  {tryPrice.toLocaleString('tr-TR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                </span>
                <span className="text-sm font-semibold text-red-500">₺</span>
              </>
            ) : (
              <>
                <span className="text-xl font-black text-gray-900">
                  {fmt(spot.displayPrice)}
                </span>
                <span className="text-sm font-semibold text-gray-400">USD</span>
              </>
            )}
            {canToggle && (
              <span className="text-[10px] text-gray-300 ml-0.5">
                {showTry ? '↩ USD' : '↩ ₺'}
              </span>
            )}
          </div>

          <p className="text-xs text-gray-500 mt-0.5">
            / {meta.unit}
            {spot.centConverted && (
              <span className="ml-1 text-gray-400">
                (ham: {fmt(spot.rawPrice, 2)} {spot.rawCurrency})
              </span>
            )}
          </p>

          {pct && (
            <div className={`flex items-center gap-1 mt-2 text-xs font-bold ${isPos ? 'text-emerald-600' : 'text-rose-600'}`}>
              {isPos
                ? <TrendingUp className="w-3.5 h-3.5" />
                : <TrendingDown className="w-3.5 h-3.5" />}
              {pct.text}
              {spot.change != null && (
                <span className="font-normal text-gray-400 ml-1">
                  ({isPos ? '+' : ''}{fmt(spot.change, 4)})
                </span>
              )}
            </div>
          )}
        </div>
      ) : (
        <div className="flex items-center gap-1.5 text-xs text-gray-400 mt-2">
          <AlertCircle className="w-3.5 h-3.5" />
          Veri alınamadı
        </div>
      )}

      {/* Alt: sembol + kaynak */}
      <div className="flex items-center justify-between mt-3 pt-3 border-t border-gray-100">
        <span className="text-[10px] font-mono text-gray-400 bg-gray-50 px-1.5 py-0.5 rounded">
          {meta.symbol}
        </span>
        <span className="text-[10px] text-gray-400">Yahoo Finance</span>
      </div>
    </button>
  );
}
