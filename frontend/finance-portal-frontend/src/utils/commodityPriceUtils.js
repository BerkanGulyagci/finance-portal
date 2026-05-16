import { parseTrNumber } from './numberFormat';

export const YAHOO_COMMODITY_SYMBOLS = new Set([
  'CL=F', 'BZ=F', 'NG=F', 'HG=F', 'ZW=F', 'ZC=F', 'KC=F', 'CC=F', 'CT=F',
]);

export function isYahooCommoditySymbol(symbol) {
  if (!symbol || symbol.includes(':')) return false;
  return YAHOO_COMMODITY_SYMBOLS.has(String(symbol).trim().toUpperCase());
}

function parseNum(v) {
  if (v == null || v === '') return null;
  if (typeof v === 'number' && Number.isFinite(v)) return v;
  const n = parseTrNumber(v);
  return n != null && Number.isFinite(n) ? n : null;
}

/** Yahoo/Investing uyumlu ana fiyat (genelde USD). USX → /100. */
export function getPrimarySpotPrice(spot) {
  if (!spot) return null;

  const raw = parseNum(spot.rawPrice);
  if (raw != null) {
    if (spot.centConverted || spot.rawCurrency === 'USX') {
      return { value: raw / 100, currency: 'USD', decimals: 4 };
    }
    const cur = spot.rawCurrency || 'USD';
    return { value: raw, currency: cur, decimals: cur === 'USD' ? 2 : 4 };
  }

  const disp = parseNum(spot.displayPrice);
  if (disp != null) {
    const cur = spot.displayCurrency || 'USD';
    return { value: disp, currency: cur, decimals: cur === 'TRY' ? 2 : 4 };
  }

  return null;
}

/** Backend TRY fiyatı — tekrar kur ile çarpılmaz. */
export function getTrySpotPrice(spot) {
  if (!spot || spot.displayCurrency !== 'TRY') return null;
  return parseNum(spot.displayPrice);
}

export function canToggleTry(spot) {
  return getPrimarySpotPrice(spot) != null && getTrySpotPrice(spot) != null;
}

/** Portföy işlemi: otomatik fiyat alanı için TRY. */
export function pickCommoditySpotPriceTry(dto) {
  return getTrySpotPrice(dto);
}

/** Vadeli (Yahoo tarzı sembol): USD ham fiyat. */
export function pickCommoditySpotPriceUsd(dto) {
  const primary = getPrimarySpotPrice(dto);
  return primary?.currency === 'USD' ? primary.value : parseNum(dto?.rawPrice);
}

export function fmtUsd(v, decimals = 2) {
  if (v == null || !Number.isFinite(v)) return '-';
  return v.toLocaleString('en-US', {
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  });
}

export function fmtTry(v, decimals = 2) {
  if (v == null || !Number.isFinite(v)) return '-';
  return v.toLocaleString('tr-TR', {
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  });
}
