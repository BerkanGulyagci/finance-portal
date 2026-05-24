// Dashboard ortak biçimlendiriciler.

export function num(v) {
  const n = typeof v === 'number' ? v : parseFloat(v);
  return Number.isFinite(n) ? n : 0;
}

export function fmtMoney(v, { min = 2, max = 2 } = {}) {
  const n = num(v);
  return n.toLocaleString('tr-TR', { minimumFractionDigits: min, maximumFractionDigits: max });
}

export function fmtPct(v, digits = 2) {
  const n = num(v);
  return `${n >= 0 ? '+' : ''}${n.toFixed(digits)}%`;
}

/** Pozitif yeşil, negatif kırmızı, sıfır nötr. */
export function pctClass(v) {
  const n = num(v);
  return n > 0 ? 'text-emerald-600' : n < 0 ? 'text-rose-600' : 'text-gray-500';
}

/** Varlık türü → Türkçe etiket. */
export const ASSET_LABEL = {
  STOCK: 'Hisse', FUND: 'Fon', FX: 'Döviz', FUTURE: 'Vadeli',
  CRYPTO: 'Kripto', GOLD: 'Altın', COMMODITY: 'Emtia', BOND: 'Tahvil',
};
