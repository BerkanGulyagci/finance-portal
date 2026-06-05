import { useEffect, useState, useMemo, useCallback } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { Check, BarChart2 } from 'lucide-react';
import { getStocks, getAllStocks } from '../../../api/marketApi';
import SortableTh from '../../../components/common/SortableTh';
import { SkeletonTable } from '../../../components/common/Skeleton';
import WatchlistStar from '../../../components/instrument/WatchlistStar';
import InstrumentLogo from '../../../components/instrument/InstrumentLogo';
import Pagination from '../../../components/common/Pagination';
import IndicesView from './IndicesView';
import IndexChart from './components/IndexChart';
import { useTranslation } from '../../../context/LanguageContext';

const PAGE_SIZE = 20;

const INDEX_FILTERS = [
  { key: '',        label: 'Tüm Hisseler', size: PAGE_SIZE, indexSymbol: null },
  { key: 'BIST30',  label: 'BIST 30',      size: 30,        indexSymbol: 'XU030.IS' },
  { key: 'BIST50',  label: 'BIST 50',      size: 50,        indexSymbol: 'XU050.IS' },
  { key: 'BIST100', label: 'BIST 100',     size: 100,       indexSymbol: 'XU100.IS' },
];

function pct(v) {
  if (v == null) return <span className="text-gray-400">-</span>;
  const n = parseFloat(v);
  return <span className={n >= 0 ? 'text-emerald-600 font-semibold' : 'text-rose-600 font-semibold'}>{n >= 0 ? '+' : ''}{n.toFixed(2)}%</span>;
}
function num(v, dec = 2) {
  return v == null ? '-' : parseFloat(v).toLocaleString('tr-TR', { minimumFractionDigits: dec, maximumFractionDigits: dec });
}

export default function StocksPage() {
  const { t } = useTranslation();
  const [searchParams, setSearchParams] = useSearchParams();
  const view = searchParams.get('view') === 'indices' ? 'indices' : 'stocks';
  const [pageData, setPageData]     = useState(null);
  const [allStocksCache, setAllStocksCache] = useState([]); // tüm hisseler arama için
  const [loading, setLoading]       = useState(true);
  const [error, setError]           = useState('');
  const [page, setPage]             = useState(0);
  const [search, setSearch]         = useState('');
  const [activeIndex, setActiveIndex] = useState('');

  const activeFilter = INDEX_FILTERS.find(f => f.key === activeIndex) ?? INDEX_FILTERS[0];
  const currentSize  = activeFilter.size;

  const fetchPage = useCallback((p, idx, sz) => {
    setLoading(true);
    setError('');
    getStocks(p, sz, idx || undefined)
      .then(data => setPageData(data))
      .catch(e => setError(!e.response ? t('Sunucuya ulaşılamıyor.') : `${t('Hata')} (${e.response.status})`))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => { if (view === 'stocks') fetchPage(page, activeIndex, currentSize); }, [view, page, activeIndex, currentSize, fetchPage]);

  // Tüm hisseleri arka planda çek — arama için
  useEffect(() => {
    getAllStocks().then(data => setAllStocksCache(data ?? [])).catch(() => {});
  }, []);

  function handleIndexChange(idx) {
    setActiveIndex(idx);
    setPage(0);
    setSearch('');
    if (view === 'indices') setSearchParams({}, { replace: true });
  }

  function handleIndicesTab() {
    setSearch('');
    setSearchParams({ view: 'indices' });
  }

  function handlePageChange(p) {
    setPage(p);
    setSearch('');
    window.scrollTo(0, 0);
  }

  const items = pageData?.content ?? [];

  // Sıralama state'i — sortKey null ise "doğal" sıra (server pagination).
  const [sortKey, setSortKey] = useState(null);
  const [sortDir, setSortDir] = useState('asc');
  const handleSort = useCallback((key) => {
    setPage(0); // sıralama değişince ilk sayfaya dön
    setSortKey(prevKey => {
      if (prevKey === key) {
        setSortDir(d => (d === 'asc' ? 'desc' : 'asc'));
        return key;
      }
      setSortDir('asc');
      return key;
    });
  }, []);

  // Veri kaynağı: arama VEYA sıralama aktifse TÜM hisseler (cache); değilse sayfa verisi.
  // Böylece bir sütuna basınca sadece görünen sayfa değil, TÜM liste sıralanır.
  const useFullList = (!!search.trim() || !!sortKey) && allStocksCache.length > 0;

  const baseRows = useMemo(() => {
    if (!useFullList) return items;
    let rows = allStocksCache;
    if (search.trim()) {
      const q = search.toLowerCase();
      rows = rows.filter(s => s.symbol?.toLowerCase().includes(q) || s.name?.toLowerCase().includes(q));
    }
    // Endeks filtresi aktifse (BIST30/50/100) o filtrenin sembolleriyle sınırla — cache tüm hisseler.
    // activeIndex varken zaten ayrı endeks listesi geliyor; full-list yolunda endeks filtresi
    // uygulanamıyorsa (cache'te endeks üyeliği yok) activeIndex'te server verisine düşülür.
    return rows;
  }, [useFullList, items, allStocksCache, search]);

  // Sıralama (client-side) — sortKey varsa baseRows'u sırala.
  const sortedAll = useMemo(() => {
    if (!sortKey || !baseRows?.length) return baseRows;
    return [...baseRows].sort((a, b) => {
      const av = a[sortKey], bv = b[sortKey];
      const an = parseFloat(String(av ?? '').replace(',', '.').replace('%', ''));
      const bn = parseFloat(String(bv ?? '').replace(',', '.').replace('%', ''));
      if (!isNaN(an) && !isNaN(bn)) return sortDir === 'asc' ? an - bn : bn - an;
      const as = String(av ?? '').toLowerCase(), bs = String(bv ?? '').toLowerCase();
      if (as < bs) return sortDir === 'asc' ? -1 : 1;
      if (as > bs) return sortDir === 'asc' ? 1 : -1;
      return 0;
    });
  }, [baseRows, sortKey, sortDir]);

  // Full-list yolunda client-side sayfalama; aksi halde server verisi olduğu gibi.
  const clientPaged = useFullList && !search.trim() && sortKey
    ? sortedAll.slice(page * currentSize, page * currentSize + currentSize)
    : sortedAll;

  const sorted = clientPaged;
  // Sayfa sayısı: full-list sıralamada cache uzunluğundan; aksi halde server'dan.
  const serverTotalPages = pageData?.totalPages ?? 0;
  const serverTotalElements = pageData?.totalElements ?? 0;
  const clientSorting = useFullList && !search.trim() && !!sortKey;
  const totalPages    = clientSorting ? Math.ceil(sortedAll.length / currentSize) : serverTotalPages;
  const totalElements = clientSorting ? sortedAll.length : serverTotalElements;

  const thProps = (key, label, align = 'left') => ({
    label, sortKey: key, currentKey: sortKey, currentDir: sortDir, onSort: handleSort, align
  });

  return (
    <div>
      <div className="flex items-center justify-between mb-4 flex-wrap gap-2">
        <h1 className="text-xl sm:text-2xl font-bold text-gray-900 border-l-4 border-[#093eaa] pl-4">{t('Hisse Senetleri')}</h1>
        <Link
          to="/market/stocks/compare"
          className="inline-flex items-center gap-2 px-5 py-2.5 bg-[#093eaa] text-white text-sm font-semibold rounded-full shadow-sm hover:bg-[#0a47c2] hover:shadow-md active:bg-[#072f86] transition-all"
        >
          <BarChart2 className="w-4 h-4" />
          {t('Karşılaştır')}
        </Link>
      </div>
      <p className="text-sm text-gray-500 mb-4 pl-5">
        {view === 'indices'
          ? t('BIST endeksleri · canlı değerler')
          : <>{t('Borsa İstanbul (BIST) hisse senedi fiyatları')}
              {totalElements > 0 && <span className="ml-2 text-gray-400">· {totalElements} {t('hisse')}</span>}</>}
      </p>

      {/* Görünüm seçici — Material 3 segmented buttons (hisse filtreleri + Endeksler) */}
      <div className="mb-4 overflow-x-auto pb-1">
        <div className="inline-flex rounded-full border border-gray-300 bg-white divide-x divide-gray-300 shadow-sm overflow-hidden">
          {INDEX_FILTERS.map(f => {
            const active = view === 'stocks' && activeIndex === f.key;
            return (
              <button key={f.key} onClick={() => handleIndexChange(f.key)}
                className={`px-4 sm:px-5 py-2 text-sm font-medium inline-flex items-center gap-1.5 whitespace-nowrap transition-colors ${
                  active ? 'bg-[#093eaa]/[0.12] text-[#093eaa]' : 'text-gray-600 hover:bg-gray-50 active:bg-gray-100'
                }`}>
                {active && <Check className="w-4 h-4 -ml-0.5" />}
                {t(f.label)}
              </button>
            );
          })}
          <button onClick={handleIndicesTab}
            className={`px-4 sm:px-5 py-2 text-sm font-medium inline-flex items-center gap-1.5 whitespace-nowrap transition-colors ${
              view === 'indices' ? 'bg-[#093eaa]/[0.12] text-[#093eaa]' : 'text-gray-600 hover:bg-gray-50 active:bg-gray-100'
            }`}>
            {view === 'indices' && <Check className="w-4 h-4 -ml-0.5" />}
            {t('Endeksler')}
          </button>
        </div>
      </div>

      {/* Endeks grafiği — sadece hisse görünümünde + bir BIST filtresi seçiliyken.
          Endeks DETAY sayfasıyla AYNI bileşen (aynı kaynak + aynı çizim + hover). */}
      {view === 'stocks' && activeFilter.indexSymbol && <IndexChart symbol={activeFilter.indexSymbol} label={activeFilter.label} />}

      {/* Hisse tablosu / Endeksler listesi */}
      {view === 'indices' ? <IndicesView /> : (
      <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden">
        <div className="p-4 border-b border-gray-100 flex items-center gap-3 flex-wrap">
          <input
            type="text"
            placeholder={t('Sembol veya şirket ara...')}
            value={search}
            onChange={e => setSearch(e.target.value)}
            className="px-4 py-2 bg-gray-50 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-[#093eaa] w-full sm:w-auto sm:min-w-[240px]"
          />
          {search && (
            <button onClick={() => setSearch('')}
              className="px-3 py-2 border border-gray-200 rounded-xl text-sm hover:bg-gray-50">✕</button>
          )}
          <span className="text-xs text-gray-400 ml-auto">
            {activeIndex
              ? `${sorted.length} ${t('hisse')}`
              : search
                ? `${sorted.length} ${t('sonuç (tüm hisseler)')}`
                : clientSorting
                  ? `${t('Sayfa')} ${page + 1} / ${totalPages} · ${totalElements} ${t('hisse (tümü sıralandı)')}`
                  : `${t('Sayfa')} ${page + 1} / ${totalPages} · ${sorted.length} ${t('sonuç')}`}
          </span>
        </div>

        {loading && <SkeletonTable rows={10} cols={9} />}
        {error && <div className="p-6 text-rose-500 text-sm">{error}</div>}

        {!loading && !error && (
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead className="bg-gray-50">
                <tr>
                  <SortableTh {...thProps('symbol', t('Sembol'))} />
                  <SortableTh {...thProps('name', t('Şirket'))} />
                  <SortableTh {...thProps('price', t('Fiyat'), 'right')} />
                  <SortableTh {...thProps('change', t('Değişim'), 'right')} />
                  <SortableTh {...thProps('changePercent', '%', 'right')} />
                  <SortableTh {...thProps('dayHigh', t('Yüksek'), 'right')} />
                  <SortableTh {...thProps('dayLow', t('Düşük'), 'right')} />
                  <SortableTh {...thProps('volume', t('Hacim'), 'right')} />
                  <SortableTh {...thProps('exchange', t('Borsa'))} />
                  <th className="px-2 py-3 w-8" aria-label={t('İzle')} />
                </tr>
              </thead>
              <tbody>
                {sorted.length === 0
                  ? <tr><td colSpan={10} className="px-4 py-8 text-center text-gray-400 text-sm">{t('Sonuç bulunamadı.')}</td></tr>
                  : sorted.map(r => (
                    <tr key={r.symbol} className="border-t border-gray-100 hover:bg-blue-50 transition-colors cursor-pointer">
                      <td className="px-4 py-3 font-bold text-sm">
                        <div className="flex items-center gap-2.5">
                          <InstrumentLogo symbol={r.symbol} name={r.name} size={24} />
                          <Link to={`/market/stocks/${r.symbol}`} className="text-[#093eaa] hover:underline">{r.symbol}</Link>
                        </div>
                      </td>
                      <td className="px-4 py-3 text-sm text-gray-700">{r.name ?? '-'}</td>
                      <td className="px-4 py-3 text-sm font-semibold text-right">
                        {num(r.price)} <span className="text-gray-400 text-xs">{r.currency}</span>
                      </td>
                      <td className="px-4 py-3 text-sm text-right">
                        {r.change == null ? '-' : <span className={parseFloat(r.change) >= 0 ? 'text-emerald-600 font-semibold' : 'text-rose-600 font-semibold'}>{num(r.change)}</span>}
                      </td>
                      <td className="px-4 py-3 text-sm text-right">{pct(r.changePercent)}</td>
                      <td className="px-4 py-3 text-sm text-gray-600 text-right">{num(r.dayHigh)}</td>
                      <td className="px-4 py-3 text-sm text-gray-600 text-right">{num(r.dayLow)}</td>
                      <td className="px-4 py-3 text-sm text-gray-600 text-right">
                        {r.volume == null ? '-' : Number(r.volume).toLocaleString('tr-TR')}
                      </td>
                      <td className="px-4 py-3 text-xs text-gray-400">{r.exchange ?? '-'}</td>
                      <td className="px-2 py-3 text-center" onClick={e => e.stopPropagation()}>
                        <WatchlistStar assetType="STOCK" symbol={r.symbol} name={r.name} price={r.price} />
                      </td>
                    </tr>
                  ))
                }
              </tbody>
            </table>
          </div>
        )}

        {/* Pagination — endeks filtresi ve arama yokken (sıralamada client-side sayfalama). */}
        {!loading && !activeIndex && !search && totalPages > 1 && (
          <Pagination
            page={page}
            totalPages={totalPages}
            totalElements={totalElements}
            unitLabel="hisse"
            onChange={clientSorting ? (p) => { setPage(p); window.scrollTo(0, 0); } : handlePageChange}
          />
        )}
      </div>
      )}
    </div>
  );
}
