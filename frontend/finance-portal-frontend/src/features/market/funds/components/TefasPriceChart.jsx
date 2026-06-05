import { useState, useEffect, useRef, useCallback, useMemo } from 'react';
import { ChevronDown } from 'lucide-react';
import { init as klineInit, dispose as klineDispose } from 'klinecharts';
import { useChartDrawings } from '../../../../hooks/useChartDrawings';
import { getFundPriceHistory } from '../../../../api/marketApi';
import { FUND_CHART_RANGES, buildFundChartSeries } from '../utils/fundChartSeries';
import { buildTrendItem } from '../../../../utils/trendUtils';
import TrendBadge from '../../../../components/common/TrendBadge';
import { useTranslation } from '../../../../context/LanguageContext';
import { useTheme } from '../../../../context/ThemeContext';
import IndicatorMenu from '../../../../components/common/IndicatorMenu';
import { useYAxisWheelZoom } from '../../../../hooks/useYAxisWheelZoom';

function toFloat(v) {
  if (v == null) return null;
  const n = parseFloat(v);
  return isNaN(n) ? null : n;
}

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

function FundDrawingToolbar({ activeTool, onSelectTool, onDeleteSelected, onClearAll, indicatorSlot }) {
  const { t } = useTranslation();
  const [openGroup, setOpenGroup] = useState(null);
  return (
    <div className="flex flex-wrap items-center gap-1.5 p-2 bg-gray-50 border border-gray-200 rounded-xl mb-2">
      {FUND_DRAWING_TOOLS.map(({ group, tools }) => (
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
      {indicatorSlot && (
        <>
          <div className="w-px h-6 bg-gray-200 mx-1" />
          {indicatorSlot}
        </>
      )}
      <div className="w-px h-6 bg-gray-200 mx-1" />
      <button onClick={onDeleteSelected} title={t('Seçili çizimi sil')}
        className="p-1.5 rounded-lg text-gray-500 hover:bg-red-50 hover:text-red-500 transition-all border border-gray-200">
        <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
        </svg>
      </button>
      <button onClick={onClearAll} title={t('Tüm çizimleri temizle')}
        className="p-1.5 rounded-lg text-gray-500 hover:bg-red-50 hover:text-red-500 transition-all border border-gray-200">
        <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
        </svg>
      </button>
      {activeTool && (
        <span className="ml-auto text-xs text-[#093eaa] font-medium bg-blue-50 px-2 py-1 rounded-lg">
          {(() => { const lbl = FUND_DRAWING_TOOLS.flatMap(g => g.tools).find(it => it.id === activeTool)?.label; return lbl ? t(lbl) : activeTool; })()}
          <button onClick={() => onSelectTool(null)} className="ml-1.5 opacity-60 hover:opacity-100">✕</button>
        </span>
      )}
    </div>
  );
}

const FUND_MA_DEFS = [
  { period: 20,  color: '#f59e0b', label: 'MA20'  },
  { period: 50,  color: '#a855f7', label: 'MA50'  },
  { period: 200, color: '#ef4444', label: 'MA200' },
];

export default function TefasPriceChart({ code, fonTipi, priceHistory, monthlyReturns }) {
  const { t } = useTranslation();
  const { isDark } = useTheme();
  const [range, setRange]           = useState('1Y');
  const [activeMAs, setActiveMAs]   = useState([]);
  const [showTrend, setShowTrend]   = useState(false);
  const chartId        = useRef(`fund_chart_${Math.random().toString(36).slice(2)}`);
  const chartRef       = useRef(null);
  const trendOverlayId = useRef(null);

  // Çizim kalıcılığı — ortak hook (mouseup snapshot). Trend overlay'i ayrı (liveIds'e girmez → kaydedilmez).
  const {
    activeTool, setActiveTool, handleSelectTool, handleDeleteSelected,
    handleClearAll: clearDrawings, restoreOverlays,
  } = useChartDrawings({ chartRef, chartIdRef: chartId, persistKey: code ? `chart-overlays:fund:${code}` : '' });
  const handleClearAll = useCallback(() => { clearDrawings(); trendOverlayId.current = null; }, [clearDrawings]);

  // Fiyat geçmişi: önce TEFAS (5 yıla kadar gerçek günlük seri), boşsa Rasyonet
  // (~1 yıl + 5Y için aylık tahmin) fallback. Künye verisi yine Rasyonet'ten geliyor.
  const [filtered, setFiltered]         = useState([]);
  const [chartLoading, setChartLoading] = useState(true);
  const [chartSource, setChartSource]   = useState('Rasyonet');
  useYAxisWheelZoom(chartRef, !chartLoading);

  useEffect(() => {
    let cancelled = false;

    // 1 yıla kadar (1Y dahil): Rasyonet — hızlı ve güvenilir, ağ isteği yok.
    if (range !== '5Y') {
      const fb = buildFundChartSeries(priceHistory, monthlyReturns, range);
      setFiltered(fb.points);
      setChartSource('Rasyonet');
      setChartLoading(false);
      return () => { cancelled = true; };
    }

    // 5Y: derin geçmiş TEFAS'tan çekilir (yavaş → loading animasyonu); son ~1 yıl Rasyonet ile
    // doldurulur (TEFAS burst-throttle ettiği için son dönemde boşluk kalabilir).
    setChartLoading(true);
    setFiltered([]); // loading animasyonu görünsün
    (async () => {
      let tefas = [];
      if (code) {
        try {
          const resp = await getFundPriceHistory(code, '5Y', fonTipi);
          tefas = (resp?.points ?? [])
            .map(p => ({ date: String(p.date).slice(0, 10), price: toFloat(p.price) }))
            .filter(p => p.date && p.price != null && p.price > 0);
        } catch { tefas = []; }
      }
      const rasyonetDaily = (priceHistory ?? [])
        .map(p => ({ date: String(p.date).slice(0, 10), price: toFloat(p.price) }))
        .filter(p => p.date && p.price != null && p.price > 0);

      const byDate = new Map();
      tefas.forEach(p => byDate.set(p.date, p));
      rasyonetDaily.forEach(p => byDate.set(p.date, p)); // çakışmada güncel Rasyonet kazanır

      const cutoff = new Date();
      cutoff.setHours(0, 0, 0, 0);
      cutoff.setDate(cutoff.getDate() - 1825);
      const points = [...byDate.values()]
        .filter(p => new Date(p.date).getTime() >= cutoff.getTime())
        .sort((a, b) => new Date(a.date) - new Date(b.date));

      if (!cancelled) {
        setFiltered(points);
        setChartSource(tefas.length ? 'TEFAS + Rasyonet' : 'Rasyonet');
        setChartLoading(false);
      }
    })();
    return () => { cancelled = true; };
  }, [code, fonTipi, range, priceHistory, monthlyReturns]);

  // Trend hesabı — filtrelenmiş veri üzerinden
  const trendInfo = useMemo(() => {
    const vals = filtered.map(p => toFloat(p.price)).filter(v => v != null && v > 0 && !isNaN(v));
    if (vals.length < 2) return null;
    const first = vals[0], last = vals[vals.length - 1];
    const pct = ((last - first) / first) * 100;
    if (isNaN(pct)) return null;
    const label = pct > 1 ? t('Yükselen Trend') : pct < -1 ? t('Düşen Trend') : t('Yatay Trend');
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
        const sorted = [...periods].sort((a, b) => a - b);
        const lines = sorted.map(p => ({ color: FUND_MA_DEFS.find(m => m.period === p)?.color ?? '#888', size: 1.5 }));
        chart.createIndicator(
          { name: 'MA', calcParams: sorted, styles: { lines } },
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

    // Kaydedilmiş çizimleri geri yükle (applyNewData'dan sonra; trend overlay'i ayrı oluşturulur).
    restoreOverlays(klineData);

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

    // Aktif MA'ları yeniden uygula (buton rengiyle aynı çizgi)
    if (activeMAs.length > 0) {
      try {
        const sorted = [...activeMAs].sort((a, b) => a - b);
        const lines = sorted.map(p => ({ color: FUND_MA_DEFS.find(m => m.period === p)?.color ?? '#888', size: 1.5 }));
        chart.createIndicator({ name: 'MA', calcParams: sorted, styles: { lines } }, false, { id: 'candle_pane' });
      } catch (_) {}
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

  if (chartLoading && filtered.length === 0) {
    return (
      <div className="flex items-center justify-center h-48">
        <div className="flex gap-1.5">
          <div className="w-2 h-2 bg-[#093eaa] rounded-full animate-bounce" />
          <div className="w-2 h-2 bg-[#093eaa]/60 rounded-full animate-bounce [animation-delay:100ms]" />
          <div className="w-2 h-2 bg-[#093eaa]/30 rounded-full animate-bounce [animation-delay:200ms]" />
        </div>
      </div>
    );
  }
  if (!chartLoading && filtered.length === 0) {
    return <div className="flex items-center justify-center h-48 text-gray-400 text-sm">{t('Fiyat geçmişi bulunamadı.')}</div>;
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
        <div className="flex items-center gap-2 flex-wrap justify-end">
          <span className="text-xs text-gray-400">
            {chartLoading ? `${t('yükleniyor')}…` : `${filtered.length} ${t('veri noktası')} · ${chartSource}`}
          </span>
          <div className="inline-flex items-center gap-0.5 rounded-lg border border-gray-200 bg-gray-50 p-0.5">
            {FUND_CHART_RANGES.map(r => (
              <button key={r.key} onClick={() => setRange(r.key)}
                className={`px-3 py-1 rounded-md text-xs font-semibold whitespace-nowrap transition-all ${
                  range === r.key ? 'bg-white text-[#093eaa] shadow-sm' : 'text-gray-500 hover:text-gray-800'
                }`}>
                {t(r.label)}
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* Trend rozeti */}
      {(() => {
        const ti = buildTrendItem(filtered.map(p => toFloat(p.price)), 'FUND');
        return ti ? <div className="mb-2"><TrendBadge item={ti} size="xs" /></div> : null;
      })()}

      {/* ── Drawing Toolbar (İndikatör dropdown çizim gruplarının yanında — hisse detay tarzı) ── */}
      <FundDrawingToolbar
        activeTool={activeTool}
        onSelectTool={handleSelectTool}
        onDeleteSelected={handleDeleteSelected}
        onClearAll={handleClearAll}
        indicatorSlot={
          <IndicatorMenu
            maDefs={FUND_MA_DEFS}
            activeMAs={activeMAs}
            onToggleMA={toggleMA}
            dataLen={filtered.length}
            extras={[{ key: 'trend', label: 'Trend', color: '#f59e0b', active: showTrend, onToggle: toggleTrend }]}
          />
        }
      />

      {/* ── Chart ── */}
      <div className="relative"
        onMouseLeave={() => setHoverInfo(null)}>
        {/* Custom tooltip */}
        {hoverInfo && (
          <div className={`absolute z-30 pointer-events-none text-xs rounded-lg px-3 py-2 shadow-lg ring-1 backdrop-blur-md whitespace-nowrap ${
            isDark ? 'bg-slate-900/90 text-white ring-white/10' : 'bg-white/95 text-gray-900 ring-black/10'
          }`}
            style={{
              left: Math.min(hoverInfo.x + 12, 999),
              top: Math.max(hoverInfo.y - 40, 4),
              transform: 'translateX(0)',
            }}>
            <div className={`mb-0.5 ${isDark ? 'text-slate-400' : 'text-gray-500'}`}>{hoverInfo.date}</div>
            <div className="font-bold text-sm">{hoverInfo.price}</div>
          </div>
        )}
        <div id={chartId.current} style={{ width: '100%', height: '320px' }} />
      </div>

      {range === '5Y' && chartSource === 'TEFAS + Rasyonet' && (
        <p className="text-xs text-gray-500 mt-2">
          {t('5Y görünüm: derin geçmiş TEFAS, son ~1 yıl Rasyonet günlük fiyatlarıyla tamamlanır.')}
        </p>
      )}
    </div>
  );
}

