import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { TrendingUp, TrendingDown, Plus, ChevronRight, Trash2, Pencil } from 'lucide-react';
import { getMyPortfolios, deletePortfolio } from '../../api/portfolioApi';
import CreatePortfolioModal from './components/CreatePortfolioModal';
import EditPortfolioModal from './components/EditPortfolioModal';
import PortfolioTypeBadge from './components/PortfolioTypeBadge';
import { useTranslation } from '../../i18n/LanguageContext';

function fmt(value, dec = 2) {
  if (value === null || value === undefined) return '-';
  const n = typeof value === 'string' ? parseFloat(value) : value;
  if (isNaN(n)) return '-';
  return n.toLocaleString('tr-TR', { minimumFractionDigits: dec, maximumFractionDigits: dec });
}

function PnlBadge({ value }) {
  const n = parseFloat(value ?? 0);
  const pos = n >= 0;
  return (
    <span className={`flex items-center gap-1 font-bold text-sm ${pos ? 'text-emerald-600' : 'text-rose-600'}`}>
      {pos ? <TrendingUp className="w-3.5 h-3.5" /> : <TrendingDown className="w-3.5 h-3.5" />}
      {pos ? '+' : ''}{fmt(n)}
    </span>
  );
}

export default function PortfolioPage() {
  const { t } = useTranslation();
  const [portfolios, setPortfolios] = useState([]);
  const [loading, setLoading]       = useState(true);
  const [error, setError]           = useState('');
  const [showCreate, setShowCreate] = useState(false);
  const [deletingId, setDeletingId] = useState(null);
  const [editingPortfolio, setEditingPortfolio] = useState(null);

  async function load() {
    setLoading(true);
    try {
      const data = await getMyPortfolios();
      setPortfolios(data ?? []);
    } catch (err) {
      if (!err.response) setError(t('Sunucuya ulaşılamıyor.'));
      else if ([401, 403].includes(err.response.status)) setError(t('Giriş yapmanız gerekiyor.'));
      else setError(t('Portföyler yüklenemedi ({status}).', { status: err.response.status }));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { load(); }, []);

  async function handleDelete(e, portfolioId) {
    e.preventDefault();
    e.stopPropagation();
    if (!window.confirm(t('Bu portföyü silmek istediğinizden emin misiniz? Tüm veriler silinecek.'))) return;
    setDeletingId(portfolioId);
    try {
      await deletePortfolio(portfolioId);
      setPortfolios(prev => prev.filter(p => p.id !== portfolioId));
    } catch {
      alert(t('Portföy silinemedi.'));
    } finally {
      setDeletingId(null);
    }
  }

  function handleEditUpdated(updated) {
    setPortfolios(prev => prev.map(p => p.id === updated.id ? { ...p, ...updated } : p));
  }

  return (
    <div>
      {showCreate && (
        <CreatePortfolioModal
          onClose={() => setShowCreate(false)}
          onCreated={list => { setPortfolios(list); setShowCreate(false); }}
        />
      )}

      {editingPortfolio && (
        <EditPortfolioModal
          portfolio={editingPortfolio}
          onClose={() => setEditingPortfolio(null)}
          onUpdated={handleEditUpdated}
        />
      )}

      {/* Başlık */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900 border-l-4 border-[#093eaa] pl-4">
            {t('Portföylerim')}
          </h1>
          <p className="text-sm text-gray-500 mt-1 pl-5">{t('{count} portföy', { count: portfolios.length })}</p>
        </div>
        <button
          onClick={() => setShowCreate(true)}
          className="flex items-center gap-2 bg-[#093eaa] text-white px-4 py-2 rounded-xl text-sm font-semibold hover:bg-[#093eaa]/90 transition-all"
        >
          <Plus className="w-4 h-4" /> {t('Yeni Portföy')}
        </button>
      </div>

      {/* Loading */}
      {loading && (
        <div className="bg-white rounded-2xl border border-gray-200 p-8 text-center">
          <div className="flex items-center justify-center gap-2">
            <div className="w-2 h-2 bg-[#093eaa] rounded-full animate-bounce" />
            <div className="w-2 h-2 bg-[#093eaa]/60 rounded-full animate-bounce [animation-delay:100ms]" />
            <div className="w-2 h-2 bg-[#093eaa]/30 rounded-full animate-bounce [animation-delay:200ms]" />
          </div>
          <p className="text-gray-400 text-sm mt-3">{t('Yükleniyor...')}</p>
        </div>
      )}

      {/* Hata */}
      {error && (
        <div className="bg-red-50 border border-red-200 text-red-600 text-sm px-6 py-4 rounded-2xl">
          {error}
        </div>
      )}

      {/* Boş durum */}
      {!loading && !error && portfolios.length === 0 && (
        <div className="bg-white rounded-2xl border border-gray-200 p-12 text-center">
          <div className="w-16 h-16 bg-blue-50 rounded-full flex items-center justify-center mx-auto mb-4">
            <TrendingUp className="w-8 h-8 text-[#093eaa]" />
          </div>
          <h3 className="font-bold text-gray-900 mb-2">{t('Henüz portföy yok')}</h3>
          <p className="text-gray-500 text-sm mb-4">
            {t('İlk portföyünüzü oluşturun ve yatırımlarınızı takip edin.')}
          </p>
          <button
            onClick={() => setShowCreate(true)}
            className="bg-[#093eaa] text-white px-6 py-2 rounded-xl text-sm font-semibold hover:bg-[#093eaa]/90"
          >
            {t('Portföy Oluştur')}
          </button>
        </div>
      )}

      {/* Kart grid */}
      {!loading && !error && portfolios.length > 0 && (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {portfolios.map(p => {
            const isWatchlist = p.portfolioType === 'WATCHLIST';
            const pnl    = parseFloat(p.totalProfitLoss ?? 0);
            const mv     = parseFloat(p.totalMarketValue ?? 0);
            const cost   = parseFloat(p.totalCost ?? 0);
            const pnlPct = cost > 0 ? ((pnl / cost) * 100).toFixed(2) : null;

            return (
              <Link
                key={p.id}
                to={`/portfolio/${p.id}`}
                className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6 hover:shadow-md hover:border-[#093eaa]/30 transition-all group"
              >
                {/* Kart başlığı */}
                <div className="flex items-start justify-between mb-3">
                  <div className="flex-1 min-w-0">
                    <h3 className="font-bold text-gray-900 group-hover:text-[#093eaa] transition-colors truncate">
                      {p.name}
                    </h3>
                    {p.description && (
                      <p className="text-xs text-gray-400 mt-0.5 truncate">{p.description}</p>
                    )}
                  </div>
                  <ChevronRight className="w-4 h-4 text-gray-300 group-hover:text-[#093eaa] transition-colors ml-2 mt-0.5 shrink-0" />
                </div>

                {/* Type badge + currency */}
                <div className="flex items-center gap-2 mb-4">
                  <PortfolioTypeBadge type={p.portfolioType} />
                  <span className="text-xs font-bold bg-gray-100 text-gray-500 px-2 py-0.5 rounded-full">
                    {p.currency}
                  </span>
                </div>

                {/* İçerik — WATCHLIST vs HOLDINGS */}
                {isWatchlist ? (
                  <div className="text-sm text-gray-500">
                    <span className="font-semibold text-gray-700">
                      {p.watchlistItemCount ?? 0}
                    </span> {t('sembol takip ediliyor')}
                  </div>
                ) : (
                  <div className="space-y-2.5">
                    <div className="flex justify-between text-sm">
                      <span className="text-gray-500">{t('Toplam Maliyet')}</span>
                      <span className="font-semibold text-gray-900">{fmt(cost)}</span>
                    </div>
                    <div className="flex justify-between text-sm">
                      <span className="text-gray-500">{t('Piyasa Değeri')}</span>
                      <span className="font-semibold text-gray-900">{mv > 0 ? fmt(mv) : '-'}</span>
                    </div>
                    <div className="flex justify-between text-sm pt-2 border-t border-gray-100">
                      <span className="text-gray-500">{t('Kâr / Zarar')}</span>
                      <div className="flex items-center gap-2">
                        <PnlBadge value={pnl} />
                        {pnlPct && <span className="text-xs text-gray-400">({pnlPct}%)</span>}
                      </div>
                    </div>
                  </div>
                )}

                {/* Alt bilgi */}
                <div className="mt-4 pt-3 border-t border-gray-100 flex items-center justify-between text-xs text-gray-400">
                  {isWatchlist ? (
                    <span>{t('{count} sembol · 0 işlem', { count: p.watchlistItemCount ?? 0 })}</span>
                  ) : (
                    <span>{t('{assets} varlık · {tx} işlem', { assets: p.holdings?.length ?? 0, tx: p.transactions?.length ?? 0 })}</span>
                  )}
                  <div className="flex items-center gap-2">
                    <button
                      onClick={e => { e.preventDefault(); e.stopPropagation(); setEditingPortfolio(p); }}
                      className="text-gray-300 hover:text-[#093eaa] transition-colors"
                      title={t('Portföyü düzenle')}
                    >
                      <Pencil className="w-3.5 h-3.5" />
                    </button>
                    <button
                      onClick={e => handleDelete(e, p.id)}
                      disabled={deletingId === p.id}
                      className="text-gray-300 hover:text-rose-500 transition-colors disabled:opacity-40"
                      title={t('Portföyü sil')}
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  </div>
                </div>
              </Link>
            );
          })}
        </div>
      )}
    </div>
  );
}
