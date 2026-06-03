import EChartsCompareChart from './components/EChartsCompareChart';
import PerformanceMetricsTable from './components/PerformanceMetricsTable';
import { useState, useEffect, useMemo } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { ArrowLeft, X, Plus, BarChart2, TrendingUp, TrendingDown } from 'lucide-react';
import { getStockChart, getStockMidasDetail, getAllStocks, getMarketPriceHistory } from '../../../api/marketApi';
import { STOCK_CHART_RANGES } from './utils/stockChartRanges';
import { useTranslation } from '../../../context/LanguageContext';
import InstrumentSearchModal from '../../../components/instrument/InstrumentSearchModal';
import {
  ASSET_LABELS, BENCHMARK_SHORTCUTS, INDEX_SHORTCUTS, COLORS, MAX_STOCKS,
  rebaseToCommonStart, interpolateSeries, toGenericRange, calcMetrics,
} from './utils/stockCompareUtils';

const RANGES = STOCK_CHART_RANGES;

// ── Yükleniyor göstergesi ─────────────────────────────────────────────────────
function BounceDots({ size = 'md' }) {
  const sz = size === 'sm' ? 'w-1.5 h-1.5' : 'w-2 h-2';
  return (
    <div className="flex gap-1.5 items-center justify-center">
      <div className={`${sz} bg-[#093eaa] rounded-full animate-bounce`} />
      <div className={`${sz} bg-[#093eaa]/60 rounded-full animate-bounce [animation-delay:100ms]`} />
      <div className={`${sz} bg-[#093eaa]/30 rounded-full animate-bounce [animation-delay:200ms]`} />
    </div>
  );
}

// ── ECharts Karşılaştırma Grafiği ─────────────────────────────────────────────
export default function StockComparePage() {
  const { t } = useTranslation();
  const [searchParams] = useSearchParams();
  const [allStocks, setAllStocks] = useState([]);
  const [stocksLoading, setStocksLoading] = useState(true);

  const [selectedSymbols, setSelectedSymbols] = useState([]);
  const [extraItems, setExtraItems] = useState([]); // [{assetType, symbol, name, key}] — hisse-dışı kıyaslar
  const [searchOpen, setSearchOpen] = useState(false);
  const [chartSeriesDefs, setChartSeriesDefs] = useState([]); // [{key, name, color}]
  const [searchQuery, setSearchQuery] = useState('');
  const [showDropdown, setShowDropdown] = useState(false);

  const [rangeIdx, setRangeIdx] = useState(2); // default 1A
  const [chartData, setChartData] = useState([]);
  const [rawPrices, setRawPrices] = useState({}); // { symbol: number[] }
  const [midasDetails, setMidasDetails] = useState({}); // { symbol: MidasDetail }
  const [chartLoading, setChartLoading] = useState(false);
  const [detailsLoading, setDetailsLoading] = useState(false);
  const [compared, setCompared] = useState(false);
  const [bist100Prices, setBist100Prices] = useState([]);
  const [investment, setInvestment] = useState('1000');

  const activeRange = RANGES[rangeIdx];

  // ── Tüm hisseleri yükle ───────────────────────────────────────────────────
  useEffect(() => {
    setStocksLoading(true);
    getAllStocks()
      .then(data => setAllStocks(data ?? []))
      .catch(() => setAllStocks([]))
      .finally(() => setStocksLoading(false));
  }, []);

  // ── Detay sayfasından gelen ?add= ile ön-seçim ────────────────────────────
  // Biçim: "SEMBOL" (hisse, geriye uyumlu) ya da "TÜR|SEMBOL" (+ ?name=Görünen Ad).
  useEffect(() => {
    const add = searchParams.get('add');
    if (!add) return;
    const name = searchParams.get('name') || undefined;
    if (add.includes('|')) {
      const [type, sym] = add.split('|');
      if (!type || !sym) return;
      if (type.toUpperCase() === 'STOCK') {
        setSelectedSymbols(prev => prev.includes(sym) || prev.length >= MAX_STOCKS ? prev : [...prev, sym]);
      } else {
        const key = `${type}|${sym}`;
        setExtraItems(prev => prev.some(e => e.key === key) ? prev : [...prev, { assetType: type, symbol: sym, name: name || sym, key }]);
      }
    } else {
      setSelectedSymbols(prev => prev.includes(add) || prev.length >= MAX_STOCKS ? prev : [...prev, add]);
    }
  }, [searchParams]);

  // ── Arama filtresi ────────────────────────────────────────────────────────
  const filteredStocks = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    return allStocks
      .filter(s => !selectedSymbols.includes(s.symbol))
      .filter(s =>
        !q ||
        s.symbol?.toLowerCase().includes(q) ||
        s.name?.toLowerCase().includes(q)
      )
      .slice(0, 20);
  }, [allStocks, selectedSymbols, searchQuery]);

  // ── Hisse ekle / kaldır ───────────────────────────────────────────────────
  const totalCount = selectedSymbols.length + extraItems.length;
  const mixedMode = extraItems.length > 0;

  function addSymbol(symbol) {
    if (selectedSymbols.includes(symbol)) return;
    if (totalCount >= MAX_STOCKS) return;
    setSelectedSymbols(prev => [...prev, symbol]);
    setSearchQuery('');
    setShowDropdown(false);
    setCompared(false);
  }
  function removeSymbol(symbol) {
    setSelectedSymbols(prev => prev.filter(s => s !== symbol));
    setRawPrices(prev => { const n = { ...prev }; delete n[symbol]; return n; });
    setMidasDetails(prev => { const n = { ...prev }; delete n[symbol]; return n; });
    setCompared(false);
  }

  // ── Hisse-dışı enstrüman ekle/kaldır (cross-kıyas) ────────────────────────
  function addExtra(sel) {
    if (!sel?.assetType || !sel?.symbol) return;
    if (sel.assetType === 'STOCK') { addSymbol(sel.symbol); setSearchOpen(false); return; }
    const key = `${sel.assetType}|${sel.symbol}`;
    if (extraItems.some(e => e.key === key) || totalCount >= MAX_STOCKS) return;
    setExtraItems(prev => [...prev, { assetType: sel.assetType, symbol: sel.symbol, name: sel.name || sel.symbol, key }]);
    setSearchOpen(false);
    setCompared(false);
  }
  function removeExtra(key) {
    setExtraItems(prev => prev.filter(e => e.key !== key));
    setCompared(false);
  }

  /** Tüm seçili öğeler (hisse + ekstra), renk atanmış seri tanımları. */
  function buildAllDefs() {
    const stockDefs = selectedSymbols.map(sym => ({
      key: sym,
      assetType: 'STOCK',
      symbol: sym,
      label: INDEX_SHORTCUTS.find(x => x.symbol === sym)?.label
        ?? sym.replace('.IS', '').replace('.is', '').toUpperCase(),
    }));
    const extraDefs = extraItems.map(e => ({
      key: e.key, assetType: e.assetType, symbol: e.symbol, label: e.name || e.symbol,
    }));
    return [...stockDefs, ...extraDefs].map((d, i) => ({ ...d, color: COLORS[i % COLORS.length] }));
  }

  // ── Karşılaştır (birleşik: hepsi hisse → detaylı; karışık → sadece grafik) ──
  async function runCompare(rangeObj) {
    const defs = buildAllDefs();
    if (defs.length < 2) return;
    const mixed = extraItems.length > 0;
    setChartLoading(true);
    setCompared(false);

    if (mixed) {
      // Tüm öğeleri genel endpoint ile (günlük TL), tarih bazında hizala
      setDetailsLoading(false);
      const gr = toGenericRange(rangeObj.range);
      const results = await Promise.all(defs.map(async d => {
        try {
          const res = await getMarketPriceHistory(d.assetType, d.symbol, gr);
          return { key: d.key, timestamps: res?.timestamps ?? [], prices: res?.closePrices ?? [] };
        } catch {
          return { key: d.key, timestamps: [], prices: [] };
        }
      }));
      const seriesMap = {};
      results.forEach(({ key, timestamps, prices }) => {
        if (!timestamps.length || !prices.length) return;
        const first = parseFloat(prices[0]);
        if (!first) return;
        timestamps.forEach((ts, i) => {
          const price = parseFloat(prices[i]);
          if (!Number.isFinite(price)) return;
          const dayKey = new Date(ts * 1000).toISOString().slice(0, 10);
          if (!seriesMap[dayKey]) seriesMap[dayKey] = { dayKey };
          seriesMap[dayKey][key] = parseFloat(((price - first) / first * 100).toFixed(3));
          seriesMap[dayKey][`__price_${key}`] = price;
        });
      });
      const sorted = Object.values(seriesMap).sort((a, b) => a.dayKey.localeCompare(b.dayKey));
      let formatted = sorted.map(row => ({
        ...row,
        label: new Date(row.dayKey + 'T00:00:00').toLocaleDateString('tr-TR', { day: 'numeric', month: 'short', year: '2-digit' }),
      }));
      // Adil kıyas: tümünü ortak başlangıç tarihinden %0'a sabitle
      formatted = rebaseToCommonStart(formatted, defs.map(d => d.key));
      // Aylık göstergeler (enflasyon/mevduat) bilinen noktaları arası DOĞRUSAL bağlanır → ilk günden
      // sürekli yükselir (basamak değil), günlük BIST'e karşı adil. Son noktadan sonrası ileri-doldur.
      formatted = interpolateSeries(formatted, defs.map(d => d.key));
      setChartData(formatted);
      setChartSeriesDefs(defs.map(d => ({ key: d.key, name: d.label, color: d.color })));
      setChartLoading(false);
      setCompared(true);
      return;
    }

    // Hepsi hisse: mevcut zengin akış (Midas + BIST100 + ts hizalı)
    setDetailsLoading(true);
    const [chartResults, midasResults, bist100Result] = await Promise.all([
      Promise.all(selectedSymbols.map(async sym => {
        try { const res = await getStockChart(sym, rangeObj.range, rangeObj.interval); return { sym, timestamps: res?.timestamps ?? [], prices: res?.closePrices ?? [] }; }
        catch { return { sym, timestamps: [], prices: [] }; }
      })),
      Promise.all(selectedSymbols.map(async sym => {
        try { const detail = await getStockMidasDetail(sym); return { sym, detail }; }
        catch { return { sym, detail: null }; }
      })),
      getStockChart('XU100.IS', rangeObj.range, rangeObj.interval).catch(() => null),
    ]);

    setBist100Prices(bist100Result?.closePrices ?? []);
    const newMidas = {};
    midasResults.forEach(({ sym, detail }) => { newMidas[sym] = detail; });
    setMidasDetails(newMidas);
    setDetailsLoading(false);
    const newRaw = {};
    chartResults.forEach(({ sym, prices }) => { newRaw[sym] = prices; });
    setRawPrices(newRaw);

    const seriesMap = {};
    chartResults.forEach(({ sym, timestamps, prices }) => {
      if (!timestamps.length || !prices.length) return;
      const firstPrice = parseFloat(prices[0]);
      if (!firstPrice || firstPrice === 0) return;
      timestamps.forEach((ts, i) => {
        const price = parseFloat(prices[i]);
        if (price == null || isNaN(price)) return;
        if (!seriesMap[ts]) seriesMap[ts] = { ts };
        seriesMap[ts][sym] = parseFloat(((price - firstPrice) / firstPrice * 100).toFixed(3));
        seriesMap[ts][`__price_${sym}`] = price;
      });
    });
    const sorted = Object.values(seriesMap).sort((a, b) => a.ts - b.ts);
    let formatted = sorted.map(row => {
      const d = new Date(row.ts * 1000);
      let label;
      if (rangeObj.range === '1d') label = d.toLocaleTimeString('tr-TR', { hour: '2-digit', minute: '2-digit' });
      else if (rangeObj.range === '5d') label = d.toLocaleDateString('tr-TR', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' });
      else label = d.toLocaleDateString('tr-TR', { day: 'numeric', month: 'short', year: rangeObj.range === '5y' ? '2-digit' : undefined });
      return { ...row, label };
    });
    // Adil kıyas: farklı geçmişli hisselerde tümünü ortak başlangıçtan %0'a sabitle
    formatted = rebaseToCommonStart(formatted, selectedSymbols);
    setChartData(formatted);
    setChartSeriesDefs(selectedSymbols.map((sym, i) => ({
      key: sym,
      name: INDEX_SHORTCUTS.find(x => x.symbol === sym)?.label ?? sym.replace('.IS', '').toUpperCase(),
      color: COLORS[i % COLORS.length],
    })));
    setChartLoading(false);
    setCompared(true);
  }

  function handleCompare() {
    if (totalCount < 2) return;
    runCompare(activeRange);
  }

  // ── Aralık değişince otomatik yeniden karşılaştır ────────────────────────
  function handleRangeChange(idx) {
    setRangeIdx(idx);
    if (compared && totalCount >= 2) {
      runCompare(RANGES[idx]);
    }
  }

  // ── Tooltip için fiyat verisi ─────────────────────────────────────────────
  function buildTooltipDetails(payload) {
    const details = {};
    if (!payload) return details;
    payload.forEach(p => {
      const priceKey = `__price_${p.dataKey}`;
      if (p.payload?.[priceKey] != null) {
        details[p.dataKey] = { priceAtPoint: p.payload[priceKey] };
      }
    });
    return details;
  }

  // ── Tablo metrikleri ──────────────────────────────────────────────────────
  const TABLE_ROWS = [
    { label: 'Hisse Senedi Fiyatı',  key: 'currentPrice' },
    { label: 'Sermaye',              key: 'capital' },
    { label: 'Piyasa Değeri (TL)',   key: 'marketCap' },
    { label: 'F/K',                  key: 'peRatio' },
    { label: 'Hacim (mTL)',          key: 'dailyVolume' },
    { label: 'Net Kâr',              key: 'netProfit' },
    { label: 'Günlük Değişim (%)',   key: 'dailyChangePercent' },
    { label: 'Haftalık En Yüksek',   key: 'weeklyHigh' },
    { label: 'Haftalık En Düşük',    key: 'weeklyLow' },
    { label: 'Aylık En Yüksek',      key: 'monthlyHigh' },
    { label: 'Aylık En Düşük',       key: 'monthlyLow' },
  ];

  function getCellValue(symbol, key) {
    const d = midasDetails[symbol];
    if (!d) return '-';
    const val = d[key];
    if (val == null || val === '') return '-';
    return val;
  }

  function getCellStyle(key, value) {
    if (key === 'dailyChangePercent' && value !== '-') {
      const str = String(value);
      if (str.startsWith('-')) return 'text-rose-600 font-semibold';
      if (str !== '0' && str !== '0.00' && str !== '%0') return 'text-emerald-600 font-semibold';
    }
    return 'text-gray-800';
  }

  // ── Render ────────────────────────────────────────────────────────────────
  return (
    <div className="max-w-5xl mx-auto space-y-6">

      {searchOpen && (
        <InstrumentSearchModal onSelect={addExtra} onClose={() => setSearchOpen(false)} allowIndices />
      )}

      {/* ── Başlık ── */}
      <div className="flex items-center gap-3">
        <Link to="/market/stocks" className="text-gray-400 hover:text-[#093eaa] transition-colors">
          <ArrowLeft className="w-5 h-5" />
        </Link>
        <div>
          <h1 className="text-2xl font-bold text-gray-900">{t('Varlık Karşılaştırma')}</h1>
          <p className="text-xs text-gray-400 mt-0.5">
            {t('Hisse, kripto, döviz, altın, fon, enflasyon… her şeyi yan yana karşılaştır — en fazla')} {MAX_STOCKS} {t('öğe')}
          </p>
        </div>
      </div>

      {/* ── Hisse Seçici ── */}
      <div className="bg-white rounded-lg border border-gray-200 shadow-sm p-3 sm:p-5">
        <p className="text-xs font-bold text-gray-500 uppercase tracking-wider mb-3">Hisse Ekle</p>

        {/* Hızlı ekle: endeksler + faiz/enflasyon benchmark'ları */}
        <div className="flex flex-wrap gap-2 mb-3 items-center">
          <span className="text-xs text-gray-400 self-center">{t('Hızlı ekle:')}</span>
          {INDEX_SHORTCUTS.map(idx => {
            const isSelected = selectedSymbols.includes(idx.symbol);
            return (
              <button
                key={idx.symbol}
                onClick={() => isSelected ? removeSymbol(idx.symbol) : addSymbol(idx.symbol)}
                className={`inline-flex items-center gap-1.5 px-3 py-1 rounded-md text-xs font-bold transition-all border ${
                  isSelected
                    ? 'bg-[#093eaa] text-white border-[#093eaa]'
                    : 'bg-gray-50 text-gray-600 border-gray-200 hover:border-[#093eaa] hover:text-[#093eaa]'
                }`}
              >
                {idx.label}
                {isSelected && <X className="w-3 h-3 ml-0.5" />}
              </button>
            );
          })}
          <span className="w-px h-4 bg-gray-200 mx-0.5" />
          {BENCHMARK_SHORTCUTS.map(b => {
            const isSelected = extraItems.some(e => e.key === b.key);
            return (
              <button
                key={b.key}
                onClick={() => isSelected ? removeExtra(b.key) : addExtra({ assetType: b.assetType, symbol: b.symbol, name: b.label })}
                className={`inline-flex items-center gap-1.5 px-3 py-1 rounded-md text-xs font-bold transition-all border ${
                  isSelected
                    ? 'bg-[#7c3aed] text-white border-[#7c3aed]'
                    : 'bg-gray-50 text-gray-600 border-gray-200 hover:border-[#7c3aed] hover:text-[#7c3aed]'
                }`}
              >
                {b.label}
                {isSelected && <X className="w-3 h-3 ml-0.5" />}
              </button>
            );
          })}
        </div>

        {/* Seçili hisseler (chip'ler) */}
        <div className="flex flex-wrap gap-2 mb-4">
          {selectedSymbols.map((sym, idx) => {
            const indexShortcut = INDEX_SHORTCUTS.find(i => i.symbol === sym);
            const displayLabel = indexShortcut
              ? indexShortcut.label
              : sym.replace('.IS', '').replace('.is', '').toUpperCase();
            return (
              <span
                key={sym}
                className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md text-sm font-semibold text-white"
                style={{ background: COLORS[idx % COLORS.length] }}
              >
                {displayLabel}
                <button
                  onClick={() => removeSymbol(sym)}
                  className="ml-0.5 p-1.5 -m-1.5 sm:p-0 sm:m-0 sm:ml-0.5 hover:opacity-70 transition-opacity"
                  aria-label={`${sym} ${t('kaldır')}`}
                >
                  <X className="w-3.5 h-3.5" />
                </button>
              </span>
            );
          })}

          {/* Ekstra (hisse-dışı) öğeler */}
          {extraItems.map((it, ei) => {
            const globalIdx = selectedSymbols.length + ei;
            const typeLabel = ASSET_LABELS[it.assetType] ? t(ASSET_LABELS[it.assetType]) : it.assetType;
            return (
              <span
                key={it.key}
                className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md text-sm font-semibold text-white"
                style={{ background: COLORS[globalIdx % COLORS.length] }}
              >
                {it.name || it.symbol}
                <span className="opacity-70 text-xs">· {typeLabel}</span>
                <button
                  onClick={() => removeExtra(it.key)}
                  className="ml-0.5 p-1.5 -m-1.5 sm:p-0 sm:m-0 sm:ml-0.5 hover:opacity-70 transition-opacity"
                  aria-label={`${it.symbol} ${t('kaldır')}`}
                >
                  <X className="w-3.5 h-3.5" />
                </button>
              </span>
            );
          })}

          {/* Tek "Ekle" — herhangi bir enstrüman (hisse, kripto, altın, döviz, fon…) */}
          {totalCount < MAX_STOCKS && (
            <button
              onClick={() => setSearchOpen(true)}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md border-2 border-dashed border-gray-300 text-sm text-gray-500 hover:border-[#093eaa] hover:text-[#093eaa] transition-colors"
            >
              <Plus className="w-3.5 h-3.5" /> {t('Ekle')}
            </button>
          )}
        </div>

        {/* Aralık seçici + Karşılaştır butonu */}
        <div className="flex flex-col sm:flex-row sm:items-center gap-3 sm:flex-wrap">
          <div className="inline-flex items-center gap-0.5 rounded-lg border border-gray-200 bg-gray-50 p-0.5">
            {RANGES.map((r, i) => (
              <button
                key={r.label}
                onClick={() => handleRangeChange(i)}
                className={`px-3 py-1 rounded-md text-xs font-semibold transition-all ${
                  i === rangeIdx
                    ? 'bg-white text-[#093eaa] shadow-sm'
                    : 'text-gray-500 hover:text-gray-800'
                }`}
              >
                {r.label}
              </button>
            ))}
          </div>

          <button
            onClick={handleCompare}
            disabled={totalCount < 2 || chartLoading}
            className="sm:ml-auto px-5 py-2 bg-[#093eaa] text-white rounded-lg text-sm font-bold hover:bg-[#0730a0] transition-all disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2"
          >
            <BarChart2 className="w-4 h-4" />
            {chartLoading ? t('Yükleniyor...') : t('Karşılaştır')}
          </button>
        </div>

        {totalCount < 2 && totalCount > 0 && (
          <p className="text-xs text-amber-500 mt-2">
            {t('Karşılaştırma için en az 2 öğe seçmelisin.')}
          </p>
        )}
        {mixedMode && (
          <p className="text-xs text-gray-400 mt-2">
            {t('Farklı türde varlık eklendi — yalnızca normalize % grafik gösterilir (detaylı metrik tabloları yalnız hisse-hisse kıyasında çıkar).')}
          </p>
        )}
      </div>

      {/* ── Grafik Yükleniyor ── */}
      {chartLoading && (
        <div className="bg-white rounded-lg border border-gray-200 shadow-sm p-8 sm:p-16 flex items-center justify-center">
          <BounceDots />
        </div>
      )}

      {/* ── Normalize Grafik ── */}
      {compared && !chartLoading && chartData.length > 0 && (
        <div className="bg-white rounded-lg border border-gray-200 shadow-sm p-3 sm:p-6">
          <div className="mb-4">
            <h2 className="font-bold text-gray-900">{t('Göreceli Performans (%)')}</h2>
            <p className="text-xs text-gray-400 mt-0.5">
              {t('Hepsi ortak başlangıç tarihinden 0%\'dan başlar (en geç başlayan varlığın tarihi) — adil kümülatif % kıyas · Scroll ile zoom')}
            </p>
            {extraItems.some(e => ['TUFE', 'USCPI_TRY', 'DEPOSIT'].includes(e.symbol)) && (
              <p className="text-[11px] text-amber-600 mt-1">
                {t('Not: Enflasyon (TÜFE) son yayımlanan aya kadardır (~1–2 ay gecikmeli); kısa aralıklarda (3A vb.) son ayları içermeyip düz görünebilir.')}
              </p>
            )}
          </div>
          <EChartsCompareChart chartData={chartData} seriesDefs={chartSeriesDefs} />

          {/* TÜFE / ABD Enflasyonu seçiliyken: ne anlama geldiklerinin kısa açıklaması */}
          {(extraItems.some(e => e.symbol === 'TUFE') || extraItems.some(e => e.symbol === 'USCPI_TRY')) && (
            <div className="mt-4 pt-3 border-t border-gray-100 space-y-2 text-[11px] text-gray-500 leading-relaxed">
              {extraItems.some(e => e.symbol === 'TUFE') && (
                <p>
                  <span className="font-bold text-gray-700">{t('Enflasyon (TÜFE)')}:</span>{' '}
                  {t('bir yatırım değil, ölçü çizgisidir — TL fiyatların ne kadar arttığını gösterir. Bir varlık bu çizginin ÜSTÜNDEyse paran fiyatlardan hızlı büyümüştür (reel kazanç); ALTINDAysa sayı artsa bile alım gücün düşmüştür.')}
                </p>
              )}
              {extraItems.some(e => e.symbol === 'USCPI_TRY') && (
                <p>
                  <span className="font-bold text-gray-700">{t('ABD Enflasyonu')}:</span>{' '}
                  {t('ABD mallarının TL fiyatıdır (ABD enflasyonu × USD/TRY) — "dolar alım gücünü korudun mu" ölçüsü. Çizginin yüksekliği ABD enflasyonundan değil, ağırlıkla TL\'nin dolara karşı değer kaybından (USD/TRY artışından) gelir.')}
                </p>
              )}
            </div>
          )}
        </div>
      )}

      {/* ── Performans Metrikleri Tablosu (yalnız hisse-hisse kıyasında) ── */}
      {compared && !chartLoading && !mixedMode && Object.keys(rawPrices).length > 0 && (
        <PerformanceMetricsTable
          selectedSymbols={selectedSymbols}
          rawPrices={rawPrices}
          bist100Prices={bist100Prices}
          t={t}
        />
      )}

      {/* ── Finansal Karşılaştırma Tablosu — sadece gerçek hisseler için ── */}
      {compared && !chartLoading && !mixedMode && (() => {
        // Endeks sembollerini filtrele
        const stockSymbols = selectedSymbols.filter(sym =>
          !INDEX_SHORTCUTS.some(idx => idx.symbol === sym)
        );
        if (stockSymbols.length === 0) return null;
        return (
        <div className="bg-white rounded-lg border border-gray-200 shadow-sm overflow-hidden">
          <div className="px-6 py-4 border-b border-gray-100">
            <h2 className="font-bold text-gray-900">{t('Finansal Karşılaştırma')}</h2>
            <p className="text-xs text-gray-400 mt-0.5">{t('Kaynak: Midas · Veriler 15 dk gecikmeli')}</p>
          </div>
          {detailsLoading ? (
            <div className="p-12 flex items-center justify-center"><BounceDots /></div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full min-w-[560px]">
                <thead className="bg-gray-50">
                  <tr>
                    <th className="text-left px-5 py-3 text-xs font-bold text-gray-500 uppercase tracking-wider border-b border-gray-200 w-44">{t('Metrik')}</th>
                    {stockSymbols.map((sym, idx) => {
                      const globalIdx = selectedSymbols.indexOf(sym);
                      return (
                      <th key={sym} className="text-right px-5 py-3 text-xs font-bold uppercase tracking-wider border-b border-gray-200 whitespace-nowrap" style={{ color: COLORS[globalIdx % COLORS.length] }}>
                        <div className="flex items-center justify-end gap-1.5">
                          <span className="w-2.5 h-2.5 rounded-full flex-shrink-0" style={{ background: COLORS[globalIdx % COLORS.length] }} />
                          {sym.replace('.IS', '').toUpperCase()}
                        </div>
                        {midasDetails[sym]?.name && <p className="text-gray-400 font-normal normal-case text-xs mt-0.5 truncate max-w-[140px] ml-auto">{midasDetails[sym].name}</p>}
                      </th>
                      );
                    })}
                  </tr>
                </thead>
                <tbody>
                  {TABLE_ROWS.filter(row => {
                    return stockSymbols.some(sym => {
                      const val = getCellValue(sym, row.key);
                      return val !== '-' && val != null && val !== '';
                    });
                  }).map((row, rowIdx) => (
                    <tr key={row.key} className={`border-t border-gray-100 hover:bg-gray-50 ${rowIdx % 2 === 0 ? '' : 'bg-gray-50/40'}`}>
                      <td className="px-5 py-3 text-sm text-gray-500 font-medium whitespace-nowrap">{t(row.label)}</td>
                      {stockSymbols.map(sym => {
                        const val = getCellValue(sym, row.key);
                        return <td key={sym} className={`px-5 py-3 text-sm text-right font-mono ${getCellStyle(row.key, val)}`}>{val}</td>;
                      })}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
        );
      })()}

      {/* ── TL Yatırım Simülasyonu — sadece gerçek hisseler için ── */}
      {compared && !chartLoading && !mixedMode && (() => {
        const stockSymbols = selectedSymbols.filter(sym =>
          !INDEX_SHORTCUTS.some(idx => idx.symbol === sym)
        );
        if (stockSymbols.length === 0 || Object.keys(rawPrices).length === 0) return null;
        return (
        <div className="bg-white rounded-lg border border-gray-200 shadow-sm overflow-hidden">
          <div className="px-6 py-4 border-b border-gray-100 flex items-center gap-4 flex-wrap">
            <h2 className="font-bold text-gray-900">{t('TL Yatırım Simülasyonu')}</h2>
            <div className="flex items-center gap-2 ml-auto">
              <span className="text-sm text-gray-500">{t('Yatırım tutarı:')}</span>
              <input type="number" value={investment} onChange={e => setInvestment(e.target.value)}
                className="w-32 px-3 py-1.5 border border-gray-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-[#093eaa]/30 text-right" placeholder="1000" />
              <span className="text-sm font-semibold text-gray-600">₺</span>
            </div>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full min-w-[600px]">
              <thead className="bg-gray-50">
                <tr>
                  {['Hisse', 'Başlangıç Fiyatı', 'Alınan Lot', 'Bitiş Fiyatı', 'Nihai Değer (₺)', 'Kâr/Zarar'].map(h => (
                    <th key={h} className="text-left px-5 py-3 text-xs font-bold text-gray-500 uppercase tracking-wider border-b border-gray-200 whitespace-nowrap">{t(h)}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {stockSymbols.map((sym) => {
                  const globalIdx = selectedSymbols.indexOf(sym);
                  const m = calcMetrics(rawPrices[sym]);
                  const inv = parseFloat(investment || 0);
                  const units = m && m.startPrice > 0 ? inv / m.startPrice : null;
                  const finalVal = units != null ? units * m.endPrice : null;
                  const profit = finalVal != null ? finalVal - inv : null;
                  const pos = (profit ?? 0) >= 0;
                  const fmt2 = v => v == null ? '-' : parseFloat(v).toLocaleString('tr-TR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
                  const fmt4 = v => v == null ? '-' : parseFloat(v).toLocaleString('tr-TR', { minimumFractionDigits: 2, maximumFractionDigits: 4 });
                  return (
                    <tr key={sym} className="border-t border-gray-100 hover:bg-gray-50">
                      <td className="px-5 py-3">
                        <div className="flex items-center gap-2">
                          <span className="w-3 h-3 rounded-full flex-shrink-0" style={{ background: COLORS[globalIdx % COLORS.length] }} />
                          <span className="font-bold text-sm text-gray-900">{sym.replace('.IS', '').toUpperCase()}</span>
                        </div>
                      </td>
                      <td className="px-5 py-3 text-sm font-mono text-gray-700">{m ? `₺${fmt4(m.startPrice)}` : '-'}</td>
                      <td className="px-5 py-3 text-sm font-mono text-gray-700">{units != null ? units.toLocaleString('tr-TR', { maximumFractionDigits: 2 }) : '-'}</td>
                      <td className="px-5 py-3 text-sm font-mono text-gray-700">{m ? `₺${fmt4(m.endPrice)}` : '-'}</td>
                      <td className="px-5 py-3 text-sm font-bold text-gray-900">{finalVal != null ? `₺${fmt2(finalVal)}` : '-'}</td>
                      <td className="px-5 py-3">
                        {profit != null ? (
                          <span className={`text-sm font-bold flex items-center gap-1 ${pos ? 'text-emerald-600' : 'text-rose-600'}`}>
                            {pos ? <TrendingUp className="w-3.5 h-3.5" /> : <TrendingDown className="w-3.5 h-3.5" />}
                            {pos ? '+' : ''}₺{fmt2(profit)}
                          </span>
                        ) : '-'}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
          <div className="px-5 py-2 bg-gray-50 border-t border-gray-100 text-xs text-gray-400">
            {t('* Simülasyon dönem başı ve sonu fiyatları kullanılarak hesaplanmıştır. Komisyon ve vergiler dahil değildir. Gösterge niteliğindedir.')}
          </div>
        </div>
        );
      })()}
      {!compared && !chartLoading && (
        <div className="bg-white rounded-lg border border-gray-200 shadow-sm p-6 sm:p-14 text-center">
          <BarChart2 className="w-12 h-12 text-gray-200 mx-auto mb-3" />
          <p className="text-gray-500 font-semibold">
            {selectedSymbols.length === 0
              ? t('Karşılaştırmak istediğin hisseleri seç')
              : selectedSymbols.length === 1
              ? 'En az bir hisse daha ekle'
              : t('"Karşılaştır" butonuna tıkla')}
          </p>
          <p className="text-gray-400 text-sm mt-1">
            {t('En fazla')} {MAX_STOCKS} {t('BIST hissesini yan yana karşılaştırabilirsin')}
          </p>
        </div>
      )}
    </div>
  );
}
