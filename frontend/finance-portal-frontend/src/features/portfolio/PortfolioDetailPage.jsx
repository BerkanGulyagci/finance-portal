import { useEffect, useState, useCallback } from 'react';
import { useParams, Link, useSearchParams } from 'react-router-dom';
import { ArrowLeft, FileSpreadsheet, FileText, Pencil } from 'lucide-react';
import { getPortfolioById } from '../../api/portfolioApi';
import { downloadPortfolioExcel, downloadPortfolioPdf } from './utils/portfolioReportExport';
import PortfolioTypeBadge from './components/PortfolioTypeBadge';
import EditPortfolioModal from './components/EditPortfolioModal';
import HoldingsDetail from './components/HoldingsDetail';
import WatchlistDetail from './components/WatchlistDetail';
import { useTranslation } from '../../context/LanguageContext';

export default function PortfolioDetailPage() {
  const { t } = useTranslation();
  const { id } = useParams();
  const [searchParams, setSearchParams] = useSearchParams();
  const [portfolio, setPortfolio]   = useState(null);
  const [loading, setLoading]       = useState(true);
  const [error, setError]           = useState('');
  const [exporting, setExporting]   = useState(false);
  const [showEdit, setShowEdit]     = useState(false);
  const [holdingsTab, setHoldingsTab] = useState('holdings'); // HoldingsDetail aktif tab — export "ekranda seçili"

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const data = await getPortfolioById(id);
      setPortfolio(data);
    } catch (err) {
      setError(!err.response ? t('Sunucuya ulaşılamıyor.') : t('Hata ({status})', { status: err.response.status }));
    } finally {
      setLoading(false);
    }
  }, [id, t]);

  useEffect(() => { load(); }, [load]);

  if (loading) {
    return (
      <div className="flex items-center justify-center py-20">
        <div className="flex gap-2">
          <div className="w-2 h-2 bg-[#093eaa] rounded-full animate-bounce" />
          <div className="w-2 h-2 bg-[#093eaa]/60 rounded-full animate-bounce [animation-delay:100ms]" />
          <div className="w-2 h-2 bg-[#093eaa]/30 rounded-full animate-bounce [animation-delay:200ms]" />
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="bg-red-50 border border-red-200 text-red-600 text-sm px-6 py-4 rounded-lg">
        {error}
      </div>
    );
  }

  if (!portfolio) return null;

  const isWatchlist = portfolio.portfolioType === 'WATCHLIST';
  const shouldOpenAddTx = searchParams.get('addTx') === '1';
  const initialInstrument = (!isWatchlist && shouldOpenAddTx && searchParams.get('symbol') && searchParams.get('assetType'))
    ? {
        symbol: searchParams.get('symbol'),
        assetType: searchParams.get('assetType'),
        name: searchParams.get('name') || searchParams.get('symbol'),
        price: searchParams.get('price') ? parseFloat(searchParams.get('price')) : undefined,
      }
    : null;

  function consumeInitialInstrument() {
    if (!shouldOpenAddTx) return;
    const next = new URLSearchParams(searchParams);
    ['addTx', 'symbol', 'assetType', 'name', 'price'].forEach(k => next.delete(k));
    setSearchParams(next, { replace: true });
  }

  /**
   * Ekrandaki seçili sekmeye göre export verisini hazırla:
   * - "holdings" sekmesi → sadece varlıklar
   * - "transactions" sekmesi → sadece işlemler
   * - charts/stats sekmeleri → her ikisi (varsayılan)
   */
  function buildExportOpts() {
    if (holdingsTab === 'holdings') {
      return { holdings: portfolio.holdings ?? [], transactions: [] };
    }
    if (holdingsTab === 'transactions') {
      return { holdings: [], transactions: portfolio.transactions ?? [] };
    }
    return { holdings: portfolio.holdings ?? [], transactions: portfolio.transactions ?? [] };
  }

  async function handleExportExcel() {
    setExporting(true);
    try {
      downloadPortfolioExcel(portfolio, buildExportOpts());
    } catch {
      alert(t('Excel dışa aktarılamadı.'));
    } finally {
      setExporting(false);
    }
  }

  async function handleExportPdf() {
    setExporting(true);
    try {
      await downloadPortfolioPdf(portfolio, buildExportOpts());
    } catch {
      alert(t('PDF dışa aktarılamadı.'));
    } finally {
      setExporting(false);
    }
  }

  return (
    <div className="space-y-6">
      {showEdit && (
        <EditPortfolioModal
          portfolio={portfolio}
          onClose={() => setShowEdit(false)}
          onUpdated={updated => { setPortfolio(prev => ({ ...prev, ...updated })); setShowEdit(false); }}
        />
      )}

      {/* Header — M3 kart */}
      <div className="bg-white rounded-lg border border-gray-200 shadow-sm p-5 flex items-start gap-3">
        <Link to="/portfolio" className="text-gray-400 hover:text-[#093eaa] transition-colors mt-1 shrink-0">
          <ArrowLeft className="w-5 h-5" />
        </Link>
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 flex-wrap">
            <h1 className="text-2xl font-bold text-gray-900 truncate">{portfolio.name}</h1>
            <PortfolioTypeBadge type={portfolio.portfolioType} />
            <span className="text-xs font-bold bg-gray-100 text-gray-500 px-2 py-0.5 rounded-md">
              {portfolio.currency}
            </span>
          </div>
          {portfolio.description && (
            <p className="text-sm text-gray-400 mt-0.5">{portfolio.description}</p>
          )}
        </div>
        <div className="flex items-center gap-2 shrink-0">
          <button
            type="button"
            onClick={() => setShowEdit(true)}
            className="inline-flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-semibold border border-gray-200 bg-white text-gray-700 hover:bg-gray-50 hover:border-[#093eaa]/40 hover:text-[#093eaa] transition-colors"
          >
            <Pencil className="w-4 h-4" />
            {t('Düzenle')}
          </button>
          {!isWatchlist && (
            <>
              <button
                type="button"
                onClick={handleExportExcel}
                disabled={exporting}
                className="inline-flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-semibold border border-gray-200 bg-white text-emerald-700 hover:bg-emerald-50 hover:border-emerald-300 transition-colors disabled:opacity-50"
                title={t('Excel (.xlsx) olarak indir')}
              >
                <FileSpreadsheet className="w-4 h-4" />
                {t('Excel')}
              </button>
              <button
                type="button"
                onClick={handleExportPdf}
                disabled={exporting}
                className="inline-flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-semibold border border-gray-200 bg-white text-rose-700 hover:bg-rose-50 hover:border-rose-300 transition-colors disabled:opacity-50"
                title={t('PDF rapor olarak indir')}
              >
                <FileText className="w-4 h-4" />
                {t('PDF')}
              </button>
            </>
          )}
        </div>
      </div>

      {/* Tip bazlı detay */}
      {isWatchlist ? (
        <WatchlistDetail portfolio={portfolio} />
      ) : (
        <HoldingsDetail
          portfolio={portfolio}
          onPortfolioUpdate={setPortfolio}
          initialInstrument={initialInstrument}
          onInitialInstrumentConsumed={consumeInitialInstrument}
          onActiveTabChange={setHoldingsTab}
        />
      )}
    </div>
  );
}
