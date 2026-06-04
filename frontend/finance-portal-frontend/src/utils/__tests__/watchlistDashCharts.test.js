import { describe, it, expect, beforeEach, vi } from 'vitest';
import {
  DASH_WL_CHARTS_KEY,
  DASH_WL_EVENT,
  readWlCharts,
  saveWlCharts,
  addWlChart,
  removeWlChart,
} from '../watchlistDashCharts';

// Bu modül anonim kullanıcı için yalnızca localStorage'a dokunur (auth_token yok → sunucu
// senkronu tetiklenmez), dolayısıyla prefGet/prefSet'i mock'lamadan gerçek hâliyle kullanıyoruz.
// jsdom localStorage + window olaylarını sağlar.
beforeEach(() => {
  localStorage.clear();
});

describe('sabitler', () => {
  it('beklenen anahtar ve olay adlarını dışa aktarır', () => {
    expect(DASH_WL_CHARTS_KEY).toBe('fp-dashboard-wl-charts');
    expect(DASH_WL_EVENT).toBe('fp-dashboard-wl-changed');
  });
});

describe('readWlCharts', () => {
  it('hiçbir şey kayıtlı değilse boş dizi döner', () => {
    expect(readWlCharts()).toEqual([]);
  });

  it('kayıtlı dizi değerini olduğu gibi döner', () => {
    const list = [{ watchlistId: 1, watchlistName: 'A', chartKind: 'line' }];
    localStorage.setItem(DASH_WL_CHARTS_KEY, JSON.stringify(list));
    expect(readWlCharts()).toEqual(list);
  });

  it('kayıtlı değer dizi değilse (obje) boş dizi döner', () => {
    localStorage.setItem(DASH_WL_CHARTS_KEY, JSON.stringify({ foo: 'bar' }));
    expect(readWlCharts()).toEqual([]);
  });

  it('kayıtlı değer dizi değilse (sayı/string/null) boş dizi döner', () => {
    localStorage.setItem(DASH_WL_CHARTS_KEY, JSON.stringify(42));
    expect(readWlCharts()).toEqual([]);

    localStorage.setItem(DASH_WL_CHARTS_KEY, JSON.stringify('selam'));
    expect(readWlCharts()).toEqual([]);

    localStorage.setItem(DASH_WL_CHARTS_KEY, JSON.stringify(null));
    expect(readWlCharts()).toEqual([]);
  });

  it('bozuk JSON için (prefGet yutar) boş dizi döner', () => {
    localStorage.setItem(DASH_WL_CHARTS_KEY, '{bozuk');
    expect(readWlCharts()).toEqual([]);
  });
});

describe('saveWlCharts', () => {
  it('listeyi localStorage\'a JSON olarak yazar', () => {
    const list = [{ watchlistId: 7, watchlistName: 'X', chartKind: 'candle' }];
    saveWlCharts(list);
    expect(JSON.parse(localStorage.getItem(DASH_WL_CHARTS_KEY))).toEqual(list);
    // readWlCharts ile tur dönüşü doğrulanır
    expect(readWlCharts()).toEqual(list);
  });

  it('değişiklik olayını (DASH_WL_EVENT) yayınlar', () => {
    const handler = vi.fn();
    window.addEventListener(DASH_WL_EVENT, handler);
    try {
      saveWlCharts([]);
      expect(handler).toHaveBeenCalledTimes(1);
    } finally {
      window.removeEventListener(DASH_WL_EVENT, handler);
    }
  });

  it('boş liste de yazılabilir', () => {
    saveWlCharts([{ watchlistId: 1, chartKind: 'line' }]);
    saveWlCharts([]);
    expect(readWlCharts()).toEqual([]);
  });
});

describe('addWlChart', () => {
  it('yeni öğe ekler ve true döner', () => {
    const item = { watchlistId: 1, watchlistName: 'A', chartKind: 'line' };
    expect(addWlChart(item)).toBe(true);
    expect(readWlCharts()).toEqual([item]);
  });

  it('aynı watchlistId + chartKind varsa eklemez ve false döner', () => {
    const item = { watchlistId: 1, watchlistName: 'A', chartKind: 'line' };
    addWlChart(item);
    // İsim farklı olsa bile id+kind eşleşmesi yeterli → kopya sayılır.
    const dup = { watchlistId: 1, watchlistName: 'Başka', chartKind: 'line' };
    expect(addWlChart(dup)).toBe(false);
    expect(readWlCharts()).toEqual([item]); // liste değişmedi
  });

  it('aynı watchlistId ama farklı chartKind ise yeni öğe olarak ekler', () => {
    const a = { watchlistId: 1, watchlistName: 'A', chartKind: 'line' };
    const b = { watchlistId: 1, watchlistName: 'A', chartKind: 'candle' };
    expect(addWlChart(a)).toBe(true);
    expect(addWlChart(b)).toBe(true);
    expect(readWlCharts()).toEqual([a, b]);
  });

  it('aynı chartKind ama farklı watchlistId ise yeni öğe olarak ekler', () => {
    const a = { watchlistId: 1, chartKind: 'line' };
    const b = { watchlistId: 2, chartKind: 'line' };
    expect(addWlChart(a)).toBe(true);
    expect(addWlChart(b)).toBe(true);
    expect(readWlCharts()).toEqual([a, b]);
  });

  it('ekleme sırasında değişiklik olayını yayınlar; kopyada yayınlamaz', () => {
    const handler = vi.fn();
    window.addEventListener(DASH_WL_EVENT, handler);
    try {
      const item = { watchlistId: 5, chartKind: 'line' };
      addWlChart(item);          // ekler → 1 olay
      addWlChart(item);          // kopya → erken return, olay yok
      expect(handler).toHaveBeenCalledTimes(1);
    } finally {
      window.removeEventListener(DASH_WL_EVENT, handler);
    }
  });
});

describe('removeWlChart', () => {
  it('eşleşen öğeyi (id + kind) listeden çıkarır', () => {
    const a = { watchlistId: 1, chartKind: 'line' };
    const b = { watchlistId: 2, chartKind: 'candle' };
    saveWlCharts([a, b]);
    removeWlChart(1, 'line');
    expect(readWlCharts()).toEqual([b]);
  });

  it('yalnızca id eşleşip kind eşleşmezse hiçbir şeyi çıkarmaz', () => {
    const a = { watchlistId: 1, chartKind: 'line' };
    saveWlCharts([a]);
    removeWlChart(1, 'candle'); // kind farklı → eşleşme yok
    expect(readWlCharts()).toEqual([a]);
  });

  it('yalnızca kind eşleşip id eşleşmezse hiçbir şeyi çıkarmaz', () => {
    const a = { watchlistId: 1, chartKind: 'line' };
    saveWlCharts([a]);
    removeWlChart(2, 'line'); // id farklı → eşleşme yok
    expect(readWlCharts()).toEqual([a]);
  });

  it('bilinmeyen öğe için liste değişmez', () => {
    const a = { watchlistId: 1, chartKind: 'line' };
    saveWlCharts([a]);
    removeWlChart(99, 'area');
    expect(readWlCharts()).toEqual([a]);
  });

  it('boş listede çağrılırsa boş kalır', () => {
    removeWlChart(1, 'line');
    expect(readWlCharts()).toEqual([]);
  });

  it('aynı id+kind\'den birden fazla varsa hepsini çıkarır', () => {
    const a = { watchlistId: 1, chartKind: 'line' };
    saveWlCharts([a, { ...a }, { watchlistId: 2, chartKind: 'line' }]);
    removeWlChart(1, 'line');
    expect(readWlCharts()).toEqual([{ watchlistId: 2, chartKind: 'line' }]);
  });

  it('çıkarma sırasında değişiklik olayını yayınlar', () => {
    const handler = vi.fn();
    saveWlCharts([{ watchlistId: 1, chartKind: 'line' }]);
    window.addEventListener(DASH_WL_EVENT, handler);
    try {
      removeWlChart(1, 'line');
      expect(handler).toHaveBeenCalledTimes(1);
    } finally {
      window.removeEventListener(DASH_WL_EVENT, handler);
    }
  });
});
