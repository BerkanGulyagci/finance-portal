import { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import { Link } from 'react-router-dom';
import { ArrowLeft, X, Plus, BarChart2, Search, TrendingUp, TrendingDown } from 'lucide-react';
import { getStockChart, getStockMidasDetail, getAllStocks } from '../../../api/marketApi';
import { STOCK_CHART_RANGES } from './stockChartRanges';

// ── Sabitler ──────────────────────────────────────────────────────────────────
const COLORS = ['#093eaa', '#f97316', '#8b5cf6', '#10b981'];
const MAX_STOCKS = 4;

const RANGES = STOCK_CHART_RANGES;

// ── Performans Metrikleri Hesaplama ──────────────────────────────────────────
function calcMetrics(prices) {
  if (!prices || prices.length < 2) return null;
  const nums = prices.map(p => parseFloat(p)).filter(p => !isNaN(p) && p > 0);
  if (nums.length < 2) return null;

  const startPrice = nums[0];
  const endPrice   = nums[nums.length - 1];
  const maxPrice   = Math.max(...nums);
  const minPrice   = Math.min(...nums);
  const periodReturn = ((endPrice - startPrice) / startPrice) * 100;

  // Max Drawdown
  let peak = nums[0], maxDrawdown = 0;
  for (const p of nums) {
    if (p > peak) peak = p;
    const dd = (p - peak) / peak;
    if (dd < maxDrawdown) maxDrawdown = dd;
  }

  // Volatilite (günlük getiri std sapması)
  const returns = [];
  for (let i = 1; i < nums.length; i++) {
    if (nums[i - 1] > 0) returns.push((nums[i] - nums[i - 1]) / nums[i - 1] * 100);
  }
  const mean = returns.reduce((a, b) => a + b, 0) / (returns.length || 1);
  const variance = returns.reduce((a, b) => a + (b - mean) ** 2, 0) / (returns.length || 1);
  const volatility = Math.sqrt(variance);

  // RSI (14)
  let gains = 0, losses = 0;
  const period = Math.min(14, returns.length);
  for (let i = 0; i < period; i++) {
    if (returns[i] >= 0) gains += returns[i]; else losses -= returns[i];
  }
  let avgGain = gains / period, avgLoss = losses / period;
  for (let i = period; i < returns.length; i++) {
    avgGain = (avgGain * (period - 1) + Math.max(returns[i], 0)) / period;
    avgLoss = (avgLoss * (period - 1) + Math.max(-returns[i], 0)) / period;
  }
  const rsi = avgLoss === 0 ? 100 : 100 - (100 / (1 + avgGain / avgLoss));

  // MA hesaplama
  function ma(n) {
    if (nums.length < n) return null;
    return nums.slice(-n).reduce((a, b) => a + b, 0) / n;
  }
  const ma7  = ma(7);
  const ma25 = ma(25);
  const ma99 = ma(99);

  // Trend: çok sinyalli analiz
  const currentPrice = endPrice;
  const maGapPercent = (ma7 && ma25) ? ((ma7 - ma25) / ma25) * 100 : null;

  let trend;
  if (
    periodReturn > 5 &&
    ma7 && ma25 && ma7 > ma25 &&
    currentPrice > (ma7 ?? 0)
  ) {
    trend = 'Güçlü Yükselen';
  } else if (
    periodReturn < -5 &&
    ma7 && ma25 && ma7 < ma25 &&
    currentPrice < (ma7 ?? Infinity)
  ) {
    trend = 'Güçlü Düşen';
  } else if (
    periodReturn > 2 ||
    (ma7 && ma25 && ma7 > ma25 && currentPrice > (ma7 ?? 0))
  ) {
    trend = 'Yükselen';
  } else if (
    periodReturn < -2 ||
    (ma7 && ma25 && ma7 < ma25 && currentPrice < (ma7 ?? Infinity))
  ) {
    trend = 'Düşen';
  } else if (
    periodReturn >= -2 && periodReturn <= 2 &&
    maGapPercent != null && Math.abs(maGapPercent) < 1
  ) {
    trend = 'Yatay';
  } else {
    trend = 'Yatay';
  }

  // RSI uyarısı (trend kararına dahil değil, ayrı gösterilir)
  const rsiWarning = rsi >= 70 ? 'Aşırı Alım ⚠️' : rsi <= 30 ? 'Aşırı Satım ⚠️' : null;

  // Risk başına getiri (Sharpe benzeri)
  const riskAdjusted = volatility > 0 ? periodReturn / volatility : null;

  return { startPrice, endPrice, maxPrice, minPrice, periodReturn, drawdown: maxDrawdown * 100, volatility, rsi, rsiWarning, riskAdjusted, ma7, ma25, ma99, trend, lastPrice: endPrice };
}

// ── Endeks kısayolları ────────────────────────────────────────────────────────
const INDEX_SHORTCUTS = [
  { symbol: 'XU100.IS', label: 'BIST 100', badge: '📊' },
  { symbol: 'XU030.IS', label: 'BIST 30',  badge: '📈' },
];
function fmtNum(val, decimals = 2) {
  if (val == null || val === '' || val === '-') return '-';
  const n = parseFloat(String(val).replace(/[^\d.,-]/g, '').replace(',', '.'));
  if (isNaN(n)) return val;
  return n.toLocaleString('tr-TR', { minimumFractionDigits: decimals, maximumFractionDigits: decimals });
}

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
function EChartsCompareChart({ chartData, selectedSymbols }) {
  const chartRef = useRef(null);
  const instanceRef = useRef(null);

  useEffect(() => {
    if (!chartRef.current || !chartData.length) return;

    import('echarts').then(echarts => {
      // Mevcut instance'ı dispose et
      if (instanceRef.current) {
        instanceRef.current.dispose();
      }

      const chart = echarts.init(chartRef.current, null, { renderer: 'canvas' });
      instanceRef.current = chart;

      const labels = chartData.map(d => d.label);

      const series = selectedSymbols.map((sym, idx) => ({
        name: INDEX_SHORTCUTS.find(i => i.symbol === sym)?.label ?? sym.replace('.IS', '').replace('.is', '').toUpperCase(),
        type: 'line',
        data: chartData.map(d => {
          const pct = d[sym];
          const price = d[`__price_${sym}`];
          return { value: pct ?? null, price: price ?? null };
        }),
        smooth: false,
        symbol: 'none',
        lineStyle: { width: 2.5, color: COLORS[idx % COLORS.length] },
        itemStyle: { color: COLORS[idx % COLORS.length] },
        connectNulls: true,
      }));

      const option = {
        backgroundColor: '#ffffff',
        animation: false,
        grid: { top: 20, right: 60, bottom: 80, left: 70 },
        xAxis: {
          type: 'category',
          data: labels,
          axisLine: { lineStyle: { color: '#e5e7eb' } },
          axisTick: { show: false },
          axisLabel: {
            color: '#9ca3af',
            fontSize: 10,
            interval: Math.floor(labels.length / 6),
            rotate: 0,
          },
          splitLine: { show: false },
        },
        yAxis: {
          type: 'value',
          axisLine: { show: false },
          axisTick: { show: false },
          axisLabel: {
            color: '#9ca3af',
            fontSize: 10,
            formatter: v => `${v >= 0 ? '+' : ''}${v.toFixed(1)}%`,
          },
          splitLine: { lineStyle: { color: '#f0f0f0', type: 'dashed' } },
        },
        tooltip: {
          trigger: 'axis',
          axisPointer: {
            type: 'cross',
            crossStyle: { color: '#9ca3af', width: 1 },
            lineStyle: { color: '#9ca3af', width: 1, type: 'dashed' },
          },
          backgroundColor: '#ffffff',
          borderColor: '#e5e7eb',
          borderWidth: 1,
          borderRadius: 12,
          padding: [10, 14],
          textStyle: { color: '#374151', fontSize: 12 },
          formatter: params => {
            if (!params?.length) return '';
            let html = `<div style="font-size:11px;color:#6b7280;font-weight:600;margin-bottom:8px;padding-bottom:6px;border-bottom:1px solid #f0f0f0">${params[0].axisValue}</div>`;
            params.forEach(p => {
              if (p.value == null) return;
              const price = p.data?.price;
              const isPos = p.value >= 0;
              const pctColor = isPos ? '#10b981' : '#ef4444';
              html += `<div style="display:flex;align-items:center;gap:8px;margin-bottom:6px">
                <span style="display:inline-block;width:10px;height:10px;border-radius:50%;background:${p.color};flex-shrink:0"></span>
                <span style="font-weight:700;color:#374151;min-width:60px">${p.seriesName}:</span>
                ${price != null ? `<span style="color:#6b7280;font-family:monospace">₺${parseFloat(price).toLocaleString('tr-TR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</span>` : ''}
                <span style="font-weight:700;color:${pctColor};margin-left:auto">${isPos ? '+' : ''}${p.value.toFixed(2)}%</span>
              </div>`;
            });
            return html;
          },
        },
        legend: {
          bottom: 32,
          textStyle: { color: '#6b7280', fontSize: 12 },
          icon: 'circle',
          itemWidth: 10,
          itemHeight: 10,
        },
        dataZoom: [
          {
            type: 'inside',
            xAxisIndex: 0,
            start: 0,
            end: 100,
            zoomOnMouseWheel: true,
            moveOnMouseMove: true,
          },
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
            textStyle: { color: '#9ca3af', fontSize: 10 },
            showDetail: false,
          },
        ],
        series,
      };

      chart.setOption(option);

      // Responsive
      const ro = new ResizeObserver(() => chart.resize());
      ro.observe(chartRef.current);

      return () => { ro.disconnect(); };
    });

    return () => {
      if (instanceRef.current) {
        instanceRef.current.dispose();
        instanceRef.current = null;
      }
    };
  }, [chartData, selectedSymbols]);

  return <div ref={chartRef} style={{ width: '100%', height: '420px' }} />;
}

// ── Ana Sayfa ─────────────────────────────────────────────────────────────────
export default function StockComparePage() {
  const [allStocks, setAllStocks] = useState([]);
  const [stocksLoading, setStocksLoading] = useState(true);

  const [selectedSymbols, setSelectedSymbols] = useState([]);
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
  function addSymbol(symbol) {
    if (selectedSymbols.includes(symbol)) return;
    if (selectedSymbols.length >= MAX_STOCKS) return;
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

  // ── Karşılaştır ───────────────────────────────────────────────────────────
  const handleCompare = useCallback(async () => {
    if (selectedSymbols.length < 2) return;
    setChartLoading(true);
    setDetailsLoading(true);
    setCompared(false);

    // Grafik verisi + Midas detayları + BIST100 paralel çek
    const [chartResults, midasResults, bist100Result] = await Promise.all([
      Promise.all(
        selectedSymbols.map(async sym => {
          try {
            const res = await getStockChart(sym, activeRange.range, activeRange.interval);
            return { sym, timestamps: res?.timestamps ?? [], prices: res?.closePrices ?? [] };
          } catch {
            return { sym, timestamps: [], prices: [] };
          }
        })
      ),
      Promise.all(
        selectedSymbols.map(async sym => {
          try {
            const detail = await getStockMidasDetail(sym);
            return { sym, detail };
          } catch {
            return { sym, detail: null };
          }
        })
      ),
      getStockChart('XU100.IS', activeRange.range, activeRange.interval).catch(() => null),
    ]);

    // BIST100 fiyatlarını kaydet
    setBist100Prices(bist100Result?.closePrices ?? []);

    // Midas detaylarını kaydet
    const newMidas = {};
    midasResults.forEach(({ sym, detail }) => { newMidas[sym] = detail; });
    setMidasDetails(newMidas);
    setDetailsLoading(false);

    // Ham fiyatları kaydet (tablo için)
    const newRaw = {};
    chartResults.forEach(({ sym, prices }) => { newRaw[sym] = prices; });
    setRawPrices(newRaw);

    // Normalize edilmiş grafik verisi oluştur
    // Her hisse için başlangıç fiyatına göre % değişim hesapla
    const seriesMap = {};
    chartResults.forEach(({ sym, timestamps, prices }) => {
      if (!timestamps.length || !prices.length) return;
      const firstPrice = parseFloat(prices[0]);
      if (!firstPrice || firstPrice === 0) return;
      timestamps.forEach((ts, i) => {
        const price = parseFloat(prices[i]);
        if (price == null || isNaN(price)) return;
        const key = ts;
        if (!seriesMap[key]) seriesMap[key] = { ts };
        seriesMap[key][sym] = parseFloat(((price - firstPrice) / firstPrice * 100).toFixed(3));
        // Tooltip için gerçek fiyatı da sakla
        seriesMap[key][`__price_${sym}`] = price;
      });
    });

    // Tarihe göre sırala ve label ekle
    const sorted = Object.values(seriesMap).sort((a, b) => a.ts - b.ts);
    const formatted = sorted.map(row => {
      const d = new Date(row.ts * 1000);
      let label;
      if (activeRange.range === '1d') {
        label = d.toLocaleTimeString('tr-TR', { hour: '2-digit', minute: '2-digit' });
      } else if (activeRange.range === '5d') {
        label = d.toLocaleDateString('tr-TR', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' });
      } else {
        label = d.toLocaleDateString('tr-TR', { day: 'numeric', month: 'short', year: activeRange.range === '5y' ? '2-digit' : undefined });
      }
      return { ...row, label };
    });

    setChartData(formatted);
    setChartLoading(false);
    setCompared(true);
  }, [selectedSymbols, activeRange]);

  // ── Aralık değişince otomatik yeniden karşılaştır ────────────────────────
  function handleRangeChange(idx) {
    setRangeIdx(idx);
    // Eğer zaten karşılaştırma yapılmışsa otomatik yenile
    if (compared && selectedSymbols.length >= 2) {
      // rangeIdx state güncellemesi async olduğu için yeni range'i direkt geçiyoruz
      const newRange = RANGES[idx];
      setChartLoading(true);
      Promise.all([
        ...selectedSymbols.map(async sym => {
          try {
            const res = await getStockChart(sym, newRange.range, newRange.interval);
            return { sym, timestamps: res?.timestamps ?? [], prices: res?.closePrices ?? [] };
          } catch {
            return { sym, timestamps: [], prices: [] };
          }
        }),
        getStockChart('XU100.IS', newRange.range, newRange.interval).catch(() => null),
      ]).then(results => {
        const bist100Res = results[results.length - 1];
        const chartResults = results.slice(0, selectedSymbols.length);

        setBist100Prices(bist100Res?.closePrices ?? []);
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
        const formatted = sorted.map(row => {
          const d = new Date(row.ts * 1000);
          let label;
          if (newRange.range === '1d') {
            label = d.toLocaleTimeString('tr-TR', { hour: '2-digit', minute: '2-digit' });
          } else if (newRange.range === '5d') {
            label = d.toLocaleDateString('tr-TR', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' });
          } else {
            label = d.toLocaleDateString('tr-TR', { day: 'numeric', month: 'short', year: newRange.range === '5y' ? '2-digit' : undefined });
          }
          return { ...row, label };
        });

        setChartData(formatted);
        setChartLoading(false);
      });
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

      {/* ── Başlık ── */}
      <div className="flex items-center gap-3">
        <Link to="/market/stocks" className="text-gray-400 hover:text-[#093eaa] transition-colors">
          <ArrowLeft className="w-5 h-5" />
        </Link>
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Hisse Karşılaştırma</h1>
          <p className="text-xs text-gray-400 mt-0.5">
            BIST hisselerini yan yana karşılaştır — en fazla {MAX_STOCKS} hisse
          </p>
        </div>
      </div>

      {/* ── Hisse Seçici ── */}
      <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-5">
        <p className="text-xs font-bold text-gray-500 uppercase tracking-wider mb-3">Hisse Ekle</p>

        {/* Endeks kısayolları */}
        <div className="flex gap-2 mb-3">
          <span className="text-xs text-gray-400 self-center">Hızlı ekle:</span>
          {INDEX_SHORTCUTS.map(idx => {
            const isSelected = selectedSymbols.includes(idx.symbol);
            return (
              <button
                key={idx.symbol}
                onClick={() => isSelected ? removeSymbol(idx.symbol) : addSymbol(idx.symbol)}
                className={`inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-bold transition-all border ${
                  isSelected
                    ? 'bg-[#093eaa] text-white border-[#093eaa]'
                    : 'bg-gray-50 text-gray-600 border-gray-200 hover:border-[#093eaa] hover:text-[#093eaa]'
                }`}
              >
                {idx.badge} {idx.label}
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
              ? `${indexShortcut.badge} ${indexShortcut.label}`
              : sym.replace('.IS', '').replace('.is', '').toUpperCase();
            return (
              <span
                key={sym}
                className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-sm font-semibold text-white"
                style={{ background: COLORS[idx % COLORS.length] }}
              >
                {displayLabel}
                <button
                  onClick={() => removeSymbol(sym)}
                  className="ml-0.5 hover:opacity-70 transition-opacity"
                  aria-label={`${sym} kaldır`}
                >
                  <X className="w-3.5 h-3.5" />
                </button>
              </span>
            );
          })}

          {/* Ekle butonu */}
          {selectedSymbols.length < MAX_STOCKS && (
            <div className="relative">
              <button
                onClick={() => setShowDropdown(v => !v)}
                className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full border-2 border-dashed border-gray-300 text-sm text-gray-400 hover:border-[#093eaa] hover:text-[#093eaa] transition-colors"
              >
                <Plus className="w-3.5 h-3.5" />
                Ekle
              </button>

              {showDropdown && (
                <div className="absolute top-10 left-0 z-30 bg-white border border-gray-200 rounded-xl shadow-xl w-72 p-2">
                  {/* Arama */}
                  <div className="relative mb-2">
                    <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-gray-400" />
                    <input
                      autoFocus
                      value={searchQuery}
                      onChange={e => setSearchQuery(e.target.value)}
                      placeholder="Sembol veya şirket ara..."
                      className="w-full pl-8 pr-3 py-2 text-sm border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-[#093eaa]/30"
                    />
                  </div>

                  {/* Sonuçlar */}
                  <div className="max-h-52 overflow-y-auto space-y-0.5">
                    {stocksLoading ? (
                      <div className="py-4"><BounceDots size="sm" /></div>
                    ) : filteredStocks.length === 0 ? (
                      <p className="text-xs text-gray-400 text-center py-3">Sonuç bulunamadı</p>
                    ) : (
                      filteredStocks.map(s => (
                        <button
                          key={s.symbol}
                          onClick={() => addSymbol(s.symbol)}
                          className="w-full flex items-center justify-between gap-2 px-3 py-2 rounded-lg hover:bg-blue-50 text-sm text-left transition-colors"
                        >
                          <div>
                            <span className="font-bold text-[#093eaa]">
                              {s.symbol?.replace('.IS', '').replace('.is', '').toUpperCase()}
                            </span>
                            {s.name && (
                              <span className="text-gray-400 text-xs ml-2 truncate max-w-[140px] inline-block align-middle">
                                {s.name}
                              </span>
                            )}
                          </div>
                          {s.price != null && (
                            <span className="text-xs text-gray-500 shrink-0">₺{fmtNum(s.price)}</span>
                          )}
                        </button>
                      ))
                    )}
                  </div>

                  <button
                    onClick={() => { setShowDropdown(false); setSearchQuery(''); }}
                    className="w-full mt-2 text-xs text-gray-400 hover:text-gray-600 py-1 transition-colors"
                  >
                    Kapat
                  </button>
                </div>
              )}
            </div>
          )}
        </div>

        {/* Aralık seçici + Karşılaştır butonu */}
        <div className="flex items-center gap-3 flex-wrap">
          <div className="flex gap-1">
            {RANGES.map((r, i) => (
              <button
                key={r.label}
                onClick={() => handleRangeChange(i)}
                className={`px-3 py-1.5 rounded-lg text-xs font-bold transition-all ${
                  i === rangeIdx
                    ? 'bg-[#093eaa] text-white'
                    : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
                }`}
              >
                {r.label}
              </button>
            ))}
          </div>

          <button
            onClick={handleCompare}
            disabled={selectedSymbols.length < 2 || chartLoading}
            className="ml-auto px-5 py-2 bg-[#093eaa] text-white rounded-xl text-sm font-bold hover:bg-[#0730a0] transition-all disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-2"
          >
            <BarChart2 className="w-4 h-4" />
            {chartLoading ? 'Yükleniyor...' : 'Karşılaştır'}
          </button>
        </div>

        {selectedSymbols.length < 2 && selectedSymbols.length > 0 && (
          <p className="text-xs text-amber-500 mt-2">
            Karşılaştırma için en az 2 hisse seçmelisin.
          </p>
        )}
      </div>

      {/* ── Grafik Yükleniyor ── */}
      {chartLoading && (
        <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-16 flex items-center justify-center">
          <BounceDots />
        </div>
      )}

      {/* ── Normalize Grafik ── */}
      {compared && !chartLoading && chartData.length > 0 && (
        <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">
          <div className="mb-4">
            <h2 className="font-bold text-gray-900">Göreceli Performans (%)</h2>
            <p className="text-xs text-gray-400 mt-0.5">
              Tüm hisseler 0%'dan başlar — dönem başına göre yüzde değişim · Scroll ile zoom
            </p>
          </div>
          <EChartsCompareChart chartData={chartData} selectedSymbols={selectedSymbols} />
        </div>
      )}

      {/* ── Performans Metrikleri Tablosu ── */}
      {compared && !chartLoading && Object.keys(rawPrices).length > 0 && (() => {
        const bist100M = calcMetrics(bist100Prices);
        const metrics = {};
        selectedSymbols.forEach(sym => { metrics[sym] = calcMetrics(rawPrices[sym]); });
        const fmt2 = v => v == null ? '-' : parseFloat(v).toLocaleString('tr-TR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
        const fmt4 = v => v == null ? '-' : parseFloat(v).toLocaleString('tr-TR', { minimumFractionDigits: 2, maximumFractionDigits: 4 });

        const PERF_ROWS = [
          { label: 'Başlangıç Fiyatı', render: m => m ? `₺${fmt4(m.startPrice)}` : '-' },
          { label: 'Bitiş Fiyatı',     render: m => m ? `₺${fmt4(m.endPrice)}` : '-' },
          { label: 'Dönem Max',        render: m => m ? `₺${fmt4(m.maxPrice)}` : '-', green: true },
          { label: 'Dönem Min',        render: m => m ? `₺${fmt4(m.minPrice)}` : '-', red: true },
          {
            label: 'Dönem Getirisi',
            render: m => {
              if (!m) return '-';
              const pos = m.periodReturn >= 0;
              return <span className={pos ? 'text-emerald-600 font-bold' : 'text-rose-600 font-bold'}>{pos ? '+' : ''}{fmt2(m.periodReturn)}%</span>;
            },
          },
          {
            label: 'BIST100\'e Göre',
            render: m => {
              if (!m || !bist100M) return '-';
              const diff = m.periodReturn - bist100M.periodReturn;
              const pos = diff >= 0;
              return <span className={pos ? 'text-emerald-600 font-bold' : 'text-rose-600 font-bold'}>{pos ? '+' : ''}{fmt2(diff)}%</span>;
            },
          },
          { label: 'Max Drawdown',     render: m => m ? <span className="text-rose-600 font-semibold">{fmt2(m.drawdown)}%</span> : '-' },
          { label: 'Volatilite',       render: m => m ? `${fmt2(m.volatility)}%` : '-' },
          {
            label: 'RSI (14)',
            render: m => {
              if (!m) return '-';
              return <span className="text-gray-700">{fmt2(m.rsi)}</span>;
            },
          },
          { label: 'Risk/Getiri', render: m => m?.riskAdjusted != null ? fmt2(m.riskAdjusted) : '-' },
          // ── Teknik Analiz ──
          {
            label: 'MA7 Durumu',
            render: m => {
              if (!m?.ma7 || !m.lastPrice) return '-';
              const above = m.lastPrice >= m.ma7;
              return <span className={above ? 'text-emerald-600' : 'text-rose-600'}>{above ? '▲ Üstünde' : '▼ Altında'} <span className="text-gray-400 text-xs">(₺{fmt4(m.ma7)})</span></span>;
            },
          },
          {
            label: 'MA25 Durumu',
            render: m => {
              if (!m?.ma25 || !m.lastPrice) return '-';
              const above = m.lastPrice >= m.ma25;
              return <span className={above ? 'text-emerald-600' : 'text-rose-600'}>{above ? '▲ Üstünde' : '▼ Altında'} <span className="text-gray-400 text-xs">(₺{fmt4(m.ma25)})</span></span>;
            },
          },
          {
            label: 'Trend',
            render: m => {
              if (!m?.trend) return '-';
              const colorMap = { 'Güçlü Yükselen': 'text-emerald-700', 'Yükselen': 'text-emerald-600', 'Güçlü Düşen': 'text-rose-700', 'Düşen': 'text-rose-600', 'Yatay': 'text-gray-500' };
              const iconMap  = { 'Güçlü Yükselen': '↑↑', 'Yükselen': '↗', 'Güçlü Düşen': '↓↓', 'Düşen': '↘', 'Yatay': '→' };
              return <span className={`font-bold ${colorMap[m.trend] ?? 'text-gray-500'}`}>{iconMap[m.trend] ?? '→'} {m.trend}</span>;
            },
          },
          {
            label: 'RSI Uyarısı',
            render: m => {
              if (!m?.rsiWarning) return <span className="text-gray-300 text-xs">—</span>;
              const isOverbought = m.rsiWarning.includes('Alım');
              return <span className={`text-xs font-semibold ${isOverbought ? 'text-rose-600' : 'text-emerald-600'}`}>{m.rsiWarning}</span>;
            },
          },
        ];

        return (
          <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden">
            <div className="px-6 py-4 border-b border-gray-100 flex items-center justify-between flex-wrap gap-2">
              <div>
                <h2 className="font-bold text-gray-900">Performans Metrikleri</h2>
                <p className="text-xs text-gray-400 mt-0.5">
                  Seçili dönem · BIST100 getirisi: {bist100M ? `${bist100M.periodReturn >= 0 ? '+' : ''}${fmt2(bist100M.periodReturn)}%` : '-'}
                </p>
              </div>
              {/* En iyi performer */}
              {(() => {
                const best = selectedSymbols
                  .map(s => ({ s, ret: metrics[s]?.periodReturn ?? -Infinity }))
                  .sort((a, b) => b.ret - a.ret)[0];
                return best && best.ret !== -Infinity ? (
                  <div className="flex items-center gap-1.5 text-sm">
                    <TrendingUp className="w-4 h-4 text-emerald-500" />
                    <span className="text-gray-500">En iyi:</span>
                    <span className="font-bold text-emerald-600">{best.s.replace('.IS', '').toUpperCase()} ({best.ret >= 0 ? '+' : ''}{fmt2(best.ret)}%)</span>
                  </div>
                ) : null;
              })()}
            </div>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[560px]">
                <thead className="bg-gray-50">
                  <tr>
                    <th className="text-left px-5 py-3 text-xs font-bold text-gray-500 uppercase tracking-wider border-b border-gray-200 w-44">Metrik</th>
                    {selectedSymbols.map((sym, idx) => (
                      <th key={sym} className="text-right px-5 py-3 text-xs font-bold uppercase tracking-wider border-b border-gray-200 whitespace-nowrap" style={{ color: COLORS[idx % COLORS.length] }}>
                        <div className="flex items-center justify-end gap-1.5">
                          <span className="w-2.5 h-2.5 rounded-full flex-shrink-0" style={{ background: COLORS[idx % COLORS.length] }} />
                          {sym.replace('.IS', '').toUpperCase()}
                        </div>
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {PERF_ROWS.map((row, i) => (
                    <tr key={row.label} className={`border-t border-gray-100 hover:bg-gray-50 ${i % 2 === 0 ? '' : 'bg-gray-50/40'}`}>
                      <td className="px-5 py-3 text-sm text-gray-500 font-medium whitespace-nowrap">{row.label}</td>
                      {selectedSymbols.map(sym => (
                        <td key={sym} className="px-5 py-3 text-sm text-right font-mono text-gray-800">
                          {row.render(metrics[sym])}
                        </td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        );
      })()}

      {/* ── Finansal Karşılaştırma Tablosu — sadece gerçek hisseler için ── */}
      {compared && !chartLoading && (() => {
        // Endeks sembollerini filtrele
        const stockSymbols = selectedSymbols.filter(sym =>
          !INDEX_SHORTCUTS.some(idx => idx.symbol === sym)
        );
        if (stockSymbols.length === 0) return null;
        return (
        <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden">
          <div className="px-6 py-4 border-b border-gray-100">
            <h2 className="font-bold text-gray-900">Finansal Karşılaştırma</h2>
            <p className="text-xs text-gray-400 mt-0.5">Kaynak: Midas · Veriler 15 dk gecikmeli</p>
          </div>
          {detailsLoading ? (
            <div className="p-12 flex items-center justify-center"><BounceDots /></div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full min-w-[560px]">
                <thead className="bg-gray-50">
                  <tr>
                    <th className="text-left px-5 py-3 text-xs font-bold text-gray-500 uppercase tracking-wider border-b border-gray-200 w-44">Metrik</th>
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
                      <td className="px-5 py-3 text-sm text-gray-500 font-medium whitespace-nowrap">{row.label}</td>
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
      {compared && !chartLoading && (() => {
        const stockSymbols = selectedSymbols.filter(sym =>
          !INDEX_SHORTCUTS.some(idx => idx.symbol === sym)
        );
        if (stockSymbols.length === 0 || Object.keys(rawPrices).length === 0) return null;
        return (
        <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden">
          <div className="px-6 py-4 border-b border-gray-100 flex items-center gap-4 flex-wrap">
            <h2 className="font-bold text-gray-900">TL Yatırım Simülasyonu</h2>
            <div className="flex items-center gap-2 ml-auto">
              <span className="text-sm text-gray-500">Yatırım tutarı:</span>
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
                    <th key={h} className="text-left px-5 py-3 text-xs font-bold text-gray-500 uppercase tracking-wider border-b border-gray-200 whitespace-nowrap">{h}</th>
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
            * Simülasyon dönem başı ve sonu fiyatları kullanılarak hesaplanmıştır. Komisyon ve vergiler dahil değildir. Gösterge niteliğindedir.
          </div>
        </div>
        );
      })()}
      {!compared && !chartLoading && (
        <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-14 text-center">
          <BarChart2 className="w-12 h-12 text-gray-200 mx-auto mb-3" />
          <p className="text-gray-500 font-semibold">
            {selectedSymbols.length === 0
              ? 'Karşılaştırmak istediğin hisseleri seç'
              : selectedSymbols.length === 1
              ? 'En az bir hisse daha ekle'
              : '"Karşılaştır" butonuna tıkla'}
          </p>
          <p className="text-gray-400 text-sm mt-1">
            En fazla {MAX_STOCKS} BIST hissesini yan yana karşılaştırabilirsin
          </p>
        </div>
      )}
    </div>
  );
}
