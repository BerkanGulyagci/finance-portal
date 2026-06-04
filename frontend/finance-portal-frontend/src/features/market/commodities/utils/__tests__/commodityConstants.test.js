import { describe, it, expect } from 'vitest';
import {
  CATEGORY_META,
  CATEGORY_ORDER,
  fmt,
  fmtPct,
  // re-export edilen yardımcılar (commodityPriceUtils'ten gelir, bu modülden de erişilebilmeli)
  fmtTry,
  fmtUsd,
  getPrimarySpotPrice,
  getTrySpotPrice,
  canToggleTry,
  pickCommoditySpotPriceTry,
  pickCommoditySpotPriceUsd,
  isYahooCommoditySymbol,
} from '../commodityConstants';

describe('CATEGORY_META', () => {
  it('üç kategori için de meta tanımı içerir', () => {
    expect(Object.keys(CATEGORY_META).sort()).toEqual(
      ['AGRICULTURE', 'ENERGY', 'INDUSTRIAL_METALS'],
    );
  });

  it('her kategori beklenen Türkçe etiketi taşır', () => {
    expect(CATEGORY_META.ENERGY.label).toBe('Enerji');
    expect(CATEGORY_META.AGRICULTURE.label).toBe('Tarım');
    expect(CATEGORY_META.INDUSTRIAL_METALS.label).toBe('Sanayi Metalleri');
  });

  it('her kategori label/icon/color/bg/border alanlarına sahiptir', () => {
    for (const key of Object.keys(CATEGORY_META)) {
      const meta = CATEGORY_META[key];
      expect(meta).toHaveProperty('label');
      // icon bir lucide-react bileşeni (fonksiyon/obje) — tanımlı olmalı
      expect(meta.icon).toBeDefined();
      expect(typeof meta.color).toBe('string');
      expect(typeof meta.bg).toBe('string');
      expect(typeof meta.border).toBe('string');
    }
  });
});

describe('CATEGORY_ORDER', () => {
  it('üç kategoriyi beklenen sırada listeler', () => {
    expect(CATEGORY_ORDER).toEqual(['ENERGY', 'INDUSTRIAL_METALS', 'AGRICULTURE']);
  });

  it('sıradaki her anahtar CATEGORY_META içinde tanımlıdır', () => {
    for (const key of CATEGORY_ORDER) {
      expect(CATEGORY_META[key]).toBeDefined();
    }
  });
});

describe('fmt', () => {
  it('null/undefined için "-" döner', () => {
    expect(fmt(null)).toBe('-');
    expect(fmt(undefined)).toBe('-');
  });

  it('parse edilemeyen değer için "-" döner', () => {
    expect(fmt('abc')).toBe('-');
    expect(fmt('')).toBe('-');
    expect(fmt(NaN)).toBe('-');
  });

  it('varsayılan 2 ondalıkla en-US formatında gösterir', () => {
    expect(fmt(1234.5)).toBe('1,234.50');
    expect(fmt(0)).toBe('0.00');
  });

  it('dec parametresiyle ondalık basamağı ayarlanır', () => {
    expect(fmt(1234.5, 4)).toBe('1,234.5000');
    expect(fmt(1234.567, 0)).toBe('1,235'); // yuvarlama
  });

  it('negatif sayıları korur', () => {
    expect(fmt(-12.3)).toBe('-12.30');
  });

  it('string sayısal değerleri parseFloat ile çözer (TR formatı DEĞİL)', () => {
    // DİKKAT: burada parseFloat kullanılır, parseTrNumber değil.
    // parseFloat('1.234,56') => 1.234 (virgül ve sonrası atılır), 1234.56 DEĞİL.
    expect(fmt('1.234,56')).toBe('1.23');
    expect(fmt('80.5')).toBe('80.50');
  });
});

describe('fmtPct', () => {
  it('null/undefined için null döner', () => {
    expect(fmtPct(null)).toBeNull();
    expect(fmtPct(undefined)).toBeNull();
  });

  it('parse edilemeyen değer için null döner', () => {
    expect(fmtPct('abc')).toBeNull();
    expect(fmtPct(NaN)).toBeNull();
  });

  it('pozitif değere + öneki ekler', () => {
    expect(fmtPct(2.5)).toEqual({ value: 2.5, text: '+2.50%' });
  });

  it('sıfır >= 0 olduğu için + öneki alır', () => {
    // 0 >= 0 true → '+' öneki
    expect(fmtPct(0)).toEqual({ value: 0, text: '+0.00%' });
  });

  it('negatif değerde + öneki yok (eksi işareti fmt’ten gelir)', () => {
    expect(fmtPct(-1.5)).toEqual({ value: -1.5, text: '-1.50%' });
  });

  it('string sayısal değeri kabul eder', () => {
    expect(fmtPct('3.25')).toEqual({ value: 3.25, text: '+3.25%' });
  });
});

// ── Re-export'ların bu modülden de doğru biçimde dışa aktarıldığını doğrula ──
describe('commodityPriceUtils re-export bağlantısı', () => {
  it('yardımcı fonksiyonların tümü fonksiyon olarak dışa aktarılır', () => {
    expect(typeof fmtTry).toBe('function');
    expect(typeof fmtUsd).toBe('function');
    expect(typeof getPrimarySpotPrice).toBe('function');
    expect(typeof getTrySpotPrice).toBe('function');
    expect(typeof canToggleTry).toBe('function');
    expect(typeof pickCommoditySpotPriceTry).toBe('function');
    expect(typeof pickCommoditySpotPriceUsd).toBe('function');
    expect(typeof isYahooCommoditySymbol).toBe('function');
  });

  it('re-export edilen fonksiyonlar beklenen davranışı korur (smoke)', () => {
    expect(fmtUsd(1234.5)).toBe('1,234.50');
    expect(fmtTry(1234.5)).toBe('1.234,50');
    expect(isYahooCommoditySymbol('CL=F')).toBe(true);
    expect(isYahooCommoditySymbol('SILVER:XAG')).toBe(false);
    expect(getTrySpotPrice({ displayCurrency: 'TRY', displayPrice: 100 })).toBe(100);
  });
});
