/**
 * COMMODITY sembollerinden birimi çıkarır. Yahoo emtia için backend DTO'da
 * unit alanı (`instrument.commoditySpot.unit`) zaten geliyor; bu utility hem
 * o değeri preferred kaynak olarak alır hem de BIST kıymetli madenler için
 * sembol-bazlı fallback sağlar (commoditySpot null geldiğinde).
 *
 * Backend CommoditySymbol enum'ı ile birebir uyumlu — yeni emtia eklendiğinde
 * burası da güncellenmeli.
 */

// Yahoo emtia sembol → birim (CommoditySymbol enum ile uyumlu)
const YAHOO_UNIT_MAP = {
  'CL=F': 'Varil',
  'BZ=F': 'Varil',
  'NG=F': 'MMBtu',
  'HG=F': 'Pound',
  'ZW=F': 'Bushel',
  'ZC=F': 'Bushel',
  'KC=F': 'Pound',
  'CC=F': 'Ton',
  'CT=F': 'Pound',
};

// BIST kıymetli maden sembol kategorisi → birim
const BIST_CAT_UNIT_MAP = {
  GRAM_TRY: 'Gram',
  KG_TRY: 'Kg',
  USD_ONS: 'Ons',
  EUR_ONS: 'Ons',
};

/**
 * @param {string} symbol     Holding/instrument sembolü (örn. "NG=F", "SILVER:GRAM_TRY")
 * @param {object} [spotMeta] Opsiyonel: instrument.commoditySpot — Yahoo backend DTO
 * @returns {string|null} Birim (Varil/MMBtu/...). Bilinmiyorsa null.
 */
export function getCommodityUnit(symbol, spotMeta) {
  if (!symbol) return null;
  if (spotMeta && spotMeta.unit) return spotMeta.unit;

  const upper = String(symbol).toUpperCase();
  if (YAHOO_UNIT_MAP[upper]) return YAHOO_UNIT_MAP[upper];

  if (upper.includes(':')) {
    const cat = upper.split(':', 2)[1];
    if (BIST_CAT_UNIT_MAP[cat]) return BIST_CAT_UNIT_MAP[cat];
  }
  return null;
}
