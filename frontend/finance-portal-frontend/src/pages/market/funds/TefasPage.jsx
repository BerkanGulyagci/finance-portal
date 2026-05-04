import { useEffect, useState, useMemo } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { BarChart2 } from 'lucide-react';
import { getTefasFunds } from '../../../api/marketApi';
import { useSortable } from '../../../hooks/useSortable';
import SortableTh from '../../../components/common/SortableTh';

const PAGE_SIZE = 15;

function num(v, dec = 2) {
  return v == null ? '-' : parseFloat(v).toLocaleString('tr-TR', { minimumFractionDigits: dec, maximumFractionDigits: dec });
}

function formatDate(ts) {
  if (!ts) return '-';
  try {
    const d = new Date(parseInt(ts));
    return d.toLocaleDateString('tr-TR');
  } catch { return ts; }
}

const KINDS = [
  { key: 'YAT',  label: 'Menkul Kıymet Yatırım Fonları' },
  { key: 'BYF',  label: 'Borsa Yatırım Fonları' },
  { key: 'EMK',  label: 'Emeklilik Fonları' },
  { key: 'GYF',  label: 'Gayrimenkul Yatırım Fonları' },
  { key: 'GSYF', label: 'Girişim Sermayesi Yatırım Fonları' },
];

export default function TefasPage() {
  const navigate = useNavigate();
  const [kind, setKind] = useState('YAT');
  const [all, setAll] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);

  useEffect(() => {
    setLoading(true);
    setError('');
    getTefasFunds(kind, 0, 2000)
      .then(r => setAll(r.content ?? []))
      .catch(e => setError(!e.response ? 'Sunucuya ulaşılamıyor.' : `Hata (${e.response.status})`))
      .finally(() => setLoading(false));
  }, [kind]);

  const filtered = useMemo(() => {
    if (!search.trim()) return all;
    const q = search.toLowerCase();
    return all.filter(f => f.code?.toLowerCase().includes(q) || f.title?.toLowerCase().includes(q));
  }, [all, search]);

  const { sorted, sortKey, sortDir, handleSort } = useSortable(filtered, 'code', 'asc');
  const totalPages = Math.ceil(sorted.length / PAGE_SIZE);
  const paged = sorted.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE);

  const thProps = (key, label, align = 'left') => ({
    label, sortKey: key, currentKey: sortKey, currentDir: sortDir,
    onSort: (k) => { handleSort(k); setPage(0); }, align
  });

  return (
    <div>
      <h1 className="text-2xl font-bold text-gray-900 mb-2 border-l-4 border-[#093eaa] pl-4">TEFAS Fonları</h1>
      <p className="text-sm text-gray-500 mb-6 pl-5">Türkiye Elektronik Fon Alım Satım Platformu · fundturkey.com.tr</p>

      {/* Fon tipi — radio button style */}
      <div className="bg-white rounded-2xl border border-gray-200 shadow-sm mb-4 px-5 py-4">
        <span className="text-sm font-bold text-gray-600 mr-4">Fon Tipi :</span>
        <div className="inline-flex flex-wrap gap-x-5 gap-y-2">
          {KINDS.map(k => (
            <label key={k.key} className="flex items-center gap-1.5 cursor-pointer text-sm text-gray-700 hover:text-[#093eaa]">
              <input
                type="radio"
                name="fundKind"
                value={k.key}
                checked={kind === k.key}
                onChange={() => { setKind(k.key); setPage(0); setSearch(''); }}
                className="accent-[#093eaa] w-3.5 h-3.5"
              />
              {k.label}
            </label>
          ))}
        </div>
      </div>

      <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden">
        {/* Search + count */}
        <div className="p-4 border-b border-gray-100 flex items-center justify-between gap-3 flex-wrap">
          <div className="flex items-center gap-3">
            <input type="text" placeholder="Fon kodu veya adı ile filtrele..."
              value={search} onChange={e => { setSearch(e.target.value); setPage(0); }}
              className="px-4 py-2 bg-gray-50 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-[#093eaa] min-w-[280px]" />
          </div>
          <span className="text-xs text-gray-400">
            {sorted.length} kayıttan {page * PAGE_SIZE + 1} - {Math.min((page + 1) * PAGE_SIZE, sorted.length)} arası gösteriliyor
          </span>
        </div>

        {loading && (
          <div className="p-8 text-center">
            <div className="flex items-center justify-center gap-2 mb-2">
              <div className="w-2 h-2 bg-[#093eaa] rounded-full animate-bounce" />
              <div className="w-2 h-2 bg-[#093eaa]/60 rounded-full animate-bounce [animation-delay:100ms]" />
              <div className="w-2 h-2 bg-[#093eaa]/30 rounded-full animate-bounce [animation-delay:200ms]" />
            </div>
            <p className="text-gray-400 text-sm">Fonlar yükleniyor...</p>
          </div>
        )}
        {error && <div className="p-6 text-rose-500 text-sm">{error}</div>}

        {!loading && !error && (
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="bg-[#093eaa] text-white">
                  <SortableTh {...thProps('date', 'Tarih')} className="text-white hover:text-white hover:bg-[#0a3590]" />
                  <SortableTh {...thProps('code', 'Fon Kodu')} className="text-white hover:text-white hover:bg-[#0a3590]" />
                  <SortableTh {...thProps('title', 'Fon Adı')} className="text-white hover:text-white hover:bg-[#0a3590]" />
                  <SortableTh {...thProps('return1M', '1 Ay %', 'right')} className="text-white hover:text-white hover:bg-[#0a3590]" />
                  <SortableTh {...thProps('return3M', '3 Ay %', 'right')} className="text-white hover:text-white hover:bg-[#0a3590]" />
                  <SortableTh {...thProps('return1Y', '1 Yıl %', 'right')} className="text-white hover:text-white hover:bg-[#0a3590]" />
                  <SortableTh {...thProps('numberOfInvestors', 'Yatırımcı', 'right')} className="text-white hover:text-white hover:bg-[#0a3590]" />
                  <th className="px-4 py-3 border-b border-[#0a3590] w-10" />
                </tr>
              </thead>
              <tbody>
                {paged.map((r, i) => (
                  <tr key={r.code} className={`border-t border-gray-100 hover:bg-blue-50 transition-colors ${i % 2 === 0 ? 'bg-white' : 'bg-gray-50/50'}`}>
                    <td className="px-4 py-3 text-xs text-gray-400">{r.date ?? '-'}</td>
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-2">
                        {r.logoUrl && (
                          <img src={r.logoUrl} alt={r.code}
                            className="w-6 h-6 rounded object-contain flex-shrink-0"
                            onError={e => { e.target.style.display = 'none'; }} />
                        )}
                        <Link to={`/market/tefas/${r.code}`} state={{ listItem: r }} className="font-bold text-[#093eaa] text-sm hover:underline">
                          {r.code}
                        </Link>
                      </div>
                    </td>
                    <td className="px-4 py-3 text-sm text-gray-800 max-w-xs truncate">{r.title ?? '-'}</td>
                    <td className="px-4 py-3 text-sm text-right">
                      {r.return1M != null ? (
                        <span className={`font-semibold ${r.return1M >= 0 ? 'text-emerald-600' : 'text-rose-600'}`}>
                          {r.return1M >= 0 ? '+' : ''}{r.return1M.toFixed(2)}%
                        </span>
                      ) : '-'}
                    </td>
                    <td className="px-4 py-3 text-sm text-right">
                      {r.return3M != null ? (
                        <span className={`font-semibold ${r.return3M >= 0 ? 'text-emerald-600' : 'text-rose-600'}`}>
                          {r.return3M >= 0 ? '+' : ''}{r.return3M.toFixed(2)}%
                        </span>
                      ) : '-'}
                    </td>
                    <td className="px-4 py-3 text-sm text-right">
                      {r.return1Y != null ? (
                        <span className={`font-semibold ${r.return1Y >= 0 ? 'text-emerald-600' : 'text-rose-600'}`}>
                          {r.return1Y >= 0 ? '+' : ''}{r.return1Y.toFixed(2)}%
                        </span>
                      ) : '-'}
                    </td>
                    <td className="px-4 py-3 text-sm text-gray-700 text-right">
                      {r.numberOfInvestors == null ? '-' : Number(r.numberOfInvestors).toLocaleString('tr-TR')}
                    </td>
                    <td className="px-2 py-3">
                      <Link
                        to={`/market/tefas/compare?codes=${r.code}`}
                        onClick={e => e.stopPropagation()}
                        title="Karşılaştır"
                        className="p-1.5 rounded-lg bg-gray-100 hover:bg-[#093eaa] hover:text-white text-gray-400 transition-all inline-flex"
                      >
                        <BarChart2 className="w-3.5 h-3.5" />
                      </Link>
                    </td>
                  </tr>
                ))}
                {paged.length === 0 && (
                  <tr><td colSpan={6} className="px-4 py-8 text-center text-gray-400 text-sm">Sonuç bulunamadı.</td></tr>
                )}
              </tbody>
            </table>
          </div>
        )}

        {/* Pagination — TEFAS style */}
        {totalPages > 1 && (
          <div className="p-4 flex items-center justify-between border-t border-gray-100 flex-wrap gap-3">
            <span className="text-xs text-gray-500">
              {sorted.length} kayıttan {page * PAGE_SIZE + 1} - {Math.min((page + 1) * PAGE_SIZE, sorted.length)} arası
            </span>
            <div className="flex gap-1">
              <button disabled={page === 0} onClick={() => setPage(0)}
                className="px-3 py-1.5 rounded border border-gray-200 text-xs hover:bg-gray-50 disabled:opacity-40">Önceki</button>
              {Array.from({ length: Math.min(totalPages, 7) }, (_, i) => {
                const p = page < 4 ? i : page - 3 + i;
                if (p >= totalPages) return null;
                return (
                  <button key={p} onClick={() => setPage(p)}
                    className={`px-3 py-1.5 rounded border text-xs ${p === page ? 'bg-[#093eaa] text-white border-[#093eaa]' : 'border-gray-200 hover:bg-gray-50'}`}>
                    {p + 1}
                  </button>
                );
              })}
              {totalPages > 7 && <span className="px-2 py-1.5 text-xs text-gray-400">... {totalPages}</span>}
              <button disabled={page >= totalPages - 1} onClick={() => setPage(totalPages - 1)}
                className="px-3 py-1.5 rounded border border-gray-200 text-xs hover:bg-gray-50 disabled:opacity-40">Sonraki</button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
