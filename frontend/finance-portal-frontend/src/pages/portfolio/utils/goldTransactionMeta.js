/** Adet bazlı altın — küsuratsız miktar zorunlu */
const PIECE_SYMBOLS = new Set([
  'CEYREK',
  'YARIM',
  'TAM',
  'CUMHUR',
  'ATA',
  'ZIYNET',
]);

const META_BY_SYMBOL = {
  GOLD: {
    category: 'ounce',
    quantityLabel: 'Ons Miktarı',
    priceLabel: 'Ons Fiyatı',
    quantityUnit: 'ons',
    fractionalQty: true,
    infoNote: '1 GOLD = 1 ons altın olarak hesaplanır.',
  },
  GRAM: {
    category: 'gram',
    quantityLabel: 'Gram Miktarı',
    priceLabel: 'Gram Fiyatı',
    quantityUnit: 'gram',
    fractionalQty: true,
  },
  '14AYAR': {
    category: 'bracelet',
    quantityLabel: 'Gram Miktarı',
    priceLabel: 'Gram Fiyatı',
    quantityUnit: 'gram',
    fractionalQty: true,
  },
  AYAR14: {
    category: 'bracelet',
    quantityLabel: 'Gram Miktarı',
    priceLabel: 'Gram Fiyatı',
    quantityUnit: 'gram',
    fractionalQty: true,
  },
  '22AYAR': {
    category: 'bracelet',
    quantityLabel: 'Gram Miktarı',
    priceLabel: 'Gram Fiyatı',
    quantityUnit: 'gram',
    fractionalQty: true,
  },
  AYAR22: {
    category: 'bracelet',
    quantityLabel: 'Gram Miktarı',
    priceLabel: 'Gram Fiyatı',
    quantityUnit: 'gram',
    fractionalQty: true,
  },
  CEYREK: {
    category: 'piece',
    quantityLabel: 'Adet',
    priceLabel: 'Adet Fiyatı',
    quantityUnit: 'adet',
    fractionalQty: false,
  },
  YARIM: {
    category: 'piece',
    quantityLabel: 'Adet',
    priceLabel: 'Adet Fiyatı',
    quantityUnit: 'adet',
    fractionalQty: false,
  },
  TAM: {
    category: 'piece',
    quantityLabel: 'Adet',
    priceLabel: 'Adet Fiyatı',
    quantityUnit: 'adet',
    fractionalQty: false,
  },
  ZIYNET: {
    category: 'piece',
    quantityLabel: 'Adet',
    priceLabel: 'Adet Fiyatı',
    quantityUnit: 'adet',
    fractionalQty: false,
  },
  CUMHUR: {
    category: 'piece',
    quantityLabel: 'Adet',
    priceLabel: 'Adet Fiyatı',
    quantityUnit: 'adet',
    fractionalQty: false,
  },
  ATA: {
    category: 'piece',
    quantityLabel: 'Adet',
    priceLabel: 'Adet Fiyatı',
    quantityUnit: 'adet',
    fractionalQty: false,
  },
};

export function isGoldAssetType(assetType) {
  return String(assetType ?? '').toUpperCase() === 'GOLD';
}

export function getGoldTransactionMeta(symbol) {
  const s = (symbol ?? '').trim().toUpperCase();
  const base = META_BY_SYMBOL[s];
  if (base) {
    return {
      ...base,
      symbol: s,
      floorQty: !base.fractionalQty,
    };
  }
  if (PIECE_SYMBOLS.has(s)) {
    return {
      category: 'piece',
      symbol: s,
      quantityLabel: 'Adet',
      priceLabel: 'Adet Fiyatı',
      quantityUnit: 'adet',
      fractionalQty: false,
      floorQty: true,
    };
  }
  return {
    category: 'unknown',
    symbol: s,
    quantityLabel: 'Miktar',
    priceLabel: 'Fiyat',
    quantityUnit: '',
    fractionalQty: true,
    floorQty: false,
  };
}

/** Alış → satış fiyatı; satış → alış fiyatı; yoksa tek price */
export function resolveInstrumentTransactionPrice(instrument, isBuy) {
  if (!instrument) return null;
  const pick = (v) => {
    if (v == null || v === '') return null;
    const n = typeof v === 'number' ? v : parseFloat(v);
    return Number.isFinite(n) && n > 0 ? n : null;
  };
  if (isBuy) {
    return (
      pick(instrument.sellPrice)
      ?? pick(instrument.ask)
      ?? pick(instrument.price)
    );
  }
  return (
    pick(instrument.buyPrice)
    ?? pick(instrument.bid)
    ?? pick(instrument.price)
  );
}

export function sanitizeGoldQuantityInput(raw, floorQty) {
  const s = String(raw ?? '');
  if (!s) return '';
  if (floorQty) {
    const head = s.split(/[.,]/)[0].replace(/\D/g, '');
    if (head === '') return '';
    const n = parseInt(head, 10);
    if (!Number.isFinite(n) || n < 0) return '';
    return String(n);
  }
  return s;
}

export function isValidGoldQuantity(qty, floorQty) {
  if (!Number.isFinite(qty) || qty <= 0) return false;
  if (floorQty) return Number.isInteger(qty);
  return true;
}

/** Özet / hata metinleri için miktar formatı */
export function formatGoldQuantity(qty, meta) {
  if (qty == null || !Number.isFinite(qty)) return '-';
  if (meta?.floorQty) {
    return qty.toLocaleString('tr-TR', { maximumFractionDigits: 0 });
  }
  const s = qty.toFixed(8).replace(/\.?0+$/, '');
  if (!s.includes('.')) {
    return Number(s).toLocaleString('tr-TR', { maximumFractionDigits: 0 });
  }
  const [intg, frac] = s.split('.');
  const intLoc = Number(intg).toLocaleString('tr-TR', { useGrouping: true });
  return `${intLoc},${frac}`;
}

export function goldQuantitySuffix(meta) {
  if (!meta) return '';
  if (meta.category === 'piece') return 'adet';
  if (meta.category === 'ounce') return 'ons';
  if (meta.category === 'gram' || meta.category === 'bracelet') return 'gram';
  return meta.quantityUnit || '';
}
