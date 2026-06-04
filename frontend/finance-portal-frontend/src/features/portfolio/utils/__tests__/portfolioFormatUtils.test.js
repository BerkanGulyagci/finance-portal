import { describe, it, expect } from 'vitest';
import {
  MASK_MONEY,
  MASK_PERCENT,
  MASK_QTY,
  formatMoney,
  formatPercent,
  formatChartValue,
} from '../portfolioFormatUtils';

// Not: '../../../utils/numberFormat' (formatTrNumber) SAF olduğu için mock'lanmadı,
// gerçek TR formatlama davranışı baz alınarak assert edildi.

describe('mask sabitleri', () => {
  it('beklenen maske string değerlerini içerir', () => {
    expect(MASK_MONEY).toBe('•••••');
    expect(MASK_PERCENT).toBe('••••');
    expect(MASK_QTY).toBe('••••');
  });
});

describe('formatMoney', () => {
  it('valuesHidden true ise para maskesini döner (değere bakmadan)', () => {
    expect(formatMoney(1234.5, 'TRY', true)).toBe(MASK_MONEY);
    // gizliyken null bile olsa maske döner (null kontrolünden önce)
    expect(formatMoney(null, 'USD', true)).toBe(MASK_MONEY);
  });

  it('null/undefined/sonlu-olmayan değer için "-" döner', () => {
    expect(formatMoney(null, 'TRY', false)).toBe('-');
    expect(formatMoney(undefined, 'TRY', false)).toBe('-');
    expect(formatMoney(NaN, 'TRY', false)).toBe('-');
    expect(formatMoney(Infinity, 'TRY', false)).toBe('-');
    expect(formatMoney('abc', 'TRY', false)).toBe('-');
  });

  it('para birimi verilince sayıdan sonra ekler', () => {
    expect(formatMoney(1234.5, 'TRY', false)).toBe('1.234,5 TRY');
    expect(formatMoney(1234567.89, 'USD', false)).toBe('1.234.567,89 USD');
  });

  it('para birimi yoksa sadece formatlanmış sayıyı döner', () => {
    expect(formatMoney(1234.5, null, false)).toBe('1.234,5');
    expect(formatMoney(1234.5, '', false)).toBe('1.234,5');
    expect(formatMoney(1234.5, undefined, false)).toBe('1.234,5');
  });

  it('formatTrNumber kullandığı için minimum ondalık 0 → tam sayıda virgül yok, gereksiz sıfır eklenmez', () => {
    // 5 → "5" (formatPercent'in aksine "5,00" DEĞİL)
    expect(formatMoney(5, null, false)).toBe('5');
    expect(formatMoney(0, 'TRY', false)).toBe('0 TRY');
  });

  it('negatif ve string sayıyı doğru işler', () => {
    expect(formatMoney(-50.25, 'TRY', false)).toBe('-50,25 TRY');
    // string "12.5" Number()=12.5 olarak parse edilir (JS noktası)
    expect(formatMoney('12.5', null, false)).toBe('12,5');
  });

  it('valuesHidden parametresi yoksa (undefined) gizleme yapmaz', () => {
    expect(formatMoney(100, 'TRY')).toBe('100 TRY');
  });
});

describe('formatPercent', () => {
  it('valuesHidden true ise yüzde maskesini döner', () => {
    expect(formatPercent(12.3, true)).toBe(MASK_PERCENT);
    expect(formatPercent(null, true)).toBe(MASK_PERCENT);
  });

  it('null/undefined/sonlu-olmayan değer için "-" döner', () => {
    expect(formatPercent(null, false)).toBe('-');
    expect(formatPercent(undefined, false)).toBe('-');
    expect(formatPercent(NaN, false)).toBe('-');
    expect(formatPercent(Infinity, false)).toBe('-');
    expect(formatPercent('xx', false)).toBe('-');
  });

  it('toLocaleString kullandığı için minimum ondalık 2 → trailing sıfırlar korunur', () => {
    // formatMoney'nin aksine 5 → "5,00%" (gereksiz sıfırlar GÖSTERİLİR)
    expect(formatPercent(5, false)).toBe('5,00%');
    expect(formatPercent(12.3456, false)).toBe('12,35%');
    expect(formatPercent(0, false)).toBe('0,00%');
  });

  it('0 < |değer| < 0.005 ise 4 ondalık kullanır', () => {
    expect(formatPercent(0.003, false)).toBe('0,0030%');
    expect(formatPercent(-0.001, false)).toBe('-0,0010%');
  });

  it('sınır değer 0.005 (< değil) → 2 ondalığa düşer', () => {
    // abs < 0.005 koşulu 0.005'i KAPSAMAZ
    expect(formatPercent(0.005, false)).toBe('0,01%'); // 0,005 → 2 basamağa yuvarlanır
  });

  it('signed:true ve değer pozitifse "+" öneki ekler', () => {
    expect(formatPercent(2.5, false, { signed: true })).toBe('+2,50%');
  });

  it('signed:true olsa bile sıfır ve negatifte "+" eklenmez', () => {
    // prefix yalnızca n > 0 iken; 0 ve negatif için yok
    expect(formatPercent(0, false, { signed: true })).toBe('0,00%');
    expect(formatPercent(-2.5, false, { signed: true })).toBe('-2,50%');
  });

  it('signed varsayılan false → pozitifte "+" yok', () => {
    expect(formatPercent(2.5, false)).toBe('2,50%');
  });

  it('üçüncü argüman verilmezse varsayılan ({}) ile çalışır', () => {
    expect(formatPercent(3.14, false)).toBe('3,14%');
  });
});

describe('formatChartValue', () => {
  it('valuesHidden true ise para maskesini döner', () => {
    expect(formatChartValue(100, true, 'VALUE')).toBe(MASK_MONEY);
    expect(formatChartValue(100, true, 'GROWTH')).toBe(MASK_MONEY);
  });

  it('null/undefined/sonlu-olmayan değer için "-" döner', () => {
    expect(formatChartValue(null, false, 'VALUE')).toBe('-');
    expect(formatChartValue(undefined, false, 'GROWTH')).toBe('-');
    expect(formatChartValue(NaN, false, 'VALUE')).toBe('-');
    expect(formatChartValue(Infinity, false, 'GROWTH')).toBe('-');
  });

  it('metric GROWTH ise işaretli yüzde olarak biçimler', () => {
    // formatPercent(n, false, { signed:true }) ile aynı çıktı
    expect(formatChartValue(2.5, false, 'GROWTH')).toBe('+2,50%');
    expect(formatChartValue(-2.5, false, 'GROWTH')).toBe('-2,50%');
    expect(formatChartValue(0, false, 'GROWTH')).toBe('0,00%');
  });

  it('metric GROWTH değilse formatTrNumber (2 ondalık) ile biçimler', () => {
    // GROWTH dışındaki tüm metric'ler (VALUE, bilinmeyen, undefined) aynı dalı kullanır
    expect(formatChartValue(1234.5, false, 'VALUE')).toBe('1.234,5');
    expect(formatChartValue(1234.5, false, 'UNKNOWN_METRIC')).toBe('1.234,5');
    expect(formatChartValue(5, false)).toBe('5'); // metric undefined → trailing sıfır yok
  });

  it('GROWTH dalı toLocaleString trailing sıfır davranışını miras alır', () => {
    expect(formatChartValue(5, false, 'GROWTH')).toBe('+5,00%');
  });
});
