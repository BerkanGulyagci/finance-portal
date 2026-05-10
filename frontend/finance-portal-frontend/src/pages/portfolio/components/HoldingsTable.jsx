import { Trash2 } from 'lucide-react';

function fmt(v, dec = 2) {
  if (v == null) return '-';
  const n = parseFloat(v);
  if (isNaN(n)) return '-';
  return n.toLocaleString('tr-TR', { minimumFractionDigits: dec, maximumFractionDigits: dec });
}

function PnlCell({ value }) {
  const n = parseFloat(value ?? 0);
  const pos = n >= 0;
  return (
    <span className={`font-bold ${pos ? 'text-emerald-600' : 'text-rose-600'}`}>
      {pos ? '+' : ''}{fmt(n)}
    </span>
  );
}

const ASSET_LABELS = {
  STOCK: 'Hisse', CRYPTO: 'Kripto', FX: 'Döviz',
  FUND: 'Fon', FUTURE: 'Vadeli', GOLD: 'Altın',
  COMMODITY: 'Emtia', BOND: 'Tahvil',
};

/**
 * Props:
 *   holdings: PortfolioHoldingResponse[]
 */
export default function HoldingsTable({ holdings = [] }) {
  if (!holdings.length) {
    return (
      <div className="p-12 text-center text-gray-400 text-sm">
        Henüz varlık yok. "Pozisyon Ekle" butonuna basarak başlayın.
      </div>
    );
  }

  return (
    <div className="overflow-x-auto">
      <table className="w-full">
        <thead className="bg-gray-50">
          <tr>
            {['Sembol', 'Tür', 'Miktar', 'Ort. Alış', 'Güncel Fiyat', 'Toplam Maliyet', 'Piyasa Değeri', 'Kâr/Zarar'].map(h => (
              <th key={h} className="text-left px-4 py-3 text-xs font-bold text-gray-500 uppercase tracking-wider border-b border-gray-200 whitespace-nowrap">
                {h}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {holdings.map((h, i) => {
            const pnl = parseFloat(h.profitLoss ?? 0);
            const cost = parseFloat(h.totalCost ?? 0);
            const pct = cost > 0 ? ((pnl / cost) * 100).toFixed(2) : null;
            return (
              <tr key={i} className="border-t border-gray-100 hover:bg-gray-50 transition-colors">
                <td className="px-4 py-3 font-bold text-[#093eaa] text-sm">{h.symbol}</td>
                <td className="px-4 py-3">
                  <span className="text-xs bg-gray-100 text-gray-600 px-2 py-0.5 rounded-full font-semibold">
                    {ASSET_LABELS[h.assetType] ?? h.assetType}
                  </span>
                </td>
                <td className="px-4 py-3 text-sm font-mono">{fmt(h.totalQuantity, 4)}</td>
                <td className="px-4 py-3 text-sm">{fmt(h.averageCost)}</td>
                <td className="px-4 py-3 text-sm font-semibold">
                  {h.currentPrice ? fmt(h.currentPrice) : <span className="text-gray-300">-</span>}
                </td>
                <td className="px-4 py-3 text-sm">{fmt(h.totalCost)}</td>
                <td className="px-4 py-3 text-sm font-semibold">
                  {h.marketValue ? fmt(h.marketValue) : <span className="text-gray-300">-</span>}
                </td>
                <td className="px-4 py-3 text-sm">
                  <div className="flex flex-col">
                    <PnlCell value={pnl} />
                    {pct && (
                      <span className={`text-xs ${parseFloat(pct) >= 0 ? 'text-emerald-500' : 'text-rose-500'}`}>
                        {parseFloat(pct) >= 0 ? '+' : ''}{pct}%
                      </span>
                    )}
                  </div>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
