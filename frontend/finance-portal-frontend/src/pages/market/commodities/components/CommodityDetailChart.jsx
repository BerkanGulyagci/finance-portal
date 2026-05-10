import { useEffect, useRef, useState, useCallback } from 'react';
import { init as klineInit, dispose as klineDispose, registerOverlay } from 'klinecharts';
import { Trash2, X } from 'lucide-react';

// ── Custom Overlay Kayıtları ───────────────────────────────────────────────────
// Dikdörtgen
try {
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
} catch (_) { /* zaten kayıtlıysa sessizce geç */ }

// Çember
try {
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
} catch (_) { /* zaten kayıtlıysa sessizce geç */ }

// ── Drawing Toolbar ────────────────────────────────────────────────────────────

const DRAWING_TOOLS = [
  { group: 'Çizgiler', tools: [
    { id: 'segment',                label: 'Çizgi Segmenti',   icon: '╱' },
    { id: 'straightLine',           label: 'Düz Çizgi (∞)',    icon: '⟵⟶' },
    { id: 'rayLine',                label: 'Işın',             icon: '⟶' },
    { id: 'horizontalStraightLine', label: 'Yatay Çizgi',      icon: '─' },
    { id: 'verticalStraightLine',   label: 'Dikey Çizgi',      icon: '│' },
    { id: 'priceLine',              label: 'Fiyat Çizgisi',    icon: '₺─' },
  ]},
  { group: 'Kanallar', tools: [
    { id: 'parallelStraightLine',   label: 'Paralel Kanal',    icon: '⫽' },
    { id: 'priceChannelLine',       label: 'Fiyat Kanalı',     icon: '▱' },
  ]},
  { group: 'Fibonacci', tools: [
    { id: 'fibonacciLine',          label: 'Fibonacci',        icon: 'Fib' },
  ]},
  { group: 'Şekiller', tools: [
    { id: 'customRect',   label: 'Dikdörtgen', icon: '□' },
    { id: 'customCircle', label: 'Çember',     icon: '○' },
  ]},
];

function DrawingToolbar({ activeTool, onSelectTool, onDeleteSelected, onClearAll }) {
  const [openGroup, setOpenGroup] = useState(null);

  return (
    <div className="flex flex-wrap items-center gap-1.5 p-2 bg-gray-50 border border-gray-200 rounded-xl mb-2">
      {DRAWING_TOOLS.map(({ group, tools }) => (
        <div key={group} className="relative">
          <button
            onClick={() => setOpenGroup(openGroup === group ? null : group)}
            className={`px-2.5 py-1.5 rounded-lg text-xs font-semibold transition-all border ${
              tools.some(t => t.id === activeTool)
                ? 'bg-[#093eaa] text-white border-[#093eaa]'
                : 'bg-white text-gray-600 border-gray-200 hover:border-[#093eaa] hover:text-[#093eaa]'
            }`}
          >
            {group} ▾
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
                  <span>{tool.label}</span>
                </button>
              ))}
            </div>
          )}
        </div>
      ))}

      <div className="w-px h-6 bg-gray-200 mx-1" />

      <button onClick={onDeleteSelected} title="Seçili çizimi sil"
        className="p-1.5 rounded-lg text-gray-500 hover:bg-red-50 hover:text-red-500 transition-all border border-gray-200">
        <Trash2 className="w-3.5 h-3.5" />
      </button>
      <button onClick={onClearAll} title="Tüm çizimleri temizle"
        className="p-1.5 rounded-lg text-gray-500 hover:bg-red-50 hover:text-red-500 transition-all border border-gray-200">
        <X className="w-3.5 h-3.5" />
      </button>

      {activeTool && (
        <span className="ml-auto text-xs text-[#093eaa] font-medium bg-blue-50 px-2 py-1 rounded-lg">
          {DRAWING_TOOLS.flatMap(g => g.tools).find(t => t.id === activeTool)?.label ?? activeTool}
          <button onClick={() => onSelectTool(null)} className="ml-1.5 opacity-60 hover:opacity-100">✕</button>
        </span>
      )}
    </div>
  );
}

// ── MA ve Alt İndikatör Tanımları ─────────────────────────────────────────────

const MA_PERIODS = [
  { period: 7,  color: '#f59e0b', label: 'MA7'  },
  { period: 30, color: '#8b5cf6', label: 'MA30' },
  { period: 90, color: '#ef4444', label: 'MA90' },
];

const SUB_INDICATORS = [
  { name: 'VOL',  label: 'Hacim', color: '#6b7280' },
  { name: 'RSI',  label: 'RSI',   color: '#f59e0b' },
  { name: 'MACD', label: 'MACD',  color: '#3b82f6' },
  { name: 'KDJ',  label: 'KDJ',   color: '#8b5cf6' },
];

// ── Ana Grafik Bileşeni ───────────────────────────────────────────────────────

export default function CommodityDetailChart({ points, chartMode, loading }) {
  const chartId  = useRef(`commodity_chart_${Date.now()}`);
  const chartRef = useRef(null);
  const indicatorPaneIds = useRef({});

  const [activeMAs,      setActiveMAs]      = useState([]);
  const [activeSubInds,  setActiveSubInds]  = useState([]);
  const [activeTool,     setActiveTool]     = useState(null);

  // ── MA toggle ──────────────────────────────────────────────────────────────
  const applyMA = useCallback((periods) => {
    const chart = chartRef.current;
    if (!chart) return;
    chart.removeIndicator('candle_pane', 'MA');
    if (periods.length > 0) {
      chart.createIndicator(
        { name: 'MA', calcParams: periods },
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

  // ── Alt indikatör toggle ───────────────────────────────────────────────────
  const toggleSubIndicator = useCallback((indName) => {
    const chart = chartRef.current;
    if (!chart) return;

    setActiveSubInds(prev => {
      const isActive = prev.includes(indName);
      if (isActive) {
        const paneId = indicatorPaneIds.current[indName];
        if (paneId) {
          try { chart.removeIndicator(paneId, indName); } catch {}
          delete indicatorPaneIds.current[indName];
        }
        return prev.filter(n => n !== indName);
      } else {
        try {
          const paneId = chart.createIndicator({ name: indName }, true, { height: 80 });
          if (paneId) indicatorPaneIds.current[indName] = paneId;
        } catch {}
        return [...prev, indName];
      }
    });
  }, []);

  // ── Drawing toolbar ────────────────────────────────────────────────────────
  const handleSelectTool = useCallback((toolId) => {
    setActiveTool(toolId);
    if (chartRef.current && toolId) {
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

  // ── Grafik render ──────────────────────────────────────────────────────────
  useEffect(() => {
    if (!points?.length) return;

    const id = chartId.current;
    const chart = klineInit(id);
    chartRef.current = chart;
    indicatorPaneIds.current = {};

    const isCandle = chartMode === 'candle';

    // Veriyi KlineCharts formatına çevir
    const klineData = points
      .map(p => {
        const close = parseFloat(p.displayClose ?? p.rawClose ?? 0);
        const open  = parseFloat(p.displayOpen  ?? p.rawOpen  ?? close);
        const high  = parseFloat(p.displayHigh  ?? p.rawHigh  ?? close);
        const low   = parseFloat(p.displayLow   ?? p.rawLow   ?? close);
        const vol   = p.volume != null ? parseFloat(p.volume) : 0;
        const ts    = p.timestamp
          ? p.timestamp * 1000
          : new Date(p.date).getTime();

        if (isNaN(close) || close <= 0) return null;
        return { timestamp: ts, open, high, low, close, volume: vol, turnover: 0 };
      })
      .filter(Boolean)
      .sort((a, b) => a.timestamp - b.timestamp);

    if (!klineData.length) return;

    const isDown = klineData[klineData.length - 1].close < klineData[0].close;
    const color  = isDown ? '#ef4444' : '#10b981';

    if (isCandle) {
      chart.setStyles({ candle: { type: 'candle_solid' } });
    } else {
      chart.setStyles({
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
    }

    chart.applyNewData(klineData);

    // Cleanup
    return () => {
      klineDispose(id);
      indicatorPaneIds.current = {};
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [points, chartMode]);

  // ── Render ─────────────────────────────────────────────────────────────────

  return (
    <div>
      {/* MA butonları */}
      <div className="flex items-center gap-3 mb-2 flex-wrap">
        <div className="flex gap-1 flex-wrap">
          {MA_PERIODS.map(({ period, color, label }) => {
            const active = activeMAs.includes(period);
            return (
              <button key={period} onClick={() => toggleMA(period)}
                className={`px-2.5 py-1 rounded-lg text-xs font-bold transition-all border ${
                  active ? 'text-white border-transparent' : 'bg-gray-50 text-gray-500 border-gray-200 hover:bg-gray-100'
                }`}
                style={active ? { backgroundColor: color, borderColor: color } : {}}>
                {label}
              </button>
            );
          })}
        </div>

        {/* Alt indikatörler */}
        <div className="flex gap-1 flex-wrap items-center">
          <span className="text-xs text-gray-400">İndikatör:</span>
          {SUB_INDICATORS.map(({ name, label, color }) => {
            const active = activeSubInds.includes(name);
            return (
              <button key={name} onClick={() => toggleSubIndicator(name)}
                className={`px-2.5 py-1 rounded-lg text-xs font-semibold transition-all border ${
                  active ? 'text-white border-transparent' : 'bg-gray-50 text-gray-500 border-gray-200 hover:bg-gray-100'
                }`}
                style={active ? { backgroundColor: color, borderColor: color } : {}}>
                {label}
              </button>
            );
          })}
        </div>
      </div>

      {/* Drawing Toolbar */}
      <DrawingToolbar
        activeTool={activeTool}
        onSelectTool={handleSelectTool}
        onDeleteSelected={handleDeleteSelected}
        onClearAll={handleClearAll}
      />

      {/* Grafik */}
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
        {!loading && !points?.length && (
          <div className="absolute inset-0 flex items-center justify-center text-gray-400 text-sm">
            Grafik verisi bulunamadı.
          </div>
        )}
        <div id={chartId.current} style={{ width: '100%', height: '460px' }} />
      </div>

      <p className="text-xs text-gray-400 mt-2">Kaynak: Yahoo Finance · OHLC verisi</p>
    </div>
  );
}
