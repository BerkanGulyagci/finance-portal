import { useEffect, useState, useCallback, useRef } from 'react';
import { SlidersHorizontal, ChevronDown } from 'lucide-react';
import { init as klineInit, dispose as klineDispose } from 'klinecharts';
import DrawingToolbar from './DrawingToolbar';
import { getStockChart } from '../../../../api/marketApi';
import {
  DRAWING_TOOLS, MA_PERIODS, SUB_INDICATORS,
  maLineStyles, STOCK_WARMUP_RANGE, RANGE_WINDOW_MS, fitVisibleToWindow,
} from '../utils/stockChartConfig';
import { STOCK_CHART_RANGES } from '../utils/stockChartRanges';
import { useTranslation } from '../../../../context/LanguageContext';

const RANGES = STOCK_CHART_RANGES;

/* ─── KlineCharts Çizgi Grafiği ─── */
export default function LineChart({ symbol }) {
  const { t } = useTranslation();
  const chartId = useRef(`kline_line_${Date.now()}`);
  const chartRef = useRef(null);
  const indicatorPaneIds = useRef({});
  const [rangeIdx, setRangeIdx] = useState(2);
  const [loading, setLoading] = useState(true);
  const [error, setError]     = useState(false);
  const [activeTool, setActiveTool] = useState(null);
  const [activeMAs, setActiveMAs] = useState([]);
  const [activeSubInds, setActiveSubInds] = useState([]);
  const [indMenuOpen, setIndMenuOpen] = useState(false);

  const rangeConfig = RANGES[rangeIdx];
  const { range, interval } = rangeConfig;

  // Çizgi grafikte de indikatör/MA (kapanış serisi üzerinden)
  const applyMA = useCallback((periods) => {
    const chart = chartRef.current;
    if (!chart) return;
    chart.removeIndicator('candle_pane', 'MA');
    if (periods.length > 0) {
      chart.createIndicator({ name: 'MA', calcParams: periods, styles: maLineStyles(periods) }, false, { id: 'candle_pane' });
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
        if (paneId) { try { chart.removeIndicator(paneId, indName); delete indicatorPaneIds.current[indName]; } catch { /* yoksay */ } }
        return prev.filter(n => n !== indName);
      }
      try {
        const paneId = chart.createIndicator({ name: indName }, true, { height: 80 });
        if (paneId) indicatorPaneIds.current[indName] = paneId;
      } catch { /* yoksay */ }
      return [...prev, indName];
    });
  }, []);

  const handleSelectTool = useCallback((toolId) => {
    setActiveTool(toolId);
    if (!chartRef.current) return;
    if (toolId) {
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

  useEffect(() => {
    const id = chartId.current;
    const chart = klineInit(id);
    chart.setStyles({ candle: { type: 'area' } });
    chartRef.current = chart;
    indicatorPaneIds.current = {};

    setLoading(true);
    setError(false);

    const fetchRange = STOCK_WARMUP_RANGE[range] ?? range;
    getStockChart(symbol, fetchRange, interval)
      .then(res => {
        const ts     = res?.timestamps  ?? [];
        const prices = res?.closePrices ?? [];
        if (!ts.length || !prices.length) { setError(true); setLoading(false); return; }

        const klineData = ts
          .map((t, i) => {
            const p = parseFloat(prices[i]);
            if (!Number.isFinite(p) || p <= 0) return null;
            return {
              timestamp: Number(t) * 1000,
              open: p,
              high: p,
              low: p,
              close: p,
              volume: 0,
              turnover: 0,
            };
          })
          .filter(Boolean)
          .sort((a, b) => a.timestamp - b.timestamp);

        if (!klineData.length) {
          setError(true);
          setLoading(false);
          return;
        }

        const isUp = klineData[klineData.length - 1].close >= klineData[0].close;
        const color = isUp ? '#10b981' : '#ef4444';
        chartRef.current?.setStyles({
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

        chartRef.current?.applyNewData(klineData);
        // Görünür alanı yalnız seçili pencereye sığdır (ısınma verisi sola kayar, MA dolu çizilir)
        const winMsL = RANGE_WINDOW_MS[range];
        fitVisibleToWindow(chartRef.current, id, klineData, (winMsL && winMsL !== Infinity) ? Date.now() - winMsL : 0);
        // Veri yüklendikten sonra aktif indikatörleri yeniden ekle
        if (chartRef.current) {
          if (activeMAs.length > 0) {
            chartRef.current.createIndicator({ name: 'MA', calcParams: activeMAs, styles: maLineStyles(activeMAs) }, false, { id: 'candle_pane' });
          }
          activeSubInds.forEach(indName => {
            try {
              const paneId = chartRef.current.createIndicator({ name: indName }, true, { height: 80 });
              if (paneId) indicatorPaneIds.current[indName] = paneId;
            } catch { /* yoksay */ }
          });
        }
        setLoading(false);
      })
      .catch(() => { setError(true); setLoading(false); });

    return () => { klineDispose(id); indicatorPaneIds.current = {}; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [symbol, range, interval]);

  // İndikatör menüsü (çizgi modu — kapanış serisi; VOL hariç, hacim yok)
  const lineIndicatorDropdown = (
    <div className="relative">
      <button
        onClick={() => setIndMenuOpen(v => !v)}
        className={`inline-flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-xs font-semibold border transition-all ${
          indMenuOpen || (activeMAs.length + activeSubInds.length) > 0
            ? 'bg-[#093eaa] text-white border-[#093eaa]'
            : 'bg-white text-gray-600 border-gray-200 hover:border-[#093eaa] hover:text-[#093eaa]'
        }`}
      >
        <SlidersHorizontal className="w-3.5 h-3.5" />
        {t('İndikatör')}
        {(activeMAs.length + activeSubInds.length) > 0 && (
          <span className="inline-flex items-center justify-center min-w-[16px] h-4 px-1 rounded-full bg-[#093eaa] text-white text-[10px] font-bold">
            {activeMAs.length + activeSubInds.length}
          </span>
        )}
        <ChevronDown className={`w-3 h-3 transition-transform ${indMenuOpen ? 'rotate-180' : ''}`} />
      </button>
      {indMenuOpen && (
        <>
          <div className="fixed inset-0 z-40" onClick={() => setIndMenuOpen(false)} />
          <div className="absolute left-0 top-full mt-1 z-50 bg-white border border-gray-200 rounded-xl shadow-lg p-3 w-64">
            <p className="text-[10px] font-bold text-gray-400 uppercase tracking-wider mb-2">{t('Hareketli Ortalama')}</p>
            <div className="flex flex-wrap gap-1.5 mb-3">
              {MA_PERIODS.map(({ period, color, label }) => {
                const active = activeMAs.includes(period);
                return (
                  <button key={period} onClick={() => toggleMA(period)}
                    className={`px-2.5 py-1 rounded-md text-xs font-bold transition-all border ${active ? 'text-white border-transparent' : 'bg-gray-50 text-gray-500 border-gray-200 hover:bg-gray-100'}`}
                    style={active ? { backgroundColor: color, borderColor: color } : {}}>
                    {label}
                  </button>
                );
              })}
            </div>
            <p className="text-[10px] font-bold text-gray-400 uppercase tracking-wider mb-2">{t('Osilatör / İndikatör')}</p>
            <div className="flex flex-wrap gap-1.5">
              {SUB_INDICATORS.filter(s => s.name !== 'VOL').map(({ name, label, color }) => {
                const active = activeSubInds.includes(name);
                return (
                  <button key={name} onClick={() => toggleSubIndicator(name)}
                    className={`px-2.5 py-1 rounded-md text-xs font-semibold transition-all border ${active ? 'text-white border-transparent' : 'bg-gray-50 text-gray-500 border-gray-200 hover:bg-gray-100'}`}
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
      {/* ── Zaman aralığı — segmented, ortalı ── */}
      <div className="flex justify-center mb-3">
        <div className="inline-flex items-center gap-0.5 rounded-lg border border-gray-200 bg-gray-50 p-0.5 overflow-x-auto max-w-full [&::-webkit-scrollbar]:hidden">
          {RANGES.map((r, i) => (
            <button key={r.label} onClick={() => setRangeIdx(i)}
              className={`px-3 py-1 rounded-md text-xs font-semibold whitespace-nowrap transition-all ${
                i === rangeIdx ? 'bg-white text-[#093eaa] shadow-sm' : 'text-gray-500 hover:text-gray-800'
              }`}>
              {r.label}
            </button>
          ))}
        </div>
      </div>

      {/* Drawing Toolbar (İndikatör menüsü dahil) */}
      <DrawingToolbar
        activeTool={activeTool}
        onSelectTool={handleSelectTool}
        onDeleteSelected={handleDeleteSelected}
        onClearAll={handleClearAll}
        indicatorSlot={lineIndicatorDropdown}
      />

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
        {error && !loading && (
          <div className="absolute inset-0 flex items-center justify-center text-gray-400 text-sm">
            {t('Grafik verisi yüklenemedi.')}
          </div>
        )}
        <div id={chartId.current} style={{ width: '100%', height: '380px' }} />
      </div>
    </div>
  );
}
