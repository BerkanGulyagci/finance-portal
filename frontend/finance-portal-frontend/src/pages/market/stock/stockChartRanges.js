/**
 * Hisse grafikleri — Yahoo range + interval eşlemeleri.
 *
 * 1G  → 5dk (sabit)
 * 1H  → saatlik
 * 1A  → saatlik
 * 3A  → saatlik
 * 6A  → saatlik
 * 1Y  → günlük
 * 5Y  → haftalık
 * Tüm → aylık
 */
export const STOCK_CHART_RANGES = [
  { label: '1G', range: '1d', interval: '5m' },
  { label: '1H', range: '5d', interval: '1h' },
  { label: '1A', range: '1mo', interval: '1h' },
  { label: '3A', range: '3mo', interval: '1h' },
  { label: '6A', range: '6mo', interval: '1h' },
  { label: '1Y', range: '1y', interval: '1d' },
  { label: '5Y', range: '5y', interval: '1wk' },
  { label: 'Tüm', range: 'max', interval: '1mo' },
];

/** ECharts / Recharts eksen etiketi */
export function formatStockChartTimeLabel(timestampSec, range, interval) {
  const d = new Date(Number(timestampSec) * 1000);
  if (range === '1d' || ['5m', '15m', '30m'].includes(interval)) {
    return d.toLocaleTimeString('tr-TR', { hour: '2-digit', minute: '2-digit' });
  }
  if (interval === '1h' || interval === '90m') {
    if (range === '5d') {
      return d.toLocaleString('tr-TR', { weekday: 'short', hour: '2-digit', minute: '2-digit' });
    }
    return d.toLocaleString('tr-TR', { day: '2-digit', month: 'short', hour: '2-digit' });
  }
  if (interval === '1d') {
    return d.toLocaleDateString('tr-TR', { day: '2-digit', month: '2-digit' });
  }
  return d.toLocaleDateString('tr-TR', { month: '2-digit', year: '2-digit' });
}
