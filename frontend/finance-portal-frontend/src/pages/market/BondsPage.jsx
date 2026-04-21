import { useEffect, useState, useMemo } from 'react';
import { getBonds } from '../../api/marketApi';
import { useSortable } from '../../hooks/useSortable';
import SortableTh from '../../components/common/SortableTh';

const PAGE_SIZE = 20;

function num(v) {
  if (!v || v === '0,000000') return <span className="text-gray-300">-</span>;
  return v;
}

function rate(v) {
  if (!v || v === '0,00') return <span className="text-gray-300">-</span>;
  const n = parseFloat(v.replace(',', '.'));
  if (isNaN(n)) return v;
  return <span className="font-semibold text-gray-900">%{v}</span>;
}

function Pagination({ page, totalPages, onChange }) {
  if (totalPages <= 1) return null;
  const pages = [];
  const start = Math.max(0, page - 2);
  const end = Math.min(totalPages - 1, page + 2);
  for (let i = start; i <= end; i++) pages.push(i);
  return (
    <div className="flex items-center justify-center gap-1 py-4 border-t border-gray-100">
      <button onClick={() => onChange(0)} disabled={page === 0} className="px-2 py-1 text-xs rounded border border-gray-200 disabled:opacity-40 hover:bg-gray-50">«</button>
      <button onClick={() => onChange(page - 1)} disabled={page === 0} className="px-2 py-1 text-xs rounded border border-gray-200 disabled:opacity-40 hover:bg-gray-50">‹</button>
      {pages.map(p => (
        <button key={p} onClick={() => onChange(p)}
          className={`px-3 py-1 text-xs rounded border ${p === page ? 'bg-[#093eaa] text-white border-[#093eaa]' : 'border-gray-200 hover:bg-gray-50'}`}>
          {p + 1}
        </button>
      ))}
      <button onClick={() => onChange(page + 1)} disabled={page === totalPages - 1} className="px-2 py-1 text-xs rounded border border-gray-200 disabled:opacity-40 hover:bg-gray-50">›</button>
      <button onClick={() => onChange(totalPages - 1)} disabled={page === totalPages - 1} className="px-2 py-1 text-xs rounded border border-gray-200 disabled:opacity-40 hover:bg-gray-50">»</button>
    </div>
  );
}

export default function BondsPage() {
  const [all, setAll] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const [showZero, setShowZero] = useState(false);

  useEffect(() => {
    getBonds()
      .then(setAll)
      .catch(e => setError(!e.response ? 'Sunucuya ulaşılamıyor.' : `Hata (${e.response.status})`))
      .finally(() => setLoading(false));
  }, []);

  const filtered = useMemo(() => {
    let data = all;
    if (!showZero) {
      data = data.filter(b => b.buyPrice && b.buyPrice !== '0,000000');
    }
    if (search.trim()) {
      const q = search.toLowerCase();
      data = data.filter(b => b.name?.toLowerCase().includes(q) || b.maturityDate?.includes(q));
    }
    return data;
  }, [all, search, showZero]);

  const { sorted, sortKey, sortDir, handleSort } = useSortable(filtered, 'daysToMaturity', 'asc');
  const totalPages = Math.ceil(sorted.length / PAGE_SIZE);
  const paged = sorted.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE);

  const thProps = (key, label, align = 'left') => ({
    label, sortKey: key, currentKey: sortKey, currentDir: sortDir,
    onSort: (k) => { handleSort(k); setPage(0); }, align
  });

  // Stats
  const activeItems = all.filter(b => b.buyPrice && b.buyPrice !== '0,000000');
  const avgBuyRate = activeItems.length > 0
    ? (activeItems.reduce((s, b) => s + parseFloat((b.buyRate || '0').replace(',', '.')), 0) / activeItems.length).toFixed(2)
    : null;

  return (
    <div>
      <h1 className="text-2xl font-bold text-gray-900 mb-2 border-l-4 border-[#093eaa] pl-4">Tahvil / Bono</h1>
      <p className="text-sm text-gray-500 mb-6 pl-5">Devlet İç Borçlanma Senetleri (DİBS) · Kaynak: Ziraat Bankası</p>

      {/* Stats cards */}
      {!loading && !error && activeItems.length > 0 && (
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mb-6">
          <div className="bg-white rounded-xl border border-gray-200 p-4">
            <p className="text-xs text-gray-400 mb-1">Toplam Enstrüman</p>
            <p className="text-xl font-bold text-gray-900">{all.length}</p>
          </div>
          <div className="bg-white rounded-xl border border-gray-200 p-4">
            <p className="text-xs text-gray-400 mb-1">Fiyatlı Enstrüman</p>
            <p className="text-xl font-bold text-[#093eaa]">{activeItems.length}</p>
          </div>
          <div className="bg-white rounded-xl border border-gray-200 p-4">
            <p className="text-xs text-gray-400 mb-1">Ort. Alış Oranı</p>
            <p className="text-xl font-bold text-emerald-600">%{avgBuyRate}</p>
          </div>
          <div className="bg-white rounded-xl border border-gray-200 p-4">
            <p className="text-xs text-gray-400 mb-1">Para Birimi</p>
            <p className="text-xl font-bold text-gray-900">TL</p>
          </div>
        </div>
      )}

      <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden">
        {/* Toolbar */}
        <div className="p-4 border-b border-gray-100 flex flex-wrap items-center gap-3">
          <div className="relative flex-1 min-w-[200px] max-w-sm">
            <svg className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
            </svg>
            <input type="text" placeholder="Kıymet adı veya vade ara..."
              value={search} onChange={e => { setSearch(e.target.value); setPage(0); }}
              className="w-full pl-9 pr-3 py-2 text-sm border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-[#093eaa]/30 focus:border-[#093eaa]" />
          </div>
          <label className="flex items-center gap-2 text-sm text-gray-600 cursor-pointer select-none">
            <input type="checkbox" checked={showZero} onChange={e => { setShowZero(e.target.checked); setPage(0); }}
              className="w-4 h-4 accent-[#093eaa]" />
            Fiyatsızları göster
          </label>
          <span className="text-xs text-gray-400 ml-auto">{sorted.length} enstrüman</span>
        </div>

        {loading && (
          <div className="p-8 text-center">
            <div className="flex items-center justify-center gap-2 mb-2">
              <div className="w-2 h-2 bg-[#093eaa] rounded-full animate-bounce" />
              <div className="w-2 h-2 bg-[#093eaa]/60 rounded-full animate-bounce [animation-delay:100ms]" />
              <div className="w-2 h-2 bg-[#093eaa]/30 rounded-full animate-bounce [animation-delay:200ms]" />
            </div>
            <p className="text-gray-400 text-sm">Tahvil verileri yükleniyor...</p>
          </div>
        )}
        {error && <div className="p-6 text-rose-500 text-sm">{error}</div>}

        {!loading && !error && (
          <>
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead className="bg-gray-50">
                  <tr>
                    <SortableTh {...thProps('name', 'Kıymet Adı')} />
                    <SortableTh {...thProps('maturityDate', 'Vade')} />
                    <SortableTh {...thProps('daysToMaturity', 'Kalan Gün', 'right')} />
                    <SortableTh {...thProps('currency', 'Döviz')} />
                    <SortableTh {...thProps('buyPrice', 'Alış Fiyatı', 'right')} />
                    <SortableTh {...thProps('buyRate', 'Alış Oranı (%)', 'right')} />
                    <SortableTh {...thProps('sellPrice', 'Satış Fiyatı', 'right')} />
                    <SortableTh {...thProps('sellRate', 'Satış Oranı (%)', 'right')} />
                  </tr>
                </thead>
                <tbody>
                  {paged.length === 0
                    ? <tr><td colSpan={8} className="px-4 py-8 text-center text-gray-400 text-sm">Sonuç bulunamadı.</td></tr>
                    : paged.map((b, i) => {
                      const hasPrice = b.buyPrice && b.buyPrice !== '0,000000';
                      return (
                        <tr key={i} className={`border-t border-gray-100 transition-colors ${hasPrice ? 'hover:bg-blue-50' : 'opacity-50 hover:bg-gray-50'}`}>
                          <td className="px-4 py-3 font-bold text-[#093eaa] text-sm font-mono">{b.name}</td>
                          <td className="px-4 py-3 text-sm text-gray-700 whitespace-nowrap">{b.maturityDate}</td>
                          <td className="px-4 py-3 text-sm text-right">
                            <span className={`px-2 py-0.5 rounded-full text-xs font-semibold ${
                              (b.daysToMaturity ?? 9999) <= 180 ? 'bg-amber-100 text-amber-700' :
                              (b.daysToMaturity ?? 9999) <= 365 ? 'bg-blue-100 text-blue-700' :
                              'bg-gray-100 text-gray-600'
                            }`}>
                              {b.daysToMaturity ?? '-'}
                            </span>
                          </td>
                          <td className="px-4 py-3 text-sm text-gray-500">{b.currency}</td>
                          <td className="px-4 py-3 text-sm text-right font-mono">{num(b.buyPrice)}</td>
                          <td className="px-4 py-3 text-sm text-right text-emerald-600">{rate(b.buyRate)}</td>
                          <td className="px-4 py-3 text-sm text-right font-mono">{num(b.sellPrice)}</td>
                          <td className="px-4 py-3 text-sm text-right text-rose-600">{rate(b.sellRate)}</td>
                        </tr>
                      );
                    })
                  }
                </tbody>
              </table>
            </div>
            <Pagination page={page} totalPages={totalPages} onChange={p => setPage(p)} />
            <div className="px-4 py-2 bg-gray-50 border-t border-gray-100 text-xs text-gray-400">
              * Gün içerisinde fiyatlar değişebilmektedir. Kaynak: Ziraat Bankası · ziraatbank.com.tr
            </div>
          </>
        )}
      </div>
    </div>
  );
}
