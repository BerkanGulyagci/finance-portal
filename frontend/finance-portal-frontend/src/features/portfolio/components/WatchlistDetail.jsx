import { useState, useEffect, useCallback } from 'react';
import { Plus, X, FileSpreadsheet, FileText, ChevronDown } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import WatchlistTable from './WatchlistTable';
import WatchlistCharts from './WatchlistCharts';
import AddWatchlistItemModal from './AddWatchlistItemModal';
import { getWatchlistItems, deleteWatchlistItem, getMyPortfolios } from '../../../api/portfolioApi';
import { downloadPortfolioExcel, downloadPortfolioPdf } from '../utils/portfolioReportExport';
import { getWatchlistDetailPath } from '../constants/watchlistMarketRoutes';
import { computeTrend, TREND_SIGNAL } from '../../../utils/trendUtils';
import { useTranslation } from '../../../context/LanguageContext';

const TABS = [
  { key: 'overview', label: 'Özet' },
  { key: 'charts',   label: 'Grafikler' },
];

/**
 * WATCHLIST portföy detay ekranı.
 *
 * Props:
 *   portfolio: PortfolioResponse
 */
export default function WatchlistDetail({ portfolio }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState('overview');
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showAdd, setShowAdd] = useState(false);
  const [deletingId, setDeletingId] = useState(null);
  const [showTargetPortfolioModal, setShowTargetPortfolioModal] = useState(false);
  const [holdingsPortfolios, setHoldingsPortfolios] = useState([]);
  const [targetInstrument, setTargetInstrument] = useState(null);
  const [portfolioLoading, setPortfolioLoading] = useState(false);
  const [typeFilter, setTypeFilter] = useState('ALL');
  const [trendFilter, setTrendFilter] = useState('ALL');

  const loadItems = useCallback(async () => {
    setLoading(true);
    try {
      const data = await getWatchlistItems(portfolio.id);
      setItems(data ?? []);
    } catch {
      setItems([]);
    } finally {
      setLoading(false);
    }
  }, [portfolio.id]);

  useEffect(() => { loadItems(); }, [loadItems]);

  async function handleDelete(itemId) {
    if (!window.confirm(t('Bu sembolü izleme listesinden çıkarmak istediğinizden emin misiniz?'))) return;
    setDeletingId(itemId);
    try {
      await deleteWatchlistItem(portfolio.id, itemId);
      setItems(prev => prev.filter(i => i.id !== itemId));
    } catch {
      alert(t('Sembol silinemedi.'));
    } finally {
      setDeletingId(null);
    }
  }

  function handleAdded(newItem) {
    setItems(prev => [...prev, newItem]);
  }

  async function handleAddToPortfolio(item, displayName) {
    setTargetInstrument({
      symbol: item.symbol,
      assetType: item.assetType,
      name: displayName || item.symbol,
      price: item.lastPrice ?? item.sell ?? item.buy ?? null,
    });
    setShowTargetPortfolioModal(true);
    setPortfolioLoading(true);
    try {
      const all = await getMyPortfolios();
      const onlyHoldings = (all ?? []).filter(p => p.portfolioType === 'HOLDINGS');
      setHoldingsPortfolios(onlyHoldings);
    } catch {
      setHoldingsPortfolios([]);
    } finally {
      setPortfolioLoading(false);
    }
  }

  const resolveDetailPath = useCallback(
    item => getWatchlistDetailPath(item.assetType, item.symbol),
    [],
  );

  /** Profesyonel Excel raporu (portföy export utility'siyle aynı) */
  function handleExportExcel() {
    try {
      downloadPortfolioExcel({ ...portfolio, watchlistItems: items });
    } catch {
      alert(t('Excel dışa aktarılamadı.'));
    }
  }

  /** Native jsPDF ile stilize PDF rapor */
  function handleExportPdf() {
    try {
      downloadPortfolioPdf({ ...portfolio, watchlistItems: items });
    } catch {
      alert(t('PDF dışa aktarılamadı.'));
    }
  }

  function goToHoldingPortfolio(portfolioId) {
    if (!targetInstrument) return;
    const params = new URLSearchParams({
      addTx: '1',
      symbol: targetInstrument.symbol,
      assetType: targetInstrument.assetType,
      name: targetInstrument.name,
    });
    if (targetInstrument.price != null && !Number.isNaN(parseFloat(targetInstrument.price))) {
      params.set('price', String(targetInstrument.price));
    }
    setShowTargetPortfolioModal(false);
    navigate(`/portfolio/${portfolioId}?${params.toString()}`);
  }

  // Holdings ile aynı çoklu-sinyal trend mantığı (MA20/MA50 + 52 hafta konumu + momentum).
  function matchesTrend(item) {
    if (trendFilter === 'ALL') return true;
    const sig = computeTrend(item).signal; // 'UP' | 'DOWN' | 'SIDEWAYS' | null
    if (sig == null) return trendFilter === 'NO_DATA';
    if (trendFilter === 'UP') return sig === TREND_SIGNAL.UP;
    if (trendFilter === 'DOWN') return sig === TREND_SIGNAL.DOWN;
    if (trendFilter === 'FLAT') return sig === TREND_SIGNAL.SIDEWAYS;
    return true;
  }

  const filteredItems = items.filter(item => {
    const typeOk = typeFilter === 'ALL' || item.assetType === typeFilter;
    return typeOk && matchesTrend(item);
  });

  const grouped = filteredItems.reduce((acc, item) => {
    const key = item.assetType || 'OTHER';
    acc[key] = acc[key] || [];
    acc[key].push(item);
    return acc;
  }, {});

  const SECTIONS = [
    { key: 'STOCK', label: t('Hisseler'), variant: 'default', tone: 'blue' },
    { key: 'CRYPTO', label: t('Kripto'), variant: 'default', tone: 'violet' },
    { key: 'FUND', label: t('Fon'), variant: 'fund', tone: 'emerald' },
    { key: 'COMMODITY', label: t('Emtialar'), variant: 'default', tone: 'amber' },
    { key: 'GOLD', label: t('Altın'), variant: 'default', tone: 'yellow' },
    { key: 'FUTURE', label: t('Vadeli'), variant: 'default', tone: 'slate' },
    { key: 'FX', label: t('Döviz'), variant: 'fx', tone: 'cyan' },
    { key: 'BOND', label: t('DİBS'), variant: 'bond', tone: 'rose' },
  ];

  function toneClasses(tone) {
    switch (tone) {
      case 'blue': return 'bg-blue-50 text-blue-700 border-blue-100';
      case 'violet': return 'bg-violet-50 text-violet-700 border-violet-100';
      case 'emerald': return 'bg-emerald-50 text-emerald-700 border-emerald-100';
      case 'amber': return 'bg-amber-50 text-amber-700 border-amber-100';
      case 'yellow': return 'bg-yellow-50 text-yellow-700 border-yellow-100';
      case 'cyan': return 'bg-cyan-50 text-cyan-700 border-cyan-100';
      case 'rose': return 'bg-rose-50 text-rose-700 border-rose-100';
      default: return 'bg-slate-50 text-slate-700 border-slate-100';
    }
  }

  return (
    <div className="space-y-5">
      {showAdd && (
        <AddWatchlistItemModal
          portfolioId={portfolio.id}
          portfolioName={portfolio.name}
          onClose={() => setShowAdd(false)}
          onAdded={item => { handleAdded(item); setShowAdd(false); }}
        />
      )}
      {showTargetPortfolioModal && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-lg shadow-xl w-full max-w-lg">
            <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100">
              <div>
                <h3 className="font-bold text-gray-900">{t('Varlık Portföyü Seç')}</h3>
                <p className="text-sm text-gray-500 mt-0.5">
                  {t('{name} için alım/satım ekranı açılacak.', { name: targetInstrument?.name || targetInstrument?.symbol })}
                </p>
              </div>
              <button
                onClick={() => setShowTargetPortfolioModal(false)}
                className="text-gray-400 hover:text-gray-600"
              >
                <X className="w-5 h-5" />
              </button>
            </div>
            <div className="p-4 max-h-[55vh] overflow-y-auto">
              {portfolioLoading ? (
                <div className="py-8 text-center text-sm text-gray-400">{t('Portföyler yükleniyor...')}</div>
              ) : holdingsPortfolios.length === 0 ? (
                <div className="py-8 text-center text-sm text-gray-500">
                  {t('Varlık portföyü bulunamadı. Önce bir HOLDINGS portföyü oluşturun.')}
                </div>
              ) : (
                <div className="space-y-2">
                  {holdingsPortfolios.map(p => (
                    <button
                      key={p.id}
                      onClick={() => goToHoldingPortfolio(p.id)}
                      className="w-full text-left p-3 border border-gray-200 rounded-lg hover:border-[#093eaa]/40 hover:bg-[#093eaa]/5 transition-colors"
                    >
                      <div className="font-semibold text-gray-900">{p.name}</div>
                      <div className="text-xs text-gray-500 mt-0.5">{p.currency}</div>
                    </button>
                  ))}
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      {/* Üst bar */}
      <div className="flex items-center justify-between flex-wrap gap-3">
        <div className="flex gap-2 flex-wrap">
          {TABS.map(tab => (
            <button
              key={tab.key}
              onClick={() => setActiveTab(tab.key)}
              className={`px-4 py-2 rounded-lg text-sm font-semibold transition-all ${
                activeTab === tab.key
                  ? 'bg-sky-500 text-white'
                  : 'bg-white text-gray-600 border border-gray-200 hover:bg-gray-50'
              }`}
            >
              {t(tab.label)}
            </button>
          ))}
        </div>
        <div className="flex items-center gap-2 flex-wrap shrink-0">
          <button
            type="button"
            onClick={handleExportExcel}
            disabled={loading}
            className="flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-semibold border border-gray-200 bg-white text-emerald-700 hover:bg-emerald-50 hover:border-emerald-300 transition-all disabled:opacity-50"
            title={t('Excel (.xlsx) olarak indir')}
          >
            <FileSpreadsheet className="w-4 h-4" />
            {t('Excel')}
          </button>
          <button
            type="button"
            onClick={handleExportPdf}
            disabled={loading}
            className="flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-semibold border border-gray-200 bg-white text-rose-700 hover:bg-rose-50 hover:border-rose-300 transition-all disabled:opacity-50"
            title={t('PDF rapor olarak indir')}
          >
            <FileText className="w-4 h-4" />
            {t('PDF')}
          </button>
          <button
            type="button"
            onClick={() => setShowAdd(true)}
            className="flex items-center gap-2 bg-sky-500 text-white px-4 py-2 rounded-lg text-sm font-semibold hover:bg-sky-600 transition-all"
          >
            <Plus className="w-4 h-4" /> {t('Sembol Ekle')}
          </button>
        </div>
      </div>

      {activeTab === 'overview' && (
        <div className="bg-white rounded-lg border border-gray-200 shadow-sm px-4 py-3 flex flex-wrap items-center gap-4">
          <div className="flex items-center gap-2">
            <span className="text-xs font-semibold text-[#434653]">{t('Tür')}</span>
            <div className="relative">
              <select
                value={typeFilter}
                onChange={e => setTypeFilter(e.target.value)}
                className="appearance-none bg-[#f3f3fc] border border-[#e2e1eb] rounded-lg pl-3 pr-9 py-2 text-sm text-[#1a1b22] font-medium focus:outline-none focus:ring-2 focus:ring-[#093eaa]/30 focus:border-[#093eaa] hover:border-[#c4c5d5] transition-colors cursor-pointer"
              >
                <option value="ALL">{t('Tümü')}</option>
                {SECTIONS.map(s => <option key={s.key} value={s.key}>{s.label}</option>)}
              </select>
              <ChevronDown className="w-4 h-4 text-[#747684] absolute right-2.5 top-1/2 -translate-y-1/2 pointer-events-none" />
            </div>
          </div>
          <div className="flex items-center gap-2">
            <span className="text-xs font-semibold text-[#434653]">{t('Trend')}</span>
            <div className="relative">
              <select
                value={trendFilter}
                onChange={e => setTrendFilter(e.target.value)}
                className="appearance-none bg-[#f3f3fc] border border-[#e2e1eb] rounded-lg pl-3 pr-9 py-2 text-sm text-[#1a1b22] font-medium focus:outline-none focus:ring-2 focus:ring-[#093eaa]/30 focus:border-[#093eaa] hover:border-[#c4c5d5] transition-colors cursor-pointer"
              >
                <option value="ALL">{t('Tümü')}</option>
                <option value="UP">{t('Yükseliş')}</option>
                <option value="DOWN">{t('Düşüş')}</option>
                <option value="FLAT">{t('Yatay')}</option>
                <option value="NO_DATA">{t('Veri yok')}</option>
              </select>
              <ChevronDown className="w-4 h-4 text-[#747684] absolute right-2.5 top-1/2 -translate-y-1/2 pointer-events-none" />
            </div>
          </div>
        </div>
      )}

      {/* Tab içerikleri — her varlık türü ayrı kart (mockup düzeni) */}
      {activeTab === 'overview' && (
        loading ? (
          <div className="bg-white rounded-lg border border-gray-200 shadow-sm p-8 flex justify-center gap-1.5">
            <div className="w-2 h-2 bg-sky-500 rounded-full animate-bounce" />
            <div className="w-2 h-2 bg-sky-500/60 rounded-full animate-bounce [animation-delay:100ms]" />
            <div className="w-2 h-2 bg-sky-500/30 rounded-full animate-bounce [animation-delay:200ms]" />
          </div>
        ) : (
          <div className="space-y-4">
            {SECTIONS.map(sec => {
              const list = grouped[sec.key] || [];
              if (!list.length) return null;
              return (
                <div key={sec.key} className="bg-white rounded-lg border border-gray-200 shadow-sm overflow-hidden">
                  <div className={`px-5 py-3 border-b flex items-center justify-between ${toneClasses(sec.tone)}`}>
                    <h3 className="font-bold flex items-center gap-2">
                      {sec.label}
                      <span className="text-xs font-bold px-2 py-0.5 rounded-md bg-white/70 border border-black/10">
                        {list.length}
                      </span>
                    </h3>
                  </div>
                  <WatchlistTable
                    variant={sec.variant}
                    items={list}
                    onDelete={handleDelete}
                    deletingId={deletingId}
                    onAddToPortfolio={handleAddToPortfolio}
                    resolveDetailPath={resolveDetailPath}
                  />
                </div>
              );
            })}
          </div>
        )
      )}
      {activeTab === 'charts' && (
        <WatchlistCharts
          items={items}
          loading={loading}
          resolveDetailPath={resolveDetailPath}
          portfolioName={portfolio.name}
          portfolioId={portfolio.id}
        />
      )}

      {/* Özet bilgi */}
      {activeTab === 'overview' && !loading && (
        <p className="text-xs text-gray-400 pl-1">
          {t('{count} sembol takip ediliyor', { count: items.length })}
        </p>
      )}
    </div>
  );
}
