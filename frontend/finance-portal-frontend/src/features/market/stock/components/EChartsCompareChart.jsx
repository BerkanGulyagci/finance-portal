import { useEffect, useRef } from 'react';
import { useTheme } from '../../../../context/ThemeContext';

// Fiyatı tooltip için biçimle: ≥1 TL → 2 ondalık; <1 TL (mikro-cap coin, ör. 0,0000000012 TL) →
// ~4 anlamlı basamak görünene kadar ondalık aç (sabit 2 ondalıkta "₺0,00" görünüyordu).
function fmtTooltipPrice(price) {
  const n = parseFloat(price);
  if (!Number.isFinite(n)) return '';
  const abs = Math.abs(n);
  if (abs === 0) return '0';
  const maxDec = abs >= 1 ? 2 : Math.min(20, Math.max(2, 3 - Math.floor(Math.log10(abs))));
  return n.toLocaleString('tr-TR', { minimumFractionDigits: 2, maximumFractionDigits: maxDec });
}

export default function EChartsCompareChart({ chartData, seriesDefs }) {
  const chartRef = useRef(null);
  const instanceRef = useRef(null);
  const { isDark } = useTheme();

  useEffect(() => {
    if (!chartRef.current || !chartData.length) return;

    import('echarts').then(echarts => {
      // Mevcut instance'ı dispose et
      if (instanceRef.current) {
        instanceRef.current.dispose();
      }

      const chart = echarts.init(chartRef.current, null, { renderer: 'canvas' });
      instanceRef.current = chart;

      const labels = chartData.map(d => d.label);

      const series = seriesDefs.map(def => ({
        name: def.name,
        type: 'line',
        data: chartData.map(d => ({
          value: d[def.key] ?? null,
          price: d[`__price_${def.key}`] ?? null,
        })),
        smooth: false,
        symbol: 'none',
        lineStyle: { width: 2.5, color: def.color },
        itemStyle: { color: def.color },
        connectNulls: true,
      }));

      // Yalnız enflasyon/faiz benchmark'ları fiyat değil soyut endekstir → tooltip'te ₺ gösterme.
      // BIST endeksleri (INDICATOR|XBANK …) GERÇEK ₺ değeri taşır → onlarda ₺ göster.
      const BENCHMARK_KEYS = new Set(['INDICATOR|TUFE', 'INDICATOR|USCPI_TRY', 'INDICATOR|DEPOSIT']);
      const noPriceNames = new Set(
        (seriesDefs || []).filter(d => BENCHMARK_KEYS.has(String(d.key))).map(d => d.name),
      );

      const option = {
        backgroundColor: 'transparent',
        animation: false,
        grid: { top: 20, right: 60, bottom: 80, left: 70 },
        xAxis: {
          type: 'category',
          data: labels,
          axisLine: { lineStyle: { color: '#e5e7eb' } },
          axisTick: { show: false },
          axisLabel: {
            color: '#9ca3af',
            fontSize: 10,
            interval: Math.floor(labels.length / 6),
            rotate: 0,
          },
          splitLine: { show: false },
        },
        yAxis: {
          type: 'value',
          axisLine: { show: false },
          axisTick: { show: false },
          axisLabel: {
            color: '#9ca3af',
            fontSize: 10,
            formatter: v => `${v >= 0 ? '+' : ''}${v.toFixed(1)}%`,
          },
          splitLine: { lineStyle: { color: '#f0f0f0', type: 'dashed' } },
        },
        tooltip: {
          trigger: 'axis',
          axisPointer: {
            type: 'cross',
            crossStyle: { color: '#9ca3af', width: 1 },
            lineStyle: { color: '#9ca3af', width: 1, type: 'dashed' },
          },
          backgroundColor: isDark ? 'rgba(15,23,42,0.92)' : 'rgba(255,255,255,0.96)',
          borderColor: isDark ? 'rgba(255,255,255,0.12)' : '#e5e7eb',
          borderWidth: 1,
          borderRadius: 12,
          padding: [10, 14],
          textStyle: { color: isDark ? '#e2e8f0' : '#374151', fontSize: 12 },
          formatter: params => {
            if (!params?.length) return '';
            const labelColor = isDark ? '#94a3b8' : '#6b7280';
            const mainColor = isDark ? '#e2e8f0' : '#374151';
            const borderCol = isDark ? 'rgba(255,255,255,0.12)' : '#f0f0f0';
            let html = `<div style="font-size:11px;color:${labelColor};font-weight:600;margin-bottom:8px;padding-bottom:6px;border-bottom:1px solid ${borderCol}">${params[0].axisValue}</div>`;
            params.forEach(p => {
              if (p.value == null) return;
              const price = p.data?.price;
              const isPos = p.value >= 0;
              const pctColor = isPos ? '#10b981' : '#ef4444';
              html += `<div style="display:flex;align-items:center;gap:8px;margin-bottom:6px">
                <span style="display:inline-block;width:10px;height:10px;border-radius:50%;background:${p.color};flex-shrink:0"></span>
                <span style="font-weight:700;color:${mainColor};min-width:60px">${p.seriesName}:</span>
                ${price != null && !noPriceNames.has(p.seriesName) ? `<span style="color:${labelColor};font-family:monospace">₺${fmtTooltipPrice(price)}</span>` : ''}
                <span style="font-weight:700;color:${pctColor};margin-left:auto">${isPos ? '+' : ''}${p.value.toFixed(2)}%</span>
              </div>`;
            });
            return html;
          },
        },
        legend: {
          bottom: 32,
          textStyle: { color: '#6b7280', fontSize: 12 },
          icon: 'circle',
          itemWidth: 10,
          itemHeight: 10,
        },
        dataZoom: [
          {
            type: 'inside',
            xAxisIndex: 0,
            start: 0,
            end: 100,
            zoomOnMouseWheel: true,
            moveOnMouseMove: true,
          },
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
            textStyle: { color: '#9ca3af', fontSize: 10 },
            showDetail: false,
          },
        ],
        series,
      };

      chart.setOption(option);

      // Responsive
      const ro = new ResizeObserver(() => chart.resize());
      ro.observe(chartRef.current);

      return () => { ro.disconnect(); };
    });

    return () => {
      if (instanceRef.current) {
        instanceRef.current.dispose();
        instanceRef.current = null;
      }
    };
  }, [chartData, seriesDefs, isDark]);

  return <div ref={chartRef} style={{ width: '100%', height: '420px' }} />;
}

