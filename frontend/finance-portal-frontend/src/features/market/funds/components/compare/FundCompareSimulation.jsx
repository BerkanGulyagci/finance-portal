import { useState } from 'react';
import { TrendingUp, TrendingDown } from 'lucide-react';
import { COMPARE_COLORS, toNum, calcPeriodReturn } from './compareUtils';
import { useTranslation } from '../../../../../context/LanguageContext';

export default function FundCompareSimulation({ detailMap, selectedCodes, range }) {
  const { t } = useTranslation();
  const [investment, setInvestment] = useState('10000');

  const inv = parseFloat(investment || 0);

  return (
    <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden">
      <div className="px-6 py-4 border-b border-gray-100 flex items-center justify-between flex-wrap gap-3">
        <div>
          <h2 className="font-bold text-gray-900">{t('TL Yatırım Simülasyonu')}</h2>
          <p className="text-xs text-gray-400 mt-0.5">{t('Seçili dönem getirisine göre tahmini değer')}</p>
        </div>
        <div className="flex items-center gap-2">
          <span className="text-sm text-gray-500">{t('Yatırım:')}</span>
          <input
            type="number"
            value={investment}
            onChange={e => setInvestment(e.target.value)}
            className="w-32 px-3 py-1.5 border border-gray-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-[#093eaa]/30 text-right font-mono"
            placeholder="10000"
            min="0"
          />
          <span className="text-sm font-semibold text-gray-600">₺</span>
        </div>
      </div>

      <div className="overflow-x-auto">
        <table className="w-full min-w-[500px]">
          <thead className="bg-gray-50">
            <tr>
              {['Fon', 'Dönem Getirisi', 'Tahmini Değer', 'Kâr / Zarar'].map(h => (
                <th key={h} className="text-left px-5 py-3 text-xs font-bold text-gray-500 uppercase tracking-wider border-b border-gray-200 whitespace-nowrap">{t(h)}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {selectedCodes.map((code, idx) => {
              const d = detailMap[code];
              const ret = calcPeriodReturn(d?.priceHistory ?? [], range);
              const finalVal = (ret != null && !isNaN(inv) && inv > 0)
                ? inv * (1 + ret / 100)
                : null;
              const profit = finalVal != null ? finalVal - inv : null;
              const pos = (profit ?? 0) >= 0;
              const color = COMPARE_COLORS[idx % COMPARE_COLORS.length];

              return (
                <tr key={code} className="border-t border-gray-100 hover:bg-gray-50 transition-colors">
                  <td className="px-5 py-3">
                    <div className="flex items-center gap-2">
                      <span className="w-3 h-3 rounded-full shrink-0" style={{ background: color }} />
                      <span className="font-bold text-sm" style={{ color }}>{code}</span>
                    </div>
                  </td>
                  <td className="px-5 py-3">
                    {ret != null ? (
                      <span className={`text-sm font-bold flex items-center gap-1 ${pos ? 'text-emerald-600' : 'text-rose-600'}`}>
                        {pos ? <TrendingUp className="w-3.5 h-3.5" /> : <TrendingDown className="w-3.5 h-3.5" />}
                        {ret >= 0 ? '+' : ''}{ret.toFixed(2)}%
                      </span>
                    ) : <span className="text-gray-400 text-sm">-</span>}
                  </td>
                  <td className="px-5 py-3 text-sm font-bold text-gray-900">
                    {finalVal != null
                      ? `₺${finalVal.toLocaleString('tr-TR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
                      : '-'}
                  </td>
                  <td className="px-5 py-3">
                    {profit != null ? (
                      <span className={`text-sm font-bold ${pos ? 'text-emerald-600' : 'text-rose-600'}`}>
                        {pos ? '+' : ''}₺{profit.toLocaleString('tr-TR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                      </span>
                    ) : '-'}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

      <div className="px-5 py-2.5 bg-amber-50 border-t border-amber-100">
        <p className="text-xs text-amber-700">
          {t('⚠ Bu simülasyon yatırım tavsiyesi değildir. Seçili dönem normalize getirisi kullanılarak hesaplanmıştır. Geçmiş performans gelecekteki getirilerin garantisi değildir.')}
        </p>
      </div>
    </div>
  );
}
