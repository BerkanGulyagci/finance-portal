import { TrendingUp, TrendingDown } from 'lucide-react';
import { INDEX_SHORTCUTS, COLORS, calcMetrics } from '../utils/stockCompareUtils';

/**
 * "TL Yatırım Simülasyonu" kartı — seçili gerçek hisseler için, verilen tutarla dönem başı/sonu
 * fiyatlarından alınan lot + nihai değer + kâr/zarar. SAF sunum (investment state'i parent'ta tutulur,
 * prop olarak gelir). StockComparePage'den taşındı; JSX/Tailwind class'ları ve hesaplar birebir aynı.
 */
export default function TlInvestmentSimulation({ selectedSymbols, rawPrices, investment, setInvestment, t }) {
  const stockSymbols = selectedSymbols.filter(sym =>
    !INDEX_SHORTCUTS.some(idx => idx.symbol === sym)
  );
  if (stockSymbols.length === 0 || Object.keys(rawPrices).length === 0) return null;
  return (
    <div className="bg-white rounded-lg border border-gray-200 shadow-sm overflow-hidden">
      <div className="px-6 py-4 border-b border-gray-100 flex items-center gap-4 flex-wrap">
        <h2 className="font-bold text-gray-900">{t('TL Yatırım Simülasyonu')}</h2>
        <div className="flex items-center gap-2 ml-auto">
          <span className="text-sm text-gray-500">{t('Yatırım tutarı:')}</span>
          <input type="number" value={investment} onChange={e => setInvestment(e.target.value)}
            className="w-32 px-3 py-1.5 border border-gray-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-[#093eaa]/30 text-right" placeholder="1000" />
          <span className="text-sm font-semibold text-gray-600">₺</span>
        </div>
      </div>
      <div className="overflow-x-auto">
        <table className="w-full min-w-[600px]">
          <thead className="bg-gray-50">
            <tr>
              {['Hisse', 'Başlangıç Fiyatı', 'Alınan Lot', 'Bitiş Fiyatı', 'Nihai Değer (₺)', 'Kâr/Zarar'].map(h => (
                <th key={h} className="text-left px-5 py-3 text-xs font-bold text-gray-500 uppercase tracking-wider border-b border-gray-200 whitespace-nowrap">{t(h)}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {stockSymbols.map((sym) => {
              const globalIdx = selectedSymbols.indexOf(sym);
              const m = calcMetrics(rawPrices[sym]);
              const inv = parseFloat(investment || 0);
              const units = m && m.startPrice > 0 ? inv / m.startPrice : null;
              const finalVal = units != null ? units * m.endPrice : null;
              const profit = finalVal != null ? finalVal - inv : null;
              const pos = (profit ?? 0) >= 0;
              const fmt2 = v => v == null ? '-' : parseFloat(v).toLocaleString('tr-TR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
              const fmt4 = v => v == null ? '-' : parseFloat(v).toLocaleString('tr-TR', { minimumFractionDigits: 2, maximumFractionDigits: 4 });
              return (
                <tr key={sym} className="border-t border-gray-100 hover:bg-gray-50">
                  <td className="px-5 py-3">
                    <div className="flex items-center gap-2">
                      <span className="w-3 h-3 rounded-full flex-shrink-0" style={{ background: COLORS[globalIdx % COLORS.length] }} />
                      <span className="font-bold text-sm text-gray-900">{sym.replace('.IS', '').toUpperCase()}</span>
                    </div>
                  </td>
                  <td className="px-5 py-3 text-sm font-mono text-gray-700">{m ? `₺${fmt4(m.startPrice)}` : '-'}</td>
                  <td className="px-5 py-3 text-sm font-mono text-gray-700">{units != null ? units.toLocaleString('tr-TR', { maximumFractionDigits: 2 }) : '-'}</td>
                  <td className="px-5 py-3 text-sm font-mono text-gray-700">{m ? `₺${fmt4(m.endPrice)}` : '-'}</td>
                  <td className="px-5 py-3 text-sm font-bold text-gray-900">{finalVal != null ? `₺${fmt2(finalVal)}` : '-'}</td>
                  <td className="px-5 py-3">
                    {profit != null ? (
                      <span className={`text-sm font-bold flex items-center gap-1 ${pos ? 'text-emerald-600' : 'text-rose-600'}`}>
                        {pos ? <TrendingUp className="w-3.5 h-3.5" /> : <TrendingDown className="w-3.5 h-3.5" />}
                        {pos ? '+' : ''}₺{fmt2(profit)}
                      </span>
                    ) : '-'}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
      <div className="px-5 py-2 bg-gray-50 border-t border-gray-100 text-xs text-gray-400">
        {t('* Simülasyon dönem başı ve sonu fiyatları kullanılarak hesaplanmıştır. Komisyon ve vergiler dahil değildir. Gösterge niteliğindedir.')}
      </div>
    </div>
  );
}
