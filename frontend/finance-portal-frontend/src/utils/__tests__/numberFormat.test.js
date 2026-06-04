import { describe, it, expect } from 'vitest';
import {
  parseTrNumber,
  formatTrNumber,
  formatPrice,
  computeKlinePricePrecision,
  computeKlineVolumePrecision,
  formatQuantity,
} from '../numberFormat';

describe('parseTrNumber', () => {
  it('null/undefined/boş için null döner', () => {
    expect(parseTrNumber(null)).toBeNull();
    expect(parseTrNumber(undefined)).toBeNull();
    expect(parseTrNumber('')).toBeNull();
    expect(parseTrNumber('   ')).toBeNull();
    expect(parseTrNumber('-')).toBeNull();
  });

  it('zaten sayı olan (JSON BigDecimal) değeri olduğu gibi döner', () => {
    expect(parseTrNumber(55.91)).toBe(55.91);
    expect(parseTrNumber(0)).toBe(0);
    expect(parseTrNumber(-12.5)).toBe(-12.5);
  });

  it('Infinity/NaN number girdiyi string yoluna düşürür', () => {
    // Number.isFinite false → String()'e çevrilir. "NaN" → Number("NaN")=NaN → null.
    // "Infinity" → Number("Infinity")=Infinity (geçerli sayı) → Infinity döner.
    expect(parseTrNumber(NaN)).toBeNull();
    expect(parseTrNumber(Infinity)).toBe(Infinity);
  });

  it('Türkçe formatı doğru parse eder', () => {
    expect(parseTrNumber('5.734,00')).toBe(5734);
    expect(parseTrNumber('77.813')).toBe(77813);
    expect(parseTrNumber('95,45')).toBe(95.45);
    expect(parseTrNumber('-0,88')).toBe(-0.88);
    expect(parseTrNumber('146.129,00')).toBe(146129);
  });

  it('baştaki/sondaki boşlukları trimler', () => {
    expect(parseTrNumber('  95,45  ')).toBe(95.45);
  });

  it('geçersiz string için null döner', () => {
    expect(parseTrNumber('abc')).toBeNull();
    expect(parseTrNumber('12,34,56xx')).toBeNull();
  });
});

describe('formatTrNumber', () => {
  it('null/undefined/NaN için "-" döner', () => {
    expect(formatTrNumber(null)).toBe('-');
    expect(formatTrNumber(undefined)).toBe('-');
    expect(formatTrNumber(NaN)).toBe('-');
  });

  it('binlik ayracı olarak nokta, ondalık olarak virgül kullanır', () => {
    expect(formatTrNumber(146129)).toBe('146.129');
    expect(formatTrNumber(95.45)).toBe('95,45');
  });

  it('maximumFractionDigits parametresini uygular', () => {
    expect(formatTrNumber(1.23456, 3)).toBe('1,235');
    expect(formatTrNumber(1.23456, 0)).toBe('1');
  });

  it('0 değerini doğru gösterir', () => {
    expect(formatTrNumber(0)).toBe('0');
  });
});

describe('formatPrice', () => {
  it('2 ondalık ile formatlar', () => {
    expect(formatPrice(1234.5)).toBe('1.234,5');
    expect(formatPrice(null)).toBe('-');
  });
});

describe('formatQuantity', () => {
  it('ondalıksız formatlar', () => {
    expect(formatQuantity(1234.9)).toBe('1.235');
    expect(formatQuantity(null)).toBe('-');
  });
});

describe('computeKlinePricePrecision', () => {
  it('dizi olmayan/boş girdi için 2 döner', () => {
    expect(computeKlinePricePrecision(null)).toBe(2);
    expect(computeKlinePricePrecision([])).toBe(2);
    expect(computeKlinePricePrecision('xyz')).toBe(2);
    expect(computeKlinePricePrecision([0, -1, NaN])).toBe(2); // pozitif değer yok
  });

  it('min >= 100 için 2 döner', () => {
    expect(computeKlinePricePrecision([100, 250, 999])).toBe(2);
  });

  it('1 <= min < 100 için 4 döner', () => {
    expect(computeKlinePricePrecision([5, 80])).toBe(4);
    expect(computeKlinePricePrecision([1, 2])).toBe(4);
  });

  it('0.01 <= min < 1 için 4 döner', () => {
    expect(computeKlinePricePrecision([0.5, 0.9])).toBe(4);
  });

  it('mikro fiyatlar için artan hassasiyet (4..12 arası) döner', () => {
    const p = computeKlinePricePrecision([0.00001]);
    expect(p).toBeGreaterThanOrEqual(4);
    expect(p).toBeLessThanOrEqual(12);
    // 0.00001 → exp = -5 → -(-5)+3 = 8
    expect(p).toBe(8);
  });

  it('string sayıları da kabul eder', () => {
    expect(computeKlinePricePrecision(['150', '300'])).toBe(2);
  });
});

describe('computeKlineVolumePrecision', () => {
  it('dizi olmayan/boş girdi için 2 döner', () => {
    expect(computeKlineVolumePrecision(null)).toBe(2);
    expect(computeKlineVolumePrecision([])).toBe(2);
    expect(computeKlineVolumePrecision([0, -3])).toBe(2);
  });

  it('max >= 1.000.000 için 0 döner', () => {
    expect(computeKlineVolumePrecision([5_000_000, 10])).toBe(0);
  });

  it('1 <= max < 1.000.000 için 2 döner', () => {
    expect(computeKlineVolumePrecision([1, 999])).toBe(2);
  });

  it('max < 1 için artan hassasiyet (2..8 arası) döner', () => {
    const p = computeKlineVolumePrecision([0.001]);
    expect(p).toBeGreaterThanOrEqual(2);
    expect(p).toBeLessThanOrEqual(8);
    // 0.001 → exp = -3 → -(-3)+2 = 5
    expect(p).toBe(5);
  });
});
