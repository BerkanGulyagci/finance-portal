import { useEffect, useState, useMemo } from 'react';
import { Plus, Eye, EyeOff, TrendingUp, TrendingDown, Info } from 'lucide-react';
import HoldingsTable from './HoldingsTable';
import TransactionsTable from './TransactionsTable';
import AddTransactionModal from './AddTransactionModal';
import PortfolioPerformanceChart from './PortfolioPerformanceChart';
import PortfolioAnalyticsSection from './analytics/PortfolioAnalyticsSection';
import PortfolioStatsSummary from './analytics/PortfolioStatsSummary';
import { deleteTransaction, getPortfolioById } from '../../../api/portfolioApi';
import { getCommoditySpot, getFxTcmb, getCryptos, getGoldSpot } from '../../../api/marketApi';
import { isYahooCommoditySymbol } from '../../../utils/commodityPriceUtils';
import { formatMoney, formatPercent } from '../utils/portfolioFormatUtils';
import { useTranslation } from '../../../context/LanguageContext';

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
  // BOND: TCMB konvansiyonu — fiyat 100 nominal başına kote → günlük TL K/Z = qty × change / 100.
  // Altın bond (adet/gram) istisna: /100 yok.
  const isBond = h?.assetType === 'BOND';
  const isGoldBond = h?.category === 'GOLD_INDEXED_BOND'
    || h?.category === 'GOLD_INDEXED_LEASE_CERTIFICATE';
  const scale = (isBond && !isGoldBond) ? 100 : 1;
  const perShare = num(h, 'dailyChangeAmount', 'change');
  if (qty != null && perShare != null) return (qty * perShare) / scale;
  const pc = num(h, 'previousClose', 'regularMarketPreviousClose', 'prevClose');
  const cp = num(h, 'currentPrice');
  if (qty != null && cp != null && pc != null) return (qty * (cp - pc)) / scale;
  return null;
}

const TABS = [
  { key: 'holdings', label: 'Varlıklar' },
  { key: 'transactions', label: 'İşlemler' },
  { key: 'charts', label: 'Grafikler' },
  { key: 'stats', label: 'Fırsat Maliyeti' },
];
// Tab labels translated via t() at render site below.

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
                className="pointer-events-none absolute left-0 bottom-full mb-1.5 hidden group-hover:block group-focus-within:block z-30 w-52 rounded-lg bg-gray-900 text-white text-xs px-2.5 py-2 shadow-lg text-left font-normal normal-case tracking-normal leading-snug"
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
  const { t } = useTranslation();
  const [activeTab, setActiveTab] = useState('holdings');
  const [showAddTx, setShowAddTx] = useState(false);
  const [deletingTxId, setDeletingTxId] = useState(null);
  const [valuesHidden, setValuesHidden] = useState(false);
  const [chartRefreshKey, setChartRefreshKey] = useState(0); // işlem ekleme/silmede grafiği yeniden çiz
  const [valCurr, setValCurr] = useState('TRY'); // portföy değeri gösterim para birimi
  const [rates, setRates] = useState(null);      // {usd, eur, btc, gold} — TL karşılıkları

  async function loadRates() {
    if (rates) return rates;
    try {
      const [fx, coins, gold] = await Promise.all([
        getFxTcmb(), getCryptos(0, 50, 'try'), getGoldSpot(),
      ]);
      const findRate = sym => {
        const x = (fx?.rates ?? []).find(r => r.symbol === sym);
        if (!x) return null;
        const unit = x.unit && x.unit > 1 ? x.unit : 1;
        const p = x.sell ?? x.buy ?? null;
        return p != null ? p / unit : null;
      };
      const btc = (coins ?? []).find(c => (c.symbol || '').toUpperCase() === 'BTC')?.currentPrice ?? null;
      const gram = gold ? (gold.gramGoldTry ?? gold.gramTl ?? gold.officialPureGoldGramTry ?? null) : null;
      const next = {
        usd: findRate('USD'),
        eur: findRate('EUR'),
        btc: btc ? Number(btc) : null,
        gold: gram ? Number(gram) : null,
      };
      setRates(next);
      return next;
    } catch {
      return null;
    }
  }

  async function cycleCurrency() {
    const r = await loadRates();
    const order = ['TRY', 'USD', 'EUR', 'BTC', 'GOLD'];
    setValCurr(c => (r ? order[(order.indexOf(c) + 1) % order.length] : 'TRY'));
  }

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

  const valueText = (() => {
    if (valuesHidden) return formatMoney(mv, currency, true);
    if (valCurr === 'TRY' || !rates) return formatMoney(mv, currency, false);
    const r = valCurr === 'USD' ? rates.usd
      : valCurr === 'EUR' ? rates.eur
      : valCurr === 'BTC' ? rates.btc
      : rates.gold;
    if (!r) return formatMoney(mv, currency, false);
    if (valCurr === 'BTC') return `₿${(mv / r).toLocaleString('tr-TR', { maximumFractionDigits: 4 })}`;
    if (valCurr === 'GOLD') return `${(mv / r).toLocaleString('tr-TR', { maximumFractionDigits: 2 })} gr`;
    const sym = valCurr === 'USD' ? '$' : '€';
    return `${sym}${(mv / r).toLocaleString('tr-TR', { maximumFractionDigits: 0 })}`;
  })();

  // Reel (enflasyona göre düzeltilmiş) K/Z — TL alım gücü çerçevesi (backend TÜFE).
  const realPnl = toNumberOrNull(portfolio.totalRealProfitLoss);
  const realPct = toNumberOrNull(portfolio.totalRealProfitLossPercent);
  const realPos = realPnl != null ? realPnl >= 0 : undefined;

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
      alert(t('Bu satırda işlem kimliği yok; sayfayı yenileyip tekrar deneyin.'));
      return;
    }
    if (!window.confirm(t('Bu işlemi silmek istediğinizden emin misiniz?'))) return;
    setDeletingTxId(txId);
    try {
      const updated = await deleteTransaction(portfolio.id, txId);
      onPortfolioUpdate(updated);
      setChartRefreshKey(k => k + 1);
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
      alert(msg ? `${t('İşlem silinemedi:')} ${msg}` : t('İşlem silinemedi. Bağlantınızı kontrol edip tekrar deneyin.'));
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
          onAdded={updated => { onPortfolioUpdate(updated); setShowAddTx(false); setChartRefreshKey(k => k + 1); }}
        />
      )}

      {/* Portföy Özeti */}
      <div className="bg-white rounded-lg border border-gray-200 shadow-sm overflow-hidden">
        <div className="flex items-center justify-between px-5 py-4 border-b border-gray-100">
          <h2 className="text-base font-bold text-gray-900">{t('Portföy Özeti')}</h2>
          <button
            type="button"
            onClick={() => setValuesHidden(v => !v)}
            className="p-2 rounded-lg border border-gray-200 text-gray-500 hover:text-[#093eaa] hover:border-[#093eaa]/30 hover:bg-blue-50/50 transition-colors"
            title={valuesHidden ? t('Değerleri göster') : t('Değerleri gizle')}
            aria-label={valuesHidden ? t('Değerleri göster') : t('Değerleri gizle')}
          >
            {valuesHidden ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
          </button>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-[minmax(0,32%)_1px_minmax(0,68%)]">
          {/* Sol: metrikler */}
          <div className="p-5 lg:pr-4">
            <p className="text-sm font-medium text-gray-500 mb-1">{t('Güncel Portföy Değeri')}</p>
            <button
              type="button"
              onClick={cycleCurrency}
              title={t('Para birimini değiştir (TL / USD / EUR / BTC / Gram Altın)')}
              className="group mb-4 flex items-center gap-2 text-left"
            >
              <span className="text-2xl font-bold text-gray-900">{valueText}</span>
              <span className="text-[10px] font-bold text-[#093eaa] bg-[#093eaa]/10 px-1.5 py-0.5 rounded group-hover:bg-[#093eaa]/20 transition-colors">
                {valCurr === 'TRY' ? 'TL' : valCurr === 'GOLD' ? 'Altın' : valCurr}
              </span>
              {!valuesHidden && (
                isPos
                  ? <TrendingUp className="w-5 h-5 text-emerald-500 shrink-0" aria-hidden />
                  : <TrendingDown className="w-5 h-5 text-rose-500 shrink-0" aria-hidden />
              )}
            </button>

            <div className="h-px bg-gray-200 mb-1" />

            <SummaryRow
              label={t('Toplam Maliyet')}
              value={formatMoney(cost > 0 ? cost : null, currency, valuesHidden)}
            />
            <SummaryRow
              label={t('Günlük K/Z')}
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
              label={t('Açık K/Z')}
              tooltip={t('Satılmamış pozisyonlardaki anlık kar/zarar (nominal — enflasyon hariç).')}
              value={`${isPos && !valuesHidden ? '+' : ''}${formatMoney(pnl, currency, valuesHidden)}`}
              subValue={pnlPct != null ? formatPercent(pnlPct, valuesHidden, { signed: true }) : null}
              positive={isPos}
            />
            {realPnl != null && (
              <SummaryRow
                label={t('Reel K/Z')}
                tooltip={t('Enflasyondan (TÜFE) arındırılmış gerçek kâr/zarar — TL alım gücü cinsinden. Her varlık kendi ilk alış tarihinden bugüne hesaplanır ve tüm portföyü kapsar (yeni alımlarda enflasyon henüz birikmediği için nominale eşittir). Pozitifse enflasyonu yendiniz.')}
                value={`${realPos && !valuesHidden ? '+' : ''}${formatMoney(realPnl, currency, valuesHidden)}`}
                subValue={realPct != null ? formatPercent(realPct, valuesHidden, { signed: true }) : null}
                positive={realPos}
              />
            )}
            <SummaryRow
              label={t('Gerçekleşen K/Z')}
              tooltip={t('Satılan pozisyonlardan oluşan kesinleşmiş kar/zarar.')}
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
              refreshKey={chartRefreshKey}
            />
          </div>
        </div>
      </div>

      {/* Pozisyon Ekle + Sekmeler */}
      <div className="flex items-center justify-between flex-wrap gap-3">
        <div className="flex gap-2 flex-wrap">
          {TABS.map(tab => (
            <button
              key={tab.key}
              onClick={() => setActiveTab(tab.key)}
              className={`px-4 py-2 rounded-lg text-sm font-semibold transition-all ${
                activeTab === tab.key
                  ? 'bg-[#093eaa] text-white'
                  : 'bg-white text-gray-600 border border-gray-200 hover:bg-gray-50'
              }`}
            >
              {t(tab.label)}
            </button>
          ))}
        </div>
        <button
          onClick={() => setShowAddTx(true)}
          className="flex items-center gap-2 bg-[#093eaa] text-white px-4 py-2 rounded-lg text-sm font-semibold hover:bg-[#093eaa]/90 transition-all"
        >
          <Plus className="w-4 h-4" /> {t('Pozisyon Ekle')}
        </button>
      </div>

      {/* Tab içerikleri */}
      <div className="bg-white rounded-lg border border-gray-200 shadow-sm overflow-hidden">
        {activeTab === 'holdings' && (
          <HoldingsTable
            holdings={holdingsRows}
            commoditySpots={commoditySpots}
            valuesHidden={valuesHidden}
            transactions={portfolio.transactions ?? []}
            portfolioId={portfolio.id}
            onPortfolioChanged={onPortfolioUpdate}
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
            portfolioId={portfolio.id}
            portfolioName={portfolio.name}
          />
        )}
        {activeTab === 'stats' && (
          <PortfolioStatsSummary
            portfolioId={portfolio.id}
            holdings={holdingsRows}
            valuesHidden={valuesHidden}
            currency={currency}
          />
        )}
      </div>
    </div>
  );
}
