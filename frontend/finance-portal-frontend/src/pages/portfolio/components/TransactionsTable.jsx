import { useMemo, useState } from 'react';
import { Trash2, Search, X, Coins } from 'lucide-react';
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
 *   goldBondSymbols: Set<string> — altın bond ISIN'leri (BOND /100 istisnası); opsiyonel
 */
export default function TransactionsTable({ transactions = [], onDelete, deletingId, goldBondSymbols }) {
  const isGoldBondSymbol = (sym) => goldBondSymbols instanceof Set && goldBondSymbols.has(String(sym ?? '').toUpperCase());
  const { t: tr } = useTranslation();

  // ── Filtreler ────────────────────────────────────────────────────────────
  const [search, setSearch]       = useState('');
  const [typeFilter, setTypeFilter] = useState('ALL'); // ALL | BUY | SELL | COUPON_INCOME
  const [dateFrom, setDateFrom]   = useState('');
  const [dateTo, setDateTo]       = useState('');

  const filtered = useMemo(() => {
    const q = search.trim().toUpperCase();
    return (transactions || []).filter(t => {
      if (typeFilter !== 'ALL' && t.transactionType !== typeFilter) return false;
      if (q && !(String(t.symbol ?? '').toUpperCase().includes(q)
                 || String(t.assetType ?? '').toUpperCase().includes(q))) return false;
      const day = (t.transactionDate || '').slice(0, 10);
      if (dateFrom && day && day < dateFrom) return false;
      if (dateTo && day && day > dateTo) return false;
      return true;
    });
  }, [transactions, search, typeFilter, dateFrom, dateTo]);

  const hasAnyFilter = !!search || typeFilter !== 'ALL' || !!dateFrom || !!dateTo;
  const clearFilters = () => { setSearch(''); setTypeFilter('ALL'); setDateFrom(''); setDateTo(''); };

  if (!transactions.length) {
    return (
      <div className="p-12 text-center text-gray-400 text-sm">
        {tr('Henüz işlem yok.')}
      </div>
    );
  }

  const mix = txBondMix(filtered);
  const priceHeader = mix.onlyBond
    ? tr('Birim Fiyat (/100)')
    : mix.mixed
      ? tr('Fiyat / Birim Fiyat')
      : tr('Fiyat');

  const headers = [tr('Tarih'), tr('İşlem'), tr('Sembol'), tr('Tür'), tr('Miktar'), priceHeader, tr('Toplam'), tr('Komisyon'), ''];

  return (
    <div>
      {/* Filtre çubuğu */}
      <div className="flex flex-wrap items-center gap-2 px-4 py-3 border-b border-gray-200 bg-gray-50/40">
        <div className="relative">
          <Search className="w-3.5 h-3.5 text-gray-400 absolute left-2.5 top-1/2 -translate-y-1/2" />
          <input
            type="text"
            value={search}
            onChange={e => setSearch(e.target.value)}
            placeholder={tr('Sembol ya da tür ara')}
            className="pl-8 pr-3 py-1.5 text-xs border border-gray-200 rounded-md w-44 focus:outline-none focus:border-[#093eaa]"
          />
        </div>
        <select
          value={typeFilter}
          onChange={e => setTypeFilter(e.target.value)}
          className="px-2.5 py-1.5 text-xs border border-gray-200 rounded-md focus:outline-none focus:border-[#093eaa]"
        >
          <option value="ALL">{tr('Tüm işlemler')}</option>
          <option value="BUY">{tr('Sadece Alış')}</option>
          <option value="SELL">{tr('Sadece Satış')}</option>
          <option value="COUPON_INCOME">{tr('Sadece Kupon')}</option>
        </select>
        <div className="flex items-center gap-1.5 text-xs">
          <span className="text-gray-500">{tr('Tarih:')}</span>
          <input
            type="date"
            value={dateFrom}
            onChange={e => setDateFrom(e.target.value)}
            className="px-2 py-1 text-xs border border-gray-200 rounded-md focus:outline-none focus:border-[#093eaa]"
          />
          <span className="text-gray-400">→</span>
          <input
            type="date"
            value={dateTo}
            onChange={e => setDateTo(e.target.value)}
            className="px-2 py-1 text-xs border border-gray-200 rounded-md focus:outline-none focus:border-[#093eaa]"
          />
        </div>
        {hasAnyFilter && (
          <button
            type="button"
            onClick={clearFilters}
            className="inline-flex items-center gap-1 px-2 py-1 text-[11px] text-gray-500 hover:text-rose-600 hover:bg-rose-50 rounded-md"
          >
            <X className="w-3 h-3" /> {tr('Temizle')}
          </button>
        )}
        <div className="ml-auto text-[11px] text-gray-500">
          {filtered.length} / {transactions.length} {tr('kayıt')}
        </div>
      </div>

      {filtered.length === 0 ? (
        <div className="p-8 text-center text-gray-400 text-xs">
          {tr('Bu filtreyle eşleşen işlem yok.')}
        </div>
      ) : (
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
              {filtered.map((t) => {
                const isBuy = t.transactionType === 'BUY';
                const isCoupon = t.transactionType === 'COUPON_INCOME';
                const isBondTx = String(t.assetType ?? '').toUpperCase() === 'BOND';
                // BOND için "Toplam" = qty × price / 100 (TCMB konvansiyonu).
                // Altın bond istisnası: birim adet/gram → /100 YOK.
                // COUPON_INCOME: qty = TL tutar, price = 1.
                const isGoldBondTx = isBondTx && isGoldBondSymbol(t.symbol);
                const total = isCoupon
                  ? parseFloat(t.quantity ?? 0)
                  : (isBondTx && !isGoldBondTx
                      ? parseFloat(t.quantity ?? 0) * parseFloat(t.price ?? 0) / 100
                      : parseFloat(t.quantity ?? 0) * parseFloat(t.price ?? 0));
                const rowKey = t.id ?? `${t.transactionDate}-${t.symbol}-${t.transactionType}`;
                const badge = isCoupon
                  ? { label: tr('KUPON'), classes: 'bg-blue-100 text-blue-700', icon: <Coins className="w-3 h-3 inline -mt-0.5 mr-0.5" /> }
                  : isBuy
                    ? { label: tr('ALIŞ'), classes: 'bg-emerald-100 text-emerald-700' }
                    : { label: tr('SATIŞ'), classes: 'bg-rose-100 text-rose-700' };
                return (
                  <tr key={rowKey} className="border-t border-gray-100 hover:bg-gray-50 transition-colors">
                    <td className="px-4 py-3 text-xs text-gray-400 whitespace-nowrap">
                      {t.transactionDate?.split('T')[0] ?? '-'}
                    </td>
                    <td className="px-4 py-3">
                      <span className={`text-xs font-bold px-2 py-0.5 rounded-md whitespace-nowrap ${badge.classes}`}>
                        {badge.icon}{badge.label}
                      </span>
                    </td>
                    <td className="px-4 py-3 font-bold text-[#093eaa] text-sm">{t.symbol}</td>
                    <td className="px-4 py-3">
                      <span className="text-xs bg-gray-100 text-gray-600 px-2 py-0.5 rounded-md font-semibold">
                        {ASSET_LABELS[t.assetType] ? tr(ASSET_LABELS[t.assetType]) : t.assetType}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-sm font-mono">
                      {isCoupon ? '—' : fmtQty(t.quantity)}
                    </td>
                    <td className="px-4 py-3 text-sm">
                      {isCoupon ? '—' : fmt(t.price)}
                    </td>
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
      )}
    </div>
  );
}
