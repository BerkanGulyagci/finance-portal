import { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import { RefreshCw, TrendingUp, TrendingDown, BarChart2, Plus, X, ChevronDown, Trash2 } from 'lucide-react';
import { init as klineInit, dispose as klineDispose, registerIndicator, registerOverlay } from 'klinecharts';
import { getViopChart, getViopContracts } from '../../../../api/marketApi';

// ── Custom Overlay Kayıtları (bir kez çalışır) ────────────────────────────────
let overlaysRegistered = false;
function ensureOverlays() {
  if (overlaysRegistered) return;
  overlaysRegistered = true;

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
        return [{ type: 'rect', attrs: { x, y, width: w, height: h },
          styles: { style: 'stroke_fill', color: 'rgba(22,119,255,0.15)', borderColor: '#1677ff', borderSize: 1 } }];
      }
      return [];
    },
  });

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
        const r  = Math.sqrt(dx * dx + dy * dy);
        return [{ type: 'circle', attrs: { x: coordinates[0].x, y: coordinates[0].y, r },
          styles: { style: 'stroke_fill', color: 'rgba(22,119,255,0.15)', borderColor: '#1677ff', borderSize: 1 } }];
      }
      return [];
    },
  });
}

// ── Drawing tools ────────────────────────────────────────────────────────────

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
            }`}>
            {group} ▾
          </button>
          {openGroup === group && (
            <div className="absolute top-full left-0 mt-1 z-50 bg-white border border-gray-200 rounded-xl shadow-lg p-1 min-w-[180px]">
              {tools.map(tool => (
                <button key={tool.id}
                  onClick={() => { onSelectTool(tool.id === activeTool ? null : tool.id); setOpenGroup(null); }}
                  className={`w-full flex items-center gap-2.5 px-3 py-2 rounded-lg text-xs transition-all ${
                    tool.id === activeTool ? 'bg-[#093eaa] text-white' : 'text-gray-700 hover:bg-gray-50'
                  }`}>
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

const PERIODS = [
  { key: 'ONE_DAY',      label: '1 Gün',   days: 1   },
  { key: 'ONE_WEEK',     label: '1 Hafta', days: 7   },
  { key: 'ONE_MONTH',    label: '1 Ay',    days: 30  },
  { key: 'THREE_MONTHS', label: '3 Ay',    days: 90  },
  { key: 'SIX_MONTHS',   label: '6 Ay',    days: 180 },
  { key: 'ONE_YEAR',     label: '1 Yıl',   days: 365 },
];

const MAIN_COLOR    = '#093eaa';
const COMPARE_COLOR = '#f97316';
const MA7_COLOR   = '#f59e0b';
const MA30_COLOR  = '#a855f7';
const EMA_COLOR   = '#06b6d4';
const BOLL_COLOR  = '#3b82f6';

// ── Yardımcılar ───────────────────────────────────────────────────────────────

function fmt(v, dec = 2) {
  if (v == null) return '-';
  const n = parseFloat(v);
  if (isNaN(n)) return '-';
  return n.toLocaleString('tr-TR', { minimumFractionDigits: dec, maximumFractionDigits: dec });
}

function fmtPct(v, dec = 2) {
  if (v == null) return '-';
  const n = parseFloat(v);
  if (isNaN(n)) return '-';
  if (n > 0) return `+%${fmt(n, dec)}`;
  if (n < 0) return `-%${fmt(Math.abs(n), dec)}`;
  return `%${fmt(0, dec)}`;
}

/** VIOP point → KLineCharts format */
function toKlineData(points) {
  return points
    .map(p => {
      const v = parseFloat(p.value);
      if (isNaN(v)) return null;
      return { timestamp: p.timestamp, open: v, high: v, low: v, close: v, volume: 0, turnover: 0 };
    })
    .filter(Boolean)
    .sort((a, b) => a.timestamp - b.timestamp);
}

/** İlk değer = 100 baz normalize */
function normalizeTo100(points) {
  if (!points?.length) return [];
  const first = parseFloat(points[0].value);
  if (!first || first === 0) return points;
  return points.map(p => ({ ...p, normValue: (parseFloat(p.value) / first) * 100 }));
}

/** Ortak başlangıç timestamp'i bul */
function findCommonStart(mainPts, cmpPts) {
  const mainSet = new Set(mainPts.map(p => p.timestamp));
  const common  = cmpPts.filter(p => mainSet.has(p.timestamp));
  return common.length > 0 ? common[0].timestamp : null;
}

/** Periyot değişim yüzdesi */
function calcPeriodPct(points) {
  const vals = points.map(p => parseFloat(p.value)).filter(v => !isNaN(v) && v > 0);
  if (vals.length < 2) return null;
  return ((vals[vals.length - 1] - vals[0]) / vals[0]) * 100;
}

// ── Custom indicator registry ─────────────────────────────────────────────────

const registeredIndicators = new Set();
function ensureIndicator(name) {
  if (registeredIndicators.has(name)) return;
  try {
    registerIndicator({
      name,
      figures: [{ key: 'val', title: `${name}: `, type: 'line' }],
      calc: () => [],
    });
    registeredIndicators.add(name);
  } catch (_) {
    registeredIndicators.add(name);
  }
}

// ── KLineCharts stil ──────────────────────────────────────────────────────────

function buildStyles(lineColor) {
  return {
    candle: {
      type: 'area',
      area: {
        lineColor, lineSize: 2, value: 'close', smooth: true,
        backgroundColor: [
          { offset: 0, color: lineColor + '28' },
          { offset: 1, color: lineColor + '00' },
        ],
      },
      tooltip: { showRule: 'none' },
      priceMark: {
        last: { show: true, upColor: lineColor, downColor: lineColor, noChangeColor: lineColor },
        high: { show: false }, low: { show: false },
      },
    },
    indicator: { tooltip: { showRule: 'none' } },
    yAxis: { type: 'normal' },
  };
}

// ── KLineCharts bileşeni ──────────────────────────────────────────────────────

function ViopKlineChart({ mainPoints, comparePoints, compareName, isComparing, showMA7, showMA30, showEMA, showBOLL, showRSI, rsiPaneRef, activeTool, chartRef: externalRef }) {
  const chartId  = useRef(`viop_kline_${Math.random().toString(36).slice(2)}`);
  const chartRef = useRef(null);

  // Overlay kayıtlarını garantile
  useEffect(() => { ensureOverlays(); }, []);

  // Chart instance'ı dışarıya aç — her render'da sync et
  useEffect(() => {
    if (externalRef) externalRef.current = chartRef.current;
  });

  // Grafik verisi değişince yeniden oluştur
  useEffect(() => {
    if (!mainPoints?.length) return;

    const id    = chartId.current;
    const chart = klineInit(id);
    chartRef.current = chart;

    if (isComparing && comparePoints.length > 0) {
      // Performans modu: normalize (100 baz)
      const commonTs = findCommonStart(mainPoints, comparePoints);
      const mainFrom = commonTs ? mainPoints.findIndex(p => p.timestamp >= commonTs) : 0;
      const cmpFrom  = commonTs ? comparePoints.findIndex(p => p.timestamp >= commonTs) : 0;

      const mainSlice = mainPoints.slice(mainFrom);
      const cmpSlice  = comparePoints.slice(cmpFrom);

      const mainNorm = normalizeTo100(mainSlice);
      const cmpNorm  = normalizeTo100(cmpSlice);

      const cmpMap = new Map(cmpNorm.map(p => [p.timestamp, p.normValue]));

      const klineData = mainNorm
        .map(p => ({ timestamp: p.timestamp, open: p.normValue, high: p.normValue, low: p.normValue, close: p.normValue, volume: 0, turnover: 0 }))
        .sort((a, b) => a.timestamp - b.timestamp);

      if (!klineData.length) { klineDispose(id); return; }

      chart.setStyles(buildStyles(MAIN_COLOR));
      chart.applyNewData(klineData);

      // Karşılaştırma serisi overlay
      const cmpValues = klineData.map(d => cmpMap.get(d.timestamp) ?? null);
      const indName   = (compareName ?? 'CMP').replace(/[^A-Za-z0-9_]/g, '_').slice(0, 20);
      ensureIndicator(indName);
      try {
        chart.createIndicator(
          {
            name: indName,
            figures: [{ key: 'val', title: `${indName}: `, type: 'line' }],
            calc: (list) => list.map((_, i) => ({ val: cmpValues[i] ?? null })),
            styles: { lines: [{ color: COMPARE_COLOR, size: 2, smooth: true }] },
          },
          false,
          { id: 'candle_pane' }
        );
      } catch (e) { console.warn('[ViopChart] compare overlay:', e); }

    } else {
      // Değer modu
      const klineData = toKlineData(mainPoints);
      if (!klineData.length) { klineDispose(id); return; }

      const isUp  = klineData[klineData.length - 1].close >= klineData[0].close;
      const color = isUp ? '#10b981' : '#ef4444';
      chart.setStyles(buildStyles(color));
      chart.applyNewData(klineData);

      // MA
      const maParams = [...(showMA7 ? [7] : []), ...(showMA30 ? [30] : [])];
      if (maParams.length > 0) {
        try {
          chart.createIndicator({ name: 'MA', calcParams: maParams }, false, { id: 'candle_pane' });
        } catch (e) { console.warn('[ViopChart] MA init:', e); }
      }

      // EMA
      if (showEMA) {
        try {
          chart.createIndicator({
            name: 'EMA', calcParams: [12, 26],
            styles: { lines: [{ color: EMA_COLOR, size: 1.5 }, { color: '#f97316', size: 1.5 }] },
          }, false, { id: 'candle_pane' });
        } catch (e) { console.warn('[ViopChart] EMA init:', e); }
      }

      // Bollinger
      if (showBOLL) {
        try {
          chart.createIndicator({ name: 'BOLL', calcParams: [20, 2] }, false, { id: 'candle_pane' });
        } catch (e) { console.warn('[ViopChart] BOLL init:', e); }
      }

      // RSI — alt panel
      if (showRSI) {
        try {
          const paneId = chart.createIndicator({ name: 'RSI', calcParams: [14] }, true, { height: 80 });
          if (rsiPaneRef) rsiPaneRef.current = paneId ?? null;
        } catch (e) { console.warn('[ViopChart] RSI init:', e); }
      }
    }

    return () => { klineDispose(id); chartRef.current = null; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mainPoints, comparePoints, compareName, isComparing]);

  // MA / EMA / BOLL toggle — chart dispose etmeden güncelle
  useEffect(() => {
    const chart = chartRef.current;
    if (!chart || isComparing) return;

    // MA
    try { chart.removeIndicator('candle_pane', 'MA'); } catch (_) {}
    const maParams = [...(showMA7 ? [7] : []), ...(showMA30 ? [30] : [])];
    if (maParams.length > 0) {
      try { chart.createIndicator({ name: 'MA', calcParams: maParams }, false, { id: 'candle_pane' }); }
      catch (e) { console.warn('[ViopChart] MA toggle:', e); }
    }

    // EMA
    try { chart.removeIndicator('candle_pane', 'EMA'); } catch (_) {}
    if (showEMA) {
      try {
        chart.createIndicator({
          name: 'EMA', calcParams: [12, 26],
          styles: { lines: [{ color: EMA_COLOR, size: 1.5 }, { color: '#f97316', size: 1.5 }] },
        }, false, { id: 'candle_pane' });
      } catch (e) { console.warn('[ViopChart] EMA toggle:', e); }
    }

    // Bollinger
    try { chart.removeIndicator('candle_pane', 'BOLL'); } catch (_) {}
    if (showBOLL) {
      try { chart.createIndicator({ name: 'BOLL', calcParams: [20, 2] }, false, { id: 'candle_pane' }); }
      catch (e) { console.warn('[ViopChart] BOLL toggle:', e); }
    }
  }, [showMA7, showMA30, showEMA, showBOLL, isComparing]);

  // RSI toggle — ayrı effect (alt panel pane ID yönetimi)
  useEffect(() => {
    const chart = chartRef.current;
    if (!chart || isComparing) return;
    if (!showRSI) {
      // Kaldır
      const paneId = rsiPaneRef?.current;
      if (paneId) {
        try { chart.removeIndicator(paneId, 'RSI'); } catch (_) {}
        if (rsiPaneRef) rsiPaneRef.current = null;
      }
    } else {
      // Ekle (zaten yoksa)
      if (!rsiPaneRef?.current) {
        try {
          const paneId = chart.createIndicator({ name: 'RSI', calcParams: [14] }, true, { height: 80 });
          if (rsiPaneRef) rsiPaneRef.current = paneId ?? null;
        } catch (e) { console.warn('[ViopChart] RSI toggle:', e); }
      }
    }
  }, [showRSI, isComparing, rsiPaneRef]);

  // activeTool değişince overlay oluştur
  useEffect(() => {
    if (!chartRef.current || !activeTool) return;
    try { chartRef.current.createOverlay({ name: activeTool }); }
    catch (e) { console.warn('[ViopChart] overlay:', e); }
  }, [activeTool]);

  if (!mainPoints?.length) {
    return (
      <div className="flex flex-col items-center justify-center h-[380px] gap-3 text-gray-400">
        <BarChart2 className="w-10 h-10 opacity-30" />
        <p className="text-sm">Bu dönem için grafik verisi bulunamadı.</p>
      </div>
    );
  }

  return <div id={chartId.current} style={{ width: '100%', height: '380px' }} />;
}

// ── Toggle butonu ─────────────────────────────────────────────────────────────

function Toggle({ label, active, color, onClick, disabled }) {
  return (
    <button onClick={onClick} disabled={disabled}
      className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-bold border transition-all ${
        disabled
          ? 'border-gray-100 bg-gray-50 text-gray-300 cursor-not-allowed'
          : active
            ? 'border-transparent text-white'
            : 'border-gray-200 bg-gray-50 text-gray-500 hover:bg-gray-100'
      }`}
      style={active && !disabled ? { backgroundColor: color } : {}}>
      <span className="inline-block w-3 h-0.5 rounded"
        style={{ backgroundColor: disabled ? '#d1d5db' : active ? 'white' : color }} />
      {label}
    </button>
  );
}

// ── Sözleşme seçici dropdown ──────────────────────────────────────────────────

function ViopCompareSelector({ mainName, compareName, onSelect, onClear }) {
  const [open, setOpen]           = useState(false);
  const [search, setSearch]       = useState('');
  const [contracts, setContracts] = useState([]);
  const [loading, setLoading]     = useState(false);
  const ref = useRef(null);

  useEffect(() => {
    const handler = (e) => { if (ref.current && !ref.current.contains(e.target)) setOpen(false); };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, []);

  useEffect(() => {
    if (!open || contracts.length > 0) return;
    setLoading(true);
    getViopContracts()
      .then(setContracts)
      .catch(() => setContracts([]))
      .finally(() => setLoading(false));
  }, [open, contracts.length]);

  const filtered = contracts.filter(c => {
    if (!c.name || c.name === mainName) return false;
    if (!search.trim()) return true;
    return c.name.toLowerCase().includes(search.toLowerCase());
  });

  const shortName = (name) => name?.split(' ')[0] ?? name;

  return (
    <div className="relative" ref={ref}>
      {compareName ? (
        <div className="flex items-center gap-1">
          <span className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-bold bg-orange-500 text-white">
            <span className="inline-block w-2 h-2 rounded-full bg-white/60" />
            {shortName(compareName)}
          </span>
          <button onClick={onClear}
            className="p-1.5 rounded-lg bg-gray-100 hover:bg-rose-100 hover:text-rose-600 transition-all"
            title="Karşılaştırmayı kaldır">
            <X className="w-3.5 h-3.5" />
          </button>
        </div>
      ) : (
        <button onClick={() => setOpen(o => !o)}
          className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-bold border border-gray-200 bg-gray-50 text-gray-600 hover:bg-gray-100 transition-all">
          <Plus className="w-3 h-3" />
          Karşılaştır
          <ChevronDown className={`w-3 h-3 transition-transform ${open ? 'rotate-180' : ''}`} />
        </button>
      )}

      {open && !compareName && (
        <div className="absolute top-full right-0 mt-1 w-80 bg-white border border-gray-200 rounded-xl shadow-xl z-50">
          <div className="p-3 border-b border-gray-100">
            <input autoFocus type="text" placeholder="Sözleşme ara..."
              value={search} onChange={e => setSearch(e.target.value)}
              className="w-full px-3 py-1.5 bg-gray-50 border border-gray-200 rounded-lg text-xs focus:outline-none focus:ring-2 focus:ring-[#093eaa]/30" />
          </div>
          <div className="max-h-56 overflow-y-auto">
            {loading && (
              <div className="flex items-center justify-center py-6 gap-1.5">
                <div className="w-1.5 h-1.5 bg-[#093eaa] rounded-full animate-bounce" />
                <div className="w-1.5 h-1.5 bg-[#093eaa]/60 rounded-full animate-bounce [animation-delay:100ms]" />
                <div className="w-1.5 h-1.5 bg-[#093eaa]/30 rounded-full animate-bounce [animation-delay:200ms]" />
              </div>
            )}
            {!loading && filtered.length === 0 && (
              <p className="text-xs text-gray-400 text-center py-4">Sözleşme bulunamadı.</p>
            )}
            {!loading && filtered.map(c => (
              <button key={c.name}
                onClick={() => { onSelect(c.name); setOpen(false); setSearch(''); }}
                className="w-full flex items-center justify-between gap-2 px-3 py-2.5 text-left hover:bg-blue-50 transition-colors border-b border-gray-50 last:border-0">
                <div>
                  <p className="text-sm font-bold text-[#093eaa]">{c.name}</p>
                  <p className="text-xs text-gray-400">{c.lastPrice ?? '-'} · {c.changePercent ?? ''}</p>
                </div>
              </button>
            ))}
          </div>
          <div className="p-2 border-t border-gray-100">
            <button onClick={() => setOpen(false)} className="w-full text-xs text-gray-400 py-1 hover:text-gray-600">Kapat</button>
          </div>
        </div>
      )}
    </div>
  );
}

// ── Ana bileşen ───────────────────────────────────────────────────────────────

export default function ViopPriceChart({ contractName }) {
  const [period, setPeriod]     = useState('ONE_DAY');
  const [points, setPoints]     = useState([]);
  const [loading, setLoading]   = useState(false);
  const [status, setStatus]     = useState('idle'); // idle | ok | empty | unsupported | error

  const [showMA7, setShowMA7]   = useState(true);
  const [showMA30, setShowMA30] = useState(false);
  const [showEMA, setShowEMA]   = useState(false);
  const [showBOLL, setShowBOLL] = useState(false);
  const [showRSI, setShowRSI]   = useState(false);
  const rsiPaneId = useRef(null); // RSI pane ID'sini sakla

  const [activeTool, setActiveTool] = useState(null);

  const chartInstanceRef = useRef(null);

  const [compareName, setCompareName]       = useState(null);
  const [comparePoints, setComparePoints]   = useState([]);
  const [compareLoading, setCompareLoading] = useState(false);
  const [compareError, setCompareError]     = useState(false);

  const isComparing = !!compareName;

  // ESC ile aktif çizim aracını kapat
  useEffect(() => {
    const handler = (e) => { if (e.key === 'Escape') setActiveTool(null); };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, []);

  // ── Veri çekme ──────────────────────────────────────────────────────────────

  const fetchMain = useCallback(async (name, p) => {
    if (!name) return;
    setLoading(true); setStatus('idle'); setPoints([]);
    try {
      const resp = await getViopChart(name, p);
      if (!resp.success) { setStatus('unsupported'); return; }
      const data = resp.data ?? [];
      if (!data.length) { setStatus('empty'); return; }
      setPoints(data); setStatus('ok');
    } catch (e) {
      setStatus(e?.response?.status === 422 ? 'unsupported' : 'error');
    } finally { setLoading(false); }
  }, []);

  const fetchCompare = useCallback(async (name, p) => {
    if (!name) return;
    setCompareLoading(true); setCompareError(false); setComparePoints([]);
    try {
      const resp = await getViopChart(name, p);
      if (!resp.success || !(resp.data ?? []).length) { setCompareError(true); return; }
      setComparePoints(resp.data);
    } catch { setCompareError(true); }
    finally { setCompareLoading(false); }
  }, []);

  useEffect(() => { fetchMain(contractName, period); }, [contractName, period, fetchMain]);

  useEffect(() => {
    if (compareName) fetchCompare(compareName, period);
    else { setComparePoints([]); setCompareError(false); }
  }, [compareName, period, fetchCompare]);

  // Karşılaştırma seçilince indikatörleri ve çizim aracını kapat
  useEffect(() => {
    if (compareName) {
      setShowMA7(false); setShowMA30(false); setShowEMA(false); setShowBOLL(false);
      setShowRSI(false); setActiveTool(null);
    } else {
      setShowMA7(true); setShowMA30(false); setShowEMA(false); setShowBOLL(false);
    }
  }, [compareName]);

  // RSI toggle — alt panel olarak ekle/kaldır
  const toggleRSI = useCallback(() => {
    const chart = chartInstanceRef.current;
    if (!chart) return;
    if (showRSI) {
      // Kaldır
      if (rsiPaneId.current) {
        try { chart.removeIndicator(rsiPaneId.current, 'RSI'); } catch (_) {}
        rsiPaneId.current = null;
      }
      setShowRSI(false);
    } else {
      // Ekle — ayrı alt panel
      try {
        const paneId = chart.createIndicator({ name: 'RSI', calcParams: [14] }, true, { height: 80 });
        if (paneId) rsiPaneId.current = paneId;
      } catch (e) { console.warn('[ViopChart] RSI:', e); }
      setShowRSI(true);
    }
  }, [showRSI]);

  // ── Özet istatistikler ───────────────────────────────────────────────────────

  const mainPct   = useMemo(() => calcPeriodPct(points), [points]);
  const cmpPct    = useMemo(() => calcPeriodPct(comparePoints), [comparePoints]);
  const currentPeriod = PERIODS.find(p => p.key === period) ?? PERIODS[0];
  const isLoading = loading || compareLoading;

  const shortName = (name) => name?.split(' ')[0] ?? name;

  return (
    <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">

      {/* ── Başlık satırı ── */}
      <div className="flex items-start justify-between mb-4 flex-wrap gap-3">
        <div>
          <h2 className="font-bold text-gray-900">VİOP Sözleşme Grafiği</h2>
          {/* Tek seri: periyot değişimi */}
          {!isComparing && mainPct != null && status === 'ok' && (
            <span className={`text-sm font-semibold flex items-center gap-1 mt-0.5 ${mainPct >= 0 ? 'text-emerald-600' : 'text-rose-600'}`}>
              {mainPct >= 0 ? <TrendingUp className="w-4 h-4" /> : <TrendingDown className="w-4 h-4" />}
              {currentPeriod.label} değişim: {fmtPct(mainPct, 2)}
            </span>
          )}
          {/* Karşılaştırma: her iki seri */}
          {isComparing && (
            <div className="flex items-center gap-4 mt-1 flex-wrap">
              {mainPct != null && (
                <span className="flex items-center gap-1.5 text-sm font-semibold">
                  <span className="inline-block w-3 h-0.5 rounded bg-[#093eaa]" />
                  <span className="text-gray-600 text-xs">{shortName(contractName)}</span>
                  <span className={mainPct >= 0 ? 'text-emerald-600' : 'text-rose-600'}>{fmtPct(mainPct, 2)}</span>
                </span>
              )}
              {cmpPct != null && (
                <span className="flex items-center gap-1.5 text-sm font-semibold">
                  <span className="inline-block w-3 h-0.5 rounded bg-orange-500" />
                  <span className="text-gray-600 text-xs">{shortName(compareName)}</span>
                  <span className={cmpPct >= 0 ? 'text-emerald-600' : 'text-rose-600'}>{fmtPct(cmpPct, 2)}</span>
                </span>
              )}
            </div>
          )}
        </div>

        {/* Period butonları + yenile */}
        <div className="flex items-center gap-2 flex-wrap">
          <div className="flex gap-1 flex-wrap">
            {PERIODS.map(p => (
              <button key={p.key} onClick={() => setPeriod(p.key)}
                className={`px-3 py-1.5 rounded-lg text-xs font-bold transition-all ${
                  period === p.key ? 'bg-[#093eaa] text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
                }`}>
                {p.label}
              </button>
            ))}
          </div>
          <button onClick={() => fetchMain(contractName, period)}
            className="p-1.5 rounded-lg bg-gray-100 hover:bg-gray-200 transition-all" title="Yenile">
            <RefreshCw className={`w-3.5 h-3.5 text-gray-500 ${isLoading ? 'animate-spin' : ''}`} />
          </button>
        </div>
      </div>

      {/* ── Kontrol satırı ── */}
      <div className="flex items-center gap-3 mb-4 flex-wrap">
        <div className="flex gap-1 bg-gray-100 rounded-lg p-0.5">
          <span className="px-3 py-1 rounded-md text-xs font-bold bg-white text-gray-900 shadow-sm">
            {isComparing ? 'Performans Grafiği' : 'Değer Grafiği'}
          </span>
        </div>

        <Toggle label="MA7"  active={showMA7}  color={MA7_COLOR}  disabled={isComparing} onClick={() => setShowMA7(v => !v)} />
        <Toggle label="MA30" active={showMA30} color={MA30_COLOR} disabled={isComparing} onClick={() => setShowMA30(v => !v)} />
        <Toggle label="EMA 12/26" active={showEMA}  color={EMA_COLOR}  disabled={isComparing} onClick={() => setShowEMA(v => !v)} />
        <Toggle label="Bollinger" active={showBOLL} color={BOLL_COLOR} disabled={isComparing} onClick={() => setShowBOLL(v => !v)} />
        <Toggle label="RSI 14"    active={showRSI}  color="#f59e0b"    disabled={isComparing} onClick={toggleRSI} />

        <div className="ml-auto">
          <ViopCompareSelector
            mainName={contractName}
            compareName={compareName}
            onSelect={name => setCompareName(name)}
            onClear={() => { setCompareName(null); setComparePoints([]); }}
          />
        </div>
      </div>

      {/* Karşılaştırma legend */}
      {isComparing && (
        <div className="flex items-center gap-6 mb-3 bg-gray-50 rounded-lg px-3 py-2 flex-wrap">
          <span className="flex items-center gap-2 text-xs">
            <span className="inline-block w-5 h-0.5 rounded bg-[#093eaa]" />
            <span className="font-bold text-gray-700">{shortName(contractName)}</span>
            {points.length > 0 && <span className="text-gray-500">{fmt(parseFloat(points[points.length - 1]?.value))}</span>}
          </span>
          <span className="flex items-center gap-2 text-xs">
            <span className="inline-block w-5 h-0.5 rounded bg-orange-500" />
            <span className="font-bold text-gray-700">{shortName(compareName)}</span>
            {comparePoints.length > 0 && <span className="text-gray-500">{fmt(parseFloat(comparePoints[comparePoints.length - 1]?.value))}</span>}
          </span>
          <span className="ml-auto text-gray-400 text-xs">İlk değer = 100 bazlı normalize</span>
        </div>
      )}

      {/* ── Grafik alanı ── */}
      <div className="relative" style={{ minHeight: '380px' }}>
        {isLoading && (
          <div className="absolute inset-0 flex items-center justify-center bg-white/70 z-10 rounded-xl">
            <div className="flex gap-1.5">
              <div className="w-2 h-2 bg-[#093eaa] rounded-full animate-bounce" />
              <div className="w-2 h-2 bg-[#093eaa]/60 rounded-full animate-bounce [animation-delay:100ms]" />
              <div className="w-2 h-2 bg-[#093eaa]/30 rounded-full animate-bounce [animation-delay:200ms]" />
            </div>
          </div>
        )}

        {!isLoading && status === 'unsupported' && (
          <div className="flex flex-col items-center justify-center h-[380px] gap-3 text-gray-400">
            <BarChart2 className="w-10 h-10 opacity-30" />
            <p className="text-sm font-semibold text-gray-600">Bu sözleşme türü için grafik desteği henüz eklenmedi.</p>
            <p className="text-xs text-gray-400">Opsiyon sözleşmeleri şu an grafik göstermemektedir.</p>
          </div>
        )}

        {!isLoading && status === 'empty' && (
          <div className="flex flex-col items-center justify-center h-[380px] gap-3 text-gray-400">
            <BarChart2 className="w-10 h-10 opacity-30" />
            <p className="text-sm font-semibold text-gray-600">Bu dönem için VİOP grafik verisi bulunamadı.</p>
          </div>
        )}

        {!isLoading && status === 'error' && (
          <div className="flex flex-col items-center justify-center h-[380px] gap-3 text-gray-400">
            <BarChart2 className="w-10 h-10 opacity-30" />
            <p className="text-sm font-semibold text-rose-600">VİOP grafik verisi şu anda alınamadı.</p>
          </div>
        )}

        {compareError && !isLoading && (
          <div className="mb-2 px-3 py-2 bg-amber-50 border border-amber-200 rounded-lg text-xs text-amber-700">
            Karşılaştırma verisi alınamadı. Ana sözleşme grafiği gösteriliyor.
          </div>
        )}

        {!isLoading && status === 'ok' && (
          <>
            {/* Drawing Toolbar — karşılaştırma modunda gizle */}
            {!isComparing && (
              <DrawingToolbar
                activeTool={activeTool}
                onSelectTool={(toolId) => setActiveTool(toolId)}
                onDeleteSelected={() => { chartInstanceRef.current?.removeOverlay(); }}
                onClearAll={() => {
                  DRAWING_TOOLS.flatMap(g => g.tools).forEach(t => {
                    try { chartInstanceRef.current?.removeOverlay({ name: t.id }); } catch {}
                  });
                  setActiveTool(null);
                }}
              />
            )}
            <ViopKlineChart
              mainPoints={points}
              comparePoints={comparePoints}
              compareName={compareName ? (compareName.replace(/[^A-Za-z0-9_]/g, '_').slice(0, 20)) : null}
              isComparing={isComparing && comparePoints.length > 0}
              showMA7={showMA7}
              showMA30={showMA30}
              showEMA={showEMA}
              showBOLL={showBOLL}
              showRSI={showRSI}
              rsiPaneRef={rsiPaneId}
              activeTool={activeTool}
              chartRef={chartInstanceRef}
            />
          </>
        )}
      </div>

      <p className="text-xs text-gray-400 mt-3">
        Kaynak: İş Yatırım · isyatirim.com.tr
        {isComparing ? ' · Karşılaştırma grafiği normalize edilmiştir (ilk değer = 100 baz).' : ''}
      </p>
    </div>
  );
}
