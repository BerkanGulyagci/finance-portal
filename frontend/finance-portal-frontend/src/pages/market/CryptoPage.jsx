import { useEffect, useState, useMemo } from 'react';
import { TrendingUp, TrendingDown } from 'lucide-react';
import { getAllCryptos } from '../../api/marketApi';
import { useAuth } from '../../context/AuthContext';
import { useNavigate } from 'react-router-dom';

const PAGE_SIZE = 50;

function pct(v) {
  if (v == null) return <span className="text-gray-300">-</span>;
  const n = parseFloat(v);
  const pos = n >= 0;
  return (
    <span className={`flex items-center gap-0.5 font-semibold ${pos ? 'text-emerald-600' : 'text-rose-600'}`}>
      {pos ? <TrendingUp className="w-3 h-3" /> : <TrendingDown className="w-3 h-3" />}
      {pos ? '+' : ''}{n.toFixed(1)}%
    </span>
  );
}

function num(v, dec = 2) {
  return v == null ? '-' : parseFloat(v).toLocaleString('tr-TR', { minimumFractionDigits: dec, maximumFractionDigits: dec });
}

function BuyButton({ coin, onBuy }) {
  return (
    <button
      onClick={() => onBuy(coin)}
      className="flex items-center gap-1 border border-emerald-500 text-emerald-600 text-xs font-bold px-3 py-1 rounded-full hover:bg-emerald-50 transition-colors whitespace-nowrap"
    >
      Satın Al
    </button>
  );
}

export default function CryptoPage() {
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const { isAuthenticated } = useAuth();
  const navigate = useNavigate();

  useEffect(() => {
    getAllCryptos().then(setItems)
      .catch(e => setError(!e.response ? 'Sunucuya ulaşılamıyor.' : `Hata (${e.response.status})`))
      .finally(() => setLoading(false));
  }, []);

  const filtered = useMemo(() => {
    if (!search.trim()) return items;
    const q = search.toLowerCase();
    return items.filter(c => c.name?.toLowerCase().includes(q) || c.symbol?.toLowerCase().includes(q));
  }, [items, search]);

  const totalPages = Math.ceil(filtered.length / PAGE_SIZE);
  const paged = filtered.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE);

  function handleBuy(coin) {
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
              <span className="text-xs text-gray-400">{filtered.length} coin</span>
            </div>
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead className="bg-gray-50">
                <tr>
                  <th className="text-left px-4 py-3 text-xs font-bold text-gray-500 uppercase tracking-wider w-10">#</th>
                  <th className="text-left px-4 py-3 text-xs font-bold text-gray-500 uppercase tracking-wider">Coin</th>
                  <th className="text-right px-4 py-3 text-xs font-bold text-gray-500 uppercase tracking-wider">Fiyat</th>
                  <th className="text-right px-4 py-3 text-xs font-bold text-gray-500 uppercase tracking-wider">1sa</th>
                  <th className="text-right px-4 py-3 text-xs font-bold text-gray-500 uppercase tracking-wider">24sa</th>
                  <th className="text-right px-4 py-3 text-xs font-bold text-gray-500 uppercase tracking-wider">7g</th>
                  <th className="text-right px-4 py-3 text-xs font-bold text-gray-500 uppercase tracking-wider">24 Saatlik Hacim</th>
                  <th className="text-right px-4 py-3 text-xs font-bold text-gray-500 uppercase tracking-wider">Piyasa Değeri</th>
                  <th className="px-4 py-3"></th>
                </tr>
              </thead>
              <tbody>
                {paged.map(c => (
                  <tr key={c.id} className="border-t border-gray-100 hover:bg-gray-50 transition-colors">
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
                    <td className="px-4 py-3 text-sm text-gray-600 text-right">{c.marketCap != null ? `₺${num(c.marketCap, 0)}` : '-'}</td>
                    <td className="px-4 py-3 text-right">
                      <BuyButton coin={c} onBuy={handleBuy} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {totalPages > 1 && (
            <div className="p-4 flex gap-2 flex-wrap border-t border-gray-100">
              <button disabled={page === 0} onClick={() => setPage(p => p - 1)} className="px-3 py-1.5 rounded-lg border border-gray-200 text-sm hover:bg-gray-50 disabled:opacity-40">‹</button>
              {Array.from({ length: Math.min(totalPages, 10) }, (_, i) => (
                <button key={i} onClick={() => setPage(i)} className={`px-3 py-1.5 rounded-lg border text-sm ${i === page ? 'bg-[#093eaa] text-white border-[#093eaa]' : 'border-gray-200 hover:bg-gray-50'}`}>{i + 1}</button>
              ))}
              {totalPages > 10 && <span className="text-xs text-gray-400 self-center">... {totalPages} sayfa</span>}
              <button disabled={page >= totalPages - 1} onClick={() => setPage(p => p + 1)} className="px-3 py-1.5 rounded-lg border border-gray-200 text-sm hover:bg-gray-50 disabled:opacity-40">›</button>
            </div>
          )}
          </>
        )}
      </div>
    </div>
  );
}
