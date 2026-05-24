/**
 * Portföy holdings analitik hesapları (marketValue + günlük / açık K-Z %).
 */

export const PORTFOLIO_ASSET_LABELS = {
  STOCK: 'Hisse',
  CRYPTO: 'Kripto',
  FUND: 'Fon',
  COMMODITY: 'Emtia',
  GOLD: 'Altın',
  FUTURE: 'Vadeli',
  FX: 'Döviz',
  BOND: 'DİBS',
  OTHER: 'Diğer',
};

export const CHART_DONUT_COLORS = [
  '#0ea5e9',
  '#8b5cf6',
  '#10b981',
  '#f59e0b',
  '#eab308',
  '#64748b',
  '#06b6d4',
  '#f43f5e',
];

function num(h, ...keys) {
  for (const k of keys) {
    const v = h?.[k];
    if (v == null || v === '') continue;
    const n = typeof v === 'number' ? v : parseFloat(v);
    if (Number.isFinite(n)) return n;
  }
  return null;
}

export function parseMarketValue(h) {
  return num(h, 'marketValue');
}

export function parseTotalCost(h) {
  return num(h, 'totalCost');
}

/** marketValue yoksa miktar × güncel fiyat. */
export function parseMarketValueExtended(h) {
  const mv = parseMarketValue(h);
  if (mv != null) return mv;
  const q = num(h, 'totalQuantity');
  const p = num(h, 'currentPrice');
  if (q != null && p != null) return q * p;
  return null;
}

export function parseProfitLoss(h) {
  const direct = num(h, 'profitLoss', 'unrealizedGainLoss');
  if (direct != null) return direct;
  const mv = parseMarketValueExtended(h);
  const cost = parseTotalCost(h);
  if (mv != null && cost != null) return mv - cost;
  return null;
}

export function parseDailyProfitLoss(h) {
  const direct = num(h, 'dailyGainLoss', 'dailyPnl', 'dailyProfitLoss', 'dayGain');
  if (direct != null) return direct;
  const qty = num(h, 'totalQuantity');
  const perShare = num(h, 'dailyChangeAmount', 'change');
  if (qty != null && perShare != null) return qty * perShare;
  const pc = num(h, 'previousClose', 'regularMarketPreviousClose', 'prevClose');
  const cp = num(h, 'currentPrice');
  if (qty != null && cp != null && pc != null) return qty * (cp - pc);
  return null;
}

export function hasAnyDailyProfitLossData(holdings) {
  return (holdings ?? []).some(h => parseDailyProfitLoss(h) !== null);
}

export function parseDailyChangePercent(h) {
  return num(h, 'dailyGainLossPercent', 'dailyChangePercent', 'changePercent', 'returnOneDay');
}

export function parseUnrealizedChangePercent(h) {
  const direct = num(h, 'profitLossPercent', 'unrealizedGainLossPercent');
  if (direct != null) return direct;
  const mv = parseMarketValue(h);
  const cost = num(h, 'totalCost');
  if (mv == null || cost == null || cost <= 0) return null;
  return ((mv - cost) / cost) * 100;
}

/** Sıralama için: önce günlük %, yoksa açık K/Z %. */
export function parseRankingChangePercent(h) {
  const daily = parseDailyChangePercent(h);
  if (daily != null) return daily;
  return parseUnrealizedChangePercent(h);
}

export function groupByAssetType(holdings) {
  const map = {};
  for (const h of holdings ?? []) {
    const t = h.assetType || 'OTHER';
    if (!map[t]) map[t] = [];
    map[t].push(h);
  }
  return map;
}

/**
 * @returns {{ name: string, type: string, value: number, sharePct: number }[]}
 */
export function calculateAllocationByType(holdings) {
  const groups = groupByAssetType(holdings);
  let total = 0;
  const rows = [];
  for (const [type, list] of Object.entries(groups)) {
    let sum = 0;
    for (const h of list) {
      // marketValue alanı boşsa miktar × güncel fiyat fallback'i kullan;
      // yoksa (ör. bazı hisseler) o varlık türü pie'da hiç görünmüyordu.
      const mv = parseMarketValueExtended(h);
      if (mv != null && mv > 0) sum += mv;
    }
    if (sum <= 0) continue;
    total += sum;
    rows.push({
      type,
      name: PORTFOLIO_ASSET_LABELS[type] ?? type,
      value: sum,
    });
  }
  rows.sort((a, b) => b.value - a.value);
  for (const r of rows) {
    r.sharePct = total > 0 ? (r.value / total) * 100 : 0;
  }
  return { rows, total };
}

/**
 * En büyük 5 pozisyon + Diğer (marketValue).
 */
export function calculateTopHoldingsDistribution(holdings, topN = 5) {
  const withMv = (holdings ?? [])
    .map(h => ({
      h,
      mv: parseMarketValue(h),
      label: (h.name && h.name !== h.symbol ? h.name : h.symbol) || '—',
      type: h.assetType,
    }))
    .filter(x => x.mv != null && x.mv > 0)
    .sort((a, b) => b.mv - a.mv);

  if (!withMv.length) return { rows: [], total: 0 };

  const total = withMv.reduce((s, x) => s + x.mv, 0);
  const top = withMv.slice(0, topN);
  const rest = withMv.slice(topN);
  const rows = top.map(x => ({
    name: x.label,
    type: x.type,
    value: x.mv,
    sharePct: (x.mv / total) * 100,
  }));

  if (rest.length) {
    const otherSum = rest.reduce((s, x) => s + x.mv, 0);
    rows.push({
      name: 'Diğer',
      type: 'OTHER',
      value: otherSum,
      sharePct: (otherSum / total) * 100,
    });
  }

  return { rows, total };
}

export function calculateDailyStatus(holdings) {
  let up = 0;
  let down = 0;
  let flat = 0;
  let nodata = 0;
  for (const h of holdings ?? []) {
    const n = parseDailyChangePercent(h);
    if (n === null) nodata += 1;
    else if (n > 0) up += 1;
    else if (n < 0) down += 1;
    else flat += 1;
  }
  return {
    total: (holdings ?? []).length,
    up,
    down,
    flat,
    nodata,
  };
}

export function hasAnyDailyChangeData(holdings) {
  return (holdings ?? []).some(h => parseDailyChangePercent(h) !== null);
}

export function calculateAverageChangeByType(holdings) {
  const groups = groupByAssetType(holdings);
  const out = [];
  for (const [type, list] of Object.entries(groups)) {
    const vals = list.map(parseDailyChangePercent).filter(v => v !== null);
    if (!vals.length) continue;
    const avg = vals.reduce((a, b) => a + b, 0) / vals.length;
    out.push({
      type,
      label: PORTFOLIO_ASSET_LABELS[type] ?? type,
      avg,
      count: vals.length,
    });
  }
  out.sort((a, b) => a.label.localeCompare(b.label, 'tr'));
  return out;
}

export function getTopGainers(holdings, limit = 5) {
  const withVal = (holdings ?? [])
    .map(h => ({ h, pct: parseRankingChangePercent(h) }))
    .filter(x => x.pct != null && x.pct > 0);
  withVal.sort((a, b) => b.pct - a.pct || (a.h.symbol ?? '').localeCompare(b.h.symbol ?? ''));
  return withVal.slice(0, limit).map(({ h, pct }) => ({ holding: h, changePercent: pct }));
}

export function getTopLosers(holdings, limit = 5) {
  const withVal = (holdings ?? [])
    .map(h => ({ h, pct: parseRankingChangePercent(h) }))
    .filter(x => x.pct != null && x.pct < 0);
  withVal.sort((a, b) => a.pct - b.pct || (a.h.symbol ?? '').localeCompare(b.h.symbol ?? ''));
  return withVal.slice(0, limit).map(({ h, pct }) => ({ holding: h, changePercent: pct }));
}

export function formatSharePercent(percentOf100, valuesHidden) {
  if (valuesHidden) return '••••';
  if (!Number.isFinite(percentOf100)) return '-';
  const x = Math.round(percentOf100 * 10) / 10;
  const str = Number.isInteger(x)
    ? x.toLocaleString('tr-TR')
    : x.toLocaleString('tr-TR', { minimumFractionDigits: 1, maximumFractionDigits: 1 });
  return `%${str}`;
}

export function formatPctSigned(n, valuesHidden) {
  if (valuesHidden) return '••••';
  if (n == null || !Number.isFinite(n)) return '-';
  const s = n.toLocaleString('tr-TR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  return `${n >= 0 ? '+' : ''}${s}%`;
}

export function formatPctAxis(n) {
  if (!Number.isFinite(n)) return '';
  return `${n.toLocaleString('tr-TR', { maximumFractionDigits: 2 })}%`;
}

export function holdingDisplayName(h) {
  if (!h) return '—';
  const name = (h.name ?? '').trim();
  const sym = (h.symbol ?? '').trim();
  if (name && name !== sym) return name;
  return sym || '—';
}

export function assetTypeBadgeClass(t) {
  switch (t) {
    case 'STOCK':
      return 'bg-sky-100 text-sky-900';
    case 'CRYPTO':
      return 'bg-violet-100 text-violet-900';
    case 'FUND':
      return 'bg-indigo-100 text-indigo-900';
    case 'COMMODITY':
      return 'bg-amber-100 text-amber-950';
    case 'GOLD':
      return 'bg-yellow-100 text-yellow-950';
    case 'FX':
      return 'bg-teal-100 text-teal-900';
    case 'BOND':
      return 'bg-slate-100 text-slate-800';
    case 'FUTURE':
      return 'bg-orange-100 text-orange-950';
    default:
      return 'bg-gray-100 text-gray-800';
  }
}

export function pctToneClass(n) {
  if (n == null || !Number.isFinite(n)) return 'text-gray-400';
  if (n > 0) return 'text-emerald-600';
  if (n < 0) return 'text-rose-600';
  return 'text-gray-500';
}

export function sumMarketValue(holdings) {
  let sum = 0;
  let any = false;
  for (const h of holdings ?? []) {
    const mv = parseMarketValueExtended(h);
    if (mv != null && mv > 0) {
      sum += mv;
      any = true;
    }
  }
  return any ? sum : 0;
}

/** Maliyet / piyasa değeri karşılaştırması (bar chart). */
export function calculateCostVsMarketRows(holdings, maxRows = 12) {
  const rows = (holdings ?? [])
    .map(h => {
      const cost = parseTotalCost(h) ?? 0;
      const mv = parseMarketValueExtended(h) ?? 0;
      if (cost <= 0 && mv <= 0) return null;
      return {
        key: h.id ?? h.symbol,
        name: holdingDisplayName(h),
        cost,
        marketValue: mv,
        assetType: h.assetType,
      };
    })
    .filter(Boolean)
    .sort((a, b) => b.marketValue - a.marketValue);

  const truncated = rows.length > maxRows;
  return { rows: rows.slice(0, maxRows), truncated, total: rows.length };
}

/** Varlık bazlı açık K/Z (mutlak değere göre sıralı). */
export function calculateProfitLossByHolding(holdings, maxRows = 12) {
  const rows = (holdings ?? [])
    .map(h => {
      const pl = parseProfitLoss(h);
      if (pl == null || !Number.isFinite(pl)) return null;
      return {
        key: h.id ?? h.symbol,
        name: holdingDisplayName(h),
        profitLoss: pl,
        assetType: h.assetType,
      };
    })
    .filter(Boolean)
    .sort((a, b) => Math.abs(b.profitLoss) - Math.abs(a.profitLoss));

  const truncated = rows.length > maxRows;
  return { rows: rows.slice(0, maxRows), truncated, total: rows.length };
}

/** AssetType bazında toplam açık K/Z. */
export function calculateProfitLossByType(holdings) {
  const groups = groupByAssetType(holdings);
  const rows = [];
  for (const [type, list] of Object.entries(groups)) {
    let sum = 0;
    let any = false;
    for (const h of list) {
      const pl = parseProfitLoss(h);
      if (pl != null && Number.isFinite(pl)) {
        sum += pl;
        any = true;
      }
    }
    if (!any) continue;
    rows.push({
      type,
      label: PORTFOLIO_ASSET_LABELS[type] ?? type,
      profitLoss: sum,
    });
  }
  rows.sort((a, b) => Math.abs(b.profitLoss) - Math.abs(a.profitLoss));
  return rows;
}

/** Günlük K/Z katkısı. */
export function calculateDailyContributionRows(holdings, maxRows = 12) {
  const rows = (holdings ?? [])
    .map(h => {
      const daily = parseDailyProfitLoss(h);
      if (daily == null || !Number.isFinite(daily)) return null;
      return {
        key: h.id ?? h.symbol,
        name: holdingDisplayName(h),
        daily,
        assetType: h.assetType,
      };
    })
    .filter(Boolean)
    .sort((a, b) => Math.abs(b.daily) - Math.abs(a.daily));

  const truncated = rows.length > maxRows;
  return { rows: rows.slice(0, maxRows), truncated, total: rows.length };
}

export function getConcentrationRiskLevel(topPositionSharePct) {
  if (topPositionSharePct == null || !Number.isFinite(topPositionSharePct)) return null;
  if (topPositionSharePct >= 70) return { level: 'high', label: 'Yüksek yoğunlaşma' };
  if (topPositionSharePct >= 40) return { level: 'medium', label: 'Orta yoğunlaşma' };
  return { level: 'low', label: 'Dengeli' };
}

export function calculateConcentrationMetrics(holdings) {
  const list = holdings ?? [];
  const withMv = list
    .map(h => ({
      h,
      mv: parseMarketValueExtended(h),
      name: holdingDisplayName(h),
    }))
    .filter(x => x.mv != null && x.mv > 0);

  const totalMv = withMv.reduce((s, x) => s + x.mv, 0);
  const { rows: typeRows } = calculateAllocationByType(list);

  let topPosition = null;
  if (withMv.length && totalMv > 0) {
    const top = [...withMv].sort((a, b) => b.mv - a.mv)[0];
    topPosition = {
      name: top.name,
      sharePct: (top.mv / totalMv) * 100,
    };
  }

  let topCategory = null;
  if (typeRows.length && totalMv > 0) {
    const top = typeRows[0];
    topCategory = {
      name: top.name,
      sharePct: top.sharePct,
    };
  }

  const positionCount = withMv.length;
  const typeCount = typeRows.length;
  const risk = getConcentrationRiskLevel(topPosition?.sharePct ?? null);

  return {
    totalMv,
    topPosition,
    topCategory,
    positionCount,
    typeCount,
    risk,
    hasData: totalMv > 0 && (topPosition != null || topCategory != null),
  };
}

export function truncateChartLabel(name, max = 16) {
  if (!name) return '—';
  return name.length > max ? `${name.slice(0, max - 1)}…` : name;
}
