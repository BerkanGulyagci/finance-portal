import { useEffect, useState, useMemo } from 'react';
import { Plus, Eye, EyeOff, TrendingUp, TrendingDown, Info } from 'lucide-react';
import HoldingsTable from './HoldingsTable';
import TransactionsTable from './TransactionsTable';
import AddTransactionModal from './AddTransactionModal';
import PortfolioPerformanceChart from './PortfolioPerformanceChart';
import PortfolioAnalyticsSection from './analytics/PortfolioAnalyticsSection';
import PortfolioStatsSummary from './analytics/PortfolioStatsSummary';
import { deleteTransaction, getPortfolioById } from '../../../api/portfolioApi';
import { getCommoditySpot } from '../../../api/marketApi';
import { isYahooCommoditySymbol } from '../../../utils/commodityPriceUtils';
import { formatMoney, formatPercent } from '../utils/portfolioFormatUtils';

function toNumberOrNull(v) {
  if (v == null || v === '') return null;
  const n = typeof v === 'number' ? v : parseFloat(v);
  return Number.isFinite(n) ? n : null;
}

function num(h, ...keys) {
  for (const k of keys) {
    const v = h[k];
    if (v == null || v === '') continue;
    const n = parseFloat(v);
    if (Number.isFinite(n)) return n;
  }
  return null;
}

function positionDailyGainLoss(h) {
  const qty = num(h, 'totalQuantity');
  const perShare = num(h, 'dailyChangeAmount', 'change');
  if (qty != null && perShare != null) return qty * perShare;
  const pc = num(h, 'previousClose', 'regularMarketPreviousClose', 'prevClose');
  const cp = num(h, 'currentPrice');
  if (qty != null && cp != null && pc != null) return qty * (cp - pc);
  return null;
}

const TABS = [
  { key: 'holdings', label: 'Varlıklar' },
  { key: 'transactions', label: 'İşlemler' },
  { key: 'charts', label: 'Grafikler' },
  { key: 'stats', label: 'İstatistikler' },
];

function SummaryRow({ label, value, subValue, positive, dotColor, tooltip }) {
  return (
    <div className="flex items-start justify-between gap-3 py-2.5 border-b border-gray-100 last:border-0">
      <div className="flex items-center gap-2 min-w-0">
        {dotColor && (
          <span className="w-2 h-2 rounded-full shrink-0" style={{ backgroundColor: dotColor }} />
        )}
        <span className="text-sm text-gray-500 flex items-center gap-1">
          {label}
          {tooltip && (
            <span className="relative inline-flex group">
              <Info
                className="w-3.5 h-3.5 text-gray-400 cursor-help shrink-0"
                aria-label={tooltip}
                title={tooltip}
                tabIndex={0}
              />
              <span
                role="tooltip"
                className="pointer-events-none absolute left-1/2 -translate-x-1/2 bottom-full mb-1.5 hidden group-hover:block group-focus-within:block z-20 w-52 rounded-lg bg-gray-900 text-white text-xs px-2.5 py-2 shadow-lg text-left font-normal normal-case tracking-normal leading-snug"
              >
                {tooltip}
              </span>
            </span>
          )}
        </span>
      </div>
      <div className="text-right shrink-0">
        <p className={`text-sm font-bold ${positive === true ? 'text-emerald-600' : positive === false ? 'text-rose-600' : 'text-gray-900'}`}>
          {value}
        </p>
        {subValue && (
          <p className={`text-xs mt-0.5 ${positive === true ? 'text-emerald-500' : positive === false ? 'text-rose-500' : 'text-gray-400'}`}>
            {subValue}
          </p>
        )}
      </div>
    </div>
  );
}

/**
 * HOLDINGS portföy detay ekranı.
 */
export default function HoldingsDetail({ portfolio, onPortfolioUpdate, initialInstrument, onInitialInstrumentConsumed }) {
  const [activeTab, setActiveTab] = useState('holdings');
  const [showAddTx, setShowAddTx] = useState(false);
  const [deletingTxId, setDeletingTxId] = useState(null);
  const [valuesHidden, setValuesHidden] = useState(false);

  useEffect(() => {
    if (!initialInstrument) return;
    setShowAddTx(true);
    onInitialInstrumentConsumed?.();
  }, [initialInstrument, onInitialInstrumentConsumed]);

  const holdingsRows = portfolio.holdings ?? [];
  const [commoditySpots, setCommoditySpots] = useState({});
  const currency = portfolio.currency || 'TRY';

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

  const pnlPct = cost > 0 ? (pnl / cost) * 100 : null;
  const isPos = pnl >= 0;

  const dailyPnl = useMemo(() => {
    let sum = 0;
    let any = false;
    for (const h of holdingsRows) {
      const v = positionDailyGainLoss(h);
      if (v != null && Number.isFinite(v)) {
        sum += v;
        any = true;
      }
    }
    return any ? sum : null;
  }, [holdingsRows]);

  const dailyPct = useMemo(() => {
    if (dailyPnl == null || mv <= 0) return null;
    const prev = mv - dailyPnl;
    if (prev <= 0) return null;
    return (dailyPnl / prev) * 100;
  }, [dailyPnl, mv]);

  const realizedPnl = useMemo(() => {
    let sum = 0;
    let any = false;
    for (const h of holdingsRows) {
      const v = toNumberOrNull(h.realizedGainLoss);
      if (v != null) {
        sum += v;
        any = true;
      }
    }
    return any ? sum : 0;
  }, [holdingsRows]);

  const dailyPos = dailyPnl != null && dailyPnl >= 0;

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

      {/* Portföy Özeti */}
      <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden">
        <div className="flex items-center justify-between px-5 py-4 border-b border-gray-100">
          <h2 className="text-base font-bold text-gray-900">Portföy Özeti</h2>
          <button
            type="button"
            onClick={() => setValuesHidden(v => !v)}
            className="p-2 rounded-xl border border-gray-200 text-gray-500 hover:text-[#093eaa] hover:border-[#093eaa]/30 hover:bg-blue-50/50 transition-colors"
            title={valuesHidden ? 'Değerleri göster' : 'Değerleri gizle'}
            aria-label={valuesHidden ? 'Değerleri göster' : 'Değerleri gizle'}
          >
            {valuesHidden ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
          </button>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-[minmax(0,32%)_1px_minmax(0,68%)]">
          {/* Sol: metrikler */}
          <div className="p-5 lg:pr-4">
            <p className="text-sm font-medium text-gray-500 mb-1">Güncel Portföy Değeri</p>
            <p className="text-2xl font-bold text-gray-900 mb-4 flex items-center gap-2">
              {formatMoney(mv, currency, valuesHidden)}
              {!valuesHidden && (
                isPos
                  ? <TrendingUp className="w-5 h-5 text-emerald-500 shrink-0" aria-hidden />
                  : <TrendingDown className="w-5 h-5 text-rose-500 shrink-0" aria-hidden />
              )}
            </p>

            <div className="h-px bg-gray-200 mb-1" />

            <SummaryRow
              label="Toplam Maliyet"
              value={formatMoney(cost > 0 ? cost : null, currency, valuesHidden)}
            />
            <SummaryRow
              label="Günlük K/Z"
              value={
                dailyPnl != null
                  ? `${dailyPos && !valuesHidden ? '+' : ''}${formatMoney(dailyPnl, currency, valuesHidden)}`
                  : '-'
              }
              subValue={
                dailyPct != null
                  ? formatPercent(dailyPct, valuesHidden, { signed: true })
                  : null
              }
              positive={dailyPnl != null ? dailyPos : undefined}
            />
            <SummaryRow
              label="Açık K/Z"
              tooltip="Satılmamış pozisyonlardaki anlık kar/zarar."
              value={`${isPos && !valuesHidden ? '+' : ''}${formatMoney(pnl, currency, valuesHidden)}`}
              subValue={pnlPct != null ? formatPercent(pnlPct, valuesHidden, { signed: true }) : null}
              positive={isPos}
            />
            <SummaryRow
              label="Gerçekleşen K/Z"
              tooltip="Satılan pozisyonlardan oluşan kesinleşmiş kar/zarar."
              value={formatMoney(realizedPnl, currency, valuesHidden)}
              positive={realizedPnl > 0 ? true : realizedPnl < 0 ? false : undefined}
            />
          </div>

          <div className="hidden lg:block bg-gray-100 w-px self-stretch" />

          {/* Sağ: performans grafiği */}
          <div className="p-5 lg:pl-4 border-t lg:border-t-0 border-gray-100">
            <PortfolioPerformanceChart
              portfolioId={portfolio.id}
              valuesHidden={valuesHidden}
              summaryMarketValue={mv > 0 ? mv : null}
            />
          </div>
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
          <HoldingsTable
            holdings={holdingsRows}
            commoditySpots={commoditySpots}
            valuesHidden={valuesHidden}
          />
        )}
        {activeTab === 'transactions' && (
          <TransactionsTable
            transactions={portfolio.transactions ?? []}
            onDelete={handleDeleteTx}
            deletingId={deletingTxId}
          />
        )}
        {activeTab === 'charts' && (
          <PortfolioAnalyticsSection
            holdings={holdingsRows}
            valuesHidden={valuesHidden}
            currency={currency}
          />
        )}
        {activeTab === 'stats' && (
          <PortfolioStatsSummary
            holdings={holdingsRows}
            valuesHidden={valuesHidden}
            currency={currency}
          />
        )}
      </div>
    </div>
  );
}
