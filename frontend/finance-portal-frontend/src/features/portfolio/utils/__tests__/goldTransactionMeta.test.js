import { describe, it, expect } from 'vitest';
import {
  isGoldAssetType,
  getGoldTransactionMeta,
  resolveInstrumentTransactionPrice,
  sanitizeGoldQuantityInput,
  isValidGoldQuantity,
  formatGoldQuantity,
  goldQuantitySuffix,
} from '../goldTransactionMeta';

describe('isGoldAssetType', () => {
  it('GOLD için true (büyük/küçük harf duyarsız)', () => {
    expect(isGoldAssetType('GOLD')).toBe(true);
    expect(isGoldAssetType('gold')).toBe(true);
    expect(isGoldAssetType('Gold')).toBe(true);
  });

  it('GOLD dışındaki tipler için false', () => {
    expect(isGoldAssetType('STOCK')).toBe(false);
    expect(isGoldAssetType('CRYPTO')).toBe(false);
  });

  it('null/undefined/boş → false', () => {
    expect(isGoldAssetType(null)).toBe(false);
    expect(isGoldAssetType(undefined)).toBe(false);
    expect(isGoldAssetType('')).toBe(false);
  });
});

describe('getGoldTransactionMeta', () => {
  it('META tablosundaki ons sembolü (GOLD) tam meta + floorQty=false döner', () => {
    const meta = getGoldTransactionMeta('GOLD');
    expect(meta.category).toBe('ounce');
    expect(meta.symbol).toBe('GOLD');
    expect(meta.quantityUnit).toBe('ons');
    expect(meta.fractionalQty).toBe(true);
    // fractionalQty true → floorQty bunun tersi (false)
    expect(meta.floorQty).toBe(false);
    expect(meta.infoNote).toBe('1 GOLD = 1 ons altın olarak hesaplanır.');
  });

  it('gram sembolü (GRAM) gram kategorisi döner', () => {
    const meta = getGoldTransactionMeta('GRAM');
    expect(meta.category).toBe('gram');
    expect(meta.quantityUnit).toBe('gram');
    expect(meta.floorQty).toBe(false);
  });

  it('bilezik ayar sembolleri bracelet kategorisi döner', () => {
    expect(getGoldTransactionMeta('14AYAR').category).toBe('bracelet');
    expect(getGoldTransactionMeta('AYAR14').category).toBe('bracelet');
    expect(getGoldTransactionMeta('22AYAR').category).toBe('bracelet');
    expect(getGoldTransactionMeta('AYAR22').category).toBe('bracelet');
  });

  it('adet bazlı sembol (CEYREK) piece + floorQty=true döner', () => {
    const meta = getGoldTransactionMeta('CEYREK');
    expect(meta.category).toBe('piece');
    expect(meta.fractionalQty).toBe(false);
    // fractionalQty false → floorQty true
    expect(meta.floorQty).toBe(true);
    expect(meta.quantityLabel).toBe('Adet');
    expect(meta.quantityUnit).toBe('adet');
  });

  it('tüm adet sembolleri piece kategorisi döner', () => {
    for (const sym of ['YARIM', 'TAM', 'ZIYNET', 'CUMHUR', 'ATA']) {
      const meta = getGoldTransactionMeta(sym);
      expect(meta.category).toBe('piece');
      expect(meta.floorQty).toBe(true);
    }
  });

  it('sembolü trim+upper normalize eder', () => {
    const meta = getGoldTransactionMeta('  gold  ');
    expect(meta.symbol).toBe('GOLD');
    expect(meta.category).toBe('ounce');
  });

  it('bilinmeyen sembol → unknown meta (fractionalQty true, floorQty false)', () => {
    const meta = getGoldTransactionMeta('XYZ');
    expect(meta.category).toBe('unknown');
    expect(meta.symbol).toBe('XYZ');
    expect(meta.quantityLabel).toBe('Miktar');
    expect(meta.priceLabel).toBe('Fiyat');
    expect(meta.quantityUnit).toBe('');
    expect(meta.fractionalQty).toBe(true);
    expect(meta.floorQty).toBe(false);
  });

  it('null/undefined/boş → unknown meta (symbol boş)', () => {
    const meta = getGoldTransactionMeta(null);
    expect(meta.category).toBe('unknown');
    expect(meta.symbol).toBe('');
    expect(getGoldTransactionMeta(undefined).category).toBe('unknown');
    expect(getGoldTransactionMeta('').category).toBe('unknown');
  });

  // PIECE_SYMBOLS fallback dalı: META_BY_SYMBOL'da olmayıp PIECE_SYMBOLS'da olan
  // bir sembol gerekir; ancak tüm PIECE_SYMBOLS META'da da var, bu dal pratikte
  // erişilemez. Yine de piece sonucunun tutarlı olduğunu doğruluyoruz.
  it('PIECE_SYMBOLS içindeki semboller piece sonucu üretir', () => {
    expect(getGoldTransactionMeta('ATA').category).toBe('piece');
    expect(getGoldTransactionMeta('ZIYNET').category).toBe('piece');
  });
});

describe('resolveInstrumentTransactionPrice', () => {
  it('instrument null/undefined → null', () => {
    expect(resolveInstrumentTransactionPrice(null, true)).toBe(null);
    expect(resolveInstrumentTransactionPrice(undefined, false)).toBe(null);
  });

  it('alışta (isBuy) sellPrice önceliklidir', () => {
    const inst = { sellPrice: 100, ask: 90, buyPrice: 80, bid: 70, price: 60 };
    expect(resolveInstrumentTransactionPrice(inst, true)).toBe(100);
  });

  it('alışta sellPrice yoksa ask, o da yoksa price', () => {
    expect(resolveInstrumentTransactionPrice({ ask: 90, price: 60 }, true)).toBe(90);
    expect(resolveInstrumentTransactionPrice({ price: 60 }, true)).toBe(60);
  });

  it('satışta (!isBuy) buyPrice önceliklidir', () => {
    const inst = { sellPrice: 100, ask: 90, buyPrice: 80, bid: 70, price: 60 };
    expect(resolveInstrumentTransactionPrice(inst, false)).toBe(80);
  });

  it('satışta buyPrice yoksa bid, o da yoksa price', () => {
    expect(resolveInstrumentTransactionPrice({ bid: 70, price: 60 }, false)).toBe(70);
    expect(resolveInstrumentTransactionPrice({ price: 60 }, false)).toBe(60);
  });

  it('string sayısal değerleri parse eder', () => {
    expect(resolveInstrumentTransactionPrice({ sellPrice: '123.5' }, true)).toBeCloseTo(123.5);
  });

  it('0, negatif, boş ve geçersiz fiyatları atlar (sonraki alana düşer)', () => {
    // sellPrice=0 geçersiz → ask=90'a düşer
    expect(resolveInstrumentTransactionPrice({ sellPrice: 0, ask: 90 }, true)).toBe(90);
    // ask negatif → price'a düşer
    expect(resolveInstrumentTransactionPrice({ sellPrice: '', ask: -5, price: 60 }, true)).toBe(60);
    // hepsi geçersiz → null
    expect(resolveInstrumentTransactionPrice({ sellPrice: 0, ask: null, price: 'abc' }, true)).toBe(null);
  });

  it('hiçbir fiyat alanı yoksa → null', () => {
    expect(resolveInstrumentTransactionPrice({}, true)).toBe(null);
    expect(resolveInstrumentTransactionPrice({}, false)).toBe(null);
  });
});

describe('sanitizeGoldQuantityInput', () => {
  it('null/undefined/boş → boş string', () => {
    expect(sanitizeGoldQuantityInput(null, false)).toBe('');
    expect(sanitizeGoldQuantityInput(undefined, true)).toBe('');
    expect(sanitizeGoldQuantityInput('', true)).toBe('');
  });

  it('floorQty=false girdiyi olduğu gibi (string) döner', () => {
    expect(sanitizeGoldQuantityInput('12.75', false)).toBe('12.75');
    expect(sanitizeGoldQuantityInput('abc', false)).toBe('abc');
  });

  it('floorQty=true ondalık kısmı atar, tam sayı döner', () => {
    expect(sanitizeGoldQuantityInput('12.7', true)).toBe('12');
    expect(sanitizeGoldQuantityInput('12,7', true)).toBe('12');
  });

  it('floorQty=true sayı dışı karakterleri temizler', () => {
    expect(sanitizeGoldQuantityInput('1a2b3', true)).toBe('123');
  });

  it('floorQty=true baştaki sıfırları normalize eder', () => {
    expect(sanitizeGoldQuantityInput('007', true)).toBe('7');
  });

  it('floorQty=true sadece ondalık/sayısız → boş', () => {
    expect(sanitizeGoldQuantityInput('.5', true)).toBe('');
    expect(sanitizeGoldQuantityInput('abc', true)).toBe('');
  });
});

describe('isValidGoldQuantity', () => {
  it('pozitif ve sonlu olmayan değerler geçersiz', () => {
    expect(isValidGoldQuantity(NaN, false)).toBe(false);
    expect(isValidGoldQuantity(Infinity, false)).toBe(false);
    expect(isValidGoldQuantity(0, false)).toBe(false);
    expect(isValidGoldQuantity(-3, false)).toBe(false);
  });

  it('floorQty=false: pozitif ondalık geçerli', () => {
    expect(isValidGoldQuantity(2.5, false)).toBe(true);
    expect(isValidGoldQuantity(1, false)).toBe(true);
  });

  it('floorQty=true: yalnız tam sayı geçerli', () => {
    expect(isValidGoldQuantity(3, true)).toBe(true);
    expect(isValidGoldQuantity(3.5, true)).toBe(false);
  });
});

describe('formatGoldQuantity', () => {
  it('null/undefined/sonsuz/NaN → "-"', () => {
    expect(formatGoldQuantity(null, {})).toBe('-');
    expect(formatGoldQuantity(undefined, {})).toBe('-');
    expect(formatGoldQuantity(NaN, {})).toBe('-');
    expect(formatGoldQuantity(Infinity, {})).toBe('-');
  });

  it('floorQty meta: ondalıksız (tr-TR) binlik formatlar', () => {
    expect(formatGoldQuantity(1234, { floorQty: true })).toBe('1.234');
    // ondalık varsa bile floor → ondalık atılır
    expect(formatGoldQuantity(1234.9, { floorQty: true })).toBe('1.235');
  });

  it('meta yoksa/fractional: tam sayıyı binlikle gösterir (ondalık yok)', () => {
    // 1000.toFixed(8)="1000.00000000" → trailing 0 silinir → "1000" → nokta yok dalı
    expect(formatGoldQuantity(1000, null)).toBe('1.000');
    expect(formatGoldQuantity(5, { floorQty: false })).toBe('5');
  });

  it('fractional: ondalıklı değeri "1.234,5" biçiminde gösterir', () => {
    // intg binlik nokta + ondalık virgül
    expect(formatGoldQuantity(1234.5, { floorQty: false })).toBe('1.234,5');
  });

  it('fractional: küçük ondalık değer için sondaki sıfırları kırpar', () => {
    // 2.5.toFixed(8)="2.50000000" → "2.5"
    expect(formatGoldQuantity(2.5, { floorQty: false })).toBe('2,5');
  });
});

describe('goldQuantitySuffix', () => {
  it('meta yoksa → boş string', () => {
    expect(goldQuantitySuffix(null)).toBe('');
    expect(goldQuantitySuffix(undefined)).toBe('');
  });

  it('piece → "adet"', () => {
    expect(goldQuantitySuffix({ category: 'piece' })).toBe('adet');
  });

  it('ounce → "ons"', () => {
    expect(goldQuantitySuffix({ category: 'ounce' })).toBe('ons');
  });

  it('gram ve bracelet → "gram"', () => {
    expect(goldQuantitySuffix({ category: 'gram' })).toBe('gram');
    expect(goldQuantitySuffix({ category: 'bracelet' })).toBe('gram');
  });

  it('bilinmeyen kategori → quantityUnit fallback', () => {
    expect(goldQuantitySuffix({ category: 'unknown', quantityUnit: 'birim' })).toBe('birim');
    // quantityUnit yoksa boş string
    expect(goldQuantitySuffix({ category: 'unknown' })).toBe('');
  });
});
