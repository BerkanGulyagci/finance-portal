/**
 * Portföy verisini UTF-8 (Excel uyumlu BOM) CSV olarak hazırlar ve indirir.
 *
 * Alan ayırıcı olarak noktalı virgül (;) kullanılır — Türkçe Excel virgüllü CSV'yi
 * tek hücrede gösterdiği için. Sayılar tr-TR biçiminde (ondalık virgül).
 */

const ASSET_LABELS = {
  STOCK: 'Hisse',
  CRYPTO: 'Kripto',
  FX: 'Döviz',
  FUND: 'Fon',
  FUTURE: 'Vadeli',
  GOLD: 'Altın',
  COMMODITY: 'Emtia',
  BOND: 'DİBS',
};

/** Türkçe Excel ile uyumlu sütun ayırıcı */
const SEP = ';';

function assetLabel(t) {
  if (t == null) return '';
  return ASSET_LABELS[t] ?? String(t);
}

/**
 * Hücrede ayırıcı, tırnak veya satır sonu varsa RFC 4180 tarzı tırnakla.
 */
function escapeCsvCell(v) {
  if (v === null || v === undefined) return '';
  const s = String(v);
  if (/[;"\n\r]/.test(s)) return `"${s.replace(/"/g, '""')}"`;
  return s;
}

function csvRow(cells) {
  return cells.map(escapeCsvCell).join(SEP);
}

export function safeFilenameBase(name) {
  const raw = (name || 'portfoy').trim() || 'portfoy';
  return raw.replace(/[/\\?%*:|"<>]/g, '-').slice(0, 80);
}

/** Boş değilse Türkçe yerel sayı (ondalık virgül), yuvarlatılmış */
function numTr(v, maxFrac = 2) {
  if (v === null || v === undefined || v === '') return '';
  const n = typeof v === 'number' ? v : parseFloat(v);
  if (!Number.isFinite(n)) return '';
  return n.toLocaleString('tr-TR', {
    minimumFractionDigits: 0,
    maximumFractionDigits: maxFrac,
  });
}

function triggerCsvDownload(filename, csvBody) {
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

function buildHoldingsCsv(portfolio) {
  const lines = [];
  lines.push(csvRow(['Portföy Adı', portfolio.name ?? '']));
  lines.push(csvRow(['Tip', 'Varlık portföyü']));
  lines.push(csvRow(['Para Birimi', portfolio.currency ?? '']));
  lines.push(csvRow(['Toplam Maliyet', numTr(portfolio.totalCost, 2)]));
  lines.push(csvRow(['Piyasa Değeri', numTr(portfolio.totalMarketValue, 2)]));
  lines.push(csvRow(['Gerçekleşmemiş K/Z', numTr(portfolio.totalProfitLoss, 2)]));
  lines.push('');
  lines.push(csvRow(['VARLIKLAR']));
  lines.push(
    csvRow([
      'Sembol',
      'Tür',
      'Miktar',
      'Ortalama Alış',
      'Güncel Fiyat',
      'Toplam Maliyet',
      'Piyasa Değeri',
      'Kâr/Zarar',
      'Para Birimi',
    ])
  );
  for (const h of portfolio.holdings ?? []) {
    lines.push(
      csvRow([
        h.symbol ?? '',
        assetLabel(h.assetType),
        numTr(h.totalQuantity, 6),
        numTr(h.averageCost, 4),
        numTr(h.currentPrice, 4),
        numTr(h.totalCost, 2),
        numTr(h.marketValue, 2),
        numTr(h.profitLoss, 2),
        h.currency ?? '',
      ])
    );
  }
  lines.push('');
  lines.push(csvRow(['İŞLEMLER']));
  lines.push(
    csvRow([
      'Tarih',
      'İşlem',
      'Sembol',
      'Tür',
      'Miktar',
      'Fiyat',
      'Komisyon',
    ])
  );
  for (const t of portfolio.transactions ?? []) {
    const isBuy = t.transactionType === 'BUY';
    lines.push(
      csvRow([
        t.transactionDate ? String(t.transactionDate).replace('T', ' ').slice(0, 19) : '',
        isBuy ? 'ALIŞ' : 'SATIŞ',
        t.symbol ?? '',
        assetLabel(t.assetType),
        numTr(t.quantity, 6),
        numTr(t.price, 4),
        numTr(t.commission, 2),
      ])
    );
  }
  return lines.join('\r\n');
}

/**
 * Varlık portföyü (HOLDINGS) CSV dışa aktarımı.
 * İzleme listesi için pages/portfolio/utils/exportWatchlistCsv kullanın.
 */
export function downloadPortfolioCsv(portfolio) {
  const stamp = new Date().toISOString().slice(0, 10);
  const base = safeFilenameBase(portfolio?.name);
  const body = buildHoldingsCsv(portfolio);
  triggerCsvDownload(`${base}_varlik_${stamp}.csv`, body);
}
