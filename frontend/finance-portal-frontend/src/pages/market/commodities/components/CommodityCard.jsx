import { useState } from 'react';
import { TrendingUp, TrendingDown, AlertCircle } from 'lucide-react';
import {
  CATEGORY_META,
  fmt,
  fmtPct,
  fmtTry,
  getPrimarySpotPrice,
  getTrySpotPrice,
  canToggleTry,
} from './commodityConstants';
import { useTranslation } from '../../../../i18n/LanguageContext';

export default function CommodityCard({ meta, spot, loading, onClick }) {
  const { t } = useTranslation();
  const catMeta = CATEGORY_META[meta.category] ?? CATEGORY_META.ENERGY;
  const pct = spot ? fmtPct(spot.changePercent) : null;
  const isPos = pct ? pct.value >= 0 : null;

  const [showTry, setShowTry] = useState(false);
  const primary = spot ? getPrimarySpotPrice(spot) : null;
  const tryPrice = spot ? getTrySpotPrice(spot) : null;
  const toggleable = canToggleTry(spot);

  function handlePriceClick(e) {
    e.stopPropagation();
    if (toggleable) setShowTry((v) => !v);
  }

  return (
    <button
      onClick={onClick}
      className="w-full h-full text-left bg-white rounded-2xl border border-gray-200 shadow-sm hover:shadow-md hover:border-gray-300 hover:-translate-y-0.5 transition-all duration-200 p-5 group min-h-[200px] flex flex-col"
    >
      {/* Başlık + kategori rozetleri */}
      <div className="flex items-start justify-between gap-2 mb-4">
        <div>
          <p className="font-bold text-gray-900 text-sm group-hover:text-[#093eaa] transition-colors">
            {meta.displayNameTr}
          </p>
          <p className="text-xs text-gray-400 mt-0.5">{meta.displayNameEn}</p>
        </div>
        <div className="flex flex-col items-end gap-1 shrink-0">
          <span
            className={`text-[10px] font-bold px-2 py-0.5 rounded-full ${catMeta.bg} ${catMeta.color} border ${catMeta.border}`}
          >
            {t(catMeta.label)}
          </span>
          <span className="text-[10px] font-semibold px-2 py-0.5 rounded-full bg-amber-50 text-amber-700 border border-amber-200">
            {t('Vadeli')}
          </span>
        </div>
      </div>

      {/* Fiyat alanı */}
      <div className="flex-1 flex flex-col justify-center">
        {loading ? (
          <div className="space-y-2">
            <div className="h-7 bg-gray-100 rounded animate-pulse w-32" />
            <div className="h-4 bg-gray-100 rounded animate-pulse w-20" />
          </div>
        ) : spot && !spot.stale && primary ? (
          <div>
            <div
              onClick={handlePriceClick}
              title={toggleable ? (showTry ? t('USD göster') : t('TL göster')) : undefined}
              className={`rounded-lg px-1 -mx-1 transition-colors ${
                toggleable ? 'cursor-pointer hover:bg-gray-50 active:bg-gray-100' : ''
              }`}
            >
              {showTry && tryPrice != null ? (
                <>
                  <div className="inline-flex items-baseline gap-1.5">
                    <span className="text-2xl font-black text-gray-900">{fmtTry(tryPrice)}</span>
                    <span className="text-sm font-semibold text-red-500">TRY</span>
                    {toggleable && <span className="text-[10px] text-gray-300 ml-0.5">↩ USD</span>}
                  </div>
                  <p className="text-[11px] text-gray-400 mt-0.5">
                    ≈ {fmt(primary.value, primary.decimals)} {primary.currency} / {meta.unit}
                  </p>
                </>
              ) : (
                <>
                  <div className="inline-flex items-baseline gap-1.5">
                    <span className="text-2xl font-black text-gray-900">
                      {fmt(primary.value, primary.decimals)}
                    </span>
                    <span className="text-sm font-semibold text-gray-500">
                      {primary.currency} / {meta.unit}
                    </span>
                    {toggleable && <span className="text-[10px] text-gray-300 ml-0.5">↩ ₺</span>}
                  </div>
                  {tryPrice != null && (
                    <p className="text-[11px] text-gray-400 mt-0.5">
                      ≈ {fmtTry(tryPrice)} TRY / {meta.unit}
                    </p>
                  )}
                </>
              )}
            </div>

            {pct && (
              <div
                className={`inline-flex items-center gap-1 mt-3 px-2 py-1 rounded-lg text-xs font-bold ${
                  isPos ? 'bg-emerald-50 text-emerald-600' : 'bg-rose-50 text-rose-600'
                }`}
              >
                {isPos ? <TrendingUp className="w-3.5 h-3.5" /> : <TrendingDown className="w-3.5 h-3.5" />}
                {pct.text}
                {spot.change != null && (
                  <span className="font-normal text-gray-400 ml-1">
                    ({isPos ? '+' : ''}
                    {fmt(spot.change, 4)})
                  </span>
                )}
              </div>
            )}
          </div>
        ) : (
          <div className="flex items-center gap-1.5 text-xs text-gray-400">
            <AlertCircle className="w-3.5 h-3.5" />
            {t('Veri alınamadı')}
          </div>
        )}
      </div>

      {/* Alt bilgi: sembol + kaynak */}
      <div className="flex items-center justify-between mt-4 pt-3 border-t border-gray-100">
        <span className="text-[10px] font-mono text-gray-400 bg-gray-50 px-1.5 py-0.5 rounded">
          {meta.symbol}
        </span>
        <span className="text-[10px] text-gray-400">Yahoo Finance</span>
      </div>
    </button>
  );
}
