import { useEffect, useRef, useState, useMemo, useCallback } from 'react';
import { useParams, useLocation, Link } from 'react-router-dom';
import { ArrowLeft, ExternalLink, TrendingUp, TrendingDown } from 'lucide-react';
import { init as klineInit, dispose as klineDispose } from 'klinecharts';
import { getRasyonetFundDetail } from '../../../api/marketApi';

// ── Yardımcılar ───────────────────────────────────────────────────────────────

function toFloat(v) {
  if (v == null) return null;
  const n = parseFloat(v);
  return isNaN(n) ? null : n;
}

function fmt(v, dec = 6) {
  const n = toFloat(v);
  if (n == null) return '-';
  // dec verilmişse kullan, yoksa fiyat büyüklüğüne göre otomatik
  const abs = Math.abs(n);
  const auto = abs >= 100 ? 2 : abs >= 10 ? 3 : abs >= 1 ? 4 : abs >= 0.1 ? 5 : abs >= 0.01 ? 6 : 8;
  const d = dec === 6 ? auto : dec; // 6 default ise otomatik kullan
  return n.toLocaleString('tr-TR', { minimumFractionDigits: d, maximumFractionDigits: d });
}

function fmtPct(v, dec = 2) {
  const n = toFloat(v);
  if (n == null) return { text: '-', color: 'text-gray-400', pos: null };
  const text = `${n >= 0 ? '+' : ''}${n.toFixed(dec)}%`;
  return { text, color: n >= 0 ? 'text-emerald-600' : 'text-rose-600', pos: n >= 0 };
}

function fmtBig(v) {
  const n = toFloat(v);
  if (n == null) return '-';
  if (n >= 1e9) return `${(n / 1e9).toFixed(2)}B`;
  if (n >= 1e6) return `${(n / 1e6).toFixed(2)}M`;
  if (n >= 1e3) return `${(n / 1e3).toFixed(2)}K`;
  return n.toFixed(2);
}

/** API bazen uzun float string döner; yüzde olarak kısa göster */
function fmtPercentField(raw) {
  if (raw == null || raw === '') return '-';
  const s = String(raw).trim().replace(/\s/g, '').replace(',', '.').replace(/%/g, '');
  const n = parseFloat(s);
  if (Number.isNaN(n)) return String(raw);
  return `%${n.toFixed(2)}`;
}

// ── Risk göstergesi ───────────────────────────────────────────────────────────

function RiskMeter({ level }) {
  if (level == null) return null;
  const colors = ['', '#22c55e', '#84cc16', '#eab308', '#f97316', '#ef4444', '#dc2626', '#7c3aed'];
  return (
    <div className="flex items-center gap-2">
      <div className="flex gap-1">
        {[1, 2, 3, 4, 5, 6, 7].map(i => (
          <div key={i} className="w-6 h-3 rounded-sm"
            style={{ backgroundColor: i <= level ? colors[level] : '#e5e7eb' }} />
        ))}
      </div>
      <span className="text-sm font-bold" style={{ color: colors[level] }}>
        Risk {level}/7
      </span>
    </div>
  );
}

// ── Getiri kartı ──────────────────────────────────────────────────────────────

function ReturnCard({ label, value }) {
  const { text, color } = fmtPct(value);
  return (
    <div className="bg-gray-50 rounded-xl p-3 text-center">
      <p className="text-xs text-gray-400 mb-1">{label}</p>
      <p className={`text-sm font-bold ${color}`}>{text}</p>
    </div>
  );
}

// ── Fiyat grafiği (KLineCharts) — zaman filtreli ─────────────────────────────

const CHART_RANGES = [
  { key: '1W',  label: '1H',   days: 7   },
  { key: '1M',  label: '1 Ay', days: 30  },
  { key: '3M',  label: '3 Ay', days: 90  },
  { key: '6M',  label: '6 Ay', days: 180 },
  { key: '1Y',  label: '1 Yıl',days: 365 },
];

/* ─── Drawing Toolbar (hisse sayfasıyla aynı yapı) ─── */
const FUND_DRAWING_TOOLS = [
  { group: 'Çizgiler', tools: [
    { id: 'segment',                label: 'Çizgi Segmenti', icon: '╱' },
    { id: 'straightLine',           label: 'Düz Çizgi',      icon: '⟵⟶' },
    { id: 'horizontalStraightLine', label: 'Yatay Çizgi',    icon: '─' },
    { id: 'verticalStraightLine',   label: 'Dikey Çizgi',    icon: '│' },
    { id: 'priceLine',              label: 'Fiyat Çizgisi',  icon: '₺─' },
  ]},
  { group: 'Kanallar', tools: [
    { id: 'parallelStraightLine', label: 'Paralel Kanal', icon: '⫽' },
    { id: 'priceChannelLine',     label: 'Fiyat Kanalı',  icon: '▱' },
  ]},
  { group: 'Fibonacci', tools: [
    { id: 'fibonacciLine', label: 'Fibonacci', icon: 'Fib' },
  ]},
];

function FundDrawingToolbar({ activeTool, onSelectTool, onDeleteSelected, onClearAll }) {
  const [openGroup, setOpenGroup] = useState(null);
  return (
    <div className="flex flex-wrap items-center gap-1.5 p-2 bg-gray-50 border border-gray-200 rounded-xl mb-2">
      {FUND_DRAWING_TOOLS.map(({ group, tools }) => (
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
        <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
        </svg>
      </button>
      <button onClick={onClearAll} title="Tüm çizimleri temizle"
        className="p-1.5 rounded-lg text-gray-500 hover:bg-red-50 hover:text-red-500 transition-all border border-gray-200">
        <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
        </svg>
      </button>
      {activeTool && (
        <span className="ml-auto text-xs text-[#093eaa] font-medium bg-blue-50 px-2 py-1 rounded-lg">
          {FUND_DRAWING_TOOLS.flatMap(g => g.tools).find(t => t.id === activeTool)?.label ?? activeTool}
          <button onClick={() => onSelectTool(null)} className="ml-1.5 opacity-60 hover:opacity-100">✕</button>
        </span>
      )}
    </div>
  );
}

const FUND_MA_DEFS = [
  { period: 7,  color: '#f59e0b', label: 'MA7'  },
  { period: 30, color: '#3b82f6', label: 'MA30' },
  { period: 90, color: '#8b5cf6', label: 'MA90' },
];

function FundPriceChart({ priceHistory }) {
  const [range, setRange]           = useState('1Y');
  const [activeMAs, setActiveMAs]   = useState([]);
  const [showTrend, setShowTrend]   = useState(false);
  const [activeTool, setActiveTool] = useState(null);
  const chartId        = useRef(`fund_chart_${Math.random().toString(36).slice(2)}`);
  const chartRef       = useRef(null);
  const trendOverlayId = useRef(null);

  // Seçili range'e göre slice
  const filtered = useMemo(() => {
    if (!priceHistory || priceHistory.length === 0) return [];
    const sel = CHART_RANGES.find(r => r.key === range);
    if (!sel) return priceHistory;
    const cutoff = Date.now() - sel.days * 86400_000;
    const result = priceHistory.filter(p => new Date(p.date).getTime() >= cutoff);
    return result.length > 0 ? result : priceHistory.slice(-sel.days);
  }, [priceHistory, range]);

  // Trend hesabı — filtrelenmiş veri üzerinden
  const trendInfo = useMemo(() => {
    const vals = filtered.map(p => toFloat(p.price)).filter(v => v != null && v > 0 && !isNaN(v));
    if (vals.length < 2) return null;
    const first = vals[0], last = vals[vals.length - 1];
    const pct = ((last - first) / first) * 100;
    if (isNaN(pct)) return null;
    const label = pct > 1 ? 'Yükselen Trend' : pct < -1 ? 'Düşen Trend' : 'Yatay Trend';
    const color = pct > 1 ? '#10b981' : pct < -1 ? '#ef4444' : '#f59e0b';
    return {
      pct, label, color,
      firstTs:    new Date(filtered[0].date).getTime(),
      lastTs:     new Date(filtered[filtered.length - 1].date).getTime(),
      firstPrice: first,
      lastPrice:  last,
    };
  }, [filtered]);

  // Dönem getirisi
  const vals = filtered.map(p => toFloat(p.price)).filter(v => v != null);
  const periodReturn = vals.length > 1 ? ((vals[vals.length - 1] - vals[0]) / vals[0]) * 100 : null;
  const isUp = (periodReturn ?? 0) >= 0;

  // MA uygula — chart yeniden init etmeden
  const applyMA = useCallback((periods) => {
    const chart = chartRef.current;
    if (!chart) return;
    try { chart.removeIndicator('candle_pane', 'MA'); } catch (_) {}
    if (periods.length > 0) {
      try {
        chart.createIndicator(
          { name: 'MA', calcParams: [...periods].sort((a, b) => a - b) },
          false,
          { id: 'candle_pane' }
        );
      } catch (_) {}
    }
  }, []);

  // MA toggle — tekrar tıklayınca kaldır
  const toggleMA = useCallback((period) => {
    setActiveMAs(prev => {
      const next = prev.includes(period) ? prev.filter(p => p !== period) : [...prev, period];
      applyMA(next);
      return next;
    });
  }, [applyMA]);

  // Trend overlay — chart yeniden init etmeden ekle/kaldır
  const applyTrend = useCallback((show, info) => {
    const chart = chartRef.current;
    if (!chart) return;
    if (trendOverlayId.current) {
      try { chart.removeOverlay({ id: trendOverlayId.current }); } catch (_) {}
      trendOverlayId.current = null;
    }
    if (show && info) {
      try {
        const oid = chart.createOverlay({
          name: 'segment',
          points: [
            { timestamp: info.firstTs, value: info.firstPrice },
            { timestamp: info.lastTs,  value: info.lastPrice  },
          ],
          styles: { line: { color: info.color, size: 2, style: 'dashed', dashedValue: [6, 3] } },
          lock: true,
        });
        trendOverlayId.current = oid ?? null;
      } catch (_) {}
    }
  }, []);

  // Trend toggle — tekrar tıklayınca kaldır
  const toggleTrend = useCallback(() => {
    setShowTrend(prev => {
      const next = !prev;
      applyTrend(next, trendInfo);
      return next;
    });
  }, [applyTrend, trendInfo]);

  // Drawing tool — tekrar tıklayınca kaldır
  const handleSelectTool = useCallback((toolId) => {
    setActiveTool(toolId);
    if (!chartRef.current) return;
    if (toolId) { try { chartRef.current.createOverlay({ name: toolId }); } catch (_) {} }
  }, []);

  const handleDeleteSelected = useCallback(() => {
    try { chartRef.current?.removeOverlay(); } catch (_) {}
  }, []);

  const handleClearAll = useCallback(() => {
    FUND_DRAWING_TOOLS.flatMap(g => g.tools).forEach(t => {
      try { chartRef.current?.removeOverlay({ name: t.id }); } catch (_) {}
    });
    if (trendOverlayId.current) {
      try { chartRef.current?.removeOverlay({ id: trendOverlayId.current }); } catch (_) {}
      trendOverlayId.current = null;
    }
    setActiveTool(null);
  }, []);

  // Custom tooltip state — KLineCharts tooltip yerine React ile
  const [hoverInfo, setHoverInfo] = useState(null); // { price, date, x, y }

  // Hassas decimal hesabı
  const precisionDec = useCallback((price) => {
    if (!price || isNaN(price)) return 6;
    const abs = Math.abs(price);
    return abs >= 100 ? 2 : abs >= 10 ? 3 : abs >= 1 ? 4 : abs >= 0.1 ? 5 : abs >= 0.01 ? 6 : 8;
  }, []);

  // ESC ile drawing tool kapat
  useEffect(() => {
    const handler = (e) => { if (e.key === 'Escape') setActiveTool(null); };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, []);
  useEffect(() => {
    if (filtered.length === 0) return;
    const id = chartId.current;
    klineDispose(id);
    const chart = klineInit(id);
    chartRef.current = chart;
    trendOverlayId.current = null;

    const prices = filtered.map(p => toFloat(p.price)).filter(v => v != null);
    const up = prices.length > 1 && prices[prices.length - 1] >= prices[0];
    const color = up ? '#10b981' : '#ef4444';

    chart.setStyles({
      candle: {
        type: 'area',
        area: {
          lineColor: color, lineSize: 2, value: 'close', smooth: true,
          backgroundColor: [{ offset: 0, color: color + '28' }, { offset: 1, color: color + '00' }],
        },
        tooltip: { showRule: 'none' },
        priceMark: {
          last: { show: true, upColor: color, downColor: color, noChangeColor: color },
          high: { show: false },
          low:  { show: false },
        },
      },
      crosshair: {
        show: true,
        horizontal: { show: true, line: { show: true, style: 'dashed', dashedValue: [4, 2], size: 1, color: '#9ca3af' }, text: { show: true, size: 11, color: '#fff', paddingLeft: 4, paddingRight: 4, paddingTop: 3, paddingBottom: 3, borderRadius: 2, backgroundColor: '#374151' } },
        vertical:   { show: true, line: { show: true, style: 'dashed', dashedValue: [4, 2], size: 1, color: '#9ca3af' }, text: { show: true, size: 11, color: '#fff', paddingLeft: 4, paddingRight: 4, paddingTop: 3, paddingBottom: 3, borderRadius: 2, backgroundColor: '#374151' } },
      },
      xAxis: { show: true },
      yAxis: {
        show: true,
        tickText: {
          formatter: (value) => {
            if (value == null || isNaN(value)) return '';
            const abs = Math.abs(value);
            const dec = abs >= 100 ? 2 : abs >= 10 ? 3 : abs >= 1 ? 4 : abs >= 0.1 ? 5 : abs >= 0.01 ? 6 : 8;
            return value.toFixed(dec);
          },
        },
      },
      grid: { horizontal: { show: true, color: '#f3f4f6', size: 1 }, vertical: { show: false } },
    });

    const klineData = filtered
      .map(p => { const v = toFloat(p.price); if (!v) return null; const ts = new Date(p.date).getTime(); return { timestamp: ts, open: v, high: v, low: v, close: v, volume: 0, turnover: 0 }; })
      .filter(Boolean)
      .sort((a, b) => a.timestamp - b.timestamp);

    chart.applyNewData(klineData);

    // subscribeAction ile crosshair verisini dinle → React custom tooltip
    try {
      chart.subscribeAction('onCrosshairChange', (data) => {
        if (!data || !data.kLineData) {
          setHoverInfo(null);
          return;
        }
        const kd = data.kLineData;
        const price = kd.close ?? kd.open ?? 0;
        if (!price || isNaN(price)) { setHoverInfo(null); return; }
        const d = new Date(kd.timestamp);
        const dateStr = `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;
        const abs = Math.abs(price);
        const dec = abs >= 100 ? 2 : abs >= 10 ? 3 : abs >= 1 ? 4 : abs >= 0.1 ? 5 : abs >= 0.01 ? 6 : 8;
        setHoverInfo({ price: price.toFixed(dec), date: dateStr, x: data.x ?? 0, y: data.y ?? 0 });
      });
    } catch (_) {}

    // Aktif MA'ları yeniden uygula
    if (activeMAs.length > 0) {
      try { chart.createIndicator({ name: 'MA', calcParams: [...activeMAs].sort((a, b) => a - b) }, false, { id: 'candle_pane' }); } catch (_) {}
    }

    // Aktif trend'i yeniden uygula
    if (showTrend && trendInfo) {
      try {
        const oid = chart.createOverlay({
          name: 'segment',
          points: [{ timestamp: trendInfo.firstTs, value: trendInfo.firstPrice }, { timestamp: trendInfo.lastTs, value: trendInfo.lastPrice }],
          styles: { line: { color: trendInfo.color, size: 2, style: 'dashed', dashedValue: [6, 3] } },
          lock: true,
        });
        trendOverlayId.current = oid ?? null;
      } catch (_) {}
    }

    return () => { klineDispose(id); chartRef.current = null; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filtered]);

  if (!priceHistory || priceHistory.length === 0) {
    return <div className="flex items-center justify-center h-48 text-gray-400 text-sm">Fiyat geçmişi bulunamadı.</div>;
  }

  return (
    <div>
      {/* ── Satır 1: Dönem getirisi + Zaman aralığı ── */}
      <div className="flex items-center justify-between mb-2 flex-wrap gap-2">
        <div className="flex items-center gap-2 min-h-[24px]">
          {periodReturn != null && (
            <span className={`text-sm font-semibold ${isUp ? 'text-emerald-600' : 'text-rose-600'}`}>
              {isUp ? '+' : ''}{periodReturn.toFixed(2)}%
            </span>
          )}
          {trendInfo && showTrend && (
            <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-bold"
              style={{ backgroundColor: trendInfo.color + '18', color: trendInfo.color }}>
              {trendInfo.label} ({trendInfo.pct >= 0 ? '+' : ''}{trendInfo.pct.toFixed(1)}%)
            </span>
          )}
        </div>
        <div className="flex gap-1">
          {CHART_RANGES.map(r => (
            <button key={r.key} onClick={() => setRange(r.key)}
              className={`px-3 py-1.5 rounded-lg text-xs font-bold transition-all ${
                range === r.key ? 'bg-[#093eaa] text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
              }`}>
              {r.label}
            </button>
          ))}
        </div>
      </div>

      {/* ── Satır 2: MA + Trend butonları ── */}
      <div className="flex items-center gap-2 mb-2 flex-wrap">
        {FUND_MA_DEFS.map(({ period, color, label }) => {
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
        <div className="w-px h-5 bg-gray-200" />
        <button onClick={toggleTrend}
          className={`px-2.5 py-1 rounded-lg text-xs font-bold transition-all border ${
            showTrend
              ? 'bg-amber-500 text-white border-amber-500'
              : 'bg-gray-50 text-gray-500 border-gray-200 hover:bg-gray-100'
          }`}>
          📈 Trend
        </button>
      </div>

      {/* ── Drawing Toolbar (hisse sayfasıyla aynı) ── */}
      <FundDrawingToolbar
        activeTool={activeTool}
        onSelectTool={handleSelectTool}
        onDeleteSelected={handleDeleteSelected}
        onClearAll={handleClearAll}
      />

      {/* ── Chart ── */}
      <div className="relative"
        onMouseLeave={() => setHoverInfo(null)}>
        {/* Custom tooltip */}
        {hoverInfo && (
          <div className="absolute z-30 pointer-events-none bg-gray-900 text-white text-xs rounded-lg px-3 py-2 shadow-lg whitespace-nowrap"
            style={{
              left: Math.min(hoverInfo.x + 12, 999),
              top: Math.max(hoverInfo.y - 40, 4),
              transform: 'translateX(0)',
            }}>
            <div className="text-gray-400 mb-0.5">{hoverInfo.date}</div>
            <div className="font-bold text-sm">{hoverInfo.price}</div>
          </div>
        )}
        <div id={chartId.current} style={{ width: '100%', height: '320px' }} />
      </div>
    </div>
  );
}

// ── Aylık getiri bar chart ────────────────────────────────────────────────────

function MonthlyReturnChart({ monthlyReturns }) {
  const [hovered, setHovered] = useState(null);

  if (!monthlyReturns || monthlyReturns.length === 0) return null;

  const sorted = [...monthlyReturns].sort((a, b) => {
    const ay = parseInt(a.year) * 12 + parseInt(a.month);
    const by = parseInt(b.year) * 12 + parseInt(b.month);
    return ay - by;
  });
  const recent = sorted.slice(-24);

  const vals = recent.map(m => toFloat(m.value)).filter(v => v != null);
  if (vals.length === 0) return null;

  const maxPos = Math.max(...vals.filter(v => v >= 0), 0.01);
  const maxNeg = Math.max(...vals.filter(v => v < 0).map(Math.abs), 0.01);
  const posHeight = 80;
  const negHeight = vals.some(v => v < 0) ? 40 : 0;

  const MONTH_TR = {
    '1': 'Oca', '2': 'Şub', '3': 'Mar', '4': 'Nis', '5': 'May', '6': 'Haz',
    '7': 'Tem', '8': 'Ağu', '9': 'Eyl', '10': 'Eki', '11': 'Kas', '12': 'Ara'
  };

  return (
    <div className="relative select-none">
      {/* Custom tooltip */}
      {hovered && (
        <div className="absolute z-20 bg-gray-900 text-white text-xs rounded-lg px-3 py-2 pointer-events-none shadow-lg whitespace-nowrap"
          style={{ left: hovered.x, top: hovered.y, transform: 'translate(-50%, -110%)' }}>
          <p className="font-semibold">{hovered.label}</p>
          <p className={hovered.value >= 0 ? 'text-emerald-400' : 'text-rose-400'}>
            {hovered.value >= 0 ? '+' : ''}{hovered.value.toFixed(2)}%
          </p>
        </div>
      )}

      <div className="flex items-end gap-0.5" style={{ height: `${posHeight + negHeight}px` }}>
        {recent.map((m, i) => {
          const v = toFloat(m.value);
          if (v == null) return <div key={i} className="flex-1" />;
          const isPos = v >= 0;
          const barH = isPos
            ? Math.max(3, (Math.abs(v) / maxPos) * posHeight)
            : Math.max(3, (Math.abs(v) / maxNeg) * negHeight);
          const monthLabel = `${MONTH_TR[m.month] ?? m.month} ${m.year}`;

          return (
            <div key={i} className="flex-1 flex flex-col cursor-pointer"
              style={{ height: `${posHeight + negHeight}px` }}
              onMouseEnter={e => {
                const rect = e.currentTarget.getBoundingClientRect();
                const parent = e.currentTarget.closest('.relative').getBoundingClientRect();
                setHovered({ x: rect.left - parent.left + rect.width / 2, y: rect.top - parent.top, label: monthLabel, value: v });
              }}
              onMouseLeave={() => setHovered(null)}>
              <div className="flex flex-col justify-end" style={{ height: `${posHeight}px` }}>
                {isPos && (
                  <div className="w-full rounded-t-sm transition-opacity hover:opacity-75"
                    style={{ height: `${barH}px`, backgroundColor: '#10b981' }} />
                )}
              </div>
              {negHeight > 0 && (
                <div className="flex flex-col justify-start" style={{ height: `${negHeight}px` }}>
                  {!isPos && (
                    <div className="w-full rounded-b-sm transition-opacity hover:opacity-75"
                      style={{ height: `${barH}px`, backgroundColor: '#ef4444' }} />
                  )}
                </div>
              )}
            </div>
          );
        })}
      </div>

      <div className="border-t border-gray-300" />
      <div className="flex items-center gap-0.5 mt-1">
        {recent.map((m, i) => (
          <div key={i} className="flex-1 text-center overflow-hidden">
            {i % 3 === 0 && (
              <span className="text-[9px] text-gray-400 whitespace-nowrap">
                {MONTH_TR[m.month] ?? m.month} {m.year?.slice(-2)}
              </span>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}

// ── Varlık dağılımı (pie + tablo) ────────────────────────────────────────────

const PIE_COLORS = ['#3b82f6','#10b981','#f59e0b','#ef4444','#8b5cf6','#06b6d4','#f97316','#84cc16','#ec4899','#6366f1'];

function AssetAllocationChart({ assetAllocation }) {
  const [hovered, setHovered] = useState(null);
  if (!assetAllocation || assetAllocation.length === 0) return null;

  const total = assetAllocation.reduce((s, a) => s + (toFloat(a.percentage) ?? 0), 0);
  const size = 220;
  const cx = size / 2, cy = size / 2, r = 85, innerR = 44;

  let angle = -90;
  const slices = assetAllocation.map((a, i) => {
    const pct = (toFloat(a.percentage) ?? 0) / (total || 1);
    const startAngle = angle;
    const sweep = pct * 360;
    angle += sweep;
    const endAngle = angle;

    const toRad = deg => (deg * Math.PI) / 180;
    const x1 = cx + r * Math.cos(toRad(startAngle));
    const y1 = cy + r * Math.sin(toRad(startAngle));
    const x2 = cx + r * Math.cos(toRad(endAngle));
    const y2 = cy + r * Math.sin(toRad(endAngle));
    const xi1 = cx + innerR * Math.cos(toRad(startAngle));
    const yi1 = cy + innerR * Math.sin(toRad(startAngle));
    const xi2 = cx + innerR * Math.cos(toRad(endAngle));
    const yi2 = cy + innerR * Math.sin(toRad(endAngle));
    const large = sweep > 180 ? 1 : 0;

    const d = sweep < 0.1 ? '' :
      `M ${x1} ${y1} A ${r} ${r} 0 ${large} 1 ${x2} ${y2} L ${xi2} ${yi2} A ${innerR} ${innerR} 0 ${large} 0 ${xi1} ${yi1} Z`;

    return { ...a, d, color: PIE_COLORS[i % PIE_COLORS.length], pct: pct * 100 };
  });

  return (
    <div className="flex flex-col sm:flex-row items-center gap-6">
      {/* Donut pie */}
      <div className="shrink-0">
        <svg width={size} height={size}>
          {slices.map((s, i) => (
            <path key={i} d={s.d}
              fill={s.color}
              opacity={hovered === null || hovered === i ? 0.9 : 0.4}
              stroke="white" strokeWidth={1.5}
              className="cursor-pointer transition-opacity duration-150"
              onMouseEnter={() => setHovered(i)}
              onMouseLeave={() => setHovered(null)} />
          ))}
          {hovered !== null && slices[hovered] ? (
            <>
              <text x={cx} y={cy - 7} textAnchor="middle" fontSize={10} fill="#6b7280">
                {slices[hovered].name?.length > 16 ? slices[hovered].name.slice(0, 16) + '…' : slices[hovered].name}
              </text>
              <text x={cx} y={cy + 12} textAnchor="middle" fontSize={18} fontWeight="bold" fill="#111827">
                %{slices[hovered].pct.toFixed(1)}
              </text>
            </>
          ) : (
            <text x={cx} y={cy + 5} textAnchor="middle" fontSize={13} fill="#9ca3af">Dağılım</text>
          )}
        </svg>
      </div>

      {/* Tablo */}
      <div className="flex-1 w-full">
        <div className="grid grid-cols-[1fr_auto] text-xs text-gray-400 font-semibold pb-1 border-b border-gray-100 mb-1 px-1">
          <span>Varlık</span>
          <span>% Oran</span>
        </div>
        {slices.map((s, i) => (
          <div key={i}
            className={`grid grid-cols-[1fr_auto] text-sm py-1.5 border-b border-gray-50 px-1 rounded transition-colors cursor-default ${hovered === i ? 'bg-gray-50' : ''}`}
            onMouseEnter={() => setHovered(i)}
            onMouseLeave={() => setHovered(null)}>
            <div className="flex items-center gap-2">
              <div className="w-2.5 h-2.5 rounded-full shrink-0" style={{ backgroundColor: s.color }} />
              <span className="text-gray-700">{s.name}</span>
            </div>
            <span className="text-gray-900 font-semibold">%{(toFloat(s.percentage) ?? 0).toFixed(2)}</span>
          </div>
        ))}
        <p className="text-[10px] text-gray-400 mt-2 px-1">Veriler saatlik olarak güncellenmektedir.</p>
      </div>
    </div>
  );
}

export default function TefasFundDetailPage() {
  const { code }   = useParams();
  const location   = useLocation();
  const [fund, setFund]       = useState(location.state?.listItem ?? null);
  const [detail, setDetail]   = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError]     = useState('');
  const [activeTab, setActiveTab] = useState('performance');

  // sourceCode: listItem'dan al (BES=TPF, OKS=TAF, TEFAS=TMF)
  // fundCategory → sourceCode mapping
  const sourceCode = (() => {
    const cat = location.state?.listItem?.fundCategory;
    if (cat === 'PENSION_FUND') return 'TPF';
    if (cat === 'AUTO_ENROLLMENT_FUND') return 'TAF';
    // Osmanlı: uniqueCode'dan çıkar (örn: OSL:TMF → TMF)
    const uc = location.state?.listItem?.uniqueCode;
    if (uc && uc.includes(':')) return uc.split(':')[1];
    return 'TMF';
  })();

  useEffect(() => {
    setLoading(true);
    setError('');
    getRasyonetFundDetail(code, sourceCode)
      .then(data => {
        if (!data) setError('Fon bilgisi bulunamadı.');
        else setDetail(data);
      })
      .catch(() => setError('Veriler yüklenemedi.'))
      .finally(() => setLoading(false));
  }, [code, sourceCode]);

  const d = detail;
  const name = d?.name ?? fund?.name ?? code;
  const price = d?.price ?? fund?.price;
  const ret1m = d?.returnOneMonth ?? fund?.returnOneMonth;
  const { text: ret1mText, color: ret1mColor, pos: ret1mPos } = fmtPct(ret1m);

  const TABS = [
    { key: 'performance', label: 'Performans' },
    { key: 'chart',       label: 'Grafik' },
    { key: 'info',        label: 'Fon Bilgileri' },
  ];

  return (
    <div className="max-w-5xl mx-auto space-y-5">
      {/* Geri */}
      <Link to="/market/tefas" className="inline-flex items-center gap-1.5 text-sm text-[#093eaa] font-semibold hover:underline">
        <ArrowLeft className="w-4 h-4" /> TEFAS Fonları
      </Link>

      {/* ── Header ── */}
      <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">
        <div className="flex items-start justify-between flex-wrap gap-4">
          <div>
            <div className="flex items-center gap-3 mb-1">
              <span className="px-2.5 py-1 bg-[#093eaa] text-white text-xs font-bold rounded-lg font-mono">{code}</span>
              {d?.fundType && (
                <span className="px-2 py-0.5 bg-blue-50 text-blue-700 text-xs font-semibold rounded-full">{d.fundType}</span>
              )}
            </div>
            <h1 className="text-xl font-bold text-gray-900 leading-tight">{name}</h1>
            {d?.managerName && (
              <p className="text-sm text-gray-500 mt-0.5">{d.managerName}</p>
            )}
          </div>
          <div className="text-right">
            <p className="text-3xl font-bold text-gray-900 font-mono">{fmt(price, 6)}</p>
            <p className="text-xs text-gray-400 mt-0.5">TL</p>
            {ret1m != null && (
              <p className={`text-sm font-semibold flex items-center justify-end gap-1 mt-1 ${ret1mColor}`}>
                {ret1mPos ? <TrendingUp className="w-4 h-4" /> : <TrendingDown className="w-4 h-4" />}
                1 Ay: {ret1mText}
              </p>
            )}
          </div>
        </div>

        {/* Risk + KAP */}
        <div className="flex items-center justify-between mt-4 flex-wrap gap-3">
          <RiskMeter level={d?.riskLevel} />
          {d?.kapLink && (
            <a href={d.kapLink} target="_blank" rel="noopener noreferrer"
              className="inline-flex items-center gap-1.5 text-xs text-[#093eaa] hover:underline font-semibold">
              <ExternalLink className="w-3.5 h-3.5" /> KAP Fon Bilgileri
            </a>
          )}
        </div>
      </div>

      {/* ── Loading / Error ── */}
      {loading && (
        <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-12 flex items-center justify-center">
          <div className="flex gap-2">
            <div className="w-2 h-2 bg-[#093eaa] rounded-full animate-bounce" />
            <div className="w-2 h-2 bg-[#093eaa]/60 rounded-full animate-bounce [animation-delay:100ms]" />
            <div className="w-2 h-2 bg-[#093eaa]/30 rounded-full animate-bounce [animation-delay:200ms]" />
          </div>
        </div>
      )}
      {error && (
        <div className="bg-rose-50 border border-rose-200 rounded-2xl p-4 text-rose-600 text-sm">{error}</div>
      )}

      {/* ── Tab içeriği ── */}
      {!loading && !error && d && (
        <>
          {/* Tab butonları */}
          <div className="flex gap-1 bg-gray-100 rounded-xl p-1 w-fit">
            {TABS.map(t => (
              <button key={t.key} onClick={() => setActiveTab(t.key)}
                className={`px-4 py-2 rounded-lg text-sm font-semibold transition-all ${
                  activeTab === t.key ? 'bg-white text-[#093eaa] shadow-sm' : 'text-gray-500 hover:text-gray-700'
                }`}>
                {t.label}
              </button>
            ))}
          </div>

          {/* ── Performans tab ── */}
          {activeTab === 'performance' && (
            <div className="space-y-5">
              <div className="grid grid-cols-3 sm:grid-cols-5 lg:grid-cols-9 gap-2">
                <ReturnCard label="Günlük" value={d.returnOneDay} />
                <ReturnCard label="Haftalık" value={d.returnOneWeek} />
                <ReturnCard label="1 Ay" value={d.returnOneMonth} />
                <ReturnCard label="3 Ay" value={d.returnThreeMonths} />
                <ReturnCard label="6 Ay" value={d.returnSixMonths} />
                <ReturnCard label="YBG" value={d.returnYearToDate} />
                <ReturnCard label="1 Yıl" value={d.returnOneYear} />
                <ReturnCard label="3 Yıl" value={d.returnThreeYears} />
                <ReturnCard label="5 Yıl" value={d.returnFiveYears} />
              </div>

              {/* Aylık getiri grafiği */}
              {d.monthlyReturns && d.monthlyReturns.length > 0 && (
                <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">
                  <h2 className="font-bold text-gray-900 mb-4">Aylık Getiri (Son 24 Ay)</h2>
                  <MonthlyReturnChart monthlyReturns={d.monthlyReturns} />
                </div>
              )}

              {/* Risk bilgileri */}
              {(d.riskBest || d.riskWorst || d.riskPositiveRateOfReturn) && (
                <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">
                  <h2 className="font-bold text-gray-900 mb-4">Risk ve Performans</h2>
                  <div className="grid grid-cols-3 gap-4">
                    <div className="bg-emerald-50 rounded-xl p-4 text-center">
                      <p className="text-xs text-gray-500 mb-1">En İyi Ay Getirisi</p>
                      <p className="text-lg font-bold text-emerald-600">
                        {d.riskBest ? `+${parseFloat(d.riskBest).toFixed(2)}%` : '-'}
                      </p>
                    </div>
                    <div className="bg-rose-50 rounded-xl p-4 text-center">
                      <p className="text-xs text-gray-500 mb-1">En Kötü Ay Getirisi</p>
                      <p className="text-lg font-bold text-rose-600">
                        {d.riskWorst ? `${parseFloat(d.riskWorst).toFixed(2)}%` : '-'}
                      </p>
                    </div>
                    <div className="bg-blue-50 rounded-xl p-4 text-center">
                      <p className="text-xs text-gray-500 mb-1">Pozitif Getiri Oranı</p>
                      <p className="text-lg font-bold text-[#093eaa]">{d.riskPositiveRateOfReturn ?? '-'}</p>
                    </div>
                  </div>
                </div>
              )}
            </div>
          )}

          {/* ── Grafik tab ── */}
          {activeTab === 'chart' && (
            <div className="space-y-5">
              <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">
                <div className="flex items-center justify-between mb-2">
                  <h2 className="font-bold text-gray-900">Fiyat Grafiği</h2>
                  {d.priceHistory && (
                    <span className="text-xs text-gray-400">{d.priceHistory.length} veri noktası</span>
                  )}
                </div>
                <FundPriceChart priceHistory={d.priceHistory} />
                <p className="text-xs text-gray-400 mt-3">Kaynak: Rasyonet · YatırımDirekt</p>
              </div>

              {d.assetAllocation && d.assetAllocation.length > 0 && (
                <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">
                  <h2 className="font-bold text-gray-900 mb-4">Varlık Dağılımı</h2>
                  <AssetAllocationChart assetAllocation={d.assetAllocation} />
                </div>
              )}
            </div>
          )}

          {/* ── Fon Bilgileri tab ── */}
          {activeTab === 'info' && (
            <div className="space-y-5">
              {/* Temel bilgiler */}
              <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">
                <h2 className="font-bold text-gray-900 mb-4">Fon Temel Bilgileri</h2>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-0">
                  {[
                    ['Fon Kodu', d.code ?? '-'],
                    ['Fon Adı', d.name ?? '-'],
                    ['Fon Türü', d.fundType ?? '-'],
                    ['Birim Pay Değeri', fmt(d.price, 6)],
                    ['Fon Büyüklüğü', d.marketCap != null ? `${fmtBig(d.marketCap)} TL` : '-'],
                    ['Fon Büyüklüğü USD', d.marketCapUsd != null ? `${fmtBig(d.marketCapUsd)} USD` : '-'],
                    ['Alım Valörü', d.buySettlement ? `T+${d.buySettlement}` : '-'],
                    ['Satım Valörü', d.sellSettlement ? `T+${d.sellSettlement}` : '-'],
                    ['Min. Alım Miktarı', d.minimumQuantitySales ?? '-'],
                    ['Yıllık Yönetim Ücreti', fmtPercentField(d.managementFeeAnnual)],
                    ['Komisyon', d.commission != null && d.commission !== '' ? fmtPercentField(d.commission) : '-'],
                    ['Fon Yöneticisi', d.managerName ?? '-'],
                    ['Kurucu', d.founderName ?? '-'],
                  ].map(([label, value]) => (
                    <div key={label} className="flex justify-between items-start py-3 border-b border-gray-100 px-1">
                      <span className="text-sm text-gray-500 w-44 shrink-0">{label}</span>
                      <span className="text-sm text-gray-900 font-medium text-right">{value}</span>
                    </div>
                  ))}
                </div>
              </div>

              {/* Strateji */}
              {d.strategy && (
                <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">
                  <h2 className="font-bold text-gray-900 mb-3">Yatırım Stratejisi</h2>
                  <p className="text-sm text-gray-700 leading-relaxed">{d.strategy}</p>
                </div>
              )}
            </div>
          )}
        </>
      )}

      {/* Disclaimer */}
      <div className="bg-amber-50 border border-amber-200 rounded-2xl p-4">
        <p className="text-xs text-amber-800 leading-relaxed">
          <span className="font-bold">⚠ Uyarı:</span> Bu sayfada yer alan veriler yatırım tavsiyesi değildir.
          Geçmiş performans gelecekteki getirilerin garantisi değildir.
          Veriler Rasyonet / YatırımDirekt kaynaklıdır.
        </p>
      </div>
    </div>
  );
}
