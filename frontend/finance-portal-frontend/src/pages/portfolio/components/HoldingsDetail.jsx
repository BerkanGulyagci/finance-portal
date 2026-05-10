import { useEffect, useState } from 'react';
import { Plus, TrendingUp, TrendingDown } from 'lucide-react';
import HoldingsTable from './HoldingsTable';
import TransactionsTable from './TransactionsTable';
import AddTransactionModal from './AddTransactionModal';
import { deleteTransaction, getPortfolioById } from '../../../api/portfolioApi';

function fmt(v, dec = 2) {
  if (v == null) return '-';
  const n = parseFloat(v);
  if (isNaN(n)) return '-';
  return n.toLocaleString('tr-TR', { minimumFractionDigits: dec, maximumFractionDigits: dec });
}

const TABS = [
  { key: 'holdings',     label: 'Varlıklar' },
  { key: 'transactions', label: 'İşlemler' },
  { key: 'charts',       label: 'Grafikler' },
  { key: 'stats',        label: 'İstatistikler' },
];

/**
 * HOLDINGS portföy detay ekranı.
 *
 * Props:
 *   portfolio: PortfolioResponse
 *   onPortfolioUpdate(updated): void
 */
export default function HoldingsDetail({ portfolio, onPortfolioUpdate, initialInstrument, onInitialInstrumentConsumed }) {
  const [activeTab, setActiveTab] = useState('holdings');
  const [showAddTx, setShowAddTx] = useState(false);
  const [deletingTxId, setDeletingTxId] = useState(null);

  useEffect(() => {
    if (!initialInstrument) return;
    setShowAddTx(true);
    onInitialInstrumentConsumed?.();
  }, [initialInstrument, onInitialInstrumentConsumed]);

  const pnl  = parseFloat(portfolio.totalProfitLoss ?? 0);
  const mv   = parseFloat(portfolio.totalMarketValue ?? 0);
  const cost = parseFloat(portfolio.totalCost ?? 0);
  const pnlPct = cost > 0 ? ((pnl / cost) * 100).toFixed(2) : null;
  const isPos = pnl >= 0;

  async function handleDeleteTx(txId) {
    if (!window.confirm('Bu işlemi silmek istediğinizden emin misiniz?')) return;
    setDeletingTxId(txId);
    try {
      const updated = await deleteTransaction(portfolio.id, txId);
      onPortfolioUpdate(updated);
    } catch {
      alert('İşlem silinemedi.');
    } finally {
      setDeletingTxId(null);
    }
  }

  return (
    <div className="space-y-5">
      {showAddTx && (
        <AddTransactionModal
          portfolioId={portfolio.id}
          portfolioName={portfolio.name}
          initialInstrument={initialInstrument}
          onClose={() => setShowAddTx(false)}
          onAdded={updated => { onPortfolioUpdate(updated); setShowAddTx(false); }}
        />
      )}

      {/* Özet kartları */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
        <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-5">
          <p className="text-xs text-gray-500 font-semibold mb-1">Toplam Maliyet</p>
          <p className="text-xl font-bold text-gray-900">{fmt(cost)}</p>
        </div>
        <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-5">
          <p className="text-xs text-gray-500 font-semibold mb-1">Piyasa Değeri</p>
          <p className="text-xl font-bold text-gray-900">{mv > 0 ? fmt(mv) : '-'}</p>
        </div>
        <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-5">
          <p className="text-xs text-gray-500 font-semibold mb-1">Açık Kâr/Zarar</p>
          <p className={`text-xl font-bold flex items-center gap-1 ${isPos ? 'text-emerald-600' : 'text-rose-600'}`}>
            {isPos ? <TrendingUp className="w-4 h-4" /> : <TrendingDown className="w-4 h-4" />}
            {fmt(pnl)}
          </p>
          {pnlPct && (
            <p className={`text-xs mt-0.5 ${isPos ? 'text-emerald-500' : 'text-rose-500'}`}>
              {isPos ? '+' : ''}{pnlPct}%
            </p>
          )}
        </div>
        <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-5">
          <p className="text-xs text-gray-500 font-semibold mb-1">Günlük K/Z</p>
          <p className="text-xl font-bold text-gray-400">-</p>
          <p className="text-xs text-gray-300 mt-0.5">Yakında</p>
        </div>
      </div>

      {/* Pozisyon Ekle + Sekmeler */}
      <div className="flex items-center justify-between flex-wrap gap-3">
        <div className="flex gap-2 flex-wrap">
          {TABS.map(t => (
            <button
              key={t.key}
              onClick={() => setActiveTab(t.key)}
              className={`px-4 py-2 rounded-xl text-sm font-semibold transition-all ${
                activeTab === t.key
                  ? 'bg-[#093eaa] text-white'
                  : 'bg-white text-gray-600 border border-gray-200 hover:bg-gray-50'
              }`}
            >
              {t.label}
            </button>
          ))}
        </div>
        <button
          onClick={() => setShowAddTx(true)}
          className="flex items-center gap-2 bg-[#093eaa] text-white px-4 py-2 rounded-xl text-sm font-semibold hover:bg-[#093eaa]/90 transition-all"
        >
          <Plus className="w-4 h-4" /> Pozisyon Ekle
        </button>
      </div>

      {/* Tab içerikleri */}
      <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden">
        {activeTab === 'holdings' && (
          <HoldingsTable holdings={portfolio.holdings ?? []} />
        )}
        {activeTab === 'transactions' && (
          <TransactionsTable
            transactions={portfolio.transactions ?? []}
            onDelete={handleDeleteTx}
            deletingId={deletingTxId}
          />
        )}
        {activeTab === 'charts' && (
          <div className="p-12 text-center text-gray-400 text-sm">
            Grafik görünümü yakında eklenecek.
          </div>
        )}
        {activeTab === 'stats' && (
          <div className="p-12 text-center text-gray-400 text-sm">
            İstatistikler yakında eklenecek.
          </div>
        )}
      </div>
    </div>
  );
}
