import { describe, it, expect } from 'vitest';
import { getCommodityUnit } from '../commodityUnit';

describe('getCommodityUnit', () => {
  it('sembol yoksa (null/undefined/boş) null döner', () => {
    expect(getCommodityUnit(null)).toBe(null);
    expect(getCommodityUnit(undefined)).toBe(null);
    expect(getCommodityUnit('')).toBe(null);
  });

  it('spotMeta.unit varsa onu tercih eder (backend DTO preferred kaynak)', () => {
    // spotMeta.unit verildiğinde sembol-bazlı eşleme HİÇ çalışmaz, doğrudan döner
    expect(getCommodityUnit('CL=F', { unit: 'Barrel' })).toBe('Barrel');
    // sembol bilinmese bile spotMeta.unit kazanır
    expect(getCommodityUnit('UNKNOWN', { unit: 'Özel' })).toBe('Özel');
    // BIST sembolünde de spotMeta.unit önceliklidir
    expect(getCommodityUnit('SILVER:GRAM_TRY', { unit: 'Adet' })).toBe('Adet');
  });

  it('spotMeta var ama unit boş/eksikse sembol-bazlı fallback’e düşer', () => {
    // spotMeta truthy ama .unit yok → koşul geçer, fallback devreye girer
    expect(getCommodityUnit('NG=F', {})).toBe('MMBtu');
    expect(getCommodityUnit('NG=F', { unit: null })).toBe('MMBtu');
    expect(getCommodityUnit('NG=F', { unit: '' })).toBe('MMBtu');
  });

  it('Yahoo emtia sembolleri için doğru birimi döner', () => {
    expect(getCommodityUnit('CL=F')).toBe('Varil');
    expect(getCommodityUnit('BZ=F')).toBe('Varil');
    expect(getCommodityUnit('NG=F')).toBe('MMBtu');
    expect(getCommodityUnit('HG=F')).toBe('Pound');
    expect(getCommodityUnit('ZW=F')).toBe('Bushel');
    expect(getCommodityUnit('ZC=F')).toBe('Bushel');
    expect(getCommodityUnit('KC=F')).toBe('Pound');
    expect(getCommodityUnit('CC=F')).toBe('Ton');
    expect(getCommodityUnit('CT=F')).toBe('Pound');
  });

  it('Yahoo sembolü case-insensitive eşleşir (toUpperCase normalize)', () => {
    expect(getCommodityUnit('cl=f')).toBe('Varil');
    expect(getCommodityUnit('ng=f')).toBe('MMBtu');
  });

  it('BIST kıymetli maden kategorilerini birime çevirir (SEMBOL:KATEGORI)', () => {
    expect(getCommodityUnit('SILVER:GRAM_TRY')).toBe('Gram');
    expect(getCommodityUnit('GOLD:KG_TRY')).toBe('Kg');
    expect(getCommodityUnit('GOLD:USD_ONS')).toBe('Ons');
    expect(getCommodityUnit('GOLD:EUR_ONS')).toBe('Ons');
  });

  it('BIST kategorisi de case-insensitive eşleşir', () => {
    expect(getCommodityUnit('silver:gram_try')).toBe('Gram');
    expect(getCommodityUnit('Gold:Kg_Try')).toBe('Kg');
  });

  it('iki nokta üst üste birden fazlaysa yalnızca ilk parça ayrılır (split limit 2)', () => {
    // split(':', 2) → ['GOLD', 'GRAM_TRY']; üçüncü parça yok sayılır, kategori yine eşleşir
    expect(getCommodityUnit('GOLD:GRAM_TRY:EXTRA')).toBe('Gram');
  });

  it('bilinmeyen Yahoo/düz sembol → null', () => {
    expect(getCommodityUnit('AAPL')).toBe(null);
    expect(getCommodityUnit('ES=F')).toBe(null);
  });

  it('iki nokta var ama kategori bilinmiyorsa → null', () => {
    expect(getCommodityUnit('SILVER:UNKNOWN_CAT')).toBe(null);
    // ':' var fakat kategori boş → eşleşme yok
    expect(getCommodityUnit('SILVER:')).toBe(null);
  });

  it('sayısal/obje sembol String’e çevrilir, eşleşmezse null döner', () => {
    // String(symbol).toUpperCase() çağrılır; 123 truthy olduğu için erken-return olmaz
    expect(getCommodityUnit(123)).toBe(null);
  });
});
