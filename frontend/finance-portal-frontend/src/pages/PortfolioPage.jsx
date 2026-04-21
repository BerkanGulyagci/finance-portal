import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { TrendingUp, TrendingDown, Plus, X, ChevronRight, Trash2 } from 'lucide-react';
import { getMyPortfolios, createPortfolio, deletePortfolio } from '../api/portfolioApi';

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

// ── Create Portfolio Modal ────────────────────────────────────────────────────
function CreatePortfolioModal({ onClose, onCreated }) {
  const [form, setForm] = useState({ name: '', description: '', currency: 'TRY' });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  async function handleSubmit(e) {
    e.preventDefault();
    if (!form.name.trim()) { setError('Portföy adı zorunludur.'); return; }
    setLoading(true);
    setError('');
    try {
      const p = await createPortfolio(form);
      onCreated(p);
      onClose();
    } catch (err) {
      setError(err.response?.data?.message || 'Portföy oluşturulamadı.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="fixed inset-0 bg-black/40 z-50 flex items-center justify-center p-4">
      <div className="bg-white rounded-2xl shadow-xl w-full max-w-md">
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100">
          <h2 className="font-bold text-gray-900">Yeni Portföy Oluştur</h2>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600"><X className="w-5 h-5" /></button>
        </div>
        <form onSubmit={handleSubmit} className="p-6 space-y-4">
          <div>
            <label className="block text-sm font-semibold text-gray-700 mb-1.5">Portföy Adı *</label>
            <input type="text" value={form.name} onChange={e => setForm(f => ({ ...f, name: e.target.value }))}
              placeholder="Örn: Hisse Portföyüm"
              className="w-full px-4 py-2.5 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-[#093eaa]/30 focus:border-[#093eaa]" />
          </div>
          <div>
            <label className="block text-sm font-semibold text-gray-700 mb-1.5">Açıklama</label>
            <input type="text" value={form.description} onChange={e => setForm(f => ({ ...f, description: e.target.value }))}
              placeholder="İsteğe bağlı"
              className="w-full px-4 py-2.5 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-[#093eaa]/30 focus:border-[#093eaa]" />
          </div>
          <div>
            <label className="block text-sm font-semibold text-gray-700 mb-1.5">Para Birimi</label>
            <select value={form.currency} onChange={e => setForm(f => ({ ...f, currency: e.target.value }))}
              className="w-full px-4 py-2.5 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-[#093eaa]/30 focus:border-[#093eaa] bg-white">
              {['TRY', 'USD', 'EUR', 'GBP'].map(c => <option key={c} value={c}>{c}</option>)}
            </select>
          </div>
          {error && <p className="text-rose-500 text-sm">{error}</p>}
          <div className="flex gap-3 pt-2">
            <button type="button" onClick={onClose}
              className="flex-1 px-4 py-2.5 border border-gray-200 rounded-xl text-sm font-semibold hover:bg-gray-50">
              İptal
            </button>
            <button type="submit" disabled={loading}
              className="flex-1 bg-[#093eaa] text-white px-4 py-2.5 rounded-xl text-sm font-semibold hover:bg-[#093eaa]/90 disabled:opacity-60">
              {loading ? 'Oluşturuluyor...' : 'Oluştur'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

// ── Main Page ─────────────────────────────────────────────────────────────────
export default function PortfolioPage() {
  const [portfolios, setPortfolios] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [showCreate, setShowCreate] = useState(false);
  const [deletingId, setDeletingId] = useState(null);

  async function handleDelete(e, portfolioId) {
    e.preventDefault();
    e.stopPropagation();
    if (!window.confirm('Bu portföyü silmek istediğinizden emin misiniz? Tüm işlemler de silinecek.')) return;
    setDeletingId(portfolioId);
    try {
      await deletePortfolio(portfolioId);
      setPortfolios(prev => prev.filter(p => p.id !== portfolioId));
    } catch {
      alert('Portföy silinemedi.');
    } finally {
      setDeletingId(null);
    }
  }

  useEffect(() => {
    getMyPortfolios()
      .then(setPortfolios)
      .catch(err => {
        if (!err.response) setError('Sunucuya ulaşılamıyor.');
        else if ([401, 403].includes(err.response.status)) setError('Bu sayfayı görüntülemek için giriş yapmanız gerekiyor.');
        else setError(`Portföyler yüklenemedi (${err.response.status}).`);
      })
      .finally(() => setLoading(false));
  }, []);

  return (
    <div>
      {showCreate && (
        <CreatePortfolioModal
          onClose={() => setShowCreate(false)}
          onCreated={p => setPortfolios(prev => [...prev, p])}
        />
      )}

      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900 border-l-4 border-[#093eaa] pl-4">Portföylerim</h1>
          <p className="text-sm text-gray-500 mt-1 pl-5">{portfolios.length} portföy</p>
        </div>
        <button onClick={() => setShowCreate(true)}
          className="flex items-center gap-2 bg-[#093eaa] text-white px-4 py-2 rounded-xl text-sm font-semibold hover:bg-[#093eaa]/90 transition-all">
          <Plus className="w-4 h-4" /> Yeni Portföy
        </button>
      </div>

      {loading && (
        <div className="bg-white rounded-2xl border border-gray-200 p-8 text-center">
          <div className="flex items-center justify-center gap-2">
            <div className="w-2 h-2 bg-[#093eaa] rounded-full animate-bounce" />
            <div className="w-2 h-2 bg-[#093eaa]/60 rounded-full animate-bounce [animation-delay:100ms]" />
            <div className="w-2 h-2 bg-[#093eaa]/30 rounded-full animate-bounce [animation-delay:200ms]" />
          </div>
          <p className="text-gray-400 text-sm mt-3">Yükleniyor...</p>
        </div>
      )}

      {error && <div className="bg-red-50 border border-red-200 text-red-600 text-sm px-6 py-4 rounded-2xl">{error}</div>}

      {!loading && !error && portfolios.length === 0 && (
        <div className="bg-white rounded-2xl border border-gray-200 p-12 text-center">
          <div className="w-16 h-16 bg-blue-50 rounded-full flex items-center justify-center mx-auto mb-4">
            <TrendingUp className="w-8 h-8 text-[#093eaa]" />
          </div>
          <h3 className="font-bold text-gray-900 mb-2">Henüz portföy yok</h3>
          <p className="text-gray-500 text-sm mb-4">İlk portföyünüzü oluşturun ve yatırımlarınızı takip edin.</p>
          <button onClick={() => setShowCreate(true)}
            className="bg-[#093eaa] text-white px-6 py-2 rounded-xl text-sm font-semibold hover:bg-[#093eaa]/90">
            Portföy Oluştur
          </button>
        </div>
      )}

      {!loading && !error && portfolios.length > 0 && (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {portfolios.map(p => {
            const pnl = parseFloat(p.totalProfitLoss ?? 0);
            const mv = parseFloat(p.totalMarketValue ?? 0);
            const cost = parseFloat(p.totalCost ?? 0);
            const pnlPct = cost > 0 ? ((pnl / cost) * 100).toFixed(2) : null;
            return (
              <Link key={p.id} to={`/portfolio/${p.id}`}
                className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6 hover:shadow-md hover:border-[#093eaa]/30 transition-all group">
                <div className="flex items-start justify-between mb-4">
                  <div className="flex-1 min-w-0">
                    <h3 className="font-bold text-gray-900 group-hover:text-[#093eaa] transition-colors truncate">{p.name}</h3>
                    {p.description && <p className="text-xs text-gray-400 mt-0.5 truncate">{p.description}</p>}
                  </div>
                  <div className="flex items-center gap-2 ml-2">
                    <span className="text-xs font-bold bg-blue-50 text-[#093eaa] px-2 py-1 rounded-lg">{p.currency}</span>
                    <ChevronRight className="w-4 h-4 text-gray-300 group-hover:text-[#093eaa] transition-colors" />
                  </div>
                </div>

                <div className="space-y-2.5">
                  <div className="flex justify-between text-sm">
                    <span className="text-gray-500">Toplam Maliyet</span>
                    <span className="font-semibold text-gray-900">{fmt(cost)}</span>
                  </div>
                  <div className="flex justify-between text-sm">
                    <span className="text-gray-500">Piyasa Değeri</span>
                    <span className="font-semibold text-gray-900">{mv > 0 ? fmt(mv) : '-'}</span>
                  </div>
                  <div className="flex justify-between text-sm pt-2 border-t border-gray-100">
                    <span className="text-gray-500">Kar / Zarar</span>
                    <div className="flex items-center gap-2">
                      <PnlBadge value={pnl} />
                      {pnlPct && <span className="text-xs text-gray-400">({pnlPct}%)</span>}
                    </div>
                  </div>
                </div>

                <div className="mt-4 pt-3 border-t border-gray-100 flex items-center justify-between text-xs text-gray-400">
                  <span>{p.holdings?.length ?? 0} varlık</span>
                  <div className="flex items-center gap-3">
                    <span>{p.transactions?.length ?? 0} işlem</span>
                    <button onClick={e => handleDelete(e, p.id)} disabled={deletingId === p.id}
                      className="text-gray-300 hover:text-rose-500 transition-colors disabled:opacity-40"
                      title="Portföyü sil">
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
