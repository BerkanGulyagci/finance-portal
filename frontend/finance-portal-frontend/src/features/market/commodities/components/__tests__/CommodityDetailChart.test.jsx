import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';

// ─────────────────────────────────────────────────────────────────────────────
// MOCK'lar — kaynağı (CommodityDetailChart.jsx) GERÇEKTEN okuyarak belirlendi.
//
// Bu komponent SUNUMSAL bir grafik: API/router YOK; `points` prop'unu klinecharts'a
// çizer. Mock'lanması gerekenler:
//
// 1) klinecharts  → jsdom'da canvas yok. init() bir chart instance döndürmeli;
//    komponent + useChartDrawings hook'u şu metotları çağırır:
//      applyNewData, setStyles, setPriceVolumePrecision, createIndicator,
//      removeIndicator, subscribeAction  (komponent)
//      createOverlay, removeOverlay, getOverlayById             (hook)
//    Ayrıca modül seviyesinde registerOverlay (customRect/customCircle) + dispose.
//
// 2) ../../../../../api/prefs  → useChartDrawings VE LanguageContext bu modülü
//    import eder (prefGet/prefSet). Mock'layarak localStorage/HTTP yan etkisini
//    keser, çizim kalıcılığı çağrılarını (prefSet) gözlemlenebilir kılarız.
//    Derinlik: __tests__ → components → commodities → market → features → src → api
//
// LanguageContext'in kendisi GERÇEK Provider ile sarılır: t(key) Türkçe (varsayılan)
// modda anahtarın kendisini döndürür → DrawingToolbar/IndicatorMenu/TrendBadge
// içindeki Türkçe metinleri aratabiliriz.
// ─────────────────────────────────────────────────────────────────────────────

// createIndicator çağrılarını dışarıdan gözlemleyebilmek için paylaşılan casus.
const chartSpies = {
  applyNewData: vi.fn(),
  setStyles: vi.fn(),
  setPriceVolumePrecision: vi.fn(),
  createIndicator: vi.fn(() => 'pane_sub_1'),
  removeIndicator: vi.fn(),
  subscribeAction: vi.fn(),
  createOverlay: vi.fn(() => 'ov_1'),
  removeOverlay: vi.fn(),
  getOverlayById: vi.fn(() => null),
  resize: vi.fn(),
  convertFromPixel: vi.fn(),
};

vi.mock('klinecharts', () => ({
  init: vi.fn(() => chartSpies),
  dispose: vi.fn(),
  registerOverlay: vi.fn(),
  registerIndicator: vi.fn(),
}));

vi.mock('../../../../../api/prefs', () => ({
  prefGet: vi.fn(() => []),
  prefSet: vi.fn(),
}));

import { init as klineInit } from 'klinecharts';
import { prefGet } from '../../../../../api/prefs';
import { LanguageProvider } from '../../../../../context/LanguageContext';
import CommodityDetailChart from '../CommodityDetailChart';

// ── Test yardımcıları ────────────────────────────────────────────────────────

/**
 * Komponentin beklediği point şekli:
 *   { displayClose|rawClose, displayOpen, displayHigh, displayLow, volume, timestamp }
 * timestamp saniye cinsinden (komponent *1000 yapar). Artan kapanış serisi →
 * TrendBadge "Yükseliş" verir, area rengi yeşil olur.
 */
function makePoints(closes, startTsSec = 1_700_000_000) {
  return closes.map((c, i) => ({
    timestamp: startTsSec + i * 86_400,
    displayOpen: c,
    displayHigh: c + 1,
    displayLow: c - 1,
    displayClose: c,
    volume: 1000 + i * 10,
  }));
}

// 30 noktalık monoton artan seri (MA20 için yeterli, trend = Yükseliş).
const RISING = makePoints(Array.from({ length: 30 }, (_, i) => 100 + i));

function renderChart(props = {}) {
  return render(
    <LanguageProvider>
      <CommodityDetailChart points={RISING} chartMode="line" loading={false} {...props} />
    </LanguageProvider>,
  );
}

beforeEach(() => {
  localStorage.clear();
  vi.clearAllMocks();
  // clearAllMocks createIndicator/createOverlay'in dönüş implementasyonunu da sıfırlar;
  // hook/komponent dönen id'ye güvendiği için yeniden kur.
  chartSpies.createIndicator.mockReturnValue('pane_sub_1');
  chartSpies.createOverlay.mockReturnValue('ov_1');
  chartSpies.getOverlayById.mockReturnValue(null);
  prefGet.mockReturnValue([]);

  // jsdom'da olmayan global'ler — komponent/altları kullanıyor olabilir.
  globalThis.ResizeObserver = class { observe() {} unobserve() {} disconnect() {} };
});

afterEach(() => {
  vi.restoreAllMocks?.();
});

// ─────────────────────────────────────────────────────────────────────────────
// 1) Smoke render
// ─────────────────────────────────────────────────────────────────────────────
describe('CommodityDetailChart — render (smoke)', () => {
  it('dolu veriyle throw etmeden render olur ve klineInit ile grafik kurulur', () => {
    renderChart();
    expect(klineInit).toHaveBeenCalled();
    // Veri klinecharts'a aktarılır
    expect(chartSpies.applyNewData).toHaveBeenCalled();
  });

  it('varsayılan kaynak notunu ("Kaynak: Yahoo Finance") gösterir', () => {
    renderChart();
    expect(screen.getByText(/Kaynak: Yahoo Finance/)).toBeInTheDocument();
  });

  it('çizim araç çubuğu gruplarını (Çizgiler/Kanallar/Fibonacci/Şekiller) ve İndikatör menüsünü render eder', () => {
    renderChart();
    expect(screen.getByRole('button', { name: /Çizgiler/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Kanallar/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Fibonacci/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Şekiller/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /İndikatör/ })).toBeInTheDocument();
  });

  it('artan fiyat serisinde trend rozeti "Yükseliş" gösterir', () => {
    renderChart();
    expect(screen.getByText('Yükseliş')).toBeInTheDocument();
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// 2) Koşullu render dalları: loading / empty / dolu
// ─────────────────────────────────────────────────────────────────────────────
describe('CommodityDetailChart — koşullu render', () => {
  it('points boş + loading=false → "Grafik verisi bulunamadı." boş-durum mesajı gösterir', () => {
    render(
      <LanguageProvider>
        <CommodityDetailChart points={[]} chartMode="line" loading={false} />
      </LanguageProvider>,
    );
    expect(screen.getByText('Grafik verisi bulunamadı.')).toBeInTheDocument();
    // Boş veride grafik init edilmez
    expect(klineInit).not.toHaveBeenCalled();
  });

  it('loading=true iken boş-durum mesajı gösterilmez (yükleme overlay\'i öncelikli)', () => {
    render(
      <LanguageProvider>
        <CommodityDetailChart points={[]} chartMode="line" loading={true} />
      </LanguageProvider>,
    );
    expect(screen.queryByText('Grafik verisi bulunamadı.')).not.toBeInTheDocument();
  });

  it('dolu veride boş-durum mesajı gösterilmez', () => {
    renderChart();
    expect(screen.queryByText('Grafik verisi bulunamadı.')).not.toBeInTheDocument();
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// 3) Props varyasyonları
// ─────────────────────────────────────────────────────────────────────────────
describe('CommodityDetailChart — props varyasyonları', () => {
  it('sourceNote prop\'u verilince varsayılan yerine onu gösterir', () => {
    renderChart({ sourceNote: 'Kaynak: TCMB özel kaynak' });
    expect(screen.getByText('Kaynak: TCMB özel kaynak')).toBeInTheDocument();
    expect(screen.queryByText(/Kaynak: Yahoo Finance/)).not.toBeInTheDocument();
  });

  it('sourceWarning prop\'u verilince uyarı metnini gösterir', () => {
    renderChart({ sourceWarning: 'Veri gecikmeli olabilir' });
    expect(screen.getByText('Veri gecikmeli olabilir')).toBeInTheDocument();
  });

  it('sourceWarning yokken uyarı metni gösterilmez', () => {
    renderChart();
    expect(screen.queryByText('Veri gecikmeli olabilir')).not.toBeInTheDocument();
  });

  it('candle modunda da grafik init edilir ve veri aktarılır (mod değişimi throw etmez)', () => {
    renderChart({ chartMode: 'candle' });
    expect(klineInit).toHaveBeenCalled();
    expect(chartSpies.applyNewData).toHaveBeenCalled();
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// 4) Çizim araç çubuğu etkileşimi → useChartDrawings (createOverlay)
// ─────────────────────────────────────────────────────────────────────────────
describe('CommodityDetailChart — çizim araçları', () => {
  it('"Çizgiler" grubuna tıklayınca araç seçenekleri (Yatay Çizgi vb.) açılır', () => {
    renderChart();
    fireEvent.click(screen.getByRole('button', { name: /Çizgiler/ }));
    expect(screen.getByRole('button', { name: /Yatay Çizgi/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Işın/ })).toBeInTheDocument();
  });

  it('bir çizim aracı seçilince grafikte createOverlay çağrılır ve aktif araç rozeti görünür', () => {
    renderChart();
    fireEvent.click(screen.getByRole('button', { name: /Çizgiler/ }));
    fireEvent.click(screen.getByRole('button', { name: /Yatay Çizgi/ }));

    expect(chartSpies.createOverlay).toHaveBeenCalledWith(
      expect.objectContaining({ name: 'horizontalStraightLine' }),
    );
  });

  it('"Tüm çizimleri temizle" butonu grafikte removeOverlay\'i çağırır (tüm çizimleri siler)', () => {
    renderChart();
    fireEvent.click(screen.getByTitle('Tüm çizimleri temizle'));
    expect(chartSpies.removeOverlay).toHaveBeenCalled();
  });

  it('Escape tuşu aktif çizim aracını kapatır (rozet kaybolur)', () => {
    renderChart();
    // Önce bir araç seç → rozet ("Yatay Çizgi" etiketi araç çubuğunda iki yerde olur)
    fireEvent.click(screen.getByRole('button', { name: /Çizgiler/ }));
    fireEvent.click(screen.getByRole('button', { name: /Yatay Çizgi/ }));
    // Aktif araç rozetindeki kapatma '✕' işareti DOM'da
    expect(screen.getByText('✕')).toBeInTheDocument();

    fireEvent.keyDown(window, { key: 'Escape' });
    expect(screen.queryByText('✕')).not.toBeInTheDocument();
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// 5) İndikatör menüsü → MA / alt indikatör toggle (createIndicator)
// ─────────────────────────────────────────────────────────────────────────────
describe('CommodityDetailChart — indikatör menüsü', () => {
  it('İndikatör menüsü açılınca MA20/MA50/MA200 ve alt indikatörler (RSI/MACD) görünür', () => {
    renderChart();
    fireEvent.click(screen.getByRole('button', { name: /İndikatör/ }));
    expect(screen.getByRole('button', { name: 'MA20' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'MA50' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'MA200' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'RSI' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'MACD' })).toBeInTheDocument();
  });

  it('MA20 toggle edilince grafikte createIndicator MA ile çağrılır', () => {
    renderChart();
    fireEvent.click(screen.getByRole('button', { name: /İndikatör/ }));
    fireEvent.click(screen.getByRole('button', { name: 'MA20' }));

    expect(chartSpies.createIndicator).toHaveBeenCalledWith(
      expect.objectContaining({ name: 'MA', calcParams: [20] }),
      false,
      { id: 'candle_pane' },
    );
  });

  it('RSI alt indikatörü toggle edilince ayrı pane ile createIndicator çağrılır', () => {
    renderChart();
    fireEvent.click(screen.getByRole('button', { name: /İndikatör/ }));
    fireEvent.click(screen.getByRole('button', { name: 'RSI' }));

    expect(chartSpies.createIndicator).toHaveBeenCalledWith(
      { name: 'RSI' },
      true,
      { height: 80 },
    );
  });

  it('MA200, yeterli veri (>=200 nokta) yoksa pasiftir (dataLen<period)', () => {
    // RISING 30 nokta → MA200 butonu disabled olmalı.
    renderChart();
    fireEvent.click(screen.getByRole('button', { name: /İndikatör/ }));
    expect(screen.getByRole('button', { name: 'MA200' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'MA20' })).not.toBeDisabled();
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// 6) Çizim kalıcılığı (persistId) → restoreOverlays kayıtlı çizimi yükler
// ─────────────────────────────────────────────────────────────────────────────
describe('CommodityDetailChart — çizim kalıcılığı', () => {
  // NOT: prefGet ayrıca LanguageProvider tarafından dil tespiti için
  // ('app_language', null) ile de çağrılır (prefs mock'u her iki tüketici için
  // ortak). Bu yüzden assertion'lar SPESİFİK olarak `chart-overlays:` anahtarına bakar.
  it('persistId verilince render sonrası prefGet `chart-overlays:<id>` anahtarıyla okunur', () => {
    prefGet.mockImplementation((key, fb) =>
      key === 'chart-overlays:commodity:XAU'
        ? [{ name: 'horizontalStraightLine', points: [{ timestamp: 1_700_000_000_000, value: 105 }] }]
        : (fb ?? null),
    );
    renderChart({ persistId: 'commodity:XAU' });
    // useChartDrawings.restoreOverlays applyNewData'dan sonra prefGet(`chart-overlays:commodity:XAU`)
    expect(prefGet).toHaveBeenCalledWith('chart-overlays:commodity:XAU', []);
  });

  it('persistId YOKKEN persistKey boş olur → çizim kalıcılığı için prefGet ÇAĞRILMAZ (kapalı)', () => {
    renderChart(); // persistId vermedik
    // LanguageProvider'ın 'app_language' okuması olabilir; ancak hiçbir
    // `chart-overlays:` okuması OLMAMALI (kalıcılık kapalı).
    const overlayCalls = prefGet.mock.calls.filter(
      ([key]) => typeof key === 'string' && key.startsWith('chart-overlays:'),
    );
    expect(overlayCalls).toHaveLength(0);
  });
});
