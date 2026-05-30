/** Ortak Recharts bar stilleri — ince çubuklar, küçük değerler için minPointSize. */

export const CHART_GRID = {
  stroke: '#e8edf3',
  strokeDasharray: '4 4',
};

export const TICK = { fontSize: 10, fill: '#64748b' };

export const BAR_GROUPED_CHART = { barCategoryGap: '38%', barGap: 3 };
export const BAR_GROUPED_BAR = { maxBarSize: 12, minPointSize: 3 };

export const BAR_VERTICAL_CHART = { barCategoryGap: '40%' };
export const BAR_VERTICAL_BAR = { maxBarSize: 16, minPointSize: 5 };

export const BAR_HORIZONTAL_CHART = { barCategoryGap: '32%' };
export const BAR_HORIZONTAL_BAR = { maxBarSize: 9, minPointSize: 7 };

export const COLOR_POS = '#22c55e';
export const COLOR_NEG = '#ef4444';
export const COLOR_COST = '#94a3b8';
export const COLOR_MV = '#38bdf8';

/** Büyük farkta üst sınırı 2× yap — en yüksek çubuk grafiğin ~ortasına kadar gelir. */
export function positiveMoneyDomain(values, skewRatioThreshold = 6, headroomMultiplier = 2) {
  const nums = values.filter(v => typeof v === 'number' && Number.isFinite(v) && v > 0);
  if (!nums.length) return [0, 1];
  const max = Math.max(...nums);
  const min = Math.min(...nums);
  const ratio = min > 0 ? max / min : 1;
  const mult = ratio >= skewRatioThreshold ? headroomMultiplier : 1.08;
  return [0, max * mult];
}

export function isSkewedMoneyScale(values, skewRatioThreshold = 6) {
  const nums = values.filter(v => typeof v === 'number' && v > 0);
  if (nums.length < 2) return false;
  return Math.max(...nums) / Math.min(...nums) >= skewRatioThreshold;
}

export function signedNumericDomain(values, padFactor = 1.15) {
  const nums = values.filter(v => typeof v === 'number' && Number.isFinite(v));
  if (!nums.length) return ['auto', 'auto'];
  const maxAbs = Math.max(...nums.map(v => Math.abs(v)), 1);
  const bound = maxAbs * padFactor;
  return [-bound, bound];
}

export function positivePctDomain(values, padFactor = 1.12) {
  const nums = values.filter(v => typeof v === 'number' && Number.isFinite(v));
  if (!nums.length) return [0, 'auto'];
  const min = Math.min(...nums, 0);
  const max = Math.max(...nums, 0);
  if (max <= 0 && min >= 0) return [0, 1];
  const span = max - min || Math.max(max, 1);
  const pad = span * (padFactor - 1);
  return [min < 0 ? min - pad : 0, max > 0 ? max + pad : 1];
}

export function formatCompactMoney(v, currency, valuesHidden) {
  if (valuesHidden) return '••••';
  if (v == null || !Number.isFinite(v)) return '';
  const abs = Math.abs(v);
  const s =
    abs >= 1000
      ? `${(v / 1000).toLocaleString('tr-TR', { maximumFractionDigits: 1 })}K`
      : v.toLocaleString('tr-TR', { maximumFractionDigits: 0 });
  return currency ? `${s} ${currency}` : s;
}

export function formatCompactPct(v, valuesHidden) {
  if (valuesHidden) return '••••';
  if (v == null || !Number.isFinite(v)) return '';
  return `${v.toLocaleString('tr-TR', { maximumFractionDigits: 2 })}%`;
}
