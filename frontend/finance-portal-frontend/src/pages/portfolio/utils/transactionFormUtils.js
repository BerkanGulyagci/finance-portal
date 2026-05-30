// İşlem (alış/satış) modalı için saf yardımcılar — AddTransactionModal ve
// TransactionSummary tarafından paylaşılır.

export function extractApiErrorMessage(err) {
  const body = err?.response?.data;
  if (!body) return err?.message || '';
  if (typeof body.message === 'string' && body.message.trim()) return body.message.trim();
  if (body.data && typeof body.data === 'object') {
    const parts = Object.values(body.data).filter(v => typeof v === 'string' && v.trim());
    if (parts.length) return parts.join(' ');
  }
  return '';
}

export function fmtNum(n, dec = 2) {
  if (n == null || !Number.isFinite(n)) return '-';
  return n.toLocaleString('tr-TR', { minimumFractionDigits: dec, maximumFractionDigits: dec });
}

export function fmtWithCcy(n, ccy, dec = 2) {
  const s = fmtNum(n, dec);
  return ccy ? `${s} ${ccy}` : s;
}

export function guessCurrency(assetType, symbol) {
  switch (assetType) {
    case 'STOCK':
      return (symbol ?? '').toUpperCase().endsWith('.IS') ? 'TRY' : 'USD';
    case 'CRYPTO':
    case 'FX':
    case 'FUND':
    case 'BOND':
    case 'GOLD':
    case 'COMMODITY':
      return 'TRY';
    case 'FUTURE':
      return /^[A-Z0-9.=]{1,15}$/i.test(symbol ?? '') ? 'USD' : 'TRY';
    default:
      return '';
  }
}

/**
 * Ham sayısal string ('.' ondalık) → tr-TR gruplu görünüm ('.' binlik, ',' ondalık).
 * Örn: "3285.5" → "3.285,5", "1234" → "1.234", "3285." → "3.285," (yazım sürerken korunur).
 * Sadece input GÖRÜNÜMÜ içindir; form'da değer ham (hesaplamalar bozulmaz) tutulur.
 */
export function formatGroupedInput(raw) {
  if (raw == null || raw === '') return '';
  let s = String(raw);
  const neg = s.startsWith('-');
  if (neg) s = s.slice(1);
  const dot = s.indexOf('.');
  const hasDot = dot >= 0;
  const intRaw = (hasDot ? s.slice(0, dot) : s).replace(/\D/g, '');
  const decRaw = hasDot ? s.slice(dot + 1).replace(/\D/g, '') : '';
  const grouped = intRaw
    ? intRaw.replace(/\B(?=(\d{3})+(?!\d))/g, '.')
    : (hasDot ? '0' : '');
  let out = grouped;
  if (hasDot) out += ',' + decRaw;
  return (neg ? '-' : '') + out;
}

/** Gruplu görünüm → ham sayısal string ('.' ondalık) — form'da saklanan/hesaplanan değer. */
export function parseGroupedInput(display) {
  if (display == null) return '';
  let s = String(display).trim();
  if (s === '') return '';
  const neg = s.startsWith('-');
  s = s.replace(/\./g, '').replace(/,/g, '.').replace(/[^0-9.]/g, '');
  const i = s.indexOf('.');
  if (i >= 0) s = s.slice(0, i + 1) + s.slice(i + 1).replace(/\./g, '');
  return (neg ? '-' : '') + s;
}

/** Para birimi kodu → sembol (₺/$/€/£); bilinmiyorsa kodun kendisi. */
export function currencySymbol(ccy) {
  switch ((ccy || '').toUpperCase()) {
    case 'TRY': return '₺';
    case 'USD': return '$';
    case 'EUR': return '€';
    case 'GBP': return '£';
    default: return ccy || '';
  }
}

export function getShortSymbol(sym) {
  if (!sym) return '';
  const s = sym.toUpperCase();
  const paren = s.indexOf(' (');
  return paren > 0 ? s.slice(0, paren) : s;
}

/**
 * BUY: supports = tutar ile gösterilsin mi, floor = tam adede indir
 * SELL: aynı kural geçerli
 */
export function assetConfig(assetType) {
  const t = String(assetType ?? '').toUpperCase();
  switch (t) {
    case 'STOCK':
      return { supports: true, floor: true };
    case 'CRYPTO':
    case 'FX':
    case 'FUND':
    case 'GOLD':
    case 'COMMODITY':
    case 'BOND':
      return { supports: true, floor: false };
    case 'FUTURE':
    default:
      return { supports: false, floor: false };
  }
}

export function safePositivePrice(p) {
  const n = Number(p);
  return Number.isFinite(n) && n > 0 ? n : null;
}

export function initPrice(p) {
  if (p == null || p === '') return '';
  const n = Number(p);
  return Number.isFinite(n) && n > 0 ? String(p) : '';
}

/** holdings listesinden sembol + assetType eşleşen açık pozisyon miktarını döndürür */
export function findAvailableQty(holdings, symbol, assetType) {
  if (!holdings?.length || !symbol || !assetType) return null;
  const sym = symbol.toUpperCase();
  const match = holdings.find(
    h =>
      (h.symbol ?? '').toUpperCase() === sym &&
      (h.assetType ?? '') === assetType,
  );
  if (!match) return null;
  const n = parseFloat(match.totalQuantity);
  return Number.isFinite(n) && n > 0 ? n : 0;
}

/** Fon: kullanıcı genelde tutar ile işlem yapar → varsayılan "Tutar ile". Diğerleri miktar. */
export function defaultInputMode(assetType) {
  return String(assetType ?? '').toUpperCase() === 'FUND' ? 'amount' : 'quantity';
}

export function isFundAssetType(assetType) {
  return String(assetType ?? '').toUpperCase() === 'FUND';
}

export function isBondAssetType(assetType) {
  return String(assetType ?? '').toUpperCase() === 'BOND';
}

/**
 * Eurobond (Hazine dış borç) enstrümanı mı? Yurt içi DİBS'ten ayırt eder.
 * Açık işaret (subType/isEurobond) varsa onu kullanır; yoksa ISIN sezgisi: tam ISIN
 * biçiminde olup "TR" (yurt içi) ile başlamayan tahviller eurobond'dur (US/XS/DE/JP…).
 * Eurobond fiyatı döviz cinsindendir → modalda otomatik doldurulmaz, TL elle girilir.
 */
export function isEurobondInstrument(inst) {
  if (!inst || !isBondAssetType(inst.assetType)) return false;
  if (inst.subType === 'EUROBOND' || inst.isEurobond === true) return true;
  const s = String(inst.symbol ?? '').trim().toUpperCase();
  return /^[A-Z]{2}[A-Z0-9]{9}[0-9]$/.test(s) && !s.startsWith('TR');
}

export function isFutureAssetType(assetType) {
  return String(assetType ?? '').toUpperCase() === 'FUTURE';
}

/** Vadeli: sadece pozitif tam sayı (küsurat yok). */
export function sanitizeFutureContractQtyInput(raw) {
  const s = String(raw ?? '');
  if (!s) return '';
  const head = s.split(/[.,]/)[0].replace(/\D/g, '');
  if (head === '') return '';
  const n = parseInt(head, 10);
  if (!Number.isFinite(n) || n < 0) return '';
  return String(n);
}
