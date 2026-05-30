import { parseTrNumber, formatPrice } from '../../../../utils/numberFormat';
import { useTranslation } from '../../../../context/LanguageContext';

export default function ViopPriceRange({ contract }) {
  const { t } = useTranslation();
  if (!contract) return null;

  const low = parseTrNumber(contract.low);
  const high = parseTrNumber(contract.high);
  const last = parseTrNumber(contract.lastPrice);

  // Geçersiz veriler varsa gösterme
  if (low === null || high === null || last === null) return null;

  // Pozisyon hesapla
  let positionPercent = 50; // Varsayılan
  if (high !== low) {
    positionPercent = ((last - low) / (high - low)) * 100;
    // 0-100 arasında clamp et
    positionPercent = Math.max(0, Math.min(100, positionPercent));
  }

  return (
    <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">
      <h2 className="font-bold text-gray-900 mb-4">{t('Gün İçi Fiyat Aralığı')}</h2>

      {/* Değerler */}
      <div className="flex justify-between items-center mb-3">
        <div className="text-left">
          <p className="text-xs text-gray-500 mb-1">{t('Düşük')}</p>
          <p className="text-sm font-bold text-gray-900">{formatPrice(low)}</p>
        </div>
        <div className="text-center">
          <p className="text-xs text-blue-600 font-semibold mb-1">{t('Son')}</p>
          <p className="text-base font-black text-blue-900">{formatPrice(last)}</p>
        </div>
        <div className="text-right">
          <p className="text-xs text-gray-500 mb-1">{t('Yüksek')}</p>
          <p className="text-sm font-bold text-gray-900">{formatPrice(high)}</p>
        </div>
      </div>

      {/* Range Bar */}
      <div className="relative h-2 bg-gray-200 rounded-full overflow-hidden">
        {/* Dolgu */}
        <div 
          className="absolute left-0 top-0 h-full bg-gradient-to-r from-emerald-400 to-emerald-500 transition-all duration-300"
          style={{ width: `${positionPercent}%` }}
        />
        
        {/* İşaretçi */}
        <div 
          className="absolute top-1/2 -translate-y-1/2 w-3 h-3 bg-blue-600 rounded-full border-2 border-white shadow-lg transition-all duration-300"
          style={{ left: `${positionPercent}%`, transform: 'translate(-50%, -50%)' }}
        />
      </div>

      {/* Yüzde göstergesi */}
      <div className="mt-2 text-center">
        <p className="text-xs text-gray-400">
          {t('Aralığın')} <span className="font-semibold text-gray-600">%{positionPercent.toFixed(1)}</span>{t('\'inde')}
        </p>
      </div>
    </div>
  );
}
