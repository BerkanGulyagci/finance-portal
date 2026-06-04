import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';

// ─────────────────────────────────────────────────────────────────────────────
// MOCK'lar — kaynağı (CryptoLineChart.jsx) GERÇEKTEN okuyarak belirlendi.
//
// 1) klinecharts: jsdom'da canvas YOK → init/dispose stub'lanır. init() bir chart
//    instance döndürür; kaynak + useChartDrawings hook'u şu metotları çağırır:
//      setStyles / applyNewData / setPriceVolumePrecision / createIndicator /
//      removeIndicator / subscribeAction / createOverlay / removeOverlay /
//      getOverlayById. Hepsi vi.fn ile karşılanır.
//    - createIndicator alt-pane id döndürür → 'pane_x'.
//    - createOverlay overlay id döndürür → 'ov_1'.
//    - getOverlayById null döndürür (snapshot'ta "ölü" sayılır, zararsız).
//    klinecharts MOCK olduğundan createIndicator'a verilen calc/styles callback'leri
//    ve subscribeAction handler'ı İÇERİDE asla çağrılmaz → hover tooltip render olmaz
//    (yapıyı test ediyoruz, klinecharts'ın iç crosshair'ini değil).
//
// 2) LanguageContext: GERÇEK LanguageProvider kullanılır (tr varsayılan, t(key)
//    anahtarın kendisini döndürür) → Türkçe metinleri/etiketleri aratabiliriz.
//
// 3) useChartDrawings hook'u + DrawingToolbar GERÇEK kullanılır (sadece klinecharts +
//    prefs/localStorage'a dokunur). prefSet/prefGet localStorage tabanlı; auth_token YOK
//    (beforeEach temizler) → sunucuya HTTP gitmez, lib/http mock'lamaya gerek yok.
//
// CryptoLineChart tamamen props-driven: router YOK, doğrudan ağ/api çağrısı YOK
// (veriyi `chartData`/`compareData` prop'larından alır) → MemoryRouter/api mock gerekmez.
// ─────────────────────────────────────────────────────────────────────────────

// Aktif chart instance'ını testlerden erişilebilir tutmak için referans yakalarız.
let lastChart = null;
function makeChart() {
  lastChart = {
    applyNewData: vi.fn(),
    setStyles: vi.fn(),
    setPriceVolumePrecision: vi.fn(),
    createIndicator: vi.fn(() => 'pane_x'),
    removeIndicator: vi.fn(),
    subscribeAction: vi.fn(),
    resize: vi.fn(),
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
import CryptoLineChart from '../CryptoLineChart';

// ─── Yardımcılar ──────────────────────────────────────────────────────────────

/** Kaynağın beklediği tek-coin nokta şekli: { ts, price, volume }.
 *  Tek-coin modunda close=price filtrelenir (close>0 ve !isNaN). */
function makeChartData(count = 30, start = 100) {
  const pts = [];
  const base = Date.UTC(2024, 0, 1);
  for (let i = 0; i < count; i++) {
    pts.push({ ts: base + i * 864e5, price: start + i, volume: 1000 + i * 10 });
  }
  return pts;
}

/** Karşılaştırma serisi: [ [ts, price], ... ] (compareData[coinId]). */
function makeCompareSeries(count = 30, start = 50) {
  const rows = [];
  const base = Date.UTC(2024, 0, 1);
  for (let i = 0; i < count; i++) {
    rows.push([base + i * 864e5, start + i * 0.5]);
  }
  return rows;
}

const BASE_PROPS = {
  chartData: makeChartData(30),
  currency: 'TRY',
  compareCoins: [],
  compareData: {},
  coinId: 'bitcoin',
  mainCoinSymbol: 'btc',
  activeMAs: [],
};

function renderChart(props = {}) {
  return render(
    <LanguageProvider>
      <CryptoLineChart {...BASE_PROPS} {...props} />
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
// 1) Smoke + tek-coin iskeleti (DrawingToolbar + kaynak satırı render olur)
// ─────────────────────────────────────────────────────────────────────────────
describe('CryptoLineChart — render (smoke, tek coin)', () => {
  it('mocklarla throw etmeden render olur ve çizim aracı grup butonları DOM\'da', () => {
    expect(() => renderChart()).not.toThrow();
    // DrawingToolbar yalnız karşılaştırma DIŞINDA gösterilir → grup butonları görünür.
    expect(screen.getByRole('button', { name: /Çizgiler/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Kanallar/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Fibonacci/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Şekiller/ })).toBeInTheDocument();
  });

  it('sil/temizle butonları (title) render edilir', () => {
    renderChart();
    expect(screen.getByTitle('Seçili çizimi sil')).toBeInTheDocument();
    expect(screen.getByTitle('Tüm çizimleri temizle')).toBeInTheDocument();
  });

  it('kaynak/para birimi alt yazısı CoinGecko + currency içerir (karşılaştırma eki YOK)', () => {
    renderChart({ currency: 'USD' });
    // t() anahtarı döndürür: "Kaynak: CoinGecko ·" + " USD " + "bazlı"
    expect(screen.getByText(/Kaynak: CoinGecko/)).toBeInTheDocument();
    expect(screen.getByText(/USD/)).toBeInTheDocument();
    expect(screen.queryByText(/% değişim bazlı karşılaştırma/)).not.toBeInTheDocument();
  });

  it('dolu veri ile klinecharts.init + applyNewData + setStyles çağrılır (canvas konteyneri mount)', () => {
    renderChart({ chartData: makeChartData(30) });
    expect(klineInit).toHaveBeenCalled();
    expect(lastChart).not.toBeNull();
    expect(lastChart.applyNewData).toHaveBeenCalled();
    expect(lastChart.setStyles).toHaveBeenCalled();
  });

  it('tek-coin modunda restoreOverlays yolu (removeIndicator candle_pane/MA) ve setPriceVolumePrecision çağrılır', () => {
    renderChart({ chartData: makeChartData(30) });
    expect(lastChart).not.toBeNull();
    // Tek-coin: eski MA temizliği + hassasiyet ayarı yapılır (karşılaştırma modunda YAPILMAZ).
    expect(lastChart.removeIndicator).toHaveBeenCalledWith('candle_pane', 'MA');
    expect(lastChart.setPriceVolumePrecision).toHaveBeenCalled();
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// 2) Koşullu render dalı: boş veri → "Grafik verisi yüklenemedi."
// ─────────────────────────────────────────────────────────────────────────────
describe('CryptoLineChart — koşullu render (boş veri)', () => {
  it('chartData BOŞ ise "Grafik verisi yüklenemedi." gösterilir, init/toolbar YOK', () => {
    renderChart({ chartData: [] });
    expect(screen.getByText('Grafik verisi yüklenemedi.')).toBeInTheDocument();
    expect(klineInit).not.toHaveBeenCalled();
    // Erken return → DrawingToolbar render edilmez.
    expect(screen.queryByRole('button', { name: /Çizgiler/ })).not.toBeInTheDocument();
  });

  it('chartData null ise de boş mesajı gösterilir (throw etmez)', () => {
    expect(() => renderChart({ chartData: null })).not.toThrow();
    expect(screen.getByText('Grafik verisi yüklenemedi.')).toBeInTheDocument();
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// 3) MA (activeMAs) davranışı → tek-coin modunda createIndicator(name:MA)
// ─────────────────────────────────────────────────────────────────────────────
describe('CryptoLineChart — hareketli ortalama (activeMAs)', () => {
  it('activeMAs verilince MA indikatörü (calcParams seçili periyotlar) oluşturulur', () => {
    renderChart({ chartData: makeChartData(40), activeMAs: [20, 50] });
    expect(lastChart).not.toBeNull();
    const maCall = lastChart.createIndicator.mock.calls.find(
      ([cfg]) => cfg && cfg.name === 'MA',
    );
    expect(maCall).toBeTruthy();
    expect(maCall[0].calcParams).toEqual([20, 50]);
  });

  it('activeMAs BOŞ iken MA createIndicator çağrılmaz (yalnız removeIndicator temizliği)', () => {
    renderChart({ chartData: makeChartData(30), activeMAs: [] });
    const maCall = lastChart.createIndicator.mock.calls.find(
      ([cfg]) => cfg && cfg.name === 'MA',
    );
    expect(maCall).toBeUndefined();
    expect(lastChart.removeIndicator).toHaveBeenCalledWith('candle_pane', 'MA');
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// 4) Çizim aracı (DrawingToolbar) etkileşimi → createOverlay / removeOverlay
// ─────────────────────────────────────────────────────────────────────────────
describe('CryptoLineChart — çizim aracı etkileşimi (tek coin)', () => {
  it('"Şekiller" grubuna tıklayınca alt araç (Dikdörtgen/Çember) dropdown\'u açılır', () => {
    renderChart();
    fireEvent.click(screen.getByRole('button', { name: /Şekiller/ }));
    expect(screen.getByRole('button', { name: /Dikdörtgen/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Çember/ })).toBeInTheDocument();
  });

  it('bir çizim aracı seçilince chart.createOverlay (doğru name ile) çağrılır + aktif-araç rozeti çıkar', () => {
    renderChart({ chartData: makeChartData(30) });
    expect(lastChart).not.toBeNull();

    fireEvent.click(screen.getByRole('button', { name: /Çizgiler/ }));
    fireEvent.click(screen.getByRole('button', { name: /Yatay Çizgi/ }));

    expect(lastChart.createOverlay).toHaveBeenCalled();
    expect(lastChart.createOverlay.mock.calls[0][0].name).toBe('horizontalStraightLine');
    // Aktif araç rozeti — etiket görünür hale gelir (>1 kez geçebilir).
    expect(screen.getAllByText('Yatay Çizgi').length).toBeGreaterThan(0);
  });

  it('"Seçili çizimi sil" butonu chart.removeOverlay() (argümansız) çağırır', () => {
    renderChart({ chartData: makeChartData(30) });
    expect(lastChart).not.toBeNull();
    fireEvent.click(screen.getByTitle('Seçili çizimi sil'));
    expect(lastChart.removeOverlay).toHaveBeenCalledWith();
  });

  it('"Tüm çizimleri temizle" chart.removeOverlay() çağırır ve aktif aracı sıfırlar (rozet kapanır)', () => {
    renderChart({ chartData: makeChartData(30) });
    expect(lastChart).not.toBeNull();

    fireEvent.click(screen.getByRole('button', { name: /Şekiller/ }));
    fireEvent.click(screen.getByRole('button', { name: /Dikdörtgen/ }));
    expect(screen.getByRole('button', { name: '✕' })).toBeInTheDocument();

    fireEvent.click(screen.getByTitle('Tüm çizimleri temizle'));
    expect(lastChart.removeOverlay).toHaveBeenCalledWith();
    expect(screen.queryByRole('button', { name: '✕' })).not.toBeInTheDocument();
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// 5) Karşılaştırma modu (compareCoins.length > 0)
//    → DrawingToolbar YOK, normalize + her coin için createIndicator + subscribeAction
// ─────────────────────────────────────────────────────────────────────────────
describe('CryptoLineChart — karşılaştırma modu', () => {
  const compareCoins = [{ id: 'ethereum', symbol: 'eth', name: 'Ethereum' }];
  const compareProps = {
    chartData: makeChartData(30),
    compareCoins,
    compareData: { ethereum: makeCompareSeries(30) },
    coinId: 'bitcoin',
    mainCoinSymbol: 'btc',
  };

  it('karşılaştırma modunda DrawingToolbar GÖSTERİLMEZ (çizim grup butonu yok)', () => {
    renderChart(compareProps);
    expect(screen.queryByRole('button', { name: /Çizgiler/ })).not.toBeInTheDocument();
    expect(screen.queryByTitle('Seçili çizimi sil')).not.toBeInTheDocument();
  });

  it('alt yazıya "% değişim bazlı karşılaştırma" eki eklenir', () => {
    renderChart(compareProps);
    expect(screen.getByText(/% değişim bazlı karşılaştırma/)).toBeInTheDocument();
  });

  it('init + applyNewData (normalize veri) + subscribeAction (crosshair) çağrılır', () => {
    renderChart(compareProps);
    expect(klineInit).toHaveBeenCalled();
    expect(lastChart).not.toBeNull();
    expect(lastChart.applyNewData).toHaveBeenCalled();
    expect(lastChart.subscribeAction).toHaveBeenCalledWith('onCrosshairChange', expect.any(Function));
  });

  it('her karşılaştırma coini için bir MA-overlay (createIndicator name:MA, candle_pane) eklenir', () => {
    renderChart(compareProps);
    const maCalls = lastChart.createIndicator.mock.calls.filter(
      ([cfg]) => cfg && cfg.name === 'MA',
    );
    // 1 karşılaştırma coini → 1 MA overlay çağrısı.
    expect(maCalls.length).toBe(1);
    // candle_pane'e eklenir (3. argüman).
    expect(maCalls[0][2]).toEqual({ id: 'candle_pane' });
  });

  it('karşılaştırma modunda restoreOverlays YOLU çalışmaz (removeIndicator candle_pane/MA YAPILMAZ)', () => {
    renderChart(compareProps);
    // Tek-coin temizliği yalnız tekil modda; karşılaştırma dalında MA-temizleme yok.
    expect(lastChart.removeIndicator).not.toHaveBeenCalled();
    expect(lastChart.setPriceVolumePrecision).not.toHaveBeenCalled();
  });

  it('iki karşılaştırma coini → iki MA-overlay çağrısı (createIndicator)', () => {
    renderChart({
      ...compareProps,
      compareCoins: [
        { id: 'ethereum', symbol: 'eth' },
        { id: 'solana', symbol: 'sol' },
      ],
      compareData: {
        ethereum: makeCompareSeries(30, 40),
        solana: makeCompareSeries(30, 20),
      },
    });
    const maCalls = lastChart.createIndicator.mock.calls.filter(
      ([cfg]) => cfg && cfg.name === 'MA',
    );
    expect(maCalls.length).toBe(2);
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// 6) Props varyasyonları / dayanıklılık
// ─────────────────────────────────────────────────────────────────────────────
describe('CryptoLineChart — props varyasyonları', () => {
  it('coinId verilmese de (persistKey boş → no-op) tek-coin grafiği throw etmeden render edilir', () => {
    expect(() => renderChart({ coinId: undefined, chartData: makeChartData(20) })).not.toThrow();
    expect(klineInit).toHaveBeenCalled();
  });

  it('geçersiz/sıfır fiyatlı noktalar filtrelenir; hiç geçerli nokta yoksa init+dispose throw etmez', () => {
    // close<=0 / NaN → klineData filtreler. Tümü 0 ise applyNewData edilmez ama init+dispose döner.
    const badData = [
      { ts: Date.UTC(2024, 0, 1), price: 0, volume: 0 },
      { ts: Date.UTC(2024, 0, 2), price: 0, volume: 0 },
    ];
    expect(() => renderChart({ chartData: badData })).not.toThrow();
    expect(klineInit).toHaveBeenCalled();
    expect(lastChart.applyNewData).not.toHaveBeenCalled();
  });

  it('unmount edilince klinecharts.dispose ile grafik temizlenir (bellek sızıntısı yok)', () => {
    const { unmount } = renderChart({ chartData: makeChartData(20) });
    expect(klineInit).toHaveBeenCalled();
    unmount();
    expect(klineDispose).toHaveBeenCalled();
  });
});
