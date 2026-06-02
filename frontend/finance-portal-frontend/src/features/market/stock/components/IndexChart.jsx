import { useEffect, useState, useRef } from 'react';
import { getStockChart, getIndex } from '../../../../api/marketApi';
import { STOCK_CHART_RANGES, formatStockChartTimeLabel } from '../utils/stockChartRanges';
import { useTranslation } from '../../../../context/LanguageContext';

/**
 * Tek tip BIST endeks grafiği — hem Hisse sayfasındaki BIST 30/50/100 hem de endeks DETAY
 * sayfasında AYNI bileşen kullanılır (aynı kaynak + aynı çizim). ECharts alan/çizgi grafiği;
 * "Son Değer" ve günlük % Endeksler listesiyle BİREBİR aynı kaynaktan (getIndex → cache snapshot).
 * Crosshair + değer etiketli hover.
 *
 * @param symbol      Yahoo sembolü (XU100.IS, XBANK.IS …)
 * @param label       Görünen ad (BIST 100 …) — showSummary=true iken başlıkta
 * @param showSummary Başlıkta canlı fiyat+% ve altta "Son Değer" göster (liste sayfası için).
 *                    Detay sayfasında false — sayfanın kendi fiyat kartı var.
 * @param height      Grafik yüksekliği (px)
 */
export default function IndexChart({ symbol, label, showSummary = true, height = 260 }) {
  const { t } = useTranslation();
  const [data, setData]         = useState([]);
  const [summary, setSummary]   = useState(null); // { price, changePercent } — Endeksler listesiyle aynı snapshot
  const [loading, setLoading]   = useState(true);
  const [rangeIdx, setRangeIdx] = useState(2); // default 1A

  const chartRef    = useRef(null);
  const instanceRef = useRef(null);

  const activeRange = STOCK_CHART_RANGES[rangeIdx];
  const code = (symbol ?? '').replace(/\.IS$/i, '').toUpperCase();

  // Grafik serisi + endeks özeti (getIndex = liste ile aynı cache snapshot). Son noktayı snapshot
  // fiyatıyla hizala → grafik ucu + "Son Değer" listeyle birebir tutarlı.
  useEffect(() => {
    setLoading(true);
    let alive = true;
    Promise.all([
      getStockChart(symbol, activeRange.range, activeRange.interval).catch(() => null),
      getIndex(code).catch(() => null),
    ])
      .then(([res, idx]) => {
        if (!alive) return;
        const ts     = res?.timestamps  ?? [];
        const prices = res?.closePrices ?? [];
        const points = ts.map((tt, i) => ({
          label: formatStockChartTimeLabel(tt, activeRange.range, activeRange.interval),
          price: prices[i] != null ? parseFloat(prices[i]) : null,
        })).filter(d => d.price != null);
        const listPrice = idx?.price != null ? parseFloat(idx.price) : null;
        if (listPrice != null && points.length > 0) {
          points[points.length - 1] = { ...points[points.length - 1], price: listPrice };
        }
        setData(points);
        setSummary(listPrice != null
          ? { price: listPrice, changePercent: idx?.changePercent != null ? parseFloat(idx.changePercent) : null }
          : null);
      })
      .finally(() => { if (alive) setLoading(false); });
    return () => { alive = false; };
  }, [symbol, code, activeRange.range, activeRange.interval]);

  // ECharts render
  useEffect(() => {
    if (!chartRef.current || data.length < 2) return;

    import('echarts').then(echarts => {
      if (instanceRef.current) instanceRef.current.dispose();

      const chart = echarts.init(chartRef.current, null, { renderer: 'canvas' });
      instanceRef.current = chart;

      const isUp  = data[data.length - 1].price >= data[0].price;
      const color = isUp ? '#10b981' : '#ef4444';

      chart.setOption({
        backgroundColor: 'transparent',
        animation: false,
        grid: { top: 16, right: 16, bottom: 80, left: 72 },
        xAxis: {
          type: 'category',
          data: data.map(d => d.label),
          boundaryGap: false,
          axisLine: { lineStyle: { color: '#e5e7eb' } },
          axisTick: { show: false },
          axisLabel: { color: '#9ca3af', fontSize: 10, interval: 'auto' },
          splitLine: { show: false },
        },
        yAxis: {
          type: 'value',
          min: 'dataMin',
          max: 'dataMax',
          axisLine: { show: false },
          axisTick: { show: false },
          axisLabel: {
            color: '#9ca3af',
            fontSize: 10,
            formatter: v => v.toLocaleString('tr-TR', { maximumFractionDigits: 0 }),
          },
          splitLine: { lineStyle: { color: '#f0f0f0', type: 'dashed' } },
        },
        tooltip: {
          trigger: 'axis',
          // Hover: çapraz crosshair + eksen üzerinde değer etiketleri
          axisPointer: {
            type: 'cross',
            lineStyle: { color: '#093eaa', type: 'dashed', width: 1 },
            label: {
              backgroundColor: '#093eaa',
              color: '#ffffff',
              fontSize: 10,
              formatter: p => (p.axisDimension === 'y'
                ? Number(p.value).toLocaleString('tr-TR', { maximumFractionDigits: 2 })
                : p.value),
            },
          },
          backgroundColor: '#ffffff',
          borderColor: '#e5e7eb',
          borderRadius: 12,
          padding: [10, 14],
          formatter: params => {
            if (!params?.length) return '';
            const p = params[0];
            return `<div style="font-size:11px;color:#6b7280;font-weight:600;margin-bottom:6px;padding-bottom:6px;border-bottom:1px solid #f0f0f0">${p.axisValue}</div>
              <div style="display:flex;justify-content:space-between;gap:16px;align-items:center">
                <span style="font-size:11px;color:#6b7280">${t('Değer:')}</span>
                <span style="font-weight:700;font-size:11px;color:#111827">${p.value != null ? parseFloat(p.value).toLocaleString('tr-TR', { maximumFractionDigits: 2 }) : '-'}</span>
              </div>`;
          },
        },
        dataZoom: [
          { type: 'inside', xAxisIndex: 0, start: 0, end: 100, zoomOnMouseWheel: true },
          {
            type: 'slider',
            xAxisIndex: 0,
            start: 0,
            end: 100,
            height: 18,
            bottom: 8,
            borderColor: '#e5e7eb',
            fillerColor: 'rgba(9,62,170,0.08)',
            handleStyle: { color: '#093eaa' },
            showDetail: false,
          },
        ],
        series: [{
          type: 'line',
          data: data.map(d => d.price),
          smooth: false,
          // Normalde nokta gizli; hover'da o noktada işaret belirir
          symbol: 'circle',
          symbolSize: 7,
          showSymbol: false,
          lineStyle: { width: 2, color },
          itemStyle: { color },
          emphasis: { focus: 'series', itemStyle: { borderColor: '#fff', borderWidth: 2 } },
          areaStyle: {
            color: {
              type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
              colorStops: [
                { offset: 0, color: color + '33' },
                { offset: 1, color: color + '00' },
              ],
            },
          },
        }],
      });

      const ro = new ResizeObserver(() => chart.resize());
      ro.observe(chartRef.current);
      return () => ro.disconnect();
    });

    return () => {
      if (instanceRef.current) {
        instanceRef.current.dispose();
        instanceRef.current = null;
      }
    };
  }, [data, loading]);

  const isUp      = data.length > 1 && data[data.length - 1].price >= data[0].price;
  const change    = data.length > 1
    ? (((data[data.length - 1].price - data[0].price) / data[0].price) * 100).toFixed(2)
    : null;
  const absChange = data.length > 1
    ? (data[data.length - 1].price - data[0].price).toLocaleString('tr-TR', { maximumFractionDigits: 2 })
    : null;

  return (
    <div className={showSummary ? 'bg-white rounded-2xl border border-gray-200 shadow-sm p-3 sm:p-5 mb-6' : ''}>
      <div className="flex items-center justify-between mb-4 flex-wrap gap-2">
        <div className="flex items-baseline gap-3 flex-wrap">
          {showSummary && <h2 className="text-base font-bold text-gray-900">{label} {t('Endeksi')}</h2>}
          {showSummary && summary?.price != null && (
            <span className="flex items-baseline gap-1.5">
              <span className="text-base font-bold text-gray-900">
                {summary.price.toLocaleString('tr-TR', { maximumFractionDigits: 2 })}
              </span>
              {summary.changePercent != null && (
                <span className={`text-xs font-bold ${summary.changePercent >= 0 ? 'text-emerald-600' : 'text-rose-600'}`}>
                  {summary.changePercent >= 0 ? '+' : ''}{summary.changePercent.toFixed(2)}%
                  <span className="text-gray-400 font-normal ml-1">{t('bugün')}</span>
                </span>
              )}
            </span>
          )}
        </div>
        <div className="flex gap-1">
          {STOCK_CHART_RANGES.map((r, i) => (
            <button key={r.label} onClick={() => setRangeIdx(i)}
              className={`px-2.5 py-1 rounded-lg text-xs font-bold transition-all ${
                i === rangeIdx ? 'bg-[#093eaa] text-white' : 'bg-gray-100 text-gray-500 hover:bg-gray-200'
              }`}>
              {t(r.label)}
            </button>
          ))}
        </div>
      </div>

      {/* Loading overlay — chart div her zaman DOM'da kalır */}
      <div style={{ position: 'relative', height }}>
        {loading && (
          <div style={{ position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', background: 'var(--card, #fff)', zIndex: 10 }}>
            <div className="flex gap-1.5">
              <div className="w-2 h-2 bg-[#093eaa] rounded-full animate-bounce" />
              <div className="w-2 h-2 bg-[#093eaa]/60 rounded-full animate-bounce [animation-delay:100ms]" />
              <div className="w-2 h-2 bg-[#093eaa]/30 rounded-full animate-bounce [animation-delay:200ms]" />
            </div>
          </div>
        )}
        {!loading && data.length < 2 && (
          <div style={{ position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <span className="text-gray-400 text-sm">{t('Grafik verisi yüklenemedi.')}</span>
          </div>
        )}
        <div ref={chartRef} style={{ width: '100%', height: '100%', visibility: loading || data.length < 2 ? 'hidden' : 'visible' }} />
      </div>

      {change != null && (
        <div className="flex items-center justify-between mt-3 pt-3 border-t border-gray-100">
          <div>
            <p className="text-xs text-gray-400">{t(activeRange.label)} {t('Değişim')}</p>
            <p className={`text-sm font-bold ${isUp ? 'text-emerald-600' : 'text-rose-600'}`}>
              {isUp ? '+' : ''}₺{absChange} ({isUp ? '+' : ''}{change}%)
            </p>
          </div>
          {showSummary && (
            <div className="text-right">
              <p className="text-xs text-gray-400">{t('Son Değer')}</p>
              <p className="text-sm font-bold text-gray-900">
                {data[data.length - 1]?.price.toLocaleString('tr-TR', { maximumFractionDigits: 2 })}
              </p>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
