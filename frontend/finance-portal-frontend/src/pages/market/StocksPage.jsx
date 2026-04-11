import { useEffect, useState, useMemo } from 'react';
import { getAllStocks } from '../../api/marketApi';

const PAGE_SIZE = 20;

function pct(v) {
  if (v == null) return <span className="text-gray-400">-</span>;
  const n = parseFloat(v);
  return <span className={n >= 0 ? 'text-emerald-600 font-semibold' : 'text-rose-600 font-semibold'}>{n >= 0 ? '+' : ''}{n.toFixed(2)}%</span>;
}
function num(v, dec = 2) {
  return v == null ? '-' : parseFloat(v).toLocaleString('tr-TR', { minimumFractionDigits: dec, maximumFractionDigits: dec });
}

export default function StocksPage() {
  const [all, setAll] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);

  useEffect(() => {
    getAllStocks().then(setAll).catch(e => setError(!e.response ? 'Sunucuya ulaşılamıyor.' : `Hata (${e.response.status})`)).finally(() => setLoading(false));
  }, []);

  const filtered = useMemo(() => {
    if (!search.trim()) return all;
    const q = search.toLowerCase();
    return all.filter(s => s.symbol?.toLowerCase().includes(q) || s.name?.toLowerCase().includes(q));
  }, [all, search]);

  const totalPages = Math.ceil(filtered.length / PAGE_SIZE);
  const paged = filtered.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE);

  return (
    <div>
      <h1 className="text-2xl font-bold text-gray-900 mb-2 border-l-4 border-[#093eaa] pl-4">Hisse Senetleri</h1>
      <p className="text-sm text-gray-500 mb-6 pl-5">Borsa İstanbul (BIST) hisse senedi fiyatları</p>

      <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden">
        <div className="p-4 border-b border-gray-100 flex items-center gap-3">
          <input type="text" placeholder="Sembol veya şirket adı ara..."
            value={search} onChange={e => { setSearch(e.target.value); setPage(0); }}
            className="px-4 py-2 bg-gray-50 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-[#093eaa] min-w-[260px]" />
          <span className="text-xs text-gray-400">{filtered.length} hisse</span>
        </div>

        {loading && <div className="p-8 text-center text-gray-400 text-sm">Yükleniyor...</div>}
        {error && <div className="p-6 text-rose-500 text-sm">{error}</div>}

        {!loading && !error && (
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead className="bg-gray-50">
                <tr>{['Sembol', 'Şirket', 'Fiyat', 'Değişim', '%', 'Yüksek', 'Düşük', 'Hacim', 'Borsa'].map(h =>
                  <th key={h} className="text-left px-4 py-3 text-xs font-bold text-gray-500 uppercase tracking-wider whitespace-nowrap border-b border-gray-200">{h}</th>
                )}</tr>
              </thead>
              <tbody>
                {paged.map(r => (
                  <tr key={r.symbol} className="border-t border-gray-100 hover:bg-gray-50 transition-colors">
                    <td className="px-4 py-3 font-bold text-[#093eaa] text-sm">{r.symbol}</td>
                    <td className="px-4 py-3 text-sm text-gray-700">{r.name ?? '-'}</td>
                    <td className="px-4 py-3 text-sm font-semibold">{num(r.price)} <span className="text-gray-400 text-xs">{r.currency}</span></td>
                    <td className="px-4 py-3 text-sm">{r.change == null ? '-' : <span className={parseFloat(r.change) >= 0 ? 'text-emerald-600 font-semibold' : 'text-rose-600 font-semibold'}>{num(r.change)}</span>}</td>
                    <td className="px-4 py-3 text-sm">{pct(r.changePercent)}</td>
                    <td className="px-4 py-3 text-sm text-gray-600">{num(r.dayHigh)}</td>
                    <td className="px-4 py-3 text-sm text-gray-600">{num(r.dayLow)}</td>
                    <td className="px-4 py-3 text-sm text-gray-600">{r.volume == null ? '-' : Number(r.volume).toLocaleString('tr-TR')}</td>
                    <td className="px-4 py-3 text-xs text-gray-400">{r.exchange ?? '-'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

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
      </div>
    </div>
  );
}
