import { describe, it, expect } from 'vitest';
import { pickSilverGramCloseTry } from '../silverPriceUtils';

describe('pickSilverGramCloseTry', () => {
  // --- 1) history son close önceliği ---
  describe('history önceliği', () => {
    it('history son noktanın close değerini döner (en yüksek öncelik)', () => {
      const history = [{ close: 10 }, { close: 20 }, { close: 33.5 }];
      // spot'ta farklı değerler olsa bile history kazanır
      expect(
        pickSilverGramCloseTry({ silverGramCloseTry: 99, silverGramTry: 88 }, history),
      ).toBe(33.5);
    });

    it('close string ise parseFloat ile sayıya çevrilir', () => {
      expect(pickSilverGramCloseTry(null, [{ close: '42.75' }])).toBeCloseTo(42.75, 2);
    });

    it('spot null olsa bile geçerli history close varsa onu döner', () => {
      expect(pickSilverGramCloseTry(null, [{ close: 50 }])).toBe(50);
    });

    it('son close null ise history atlanır, spot.silverGramCloseTry fallback', () => {
      // last.close == null → history geçersiz → spot'a düşer
      expect(
        pickSilverGramCloseTry({ silverGramCloseTry: 25 }, [{ close: 10 }, { close: null }]),
      ).toBe(25);
    });

    it('son close <= 0 ise history atlanır (0 ve negatif geçersiz)', () => {
      expect(pickSilverGramCloseTry({ silverGramCloseTry: 30 }, [{ close: 0 }])).toBe(30);
      expect(pickSilverGramCloseTry({ silverGramCloseTry: 30 }, [{ close: -5 }])).toBe(30);
    });

    it('son close NaN/parse edilemez ise history atlanır', () => {
      expect(pickSilverGramCloseTry({ silverGramCloseTry: 12 }, [{ close: 'abc' }])).toBe(12);
    });

    it('last point undefined/null olsa bile çökmeden spot fallback yapar', () => {
      // last?.close optional-chaining → undefined → NaN → history geçersiz
      expect(pickSilverGramCloseTry({ silverGramCloseTry: 7 }, [undefined])).toBe(7);
      expect(pickSilverGramCloseTry({ silverGramCloseTry: 7 }, [null])).toBe(7);
    });

    it('boş history dizisi atlanır, spot fallback', () => {
      expect(pickSilverGramCloseTry({ silverGramCloseTry: 15 }, [])).toBe(15);
    });

    it('history dizi değilse (null/undefined/obje) atlanır, spot fallback', () => {
      expect(pickSilverGramCloseTry({ silverGramCloseTry: 18 }, null)).toBe(18);
      expect(pickSilverGramCloseTry({ silverGramCloseTry: 18 }, undefined)).toBe(18);
      expect(pickSilverGramCloseTry({ silverGramCloseTry: 18 }, { close: 999 })).toBe(18);
    });
  });

  // --- 2) spot fallback zinciri ---
  describe('spot fallback zinciri', () => {
    it('spot.silverGramCloseTry geçerli ise onu döner (silverGramTry yerine)', () => {
      expect(
        pickSilverGramCloseTry({ silverGramCloseTry: 41, silverGramTry: 40 }, []),
      ).toBe(41);
    });

    it('silverGramCloseTry string ise parseFloat ile çevrilir', () => {
      expect(pickSilverGramCloseTry({ silverGramCloseTry: '36.20' }, [])).toBeCloseTo(36.2, 2);
    });

    it('silverGramCloseTry yoksa/null ise silverGramTry (ağırlıklı ort.) fallback', () => {
      expect(pickSilverGramCloseTry({ silverGramTry: 38 }, [])).toBe(38);
      expect(
        pickSilverGramCloseTry({ silverGramCloseTry: null, silverGramTry: 38 }, []),
      ).toBe(38);
    });

    it('silverGramCloseTry <= 0 ise silverGramTry fallback', () => {
      expect(
        pickSilverGramCloseTry({ silverGramCloseTry: 0, silverGramTry: 22 }, []),
      ).toBe(22);
      expect(
        pickSilverGramCloseTry({ silverGramCloseTry: -1, silverGramTry: 22 }, []),
      ).toBe(22);
    });

    it('silverGramCloseTry parse edilemez ise silverGramTry fallback', () => {
      expect(
        pickSilverGramCloseTry({ silverGramCloseTry: 'xx', silverGramTry: 19.5 }, []),
      ).toBeCloseTo(19.5, 2);
    });

    it('silverGramTry string ise parseFloat ile çevrilir', () => {
      expect(pickSilverGramCloseTry({ silverGramTry: '27.05' }, [])).toBeCloseTo(27.05, 2);
    });
  });

  // --- 3) null / geçersiz dönüşler ---
  describe('null / geçersiz girişler', () => {
    it('spot null/undefined ve geçerli history yoksa null döner', () => {
      expect(pickSilverGramCloseTry(null, null)).toBeNull();
      expect(pickSilverGramCloseTry(undefined, [])).toBeNull();
      // history close geçersiz + spot null → null
      expect(pickSilverGramCloseTry(null, [{ close: 0 }])).toBeNull();
    });

    it('spot var ama hiçbir alanı geçerli değilse null döner', () => {
      expect(pickSilverGramCloseTry({}, [])).toBeNull();
      expect(pickSilverGramCloseTry({ foo: 'bar' }, [])).toBeNull();
      expect(
        pickSilverGramCloseTry({ silverGramCloseTry: 0, silverGramTry: -3 }, []),
      ).toBeNull();
      expect(
        pickSilverGramCloseTry({ silverGramCloseTry: 'a', silverGramTry: 'b' }, []),
      ).toBeNull();
    });

    it('argümansız çağrıda (her ikisi de undefined) null döner', () => {
      expect(pickSilverGramCloseTry()).toBeNull();
    });
  });
});
