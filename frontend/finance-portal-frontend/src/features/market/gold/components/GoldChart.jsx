import { useEffect, useRef, useState, useCallback } from 'react';
import { init as klineInit, dispose as klineDispose, registerOverlay } from 'klinecharts';
import { Trash2, X, ChevronDown } from 'lucide-react';
import { useTranslation } from '../../../../context/LanguageContext';
import { useTheme } from '../../../../context/ThemeContext';
import IndicatorMenu from '../../../../components/common/IndicatorMenu';
import { useChartDrawings } from '../../../../hooks/useChartDrawings';

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

// MA çizgi renkleri (buton renkleriyle aynı) — MA20/50/200
const GOLD_MA_COLORS = { 20: '#f59e0b', 50: '#a855f7', 200: '#ef4444' };

// ── Alt indikatörler ──────────────────────────────────────────────────────────
const SUB_INDICATORS = [
  { name: 'VOL',  label: 'Hacim', color: '#6b7280' },
  { name: 'RSI',  label: 'RSI',   color: '#f59e0b' },
  { name: 'MACD', label: 'MACD',  color: '#3b82f6' },
  { name: 'KDJ',  label: 'KDJ',   color: '#8b5cf6' },
];

// ── Drawing Toolbar ───────────────────────────────────────────────────────────
function DrawingToolbar({ activeTool, onSelectTool, onDeleteSelected, onClearAll, indicatorSlot }) {
  const { t } = useTranslation();
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
                <button key={tool.id}
                  onClick={() => { onSelectTool(tool.id === activeTool ? null : tool.id); setOpenGroup(null); }}
                  className={`w-full flex items-center gap-2.5 px-3 py-2 rounded-lg text-xs transition-all ${
                    tool.id === activeTool ? 'bg-[#093eaa] text-white' : 'text-gray-700 hover:bg-gray-50'
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
      {indicatorSlot && (
        <>
          <div className="w-px h-6 bg-gray-200 mx-1" />
          {indicatorSlot}
        </>
      )}
      <div className="w-px h-6 bg-gray-200 mx-1" />
      <button onClick={onDeleteSelected} title={t('Seçili çizimi sil')}
        className="inline-flex items-center px-2 py-1.5 rounded-lg text-gray-500 hover:bg-red-50 hover:text-red-500 hover:border-red-200 transition-all border border-gray-200">
        <Trash2 className="w-3.5 h-3.5" />
      </button>
      <button onClick={onClearAll} title={t('Tüm çizimleri temizle')}
        className="inline-flex items-center px-2 py-1.5 rounded-lg text-gray-500 hover:bg-red-50 hover:text-red-500 hover:border-red-200 transition-all border border-gray-200">
        <X className="w-3.5 h-3.5" />
      </button>
      {activeTool && (
        <span className="ml-auto text-xs text-[#093eaa] font-medium bg-blue-50 px-2 py-1 rounded-lg">
          {(() => { const lbl = DRAWING_TOOLS.flatMap(g => g.tools).find(it => it.id === activeTool)?.label; return lbl ? t(lbl) : activeTool; })()}
          <button onClick={() => onSelectTool(null)} className="ml-1.5 opacity-60 hover:opacity-100">✕</button>
        </span>
      )}
    </div>
  );
}

// ── Floating tooltip (tema uyumlu: açık→beyaz/siyah, koyu→koyu/beyaz) ──────────
function FloatingTooltip({ tooltip, containerRef, currency }) {
  const { isDark } = useTheme();
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
  const card = isDark ? 'bg-slate-900/90 text-white ring-white/10' : 'bg-white/95 text-gray-900 ring-black/10';
  const subText = isDark ? 'text-slate-400' : 'text-gray-500';
  return (
    <div style={{ position:'absolute', left, top, pointerEvents:'none', zIndex:20, minWidth:130 }}
      className={`rounded-xl px-3 py-2.5 shadow-xl ring-1 backdrop-blur-md ${card}`}>
      <p className={`text-xs mb-1 font-medium ${subText}`}>{fmtD(date)}</p>
      <p className="text-lg font-black leading-tight">{sym}{fmtP(close)}</p>
      {showHiLo && (
        <p className={`text-xs mt-1.5 ${subText}`}>
          <span className="text-emerald-500 font-semibold">↑</span> {sym}{fmtP(high)}
          <span className="mx-1.5 opacity-40">·</span>
          <span className="text-rose-500 font-semibold">↓</span> {sym}{fmtP(low)}
        </p>
      )}
    </div>
  );
}

// ── Ortak chart hook ──────────────────────────────────────────────────────────
function useGoldKline({ idRef, points, buildStyles, buildKlineData, isLine, currency,
                        showMA20, showMA50, showMA200, showTrend,
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
    chart.setStyles(buildStyles());

    const kd = buildKlineData(points);
    klineDataRef.current = kd;
    if (!kd.length) { klineDispose(id); return; }
    chart.applyNewData(kd);

    // Veri uygulandıktan SONRA hazır bildir (çizimler veri üstüne doğru otursun) — DOM id'si + klineData.
    if (onChartReady) onChartReady(chart, id, kd);

    // MA
    const maP = [...(showMA20 && points.length>=20?[20]:[]), ...(showMA50 && points.length>=50?[50]:[]), ...(showMA200 && points.length>=200?[200]:[])];
    if (maP.length) try { chart.createIndicator({ name:'MA', calcParams:maP, styles:{ lines: maP.map(p => ({ color: GOLD_MA_COLORS[p], size:1.5 })) } }, false, { id:'candle_pane' }); } catch(_){}
    if (showTrend) try { chart.createIndicator({ name:'EMA', calcParams:[20],
      styles:{ lines:[{ color:'#10b981', size:1.5 }] } }, false, { id:'candle_pane' }); } catch(_){}

    // Alt indikatörler
    subPaneIds.current = {};
    activeSubInds.forEach(name => {
      try { const pid = chart.createIndicator({ name }, true, { height:80 }); if (pid) subPaneIds.current[name] = pid; } catch(_){}
    });

    // Crosshair (sadece çizgi grafikte)
    const containerEl = containerRef.current;
    const clearTooltip = () => setTooltip(null);
    if (isLine) {
      chart.subscribeAction('onCrosshairChange', (data) => {
        if (!data || data.dataIndex == null || data.dataIndex < 0) { setTooltip(null); return; }
        const bar = klineDataRef.current[data.dataIndex];
        if (!bar) { setTooltip(null); return; }
        const cw = containerRef.current?.getBoundingClientRect().width ?? 600;
        setTooltip({ x:data.x??0, y:data.y??0, date:bar.timestamp,
          close:bar.close, high:bar.high, low:bar.low, containerWidth:cw });
      });
      // Mouse grafikten çıkınca tooltip'i temizle — klinecharts mouse-out'ta onCrosshairChange'i
      // güvenilir tetiklemiyor, hover kartı ekranda kalıyordu (native mouseleave ile garanti).
      if (containerEl) containerEl.addEventListener('mouseleave', clearTooltip);
    }

    return () => {
      if (containerEl) containerEl.removeEventListener('mouseleave', clearTooltip);
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
    const maP = [...(showMA20 && points.length>=20?[20]:[]), ...(showMA50 && points.length>=50?[50]:[]), ...(showMA200 && points.length>=200?[200]:[])];
    if (maP.length) try { chart.createIndicator({ name:'MA', calcParams:maP, styles:{ lines: maP.map(p => ({ color: GOLD_MA_COLORS[p], size:1.5 })) } }, false, { id:'candle_pane' }); } catch(_){}
  }, [showMA20, showMA50, showMA200]);

  // Trend toggle
  useEffect(() => {
    const chart = chartRef.current;
    if (!chart) return;
    try { chart.removeIndicator('candle_pane', 'EMA'); } catch(_){}
    if (showTrend) try { chart.createIndicator({ name:'EMA', calcParams:[20],
      styles:{ lines:[{ color:'#10b981', size:1.5 }] } }, false, { id:'candle_pane' }); } catch(_){}
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
function GoldLineChart({ points, isDown, showMA20, showMA50, showMA200, showTrend,
                         activeSubInds, subPaneIds, currency, onChartReady, height = 280 }) {
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
    showMA20, showMA50, showMA200, showTrend, activeSubInds, subPaneIds, onChartReady,
  });

  if (!points?.length) return <GoldNoData height={height} />;
  return (
    <div ref={containerRef} style={{ position:'relative' }}>
      <div id={idRef.current} style={{ width:'100%', height:`${height}px` }} />
      <FloatingTooltip tooltip={tooltip} containerRef={containerRef} currency={currency} />
    </div>
  );
}

// ── Mum grafiği ───────────────────────────────────────────────────────────────
function GoldCandleChart({ points, showMA20, showMA50, showMA200, showTrend,
                           activeSubInds, subPaneIds, onChartReady, height = 280 }) {
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
    showMA20, showMA50, showMA200, showTrend, activeSubInds, subPaneIds, onChartReady,
  });

  if (!points?.length) return <GoldNoData height={height} />;
  return <div id={idRef.current} style={{ width:'100%', height:`${height}px` }} />;
}

function GoldNoData({ height = 280 }) {
  const { t } = useTranslation();
  return (
    <div className="flex items-center justify-center text-gray-400 text-sm" style={{ height: `${height}px` }}>
      {t('Grafik verisi yüklenemedi.')}
    </div>
  );
}

// ── Ana export ────────────────────────────────────────────────────────────────
export default function GoldChart({
  points, chartMode, isDown, loading,
  showMA20, showMA50, showMA200, showTrend, currency,
  onToggleMA20, onToggleMA50, onToggleMA200, onToggleTrend, dataLen = 0,
  height = 280,
  persistId = null,   // çizim kalıcılık anahtarı (ör. "gold:gram"); yoksa kalıcılık kapalı
}) {
  const { t } = useTranslation();
  const [activeSubInds, setActiveSubInds] = useState([]);
  const subPaneIds  = useRef({});
  const chartRef    = useRef(null);
  const chartIdRef  = useRef(null);

  // Çizim KALICILIĞI — hisse CandlestickChart ile AYNI hook. Çizim araçlarını (handleSelectTool)
  // ve sil/temizle'yi hook'tan alıyoruz; hook overlay'leri kendi event'leriyle (onDrawEnd→kaydet)
  // oluşturduğu için çizimler localStorage/sunucuya yazılır ve restoreOverlays ile geri yüklenir.
  // persistKey null (persistId verilmemiş) ise hook overlay oluşturur ama kalıcılık yapmaz.
  const {
    activeTool, setActiveTool,
    handleSelectTool, handleDeleteSelected, handleClearAll,
    restoreOverlays,
  } = useChartDrawings({
    chartRef,
    chartIdRef,
    persistKey: persistId ? `chart-overlays:${persistId}` : null,
  });

  // ESC ile çizim aracını kapat
  useEffect(() => {
    const h = (e) => { if (e.key === 'Escape') setActiveTool(null); };
    window.addEventListener('keydown', h);
    return () => window.removeEventListener('keydown', h);
  }, [setActiveTool]);

  const handleChartReady = useCallback((chart, domId, klineData) => {
    chartRef.current = chart;
    // chartIdRef = grafik DOM element id'si (useChartDrawings getElementById ile mouseup dinler).
    if (domId) chartIdRef.current = domId;
    // Chart hazır + veri uygulandı → kayıtlı çizimleri geri yükle (klineData ile pencere filtresi).
    if (chart) restoreOverlays(klineData);
  }, [restoreOverlays]);

  const handleClearAllLocal = useCallback(() => {
    handleClearAll();
    ALL_DRAWING_IDS.forEach(id => {
      try { chartRef.current?.removeOverlay({ name: id }); } catch(_){}
    });
    setActiveTool(null);
  }, [handleClearAll, setActiveTool]);

  const toggleSubInd = useCallback((name) => {
    setActiveSubInds(prev =>
      prev.includes(name) ? prev.filter(n => n !== name) : [...prev, name]
    );
  }, []);

  const indicatorMenu = (
    <IndicatorMenu
      maDefs={[{ period: 20, color: '#f59e0b', label: 'MA20' }, { period: 50, color: '#a855f7', label: 'MA50' }, { period: 200, color: '#ef4444', label: 'MA200' }]}
      activeMAs={[...(showMA20 ? [20] : []), ...(showMA50 ? [50] : []), ...(showMA200 ? [200] : [])]}
      onToggleMA={(p) => { if (p === 20) onToggleMA20?.(); else if (p === 50) onToggleMA50?.(); else onToggleMA200?.(); }}
      dataLen={dataLen}
      subDefs={SUB_INDICATORS}
      activeSubs={activeSubInds}
      onToggleSub={toggleSubInd}
      extras={[{ key: 'trend', label: 'Trend', color: '#10b981', active: showTrend, onToggle: () => onToggleTrend?.() }]}
    />
  );

  return (
    <div className="space-y-2">
      {/* Drawing toolbar — İndikatör dropdown dahil (MA + Trend + osilatörler; hisse detay tarzı) */}
      <DrawingToolbar
        activeTool={activeTool}
        onSelectTool={handleSelectTool}
        onDeleteSelected={handleDeleteSelected}
        onClearAll={handleClearAllLocal}
        indicatorSlot={indicatorMenu}
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
            points={points} showMA20={showMA20} showMA50={showMA50}
            showMA200={showMA200} showTrend={showTrend}
            activeSubInds={activeSubInds} subPaneIds={subPaneIds}
            onChartReady={handleChartReady} height={height}
          />
        ) : (
          <GoldLineChart
            points={points} isDown={isDown} showMA20={showMA20} showMA50={showMA50}
            showMA200={showMA200} showTrend={showTrend} currency={currency}
            activeSubInds={activeSubInds} subPaneIds={subPaneIds}
            onChartReady={handleChartReady} height={height}
          />
        )}
      </div>
    </div>
  );
}
