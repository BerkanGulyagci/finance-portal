import { useTranslation } from '../../../../context/LanguageContext';

/**
 * Değerli metal (Altın/Gümüş/Platin/Paladyum) sayfaları için kompakt fiyat bilgileri kartı.
 * VİOP "Fiyat Bilgileri" kartı (ViopContractStats) stilinde: 2'li kutu-grid, label üstte küçük +
 * değer altta bold; ilk öğe highlight (Son/Güncel Fiyat).
 *
 * BOŞ alanlar OTOMATİK GİZLENİR — value null/'-'/'' olan kutular hiç render edilmez
 * (ör. gram/teorik üründe hacim-miktar yok → kart boş kutu bırakmaz).
 *
 * @param stats [{ label, value, highlight?, color? }]  — value '-' veya null ise atlanır
 * @param source  opsiyonel kaynak notu (altta küçük gri)
 */
export default function PreciousStatsCard({ title = 'Fiyat Bilgileri', stats = [], source }) {
  const { t } = useTranslation();

  // Boş gelenler görünmesin: value yok / '-' / boş string olanları ele.
  const visible = stats.filter(s => {
    const v = s.value;
    return v != null && v !== '-' && String(v).trim() !== '';
  });
  if (!visible.length) return null;

  return (
    <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-4 sm:p-5 h-full">
      <h2 className="font-bold text-gray-900 mb-3 text-sm sm:text-base">{t(title)}</h2>

      <div className="grid grid-cols-2 gap-2.5">
        {visible.map((stat, i) => (
          <div
            key={i}
            className={`rounded-xl p-3 text-center ${
              stat.highlight
                ? 'bg-[#093eaa]/[0.06] border border-[#093eaa]/20'
                : 'bg-gray-50 border border-transparent'
            }`}
          >
            <p className={`text-[10px] font-semibold mb-1 ${stat.highlight ? 'text-[#093eaa]' : 'text-gray-500'}`}>
              {t(stat.label)}
            </p>
            <p className={`text-sm font-bold ${stat.color ?? (stat.highlight ? 'text-[#093eaa]' : 'text-gray-900')}`}>
              {stat.value}
            </p>
          </div>
        ))}
      </div>

      {source && (
        <p className="text-[11px] text-gray-400 mt-3">{source}</p>
      )}
    </div>
  );
}
