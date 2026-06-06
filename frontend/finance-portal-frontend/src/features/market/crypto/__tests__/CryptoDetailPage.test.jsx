import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { LanguageProvider } from '../../../../context/LanguageContext';

// ─────────────────────────────────────────────────────────────────────────────
// MOCK STRATEJİSİ
// CryptoDetailPage ağır çocuk komponentler (klinecharts canvas grafikleri, çizim
// hook'u, AuthContext'e bağlı InstrumentActionButtons) ve marketApi (ağ) içerir.
// jsdom'da canvas/ağ olmadığından bunların hepsi stub'lanır; LanguageProvider ve
// react-router-dom (useParams=coinId, useNavigate spy) GERÇEK kullanılır.
// ─────────────────────────────────────────────────────────────────────────────

// klinecharts — canvas jsdom'da çalışmaz (chart komponentleri zaten mock'lu ama
// dolaylı import'a karşı güvenlik ağı).
vi.mock('klinecharts', () => ({
  init: vi.fn(() => ({
    applyNewData: vi.fn(), createIndicator: vi.fn(), removeIndicator: vi.fn(),
    resize: vi.fn(), setStyles: vi.fn(), subscribeAction: vi.fn(),
    convertFromPixel: vi.fn(), createOverlay: vi.fn(), removeOverlay: vi.fn(),
  })),
  dispose: vi.fn(), registerOverlay: vi.fn(), registerIndicator: vi.fn(),
}));

// Grafik komponentleri — render edildiklerini görmek için sade stub.
vi.mock('../../commodities/components/CommodityDetailChart', () => ({
  default: ({ points, loading, sourceNote }) => (
    <div data-testid="commodity-chart" data-loading={String(!!loading)} data-points={points?.length ?? 0}>
      {sourceNote}
    </div>
  ),
}));
vi.mock('../components/CryptoLineChart', () => ({
  default: () => <div data-testid="crypto-line-chart" />,
}));
vi.mock('../components/CompareDropdown', () => ({
  default: ({ onAdd }) => (
    <button data-testid="compare-dropdown" onClick={() => onAdd?.({ id: 'ethereum', symbol: 'ETH', name: 'Ethereum' })}>
      compare
    </button>
  ),
}));

// AuthContext'e + portfolioApi'ye bağlı buton kümeleri — sade stub.
vi.mock('../../../../components/instrument/InstrumentActionButtons', () => ({
  default: ({ symbol }) => <div data-testid="action-buttons">{symbol}</div>,
}));
vi.mock('../../../../components/common/UniversalCompareButton', () => ({
  default: ({ symbol }) => <button data-testid="universal-compare">{symbol}</button>,
}));

// marketApi — tüm ağ çağrıları stub. Her test kendi dönüş değerini ayarlar.
vi.mock('../../../../api/marketApi', () => ({
  getAllCryptos: vi.fn(),
  getCryptoDetail: vi.fn(),
  getCryptoChart: vi.fn(),
  getCryptoOhlc: vi.fn(),
  getCryptoFearGreed: vi.fn(),
}));

// react-router-dom — useParams (coinId) + useNavigate (spy) override; MemoryRouter/Link gerçek.
const navigateMock = vi.fn();
vi.mock('react-router-dom', async (orig) => {
  const actual = await orig();
  return { ...actual, useNavigate: () => navigateMock, useParams: () => ({ coinId: 'bitcoin' }) };
});

import {
  getAllCryptos, getCryptoDetail, getCryptoChart, getCryptoOhlc, getCryptoFearGreed,
} from '../../../../api/marketApi';
import CryptoDetailPage from '../CryptoDetailPage';

// ── Test verisi ──────────────────────────────────────────────────────────────
const BITCOIN_COIN = {
  id: 'bitcoin', symbol: 'btc', name: 'Bitcoin', image: 'https://img/btc.png',
  currentPrice: 2500000, marketCap: 50000000000000, marketCapRank: 1,
  totalVolume: 1200000000000, high24h: 2600000, low24h: 2400000,
  priceChange24h: 50000, priceChangePercentage24h: 2.5,
  priceChangePercentage1h: 0.3, priceChangePercentage7d: 5.2,
};

// Aynı sıralamaya yakın benzer coinler (similarCoins filtresi: |rank farkı| <= 10)
const NEAR_COINS = [
  { id: 'ethereum', symbol: 'eth', name: 'Ethereum', image: 'https://img/eth.png', marketCapRank: 2, priceChangePercentage24h: 1.1 },
  { id: 'tether',   symbol: 'usdt', name: 'Tether',   image: 'https://img/usdt.png', marketCapRank: 3, priceChangePercentage24h: -0.2 },
];

const DETAIL_FULL = {
  symbol: 'btc', name: 'Bitcoin',
  image: { small: 'https://img/btc.png' },
  market_cap_rank: 1,
  market_data: {
    current_price: { try: 2500000 }, market_cap: { try: 50000000000000 },
    total_volume: { try: 1200000000000 }, high_24h: { try: 2600000 }, low_24h: { try: 2400000 },
    price_change_24h: 50000, price_change_percentage_24h: 2.5,
    ath: { try: 3000000 }, atl: { try: 1000 },
    ath_change_percentage: { try: -16.6 }, atl_change_percentage: { try: 24999 },
    circulating_supply: 19500000, total_supply: 21000000, max_supply: 21000000,
  },
  description: { tr: 'Bitcoin merkeziyetsiz bir dijital para birimidir.', en: 'Bitcoin is decentralized.' },
  links: {
    homepage: ['https://bitcoin.org'],
    twitter_screen_name: 'bitcoin',
    repos_url: { github: ['https://github.com/bitcoin/bitcoin'] },
  },
  categories: ['Cryptocurrency', 'Layer 1 (L1)'],
};

// Çizgi grafik için CoinGecko market-chart formatı
const CHART_DATA = {
  prices: Array.from({ length: 30 }, (_, i) => [1700000000000 + i * 86400000, 2400000 + i * 1000]),
  total_volumes: Array.from({ length: 30 }, (_, i) => [1700000000000 + i * 86400000, 1000000 + i]),
};

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/market/crypto/bitcoin']}>
      <LanguageProvider>
        <CryptoDetailPage />
      </LanguageProvider>
    </MemoryRouter>,
  );
}

describe('CryptoDetailPage (Vitest + @testing-library/react)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    // Varsayılan happy-path: liste coin'i içerir, detail tam, chart dolu.
    getAllCryptos.mockResolvedValue([BITCOIN_COIN, ...NEAR_COINS]);
    getCryptoDetail.mockResolvedValue(DETAIL_FULL);
    getCryptoChart.mockResolvedValue(CHART_DATA);
    getCryptoOhlc.mockResolvedValue([]);
    getCryptoFearGreed.mockResolvedValue([]);
  });

  it('mock\'larla hata fırlatmadan render olur ve geri-dönüş linkini gösterir (smoke)', async () => {
    renderPage();
    // "Kripto Para" geri linki her zaman render edilir (loading'ten bağımsız).
    expect(screen.getByText('Kripto Para')).toBeInTheDocument();
    // Async dolum sonrası coin başlığı gelir.
    expect(await screen.findByRole('heading', { name: 'Bitcoin' })).toBeInTheDocument();
  });

  it('coin listede bulununca başlık kartını (ad/sembol/para birimi butonları) doldurur', async () => {
    renderPage();
    expect(await screen.findByRole('heading', { name: 'Bitcoin' })).toBeInTheDocument();
    // Sembol büyük harf gösterilir (uppercase CSS + ham 'btc' metni DOM'da).
    expect(screen.getAllByText('btc').length).toBeGreaterThan(0);
    // Para birimi seçici butonları
    expect(screen.getByRole('button', { name: 'TRY' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'USD' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'EUR' })).toBeInTheDocument();
    // Yan kartlar
    expect(screen.getByText('Dönemsel Getiri')).toBeInTheDocument();
    expect(screen.getByText('Piyasa Verileri')).toBeInTheDocument();
    // getAllCryptos ilk yüklemede 'try' (varsayılan) ile çağrılır.
    expect(getAllCryptos).toHaveBeenCalledWith('try');
  });

  it('para birimi butonuna tıklanınca seçili para birimiyle yeniden veri çeker', async () => {
    renderPage();
    await screen.findByRole('heading', { name: 'Bitcoin' });
    fireEvent.click(screen.getByRole('button', { name: 'USD' }));
    // currency state 'USD' olur → getAllCryptos('usd') tetiklenir.
    await waitFor(() => expect(getAllCryptos).toHaveBeenCalledWith('usd'));
  });

  it('detail verisinden Tüm Zamanlar (ATH/ATL), Bilgi ve kategori bölümlerini gösterir', async () => {
    renderPage();
    // Detail bağımlı bölümler async dolar.
    expect(await screen.findByText('Tüm Zamanlar')).toBeInTheDocument();
    expect(screen.getByText('En Yüksek')).toBeInTheDocument();
    expect(screen.getByText('En Düşük')).toBeInTheDocument();
    // Bilgi kartı: homepage hostname + twitter + github + kategoriler
    expect(screen.getByText('bitcoin.org')).toBeInTheDocument();
    expect(screen.getByText('@bitcoin')).toBeInTheDocument();
    expect(screen.getByText('GitHub')).toBeInTheDocument();
    expect(screen.getByText('Cryptocurrency')).toBeInTheDocument();
    // Açıklama bölümü ("... Hakkında") detail.description'dan gelir.
    expect(screen.getByText(/Hakkında/)).toBeInTheDocument();
  });

  it('grafik çizgi modunda CommodityDetailChart\'ı kaynak notuyla render eder', async () => {
    renderPage();
    const chart = await screen.findByTestId('commodity-chart');
    expect(chart).toBeInTheDocument();
    // CoinGecko bazlı kaynak notu (yahoo olmayan varsayılan aralık 1H).
    await waitFor(() => expect(chart.textContent).toMatch(/CoinGecko/));
    // Çizgi modu chart verisini çeker (getCryptoChart), OHLC çekmez.
    expect(getCryptoChart).toHaveBeenCalled();
    expect(getCryptoOhlc).not.toHaveBeenCalled();
  });

  it('"Mum" moduna geçilince OHLC verisini çeker', async () => {
    renderPage();
    await screen.findByRole('heading', { name: 'Bitcoin' });
    // Grafik modu butonu (emoji + "Mum" etiketi t(key) ile aynen render olur).
    fireEvent.click(screen.getByRole('button', { name: /Mum/ }));
    await waitFor(() => expect(getCryptoOhlc).toHaveBeenCalled());
  });

  it('benzer coinlere tıklayınca ilgili coin detayına yönlendirir', async () => {
    renderPage();
    // similarCoins: detail.categories dolu + allCoins yakın-rank → "Benzer Coinler".
    expect(await screen.findByText('Benzer Coinler')).toBeInTheDocument();
    // Ethereum benzer coin kartı (button) — tıkla → navigate('/market/crypto/ethereum').
    const ethBtn = screen.getByRole('button', { name: /Ethereum/ });
    fireEvent.click(ethBtn);
    expect(navigateMock).toHaveBeenCalledWith('/market/crypto/ethereum');
  });

  it('liste boş + detail market_data\'sız olunca coin kartını göstermez (boş dal)', async () => {
    getAllCryptos.mockResolvedValue([]);
    getCryptoDetail.mockResolvedValue({ symbol: 'btc', name: 'Bitcoin' }); // market_data yok
    renderPage();
    // Geri linki yine de var.
    expect(screen.getByText('Kripto Para')).toBeInTheDocument();
    // coin asla set edilmediğinden başlık kartı (heading) oluşmaz.
    await waitFor(() => expect(getAllCryptos).toHaveBeenCalled());
    expect(screen.queryByRole('heading', { name: 'Bitcoin' })).not.toBeInTheDocument();
    // "Piyasa Verileri" kartı da coin'e bağlı → yok.
    expect(screen.queryByText('Piyasa Verileri')).not.toBeInTheDocument();
  });
});
