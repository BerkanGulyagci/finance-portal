import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';

// ─────────────────────────────────────────────────────────────────────────────
// MOCK'lar — kaynağı (FxChart.jsx) GERÇEKTEN okuyarak belirlendi.
//
// 1) klinecharts: jsdom'da canvas YOK → init/dispose/registerOverlay stub'lanır.
//    init() bir chart instance döndürür; kaynak + useChartDrawings hook'u şunları
//    çağırır: setStyles / applyNewData / createIndicator / removeIndicator /
//    createOverlay / removeOverlay / getOverlayById. Hepsi vi.fn ile karşılanır.
//    createIndicator alt-pane id döndürmeli (indicatorPaneIds[name]=pid) →
//    'pane_x' döndürüyoruz. createOverlay overlay id döndürür → 'ov_1'.
//    getOverlayById null döndürür (snapshot'ta "ölü" sayılır, zararsız).
// 2) LanguageContext: GERÇEK LanguageProvider kullanılır (tr varsayılan, t(key)
//    anahtarın kendisini döndürür) → Türkçe metinleri/etiketleri aratabiliriz.
// 3) useChartDrawings hook'u GERÇEK kullanılır (sadece klinecharts + prefs/localStorage'a
//    dokunur). prefSet/prefGet localStorage tabanlı; auth_token YOK (beforeEach temizler)
//    → sunucuya HTTP gitmez, lib/http mock'lamaya gerek yok.
// 4) lucide-react ikonları jsdom'da SVG olarak sorunsuz render olur → mock yok.
//
// FxChart props-driven bir bileşendir: router YOK, doğrudan ağ/api çağrısı YOK
// (veriyi `chartPoints` prop'undan alır). Bu yüzden MemoryRouter/api mock gerekmez.
// ─────────────────────────────────────────────────────────────────────────────

// Aktif chart instance'ını testlerden erişilebilir tutmak için referans yakalarız.
let lastChart = null;
function makeChart() {
  lastChart = {
    applyNewData: vi.fn(),
    createIndicator: vi.fn(() => 'pane_x'),
    removeIndicator: vi.fn(),
    resize: vi.fn(),
    setStyles: vi.fn(),
    subscribeAction: vi.fn(),
    convertFromPixel: vi.fn(),
    createOverlay: vi.fn(() => 'ov_1'),
    removeOverlay: vi.fn(),
    getOverlayById: vi.fn(() => null),
  };
  return lastChart;
}

vi.mock('klinecharts', () => ({
  init: vi.fn(() => makeChart()),
  dispose: vi.fn(),
  registerOverlay: vi.fn(),
  registerIndicator: vi.fn(),
}));

import { init as klineInit, dispose as klineDispose } from 'klinecharts';
import { LanguageProvider } from '../../../../../context/LanguageContext';
import FxChart from '../FxChart';

// ─── Yardımcılar ──────────────────────────────────────────────────────────────

/** Kaynağın beklediği nokta şekli: { date, value }. buildKlineData close>0 (=value)
 *  ve geçerli tarih filtreler. */
function makePoints(count = 30, start = 30) {
  const pts = [];
  for (let i = 0; i < count; i++) {
    const d = new Date(2024, 0, 1 + i);
    pts.push({ date: d.toISOString().slice(0, 10), value: start + i * 0.1 });
  }
  return pts;
}

const BASE_PROPS = {
  symbol: 'USD',
  chartPoints: makePoints(30),
  lineColor: '#093eaa',
  mainLabel: 'USD/TRY',
  range: '1Y',
  onRangeChange: vi.fn(),
  loadingChart: false,
};

function renderChart(props = {}) {
  return render(
    <LanguageProvider>
      <FxChart {...BASE_PROPS} {...props} />
    </LanguageProvider>,
  );
}

beforeEach(() => {
  localStorage.clear();
  vi.clearAllMocks();
  lastChart = null;
  // jsdom'da olmayan global'ler — klinecharts mock'lansa da güvenli olsun diye.
  globalThis.ResizeObserver = class { observe() {} unobserve() {} disconnect() {} };
});

// ─────────────────────────────────────────────────────────────────────────────
// 1) Smoke + temel iskelet (zaman aralığı + çizim çubuğu her zaman render olur)
// ─────────────────────────────────────────────────────────────────────────────
describe('FxChart — render (smoke)', () => {
  it('mocklarla throw etmeden render olur ve çizim aracı grup butonları DOM\'da', () => {
    renderChart();
    expect(screen.getByRole('button', { name: /Çizgiler/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Kanallar/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Fibonacci/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Şekiller/ })).toBeInTheDocument();
  });

  it('İndikatör menüsü (slot) ve sil/temizle butonları (title) render edilir', () => {
    renderChart();
    expect(screen.getByRole('button', { name: /İndikatör/ })).toBeInTheDocument();
    expect(screen.getByTitle('Seçili çizimi sil')).toBeInTheDocument();
    expect(screen.getByTitle('Tüm çizimleri temizle')).toBeInTheDocument();
  });

  it('tüm zaman aralığı butonları (1H..10Y) render edilir', () => {
    renderChart();
    // RANGE_LABELS: 1W→1H, 1M→1A, 3M→3A, 6M→6A, 1Y→1Y, 5Y→5Y, 10Y→10Y
    ['1H', '1A', '3A', '6A', '5Y', '10Y'].forEach(lbl => {
      expect(screen.getByRole('button', { name: lbl })).toBeInTheDocument();
    });
    // '1Y' iki kez geçer (1Y range + 1Y label aynı) → en az bir tane bulunur
    expect(screen.getAllByRole('button', { name: '1Y' }).length).toBeGreaterThan(0);
  });

  it('dolu veri ile klinecharts.init + applyNewData çağrılır (canvas konteyneri mount)', () => {
    renderChart({ chartPoints: makePoints(30) });
    expect(klineInit).toHaveBeenCalled();
    expect(lastChart).not.toBeNull();
    expect(lastChart.applyNewData).toHaveBeenCalled();
    expect(lastChart.setStyles).toHaveBeenCalled();
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// 2) Koşullu render: boş veri / loading
// ─────────────────────────────────────────────────────────────────────────────
describe('FxChart — koşullu render dalları', () => {
  it('chartPoints BOŞ + loading=false ise "Grafik verisi yüklenemedi." gösterilir, init çağrılmaz', () => {
    renderChart({ chartPoints: [], loadingChart: false });
    expect(screen.getByText('Grafik verisi yüklenemedi.')).toBeInTheDocument();
    expect(klineInit).not.toHaveBeenCalled();
  });

  it('loadingChart=true iken yükleme göstergesi (3 animate-bounce nokta) görünür', () => {
    const { container } = renderChart({ loadingChart: true });
    expect(container.querySelectorAll('.animate-bounce').length).toBe(3);
  });

  it('loadingChart=false iken yükleme göstergesi görünmez', () => {
    const { container } = renderChart({ loadingChart: false });
    expect(container.querySelectorAll('.animate-bounce').length).toBe(0);
  });

  it('chartPoints boş + loadingChart=true iken "veri yüklenemedi" YERİNE yükleme gösterilir', () => {
    // (!chartPoints || len===0) && !loadingChart → false olduğundan boş mesajı GÖSTERİLMEZ
    renderChart({ chartPoints: [], loadingChart: true });
    expect(screen.queryByText('Grafik verisi yüklenemedi.')).not.toBeInTheDocument();
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// 3) Zaman aralığı (range) etkileşimi → onRangeChange
// ─────────────────────────────────────────────────────────────────────────────
describe('FxChart — zaman aralığı seçimi', () => {
  it('bir aralık butonuna tıklayınca onRangeChange ilgili kod ile çağrılır', () => {
    const onRangeChange = vi.fn();
    renderChart({ onRangeChange, range: '1Y' });
    // '3A' etiketi → '3M' range kodu
    fireEvent.click(screen.getByRole('button', { name: '3A' }));
    expect(onRangeChange).toHaveBeenCalledWith('3M');
  });

  it('farklı bir aralık (1H → 1W) seçimi doğru kodu iletir', () => {
    const onRangeChange = vi.fn();
    renderChart({ onRangeChange });
    fireEvent.click(screen.getByRole('button', { name: '1H' }));
    expect(onRangeChange).toHaveBeenCalledWith('1W');
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// 4) Çizim aracı (DrawingToolbar) etkileşimi → dropdown + createOverlay/removeOverlay
// ─────────────────────────────────────────────────────────────────────────────
describe('FxChart — çizim aracı etkileşimi', () => {
  it('"Şekiller" grubuna tıklayınca alt araç (Dikdörtgen/Çember) dropdown\'u açılır', () => {
    renderChart();
    fireEvent.click(screen.getByRole('button', { name: /Şekiller/ }));
    expect(screen.getByRole('button', { name: /Dikdörtgen/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Çember/ })).toBeInTheDocument();
  });

  it('bir çizim aracı seçilince chart.createOverlay çağrılır ve aktif-araç rozeti çıkar', () => {
    renderChart({ chartPoints: makePoints(30) });
    expect(lastChart).not.toBeNull();

    fireEvent.click(screen.getByRole('button', { name: /Çizgiler/ }));
    fireEvent.click(screen.getByRole('button', { name: /Yatay Çizgi/ }));

    // useChartDrawings.handleSelectTool → createOverlay({ name, ...overlayEvents })
    expect(lastChart.createOverlay).toHaveBeenCalled();
    expect(lastChart.createOverlay.mock.calls[0][0].name).toBe('horizontalStraightLine');
    // Aktif araç rozeti (ml-auto span) — etiket görünür hale gelir (>1 kez geçebilir)
    expect(screen.getAllByText('Yatay Çizgi').length).toBeGreaterThan(0);
  });

  it('"Seçili çizimi sil" butonu chart.removeOverlay() (argümansız) çağırır', () => {
    renderChart({ chartPoints: makePoints(30) });
    expect(lastChart).not.toBeNull();

    fireEvent.click(screen.getByTitle('Seçili çizimi sil'));
    // useChartDrawings.clearAll → removeOverlay() argümansız
    expect(lastChart.removeOverlay).toHaveBeenCalledWith();
  });

  it('"Tüm çizimleri temizle" chart.removeOverlay() çağırır ve aktif aracı sıfırlar', () => {
    renderChart({ chartPoints: makePoints(30) });
    expect(lastChart).not.toBeNull();

    // Önce bir araç seç → rozet açılır
    fireEvent.click(screen.getByRole('button', { name: /Şekiller/ }));
    fireEvent.click(screen.getByRole('button', { name: /Dikdörtgen/ }));
    expect(screen.getByRole('button', { name: '✕' })).toBeInTheDocument();

    fireEvent.click(screen.getByTitle('Tüm çizimleri temizle'));
    expect(lastChart.removeOverlay).toHaveBeenCalledWith();
    // handleClearAll → setActiveTool(null) → rozet kapanır
    expect(screen.queryByRole('button', { name: '✕' })).not.toBeInTheDocument();
  });

  it('ESC tuşu aktif çizim aracını kapatır (rozet kaybolur)', () => {
    renderChart({ chartPoints: makePoints(30) });
    fireEvent.click(screen.getByRole('button', { name: /Şekiller/ }));
    fireEvent.click(screen.getByRole('button', { name: /Dikdörtgen/ }));
    expect(screen.getByRole('button', { name: '✕' })).toBeInTheDocument();

    fireEvent.keyDown(window, { key: 'Escape' });
    expect(screen.queryByRole('button', { name: '✕' })).not.toBeInTheDocument();
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// 5) İndikatör menüsü (MA / alt indikatör) → chart üzerinde createIndicator
// ─────────────────────────────────────────────────────────────────────────────
describe('FxChart — İndikatör menüsü (MA / osilatör)', () => {
  it('İndikatör butonuna tıklayınca MA20/MA50/MA200 ve osilatör (RSI/MACD/KDJ) seçenekleri açılır', () => {
    renderChart();
    fireEvent.click(screen.getByRole('button', { name: /İndikatör/ }));
    expect(screen.getByRole('button', { name: 'MA20' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'MA50' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'MA200' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'RSI' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'MACD' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'KDJ' })).toBeInTheDocument();
  });

  it('MA20\'ye tıklayınca chart üzerinde MA indikatörü (calcParams 20 içeren) oluşturulur', async () => {
    renderChart({ chartPoints: makePoints(30) });
    expect(lastChart).not.toBeNull();

    fireEvent.click(screen.getByRole('button', { name: /İndikatör/ }));
    fireEvent.click(screen.getByRole('button', { name: 'MA20' }));

    // toggleMA → applyMA → createIndicator({ name:'MA', calcParams:[20], ... })
    await waitFor(() => {
      const maCall = lastChart.createIndicator.mock.calls.find(
        ([cfg]) => cfg && cfg.name === 'MA',
      );
      expect(maCall).toBeTruthy();
      expect(maCall[0].calcParams).toContain(20);
    });
    // İndikatör sayacı (aktif=1) rozetinde "1" görünür
    expect(screen.getByText('1')).toBeInTheDocument();
  });

  it('alt-indikatör (RSI) seçilince chart üzerinde createIndicator(name:RSI) çağrılır', async () => {
    renderChart({ chartPoints: makePoints(30) });
    expect(lastChart).not.toBeNull();

    fireEvent.click(screen.getByRole('button', { name: /İndikatör/ }));
    fireEvent.click(screen.getByRole('button', { name: 'RSI' }));

    // toggleSubIndicator → createIndicator({ name:'RSI' }, true, { height:80 })
    await waitFor(() => {
      const rsiCall = lastChart.createIndicator.mock.calls.find(
        ([cfg]) => cfg && cfg.name === 'RSI',
      );
      expect(rsiCall).toBeTruthy();
    });
  });

  it('aktif MA tekrar tıklanınca (toggle off) removeIndicator çağrılır', async () => {
    renderChart({ chartPoints: makePoints(30) });
    expect(lastChart).not.toBeNull();

    fireEvent.click(screen.getByRole('button', { name: /İndikatör/ }));
    fireEvent.click(screen.getByRole('button', { name: 'MA50' })); // aç
    fireEvent.click(screen.getByRole('button', { name: 'MA50' })); // kapat

    // applyMA her çağrıda önce removeIndicator('candle_pane','MA') yapar
    await waitFor(() => {
      expect(lastChart.removeIndicator).toHaveBeenCalledWith('candle_pane', 'MA');
    });
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// 5b) KÖKLÜ FIX regresyonu — zaman filtresi (range) değişiminde indikatör KATLANMASI
//     Bug: chartPoints değişince chart dispose+init oluyor, aktif indikatörler ÜST ÜSTE
//     biniyordu (ikiye katlanma + hayalet panel). Fix: chart instance YAŞAR, yalnız
//     applyNewData çağrılır; indikatörler idempotent reconcile ile TEK kopya kalır.
// ─────────────────────────────────────────────────────────────────────────────
describe('FxChart — range değişiminde indikatör KATLANMAZ (köklü fix)', () => {
  it('chartPoints (range) değişince chart YENİDEN init/dispose EDİLMEZ; sadece applyNewData', () => {
    const { rerender } = renderChart({ chartPoints: makePoints(30) });
    expect(klineInit).toHaveBeenCalledTimes(1);
    const firstChart = lastChart;
    const applyCallsBefore = firstChart.applyNewData.mock.calls.length;

    // Range değişimi = parent yeni chartPoints ref'i geçirir
    rerender(
      <LanguageProvider>
        <FxChart {...BASE_PROPS} chartPoints={makePoints(40, 25)} />
      </LanguageProvider>,
    );

    // KÖKLÜ FIX: instance yaşar → ikinci init YOK, dispose YOK
    expect(klineInit).toHaveBeenCalledTimes(1);
    expect(klineDispose).not.toHaveBeenCalled();
    // Aynı instance üzerinde yeni veri uygulanır
    expect(lastChart).toBe(firstChart);
    expect(firstChart.applyNewData.mock.calls.length).toBeGreaterThan(applyCallsBefore);
  });

  it('MA20 açıkken range değişince MA TEK kopya kalır (idempotent: önce remove, sonra create)', () => {
    const { rerender } = renderChart({ chartPoints: makePoints(30) });
    const chart = lastChart;

    // MA20 aç
    fireEvent.click(screen.getByRole('button', { name: /İndikatör/ }));
    fireEvent.click(screen.getByRole('button', { name: 'MA20' }));
    chart.createIndicator.mockClear();
    chart.removeIndicator.mockClear();

    // Range değişimi
    rerender(
      <LanguageProvider>
        <FxChart {...BASE_PROPS} chartPoints={makePoints(40, 25)} />
      </LanguageProvider>,
    );

    // reconcileIndicators: MA önce removeIndicator('candle_pane','MA') sonra TEK createIndicator(MA)
    expect(chart.removeIndicator).toHaveBeenCalledWith('candle_pane', 'MA');
    const maCreates = chart.createIndicator.mock.calls.filter(([cfg]) => cfg && cfg.name === 'MA');
    expect(maCreates.length).toBe(1); // KATLANMA YOK (2 değil)
    expect(maCreates[0][0].calcParams).toContain(20);
  });

  it('RSI açıkken range değişince RSI TEK kopya kalır (eski pane kaldırılıp yeniden eklenir)', () => {
    const { rerender } = renderChart({ chartPoints: makePoints(30) });
    const chart = lastChart;

    fireEvent.click(screen.getByRole('button', { name: /İndikatör/ }));
    fireEvent.click(screen.getByRole('button', { name: 'RSI' }));
    chart.createIndicator.mockClear();
    chart.removeIndicator.mockClear();

    rerender(
      <LanguageProvider>
        <FxChart {...BASE_PROPS} chartPoints={makePoints(40, 25)} />
      </LanguageProvider>,
    );

    // Eski RSI pane kaldırılır (mock paneId='pane_x'), sonra TEK kez yeniden eklenir
    expect(chart.removeIndicator).toHaveBeenCalledWith('pane_x', 'RSI');
    const rsiCreates = chart.createIndicator.mock.calls.filter(([cfg]) => cfg && cfg.name === 'RSI');
    expect(rsiCreates.length).toBe(1); // KATLANMA YOK
  });

  it('tema (lineColor/mainLabel) değişince chart YENİDEN init EDİLMEZ — sadece setStyles', () => {
    const { rerender } = renderChart({ chartPoints: makePoints(30) });
    expect(klineInit).toHaveBeenCalledTimes(1);
    const chart = lastChart;
    chart.setStyles.mockClear();

    // Tema değişimi simülasyonu: lineColor + mainLabel değişir (chartPoints AYNI ref)
    rerender(
      <LanguageProvider>
        <FxChart {...BASE_PROPS} lineColor="#10b981" mainLabel="EUR/TRY" />
      </LanguageProvider>,
    );

    expect(klineInit).toHaveBeenCalledTimes(1); // re-init YOK (önceki fix korunur)
    expect(klineDispose).not.toHaveBeenCalled();
    expect(chart.setStyles).toHaveBeenCalled(); // yalnız stil güncellenir
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// 6) Props varyasyonları
// ─────────────────────────────────────────────────────────────────────────────
describe('FxChart — props varyasyonları', () => {
  it('symbol verilmese de (persistKey boş) throw etmeden render edilir', () => {
    expect(() => renderChart({ symbol: undefined, chartPoints: makePoints(20) })).not.toThrow();
    expect(klineInit).toHaveBeenCalled();
  });

  it('farklı lineColor + mainLabel ile render edilir (setStyles area ayarlanır)', () => {
    renderChart({ lineColor: '#10b981', mainLabel: 'EUR/TRY', chartPoints: makePoints(25) });
    expect(lastChart).not.toBeNull();
    expect(lastChart.setStyles).toHaveBeenCalled();
  });

  it('geçersiz/sıfır value\'lu noktalar filtrelenir; hiç geçerli nokta yoksa init+dispose döngüsü throw etmez', () => {
    // value<=0 → buildKlineData filtreler; tümü 0 ise klineDispose çağrılır
    const badPoints = [
      { date: '2024-01-01', value: 0 },
      { date: '2024-01-02', value: -1 },
    ];
    expect(() => renderChart({ chartPoints: badPoints })).not.toThrow();
    // init çağrılır (chartPoints len>0) ama applyNewData edilmez (klineData boş)
    expect(klineInit).toHaveBeenCalled();
  });
});
