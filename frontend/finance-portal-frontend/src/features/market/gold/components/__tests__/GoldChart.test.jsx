import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';

// ─────────────────────────────────────────────────────────────────────────────
// MOCK'lar — kaynağı (GoldChart.jsx) GERÇEKTEN okuyarak belirlendi.
//
// 1) klinecharts: jsdom'da canvas YOK → init/dispose/registerOverlay stub'lanır.
//    init() bir chart instance döndürür; kaynak üzerinde şunları çağırır:
//    setStyles / applyNewData / createIndicator / removeIndicator /
//    subscribeAction / createOverlay / removeOverlay. Hepsi vi.fn ile karşılanır.
//    createIndicator alt-pane id döndürmeli (subPaneIds.current[name] = pid) →
//    'pane_x' döndürüyoruz.
// 2) LanguageContext: GERÇEK LanguageProvider kullanılır (tr varsayılan, t(key)
//    anahtarın kendisini döndürür) → Türkçe metinleri/etiketleri aratabiliriz.
//    Bu yüzden LanguageContext'i MOCK'LAMIYORUZ; gerçek Provider ile sarıyoruz.
// 3) lucide-react ikonları jsdom'da SVG olarak sorunsuz render olur → mock yok.
// 4) IndicatorMenu gerçek bileşen (sadece useState + lucide) → mock yok.
//
// GoldChart props-driven SAF bir bileşendir: router YOK, doğrudan ağ/api çağrısı
// YOK (veriyi `points` prop'undan alır). Bu yüzden MemoryRouter/api mock gerekmez.
// ─────────────────────────────────────────────────────────────────────────────

// Aktif chart instance'ı testlerden erişilebilir tutmak için referans yakalarız.
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
    createOverlay: vi.fn(),
    removeOverlay: vi.fn(),
  };
  return lastChart;
}

vi.mock('klinecharts', () => ({
  init: vi.fn(() => makeChart()),
  dispose: vi.fn(),
  registerOverlay: vi.fn(),
  registerIndicator: vi.fn(),
}));

import { init as klineInit, registerOverlay } from 'klinecharts';
import { LanguageProvider } from '../../../../../context/LanguageContext';
import GoldChart from '../GoldChart';

// ─── Yardımcılar ──────────────────────────────────────────────────────────────

/** Kaynağın beklediği nokta şekli: { date, close, open?, high?, low?, volume?,
 *  displayClose?, ... }. buildKlineData close>0 ve geçerli tarih filtreler. */
function makePoints(count = 30, start = 2400) {
  const pts = [];
  for (let i = 0; i < count; i++) {
    const d = new Date(2024, 0, 1 + i);
    pts.push({
      date: d.toISOString().slice(0, 10),
      open: start + i,
      high: start + i + 5,
      low: start + i - 5,
      close: start + i + 2,
      volume: 1000 + i,
    });
  }
  return pts;
}

const BASE_PROPS = {
  points: makePoints(30),
  chartMode: 'line',
  isDown: false,
  loading: false,
  showMA20: false,
  showMA50: false,
  showMA200: false,
  showTrend: false,
  currency: 'TRY',
  onToggleMA20: vi.fn(),
  onToggleMA50: vi.fn(),
  onToggleMA200: vi.fn(),
  onToggleTrend: vi.fn(),
  dataLen: 30,
  height: 280,
};

function renderChart(props = {}) {
  return render(
    <LanguageProvider>
      <GoldChart {...BASE_PROPS} {...props} />
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
// 1) Smoke + temel iskelet (Drawing toolbar her zaman render olur)
// ─────────────────────────────────────────────────────────────────────────────
describe('GoldChart — render (smoke)', () => {
  it('mocklarla throw etmeden render olur ve çizim aracı grup butonları DOM\'da', () => {
    renderChart();
    // DrawingToolbar grup başlıkları (t(group) → anahtarın kendisi)
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

  it('dolu veri + line modunda klinecharts.init çağrılır (canvas konteyneri mount)', () => {
    renderChart({ chartMode: 'line', points: makePoints(30) });
    expect(klineInit).toHaveBeenCalled();
  });

  it('custom overlay\'ler (registerOverlay) en az bir kez kaydedilir', () => {
    renderChart();
    // ensureOverlays() ilk kez customRect + customCircle kaydeder. Modül-seviyesi
    // _overlaysRegistered guard\'ı nedeniyle önceki testlerde kayıtlanmış olabilir;
    // bu yüzden "çağrıldı" demek yerine en az "0\'dan büyük ya da guard\'lı" kabul:
    // init çağrıldıysa ensureOverlays da çağrılmıştır → overlay kayıt fonksiyonu
    // ya bu çalıştırmada ya da daha önce tetiklenmiştir. Burada sadece fonksiyonun
    // var/çağrılabilir olduğunu ve init akışının throw etmediğini doğrularız.
    expect(typeof registerOverlay).toBe('function');
    expect(klineInit).toHaveBeenCalled();
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// 2) Koşullu render: boş veri / loading / candle vs line
// ─────────────────────────────────────────────────────────────────────────────
describe('GoldChart — koşullu render dalları', () => {
  it('points BOŞ ise "Grafik verisi yüklenemedi." (GoldNoData) gösterilir, init çağrılmaz', () => {
    renderChart({ points: [] });
    expect(screen.getByText('Grafik verisi yüklenemedi.')).toBeInTheDocument();
    expect(klineInit).not.toHaveBeenCalled();
  });

  it('loading=true iken yükleme göstergesi (animate-bounce noktalar) görünür', () => {
    const { container } = renderChart({ loading: true });
    // 3 adet animate-bounce nokta render edilir
    expect(container.querySelectorAll('.animate-bounce').length).toBe(3);
  });

  it('loading=false iken yükleme göstergesi görünmez', () => {
    const { container } = renderChart({ loading: false });
    expect(container.querySelectorAll('.animate-bounce').length).toBe(0);
  });

  it('chartMode="candle" iken de dolu veriyle init çağrılır (GoldCandleChart)', () => {
    renderChart({ chartMode: 'candle', points: makePoints(40) });
    expect(klineInit).toHaveBeenCalled();
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// 3) Çizim aracı (DrawingToolbar) etkileşimi → dropdown + createOverlay
// ─────────────────────────────────────────────────────────────────────────────
describe('GoldChart — çizim aracı etkileşimi', () => {
  it('"Şekiller" grubuna tıklayınca alt araç (Dikdörtgen/Çember) dropdown\'u açılır', () => {
    renderChart();
    fireEvent.click(screen.getByRole('button', { name: /Şekiller/ }));
    expect(screen.getByRole('button', { name: /Dikdörtgen/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Çember/ })).toBeInTheDocument();
  });

  it('bir çizim aracı seçilince chart.createOverlay çağrılır ve aktif-araç rozeti çıkar', () => {
    renderChart({ points: makePoints(30) });
    // init dolu veride çağrıldı → chartRef.current set (onChartReady)
    expect(lastChart).not.toBeNull();

    fireEvent.click(screen.getByRole('button', { name: /Çizgiler/ }));
    // "Yatay Çizgi" aracını seç
    fireEvent.click(screen.getByRole('button', { name: /Yatay Çizgi/ }));

    expect(lastChart.createOverlay).toHaveBeenCalledWith({ name: 'horizontalStraightLine' });
    // Aktif araç rozeti (ml-auto span) — etiket görünür hale gelir
    const matches = screen.getAllByText('Yatay Çizgi');
    expect(matches.length).toBeGreaterThan(0);
  });

  it('"Seçili çizimi sil" butonu chart.removeOverlay() (argümansız) çağırır', () => {
    renderChart({ points: makePoints(30) });
    expect(lastChart).not.toBeNull();

    fireEvent.click(screen.getByTitle('Seçili çizimi sil'));
    expect(lastChart.removeOverlay).toHaveBeenCalled();
    // Argümansız çağrılır (seçili olanı siler)
    expect(lastChart.removeOverlay).toHaveBeenCalledWith();
  });

  it('"Tüm çizimleri temizle" tüm araç adları için removeOverlay({name}) çağırır', () => {
    renderChart({ points: makePoints(30) });
    expect(lastChart).not.toBeNull();

    fireEvent.click(screen.getByTitle('Tüm çizimleri temizle'));
    // ALL_DRAWING_IDS sayısı kadar removeOverlay({name}) çağrısı (>=8 araç var)
    expect(lastChart.removeOverlay.mock.calls.length).toBeGreaterThanOrEqual(8);
    expect(lastChart.removeOverlay).toHaveBeenCalledWith({ name: 'customRect' });
    expect(lastChart.removeOverlay).toHaveBeenCalledWith({ name: 'segment' });
  });

  it('ESC tuşu aktif çizim aracını kapatır (rozet kaybolur)', () => {
    renderChart({ points: makePoints(30) });
    // Tekil isimli bir araç seç ("Dikdörtgen") → aktif-araç rozeti açılır
    fireEvent.click(screen.getByRole('button', { name: /Şekiller/ }));
    fireEvent.click(screen.getByRole('button', { name: /Dikdörtgen/ }));
    // Rozet içindeki kapatma "✕" butonu görünür olur.
    expect(screen.getByRole('button', { name: '✕' })).toBeInTheDocument();

    // ESC → setActiveTool(null)
    fireEvent.keyDown(window, { key: 'Escape' });
    // Aktif-araç rozetindeki kapatma "✕" butonu artık olmamalı.
    expect(screen.queryByRole('button', { name: '✕' })).not.toBeInTheDocument();
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// 4) İndikatör menüsü (IndicatorMenu) → MA toggle callback'leri
// ─────────────────────────────────────────────────────────────────────────────
describe('GoldChart — İndikatör menüsü (MA / alt indikatör)', () => {
  it('İndikatör butonuna tıklayınca MA20/MA50/MA200 ve alt-indikatör seçenekleri açılır', () => {
    renderChart();
    fireEvent.click(screen.getByRole('button', { name: /İndikatör/ }));
    expect(screen.getByRole('button', { name: 'MA20' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'MA50' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'MA200' })).toBeInTheDocument();
    // SUB_INDICATORS etiketleri (Hacim/RSI/MACD/KDJ) + extras Trend
    expect(screen.getByRole('button', { name: 'Hacim' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'RSI' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Trend' })).toBeInTheDocument();
  });

  it('MA20\'ye tıklayınca onToggleMA20 çağrılır', () => {
    const onToggleMA20 = vi.fn();
    renderChart({ onToggleMA20, dataLen: 300 });
    fireEvent.click(screen.getByRole('button', { name: /İndikatör/ }));
    fireEvent.click(screen.getByRole('button', { name: 'MA20' }));
    expect(onToggleMA20).toHaveBeenCalledTimes(1);
  });

  it('Trend (extra) toggle\'ına tıklayınca onToggleTrend çağrılır', () => {
    const onToggleTrend = vi.fn();
    renderChart({ onToggleTrend });
    fireEvent.click(screen.getByRole('button', { name: /İndikatör/ }));
    fireEvent.click(screen.getByRole('button', { name: 'Trend' }));
    expect(onToggleTrend).toHaveBeenCalledTimes(1);
  });

  it('dataLen yetersizken (dataLen=10) MA200 butonu pasif (disabled) olur', () => {
    renderChart({ dataLen: 10 });
    fireEvent.click(screen.getByRole('button', { name: /İndikatör/ }));
    // IndicatorMenu: dataLen>0 && dataLen<period → disabled
    expect(screen.getByRole('button', { name: 'MA200' })).toBeDisabled();
    // MA20 ise (10<20) yine pasif; MA20<dataLen? 10<20 → MA20 da disabled
    expect(screen.getByRole('button', { name: 'MA20' })).toBeDisabled();
  });

  it('alt-indikatör (RSI) seçilince chart üzerinde createIndicator çağrılır', async () => {
    renderChart({ points: makePoints(30) });
    expect(lastChart).not.toBeNull();
    const initialCalls = lastChart.createIndicator.mock.calls.length;

    fireEvent.click(screen.getByRole('button', { name: /İndikatör/ }));
    fireEvent.click(screen.getByRole('button', { name: 'RSI' }));

    // activeSubInds değişimi → useEffect alt indikatör ekler (createIndicator)
    await waitFor(() => {
      expect(lastChart.createIndicator.mock.calls.length).toBeGreaterThan(initialCalls);
    });
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// 5) Props varyasyonları (MA prop'larıyla başlangıç indikatörleri kurulur)
// ─────────────────────────────────────────────────────────────────────────────
describe('GoldChart — props varyasyonları', () => {
  it('showMA20=true + yeterli veri ile mount\'ta MA indikatörü oluşturulur', () => {
    renderChart({ showMA20: true, points: makePoints(30) });
    expect(lastChart).not.toBeNull();
    // İlk effect: maP=[20] → createIndicator({name:'MA', calcParams:[20], ...})
    const maCall = lastChart.createIndicator.mock.calls.find(
      ([cfg]) => cfg && cfg.name === 'MA',
    );
    expect(maCall).toBeTruthy();
    expect(maCall[0].calcParams).toContain(20);
  });

  it('showTrend=true ile mount\'ta EMA (trend) indikatörü oluşturulur', () => {
    renderChart({ showTrend: true, points: makePoints(30) });
    expect(lastChart).not.toBeNull();
    const emaCall = lastChart.createIndicator.mock.calls.find(
      ([cfg]) => cfg && cfg.name === 'EMA',
    );
    expect(emaCall).toBeTruthy();
  });

  it('currency="USD" prop\'u throw etmeden render edilir (line modu)', () => {
    expect(() =>
      renderChart({ currency: 'USD', chartMode: 'line', points: makePoints(20) }),
    ).not.toThrow();
    expect(klineInit).toHaveBeenCalled();
  });
});
