import { useEffect, useState, useMemo } from 'react';
import { TrendingUp, TrendingDown } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { getCryptos } from '../../api/marketApi';
import { useAuth } from '../../context/AuthContext';
import { useSortable } from '../../hooks/useSortable';
import SortableTh from '../../components/common/SortableTh';

const PAGE_SIZE = 50;

function pct(v) {
  if (v == null) return <span className="text-gray-300">-</span>;
  const n = parseFloat(v);
  const pos = n >= 0;
  return (
    <span className={`flex items-center justify-end gap-0.5 font-semibold ${pos ? 'text-emerald-600' : 'text-rose-600'}`}>
      {pos ? <TrendingUp className="w-3 h-3" /> : <TrendingDown className="w-3 h-3" />}
      {pos ? '+' : ''}{n.toFixed(1)}%
    </span>
  );
}
function num(v, dec = null) {
  if (v == null) return '-';
  const n = parseFloat(v);
  if (dec !== null) return n.toLocaleString('tr-TR', { minimumFractionDigits: dec, maximumFractionDigits: dec });
  // Otomatik hassasiyet
  let d;
  if (n >= 1000)        d = 2;
  else if (n >= 1)      d = 4;
  else if (n >= 0.01)   d = 6;
  else if (n >= 0.0001) d = 8;
  else                  d = 10;
  return n.toLocaleString('tr-TR', { minimumFractionDigits: d, maximumFractionDigits: d });
}

export default function CryptoPage() {
  const [items, setItems]         = useState([]);
  const [loading, setLoading]     = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError]         = useState('');
  const [search, setSearch]       = useState('');
  const [page, setPage]           = useState(0);
  const { isAuthenticated } = useAuth();
  const navigate = useNavigate();

  useEffect(() => {
    let cancelled = false;

    async function fetchAll() {
      setLoading(true);
      setItems([]);
      setError('');

      for (let p = 0; p < 4; p++) {
        try {
          const batch = await getCryptos(p, 250, 'try');
          if (cancelled) return;
          if (!batch || batch.length === 0) break;

          setItems(prev => [...prev, ...batch]);
          if (p === 0) setLoading(false);
          else setLoadingMore(true);

          if (p < 3) await new Promise(r => setTimeout(r, 1300));
        } catch (e) {
          if (cancelled) return;
          if (p === 0) setError(!e.response ? 'Sunucuya ulaşılamıyor.' : `Hata (${e.response?.status})`);
          break;
        }
      }

      if (!cancelled) { setLoading(false); setLoadingMore(false); }
    }

    fetchAll();
    return () => { cancelled = true; };
  }, []);

  const filtered = useMemo(() => {
    if (!search.trim()) return items;
    const q = search.toLowerCase();
    return items.filter(c => c.name?.toLowerCase().includes(q) || c.symbol?.toLowerCase().includes(q));
  }, [items, search]);

  const { sorted, sortKey, sortDir, handleSort } = useSortable(filtered, 'marketCapRank', 'asc');

  const totalPages = Math.ceil(sorted.length / PAGE_SIZE);
  const paged = sorted.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE);

  const thProps = (key, label, align = 'right') => ({
    label, sortKey: key, currentKey: sortKey, currentDir: sortDir,
    onSort: (k) => { handleSort(k); setPage(0); }, align
  });

  function handleBuy() {
    if (!isAuthenticated) {
      navigate('/login', { state: { from: '/portfolio' } });
      return;
    }
    navigate('/portfolio');
  }

  return (
    <div>
      <h1 className="text-2xl font-bold text-gray-900 mb-2 border-l-4 border-[#093eaa] pl-4">Kripto Para</h1>
      <p className="text-sm text-gray-500 mb-6 pl-5">CoinGecko verilerine göre TRY bazlı kripto para fiyatları</p>

      <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden">
        {loading && <div className="p-8 text-center text-gray-400 text-sm">Yükleniyor...</div>}
        {error && <div className="p-6 text-rose-500 text-sm">{error}</div>}

        {!loading && !error && (
          <>
            <div className="p-4 border-b border-gray-100 flex items-center gap-3">
              <input type="text" placeholder="Coin adı veya sembol ara..."
                value={search} onChange={e => { setSearch(e.target.value); setPage(0); }}
                className="px-4 py-2 bg-gray-50 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-[#093eaa] min-w-[240px]" />
              <span className="text-xs text-gray-400">{sorted.length} coin</span>
              {loadingMore && <span className="text-xs text-[#093eaa] animate-pulse">daha fazla yükleniyor...</span>}
            </div>
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead className="bg-gray-50">
                  <tr>
                    <SortableTh {...thProps('marketCapRank', '#', 'left')} className="w-10" />
                    <SortableTh {...thProps('name', 'Coin', 'left')} />
                    <SortableTh {...thProps('currentPrice', 'Fiyat')} />
                    <SortableTh {...thProps('priceChangePercentage1h', '1sa')} />
                    <SortableTh {...thProps('priceChangePercentage24h', '24sa')} />
                    <SortableTh {...thProps('priceChangePercentage7d', '7g')} />
                    <SortableTh {...thProps('totalVolume', '24s Hacim')} />
                    <SortableTh {...thProps('marketCap', 'Piyasa Değeri')} />
                    <th className="px-4 py-3 border-b border-gray-200" />
                  </tr>
                </thead>
                <tbody>
                  {paged.map(c => (
                    <tr key={c.id}
                      className="border-t border-gray-100 hover:bg-blue-50 transition-colors cursor-pointer"
                      onClick={() => navigate(`/market/crypto/${c.id}`)}>
                      <td className="px-4 py-3 text-sm text-gray-400">{c.marketCapRank ?? '-'}</td>
                      <td className="px-4 py-3">
                        <div className="flex items-center gap-2">
                          {c.image && <img src={c.image} alt="" className="w-7 h-7 rounded-full" />}
                          <span className="font-bold text-gray-900 text-sm">{c.name}</span>
                          <span className="text-gray-400 text-xs uppercase">{c.symbol}</span>
                        </div>
                      </td>
                      <td className="px-4 py-3 text-sm font-semibold text-gray-900 text-right">
                        {c.currentPrice != null ? `₺${num(c.currentPrice)}` : '-'}
                      </td>
                      <td className="px-4 py-3 text-sm text-right">{pct(c.priceChangePercentage1h)}</td>
                      <td className="px-4 py-3 text-sm text-right">{pct(c.priceChangePercentage24h)}</td>
                      <td className="px-4 py-3 text-sm text-right">{pct(c.priceChangePercentage7d)}</td>
                      <td className="px-4 py-3 text-sm text-gray-600 text-right">{c.totalVolume != null ? `₺${num(c.totalVolume, 0)}` : '-'}</td>
                      <td className="px-4 py-3 text-sm text-gray-600 text-right">{c.marketCap != null ? `₺${num(c.marketCap, 0)}` : '-'}</td>                      <td className="px-4 py-3 text-right">
                        <button onClick={handleBuy}
                          className="border border-emerald-500 text-emerald-600 text-xs font-bold px-3 py-1 rounded-full hover:bg-emerald-50 transition-colors whitespace-nowrap">
                          Satın Al
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            {totalPages > 1 && (
              <div className="p-4 flex gap-2 flex-wrap border-t border-gray-100 items-center">
                <button disabled={page === 0} onClick={() => setPage(p => p - 1)}
                  className="px-3 py-1.5 rounded-lg border border-gray-200 text-sm hover:bg-gray-50 disabled:opacity-40">‹</button>

                {(() => {
                  const delta = 2;
                  const left  = Math.max(0, page - delta);
                  const right = Math.min(totalPages - 1, page + delta);
                  const pages = [];

                  if (left > 0) {
                    pages.push(0);
                    if (left > 1) pages.push('...');
                  }
                  for (let i = left; i <= right; i++) pages.push(i);
                  if (right < totalPages - 1) {
                    if (right < totalPages - 2) pages.push('...');
                    pages.push(totalPages - 1);
                  }

                  return pages.map((p, idx) =>
                    p === '...'
                      ? <span key={`ellipsis-${idx}`} className="px-1 text-gray-400 text-sm">…</span>
                      : <button key={p} onClick={() => setPage(p)}
                          className={`px-3 py-1.5 rounded-lg border text-sm font-medium transition-all ${p === page ? 'bg-[#093eaa] text-white border-[#093eaa]' : 'border-gray-200 hover:bg-gray-50'}`}>
                          {p + 1}
                        </button>
                  );
                })()}

                <button disabled={page >= totalPages - 1} onClick={() => setPage(p => p + 1)}
                  className="px-3 py-1.5 rounded-lg border border-gray-200 text-sm hover:bg-gray-50 disabled:opacity-40">›</button>
                <span className="text-xs text-gray-400 ml-1">{totalPages} sayfa</span>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
}
