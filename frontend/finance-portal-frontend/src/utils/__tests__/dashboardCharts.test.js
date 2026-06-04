import { describe, it, expect, beforeEach, vi } from 'vitest';
import {
  DASH_PF_CHARTS_KEY,
  DASH_PF_EVENT,
  readPfCharts,
  savePfCharts,
  addPfChart,
  removePfChart,
} from '../dashboardCharts';

// Bu modül SAF mantık: prefGet/prefSet (../api/prefs) gerçek kullanılır.
// Test ortamında auth_token YOK → prefSet yalnızca localStorage'a yazar, ağ çağrısı yapmaz.
// localStorage ve window jsdom'da mevcut.

beforeEach(() => {
  localStorage.clear();
});

describe('dashboardCharts — sabitler', () => {
  it('beklenen localStorage anahtarı ve olay adı tanımlı', () => {
    expect(DASH_PF_CHARTS_KEY).toBe('fp-dashboard-pf-charts');
    expect(DASH_PF_EVENT).toBe('fp-dashboard-pf-changed');
  });
});

describe('readPfCharts', () => {
  it('hiç kayıt yoksa boş dizi döner', () => {
    expect(readPfCharts()).toEqual([]);
  });

  it('localStorage\'daki dizi değerini aynen döner', () => {
    const list = [{ portfolioId: 1, portfolioName: 'A', chartKey: 'alloc' }];
    localStorage.setItem(DASH_PF_CHARTS_KEY, JSON.stringify(list));
    expect(readPfCharts()).toEqual(list);
  });

  it('saklanan değer dizi değilse (obje) boş dizi döner', () => {
    localStorage.setItem(DASH_PF_CHARTS_KEY, JSON.stringify({ a: 1 }));
    expect(readPfCharts()).toEqual([]);
  });

  it('saklanan değer dizi değilse (string) boş dizi döner', () => {
    localStorage.setItem(DASH_PF_CHARTS_KEY, JSON.stringify('merhaba'));
    expect(readPfCharts()).toEqual([]);
  });

  it('saklanan değer dizi değilse (number) boş dizi döner', () => {
    localStorage.setItem(DASH_PF_CHARTS_KEY, JSON.stringify(42));
    expect(readPfCharts()).toEqual([]);
  });

  it('bozuk JSON için (prefGet catch → fallback) boş dizi döner', () => {
    localStorage.setItem(DASH_PF_CHARTS_KEY, '{bozuk json');
    expect(readPfCharts()).toEqual([]);
  });
});

describe('savePfCharts', () => {
  it('listeyi localStorage\'a JSON olarak yazar', () => {
    const list = [{ portfolioId: 7, portfolioName: 'X', chartKey: 'trend' }];
    savePfCharts(list);
    expect(JSON.parse(localStorage.getItem(DASH_PF_CHARTS_KEY))).toEqual(list);
    // yazdığını readPfCharts ile de doğrula
    expect(readPfCharts()).toEqual(list);
  });

  it('DASH_PF_EVENT olayını window üzerinde tetikler', () => {
    const handler = vi.fn();
    window.addEventListener(DASH_PF_EVENT, handler);
    savePfCharts([]);
    expect(handler).toHaveBeenCalledTimes(1);
    window.removeEventListener(DASH_PF_EVENT, handler);
  });

  it('boş liste yazılabilir', () => {
    savePfCharts([{ portfolioId: 1, chartKey: 'a' }]);
    savePfCharts([]);
    expect(readPfCharts()).toEqual([]);
  });
});

describe('addPfChart', () => {
  it('yeni öğe ekler ve true döner', () => {
    const item = { portfolioId: 1, portfolioName: 'A', chartKey: 'alloc' };
    expect(addPfChart(item)).toBe(true);
    expect(readPfCharts()).toEqual([item]);
  });

  it('aynı portfolioId + chartKey tekrar eklenmez, false döner', () => {
    const item = { portfolioId: 1, portfolioName: 'A', chartKey: 'alloc' };
    addPfChart(item);
    // portfolioName farklı olsa da (id+key aynı) eklenmez
    const dup = { portfolioId: 1, portfolioName: 'Farklı', chartKey: 'alloc' };
    expect(addPfChart(dup)).toBe(false);
    expect(readPfCharts()).toHaveLength(1);
    expect(readPfCharts()[0]).toEqual(item);
  });

  it('aynı portfolioId ama farklı chartKey ayrı öğe olarak eklenir', () => {
    addPfChart({ portfolioId: 1, chartKey: 'alloc' });
    expect(addPfChart({ portfolioId: 1, chartKey: 'trend' })).toBe(true);
    expect(readPfCharts()).toHaveLength(2);
  });

  it('aynı chartKey ama farklı portfolioId ayrı öğe olarak eklenir', () => {
    addPfChart({ portfolioId: 1, chartKey: 'alloc' });
    expect(addPfChart({ portfolioId: 2, chartKey: 'alloc' })).toBe(true);
    expect(readPfCharts()).toHaveLength(2);
  });

  it('ekleme DASH_PF_EVENT tetikler; tekrarda tetiklemez', () => {
    const handler = vi.fn();
    window.addEventListener(DASH_PF_EVENT, handler);
    addPfChart({ portfolioId: 9, chartKey: 'k' });
    expect(handler).toHaveBeenCalledTimes(1);
    // duplicate erken-return → savePfCharts çağrılmaz → olay yok
    addPfChart({ portfolioId: 9, chartKey: 'k' });
    expect(handler).toHaveBeenCalledTimes(1);
    window.removeEventListener(DASH_PF_EVENT, handler);
  });
});

describe('removePfChart', () => {
  it('eşleşen öğeyi listeden çıkarır', () => {
    addPfChart({ portfolioId: 1, chartKey: 'alloc' });
    addPfChart({ portfolioId: 2, chartKey: 'trend' });
    removePfChart(1, 'alloc');
    expect(readPfCharts()).toEqual([{ portfolioId: 2, chartKey: 'trend' }]);
  });

  it('yalnızca portfolioId+chartKey ikilisi tam eşleşeni çıkarır', () => {
    addPfChart({ portfolioId: 1, chartKey: 'alloc' });
    addPfChart({ portfolioId: 1, chartKey: 'trend' });
    // chartKey farklı → sadece biri silinir
    removePfChart(1, 'alloc');
    expect(readPfCharts()).toEqual([{ portfolioId: 1, chartKey: 'trend' }]);
  });

  it('eşleşme yoksa liste değişmeden kalır', () => {
    addPfChart({ portfolioId: 1, chartKey: 'alloc' });
    removePfChart(99, 'yok');
    expect(readPfCharts()).toEqual([{ portfolioId: 1, chartKey: 'alloc' }]);
  });

  it('boş listede çağrı güvenli — boş dizi kalır', () => {
    removePfChart(1, 'alloc');
    expect(readPfCharts()).toEqual([]);
  });

  it('çıkarma DASH_PF_EVENT tetikler', () => {
    addPfChart({ portfolioId: 1, chartKey: 'alloc' });
    const handler = vi.fn();
    window.addEventListener(DASH_PF_EVENT, handler);
    removePfChart(1, 'alloc');
    expect(handler).toHaveBeenCalledTimes(1);
    window.removeEventListener(DASH_PF_EVENT, handler);
  });
});
