import { useEffect, useState, useCallback, useRef, useMemo } from 'react';
import { useParams, Link } from 'react-router-dom';
import {
  ArrowLeft, RefreshCw, TrendingUp, TrendingDown, Star, ArrowLeftRight, BarChart2,
  ChevronDown, Trash2, X, SlidersHorizontal, Globe2, Landmark,
} from 'lucide-react';
import { getFxHistory, getFxTcmb, getFxOpen, getBankCurrencyRates } from '../../../api/marketApi.js';
import { FX_META, FlagImg } from '../../../utils/fxMeta.jsx';
import { init as klineInit, dispose as klineDispose, registerOverlay } from 'klinecharts';
import { useTranslation } from '../../../context/LanguageContext';
import UniversalCompareButton from '../../../components/common/UniversalCompareButton';
import TrendBadge from '../../../components/common/TrendBadge';
import InstrumentActionButtons from '../../../components/instrument/InstrumentActionButtons';
import { buildTrendItem } from '../../../utils/trendUtils';

// ── Helpers ───────────────────────────────────────────────────────────────────
function fmt(v, dec = 4) {
  if (v == null) return '-';
  return parseFloat(v).toLocaleString('tr-TR', { minimumFractionDigits: dec, maximumFractionDigits: dec });
}
function fmtAuto(v) {
  if (v == null) return '-';
  const n = parseFloat(v);
  if (isNaN(n)) return '-';
  return n.toLocaleString('tr-TR', { minimumFractionDigits: 4, maximumFractionDigits: 4 });
}

const RANGES = ['1W', '1M', '3M', '6M', '1Y', 'ALL'];
const RANGE_LABELS = { '1W': '1H', '1M': '1A', '3M': '3A', '6M': '6A', '1Y': '1Y', 'ALL': 'Tümü' };

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

// ── Çizim araçları (hisse detay sayfasındaki toolbar tasarımı) ─────────────────
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

/* ─── Çizim araç çubuğu (hisse detay tasarımı) ─── */
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
              <div className="fixed inset-0 z-40" onClick={() => setOpenGroup(null)} />
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

/* ─── FX grafiği (çizim + indikatör toolbar'lı, küçültülmüş) ─── */
function FxChart({ chartPoints, lineColor, mainLabel, range, onRangeChange, loadingChart }) {
  const { t } = useTranslation();
  const chartId = useRef(`kline_fx_${Date.now()}`);
  const chartRef = useRef(null);
  const indicatorPaneIds = useRef({});

  const [activeMAs, setActiveMAs] = useState([]);
  const [activeSubInds, setActiveSubInds] = useState([]);
  const [activeTool, setActiveTool] = useState(null);
  const [indMenuOpen, setIndMenuOpen] = useState(false);

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

  const handleSelectTool = useCallback((toolId) => {
    setActiveTool(toolId);
    if (!chartRef.current) return;
    if (toolId) { try { chartRef.current.createOverlay({ name: toolId }); } catch (_) { /* yoksay */ } }
  }, []);
  const handleDeleteSelected = useCallback(() => { try { chartRef.current?.removeOverlay(); } catch (_) { /* yoksay */ } }, []);
  const handleClearAll = useCallback(() => {
    DRAWING_TOOLS.flatMap(g => g.tools).forEach(tool => { try { chartRef.current?.removeOverlay({ name: tool.id }); } catch (_) { /* yoksay */ } });
    setActiveTool(null);
  }, []);

  useEffect(() => {
    const handler = (e) => { if (e.key === 'Escape') setActiveTool(null); };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, []);

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
          rect: { offsetLeft: 8, offsetTop: 8, offsetRight: 8, paddingLeft: 10, paddingRight: 10, paddingTop: 8, paddingBottom: 8, borderRadius: 8, borderSize: 1, borderColor: '#e5e7eb', color: 'rgba(255,255,255,0.94)' },
          custom: (data) => {
            const d = data?.current ?? {};
            const date = d.timestamp ? new Date(d.timestamp).toLocaleDateString('tr-TR', { day: '2-digit', month: 'short', year: 'numeric' }) : '';
            return [
              { title: '', value: { text: date, color: '#6b7280' } },
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

    return () => { klineDispose(id); chartRef.current = null; indicatorPaneIds.current = {}; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [chartPoints, lineColor, mainLabel]);

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
          <div className="fixed inset-0 z-40" onClick={() => setIndMenuOpen(false)} />
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

// ── Open Exchange Rates mini kart ──────────────────────────────────────────────
function OpenRateCard({ sym, tryPerUnit }) {
  const { t } = useTranslation();
  return (
    <section className="bg-white rounded-3xl border border-[#e2e8f0] shadow-[0_1px_3px_rgba(15,23,42,0.06)] p-5">
      <h2 className="font-bold text-[#1a1c1e] mb-3 flex items-center gap-2 text-sm">
        <Globe2 className="w-4 h-4 text-[#093eaa]" /> Open Exchange Rates
      </h2>
      {tryPerUnit != null ? (
        <>
          <p className="text-2xl font-black text-[#1a1c1e] tabular-nums">
            {tryPerUnit.toLocaleString('tr-TR', { minimumFractionDigits: 4, maximumFractionDigits: 4 })} <span className="text-sm font-semibold text-[#5a6472]">TRY</span>
          </p>
          <p className="text-xs text-[#5a6472] mt-0.5">1 {sym} = {tryPerUnit.toLocaleString('tr-TR', { minimumFractionDigits: 4, maximumFractionDigits: 4 })} TRY</p>
          <span className="inline-flex items-center gap-1 mt-2 text-[11px] font-semibold text-emerald-700 bg-emerald-50 px-2 py-0.5 rounded-full">
            <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse" /> {t('Gerçek zamanlıya yakın')}
          </span>
        </>
      ) : (
        <p className="text-sm text-[#9aa6b6]">{t('Veri bulunamadı.')}</p>
      )}
    </section>
  );
}

// ── Diğer Bankalar kart ────────────────────────────────────────────────────────
function BankRatesCard({ rates, loading }) {
  const { t } = useTranslation();
  const present = rates.filter(r => r.has);
  const absent  = rates.filter(r => !r.has);

  return (
    <section className="bg-white rounded-3xl border border-[#e2e8f0] shadow-[0_1px_3px_rgba(15,23,42,0.06)] p-5">
      <div className="flex items-center justify-between mb-3">
        <h2 className="font-bold text-[#1a1c1e] flex items-center gap-2 text-sm">
          <Landmark className="w-4 h-4 text-[#093eaa]" /> {t('Diğer Bankalar')}
          {present.length > 0 && <span className="text-xs font-semibold text-[#9aa6b6]">({present.length})</span>}
        </h2>
        <Link to="/market/fx?tab=banks" className="text-xs font-semibold text-[#093eaa] hover:underline">{t('Tümünü Gör')}</Link>
      </div>

      {loading ? (
        <p className="text-sm text-[#9aa6b6] py-2">{t('Banka kurları yükleniyor...')}</p>
      ) : present.length === 0 ? (
        <div className="py-4 text-center">
          <p className="text-sm font-semibold text-[#1a1c1e]">{t('Banka kuru bulunamadı')}</p>
          <p className="text-xs text-[#9aa6b6] mt-0.5">{t('Bu döviz için banka kuru yok.')}</p>
        </div>
      ) : (
        <>
          <div className="grid grid-cols-[1fr_auto_auto] gap-x-3 text-[10px] font-bold text-[#9aa6b6] uppercase tracking-wider pb-1.5 border-b border-[#eef2f8]">
            <span>{t('Banka')}</span>
            <span className="text-right">{t('Alış')}</span>
            <span className="text-right">{t('Satış')}</span>
          </div>
          <div className="divide-y divide-[#f1f5f9] m3-stagger">
            {present.map((r, i) => (
              <div key={`${r.bankName}-${i}`} className="grid grid-cols-[1fr_auto_auto] gap-x-3 items-center py-2 text-sm">
                <span className="font-semibold text-[#1a1c1e] truncate">{r.bankName}</span>
                <span className="text-right tabular-nums text-[#5a6472]">{fmtAuto(r.buyRate)}</span>
                <span className="text-right tabular-nums font-semibold text-[#1a1c1e]">{fmtAuto(r.sellRate)}</span>
              </div>
            ))}
          </div>
          {absent.length > 0 && (
            <p className="mt-3 pt-3 border-t border-[#eef2f8] text-[11px] text-[#9aa6b6] leading-snug">
              {t('{n} banka bu dövizi kote etmiyor', { n: absent.length })}: {absent.map(r => r.bankName).join(', ')}
            </p>
          )}
        </>
      )}
    </section>
  );
}

// ── Döviz Çevirici (kompakt — kenar sütunu) ────────────────────────────────────
function CurrencyChip({ value, onChange, currencies }) {
  return (
    <div className="relative shrink-0">
      <select
        value={value}
        onChange={e => onChange(e.target.value)}
        className="appearance-none bg-[#eef2f8] hover:bg-[#e3eaf6] text-[#093eaa] font-bold text-xs rounded-full pl-2.5 pr-6 py-1 cursor-pointer focus:outline-none focus:ring-2 focus:ring-[#093eaa]/30 transition-colors"
      >
        {currencies.map(c => <option key={c} value={c}>{c}</option>)}
      </select>
      <ChevronDown className="w-3 h-3 absolute right-1.5 top-1/2 -translate-y-1/2 text-[#093eaa] pointer-events-none" />
    </div>
  );
}

function FxConverter({ symbol, allRates, embedded = false }) {
  const { t } = useTranslation();
  const sym = symbol?.toUpperCase();
  const currencies = useMemo(() => ['TRY', ...Object.keys(FX_META).filter(k => k !== 'TRY')], []);

  const [fromCur, setFromCur] = useState(sym ?? 'USD');
  const [toCur, setToCur]     = useState('TRY');
  const [amount, setAmount]   = useState('1');

  useEffect(() => { if (sym) setFromCur(sym); }, [sym]);

  function getRateTry(cur) {
    if (cur === 'TRY') return 1;
    const r = allRates.find(x => x.symbol === cur);
    return r?.sell ? parseFloat(r.sell) : null;
  }
  const fromRate = getRateTry(fromCur);
  const toRate   = getRateTry(toCur);

  function calculate() {
    const a = parseFloat(amount || 0);
    if (!a || isNaN(a) || !fromRate || !toRate) return '-';
    return ((a * fromRate) / toRate).toLocaleString('tr-TR', { minimumFractionDigits: 2, maximumFractionDigits: 6 });
  }
  const unitRate = (fromRate && toRate)
    ? (fromRate / toRate).toLocaleString('tr-TR', { minimumFractionDigits: 4, maximumFractionDigits: 6 }) : null;

  function swap() { setFromCur(toCur); setToCur(fromCur); }

  return (
    <div className={embedded ? '' : 'bg-white rounded-3xl border border-[#e2e8f0] shadow-[0_1px_3px_rgba(15,23,42,0.06)] p-5'}>
      <h2 className="font-bold text-[#1a1c1e] mb-3 flex items-center gap-2 text-sm">
        <ArrowLeftRight className="w-4 h-4 text-[#093eaa]" /> {t('Döviz Çevirici')}
      </h2>

      <div className="space-y-2">
        <div className="relative">
          <input
            type="number" value={amount} onChange={e => setAmount(e.target.value)} placeholder="1"
            className="w-full pl-3 pr-20 py-2.5 rounded-xl border border-[#e2e8f0] bg-white text-[#1a1c1e] font-semibold tabular-nums focus:outline-none focus:ring-2 focus:ring-[#093eaa]/30 focus:border-[#093eaa] transition-all"
          />
          <div className="absolute right-2 top-1/2 -translate-y-1/2"><CurrencyChip value={fromCur} onChange={setFromCur} currencies={currencies} /></div>
        </div>

        <div className="flex justify-center">
          <button onClick={swap} title={t('Ters çevir')}
            className="m3-state w-8 h-8 rounded-full bg-[#093eaa] text-white flex items-center justify-center shadow-[0_3px_10px_-3px_rgba(9,62,170,0.6)] hover:rotate-180 transition-transform duration-500">
            <ArrowLeftRight className="w-4 h-4" />
          </button>
        </div>

        <div className="relative">
          <input
            type="text" readOnly value={calculate()}
            className="w-full pl-3 pr-20 py-2.5 rounded-xl border border-[#eef2f8] bg-[#f6f8fc] text-[#1a1c1e] font-semibold tabular-nums"
          />
          <div className="absolute right-2 top-1/2 -translate-y-1/2"><CurrencyChip value={toCur} onChange={setToCur} currencies={currencies} /></div>
        </div>
      </div>

      <div className="mt-3 p-3 rounded-xl bg-[#eef4ff] border border-[#dbe6ff] text-center">
        {unitRate ? (
          <p className="text-sm font-black text-[#093eaa] tabular-nums">1 {fromCur} = {unitRate} {toCur}</p>
        ) : (
          <p className="text-xs text-[#5a6472]">{t('Bu döviz çifti için kur bulunamadı.')}</p>
        )}
      </div>
    </div>
  );
}

// ── Main Page ─────────────────────────────────────────────────────────────────
export default function FxDetailPage() {
  const { t } = useTranslation();
  const { symbol } = useParams();
  const sym = symbol?.toUpperCase();

  const [history, setHistory]       = useState(null);
  const [loading, setLoading]       = useState(true);
  const [loadingChart, setLoadingChart] = useState(false);
  const [range, setRange]           = useState('1M');
  const [currentRate, setCurrentRate] = useState(null);
  const [allRates, setAllRates]     = useState([]);

  // Yan panel verileri
  const [allBankRows, setAllBankRows] = useState([]);
  const [loadingBanks, setLoadingBanks] = useState(true);
  const [openTryPerUnit, setOpenTryPerUnit] = useState(null);

  // Watchlist
  const [inWatchlist, setInWatchlist] = useState(() => {
    try { return JSON.parse(localStorage.getItem('fx_watchlist') ?? '[]').includes(sym); } catch { return false; }
  });
  function toggleWatchlist() {
    try {
      const wl = JSON.parse(localStorage.getItem('fx_watchlist') ?? '[]');
      const updated = inWatchlist ? wl.filter(x => x !== sym) : [...wl, sym];
      localStorage.setItem('fx_watchlist', JSON.stringify(updated));
      setInWatchlist(!inWatchlist);
    } catch {}
  }

  // TCMB rates
  useEffect(() => {
    getFxTcmb()
      .then(data => {
        const rates = data?.rates ?? [];
        setAllRates(rates);
        setCurrentRate(rates.find(r => r.symbol === sym) ?? null);
      })
      .catch(() => {});
  }, [sym]);

  // Banka kurları — tüm bankaları çek, bu döviz için hepsini göster (kuru olmayan banka da listelenir)
  useEffect(() => {
    setLoadingBanks(true);
    getBankCurrencyRates()
      .then(data => setAllBankRows(Array.isArray(data) ? data : []))
      .catch(() => setAllBankRows([]))
      .finally(() => setLoadingBanks(false));
  }, []);

  const bankRows = useMemo(() => {
    const banks = [...new Set(allBankRows.map(r => r.bankName).filter(Boolean))];
    const rows = banks.map(b => {
      const row = allBankRows.find(r => r.bankName === b && r.currencyCode === sym);
      return { bankName: b, buyRate: row?.buyRate ?? null, sellRate: row?.sellRate ?? null, has: !!row };
    });
    // Kuru olan bankalar üstte, ardından alfabetik
    return rows.sort((a, b) => (Number(b.has) - Number(a.has)) || a.bankName.localeCompare(b.bankName, 'tr'));
  }, [allBankRows, sym]);

  // Open Exchange Rates — base TRY → 1 sym = (1 / value) TRY
  useEffect(() => {
    setOpenTryPerUnit(null);
    getFxOpen('TRY')
      .then(data => {
        const item = (data?.rates ?? []).find(r => r.symbol === sym);
        const v = item?.sell != null ? parseFloat(item.sell) : null;
        if (v && v > 0) setOpenTryPerUnit(1 / v);
      })
      .catch(() => {});
  }, [sym]);

  // History
  const loadHistory = useCallback(() => {
    setLoadingChart(true);
    getFxHistory(symbol, range)
      .then(data => { setHistory(data); setLoading(false); })
      .catch(() => { setHistory(null); setLoading(false); })
      .finally(() => setLoadingChart(false));
  }, [symbol, range]);
  useEffect(() => { setLoading(true); loadHistory(); }, [loadHistory]);

  const chartPoints = useMemo(
    () => history?.points?.map(p => ({ date: p.date, value: parseFloat(p.close) })) ?? [],
    [history],
  );
  const isUp = chartPoints.length > 1 && chartPoints[chartPoints.length - 1].value >= chartPoints[0].value;
  const strokeColor = isUp ? '#10b981' : '#ef4444';

  const trendItem = useMemo(() => buildTrendItem(chartPoints.map(p => p.value), 'FX'), [chartPoints]);

  const buy  = currentRate?.buy;
  const sell = currentRate?.sell;
  const unit = currentRate?.unit;
  const effBuy  = currentRate?.effectiveBuy;
  const effSell = currentRate?.effectiveSell;

  const firstVal = chartPoints.length > 1 ? chartPoints[0].value : null;
  const lastVal  = chartPoints.length > 1 ? chartPoints[chartPoints.length - 1].value : null;
  const change   = firstVal && lastVal ? lastVal - firstVal : null;
  const changePct = firstVal && change != null ? (change / firstVal) * 100 : null;
  const changePositive = (change ?? 0) >= 0;

  const statCards = [
    { label: 'Döviz Alış',   en: 'Forex Buying',     value: fmt(buy) },
    { label: 'Döviz Satış',  en: 'Forex Selling',    value: fmt(sell) },
    ...(effBuy != null  ? [{ label: 'Efektif Alış',  en: 'Banknote Buying',  value: fmt(effBuy) }]  : []),
    ...(effSell != null ? [{ label: 'Efektif Satış', en: 'Banknote Selling', value: fmt(effSell) }] : []),
    { label: 'Birim',        en: 'Unit',             value: unit ?? '-' },
  ];

  return (
    <div className="space-y-6 m3-fade-in">
      {/* Üst aksiyon çubuğu */}
      <div className="flex items-center justify-between flex-wrap gap-3">
        <Link to="/market/fx" className="m3-state inline-flex items-center gap-1.5 text-sm text-[#093eaa] font-semibold hover:underline rounded-full px-2 py-1 -ml-2">
          <ArrowLeft className="w-4 h-4" /> {t('Döviz Kurları')}
        </Link>
        <button
          onClick={toggleWatchlist}
          className={`m3-state inline-flex items-center gap-2 px-4 py-2 rounded-full text-sm font-semibold border transition-all ${
            inWatchlist ? 'bg-amber-50 border-amber-300 text-amber-700' : 'bg-white border-[#e2e8f0] text-[#5a6472] hover:border-[#093eaa]/40'
          }`}>
          <Star className={`w-4 h-4 ${inWatchlist ? 'fill-amber-400 text-amber-400' : ''}`} />
          {inWatchlist ? t('İzleme Listesinde') : t('İzleme Listesine Ekle')}
        </button>
      </div>

      {/* Başlık kartı — sol: kimlik+fiyat+istatistik · sağ: döviz çevirici */}
      <div className="bg-white rounded-3xl border border-[#e2e8f0] shadow-[0_1px_3px_rgba(15,23,42,0.06)] p-6">
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          {/* Sol */}
          <div className="flex flex-col">
            <div className="flex items-center gap-4">
              <div className="w-14 h-14 rounded-full bg-[#eef2f8] ring-1 ring-[#e2e8f0] flex items-center justify-center overflow-hidden shrink-0">
                <FlagImg cc={FX_META[sym]?.cc} size={40} />
              </div>
              <div>
                <div className="flex items-center gap-2 flex-wrap">
                  <h1 className="text-2xl sm:text-3xl font-black text-[#1a1c1e] leading-tight">{sym}/TRY</h1>
                  <span className="bg-[#eef2f8] px-2.5 py-1 rounded-full text-xs font-semibold text-[#5a6472]">{FX_META[sym]?.ad ?? sym}</span>
                </div>
                {loading ? (
                  <div className="flex items-center gap-1.5 py-2">
                    <div className="w-2 h-2 bg-[#093eaa] rounded-full animate-bounce" />
                    <div className="w-2 h-2 bg-[#093eaa]/60 rounded-full animate-bounce [animation-delay:120ms]" />
                    <div className="w-2 h-2 bg-[#093eaa]/30 rounded-full animate-bounce [animation-delay:240ms]" />
                  </div>
                ) : (
                  <div className="flex items-end gap-3 flex-wrap mt-2">
                    {sell != null && <span className="text-3xl sm:text-4xl font-black text-[#1a1c1e] tabular-nums tracking-tight">{fmt(sell)}</span>}
                    {change != null && (
                      <span className={`flex items-center gap-1 text-sm font-bold mb-1.5 ${changePositive ? 'text-emerald-600' : 'text-rose-600'}`}>
                        {changePositive ? <TrendingUp className="w-4 h-4" /> : <TrendingDown className="w-4 h-4" />}
                        {changePositive ? '+' : ''}{fmt(change)} ({changePositive ? '+' : ''}{fmt(changePct, 2)}%)
                      </span>
                    )}
                    {trendItem && <span className="mb-1"><TrendBadge item={trendItem} size="sm" /></span>}
                  </div>
                )}
              </div>
            </div>

            {/* Portföye Ekle + Alarm (yalnız giriş yapan kullanıcı) */}
            <div className="mt-4">
              <InstrumentActionButtons
                assetType="FX"
                symbol={sym}
                name={FX_META[sym]?.ad ?? sym}
                price={sell}
              />
            </div>

            {/* İstatistik çubuğu (sola alta yaslı) */}
            {currentRate && (
              <div className="mt-5 pt-5 border-t border-[#eef2f8] grid grid-cols-2 sm:grid-cols-3 gap-2 m3-stagger">
                {statCards.map(item => (
                  <div key={item.label} className="bg-[#f6f8fc] rounded-2xl p-3 text-center">
                    <p className="text-xs text-[#5a6472] font-semibold leading-tight">{t(item.label)}</p>
                    <p className="text-[10px] text-[#9aa6b6] mb-1.5">{item.en}</p>
                    <p className="text-sm font-bold text-[#1a1c1e] tabular-nums">{item.value}</p>
                  </div>
                ))}
              </div>
            )}
          </div>

          {/* Sağ — döviz çevirici (önceden boş duran alan) */}
          <div className="lg:border-l lg:border-[#eef2f8] lg:pl-6 flex flex-col justify-center">
            <FxConverter symbol={sym} allRates={allRates} embedded />
          </div>
        </div>
      </div>

      {/* Grafik (sol) + yan panel (sağ) — kolonlar üstten hizalı (grafik kartı esnemesin) */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 lg:items-start">
        <div className="lg:col-span-2 bg-white rounded-3xl border border-[#e2e8f0] shadow-[0_1px_3px_rgba(15,23,42,0.06)] p-5">
          {/* Grafik başlığı + karşılaştır butonları (grafiğin içine alındı) */}
          <div className="flex items-center justify-between gap-2 mb-3 flex-wrap">
            <span className="font-bold text-[#1a1c1e] text-sm flex items-center gap-2">
              <span className="rounded-full ring-1 ring-[#e2e8f0] overflow-hidden inline-flex"><FlagImg cc={FX_META[sym]?.cc} size={18} /></span>
              {sym}/TRY
            </span>
            <div className="flex items-center gap-2 flex-wrap">
              <button onClick={loadHistory} className="m3-state p-2 rounded-full bg-[#eef2f8] hover:bg-[#e3eaf6] transition-all text-[#5a6472]" title={t('Yenile')}>
                <RefreshCw className={`w-4 h-4 ${loadingChart ? 'animate-spin' : ''}`} />
              </button>
              <Link to={`/market/compare?symbols=${sym}`} title={t('Dövizleri kendi karşılaştırma sayfasında kıyasla')}
                className="m3-state inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-bold border border-[#093eaa]/25 text-[#093eaa] bg-[#093eaa]/5 hover:bg-[#093eaa]/10 transition-all">
                <BarChart2 className="w-3.5 h-3.5" /> {t('Karşılaştır')}
              </Link>
              <UniversalCompareButton assetType="FX" symbol={sym} name={`${sym}/TRY`} />
            </div>
          </div>

          <FxChart
            chartPoints={chartPoints}
            lineColor={strokeColor}
            mainLabel={`${sym}/TRY`}
            range={range}
            onRangeChange={setRange}
            loadingChart={loadingChart}
          />
        </div>

        <div className="space-y-6">
          <OpenRateCard sym={sym} tryPerUnit={openTryPerUnit} />
          <BankRatesCard rates={bankRows} loading={loadingBanks} />
        </div>
      </div>
    </div>
  );
}
