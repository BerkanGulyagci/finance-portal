import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { MarketTicker } from '../MarketTicker';
import { LanguageProvider } from '../../../context/LanguageContext';
import { TICKER_PREFS_KEY } from '../../../utils/tickerPrefs';

// ── marketApi tamamen mock'lanır ─────────────────────────────────────────────
// MarketTicker mount'ta onlarca piyasa endpoint'i çağırır (FX/kripto/altın/BIST/
// ekonomi). jsdom'da ağ yok → hepsini vi.fn ile stub'la, her teste göre veri ver.
vi.mock('../../../api/marketApi', () => ({
  getFxTcmb: vi.fn(),
  getCryptos: vi.fn(),
  getGoldSpot: vi.fn(),
  getBankCurrencyRatesByCurrency: vi.fn(),
  getFxHistory: vi.fn(),
  getGoldHistory: vi.fn(),
  getCryptoChart: vi.fn(),
  getMarketPriceHistory: vi.fn(),
  getEconomicIndicators: vi.fn(),
}));

import * as marketApi from '../../../api/marketApi';

// Şerit açık/kapalı durumunu localStorage'da tutan anahtar (kaynaktaki sabitin aynısı).
const TICKER_OPEN_STORAGE_KEY = 'finance-portal-market-ticker-open';

// LanguageProvider varsayılan dili "tr" → t(key) anahtarın kendisini döndürür.
function renderTicker() {
  return render(
    <MemoryRouter>
      <LanguageProvider>
        <MarketTicker />
      </LanguageProvider>
    </MemoryRouter>
  );
}

/** Tüm mock'ları "boş/yok" varsayılanına çeker — testler gerektiğinde override eder. */
function stubEmptyDefaults() {
  marketApi.getFxTcmb.mockResolvedValue(null);
  marketApi.getCryptos.mockResolvedValue([]);
  marketApi.getGoldSpot.mockResolvedValue(null);
  marketApi.getBankCurrencyRatesByCurrency.mockResolvedValue([]);
  marketApi.getFxHistory.mockResolvedValue(null);
  marketApi.getGoldHistory.mockResolvedValue(null);
  marketApi.getCryptoChart.mockResolvedValue({});
  // BIST + özel varlık price-history: boş seri → o öğeler atlanır.
  marketApi.getMarketPriceHistory.mockResolvedValue({ closePrices: [], timestamps: [] });
  marketApi.getEconomicIndicators.mockResolvedValue(null);
}

let warnSpy;

beforeEach(() => {
  vi.clearAllMocks();
  localStorage.clear();
  // Kaynak, boş BIST verisinde bilerek console.warn atar — test çıktısını kirletmesin.
  warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});
  // requestAnimationFrame jsdom'da var; yine de kayan-şerit döngüsü no-op kalsın diye
  // callback'i hiç tetiklemeyen bir stub veririz (test asılmasın / sonsuz rAF olmasın).
  vi.spyOn(globalThis, 'requestAnimationFrame').mockReturnValue(1);
  vi.spyOn(globalThis, 'cancelAnimationFrame').mockImplementation(() => {});
  stubEmptyDefaults();
});

afterEach(() => {
  warnSpy?.mockRestore();
  vi.restoreAllMocks();
});

describe('MarketTicker (jsdom + testing-library)', () => {
  it('hiç piyasa öğesi yoksa null render eder (boş DOM)', async () => {
    const { container } = renderTicker();
    // load() async; tüm mock'lar boş döndüğü için items boş kalır → null.
    await waitFor(() => {
      expect(marketApi.getFxTcmb).toHaveBeenCalled();
    });
    expect(container).toBeEmptyDOMElement();
  });

  it('FX/kripto/ekonomi verisi gelince ilgili öğeleri (label) render eder', async () => {
    marketApi.getFxTcmb.mockResolvedValue({
      rates: [{ symbol: 'USD', sell: '34.5' }, { symbol: 'EUR', sell: '37.2' }],
    });
    marketApi.getCryptos.mockResolvedValue([
      { symbol: 'btc', id: 'bitcoin', currentPrice: 2500000, priceChangePercentage24h: 1.5 },
    ]);
    marketApi.getEconomicIndicators.mockResolvedValue({ inflation: '38.1', policyRate: '50' });

    renderTicker();

    // Async yükleme sonrası öğeler basılır; şerit `copies` kez tekrarlandığından getAllBy.
    const usd = await screen.findAllByText('USD/TRY');
    expect(usd.length).toBeGreaterThan(0);
    expect(screen.getAllByText('EUR/TRY').length).toBeGreaterThan(0);
    expect(screen.getAllByText('BTC').length).toBeGreaterThan(0);
    // Ekonomi: key 'eco:inflation' → label t('TÜFE') = "TÜFE", değer "%38.1".
    expect(screen.getAllByText('TÜFE').length).toBeGreaterThan(0);
    expect(screen.getAllByText('%38.1').length).toBeGreaterThan(0);
  });

  it('açık şeritte gizle butonu bulunur; tıklanınca kapanır ve "Göster" gösterilir', async () => {
    marketApi.getFxTcmb.mockResolvedValue({ rates: [{ symbol: 'USD', sell: '34.5' }] });

    renderTicker();

    // Veri gelene + şerit açılana kadar bekle (varsayılan açık).
    const hideBtn = await screen.findByLabelText('Piyasa şeridini gizle');
    expect(hideBtn).toBeInTheDocument();

    fireEvent.click(hideBtn);

    // Kapalı görünüm: "Göster" butonu + "Piyasa özeti gizli" metni.
    expect(await screen.findByText('Göster')).toBeInTheDocument();
    expect(screen.getByText('Piyasa özeti gizli')).toBeInTheDocument();
    // localStorage'a kapalı (0) yazılmalı.
    expect(localStorage.getItem(TICKER_OPEN_STORAGE_KEY)).toBe('0');
  });

  it('localStorage kapalı (0) ise kapalı başlar; "Göster"e tıklanınca açılır', async () => {
    localStorage.setItem(TICKER_OPEN_STORAGE_KEY, '0');
    marketApi.getFxTcmb.mockResolvedValue({ rates: [{ symbol: 'USD', sell: '34.5' }] });

    renderTicker();

    // Kapalı başladığı için önce "Göster" gelir (öğeler yüklendikten sonra).
    const showBtn = await screen.findByText('Göster');
    expect(showBtn).toBeInTheDocument();
    // Açık şeride özgü "gizle" butonu henüz YOK.
    expect(screen.queryByLabelText('Piyasa şeridini gizle')).not.toBeInTheDocument();

    fireEvent.click(showBtn);

    // Artık açık: gizle butonu + USD öğesi görünür.
    expect(await screen.findByLabelText('Piyasa şeridini gizle')).toBeInTheDocument();
    expect(screen.getAllByText('USD/TRY').length).toBeGreaterThan(0);
  });

  it('enabledKeys ile devre dışı bırakılan öğe (eco:inflation) render edilmez', async () => {
    // Sadece USD açık; ekonomi/diğer anahtarlar tercih listesinde değil → filtrelenir.
    localStorage.setItem(TICKER_PREFS_KEY, JSON.stringify(['fx:USD']));
    marketApi.getFxTcmb.mockResolvedValue({ rates: [{ symbol: 'USD', sell: '34.5' }] });
    marketApi.getEconomicIndicators.mockResolvedValue({ inflation: '38.1' });

    renderTicker();

    expect((await screen.findAllByText('USD/TRY')).length).toBeGreaterThan(0);
    // eco:inflation tercihte yok → TÜFE öğesi DOM'da olmamalı.
    await waitFor(() => {
      expect(marketApi.getEconomicIndicators).toHaveBeenCalled();
    });
    expect(screen.queryByText('TÜFE')).not.toBeInTheDocument();
  });

  it('yükleme hata fırlatırsa (getFxTcmb reject) çökmeyip null render eder', async () => {
    // getFxTcmb içindeki .catch(() => null) bu reddi yutar; yine de sağlamlık testi:
    // tüm akış try/catch'le sarılı, items boş → null.
    marketApi.getFxTcmb.mockRejectedValue(new Error('network down'));

    const { container } = renderTicker();
    await waitFor(() => {
      expect(marketApi.getFxTcmb).toHaveBeenCalled();
    });
    expect(container).toBeEmptyDOMElement();
  });
});
