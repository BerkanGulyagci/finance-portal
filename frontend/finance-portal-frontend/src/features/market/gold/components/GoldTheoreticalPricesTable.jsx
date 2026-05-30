import { GOLD_TABS, fmt } from './goldConstants';
import { useTranslation } from '../../../../context/LanguageContext';

const TABLE_ROWS = [
  { label: 'Gram Altın',        grossWeight: 1,     fineness: 1.0000, spotField: 'gramGoldTry',          unit: '1g' },
  { label: 'Çeyrek Altın',      grossWeight: 1.754, fineness: 0.9166, spotField: 'quarterGoldTry',       unit: '1.754g' },
  { label: 'Yarım Altın',       grossWeight: 3.508, fineness: 0.9166, spotField: 'halfGoldTry',          unit: '3.508g' },
  { label: 'Ziynet Tam Altın',  grossWeight: 7.016, fineness: 0.9166, spotField: 'ziynetGoldTry',        unit: '7.016g' },
  { label: 'Cumhuriyet Altını', grossWeight: 7.216, fineness: 0.9166, spotField: 'republicGoldTry',      unit: '7.216g' },
  { label: '14 Ayar Bilezik',   grossWeight: 1,     fineness: 0.5850, spotField: 'fourteenKBraceletTry', unit: '1g' },
  { label: '22 Ayar Bilezik',   grossWeight: 1,     fineness: 0.9166, spotField: 'twentyTwoKBraceletTry',unit: '1g' },
];

export default function GoldTheoreticalPricesTable({ spot }) {
  const { t } = useTranslation();
  if (!spot) return null;

  return (
    <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden">
      <div className="px-6 py-4 border-b border-gray-100 flex items-center justify-between">
        <h2 className="font-bold text-gray-900">{t('Teorik Referans Fiyatlar')}</h2>
        <span className="text-xs text-gray-400 bg-gray-50 border border-gray-200 px-2 py-0.5 rounded-full">
          {t('Kaynak: Borsa İstanbul')}
        </span>
      </div>

      <div className="overflow-x-auto">
        <table className="w-full min-w-[560px]">
          <thead className="bg-gray-50">
            <tr>
              {['Tür', 'Brüt Ağırlık', 'Milyem', 'Teorik Fiyat (₺)', 'Açıklama'].map(h => (
                <th
                  key={h}
                  className="text-left px-5 py-3 text-xs font-bold text-gray-500 uppercase tracking-wider border-b border-gray-200"
                >
                  {t(h)}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {TABLE_ROWS.map((row, i) => {
              const price = spot[row.spotField];
              const valid = price != null && !isNaN(parseFloat(price));
              return (
                <tr key={i} className="border-t border-gray-100 hover:bg-gray-50 transition-colors">
                  <td className="px-5 py-3 text-sm font-bold text-gray-800">{t(row.label)}</td>
                  <td className="px-5 py-3 text-sm text-gray-600">{row.unit}</td>
                  <td className="px-5 py-3 text-sm text-gray-600 font-mono">{row.fineness.toFixed(4)}</td>
                  <td className="px-5 py-3 text-sm font-semibold text-gray-900">
                    {valid ? `₺${fmt(price)}` : '-'}
                  </td>
                  <td className="px-5 py-3 text-xs text-gray-400">
                    {row.grossWeight === 1
                      ? `${row.fineness.toFixed(4)} × ${t('gram referans')}`
                      : `${row.grossWeight}g × ${row.fineness.toFixed(4)} × ${t('gram referans')}`}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

      <div className="px-5 py-2.5 bg-gray-50 border-t border-gray-100 text-xs text-gray-400">
        {t('Gram referans = BIST Ağırlıklı Ortalama TL/Kg ÷ 1000 =')} ₺{fmt(spot.officialPureGoldGramTry)} &nbsp;|&nbsp;
        {t('Bu değerler alış/satış fiyatı değildir. İşçilik, basım primi, kuyumcu kârı ve alış-satış makası dahil değildir.')}
      </div>
    </div>
  );
}
