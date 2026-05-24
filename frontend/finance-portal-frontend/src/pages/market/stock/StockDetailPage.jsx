import { useEffect, useState, useCallback, useRef } from 'react';
import { useParams, Link } from 'react-router-dom';
import { ArrowLeft, TrendingUp, TrendingDown, Trash2, X, SlidersHorizontal, ChevronDown, BarChart2 } from 'lucide-react';
import {
  AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip,
  ResponsiveContainer
} from 'recharts';
import { getStockMidasDetail, getStockChart, getStockOhlc, getMarketPriceHistory } from '../../../api/marketApi';
import TrendBadge from '../../../components/common/TrendBadge';
import InstrumentActionButtons from '../../../components/instrument/InstrumentActionButtons';
import { buildTrendItem } from '../../../utils/trendUtils';
import { init as klineInit, dispose as klineDispose, registerOverlay } from 'klinecharts';
import RelatedViopContracts from './components/RelatedViopContracts';
import { STOCK_CHART_RANGES } from './stockChartRanges';
import { useTranslation } from '../../../i18n/LanguageContext';

// ── Custom Overlay Kayıtları (uygulama başında bir kez çalışır) ──────────────
// customRect: 2 köşe noktasıyla dikdörtgen çizer
registerOverlay({
  name: 'customRect',
  totalStep: 3,
  needDefaultPointFigure: true,
  needDefaultXAxisFigure: true,
  needDefaultYAxisFigure: true,
  createPointFigures: ({ overlay, coordinates }) => {
    if (coordinates.length === 2) {
      const x = Math.min(coordinates[0].x, coordinates[1].x);
      const y = Math.min(coordinates[0].y, coordinates[1].y);
      const w = Math.abs(coordinates[1].x - coordinates[0].x);
      const h = Math.abs(coordinates[1].y - coordinates[0].y);
      return [{
        type: 'rect',
        attrs: { x, y, width: w, height: h },
        styles: { style: 'stroke_fill', color: 'rgba(22,119,255,0.15)', borderColor: '#1677ff', borderSize: 1 },
      }];
    }
    return [];
  },
});

// customCircle: merkez + kenar noktasıyla çember çizer
registerOverlay({
  name: 'customCircle',
  totalStep: 3,
  needDefaultPointFigure: true,
  needDefaultXAxisFigure: true,
  needDefaultYAxisFigure: true,
  createPointFigures: ({ overlay, coordinates }) => {
    if (coordinates.length === 2) {
      const dx = coordinates[1].x - coordinates[0].x;
      const dy = coordinates[1].y - coordinates[0].y;
      const r = Math.sqrt(dx * dx + dy * dy);
      return [{
        type: 'circle',
        attrs: { x: coordinates[0].x, y: coordinates[0].y, r },
        styles: { style: 'stroke_fill', color: 'rgba(22,119,255,0.15)', borderColor: '#1677ff', borderSize: 1 },
      }];
    }
    return [];
  },
});

const RANGES = STOCK_CHART_RANGES;
// Detay grafiği: yatırım-vadeli aralıklarda (1A–Tüm) GÜNLÜK çubuk kullanılır → MA20/50/200
// her zaman 20/50/200 GÜN demektir (tutarlı; "Tüm"de 200 ay gibi saçma olmaz). Yalnız 1G/1H
// gün-içi kalır (orada MA gün-içi çubuk, normal). MA ısınması fitVisibleToWindow ile sağlanır.
const OHLC_RANGES = [
  { label: '1G', range: '1d',  interval: '5m' },
  { label: '1H', range: '5d',  interval: '1h' },
  { label: '1A', range: '1mo', interval: '1d' },
  { label: '3A', range: '3mo', interval: '1d' },
  { label: '6A', range: '6mo', interval: '1d' },
  { label: '1Y', range: '1y',  interval: '1d' },
  { label: '5Y', range: '5y',  interval: '1d' },
  // 'max'+'1d' Yahoo'da ~aylığa seyrekleşiyor (MA200 yine 200 ay olur); '10y' gerçek günlük verir.
  { label: 'Tüm', range: '10y', interval: '1d' },
];

/* ─── Custom Tooltip ─── */
function ChartTooltip({ active, payload, label }) {
  if (!active || !payload?.length) return null;
  return (
    <div className="bg-[#093eaa] text-white text-xs px-3 py-2 rounded-lg shadow-lg pointer-events-none">
      <p className="font-bold">₺{parseFloat(payload[0].value).toFixed(2)}</p>
      <p className="opacity-80 mt-0.5">{label}</p>
    </div>
  );
}

/* ─── Interactive Chart ─── */
function StockChart({ timestamps, prices }) {
  if (!timestamps?.length || !prices?.length) {
    return (
      <div className="h-64 flex items-center justify-center text-gray-300 text-sm bg-gray-50 rounded-xl">
        Grafik verisi yok
      </div>
    );
  }

  const data = timestamps
    .map((t, i) => {
      if (prices[i] == null) return null;
      const d = new Date(t * 1000);
      const label = d.toLocaleString('tr-TR', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' });
      return { time: label, price: parseFloat(prices[i]) };
    })
    .filter(Boolean);

  const vals = data.map(d => d.price);
  const minVal = Math.min(...vals) * 0.9995;
  const maxVal = Math.max(...vals) * 1.0005;
  const isPos = vals[vals.length - 1] >= vals[0];
  const color = isPos ? '#10b981' : '#ef4444';

  return (
    <ResponsiveContainer width="100%" height={280}>
      <AreaChart data={data} margin={{ top: 10, right: 20, left: 0, bottom: 0 }}>
        <defs>
          <linearGradient id="grad" x1="0" y1="0" x2="0" y2="1">
            <stop offset="5%"  stopColor={color} stopOpacity={0.2} />
            <stop offset="95%" stopColor={color} stopOpacity={0} />
          </linearGradient>
        </defs>
        <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" vertical={false} />
        <XAxis dataKey="time" tick={{ fontSize: 10, fill: '#94a3b8' }} tickLine={false} axisLine={false}
          interval={Math.floor(data.length / 5)} />
        <YAxis domain={[minVal, maxVal]} tick={{ fontSize: 10, fill: '#94a3b8' }}
          tickLine={false} axisLine={false} tickFormatter={v => v.toFixed(2)} width={60} />
        <Tooltip content={<ChartTooltip />} cursor={{ stroke: '#093eaa', strokeWidth: 1, strokeDasharray: '4 4' }} />
        <Area type="monotone" dataKey="price" stroke={color} strokeWidth={2.5}
          fill="url(#grad)" dot={false} activeDot={{ r: 5, fill: color, stroke: '#fff', strokeWidth: 2 }} />
      </AreaChart>
    </ResponsiveContainer>
  );
}

/* ─── Drawing Toolbar Tanımları ─── */
// KlineCharts v9.0.1 built-in overlay isimleri
const DRAWING_TOOLS = [
  { group: 'Çizgiler', tools: [
    { id: 'segment',                label: 'Çizgi Segmenti',   icon: '╱' },
    { id: 'straightLine',           label: 'Düz Çizgi (∞)',    icon: '⟵⟶' },
    { id: 'rayLine',                label: 'Işın',             icon: '⟶' },
    { id: 'horizontalStraightLine', label: 'Yatay Çizgi',      icon: '─' },
    { id: 'verticalStraightLine',   label: 'Dikey Çizgi',      icon: '│' },
    { id: 'horizontalSegment',      label: 'Yatay Segment',    icon: '━' },
    { id: 'verticalSegment',        label: 'Dikey Segment',    icon: '┃' },
    { id: 'priceLine',              label: 'Fiyat Çizgisi',    icon: '₺─' },
  ]},
  { group: 'Kanallar', tools: [
    { id: 'parallelStraightLine',   label: 'Paralel Kanal',    icon: '⫽' },
    { id: 'priceChannelLine',       label: 'Fiyat Kanalı',     icon: '▱' },
  ]},
  { group: 'Fibonacci', tools: [
    { id: 'fibonacciLine',          label: 'Fibonacci Retracement', icon: 'Fib' },
  ]},
  { group: 'Şekiller', tools: [
    { id: 'customRect',   label: 'Dikdörtgen', icon: '□' },
    { id: 'customCircle', label: 'Çember',     icon: '○' },
  ]},
];

/* ─── Drawing Toolbar Bileşeni ─── */
function DrawingToolbar({ activeTool, onSelectTool, onDeleteSelected, onClearAll, indicatorSlot }) {
  const { t } = useTranslation();
  const [openGroup, setOpenGroup] = useState(null);

  return (
    <div className="flex flex-wrap items-center gap-1.5 p-2 bg-gray-50 border border-gray-200 rounded-xl mb-2">
      {DRAWING_TOOLS.map(({ group, tools }) => (
        <div key={group} className="relative">
          <button
            onClick={() => setOpenGroup(openGroup === group ? null : group)}
            className={`inline-flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-xs font-semibold transition-all border ${
              tools.some(tool => tool.id === activeTool) || openGroup === group
                ? 'border-[#093eaa] text-[#093eaa] bg-[#093eaa]/5'
                : 'bg-white text-gray-600 border-gray-200 hover:border-[#093eaa] hover:text-[#093eaa]'
            }`}
          >
            {t(group)}
            <ChevronDown className={`w-3 h-3 transition-transform ${openGroup === group ? 'rotate-180' : ''}`} />
          </button>
          {openGroup === group && (
            <div className="absolute top-full left-0 mt-1 z-50 bg-white border border-gray-200 rounded-xl shadow-lg p-1 min-w-[180px]">
              {tools.map(tool => (
                <button
                  key={tool.id}
                  onClick={() => {
                    onSelectTool(tool.id === activeTool ? null : tool.id);
                    setOpenGroup(null);
                  }}
                  className={`w-full flex items-center gap-2.5 px-3 py-2 rounded-lg text-xs transition-all ${
                    tool.id === activeTool
                      ? 'bg-[#093eaa] text-white'
                      : 'text-gray-700 hover:bg-gray-50'
                  }`}
                >
                  <span className="w-8 text-center font-mono text-[11px] opacity-70">{tool.icon}</span>
                  <span>{t(tool.label)}</span>
                </button>
              ))}
            </div>
          )}
        </div>
      ))}

      {/* İndikatör menüsü (yalnız mum grafikte; çizim gruplarının yanında) */}
      {indicatorSlot && (
        <>
          <div className="w-px h-6 bg-gray-200 mx-1" />
          {indicatorSlot}
        </>
      )}

      {/* Ayırıcı */}
      <div className="w-px h-6 bg-gray-200 mx-1" />

      {/* Seçili çizimi sil */}
      <button
        onClick={onDeleteSelected}
        title={t('Seçili çizimi sil')}
        className="inline-flex items-center px-2 py-1.5 rounded-lg text-gray-500 hover:bg-red-50 hover:text-red-500 hover:border-red-200 transition-all border border-gray-200"
      >
        <Trash2 className="w-3.5 h-3.5" />
      </button>

      {/* Tümünü temizle */}
      <button
        onClick={onClearAll}
        title={t('Tüm çizimleri temizle')}
        className="inline-flex items-center px-2 py-1.5 rounded-lg text-gray-500 hover:bg-red-50 hover:text-red-500 hover:border-red-200 transition-all border border-gray-200"
      >
        <X className="w-3.5 h-3.5" />
      </button>

      {/* Aktif araç göstergesi */}
      {activeTool && (
        <span className="ml-auto text-xs text-[#093eaa] font-medium bg-blue-50 px-2 py-1 rounded-lg">
          {(() => { const lbl = DRAWING_TOOLS.flatMap(g => g.tools).find(it => it.id === activeTool)?.label; return lbl ? t(lbl) : activeTool; })()}
          <button onClick={() => onSelectTool(null)} className="ml-1.5 opacity-60 hover:opacity-100">✕</button>
        </span>
      )}
    </div>
  );
}

const MA_PERIODS = [
  { period: 20,  color: '#f59e0b', label: 'MA20'  },
  { period: 50,  color: '#8b5cf6', label: 'MA50'  },
  { period: 200, color: '#ef4444', label: 'MA200' },
];

// MA çizgi renkleri buton renkleriyle aynı olsun (klinecharts default paleti yerine)
function maLineStyles(periods) {
  return { lines: periods.map(p => ({ color: MA_PERIODS.find(m => m.period === p)?.color ?? '#888', size: 1.5 })) };
}

// ── MA ısınması (finans-sitesi davranışı) ──────────────────────────────────────
// Seçili aralık için, MA'nın pencerenin BAŞINDAN dolu çizilebilmesi adına pencereden
// ÖNCE ekstra veri çekeriz (aynı interval, daha uzun range). MA tüm seri üzerinden
// hesaplanır; ekranda yalnız seçili pencere gösterilir (ısınma sola kaydırılır).
// Günlük aralıklarda MA200'ün (200 gün) pencereyi baştan doldurabilmesi için, pencere + ~200
// işlem günü kapsayan daha uzun bir range çekilir (aynı interval). 5Y/Tüm → tüm geçmiş.
const STOCK_WARMUP_RANGE = {
  '1d': '5d', '5d': '1mo',
  '1mo': '1y', '3mo': '2y', '6mo': '2y', '1y': '2y', '5y': '10y', '10y': '10y',
};
const RANGE_WINDOW_MS = {
  '1d': 1 * 864e5, '5d': 5 * 864e5, '1mo': 31 * 864e5, '3mo': 93 * 864e5,
  '6mo': 186 * 864e5, '1y': 366 * 864e5, '5y': 1827 * 864e5, '10y': Infinity,
};

/** Tüm veri (ısınma + pencere) uygulandıktan sonra görünür alanı yalnız pencereye sığdırır. */
function fitVisibleToWindow(chart, chartId, allData, windowStartTs) {
  if (!chart || !allData?.length) return;
  requestAnimationFrame(() => {
    try {
      const el = document.getElementById(chartId);
      const width = el?.clientWidth ?? 0;
      if (width <= 0) return;
      const windowCount = windowStartTs > 0
        ? allData.filter(d => d.timestamp >= windowStartTs).length
        : allData.length;
      const count = Math.max(1, windowCount);
      chart.setOffsetRightDistance(8);
      chart.setBarSpace(Math.max(2, (width - 16) / count));
    } catch (_) { /* yoksay */ }
  });
}

const SUB_INDICATORS = [
  { name: 'VOL',  label: 'Hacim', color: '#6b7280' },
  { name: 'RSI',  label: 'RSI',   color: '#f59e0b' },
  { name: 'MACD', label: 'MACD',  color: '#3b82f6' },
  { name: 'KDJ',  label: 'KDJ',   color: '#8b5cf6' },
  { name: 'BOLL', label: 'BOLL',  color: '#10b981' },
];

function CandlestickChart({ symbol }) {
  const { t } = useTranslation();
  const chartId = useRef(`kline_${Date.now()}`);
  const chartRef = useRef(null);
  const indicatorPaneIds = useRef({}); // Her indikatör için pane ID'sini sakla
  const [ohlcRangeIdx, setOhlcRangeIdx] = useState(3);
  const [activeMAs, setActiveMAs] = useState([]);
  const [activeSubInds, setActiveSubInds] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError]     = useState(false);
  const [activeTool, setActiveTool] = useState(null);
  const [indMenuOpen, setIndMenuOpen] = useState(false);

  const rangeConfig = OHLC_RANGES[ohlcRangeIdx];
  const { range, interval } = rangeConfig;

  // MA toggle — calcParams ile tüm aktif periyotları tek seferde set et
  const applyMA = useCallback((periods) => {
    const chart = chartRef.current;
    if (!chart) return;
    chart.removeIndicator('candle_pane', 'MA');
    if (periods.length > 0) {
      chart.createIndicator(
        { name: 'MA', calcParams: periods, styles: maLineStyles(periods) },
        false,
        { id: 'candle_pane' }
      );
    }
  }, []);

  const toggleMA = useCallback((period) => {
    setActiveMAs(prev => {
      const next = prev.includes(period)
        ? prev.filter(p => p !== period)
        : [...prev, period].sort((a, b) => a - b);
      applyMA(next);
      return next;
    });
  }, [applyMA]);

  // Alt indikatör toggle - pane ID tracking ile
  const toggleSubIndicator = useCallback((indName) => {
    const chart = chartRef.current;
    if (!chart) return;
    
    setActiveSubInds(prev => {
      const isCurrentlyActive = prev.includes(indName);
      
      if (isCurrentlyActive) {
        // Kaldır
        const paneId = indicatorPaneIds.current[indName];
        if (paneId) {
          try {
            chart.removeIndicator(paneId, indName);
            delete indicatorPaneIds.current[indName];
          } catch (e) {
            console.error('Error removing indicator:', indName, e);
          }
        }
        return prev.filter(n => n !== indName);
      } else {
        // Ekle
        try {
          const paneId = chart.createIndicator(
            { name: indName },
            true,
            { height: 80 }
          );
          if (paneId) {
            indicatorPaneIds.current[indName] = paneId;
          }
        } catch (e) {
          console.error('Error adding indicator:', indName, e);
        }
        return [...prev, indName];
      }
    });
  }, []);

  const handleSelectTool = useCallback((toolId) => {
    setActiveTool(toolId);
    if (!chartRef.current) return;
    if (toolId) {
      chartRef.current.createOverlay({ name: toolId });
    }
  }, []);

  const handleDeleteSelected = useCallback(() => {
    chartRef.current?.removeOverlay();
  }, []);

  const handleClearAll = useCallback(() => {
    DRAWING_TOOLS.flatMap(g => g.tools).forEach(t => {
      try { chartRef.current?.removeOverlay({ name: t.id }); } catch {}
    });
    setActiveTool(null);
  }, []);

  useEffect(() => {
    const handler = (e) => { if (e.key === 'Escape') setActiveTool(null); };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, []);

  useEffect(() => {
    const id = chartId.current;
    const chart = klineInit(id);
    chartRef.current = chart;
    indicatorPaneIds.current = {}; // Temizle

    setLoading(true);
    setError(false);

    const fetchRange = STOCK_WARMUP_RANGE[range] ?? range;
    getStockOhlc(symbol, fetchRange, interval)
      .then(data => {
        if (!data?.length) { setError(true); setLoading(false); return; }
        const klineData = data
          .map(d => ({
            timestamp: Number(d.time) * 1000,
            open:   parseFloat(d.open),
            high:   parseFloat(d.high),
            low:    parseFloat(d.low),
            close:  parseFloat(d.close),
            volume: Number(d.volume ?? 0),
            turnover: 0,
          }))
          .filter(d => !isNaN(d.open) && d.open > 0)
          .sort((a, b) => a.timestamp - b.timestamp);

        chartRef.current?.applyNewData(klineData);
        // Görünür alanı yalnız seçili pencereye sığdır (ısınma verisi sola kayar, MA dolu çizilir)
        const winMsC = RANGE_WINDOW_MS[range];
        fitVisibleToWindow(chartRef.current, id, klineData, (winMsC && winMsC !== Infinity) ? Date.now() - winMsC : 0);

        // Data yüklendikten SONRA aktif indikatörleri ekle
        if (chartRef.current) {
          // MA ekle
          if (activeMAs.length > 0) {
            chartRef.current.createIndicator(
              { name: 'MA', calcParams: activeMAs, styles: maLineStyles(activeMAs) },
              false,
              { id: 'candle_pane' }
            );
          }

          // Alt indikatörleri ekle ve pane ID'lerini kaydet
          activeSubInds.forEach(indName => {
            try {
              const paneId = chartRef.current.createIndicator(
                { name: indName },
                true,
                { height: 80 }
              );
              if (paneId) {
                indicatorPaneIds.current[indName] = paneId;
              }
            } catch (e) {
              console.error('Error creating indicator in useEffect:', indName, e);
            }
          });
        }
        
        setLoading(false);
      })
      .catch(() => { setError(true); setLoading(false); });

    // Cleanup
    return () => { 
      klineDispose(id);
      indicatorPaneIds.current = {};
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [symbol, range, interval]);

  // İndikatör menüsü (çizim araç çubuğunun yanına yerleşir)
  const indicatorDropdown = (
    <div className="relative">
      <button
        onClick={() => setIndMenuOpen(v => !v)}
        className={`inline-flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-xs font-semibold border transition-all ${
          indMenuOpen || (activeMAs.length + activeSubInds.length) > 0
            ? 'bg-[#093eaa] text-white border-[#093eaa]'
            : 'bg-white text-gray-600 border-gray-200 hover:border-[#093eaa] hover:text-[#093eaa]'
        }`}
      >
        <SlidersHorizontal className="w-3.5 h-3.5" />
        {t('İndikatör')}
        {(activeMAs.length + activeSubInds.length) > 0 && (
          <span className="inline-flex items-center justify-center min-w-[16px] h-4 px-1 rounded-full bg-[#093eaa] text-white text-[10px] font-bold">
            {activeMAs.length + activeSubInds.length}
          </span>
        )}
        <ChevronDown className={`w-3 h-3 transition-transform ${indMenuOpen ? 'rotate-180' : ''}`} />
      </button>

      {indMenuOpen && (
        <>
          <div className="fixed inset-0 z-40" onClick={() => setIndMenuOpen(false)} />
          <div className="absolute left-0 top-full mt-1 z-50 bg-white border border-gray-200 rounded-xl shadow-lg p-3 w-64">
            <p className="text-[10px] font-bold text-gray-400 uppercase tracking-wider mb-2">{t('Hareketli Ortalama')}</p>
            <div className="flex flex-wrap gap-1.5 mb-3">
              {MA_PERIODS.map(({ period, color, label }) => {
                const active = activeMAs.includes(period);
                return (
                  <button key={period} onClick={() => toggleMA(period)}
                    className={`px-2.5 py-1 rounded-md text-xs font-bold transition-all border ${
                      active ? 'text-white border-transparent' : 'bg-gray-50 text-gray-500 border-gray-200 hover:bg-gray-100'
                    }`}
                    style={active ? { backgroundColor: color, borderColor: color } : {}}>
                    {label}
                  </button>
                );
              })}
            </div>
            <p className="text-[10px] font-bold text-gray-400 uppercase tracking-wider mb-2">{t('Osilatör / İndikatör')}</p>
            <div className="flex flex-wrap gap-1.5">
              {SUB_INDICATORS.map(({ name, label, color }) => {
                const active = activeSubInds.includes(name);
                return (
                  <button key={name} onClick={() => toggleSubIndicator(name)}
                    className={`px-2.5 py-1 rounded-md text-xs font-semibold transition-all border ${
                      active ? 'text-white border-transparent' : 'bg-gray-50 text-gray-500 border-gray-200 hover:bg-gray-100'
                    }`}
                    style={active ? { backgroundColor: color, borderColor: color } : {}}>
                    {label}
                  </button>
                );
              })}
            </div>
          </div>
        </>
      )}
    </div>
  );

  return (
    <div>
      {/* ── Zaman aralığı — segmented, ortalı ── */}
      <div className="flex justify-center mb-3">
        <div className="inline-flex items-center gap-0.5 rounded-lg border border-gray-200 bg-gray-50 p-0.5 overflow-x-auto max-w-full [&::-webkit-scrollbar]:hidden">
          {OHLC_RANGES.map((r, i) => (
            <button key={r.label} onClick={() => setOhlcRangeIdx(i)}
              className={`px-3 py-1 rounded-md text-xs font-semibold whitespace-nowrap transition-all ${
                i === ohlcRangeIdx ? 'bg-white text-[#093eaa] shadow-sm' : 'text-gray-500 hover:text-gray-800'
              }`}>
              {r.label}
            </button>
          ))}
        </div>
      </div>

      {/* ── Drawing Toolbar (İndikatör menüsü çizim gruplarının yanında) ── */}
      <DrawingToolbar
        activeTool={activeTool}
        onSelectTool={handleSelectTool}
        onDeleteSelected={handleDeleteSelected}
        onClearAll={handleClearAll}
        indicatorSlot={indicatorDropdown}
      />

      <div className="relative">
        {loading && (
          <div className="absolute inset-0 flex items-center justify-center bg-white/80 z-10">
            <div className="flex gap-1.5">
              <div className="w-2 h-2 bg-[#093eaa] rounded-full animate-bounce" />
              <div className="w-2 h-2 bg-[#093eaa]/60 rounded-full animate-bounce [animation-delay:100ms]" />
              <div className="w-2 h-2 bg-[#093eaa]/30 rounded-full animate-bounce [animation-delay:200ms]" />
            </div>
          </div>
        )}
        {error && !loading && (
          <div className="absolute inset-0 flex items-center justify-center text-gray-400 text-sm">
            {t('Mum grafiği verisi yüklenemedi.')}
          </div>
        )}
        <div id={chartId.current} style={{ width: '100%', height: '460px' }} />
      </div>
      <p className="text-xs text-gray-400 mt-2">Kaynak: Yahoo Finance · OHLC verisi</p>
    </div>
  );
}

/* ─── KlineCharts Çizgi Grafiği ─── */
function LineChart({ symbol }) {
  const { t } = useTranslation();
  const chartId = useRef(`kline_line_${Date.now()}`);
  const chartRef = useRef(null);
  const indicatorPaneIds = useRef({});
  const [rangeIdx, setRangeIdx] = useState(2);
  const [loading, setLoading] = useState(true);
  const [error, setError]     = useState(false);
  const [activeTool, setActiveTool] = useState(null);
  const [activeMAs, setActiveMAs] = useState([]);
  const [activeSubInds, setActiveSubInds] = useState([]);
  const [indMenuOpen, setIndMenuOpen] = useState(false);

  const rangeConfig = RANGES[rangeIdx];
  const { range, interval } = rangeConfig;

  // Çizgi grafikte de indikatör/MA (kapanış serisi üzerinden)
  const applyMA = useCallback((periods) => {
    const chart = chartRef.current;
    if (!chart) return;
    chart.removeIndicator('candle_pane', 'MA');
    if (periods.length > 0) {
      chart.createIndicator({ name: 'MA', calcParams: periods, styles: maLineStyles(periods) }, false, { id: 'candle_pane' });
    }
  }, []);
  const toggleMA = useCallback((period) => {
    setActiveMAs(prev => {
      const next = prev.includes(period) ? prev.filter(p => p !== period) : [...prev, period].sort((a, b) => a - b);
      applyMA(next);
      return next;
    });
  }, [applyMA]);
  const toggleSubIndicator = useCallback((indName) => {
    const chart = chartRef.current;
    if (!chart) return;
    setActiveSubInds(prev => {
      if (prev.includes(indName)) {
        const paneId = indicatorPaneIds.current[indName];
        if (paneId) { try { chart.removeIndicator(paneId, indName); delete indicatorPaneIds.current[indName]; } catch { /* yoksay */ } }
        return prev.filter(n => n !== indName);
      }
      try {
        const paneId = chart.createIndicator({ name: indName }, true, { height: 80 });
        if (paneId) indicatorPaneIds.current[indName] = paneId;
      } catch { /* yoksay */ }
      return [...prev, indName];
    });
  }, []);

  const handleSelectTool = useCallback((toolId) => {
    setActiveTool(toolId);
    if (!chartRef.current) return;
    if (toolId) {
      chartRef.current.createOverlay({ name: toolId });
    }
  }, []);

  const handleDeleteSelected = useCallback(() => {
    chartRef.current?.removeOverlay();
  }, []);

  const handleClearAll = useCallback(() => {
    DRAWING_TOOLS.flatMap(g => g.tools).forEach(t => {
      try { chartRef.current?.removeOverlay({ name: t.id }); } catch {}
    });
    setActiveTool(null);
  }, []);

  useEffect(() => {
    const handler = (e) => { if (e.key === 'Escape') setActiveTool(null); };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, []);

  useEffect(() => {
    const id = chartId.current;
    const chart = klineInit(id);
    chart.setStyles({ candle: { type: 'area' } });
    chartRef.current = chart;
    indicatorPaneIds.current = {};

    setLoading(true);
    setError(false);

    const fetchRange = STOCK_WARMUP_RANGE[range] ?? range;
    getStockChart(symbol, fetchRange, interval)
      .then(res => {
        const ts     = res?.timestamps  ?? [];
        const prices = res?.closePrices ?? [];
        if (!ts.length || !prices.length) { setError(true); setLoading(false); return; }

        const klineData = ts
          .map((t, i) => {
            const p = parseFloat(prices[i]);
            if (!Number.isFinite(p) || p <= 0) return null;
            return {
              timestamp: Number(t) * 1000,
              open: p,
              high: p,
              low: p,
              close: p,
              volume: 0,
              turnover: 0,
            };
          })
          .filter(Boolean)
          .sort((a, b) => a.timestamp - b.timestamp);

        if (!klineData.length) {
          setError(true);
          setLoading(false);
          return;
        }

        const isUp = klineData[klineData.length - 1].close >= klineData[0].close;
        const color = isUp ? '#10b981' : '#ef4444';
        chartRef.current?.setStyles({
          candle: {
            type: 'area',
            area: {
              lineColor: color,
              lineSize: 2,
              value: 'close',
              smooth: true,
              backgroundColor: [
                { offset: 0, color: color + '33' },
                { offset: 1, color: color + '00' },
              ],
            },
          },
        });

        chartRef.current?.applyNewData(klineData);
        // Görünür alanı yalnız seçili pencereye sığdır (ısınma verisi sola kayar, MA dolu çizilir)
        const winMsL = RANGE_WINDOW_MS[range];
        fitVisibleToWindow(chartRef.current, id, klineData, (winMsL && winMsL !== Infinity) ? Date.now() - winMsL : 0);
        // Veri yüklendikten sonra aktif indikatörleri yeniden ekle
        if (chartRef.current) {
          if (activeMAs.length > 0) {
            chartRef.current.createIndicator({ name: 'MA', calcParams: activeMAs, styles: maLineStyles(activeMAs) }, false, { id: 'candle_pane' });
          }
          activeSubInds.forEach(indName => {
            try {
              const paneId = chartRef.current.createIndicator({ name: indName }, true, { height: 80 });
              if (paneId) indicatorPaneIds.current[indName] = paneId;
            } catch { /* yoksay */ }
          });
        }
        setLoading(false);
      })
      .catch(() => { setError(true); setLoading(false); });

    return () => { klineDispose(id); indicatorPaneIds.current = {}; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [symbol, range, interval]);

  // İndikatör menüsü (çizgi modu — kapanış serisi; VOL hariç, hacim yok)
  const lineIndicatorDropdown = (
    <div className="relative">
      <button
        onClick={() => setIndMenuOpen(v => !v)}
        className={`inline-flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-xs font-semibold border transition-all ${
          indMenuOpen || (activeMAs.length + activeSubInds.length) > 0
            ? 'bg-[#093eaa] text-white border-[#093eaa]'
            : 'bg-white text-gray-600 border-gray-200 hover:border-[#093eaa] hover:text-[#093eaa]'
        }`}
      >
        <SlidersHorizontal className="w-3.5 h-3.5" />
        {t('İndikatör')}
        {(activeMAs.length + activeSubInds.length) > 0 && (
          <span className="inline-flex items-center justify-center min-w-[16px] h-4 px-1 rounded-full bg-[#093eaa] text-white text-[10px] font-bold">
            {activeMAs.length + activeSubInds.length}
          </span>
        )}
        <ChevronDown className={`w-3 h-3 transition-transform ${indMenuOpen ? 'rotate-180' : ''}`} />
      </button>
      {indMenuOpen && (
        <>
          <div className="fixed inset-0 z-40" onClick={() => setIndMenuOpen(false)} />
          <div className="absolute left-0 top-full mt-1 z-50 bg-white border border-gray-200 rounded-xl shadow-lg p-3 w-64">
            <p className="text-[10px] font-bold text-gray-400 uppercase tracking-wider mb-2">{t('Hareketli Ortalama')}</p>
            <div className="flex flex-wrap gap-1.5 mb-3">
              {MA_PERIODS.map(({ period, color, label }) => {
                const active = activeMAs.includes(period);
                return (
                  <button key={period} onClick={() => toggleMA(period)}
                    className={`px-2.5 py-1 rounded-md text-xs font-bold transition-all border ${active ? 'text-white border-transparent' : 'bg-gray-50 text-gray-500 border-gray-200 hover:bg-gray-100'}`}
                    style={active ? { backgroundColor: color, borderColor: color } : {}}>
                    {label}
                  </button>
                );
              })}
            </div>
            <p className="text-[10px] font-bold text-gray-400 uppercase tracking-wider mb-2">{t('Osilatör / İndikatör')}</p>
            <div className="flex flex-wrap gap-1.5">
              {SUB_INDICATORS.filter(s => s.name !== 'VOL').map(({ name, label, color }) => {
                const active = activeSubInds.includes(name);
                return (
                  <button key={name} onClick={() => toggleSubIndicator(name)}
                    className={`px-2.5 py-1 rounded-md text-xs font-semibold transition-all border ${active ? 'text-white border-transparent' : 'bg-gray-50 text-gray-500 border-gray-200 hover:bg-gray-100'}`}
                    style={active ? { backgroundColor: color, borderColor: color } : {}}>
                    {label}
                  </button>
                );
              })}
            </div>
          </div>
        </>
      )}
    </div>
  );

  return (
    <div>
      {/* ── Zaman aralığı — segmented, ortalı ── */}
      <div className="flex justify-center mb-3">
        <div className="inline-flex items-center gap-0.5 rounded-lg border border-gray-200 bg-gray-50 p-0.5 overflow-x-auto max-w-full [&::-webkit-scrollbar]:hidden">
          {RANGES.map((r, i) => (
            <button key={r.label} onClick={() => setRangeIdx(i)}
              className={`px-3 py-1 rounded-md text-xs font-semibold whitespace-nowrap transition-all ${
                i === rangeIdx ? 'bg-white text-[#093eaa] shadow-sm' : 'text-gray-500 hover:text-gray-800'
              }`}>
              {r.label}
            </button>
          ))}
        </div>
      </div>

      {/* Drawing Toolbar (İndikatör menüsü dahil) */}
      <DrawingToolbar
        activeTool={activeTool}
        onSelectTool={handleSelectTool}
        onDeleteSelected={handleDeleteSelected}
        onClearAll={handleClearAll}
        indicatorSlot={lineIndicatorDropdown}
      />

      <div className="relative">
        {loading && (
          <div className="absolute inset-0 flex items-center justify-center bg-white/80 z-10">
            <div className="flex gap-1.5">
              <div className="w-2 h-2 bg-[#093eaa] rounded-full animate-bounce" />
              <div className="w-2 h-2 bg-[#093eaa]/60 rounded-full animate-bounce [animation-delay:100ms]" />
              <div className="w-2 h-2 bg-[#093eaa]/30 rounded-full animate-bounce [animation-delay:200ms]" />
            </div>
          </div>
        )}
        {error && !loading && (
          <div className="absolute inset-0 flex items-center justify-center text-gray-400 text-sm">
            {t('Grafik verisi yüklenemedi.')}
          </div>
        )}
        <div id={chartId.current} style={{ width: '100%', height: '380px' }} />
      </div>
    </div>
  );
}

function MetricCard({ label, value, highlight }) {
  const { t } = useTranslation();
  if (!value) return null;
  return (
    <div className="bg-gray-50 rounded-xl p-4">
      <p className="text-xs text-gray-400 mb-1">{t(label)}</p>
      <p className={`text-sm font-bold ${highlight ? 'text-[#093eaa]' : 'text-gray-900'}`}>{value}</p>
    </div>
  );
}

/* ─── Info Row ─── */
function InfoRow({ label, value }) {
  const { t } = useTranslation();
  if (!value) return null;
  return (
    <div className="flex justify-between items-start py-3.5 border-b border-gray-100 last:border-0">
      <span className="text-sm text-gray-500 w-44 shrink-0">{t(label)}</span>
      <span className="text-sm text-gray-900 font-medium text-right max-w-xs">{value}</span>
    </div>
  );
}

/* ─── Main Page ─── */
export default function StockDetailPage() {
  const { t } = useTranslation();
  const { symbol } = useParams();
  const [midas, setMidas] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [chartMode, setChartMode] = useState('tv');
  const [trendItem, setTrendItem] = useState(null);

  useEffect(() => {
    setLoading(true);
    Promise.all([
      getStockMidasDetail(symbol).catch(() => null),
    ]).then(([m]) => { setMidas(m); })
      .catch(e => setError(!e.response ? t('Sunucuya ulaşılamıyor.') : `${t('Hata')} (${e.response.status})`))
      .finally(() => setLoading(false));
  }, [symbol]);

  // Trend rozeti — 1Y kapanış serisinden (MA20/50 + 52h konumu)
  useEffect(() => {
    let cancelled = false;
    setTrendItem(null);
    getMarketPriceHistory('STOCK', symbol, '1Y')
      .then(res => { if (!cancelled) setTrendItem(buildTrendItem(res?.closePrices ?? [], 'STOCK')); })
      .catch(() => { if (!cancelled) setTrendItem(null); });
    return () => { cancelled = true; };
  }, [symbol]);

  const ticker = symbol?.replace('.IS', '').replace('.is', '').toUpperCase();
  const isPos = midas?.dailyChangePercent && !midas.dailyChangePercent.startsWith('-');

  return (
    <div className="max-w-4xl mx-auto space-y-6">
      {/* Back + Title */}
      <div className="flex items-center gap-3">
        <Link to="/market/stocks" className="text-gray-400 hover:text-[#093eaa] transition-colors">
          <ArrowLeft className="w-5 h-5" />
        </Link>
        <div className="flex items-center gap-3">
          {midas?.logoUrl && (
            <img
              src={`/api/proxy/image?url=${encodeURIComponent(midas.logoUrl)}`}
              alt={midas.name}
              className="w-10 h-10 rounded-xl object-contain border border-gray-100 p-0.5 bg-white shadow-sm"
              onError={e => { e.target.style.display = 'none'; }}
            />
          )}
          <div>
            <div className="flex items-center gap-2 flex-wrap">
              <h1 className="text-2xl font-bold text-gray-900">
                {ticker} {t('Hisse')} {midas?.name ? `- ${midas.name}` : ''}
              </h1>
              {trendItem && <TrendBadge item={trendItem} size="sm" />}
            </div>
            <p className="text-xs text-gray-400 mt-0.5">{t('Borsa İstanbul · Veriler 15 dk gecikmeli · Kaynak: Midas')}</p>
          </div>
        </div>
      </div>

      {loading && (
        <div className="flex items-center justify-center py-20">
          <div className="flex gap-2">
            <div className="w-2 h-2 bg-[#093eaa] rounded-full animate-bounce" />
            <div className="w-2 h-2 bg-[#093eaa]/60 rounded-full animate-bounce [animation-delay:100ms]" />
            <div className="w-2 h-2 bg-[#093eaa]/30 rounded-full animate-bounce [animation-delay:200ms]" />
          </div>
        </div>
      )}

      {error && <div className="bg-red-50 border border-red-200 text-red-600 text-sm px-6 py-4 rounded-2xl">{error}</div>}

      {!loading && !error && (
        <>
          {/* ── Price + Volume + Chart ── */}
          <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden">
            <div className="grid grid-cols-2 divide-x divide-gray-100">
              <div className="p-6">
                <p className="text-4xl font-bold text-gray-900">
                  {midas?.currentPrice ?? '-'}
                </p>
                <p className="text-sm text-gray-400 mt-1">{t('Güncel Fiyat')}</p>
              </div>
              <div className="p-6">
                <p className="text-2xl font-bold text-gray-900 truncate">
                  {midas?.dailyVolume ?? '-'}
                </p>
                <p className="text-sm text-gray-400 mt-1">{t('Günlük İşlem Hacmi')}</p>
              </div>
            </div>

            <div className="px-4 pt-2 pb-2">
              {/* Grafik mod seçici (segmented) + Karşılaştır */}
              <div className="flex items-center justify-between gap-2 mb-3 flex-wrap">
                <div className="inline-flex items-center gap-0.5 rounded-lg border border-gray-200 bg-gray-50 p-0.5">
                  {[{ key: 'tv', label: 'Mum Grafik' }, { key: 'line', label: 'Çizgi' }].map(m => (
                    <button key={m.key} onClick={() => setChartMode(m.key)}
                      className={`px-3 py-1.5 rounded-md text-xs font-semibold transition-all ${chartMode === m.key ? 'bg-white text-[#093eaa] shadow-sm' : 'text-gray-500 hover:text-gray-800'}`}>
                      {t(m.label)}
                    </button>
                  ))}
                </div>
                <div className="flex items-center gap-2 flex-wrap">
                  <Link
                    to={`/market/stocks/compare?add=${encodeURIComponent(symbol)}`}
                    className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold border border-[#093eaa]/30 text-[#093eaa] bg-[#093eaa]/5 hover:bg-[#093eaa]/10 transition-colors"
                  >
                    <BarChart2 className="w-3.5 h-3.5" />
                    {t('Karşılaştır')}
                  </Link>
                  <InstrumentActionButtons
                    assetType="STOCK"
                    symbol={symbol}
                    name={midas?.name || ticker}
                    price={midas?.currentPrice}
                  />
                </div>
              </div>

              {chartMode === 'tv' && <CandlestickChart symbol={symbol} />}

              {chartMode === 'line' && <LineChart symbol={symbol} />}
            </div>

            {midas?.dailyChangePercent && (
              <div className="px-6 pb-5">
                <span className={`text-sm font-bold flex items-center gap-1.5 ${isPos ? 'text-emerald-600' : 'text-rose-600'}`}>
                  {isPos ? <TrendingUp className="w-4 h-4" /> : <TrendingDown className="w-4 h-4" />}
                  {t('Günlük Değişim:')} {midas.dailyChange ?? ''} ({midas.dailyChangePercent})
                </span>
              </div>
            )}
          </div>

          {/* ── Financial Metrics Table ── */}
          <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">
            <h2 className="text-base font-bold text-gray-900 mb-4">{ticker} Hisse ve Finansal Bilgileri</h2>
            <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
              <MetricCard label="Son İşlem Fiyatı"     value={midas?.currentPrice} highlight />
              <MetricCard label="Alış"                 value={midas?.bid} />
              <MetricCard label="Satış"                value={midas?.ask} />
              <MetricCard label="Açılış Fiyatı"        value={midas?.openPrice} />
              <MetricCard label="Günlük Değişim %"     value={midas?.dailyChangePercent} />
              <MetricCard label="Günlük Değişim (TL)"  value={midas?.dailyChange} />
              <MetricCard label="Günlük Hacim (TL)"    value={midas?.dailyVolume} />
              <MetricCard label="Günlük Hacim (Lot)"   value={midas?.volumeLot} />
              <MetricCard label="Tavan"                value={midas?.upperLimit} />
              <MetricCard label="Taban"                value={midas?.lowerLimit} />
              <MetricCard label="Haftalık En Yüksek"   value={midas?.weeklyHigh} />
              <MetricCard label="Haftalık En Düşük"    value={midas?.weeklyLow} />
              <MetricCard label="Aylık En Yüksek"      value={midas?.monthlyHigh} />
              <MetricCard label="Aylık En Düşük"       value={midas?.monthlyLow} />
              <MetricCard label="Piyasa Değeri"        value={midas?.marketCap} />
              <MetricCard label="Sermaye"              value={midas?.capital} />
              <MetricCard label="F/K"                  value={midas?.peRatio} />
              <MetricCard label="PD/DD"                value={midas?.pbRatio} />
              <MetricCard label="Halka Açıklık (%)"    value={midas?.freeFloat} />
              <MetricCard label="Yabancı Oranı (%)"    value={midas?.foreignRatio} />
              <MetricCard label="Volatilite"           value={midas?.volatility} />
              <MetricCard label="Net Kâr"              value={midas?.netProfit} />
            </div>
          </div>

          {/* ── Ortaklık Yapısı ── */}
          {midas?.shareholders?.length > 0 && (
            <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">
              <h2 className="text-base font-bold text-gray-900 mb-6">{ticker} {t('Ortaklık Yapısı')}</h2>
              <table className="w-full">
                <thead className="bg-gray-50">
                  <tr>
                    <th className="text-left px-4 py-3 text-xs font-bold text-gray-500 uppercase tracking-wider">{t('Ticari Ünvan')}</th>
                    <th className="text-right px-4 py-3 text-xs font-bold text-gray-500 uppercase tracking-wider">{t('Pay Oranı (%)')}</th>
                  </tr>
                </thead>
                <tbody>
                  {midas.shareholders.map((s, i) => (
                    <tr key={i} className="border-t border-gray-100 hover:bg-gray-50 transition-colors">
                      <td className="px-4 py-3 text-sm text-gray-800">{s.name}</td>
                      <td className="px-4 py-3 text-sm font-semibold text-gray-900 text-right">{s.sharePercent}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {/* ── Şirket Hakkında ── */}
          <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">
            <h2 className="text-base font-bold text-gray-900 mb-1">{ticker} - {t('Şirket Hakkında')}</h2>
            {midas?.name && (
              <p className="text-sm text-gray-400 mb-6">
                {midas.name} {t('şirketiyle ilgili bilmen gerekenleri aşağıda bulabilirsin.')}
              </p>
            )}
            <InfoRow label="CEO"              value={midas?.ceo} />
            <InfoRow label="Kuruluş Tarihi"   value={midas?.foundedDate} />
            <InfoRow label="Halka Arz Tarihi" value={midas?.ipoDate} />
            <InfoRow label="Sektör"           value={midas?.sector} />
            <InfoRow label="Çalışan Sayısı"   value={midas?.employeeCount} />
            <InfoRow label="Adres"            value={midas?.address} />
            <InfoRow label="Merkez"           value={midas?.country} />
          </div>

          {/* ── İlişkili VİOP Kontratları ── */}
          <RelatedViopContracts symbol={ticker} />

          {/* ── Şirket Açıklaması ── */}
          {(midas?.description || midas?.logoUrl) && (
            <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">
              {/* Logo + Name header */}
              <div className="flex items-center gap-4 mb-6">
                {midas.logoUrl && (
                  <img
                    src={`/api/proxy/image?url=${encodeURIComponent(midas.logoUrl)}`}
                    alt={midas.name}
                    className="w-16 h-16 rounded-xl object-contain border border-gray-100 p-1 bg-white"
                    onError={e => { e.target.style.display = 'none'; }}
                  />
                )}
                <h2 className="text-xl font-bold text-gray-900">{midas.name ?? ticker}</h2>
              </div>
              {midas.description && midas.description.split('\n\n').map((para, i) => (
                <p key={i} className="text-sm text-gray-600 leading-relaxed mb-4 last:mb-0">{para}</p>
              ))}
            </div>
          )}

          {!midas && !chart && (
            <div className="bg-white rounded-2xl border border-gray-200 p-12 text-center">
              <p className="text-gray-400">{t('Bu hisse için detay verisi bulunamadı.')}</p>
            </div>
          )}
        </>
      )}
    </div>
  );
}
