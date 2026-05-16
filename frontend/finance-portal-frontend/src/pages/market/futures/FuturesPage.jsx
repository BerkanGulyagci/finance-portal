import { useEffect, useState, useRef, useMemo } from 'react';
import { Link } from 'react-router-dom';
import { getFutures, getViopContracts } from '../../../api/marketApi';
import { useSortable } from '../../../hooks/useSortable';
import SortableTh from '../../../components/common/SortableTh';

const PAGE_SIZE = 20;

function pct(v) {
  if (v == null) return <span className="text-gray-400">-</span>;
  const n = parseFloat(v);
  return <span className={n >= 0 ? 'text-emerald-600 font-semibold' : 'text-rose-600 font-semibold'}>{n >= 0 ? '+' : ''}{n.toFixed(2)}%</span>;
}
function num(v, dec = 2) {
  return v == null ? '-' : parseFloat(v).toLocaleString('tr-TR', { minimumFractionDigits: dec, maximumFractionDigits: dec });
}
function pctViop(v) {
  if (!v) return <span className="text-gray-400">-</span>;
  const clean = v.replace('%', '').replace(',', '.').trim();
  const n = parseFloat(clean);
  if (isNaN(n)) return <span className="text-gray-600 text-xs">{v}</span>;
  return <span className={n >= 0 ? 'text-emerald-600 font-semibold' : 'text-rose-600 font-semibold'}>{v}</span>;
}

function Pagination({ page, totalPages, onChange }) {
  if (totalPages <= 1) return null;
  const pages = [];
  const start = Math.max(0, page - 2);
  const end = Math.min(totalPages - 1, page + 2);
  for (let i = start; i <= end; i++) pages.push(i);
  return (
    <div className="flex items-center justify-center gap-1 py-4">
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

export default function FuturesPage() {
  const [activeTab, setActiveTab] = useState('viop');

  const [viopItems, setViopItems] = useState([]);
  const [viopSearch, setViopSearch] = useState('');
  const [viopPage, setViopPage] = useState(0);
  const viopFetched = useRef(false);

  const [globalItems, setGlobalItems] = useState([]);
  const [globalSearch, setGlobalSearch] = useState('');
  const [globalPage, setGlobalPage] = useState(0);
  const globalFetched = useRef(false);

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!viopFetched.current) {
      viopFetched.current = true;
      setLoading(true);
      getViopContracts()
        .then(setViopItems)
        .catch(e => setError(!e.response ? 'Sunucuya ulaşılamıyor.' : `Hata (${e.response.status})`))
        .finally(() => setLoading(false));
    }
  }, []);

  function handleTab(tab) {
    setActiveTab(tab);
    if (tab === 'global' && !globalFetched.current) {
      globalFetched.current = true;
      setLoading(true);
      setError('');
      getFutures(0, 100)
        .then(r => setGlobalItems(r.content ?? []))
        .catch(e => setError(!e.response ? 'Sunucuya ulaşılamıyor.' : `Hata (${e.response.status})`))
        .finally(() => setLoading(false));
    }
  }

  // VIOP filtered
  const filteredViop = useMemo(() => {
    if (!viopSearch.trim()) return viopItems;
    const q = viopSearch.toLowerCase();
    return viopItems.filter(r => r.name?.toLowerCase().includes(q));
  }, [viopItems, viopSearch]);

  const { sorted: sortedViop, sortKey: viopSortKey, sortDir: viopSortDir, handleSort: handleViopSort } = useSortable(filteredViop, 'name', 'asc');
  const viopTotalPages = Math.ceil(sortedViop.length / PAGE_SIZE);
  const viopPageItems = sortedViop.slice(viopPage * PAGE_SIZE, (viopPage + 1) * PAGE_SIZE);

  // Global filtered
  const filteredGlobal = useMemo(() => {
    if (!globalSearch.trim()) return globalItems;
    const q = globalSearch.toLowerCase();
    return globalItems.filter(r => r.symbol?.toLowerCase().includes(q) || r.name?.toLowerCase().includes(q));
  }, [globalItems, globalSearch]);

  const { sorted: sortedGlobal, sortKey: globalSortKey, sortDir: globalSortDir, handleSort: handleGlobalSort } = useSortable(filteredGlobal, 'symbol', 'asc');
  const globalTotalPages = Math.ceil(sortedGlobal.length / PAGE_SIZE);
  const globalPageItems = sortedGlobal.slice(globalPage * PAGE_SIZE, (globalPage + 1) * PAGE_SIZE);

  const viopThProps = (key, label, align = 'left') => ({
    label, sortKey: key, currentKey: viopSortKey, currentDir: viopSortDir,
    onSort: (k) => { handleViopSort(k); setViopPage(0); }, align
  });
  const globalThProps = (key, label, align = 'left') => ({
    label, sortKey: key, currentKey: globalSortKey, currentDir: globalSortDir,
    onSort: (k) => { handleGlobalSort(k); setGlobalPage(0); }, align
  });

  return (
    <div>
      <h1 className="text-2xl font-bold text-gray-900 mb-2 border-l-4 border-[#093eaa] pl-4">Vadeli İşlemler</h1>
      <p className="text-sm text-gray-500 mb-6 pl-5">Türkiye VİOP kontratları ve küresel vadeli işlemler</p>

      <div className="flex gap-2 mb-4">
        <button onClick={() => handleTab('viop')}
          className={`px-4 py-2 rounded-xl text-sm font-semibold transition-all ${activeTab === 'viop' ? 'bg-[#093eaa] text-white' : 'bg-white text-gray-600 border border-gray-200 hover:bg-gray-50'}`}>
          Türkiye VİOP
        </button>
        <button onClick={() => handleTab('global')}
          className={`px-4 py-2 rounded-xl text-sm font-semibold transition-all ${activeTab === 'global' ? 'bg-[#093eaa] text-white' : 'bg-white text-gray-600 border border-gray-200 hover:bg-gray-50'}`}>
          Küresel Vadeli
        </button>
      </div>

      <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden">
        {loading && <div className="p-8 text-center text-gray-400 text-sm">Yükleniyor...</div>}
        {error && <div className="p-6 text-rose-500 text-sm">{error}</div>}

        {/* VIOP Tab */}
        {!loading && !error && activeTab === 'viop' && (
          <>
            <div className="px-4 py-3 border-b border-gray-100 flex items-center gap-3">
              <div className="relative flex-1 max-w-sm">
                <svg className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
                </svg>
                <input type="text" placeholder="Sözleşme ara..." value={viopSearch}
                  onChange={e => { setViopSearch(e.target.value); setViopPage(0); }}
                  className="w-full pl-9 pr-3 py-2 text-sm border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-[#093eaa]/30 focus:border-[#093eaa]" />
              </div>
              <span className="text-xs text-gray-400">{sortedViop.length} sözleşme</span>
            </div>

            {viopItems.length === 0
              ? <p className="p-6 text-gray-400 text-sm">VİOP verisi bulunamadı.</p>
              : (
                <>
                  <div className="px-4 py-1.5 bg-gray-50 border-b border-gray-100 text-xs text-gray-400">
                    Kaynak: Akbank Yatırım · yatirim.akbank.com
                  </div>
                  <div className="overflow-x-auto">
                    <table className="w-full">
                      <thead className="bg-gray-50">
                        <tr>
                          <SortableTh {...viopThProps('name', 'Sözleşme')} />
                          <SortableTh {...viopThProps('changePercent', 'Fark (%)', 'right')} />
                          <SortableTh {...viopThProps('lastPrice', 'Son', 'right')} />
                          <SortableTh {...viopThProps('high', 'Yüksek', 'right')} />
                          <SortableTh {...viopThProps('low', 'Düşük', 'right')} />
                          <SortableTh {...viopThProps('openPositionCount', 'Açık Poz. Sayısı', 'right')} />
                          <SortableTh {...viopThProps('openPositionChange', 'Açık Poz. Değ.', 'right')} />
                          <SortableTh {...viopThProps('settlementPrice', 'Uzlaşma', 'right')} />
                          <SortableTh {...viopThProps('prevSettlementPrice', 'Önceki Uzlaşma', 'right')} />
                          <th className="px-3 py-3 text-xs font-bold text-gray-500 uppercase tracking-wider border-b border-gray-200 text-left">Zaman</th>
                        </tr>
                      </thead>
                      <tbody>
                        {viopPageItems.length === 0
                          ? <tr><td colSpan={10} className="px-4 py-8 text-center text-gray-400 text-sm">Sonuç bulunamadı.</td></tr>
                          : viopPageItems.map((r, i) => (
                              <tr key={i} className="border-t border-gray-100 hover:bg-gray-50 transition-colors">
                                <td className="px-3 py-2.5 text-sm">
                                  <Link 
                                    to={`/market/futures/${encodeURIComponent(r.name)}`}
                                    state={{ contract: r }}
                                    className="font-semibold text-[#093eaa] hover:underline whitespace-nowrap"
                                  >
                                    {r.name}
                                  </Link>
                                </td>
                                <td className="px-3 py-2.5 text-sm text-right">{pctViop(r.changePercent)}</td>
                                <td className="px-3 py-2.5 text-sm font-semibold text-gray-900 text-right">{r.lastPrice ?? '-'}</td>
                                <td className="px-3 py-2.5 text-sm text-gray-600 text-right">{r.high ?? '-'}</td>
                                <td className="px-3 py-2.5 text-sm text-gray-600 text-right">{r.low ?? '-'}</td>
                                <td className="px-3 py-2.5 text-sm text-gray-600 text-right">{r.openPositionCount ?? '-'}</td>
                                <td className="px-3 py-2.5 text-sm text-gray-600 text-right">{r.openPositionChange ?? '-'}</td>
                                <td className="px-3 py-2.5 text-sm text-gray-600 text-right">{r.settlementPrice ?? '-'}</td>
                                <td className="px-3 py-2.5 text-sm text-gray-600 text-right">{r.prevSettlementPrice ?? '-'}</td>
                                <td className="px-3 py-2.5 text-xs text-gray-400">{r.time ?? '-'}</td>
                              </tr>
                            ))
                        }
                      </tbody>
                    </table>
                  </div>
                  <Pagination page={viopPage} totalPages={viopTotalPages} onChange={p => setViopPage(p)} />
                </>
              )
            }
          </>
        )}

        {/* Global Futures Tab */}
        {!loading && !error && activeTab === 'global' && (
          <>
            <div className="px-4 py-3 border-b border-gray-100 flex items-center gap-3">
              <div className="relative flex-1 max-w-sm">
                <svg className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
                </svg>
                <input type="text" placeholder="Sembol veya isim ara..." value={globalSearch}
                  onChange={e => { setGlobalSearch(e.target.value); setGlobalPage(0); }}
                  className="w-full pl-9 pr-3 py-2 text-sm border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-[#093eaa]/30 focus:border-[#093eaa]" />
              </div>
              <span className="text-xs text-gray-400">{sortedGlobal.length} kontrat</span>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead className="bg-gray-50">
                  <tr>
                    <SortableTh {...globalThProps('symbol', 'Sembol')} />
                    <SortableTh {...globalThProps('name', 'Ad')} />
                    <SortableTh {...globalThProps('price', 'Fiyat', 'right')} />
                    <SortableTh {...globalThProps('change', 'Değişim', 'right')} />
                    <SortableTh {...globalThProps('changePercent', '%', 'right')} />
                    <SortableTh {...globalThProps('dayHigh', 'Yüksek', 'right')} />
                    <SortableTh {...globalThProps('dayLow', 'Düşük', 'right')} />
                    <SortableTh {...globalThProps('volume', 'Hacim', 'right')} />
                    <SortableTh {...globalThProps('exchange', 'Borsa')} />
                  </tr>
                </thead>
                <tbody>
                  {globalPageItems.length === 0
                    ? <tr><td colSpan={9} className="px-4 py-8 text-center text-gray-400 text-sm">Veri yok.</td></tr>
                    : globalPageItems.map(r => (
                      <tr key={r.symbol} className="border-t border-gray-100 hover:bg-gray-50 transition-colors">
                        <td className="px-4 py-3 font-bold text-[#093eaa] text-sm">{r.symbol}</td>
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
            <Pagination page={globalPage} totalPages={globalTotalPages} onChange={p => setGlobalPage(p)} />
          </>
        )}
      </div>
    </div>
  );
}
