import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent} from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { LanguageProvider } from '../../../../context/LanguageContext';

// ─────────────────────────────────────────────────────────────────────────────
// MOCK STRATEJİSİ
// StockDetailPage ağır çocuk komponentler (klinecharts canvas grafikleri:
// CandlestickChart/LineChart, AuthContext+portfolioApi'ye bağlı
// InstrumentActionButtons, kendi ağını çeken RelatedViopContracts) ve marketApi
// (ağ) içerir. jsdom'da canvas/ağ olmadığından bunların hepsi stub'lanır.
// LanguageProvider GERÇEK (t anahtarı döndürür → Türkçe etiketler aynen render).
// react-router-dom: useParams=symbol override; MemoryRouter/Link gerçek.
// ─────────────────────────────────────────────────────────────────────────────

// klinecharts — modül seviyesinde registerOverlay ÇAĞRILIYOR (import anında),
// ayrıca chart komponentleri init/dispose kullanır. Canvas jsdom'da yok → stub.
vi.mock('klinecharts', () => ({
  init: vi.fn(() => ({
    applyNewData: vi.fn(), createIndicator: vi.fn(), removeIndicator: vi.fn(),
    resize: vi.fn(), setStyles: vi.fn(), subscribeAction: vi.fn(),
    convertFromPixel: vi.fn(), createOverlay: vi.fn(), removeOverlay: vi.fn(),
  })),
  dispose: vi.fn(), registerOverlay: vi.fn(), registerIndicator: vi.fn(),
}));

// Grafik çocuk komponentleri — sade stub; hangi modun render edildiğini görmek için
// gelen symbol prop'unu yazar.
vi.mock('../components/CandlestickChart', () => ({
  default: ({ symbol }) => <div data-testid="candlestick-chart">{symbol}</div>,
}));
vi.mock('../components/LineChart', () => ({
  default: ({ symbol }) => <div data-testid="line-chart">{symbol}</div>,
}));

// Kendi ağını (VİOP) çeken bölüm — sade stub.
vi.mock('../components/RelatedViopContracts', () => ({
  default: ({ symbol }) => <div data-testid="related-viop">{symbol}</div>,
}));

// AuthContext + portfolioApi'ye bağlı buton kümesi — sade stub.
vi.mock('../../../../components/instrument/InstrumentActionButtons', () => ({
  default: ({ symbol, name, price }) => (
    <div data-testid="action-buttons" data-name={name} data-price={price ?? ''}>{symbol}</div>
  ),
}));

// marketApi — StockDetailPage'in doğrudan kullandığı iki çağrı stub. Her test
// kendi dönüş değerini ayarlar.
vi.mock('../../../../api/marketApi', () => ({
  getStockMidasDetail: vi.fn(),
  getMarketPriceHistory: vi.fn(),
}));

// react-router-dom — useParams (symbol) override; MemoryRouter/Link gerçek.
vi.mock('react-router-dom', async (orig) => {
  const actual = await orig();
  return { ...actual, useParams: () => ({ symbol: 'THYAO.IS' }) };
});

import { getStockMidasDetail, getMarketPriceHistory } from '../../../../api/marketApi';
import StockDetailPage from '../StockDetailPage';

// ── Test verisi ──────────────────────────────────────────────────────────────
const MIDAS_FULL = {
  name: 'Türk Hava Yolları',
  logoUrl: 'https://img/thyao.png',
  currentPrice: '320,50',
  dailyVolume: '1.250.000.000',
  dailyChange: '+5,20',
  dailyChangePercent: '+1,65%',
  bid: '320,40',
  ask: '320,60',
  openPrice: '316,00',
  volumeLot: '3.900.000',
  upperLimit: '347,50',
  lowerLimit: '284,50',
  weeklyHigh: '325,00',
  weeklyLow: '300,00',
  monthlyHigh: '330,00',
  monthlyLow: '290,00',
  marketCap: '442.000.000.000',
  capital: '1.380.000.000',
  peRatio: '4,12',
  pbRatio: '0,95',
  freeFloat: '50,9',
  foreignRatio: '38,4',
  volatility: '2,10',
  netProfit: '50.000.000.000',
  shareholders: [
    { name: 'Türkiye Wealth Fund', sharePercent: '49,12' },
    { name: 'Halka Açık', sharePercent: '50,88' },
  ],
  ceo: 'Bilal Ekşi',
  foundedDate: '1933',
  ipoDate: '1990',
  sector: 'Havayolu Taşımacılığı',
  employeeCount: '85.000',
  address: 'Yeşilköy, İstanbul',
  country: 'Türkiye',
  description: 'THY bayrak taşıyıcı havayolu şirketidir.\n\nDünya genelinde geniş uçuş ağına sahiptir.',
};

// 60 elemanlı artan kapanış serisi → buildTrendItem MA20<MA50<last → UP (Yükseliş)
const PRICE_HISTORY_UP = {
  timestamps: Array.from({ length: 60 }, (_, i) => 1700000000 + i * 86400),
  closePrices: Array.from({ length: 60 }, (_, i) => 100 + i),
};

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/market/stocks/THYAO.IS']}>
      <LanguageProvider>
        <StockDetailPage />
      </LanguageProvider>
    </MemoryRouter>,
  );
}

describe('StockDetailPage (Vitest + @testing-library/react)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    // Varsayılan happy-path: midas tam, trend serisi yükselişte.
    getStockMidasDetail.mockResolvedValue(MIDAS_FULL);
    getMarketPriceHistory.mockResolvedValue(PRICE_HISTORY_UP);
  });

  it('mock\'larla hata fırlatmadan render olur ve geri linkini gösterir (smoke)', async () => {
    renderPage();
    // Geri linki "/market/stocks" her zaman render edilir (loading sırasında tek link odur).
    expect(screen.getByRole('link')).toHaveAttribute('href', '/market/stocks');
    // Async dolum sonrası başlık ticker + ad ile gelir.
    expect(await screen.findByRole('heading', {
      name: /THYAO Hisse - Türk Hava Yolları/,
    })).toBeInTheDocument();
  });

  it('başlangıçta skeleton gösterir, veri gelince kaldırır', async () => {
    // midas çağrısını askıda tut → loading=true kalsın.
    let resolveMidas;
    getStockMidasDetail.mockReturnValue(new Promise((res) => { resolveMidas = res; }));
    const { container } = renderPage();
    // Skeleton placeholder (animate-pulse) DOM'da (yükleme).
    expect(container.querySelectorAll('.animate-pulse').length).toBeGreaterThan(0);
    // Metrik kartı henüz yok.
    expect(screen.queryByText('Güncel Fiyat')).not.toBeInTheDocument();
    // Çöz → loading biter, içerik gelir.
    resolveMidas(MIDAS_FULL);
    expect(await screen.findByText('Güncel Fiyat')).toBeInTheDocument();
    expect(container.querySelectorAll('.animate-pulse').length).toBe(0);
  });

  it('midas dolu olunca fiyat, hacim ve finansal metrik kartlarını gösterir', async () => {
    renderPage();
    await screen.findByText('Güncel Fiyat');
    // Güncel fiyat birden çok yerde görünür (üst kart + metrik kartı) → getAllByText.
    expect(screen.getAllByText('320,50').length).toBeGreaterThan(0);
    expect(screen.getByText('Günlük İşlem Hacmi')).toBeInTheDocument();
    // Hacim hem üst kartta hem "Günlük Hacim (TL)" metrik kartında → birden çok eşleşme.
    expect(screen.getAllByText('1.250.000.000').length).toBeGreaterThan(0);
    // Metrik kartı etiketleri (t anahtarı = Türkçe metin).
    expect(screen.getByText('Alış')).toBeInTheDocument();
    expect(screen.getByText('Satış')).toBeInTheDocument();
    expect(screen.getByText('F/K')).toBeInTheDocument();
    expect(screen.getByText('Piyasa Değeri')).toBeInTheDocument();
    // Günlük değişim satırı (+ yön = pozitif).
    expect(screen.getByText(/Günlük Değişim:/)).toBeInTheDocument();
  });

  it('ortaklık yapısı, şirket bilgisi ve açıklama bölümlerini doldurur', async () => {
    renderPage();
    await screen.findByText('Güncel Fiyat');
    // Ortaklık tablosu başlıkları + ortak satırları.
    expect(screen.getByText('Ticari Ünvan')).toBeInTheDocument();
    expect(screen.getByText('Pay Oranı (%)')).toBeInTheDocument();
    expect(screen.getByText('Türkiye Wealth Fund')).toBeInTheDocument();
    expect(screen.getByText('49,12')).toBeInTheDocument();
    // Şirket Hakkında satırları (InfoRow).
    expect(screen.getByText('CEO')).toBeInTheDocument();
    expect(screen.getByText('Bilal Ekşi')).toBeInTheDocument();
    expect(screen.getByText('Sektör')).toBeInTheDocument();
    expect(screen.getByText('Havayolu Taşımacılığı')).toBeInTheDocument();
    // Açıklama paragrafları (\n\n ile bölünür).
    expect(screen.getByText('THY bayrak taşıyıcı havayolu şirketidir.')).toBeInTheDocument();
    expect(screen.getByText('Dünya genelinde geniş uçuş ağına sahiptir.')).toBeInTheDocument();
  });

  it('varsayılan modda Mum grafiği render eder; "Alan"a tıklayınca alan grafiğine geçer', async () => {
    renderPage();
    await screen.findByText('Güncel Fiyat');
    // Varsayılan chartMode='tv' → CandlestickChart (symbol prop'u iletilir).
    expect(screen.getByTestId('candlestick-chart')).toHaveTextContent('THYAO.IS');
    expect(screen.queryByTestId('line-chart')).not.toBeInTheDocument();
    // "Alan" moduna geç.
    fireEvent.click(screen.getByRole('button', { name: 'Alan' }));
    expect(screen.getByTestId('line-chart')).toBeInTheDocument();
    expect(screen.queryByTestId('candlestick-chart')).not.toBeInTheDocument();
  });

  it('1Y kapanış serisinden yükseliş trend rozetini gösterir', async () => {
    renderPage();
    // buildTrendItem(artış serisi) → multi-signal UP → "Yükseliş" rozeti.
    expect(await screen.findByText('Yükseliş')).toBeInTheDocument();
    expect(getMarketPriceHistory).toHaveBeenCalledWith('STOCK', 'THYAO.IS', '1Y');
  });

  it('karşılaştır linki + aksiyon butonlarını doğru prop\'larla render eder', async () => {
    renderPage();
    await screen.findByText('Güncel Fiyat');
    // "Karşılaştır" linki compare sayfasına symbol query'siyle gider.
    const compareLink = screen.getByRole('link', { name: /Karşılaştır/ });
    expect(compareLink).toHaveAttribute(
      'href',
      expect.stringContaining('/market/stocks/compare?add=THYAO.IS'),
    );
    // InstrumentActionButtons stub'ı symbol/name/price alır.
    const actions = screen.getByTestId('action-buttons');
    expect(actions).toHaveTextContent('THYAO.IS');
    expect(actions).toHaveAttribute('data-name', 'Türk Hava Yolları');
    expect(actions).toHaveAttribute('data-price', '320,50');
    // VİOP bölümü ticker (.IS soyulmuş) ile render edilir.
    expect(screen.getByTestId('related-viop')).toHaveTextContent('THYAO');
  });

  it('midas null dönerse "detay verisi bulunamadı" boş-durum mesajını gösterir', async () => {
    getStockMidasDetail.mockResolvedValue(null);
    getMarketPriceHistory.mockResolvedValue({ timestamps: [], closePrices: [] });
    renderPage();
    // !midas dalı → boş-durum mesajı.
    expect(await screen.findByText('Bu hisse için detay verisi bulunamadı.')).toBeInTheDocument();
    // midas'a bağlı kartlar yok.
    expect(screen.queryByText('Ortaklık Yapısı')).not.toBeInTheDocument();
    expect(screen.queryByText('Türkiye Wealth Fund')).not.toBeInTheDocument();
    // Başlık yine de ticker + "Hisse" ile (midas.name olmadan) render edilir.
    expect(screen.getAllByRole('heading', { name: /THYAO Hisse/ })[0]).toBeInTheDocument();
  });

  it('trend serisi boşsa rozet göstermez (sadece tire / hiç badge)', async () => {
    getMarketPriceHistory.mockResolvedValue({ timestamps: [], closePrices: [] });
    renderPage();
    await screen.findByText('Güncel Fiyat');
    // closePrices boş → buildTrendItem null → trendItem null → TrendBadge hiç render edilmez.
    expect(screen.queryByText('Yükseliş')).not.toBeInTheDocument();
    expect(screen.queryByText('Düşüş')).not.toBeInTheDocument();
  });
});
