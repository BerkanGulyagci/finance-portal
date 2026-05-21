import { RefreshCw } from 'lucide-react';
import { RANGES, RANGE_LABELS, CANDLE_DISCLAIMER } from './goldConstants';
import { useTranslation } from '../../../../i18n/LanguageContext';

export default function GoldChartToolbar({
  activeTab,
  range,
  onRangeChange,
  chartMode,
  onChartModeChange,
  showMA7, onToggleMA7,
  showMA30, onToggleMA30,
  showMA90, onToggleMA90,
  showTrend, onToggleTrend,
  loading,
  onRefresh,
}) {
  const { t } = useTranslation();
  // 5Y ve ALL range'de mum grafik desteklenmez (referans endpoint OHLC vermiyor)
  const isLongRange = range === '5Y' || range === 'ALL';
  const canCandle = (activeTab?.canCandle ?? false) && !isLongRange;

  return (
    <div className="space-y-2 mb-4">
      <div className="flex items-center justify-between flex-wrap gap-2">
        {/* Sol: grafik modu (sadece ons ve gram) */}
        <div className="flex items-center gap-1.5 flex-wrap">
          {canCandle && (
            <div className="flex gap-1 bg-gray-100 rounded-lg p-0.5">
              {[
                { key: 'line',   label: 'Çizgi' },
                { key: 'candle', label: 'Mum' },
              ].map(m => (
                <button
                  key={m.key}
                  onClick={() => onChartModeChange(m.key)}
                  className={`px-3 py-1 rounded-md text-xs font-bold transition-all ${
                    chartMode === m.key
                      ? 'bg-white text-[#093eaa] shadow-sm'
                      : 'text-gray-500 hover:text-gray-700'
                  }`}
                >
                  {t(m.label)}
                </button>
              ))}
            </div>
          )}

          {/* İndikatörler */}
          {[
            { key: 'ma7',   label: 'MA7',   active: showMA7,   toggle: onToggleMA7,   color: '#f59e0b' },
            { key: 'ma30',  label: 'MA30',  active: showMA30,  toggle: onToggleMA30,  color: '#a855f7' },
            { key: 'ma90',  label: 'MA90',  active: showMA90,  toggle: onToggleMA90,  color: '#06b6d4' },
            { key: 'trend', label: t('Trend'), active: showTrend, toggle: onToggleTrend, color: '#ef4444' },
          ].map(ind => (
            <button
              key={ind.key}
              onClick={ind.toggle}
              className={`flex items-center gap-1 px-2.5 py-1 rounded-lg text-xs font-bold border transition-all ${
                ind.active
                  ? 'text-white border-transparent'
                  : 'border-gray-200 bg-gray-50 text-gray-500 hover:bg-gray-100'
              }`}
              style={ind.active ? { backgroundColor: ind.color } : {}}
            >
              <span
                className="inline-block w-3 h-0.5 rounded"
                style={{ backgroundColor: ind.active ? 'white' : ind.color }}
              />
              {ind.label}
            </button>
          ))}
        </div>

        {/* Sağ: range + yenile */}
        <div className="flex items-center gap-1.5">
          <div className="flex gap-1">
            {RANGES.map(r => (
              <button
                key={r}
                onClick={() => onRangeChange(r)}
                className={`px-3 py-1.5 rounded-lg text-xs font-bold transition-all ${
                  range === r
                    ? 'bg-[#093eaa] text-white'
                    : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
                }`}
              >
                {t(RANGE_LABELS[r])}
              </button>
            ))}
          </div>
          <button
            onClick={onRefresh}
            className="p-1.5 rounded-lg bg-gray-100 hover:bg-gray-200 transition-all"
            title={t('Yenile')}
          >
            <RefreshCw className={`w-3.5 h-3.5 text-gray-500 ${loading ? 'animate-spin' : ''}`} />
          </button>
        </div>
      </div>

      {/* Mum grafik uyarısı */}
      {canCandle && chartMode === 'candle' && (
        <p className="text-xs text-amber-600 bg-amber-50 border border-amber-100 rounded-lg px-3 py-1.5">
          ⚠ {t(CANDLE_DISCLAIMER)}
        </p>
      )}
    </div>
  );
}
