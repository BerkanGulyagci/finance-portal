import { ArrowLeft, TrendingUp, TrendingDown } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { CATEGORY_META, fmt, fmtPct } from './commodityConstants';

export default function CommodityDetailHeader({ meta, spot, usdTryRate, showTry, onToggleCurrency }) {
  const navigate = useNavigate();
  const catMeta  = CATEGORY_META[meta?.category] ?? CATEGORY_META.ENERGY;
  const pct      = spot ? fmtPct(spot.changePercent) : null;
  const isPos    = pct ? pct.value >= 0 : null;

  const canToggle = usdTryRate != null && spot?.displayPrice != null;
  const tryPrice  = canToggle ? parseFloat(spot.displayPrice) * usdTryRate : null;

  return (
    <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">
      {/* Geri butonu */}
      <button
        onClick={() => navigate('/market/commodities')}
        className="inline-flex items-center gap-1.5 text-sm text-[#093eaa] font-semibold hover:underline mb-4"
      >
        <ArrowLeft className="w-4 h-4" />
        Global Emtialar
      </button>

      <div className="flex flex-wrap items-start justify-between gap-4">
        {/* Sol: isim + fiyat */}
        <div>
          <div className="flex items-center gap-2 mb-1">
            <h1 className="text-2xl font-black text-gray-900">{meta?.displayNameTr}</h1>
            <span className="text-sm text-gray-400 font-medium">{meta?.displayNameEn}</span>
          </div>

          <div className="flex items-center gap-2 mb-3">
            <span className={`text-xs font-bold px-2 py-0.5 rounded-full ${catMeta.bg} ${catMeta.color} border ${catMeta.border}`}>
              {catMeta.label}
            </span>
            <span className="text-xs font-semibold px-2 py-0.5 rounded-full bg-amber-50 text-amber-700 border border-amber-200">
              Vadeli
            </span>
            <span className="text-xs font-mono text-gray-400 bg-gray-50 px-1.5 py-0.5 rounded">
              {meta?.symbol}
            </span>
          </div>

          {spot && !spot.stale ? (
            <div>
              {/* Tıklanabilir fiyat */}
              <button
                onClick={onToggleCurrency}
                disabled={!canToggle}
                className={`flex items-baseline gap-2 rounded-xl px-2 py-1 -mx-2 transition-colors ${
                  canToggle ? 'hover:bg-gray-50 cursor-pointer' : 'cursor-default'
                }`}
                title={canToggle ? (showTry ? 'USD göster' : 'TL göster') : undefined}
              >
                {showTry && tryPrice != null ? (
                  <>
                    <span className="text-4xl font-black text-gray-900">
                      {tryPrice.toLocaleString('tr-TR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                    </span>
                    <span className="text-xl font-bold text-red-500">₺</span>
                  </>
                ) : (
                  <>
                    <span className="text-4xl font-black text-gray-900">{fmt(spot.displayPrice)}</span>
                    <span className="text-xl font-bold text-gray-400">USD</span>
                  </>
                )}
                {canToggle && (
                  <span className="text-xs text-gray-300 ml-1">{showTry ? '↩ USD' : '↩ ₺'}</span>
                )}
              </button>

              <p className="text-sm text-gray-500 mt-1 ml-2">/ {meta?.unit}</p>

              {pct && (
                <div className={`flex items-center gap-1.5 mt-2 ml-2 text-sm font-bold ${isPos ? 'text-emerald-600' : 'text-rose-600'}`}>
                  {isPos ? <TrendingUp className="w-4 h-4" /> : <TrendingDown className="w-4 h-4" />}
                  {pct.text}
                  {spot.change != null && (
                    <span className="font-normal text-gray-400">
                      ({isPos ? '+' : ''}{fmt(spot.change, 4)})
                    </span>
                  )}
                </div>
              )}

              {spot.centConverted && (
                <p className="text-xs text-gray-400 mt-1 ml-2">
                  Ham: {fmt(spot.rawPrice, 2)} {spot.rawCurrency}
                </p>
              )}
            </div>
          ) : (
            <p className="text-gray-400 text-sm">Fiyat verisi alınamadı</p>
          )}
        </div>

        {/* Sağ: 52 hafta + hacim */}
        {spot && !spot.stale && (
          <div className="grid grid-cols-2 gap-3 text-sm">
            {[
              { label: 'Gün Yüksek',  value: spot.dayHigh   },
              { label: 'Gün Düşük',   value: spot.dayLow    },
              { label: '52H Yüksek',  value: spot.weekHigh52 },
              { label: '52H Düşük',   value: spot.weekLow52  },
            ].map(item => (
              <div key={item.label} className="bg-gray-50 rounded-xl px-3 py-2">
                <p className="text-xs text-gray-400 mb-0.5">{item.label}</p>
                <p className="font-bold text-gray-800">
                  {item.value != null ? `${fmt(item.value)} USD` : '-'}
                </p>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
