import { RefreshCw } from 'lucide-react';
import { useTranslation } from '../../../../context/LanguageContext';

export default function CommoditiesHeader({ loading, onRefresh, usdTryRate }) {
  const { t } = useTranslation();
  return (
    <div className="flex items-center justify-between">
      <div>
        <h1 className="text-2xl font-bold text-gray-900 border-l-4 border-[#093eaa] pl-4">
          {t('Global Emtialar')}
        </h1>
        <p className="text-sm text-gray-500 mt-1 pl-5">
          {t('Yahoo Finance vadeli kontrat referans verileri')}
          {usdTryRate && (
            <span className="ml-2 text-xs text-gray-400 bg-gray-100 px-2 py-0.5 rounded-full">
              1 USD = {usdTryRate.toLocaleString('tr-TR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} ₺ (TCMB)
            </span>
          )}
        </p>
      </div>
      <button
        onClick={onRefresh}
        disabled={loading}
        className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold text-gray-600 bg-gray-100 hover:bg-gray-200 rounded-lg transition-all disabled:opacity-50"
      >
        <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} />
        {t('Yenile')}
      </button>
    </div>
  );
}
