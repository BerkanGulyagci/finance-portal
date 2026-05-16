/**
 * İzleme listesi: tek şemalı CSV (tüm varlık türleri aynı kolon sırası).
 * UTF-8 BOM ile Excel uyumu; her alan çift tırnak içinde; virgül ayırıcı.
 */

const CATEGORY_LABEL = {
  STOCK: 'Hisse',
  CRYPTO: 'Kripto',
  FUND: 'Fon',
  COMMODITY: 'Emtia',
  GOLD: 'Altın',
  FUTURE: 'Vadeli',
  FX: 'Döviz',
  BOND: 'DİBS',
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
  GRAM: 'Gram Altın',
  CEYREK: 'Çeyrek Altın',
  YARIM: 'Yarım Altın',
  ATA: 'Ata Lira (Cumhuriyet)',
  CUMHUR: 'Ata Lira (Cumhuriyet)',
  TAM: 'Tam Altın (Ziynet)',
  ZIYNET: 'Tam Altın (Ziynet)',
  GOLD: 'Ons Altın',
  '14AYAR': '14 Ayar Bilezik',
  '22AYAR': '22 Ayar Bilezik',
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

export const WATCHLIST_CSV_HEADERS = [
  'Kategori',
  'Sembol',
  'Ad',
  'Tür',
  'Son Fiyat',
  'Alış',
  'Satış',
  'Eklendiği Fiyat',
  'Açılış',
  'Yüksek',
  'Düşük',
  'Fark',
  'Fark %',
  'Hacim',
  'Günlük Getiri',
  '1 Ay Getiri',
  '3 Ay Getiri',
  'YBG',
  '1 Yıl Getiri',
  'Risk',
  'Değer',
  'Günlük Fark',
  'Günlük Fark %',
  'Kalan Gün',
  'Kupon %',
  'Not',
  'Eklenme Tarihi',
];

function quoteField(v) {
  if (v === null || v === undefined) return '""';
  return `"${String(v).replace(/"/g, '""')}"`;
}

function csvLine(values) {
  return values.map(quoteField).join(',');
}

function fmtNum(v, maxFrac = 2) {
  if (v === null || v === undefined || v === '') return '';
  const n = typeof v === 'number' ? v : parseFloat(v);
  if (!Number.isFinite(n)) return '';
  return n.toLocaleString('tr-TR', {
    minimumFractionDigits: 0,
    maximumFractionDigits: maxFrac,
  });
}

function fmtAddedAt(v) {
  if (!v) return '';
  return String(v).replace('T', ' ').slice(0, 16);
}

function titleForItem(item) {
  if (!item) return '';
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
  return symbol;
}

function emptyRowTemplate() {
  return Array(WATCHLIST_CSV_HEADERS.length).fill('');
}

/**
 * Tek izleme kalemi → kolon sırasına göre ham string dizisi (27 öğe).
 */
export function watchlistItemToCsvValues(item) {
  const row = emptyRowTemplate();
  const t = item.assetType;
  row[0] = CATEGORY_LABEL[t] ?? '';
  row[1] = item.symbol ?? '';
  row[2] = titleForItem(item);
  row[3] = t === 'FUND' && item.fundType?.trim() ? item.fundType.trim() : '';
  row[25] = item.notes ?? '';
  row[26] = fmtAddedAt(item.addedAt);

  switch (t) {
    case 'STOCK':
    case 'CRYPTO':
    case 'GOLD':
    case 'COMMODITY':
    case 'FUTURE':
      row[4] = fmtNum(item.lastPrice, 6);
      row[7] = fmtNum(item.startPrice, 4);
      row[8] = fmtNum(item.open, 4);
      row[9] = fmtNum(item.high, 4);
      row[10] = fmtNum(item.low, 4);
      row[11] = fmtNum(item.change, 4);
      row[12] = fmtNum(item.changePercent, 2);
      row[13] = item.volume != null && item.volume !== '' ? String(item.volume) : '';
      row[14] = fmtNum(item.changePercent, 2);
      break;
    case 'FUND':
      row[4] = fmtNum(item.lastPrice, 6);
      row[14] = fmtNum(item.changePercent, 2);
      row[15] = fmtNum(item.fundReturnOneMonth, 2);
      row[16] = fmtNum(item.fundReturnThreeMonths, 2);
      row[17] = fmtNum(item.fundReturnYtd, 2);
      row[18] = fmtNum(item.fundReturnOneYear, 2);
      row[19] = item.fundRiskLevel != null ? String(item.fundRiskLevel) : '';
      break;
    case 'FX':
      row[5] = fmtNum(item.buy, 4);
      row[6] = fmtNum(item.sell, 4);
      row[7] = fmtNum(item.startPrice, 4);
      break;
    case 'BOND':
      row[20] = fmtNum(item.lastPrice, 4);
      row[7] = fmtNum(item.startPrice, 4);
      row[21] = fmtNum(item.change, 4);
      row[22] = fmtNum(item.changePercent, 2);
      row[23] = item.remainingDays != null ? String(item.remainingDays) : '';
      row[24] = fmtNum(item.couponRate, 2);
      break;
    default:
      break;
  }

  return row;
}

export function buildWatchlistCsvContent(items) {
  const lines = [];
  lines.push(csvLine(WATCHLIST_CSV_HEADERS));
  for (const item of items ?? []) {
    lines.push(csvLine(watchlistItemToCsvValues(item)));
  }
  return lines.join('\r\n');
}

function safeFilenamePart(name) {
  const raw = (name || 'watchlist').trim() || 'watchlist';
  return raw.replace(/[/\\?%*:|"<>]/g, '-').replace(/\s+/g, '_').slice(0, 80);
}

function triggerDownload(filename, csvBody) {
  const blob = new Blob(['\uFEFF', csvBody], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename.endsWith('.csv') ? filename : `${filename}.csv`;
  a.rel = 'noopener';
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}

/**
 * @param {string} portfolioName
 * @param {object[]} items — WatchlistItemResponse[]
 */
export function downloadWatchlistCsv(portfolioName, items) {
  const stamp = new Date().toISOString().slice(0, 10);
  const part = safeFilenamePart(portfolioName);
  const body = buildWatchlistCsvContent(items);
  triggerDownload(`watchlist-${part}-${stamp}.csv`, body);
}
