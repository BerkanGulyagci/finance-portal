import { useEffect, useRef, useState, useCallback, useMemo } from 'react';
import { init as klineInit, dispose as klineDispose, registerOverlay } from 'klinecharts';
import { Trash2, X, ChevronDown, TrendingUp, TrendingDown } from 'lucide-react';
import { computeKlinePricePrecision, computeKlineVolumePrecision } from '../../../../utils/numberFormat';
import { useTranslation } from '../../../../context/LanguageContext';
import TrendBadge from '../../../../components/common/TrendBadge';
import IndicatorMenu from '../../../../components/common/IndicatorMenu';
import { buildTrendItem } from '../../../../utils/trendUtils';

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
              tools.some(t => t.id === activeTool) || openGroup === group
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

      {/* İndikatör menüsü (çizim gruplarının yanında — hisse detaydaki gibi) */}
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

// ── MA ve Alt İndikatör Tanımları ─────────────────────────────────────────────

const MA_PERIODS = [
  { period: 20,  color: '#f59e0b', label: 'MA20'  },
  { period: 50,  color: '#8b5cf6', label: 'MA50'  },
  { period: 200, color: '#ef4444', label: 'MA200' },
];

const SUB_INDICATORS = [
  { name: 'VOL',  label: 'Hacim', color: '#6b7280' },
  { name: 'RSI',  label: 'RSI',   color: '#f59e0b' },
  { name: 'MACD', label: 'MACD',  color: '#3b82f6' },
  { name: 'KDJ',  label: 'KDJ',   color: '#8b5cf6' },
];

// ── Ana Grafik Bileşeni ───────────────────────────────────────────────────────

export default function CommodityDetailChart({
  points,
  chartMode,
  loading,
  sourceNote,
  sourceWarning = null,
  valueFormatter = null,
}) {
  const { t } = useTranslation();
  const resolvedSourceNote = sourceNote ?? t('Kaynak: Yahoo Finance · OHLC verisi');
  const chartId  = useRef(`commodity_chart_${Date.now()}`);
  const chartRef = useRef(null);
  const indicatorPaneIds = useRef({});

  // Hover tooltip (yalnız çizgi modunda): imleci takip eden şık kart
  const wrapperRef   = useRef(null);
  const klineDataRef = useRef([]);
  const isLineRef    = useRef(true);
  const pricePrecRef = useRef(2);
  const fmtRef       = useRef(valueFormatter);
  fmtRef.current = valueFormatter;
  const [hover, setHover] = useState(null);
  const [pos,   setPos]   = useState({ x: 0, y: 0, flipX: false, flipY: false });

  const [activeMAs,      setActiveMAs]      = useState([]);
  const [activeSubInds,  setActiveSubInds]  = useState([]);
  const [activeTool,     setActiveTool]     = useState(null);

  // Trend rozeti — fiyat serisinden (MA20/50 + 52h konumu)
  const trendItem = useMemo(
    () => buildTrendItem((points ?? []).map(p => parseFloat(p.displayClose ?? p.rawClose)), 'COMMODITY'),
    [points],
  );

  // ── MA toggle ──────────────────────────────────────────────────────────────
  const applyMA = useCallback((periods) => {
    const chart = chartRef.current;
    if (!chart) return;
    chart.removeIndicator('candle_pane', 'MA');
    if (periods.length > 0) {
      const sorted = [...periods].sort((a, b) => a - b);
      const lines = sorted.map(p => ({ color: MA_PERIODS.find(m => m.period === p)?.color ?? '#888', size: 1.5 }));
      chart.createIndicator(
        { name: 'MA', calcParams: sorted, styles: { lines } },
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
    const id = chartId.current;

    if (!points?.length) {
      try { klineDispose(id); } catch { /* ignore */ }
      chartRef.current = null;
      indicatorPaneIds.current = {};
      return;
    }

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

    try {
      chart.setPriceVolumePrecision(
        computeKlinePricePrecision(klineData.map((d) => d.close)),
        computeKlineVolumePrecision(klineData.map((d) => d.volume)),
      );
    } catch (_) { /* klinecharts sürümü */ }

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

    // ── Hover tooltip mekanizması (yalnız çizgi modu) ──────────────────────────
    klineDataRef.current = klineData;
    isLineRef.current = !isCandle;
    try {
      pricePrecRef.current = computeKlinePricePrecision(klineData.map((d) => d.close));
    } catch (_) { /* varsayılan hassasiyet */ }
    if (!isCandle) {
      // Varsayılan (sol-üst) klinecharts metin tooltip'ini gizle — yerine özel kart gösteriyoruz.
      // (Candle modunda OHLC tooltip'i faydalı olduğu için dokunulmaz.)
      try { chart.setStyles({ candle: { tooltip: { showRule: 'none' } } }); } catch (_) {}
    }
    try {
      chart.subscribeAction('onCrosshairChange', (data) => {
        if (!isLineRef.current) { setHover(null); return; }
        const arr = klineDataRef.current;
        const idx = data?.dataIndex;
        if (idx == null || idx < 0 || !arr[idx]) { setHover(null); return; }
        const ptD = arr[idx];
        const base = arr[0]?.close;
        const changePct = (base && base > 0) ? ((ptD.close - base) / base) * 100 : null;
        const dt = new Date(ptD.timestamp);
        const hasTime = dt.getHours() !== 0 || dt.getMinutes() !== 0;
        const dateLabel = dt.toLocaleDateString('tr-TR', { day: '2-digit', month: 'short', year: '2-digit' })
          + (hasTime ? ' · ' + dt.toLocaleTimeString('tr-TR', { hour: '2-digit', minute: '2-digit' }) : '');
        const prec = pricePrecRef.current;
        const priceLabel = fmtRef.current
          ? fmtRef.current(ptD.close)
          : Number(ptD.close).toLocaleString('tr-TR', { minimumFractionDigits: prec, maximumFractionDigits: prec });
        const volumeLabel = ptD.volume > 0
          ? Number(ptD.volume).toLocaleString('tr-TR', { notation: 'compact', maximumFractionDigits: 2 })
          : null;
        setHover({ dateLabel, priceLabel, changePct, up: changePct == null ? true : changePct >= 0, volumeLabel });
      });
    } catch (_) { /* klinecharts sürüm uyumsuzluğu */ }

    // Cleanup
    return () => {
      klineDispose(id);
      indicatorPaneIds.current = {};
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [points, chartMode]);

  // ── Render ─────────────────────────────────────────────────────────────────

  const indicatorMenu = (
    <IndicatorMenu
      maDefs={MA_PERIODS}
      activeMAs={activeMAs}
      onToggleMA={toggleMA}
      dataLen={points?.length ?? 0}
      subDefs={SUB_INDICATORS}
      activeSubs={activeSubInds}
      onToggleSub={toggleSubIndicator}
    />
  );

  return (
    <div>
      {/* Trend rozeti */}
      {trendItem && <div className="mb-2"><TrendBadge item={trendItem} size="xs" /></div>}

      {/* Drawing Toolbar (İndikatör menüsü çizim gruplarının yanında — hisse detay tarzı) */}
      <DrawingToolbar
        activeTool={activeTool}
        onSelectTool={handleSelectTool}
        onDeleteSelected={handleDeleteSelected}
        onClearAll={handleClearAll}
        indicatorSlot={indicatorMenu}
      />

      {/* Grafik */}
      <div
        className="relative"
        ref={wrapperRef}
        onMouseMove={(e) => {
          const r = wrapperRef.current?.getBoundingClientRect();
          if (!r) return;
          const x = e.clientX - r.left;
          const y = e.clientY - r.top;
          setPos({ x, y, flipX: x > r.width - 200, flipY: y > r.height - 96 });
        }}
        onMouseLeave={() => setHover(null)}
      >
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
            {t('Grafik verisi bulunamadı.')}
          </div>
        )}
        <div id={chartId.current} style={{ width: '100%', height: '300px' }} />

        {/* Şık hover tooltip — imleci takip eder, kenarlarda otomatik ters çevrilir (çizgi modu) */}
        {hover && (
          <div
            className="pointer-events-none absolute z-30 min-w-[140px] rounded-xl bg-slate-900/90 px-3.5 py-2.5 shadow-2xl ring-1 ring-white/10 backdrop-blur-md"
            style={{
              left: pos.x,
              top: pos.y,
              transform: `translate(${pos.flipX ? 'calc(-100% - 14px)' : '14px'}, ${pos.flipY ? 'calc(-100% - 14px)' : '14px'})`,
            }}
          >
            <div className="mb-1 flex items-center gap-1.5">
              <span className="inline-block h-2 w-2 rounded-full" style={{ background: hover.up ? '#10b981' : '#ef4444' }} />
              <span className="text-[11px] font-medium text-slate-300">{hover.dateLabel}</span>
            </div>
            <div className="text-base font-black leading-tight text-white tabular-nums">{hover.priceLabel}</div>
            {hover.changePct != null && (
              <div className={`mt-0.5 flex items-center gap-1 text-xs font-bold ${hover.up ? 'text-emerald-400' : 'text-rose-400'}`}>
                {hover.up ? <TrendingUp className="h-3 w-3" /> : <TrendingDown className="h-3 w-3" />}
                {hover.up ? '+' : ''}{hover.changePct.toFixed(2)}%
                <span className="font-medium text-slate-500">· {t('dönem')}</span>
              </div>
            )}
            {hover.volumeLabel && (
              <div className="mt-1 border-t border-white/10 pt-1 text-[11px] text-slate-400">
                {t('Hacim')}: <span className="font-semibold text-slate-200">{hover.volumeLabel}</span>
              </div>
            )}
          </div>
        )}
      </div>

      {resolvedSourceNote && <p className="text-xs text-gray-400 mt-2">{resolvedSourceNote}</p>}
      {sourceWarning && (
        <p className="text-xs text-amber-700 bg-amber-50 border border-amber-200 rounded-lg px-3 py-2 mt-2">
          {sourceWarning}
        </p>
      )}
    </div>
  );
}
