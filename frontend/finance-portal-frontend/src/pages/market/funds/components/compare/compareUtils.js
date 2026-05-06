// ── Ortak yardımcılar ────────────────────────────────────────────────────────

export const COMPARE_COLORS = [
  '#093eaa', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6',
];

export function toNum(v) {
  if (v == null) return null;
  const n = parseFloat(v);
  return isNaN(n) ? null : n;
}

export function fmtPct(v, dec = 2) {
  const n = toNum(v);
  if (n == null) return '-';
  return `${n >= 0 ? '+' : ''}${n.toFixed(dec)}%`;
}

export function fmtPctColor(v) {
  const n = toNum(v);
  if (n == null) return 'text-gray-400';
  return n >= 0 ? 'text-emerald-600' : 'text-rose-600';
}

export function fmtPrice(v) {
  const n = toNum(v);
  if (n == null) return '-';
  const abs = Math.abs(n);
  const dec = abs >= 100 ? 2 : abs >= 10 ? 3 : abs >= 1 ? 4 : abs >= 0.1 ? 5 : 6;
  return n.toLocaleString('tr-TR', { minimumFractionDigits: dec, maximumFractionDigits: dec });
}

export function fmtBig(v) {
  const n = toNum(v);
  if (n == null || isNaN(n)) return '-';
  if (n >= 1e9) return `${(n / 1e9).toFixed(2)}B ₺`;
  if (n >= 1e6) return `${(n / 1e6).toFixed(2)}M ₺`;
  if (n >= 1e3) return `${(n / 1e3).toFixed(2)}K ₺`;
  return `${n.toFixed(2)} ₺`;
}

/** priceHistory'yi seçili range'e göre filtrele */
export function filterByRange(priceHistory, range) {
  if (!priceHistory || priceHistory.length === 0) return [];
  const dayMap = { '1M': 30, '3M': 90, '6M': 180, '1Y': 365 };
  const days = dayMap[range] ?? 365;
  const cutoff = Date.now() - days * 86400_000;
  const filtered = priceHistory.filter(p => new Date(p.date).getTime() >= cutoff);
  return filtered.length > 0 ? filtered : priceHistory.slice(-days);
}

/** Normalize performans hesabı: ((price - firstPrice) / firstPrice) * 100 */
export function buildNormalizedSeries(priceHistory, range) {
  const pts = filterByRange(priceHistory, range);
  if (pts.length < 2) return [];
  const sorted = [...pts].sort((a, b) => new Date(a.date) - new Date(b.date));
  const firstPrice = toNum(sorted[0].price);
  if (!firstPrice || firstPrice === 0) return [];
  return sorted.map(p => {
    const price = toNum(p.price);
    if (price == null) return null;
    return {
      date: p.date,
      ret: parseFloat(((price - firstPrice) / firstPrice * 100).toFixed(4)),
    };
  }).filter(Boolean);
}

/** Tüm fonların tarih birleşimi üzerinden chart data oluştur */
export function buildChartData(detailMap, selectedCodes, range) {
  const allDates = new Set();
  selectedCodes.forEach(code => {
    const ph = detailMap[code]?.priceHistory ?? [];
    filterByRange(ph, range).forEach(p => allDates.add(p.date));
  });
  const sortedDates = [...allDates].sort();

  // Her fon için normalize serisi map'e al
  const seriesMap = {};
  selectedCodes.forEach(code => {
    const ph = detailMap[code]?.priceHistory ?? [];
    const series = buildNormalizedSeries(ph, range);
    const byDate = {};
    series.forEach(s => { byDate[s.date] = s.ret; });
    seriesMap[code] = byDate;
  });

  return sortedDates.map(date => {
    const row = { date };
    selectedCodes.forEach(code => {
      if (seriesMap[code][date] != null) row[code] = seriesMap[code][date];
    });
    return row;
  });
}

/** Seçili range için dönem getirisi — priceHistory'den hesapla */
export function calcPeriodReturn(priceHistory, range) {
  const pts = filterByRange(priceHistory, range);
  if (pts.length < 2) return null;
  const sorted = [...pts].sort((a, b) => new Date(a.date) - new Date(b.date));
  const first = toNum(sorted[0].price);
  const last  = toNum(sorted[sorted.length - 1].price);
  if (!first || first === 0 || !last) return null;
  return ((last - first) / first) * 100;
}
