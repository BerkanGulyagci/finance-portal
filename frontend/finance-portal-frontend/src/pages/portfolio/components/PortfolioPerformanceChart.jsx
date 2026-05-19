import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { init as klineInit, dispose as klineDispose } from 'klinecharts';
import { getPortfolioPerformance } from '../../../api/portfolioApi';
import { formatChartValue, MASK_MONEY } from '../utils/portfolioFormatUtils';
import { computeKlinePricePrecision } from '../../../utils/numberFormat';

const RANGES = [
  { key: '5D', label: '5D' },
  { key: '1M', label: '1M' },
  { key: '6M', label: '6M' },
  { key: 'YTD', label: 'YTD' },
  { key: '1Y', label: '1Y' },
];

const METRICS = [
  { key: 'VALUE', label: 'Portföy Değeri' },
  { key: 'GROWTH', label: 'Dönem Getirisi' },
];

const LINE_COLOR = '#093eaa';
const LINE_COLOR_POS = '#059669';
const LINE_COLOR_NEG = '#e11d48';

function formatExcludedLabel(symbol) {
  if (!symbol) return '';
  if (symbol.startsWith('SILVER')) return 'Gümüş';
  if (/VADELI$/i.test(symbol)) {
    return symbol.replace(/VADELI$/i, 'Vadeli');
  }
  if (symbol.length > 32) return `${symbol.slice(0, 30)}…`;
  return symbol;
}

/** Büyük portföy tutarlarında Y ekseni adımlarını seyreltir (130K, 132K …). */
function portfolioPricePrecision(values, metric) {
  if (metric === 'GROWTH') return 2;
  const nums = values.filter(v => Number.isFinite(v) && v > 0);
  if (!nums.length) return 0;
  const max = Math.max(...nums);
  const min = Math.min(...nums);
  const span = max - min;
  if (max >= 80_000 || span >= 15_000) return 0;
  if (max >= 5_000 || span >= 800) return 1;
  return computeKlinePricePrecision(nums);
}

function formatYAxisTick(value, metric, valuesHidden) {
  if (valuesHidden) return MASK_MONEY;
  if (value == null || !Number.isFinite(value)) return '';
  return formatChartValue(value, false, metric);
}

function formatTooltipDate(iso) {
  if (!iso) return '';
  try {
    const d = new Date(iso);
    return d.toLocaleDateString('tr-TR', {
      weekday: 'short',
      day: 'numeric',
      month: 'long',
      year: 'numeric',
    });
  } catch {
    return iso;
  }
}

function toKlineBars(chartData) {
  return chartData
    .map(p => {
      const v = Number(p.chartValue);
      if (!Number.isFinite(v)) return null;
      const ts = new Date(p.date).getTime();
      if (Number.isNaN(ts)) return null;
      return {
        timestamp: ts,
        open: v,
        high: v,
        low: v,
        close: v,
        volume: 0,
        turnover: 0,
      };
    })
    .filter(Boolean)
    .sort((a, b) => a.timestamp - b.timestamp);
}

/** Ani sıçrama varsa grafiği ilk anlamlı güne kaydır. */
function focusTimestampAfterJump(chartData, metric) {
  if (metric !== 'VALUE' || chartData.length < 3) return null;
  const values = chartData.map(d => Number(d.chartValue));
  const last = values[values.length - 1];
  const prev = values[values.length - 2];
  if (!(last > 0 && prev > 0 && last / prev >= 5)) return null;

  const threshold = last * 0.15;
  for (let i = 0; i < values.length; i++) {
    if (values[i] >= threshold) {
      return chartData[i].date;
    }
  }
  return chartData[chartData.length - 1]?.date ?? null;
}

function PortfolioPerformanceKlineChart({
  chartData,
  metric,
  lineColor,
  valuesHidden,
}) {
  const chartId = useRef(`kline_portfolio_perf_${Date.now()}`);
  const chartRef = useRef(null);
  const containerRef = useRef(null);
  const [hoverInfo, setHoverInfo] = useState(null);

  useEffect(() => {
    if (!chartData?.length) return;

    const id = chartId.current;
    try {
      klineDispose(id);
    } catch {
      /* ignore */
    }

    const chart = klineInit(id);
    chartRef.current = chart;

    const klineData = toKlineBars(chartData);
    if (!klineData.length) {
      klineDispose(id);
      return;
    }

    const closes = klineData.map(d => d.close);
    const prec = portfolioPricePrecision(closes, metric);

    try {
      chart.setPriceVolumePrecision(prec, 0);
    } catch {
      /* sürüm */
    }

    chart.setStyles({
      candle: {
        type: 'area',
        area: {
          lineColor,
          lineSize: 2,
          value: 'close',
          smooth: true,
          backgroundColor: [
            { offset: 0, color: `${lineColor}30` },
            { offset: 1, color: `${lineColor}00` },
          ],
        },
        tooltip: { showRule: 'none' },
        priceMark: {
          last: {
            show: true,
            upColor: lineColor,
            downColor: lineColor,
            noChangeColor: lineColor,
          },
          high: { show: false },
          low: { show: false },
        },
      },
      crosshair: {
        show: true,
        horizontal: {
          show: true,
          line: {
            show: true,
            style: 'dashed',
            dashedValue: [4, 2],
            size: 1,
            color: '#9ca3af',
          },
          text: {
            show: true,
            size: 11,
            color: '#fff',
            paddingLeft: 6,
            paddingRight: 6,
            paddingTop: 3,
            paddingBottom: 3,
            borderRadius: 4,
            backgroundColor: '#374151',
          },
        },
        vertical: {
          show: true,
          line: {
            show: true,
            style: 'dashed',
            dashedValue: [4, 2],
            size: 1,
            color: '#9ca3af',
          },
          text: {
            show: true,
            size: 11,
            color: '#fff',
            paddingLeft: 6,
            paddingRight: 6,
            paddingTop: 3,
            paddingBottom: 3,
            borderRadius: 4,
            backgroundColor: '#374151',
          },
        },
      },
      xAxis: { show: true, tickText: { color: '#94a3b8', size: 10 } },
      yAxis: {
        type: 'normal',
        show: true,
        tickText: {
          color: '#94a3b8',
          size: 10,
          formatter: v => formatYAxisTick(v, metric, valuesHidden),
        },
      },
      grid: {
        horizontal: { show: true, color: '#f1f5f9', size: 1 },
        vertical: { show: false },
      },
    });

    chart.applyNewData(klineData);

    const rowByTimestamp = new Map(
      chartData.map(row => [new Date(row.date).getTime(), row]),
    );

    try {
      chart.subscribeAction('onCrosshairChange', data => {
        if (!data?.kLineData) {
          setHoverInfo(null);
          return;
        }
        const kd = data.kLineData;
        const row = rowByTimestamp.get(kd.timestamp);
        const value = kd.close ?? kd.open;
        if (row == null || value == null || !Number.isFinite(value)) {
          setHoverInfo(null);
          return;
        }

        const container = containerRef.current;
        const w = container?.clientWidth ?? 400;
        const h = container?.clientHeight ?? 300;
        const rawX = data.x ?? 0;
        const rawY = data.y ?? 0;
        const tooltipW = 200;
        const tooltipH = metric === 'GROWTH' && !valuesHidden && row.marketValue != null ? 72 : 56;
        const left = Math.min(Math.max(rawX + 14, 8), w - tooltipW - 8);
        const top = Math.min(Math.max(rawY - tooltipH - 8, 8), h - tooltipH - 8);

        const valueLabel = metric === 'GROWTH' ? 'Dönem getirisi' : 'Portföy değeri';
        setHoverInfo({
          date: formatTooltipDate(row.date),
          valueLabel,
          valueText: formatChartValue(value, valuesHidden, metric),
          marketValueText:
            metric === 'GROWTH' && !valuesHidden && row.marketValue != null
              ? formatChartValue(row.marketValue, false, 'VALUE')
              : null,
          left,
          top,
        });
      });
    } catch {
      /* ignore */
    }

    const focusDate = focusTimestampAfterJump(chartData, metric);
    if (focusDate) {
      const ts = new Date(focusDate).getTime();
      try {
        if (typeof chart.scrollToTimestamp === 'function') {
          chart.scrollToTimestamp(ts, 200);
        }
      } catch {
        /* ignore */
      }
    }

    return () => {
      setHoverInfo(null);
      try {
        klineDispose(id);
      } catch {
        /* ignore */
      }
      chartRef.current = null;
    };
  }, [chartData, metric, lineColor, valuesHidden]);

  if (!chartData?.length) {
    return (
      <div className="flex items-center justify-center h-[300px] text-sm text-gray-400">
        Grafik verisi yok.
      </div>
    );
  }

  return (
    <div ref={containerRef} className="relative w-full" style={{ height: 300 }}>
      {hoverInfo && (
        <div
          className="absolute z-30 pointer-events-none rounded-xl border border-gray-200 bg-white px-3 py-2 shadow-lg"
          style={{ left: hoverInfo.left, top: hoverInfo.top, maxWidth: 220 }}
        >
          <p className="text-[11px] text-gray-500 leading-snug">{hoverInfo.date}</p>
          <p className="mt-1 text-xs text-gray-600">{hoverInfo.valueLabel}</p>
          <p className="text-sm font-semibold text-gray-900">{hoverInfo.valueText}</p>
          {hoverInfo.marketValueText && (
            <p className="mt-1 text-[11px] text-gray-500">
              Portföy:{' '}
              <span className="font-medium text-gray-700">{hoverInfo.marketValueText}</span>
            </p>
          )}
        </div>
      )}
      <div id={chartId.current} className="w-full h-full" />
    </div>
  );
}

/**
 * Portföy performans grafiği (üst özet kartı sağ kolon) — KLineCharts.
 */
export default function PortfolioPerformanceChart({
  portfolioId,
  valuesHidden = false,
}) {
  const [range, setRange] = useState('1M');
  const [metric, setMetric] = useState('VALUE');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [perf, setPerf] = useState(null);

  const load = useCallback(async () => {
    if (!portfolioId) return;
    setLoading(true);
    setError('');
    try {
      const data = await getPortfolioPerformance(portfolioId, range, metric);
      setPerf(data);
    } catch (err) {
      const msg =
        err.response?.data?.message ||
        (err.response?.status === 400 ? 'Geçersiz istek' : 'Performans verisi yüklenemedi');
      setError(typeof msg === 'string' ? msg : 'Performans verisi yüklenemedi');
      setPerf(null);
    } finally {
      setLoading(false);
    }
  }, [portfolioId, range, metric]);

  useEffect(() => {
    load();
  }, [load]);

  const chartData = useMemo(() => {
    const points = perf?.points ?? [];
    return points
      .map(p => {
        const raw = metric === 'GROWTH' ? p.periodGrowthPercent : p.marketValue;
        const n = raw != null ? Number(raw) : null;
        return {
          date: p.date,
          chartValue: n != null && Number.isFinite(n) ? n : null,
          marketValue: p.marketValue,
        };
      })
      .filter(d => d.chartValue != null);
  }, [perf, metric]);

  const lineColor = useMemo(() => {
    if (metric === 'GROWTH' && chartData.length >= 2) {
      const first = chartData[0]?.chartValue ?? 0;
      const last = chartData[chartData.length - 1]?.chartValue ?? 0;
      return last >= first ? LINE_COLOR_POS : LINE_COLOR_NEG;
    }
    return LINE_COLOR;
  }, [chartData, metric]);

  const excludedAssets = perf?.excludedAssets ?? [];
  const excludedCount = excludedAssets.length;
  const excludedName =
    excludedCount === 1
      ? formatExcludedLabel(excludedAssets[0]?.symbol)
      : excludedAssets.map(a => formatExcludedLabel(a?.symbol)).filter(Boolean).join(', ');

  const hasJump =
    metric === 'VALUE' &&
    chartData.length >= 2 &&
    (() => {
      const a = chartData[chartData.length - 2]?.chartValue;
      const b = chartData[chartData.length - 1]?.chartValue;
      return a > 0 && b / a >= 5;
    })();

  return (
    <div className="flex flex-col h-full min-h-[260px]">
      <div className="flex flex-wrap items-center justify-between gap-2 mb-3">
        <div className="flex flex-wrap gap-1">
          {RANGES.map(r => (
            <button
              key={r.key}
              type="button"
              onClick={() => setRange(r.key)}
              className={`px-2.5 py-1 rounded-lg text-xs font-semibold transition-colors ${
                range === r.key
                  ? 'bg-[#093eaa] text-white'
                  : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
              }`}
            >
              {r.label}
            </button>
          ))}
        </div>
        <select
          value={metric}
          onChange={e => setMetric(e.target.value)}
          className="text-xs font-semibold border border-gray-200 rounded-lg px-2 py-1.5 bg-white text-gray-700 focus:outline-none focus:ring-2 focus:ring-[#093eaa]/30"
          aria-label="Grafik metriği"
        >
          {METRICS.map(m => (
            <option key={m.key} value={m.key}>
              {m.label}
            </option>
          ))}
        </select>
      </div>

      {excludedCount > 0 && (
        <div className="text-xs text-amber-800 bg-amber-50 border border-amber-100 rounded-lg px-2 py-1.5 mb-2 leading-snug">
          <p>{excludedCount} varlık grafik hesaplamasına dahil edilemedi.</p>
          {excludedName && (
            <p className="mt-1 text-amber-700/90 truncate" title={excludedName}>
              {excludedCount === 1 ? 'Dahil edilmeyen: ' : 'Dahil edilmeyenler: '}
              {excludedName}
            </p>
          )}
          <p className="mt-0.5 text-amber-600/85">
            Özet kart tüm pozisyonları gösterir. Geçmiş günler yalnızca tarihsel fiyatı bulunan
            varlıklarla hesaplanır; bugünün noktası özet ile aynı canlı değeri yansıtır.
          </p>
        </div>
      )}

      <p className="text-[10px] text-gray-400 mb-1">
        Tekerlek ile yakınlaştırın, sürükleyerek kaydırın.
        {hasJump ? ' Büyük sıçrama varsa grafik son anlamlı güne odaklanır.' : ''}
      </p>

      <div className="relative flex-1 min-h-[300px]">
        {loading && (
          <div className="absolute inset-0 flex items-center justify-center text-sm text-gray-400 z-10 bg-white/80">
            Yükleniyor…
          </div>
        )}
        {!loading && error && (
          <div className="absolute inset-0 flex items-center justify-center text-sm text-rose-600 px-4 text-center z-10">
            {error}
          </div>
        )}
        {!loading && !error && chartData.length > 0 && (
          <PortfolioPerformanceKlineChart
            key={`${range}-${metric}-${chartData.length}-${chartData[chartData.length - 1]?.chartValue}`}
            chartData={chartData}
            metric={metric}
            lineColor={lineColor}
            valuesHidden={valuesHidden}
          />
        )}
        {!loading && !error && chartData.length === 0 && (
          <div className="absolute inset-0 flex items-center justify-center text-sm text-gray-400">
            Bu aralık için grafik verisi yok.
          </div>
        )}
      </div>
    </div>
  );
}
