// VİOP kontratının fiyat para birimini SEMBOLDEN türetir.
//
// Çoğu VİOP TL kotedir (USDTRY, EURTRY, XU030, hisseler…). AMA bazıları USD kotedir:
//   - Çapraz parite: EURUSD, GBPUSD (fiyat = 1.16 gibi dolar paritesi)
//   - Yabancı ons maden: XAUUSD, XAGUSD, XPTUSD, XPDUSD, XCUUSD (fiyat = ons doları)
// Bu kontratların fiyatı USD'dir; "₺" göstermek YANLIŞ (1.1647 ₺ değil, 1.1647 USD parite).
//
// Backend spec.currency portfolio katmanında olduğundan ve liste market katmanında
// üretildiğinden, burada sembolden deterministik türetilir (tek doğruluk kaynağı).

// USD-kote VİOP dayanak kodları (vade öncesi). Sonu "USD" olanlar.
const USD_VIOP_CODES = new Set([
  'EURUSD', 'GBPUSD',
  'XAUUSD', 'XAGUSD', 'XPTUSD', 'XPDUSD', 'XCUUSD',
]);

/**
 * Kontrat adı/sembolünden dayanak kodunu çıkarır.
 * "EURUSD (30 Haz 26) Vadeli" → "EURUSD", "F_XAUUSD0626" → "XAUUSD".
 */
function underlyingOf(nameOrSymbol) {
  if (!nameOrSymbol) return '';
  let s = String(nameOrSymbol).trim().toUpperCase();
  if (s.startsWith('F_')) s = s.slice(2);
  // Baştaki harf bloğunu al (rakam/boşluk/parantez öncesi)
  const m = s.match(/^[A-Z]+/);
  return m ? m[0] : s;
}

/** Bu VİOP kontratı USD kote mi? (sonu USD olan parite/ons maden) */
export function isUsdViop(nameOrSymbol) {
  return USD_VIOP_CODES.has(underlyingOf(nameOrSymbol));
}

/** VİOP kontratının para birimi kodu: 'USD' | 'TRY'. */
export function viopCurrency(nameOrSymbol) {
  return isUsdViop(nameOrSymbol) ? 'USD' : 'TRY';
}

/** Para birimi sembolü: USD → '$', TRY → '₺'. */
export function viopCurrencySymbol(nameOrSymbol) {
  return isUsdViop(nameOrSymbol) ? '$' : '₺';
}
