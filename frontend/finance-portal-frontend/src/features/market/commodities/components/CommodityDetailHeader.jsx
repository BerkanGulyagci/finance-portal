import { ArrowLeft, TrendingUp, TrendingDown } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import {
  CATEGORY_META,
  fmt,
  fmtPct,
  fmtTry,
  getPrimarySpotPrice,
  getTrySpotPrice,
  canToggleTry,
} from './commodityConstants';
import { useTranslation } from '../../../../context/LanguageContext';

function formatOhlcValue(value, spot, usdTryRate) {
  if (value == null) return '-';
  const primary = getPrimarySpotPrice(spot);
  if (spot?.displayCurrency === 'TRY' && usdTryRate && usdTryRate > 0) {
    return `${fmt(value / usdTryRate, primary?.decimals ?? 2)} USD`;
  }
  const cur = primary?.currency || spot?.rawCurrency || 'USD';
  return `${fmt(value, primary?.decimals ?? 2)} ${cur}`;
}

export default function CommodityDetailHeader({ meta, spot, usdTryRate, showTry, onToggleCurrency }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const catMeta = CATEGORY_META[meta?.category] ?? CATEGORY_META.ENERGY;
  const pct = spot ? fmtPct(spot.changePercent) : null;
  const isPos = pct ? pct.value >= 0 : null;

  const primary = spot ? getPrimarySpotPrice(spot) : null;
  const tryPrice = spot ? getTrySpotPrice(spot) : null;
  const toggleable = canToggleTry(spot);

  return (
    <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">
      <button
        onClick={() => navigate('/market/commodities')}
        className="inline-flex items-center gap-1.5 text-sm text-[#093eaa] font-semibold hover:underline mb-4"
      >
        <ArrowLeft className="w-4 h-4" />
        {t('Global Emtialar')}
      </button>

      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <div className="flex items-center gap-2 mb-1">
            <h1 className="text-2xl font-black text-gray-900">{meta?.displayNameTr}</h1>
            <span className="text-sm text-gray-400 font-medium">{meta?.displayNameEn}</span>
          </div>

          <div className="flex items-center gap-2 mb-3">
            <span
              className={`text-xs font-bold px-2 py-0.5 rounded-full ${catMeta.bg} ${catMeta.color} border ${catMeta.border}`}
            >
              {t(catMeta.label)}
            </span>
            <span className="text-xs font-semibold px-2 py-0.5 rounded-full bg-amber-50 text-amber-700 border border-amber-200">
              {t('Vadeli')}
            </span>
            <span className="text-xs font-mono text-gray-400 bg-gray-50 px-1.5 py-0.5 rounded">
              {meta?.symbol}
            </span>
          </div>

          {spot && !spot.stale && primary ? (
            <div>
              <button
                type="button"
                onClick={toggleable ? onToggleCurrency : undefined}
                disabled={!toggleable}
                className={`text-left rounded-xl px-2 py-1 -mx-2 transition-colors ${
                  toggleable ? 'hover:bg-gray-50 cursor-pointer' : 'cursor-default'
                }`}
                title={toggleable ? (showTry ? t('USD göster') : t('TL göster')) : undefined}
              >
                {showTry && tryPrice != null ? (
                  <>
                    <div className="flex items-baseline gap-2 flex-wrap">
                      <span className="text-4xl font-black text-gray-900">{fmtTry(tryPrice)}</span>
                      <span className="text-xl font-bold text-red-500">TRY / {meta?.unit}</span>
                      {toggleable && <span className="text-xs text-gray-300">↩ USD</span>}
                    </div>
                    <p className="text-sm text-gray-400 mt-1">
                      ≈ {fmt(primary.value, primary.decimals)} {primary.currency} / {meta?.unit}
                    </p>
                  </>
                ) : (
                  <>
                    <div className="flex items-baseline gap-2 flex-wrap">
                      <span className="text-4xl font-black text-gray-900">
                        {fmt(primary.value, primary.decimals)}
                      </span>
                      <span className="text-xl font-bold text-gray-500">
                        {primary.currency} / {meta?.unit}
                      </span>
                      {toggleable && <span className="text-xs text-gray-300">↩ ₺</span>}
                    </div>
                    {tryPrice != null && (
                      <p className="text-sm text-gray-400 mt-1">
                        ≈ {fmtTry(tryPrice)} TRY / {meta?.unit}
                      </p>
                    )}
                  </>
                )}
              </button>

              {pct && (
                <div
                  className={`flex items-center gap-1.5 mt-2 ml-2 text-sm font-bold ${
                    isPos ? 'text-emerald-600' : 'text-rose-600'
                  }`}
                >
                  {isPos ? <TrendingUp className="w-4 h-4" /> : <TrendingDown className="w-4 h-4" />}
                  {pct.text}
                  {spot.change != null && (
                    <span className="font-normal text-gray-400">
                      ({isPos ? '+' : ''}
                      {fmt(spot.change, 4)})
                    </span>
                  )}
                </div>
              )}
            </div>
          ) : (
            <p className="text-gray-400 text-sm">{t('Fiyat verisi alınamadı')}</p>
          )}
        </div>

        {spot && !spot.stale && (
          <div className="grid grid-cols-2 gap-3 text-sm">
            {[
              { label: 'Gün Yüksek', value: spot.dayHigh },
              { label: 'Gün Düşük', value: spot.dayLow },
              { label: '52H Yüksek', value: spot.weekHigh52 },
              { label: '52H Düşük', value: spot.weekLow52 },
            ].map((item) => (
              <div key={item.label} className="bg-gray-50 rounded-xl px-3 py-2">
                <p className="text-xs text-gray-400 mb-0.5">{t(item.label)}</p>
                <p className="font-bold text-gray-800">
                  {formatOhlcValue(item.value, spot, usdTryRate)}
                </p>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
