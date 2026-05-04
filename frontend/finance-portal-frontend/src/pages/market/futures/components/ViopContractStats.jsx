import { parseTrNumber, formatPrice } from '../../../../utils/numberFormat';

export default function ViopContractStats({ contract }) {
  if (!contract) return null;

  const fmt = (val) => {
    const num = parseTrNumber(val);
    return num !== null ? formatPrice(num) : '-';
  };

  // Akbank VİOP verileri
  const stats = [
    { label: 'Son Fiyat', value: fmt(contract.lastPrice), highlight: true },
    { label: 'Günlük En Yüksek', value: fmt(contract.high) },
    { label: 'Günlük En Düşük', value: fmt(contract.low) },
    { label: 'Uzlaşma Fiyatı', value: fmt(contract.settlementPrice) },
    { label: 'Önceki Uzlaşma', value: fmt(contract.prevSettlementPrice) },
    { label: 'Değişim (%)', value: contract.changePercent || '-' },
  ];

  return (
    <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">
      <div className="flex items-center justify-between mb-4">
        <h2 className="font-bold text-gray-900">Fiyat Bilgileri</h2>
      </div>
      
      <div className="grid grid-cols-2 sm:grid-cols-3 gap-4">
        {stats.map((stat, i) => (
          <div key={i} className={`rounded-xl p-4 text-center ${stat.highlight ? 'bg-blue-50 border border-blue-200' : 'bg-gray-50'}`}>
            <p className={`text-xs font-semibold mb-1.5 ${stat.highlight ? 'text-blue-600' : 'text-gray-500'}`}>
              {stat.label}
            </p>
            <p className={`text-lg font-bold ${stat.highlight ? 'text-blue-900' : 'text-gray-900'}`}>
              {stat.value}
            </p>
          </div>
        ))}
      </div>
      
      {/* Bilgi notu */}
      <div className="mt-4 bg-blue-50 rounded-xl p-3 border border-blue-200">
        <p className="text-xs text-blue-900 leading-relaxed">
          <span className="font-semibold">Kaynak:</span> Veriler Akbank VİOP'tan alınmaktadır.
        </p>
      </div>
    </div>
  );
}
