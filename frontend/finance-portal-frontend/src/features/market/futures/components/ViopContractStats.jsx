import { parseTrNumber, formatPrice } from '../../../../utils/numberFormat';
import { useTranslation } from '../../../../context/LanguageContext';

export default function ViopContractStats({ contract }) {
  const { t } = useTranslation();
  if (!contract) return null;

  const fmt = (val) => {
    const num = parseTrNumber(val);
    return num !== null ? formatPrice(num) : '-';
  };

  const changeNum = parseTrNumber(contract.changePercent);
  const changeColor = changeNum == null
    ? null
    : changeNum > 0 ? 'text-emerald-600' : changeNum < 0 ? 'text-rose-600' : null;

  // Akbank VİOP verileri
  const stats = [
    { label: 'Son Fiyat', value: fmt(contract.lastPrice), highlight: true },
    { label: 'Günlük En Yüksek', value: fmt(contract.high) },
    { label: 'Günlük En Düşük', value: fmt(contract.low) },
    { label: 'Uzlaşma Fiyatı', value: fmt(contract.settlementPrice) },
    { label: 'Önceki Uzlaşma', value: fmt(contract.prevSettlementPrice) },
    { label: 'Değişim (%)', value: contract.changePercent || '-', color: changeColor },
  ];

  return (
    <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">
      <h2 className="font-bold text-gray-900 mb-4">{t('Fiyat Bilgileri')}</h2>

      <div className="grid grid-cols-2 gap-3">
        {stats.map((stat, i) => (
          <div
            key={i}
            className={`rounded-xl p-3.5 text-center ${stat.highlight ? 'bg-[#093eaa]/[0.06] border border-[#093eaa]/20' : 'bg-gray-50 border border-transparent'}`}
          >
            <p className={`text-[11px] font-semibold mb-1.5 ${stat.highlight ? 'text-[#093eaa]' : 'text-gray-500'}`}>
              {t(stat.label)}
            </p>
            <p className={`text-base font-bold ${stat.color ?? (stat.highlight ? 'text-[#093eaa]' : 'text-gray-900')}`}>
              {stat.value}
            </p>
          </div>
        ))}
      </div>

      <p className="text-xs text-gray-400 mt-4">
        {t('Kaynak:')} {t('Veriler Akbank VİOP\'tan alınmaktadır.')}
      </p>
    </div>
  );
}
