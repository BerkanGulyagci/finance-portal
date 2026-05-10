/**
 * İzleme listesi grafik sekmesi — saf hesaplamalar (WatchlistItemResponse[]).
 * Günlük sınıflandırma: changePercent (günlük %).
 */

export const WATCHLIST_ASSET_LABELS = {
  STOCK: 'Hisse',
  CRYPTO: 'Kripto',
  FUND: 'Fon',
  COMMODITY: 'Emtia',
  GOLD: 'Altın',
  FUTURE: 'Vadeli',
  FX: 'Döviz',
  BOND: 'Tahvil',
  OTHER: 'Diğer',
};

const COMMODITY_NAMES = {
  'CL=F': 'WTI Ham Petrol',
  'BZ=F': 'Brent Ham Petrol',
  'NG=F': 'Doğal Gaz',
  'HG=F': 'Bakır',
  'ZW=F': 'Buğday',
  'ZC=F': 'Mısır',
  'KC=F': 'Kahve',
  'CC=F': 'Kakao',
  'CT=F': 'Pamuk',
};

const GOLD_NAMES = {
  GOLD: 'Altın (Ons)',
  GRAM: 'Gram Altın',
  CEYREK: 'Çeyrek Altın',
  YARIM: 'Yarım Altın',
  TAM: 'Tam Altın',
  CUMHUR: 'Cumhuriyet Altını',
  ATA: 'Ata Altın',
};

const PRECIOUS_NAMES = {
  'SILVER:GRAM_TRY': 'Gram Gümüş (₺)',
  'SILVER:KG_TRY': 'Kg Gümüş (₺)',
  'SILVER:USD_ONS': 'Ons Gümüş ($)',
  'PLATINUM:GRAM_TRY': 'Gram Platin (₺)',
  'PLATINUM:KG_TRY': 'Kg Platin (₺)',
  'PLATINUM:USD_ONS': 'Ons Platin ($)',
  'PLATINUM:EUR_ONS': 'Ons Platin (€)',
  'PALLADIUM:GRAM_TRY': 'Gram Paladyum (₺)',
  'PALLADIUM:KG_TRY': 'Kg Paladyum (₺)',
  'PALLADIUM:USD_ONS': 'Ons Paladyum ($)',
  'PALLADIUM:EUR_ONS': 'Ons Paladyum (€)',
};

/**
 * Günlük değişim yüzdesi; yoksa null.
 */
export function parseDailyChangePercent(item) {
  if (!item) return null;
  const v = item.changePercent;
  if (v === null || v === undefined || v === '') return null;
  const n = typeof v === 'number' ? v : parseFloat(v);
  return Number.isFinite(n) ? n : null;
}

/**
 * @returns {Record<string, object[]>}
 */
export function groupByAssetType(items) {
  const map = {};
  for (const item of items ?? []) {
    const t = item.assetType || 'OTHER';
    if (!map[t]) map[t] = [];
    map[t].push(item);
  }
  return map;
}

/**
 * Günlük durum sayıları (changePercent).
 */
export function calculateDailyStatus(items) {
  let up = 0;
  let down = 0;
  let flat = 0;
  let nodata = 0;
  for (const item of items ?? []) {
    const n = parseDailyChangePercent(item);
    if (n === null) nodata += 1;
    else if (n > 0) up += 1;
    else if (n < 0) down += 1;
    else flat += 1;
  }
  const list = items ?? [];
  return {
    total: list.length,
    up,
    down,
    flat,
    nodata,
  };
}

/**
 * Kategori bazlı ortalama günlük %; verisi olmayan tipler listelenmez.
 * @returns {{ type: string, label: string, avg: number, count: number }[]}
 */
export function calculateAverageChangeByType(items) {
  const groups = groupByAssetType(items);
  const out = [];
  for (const [type, list] of Object.entries(groups)) {
    const vals = list.map(parseDailyChangePercent).filter(v => v !== null);
    if (vals.length === 0) continue;
    const avg = vals.reduce((a, b) => a + b, 0) / vals.length;
    out.push({
      type,
      label: WATCHLIST_ASSET_LABELS[type] ?? type,
      avg,
      count: vals.length,
    });
  }
  out.sort((a, b) => a.label.localeCompare(b.label, 'tr'));
  return out;
}

export function hasAnyChangePercentData(items) {
  return (items ?? []).some(i => parseDailyChangePercent(i) !== null);
}

/**
 * En yüksek günlük % (tie: sıra korunur).
 */
export function getTopGainers(items, limit = 5) {
  const withVal = (items ?? [])
    .map(item => ({ item, pct: parseDailyChangePercent(item) }))
    .filter(x => x.pct !== null && x.pct > 0);
  withVal.sort((a, b) => b.pct - a.pct || (a.item.symbol ?? '').localeCompare(b.item.symbol ?? ''));
  return withVal.slice(0, limit).map(({ item, pct }) => ({
    item,
    changePercent: pct,
  }));
}

/**
 * En düşük günlük % (en çok negatif).
 */
export function getTopLosers(items, limit = 5) {
  const withVal = (items ?? [])
    .map(item => ({ item, pct: parseDailyChangePercent(item) }))
    .filter(x => x.pct !== null && x.pct < 0);
  withVal.sort((a, b) => a.pct - b.pct || (a.item.symbol ?? '').localeCompare(b.item.symbol ?? ''));
  return withVal.slice(0, limit).map(({ item, pct }) => ({
    item,
    changePercent: pct,
  }));
}

/** Tablo / grafik için kısa görünen ad (WatchlistTable ile uyumlu) */
export function getWatchlistChartDisplayTitle(item) {
  if (!item) return '-';
  const symbol = item.symbol ?? '';
  const type = item.assetType;

  if (type === 'COMMODITY') {
    const name = PRECIOUS_NAMES[symbol] || COMMODITY_NAMES[symbol];
    if (name) return name;
    return symbol;
  }
  if (type === 'GOLD') {
    return GOLD_NAMES[symbol] || symbol;
  }
  if (type === 'FUND') {
    const fn = item.fundName?.trim();
    if (fn) return fn;
    return symbol;
  }
  if (type === 'CRYPTO') {
    const s = symbol.trim();
    return s ? s.toUpperCase() : '-';
  }
  return symbol;
}

/**
 * En çok yükselen/düşen mini tablo: fon için kod alt satır; altın/emtiada teknik kod gösterme.
 */
export function getWatchlistTopListSubtitle(item) {
  if (!item) return null;
  const t = item.assetType;
  if (t === 'COMMODITY' || t === 'GOLD') return null;
  if (t === 'FUND' && item.fundName?.trim()) {
    const code = (item.symbol ?? '').trim();
    return code || null;
  }
  return null;
}

export function formatPctForChart(n) {
  if (n === null || n === undefined || Number.isNaN(n)) return '-';
  const s = n.toLocaleString('tr-TR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  return `${n >= 0 ? '+' : ''}${s}%`;
}

/**
 * 0–100 arası payı Türkçe yüzde gösterimi (grafik/tablo tutarlılığı; örn. %25, %12,5).
 */
export function formatSharePercent(percentOf100) {
  if (!Number.isFinite(percentOf100)) return '-';
  const x = Math.round(percentOf100 * 10) / 10;
  const str = Number.isInteger(x)
    ? x.toLocaleString('tr-TR')
    : x.toLocaleString('tr-TR', { minimumFractionDigits: 1, maximumFractionDigits: 1 });
  return `%${str}`;
}

export function countSharePercent(count, total) {
  if (!total || total <= 0 || !Number.isFinite(count)) return null;
  return (count / total) * 100;
}

/** Eksen etiketleri (+ işareti yok) */
export function formatPctAxis(n) {
  if (!Number.isFinite(n)) return '';
  return `${n.toLocaleString('tr-TR', { maximumFractionDigits: 2 })}%`;
}

/** Tablo: enstrüman adı sembol ile aynı veya boşsa "-" */
export function formatInstrumentColumn(symbol, name) {
  const s = (symbol ?? '').trim();
  const n = (name ?? '').trim();
  if (!n || n === '-') return '-';
  if (s && n === s) return '-';
  return n;
}

function fmtNum(v, dec = 2) {
  if (v === null || v === undefined || v === '') return '-';
  const n = typeof v === 'string' ? parseFloat(v) : v;
  if (Number.isNaN(n)) return '-';
  return n.toLocaleString('tr-TR', { minimumFractionDigits: dec, maximumFractionDigits: dec });
}

/**
 * İzleme listesi satır fiyatı (WatchlistTable "Son Fiyat" ile uyumlu: FX’te alış/satış).
 */
export function formatWatchlistTablePrice(item) {
  if (!item) return '-';
  if (item.assetType === 'FX') {
    const b = item.buy;
    const s = item.sell;
    if ((b != null && b !== '') || (s != null && s !== '')) {
      return `${fmtNum(b)} / ${fmtNum(s)}`;
    }
  }
  return fmtNum(item.lastPrice);
}

/**
 * Sembol gösterimi: kripto tickers büyük harf (API bazen "xrp" döner; yönlendirme zaten upper ile eşlenir).
 */
export function formatWatchlistSymbolDisplay(item) {
  if (!item) return '-';
  const sym = (item.symbol ?? '').trim();
  if (!sym) return '-';
  if (item.assetType === 'CRYPTO') return sym.toUpperCase();
  return sym;
}
