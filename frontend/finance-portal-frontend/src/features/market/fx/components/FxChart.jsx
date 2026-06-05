import { useEffect, useState, useRef, useCallback } from 'react';
import { ChevronDown, Trash2, X, SlidersHorizontal } from 'lucide-react';
import { init as klineInit, dispose as klineDispose, registerOverlay } from 'klinecharts';
import { useTranslation } from '../../../../context/LanguageContext';
import { useTheme } from '../../../../context/ThemeContext';
import { useChartDrawings } from '../../../../hooks/useChartDrawings';
import { useYAxisWheelZoom } from '../../../../hooks/useYAxisWheelZoom';

// ── Sabitler ──────────────────────────────────────────────────────────────────
// FX geçmişi TCMB'den gün-gün çekildiği için "Tüm" yok; en uzun 10Y (yıl başına ~260 istek, cache+LKG'li).
const RANGES = ['1W', '1M', '3M', '6M', '1Y', '5Y', '10Y'];
const RANGE_LABELS = { '1W': '1H', '1M': '1A', '3M': '3A', '6M': '6A', '1Y': '1Y', '5Y': '5Y', '10Y': '10Y' };

const FX_MA = [
  { period: 20,  color: '#f59e0b', label: 'MA20'  },
  { period: 50,  color: '#a855f7', label: 'MA50'  },
  { period: 200, color: '#ef4444', label: 'MA200' },
];
const FX_SUB_INDICATORS = [
  { name: 'RSI',  label: 'RSI',  color: '#f59e0b' },
  { name: 'MACD', label: 'MACD', color: '#3b82f6' },
  { name: 'KDJ',  label: 'KDJ',  color: '#8b5cf6' },
];

// ── Çizim araçları ────────────────────────────────────────────────────────────
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
    { id: 'fibonacciLine',          label: 'Fibonacci Retracement', icon: 'Fib' },
  ]},
  { group: 'Şekiller', tools: [
    { id: 'customRect',   label: 'Dikdörtgen', icon: '□' },
    { id: 'customCircle', label: 'Çember',     icon: '○' },
  ]},
];

// customRect / customCircle overlay kayıtları (self-contained — yeniden kayıt zararsız)
try {
  registerOverlay({
    name: 'customRect', totalStep: 3,
    needDefaultPointFigure: true, needDefaultXAxisFigure: true, needDefaultYAxisFigure: true,
    createPointFigures: ({ coordinates }) => {
      if (coordinates.length === 2) {
        const x = Math.min(coordinates[0].x, coordinates[1].x);
        const y = Math.min(coordinates[0].y, coordinates[1].y);
        const w = Math.abs(coordinates[1].x - coordinates[0].x);
        const h = Math.abs(coordinates[1].y - coordinates[0].y);
        return [{ type: 'rect', attrs: { x, y, width: w, height: h },
          styles: { style: 'stroke_fill', color: 'rgba(9,62,170,0.12)', borderColor: '#093eaa', borderSize: 1 } }];
      }
      return [];
    },
  });
  registerOverlay({
    name: 'customCircle', totalStep: 3,
    needDefaultPointFigure: true, needDefaultXAxisFigure: true, needDefaultYAxisFigure: true,
    createPointFigures: ({ coordinates }) => {
      if (coordinates.length === 2) {
        const dx = coordinates[1].x - coordinates[0].x;
        const dy = coordinates[1].y - coordinates[0].y;
        const r = Math.sqrt(dx * dx + dy * dy);
        return [{ type: 'circle', attrs: { x: coordinates[0].x, y: coordinates[0].y, r },
          styles: { style: 'stroke_fill', color: 'rgba(9,62,170,0.12)', borderColor: '#093eaa', borderSize: 1 } }];
      }
      return [];
    },
  });
} catch (_) { /* zaten kayıtlı */ }

// ── Helper ────────────────────────────────────────────────────────────────────
function fmt(v, dec = 4) {
  if (v == null) return '-';
  return parseFloat(v).toLocaleString('tr-TR', { minimumFractionDigits: dec, maximumFractionDigits: dec });
}

// ── Çizim araç çubuğu ─────────────────────────────────────────────────────────
function DrawingToolbar({ activeTool, onSelectTool, onDeleteSelected, onClearAll, indicatorSlot }) {
  const { t } = useTranslation();
  const [openGroup, setOpenGroup] = useState(null);

  return (
    <div className="flex flex-wrap items-center gap-1.5 p-2 bg-[#f6f8fc] border border-[#e2e8f0] rounded-2xl mb-2">
      {DRAWING_TOOLS.map(({ group, tools }) => (
        <div key={group} className="relative">
          <button
            onClick={() => setOpenGroup(openGroup === group ? null : group)}
            className={`inline-flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-xs font-semibold transition-all border ${
              tools.some(tool => tool.id === activeTool) || openGroup === group
                ? 'border-[#093eaa] text-[#093eaa] bg-[#093eaa]/5'
                : 'bg-white text-[#5a6472] border-[#e2e8f0] hover:border-[#093eaa] hover:text-[#093eaa]'
            }`}
          >
            {t(group)}
            <ChevronDown className={`w-3 h-3 transition-transform ${openGroup === group ? 'rotate-180' : ''}`} />
          </button>
          {openGroup === group && (
            <>
              <div className="fixed inset-0 z-40" role="button" tabIndex={0} onClick={() => setOpenGroup(null)} onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); setOpenGroup(null); } }} />
              <div className="absolute top-full left-0 mt-1 z-50 bg-white border border-[#e2e8f0] rounded-xl shadow-lg p-1 min-w-[180px]">
                {tools.map(tool => (
                  <button
                    key={tool.id}
                    onClick={() => { onSelectTool(tool.id === activeTool ? null : tool.id); setOpenGroup(null); }}
                    className={`w-full flex items-center gap-2.5 px-3 py-2 rounded-lg text-xs transition-all ${
                      tool.id === activeTool ? 'bg-[#093eaa] text-white' : 'text-[#1a1c1e] hover:bg-[#f6f8fc]'
                    }`}
                  >
                    <span className="w-8 text-center font-mono text-[11px] opacity-70">{tool.icon}</span>
                    <span>{t(tool.label)}</span>
                  </button>
                ))}
              </div>
            </>
          )}
        </div>
      ))}

      {indicatorSlot && (
        <>
          <div className="w-px h-6 bg-[#e2e8f0] mx-1" />
          {indicatorSlot}
        </>
      )}

      <div className="w-px h-6 bg-[#e2e8f0] mx-1" />

      <button onClick={onDeleteSelected} title={t('Seçili çizimi sil')}
        className="inline-flex items-center px-2 py-1.5 rounded-lg text-[#5a6472] hover:bg-red-50 hover:text-red-500 transition-all border border-[#e2e8f0]">
        <Trash2 className="w-3.5 h-3.5" />
      </button>
      <button onClick={onClearAll} title={t('Tüm çizimleri temizle')}
        className="inline-flex items-center px-2 py-1.5 rounded-lg text-[#5a6472] hover:bg-red-50 hover:text-red-500 transition-all border border-[#e2e8f0]">
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

// ── FX grafiği — kompakt MA + indikatör + çizim araçlı ────────────────────────
export default function FxChart({ symbol, chartPoints, lineColor, mainLabel, range, onRangeChange, loadingChart }) {
  const { t } = useTranslation();
  const { isDark } = useTheme();
  const chartId = useRef(`kline_fx_${Date.now()}`);
  const chartRef = useRef(null);
  const indicatorPaneIds = useRef({});

  // Fiyat ekseninde fare tekerleği ile dikey zoom.
  useYAxisWheelZoom(chartRef, !loadingChart);

  const [activeMAs, setActiveMAs] = useState([]);
  const [activeSubInds, setActiveSubInds] = useState([]);
  const [indMenuOpen, setIndMenuOpen] = useState(false);

  // Çizim kalıcılığı — ortak hook (mouseup snapshot → localStorage + sunucu senkronu).
  const {
    activeTool, setActiveTool, handleSelectTool, handleDeleteSelected, handleClearAll, restoreOverlays,
  } = useChartDrawings({ chartRef, chartIdRef: chartId, persistKey: symbol ? `chart-overlays:fx:${symbol}` : '' });

  const applyMA = useCallback((periods) => {
    const chart = chartRef.current;
    if (!chart) return;
    try { chart.removeIndicator('candle_pane', 'MA'); } catch (_) { /* yoksay */ }
    if (periods.length > 0) {
      try {
        chart.createIndicator(
          { name: 'MA', calcParams: periods, styles: { lines: periods.map(p => ({ color: FX_MA.find(m => m.period === p)?.color ?? '#888', size: 1.5 })) } },
          false, { id: 'candle_pane' },
        );
      } catch (_) { /* yoksay */ }
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
        if (paneId) { try { chart.removeIndicator(paneId, indName); delete indicatorPaneIds.current[indName]; } catch (_) { /* yoksay */ } }
        return prev.filter(n => n !== indName);
      }
      try {
        const paneId = chart.createIndicator({ name: indName }, true, { height: 80 });
        if (paneId) indicatorPaneIds.current[indName] = paneId;
      } catch (_) { /* yoksay */ }
      return [...prev, indName];
    });
  }, []);

  useEffect(() => {
    const handler = (e) => { if (e.key === 'Escape') setActiveTool(null); };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [setActiveTool]);

  // Veri uygulandığında chart'ı kur / yeniden çiz ve aktif indikatörleri geri ekle
  useEffect(() => {
    if (!chartPoints || chartPoints.length === 0) return;
    const id = chartId.current;
    const chart = klineInit(id);
    chartRef.current = chart;
    indicatorPaneIds.current = {};

    chart.setStyles({
      candle: {
        type: 'area',
        area: {
          lineColor, lineSize: 2, value: 'close', smooth: true,
          backgroundColor: [
            { offset: 0, color: lineColor + '33' },
            { offset: 1, color: lineColor + '00' },
          ],
        },
        tooltip: {
          showRule: 'follow_cross', showType: 'rect',
          text: { size: 12, marginTop: 4, marginBottom: 4, marginLeft: 8, marginRight: 8 },
          // Tema uyumlu: açık→beyaz kart/gri yazı, koyu→koyu kart/açık yazı.
          rect: {
            offsetLeft: 8, offsetTop: 8, offsetRight: 8, paddingLeft: 10, paddingRight: 10, paddingTop: 8, paddingBottom: 8,
            borderRadius: 8, borderSize: 1,
            borderColor: isDark ? 'rgba(255,255,255,0.12)' : '#e5e7eb',
            color: isDark ? 'rgba(15,23,42,0.92)' : 'rgba(255,255,255,0.94)',
          },
          custom: (data) => {
            const d = data?.current ?? {};
            const date = d.timestamp ? new Date(d.timestamp).toLocaleDateString('tr-TR', { day: '2-digit', month: 'short', year: 'numeric' }) : '';
            return [
              { title: '', value: { text: date, color: isDark ? '#94a3b8' : '#6b7280' } },
              { title: mainLabel ? { text: `${mainLabel}:`, color: lineColor } : '', value: { text: fmt(d.close, 4), color: lineColor } },
            ];
          },
        },
      },
      indicator: { tooltip: { showRule: 'follow_cross', showType: 'rect', showName: true, showParams: false, defaultValue: '—', text: { size: 12, marginTop: 4, marginBottom: 4, marginLeft: 8, marginRight: 8 } } },
      xAxis: { tickText: { color: '#4b5563', size: 11 } },
      yAxis: { type: 'normal', tickText: { color: '#4b5563', size: 11 } },
    });

    const klineData = chartPoints
      .map(p => { const ts = new Date(p.date).getTime(); const v = p.value ?? 0; return { timestamp: ts, open: v, high: v, low: v, close: v, volume: 0, turnover: 0 }; })
      .filter(d => !isNaN(d.close) && d.close > 0)
      .sort((a, b) => a.timestamp - b.timestamp);

    if (klineData.length === 0) { klineDispose(id); chartRef.current = null; return; }
    chart.applyNewData(klineData);

    // Aktif indikatörleri geri ekle (range değişince chart yeniden kurulur)
    if (activeMAs.length > 0) {
      try {
        chart.createIndicator(
          { name: 'MA', calcParams: activeMAs, styles: { lines: activeMAs.map(p => ({ color: FX_MA.find(m => m.period === p)?.color ?? '#888', size: 1.5 })) } },
          false, { id: 'candle_pane' },
        );
      } catch (_) { /* yoksay */ }
    }
    activeSubInds.forEach(indName => {
      try { const paneId = chart.createIndicator({ name: indName }, true, { height: 80 }); if (paneId) indicatorPaneIds.current[indName] = paneId; } catch (_) { /* yoksay */ }
    });

    // Kaydedilmiş çizimleri geri yükle (applyNewData'dan sonra).
    restoreOverlays(klineData);

    return () => { klineDispose(id); chartRef.current = null; indicatorPaneIds.current = {}; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [chartPoints, lineColor, mainLabel, isDark]);

  const indicatorDropdown = (
    <div className="relative">
      <button
        onClick={() => setIndMenuOpen(v => !v)}
        className={`inline-flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-xs font-semibold border transition-all ${
          indMenuOpen || (activeMAs.length + activeSubInds.length) > 0
            ? 'bg-[#093eaa] text-white border-[#093eaa]'
            : 'bg-white text-[#5a6472] border-[#e2e8f0] hover:border-[#093eaa] hover:text-[#093eaa]'
        }`}
      >
        <SlidersHorizontal className="w-3.5 h-3.5" />
        {t('İndikatör')}
        {(activeMAs.length + activeSubInds.length) > 0 && (
          <span className="inline-flex items-center justify-center min-w-[16px] h-4 px-1 rounded-full bg-white text-[#093eaa] text-[10px] font-bold">
            {activeMAs.length + activeSubInds.length}
          </span>
        )}
        <ChevronDown className={`w-3 h-3 transition-transform ${indMenuOpen ? 'rotate-180' : ''}`} />
      </button>
      {indMenuOpen && (
        <>
          <div className="fixed inset-0 z-40" role="button" tabIndex={0} onClick={() => setIndMenuOpen(false)} onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); setIndMenuOpen(false); } }} />
          <div className="absolute left-0 top-full mt-1 z-50 bg-white border border-[#e2e8f0] rounded-xl shadow-lg p-3 w-64">
            <p className="text-[10px] font-bold text-[#9aa6b6] uppercase tracking-wider mb-2">{t('Hareketli Ortalama')}</p>
            <div className="flex flex-wrap gap-1.5 mb-3">
              {FX_MA.map(({ period, color, label }) => {
                const active = activeMAs.includes(period);
                return (
                  <button key={period} onClick={() => toggleMA(period)}
                    className={`px-2.5 py-1 rounded-md text-xs font-bold transition-all border ${active ? 'text-white border-transparent' : 'bg-[#f6f8fc] text-[#5a6472] border-[#e2e8f0] hover:bg-[#eef2f8]'}`}
                    style={active ? { backgroundColor: color, borderColor: color } : {}}>
                    {label}
                  </button>
                );
              })}
            </div>
            <p className="text-[10px] font-bold text-[#9aa6b6] uppercase tracking-wider mb-2">{t('Osilatör / İndikatör')}</p>
            <div className="flex flex-wrap gap-1.5">
              {FX_SUB_INDICATORS.map(({ name, label, color }) => {
                const active = activeSubInds.includes(name);
                return (
                  <button key={name} onClick={() => toggleSubIndicator(name)}
                    className={`px-2.5 py-1 rounded-md text-xs font-semibold transition-all border ${active ? 'text-white border-transparent' : 'bg-[#f6f8fc] text-[#5a6472] border-[#e2e8f0] hover:bg-[#eef2f8]'}`}
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
      {/* Zaman aralığı (segmented) */}
      <div className="flex justify-center mb-2.5">
        <div className="inline-flex items-center gap-0.5 rounded-full border border-[#e2e8f0] bg-[#f6f8fc] p-0.5 overflow-x-auto max-w-full [&::-webkit-scrollbar]:hidden">
          {RANGES.map(r => (
            <button key={r} onClick={() => onRangeChange(r)}
              className={`px-3 py-1 rounded-full text-xs font-bold whitespace-nowrap transition-all ${
                range === r ? 'bg-[#093eaa] text-white shadow-[0_2px_8px_-2px_rgba(9,62,170,0.5)]' : 'text-[#5a6472] hover:text-[#093eaa]'
              }`}>
              {RANGE_LABELS[r]}
            </button>
          ))}
        </div>
      </div>

      <DrawingToolbar
        activeTool={activeTool}
        onSelectTool={handleSelectTool}
        onDeleteSelected={handleDeleteSelected}
        onClearAll={handleClearAll}
        indicatorSlot={indicatorDropdown}
      />

      <div className="relative rounded-2xl overflow-hidden">
        {loadingChart && (
          <div className="absolute inset-0 flex items-center justify-center bg-white/70 z-10">
            <div className="flex gap-1.5">
              <div className="w-2 h-2 bg-[#093eaa] rounded-full animate-bounce" />
              <div className="w-2 h-2 bg-[#093eaa]/60 rounded-full animate-bounce [animation-delay:120ms]" />
              <div className="w-2 h-2 bg-[#093eaa]/30 rounded-full animate-bounce [animation-delay:240ms]" />
            </div>
          </div>
        )}
        {(!chartPoints || chartPoints.length === 0) && !loadingChart ? (
          <div className="flex items-center justify-center h-[380px] text-gray-400 text-sm">{t('Grafik verisi yüklenemedi.')}</div>
        ) : (
          <div id={chartId.current} style={{ width: '100%', height: '380px' }} />
        )}
      </div>
    </div>
  );
}
