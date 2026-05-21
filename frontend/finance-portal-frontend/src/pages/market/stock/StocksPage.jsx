import { useEffect, useState, useMemo, useCallback, useRef } from 'react';
import { Link } from 'react-router-dom';
import { getStocks, getStockChart, getAllStocks } from '../../../api/marketApi';
import { useSortable } from '../../../hooks/useSortable';
import SortableTh from '../../../components/common/SortableTh';
import { STOCK_CHART_RANGES, formatStockChartTimeLabel } from './stockChartRanges';
import { useTranslation } from '../../../i18n/LanguageContext';

const PAGE_SIZE = 20;

const INDEX_FILTERS = [
  { key: '',        label: 'Tüm Hisseler', size: PAGE_SIZE, indexSymbol: null },
  { key: 'BIST30',  label: 'BIST 30',      size: 30,        indexSymbol: 'XU030.IS' },
  { key: 'BIST50',  label: 'BIST 50',      size: 50,        indexSymbol: 'XU050.IS' },
  { key: 'BIST100', label: 'BIST 100',     size: 100,       indexSymbol: 'XU100.IS' },
];

const CHART_RANGES = STOCK_CHART_RANGES;

function IndexChart({ symbol, label }) {
  const { t } = useTranslation();
  const [data, setData]         = useState([]);
  const [loading, setLoading]   = useState(true);
  const [rangeIdx, setRangeIdx] = useState(2); // default 1A

  const chartRef    = useRef(null);
  const instanceRef = useRef(null);

  const activeRange = CHART_RANGES[rangeIdx];

  // Veri çek
  useEffect(() => {
    setLoading(true);
    getStockChart(symbol, activeRange.range, activeRange.interval)
      .then(res => {
        const ts     = res?.timestamps  ?? [];
        const prices = res?.closePrices ?? [];
        setData(
          ts.map((t, i) => ({
            label: formatStockChartTimeLabel(t, activeRange.range, activeRange.interval),
            price: prices[i] != null ? parseFloat(prices[i]) : null,
          })).filter(d => d.price != null)
        );
      })
      .catch(() => setData([]))
      .finally(() => setLoading(false));
  }, [symbol, activeRange.range, activeRange.interval]);

  // ECharts render
  useEffect(() => {
    if (!chartRef.current || data.length === 0) return;

    import('echarts').then(echarts => {
      if (instanceRef.current) instanceRef.current.dispose();

      const chart = echarts.init(chartRef.current, null, { renderer: 'canvas' });
      instanceRef.current = chart;

      const isUp  = data[data.length - 1].price >= data[0].price;
      const color = isUp ? '#10b981' : '#ef4444';

      chart.setOption({
        backgroundColor: '#ffffff',
        animation: false,
        grid: { top: 16, right: 16, bottom: 80, left: 72 },
        xAxis: {
          type: 'category',
          data: data.map(d => d.label),
          axisLine: { lineStyle: { color: '#e5e7eb' } },
          axisTick: { show: false },
          axisLabel: { color: '#9ca3af', fontSize: 10, interval: 'auto' },
          splitLine: { show: false },
        },
        yAxis: {
          type: 'value',
          min: 'dataMin',
          max: 'dataMax',
          axisLine: { show: false },
          axisTick: { show: false },
          axisLabel: {
            color: '#9ca3af',
            fontSize: 10,
            formatter: v => v.toLocaleString('tr-TR', { maximumFractionDigits: 0 }),
          },
          splitLine: { lineStyle: { color: '#f0f0f0', type: 'dashed' } },
        },
        tooltip: {
          trigger: 'axis',
          axisPointer: { type: 'cross', lineStyle: { color: '#9ca3af', type: 'dashed' } },
          backgroundColor: '#ffffff',
          borderColor: '#e5e7eb',
          borderRadius: 12,
          padding: [10, 14],
          formatter: params => {
            if (!params?.length) return '';
            const p = params[0];
            return `<div style="font-size:11px;color:#6b7280;font-weight:600;margin-bottom:6px;padding-bottom:6px;border-bottom:1px solid #f0f0f0">${p.axisValue}</div>
              <div style="display:flex;justify-content:space-between;gap:16px;align-items:center">
                <span style="font-size:11px;color:#6b7280">${t('Değer:')}</span>
                <span style="font-weight:700;font-size:11px;color:#111827">${p.value != null ? parseFloat(p.value).toLocaleString('tr-TR', { maximumFractionDigits: 2 }) : '-'}</span>
              </div>`;
          },
        },
        dataZoom: [
          { type: 'inside', xAxisIndex: 0, start: 0, end: 100, zoomOnMouseWheel: true },
          {
            type: 'slider',
            xAxisIndex: 0,
            start: 0,
            end: 100,
            height: 18,
            bottom: 8,
            borderColor: '#e5e7eb',
            fillerColor: 'rgba(9,62,170,0.08)',
            handleStyle: { color: '#093eaa' },
            showDetail: false,
          },
        ],
        series: [{
          type: 'line',
          data: data.map(d => d.price),
          smooth: false,
          symbol: 'none',
          lineStyle: { width: 2, color },
          areaStyle: {
            color: {
              type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
              colorStops: [
                { offset: 0, color: color + '33' },
                { offset: 1, color: color + '00' },
              ],
            },
          },
        }],
      });

      const ro = new ResizeObserver(() => chart.resize());
      ro.observe(chartRef.current);
      return () => ro.disconnect();
    });

    return () => {
      if (instanceRef.current) {
        instanceRef.current.dispose();
        instanceRef.current = null;
      }
    };
  }, [data, loading]);

  const isUp      = data.length > 1 && data[data.length - 1].price >= data[0].price;
  const change    = data.length > 1
    ? (((data[data.length - 1].price - data[0].price) / data[0].price) * 100).toFixed(2)
    : null;
  const absChange = data.length > 1
    ? (data[data.length - 1].price - data[0].price).toLocaleString('tr-TR', { maximumFractionDigits: 2 })
    : null;

  return (
    <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-5 mb-6">
      <div className="flex items-center justify-between mb-4 flex-wrap gap-2">
        <h2 className="text-base font-bold text-gray-900">{label} {t('Endeksi')}</h2>
        <div className="flex gap-1">
          {CHART_RANGES.map((r, i) => (
            <button key={r.label} onClick={() => setRangeIdx(i)}
              className={`px-2.5 py-1 rounded-lg text-xs font-bold transition-all ${
                i === rangeIdx ? 'bg-[#093eaa] text-white' : 'bg-gray-100 text-gray-500 hover:bg-gray-200'
              }`}>
              {t(r.label)}
            </button>
          ))}
        </div>
      </div>

      {/* Loading overlay — chart div her zaman DOM'da kalır */}
      <div style={{ position: 'relative', height: 260 }}>
        {loading && (
          <div style={{ position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', background: '#fff', zIndex: 10 }}>
            <div className="flex gap-1.5">
              <div className="w-2 h-2 bg-[#093eaa] rounded-full animate-bounce" />
              <div className="w-2 h-2 bg-[#093eaa]/60 rounded-full animate-bounce [animation-delay:100ms]" />
              <div className="w-2 h-2 bg-[#093eaa]/30 rounded-full animate-bounce [animation-delay:200ms]" />
            </div>
          </div>
        )}
        {!loading && data.length === 0 && (
          <div style={{ position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <span className="text-gray-400 text-sm">{t('Grafik verisi yüklenemedi.')}</span>
          </div>
        )}
        <div ref={chartRef} style={{ width: '100%', height: '100%', visibility: loading || data.length === 0 ? 'hidden' : 'visible' }} />
      </div>

      {change != null && (
        <div className="flex items-center justify-between mt-3 pt-3 border-t border-gray-100">
          <div>
            <p className="text-xs text-gray-400">{t(activeRange.label)} {t('Değişim')}</p>
            <p className={`text-sm font-bold ${isUp ? 'text-emerald-600' : 'text-rose-600'}`}>
              {isUp ? '+' : ''}₺{absChange} ({isUp ? '+' : ''}{change}%)
            </p>
          </div>
          <div className="text-right">
            <p className="text-xs text-gray-400">{t('Son Değer')}</p>
            <p className="text-sm font-bold text-gray-900">
              {data[data.length - 1]?.price.toLocaleString('tr-TR', { maximumFractionDigits: 2 })}
            </p>
          </div>
        </div>
      )}
    </div>
  );
}

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

  useEffect(() => { fetchPage(page, activeIndex, currentSize); }, [page, activeIndex, currentSize, fetchPage]);

  // Tüm hisseleri arka planda çek — arama için
  useEffect(() => {
    getAllStocks().then(data => setAllStocksCache(data ?? [])).catch(() => {});
  }, []);

  function handleIndexChange(idx) {
    setActiveIndex(idx);
    setPage(0);
    setSearch('');
  }

  function handlePageChange(p) {
    setPage(p);
    setSearch('');
    window.scrollTo(0, 0);
  }

  const items = pageData?.content ?? [];

  const filtered = useMemo(() => {
    if (!search.trim()) return items;
    const q = search.toLowerCase();
    // Arama varsa tüm hisseler cache'inden filtrele
    const source = allStocksCache.length > 0 ? allStocksCache : items;
    return source.filter(s =>
      s.symbol?.toLowerCase().includes(q) ||
      s.name?.toLowerCase().includes(q)
    );
  }, [items, allStocksCache, search]);

  const { sorted, sortKey, sortDir, handleSort } = useSortable(filtered, 'symbol', 'asc');
  const totalPages    = pageData?.totalPages ?? 0;
  const totalElements = pageData?.totalElements ?? 0;

  const thProps = (key, label, align = 'left') => ({
    label, sortKey: key, currentKey: sortKey, currentDir: sortDir, onSort: handleSort, align
  });

  return (
    <div>
      <div className="flex items-center justify-between mb-4 flex-wrap gap-2">
        <h1 className="text-2xl font-bold text-gray-900 border-l-4 border-[#093eaa] pl-4">{t('Hisse Senetleri')}</h1>
        <Link
          to="/market/stocks/compare"
          className="flex items-center gap-1.5 px-4 py-2 bg-[#093eaa] text-white text-sm font-bold rounded-xl hover:bg-[#0730a0] transition-all"
        >
          <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
          </svg>
          {t('Karşılaştır')}
        </Link>
      </div>
      <p className="text-sm text-gray-500 mb-4 pl-5">
        {t('Borsa İstanbul (BIST) hisse senedi fiyatları')}
        {totalElements > 0 && <span className="ml-2 text-gray-400">· {totalElements} {t('hisse')}</span>}
      </p>

      {/* Endeks filtre butonları */}
      <div className="flex gap-2 mb-4 flex-wrap">
        {INDEX_FILTERS.map(f => (
          <button key={f.key} onClick={() => handleIndexChange(f.key)}
            className={`px-4 py-2 rounded-xl text-sm font-bold transition-all ${
              activeIndex === f.key
                ? 'bg-[#093eaa] text-white shadow-sm'
                : 'bg-white border border-gray-200 text-gray-600 hover:bg-gray-50'
            }`}>
            {t(f.label)}
          </button>
        ))}
      </div>

      {/* Endeks grafiği — sadece endeks seçilince göster */}
      {activeFilter.indexSymbol && <IndexChart symbol={activeFilter.indexSymbol} label={activeFilter.label} />}

      {/* Hisse tablosu */}
      <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden">
        <div className="p-4 border-b border-gray-100 flex items-center gap-3 flex-wrap">
          <input
            type="text"
            placeholder={t('Sembol veya şirket ara...')}
            value={search}
            onChange={e => setSearch(e.target.value)}
            className="px-4 py-2 bg-gray-50 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-[#093eaa] min-w-[240px]"
          />
          {search && (
            <button onClick={() => setSearch('')}
              className="px-3 py-2 border border-gray-200 rounded-xl text-sm hover:bg-gray-50">✕</button>
          )}
          <span className="text-xs text-gray-400 ml-auto">
            {activeIndex ? `${sorted.length} ${t('hisse')}` : search ? `${sorted.length} ${t('sonuç (tüm hisseler)')}` : `${t('Sayfa')} ${page + 1} / ${totalPages} · ${sorted.length} ${t('sonuç')}`}
          </span>
        </div>

        {loading && (
          <div className="p-8 text-center">
            <div className="flex items-center justify-center gap-2 mb-2">
              <div className="w-2 h-2 bg-[#093eaa] rounded-full animate-bounce" />
              <div className="w-2 h-2 bg-[#093eaa]/60 rounded-full animate-bounce [animation-delay:100ms]" />
              <div className="w-2 h-2 bg-[#093eaa]/30 rounded-full animate-bounce [animation-delay:200ms]" />
            </div>
            <p className="text-gray-400 text-sm">{t('Hisseler yükleniyor...')}</p>
          </div>
        )}
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
                </tr>
              </thead>
              <tbody>
                {sorted.length === 0
                  ? <tr><td colSpan={9} className="px-4 py-8 text-center text-gray-400 text-sm">{t('Sonuç bulunamadı.')}</td></tr>
                  : sorted.map(r => (
                    <tr key={r.symbol} className="border-t border-gray-100 hover:bg-blue-50 transition-colors cursor-pointer">
                      <td className="px-4 py-3 font-bold text-sm">
                        <Link to={`/market/stocks/${r.symbol}`} className="text-[#093eaa] hover:underline">{r.symbol}</Link>
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
                    </tr>
                  ))
                }
              </tbody>
            </table>
          </div>
        )}

        {/* Pagination — sadece endeks filtresi ve arama yokken */}
        {totalPages > 1 && !loading && !activeIndex && !search && (
          <div className="p-4 flex items-center justify-between border-t border-gray-100 flex-wrap gap-3">
            <span className="text-xs text-gray-500">{t('Toplam')} {totalElements} {t('hisse')} · {t('Sayfa')} {page + 1} / {totalPages}</span>
            <div className="flex gap-1">
              <button disabled={page === 0} onClick={() => handlePageChange(0)}
                className="px-2 py-1.5 rounded border border-gray-200 text-xs hover:bg-gray-50 disabled:opacity-40">«</button>
              <button disabled={page === 0} onClick={() => handlePageChange(page - 1)}
                className="px-3 py-1.5 rounded border border-gray-200 text-xs hover:bg-gray-50 disabled:opacity-40">‹</button>
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
                className="px-3 py-1.5 rounded border border-gray-200 text-xs hover:bg-gray-50 disabled:opacity-40">›</button>
              <button disabled={page >= totalPages - 1} onClick={() => handlePageChange(totalPages - 1)}
                className="px-2 py-1.5 rounded border border-gray-200 text-xs hover:bg-gray-50 disabled:opacity-40">»</button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
