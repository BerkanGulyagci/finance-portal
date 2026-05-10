import { Trash2 } from 'lucide-react';

function fmt(v, dec = 2) {
  if (v == null) return '-';
  const n = parseFloat(v);
  if (isNaN(n)) return '-';
  return n.toLocaleString('tr-TR', { minimumFractionDigits: dec, maximumFractionDigits: dec });
}

const ASSET_LABELS = {
  STOCK: 'Hisse', CRYPTO: 'Kripto', FX: 'Döviz',
  FUND: 'Fon', FUTURE: 'Vadeli', GOLD: 'Altın',
  COMMODITY: 'Emtia', BOND: 'Tahvil',
};

/**
 * Props:
 *   transactions: PortfolioTransactionResponse[]
 *   onDelete(txId): void
 *   deletingId: string | null
 */
export default function TransactionsTable({ transactions = [], onDelete, deletingId }) {
  if (!transactions.length) {
    return (
      <div className="p-12 text-center text-gray-400 text-sm">
        Henüz işlem yok.
      </div>
    );
  }

  return (
    <div className="overflow-x-auto">
      <table className="w-full">
        <thead className="bg-gray-50">
          <tr>
            {['Tarih', 'İşlem', 'Sembol', 'Tür', 'Miktar', 'Fiyat', 'Toplam', 'Komisyon', ''].map(h => (
              <th key={h} className="text-left px-4 py-3 text-xs font-bold text-gray-500 uppercase tracking-wider border-b border-gray-200 whitespace-nowrap">
                {h}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {transactions.map((t, i) => {
            const total = parseFloat(t.quantity ?? 0) * parseFloat(t.price ?? 0);
            const isBuy = t.transactionType === 'BUY';
            return (
              <tr key={i} className="border-t border-gray-100 hover:bg-gray-50 transition-colors">
                <td className="px-4 py-3 text-xs text-gray-400 whitespace-nowrap">
                  {t.transactionDate?.split('T')[0] ?? '-'}
                </td>
                <td className="px-4 py-3">
                  <span className={`text-xs font-bold px-2 py-0.5 rounded-full ${
                    isBuy ? 'bg-emerald-100 text-emerald-700' : 'bg-rose-100 text-rose-700'
                  }`}>
                    {isBuy ? 'ALIŞ' : 'SATIŞ'}
                  </span>
                </td>
                <td className="px-4 py-3 font-bold text-[#093eaa] text-sm">{t.symbol}</td>
                <td className="px-4 py-3">
                  <span className="text-xs bg-gray-100 text-gray-600 px-2 py-0.5 rounded-full font-semibold">
                    {ASSET_LABELS[t.assetType] ?? t.assetType}
                  </span>
                </td>
                <td className="px-4 py-3 text-sm font-mono">{fmt(t.quantity, 4)}</td>
                <td className="px-4 py-3 text-sm">{fmt(t.price)}</td>
                <td className="px-4 py-3 text-sm font-semibold">{fmt(total)}</td>
                <td className="px-4 py-3 text-sm text-gray-400">{fmt(t.commission)}</td>
                <td className="px-4 py-3">
                  {onDelete && (
                    <button
                      onClick={() => onDelete(t.id)}
                      disabled={deletingId === t.id}
                      className="text-gray-300 hover:text-rose-500 transition-colors disabled:opacity-40"
                      title="İşlemi sil"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  )}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
