import { Trash2 } from 'lucide-react';
import { useTranslation } from '../../../i18n/LanguageContext';

function fmt(v, dec = 2) {
  if (v == null) return '-';
  const n = parseFloat(v);
  if (isNaN(n)) return '-';
  return n.toLocaleString('tr-TR', { minimumFractionDigits: dec, maximumFractionDigits: dec });
}

/** Quantity için değerin büyüklüğüne göre yeterli basamak seçer. */
function fmtQty(v) {
  if (v == null) return '-';
  const n = parseFloat(v);
  if (isNaN(n)) return '-';
  const abs = Math.abs(n);
  let dec;
  if (abs === 0)       dec = 2;
  else if (abs >= 100) dec = 2;
  else if (abs >= 1)   dec = 4;
  else if (abs >= 0.01)    dec = 4;
  else if (abs >= 0.0001)  dec = 6;
  else if (abs >= 0.000001) dec = 8;
  else dec = 10;
  return n.toLocaleString('tr-TR', { minimumFractionDigits: dec, maximumFractionDigits: dec });
}

const ASSET_LABELS = {
  STOCK: 'Hisse', CRYPTO: 'Kripto', FX: 'Döviz',
  FUND: 'Fon', FUTURE: 'Vadeli', GOLD: 'Altın',
  COMMODITY: 'Emtia', BOND: 'DİBS',
};

function txBondMix(transactions) {
  if (!transactions?.length) {
    return { hasBond: false, onlyBond: false, mixed: false };
  }
  const hasBond = transactions.some(t => String(t.assetType ?? '').toUpperCase() === 'BOND');
  const onlyBond = hasBond && transactions.every(t => String(t.assetType ?? '').toUpperCase() === 'BOND');
  const mixed = hasBond && !onlyBond;
  return { hasBond, onlyBond, mixed };
}

/**
 * Props:
 *   transactions: PortfolioTransactionResponse[]
 *   onDelete(txId): void
 *   deletingId: string | null
 */
export default function TransactionsTable({ transactions = [], onDelete, deletingId }) {
  const { t: tr } = useTranslation();
  if (!transactions.length) {
    return (
      <div className="p-12 text-center text-gray-400 text-sm">
        {tr('Henüz işlem yok.')}
      </div>
    );
  }

  const mix = txBondMix(transactions);
  const priceHeader = mix.onlyBond
    ? tr('Gösterge Değeri')
    : mix.mixed
      ? tr('Fiyat / Gösterge')
      : tr('Fiyat');

  const headers = [tr('Tarih'), tr('İşlem'), tr('Sembol'), tr('Tür'), tr('Miktar'), priceHeader, tr('Toplam'), tr('Komisyon'), ''];

  return (
    <div className="overflow-x-auto">
      <table className="w-full">
        <thead className="bg-gray-50">
          <tr>
            {headers.map((h, idx) => (
              <th key={`${h}-${idx}`} className="text-left px-4 py-3 text-xs font-bold text-gray-500 uppercase tracking-wider border-b border-gray-200 whitespace-nowrap">
                {h}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {transactions.map((t) => {
            const total = parseFloat(t.quantity ?? 0) * parseFloat(t.price ?? 0);
            const isBuy = t.transactionType === 'BUY';
            const rowKey = t.id ?? `${t.transactionDate}-${t.symbol}-${t.transactionType}`;
            return (
              <tr key={rowKey} className="border-t border-gray-100 hover:bg-gray-50 transition-colors">
                <td className="px-4 py-3 text-xs text-gray-400 whitespace-nowrap">
                  {t.transactionDate?.split('T')[0] ?? '-'}
                </td>
                <td className="px-4 py-3">
                  <span className={`text-xs font-bold px-2 py-0.5 rounded-md ${
                    isBuy ? 'bg-emerald-100 text-emerald-700' : 'bg-rose-100 text-rose-700'
                  }`}>
                    {isBuy ? tr('ALIŞ') : tr('SATIŞ')}
                  </span>
                </td>
                <td className="px-4 py-3 font-bold text-[#093eaa] text-sm">{t.symbol}</td>
                <td className="px-4 py-3">
                  <span className="text-xs bg-gray-100 text-gray-600 px-2 py-0.5 rounded-md font-semibold">
                    {ASSET_LABELS[t.assetType] ? tr(ASSET_LABELS[t.assetType]) : t.assetType}
                  </span>
                </td>
                <td className="px-4 py-3 text-sm font-mono">{fmtQty(t.quantity)}</td>
                <td className="px-4 py-3 text-sm">{fmt(t.price)}</td>
                <td className="px-4 py-3 text-sm font-semibold">{fmt(total)}</td>
                <td className="px-4 py-3 text-sm text-gray-400">{fmt(t.commission)}</td>
                <td className="px-4 py-3">
                  {onDelete && (
                    <button
                      type="button"
                      onClick={() => onDelete(t.id)}
                      disabled={!t.id || deletingId === t.id}
                      className="text-gray-500 hover:text-rose-600 transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
                      title={t.id ? tr('İşlemi sil') : tr('İşlem kimliği eksik — sayfayı yenileyin')}
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
