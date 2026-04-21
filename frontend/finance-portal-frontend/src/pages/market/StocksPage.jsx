import { useEffect, useState, useMemo, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { getStocks } from '../../api/marketApi';
import { useSortable } from '../../hooks/useSortable';
import SortableTh from '../../components/common/SortableTh';

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
  const [pageData, setPageData] = useState(null); // { content, page, totalPages, totalElements }
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');
  const [searchInput, setSearchInput] = useState('');

  const fetchPage = useCallback((p) => {
    setLoading(true);
    setError('');
    getStocks(p, PAGE_SIZE)
      .then(data => setPageData(data))
      .catch(e => setError(!e.response ? 'Sunucuya ulaşılamıyor.' : `Hata (${e.response.status})`))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => { fetchPage(page); }, [page, fetchPage]);

  // Client-side search within current page
  const items = pageData?.content ?? [];
  const filtered = useMemo(() => {
    if (!search.trim()) return items;
    const q = search.toLowerCase();
    return items.filter(s => s.symbol?.toLowerCase().includes(q) || s.name?.toLowerCase().includes(q));
  }, [items, search]);

  const { sorted, sortKey, sortDir, handleSort } = useSortable(filtered, 'symbol', 'asc');

  const totalPages = pageData?.totalPages ?? 0;
  const totalElements = pageData?.totalElements ?? 0;

  const thProps = (key, label, align = 'left') => ({
    label, sortKey: key, currentKey: sortKey, currentDir: sortDir,
    onSort: handleSort, align
  });

  function handlePageChange(p) {
    setPage(p);
    setSearch('');
    setSearchInput('');
    window.scrollTo(0, 0);
  }

  function handleSearchSubmit(e) {
    e.preventDefault();
    setSearch(searchInput);
  }

  return (
    <div>
      <h1 className="text-2xl font-bold text-gray-900 mb-2 border-l-4 border-[#093eaa] pl-4">Hisse Senetleri</h1>
      <p className="text-sm text-gray-500 mb-6 pl-5">
        Borsa İstanbul (BIST) hisse senedi fiyatları
        {totalElements > 0 && <span className="ml-2 text-gray-400">· {totalElements} hisse</span>}
      </p>

      <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden">
        <div className="p-4 border-b border-gray-100 flex items-center gap-3 flex-wrap">
          <form onSubmit={handleSearchSubmit} className="flex gap-2">
            <input type="text" placeholder="Bu sayfada ara..."
              value={searchInput} onChange={e => setSearchInput(e.target.value)}
              className="px-4 py-2 bg-gray-50 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-[#093eaa] min-w-[220px]" />
            <button type="submit" className="px-3 py-2 bg-[#093eaa] text-white rounded-xl text-sm font-semibold hover:bg-[#093eaa]/90">Ara</button>
            {search && <button type="button" onClick={() => { setSearch(''); setSearchInput(''); }} className="px-3 py-2 border border-gray-200 rounded-xl text-sm hover:bg-gray-50">✕</button>}
          </form>
          <span className="text-xs text-gray-400">
            Sayfa {page + 1} / {totalPages} · {sorted.length} sonuç
          </span>
        </div>

        {loading && (
          <div className="p-8 text-center">
            <div className="flex items-center justify-center gap-2 mb-2">
              <div className="w-2 h-2 bg-[#093eaa] rounded-full animate-bounce" />
              <div className="w-2 h-2 bg-[#093eaa]/60 rounded-full animate-bounce [animation-delay:100ms]" />
              <div className="w-2 h-2 bg-[#093eaa]/30 rounded-full animate-bounce [animation-delay:200ms]" />
            </div>
            <p className="text-gray-400 text-sm">Hisseler yükleniyor...</p>
          </div>
        )}
        {error && <div className="p-6 text-rose-500 text-sm">{error}</div>}

        {!loading && !error && (
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead className="bg-gray-50">
                <tr>
                  <SortableTh {...thProps('symbol', 'Sembol')} />
                  <SortableTh {...thProps('name', 'Şirket')} />
                  <SortableTh {...thProps('price', 'Fiyat', 'right')} />
                  <SortableTh {...thProps('change', 'Değişim', 'right')} />
                  <SortableTh {...thProps('changePercent', '%', 'right')} />
                  <SortableTh {...thProps('dayHigh', 'Yüksek', 'right')} />
                  <SortableTh {...thProps('dayLow', 'Düşük', 'right')} />
                  <SortableTh {...thProps('volume', 'Hacim', 'right')} />
                  <SortableTh {...thProps('exchange', 'Borsa')} />
                </tr>
              </thead>
              <tbody>
                {sorted.length === 0
                  ? <tr><td colSpan={9} className="px-4 py-8 text-center text-gray-400 text-sm">Sonuç bulunamadı.</td></tr>
                  : sorted.map(r => (
                    <tr key={r.symbol} className="border-t border-gray-100 hover:bg-blue-50 transition-colors cursor-pointer">
                      <td className="px-4 py-3 font-bold text-sm">
                        <Link to={`/market/stocks/${r.symbol}`} className="text-[#093eaa] hover:underline">{r.symbol}</Link>
                      </td>
                      <td className="px-4 py-3 text-sm text-gray-700">{r.name ?? '-'}</td>
                      <td className="px-4 py-3 text-sm font-semibold text-right">{num(r.price)} <span className="text-gray-400 text-xs">{r.currency}</span></td>
                      <td className="px-4 py-3 text-sm text-right">{r.change == null ? '-' : <span className={parseFloat(r.change) >= 0 ? 'text-emerald-600 font-semibold' : 'text-rose-600 font-semibold'}>{num(r.change)}</span>}</td>
                      <td className="px-4 py-3 text-sm text-right">{pct(r.changePercent)}</td>
                      <td className="px-4 py-3 text-sm text-gray-600 text-right">{num(r.dayHigh)}</td>
                      <td className="px-4 py-3 text-sm text-gray-600 text-right">{num(r.dayLow)}</td>
                      <td className="px-4 py-3 text-sm text-gray-600 text-right">{r.volume == null ? '-' : Number(r.volume).toLocaleString('tr-TR')}</td>
                      <td className="px-4 py-3 text-xs text-gray-400">{r.exchange ?? '-'}</td>
                    </tr>
                  ))
                }
              </tbody>
            </table>
          </div>
        )}

        {/* Pagination */}
        {totalPages > 1 && !loading && (
          <div className="p-4 flex items-center justify-between border-t border-gray-100 flex-wrap gap-3">
            <span className="text-xs text-gray-500">
              Toplam {totalElements} hisse · Sayfa {page + 1} / {totalPages}
            </span>
            <div className="flex gap-1">
              <button disabled={page === 0} onClick={() => handlePageChange(0)}
                className="px-2 py-1.5 rounded border border-gray-200 text-xs hover:bg-gray-50 disabled:opacity-40">«</button>
              <button disabled={page === 0} onClick={() => handlePageChange(page - 1)}
                className="px-3 py-1.5 rounded border border-gray-200 text-xs hover:bg-gray-50 disabled:opacity-40">‹ Önceki</button>
              {Array.from({ length: Math.min(totalPages, 7) }, (_, i) => {
                const p = page < 4 ? i : page - 3 + i;
                if (p >= totalPages) return null;
                return (
                  <button key={p} onClick={() => handlePageChange(p)}
                    className={`px-3 py-1.5 rounded border text-xs ${p === page ? 'bg-[#093eaa] text-white border-[#093eaa]' : 'border-gray-200 hover:bg-gray-50'}`}>
                    {p + 1}
                  </button>
                );
              })}
              {totalPages > 7 && <span className="px-2 py-1.5 text-xs text-gray-400">... {totalPages}</span>}
              <button disabled={page >= totalPages - 1} onClick={() => handlePageChange(page + 1)}
                className="px-3 py-1.5 rounded border border-gray-200 text-xs hover:bg-gray-50 disabled:opacity-40">Sonraki ›</button>
              <button disabled={page >= totalPages - 1} onClick={() => handlePageChange(totalPages - 1)}
                className="px-2 py-1.5 rounded border border-gray-200 text-xs hover:bg-gray-50 disabled:opacity-40">»</button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
