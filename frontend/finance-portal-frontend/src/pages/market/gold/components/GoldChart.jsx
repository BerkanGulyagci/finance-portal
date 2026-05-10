import { useEffect, useRef, useState, useCallback } from 'react';
import { init as klineInit, dispose as klineDispose, registerOverlay } from 'klinecharts';
import { Trash2, X } from 'lucide-react';

// ── Custom overlay kayıtları (bir kez) ───────────────────────────────────────
let _overlaysRegistered = false;
function ensureOverlays() {
  if (_overlaysRegistered) return;
  _overlaysRegistered = true;
  try {
    registerOverlay({
      name: 'customRect', totalStep: 3,
      needDefaultPointFigure: true, needDefaultXAxisFigure: true, needDefaultYAxisFigure: true,
      createPointFigures: ({ coordinates }) => {
        if (coordinates.length === 2) {
          const x = Math.min(coordinates[0].x, coordinates[1].x);
          const y = Math.min(coordinates[0].y, coordinates[1].y);
          return [{ type: 'rect', attrs: { x, y, width: Math.abs(coordinates[1].x-coordinates[0].x), height: Math.abs(coordinates[1].y-coordinates[0].y) },
            styles: { style:'stroke_fill', color:'rgba(22,119,255,0.15)', borderColor:'#1677ff', borderSize:1 } }];
        }
        return [];
      },
    });
    registerOverlay({
      name: 'customCircle', totalStep: 3,
      needDefaultPointFigure: true, needDefaultXAxisFigure: true, needDefaultYAxisFigure: true,
      createPointFigures: ({ coordinates }) => {
        if (coordinates.length === 2) {
          const dx = coordinates[1].x-coordinates[0].x, dy = coordinates[1].y-coordinates[0].y;
          return [{ type:'circle', attrs:{ x:coordinates[0].x, y:coordinates[0].y, r:Math.sqrt(dx*dx+dy*dy) },
            styles:{ style:'stroke_fill', color:'rgba(22,119,255,0.15)', borderColor:'#1677ff', borderSize:1 } }];
        }
        return [];
      },
    });
  } catch (_) {}
}

// ── Drawing tools ─────────────────────────────────────────────────────────────
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

const ALL_DRAWING_IDS = DRAWING_TOOLS.flatMap(g => g.tools).map(t => t.id);

// ── Alt indikatörler ──────────────────────────────────────────────────────────
const SUB_INDICATORS = [
  { name: 'VOL',  label: 'Hacim', color: '#6b7280' },
  { name: 'RSI',  label: 'RSI',   color: '#f59e0b' },
  { name: 'MACD', label: 'MACD',  color: '#3b82f6' },
  { name: 'KDJ',  label: 'KDJ',   color: '#8b5cf6' },
];

// ── Drawing Toolbar ───────────────────────────────────────────────────────────
function DrawingToolbar({ activeTool, onSelectTool, onDeleteSelected, onClearAll }) {
  const [openGroup, setOpenGroup] = useState(null);

  useEffect(() => {
    if (!openGroup) return;
    const h = (e) => { if (!e.target.closest('[data-dg]')) setOpenGroup(null); };
    document.addEventListener('mousedown', h);
    return () => document.removeEventListener('mousedown', h);
  }, [openGroup]);

  return (
    <div className="flex flex-wrap items-center gap-1.5 p-2 bg-gray-50 border border-gray-200 rounded-xl">
      {DRAWING_TOOLS.map(({ group, tools }) => (
        <div key={group} className="relative" data-dg="1">
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
                <button key={tool.id}
                  onClick={() => { onSelectTool(tool.id === activeTool ? null : tool.id); setOpenGroup(null); }}
                  className={`w-full flex items-center gap-2.5 px-3 py-2 rounded-lg text-xs transition-all ${
                    tool.id === activeTool ? 'bg-[#093eaa] text-white' : 'text-gray-700 hover:bg-gray-50'
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

// ── Floating tooltip ──────────────────────────────────────────────────────────
function FloatingTooltip({ tooltip, containerRef, currency }) {
  if (!tooltip || !containerRef.current) return null;
  const sym = currency === 'USD' ? '$' : '₺';
  const { x, y, date, close, high, low, containerWidth } = tooltip;
  const tipW = 160;
  const left = x + tipW > containerWidth ? x - tipW - 12 : x + 12;
  const top  = Math.max(8, y - 40);
  const fmtP = v => v != null && !isNaN(v)
    ? parseFloat(v).toLocaleString('tr-TR', { minimumFractionDigits:2, maximumFractionDigits:2 }) : '-';
  const fmtD = ts => { try { return new Date(ts).toLocaleDateString('tr-TR', { year:'numeric', month:'2-digit', day:'2-digit' }); } catch { return ''; } };
  const showHiLo = high != null && low != null && (high !== close || low !== close);
  return (
    <div style={{ position:'absolute', left, top, pointerEvents:'none', zIndex:20, minWidth:130 }}
      className="bg-gray-900/90 text-white rounded-xl px-3 py-2.5 shadow-xl backdrop-blur-sm">
      <p className="text-xs text-gray-400 mb-1 font-medium">{fmtD(date)}</p>
      <p className="text-lg font-black leading-tight">{sym}{fmtP(close)}</p>
      {showHiLo && (
        <p className="text-xs text-gray-400 mt-1.5">
          <span className="text-emerald-400 font-semibold">↑</span> {sym}{fmtP(high)}
          <span className="mx-1.5 text-gray-600">·</span>
          <span className="text-rose-400 font-semibold">↓</span> {sym}{fmtP(low)}
        </p>
      )}
    </div>
  );
}

// ── Ortak chart hook ──────────────────────────────────────────────────────────
function useGoldKline({ idRef, points, buildStyles, buildKlineData, isLine, currency,
                        showMA7, showMA30, showMA90, showTrend,
                        activeSubInds, subPaneIds, onChartReady }) {
  const chartRef     = useRef(null);
  const containerRef = useRef(null);
  const klineDataRef = useRef([]);
  const [tooltip, setTooltip] = useState(null);

  // Chart oluştur — sadece points değişince
  useEffect(() => {
    if (!points?.length) return;
    ensureOverlays();
    const id    = idRef.current;
    const chart = klineInit(id);
    chartRef.current = chart;
    if (onChartReady) onChartReady(chart);

    chart.setStyles(buildStyles());

    const kd = buildKlineData(points);
    klineDataRef.current = kd;
    if (!kd.length) { klineDispose(id); return; }
    chart.applyNewData(kd);

    // MA
    const maP = [...(showMA7?[7]:[]), ...(showMA30?[30]:[]), ...(showMA90?[90]:[])];
    if (maP.length) try { chart.createIndicator({ name:'MA', calcParams:maP }, false, { id:'candle_pane' }); } catch(_){}
    if (showTrend) try { chart.createIndicator({ name:'EMA', calcParams:[20],
      styles:{ lines:[{ color:'#ef4444', size:1.5 }] } }, false, { id:'candle_pane' }); } catch(_){}

    // Alt indikatörler
    subPaneIds.current = {};
    activeSubInds.forEach(name => {
      try { const pid = chart.createIndicator({ name }, true, { height:80 }); if (pid) subPaneIds.current[name] = pid; } catch(_){}
    });

    // Crosshair (sadece çizgi grafikte)
    if (isLine) {
      chart.subscribeAction('onCrosshairChange', (data) => {
        if (!data || data.dataIndex == null || data.dataIndex < 0) { setTooltip(null); return; }
        const bar = klineDataRef.current[data.dataIndex];
        if (!bar) { setTooltip(null); return; }
        const cw = containerRef.current?.getBoundingClientRect().width ?? 600;
        setTooltip({ x:data.x??0, y:data.y??0, date:bar.timestamp,
          close:bar.close, high:bar.high, low:bar.low, containerWidth:cw });
      });
    }

    return () => {
      setTooltip(null);
      klineDispose(id);
      chartRef.current = null;
      subPaneIds.current = {};
      if (onChartReady) onChartReady(null);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [points]);

  // MA toggle — dispose etmeden
  useEffect(() => {
    const chart = chartRef.current;
    if (!chart) return;
    try { chart.removeIndicator('candle_pane', 'MA'); } catch(_){}
    const maP = [...(showMA7?[7]:[]), ...(showMA30?[30]:[]), ...(showMA90?[90]:[])];
    if (maP.length) try { chart.createIndicator({ name:'MA', calcParams:maP }, false, { id:'candle_pane' }); } catch(_){}
  }, [showMA7, showMA30, showMA90]);

  // Trend toggle
  useEffect(() => {
    const chart = chartRef.current;
    if (!chart) return;
    try { chart.removeIndicator('candle_pane', 'EMA'); } catch(_){}
    if (showTrend) try { chart.createIndicator({ name:'EMA', calcParams:[20],
      styles:{ lines:[{ color:'#ef4444', size:1.5 }] } }, false, { id:'candle_pane' }); } catch(_){}
  }, [showTrend]);

  // Alt indikatör toggle — dispose etmeden
  useEffect(() => {
    const chart = chartRef.current;
    if (!chart) return;
    // Mevcut alt indikatörleri kaldır
    Object.entries(subPaneIds.current).forEach(([name, paneId]) => {
      try { chart.removeIndicator(paneId, name); } catch(_){}
    });
    subPaneIds.current = {};
    // Aktif olanları ekle
    activeSubInds.forEach(name => {
      try { const pid = chart.createIndicator({ name }, true, { height:80 }); if (pid) subPaneIds.current[name] = pid; } catch(_){}
    });
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeSubInds]);

  return { chartRef, containerRef, tooltip };
}

// ── Çizgi grafiği ─────────────────────────────────────────────────────────────
function GoldLineChart({ points, isDown, showMA7, showMA30, showMA90, showTrend,
                         activeSubInds, subPaneIds, currency, onChartReady }) {
  const idRef = useRef(null);
  if (!idRef.current) idRef.current = `gold_line_${Math.random().toString(36).slice(2)}`;

  const color = isDown ? '#ef4444' : '#10b981';

  const buildStyles = useCallback(() => ({
    candle: {
      type: 'area',
      area: { lineColor: color, lineSize: 2, value: 'close', smooth: true,
        backgroundColor: [{ offset:0, color:color+'33' }, { offset:1, color:color+'00' }] },
      tooltip: { showRule: 'none' },
    },
    crosshair: { show:true,
      horizontal:{ show:true, line:{ style:'dashed', color:'#9ca3af', size:1 } },
      vertical:  { show:true, line:{ style:'dashed', color:'#9ca3af', size:1 } },
    },
  }), [color]);

  const buildKlineData = useCallback((pts) =>
    pts.map(p => {
      const close = parseFloat(p.displayClose ?? p.close) || 0;
      const high  = p.displayHigh != null ? parseFloat(p.displayHigh) : close;
      const low   = p.displayLow  != null ? parseFloat(p.displayLow)  : close;
      const open  = p.displayOpen != null ? parseFloat(p.displayOpen) : close;
      return { timestamp: new Date(p.date).getTime(),
        open:isNaN(open)?close:open, high:isNaN(high)?close:high,
        low:isNaN(low)?close:low, close, volume:p.volume??0, turnover:0 };
    }).filter(d => !isNaN(d.close) && d.close > 0).sort((a,b) => a.timestamp - b.timestamp),
  []);

  const { containerRef, tooltip } = useGoldKline({
    idRef, points, buildStyles, buildKlineData, isLine: true, currency,
    showMA7, showMA30, showMA90, showTrend, activeSubInds, subPaneIds, onChartReady,
  });

  if (!points?.length) return <GoldNoData />;
  return (
    <div ref={containerRef} style={{ position:'relative' }}>
      <div id={idRef.current} style={{ width:'100%', height:'320px' }} />
      <FloatingTooltip tooltip={tooltip} containerRef={containerRef} currency={currency} />
    </div>
  );
}

// ── Mum grafiği ───────────────────────────────────────────────────────────────
function GoldCandleChart({ points, showMA7, showMA30, showMA90, showTrend,
                           activeSubInds, subPaneIds, onChartReady }) {
  const idRef = useRef(null);
  if (!idRef.current) idRef.current = `gold_candle_${Math.random().toString(36).slice(2)}`;

  const buildStyles = useCallback(() => ({
    candle: { type:'candle_solid', tooltip:{ showRule:'follow_cross' } },
    crosshair: { show:true,
      horizontal:{ show:true, line:{ style:'dashed', color:'#9ca3af', size:1 } },
      vertical:  { show:true, line:{ style:'dashed', color:'#9ca3af', size:1 } },
    },
  }), []);

  const buildKlineData = useCallback((pts) =>
    pts.map(p => ({
      timestamp: new Date(p.date).getTime(),
      open:  parseFloat(p.displayOpen  ?? p.open  ?? p.close) || 0,
      high:  parseFloat(p.displayHigh  ?? p.high  ?? p.close) || 0,
      low:   parseFloat(p.displayLow   ?? p.low   ?? p.close) || 0,
      close: parseFloat(p.displayClose ?? p.close) || 0,
      volume: p.volume ?? 0, turnover: 0,
    })).filter(d => !isNaN(d.close) && d.close > 0).sort((a,b) => a.timestamp - b.timestamp),
  []);

  const { containerRef } = useGoldKline({
    idRef, points, buildStyles, buildKlineData, isLine: false,
    showMA7, showMA30, showMA90, showTrend, activeSubInds, subPaneIds, onChartReady,
  });

  if (!points?.length) return <GoldNoData />;
  return <div id={idRef.current} style={{ width:'100%', height:'320px' }} />;
}

function GoldNoData() {
  return (
    <div className="flex items-center justify-center h-[320px] text-gray-400 text-sm">
      Grafik verisi yüklenemedi.
    </div>
  );
}

// ── Ana export ────────────────────────────────────────────────────────────────
export default function GoldChart({
  points, chartMode, isDown, loading,
  showMA7, showMA30, showMA90, showTrend, currency,
}) {
  const [activeSubInds, setActiveSubInds] = useState([]);
  const subPaneIds  = useRef({});
  const chartRef    = useRef(null);
  const [activeTool, setActiveTool] = useState(null);

  // ESC ile çizim aracını kapat
  useEffect(() => {
    const h = (e) => { if (e.key === 'Escape') setActiveTool(null); };
    window.addEventListener('keydown', h);
    return () => window.removeEventListener('keydown', h);
  }, []);

  const handleChartReady = useCallback((chart) => { chartRef.current = chart; }, []);

  const handleSelectTool = useCallback((toolId) => {
    setActiveTool(toolId);
    if (chartRef.current && toolId) {
      try { chartRef.current.createOverlay({ name: toolId }); } catch(_){}
    }
  }, []);

  const handleDeleteSelected = useCallback(() => {
    try { chartRef.current?.removeOverlay(); } catch(_){}
  }, []);

  const handleClearAll = useCallback(() => {
    ALL_DRAWING_IDS.forEach(id => {
      try { chartRef.current?.removeOverlay({ name: id }); } catch(_){}
    });
    setActiveTool(null);
  }, []);

  const toggleSubInd = useCallback((name) => {
    setActiveSubInds(prev =>
      prev.includes(name) ? prev.filter(n => n !== name) : [...prev, name]
    );
  }, []);

  return (
    <div className="space-y-2">
      {/* Alt indikatörler */}
      <div className="flex items-center gap-1.5 flex-wrap">
        <span className="text-xs text-gray-400 font-medium">İndikatör:</span>
        {SUB_INDICATORS.map(({ name, label, color }) => {
          const active = activeSubInds.includes(name);
          return (
            <button key={name} onClick={() => toggleSubInd(name)}
              className={`px-2.5 py-1 rounded-lg text-xs font-semibold border transition-all ${
                active ? 'text-white border-transparent' : 'bg-gray-50 text-gray-500 border-gray-200 hover:bg-gray-100'
              }`}
              style={active ? { backgroundColor: color } : {}}>
              {label}
            </button>
          );
        })}
      </div>

      {/* Drawing toolbar */}
      <DrawingToolbar
        activeTool={activeTool}
        onSelectTool={handleSelectTool}
        onDeleteSelected={handleDeleteSelected}
        onClearAll={handleClearAll}
      />

      {/* Grafik */}
      <div className="relative">
        {loading && (
          <div className="absolute inset-0 flex items-center justify-center bg-white/70 z-10 rounded-xl">
            <div className="flex gap-1.5">
              <div className="w-2 h-2 bg-[#093eaa] rounded-full animate-bounce" />
              <div className="w-2 h-2 bg-[#093eaa]/60 rounded-full animate-bounce [animation-delay:100ms]" />
              <div className="w-2 h-2 bg-[#093eaa]/30 rounded-full animate-bounce [animation-delay:200ms]" />
            </div>
          </div>
        )}
        {chartMode === 'candle' ? (
          <GoldCandleChart
            points={points} showMA7={showMA7} showMA30={showMA30}
            showMA90={showMA90} showTrend={showTrend}
            activeSubInds={activeSubInds} subPaneIds={subPaneIds}
            onChartReady={handleChartReady}
          />
        ) : (
          <GoldLineChart
            points={points} isDown={isDown} showMA7={showMA7} showMA30={showMA30}
            showMA90={showMA90} showTrend={showTrend} currency={currency}
            activeSubInds={activeSubInds} subPaneIds={subPaneIds}
            onChartReady={handleChartReady}
          />
        )}
      </div>
    </div>
  );
}
