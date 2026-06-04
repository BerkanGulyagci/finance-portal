import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';

// ─────────────────────────────────────────────────────────────────────────────
// MOCK'lar — kaynağı (TefasPriceChart.jsx) GERÇEKTEN okuyarak belirlendi.
//
// 1) klinecharts: jsdom'da canvas yok → init/dispose stub'lanır. init() bir chart
//    instance döndürür; komponent (ve useChartDrawings hook'u) şu metotları çağırır:
//    setStyles / applyNewData / createIndicator / removeIndicator / createOverlay /
//    removeOverlay / subscribeAction / getOverlayById. Hepsi vi.fn ile no-op.
// 2) marketApi.getFundPriceHistory: yalnızca 5Y aralığında çağrılan HTTP isteği →
//    ağ yok. Yol: components/__tests__ → src/api = ../../../../../api/marketApi
// 3) LanguageContext: GERÇEK LanguageProvider kullanılır (tr varsayılan, t(key)
//    çevirisi olmayan anahtarda anahtarın kendisini döndürür) — bu sayede Türkçe
//    metinleri (ör. "Fiyat geçmişi bulunamadı.", "İndikatör", aralık etiketleri)
//    DOM'da aratabiliriz. prefs.js gerçek localStorage'ı kullanır (jsdom'da var).
// ─────────────────────────────────────────────────────────────────────────────

vi.mock('klinecharts', () => {
  const makeChart = () => ({
    applyNewData: vi.fn(),
    createIndicator: vi.fn(() => 'candle_pane'),
    removeIndicator: vi.fn(),
    resize: vi.fn(),
    setStyles: vi.fn(),
    subscribeAction: vi.fn(),
    convertFromPixel: vi.fn(),
    createOverlay: vi.fn(() => 'overlay_x'),
    removeOverlay: vi.fn(),
    getOverlayById: vi.fn(() => null),
  });
  return {
    init: vi.fn(() => makeChart()),
    dispose: vi.fn(),
    registerIndicator: vi.fn(),
    registerOverlay: vi.fn(),
  };
});

vi.mock('../../../../../api/marketApi', () => ({
  getFundPriceHistory: vi.fn(),
}));

import { init as klineInit } from 'klinecharts';
import { getFundPriceHistory } from '../../../../../api/marketApi';
import { LanguageProvider } from '../../../../../context/LanguageContext';
import TefasPriceChart from '../TefasPriceChart';

// ─── Yardımcılar ──────────────────────────────────────────────────────────────

/**
 * Komponentin beklediği priceHistory şekli: { date: 'YYYY-MM-DD', price }.
 * buildFundChartSeries son N günü (range'e göre) cutoff'lar → tarihler GÜNCEL
 * olmalı, yoksa 1Y penceresinde elenir. Bugünden geriye doğru günlük seri üretir.
 */
function makePriceHistory(count = 30, startPrice = 100) {
  const pts = [];
  for (let i = count - 1; i >= 0; i--) {
    const d = new Date();
    d.setDate(d.getDate() - i);
    const date = d.toISOString().slice(0, 10);
    pts.push({ date, price: startPrice + (count - 1 - i) }); // monoton artan → +% (Yükselen)
  }
  return pts;
}

const OK_HISTORY = makePriceHistory(30, 100); // 100 → 129, yükseliş

function renderChart(props = {}) {
  return render(
    <LanguageProvider>
      <TefasPriceChart code="TGE" fonTipi="YAT" priceHistory={OK_HISTORY} monthlyReturns={[]} {...props} />
    </LanguageProvider>
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  localStorage.clear();

  // jsdom'da olmayan global'ler — alt bileşenler/hook kullanabilir.
  globalThis.ResizeObserver = class { observe() {} unobserve() {} disconnect() {} };

  // 5Y dalında çağrılan API — varsayılan: boş seri (her test override edebilir).
  getFundPriceHistory.mockResolvedValue({ points: [] });
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('TefasPriceChart (Vitest + @testing-library/react, jsdom)', () => {
  describe('temel render (smoke)', () => {
    it("mock'larla throw etmeden render olur ve klinecharts init edilir", () => {
      expect(() => renderChart()).not.toThrow();
      // filtered.length > 0 olduğu için grafik init effect'i koşar.
      expect(klineInit).toHaveBeenCalled();
    });

    it('dolu veride aralık (range) butonlarını gösterir', () => {
      renderChart();
      // FUND_CHART_RANGES etiketleri (t(key) → anahtar): 1H, 1 Ay, 3 Ay, 6 Ay, 1 Yıl, 5Y
      ['1H', '1 Ay', '3 Ay', '6 Ay', '1 Yıl', '5Y'].forEach((lbl) =>
        expect(screen.getByText(lbl)).toBeInTheDocument()
      );
    });

    it('dönem getirisi yüzdesini (pozitif → +%) ve veri-noktası sayısını gösterir', () => {
      renderChart();
      // 100 → 129 yükseliş: pozitif yüzde "+...%" görünür.
      expect(screen.getByText(/^\+\d/)).toBeInTheDocument();
      // "<n> veri noktası · <kaynak>" satırı (1Y default → Rasyonet).
      expect(screen.getByText(/veri noktası/)).toBeInTheDocument();
      expect(screen.getByText(/Rasyonet/)).toBeInTheDocument();
    });

    it('İndikatör menüsü ve çizim araç gruplarını (toolbar) render eder', () => {
      renderChart();
      expect(screen.getByText('İndikatör')).toBeInTheDocument();
      // FundDrawingToolbar grup başlıkları
      expect(screen.getByText('Çizgiler')).toBeInTheDocument();
      expect(screen.getByText('Kanallar')).toBeInTheDocument();
      expect(screen.getByText('Fibonacci')).toBeInTheDocument();
    });

    it('grafik DOM konteynerini (kline mount noktası) sabit yükseklikle oluşturur', () => {
      const { container } = renderChart();
      const mount = container.querySelector('div[id^="fund_chart_"]');
      expect(mount).toBeTruthy();
      expect(mount).toHaveStyle({ height: '320px' });
    });
  });

  describe('koşullu render dalları (loading / empty / dolu)', () => {
    it('boş priceHistory (1Y, ağ yok) → "Fiyat geçmişi bulunamadı." gösterir', () => {
      renderChart({ priceHistory: [] });
      expect(screen.getByText('Fiyat geçmişi bulunamadı.')).toBeInTheDocument();
      // Boş dalda grafik init EDİLMEZ (filtered.length === 0).
      expect(klineInit).not.toHaveBeenCalled();
    });

    it('1Y (varsayılan) dalında ağ isteği YAPMAZ (Rasyonet senkron)', () => {
      renderChart();
      expect(getFundPriceHistory).not.toHaveBeenCalled();
    });

    it('5Y seçilince TEFAS boş + yerel veri yetersiz → boş mesaj gösterir', async () => {
      getFundPriceHistory.mockResolvedValue({ points: [] });
      // Dolu 1Y verisiyle başla (aralık butonları render olsun); 5Y'ye geçince
      // TEFAS boş döner ve 5Y penceresinde yerel veri yetersiz kalır → boş mesaj.
      renderChart();
      const btn = (await screen.findAllByText('5Y'))[0];
      fireEvent.click(btn);
      await waitFor(() => expect(getFundPriceHistory).toHaveBeenCalledWith('TGE', '5Y', 'YAT'));
    });
  });

  describe('aralık değişimi (etkileşim)', () => {
    it('5Y aralığına tıklayınca getFundPriceHistory(code, "5Y", fonTipi) çağrılır', async () => {
      renderChart();
      expect(getFundPriceHistory).not.toHaveBeenCalled();

      fireEvent.click(screen.getAllByText('5Y')[0]);

      await waitFor(() =>
        expect(getFundPriceHistory).toHaveBeenCalledWith('TGE', '5Y', 'YAT')
      );
    });

    it('5Y TEFAS noktası dönerse kaynak "TEFAS + Rasyonet" olur ve açıklama notu görünür', async () => {
      // Bugüne yakın 5Y TEFAS noktaları (cutoff: son 1825 gün) — yoksa elenir.
      const tefasPts = makePriceHistory(10, 50).map((p) => ({ date: p.date, price: p.price }));
      getFundPriceHistory.mockResolvedValue({ points: tefasPts });

      renderChart(); // priceHistory = OK_HISTORY (Rasyonet günlük)
      fireEvent.click(screen.getAllByText('5Y')[0]);

      // Birleştirilmiş kaynak etiketi
      expect(await screen.findByText(/TEFAS \+ Rasyonet/)).toBeInTheDocument();
      // 5Y + TEFAS+Rasyonet → alt açıklama paragrafı
      expect(
        screen.getByText(/derin geçmiş TEFAS, son ~1 yıl Rasyonet/)
      ).toBeInTheDocument();
    });

    it('5Y fetch reddedilse bile (TEFAS hata) yerel Rasyonet ile grafik render olur', async () => {
      getFundPriceHistory.mockRejectedValue(new Error('throttled'));

      renderChart(); // OK_HISTORY var → fallback noktalar mevcut
      fireEvent.click(screen.getAllByText('5Y')[0]);

      // Hata yutulur, Rasyonet noktalarıyla seri kurulur → boş mesaj GÖSTERİLMEZ.
      await waitFor(() => expect(getFundPriceHistory).toHaveBeenCalled());
      expect(
        screen.queryByText('Fiyat geçmişi bulunamadı.')
      ).not.toBeInTheDocument();
      expect(screen.getByText(/veri noktası/)).toBeInTheDocument();
    });
  });

  describe('indikatör / çizim etkileşimi', () => {
    it('"İndikatör" tıklanınca MA (MA20/MA50/MA200) ve Trend toggle\'ları açılır', () => {
      renderChart();
      fireEvent.click(screen.getByText('İndikatör'));
      // FUND_MA_DEFS etiketleri
      expect(screen.getByText('MA20')).toBeInTheDocument();
      expect(screen.getByText('MA50')).toBeInTheDocument();
      expect(screen.getByText('MA200')).toBeInTheDocument();
      // extras: Trend toggle
      expect(screen.getByText('Trend')).toBeInTheDocument();
    });

    it('MA20 toggle edilince chart.createIndicator MA ile çağrılır', () => {
      renderChart();
      const chart = klineInit.mock.results[0].value;
      chart.createIndicator.mockClear();

      fireEvent.click(screen.getByText('İndikatör'));
      fireEvent.click(screen.getByText('MA20'));

      // applyMA → createIndicator({ name:'MA', ... }, false, { id:'candle_pane' })
      expect(chart.createIndicator).toHaveBeenCalled();
      const arg0 = chart.createIndicator.mock.calls.at(-1)[0];
      expect(arg0).toMatchObject({ name: 'MA' });
      expect(arg0.calcParams).toContain(20);
    });

    it('çizim grubu (Çizgiler) açılınca alt araçları (Düz Çizgi vb.) listeler', () => {
      renderChart();
      fireEvent.click(screen.getByText('Çizgiler'));
      // FUND_DRAWING_TOOLS > Çizgiler grubu alt etiketleri
      expect(screen.getByText('Çizgi Segmenti')).toBeInTheDocument();
      expect(screen.getByText('Düz Çizgi')).toBeInTheDocument();
      expect(screen.getByText('Yatay Çizgi')).toBeInTheDocument();
    });

    it('bir çizim aracı seçilince chart.createOverlay çağrılır (handleSelectTool)', () => {
      renderChart();
      const chart = klineInit.mock.results[0].value;
      chart.createOverlay.mockClear();

      fireEvent.click(screen.getByText('Çizgiler'));
      fireEvent.click(screen.getByText('Düz Çizgi'));

      // useChartDrawings.handleSelectTool → createOverlay({ name:'straightLine', ... })
      expect(chart.createOverlay).toHaveBeenCalled();
      expect(chart.createOverlay.mock.calls.at(-1)[0]).toMatchObject({ name: 'straightLine' });
    });
  });
});
