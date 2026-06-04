import { describe, it, expect, beforeEach } from 'vitest';
import { renderHook, act, waitFor } from '@testing-library/react';
import { useCardOrder } from '../useCardOrder';

/**
 * useCardOrder.js bir SAF custom hook'tur; tek dış bağımlılığı jsdom'da var olan
 * localStorage'dır (HTTP/context YOK). Bu yüzden vi.mock gerekmez; her testten önce
 * localStorage temizlenir. Hook { order, hidden, move, hide, show, reset } döndürür ve
 * order'ı `storageKey`, hidden'ı (dizi olarak) `${storageKey}:hidden` altında saklar.
 */

const KEY = 'fp-cards';
const HKEY = `${KEY}:hidden`;

beforeEach(() => {
  localStorage.clear();
});

// ─────────────────────────────────────────────────────────────────────────────
// Başlangıç state'i
// ─────────────────────────────────────────────────────────────────────────────
describe('useCardOrder — başlangıç state', () => {
  it('kayıt yokken order = defaultKeys (kopya), hidden = boş Set', () => {
    const defaults = ['a', 'b', 'c'];
    const { result } = renderHook(() => useCardOrder(KEY, defaults));

    expect(result.current.order).toEqual(['a', 'b', 'c']);
    // merge non-array saved → [...defaultKeys]: yeni dizi referansı (clone)
    expect(result.current.order).not.toBe(defaults);
    expect(result.current.hidden).toBeInstanceOf(Set);
    expect(result.current.hidden.size).toBe(0);
  });

  it('döndürülen fonksiyonlar (move/hide/show/reset) tanımlıdır', () => {
    const { result } = renderHook(() => useCardOrder(KEY, ['a']));
    expect(typeof result.current.move).toBe('function');
    expect(typeof result.current.hide).toBe('function');
    expect(typeof result.current.show).toBe('function');
    expect(typeof result.current.reset).toBe('function');
  });

  it('kayıtlı geçerli sırayı okur ve aynen kullanır', () => {
    localStorage.setItem(KEY, JSON.stringify(['c', 'a', 'b']));
    const { result } = renderHook(() => useCardOrder(KEY, ['a', 'b', 'c']));
    expect(result.current.order).toEqual(['c', 'a', 'b']);
  });

  it('kayıtlı sıradaki bilinmeyen anahtarları eler, eksik varsayılanları sona ekler', () => {
    // 'x' default'larda yok → düşer; 'b' kayıtta yok → sona eklenir
    localStorage.setItem(KEY, JSON.stringify(['c', 'x', 'a']));
    const { result } = renderHook(() => useCardOrder(KEY, ['a', 'b', 'c']));
    expect(result.current.order).toEqual(['c', 'a', 'b']);
  });

  it('kayıtlı değer dizi değilse (obje) varsayılan sıraya düşer', () => {
    localStorage.setItem(KEY, JSON.stringify({ foo: 'bar' }));
    const { result } = renderHook(() => useCardOrder(KEY, ['a', 'b']));
    expect(result.current.order).toEqual(['a', 'b']);
  });

  it('bozuk JSON kayıtlıysa (parse hatası) varsayılan sıraya düşer', () => {
    localStorage.setItem(KEY, '{bozuk json');
    const { result } = renderHook(() => useCardOrder(KEY, ['a', 'b']));
    expect(result.current.order).toEqual(['a', 'b']);
  });

  it('kayıtlı hidden dizisini Set olarak okur', () => {
    localStorage.setItem(HKEY, JSON.stringify(['b']));
    const { result } = renderHook(() => useCardOrder(KEY, ['a', 'b', 'c']));
    expect(result.current.hidden).toBeInstanceOf(Set);
    expect(result.current.hidden.has('b')).toBe(true);
    expect(result.current.hidden.has('a')).toBe(false);
  });

  it('hidden kaydı dizi değilse (null/parse fallback) boş Set olur', () => {
    // readArr → null, `?? []` devreye girer
    localStorage.setItem(HKEY, '{bozuk');
    const { result } = renderHook(() => useCardOrder(KEY, ['a', 'b']));
    expect(result.current.hidden.size).toBe(0);
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// localStorage persist (efektler)
// ─────────────────────────────────────────────────────────────────────────────
describe('useCardOrder — localStorage persist', () => {
  it('ilk render order ve hidden değerlerini localStorage’a yazar', async () => {
    renderHook(() => useCardOrder(KEY, ['a', 'b']));
    await waitFor(() => {
      expect(JSON.parse(localStorage.getItem(KEY))).toEqual(['a', 'b']);
    });
    expect(JSON.parse(localStorage.getItem(HKEY))).toEqual([]);
  });

  it('hide sonrası hidden listesi localStorage’a (dizi olarak) yazılır', async () => {
    const { result } = renderHook(() => useCardOrder(KEY, ['a', 'b']));
    act(() => result.current.hide('a'));
    await waitFor(() => {
      expect(JSON.parse(localStorage.getItem(HKEY))).toEqual(['a']);
    });
  });

  it('move sonrası yeni sıra localStorage’a yazılır', async () => {
    const { result } = renderHook(() => useCardOrder(KEY, ['a', 'b', 'c']));
    act(() => result.current.move('a', 'c'));
    await waitFor(() => {
      expect(JSON.parse(localStorage.getItem(KEY))).toEqual(['b', 'c', 'a']);
    });
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// move
// ─────────────────────────────────────────────────────────────────────────────
describe('useCardOrder — move', () => {
  it('öğeyi hedef anahtarın konumuna taşır (öne çekme)', () => {
    const { result } = renderHook(() => useCardOrder(KEY, ['a', 'b', 'c', 'd']));
    act(() => result.current.move('c', 'a'));
    expect(result.current.order).toEqual(['c', 'a', 'b', 'd']);
  });

  it('öğeyi geriye taşıma (sona doğru)', () => {
    const { result } = renderHook(() => useCardOrder(KEY, ['a', 'b', 'c', 'd']));
    act(() => result.current.move('a', 'c'));
    // a çıkar → [b,c,d]; c'nin (index 1) konumuna a eklenir → [b,c,a,d]
    expect(result.current.order).toEqual(['b', 'c', 'a', 'd']);
  });

  it('fromKey === toKey ise sırayı (referansı) değiştirmez', () => {
    const { result } = renderHook(() => useCardOrder(KEY, ['a', 'b', 'c']));
    const before = result.current.order;
    act(() => result.current.move('b', 'b'));
    expect(result.current.order).toBe(before);
    expect(result.current.order).toEqual(['a', 'b', 'c']);
  });

  it('fromKey bilinmiyorsa (index < 0) sırayı değiştirmez', () => {
    const { result } = renderHook(() => useCardOrder(KEY, ['a', 'b', 'c']));
    const before = result.current.order;
    act(() => result.current.move('yok', 'a'));
    expect(result.current.order).toBe(before);
  });

  it('toKey bilinmiyorsa (index < 0) sırayı değiştirmez', () => {
    const { result } = renderHook(() => useCardOrder(KEY, ['a', 'b', 'c']));
    const before = result.current.order;
    act(() => result.current.move('a', 'yok'));
    expect(result.current.order).toBe(before);
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// hide / show
// ─────────────────────────────────────────────────────────────────────────────
describe('useCardOrder — hide / show', () => {
  it('hide bir anahtarı gizlenenlere ekler', () => {
    const { result } = renderHook(() => useCardOrder(KEY, ['a', 'b']));
    act(() => result.current.hide('a'));
    expect(result.current.hidden.has('a')).toBe(true);
    expect(result.current.hidden.size).toBe(1);
  });

  it('hide idempotenttir (aynı anahtarı iki kez ekleyince tek kalır)', () => {
    const { result } = renderHook(() => useCardOrder(KEY, ['a', 'b']));
    act(() => result.current.hide('a'));
    act(() => result.current.hide('a'));
    expect(result.current.hidden.size).toBe(1);
  });

  it('show gizlenen bir anahtarı kaldırır', () => {
    const { result } = renderHook(() => useCardOrder(KEY, ['a', 'b']));
    act(() => result.current.hide('a'));
    act(() => result.current.show('a'));
    expect(result.current.hidden.has('a')).toBe(false);
    expect(result.current.hidden.size).toBe(0);
  });

  it('var olmayan anahtarı show etmek hata vermez (no-op)', () => {
    const { result } = renderHook(() => useCardOrder(KEY, ['a', 'b']));
    act(() => result.current.show('yok'));
    expect(result.current.hidden.size).toBe(0);
  });

  it('birden fazla anahtar gizlenebilir', () => {
    const { result } = renderHook(() => useCardOrder(KEY, ['a', 'b', 'c']));
    act(() => result.current.hide('a'));
    act(() => result.current.hide('c'));
    expect([...result.current.hidden].sort()).toEqual(['a', 'c']);
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// reset
// ─────────────────────────────────────────────────────────────────────────────
describe('useCardOrder — reset', () => {
  it('sırayı varsayılana döndürür ve tüm gizlenenleri temizler', async () => {
    const { result } = renderHook(() => useCardOrder(KEY, ['a', 'b', 'c']));
    act(() => {
      result.current.move('c', 'a');
      result.current.hide('b');
    });
    expect(result.current.order).toEqual(['c', 'a', 'b']);
    expect(result.current.hidden.has('b')).toBe(true);

    act(() => result.current.reset());
    expect(result.current.order).toEqual(['a', 'b', 'c']);
    expect(result.current.hidden.size).toBe(0);

    // persist edilen değerler de varsayılana döner
    await waitFor(() => {
      expect(JSON.parse(localStorage.getItem(KEY))).toEqual(['a', 'b', 'c']);
    });
    expect(JSON.parse(localStorage.getItem(HKEY))).toEqual([]);
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// defaultKeys değişimi (useEffect merge)
// ─────────────────────────────────────────────────────────────────────────────
describe('useCardOrder — defaultKeys değişimi', () => {
  it('yeni default eklenince mevcut sırayla birleştirip sona ekler', () => {
    const { result, rerender } = renderHook(
      ({ keys }) => useCardOrder(KEY, keys),
      { initialProps: { keys: ['a', 'b'] } },
    );
    // önce sırayı değiştir
    act(() => result.current.move('b', 'a'));
    expect(result.current.order).toEqual(['b', 'a']);

    // yeni kart 'c' eklenir → mevcut sıra korunur, 'c' sona gelir
    rerender({ keys: ['a', 'b', 'c'] });
    expect(result.current.order).toEqual(['b', 'a', 'c']);
  });

  it('default kaldırılınca ilgili anahtar sıradan düşer', () => {
    const { result, rerender } = renderHook(
      ({ keys }) => useCardOrder(KEY, keys),
      { initialProps: { keys: ['a', 'b', 'c'] } },
    );
    expect(result.current.order).toEqual(['a', 'b', 'c']);

    rerender({ keys: ['a', 'c'] });
    expect(result.current.order).toEqual(['a', 'c']);
  });

  it('defaultKeys içerik aynı kalırsa (join eşit) sıra korunur', () => {
    const { result, rerender } = renderHook(
      ({ keys }) => useCardOrder(KEY, keys),
      { initialProps: { keys: ['a', 'b', 'c'] } },
    );
    act(() => result.current.move('c', 'a'));
    expect(result.current.order).toEqual(['c', 'a', 'b']);

    // yeni dizi referansı ama aynı içerik → join('|') değişmez → merge tetiklense bile
    // bilinen sıra korunur
    rerender({ keys: ['a', 'b', 'c'] });
    expect(result.current.order).toEqual(['c', 'a', 'b']);
  });
});
