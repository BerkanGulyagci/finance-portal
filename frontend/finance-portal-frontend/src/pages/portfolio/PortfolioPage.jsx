import { useEffect, useState, useMemo } from 'react';
import { Link } from 'react-router-dom';
import {
  TrendingUp, TrendingDown, Plus, ChevronRight, Trash2, Pencil,
  Wallet, CalendarClock, ShieldCheck, Trophy,
} from 'lucide-react';
import { getMyPortfolios, deletePortfolio } from '../../api/portfolioApi';
import CreatePortfolioModal from './components/CreatePortfolioModal';
import EditPortfolioModal from './components/EditPortfolioModal';
import PortfolioTypeBadge from './components/PortfolioTypeBadge';
import AlarmsManager from '../../components/instrument/AlarmsManager';
import { useTranslation } from '../../i18n/LanguageContext';

function fmt(value, dec = 2) {
  if (value === null || value === undefined) return '-';
  const n = typeof value === 'string' ? parseFloat(value) : value;
  if (isNaN(n)) return '-';
  return n.toLocaleString('tr-TR', { minimumFractionDigits: dec, maximumFractionDigits: dec });
}

function num(v) {
  const n = typeof v === 'string' ? parseFloat(v) : v;
  return Number.isFinite(n) ? n : 0;
}

function isTryCur(c) {
  return c == null || c === 'TRY' || c === 'TL';
}

/** Tüm varlık portföyleri üzerinden genel özet (üst metrik kartları için). */
function computeSummary(portfolios) {
  const holdings = (portfolios ?? []).filter(p => p.portfolioType !== 'WATCHLIST');
  let totalValue = 0, totalCost = 0, totalPnl = 0, totalReal = 0, totalDaily = 0;
  let hasReal = false, hasDaily = false;
  let best = null, worst = null;

  for (const p of holdings) {
    const mv = num(p.totalMarketValue);
    const cost = num(p.totalCost);
    const pnl = num(p.totalProfitLoss);
    totalValue += mv;
    totalCost += cost;
    totalPnl += pnl;
    if (p.totalRealProfitLoss != null) { totalReal += num(p.totalRealProfitLoss); hasReal = true; }

    // Günlük K/Z: yalnız TL pozisyonlar (kur karışmasın); qty × günlük birim değişim
    for (const h of p.holdings ?? []) {
      if (!isTryCur(h.currency)) continue;
      const q = num(h.totalQuantity);
      const ch = num(h.change);
      if (q && ch) { totalDaily += q * ch; hasDaily = true; }
    }

    const pct = cost > 0 ? (pnl / cost) * 100 : null;
    if (pct != null) {
      if (!best || pct > best.pct) best = { id: p.id, name: p.name, pct };
      if (!worst || pct < worst.pct) worst = { id: p.id, name: p.name, pct };
    }
  }

  const totalPnlPct = totalCost > 0 ? (totalPnl / totalCost) * 100 : null;
  const realCostSum = totalValue - totalReal;
  const totalRealPct = hasReal && realCostSum > 0 ? (totalReal / realCostSum) * 100 : null;
  const prevValue = totalValue - totalDaily;
  const totalDailyPct = hasDaily && prevValue > 0 ? (totalDaily / prevValue) * 100 : null;

  return {
    count: holdings.length,
    totalValue, totalCost, totalPnl, totalPnlPct,
    totalReal, totalRealPct, hasReal,
    totalDaily, totalDailyPct, hasDaily,
    best, worst,
  };
}

function MetricCard({ icon: Icon, label, value, sub, tone = 'neutral', delay = 0 }) {
  const toneMap = {
    neutral: { ring: 'border-gray-200', icon: 'text-[#093eaa] bg-[#093eaa]/10', val: 'text-gray-900' },
    up: { ring: 'border-emerald-100', icon: 'text-emerald-600 bg-emerald-50', val: 'text-emerald-600' },
    down: { ring: 'border-rose-100', icon: 'text-rose-600 bg-rose-50', val: 'text-rose-600' },
  };
  const c = toneMap[tone] ?? toneMap.neutral;
  return (
    <div
      className={`bg-white rounded-lg border ${c.ring} shadow-sm p-4 fp-fade-up`}
      style={{ animationDelay: `${delay}ms` }}
    >
      <div className="flex items-center gap-2 mb-2">
        <span className={`inline-flex items-center justify-center w-7 h-7 rounded-md ${c.icon}`}>
          <Icon className="w-4 h-4" />
        </span>
        <span className="text-xs font-semibold text-gray-500">{label}</span>
      </div>
      <div className={`text-xl font-bold tabular-nums ${c.val}`}>{value}</div>
      {sub && <div className="text-xs font-medium text-gray-400 mt-0.5">{sub}</div>}
    </div>
  );
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

  const summary = useMemo(() => computeSummary(portfolios), [portfolios]);

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
      <style>{`
        @keyframes fp-fade-up {
          from { opacity: 0; transform: translateY(12px); }
          to   { opacity: 1; transform: translateY(0); }
        }
        .fp-fade-up { opacity: 0; animation: fp-fade-up 0.45s cubic-bezier(0.22,1,0.36,1) forwards; }
      `}</style>

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
          className="flex items-center gap-2 bg-[#093eaa] text-white px-4 py-2 rounded-lg text-sm font-semibold hover:bg-[#093eaa]/90 transition-all"
        >
          <Plus className="w-4 h-4" /> {t('Yeni Portföy')}
        </button>
      </div>

      {/* Genel özet — metrik kartları */}
      {!loading && !error && summary.count > 0 && (
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
          <MetricCard
            icon={Wallet}
            label={t('Toplam Değer')}
            value={`₺${fmt(summary.totalValue)}`}
            sub={t('{count} portföy', { count: summary.count })}
            tone="neutral"
            delay={0}
          />
          <MetricCard
            icon={summary.totalPnl >= 0 ? TrendingUp : TrendingDown}
            label={t('Toplam K/Z')}
            value={`${summary.totalPnl >= 0 ? '+' : ''}₺${fmt(summary.totalPnl)}`}
            sub={summary.totalPnlPct != null
              ? `${summary.totalPnlPct >= 0 ? '+' : ''}${fmt(summary.totalPnlPct)}%`
              : null}
            tone={summary.totalPnl >= 0 ? 'up' : 'down'}
            delay={60}
          />
          {summary.hasDaily && (
            <MetricCard
              icon={CalendarClock}
              label={t('Günlük K/Z')}
              value={`${summary.totalDaily >= 0 ? '+' : ''}₺${fmt(summary.totalDaily)}`}
              sub={summary.totalDailyPct != null
                ? `${summary.totalDailyPct >= 0 ? '+' : ''}${fmt(summary.totalDailyPct)}%`
                : null}
              tone={summary.totalDaily >= 0 ? 'up' : 'down'}
              delay={120}
            />
          )}
          {summary.hasReal && (
            <MetricCard
              icon={ShieldCheck}
              label={t('Reel K/Z')}
              value={`${summary.totalReal >= 0 ? '+' : ''}₺${fmt(summary.totalReal)}`}
              sub={summary.totalRealPct != null
                ? `${summary.totalRealPct >= 0 ? '+' : ''}${fmt(summary.totalRealPct)}%`
                : null}
              tone={summary.totalReal >= 0 ? 'up' : 'down'}
              delay={180}
            />
          )}
        </div>
      )}

      {/* Loading */}
      {loading && (
        <div className="bg-white rounded-lg border border-gray-200 p-8 text-center">
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
        <div className="bg-red-50 border border-red-200 text-red-600 text-sm px-6 py-4 rounded-lg">
          {error}
        </div>
      )}

      {/* Boş durum */}
      {!loading && !error && portfolios.length === 0 && (
        <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
          <div className="w-16 h-16 bg-blue-50 rounded-full flex items-center justify-center mx-auto mb-4">
            <TrendingUp className="w-8 h-8 text-[#093eaa]" />
          </div>
          <h3 className="font-bold text-gray-900 mb-2">{t('Henüz portföy yok')}</h3>
          <p className="text-gray-500 text-sm mb-4">
            {t('İlk portföyünüzü oluşturun ve yatırımlarınızı takip edin.')}
          </p>
          <button
            onClick={() => setShowCreate(true)}
            className="bg-[#093eaa] text-white px-6 py-2 rounded-lg text-sm font-semibold hover:bg-[#093eaa]/90"
          >
            {t('Portföy Oluştur')}
          </button>
        </div>
      )}

      {/* Kart grid */}
      {!loading && !error && portfolios.length > 0 && (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {portfolios.map((p, idx) => {
            const isWatchlist = p.portfolioType === 'WATCHLIST';
            const pnl    = parseFloat(p.totalProfitLoss ?? 0);
            const mv     = parseFloat(p.totalMarketValue ?? 0);
            const cost   = parseFloat(p.totalCost ?? 0);
            const pnlPct = cost > 0 ? ((pnl / cost) * 100).toFixed(2) : null;
            const showRank = summary.best && summary.worst && summary.best.id !== summary.worst.id;
            const isBest  = showRank && p.id === summary.best.id;
            const isWorst = showRank && p.id === summary.worst.id;

            return (
              <Link
                key={p.id}
                to={`/portfolio/${p.id}`}
                className="bg-white rounded-lg border border-gray-200 shadow-sm p-6 hover:shadow-lg hover:border-[#093eaa]/30 hover:-translate-y-1 transition-all duration-200 group fp-fade-up"
                style={{ animationDelay: `${idx * 50}ms` }}
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
                <div className="flex flex-wrap items-center gap-2 mb-4">
                  <PortfolioTypeBadge type={p.portfolioType} />
                  <span className="text-xs font-bold bg-gray-100 text-gray-500 px-2 py-0.5 rounded-md">
                    {p.currency}
                  </span>
                  {isBest && (
                    <span className="inline-flex items-center gap-1 text-xs font-bold text-emerald-700 bg-emerald-50 border border-emerald-200 px-2 py-0.5 rounded-md">
                      <Trophy className="w-3 h-3" /> {t('En İyi Getiri')}
                    </span>
                  )}
                  {isWorst && (
                    <span className="inline-flex items-center gap-1 text-xs font-bold text-rose-700 bg-rose-50 border border-rose-200 px-2 py-0.5 rounded-md">
                      <TrendingDown className="w-3 h-3" /> {t('En Kötü Getiri')}
                    </span>
                  )}
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

      {/* Alarmlarım */}
      <div className="mt-8 bg-white rounded-2xl border border-gray-200 shadow-sm p-6">
        <AlarmsManager compact />
      </div>
    </div>
  );
}
