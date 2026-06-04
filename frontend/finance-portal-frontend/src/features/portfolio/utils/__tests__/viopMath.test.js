import { describe, it, expect } from 'vitest';
import {
  directionSign,
  notional,
  marginPosted,
  pnl,
  leverage,
} from '../viopMath';

describe('directionSign', () => {
  it('SHORT (her yazımda) → -1', () => {
    expect(directionSign('SHORT')).toBe(-1);
    expect(directionSign('short')).toBe(-1);
    expect(directionSign('Short')).toBe(-1);
  });

  it('baştaki/sondaki boşlukları kırparak SHORT tanır', () => {
    expect(directionSign('  SHORT  ')).toBe(-1);
  });

  it('LONG → +1', () => {
    expect(directionSign('LONG')).toBe(1);
    expect(directionSign('long')).toBe(1);
  });

  it('null/undefined/boş string → +1 (varsayılan LONG)', () => {
    expect(directionSign(null)).toBe(1);
    expect(directionSign(undefined)).toBe(1);
    expect(directionSign('')).toBe(1);
  });

  it('bilinmeyen değer → +1 (SHORT değilse LONG sayılır)', () => {
    expect(directionSign('FOO')).toBe(1);
    // SHORT dışındaki her şey LONG'a düşer
    expect(directionSign('SELL')).toBe(1);
  });
});

describe('notional', () => {
  it('qty × price × multiplier hesaplar', () => {
    expect(notional(2, 100, 10)).toBe(2000);
  });

  it('multiplier verilmezse 1 kabul edilir', () => {
    // numOrOne: undefined → 1
    expect(notional(3, 50, undefined)).toBe(150);
  });

  it('multiplier 0 ise 1 olarak ele alınır (numOrOne tuzağı)', () => {
    // 0 multiplier "boş" sayılıp 1'e yükseltilir → notional sıfırlanmaz
    expect(notional(4, 25, 0)).toBe(100);
  });

  it('string sayılar parse edilir', () => {
    expect(notional('2', '100', '10')).toBe(2000);
  });

  it('qty/price null/NaN ise 0 olur → sonuç 0', () => {
    expect(notional(null, 100, 10)).toBe(0);
    expect(notional(2, NaN, 10)).toBe(0);
    expect(notional(undefined, undefined, undefined)).toBe(0);
  });

  it('ondalık çarpımda yakın değer', () => {
    expect(notional(1.5, 2.5, 1)).toBeCloseTo(3.75, 5);
  });
});

describe('marginPosted', () => {
  it('qty × avgEntry × multiplier × marginRate hesaplar', () => {
    expect(marginPosted(2, 100, 10, 0.1)).toBeCloseTo(200, 5);
  });

  it('marginRate 0/null ise 0 döner', () => {
    // numOrZero: marginRate yok → 0 → tüm çarpım 0
    expect(marginPosted(2, 100, 10, 0)).toBe(0);
    expect(marginPosted(2, 100, 10, null)).toBe(0);
  });

  it('multiplier verilmezse 1', () => {
    expect(marginPosted(2, 100, undefined, 0.1)).toBeCloseTo(20, 5);
  });

  it('multiplier 0 ise 1 sayılır', () => {
    // numOrOne: 0 → 1
    expect(marginPosted(2, 100, 0, 0.1)).toBeCloseTo(20, 5);
  });

  it('string girdiler parse edilir', () => {
    expect(marginPosted('2', '100', '10', '0.1')).toBeCloseTo(200, 5);
  });

  it('qty/avgEntry geçersizse 0', () => {
    expect(marginPosted(NaN, 100, 10, 0.1)).toBe(0);
    expect(marginPosted(2, null, 10, 0.1)).toBe(0);
  });
});

describe('pnl', () => {
  it('LONG kârı: (güncel − giriş) × qty × multiplier × +1', () => {
    expect(pnl(2, 100, 120, 10, 'LONG')).toBe(400);
  });

  it('LONG zararı: fiyat düşünce negatif', () => {
    expect(pnl(2, 100, 90, 10, 'LONG')).toBe(-200);
  });

  it('SHORT: fiyat düşünce kâr (yön -1 işareti çevirir)', () => {
    // (90 - 100) × 2 × 10 × (-1) = 200
    expect(pnl(2, 100, 90, 10, 'SHORT')).toBe(200);
  });

  it('SHORT: fiyat yükselince zarar', () => {
    // (120 - 100) × 2 × 10 × (-1) = -400
    expect(pnl(2, 100, 120, 10, 'SHORT')).toBe(-400);
  });

  it('direction null → LONG varsayılır (işaret +1)', () => {
    expect(pnl(2, 100, 120, 10, null)).toBe(400);
  });

  it('multiplier verilmezse 1', () => {
    expect(pnl(2, 100, 110, undefined, 'LONG')).toBe(20);
  });

  it('multiplier 0 ise 1 sayılır (numOrOne)', () => {
    expect(pnl(2, 100, 110, 0, 'LONG')).toBe(20);
  });

  it('fiyat = giriş → 0', () => {
    expect(pnl(2, 100, 100, 10, 'LONG')).toBe(0);
  });

  it('qty geçersiz → 0 (çarpan sıfırlar)', () => {
    expect(pnl(null, 100, 120, 10, 'LONG')).toBe(0);
  });

  it('avgEntry geçersiz → 0 KABUL edilir, current ile hesaplanır (sıfırlamaz)', () => {
    // numOrZero(NaN)=0 → giriş 0 sayılır: (120−0)×2×10×1 = 2400. Sonuç 0 DEĞİL.
    expect(pnl(2, NaN, 120, 10, 'LONG')).toBe(2400);
  });

  it('string girdiler parse edilir', () => {
    expect(pnl('2', '100', '120', '10', 'LONG')).toBe(400);
  });

  it('ondalık sonuçta yakın değer', () => {
    expect(pnl(1, 100.5, 101.0, 1, 'LONG')).toBeCloseTo(0.5, 5);
  });
});

describe('leverage', () => {
  it('notional / margin oranı', () => {
    expect(leverage(2000, 200)).toBe(10);
  });

  it('margin 0 ise 0 döner (sıfıra bölme koruması)', () => {
    expect(leverage(2000, 0)).toBe(0);
  });

  it('margin null/NaN ise (→0) yine 0 döner', () => {
    // numOrZero margin'i 0'a indirir → erken return 0
    expect(leverage(2000, null)).toBe(0);
    expect(leverage(2000, NaN)).toBe(0);
  });

  it('notional 0 ise 0 (margin pozitifken)', () => {
    expect(leverage(0, 200)).toBe(0);
  });

  it('notional geçersizse 0 sayılır → 0', () => {
    expect(leverage(null, 200)).toBe(0);
  });

  it('string girdiler parse edilir', () => {
    expect(leverage('2000', '200')).toBe(10);
  });

  it('ondalık oranda yakın değer', () => {
    expect(leverage(150, 20)).toBeCloseTo(7.5, 5);
  });
});
