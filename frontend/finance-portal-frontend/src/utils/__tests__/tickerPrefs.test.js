import { describe, it, expect, beforeEach, vi } from 'vitest';
import {
  TICKER_PREFS_KEY,
  TICKER_PREFS_EVENT,
  TICKER_SPEED_KEY,
  TICKER_SPEED_VALUES,
  TICKER_SPEED_PX,
  TICKER_CATALOG,
  ALL_TICKER_KEYS,
  TICKER_CUSTOM_KEY,
  readTickerPrefs,
  saveTickerPrefs,
  readCustomTickerItems,
  saveCustomTickerItems,
  readTickerSpeed,
  saveTickerSpeed,
} from '../tickerPrefs';

/**
 * tickerPrefs.js SAF mantıktır; tek dış bağımlılığı '../api/prefs' (prefGet/prefSet/
 * prefSetSync). prefs modülü de kendisi SAF: yazma/okuma localStorage (JSON) üzerinden
 * yapılır ve sunucuya PUT YALNIZCA giriş yapılmışsa (auth_token mevcut) tetiklenir.
 * Testlerde her beforeEach'te localStorage temizlendiğinden auth_token yoktur → ağ
 * çağrısı olmaz; prefSetSync çözümlenmiş bir Promise döner. Bu yüzden prefs MOCK'lanmaz,
 * GERÇEK kullanılır (talimat gereği saf bağımlılığı gerçek kullan).
 */

beforeEach(() => {
  localStorage.clear();
});

// ─────────────────────────────────────────────────────────────────────────────
// Sabitler (şekil/bütünlük)
// ─────────────────────────────────────────────────────────────────────────────
describe('Sabitler — anahtarlar ve değerler', () => {
  it('localStorage/event anahtarları beklenen string değerlere sahip', () => {
    expect(TICKER_PREFS_KEY).toBe('fp-ticker-items');
    expect(TICKER_PREFS_EVENT).toBe('fp-ticker-prefs-changed');
    expect(TICKER_CUSTOM_KEY).toBe('fp-ticker-custom');
    expect(TICKER_SPEED_KEY).toBe('market_ticker_speed');
  });

  it('TICKER_SPEED_VALUES tam olarak slow/normal/fast içerir', () => {
    expect(TICKER_SPEED_VALUES).toEqual(['slow', 'normal', 'fast']);
  });

  it('TICKER_SPEED_PX her hız için px/sn katsayısı tanımlar', () => {
    expect(TICKER_SPEED_PX).toEqual({ slow: 20, normal: 40, fast: 80 });
    // Her hız değeri için karşılık gelen bir px katsayısı vardır.
    TICKER_SPEED_VALUES.forEach((v) => {
      expect(typeof TICKER_SPEED_PX[v]).toBe('number');
    });
  });
});

describe('TICKER_CATALOG — katalog yapısı', () => {
  it('boş olmayan, gruplardan oluşan bir dizidir', () => {
    expect(Array.isArray(TICKER_CATALOG)).toBe(true);
    expect(TICKER_CATALOG.length).toBeGreaterThan(0);
  });

  it('beklenen grup başlıklarını sırasıyla içerir', () => {
    expect(TICKER_CATALOG.map((g) => g.group)).toEqual([
      'TCMB Döviz',
      'Endeksler (BIST)',
      'Altın',
      'Kripto',
      'Ekonomi',
    ]);
  });

  it('her grup boş olmayan items dizisi taşır; her item key+label string içerir', () => {
    TICKER_CATALOG.forEach((g) => {
      expect(Array.isArray(g.items)).toBe(true);
      expect(g.items.length).toBeGreaterThan(0);
      g.items.forEach((i) => {
        expect(typeof i.key).toBe('string');
        expect(i.key.length).toBeGreaterThan(0);
        expect(typeof i.label).toBe('string');
        expect(i.label.length).toBeGreaterThan(0);
      });
    });
  });

  it('bilinen bazı anahtarları (USD/BIST100/BTC) içerir', () => {
    expect(ALL_TICKER_KEYS).toContain('fx:USD');
    expect(ALL_TICKER_KEYS).toContain('bist:XU100');
    expect(ALL_TICKER_KEYS).toContain('crypto:btc');
    expect(ALL_TICKER_KEYS).toContain('eco:inflation');
  });
});

describe('ALL_TICKER_KEYS — düzleştirilmiş anahtar listesi', () => {
  it('tüm grup item key’lerinin düz birleşimidir', () => {
    const expected = TICKER_CATALOG.flatMap((g) => g.items.map((i) => i.key));
    expect(ALL_TICKER_KEYS).toEqual(expected);
  });

  it('benzersiz anahtarlardan oluşur (yinelenen yok)', () => {
    expect(new Set(ALL_TICKER_KEYS).size).toBe(ALL_TICKER_KEYS.length);
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// readTickerPrefs
// ─────────────────────────────────────────────────────────────────────────────
describe('readTickerPrefs', () => {
  it('kayıt yoksa varsayılan: tüm anahtarlar açık bir Set döner', () => {
    const s = readTickerPrefs();
    expect(s).toBeInstanceOf(Set);
    expect(s.size).toBe(ALL_TICKER_KEYS.length);
    ALL_TICKER_KEYS.forEach((k) => expect(s.has(k)).toBe(true));
  });

  it('kayıtlı dizi varsa yalnızca o anahtarları içeren Set döner', () => {
    localStorage.setItem(TICKER_PREFS_KEY, JSON.stringify(['fx:USD', 'crypto:btc']));
    const s = readTickerPrefs();
    expect(s).toBeInstanceOf(Set);
    expect([...s]).toEqual(['fx:USD', 'crypto:btc']);
  });

  it('kayıtlı dizideki geçersiz/bilinmeyen anahtarları eler', () => {
    localStorage.setItem(
      TICKER_PREFS_KEY,
      JSON.stringify(['fx:USD', 'bilinmeyen:key', 'crypto:eth', 'cöp']),
    );
    const s = readTickerPrefs();
    expect([...s].sort()).toEqual(['crypto:eth', 'fx:USD'].sort());
    expect(s.has('bilinmeyen:key')).toBe(false);
  });

  it('boş dizi kayıtlıysa boş Set döner (varsayılana DÜŞMEZ)', () => {
    localStorage.setItem(TICKER_PREFS_KEY, JSON.stringify([]));
    const s = readTickerPrefs();
    expect(s.size).toBe(0);
  });

  it('kayıtlı değer dizi değilse (obje) varsayılana düşer: hepsi açık', () => {
    localStorage.setItem(TICKER_PREFS_KEY, JSON.stringify({ foo: 'bar' }));
    const s = readTickerPrefs();
    expect(s.size).toBe(ALL_TICKER_KEYS.length);
  });

  it('bozuk JSON kayıtlıysa (parse hatası) varsayılana düşer: hepsi açık', () => {
    localStorage.setItem(TICKER_PREFS_KEY, '{bozuk json');
    const s = readTickerPrefs();
    expect(s.size).toBe(ALL_TICKER_KEYS.length);
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// saveTickerPrefs
// ─────────────────────────────────────────────────────────────────────────────
describe('saveTickerPrefs', () => {
  it('Set’i diziye çevirip localStorage’a JSON olarak yazar', () => {
    saveTickerPrefs(new Set(['fx:USD', 'fx:EUR']));
    const raw = localStorage.getItem(TICKER_PREFS_KEY);
    expect(JSON.parse(raw)).toEqual(['fx:USD', 'fx:EUR']);
  });

  it('dizi de kabul eder (spread edilebilir herhangi bir iterable)', () => {
    saveTickerPrefs(['crypto:btc']);
    expect(JSON.parse(localStorage.getItem(TICKER_PREFS_KEY))).toEqual(['crypto:btc']);
  });

  it('boş Set yazıldığında boş dizi kaydeder', () => {
    saveTickerPrefs(new Set());
    expect(JSON.parse(localStorage.getItem(TICKER_PREFS_KEY))).toEqual([]);
  });

  it('aynı sekmedeki dinleyicileri haberdar etmek için TICKER_PREFS_EVENT yayınlar', () => {
    const handler = vi.fn();
    window.addEventListener(TICKER_PREFS_EVENT, handler);
    saveTickerPrefs(new Set(['fx:USD']));
    expect(handler).toHaveBeenCalledTimes(1);
    window.removeEventListener(TICKER_PREFS_EVENT, handler);
  });

  it('kaydedileni readTickerPrefs gerçekten geri okuyabilir (round-trip)', () => {
    saveTickerPrefs(new Set(['fx:USD', 'eco:ppi']));
    const s = readTickerPrefs();
    expect([...s].sort()).toEqual(['eco:ppi', 'fx:USD'].sort());
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// readCustomTickerItems
// ─────────────────────────────────────────────────────────────────────────────
describe('readCustomTickerItems', () => {
  it('kayıt yoksa boş dizi döner', () => {
    expect(readCustomTickerItems()).toEqual([]);
  });

  it('kayıtlı dizi varsa olduğu gibi döner', () => {
    const list = [{ assetType: 'STOCK', symbol: 'THYAO', name: 'Türk Hava Yolları' }];
    localStorage.setItem(TICKER_CUSTOM_KEY, JSON.stringify(list));
    expect(readCustomTickerItems()).toEqual(list);
  });

  it('kayıtlı değer dizi değilse (obje) boş dizi döner', () => {
    localStorage.setItem(TICKER_CUSTOM_KEY, JSON.stringify({ not: 'array' }));
    expect(readCustomTickerItems()).toEqual([]);
  });

  it('bozuk JSON kayıtlıysa boş diziye düşer (prefGet fallback)', () => {
    localStorage.setItem(TICKER_CUSTOM_KEY, 'bozuk');
    expect(readCustomTickerItems()).toEqual([]);
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// saveCustomTickerItems
// ─────────────────────────────────────────────────────────────────────────────
describe('saveCustomTickerItems', () => {
  it('listeyi localStorage’a JSON olarak yazar', () => {
    const list = [{ assetType: 'CRYPTO', symbol: 'BTC', name: 'Bitcoin' }];
    saveCustomTickerItems(list);
    expect(JSON.parse(localStorage.getItem(TICKER_CUSTOM_KEY))).toEqual(list);
  });

  it('TICKER_PREFS_EVENT yayınlar', () => {
    const handler = vi.fn();
    window.addEventListener(TICKER_PREFS_EVENT, handler);
    saveCustomTickerItems([]);
    expect(handler).toHaveBeenCalledTimes(1);
    window.removeEventListener(TICKER_PREFS_EVENT, handler);
  });

  it('kaydedileni readCustomTickerItems geri okuyabilir (round-trip)', () => {
    const list = [{ assetType: 'FUND', symbol: 'AFA', name: 'Fon' }];
    saveCustomTickerItems(list);
    expect(readCustomTickerItems()).toEqual(list);
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// readTickerSpeed
// ─────────────────────────────────────────────────────────────────────────────
describe('readTickerSpeed', () => {
  it('kayıt yoksa varsayılan normal döner', () => {
    expect(readTickerSpeed()).toBe('normal');
  });

  it('geçerli her hız değerini olduğu gibi döner', () => {
    TICKER_SPEED_VALUES.forEach((v) => {
      localStorage.setItem(TICKER_SPEED_KEY, JSON.stringify(v));
      expect(readTickerSpeed()).toBe(v);
    });
  });

  it('geçersiz/bilinmeyen değer kayıtlıysa normal’e düşer', () => {
    localStorage.setItem(TICKER_SPEED_KEY, JSON.stringify('turbo'));
    expect(readTickerSpeed()).toBe('normal');
  });

  it('null kayıtlıysa normal’e düşer', () => {
    localStorage.setItem(TICKER_SPEED_KEY, JSON.stringify(null));
    expect(readTickerSpeed()).toBe('normal');
  });

  it('bozuk JSON kayıtlıysa (prefGet fallback null) normal’e düşer', () => {
    localStorage.setItem(TICKER_SPEED_KEY, 'bozuk');
    expect(readTickerSpeed()).toBe('normal');
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// saveTickerSpeed
// ─────────────────────────────────────────────────────────────────────────────
describe('saveTickerSpeed', () => {
  it('geçerli değeri localStorage’a JSON olarak yazar', () => {
    saveTickerSpeed('fast');
    expect(JSON.parse(localStorage.getItem(TICKER_SPEED_KEY))).toBe('fast');
  });

  it('geçersiz değeri normal’e sabitleyerek yazar (allow-list dışı)', () => {
    saveTickerSpeed('turbo');
    expect(JSON.parse(localStorage.getItem(TICKER_SPEED_KEY))).toBe('normal');
  });

  it('undefined verilince normal yazar', () => {
    saveTickerSpeed(undefined);
    expect(JSON.parse(localStorage.getItem(TICKER_SPEED_KEY))).toBe('normal');
  });

  it('TICKER_PREFS_EVENT yayınlar', () => {
    const handler = vi.fn();
    window.addEventListener(TICKER_PREFS_EVENT, handler);
    saveTickerSpeed('slow');
    expect(handler).toHaveBeenCalledTimes(1);
    window.removeEventListener(TICKER_PREFS_EVENT, handler);
  });

  it('kaydedileni readTickerSpeed geri okuyabilir (round-trip)', () => {
    saveTickerSpeed('slow');
    expect(readTickerSpeed()).toBe('slow');
  });

  it('anonim kullanıcıda (auth_token yok) ağ hatası fırlatmaz', () => {
    // prefSetSync auth_token yokken Promise.resolve() döner; saveTickerSpeed .catch ile
    // sarar. Senkron çağrının istisna atmadığını doğrula.
    expect(() => saveTickerSpeed('fast')).not.toThrow();
  });
});
