import { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import { RefreshCw, TrendingUp, TrendingDown, BarChart2, Plus, X, ChevronDown, Trash2 } from 'lucide-react';
import { init as klineInit, dispose as klineDispose, registerIndicator, registerOverlay } from 'klinecharts';
import { getViopChart, getViopContracts } from '../../../../api/marketApi';
import { useTranslation } from '../../../../context/LanguageContext';
import UniversalCompareButton from '../../../../components/common/UniversalCompareButton';
import TrendBadge from '../../../../components/common/TrendBadge';
import IndicatorMenu from '../../../../components/common/IndicatorMenu';
import { buildTrendItem } from '../../../../utils/trendUtils';
import { useYAxisWheelZoom } from '../../../../hooks/useYAxisWheelZoom';

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
            }`}>
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
                  }`}>
                  <span className="w-8 text-center font-mono text-[11px] opacity-70">{tool.icon}</span>
                  <span>{t(tool.label)}</span>
                </button>
              ))}
            </div>
          )}
        </div>
      ))}
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
// VİOP sözleşmeleri kısa ömürlü → MA200 (200 gün) hiç dolmaz; kısa set: MA5/MA10/MA20
const MA5_COLOR  = '#f59e0b';
const MA10_COLOR = '#a855f7';
const MA20_COLOR = '#ef4444';

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

/**
 * Az veri olduğunda (örn. "1 Gün" → ~10 nokta) klinecharts noktaları sağ kenara yaslayıp
 * solu boş bırakır (asimetrik görüntü). Bar aralığını grafik genişliğine göre ayarlayarak
 * tüm noktaların genişliği doldurmasını sağlar. Çok noktalı periyotlarda (60+) varsayılan
 * davranış korunur (son kısım gösterilir, kaydırılabilir).
 */
function fitChartToWidth(chart, id, count) {
  if (!chart || !count) return;
  requestAnimationFrame(() => {
    try {
      const el = document.getElementById(id);
      const width = el?.clientWidth ?? 0;
      if (width <= 0) return;
      chart.setOffsetRightDistance(6);
      if (count <= 60) {
        const space = Math.max(3, (width - 12) / count);
        chart.setBarSpace(space);
      }
    } catch (_) { /* yoksay */ }
  });
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

function buildStyles(lineColor, mainLabel) {
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
      // Hover tooltip'i aç (hisse detay grafiğindeki gibi): ana değer + karşılaştırma serisi.
      // Çizgilerin üstünde okunabilsin diye arka planlı kutu (rect) + üstten boşluk.
      tooltip: {
        showRule: 'follow_cross',
        showType: 'rect',
        text: { size: 12, marginTop: 4, marginBottom: 4, marginLeft: 8, marginRight: 8 },
        rect: {
          offsetLeft: 8, offsetTop: 8, offsetRight: 8,
          paddingLeft: 10, paddingRight: 10, paddingTop: 8, paddingBottom: 8,
          borderRadius: 8, borderSize: 1, borderColor: '#e5e7eb',
          color: 'rgba(255,255,255,0.94)',
        },
        custom: (data) => {
          const d = data?.current ?? {};
          const date = d.timestamp
            ? new Date(d.timestamp).toLocaleDateString('tr-TR', { day: '2-digit', month: 'short', year: 'numeric' })
            : '';
          return [
            { title: '', value: { text: date, color: '#6b7280' } },
            {
              title: mainLabel ? { text: `${mainLabel}:`, color: lineColor } : '',
              value: { text: fmt(d.close), color: lineColor },
            },
          ];
        },
      },
      priceMark: {
        last: { show: true, upColor: lineColor, downColor: lineColor, noChangeColor: lineColor },
        high: { show: false }, low: { show: false },
      },
    },
    // Karşılaştırma/MA değerleri hover'da yan yana — gereksiz isim/parametre tekrarını gizle
    indicator: { tooltip: { showRule: 'follow_cross', showType: 'rect', showName: false, showParams: false, defaultValue: '—', text: { size: 12, marginTop: 4, marginBottom: 4, marginLeft: 8, marginRight: 8 } } },
    // Eksen yazıları net/koyu olsun (açık gri "bulanık" görünmesin)
    xAxis: { tickText: { color: '#4b5563', size: 11 } },
    yAxis: { type: 'normal', tickText: { color: '#4b5563', size: 11 } },
  };
}

// ── KLineCharts bileşeni ──────────────────────────────────────────────────────

function ViopKlineChart({ mainPoints, comparePoints, compareName, compareLabel, mainLabel, isComparing, showMA5, showMA10, showMA20, showRSI, rsiPaneRef, chartRef: externalRef }) {
  const { t } = useTranslation();
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

      chart.setStyles(buildStyles(MAIN_COLOR, mainLabel));
      chart.applyNewData(klineData);
      fitChartToWidth(chart, id, klineData.length);

      // Karşılaştırma serisi overlay
      const cmpValues = klineData.map(d => cmpMap.get(d.timestamp) ?? null);
      const indName   = (compareName ?? 'CMP').replace(/[^A-Za-z0-9_]/g, '_').slice(0, 20);
      const figTitle  = `${compareLabel || indName}: `;
      ensureIndicator(indName);
      try {
        chart.createIndicator(
          {
            name: indName,
            figures: [{ key: 'val', title: figTitle, type: 'line' }],
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
      chart.setStyles(buildStyles(color, mainLabel));
      chart.applyNewData(klineData);
      fitChartToWidth(chart, id, klineData.length);

      // MA (20 / 50 / 200) — yalnızca yeterli veri varsa; çizgi rengi buton rengiyle aynı
      const n = mainPoints.length;
      const maDefs = [[5, MA5_COLOR], [10, MA10_COLOR], [20, MA20_COLOR]]
        .filter(([p]) => (p === 5 ? showMA5 : p === 10 ? showMA10 : showMA20) && n >= p);
      if (maDefs.length > 0) {
        try {
          chart.createIndicator({ name: 'MA', calcParams: maDefs.map(([p]) => p), styles: { lines: maDefs.map(([, c]) => ({ color: c, size: 1.5 })) } }, false, { id: 'candle_pane' });
        } catch (e) { console.warn('[ViopChart] MA init:', e); }
      }

      // RSI — alt panel (yeterli veri varsa; az noktada RSI(14) hesaplanamaz, boş panel çıkar)
      if (showRSI && mainPoints.length >= 15) {
        try {
          const paneId = chart.createIndicator({ name: 'RSI', calcParams: [14] }, true, { height: 80 });
          if (rsiPaneRef) rsiPaneRef.current = paneId ?? null;
        } catch (e) { console.warn('[ViopChart] RSI init:', e); }
      }
    }

    return () => { klineDispose(id); chartRef.current = null; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mainPoints, comparePoints, compareName, isComparing]);

  // MA toggle — chart dispose etmeden güncelle
  useEffect(() => {
    const chart = chartRef.current;
    if (!chart || isComparing) return;

    try { chart.removeIndicator('candle_pane', 'MA'); } catch (_) {}
    const n = mainPoints.length;
    const maDefs = [[5, MA5_COLOR], [10, MA10_COLOR], [20, MA20_COLOR]]
      .filter(([p]) => (p === 5 ? showMA5 : p === 10 ? showMA10 : showMA20) && n >= p);
    if (maDefs.length > 0) {
      try { chart.createIndicator({ name: 'MA', calcParams: maDefs.map(([p]) => p), styles: { lines: maDefs.map(([, c]) => ({ color: c, size: 1.5 })) } }, false, { id: 'candle_pane' }); }
      catch (e) { console.warn('[ViopChart] MA toggle:', e); }
    }
  }, [showMA5, showMA10, showMA20, isComparing]);

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

  if (!mainPoints?.length) {
    return (
      <div className="flex flex-col items-center justify-center h-[288px] gap-3 text-gray-400">
        <BarChart2 className="w-10 h-10 opacity-30" />
        <p className="text-sm">{t('Bu dönem için grafik verisi bulunamadı.')}</p>
      </div>
    );
  }

  return <div id={chartId.current} style={{ width: '100%', height: '288px' }} />;
}

// ── Toggle butonu ─────────────────────────────────────────────────────────────

function Toggle({ label, active, color, onClick, disabled, title }) {
  return (
    <button onClick={onClick} disabled={disabled} title={title}
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
  const { t } = useTranslation();
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
            title={t('Karşılaştırmayı kaldır')}>
            <X className="w-3.5 h-3.5" />
          </button>
        </div>
      ) : (
        <button onClick={() => setOpen(o => !o)}
          className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-bold border border-gray-200 bg-gray-50 text-gray-600 hover:bg-gray-100 transition-all">
          <Plus className="w-3 h-3" />
          {t('Karşılaştır')}
          <ChevronDown className={`w-3 h-3 transition-transform ${open ? 'rotate-180' : ''}`} />
        </button>
      )}

      {open && !compareName && (
        <div className="absolute top-full right-0 mt-1 w-80 bg-white border border-gray-200 rounded-xl shadow-xl z-50">
          <div className="p-3 border-b border-gray-100">
            <input autoFocus type="text" placeholder={t('Sözleşme ara...')}
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
              <p className="text-xs text-gray-400 text-center py-4">{t('Sözleşme bulunamadı.')}</p>
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
            <button onClick={() => setOpen(false)} className="w-full text-xs text-gray-400 py-1 hover:text-gray-600">{t('Kapat')}</button>
          </div>
        </div>
      )}
    </div>
  );
}

// ── Ana bileşen ───────────────────────────────────────────────────────────────

export default function ViopPriceChart({ contractName }) {
  const { t } = useTranslation();
  const [period, setPeriod]     = useState('ONE_DAY');
  const [points, setPoints]     = useState([]);
  const [loading, setLoading]   = useState(false);
  const [status, setStatus]     = useState('idle'); // idle | ok | empty | unsupported | error

  const [showMA5, setShowMA5]   = useState(false);
  const [showMA10, setShowMA10] = useState(false);
  const [showMA20, setShowMA20] = useState(false);
  const [showRSI, setShowRSI]     = useState(false);
  const rsiPaneId = useRef(null); // RSI pane ID'sini sakla

  const chartInstanceRef = useRef(null);

  // Fiyat ekseninde fare tekerleği ile dikey zoom.
  useYAxisWheelZoom(chartInstanceRef, !loading);

  const [compareName, setCompareName]       = useState(null);
  const [comparePoints, setComparePoints]   = useState([]);
  const [compareLoading, setCompareLoading] = useState(false);
  const [compareError, setCompareError]     = useState(false);

  const isComparing = !!compareName;

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

  // Karşılaştırma seçilince indikatörleri kapat
  useEffect(() => {
    setShowMA5(false); setShowMA10(false); setShowMA20(false);
    if (compareName) setShowRSI(false);
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

  // İndikatör için yeterli veri var mı? (VİOP geçmişi kısa → MA200/RSI çoğu zaman dolmaz)
  const n = status === 'ok' ? points.length : 0;
  const maTitle = (need) => n < need ? t('Yeterli veri yok ({n} nokta gerekir)', { n: need }) : undefined;

  // Trend rozeti — seri kapanışlarından (MA20/50 + 52h konumu)
  const trendItem = useMemo(
    () => buildTrendItem(points.map(p => parseFloat(p.value)), 'FUTURE'),
    [points],
  );

  return (
    <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">

      {/* ── Başlık satırı ── */}
      <div className="flex items-start justify-between mb-4 flex-wrap gap-3">
        <div>
          <div className="flex items-center gap-2 flex-wrap">
            <h2 className="font-bold text-gray-900">{t('VİOP Sözleşme Grafiği')}</h2>
            {!isComparing && trendItem && <TrendBadge item={trendItem} size="xs" />}
          </div>
          {/* Tek seri: periyot değişimi */}
          {!isComparing && mainPct != null && status === 'ok' && (
            <span className={`text-sm font-semibold flex items-center gap-1 mt-0.5 ${mainPct >= 0 ? 'text-emerald-600' : 'text-rose-600'}`}>
              {mainPct >= 0 ? <TrendingUp className="w-4 h-4" /> : <TrendingDown className="w-4 h-4" />}
              {t(currentPeriod.label)} {t('değişim:')} {fmtPct(mainPct, 2)}
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
          <div className="inline-flex items-center gap-0.5 rounded-lg border border-gray-200 bg-gray-50 p-0.5">
            {PERIODS.map(p => (
              <button key={p.key} onClick={() => setPeriod(p.key)}
                className={`px-3 py-1 rounded-md text-xs font-semibold whitespace-nowrap transition-all ${
                  period === p.key ? 'bg-white text-[#093eaa] shadow-sm' : 'text-gray-500 hover:text-gray-800'
                }`}>
                {t(p.label)}
              </button>
            ))}
          </div>
          <button onClick={() => fetchMain(contractName, period)}
            className="p-1.5 rounded-lg bg-gray-100 hover:bg-gray-200 transition-all" title={t('Yenile')}>
            <RefreshCw className={`w-3.5 h-3.5 text-gray-500 ${isLoading ? 'animate-spin' : ''}`} />
          </button>
        </div>
      </div>

      {/* ── Kontrol satırı ── */}
      <div className="flex items-center gap-3 mb-4 flex-wrap">
        <div className="flex gap-1 bg-gray-100 rounded-lg p-0.5">
          <span className="px-3 py-1 rounded-md text-xs font-bold bg-white text-gray-900 shadow-sm">
            {isComparing ? t('Performans Grafiği') : t('Değer Grafiği')}
          </span>
        </div>

        <IndicatorMenu
          maDefs={[{ period: 5, color: MA5_COLOR, label: 'MA5' }, { period: 10, color: MA10_COLOR, label: 'MA10' }, { period: 20, color: MA20_COLOR, label: 'MA20' }]}
          activeMAs={[...(showMA5 ? [5] : []), ...(showMA10 ? [10] : []), ...(showMA20 ? [20] : [])]}
          onToggleMA={(p) => { if (p === 5) setShowMA5(v => !v); else if (p === 10) setShowMA10(v => !v); else setShowMA20(v => !v); }}
          dataLen={n}
          extras={[{ key: 'rsi', label: 'RSI 14', color: '#f59e0b', active: showRSI, onToggle: toggleRSI, disabled: n < 15, title: n < 15 ? t('Yeterli veri yok (RSI için ~15 nokta gerekir)') : undefined }]}
          disabled={isComparing}
        />

        <div className="ml-auto flex items-center gap-2">
          <div title={t('Aynı grafikte başka VİOP sözleşmeleriyle kıyasla')}>
            <ViopCompareSelector
              mainName={contractName}
              compareName={compareName}
              onSelect={name => setCompareName(name)}
              onClear={() => { setCompareName(null); setComparePoints([]); }}
            />
          </div>
          <UniversalCompareButton assetType="FUTURE" symbol={contractName} name={contractName} />
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
          <span className="ml-auto text-gray-400 text-xs">{t('İlk değer = 100 bazlı normalize')}</span>
        </div>
      )}

      {/* ── Grafik alanı ── */}
      <div className="relative" style={{ minHeight: '288px' }}>
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
          <div className="flex flex-col items-center justify-center h-[288px] gap-3 text-gray-400">
            <BarChart2 className="w-10 h-10 opacity-30" />
            <p className="text-sm font-semibold text-gray-600">{t('Bu sözleşme türü için grafik desteği henüz eklenmedi.')}</p>
            <p className="text-xs text-gray-400">{t('Opsiyon sözleşmeleri şu an grafik göstermemektedir.')}</p>
          </div>
        )}

        {!isLoading && status === 'empty' && (
          <div className="flex flex-col items-center justify-center h-[288px] gap-3 text-gray-400">
            <BarChart2 className="w-10 h-10 opacity-30" />
            <p className="text-sm font-semibold text-gray-600">{t('Bu dönem için VİOP grafik verisi bulunamadı.')}</p>
          </div>
        )}

        {!isLoading && status === 'error' && (
          <div className="flex flex-col items-center justify-center h-[288px] gap-3 text-gray-400">
            <BarChart2 className="w-10 h-10 opacity-30" />
            <p className="text-sm font-semibold text-rose-600">{t('VİOP grafik verisi şu anda alınamadı.')}</p>
          </div>
        )}

        {compareError && !isLoading && (
          <div className="mb-2 px-3 py-2 bg-amber-50 border border-amber-200 rounded-lg text-xs text-amber-700">
            {t('Karşılaştırma verisi alınamadı. Ana sözleşme grafiği gösteriliyor.')}
          </div>
        )}

        {!isLoading && status === 'ok' && (
          <ViopKlineChart
            mainPoints={points}
            comparePoints={comparePoints}
            compareName={compareName ? (compareName.replace(/[^A-Za-z0-9_]/g, '_').slice(0, 20)) : null}
            compareLabel={compareName ? shortName(compareName) : null}
            mainLabel={shortName(contractName)}
            isComparing={isComparing && comparePoints.length > 0}
            showMA5={showMA5}
            showMA10={showMA10}
            showMA20={showMA20}
            showRSI={showRSI}
            rsiPaneRef={rsiPaneId}
            chartRef={chartInstanceRef}
          />
        )}
      </div>

      <p className="text-xs text-gray-400 mt-3">
        {t('Kaynak: İş Yatırım · isyatirim.com.tr')}
        {isComparing ? t(' · Karşılaştırma grafiği normalize edilmiştir (ilk değer = 100 baz).') : ''}
      </p>
    </div>
  );
}
