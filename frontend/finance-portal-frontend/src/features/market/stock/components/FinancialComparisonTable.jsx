import BounceDots from './BounceDots';
import { INDEX_SHORTCUTS, COLORS } from '../utils/stockCompareUtils';

// ── Tablo metrikleri (Midas) ───────────────────────────────────────────────
const TABLE_ROWS = [
  { label: 'Hisse Senedi Fiyatı',  key: 'currentPrice' },
  { label: 'Sermaye',              key: 'capital' },
  { label: 'Piyasa Değeri (TL)',   key: 'marketCap' },
  { label: 'F/K',                  key: 'peRatio' },
  { label: 'Hacim (mTL)',          key: 'dailyVolume' },
  { label: 'Net Kâr',              key: 'netProfit' },
  { label: 'Günlük Değişim (%)',   key: 'dailyChangePercent' },
  { label: 'Haftalık En Yüksek',   key: 'weeklyHigh' },
  { label: 'Haftalık En Düşük',    key: 'weeklyLow' },
  { label: 'Aylık En Yüksek',      key: 'monthlyHigh' },
  { label: 'Aylık En Düşük',       key: 'monthlyLow' },
];

/**
 * "Finansal Karşılaştırma" kartı — Midas verileri (fiyat, sermaye, piyasa değeri, F/K, hacim...).
 * SAF sunum: midasDetails/detailsLoading prop olarak gelir; endeks sembolleri filtrelenir, hepsi
 * boş satırlar gizlenir. StockComparePage'den taşındı; JSX/Tailwind class'ları birebir aynı.
 */
export default function FinancialComparisonTable({ selectedSymbols, midasDetails, detailsLoading, t }) {
  const stockSymbols = selectedSymbols.filter(sym =>
    !INDEX_SHORTCUTS.some(idx => idx.symbol === sym)
  );
  if (stockSymbols.length === 0) return null;

  function getCellValue(symbol, key) {
    const d = midasDetails[symbol];
    if (!d) return '-';
    const val = d[key];
    if (val == null || val === '') return '-';
    return val;
  }

  function getCellStyle(key, value) {
    if (key === 'dailyChangePercent' && value !== '-') {
      const str = String(value);
      if (str.startsWith('-')) return 'text-rose-600 font-semibold';
      if (str !== '0' && str !== '0.00' && str !== '%0') return 'text-emerald-600 font-semibold';
    }
    return 'text-gray-800';
  }

  return (
    <div className="bg-white rounded-lg border border-gray-200 shadow-sm overflow-hidden">
      <div className="px-6 py-4 border-b border-gray-100">
        <h2 className="font-bold text-gray-900">{t('Finansal Karşılaştırma')}</h2>
        <p className="text-xs text-gray-400 mt-0.5">{t('Kaynak: Midas · Veriler 15 dk gecikmeli')}</p>
      </div>
      {detailsLoading ? (
        <div className="p-12 flex items-center justify-center"><BounceDots /></div>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full min-w-[560px]">
            <thead className="bg-gray-50">
              <tr>
                <th className="text-left px-5 py-3 text-xs font-bold text-gray-500 uppercase tracking-wider border-b border-gray-200 w-44">{t('Metrik')}</th>
                {stockSymbols.map((sym) => {
                  const globalIdx = selectedSymbols.indexOf(sym);
                  return (
                  <th key={sym} className="text-right px-5 py-3 text-xs font-bold uppercase tracking-wider border-b border-gray-200 whitespace-nowrap" style={{ color: COLORS[globalIdx % COLORS.length] }}>
                    <div className="flex items-center justify-end gap-1.5">
                      <span className="w-2.5 h-2.5 rounded-full flex-shrink-0" style={{ background: COLORS[globalIdx % COLORS.length] }} />
                      {sym.replace('.IS', '').toUpperCase()}
                    </div>
                    {midasDetails[sym]?.name && <p className="text-gray-400 font-normal normal-case text-xs mt-0.5 truncate max-w-[140px] ml-auto">{midasDetails[sym].name}</p>}
                  </th>
                  );
                })}
              </tr>
            </thead>
            <tbody>
              {TABLE_ROWS.filter(row => {
                return stockSymbols.some(sym => {
                  const val = getCellValue(sym, row.key);
                  return val !== '-' && val != null && val !== '';
                });
              }).map((row, rowIdx) => (
                <tr key={row.key} className={`border-t border-gray-100 hover:bg-gray-50 ${rowIdx % 2 === 0 ? '' : 'bg-gray-50/40'}`}>
                  <td className="px-5 py-3 text-sm text-gray-500 font-medium whitespace-nowrap">{t(row.label)}</td>
                  {stockSymbols.map(sym => {
                    const val = getCellValue(sym, row.key);
                    return <td key={sym} className={`px-5 py-3 text-sm text-right font-mono ${getCellStyle(row.key, val)}`}>{val}</td>;
                  })}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
