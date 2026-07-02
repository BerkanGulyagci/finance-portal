import { RefreshCw } from 'lucide-react';
import { RANGES, RANGE_LABELS, CANDLE_DISCLAIMER } from '../utils/goldConstants';
import { useTranslation } from '../../../../context/LanguageContext';

export default function GoldChartToolbar({
  activeTab,
  range,
  onRangeChange,
  chartMode,
  onChartModeChange,
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
                { key: 'line',   label: 'Alan' },
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

        </div>

        {/* Sağ: range + yenile */}
        <div className="flex items-center gap-1.5">
          <div className="inline-flex items-center gap-0.5 rounded-lg border border-gray-200 bg-gray-50 p-0.5">
            {RANGES.map(r => (
              <button
                key={r}
                onClick={() => onRangeChange(r)}
                className={`px-3 py-1 rounded-md text-xs font-semibold whitespace-nowrap transition-all ${
                  range === r
                    ? 'bg-white text-[#093eaa] shadow-sm'
                    : 'text-gray-500 hover:text-gray-800'
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
