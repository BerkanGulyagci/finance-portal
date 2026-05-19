import { formatTrNumber } from '../../../utils/numberFormat';

export const MASK_MONEY = '•••••';
export const MASK_PERCENT = '••••';
export const MASK_QTY = '••••';

export function formatMoney(value, currency, valuesHidden) {
  if (valuesHidden) return MASK_MONEY;
  if (value == null || !Number.isFinite(Number(value))) return '-';
  const n = Number(value);
  const s = formatTrNumber(n, 2);
  return currency ? `${s} ${currency}` : s;
}

export function formatPercent(value, valuesHidden, { signed = false } = {}) {
  if (valuesHidden) return MASK_PERCENT;
  if (value == null || !Number.isFinite(Number(value))) return '-';
  const n = Number(value);
  const abs = Math.abs(n);
  let decimals = 2;
  if (abs > 0 && abs < 0.005) decimals = 4;
  const s = n.toLocaleString('tr-TR', {
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  });
  const prefix = signed && n > 0 ? '+' : signed && n < 0 ? '' : '';
  return `${prefix}${s}%`;
}

export function formatChartValue(value, valuesHidden, metric) {
  if (valuesHidden) return MASK_MONEY;
  if (value == null || !Number.isFinite(Number(value))) return '-';
  const n = Number(value);
  if (metric === 'GROWTH') {
    return formatPercent(n, false, { signed: true });
  }
  return formatTrNumber(n, 2);
}
