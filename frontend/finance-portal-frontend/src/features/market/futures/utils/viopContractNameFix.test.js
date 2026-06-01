import { describe, it, expect } from 'vitest';
import { fixViopContractName, viopContractNamesMatch } from './viopContractNameFix';

describe('fixViopContractName', () => {
  it('null/undefined girdiyi olduğu gibi döndürür', () => {
    expect(fixViopContractName(null)).toBeNull();
    expect(fixViopContractName(undefined)).toBeUndefined();
  });

  it('bozuk Şubat varyantlarını düzeltir', () => {
    expect(fixViopContractName('SASA (27 Âub 26) Vadeli')).toContain('Şub');
    expect(fixViopContractName('SASA (27 Åub 26) Vadeli')).toContain('Şub');
    expect(fixViopContractName('SASA (27 Aşub 26) Vadeli')).toContain('Şub');
  });

  it('bozuk Ağustos varyantlarını düzeltir', () => {
    expect(fixViopContractName('XAU (29 AĞYu 26)')).toContain('Ağu');
    expect(fixViopContractName('XAU (29 Äžu 26)')).toContain('Ağu');
  });

  it('zaten doğru ismi bozmaz', () => {
    expect(fixViopContractName('HEKTS (30 Haz 26) Vadeli FIZ')).toBe('HEKTS (30 Haz 26) Vadeli FIZ');
  });
});

describe('viopContractNamesMatch', () => {
  it('birebir aynı isimler eşleşir', () => {
    expect(viopContractNamesMatch('HEKTS (30 Haz 26) Vadeli FIZ', 'HEKTS (30 Haz 26) Vadeli FIZ')).toBe(true);
  });

  it('büyük/küçük harf farkını yok sayar', () => {
    expect(viopContractNamesMatch('HEKTS (30 HAZ 26) VADELI FIZ', 'hekts (30 haz 26) vadeli fiz')).toBe(true);
  });

  // Bugün düzeltilen asıl bug: Akbank listesi sonda nokta gönderir, portföy sembolü noktasızdır.
  it('sondaki noktayı yok sayar (Akbank "FIZ." vs portföy "FIZ")', () => {
    expect(viopContractNamesMatch('HEKTS (30 Haz 26) Vadeli FIZ.', 'HEKTS (30 HAZ 26) VADELI FIZ')).toBe(true);
  });

  it('çoklu boşluğu tek boşluğa indirir', () => {
    expect(viopContractNamesMatch('SASA  (30  Haz 26)  Vadeli FIZ', 'SASA (30 Haz 26) Vadeli FIZ')).toBe(true);
  });

  it('farklı sözleşmeler eşleşmez', () => {
    expect(viopContractNamesMatch('HEKTS (30 Haz 26) Vadeli FIZ', 'SASA (30 Haz 26) Vadeli FIZ')).toBe(false);
  });

  it('null/boş güvenli', () => {
    expect(viopContractNamesMatch(null, 'SASA')).toBe(false);
    expect(viopContractNamesMatch('SASA', null)).toBe(false);
    expect(viopContractNamesMatch('', '')).toBe(false);
  });
});
