import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';

// ─────────────────────────────────────────────────────────────────────────────
// MOCK'lar — kaynağı (CandlestickChart.jsx) GERÇEKTEN okuyarak belirlendi.
//
// 1) klinecharts: jsdom'da canvas YOK → init/dispose stub'lanır. init() bir chart
//    instance döndürür; komponent + useChartDrawings hook'u şu metotları çağırır:
//    applyNewData / createIndicator / removeIndicator / createOverlay / removeOverlay /
//    getOverlayById / setOffsetRightDistance / setBarSpace (fitVisibleToWindow,
//    requestAnimationFrame+try/catch içinde) / resize / setStyles / subscribeAction /
//    convertFromPixel. Hepsi vi.fn ile no-op. createIndicator alt-indikatör eklerken
//    paneId döndürmeli → 'pane_x' döndürür.
// 2) marketApi.getStockOhlc: mount'ta + her aralık değişiminde çağrılan TEK HTTP
//    isteği → ağ yok. Yol: components/__tests__ → src/api = ../../../../../api/marketApi
// 3) LanguageContext: GERÇEK LanguageProvider (tr varsayılan, t(key) çevirisi olmayan
//    anahtarda anahtarın kendisini döndürür) — bu sayede Türkçe metinleri (aralık
//    etiketleri, "İndikatör", "Mum grafiği verisi yüklenemedi.", kaynak notu) DOM'da
//    aratabiliriz. prefs.js gerçek localStorage kullanır (jsdom'da var; auth_token
//    olmadığı için sunucu PUT'u tetiklenmez → ağ yok).
// ─────────────────────────────────────────────────────────────────────────────

vi.mock('klinecharts', () => {
  const makeChart = () => ({
    applyNewData: vi.fn(),
    createIndicator: vi.fn(() => 'pane_x'),
    removeIndicator: vi.fn(),
    resize: vi.fn(),
    setStyles: vi.fn(),
    subscribeAction: vi.fn(),
    convertFromPixel: vi.fn(),
    createOverlay: vi.fn(() => 'overlay_x'),
    removeOverlay: vi.fn(),
    getOverlayById: vi.fn(() => null),
    setOffsetRightDistance: vi.fn(),
    setBarSpace: vi.fn(),
  });
  return {
    init: vi.fn(() => makeChart()),
    dispose: vi.fn(),
    registerIndicator: vi.fn(),
    registerOverlay: vi.fn(),
  };
});

vi.mock('../../../../../api/marketApi', () => ({
  getStockOhlc: vi.fn(),
}));

import { init as klineInit, dispose as klineDispose } from 'klinecharts';
import { getStockOhlc } from '../../../../../api/marketApi';
import { LanguageProvider } from '../../../../../context/LanguageContext';
import CandlestickChart from '../CandlestickChart';

// ─── Yardımcılar ──────────────────────────────────────────────────────────────

/**
 * getStockOhlc dönüş şekli: { time(saniye), open, high, low, close, volume }.
 * Komponent time*1000 ile ms'e çevirir, parseFloat(open)>0 olanları tutar, artan
 * timestamp'e sıralar. Bugünden geriye doğru günlük seri (gerçekçi).
 */
function makeOhlc(count = 40, startPrice = 100) {
  const rows = [];
  const dayMs = 86_400_000;
  const now = Date.now();
  for (let i = count - 1; i >= 0; i--) {
    const sec = Math.floor((now - i * dayMs) / 1000);
    const base = startPrice + (count - 1 - i);
    rows.push({
      time: sec,
      open: base,
      high: base + 2,
      low: base - 2,
      close: base + 1,
      volume: 1000 + i,
    });
  }
  return rows;
}

function renderChart(props = {}) {
  return render(
    <LanguageProvider>
      <CandlestickChart symbol="THYAO" {...props} />
    </LanguageProvider>,
  );
}

// jsdom'da ResizeObserver/requestAnimationFrame davranışı — güvenli stub'lar.
beforeEach(() => {
  localStorage.clear();
  vi.clearAllMocks();
  globalThis.ResizeObserver = class { observe() {} unobserve() {} disconnect() {} };
  // Varsayılan API cevabı — dolu veri; gerekirse test içinde override edilir.
  getStockOhlc.mockResolvedValue(makeOhlc(40));
});

// ─────────────────────────────────────────────────────────────────────────────
// 1) Smoke + temel iskelet
// ─────────────────────────────────────────────────────────────────────────────
describe('CandlestickChart — render (smoke)', () => {
  it('mock\'larla render olur, kaynak notu DOM\'da ve grafik init edilir (throw etmez)', async () => {
    renderChart();
    expect(
      screen.getByText('Kaynak: Yahoo Finance · OHLC verisi'),
    ).toBeInTheDocument();
    expect(klineInit).toHaveBeenCalled();
    // Async yükleme bitsin (getStockOhlc .then) → asılı promise kalmasın
    await waitFor(() => expect(getStockOhlc).toHaveBeenCalled());
  });

  it('tüm zaman aralığı butonlarını (1G … Tüm) render eder', () => {
    renderChart();
    ['1G', '1H', '1A', '3A', '6A', '1Y', '5Y', 'Tüm'].forEach((label) => {
      expect(screen.getByRole('button', { name: label })).toBeInTheDocument();
    });
  });

  it('DrawingToolbar çizim gruplarını ve İndikatör kontrolünü gösterir', () => {
    renderChart();
    ['Çizgiler', 'Kanallar', 'Fibonacci', 'Şekiller'].forEach((group) => {
      expect(screen.getByRole('button', { name: new RegExp(group) })).toBeInTheDocument();
    });
    expect(screen.getByRole('button', { name: /İndikatör/ })).toBeInTheDocument();
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// 2) Mount'taki veri çekme — varsayılan aralık (3A → ısınma '2y', interval '1d')
// ─────────────────────────────────────────────────────────────────────────────
describe('CandlestickChart — mount veri çekme', () => {
  it('varsayılan aralıkta getStockOhlc ISINMA range\'i (2y) ve 1d interval ile çağrılır', async () => {
    renderChart();
    // ohlcRangeIdx=3 → '3A' → range '3mo' → STOCK_WARMUP_RANGE['3mo']='2y'
    await waitFor(() => {
      expect(getStockOhlc).toHaveBeenCalledWith('THYAO', '2y', '1d');
    });
  });

  it('dolu veri yüklenince applyNewData çağrılır (chart\'a veri uygulanır)', async () => {
    renderChart();
    await waitFor(() => {
      const chart = klineInit.mock.results[0].value;
      expect(chart.applyNewData).toHaveBeenCalled();
    });
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// 3) Koşullu render dalları: hata / boş veri
// ─────────────────────────────────────────────────────────────────────────────
describe('CandlestickChart — koşullu render', () => {
  it('boş veri dönerse "Mum grafiği verisi yüklenemedi." hata mesajı gösterilir', async () => {
    getStockOhlc.mockResolvedValue([]); // data?.length falsy → setError(true)
    renderChart();
    expect(
      await screen.findByText('Mum grafiği verisi yüklenemedi.'),
    ).toBeInTheDocument();
  });

  it('getStockOhlc reject olursa (ağ hatası) yine hata mesajı gösterilir', async () => {
    getStockOhlc.mockRejectedValue(new Error('network'));
    renderChart();
    expect(
      await screen.findByText('Mum grafiği verisi yüklenemedi.'),
    ).toBeInTheDocument();
  });

  it('dolu veri yüklenince hata mesajı GÖSTERİLMEZ', async () => {
    renderChart();
    await waitFor(() => expect(getStockOhlc).toHaveBeenCalled());
    await waitFor(() => {
      expect(
        screen.queryByText('Mum grafiği verisi yüklenemedi.'),
      ).not.toBeInTheDocument();
    });
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// 4) Aralık seçimi etkileşimi → yeni range/interval ile yeniden çekim
// ─────────────────────────────────────────────────────────────────────────────
describe('CandlestickChart — aralık seçimi', () => {
  it('"1G" seçilince getStockOhlc gün-içi ayarla (ısınma 5d, interval 5m) yeniden çağrılır', async () => {
    renderChart();
    await waitFor(() => expect(getStockOhlc).toHaveBeenCalledWith('THYAO', '2y', '1d'));

    fireEvent.click(screen.getByRole('button', { name: '1G' }));

    // '1G' → range '1d' → STOCK_WARMUP_RANGE['1d']='5d', interval '5m'
    await waitFor(() => {
      expect(getStockOhlc).toHaveBeenCalledWith('THYAO', '5d', '5m');
    });
  });

  it('"Tüm" seçilince 10y range + 1d interval ile çekilir', async () => {
    renderChart();
    await waitFor(() => expect(getStockOhlc).toHaveBeenCalled());

    fireEvent.click(screen.getByRole('button', { name: 'Tüm' }));

    // 'Tüm' → range '10y' → STOCK_WARMUP_RANGE['10y']='10y', interval '1d'
    await waitFor(() => {
      expect(getStockOhlc).toHaveBeenCalledWith('THYAO', '10y', '1d');
    });
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// 5) İndikatör menüsü etkileşimi (MA + alt indikatörler)
// ─────────────────────────────────────────────────────────────────────────────
describe('CandlestickChart — İndikatör menüsü', () => {
  it('İndikatör butonuna tıklayınca MA + osilatör seçenekleri açılır', () => {
    renderChart();
    fireEvent.click(screen.getByRole('button', { name: /İndikatör/ }));

    ['MA20', 'MA50', 'MA200'].forEach((label) => {
      expect(screen.getByRole('button', { name: label })).toBeInTheDocument();
    });
    // SUB_INDICATORS: VOL→'Hacim', RSI, MACD, KDJ, BOLL
    ['Hacim', 'RSI', 'MACD', 'KDJ', 'BOLL'].forEach((label) => {
      expect(screen.getByRole('button', { name: label })).toBeInTheDocument();
    });
  });

  it('MA20 seçilince chart.createIndicator MA için çağrılır (candle_pane\'e)', async () => {
    renderChart();
    await waitFor(() => expect(getStockOhlc).toHaveBeenCalled());
    const chart = klineInit.mock.results[0].value;

    fireEvent.click(screen.getByRole('button', { name: /İndikatör/ }));
    fireEvent.click(screen.getByRole('button', { name: 'MA20' }));

    // applyMA → removeIndicator('candle_pane','MA') + createIndicator MA(name:'MA')
    expect(chart.removeIndicator).toHaveBeenCalledWith('candle_pane', 'MA');
    expect(chart.createIndicator).toHaveBeenCalledWith(
      expect.objectContaining({ name: 'MA', calcParams: [20] }),
      false,
      { id: 'candle_pane' },
    );
  });

  it('alt indikatör (RSI) seçilince ayrı pane olarak createIndicator çağrılır', async () => {
    renderChart();
    await waitFor(() => expect(getStockOhlc).toHaveBeenCalled());
    const chart = klineInit.mock.results[0].value;
    chart.createIndicator.mockClear();

    fireEvent.click(screen.getByRole('button', { name: /İndikatör/ }));
    fireEvent.click(screen.getByRole('button', { name: 'RSI' }));

    // toggleSubIndicator → createIndicator({name:'RSI'}, true, {height:80})
    expect(chart.createIndicator).toHaveBeenCalledWith(
      { name: 'RSI' },
      true,
      { height: 80 },
    );
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// 6) Cleanup — unmount'ta grafik dispose edilir
// ─────────────────────────────────────────────────────────────────────────────
describe('CandlestickChart — temizlik', () => {
  it('unmount olunca klinecharts dispose çağrılır', async () => {
    const { unmount } = renderChart();
    await waitFor(() => expect(getStockOhlc).toHaveBeenCalled());
    unmount();
    expect(klineDispose).toHaveBeenCalled();
  });
});
