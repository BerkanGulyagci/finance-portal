import { describe, it, expect, beforeEach, vi } from 'vitest';
import { renderHook, act, waitFor } from '@testing-library/react';

// useChartDrawings'in TEK dış bağımlılığı '../../api/prefs' (prefGet/prefSet).
// Bunları mock'layarak localStorage/ağ yan etkilerinden bağımsız, çağrıları gözlemlenebilir kılıyoruz.
vi.mock('../../api/prefs', () => ({
  prefGet: vi.fn(),
  prefSet: vi.fn(),
}));

import { prefGet, prefSet } from '../../api/prefs';
import { useChartDrawings } from '../useChartDrawings';

// ─────────────────────────────────────────────────────────────────────────────
// Sahte klinecharts instance: hook'un kullandığı createOverlay/getOverlayById/
// removeOverlay metodlarını taklit eder. createOverlay her çağrıda artan bir id
// döndürür ve verilen overlay'i bir map'te saklar ki getOverlayById okuyabilsin.
// ─────────────────────────────────────────────────────────────────────────────
function makeChart(overrides = {}) {
  const overlays = new Map();
  let seq = 0;
  const chart = {
    overlays,
    createOverlay: vi.fn((cfg) => {
      const id = `ov_${++seq}`;
      // points verilmemişse (yeni çizim) boş noktayla başlar.
      overlays.set(id, { id, name: cfg?.name, points: cfg?.points || [] });
      return id;
    }),
    getOverlayById: vi.fn((id) => overlays.get(id) || null),
    removeOverlay: vi.fn(() => overlays.clear()),
    ...overrides,
  };
  return chart;
}

// Varsayılan persistKey — testler destructure etmeden de bu sabite referans verebilir.
const persistKey = 'chart-overlays:test';

/** Hook'u verili chart/persistKey ile kurar; chartRef.current'a chart yerleştirir. */
function setup({ chart = makeChart(), persistKey: pk = persistKey, chartId = null } = {}) {
  const chartRef = { current: chart };
  const chartIdRef = { current: chartId };
  const view = renderHook(() => useChartDrawings({ chartRef, chartIdRef, persistKey: pk }));
  return { ...view, chart, chartRef, chartIdRef, persistKey: pk };
}

beforeEach(() => {
  localStorage.clear();
  vi.clearAllMocks();
  vi.useRealTimers();
  prefGet.mockReturnValue([]); // varsayılan: kayıtlı çizim yok
});

// ─────────────────────────────────────────────────────────────────────────────
// Başlangıç durumu + döndürülen API yüzeyi
// ─────────────────────────────────────────────────────────────────────────────
describe('useChartDrawings — başlangıç durumu', () => {
  it('activeTool başlangıçta null olur', () => {
    const { result } = setup();
    expect(result.current.activeTool).toBeNull();
  });

  it('beklenen fonksiyon/alanları döndürür', () => {
    const { result } = setup();
    expect(typeof result.current.setActiveTool).toBe('function');
    expect(typeof result.current.handleSelectTool).toBe('function');
    expect(typeof result.current.handleDeleteSelected).toBe('function');
    expect(typeof result.current.handleClearAll).toBe('function');
    expect(typeof result.current.restoreOverlays).toBe('function');
    expect(result.current.overlayEvents).toEqual(
      expect.objectContaining({
        onDrawEnd: expect.any(Function),
        onPressedMoveEnd: expect.any(Function),
        onRemoved: expect.any(Function),
      }),
    );
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// setActiveTool — ham state setter
// ─────────────────────────────────────────────────────────────────────────────
describe('useChartDrawings — setActiveTool', () => {
  it('activeTool değerini doğrudan günceller', () => {
    const { result } = setup();
    act(() => result.current.setActiveTool('rayLine'));
    expect(result.current.activeTool).toBe('rayLine');
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// handleSelectTool
// ─────────────────────────────────────────────────────────────────────────────
describe('useChartDrawings — handleSelectTool', () => {
  it('toolId verilince activeTool set edilir ve createOverlay doğru name+olaylarla çağrılır', () => {
    const { result, chart } = setup();
    act(() => result.current.handleSelectTool('segment'));
    expect(result.current.activeTool).toBe('segment');
    expect(chart.createOverlay).toHaveBeenCalledTimes(1);
    const cfg = chart.createOverlay.mock.calls[0][0];
    expect(cfg.name).toBe('segment');
    // overlayEvents callback'leri config'e yayılmış olmalı.
    expect(typeof cfg.onDrawEnd).toBe('function');
    expect(typeof cfg.onPressedMoveEnd).toBe('function');
    expect(typeof cfg.onRemoved).toBe('function');
  });

  it('createOverlay dizi id döndürdüğünde ilk elemanı canlı id olarak kullanır (oid[0])', () => {
    const chart = makeChart({ createOverlay: vi.fn(() => ['arr_id_1', 'arr_id_2']) });
    // Dizi id'li overlay'i getOverlayById'nin bulabilmesi için map'e elle ekle.
    chart.overlays.set('arr_id_1', { id: 'arr_id_1', name: 'priceLine', points: [{ timestamp: 5, value: 1 }] });
    const { result } = setup({ chart });
    act(() => result.current.handleSelectTool('priceLine'));
    // Snapshot tetikleyip ilk dizi elemanının takip edildiğini doğrula.
    act(() => { result.current.overlayEvents.onDrawEnd(); });
    expect(prefSet).toHaveBeenCalled();
    const lastArg = prefSet.mock.calls.at(-1)[1];
    expect(lastArg).toEqual([{ name: 'priceLine', points: [{ timestamp: 5, value: 1 }] }]);
  });

  it('createOverlay falsy id döndürürse canlı listeye eklenmez (snapshot boş kalır)', () => {
    const chart = makeChart({ createOverlay: vi.fn(() => null) });
    const { result } = setup({ chart });
    act(() => result.current.handleSelectTool('segment'));
    // Canlı id eklenmediği için snapshot hiçbir rendered overlay görmez → force olmadan yazmaz.
    act(() => { result.current.overlayEvents.onDrawEnd(); });
    expect(prefSet).not.toHaveBeenCalled();
  });

  it('toolId falsy ise (null) sadece state set edilir, createOverlay çağrılmaz', () => {
    const { result, chart } = setup();
    act(() => result.current.handleSelectTool(null));
    expect(result.current.activeTool).toBeNull();
    expect(chart.createOverlay).not.toHaveBeenCalled();
  });

  it('chartRef.current yoksa createOverlay denenmez ama activeTool yine de set edilir', () => {
    const chartRef = { current: null };
    const chartIdRef = { current: null };
    const { result } = renderHook(() =>
      useChartDrawings({ chartRef, chartIdRef, persistKey: 'k' }),
    );
    act(() => result.current.handleSelectTool('segment'));
    expect(result.current.activeTool).toBe('segment');
    // Hata fırlatmamalı; başka gözlemlenecek yan etki yok.
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// persistSnapshot (overlayEvents.onDrawEnd / onPressedMoveEnd üzerinden tetiklenir)
// ─────────────────────────────────────────────────────────────────────────────
describe('useChartDrawings — persistSnapshot (snapshot kaydetme)', () => {
  it('canlı overlay noktalarını {name, points:[{timestamp,value}]} biçiminde prefSet ile yazar', () => {
    const { result, chart } = setup();
    act(() => result.current.handleSelectTool('segment'));
    // createOverlay map'e boş noktayla ekledi → noktaları dolduralım.
    const id = chart.createOverlay.mock.results[0].value;
    chart.overlays.set(id, {
      id,
      name: 'segment',
      points: [
        { timestamp: 100, value: 10, extra: 'atılmalı' },
        { timestamp: 200, value: 20 },
      ],
    });
    act(() => { result.current.overlayEvents.onDrawEnd(); });
    expect(prefSet).toHaveBeenCalledWith(persistKey, [
      { name: 'segment', points: [{ timestamp: 100, value: 10 }, { timestamp: 200, value: 20 }] },
    ]);
  });

  it('onDrawEnd / onPressedMoveEnd / onRemoved her zaman false döndürür', () => {
    const { result } = setup();
    expect(result.current.overlayEvents.onDrawEnd()).toBe(false);
    expect(result.current.overlayEvents.onPressedMoveEnd()).toBe(false);
    expect(result.current.overlayEvents.onRemoved({ overlay: { id: 'x' } })).toBe(false);
  });

  it('boş noktalı (henüz çizilmemiş) overlay rendered listesine girmez → force olmadan yazılmaz', () => {
    const { result } = setup();
    act(() => result.current.handleSelectTool('segment')); // points: []
    act(() => { result.current.overlayEvents.onPressedMoveEnd(); });
    expect(prefSet).not.toHaveBeenCalled();
  });

  it('getOverlayById null dönen (silinmiş) canlı id\'ler temizlenir ve onRemoved force ile boş yazar', () => {
    const { result, chart } = setup();
    act(() => result.current.handleSelectTool('segment'));
    const id = chart.createOverlay.mock.results[0].value;
    chart.overlays.delete(id); // overlay grafikten kalktı → getOverlayById null
    // onRemoved force=true ile çağırır → dead temizlenir, full=[] olsa bile yazılır.
    act(() => { result.current.overlayEvents.onRemoved({ overlay: { id } }); });
    expect(prefSet).toHaveBeenCalledWith(persistKey, []);
  });

  it('canlı id hâlâ listedeyken getOverlayById null dönerse dead branch onu listeden çıkarır', () => {
    // İki overlay: biri dolu (canlı), biri grafikten kaybolmuş (getOverlayById null) ama hâlâ liveIds'te.
    const { result, chart } = setup();
    act(() => result.current.handleSelectTool('segment')); // ov_1
    act(() => result.current.handleSelectTool('segment')); // ov_2
    const id1 = chart.createOverlay.mock.results[0].value;
    const id2 = chart.createOverlay.mock.results[1].value;
    chart.overlays.set(id1, { id: id1, name: 'segment', points: [{ timestamp: 1, value: 1 }] });
    chart.overlays.delete(id2); // id2 grafikten kalktı (null) ama liveIds'te kaldı → dead.push
    // İlk snapshot: id2 dead olarak temizlenir, id1 yazılır.
    act(() => { result.current.overlayEvents.onDrawEnd(); });
    expect(prefSet).toHaveBeenLastCalledWith(persistKey, [
      { name: 'segment', points: [{ timestamp: 1, value: 1 }] },
    ]);
    // id2 artık liveIds'te olmamalı: id1'i de silip force snapshot al → boş kalmalı.
    chart.overlays.delete(id1);
    prefSet.mockClear();
    act(() => { result.current.overlayEvents.onRemoved({}); }); // force, liveIds yalnız id1'di o da null
    expect(prefSet).toHaveBeenLastCalledWith(persistKey, []);
  });

  it('onRemoved overlay id\'sini canlı listeden siler (sonraki snapshot onu içermez)', () => {
    const { result, chart } = setup();
    act(() => result.current.handleSelectTool('segment'));
    const id = chart.createOverlay.mock.results[0].value;
    chart.overlays.set(id, { id, name: 'segment', points: [{ timestamp: 1, value: 1 }] });
    // Önce id'yi canlı listeden çıkar (onRemoved), sonra snapshot al.
    act(() => { result.current.overlayEvents.onRemoved({ overlay: { id } }); });
    prefSet.mockClear();
    act(() => { result.current.overlayEvents.onDrawEnd(); });
    // Canlı id kalmadığı için rendered boş → force olmayan onDrawEnd yazmaz.
    expect(prefSet).not.toHaveBeenCalledWith(persistKey, expect.arrayContaining([
      expect.objectContaining({ name: 'segment' }),
    ]));
  });

  it('onRemoved e.overlay.id yoksa hata vermez (opsiyonel zincir) ve yine force yazar', () => {
    const { result, persistKey } = setup();
    act(() => { result.current.overlayEvents.onRemoved({}); });
    expect(prefSet).toHaveBeenCalledWith(persistKey, []);
    prefSet.mockClear();
    // e tamamen undefined dahi olsa patlamamalı.
    expect(() => act(() => { result.current.overlayEvents.onRemoved(undefined); })).not.toThrow();
  });

  it('chart yoksa snapshot no-op (prefSet çağrılmaz)', () => {
    const chartRef = { current: null };
    const chartIdRef = { current: null };
    const { result } = renderHook(() =>
      useChartDrawings({ chartRef, chartIdRef, persistKey: 'k' }),
    );
    act(() => { result.current.overlayEvents.onDrawEnd(); });
    expect(prefSet).not.toHaveBeenCalled();
  });

  it('persistKey yoksa snapshot no-op (prefSet çağrılmaz)', () => {
    const { result } = setup({ persistKey: '' });
    act(() => { result.current.overlayEvents.onDrawEnd(); });
    expect(prefSet).not.toHaveBeenCalled();
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// clearAll / handleDeleteSelected / handleClearAll
// ─────────────────────────────────────────────────────────────────────────────
describe('useChartDrawings — temizleme akışları', () => {
  it('handleClearAll: removeOverlay çağırır, prefSet ile boş yazar ve activeTool null olur', () => {
    const { result, chart } = setup();
    act(() => result.current.handleSelectTool('segment')); // activeTool='segment'
    act(() => result.current.handleClearAll());
    expect(chart.removeOverlay).toHaveBeenCalledTimes(1);
    expect(prefSet).toHaveBeenLastCalledWith(persistKey, []);
    expect(result.current.activeTool).toBeNull();
  });

  it('handleDeleteSelected: temizler + boş yazar ANCAK activeTool\'u sıfırlamaz', () => {
    const { result, chart } = setup();
    act(() => result.current.handleSelectTool('segment'));
    act(() => result.current.handleDeleteSelected());
    expect(chart.removeOverlay).toHaveBeenCalledTimes(1);
    expect(prefSet).toHaveBeenLastCalledWith(persistKey, []);
    // clearAll activeTool'a dokunmaz; handleDeleteSelected de değiştirmez.
    expect(result.current.activeTool).toBe('segment');
  });

  it('removeOverlay fırlatsa bile hata yutulur ve state yine temizlenir', () => {
    const chart = makeChart({ removeOverlay: vi.fn(() => { throw new Error('boom'); }) });
    const { result, persistKey } = setup({ chart });
    act(() => result.current.handleSelectTool('segment'));
    expect(() => act(() => result.current.handleClearAll())).not.toThrow();
    expect(prefSet).toHaveBeenLastCalledWith(persistKey, []);
    expect(result.current.activeTool).toBeNull();
  });

  it('persistKey yoksa temizlemede prefSet çağrılmaz (yine de removeOverlay çalışır)', () => {
    const { result, chart } = setup({ persistKey: '' });
    act(() => result.current.handleClearAll());
    expect(chart.removeOverlay).toHaveBeenCalledTimes(1);
    expect(prefSet).not.toHaveBeenCalled();
  });

  it('chartRef.current null iken temizleme hata vermez (opsiyonel zincir) ve boş yazar', () => {
    const chartRef = { current: null };
    const chartIdRef = { current: null };
    const { result } = renderHook(() =>
      useChartDrawings({ chartRef, chartIdRef, persistKey: 'k' }),
    );
    expect(() => act(() => result.current.handleClearAll())).not.toThrow();
    expect(prefSet).toHaveBeenLastCalledWith('k', []);
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// restoreOverlays
// ─────────────────────────────────────────────────────────────────────────────
describe('useChartDrawings — restoreOverlays', () => {
  it('chartRef.current yoksa no-op (prefGet/createOverlay çağrılmaz)', () => {
    const chartRef = { current: null };
    const chartIdRef = { current: null };
    const { result } = renderHook(() =>
      useChartDrawings({ chartRef, chartIdRef, persistKey: 'k' }),
    );
    act(() => result.current.restoreOverlays([{ timestamp: 1 }]));
    expect(prefGet).not.toHaveBeenCalled();
  });

  it('persistKey yoksa no-op', () => {
    const { result, chart } = setup({ persistKey: '' });
    act(() => result.current.restoreOverlays([{ timestamp: 1 }]));
    expect(prefGet).not.toHaveBeenCalled();
    expect(chart.createOverlay).not.toHaveBeenCalled();
  });

  it('pencere İÇİNDEKİ kayıtlı çizimleri createOverlay ile yeniden çizer (name+points iletir)', () => {
    prefGet.mockReturnValue([
      { name: 'segment', points: [{ timestamp: 150, value: 5 }] },
    ]);
    const klineData = [{ timestamp: 100 }, { timestamp: 200 }];
    const { result, chart } = setup();
    act(() => result.current.restoreOverlays(klineData));
    expect(prefGet).toHaveBeenCalledWith(persistKey, []);
    expect(chart.createOverlay).toHaveBeenCalledTimes(1);
    const cfg = chart.createOverlay.mock.calls[0][0];
    expect(cfg.name).toBe('segment');
    expect(cfg.points).toEqual([{ timestamp: 150, value: 5 }]);
  });

  it('pencere DIŞINDAKİ çizimler çizilmez, dormant olarak saklanır → sonraki snapshot\'ta korunur', () => {
    const dormant = { name: 'segment', points: [{ timestamp: 9999, value: 1 }] };
    prefGet.mockReturnValue([dormant]);
    const klineData = [{ timestamp: 100 }, { timestamp: 200 }];
    const { result, chart } = setup();
    act(() => result.current.restoreOverlays(klineData));
    // Pencere dışı → createOverlay HİÇ çağrılmaz.
    expect(chart.createOverlay).not.toHaveBeenCalled();
    // Dormant veriyi snapshot'ın koruduğunu doğrula: force snapshot tetikle.
    act(() => { result.current.overlayEvents.onRemoved({}); }); // force=true
    expect(prefSet).toHaveBeenLastCalledWith(persistKey, [dormant]);
  });

  it('zaman damgası OLMAYAN çizim (hasTs=false) her zaman pencere içi sayılır ve çizilir', () => {
    prefGet.mockReturnValue([{ name: 'priceLine', points: [{ value: 42 }] }]);
    const { result, chart } = setup();
    act(() => result.current.restoreOverlays([{ timestamp: 100 }, { timestamp: 200 }]));
    expect(chart.createOverlay).toHaveBeenCalledTimes(1);
  });

  it('klineData boş dizi iken dataMin=-∞/dataMax=+∞ olur → zaman damgalı çizim de pencere içi sayılır', () => {
    prefGet.mockReturnValue([{ name: 'segment', points: [{ timestamp: 12345, value: 1 }] }]);
    const { result, chart } = setup();
    act(() => result.current.restoreOverlays([]));
    expect(chart.createOverlay).toHaveBeenCalledTimes(1);
  });

  it('klineData dizi DEĞİLSE (null) yine sonsuz pencere kullanılır, hata vermez', () => {
    prefGet.mockReturnValue([{ name: 'segment', points: [{ timestamp: 1, value: 1 }] }]);
    const { result, chart } = setup();
    expect(() => act(() => result.current.restoreOverlays(null))).not.toThrow();
    expect(chart.createOverlay).toHaveBeenCalledTimes(1);
  });

  it('prefGet null/dizi-olmayan döndürürse güvenle boş listeye düşer (çizim yok, hata yok)', () => {
    prefGet.mockReturnValue(null);
    const { result, chart } = setup();
    expect(() => act(() => result.current.restoreOverlays([{ timestamp: 1 }]))).not.toThrow();
    expect(chart.createOverlay).not.toHaveBeenCalled();
  });

  it('kayıtlı kayıtta points alanı yoksa (undefined) hata vermez, çizilir (hasTs=false → pencere içi)', () => {
    prefGet.mockReturnValue([{ name: 'priceLine' }]);
    const { result, chart } = setup();
    expect(() => act(() => result.current.restoreOverlays([{ timestamp: 100 }]))).not.toThrow();
    expect(chart.createOverlay).toHaveBeenCalledTimes(1);
  });

  it('createOverlay falsy id döndürürse o çizim dormant\'a düşer (snapshot ile korunur)', () => {
    const rec = { name: 'segment', points: [{ timestamp: 150, value: 5 }] };
    prefGet.mockReturnValue([rec]);
    const chart = makeChart({ createOverlay: vi.fn(() => null) });
    const { result, persistKey } = setup({ chart });
    act(() => result.current.restoreOverlays([{ timestamp: 100 }, { timestamp: 200 }]));
    // Falsy id → liveIds'e eklenmez, dormant'a eklenir → force snapshot onu yazar.
    act(() => { result.current.overlayEvents.onRemoved({}); });
    expect(prefSet).toHaveBeenLastCalledWith(persistKey, [rec]);
  });

  it('createOverlay fırlatırsa (catch) çizim dormant\'a düşer, restore patlamaz', () => {
    const rec = { name: 'segment', points: [{ timestamp: 150, value: 5 }] };
    prefGet.mockReturnValue([rec]);
    const chart = makeChart({ createOverlay: vi.fn(() => { throw new Error('x'); }) });
    const { result, persistKey } = setup({ chart });
    expect(() => act(() => result.current.restoreOverlays([{ timestamp: 100 }, { timestamp: 200 }]))).not.toThrow();
    act(() => { result.current.overlayEvents.onRemoved({}); });
    expect(prefSet).toHaveBeenLastCalledWith(persistKey, [rec]);
  });

  it('createOverlay dizi id döndürürse ilk eleman canlı sayılır (dormant\'a düşmez)', () => {
    const rec = { name: 'segment', points: [{ timestamp: 150, value: 7 }] };
    prefGet.mockReturnValue([rec]);
    const chart = makeChart({ createOverlay: vi.fn(() => ['only_id']) });
    chart.overlays.set('only_id', { id: 'only_id', name: 'segment', points: [{ timestamp: 150, value: 7 }] });
    const { result, persistKey } = setup({ chart });
    act(() => result.current.restoreOverlays([{ timestamp: 100 }, { timestamp: 200 }]));
    // Canlı kaydedildiyse snapshot dormant değil, canlı okunan noktayı yazar.
    act(() => { result.current.overlayEvents.onDrawEnd(); });
    expect(prefSet).toHaveBeenLastCalledWith(persistKey, [
      { name: 'segment', points: [{ timestamp: 150, value: 7 }] },
    ]);
  });

  it('her çağrıda canlı/dormant referanslarını sıfırlar (önceki restore izleri kalmaz)', () => {
    // 1) İlk restore: pencere dışı bir dormant ekler.
    prefGet.mockReturnValue([{ name: 'a', points: [{ timestamp: 9999, value: 1 }] }]);
    const { result, persistKey } = setup();
    act(() => result.current.restoreOverlays([{ timestamp: 100 }, { timestamp: 200 }]));
    // 2) İkinci restore: kayıt yok → dormant temizlenmeli.
    prefGet.mockReturnValue([]);
    act(() => result.current.restoreOverlays([{ timestamp: 100 }, { timestamp: 200 }]));
    prefSet.mockClear();
    act(() => { result.current.overlayEvents.onRemoved({}); }); // force snapshot
    expect(prefSet).toHaveBeenLastCalledWith(persistKey, []); // eski dormant kalmadı
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// mouseup/pointerup/touchend → 80ms debounce → persistSnapshot (useEffect)
// ─────────────────────────────────────────────────────────────────────────────
describe('useChartDrawings — DOM pointer olay dinleyicisi (debounce snapshot)', () => {
  it('chartIdRef element\'i pointerup\'ta 80ms sonra snapshot tetikler (canlı çizimi yazar)', () => {
    vi.useFakeTimers();
    // Grafik DOM düğümünü oluştur.
    const el = document.createElement('div');
    el.id = 'chart-dom';
    document.body.appendChild(el);
    const { result, chart } = setup({ chartId: 'chart-dom' });
    // Canlı bir overlay oluştur + noktalarını doldur.
    act(() => result.current.handleSelectTool('segment'));
    const id = chart.createOverlay.mock.results[0].value;
    chart.overlays.set(id, { id, name: 'segment', points: [{ timestamp: 5, value: 9 }] });
    prefSet.mockClear();
    // pointerup gönder → henüz 80ms dolmadı.
    el.dispatchEvent(new Event('pointerup', { bubbles: true }));
    expect(prefSet).not.toHaveBeenCalled();
    act(() => { vi.advanceTimersByTime(80); });
    expect(prefSet).toHaveBeenCalledWith(persistKey, [
      { name: 'segment', points: [{ timestamp: 5, value: 9 }] },
    ]);
    document.body.removeChild(el);
    vi.useRealTimers();
  });

  it('mouseup ve touchend de aynı debounce snapshot\'ı tetikler', () => {
    vi.useFakeTimers();
    const el = document.createElement('div');
    el.id = 'chart-dom2';
    document.body.appendChild(el);
    const { result, chart } = setup({ chartId: 'chart-dom2' });
    act(() => result.current.handleSelectTool('segment'));
    const id = chart.createOverlay.mock.results[0].value;
    chart.overlays.set(id, { id, name: 'segment', points: [{ timestamp: 1, value: 1 }] });

    prefSet.mockClear();
    el.dispatchEvent(new Event('mouseup', { bubbles: true }));
    act(() => { vi.advanceTimersByTime(80); });
    expect(prefSet).toHaveBeenCalledTimes(1);

    prefSet.mockClear();
    el.dispatchEvent(new Event('touchend', { bubbles: true }));
    act(() => { vi.advanceTimersByTime(80); });
    expect(prefSet).toHaveBeenCalledTimes(1);

    document.body.removeChild(el);
    vi.useRealTimers();
  });

  it('ardışık pointerup\'lar debounce edilir: tek snapshot (clearTimeout)', () => {
    vi.useFakeTimers();
    const el = document.createElement('div');
    el.id = 'chart-dom3';
    document.body.appendChild(el);
    const { result, chart } = setup({ chartId: 'chart-dom3' });
    act(() => result.current.handleSelectTool('segment'));
    const id = chart.createOverlay.mock.results[0].value;
    chart.overlays.set(id, { id, name: 'segment', points: [{ timestamp: 1, value: 1 }] });

    prefSet.mockClear();
    el.dispatchEvent(new Event('pointerup', { bubbles: true }));
    act(() => { vi.advanceTimersByTime(40); }); // henüz dolmadı
    el.dispatchEvent(new Event('pointerup', { bubbles: true })); // önceki timer iptal
    act(() => { vi.advanceTimersByTime(40); }); // ilk timer iptal edildi, bu da dolmadı
    expect(prefSet).not.toHaveBeenCalled();
    act(() => { vi.advanceTimersByTime(40); }); // ikinci timer dolar
    expect(prefSet).toHaveBeenCalledTimes(1);

    document.body.removeChild(el);
    vi.useRealTimers();
  });

  it('chartIdRef.current null ise dinleyici bağlanmaz (snapshot tetiklenmez), hata vermez', () => {
    vi.useFakeTimers();
    const { result } = setup({ chartId: null });
    // Hiçbir DOM olayı bağlanmadığı için zaman ilerlese de snapshot yok.
    act(() => { vi.advanceTimersByTime(200); });
    expect(prefSet).not.toHaveBeenCalled();
    // restoreOverlays gibi başka yollar hâlâ çalışmalı (sağlık kontrolü).
    expect(typeof result.current.restoreOverlays).toBe('function');
    vi.useRealTimers();
  });

  it('id verilse de DOM\'da öyle bir element yoksa dinleyici bağlanmaz, hata vermez', () => {
    vi.useFakeTimers();
    setup({ chartId: 'olmayan-id' });
    act(() => { vi.advanceTimersByTime(200); });
    expect(prefSet).not.toHaveBeenCalled();
    vi.useRealTimers();
  });

  it('unmount sonrası dinleyici sökülür: sonraki pointerup snapshot tetiklemez (cleanup)', () => {
    vi.useFakeTimers();
    const el = document.createElement('div');
    el.id = 'chart-dom4';
    document.body.appendChild(el);
    const { result, chart, unmount } = setup({ chartId: 'chart-dom4' });
    act(() => result.current.handleSelectTool('segment'));
    const id = chart.createOverlay.mock.results[0].value;
    chart.overlays.set(id, { id, name: 'segment', points: [{ timestamp: 1, value: 1 }] });

    unmount();
    prefSet.mockClear();
    el.dispatchEvent(new Event('pointerup', { bubbles: true }));
    act(() => { vi.advanceTimersByTime(200); });
    expect(prefSet).not.toHaveBeenCalled();

    document.body.removeChild(el);
    vi.useRealTimers();
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// async sağlık kontrolü (waitFor kalıbı) — debounce'lı snapshot gerçek zamanlayıcıyla
// ─────────────────────────────────────────────────────────────────────────────
describe('useChartDrawings — gerçek zamanlayıcıyla async snapshot', () => {
  it('pointerup sonrası (gerçek 80ms) snapshot eninde sonunda prefSet çağırır', async () => {
    const el = document.createElement('div');
    el.id = 'chart-dom-async';
    document.body.appendChild(el);
    const { result, chart } = setup({ chartId: 'chart-dom-async' });
    act(() => result.current.handleSelectTool('segment'));
    const id = chart.createOverlay.mock.results[0].value;
    chart.overlays.set(id, { id, name: 'segment', points: [{ timestamp: 2, value: 3 }] });

    prefSet.mockClear();
    el.dispatchEvent(new Event('pointerup', { bubbles: true }));
    await waitFor(() => {
      expect(prefSet).toHaveBeenCalledWith(persistKey, [
        { name: 'segment', points: [{ timestamp: 2, value: 3 }] },
      ]);
    });
    document.body.removeChild(el);
  });
});
