import { useEffect, useState, useCallback, useMemo, useRef } from 'react';
import { useParams, Link } from 'react-router-dom';
import { ArrowLeft, TrendingUp, TrendingDown, Plus, X, Trash2 } from 'lucide-react';
import { PieChart, Pie, Cell, Tooltip, ResponsiveContainer, Legend } from 'recharts';
import { getPortfolioById, addTransaction, deleteTransaction } from '../api/portfolioApi';
import { searchAssetSymbols, getAssetPrice } from '../api/marketApi';

function fmt(v, dec = 2) {
  if (v == null) return '-';
  const n = typeof v === 'string' ? parseFloat(v) : v;
  if (isNaN(n)) return '-';
  return n.toLocaleString('tr-TR', { minimumFractionDigits: dec, maximumFractionDigits: dec });
}

function PnlCell({ value }) {
  const n = parseFloat(value ?? 0);
  const pos = n >= 0;
  return (
    <span className={`font-bold ${pos ? 'text-emerald-600' : 'text-rose-600'}`}>
      {pos ? '+' : ''}{fmt(n)}
    </span>
  );
}

const ASSET_TYPES = [
  { value: 'STOCK',  label: 'Hisse Senedi' },
  { value: 'CRYPTO', label: 'Kripto Para' },
  { value: 'FX',     label: 'Döviz' },
  { value: 'FUND',   label: 'Fon / ETF' },
  { value: 'FUTURE', label: 'Vadeli' },
];

const ASSET_PLACEHOLDERS = {
  STOCK:  'THYAO.IS, AAPL',
  CRYPTO: 'btc, eth',
  FX:     'USD, EUR, GBP',
  FUND:   'SPY, QQQ, GLD',
  FUTURE: 'ES=F, GC=F, CL=F',
};

// ── Add Transaction Modal ─────────────────────────────────────────────────────
function AddTransactionModal({ portfolioId, onClose, onAdded }) {
  const now = new Date();
  const localNow = new Date(now.getTime() - now.getTimezoneOffset() * 60000)
    .toISOString().slice(0, 16);

  const [form, setForm] = useState({
    assetType: 'STOCK',
    symbol: '',
    transactionType: 'BUY',
    quantity: '',
    price: '',
    commission: '',
    transactionDate: localNow,
  });

  // Autocomplete state
  const [suggestions, setSuggestions] = useState([]);
  const [showSuggestions, setShowSuggestions] = useState(false);
  const [symbolStatus, setSymbolStatus] = useState(null); // null | 'loading' | 'valid' | 'invalid'
  const [symbolName, setSymbolName] = useState('');
  const suggestRef = useRef(null);
  const searchTimer = useRef(null);
  const priceTimer = useRef(null);

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  function set(key, val) { setForm(f => ({ ...f, [key]: val })); }

  // Enstrüman tipi değişince sembolü sıfırla
  function handleTypeChange(newType) {
    set('assetType', newType);
    set('symbol', '');
    set('price', '');
    setSymbolStatus(null);
    setSymbolName('');
    setSuggestions([]);
  }

  // Sembol input değişince autocomplete
  function handleSymbolChange(val) {
    set('symbol', val);
    set('price', '');
    setSymbolStatus(null);
    setSymbolName('');

    clearTimeout(searchTimer.current);
    if (val.trim().length < 1) { setSuggestions([]); setShowSuggestions(false); return; }

    searchTimer.current = setTimeout(async () => {
      try {
        const results = await searchAssetSymbols(form.assetType, val.trim());
        setSuggestions(results);
        setShowSuggestions(results.length > 0);
      } catch { setSuggestions([]); }
    }, 250);
  }

  // Sembol seçilince fiyat çek
  async function selectSymbol(sym) {
    set('symbol', sym);
    setSuggestions([]);
    setShowSuggestions(false);
    setSymbolStatus('loading');
    setSymbolName('');
    set('price', '');

    try {
      const result = await getAssetPrice(form.assetType, sym);
      if (result?.valid) {
        set('price', result.price?.toString() ?? '');
        setSymbolStatus('valid');
        setSymbolName(result.name ?? '');
      } else {
        setSymbolStatus('invalid');
      }
    } catch {
      setSymbolStatus('invalid');
    }
  }

  // Sembol input blur'da doğrula
  async function handleSymbolBlur() {
    setTimeout(() => setShowSuggestions(false), 150);
    const sym = form.symbol.trim();
    if (!sym || symbolStatus === 'valid') return;
    setSymbolStatus('loading');
    try {
      const result = await getAssetPrice(form.assetType, sym);
      if (result?.valid) {
        set('price', result.price?.toString() ?? '');
        setSymbolStatus('valid');
      } else {
        setSymbolStatus('invalid');
      }
    } catch {
      setSymbolStatus('invalid');
    }
  }

  async function handleSubmit(e) {
    e.preventDefault();
    if (!form.symbol.trim()) { setError('Sembol zorunludur.'); return; }
    if (symbolStatus === 'invalid') { setError('Geçersiz sembol. Lütfen listeden seçin.'); return; }
    if (!form.quantity || parseFloat(form.quantity) <= 0) { setError('Miktar 0\'dan büyük olmalıdır.'); return; }
    if (!form.price || parseFloat(form.price) <= 0) { setError('Fiyat 0\'dan büyük olmalıdır.'); return; }

    setLoading(true);
    setError('');
    try {
      const payload = {
        symbol: form.symbol.trim(),
        assetType: form.assetType,
        transactionType: form.transactionType,
        quantity: parseFloat(form.quantity),
        price: parseFloat(form.price),
        commission: form.commission ? parseFloat(form.commission) : 0,
        transactionDate: new Date(form.transactionDate).toISOString().replace('Z', ''),
      };
      const updated = await addTransaction(portfolioId, payload);
      onAdded(updated);
      onClose();
    } catch (err) {
      const msg = err.response?.data?.message || err.response?.data?.data;
      setError(typeof msg === 'string' ? msg : 'İşlem eklenemedi.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="fixed inset-0 bg-black/40 z-50 flex items-center justify-center p-4">
      <div className="bg-white rounded-2xl shadow-xl w-full max-w-lg max-h-[90vh] overflow-y-auto">
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100 sticky top-0 bg-white">
          <h2 className="font-bold text-gray-900">İşlem Ekle</h2>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600"><X className="w-5 h-5" /></button>
        </div>

        <form onSubmit={handleSubmit} className="p-6 space-y-4">
          {/* İşlem tipi */}
          <div className="grid grid-cols-2 gap-2">
            {['BUY', 'SELL'].map(t => (
              <button key={t} type="button" onClick={() => set('transactionType', t)}
                className={`py-2.5 rounded-xl text-sm font-bold transition-all ${
                  form.transactionType === t
                    ? t === 'BUY' ? 'bg-emerald-500 text-white' : 'bg-rose-500 text-white'
                    : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
                }`}>
                {t === 'BUY' ? '📈 Alış' : '📉 Satış'}
              </button>
            ))}
          </div>

          {/* Enstrüman tipi */}
          <div>
            <label className="block text-sm font-semibold text-gray-700 mb-1.5">Enstrüman Türü</label>
            <select value={form.assetType} onChange={e => handleTypeChange(e.target.value)}
              className="w-full px-4 py-2.5 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-[#093eaa]/30 focus:border-[#093eaa] bg-white">
              {ASSET_TYPES.map(a => <option key={a.value} value={a.value}>{a.label}</option>)}
            </select>
          </div>

          {/* Sembol — autocomplete */}
          <div>
            <label className="block text-sm font-semibold text-gray-700 mb-1.5">Sembol *</label>
            <div className="relative" ref={suggestRef}>
              <input type="text" value={form.symbol}
                onChange={e => handleSymbolChange(e.target.value)}
                onBlur={handleSymbolBlur}
                onFocus={() => form.symbol && setSuggestions(s => s)}
                placeholder={ASSET_PLACEHOLDERS[form.assetType]}
                className={`w-full px-4 py-2.5 border rounded-xl text-sm focus:outline-none focus:ring-2 pr-10 ${
                  symbolStatus === 'valid' ? 'border-emerald-400 focus:ring-emerald-200' :
                  symbolStatus === 'invalid' ? 'border-rose-400 focus:ring-rose-200' :
                  'border-gray-200 focus:ring-[#093eaa]/30 focus:border-[#093eaa]'
                }`} />
              {/* Status icon */}
              <div className="absolute right-3 top-1/2 -translate-y-1/2">
                {symbolStatus === 'loading' && <div className="w-4 h-4 border-2 border-[#093eaa] border-t-transparent rounded-full animate-spin" />}
                {symbolStatus === 'valid' && <span className="text-emerald-500 text-sm">✓</span>}
                {symbolStatus === 'invalid' && <span className="text-rose-500 text-sm">✗</span>}
              </div>

              {/* Autocomplete dropdown */}
              {showSuggestions && suggestions.length > 0 && (
                <div className="absolute top-full left-0 right-0 mt-1 bg-white border border-gray-200 rounded-xl shadow-lg z-50 max-h-48 overflow-y-auto">
                  {suggestions.map(s => (
                    <button key={s} type="button"
                      onMouseDown={() => selectSymbol(s)}
                      className="w-full text-left px-4 py-2.5 text-sm hover:bg-blue-50 hover:text-[#093eaa] transition-colors font-mono">
                      {s}
                    </button>
                  ))}
                </div>
              )}
            </div>
            {symbolStatus === 'valid' && symbolName && (
              <p className="text-xs text-emerald-600 mt-1">✓ {symbolName}</p>
            )}
            {symbolStatus === 'invalid' && (
              <p className="text-xs text-rose-500 mt-1">Bu sembol bulunamadı. Lütfen geçerli bir sembol girin.</p>
            )}
            {symbolStatus === null && (
              <p className="text-xs text-gray-400 mt-1">Örn: {ASSET_PLACEHOLDERS[form.assetType]}</p>
            )}
          </div>

          {/* Miktar + Fiyat */}
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-sm font-semibold text-gray-700 mb-1.5">Miktar *</label>
              <input type="number" step="any" min="0" value={form.quantity} onChange={e => set('quantity', e.target.value)}
                placeholder="0.00"
                className="w-full px-4 py-2.5 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-[#093eaa]/30 focus:border-[#093eaa]" />
            </div>
            <div>
              <label className="block text-sm font-semibold text-gray-700 mb-1.5">
                Fiyat *
                {symbolStatus === 'valid' && form.price && (
                  <span className="ml-1 text-xs text-emerald-500 font-normal">otomatik</span>
                )}
              </label>
              <input type="number" step="any" min="0" value={form.price} onChange={e => set('price', e.target.value)}
                placeholder="0.00"
                className={`w-full px-4 py-2.5 border rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-[#093eaa]/30 focus:border-[#093eaa] ${
                  symbolStatus === 'valid' && form.price ? 'border-emerald-300 bg-emerald-50' : 'border-gray-200'
                }`} />
            </div>
          </div>

          {/* Komisyon */}
          <div>
            <label className="block text-sm font-semibold text-gray-700 mb-1.5">Komisyon</label>
            <input type="number" step="any" min="0" value={form.commission} onChange={e => set('commission', e.target.value)}
              placeholder="0.00 (isteğe bağlı)"
              className="w-full px-4 py-2.5 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-[#093eaa]/30 focus:border-[#093eaa]" />
          </div>

          {/* Tarih */}
          <div>
            <label className="block text-sm font-semibold text-gray-700 mb-1.5">İşlem Tarihi *</label>
            <input type="datetime-local" value={form.transactionDate} onChange={e => set('transactionDate', e.target.value)}
              className="w-full px-4 py-2.5 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-[#093eaa]/30 focus:border-[#093eaa]" />
          </div>

          {/* Özet */}
          {form.quantity && form.price && (
            <div className="bg-gray-50 rounded-xl p-3 text-sm">
              <div className="flex justify-between text-gray-600">
                <span>Toplam Tutar</span>
                <span className="font-bold text-gray-900">
                  {(parseFloat(form.quantity || 0) * parseFloat(form.price || 0)).toLocaleString('tr-TR', { minimumFractionDigits: 2 })}
                </span>
              </div>
              {form.commission && (
                <div className="flex justify-between text-gray-600 mt-1">
                  <span>Komisyon dahil</span>
                  <span className="font-bold text-gray-900">
                    {(parseFloat(form.quantity || 0) * parseFloat(form.price || 0) + parseFloat(form.commission || 0)).toLocaleString('tr-TR', { minimumFractionDigits: 2 })}
                  </span>
                </div>
              )}
            </div>
          )}

          {error && <p className="text-rose-500 text-sm bg-rose-50 px-3 py-2 rounded-lg">{error}</p>}

          <div className="flex gap-3 pt-2">
            <button type="button" onClick={onClose}
              className="flex-1 px-4 py-2.5 border border-gray-200 rounded-xl text-sm font-semibold hover:bg-gray-50">
              İptal
            </button>
            <button type="submit" disabled={loading || symbolStatus === 'invalid'}
              className={`flex-1 text-white px-4 py-2.5 rounded-xl text-sm font-semibold disabled:opacity-60 ${
                form.transactionType === 'BUY' ? 'bg-emerald-500 hover:bg-emerald-600' : 'bg-rose-500 hover:bg-rose-600'
              }`}>
              {loading ? 'Ekleniyor...' : form.transactionType === 'BUY' ? 'Alış Ekle' : 'Satış Ekle'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

// ── Main Page ─────────────────────────────────────────────────────────────────
export default function PortfolioDetailPage() {
  const { id } = useParams();
  const [portfolio, setPortfolio] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [showAddTx, setShowAddTx] = useState(false);
  const [activeTab, setActiveTab] = useState('holdings');
  const [deletingTxId, setDeletingTxId] = useState(null);

  async function handleDeleteTx(txId) {
    if (!window.confirm('Bu işlemi silmek istediğinizden emin misiniz?')) return;
    setDeletingTxId(txId);
    try {
      const updated = await deleteTransaction(id, txId);
      setPortfolio(updated);
    } catch {
      alert('İşlem silinemedi.');
    } finally {
      setDeletingTxId(null);
    }
  }

  const load = useCallback(() => {
    setLoading(true);
    getPortfolioById(id)
      .then(setPortfolio)
      .catch(err => setError(!err.response ? 'Sunucuya ulaşılamıyor.' : `Hata (${err.response.status})`))
      .finally(() => setLoading(false));
  }, [id]);

  useEffect(() => { load(); }, [load]);

  if (loading) return (
    <div className="flex items-center justify-center py-20">
      <div className="flex gap-2">
        <div className="w-2 h-2 bg-[#093eaa] rounded-full animate-bounce" />
        <div className="w-2 h-2 bg-[#093eaa]/60 rounded-full animate-bounce [animation-delay:100ms]" />
        <div className="w-2 h-2 bg-[#093eaa]/30 rounded-full animate-bounce [animation-delay:200ms]" />
      </div>
    </div>
  );

  if (error) return <div className="bg-red-50 border border-red-200 text-red-600 text-sm px-6 py-4 rounded-2xl">{error}</div>;
  if (!portfolio) return null;

  const pnl = parseFloat(portfolio.totalProfitLoss ?? 0);
  const cost = parseFloat(portfolio.totalCost ?? 0);
  const mv = parseFloat(portfolio.totalMarketValue ?? 0);
  const pnlPct = cost > 0 ? ((pnl / cost) * 100).toFixed(2) : null;
  const isPos = pnl >= 0;

  return (
    <div className="space-y-6">
      {showAddTx && (
        <AddTransactionModal
          portfolioId={id}
          onClose={() => setShowAddTx(false)}
          onAdded={updated => { setPortfolio(updated); setShowAddTx(false); }}
        />
      )}

      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <Link to="/portfolio" className="text-gray-400 hover:text-[#093eaa] transition-colors">
            <ArrowLeft className="w-5 h-5" />
          </Link>
          <div>
            <h1 className="text-2xl font-bold text-gray-900">{portfolio.name}</h1>
            {portfolio.description && <p className="text-sm text-gray-400">{portfolio.description}</p>}
          </div>
          <span className="text-xs font-bold bg-blue-50 text-[#093eaa] px-2 py-1 rounded-lg">{portfolio.currency}</span>
        </div>
        <button onClick={() => setShowAddTx(true)}
          className="flex items-center gap-2 bg-[#093eaa] text-white px-4 py-2 rounded-xl text-sm font-semibold hover:bg-[#093eaa]/90 transition-all">
          <Plus className="w-4 h-4" /> İşlem Ekle
        </button>
      </div>

      {/* Summary Cards */}
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
          <p className="text-xs text-gray-500 font-semibold mb-1">Kar / Zarar</p>
          <p className={`text-xl font-bold flex items-center gap-1 ${isPos ? 'text-emerald-600' : 'text-rose-600'}`}>
            {isPos ? <TrendingUp className="w-4 h-4" /> : <TrendingDown className="w-4 h-4" />}
            {fmt(pnl)}
          </p>
          {pnlPct && <p className={`text-xs mt-0.5 ${isPos ? 'text-emerald-500' : 'text-rose-500'}`}>{isPos ? '+' : ''}{pnlPct}%</p>}
        </div>
        <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-5">
          <p className="text-xs text-gray-500 font-semibold mb-1">Varlık Sayısı</p>
          <p className="text-xl font-bold text-gray-900">{portfolio.holdings?.length ?? 0}</p>
          <p className="text-xs text-gray-400 mt-0.5">{portfolio.transactions?.length ?? 0} işlem</p>
        </div>
      </div>

      {/* Tabs */}
      <div className="flex gap-2">
        {[{ key: 'holdings', label: 'Varlıklar' }, { key: 'transactions', label: 'İşlem Geçmişi' }, { key: 'analysis', label: '📊 Analiz' }].map(t => (
          <button key={t.key} onClick={() => setActiveTab(t.key)}
            className={`px-4 py-2 rounded-xl text-sm font-semibold transition-all ${
              activeTab === t.key ? 'bg-[#093eaa] text-white' : 'bg-white text-gray-600 border border-gray-200 hover:bg-gray-50'
            }`}>
            {t.label}
          </button>
        ))}
      </div>

      {/* Holdings Tab */}
      {activeTab === 'holdings' && (
        <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden">
          {!portfolio.holdings?.length
            ? (
              <div className="p-12 text-center">
                <p className="text-gray-400 text-sm">Henüz varlık yok.</p>
                <button onClick={() => setShowAddTx(true)}
                  className="mt-3 text-[#093eaa] text-sm font-semibold hover:underline">
                  İlk işlemi ekle →
                </button>
              </div>
            )
            : (
              <div className="overflow-x-auto">
                <table className="w-full">
                  <thead className="bg-gray-50">
                    <tr>
                      {['Sembol', 'Tür', 'Miktar', 'Ort. Maliyet', 'Güncel Fiyat', 'Piyasa Değeri', 'K/Z', 'K/Z %'].map(h => (
                        <th key={h} className="text-left px-4 py-3 text-xs font-bold text-gray-500 uppercase tracking-wider whitespace-nowrap border-b border-gray-200">{h}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {portfolio.holdings.map((h, i) => {
                      const hpnl = parseFloat(h.profitLoss ?? 0);
                      const hcost = parseFloat(h.totalCost ?? 0);
                      const hpct = hcost > 0 ? ((hpnl / hcost) * 100).toFixed(2) : null;
                      return (
                        <tr key={i} className="border-t border-gray-100 hover:bg-gray-50 transition-colors">
                          <td className="px-4 py-3 font-bold text-[#093eaa] text-sm">{h.symbol}</td>
                          <td className="px-4 py-3">
                            <span className="text-xs bg-gray-100 text-gray-600 px-2 py-0.5 rounded-full font-semibold">{h.assetType}</span>
                          </td>
                          <td className="px-4 py-3 text-sm font-mono">{fmt(h.totalQuantity, 4)}</td>
                          <td className="px-4 py-3 text-sm">{fmt(h.averageCost)}</td>
                          <td className="px-4 py-3 text-sm font-semibold">{h.currentPrice ? fmt(h.currentPrice) : <span className="text-gray-300">-</span>}</td>
                          <td className="px-4 py-3 text-sm font-semibold">{h.marketValue ? fmt(h.marketValue) : <span className="text-gray-300">-</span>}</td>
                          <td className="px-4 py-3 text-sm"><PnlCell value={hpnl} /></td>
                          <td className="px-4 py-3 text-sm">
                            {hpct ? <span className={parseFloat(hpct) >= 0 ? 'text-emerald-600 font-semibold' : 'text-rose-600 font-semibold'}>{parseFloat(hpct) >= 0 ? '+' : ''}{hpct}%</span> : '-'}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )
          }
        </div>
      )}

      {/* Transactions Tab */}
      {activeTab === 'transactions' && (
        <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden">
          {!portfolio.transactions?.length
            ? (
              <div className="p-12 text-center">
                <p className="text-gray-400 text-sm">Henüz işlem yok.</p>
                <button onClick={() => setShowAddTx(true)}
                  className="mt-3 text-[#093eaa] text-sm font-semibold hover:underline">
                  İlk işlemi ekle →
                </button>
              </div>
            )
            : (
              <div className="overflow-x-auto">
                <table className="w-full">
                  <thead className="bg-gray-50">
                    <tr>
                      {['Tarih', 'Sembol', 'Tür', 'İşlem', 'Miktar', 'Fiyat', 'Toplam', 'Komisyon', ''].map(h => (
                        <th key={h} className="text-left px-4 py-3 text-xs font-bold text-gray-500 uppercase tracking-wider whitespace-nowrap border-b border-gray-200">{h}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {portfolio.transactions.map((t, i) => {
                      const total = parseFloat(t.quantity ?? 0) * parseFloat(t.price ?? 0);
                      return (
                        <tr key={i} className="border-t border-gray-100 hover:bg-gray-50 transition-colors">
                          <td className="px-4 py-3 text-xs text-gray-400 whitespace-nowrap">{t.transactionDate?.split('T')[0] ?? '-'}</td>
                          <td className="px-4 py-3 font-bold text-[#093eaa] text-sm">{t.symbol}</td>
                          <td className="px-4 py-3">
                            <span className="text-xs bg-gray-100 text-gray-600 px-2 py-0.5 rounded-full font-semibold">{t.assetType}</span>
                          </td>
                          <td className="px-4 py-3">
                            <span className={`text-xs font-bold px-2 py-0.5 rounded-full ${t.transactionType === 'BUY' ? 'bg-emerald-100 text-emerald-700' : 'bg-rose-100 text-rose-700'}`}>
                              {t.transactionType === 'BUY' ? 'ALIŞ' : 'SATIŞ'}
                            </span>
                          </td>
                          <td className="px-4 py-3 text-sm font-mono">{fmt(t.quantity, 4)}</td>
                          <td className="px-4 py-3 text-sm">{fmt(t.price)}</td>
                          <td className="px-4 py-3 text-sm font-semibold">{fmt(total)}</td>
                          <td className="px-4 py-3 text-sm text-gray-400">{fmt(t.commission)}</td>
                          <td className="px-4 py-3">
                            <button onClick={() => handleDeleteTx(t.id)} disabled={deletingTxId === t.id}
                              className="text-gray-300 hover:text-rose-500 transition-colors disabled:opacity-40"
                              title="İşlemi sil">
                              <Trash2 className="w-3.5 h-3.5" />
                            </button>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )
          }
        </div>
      )}

      {/* Analysis Tab */}
      {activeTab === 'analysis' && <PortfolioAnalysis portfolio={portfolio} />}
    </div>
  );
}

// ── Portfolio Analysis Component ──────────────────────────────────────────────
const COLORS = ['#093eaa', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#06b6d4', '#f97316', '#84cc16'];

const ASSET_LABELS = {
  STOCK: 'Hisse', CRYPTO: 'Kripto', FX: 'Döviz', FUND: 'Fon', FUTURE: 'Vadeli'
};

function PortfolioAnalysis({ portfolio }) {
  const holdings = portfolio.holdings ?? [];

  // Enstrüman bazlı dağılım (piyasa değeri veya maliyet)
  const bySymbol = useMemo(() => {
    return holdings
      .map(h => ({
        name: h.symbol,
        value: parseFloat(h.marketValue ?? h.totalCost ?? 0),
        type: h.assetType,
      }))
      .filter(d => d.value > 0)
      .sort((a, b) => b.value - a.value);
  }, [holdings]);

  // Enstrüman türü bazlı dağılım
  const byType = useMemo(() => {
    const map = {};
    holdings.forEach(h => {
      const type = h.assetType;
      const val = parseFloat(h.marketValue ?? h.totalCost ?? 0);
      if (val > 0) map[type] = (map[type] ?? 0) + val;
    });
    return Object.entries(map).map(([type, value]) => ({
      name: ASSET_LABELS[type] ?? type,
      value,
    })).sort((a, b) => b.value - a.value);
  }, [holdings]);

  const totalValue = bySymbol.reduce((s, d) => s + d.value, 0);

  // Toplam getiri
  const totalCost = parseFloat(portfolio.totalCost ?? 0);
  const totalMv = parseFloat(portfolio.totalMarketValue ?? 0);
  const totalPnl = parseFloat(portfolio.totalProfitLoss ?? 0);
  const totalPnlPct = totalCost > 0 ? ((totalPnl / totalCost) * 100) : 0;

  const CustomTooltip = ({ active, payload }) => {
    if (!active || !payload?.length) return null;
    const d = payload[0];
    const pct = totalValue > 0 ? ((d.value / totalValue) * 100).toFixed(1) : 0;
    return (
      <div className="bg-white border border-gray-200 rounded-xl shadow-lg px-4 py-3 text-sm">
        <p className="font-bold text-gray-900">{d.name}</p>
        <p className="text-gray-600">{d.value.toLocaleString('tr-TR', { minimumFractionDigits: 2 })}</p>
        <p className="text-[#093eaa] font-semibold">%{pct}</p>
      </div>
    );
  };

  if (holdings.length === 0) {
    return (
      <div className="bg-white rounded-2xl border border-gray-200 p-12 text-center">
        <p className="text-gray-400 text-sm">Analiz için önce işlem ekleyin.</p>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Özet istatistikler */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
        <div className="bg-white rounded-2xl border border-gray-200 p-4">
          <p className="text-xs text-gray-500 mb-1">Toplam Getiri</p>
          <p className={`text-lg font-bold ${totalPnl >= 0 ? 'text-emerald-600' : 'text-rose-600'}`}>
            {totalPnl >= 0 ? '+' : ''}{totalPnl.toLocaleString('tr-TR', { minimumFractionDigits: 2 })}
          </p>
          <p className={`text-xs mt-0.5 ${totalPnl >= 0 ? 'text-emerald-500' : 'text-rose-500'}`}>
            {totalPnlPct >= 0 ? '+' : ''}{totalPnlPct.toFixed(2)}%
          </p>
        </div>
        <div className="bg-white rounded-2xl border border-gray-200 p-4">
          <p className="text-xs text-gray-500 mb-1">Farklı Varlık</p>
          <p className="text-lg font-bold text-gray-900">{holdings.length}</p>
        </div>
        <div className="bg-white rounded-2xl border border-gray-200 p-4">
          <p className="text-xs text-gray-500 mb-1">En İyi Varlık</p>
          {(() => {
            const best = [...holdings].sort((a, b) => parseFloat(b.profitLoss ?? 0) - parseFloat(a.profitLoss ?? 0))[0];
            const bpnl = parseFloat(best?.profitLoss ?? 0);
            return best ? (
              <>
                <p className="text-sm font-bold text-[#093eaa]">{best.symbol}</p>
                <p className={`text-xs ${bpnl >= 0 ? 'text-emerald-500' : 'text-rose-500'}`}>
                  {bpnl >= 0 ? '+' : ''}{bpnl.toLocaleString('tr-TR', { minimumFractionDigits: 2 })}
                </p>
              </>
            ) : <p className="text-sm text-gray-400">-</p>;
          })()}
        </div>
        <div className="bg-white rounded-2xl border border-gray-200 p-4">
          <p className="text-xs text-gray-500 mb-1">En Kötü Varlık</p>
          {(() => {
            const worst = [...holdings].sort((a, b) => parseFloat(a.profitLoss ?? 0) - parseFloat(b.profitLoss ?? 0))[0];
            const wpnl = parseFloat(worst?.profitLoss ?? 0);
            return worst ? (
              <>
                <p className="text-sm font-bold text-[#093eaa]">{worst.symbol}</p>
                <p className={`text-xs ${wpnl >= 0 ? 'text-emerald-500' : 'text-rose-500'}`}>
                  {wpnl >= 0 ? '+' : ''}{wpnl.toLocaleString('tr-TR', { minimumFractionDigits: 2 })}
                </p>
              </>
            ) : <p className="text-sm text-gray-400">-</p>;
          })()}
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Sembol bazlı pie chart */}
        <div className="bg-white rounded-2xl border border-gray-200 p-6">
          <h3 className="font-bold text-gray-900 mb-4">Varlık Dağılımı</h3>
          <ResponsiveContainer width="100%" height={260}>
            <PieChart>
              <Pie data={bySymbol} cx="50%" cy="50%" innerRadius={60} outerRadius={100}
                dataKey="value" nameKey="name" paddingAngle={2}>
                {bySymbol.map((_, i) => (
                  <Cell key={i} fill={COLORS[i % COLORS.length]} />
                ))}
              </Pie>
              <Tooltip content={<CustomTooltip />} />
            </PieChart>
          </ResponsiveContainer>
          {/* Legend */}
          <div className="mt-3 space-y-1.5">
            {bySymbol.map((d, i) => {
              const pct = totalValue > 0 ? ((d.value / totalValue) * 100).toFixed(1) : 0;
              return (
                <div key={d.name} className="flex items-center justify-between text-sm">
                  <div className="flex items-center gap-2">
                    <div className="w-3 h-3 rounded-full flex-shrink-0" style={{ backgroundColor: COLORS[i % COLORS.length] }} />
                    <span className="font-semibold text-gray-700">{d.name}</span>
                    <span className="text-xs text-gray-400">{d.type}</span>
                  </div>
                  <span className="text-gray-600 font-semibold">%{pct}</span>
                </div>
              );
            })}
          </div>
        </div>

        {/* Tür bazlı pie chart */}
        <div className="bg-white rounded-2xl border border-gray-200 p-6">
          <h3 className="font-bold text-gray-900 mb-4">Enstrüman Türü Dağılımı</h3>
          <ResponsiveContainer width="100%" height={260}>
            <PieChart>
              <Pie data={byType} cx="50%" cy="50%" innerRadius={60} outerRadius={100}
                dataKey="value" nameKey="name" paddingAngle={2}>
                {byType.map((_, i) => (
                  <Cell key={i} fill={COLORS[i % COLORS.length]} />
                ))}
              </Pie>
              <Tooltip content={<CustomTooltip />} />
            </PieChart>
          </ResponsiveContainer>
          <div className="mt-3 space-y-1.5">
            {byType.map((d, i) => {
              const pct = totalValue > 0 ? ((d.value / totalValue) * 100).toFixed(1) : 0;
              return (
                <div key={d.name} className="flex items-center justify-between text-sm">
                  <div className="flex items-center gap-2">
                    <div className="w-3 h-3 rounded-full flex-shrink-0" style={{ backgroundColor: COLORS[i % COLORS.length] }} />
                    <span className="font-semibold text-gray-700">{d.name}</span>
                  </div>
                  <span className="text-gray-600 font-semibold">%{pct}</span>
                </div>
              );
            })}
          </div>
        </div>
      </div>

      {/* Varlık performans tablosu */}
      <div className="bg-white rounded-2xl border border-gray-200 overflow-hidden">
        <div className="px-6 py-4 border-b border-gray-100">
          <h3 className="font-bold text-gray-900">Varlık Performansı</h3>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead className="bg-gray-50">
              <tr>
                {['Sembol', 'Tür', 'Maliyet', 'Piyasa Değeri', 'K/Z', 'K/Z %', 'Ağırlık'].map(h => (
                  <th key={h} className="text-left px-4 py-3 text-xs font-bold text-gray-500 uppercase tracking-wider border-b border-gray-200">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {[...holdings].sort((a, b) => parseFloat(b.profitLoss ?? 0) - parseFloat(a.profitLoss ?? 0)).map((h, i) => {
                const hpnl = parseFloat(h.profitLoss ?? 0);
                const hcost = parseFloat(h.totalCost ?? 0);
                const hmv = parseFloat(h.marketValue ?? 0);
                const hpct = hcost > 0 ? ((hpnl / hcost) * 100).toFixed(2) : null;
                const weight = totalValue > 0 ? ((hmv || hcost) / totalValue * 100).toFixed(1) : 0;
                return (
                  <tr key={i} className="border-t border-gray-100 hover:bg-gray-50">
                    <td className="px-4 py-3 font-bold text-[#093eaa] text-sm">{h.symbol}</td>
                    <td className="px-4 py-3"><span className="text-xs bg-gray-100 text-gray-600 px-2 py-0.5 rounded-full font-semibold">{h.assetType}</span></td>
                    <td className="px-4 py-3 text-sm">{fmt(hcost)}</td>
                    <td className="px-4 py-3 text-sm font-semibold">{hmv > 0 ? fmt(hmv) : '-'}</td>
                    <td className="px-4 py-3 text-sm"><PnlCell value={hpnl} /></td>
                    <td className="px-4 py-3 text-sm">
                      {hpct ? <span className={parseFloat(hpct) >= 0 ? 'text-emerald-600 font-semibold' : 'text-rose-600 font-semibold'}>{parseFloat(hpct) >= 0 ? '+' : ''}{hpct}%</span> : '-'}
                    </td>
                    <td className="px-4 py-3 text-sm">
                      <div className="flex items-center gap-2">
                        <div className="flex-1 bg-gray-100 rounded-full h-1.5 max-w-[80px]">
                          <div className="bg-[#093eaa] h-1.5 rounded-full" style={{ width: `${weight}%` }} />
                        </div>
                        <span className="text-xs text-gray-500">%{weight}</span>
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}