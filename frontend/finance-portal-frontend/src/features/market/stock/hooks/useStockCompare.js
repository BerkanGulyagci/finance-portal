import { useState, useEffect, useMemo } from 'react';
import { useSearchParams } from 'react-router-dom';
import { getStockChart, getStockMidasDetail, getAllStocks, getMarketPriceHistory, getFundCompareHistory } from '../../../../api/marketApi';
import { STOCK_CHART_RANGES } from '../utils/stockChartRanges';
import { INDEX_SHORTCUTS, COLORS, MAX_STOCKS, rebaseToCommonStart, interpolateSeries, toGenericRange } from '../utils/stockCompareUtils';

const RANGES = STOCK_CHART_RANGES;

/**
 * StockComparePage'in TÜM durum + veri-çekme mantığı: state, effect'ler (hisse listesi yükleme,
 * ?add= ön-seçim), ekle/kaldır handler'ları ve "karşılaştır" akışı (hepsi-hisse zengin yol vs karışık
 * normalize yol). Sayfayı ince bir görünüm katmanına indirir. Davranış StockComparePage'deki
 * orijinaliyle BİREBİR aynıdır — saf mantık taşıma; tek satır hesap/koşul değişmedi.
 */
export function useStockCompare() {
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
          // Fonlarda KISA aralık (≤1Y) → Rasyonet fon-kartından (TEK hızlı çağrı) — TEFAS-aralık yolu
          // pencere-pencere throttle'lı olduğu için yavaştı. 5Y/Tüm → generic (TEFAS, tam geçmiş):
          // Rasyonet kartı ~1 yıl tuttuğundan uzun aralıkta fonun geçmişini kısaltıyordu. Diğer türler: generic.
          const fundShort = d.assetType === 'FUND' && gr !== '5Y' && gr !== 'ALL';
          const res = fundShort
            ? await getFundCompareHistory(d.symbol, gr)
            : await getMarketPriceHistory(d.assetType, d.symbol, gr);
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
      // 1Y/5Y/Tüm gibi çok-yıllı aralıklarda yılı göster (x-ekseni + hover'da "28 Tem 25" —
      // yoksa çok-yıllı grafikte "28 Tem" hangi yıl belirsiz kalıyordu).
      else {
        const longSpan = rangeObj.range === '1y' || rangeObj.range === '5y' || rangeObj.range === 'max';
        label = d.toLocaleDateString('tr-TR', { day: 'numeric', month: 'short', year: longSpan ? '2-digit' : undefined });
      }
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

  return {
    // state + setters
    allStocks, stocksLoading, selectedSymbols, extraItems, searchOpen, setSearchOpen,
    chartSeriesDefs, searchQuery, setSearchQuery, showDropdown, setShowDropdown,
    rangeIdx, chartData, rawPrices, midasDetails, chartLoading, detailsLoading,
    compared, bist100Prices, investment, setInvestment,
    // türetilenler
    activeRange, filteredStocks, totalCount, mixedMode,
    // handler'lar
    addSymbol, removeSymbol, addExtra, removeExtra, handleCompare, handleRangeChange,
  };
}
