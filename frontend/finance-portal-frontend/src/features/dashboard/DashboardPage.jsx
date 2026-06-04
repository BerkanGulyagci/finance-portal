import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { Plus, X, Bitcoin, LineChart, Layers, ArrowLeftRight, BellPlus } from 'lucide-react';
import {
  getMyPortfolios, getWatchlistItems, addWatchlistItem, deleteWatchlistItem, createPortfolio,
} from '../../api/portfolioApi';
import {
  getCryptos, getStocks, getAllTefasFunds, getFxTcmb, getEconomy, getEconomicIndicators,
} from '../../api/marketApi';
import {
  calculateAllocationByType,
  ANALYTICS_BY_KEY,
  WL_CHART_BY_KEY,
} from '../portfolio';
import InstrumentSearchModal from '../../components/instrument/InstrumentSearchModal';
import { readPfCharts, removePfChart, DASH_PF_EVENT } from '../../utils/dashboardCharts';
import { readWlCharts, removeWlChart, DASH_WL_EVENT } from '../../utils/watchlistDashCharts';
import { prefGet, prefSet } from '../../api/prefs';
import { useAuth } from '../../context/AuthContext';
import { useToast } from '../../context/ToastContext';
import { useTranslation } from '../../context/LanguageContext';
import GridBoard from '../../components/common/GridBoard';
import AlarmCreateModal from '../../components/instrument/AlarmCreateModal';
import MiniAssetChart from './components/MiniAssetChart';
import StatTiles from './components/StatTiles';
import PortfolioDistributionCard from './components/PortfolioDistributionCard';
import PortfoliosCard from './components/PortfoliosCard';
import RecentTransactionsCard from './components/RecentTransactionsCard';
import PersonalNewsCard from './components/PersonalNewsCard';
import MarketListCard from './components/MarketListCard';
import MarketMoversCard from './components/MarketMoversCard';
import VolumeLeadersCard from './components/VolumeLeadersCard';
import DrawnChartsCard from './components/DrawnChartsCard';
import EconomyCard from './components/EconomyCard';
import FavoritesCard from './components/FavoritesCard';
import { num } from './utils/dashUtils';

const CHARTS_KEY = 'fp-dashboard-charts';
const HIDDEN_PF_KEY = 'fp-dashboard-hidden-portfolios';

function readJson(key) {
  const v = prefGet(key, []);
  return Array.isArray(v) ? v : [];
}
function saveJson(key, list) { prefSet(key, list); }

function findEco(economy, key) {
  for (const g of economy?.groups ?? []) {
    const f = (g.indicators ?? []).find(i => i.key === key);
    if (f) return f;
  }
  return null;
}

// Pozisyon piyasa değerini TL'ye çevir (USD/EUR vb. karışık portföylerde dağılım doğru olsun).
function rateFor(currency, fx) {
  if (!currency || currency === 'TRY' || currency === 'TL') return 1;
  const r = fx?.rates?.find(x => x.symbol === currency)?.sell;
  return r ? Number(r) : 1;
}
function toTryHoldings(holdings, fx) {
  return (holdings ?? []).map(h => {
    const rate = rateFor(h.currency, fx);
    return rate === 1 ? h : { ...h, marketValue: num(h.marketValue) * rate, currency: 'TRY' };
  });
}

export default function DashboardPage() {
  const { t } = useTranslation();
  const { isAuthenticated, username } = useAuth();
  const toast = useToast();

  const [assetPortfolios, setAssetPortfolios] = useState([]);
  const [watchlists, setWatchlists] = useState([]);
  const [favorites, setFavorites] = useState([]);
  const [cryptos, setCryptos] = useState([]);
  const [stocks, setStocks] = useState([]);
  const [funds, setFunds] = useState([]);
  const [cryptoLoading, setCryptoLoading] = useState(true);
  const [stockLoading, setStockLoading] = useState(true);
  const [fundLoading, setFundLoading] = useState(true);
  const [fx, setFx] = useState(null);
  const [economy, setEconomy] = useState(null);
  const [indicators, setIndicators] = useState(null);

  const [charts, setCharts] = useState(() => readJson(CHARTS_KEY));
  const [pfCharts, setPfCharts] = useState(readPfCharts);
  const [wlCharts, setWlCharts] = useState(readWlCharts);
  const [hiddenPids, setHiddenPids] = useState(() => new Set(readJson(HIDDEN_PF_KEY)));
  const [searchMode, setSearchMode] = useState(null); // 'chart' | 'favorite' | 'alarm'
  const [distFocusId, setDistFocusId] = useState(null); // pie'ye sürüklenen portföy
  const [alarmInst, setAlarmInst] = useState(null);     // Alarm Kur akışı

  async function loadPortfolios() {
    const list = await getMyPortfolios().catch(() => []);
    const assets = (list ?? []).filter(p => p.portfolioType !== 'WATCHLIST');
    const wls = (list ?? []).filter(p => p.portfolioType === 'WATCHLIST');
    setAssetPortfolios(assets);
    setWatchlists(wls.map(w => ({ id: w.id, name: w.name })));
    const arrs = await Promise.all(
      wls.map(w => getWatchlistItems(w.id)
        .then(items => (items ?? []).map(it => ({ ...it, portfolioId: w.id })))
        .catch(() => [])),
    );
    setFavorites(arrs.flat());
  }

  useEffect(() => {
    if (isAuthenticated) loadPortfolios().catch(() => {});
    getCryptos(0, 6).then(setCryptos).catch(() => {}).finally(() => setCryptoLoading(false));
    getStocks(0, 6, 'BIST30').then(p => setStocks(p?.content ?? [])).catch(() => {}).finally(() => setStockLoading(false));
    getAllTefasFunds().then(list => setFunds(list ?? [])).catch(() => {}).finally(() => setFundLoading(false));
    getFxTcmb().then(setFx).catch(() => {});
    getEconomy().then(setEconomy).catch(() => {});
    getEconomicIndicators().then(setIndicators).catch(() => {});
  }, [isAuthenticated]);

  useEffect(() => {
    const onPf = () => setPfCharts(readPfCharts());
    const onWl = () => setWlCharts(readWlCharts());
    const onStorage = () => { onPf(); onWl(); };
    window.addEventListener(DASH_PF_EVENT, onPf);
    window.addEventListener(DASH_WL_EVENT, onWl);
    window.addEventListener('storage', onStorage);
    return () => {
      window.removeEventListener(DASH_PF_EVENT, onPf);
      window.removeEventListener(DASH_WL_EVENT, onWl);
      window.removeEventListener('storage', onStorage);
    };
  }, []);

  // ── Hesaplamalar ──────────────────────────────────────────────────────────────
  const combinedHoldings = useMemo(
    () => assetPortfolios.flatMap(p => p.holdings ?? []),
    [assetPortfolios],
  );
  // Dağılım/istatistik için TL'ye normalize edilmiş pozisyonlar (daha hassas)
  const combinedHoldingsTry = useMemo(() => toTryHoldings(combinedHoldings, fx), [combinedHoldings, fx]);

  const distFocusPf = distFocusId ? assetPortfolios.find(p => p.id === distFocusId) : null;
  const distHoldings = useMemo(
    () => (distFocusPf ? toTryHoldings(distFocusPf.holdings, fx) : combinedHoldingsTry),
    [distFocusPf, combinedHoldingsTry, fx],
  );

  const stats = useMemo(() => {
    const totalValue = assetPortfolios.reduce((s, p) => s + num(p.totalMarketValue), 0);
    const totalCost = assetPortfolios.reduce((s, p) => s + num(p.totalCost), 0);
    const totalPnl = assetPortfolios.reduce((s, p) => s + num(p.totalProfitLoss), 0);
    const totalPct = totalCost > 0 ? (totalPnl / totalCost) * 100 : 0;
    let mvSum = 0, weighted = 0;
    for (const h of combinedHoldingsTry) {
      const mv = num(h.marketValue);
      if (mv > 0) { mvSum += mv; weighted += mv * num(h.changePercent); }
    }
    const dailyPct = mvSum > 0 ? weighted / mvSum : 0;
    const dailyPnl = totalValue * (dailyPct / 100) / (1 + dailyPct / 100);
    const { rows } = calculateAllocationByType(combinedHoldingsTry);
    return {
      totalValue, totalPnl, totalPct, dailyPnl, dailyPct,
      topType: rows[0]?.type ?? null,
      topPct: rows[0] ? rows[0].sharePct.toFixed(1) : null,
      typeCount: rows.length,
    };
  }, [assetPortfolios, combinedHoldingsTry]);

  const recentTx = useMemo(() => {
    const all = assetPortfolios.flatMap(p =>
      (p.transactions ?? []).map(tx => ({ ...tx, portfolioName: p.name })));
    all.sort((a, b) =>
      new Date(b.transactionDate || b.createdAt || 0) - new Date(a.transactionDate || a.createdAt || 0));
    return all.slice(0, 6);
  }, [assetPortfolios]);

  const eco = useMemo(() => {
    const usd = findEco(economy, 'usdTry');
    const bist = findEco(economy, 'bist100');
    const fxUsd = fx?.rates?.find(r => r.symbol === 'USD');
    return {
      inflation: indicators?.inflation ?? findEco(economy, 'tufe')?.yoyChangePercent ?? null,
      policyRate: indicators?.policyRate ?? findEco(economy, 'politikaFaizi')?.value ?? null,
      usdTry: usd?.value ?? fxUsd?.sell ?? null,
      usdChange: usd?.changePercent ?? fxUsd?.changePercent ?? null,
      bist100: bist?.value ?? null,
      bistChange: bist?.changePercent ?? null,
    };
  }, [economy, indicators, fx]);

  const visiblePortfolios = useMemo(
    () => assetPortfolios.filter(p => !hiddenPids.has(p.id)),
    [assetPortfolios, hiddenPids],
  );

  // Varsayılan piyasa satırları
  const cryptoRows = cryptos.map(c => ({
    symbol: c.symbol?.toUpperCase(), name: c.name, price: c.currentPrice,
    changePct: c.priceChangePercentage24h, image: c.image, assetType: 'CRYPTO',
  }));
  const stockRows = stocks.slice(0, 6).map(s => ({
    symbol: s.symbol, name: s.name, price: s.price, changePct: s.changePercent, assetType: 'STOCK',
  }));
  // En popüler fonlar — fon büyüklüğüne (marketCapUsd) göre ilk 6
  const fundRows = [...funds]
    .sort((a, b) => num(b.marketCapUsd) - num(a.marketCapUsd))
    .slice(0, 6)
    .map(f => ({
      symbol: f.code ?? f.uniqueCode, name: f.name, price: f.price,
      changePct: f.returnOneDay ?? null, assetType: 'FUND',
    }));

  // ── Eylemler ────────────────────────────────────────────────────────────────
  function addChart(inst) {
    setSearchMode(null);
    setCharts(prev => {
      if (prev.some(c => c.assetType === inst.assetType && c.symbol === inst.symbol)) return prev;
      const next = [...prev, { assetType: inst.assetType, symbol: inst.symbol, name: inst.name || inst.symbol }];
      saveJson(CHARTS_KEY, next);
      return next;
    });
  }
  function removeChart(assetType, symbol) {
    setCharts(prev => {
      const next = prev.filter(c => !(c.assetType === assetType && c.symbol === symbol));
      saveJson(CHARTS_KEY, next);
      return next;
    });
  }
  function hidePortfolio(id) {
    setHiddenPids(prev => { const n = new Set(prev); n.add(id); saveJson(HIDDEN_PF_KEY, [...n]); return n; });
  }
  function resetHiddenPortfolios() { setHiddenPids(new Set()); saveJson(HIDDEN_PF_KEY, []); }

  async function addFavorite(inst) {
    setSearchMode(null);
    try {
      let pid = watchlists[0]?.id;
      if (!pid) {
        const created = await createPortfolio({ name: 'İzleme Listem', description: '', currency: 'TRY', portfolioType: 'WATCHLIST' });
        pid = created.id;
      }
      await addWatchlistItem(pid, { symbol: inst.symbol, assetType: inst.assetType, notes: '' });
      toast.success(t('Favorilere eklendi.'));
      await loadPortfolios();
    } catch (e) {
      const msg = e?.response?.data?.message;
      toast.error(msg && /already/i.test(msg) ? t('Bu varlık zaten favorilerinizde.') : t('Favori eklenemedi.'));
    }
  }
  async function removeFavorite(it) {
    try {
      await deleteWatchlistItem(it.portfolioId, it.id);
      setFavorites(prev => prev.filter(f => !(f.portfolioId === it.portfolioId && f.id === it.id)));
    } catch { toast.error(t('Favoriden çıkarılamadı.')); }
  }

  // ── GridBoard kartları ──────────────────────────────────────────────────────
  const items = [];
  // Varsayılan sıra: Portföylerim · Portföy Dağılımı · Favoriler · Son İşlemler · Ekonomi · Hisse · Kripto · Fon
  if (isAuthenticated) {
    items.push({
      key: 'portfolios', w: 4, h: 7,
      node: <PortfoliosCard portfolios={visiblePortfolios} onHide={hidePortfolio}
        hiddenCount={assetPortfolios.length - visiblePortfolios.length} onReset={resetHiddenPortfolios} />,
    });
    items.push({
      key: 'distribution', w: 4, h: 8,
      node: <PortfolioDistributionCard holdings={distHoldings} focusName={distFocusPf?.name ?? null}
        onDropPortfolio={setDistFocusId} onClear={() => setDistFocusId(null)} />,
    });
    items.push({
      key: 'favorites', w: 4, h: 7,
      node: <FavoritesCard items={favorites} onAdd={() => setSearchMode('favorite')} onRemove={removeFavorite} />,
    });
    items.push({ key: 'recentTx', w: 4, h: 8, node: <RecentTransactionsCard transactions={recentTx} /> });
    items.push({ key: 'personalNews', w: 4, h: 7, node: <PersonalNewsCard /> });
  }
  items.push({ key: 'economy', w: 4, h: 7, node: <EconomyCard eco={eco} /> });
  items.push({
    key: 'stock', w: 4, h: 8,
    node: <MarketListCard title={t('Hisse Senetleri')} icon={LineChart} accent="#0ea5e9"
      type="STOCK" logoKind="stock" defaultRows={stockRows} loading={stockLoading} storageKey="fp-dash-stock-extra" />,
  });
  items.push({
    key: 'crypto', w: 4, h: 8,
    node: <MarketListCard title={t('Kripto Piyasası')} icon={Bitcoin} accent="#f59e0b"
      type="CRYPTO" logoKind="crypto" defaultRows={cryptoRows} loading={cryptoLoading} storageKey="fp-dash-crypto-extra" />,
  });
  items.push({
    key: 'fund', w: 4, h: 8,
    node: <MarketListCard title={t('Fonlar (TEFAS)')} icon={Layers} accent="#10b981"
      type="FUND" logoKind="letter" defaultRows={fundRows} loading={fundLoading} storageKey="fp-dash-fund-extra" />,
  });
  items.push({ key: 'movers', w: 4, h: 8, node: <MarketMoversCard /> });
  items.push({ key: 'volume-leaders', w: 4, h: 8, node: <VolumeLeadersCard /> });
  items.push({ key: 'drawn-charts', w: 4, h: 7, node: <DrawnChartsCard /> });

  charts.forEach(c => {
    const owner = assetPortfolios.find(p =>
      (p.holdings ?? []).some(h => h.assetType === c.assetType
        && String(h.symbol).toUpperCase() === String(c.symbol).toUpperCase()));
    items.push({
      key: `chart:${c.assetType}:${c.symbol}`, w: 4, h: 7, noHide: true,
      node: (
        <MiniAssetChart assetType={c.assetType} symbol={c.symbol} name={c.name}
          owner={owner?.name} ownerId={owner?.id} onRemove={() => removeChart(c.assetType, c.symbol)} />
      ),
    });
  });
  pfCharts.forEach(pc => {
    const entry = ANALYTICS_BY_KEY[pc.chartKey];
    const pf = assetPortfolios.find(p => p.id === pc.portfolioId);
    if (!entry || !pf) return;
    const Comp = entry.Comp;
    items.push({
      key: `pf:${pc.portfolioId}:${pc.chartKey}`, w: 4, h: 12, noHide: true,
      node: (
        <div className="bg-white rounded-2xl border border-gray-100 shadow-sm p-3.5 h-full flex flex-col min-w-0">
          <div className="flex items-center justify-between mb-1.5 shrink-0">
            <Link to={`/portfolio/${pc.portfolioId}`}
              className="inline-flex items-center gap-1 text-[11px] font-semibold text-[#093eaa] bg-[#093eaa]/5 px-2 py-0.5 rounded-full hover:bg-[#093eaa]/10 transition-colors">
              {pc.portfolioName || pf.name}
            </Link>
            <button onClick={() => removePfChart(pc.portfolioId, pc.chartKey)} title={t('Kaldır')}
              className="p-2 sm:p-1 rounded-md text-gray-300 hover:text-rose-500 hover:bg-rose-50 transition-colors">
              <X className="w-4 h-4" />
            </button>
          </div>
          <div className="flex-1 min-h-0 overflow-auto">
            <Comp holdings={pf.holdings ?? []} valuesHidden={false} currency={pf.currency} portfolioId={pc.portfolioId} />
          </div>
        </div>
      ),
    });
  });

  // İzleme listesi "Grafikler" sekmesinden dashboard'a eklenen grafikler (kaynak izleme listesi yazılır).
  wlCharts.forEach(wc => {
    const entry = WL_CHART_BY_KEY[wc.chartKind];
    if (!entry) return;
    const wlItems = favorites.filter(f => f.portfolioId === wc.watchlistId);
    const Comp = entry.Comp;
    items.push({
      key: `wl:${wc.watchlistId}:${wc.chartKind}`, w: entry.w, h: entry.h, noHide: true,
      node: (
        <Comp
          items={wlItems}
          source={`${t('İzleme')}: ${wc.watchlistName}`}
          action={(
            <button
              type="button"
              onClick={() => removeWlChart(wc.watchlistId, wc.chartKind)}
              title={t('Kaldır')}
              className="gb-no-drag p-2 sm:p-1 rounded-md text-gray-300 hover:text-rose-500 hover:bg-rose-50 transition-colors"
            >
              <X className="w-4 h-4" />
            </button>
          )}
        />
      ),
    });
  });

  return (
    <div className="space-y-4">
      {/* Üst alan — sade başlık + aksiyonlar */}
      <div className="flex items-end justify-between gap-3 flex-wrap m3-fade-up">
        <div className="min-w-0">
          <p className="text-[11px] font-bold text-[#093eaa] uppercase tracking-[0.15em] mb-1">{t('Kontrol Paneli')}</p>
          <h1 className="text-2xl sm:text-[28px] font-bold text-gray-900 leading-tight truncate">
            {t('Hoş geldin')}{username ? `, ${username}` : ''}
          </h1>
          <p className="text-sm text-gray-500 mt-1">{t('Portföyünüzün ve piyasaların özeti')}</p>
        </div>
        <div className="flex items-center gap-2 shrink-0">
          {isAuthenticated && (
            <button onClick={() => setSearchMode('alarm')}
              className="inline-flex items-center gap-1.5 px-3 py-2 sm:px-4 sm:py-2.5 rounded-full border border-gray-200 text-gray-700 text-xs sm:text-sm font-bold hover:bg-gray-50 transition-colors">
              <BellPlus className="w-4 h-4" /> {t('Alarm Kur')}
            </button>
          )}
          <Link to="/market/stocks/compare"
            className="inline-flex items-center gap-1.5 px-3 py-2 sm:px-4 sm:py-2.5 rounded-full border border-[#093eaa]/30 text-[#093eaa] text-xs sm:text-sm font-bold hover:bg-[#093eaa]/5 transition-colors">
            <ArrowLeftRight className="w-4 h-4" /> {t('Karşılaştır')}
          </Link>
          <button onClick={() => setSearchMode('chart')}
            className="inline-flex items-center gap-1.5 px-3 py-2 sm:px-4 sm:py-2.5 rounded-full bg-[#093eaa] text-white text-xs sm:text-sm font-bold hover:bg-[#0a2966] transition-colors shadow-sm">
            <Plus className="w-4 h-4" /> {t('Grafik Ekle')}
          </button>
        </div>
      </div>

      {isAuthenticated && (
        <StatTiles
          totalValue={stats.totalValue}
          dailyPnl={stats.dailyPnl} dailyPct={stats.dailyPct}
          totalPnl={stats.totalPnl} totalPct={stats.totalPct}
          topType={stats.topType} topPct={stats.topPct}
          typeCount={stats.typeCount} portfolioCount={assetPortfolios.length}
        />
      )}

      <div className="m3-fade-up">
        <GridBoard storageKey="fp-dashboard-grid-v4" items={items} removable />
      </div>

      {searchMode && (
        <InstrumentSearchModal
          portfolioName={searchMode === 'favorite' ? t('favoriler') : searchMode === 'alarm' ? t('alarm') : t('dashboard')}
          /* Endeksler yalnız "Grafik Ekle" modunda seçilebilir (favori/alarm INDICATOR'ı işleyemez). */
          allowIndices={searchMode === 'chart'}
          onSelect={(inst) => {
            if (searchMode === 'favorite') return addFavorite(inst);
            if (searchMode === 'alarm') { setAlarmInst(inst); setSearchMode(null); return; }
            return addChart(inst);
          }}
          onClose={() => setSearchMode(null)}
        />
      )}

      {alarmInst && (
        <AlarmCreateModal
          instrument={alarmInst}
          onClose={() => setAlarmInst(null)}
          onCreated={() => setAlarmInst(null)}
          onSaved={() => setAlarmInst(null)}
        />
      )}
    </div>
  );
}
