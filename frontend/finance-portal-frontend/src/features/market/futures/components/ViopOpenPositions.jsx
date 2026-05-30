import { TrendingUp, TrendingDown, Users } from 'lucide-react';
import { parseTrNumber, formatQuantity } from '../../../../utils/numberFormat';
import { useTranslation } from '../../../../context/LanguageContext';

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
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          {/* Açık Pozisyon Sayısı */}
          <div className="bg-[#093eaa]/[0.06] border border-[#093eaa]/20 rounded-xl p-4">
            <p className="text-[11px] text-[#093eaa] font-bold uppercase tracking-wider mb-2">
              {t('Toplam Açık Pozisyon')}
            </p>
            <p className="text-2xl font-black text-[#093eaa] leading-tight">{formatQuantity(openPositionCount)}</p>
            <p className="text-xs text-[#093eaa]/70 mt-1">{t('Kontrat')}</p>
          </div>

          {/* Açık Pozisyon Değişimi */}
          <div className={`rounded-xl p-4 border ${isPositiveChange ? 'bg-emerald-50 border-emerald-200' : 'bg-rose-50 border-rose-200'}`}>
            <p className={`text-[11px] font-bold uppercase tracking-wider mb-2 ${isPositiveChange ? 'text-emerald-600' : 'text-rose-600'}`}>
              {t('Açık Pozisyon Değişimi')}
            </p>
            <div className="flex items-center gap-1.5">
              {isPositiveChange ? (
                <TrendingUp className="w-5 h-5 text-emerald-600 shrink-0" />
              ) : (
                <TrendingDown className="w-5 h-5 text-rose-600 shrink-0" />
              )}
              <p className={`text-2xl font-black leading-tight ${isPositiveChange ? 'text-emerald-700' : 'text-rose-700'}`}>
                {isPositiveChange && openPositionChange > 0 ? '+' : ''}{formatQuantity(openPositionChange)}
              </p>
            </div>
            <p className={`text-xs mt-1 ${isPositiveChange ? 'text-emerald-600' : 'text-rose-600'}`}>
              {t('Kontrat')}
            </p>
          </div>
        </div>

        {/* Bilgi notu */}
        <div className="bg-gray-50 rounded-xl p-4 border border-gray-100">
          <p className="text-xs text-gray-600 leading-relaxed">
            <span className="font-semibold">{t('Açık Pozisyon:')}</span> {t('Henüz kapatılmamış vadeli işlem sözleşmelerinin toplam sayısıdır. Yüksek açık pozisyon, piyasada yüksek likidite ve ilgi olduğunu gösterir.')}
          </p>
        </div>
      </div>
    </div>
  );
}
