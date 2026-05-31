/**
 * VİOP pozisyonu için saf hesap fonksiyonları. Backend `ViopValuationService`'in
 * frontend ikizi — `AddTransactionModal` canlı önizlemesi için kullanılır.
 *
 * Tüm fonksiyonlar saf: aynı girdiler → aynı çıktılar, side-effect yok.
 *
 * Formüller (yön LONG=+1, SHORT=−1):
 *   notional      = qty × price × multiplier
 *   marginPosted  = qty × avgEntry × multiplier × marginRate
 *   pnl           = qty × (current − avgEntry) × multiplier × directionSign
 *   leverage      = notional / marginPosted
 */

/** Yön çarpanı: LONG → +1, SHORT → -1. Null/boş → +1 (default LONG). */
export function directionSign(direction) {
  if (!direction) return 1;
  return String(direction).trim().toUpperCase() === 'SHORT' ? -1 : 1;
}

/** Nominal pozisyon büyüklüğü = qty × güncel fiyat × multiplier. */
export function notional(qty, price, multiplier) {
  const q = numOrZero(qty), p = numOrZero(price), m = numOrOne(multiplier);
  return q * p * m;
}

/** Başlangıç teminatı = qty × giriş × multiplier × marginRate. */
export function marginPosted(qty, avgEntry, multiplier, marginRate) {
  const q = numOrZero(qty), e = numOrZero(avgEntry), m = numOrOne(multiplier), r = numOrZero(marginRate);
  return q * e * m * r;
}

/** Kâr/zarar = (güncel − giriş) × qty × multiplier × yön. */
export function pnl(qty, avgEntry, currentPrice, multiplier, direction) {
  const q = numOrZero(qty), e = numOrZero(avgEntry), c = numOrZero(currentPrice), m = numOrOne(multiplier);
  return (c - e) * q * m * directionSign(direction);
}

/** Kaldıraç = notional / margin. Margin 0 ise 0 döner. */
export function leverage(notionalVal, marginVal) {
  const n = numOrZero(notionalVal), m = numOrZero(marginVal);
  if (m === 0) return 0;
  return n / m;
}

function numOrZero(v) {
  const n = typeof v === 'number' ? v : parseFloat(v);
  return Number.isFinite(n) ? n : 0;
}

function numOrOne(v) {
  const n = typeof v === 'number' ? v : parseFloat(v);
  return Number.isFinite(n) && n !== 0 ? n : 1;
}
