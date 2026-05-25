import { useEffect, useState, useRef, useMemo } from 'react';
import { Link } from 'react-router-dom';
import { getFutures, getViopContracts } from '../../../api/marketApi';
import { useSortable } from '../../../hooks/useSortable';
import SortableTh from '../../../components/common/SortableTh';
import Pagination from '../../../components/common/Pagination';
import WatchlistStar from '../../../components/instrument/WatchlistStar';
import InstrumentLogo from '../../../components/instrument/InstrumentLogo';
import { useTranslation } from '../../../i18n/LanguageContext';

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

export default function FuturesPage() {
  const { t } = useTranslation();
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
        .catch(e => setError(!e.response ? t('Sunucuya ulaşılamıyor.') : `${t('Hata')} (${e.response.status})`))
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
        .catch(e => setError(!e.response ? t('Sunucuya ulaşılamıyor.') : `${t('Hata')} (${e.response.status})`))
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
      <h1 className="text-2xl font-bold text-gray-900 mb-2 border-l-4 border-[#093eaa] pl-4">{t('Vadeli İşlemler')}</h1>
      <p className="text-sm text-gray-500 mb-6 pl-5">{t('Türkiye VİOP kontratları')}</p>

      <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden">
        {loading && <div className="p-8 text-center text-gray-400 text-sm">{t('Yükleniyor...')}</div>}
        {error && <div className="p-6 text-rose-500 text-sm">{error}</div>}

        {/* VIOP Tab */}
        {!loading && !error && activeTab === 'viop' && (
          <>
            <div className="px-4 py-3 border-b border-gray-100 flex items-center gap-3">
              <div className="relative flex-1 max-w-sm">
                <svg className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
                </svg>
                <input type="text" placeholder={t('Sözleşme ara...')} value={viopSearch}
                  onChange={e => { setViopSearch(e.target.value); setViopPage(0); }}
                  className="w-full pl-9 pr-3 py-2 text-sm border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-[#093eaa]/30 focus:border-[#093eaa]" />
              </div>
              <span className="text-xs text-gray-400">{sortedViop.length} {t('sözleşme')}</span>
            </div>

            {viopItems.length === 0
              ? <p className="p-6 text-gray-400 text-sm">{t('VİOP verisi bulunamadı.')}</p>
              : (
                <>
                  <div className="px-4 py-1.5 bg-gray-50 border-b border-gray-100 text-xs text-gray-400">
                    {t('Kaynak: Akbank Yatırım · yatirim.akbank.com')}
                  </div>
                  <div className="overflow-x-auto">
                    <table className="w-full">
                      <thead className="bg-gray-50">
                        <tr>
                          <SortableTh {...viopThProps('name', t('Sözleşme'))} />
                          <SortableTh {...viopThProps('changePercent', t('Fark (%)'), 'right')} />
                          <SortableTh {...viopThProps('lastPrice', t('Son'), 'right')} />
                          <SortableTh {...viopThProps('high', t('Yüksek'), 'right')} />
                          <SortableTh {...viopThProps('low', t('Düşük'), 'right')} />
                          <SortableTh {...viopThProps('openPositionCount', t('Açık Poz. Sayısı'), 'right')} />
                          <SortableTh {...viopThProps('openPositionChange', t('Açık Poz. Değ.'), 'right')} />
                          <SortableTh {...viopThProps('settlementPrice', t('Uzlaşma'), 'right')} />
                          <SortableTh {...viopThProps('prevSettlementPrice', t('Önceki Uzlaşma'), 'right')} />
                          <th className="px-3 py-3 text-xs font-bold text-gray-500 uppercase tracking-wider border-b border-gray-200 text-left">{t('Zaman')}</th>
                          <th className="px-2 py-3 w-8 border-b border-gray-200" aria-label={t('İzle')} />
                        </tr>
                      </thead>
                      <tbody>
                        {viopPageItems.length === 0
                          ? <tr><td colSpan={11} className="px-4 py-8 text-center text-gray-400 text-sm">{t('Sonuç bulunamadı.')}</td></tr>
                          : viopPageItems.map((r, i) => (
                              <tr key={i} className="border-t border-gray-100 hover:bg-gray-50 transition-colors">
                                <td className="px-3 py-2.5 text-sm">
                                  <div className="flex items-center gap-2.5 min-w-0">
                                    <InstrumentLogo symbol={r.name} name={r.name} size={24} />
                                    <Link
                                      to={`/market/futures/${encodeURIComponent(r.name)}`}
                                      state={{ contract: r }}
                                      className="font-semibold text-[#093eaa] hover:underline whitespace-nowrap"
                                    >
                                      {r.name}
                                    </Link>
                                  </div>
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
                                <td className="px-2 py-2.5 text-center" onClick={e => e.stopPropagation()}>
                                  <WatchlistStar assetType="FUTURE" symbol={r.name} name={r.name} price={r.lastPrice} />
                                </td>
                              </tr>
                            ))
                        }
                      </tbody>
                    </table>
                  </div>
                  <Pagination page={viopPage} totalPages={viopTotalPages} totalElements={sortedViop.length} unitLabel="sözleşme" onChange={p => { setViopPage(p); window.scrollTo(0, 0); }} />
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
                <input type="text" placeholder={t('Sembol veya isim ara...')} value={globalSearch}
                  onChange={e => { setGlobalSearch(e.target.value); setGlobalPage(0); }}
                  className="w-full pl-9 pr-3 py-2 text-sm border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-[#093eaa]/30 focus:border-[#093eaa]" />
              </div>
              <span className="text-xs text-gray-400">{sortedGlobal.length} {t('kontrat')}</span>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead className="bg-gray-50">
                  <tr>
                    <SortableTh {...globalThProps('symbol', t('Sembol'))} />
                    <SortableTh {...globalThProps('name', t('Ad'))} />
                    <SortableTh {...globalThProps('price', t('Fiyat'), 'right')} />
                    <SortableTh {...globalThProps('change', t('Değişim'), 'right')} />
                    <SortableTh {...globalThProps('changePercent', '%', 'right')} />
                    <SortableTh {...globalThProps('dayHigh', t('Yüksek'), 'right')} />
                    <SortableTh {...globalThProps('dayLow', t('Düşük'), 'right')} />
                    <SortableTh {...globalThProps('volume', t('Hacim'), 'right')} />
                    <SortableTh {...globalThProps('exchange', t('Borsa'))} />
                    <th className="px-2 py-3 w-8" aria-label={t('İzle')} />
                  </tr>
                </thead>
                <tbody>
                  {globalPageItems.length === 0
                    ? <tr><td colSpan={10} className="px-4 py-8 text-center text-gray-400 text-sm">{t('Veri yok.')}</td></tr>
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
                        <td className="px-2 py-3 text-center" onClick={e => e.stopPropagation()}>
                          <WatchlistStar assetType="FUTURE" symbol={r.symbol} name={r.name} price={r.price} />
                        </td>
                      </tr>
                    ))
                  }
                </tbody>
              </table>
            </div>
            <Pagination page={globalPage} totalPages={globalTotalPages} totalElements={sortedGlobal.length} unitLabel="kontrat" onChange={p => { setGlobalPage(p); window.scrollTo(0, 0); }} />
          </>
        )}
      </div>
    </div>
  );
}
