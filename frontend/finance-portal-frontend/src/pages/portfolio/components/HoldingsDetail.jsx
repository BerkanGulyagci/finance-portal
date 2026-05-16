import { useEffect, useState, useMemo } from 'react';
import { Plus, TrendingUp, TrendingDown } from 'lucide-react';
import HoldingsTable from './HoldingsTable';
import TransactionsTable from './TransactionsTable';
import AddTransactionModal from './AddTransactionModal';
import { deleteTransaction, getPortfolioById } from '../../../api/portfolioApi';
import { getCommoditySpot } from '../../../api/marketApi';
import { isYahooCommoditySymbol } from '../../../utils/commodityPriceUtils';

function fmt(v, dec = 2) {
  if (v == null) return '-';
  const n = parseFloat(v);
  if (isNaN(n)) return '-';
  return n.toLocaleString('tr-TR', { minimumFractionDigits: dec, maximumFractionDigits: dec });
}

function toNumberOrNull(v) {
  if (v == null || v === '') return null;
  const n = typeof v === 'number' ? v : parseFloat(v);
  return Number.isFinite(n) ? n : null;
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

  const holdingsRows = portfolio.holdings ?? [];
  const [commoditySpots, setCommoditySpots] = useState({});

  const yahooCommoditySymbols = useMemo(
    () => holdingsRows
      .filter(h => h.assetType === 'COMMODITY' && isYahooCommoditySymbol(h.symbol))
      .map(h => h.symbol),
    [holdingsRows],
  );

  useEffect(() => {
    if (!yahooCommoditySymbols.length) {
      setCommoditySpots({});
      return;
    }
    let cancelled = false;
    Promise.allSettled(
      yahooCommoditySymbols.map(sym =>
        getCommoditySpot(sym).then(spot => ({ sym, spot })),
      ),
    ).then(results => {
      if (cancelled) return;
      const map = {};
      results.forEach(r => {
        if (r.status === 'fulfilled' && r.value?.spot) {
          map[r.value.sym] = r.value.spot;
        }
      });
      setCommoditySpots(map);
    });
    return () => { cancelled = true; };
  }, [yahooCommoditySymbols.join('|')]);

  const computedMv = holdingsRows.reduce((acc, h) => {
    const v = toNumberOrNull(h.marketValue);
    return v != null ? acc + v : acc;
  }, 0);
  const computedHasMv = holdingsRows.some(h => toNumberOrNull(h.marketValue) != null);

  const backendMv = toNumberOrNull(portfolio.totalMarketValue);
  const mv = backendMv != null && backendMv > 0 ? backendMv : (computedHasMv ? computedMv : 0);

  const cost = toNumberOrNull(portfolio.totalCost) ?? 0;
  const backendPnl = toNumberOrNull(portfolio.totalProfitLoss);
  const pnl = backendPnl != null && (backendMv ?? 0) > 0
    ? backendPnl
    : (computedHasMv ? computedMv - cost : 0);

  const pnlPct = cost > 0 ? ((pnl / cost) * 100).toFixed(2) : null;
  const isPos = pnl >= 0;

  async function handleDeleteTx(txId) {
    if (!txId) {
      alert('Bu satırda işlem kimliği yok; sayfayı yenileyip tekrar deneyin.');
      return;
    }
    if (!window.confirm('Bu işlemi silmek istediğinizden emin misiniz?')) return;
    setDeletingTxId(txId);
    try {
      const updated = await deleteTransaction(portfolio.id, txId);
      onPortfolioUpdate(updated);
    } catch (err) {
      const msg =
        (typeof err.response?.data?.message === 'string' && err.response.data.message) ||
        (err.response?.data?.data && typeof err.response.data.data === 'object' &&
          Object.values(err.response.data.data).filter(Boolean).join(' ')) ||
        '';
      const notFound =
        typeof msg === 'string' &&
        (msg.includes('Transaction not found') || msg.includes('İşlem bulunamadı'));
      if (notFound) {
        try {
          const fresh = await getPortfolioById(portfolio.id);
          onPortfolioUpdate(fresh);
        } catch {
          /* yoksay */
        }
      }
      alert(msg ? `İşlem silinemedi: ${msg}` : 'İşlem silinemedi. Bağlantınızı kontrol edip tekrar deneyin.');
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
          holdings={holdingsRows}
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
          <HoldingsTable holdings={holdingsRows} commoditySpots={commoditySpots} />
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
