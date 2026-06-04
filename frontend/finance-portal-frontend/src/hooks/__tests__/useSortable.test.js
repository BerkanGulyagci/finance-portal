import { describe, it, expect } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useSortable } from '../useSortable';

// useSortable: useState + useMemo'ya dayanan saf bir sıralama hook'u.
// HTTP/context/localStorage bağımlılığı YOK → renderHook + act yeterli.

describe('useSortable hook', () => {
  describe('başlangıç state', () => {
    it('varsayılanlar: sortKey=null, sortDir="asc"', () => {
      const data = [{ a: 1 }];
      const { result } = renderHook(() => useSortable(data));
      expect(result.current.sortKey).toBeNull();
      expect(result.current.sortDir).toBe('asc');
    });

    it('sortKey null iken sorted, veriyi DEĞİŞMEDEN döndürür (erken return)', () => {
      const data = [{ a: 3 }, { a: 1 }, { a: 2 }];
      const { result } = renderHook(() => useSortable(data));
      // sortKey yok → orijinal referansın aynısı döner
      expect(result.current.sorted).toBe(data);
    });

    it('özel defaultKey ve defaultDir başlangıçta uygulanır', () => {
      const data = [{ a: 1 }];
      const { result } = renderHook(() => useSortable(data, 'a', 'desc'));
      expect(result.current.sortKey).toBe('a');
      expect(result.current.sortDir).toBe('desc');
    });
  });

  describe('handleSort', () => {
    it('farklı bir anahtara basınca sortKey değişir ve yön "asc" olur', () => {
      const data = [{ a: 1 }];
      const { result } = renderHook(() => useSortable(data));
      act(() => result.current.handleSort('a'));
      expect(result.current.sortKey).toBe('a');
      expect(result.current.sortDir).toBe('asc');
    });

    it('aynı anahtara tekrar basınca yön asc→desc→asc olarak döner (toggle)', () => {
      const data = [{ a: 1 }];
      const { result } = renderHook(() => useSortable(data, 'a', 'asc'));
      // aynı key → asc'ten desc'e
      act(() => result.current.handleSort('a'));
      expect(result.current.sortKey).toBe('a');
      expect(result.current.sortDir).toBe('desc');
      // tekrar aynı key → desc'ten asc'e
      act(() => result.current.handleSort('a'));
      expect(result.current.sortDir).toBe('asc');
    });

    it('yeni anahtar, mevcut "desc" yönünü "asc"e sıfırlar', () => {
      const data = [{ a: 1, b: 2 }];
      const { result } = renderHook(() => useSortable(data, 'a', 'desc'));
      expect(result.current.sortDir).toBe('desc');
      act(() => result.current.handleSort('b'));
      expect(result.current.sortKey).toBe('b');
      expect(result.current.sortDir).toBe('asc');
    });
  });

  describe('sorted — sayısal karşılaştırma', () => {
    it('artan (asc) sayısal sıralama', () => {
      const data = [{ v: 3 }, { v: 1 }, { v: 2 }];
      const { result } = renderHook(() => useSortable(data, 'v', 'asc'));
      expect(result.current.sorted.map(r => r.v)).toEqual([1, 2, 3]);
    });

    it('azalan (desc) sayısal sıralama', () => {
      const data = [{ v: 3 }, { v: 1 }, { v: 2 }];
      const { result } = renderHook(() => useSortable(data, 'v', 'desc'));
      expect(result.current.sorted.map(r => r.v)).toEqual([3, 2, 1]);
    });

    it('virgüllü ve yüzdeli string sayılar temizlenip sayısal sıralanır', () => {
      // "1,5%" → 1.5 ; "10,2%" → 10.2 ; "2,0%" → 2.0
      const data = [{ v: '10,2%' }, { v: '1,5%' }, { v: '2,0%' }];
      const { result } = renderHook(() => useSortable(data, 'v', 'asc'));
      expect(result.current.sorted.map(r => r.v)).toEqual(['1,5%', '2,0%', '10,2%']);
    });

    it('orijinal diziyi mutasyona uğratmaz (kopya üzerinde sıralar)', () => {
      const data = [{ v: 3 }, { v: 1 }, { v: 2 }];
      const { result } = renderHook(() => useSortable(data, 'v', 'asc'));
      // sorted yeni dizi olmalı
      expect(result.current.sorted).not.toBe(data);
      // kaynak dizi sırası korunmalı
      expect(data.map(r => r.v)).toEqual([3, 1, 2]);
    });
  });

  describe('sorted — string karşılaştırma', () => {
    it('alfabetik artan sıralama (büyük/küçük harf duyarsız)', () => {
      const data = [{ name: 'Banana' }, { name: 'apple' }, { name: 'Cherry' }];
      const { result } = renderHook(() => useSortable(data, 'name', 'asc'));
      expect(result.current.sorted.map(r => r.name)).toEqual(['apple', 'Banana', 'Cherry']);
    });

    it('alfabetik azalan sıralama', () => {
      const data = [{ name: 'apple' }, { name: 'cherry' }, { name: 'banana' }];
      const { result } = renderHook(() => useSortable(data, 'name', 'desc'));
      expect(result.current.sorted.map(r => r.name)).toEqual(['cherry', 'banana', 'apple']);
    });

    it('eşit string değerler 0 döndürür → sıra korunur (stabil)', () => {
      const data = [{ id: 1, name: 'x' }, { id: 2, name: 'x' }];
      const { result } = renderHook(() => useSortable(data, 'name', 'asc'));
      expect(result.current.sorted.map(r => r.id)).toEqual([1, 2]);
    });
  });

  describe('sorted — null/undefined ve boş veri kenar durumları', () => {
    it('data null ise sorted null döner (erken return)', () => {
      const { result } = renderHook(() => useSortable(null, 'v', 'asc'));
      expect(result.current.sorted).toBeNull();
    });

    it('data undefined ise sorted undefined döner', () => {
      const { result } = renderHook(() => useSortable(undefined, 'v', 'asc'));
      expect(result.current.sorted).toBeUndefined();
    });

    it('boş dizi olduğu gibi döner', () => {
      const data = [];
      const { result } = renderHook(() => useSortable(data, 'v', 'asc'));
      expect(result.current.sorted).toBe(data);
    });

    it('null/undefined alan değerleri boş string sayılıp string dalında sıralanır', () => {
      // null ve undefined → "" ; "b" en sona; asc'te boşlar başta
      const data = [{ v: 'b' }, { v: null }, { v: undefined }, { v: 'a' }];
      const { result } = renderHook(() => useSortable(data, 'v', 'asc'));
      const order = result.current.sorted.map(r => r.v);
      // iki boş ("") değer "a" ve "b"den önce gelir
      expect(order.slice(2)).toEqual(['a', 'b']);
      expect(order[0] ?? '').toBe('');
      expect(order[1] ?? '').toBe('');
    });

    it('asc/desc ile bilinmeyen (mevcut olmayan) anahtarda tüm değerler "" → 0, sıra korunur', () => {
      const data = [{ id: 1 }, { id: 2 }, { id: 3 }];
      const { result } = renderHook(() => useSortable(data, 'yok', 'desc'));
      // hepsi undefined → "" → eşit → orijinal sıra
      expect(result.current.sorted.map(r => r.id)).toEqual([1, 2, 3]);
    });
  });

  describe('sorted — yeniden hesaplama (memo bağımlılıkları)', () => {
    it('handleSort yönü değiştirince sorted yeniden hesaplanır', () => {
      const data = [{ v: 1 }, { v: 2 }, { v: 3 }];
      const { result } = renderHook(() => useSortable(data, 'v', 'asc'));
      expect(result.current.sorted.map(r => r.v)).toEqual([1, 2, 3]);
      act(() => result.current.handleSort('v')); // asc → desc
      expect(result.current.sorted.map(r => r.v)).toEqual([3, 2, 1]);
    });

    it('data prop değişince sorted yeni veriye göre güncellenir', () => {
      const { result, rerender } = renderHook(
        ({ d }) => useSortable(d, 'v', 'asc'),
        { initialProps: { d: [{ v: 2 }, { v: 1 }] } }
      );
      expect(result.current.sorted.map(r => r.v)).toEqual([1, 2]);
      rerender({ d: [{ v: 9 }, { v: 5 }, { v: 7 }] });
      expect(result.current.sorted.map(r => r.v)).toEqual([5, 7, 9]);
    });
  });
});
