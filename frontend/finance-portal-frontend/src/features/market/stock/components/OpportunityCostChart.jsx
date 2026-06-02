import { useEffect, useRef, useMemo } from 'react';
import { useTranslation } from '../../../../context/LanguageContext';

/**
 * Fırsat Maliyeti grafiği — karşılaştırma sayfasının normalize % verisini (chartData) TL değerine çevirir:
 * aynı tutarı (investment) her varlığa başlangıçta koysan zaman içinde ne olurdu. Altında her varlık için
 * nihai değer + EN İYİ seçeneğe göre "fırsat maliyeti" (kaçırdığın fark) özeti. Yalnız seçili VARLIKLAR —
 * portföy dahil edilmez (portföye özel what-if portföyde kalır).
 */
export default function OpportunityCostChart({ chartData, seriesDefs, investment }) {
  const { t } = useTranslation();
  const chartRef = useRef(null);
  const instanceRef = useRef(null);
  const inv = Number(investment) || 0;

  const finals = useMemo(() => {
    const rows = seriesDefs.map(def => {
      let pct = null;
      for (let i = chartData.length - 1; i >= 0; i--) {
        if (chartData[i]?.[def.key] != null) { pct = chartData[i][def.key]; break; }
      }
      const value = pct != null ? inv * (1 + pct / 100) : null;
      return { ...def, pct, value };
    });
    const best = rows.reduce((b, f) => (f.value != null && (b == null || f.value > b.value)) ? f : b, null);
    return { rows, bestKey: best?.key, bestValue: best?.value };
  }, [chartData, seriesDefs, inv]);

  useEffect(() => {
    if (!chartRef.current || !chartData.length) return;
    let disposed = false;
    import('echarts').then(echarts => {
      if (disposed) return;
      if (instanceRef.current) instanceRef.current.dispose();
      const chart = echarts.init(chartRef.current, null, { renderer: 'canvas' });
      instanceRef.current = chart;
      const labels = chartData.map(d => d.label);
      const series = seriesDefs.map(def => ({
        name: def.name, type: 'line', smooth: false, symbol: 'none', connectNulls: true,
        lineStyle: { width: 2.5, color: def.color }, itemStyle: { color: def.color },
        data: chartData.map(d => (d[def.key] != null ? Math.round(inv * (1 + d[def.key] / 100)) : null)),
      }));
      chart.setOption({
        backgroundColor: 'transparent', animation: false,
        grid: { top: 20, right: 20, bottom: 64, left: 72 },
        xAxis: {
          type: 'category', data: labels,
          axisLine: { lineStyle: { color: '#e5e7eb' } }, axisTick: { show: false },
          axisLabel: { color: '#9ca3af', fontSize: 10, interval: Math.floor(labels.length / 6) },
          splitLine: { show: false },
        },
        yAxis: {
          type: 'value', axisLine: { show: false }, axisTick: { show: false },
          axisLabel: {
            color: '#9ca3af', fontSize: 10,
            formatter: v => (Math.abs(v) >= 1000 ? `₺${(v / 1000).toLocaleString('tr-TR', { maximumFractionDigits: 1 })}b` : `₺${v}`),
          },
          splitLine: { lineStyle: { color: '#f0f0f0', type: 'dashed' } },
        },
        tooltip: {
          trigger: 'axis',
          axisPointer: { type: 'cross', lineStyle: { color: '#9ca3af', type: 'dashed' } },
          backgroundColor: '#ffffff', borderColor: '#e5e7eb', borderWidth: 1, borderRadius: 12, padding: [10, 14],
          formatter: params => {
            if (!params?.length) return '';
            let html = `<div style="font-size:11px;color:#6b7280;font-weight:600;margin-bottom:8px;padding-bottom:6px;border-bottom:1px solid #f0f0f0">${params[0].axisValue}</div>`;
            [...params].filter(p => p.value != null).sort((a, b) => b.value - a.value).forEach(p => {
              html += `<div style="display:flex;align-items:center;gap:8px;margin-bottom:4px">
                <span style="display:inline-block;width:10px;height:10px;border-radius:50%;background:${p.color};flex-shrink:0"></span>
                <span style="font-weight:700;color:#374151;min-width:64px">${p.seriesName}</span>
                <span style="margin-left:auto;font-family:monospace;font-weight:700;color:#111827">₺${Number(p.value).toLocaleString('tr-TR')}</span>
              </div>`;
            });
            return html;
          },
        },
        legend: { bottom: 4, textStyle: { color: '#6b7280', fontSize: 12 }, icon: 'circle', itemWidth: 10, itemHeight: 10 },
        series,
      });
      const ro = new ResizeObserver(() => chart.resize());
      ro.observe(chartRef.current);
    });
    return () => {
      disposed = true;
      if (instanceRef.current) { instanceRef.current.dispose(); instanceRef.current = null; }
    };
  }, [chartData, seriesDefs, inv]);

  const sorted = [...finals.rows].filter(f => f.value != null).sort((a, b) => b.value - a.value);

  return (
    <div>
      <div ref={chartRef} style={{ width: '100%', height: '300px' }} />
      <div className="mt-4 space-y-1.5">
        {sorted.map(f => {
          const isBest = f.key === finals.bestKey;
          const oc = finals.bestValue != null ? finals.bestValue - f.value : 0;
          return (
            <div key={f.key} className="flex items-center gap-3 text-sm px-1">
              <span className="w-2.5 h-2.5 rounded-full flex-shrink-0" style={{ background: f.color }} />
              <span className="font-semibold text-gray-800 w-24 sm:w-32 truncate">{f.name}</span>
              <span className="font-mono font-bold text-gray-900">₺{Math.round(f.value).toLocaleString('tr-TR')}</span>
              <span className={`ml-auto text-xs font-bold ${isBest ? 'text-emerald-600' : 'text-rose-500'}`}>
                {isBest ? `★ ${t('En iyi')}` : `${t('Fırsat maliyeti')}: −₺${Math.round(oc).toLocaleString('tr-TR')}`}
              </span>
            </div>
          );
        })}
      </div>
    </div>
  );
}
