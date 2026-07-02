import { RefreshCw } from 'lucide-react';
import { useTranslation } from '../../../../context/LanguageContext';

const RANGES = [
  { key: '1D', label: '1G' },
  { key: '1W', label: '1H' },
  { key: '1M', label: '1A' },
  { key: '3M', label: '3A' },
  { key: '6M', label: '6A' },
  { key: '1Y', label: '1Y' },
  { key: '5Y', label: '5Y' },
];

export default function CommodityDetailToolbar({
  range, onRangeChange,
  chartMode, onChartModeChange,
  loading, onRefresh,
}) {
  const { t } = useTranslation();
  return (
    <div className="flex flex-wrap items-center gap-3 pb-3 border-b border-gray-100 mb-3">
      {/* Range */}
      <div className="flex gap-1">
        {RANGES.map(r => (
          <button key={r.key} onClick={() => onRangeChange(r.key)}
            className={`px-3 py-1.5 rounded-lg text-xs font-bold transition-all ${
              range === r.key ? 'bg-[#093eaa] text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
            }`}>
            {t(r.label)}
          </button>
        ))}
      </div>

      <div className="w-px h-5 bg-gray-200" />

      {/* Grafik modu — her range'de göster */}
      <div className="flex gap-1">
        {[
          { key: 'line',   label: '〰 Alan' },
          { key: 'candle', label: '🕯 Mum'   },
        ].map(m => (
          <button key={m.key} onClick={() => onChartModeChange(m.key)}
            className={`px-3 py-1.5 rounded-lg text-xs font-bold transition-all ${
              chartMode === m.key ? 'bg-[#093eaa] text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
            }`}>
            {t(m.label)}
          </button>
        ))}
      </div>

      <div className="ml-auto">
        <button onClick={onRefresh} disabled={loading}
          className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold text-gray-600 bg-gray-100 hover:bg-gray-200 rounded-lg transition-all disabled:opacity-50">
          <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} />
          {t('Yenile')}
        </button>
      </div>
    </div>
  );
}
