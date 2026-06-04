import { describe, it, expect } from 'vitest';
import {
  GOLD_TABS,
  RANGES,
  RANGE_LABELS,
  CANDLE_DISCLAIMER,
  THEORETICAL_DISCLAIMER,
  calcTheoreticalPrice,
  getSpotPrice,
  fmt,
} from '../goldConstants';

describe('GOLD_TABS', () => {
  it('8 altın türü tanımlı ve key listesi beklenen sırada', () => {
    expect(GOLD_TABS).toHaveLength(8);
    expect(GOLD_TABS.map((t) => t.key)).toEqual([
      'ons', 'gram', 'ceyrek', 'yarim', 'cumhuriyet', 'ziynet', '14ayar', '22ayar',
    ]);
  });

  it('her tab zorunlu alanlara sahip ve tipleri doğru', () => {
    for (const tab of GOLD_TABS) {
      expect(typeof tab.key).toBe('string');
      expect(typeof tab.label).toBe('string');
      expect(typeof tab.spotField).toBe('string');
      expect(typeof tab.compareSymbol).toBe('string');
      expect(typeof tab.canCandle).toBe('boolean');
      expect(typeof tab.isTheoretical).toBe('boolean');
      // currency yalnız USD ya da TRY olabilir
      expect(['USD', 'TRY']).toContain(tab.currency);
    }
  });

  it('compareSymbol değerleri benzersiz', () => {
    const symbols = GOLD_TABS.map((t) => t.compareSymbol);
    expect(new Set(symbols).size).toBe(symbols.length);
  });

  it('spotField değerleri benzersiz', () => {
    const fields = GOLD_TABS.map((t) => t.spotField);
    expect(new Set(fields).size).toBe(fields.length);
  });

  it('ons tabı USD, mum çizilebilir ve teorik DEĞİL (grossWeight/fineness null)', () => {
    const ons = GOLD_TABS.find((t) => t.key === 'ons');
    expect(ons.currency).toBe('USD');
    expect(ons.spotField).toBe('onsUsd');
    expect(ons.canCandle).toBe(true);
    expect(ons.isTheoretical).toBe(false);
    expect(ons.grossWeight).toBeNull();
    expect(ons.fineness).toBeNull();
  });

  it('gram tabı TRY, mum çizilebilir, teorik DEĞİL, saf altın (fineness 1.0)', () => {
    const gram = GOLD_TABS.find((t) => t.key === 'gram');
    expect(gram.currency).toBe('TRY');
    expect(gram.canCandle).toBe(true);
    expect(gram.isTheoretical).toBe(false);
    expect(gram.grossWeight).toBe(1);
    expect(gram.fineness).toBeCloseTo(1.0);
  });

  it('teorik (isTheoretical) tablarda mum çizilemez ve ağırlık/ayar sayısaldır', () => {
    // ons + gram dışındaki 6 tür teorik: canCandle=false, sayısal grossWeight/fineness
    const theoretical = GOLD_TABS.filter((t) => t.isTheoretical);
    expect(theoretical).toHaveLength(6);
    for (const tab of theoretical) {
      expect(tab.canCandle).toBe(false);
      expect(tab.currency).toBe('TRY');
      expect(typeof tab.grossWeight).toBe('number');
      expect(typeof tab.fineness).toBe('number');
      expect(tab.grossWeight).toBeGreaterThan(0);
      expect(tab.fineness).toBeGreaterThan(0);
    }
  });

  it('çeyrek altın gramaj/ayar değerleri doğru (1.754 g, 0.9166 saflık)', () => {
    const ceyrek = GOLD_TABS.find((t) => t.key === 'ceyrek');
    expect(ceyrek.grossWeight).toBeCloseTo(1.754);
    expect(ceyrek.fineness).toBeCloseTo(0.9166);
  });

  it('14 ayar bilezik 22 ayardan daha düşük saflığa sahip', () => {
    const a14 = GOLD_TABS.find((t) => t.key === '14ayar');
    const a22 = GOLD_TABS.find((t) => t.key === '22ayar');
    expect(a14.fineness).toBeCloseTo(0.585);
    expect(a22.fineness).toBeCloseTo(0.9166);
    expect(a14.fineness).toBeLessThan(a22.fineness);
  });
});

describe('RANGES', () => {
  it('beklenen 7 aralık doğru sırada', () => {
    expect(RANGES).toEqual(['1D', '1W', '1M', '3M', '1Y', '5Y', 'ALL']);
  });

  it('her RANGES anahtarı için RANGE_LABELS karşılığı var', () => {
    for (const r of RANGES) {
      expect(RANGE_LABELS).toHaveProperty(r);
      expect(typeof RANGE_LABELS[r]).toBe('string');
    }
  });
});

describe('RANGE_LABELS', () => {
  it('Türkçe kısa etiketlere eşler', () => {
    expect(RANGE_LABELS['1D']).toBe('1G');
    expect(RANGE_LABELS['1W']).toBe('1H');
    expect(RANGE_LABELS['1M']).toBe('1A');
    expect(RANGE_LABELS['3M']).toBe('3A');
    expect(RANGE_LABELS['1Y']).toBe('1Y');
    expect(RANGE_LABELS['5Y']).toBe('5Y');
    expect(RANGE_LABELS['ALL']).toBe('Tümü');
  });
});

describe('Uyarı metinleri (disclaimer sabitleri)', () => {
  it('CANDLE_DISCLAIMER boş olmayan, sentetik açılışı açıklayan metin', () => {
    expect(typeof CANDLE_DISCLAIMER).toBe('string');
    expect(CANDLE_DISCLAIMER.length).toBeGreaterThan(0);
    expect(CANDLE_DISCLAIMER).toContain('sentetik');
  });

  it('THEORETICAL_DISCLAIMER teorik referans değer uyarısı içerir', () => {
    expect(typeof THEORETICAL_DISCLAIMER).toBe('string');
    expect(THEORETICAL_DISCLAIMER.length).toBeGreaterThan(0);
    expect(THEORETICAL_DISCLAIMER).toContain('teorik');
  });
});

describe('calcTheoreticalPrice', () => {
  it('baseGram × grossWeight × fineness çarpımını döner', () => {
    // 5000 TL gram × 1.754 g × 0.9166 saflık ≈ çeyrek teorik fiyatı
    expect(calcTheoreticalPrice(5000, 1.754, 0.9166)).toBeCloseTo(5000 * 1.754 * 0.9166);
  });

  it('saf gram (grossWeight=1, fineness=1) baseGram değerini aynen döner', () => {
    expect(calcTheoreticalPrice(2500, 1, 1.0)).toBeCloseTo(2500);
  });

  it('baseGram null → null (erken return)', () => {
    expect(calcTheoreticalPrice(null, 1.754, 0.9166)).toBeNull();
  });

  it('baseGram undefined → null (== null kontrolü)', () => {
    // undefined == null true olduğu için erken return tetiklenir
    expect(calcTheoreticalPrice(undefined, 1, 1)).toBeNull();
  });

  it('baseGram NaN → null', () => {
    expect(calcTheoreticalPrice(NaN, 1, 1)).toBeNull();
  });

  it('baseGram 0 → 0 (null değil, geçerli sayı)', () => {
    // 0 == null false ve isNaN(0) false → 0 * x * y = 0 döner
    expect(calcTheoreticalPrice(0, 1.754, 0.9166)).toBe(0);
  });

  it('grossWeight/fineness NaN ise sonuç NaN olur (yalnız baseGram doğrulanır)', () => {
    // fonksiyon yalnızca baseGram'ı kontrol eder; diğer çarpanlar NaN ise sonuç NaN
    expect(calcTheoreticalPrice(5000, NaN, 0.9166)).toBeNaN();
  });
});

describe('getSpotPrice', () => {
  it('tab.spotField alanındaki değeri sayıya çevirerek döner', () => {
    const spot = { onsUsd: '2350.5', gramGoldTry: 3100.25 };
    expect(getSpotPrice(spot, { spotField: 'onsUsd' })).toBeCloseTo(2350.5);
    expect(getSpotPrice(spot, { spotField: 'gramGoldTry' })).toBeCloseTo(3100.25);
  });

  it('GOLD_TABS tabı ile gerçek alandan fiyat okur', () => {
    const onsTab = GOLD_TABS.find((t) => t.key === 'ons');
    expect(getSpotPrice({ onsUsd: '1980' }, onsTab)).toBe(1980);
  });

  it('spot null/undefined → null', () => {
    expect(getSpotPrice(null, { spotField: 'onsUsd' })).toBeNull();
    expect(getSpotPrice(undefined, { spotField: 'onsUsd' })).toBeNull();
  });

  it('tab null/undefined → null', () => {
    expect(getSpotPrice({ onsUsd: 1 }, null)).toBeNull();
    expect(getSpotPrice({ onsUsd: 1 }, undefined)).toBeNull();
  });

  it('ilgili alan null/yok → null', () => {
    // alan değeri null → v != null false → null
    expect(getSpotPrice({ onsUsd: null }, { spotField: 'onsUsd' })).toBeNull();
    // alan hiç yok → undefined != null false → null
    expect(getSpotPrice({ gramGoldTry: 3 }, { spotField: 'onsUsd' })).toBeNull();
  });

  it('alan değeri 0 → 0 döner (null değil)', () => {
    // 0 != null true → parseFloat(0) = 0
    expect(getSpotPrice({ onsUsd: 0 }, { spotField: 'onsUsd' })).toBe(0);
  });

  it('sayısal olmayan string → NaN (fonksiyon NaN filtrelemez)', () => {
    // v != null true (string boş değil) → parseFloat('abc') = NaN; bu davranış kasıtlı kontrol edilmez
    expect(getSpotPrice({ onsUsd: 'abc' }, { spotField: 'onsUsd' })).toBeNaN();
  });
});

describe('fmt', () => {
  it('varsayılan 2 ondalıkla tr-TR biçimlendirir (binlik nokta, ondalık virgül)', () => {
    expect(fmt(1234.5)).toBe('1.234,50');
    expect(fmt(1980)).toBe('1.980,00');
  });

  it('dec parametresiyle ondalık basamağı ayarlanır', () => {
    expect(fmt(7.123, 1)).toBe('7,1');
    expect(fmt(7, 0)).toBe('7');
    expect(fmt(2350.555, 3)).toBe('2.350,555');
  });

  it('string sayısal değeri parse edip biçimlendirir', () => {
    expect(fmt('3100.25')).toBe('3.100,25');
  });

  it('null/undefined → "-"', () => {
    expect(fmt(null)).toBe('-');
    expect(fmt(undefined)).toBe('-');
  });

  it('boş string → "-"', () => {
    expect(fmt('')).toBe('-');
  });

  it('sayısal olmayan değer (NaN) → "-"', () => {
    expect(fmt('abc')).toBe('-');
    expect(fmt(NaN)).toBe('-');
  });

  it('0 değeri "-" değil, biçimlendirilir', () => {
    // 0 == null false, '' değil, isNaN(parseFloat(0)) false → biçimlenir
    expect(fmt(0)).toBe('0,00');
  });

  it('negatif değeri biçimlendirir', () => {
    expect(fmt(-1234.5)).toBe('-1.234,50');
  });
});
