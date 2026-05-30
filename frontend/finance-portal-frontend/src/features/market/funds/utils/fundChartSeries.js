/** Fon fiyat grafiği zaman aralıkları */
export const FUND_CHART_RANGES = [
  { key: '1W', label: '1H', days: 7 },
  { key: '1M', label: '1 Ay', days: 30 },
  { key: '3M', label: '3 Ay', days: 90 },
  { key: '6M', label: '6 Ay', days: 180 },
  { key: '1Y', label: '1 Yıl', days: 365 },
  { key: '5Y', label: '5Y', days: 1825 },
];

function toNum(v) {
  if (v == null) return null;
  const n = parseFloat(v);
  return Number.isFinite(n) ? n : null;
}

function monthToDate(year, month) {
  const m = String(month).padStart(2, '0');
  return `${year}-${m}-01`;
}

function monthKey(year, month) {
  return year * 12 + month;
}

function normalizeDaily(priceHistory) {
  if (!priceHistory?.length) return [];
  return [...priceHistory]
    .map((p) => ({ date: p.date, price: toNum(p.price) }))
    .filter((p) => p.date && p.price != null && p.price > 0)
    .sort((a, b) => new Date(a.date) - new Date(b.date));
}

function filterByCutoff(points, cutoff) {
  const t = cutoff.getTime();
  return points.filter((p) => new Date(p.date).getTime() >= t);
}

/**
 * Günlük serinin başlangıcından önceki ayları, aylık getiri % ile geriye doğru tahmin eder.
 */
function buildMonthlyExtension(monthlyReturns, dailySorted, cutoff) {
  if (!monthlyReturns?.length || !dailySorted.length) return [];

  const anchor = dailySorted[0];
  const anchorDate = new Date(anchor.date);
  const anchorMk = monthKey(anchorDate.getFullYear(), anchorDate.getMonth() + 1);

  const months = [...monthlyReturns]
    .map((m) => ({
      year: parseInt(m.year, 10),
      month: parseInt(m.month, 10),
      value: toNum(m.value),
    }))
    .filter((m) => Number.isFinite(m.year) && Number.isFinite(m.month) && m.value != null)
    .sort((a, b) => monthKey(a.year, a.month) - monthKey(b.year, b.month));

  const beforeAnchor = months.filter((m) => {
    const mk = monthKey(m.year, m.month);
    if (mk >= anchorMk) return false;
    return new Date(monthToDate(m.year, m.month)).getTime() >= cutoff.getTime();
  });

  if (!beforeAnchor.length) return [];

  let price = anchor.price;
  const points = [];
  for (let i = beforeAnchor.length - 1; i >= 0; i -= 1) {
    const m = beforeAnchor[i];
    const ret = m.value / 100;
    price = ret === -1 ? price : price / (1 + ret);
    points.unshift({ date: monthToDate(m.year, m.month), price });
  }
  return points;
}

function mergeMonthlyAndDaily(monthlyPoints, dailyPoints) {
  if (!monthlyPoints.length) return dailyPoints;
  const dailyMonths = new Set(dailyPoints.map((p) => p.date.slice(0, 7)));
  const monthlyOnly = monthlyPoints.filter((p) => !dailyMonths.has(p.date.slice(0, 7)));
  return [...monthlyOnly, ...dailyPoints].sort((a, b) => new Date(a.date) - new Date(b.date));
}

/**
 * @returns {{ points: { date: string, price: number }[], usesMonthlyExtension: boolean }}
 */
export function buildFundChartSeries(priceHistory, monthlyReturns, rangeKey) {
  const rangeDef = FUND_CHART_RANGES.find((r) => r.key === rangeKey)
    ?? FUND_CHART_RANGES.find((r) => r.key === '1Y');

  const cutoff = new Date();
  cutoff.setHours(0, 0, 0, 0);
  cutoff.setDate(cutoff.getDate() - (rangeDef?.days ?? 365));

  const daily = filterByCutoff(normalizeDaily(priceHistory), cutoff);

  if (rangeKey !== '5Y') {
    return { points: daily, usesMonthlyExtension: false };
  }

  const monthlyExt = buildMonthlyExtension(monthlyReturns, normalizeDaily(priceHistory), cutoff);
  const points = mergeMonthlyAndDaily(monthlyExt, daily);

  return {
    points: filterByCutoff(points, cutoff),
    usesMonthlyExtension: monthlyExt.length > 0,
  };
}
