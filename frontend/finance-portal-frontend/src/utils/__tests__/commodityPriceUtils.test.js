import { describe, it, expect } from 'vitest';
import {
  YAHOO_COMMODITY_SYMBOLS,
  isYahooCommoditySymbol,
  getPrimarySpotPrice,
  getTrySpotPrice,
  canToggleTry,
  pickCommoditySpotPriceTry,
  pickCommoditySpotPriceUsd,
  fmtUsd,
  fmtTry,
} from '../commodityPriceUtils';

describe('isYahooCommoditySymbol', () => {
  it('bilinen Yahoo emtia sembolünü tanır (case-insensitive)', () => {
    expect(isYahooCommoditySymbol('CL=F')).toBe(true);
    expect(isYahooCommoditySymbol('cl=f')).toBe(true);
    expect(isYahooCommoditySymbol('  NG=F  ')).toBe(true);
  });

  it('SILVER:* gibi iki nokta içeren sembolleri reddeder', () => {
    expect(isYahooCommoditySymbol('SILVER:XAG')).toBe(false);
  });

  it('null/boş/bilinmeyen için false döner', () => {
    expect(isYahooCommoditySymbol(null)).toBe(false);
    expect(isYahooCommoditySymbol('')).toBe(false);
    expect(isYahooCommoditySymbol('XYZ=F')).toBe(false);
  });

  it('YAHOO_COMMODITY_SYMBOLS bir Set ve beklenenleri içerir', () => {
    expect(YAHOO_COMMODITY_SYMBOLS).toBeInstanceOf(Set);
    expect(YAHOO_COMMODITY_SYMBOLS.has('CL=F')).toBe(true);
    expect(YAHOO_COMMODITY_SYMBOLS.has('KC=F')).toBe(true);
  });
});

describe('getPrimarySpotPrice', () => {
  it('null spot için null döner', () => {
    expect(getPrimarySpotPrice(null)).toBeNull();
    expect(getPrimarySpotPrice(undefined)).toBeNull();
  });

  it('rawPrice + USX/cent → /100, USD, 4 ondalık', () => {
    expect(getPrimarySpotPrice({ rawPrice: 2500, rawCurrency: 'USX' }))
      .toEqual({ value: 25, currency: 'USD', decimals: 4 });
    expect(getPrimarySpotPrice({ rawPrice: 2500, centConverted: true }))
      .toEqual({ value: 25, currency: 'USD', decimals: 4 });
  });

  it('rawPrice + USD → 2 ondalık', () => {
    expect(getPrimarySpotPrice({ rawPrice: 80.5, rawCurrency: 'USD' }))
      .toEqual({ value: 80.5, currency: 'USD', decimals: 2 });
  });

  it('rawPrice + non-USD → 4 ondalık', () => {
    expect(getPrimarySpotPrice({ rawPrice: 80.5, rawCurrency: 'EUR' }))
      .toEqual({ value: 80.5, currency: 'EUR', decimals: 4 });
  });

  it('rawPrice yoksa displayPrice fallback (TRY → 2, diğer → 4)', () => {
    expect(getPrimarySpotPrice({ displayPrice: 100, displayCurrency: 'TRY' }))
      .toEqual({ value: 100, currency: 'TRY', decimals: 2 });
    expect(getPrimarySpotPrice({ displayPrice: 100, displayCurrency: 'USD' }))
      .toEqual({ value: 100, currency: 'USD', decimals: 4 });
  });

  it('Türkçe formatlı string fiyatları parse eder', () => {
    expect(getPrimarySpotPrice({ rawPrice: '1.234,56', rawCurrency: 'USD' }))
      .toEqual({ value: 1234.56, currency: 'USD', decimals: 2 });
  });

  it('hiçbir geçerli fiyat yoksa null', () => {
    expect(getPrimarySpotPrice({ foo: 'bar' })).toBeNull();
    expect(getPrimarySpotPrice({ rawPrice: 'abc', displayPrice: '' })).toBeNull();
  });
});

describe('getTrySpotPrice', () => {
  it('displayCurrency TRY ise displayPrice döner', () => {
    expect(getTrySpotPrice({ displayCurrency: 'TRY', displayPrice: 3285.5 })).toBe(3285.5);
  });

  it('TRY değilse null', () => {
    expect(getTrySpotPrice({ displayCurrency: 'USD', displayPrice: 100 })).toBeNull();
    expect(getTrySpotPrice(null)).toBeNull();
  });
});

describe('canToggleTry', () => {
  it('hem primary hem TRY fiyatı varsa true', () => {
    expect(canToggleTry({ rawPrice: 80, rawCurrency: 'USD', displayCurrency: 'TRY', displayPrice: 2700 })).toBe(true);
  });

  it('TRY fiyatı yoksa false', () => {
    expect(canToggleTry({ rawPrice: 80, rawCurrency: 'USD' })).toBe(false);
  });

  it('hiçbir fiyat yoksa false', () => {
    expect(canToggleTry(null)).toBe(false);
  });
});

describe('pickCommoditySpotPriceTry', () => {
  it('TRY display fiyatını döner', () => {
    expect(pickCommoditySpotPriceTry({ displayCurrency: 'TRY', displayPrice: 2700 })).toBe(2700);
    expect(pickCommoditySpotPriceTry({ displayCurrency: 'USD', displayPrice: 80 })).toBeNull();
  });
});

describe('pickCommoditySpotPriceUsd', () => {
  it('primary USD ise onun value değerini döner', () => {
    expect(pickCommoditySpotPriceUsd({ rawPrice: 80.5, rawCurrency: 'USD' })).toBe(80.5);
  });

  it('primary USD değilse rawPrice fallback', () => {
    // displayCurrency TRY → primary.currency TRY (USD değil) → rawPrice'a düşer (burada yok) → null
    expect(pickCommoditySpotPriceUsd({ displayPrice: 2700, displayCurrency: 'TRY' })).toBeNull();
    // rawPrice mevcut ama EUR → primary USD değil → rawPrice parse edilir
    expect(pickCommoditySpotPriceUsd({ rawPrice: 90, rawCurrency: 'EUR' })).toBe(90);
  });

  it('null dto için null', () => {
    expect(pickCommoditySpotPriceUsd(null)).toBeNull();
  });
});

describe('fmtUsd', () => {
  it('null/sonsuz için "-" döner', () => {
    expect(fmtUsd(null)).toBe('-');
    expect(fmtUsd(undefined)).toBe('-');
    expect(fmtUsd(Infinity)).toBe('-');
  });

  it('en-US formatında (virgül binlik) gösterir', () => {
    expect(fmtUsd(1234.5)).toBe('1,234.50');
    expect(fmtUsd(1234.5, 4)).toBe('1,234.5000');
  });
});

describe('fmtTry', () => {
  it('null için "-" döner', () => {
    expect(fmtTry(null)).toBe('-');
  });

  it('tr-TR formatında (nokta binlik, virgül ondalık) gösterir', () => {
    expect(fmtTry(1234.5)).toBe('1.234,50');
  });
});
