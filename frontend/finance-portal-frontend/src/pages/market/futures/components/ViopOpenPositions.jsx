import { TrendingUp, TrendingDown, Users } from 'lucide-react';
import { parseTrNumber, formatQuantity } from '../../../../utils/numberFormat';
import { useTranslation } from '../../../../i18n/LanguageContext';

export default function ViopOpenPositions({ contract }) {
  const { t } = useTranslation();
  if (!contract) return null;

  // Parse edilmiş sayısal değerler
  const openPositionCount = parseTrNumber(contract.openPositionCount) || 0;
  const openPositionChange = parseTrNumber(contract.openPositionChange) || 0;
  const isPositiveChange = openPositionChange >= 0;

  return (
    <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">
      <h2 className="font-bold text-gray-900 mb-4 flex items-center gap-2">
        <Users className="w-5 h-5 text-[#093eaa]" />
        {t('Açık Pozisyon Bilgileri')}
      </h2>

      <div className="space-y-4">
        {/* Açık Pozisyon Sayısı */}
        <div className="bg-gradient-to-br from-blue-50 to-blue-100 rounded-xl p-5">
          <p className="text-xs text-blue-600 font-bold uppercase tracking-wider mb-2">
            {t('Toplam Açık Pozisyon')}
          </p>
          <p className="text-3xl font-black text-blue-900">{formatQuantity(openPositionCount)}</p>
          <p className="text-xs text-blue-600 mt-1">{t('Kontrat')}</p>
        </div>

        {/* Açık Pozisyon Değişimi */}
        <div className={`rounded-xl p-5 ${isPositiveChange ? 'bg-gradient-to-br from-emerald-50 to-emerald-100' : 'bg-gradient-to-br from-rose-50 to-rose-100'}`}>
          <p className={`text-xs font-bold uppercase tracking-wider mb-2 ${isPositiveChange ? 'text-emerald-600' : 'text-rose-600'}`}>
            {t('Açık Pozisyon Değişimi')}
          </p>
          <div className="flex items-baseline gap-2">
            {isPositiveChange ? (
              <TrendingUp className="w-6 h-6 text-emerald-600" />
            ) : (
              <TrendingDown className="w-6 h-6 text-rose-600" />
            )}
            <p className={`text-3xl font-black ${isPositiveChange ? 'text-emerald-900' : 'text-rose-900'}`}>
              {isPositiveChange && openPositionChange > 0 ? '+' : ''}{formatQuantity(openPositionChange)}
            </p>
          </div>
          <p className={`text-xs mt-1 ${isPositiveChange ? 'text-emerald-600' : 'text-rose-600'}`}>
            {t('Kontrat')}
          </p>
        </div>

        {/* Bilgi notu */}
        <div className="bg-gray-50 rounded-xl p-4 border border-gray-200">
          <p className="text-xs text-gray-600 leading-relaxed">
            <span className="font-semibold">{t('Açık Pozisyon:')}</span> {t('Henüz kapatılmamış vadeli işlem sözleşmelerinin toplam sayısıdır. Yüksek açık pozisyon, piyasada yüksek likidite ve ilgi olduğunu gösterir.')}
          </p>
        </div>
      </div>
    </div>
  );
}
