import { describe, it, expect } from 'vitest';
import { isUsdViop, viopCurrency, viopCurrencySymbol } from '../viopCurrency';

describe('isUsdViop', () => {
  it('çapraz pariteleri (EURUSD/GBPUSD) USD kote tanır', () => {
    expect(isUsdViop('EURUSD')).toBe(true);
    expect(isUsdViop('GBPUSD')).toBe(true);
  });

  it('yabancı ons madenlerini (XAUUSD/XAGUSD/XPTUSD/XPDUSD/XCUUSD) USD kote tanır', () => {
    expect(isUsdViop('XAUUSD')).toBe(true);
    expect(isUsdViop('XAGUSD')).toBe(true);
    expect(isUsdViop('XPTUSD')).toBe(true);
    expect(isUsdViop('XPDUSD')).toBe(true);
    expect(isUsdViop('XCUUSD')).toBe(true);
  });

  it('TL kote kontratları (USDTRY/EURTRY/XU030/hisse) USD saymaz', () => {
    expect(isUsdViop('USDTRY')).toBe(false);
    expect(isUsdViop('EURTRY')).toBe(false);
    expect(isUsdViop('XU030')).toBe(false);
    expect(isUsdViop('SASA')).toBe(false);
  });

  it('"F_" sembol önekini soyar', () => {
    expect(isUsdViop('F_XAUUSD0626')).toBe(true);
    expect(isUsdViop('F_USDTRY0626')).toBe(false);
  });

  it('parantezli/boşluklu kontrat adından dayanağı çıkarır', () => {
    expect(isUsdViop('EURUSD (30 Haz 26) Vadeli')).toBe(true);
    expect(isUsdViop('XAUUSD (30 Haz 26)')).toBe(true);
    expect(isUsdViop('USDTRY (30 Haz 26) Vadeli')).toBe(false);
  });

  it('büyük/küçük harf farkını yok sayar', () => {
    expect(isUsdViop('eurusd')).toBe(true);
    expect(isUsdViop('xauusd (30 haz 26)')).toBe(true);
  });

  it('baştaki/sondaki boşluğu kırpar', () => {
    expect(isUsdViop('  EURUSD  ')).toBe(true);
  });

  it('null/undefined/boş için false döner (güvenli)', () => {
    expect(isUsdViop(null)).toBe(false);
    expect(isUsdViop(undefined)).toBe(false);
    expect(isUsdViop('')).toBe(false);
  });
});

describe('viopCurrency', () => {
  it('USD kote → "USD"', () => {
    expect(viopCurrency('EURUSD')).toBe('USD');
    expect(viopCurrency('XAUUSD (30 Haz 26)')).toBe('USD');
  });

  it('TL kote → "TRY"', () => {
    expect(viopCurrency('USDTRY')).toBe('TRY');
    expect(viopCurrency('XU030')).toBe('TRY');
  });

  it('bilinmeyen/boş → varsayılan "TRY"', () => {
    expect(viopCurrency('')).toBe('TRY');
    expect(viopCurrency(null)).toBe('TRY');
    expect(viopCurrency('BILINMEYEN')).toBe('TRY');
  });
});

describe('viopCurrencySymbol', () => {
  it('USD kote → "$"', () => {
    expect(viopCurrencySymbol('GBPUSD')).toBe('$');
    expect(viopCurrencySymbol('F_XPTUSD0626')).toBe('$');
  });

  it('TL kote → "₺"', () => {
    expect(viopCurrencySymbol('USDTRY')).toBe('₺');
    expect(viopCurrencySymbol('SASA (30 Haz 26) Vadeli')).toBe('₺');
  });

  it('boş/null → varsayılan "₺"', () => {
    expect(viopCurrencySymbol(null)).toBe('₺');
    expect(viopCurrencySymbol('')).toBe('₺');
  });
});
