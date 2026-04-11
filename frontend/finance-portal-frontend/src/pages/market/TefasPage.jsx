import { useEffect, useState, useMemo } from 'react';
import { getTefasFunds } from '../../api/marketApi';

const PAGE_SIZE = 25;

function pct(v) {
  if (v == null) return <span className="text-gray-400">-</span>;
  const n = parseFloat(v);
  return <span className={n >= 0 ? 'text-emerald-600 font-semibold' : 'text-rose-600 font-semibold'}>{n >= 0 ? '+' : ''}{n.toFixed(2)}%</span>;
}
function num(v, dec = 2) {
  return v == null ? '-' : parseFloat(v).toLocaleString('tr-TR', { minimumFractionDigits: dec, maximumFractionDigits: dec });
}

const KINDS = [
  { key: 'YAT', label: 'Yatırım Fonları' },
  { key: 'BYF', label: 'Borsa Yatırım Fonları' },
  { key: 'EMK', label: 'Emeklilik Fonları' },
];

export default function TefasPage() {
  const [kind, setKind] = useState('YAT');
  const [all, setAll] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);

  useEffect(() => {
    setLoading(true);
    setError('');
    getTefasFunds(kind, 0, 1000)
      .then(r => setAll(r.content ?? []))
      .catch(e => setError(!e.response ? 'Sunucuya ulaşılamıyor.' : `Hata (${e.response.status})`))
      .finally(() => setLoading(false));
  }, [kind]);

  const filtered = useMemo(() => {
    if (!search.trim()) return all;
    const q = search.toLowerCase();
    return all.filter(f => f.code?.toLowerCase().includes(q) || f.title?.toLowerCase().includes(q));
  }, [all, search]);

  const totalPages = Math.ceil(filtered.length / PAGE_SIZE);
  const paged = filtered.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE);

  return (
    <div>
      <h1 className="text-2xl font-bold text-gray-900 mb-2 border-l-4 border-[#093eaa] pl-4">TEFAS Fonları</h1>
      <p className="text-sm text-gray-500 mb-6 pl-5">Türkiye Elektronik Fon Alım Satım Platformu verileri</p>

      {/* Kind tabs */}
      <div className="flex gap-2 mb-4">
        {KINDS.map(k => (
          <button key={k.key} onClick={() => { setKind(k.key); setPage(0); setSearch(''); }}
            className={`px-4 py-2 rounded-xl text-sm font-semibold transition-all ${kind === k.key ? 'bg-[#093eaa] text-white' : 'bg-white text-gray-600 border border-gray-200 hover:bg-gray-50'}`}>
            {k.label}
          </button>
        ))}
      </div>

      <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden">
        <div className="p-4 border-b border-gray-100 flex items-center gap-3">
          <input type="text" placeholder="Fon kodu veya adı ara..."
            value={search} onChange={e => { setSearch(e.target.value); setPage(0); }}
            className="px-4 py-2 bg-gray-50 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-[#093eaa] min-w-[260px]" />
          <span className="text-xs text-gray-400">{filtered.length} fon</span>
        </div>

        {loading && <div className="p-8 text-center text-gray-400 text-sm">Yükleniyor...</div>}
        {error && <div className="p-6 text-rose-500 text-sm">{error}</div>}

        {!loading && !error && (
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead className="bg-gray-50">
                <tr>{['Kod', 'Fon Adı', 'Fiyat (TL)', 'Günlük %', 'Portföy Büyüklüğü', 'Yatırımcı', 'Tarih'].map(h =>
                  <th key={h} className="text-left px-4 py-3 text-xs font-bold text-gray-500 uppercase tracking-wider whitespace-nowrap border-b border-gray-200">{h}</th>
                )}</tr>
              </thead>
              <tbody>
                {paged.map(r => (
                  <tr key={r.code} className="border-t border-gray-100 hover:bg-gray-50 transition-colors">
                    <td className="px-4 py-3 font-bold text-[#093eaa] text-sm">{r.code}</td>
                    <td className="px-4 py-3 text-sm text-gray-700 max-w-xs truncate">{r.title ?? '-'}</td>
                    <td className="px-4 py-3 text-sm font-semibold">{num(r.price, 6)}</td>
                    <td className="px-4 py-3 text-sm">{pct(r.dailyReturnPercent)}</td>
                    <td className="px-4 py-3 text-sm text-gray-600">{r.marketCap == null ? '-' : num(r.marketCap, 0)}</td>
                    <td className="px-4 py-3 text-sm text-gray-600">{r.numberOfInvestors == null ? '-' : Number(r.numberOfInvestors).toLocaleString('tr-TR')}</td>
                    <td className="px-4 py-3 text-xs text-gray-400">{r.date ?? '-'}</td>
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
