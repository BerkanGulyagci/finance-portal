import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';

// ── Mock'lar ─────────────────────────────────────────────────────────────────
// DashboardPage; iki context (Auth/Toast), iki API modülü (portfolioApi/marketApi),
// portföy barrel'ı (registry + allocation), GridBoard ve bir sürü alt-kart + grafik
// kütüphanesi (recharts/klinecharts üzerinden) çeker. jsdom'da canvas/ağ/grid-boyut
// yoktur → veri kaynaklarını ve AĞIR/AĞ bağlı alt komponentleri stub'larız.
// Böylece test SADECE DashboardPage'in kendi mantığını doğrular:
//   - başlık + (auth'a göre) aksiyon butonları + StatTiles özetleri
//   - GridBoard'a hangi kartların (sıra/koşul) verildiği
//   - "Grafik Ekle / Favori / Alarm" arama modları ve modal akışları
//   - favori ekle/çıkar (api + toast) ve portföy gizle/sıfırla
// StatTiles / EconomyCard / Portfolio*/Recent*/Favorites* kartları GERÇEK kalır
// (saf DOM) → gerçek entegrasyonu test ederiz.

// Auth durumu testten testte değişebilsin diye mutable holder.
const authState = { isAuthenticated: true, username: 'Berkan' };
vi.mock('../../../context/AuthContext', () => ({
  useAuth: () => authState,
}));

const toastSuccess = vi.fn();
const toastError = vi.fn();
vi.mock('../../../context/ToastContext', () => ({
  useToast: () => ({ success: toastSuccess, error: toastError, push: vi.fn() }),
}));

// portfolioApi — DashboardPage'in çağırdığı tüm fonksiyonlar.
const getMyPortfolios = vi.fn();
const getWatchlistItems = vi.fn();
const addWatchlistItem = vi.fn();
const deleteWatchlistItem = vi.fn();
const createPortfolio = vi.fn();
vi.mock('../../../api/portfolioApi', () => ({
  getMyPortfolios: (...a) => getMyPortfolios(...a),
  getWatchlistItems: (...a) => getWatchlistItems(...a),
  addWatchlistItem: (...a) => addWatchlistItem(...a),
  deleteWatchlistItem: (...a) => deleteWatchlistItem(...a),
  createPortfolio: (...a) => createPortfolio(...a),
}));

// marketApi — açılışta tetiklenen piyasa/ekonomi çağrıları.
const getCryptos = vi.fn();
const getStocks = vi.fn();
const getAllTefasFunds = vi.fn();
const getFxTcmb = vi.fn();
const getEconomy = vi.fn();
const getEconomicIndicators = vi.fn();
vi.mock('../../../api/marketApi', () => ({
  getCryptos: (...a) => getCryptos(...a),
  getStocks: (...a) => getStocks(...a),
  getAllTefasFunds: (...a) => getAllTefasFunds(...a),
  getFxTcmb: (...a) => getFxTcmb(...a),
  getEconomy: (...a) => getEconomy(...a),
  getEconomicIndicators: (...a) => getEconomicIndicators(...a),
}));

// Portföy barrel'ı: registry'ler ağır analiz/ grafik komponentleri çekiyor → boş
// bırak (charts/pfCharts/wlCharts varsayılan boş zaten). calculateAllocationByType
// ise StatTiles "Risk/Dağılım" hesabını besler → gerçeğe yakın basit bir uygulama.
vi.mock('../../portfolio', () => {
  function calculateAllocationByType(holdings) {
    const groups = {};
    for (const h of holdings ?? []) {
      const mv = Number(h.marketValue) || 0;
      if (mv <= 0) continue;
      groups[h.assetType] = (groups[h.assetType] ?? 0) + mv;
    }
    const total = Object.values(groups).reduce((s, v) => s + v, 0);
    const rows = Object.entries(groups)
      .map(([type, value]) => ({ type, name: type, value, sharePct: total > 0 ? (value / total) * 100 : 0 }))
      .sort((a, b) => b.value - a.value);
    return { rows, total };
  }
  return {
    calculateAllocationByType,
    CHART_DONUT_COLORS: ['#093eaa', '#0ea5e9', '#10b981'],
    formatSharePercent: (v) => `%${Number(v).toFixed(1)}`,
    getWatchlistDetailPath: (assetType, symbol) => `/market/${String(assetType).toLowerCase()}/${symbol}`,
    ANALYTICS_BY_KEY: {},
    WL_CHART_BY_KEY: {},
  };
});

// recharts ResponsiveContainer jsdom'da 0-boyut verir → children'ı doğrudan render et.
vi.mock('recharts', async (orig) => {
  const actual = await orig();
  return { ...actual, ResponsiveContainer: ({ children }) => children };
});

// GridBoard (react-grid-layout) jsdom'da gerçek bir grid çizmeye çalışır (WidthProvider/
// ResizeObserver). DashboardPage testinin amacı GRID değil, HANGİ kartların hangi koşulda
// items'a verildiğidir → GridBoard'u kartların node'larını düz basan basit bir kabukla değiştir.
vi.mock('../../../components/common/GridBoard', () => ({
  default: ({ items }) => (
    <div data-testid="grid-board">
      {(items ?? []).map((it) => (
        <div key={it.key} data-card-key={it.key}>{it.node}</div>
      ))}
    </div>
  ),
}));

// Ağ/grafik bağlı ALT KARTLAR — tanınabilir işaret + (gerekli) props köprüsü.
vi.mock('../components/MarketMoversCard', () => ({
  default: () => <div data-testid="movers-card">movers</div>,
}));
vi.mock('../components/VolumeLeadersCard', () => ({
  default: () => <div data-testid="volume-leaders-card">volume</div>,
}));
vi.mock('../components/PersonalNewsCard', () => ({
  default: () => <div data-testid="personal-news-card">news</div>,
}));
vi.mock('../components/MarketListCard', () => ({
  default: ({ title }) => <div data-testid="market-list-card">{title}</div>,
}));
vi.mock('../components/MiniAssetChart', () => ({
  default: ({ symbol, onRemove }) => (
    <div data-testid="mini-asset-chart">
      <span>chart:{symbol}</span>
      <button onClick={onRemove}>remove-chart</button>
    </div>
  ),
}));

// Arama modalı: onSelect ile sahte bir enstrüman seç + onClose köprüsü.
vi.mock('../../../components/instrument/InstrumentSearchModal', () => ({
  default: ({ portfolioName, allowIndices, onSelect, onClose }) => (
    <div data-testid="search-modal" data-portfolio={portfolioName} data-indices={String(allowIndices)}>
      <button onClick={() => onSelect({ assetType: 'STOCK', symbol: 'THYAO', name: 'Türk Hava Yolları' })}>
        pick-instrument
      </button>
      <button onClick={onClose}>close-search</button>
    </div>
  ),
}));
vi.mock('../../../components/instrument/AlarmCreateModal', () => ({
  default: ({ instrument, onClose }) => (
    <div data-testid="alarm-modal">
      <span>alarm:{instrument?.symbol}</span>
      <button onClick={onClose}>close-alarm</button>
    </div>
  ),
}));

import DashboardPage from '../DashboardPage';
import { LanguageProvider } from '../../../context/LanguageContext';

// Varsayılan dil "tr" → t(key) anahtarın kendisini döndürür; {count} enterpole edilir.
function renderPage() {
  return render(
    <MemoryRouter>
      <LanguageProvider>
        <DashboardPage />
      </LanguageProvider>
    </MemoryRouter>,
  );
}

// Özet/dağılım hesapları için yeterli alanlı bir HOLDINGS portföyü.
function holdingsPortfolio(over = {}) {
  return {
    id: 1,
    name: 'Ana Portföy',
    portfolioType: 'HOLDINGS',
    currency: 'TRY',
    totalMarketValue: 1500,
    totalCost: 1000,
    totalProfitLoss: 500,
    holdings: [
      { assetType: 'STOCK', symbol: 'ASELS', currency: 'TRY', marketValue: 1500, changePercent: 2 },
    ],
    transactions: [
      { id: 11, symbol: 'ASELS', assetType: 'STOCK', transactionType: 'BUY', quantity: 10, price: 100, transactionDate: '2026-05-01' },
    ],
    ...over,
  };
}

beforeEach(() => {
  vi.clearAllMocks();
  localStorage.clear();

  // jsdom'da olmayan tarayıcı API'leri (react-grid-layout WidthProvider bunlara dokunur).
  globalThis.ResizeObserver = class { observe() {} unobserve() {} disconnect() {} };
  if (!window.matchMedia) {
    window.matchMedia = vi.fn().mockImplementation((query) => ({
      matches: false, media: query, onchange: null,
      addListener: vi.fn(), removeListener: vi.fn(),
      addEventListener: vi.fn(), removeEventListener: vi.fn(), dispatchEvent: vi.fn(),
    }));
  }

  // Auth: çoğu test giriş yapılmış durumda; gerektiğinde override edilir.
  authState.isAuthenticated = true;
  authState.username = 'Berkan';

  // Varsayılan API dönüşleri — testler gerektiğinde override eder.
  getMyPortfolios.mockResolvedValue([]);
  getWatchlistItems.mockResolvedValue([]);
  addWatchlistItem.mockResolvedValue({});
  deleteWatchlistItem.mockResolvedValue({});
  createPortfolio.mockResolvedValue({ id: 99 });
  getCryptos.mockResolvedValue([]);
  getStocks.mockResolvedValue({ content: [] });
  getAllTefasFunds.mockResolvedValue([]);
  getFxTcmb.mockResolvedValue({ rates: [{ symbol: 'USD', sell: 32, changePercent: 0.5 }] });
  getEconomy.mockResolvedValue({ groups: [] });
  getEconomicIndicators.mockResolvedValue({ inflation: 38.1, policyRate: 45 });
});

describe('DashboardPage (jsdom + testing-library + React 19)', () => {
  it('smoke: mock\'larla render edilir; başlık + kullanıcı adı + aksiyon butonları görünür', async () => {
    renderPage();

    expect(screen.getByRole('heading', { name: 'Hoş geldin, Berkan' })).toBeInTheDocument();
    expect(screen.getByText('Kontrol Paneli')).toBeInTheDocument();
    expect(screen.getByTestId('grid-board')).toBeInTheDocument();
    // Auth'lu → 3 aksiyon: Alarm Kur, Karşılaştır, Grafik Ekle.
    expect(screen.getByRole('button', { name: /Alarm Kur/ })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Karşılaştır/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Grafik Ekle/ })).toBeInTheDocument();

    // Açılış efektinde piyasa/ekonomi çağrıları yapılır.
    await waitFor(() => expect(getCryptos).toHaveBeenCalled());
    expect(getStocks).toHaveBeenCalled();
    expect(getAllTefasFunds).toHaveBeenCalled();
    expect(getMyPortfolios).toHaveBeenCalled();
  });

  it('giriş yapılmışsa StatTiles ve kullanıcıya özel kartlar (Portföylerim/Favoriler/Son İşlemler) gelir', async () => {
    renderPage();

    // StatTiles özet etiketleri (auth'a bağlı).
    expect(await screen.findByText('Toplam Değer')).toBeInTheDocument();
    expect(screen.getByText('Günlük K/Z')).toBeInTheDocument();
    expect(screen.getByText('Toplam Getiri')).toBeInTheDocument();
    expect(screen.getByText('Risk / Dağılım')).toBeInTheDocument();

    // Kullanıcı kartları + her zaman görünen piyasa kartları.
    expect(screen.getByText('Portföylerim')).toBeInTheDocument();
    expect(screen.getByText('Favoriler')).toBeInTheDocument();
    expect(screen.getByText('Son İşlemler')).toBeInTheDocument();
    expect(screen.getByText('Ekonomi Göstergeleri')).toBeInTheDocument();
    expect(screen.getByTestId('movers-card')).toBeInTheDocument();
    expect(screen.getByTestId('personal-news-card')).toBeInTheDocument();
    // Üç MarketListCard (Hisse/Kripto/Fon).
    expect(screen.getByText('Hisse Senetleri')).toBeInTheDocument();
    expect(screen.getByText('Kripto Piyasası')).toBeInTheDocument();
    expect(screen.getByText('Fonlar (TEFAS)')).toBeInTheDocument();
  });

  it('giriş YAPILMAMIŞSA başlık sade kalır, kullanıcıya özel kart/aksiyonlar gizlenir', async () => {
    authState.isAuthenticated = false;
    authState.username = null;
    renderPage();

    // username yok → "Hoş geldin" (virgülsüz).
    expect(screen.getByRole('heading', { name: 'Hoş geldin' })).toBeInTheDocument();
    // Alarm Kur (auth-gated) yok; Karşılaştır linki + Grafik Ekle butonu koşulsuz → herkese açık.
    expect(screen.queryByRole('button', { name: /Alarm Kur/ })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Karşılaştır/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Grafik Ekle/ })).toBeInTheDocument();

    // StatTiles ve kullanıcı kartları yok; piyasa kartları yine var.
    expect(screen.queryByText('Toplam Değer')).not.toBeInTheDocument();
    expect(screen.queryByText('Portföylerim')).not.toBeInTheDocument();
    expect(screen.queryByText('Favoriler')).not.toBeInTheDocument();
    expect(screen.getByText('Ekonomi Göstergeleri')).toBeInTheDocument();
    expect(screen.getByTestId('movers-card')).toBeInTheDocument();

    // Giriş yokken portföy çekilmez.
    expect(getMyPortfolios).not.toHaveBeenCalled();
  });

  it('portföyler + favoriler yüklenince StatTiles toplamları ve kart içerikleri doğru render edilir', async () => {
    getMyPortfolios.mockResolvedValue([
      holdingsPortfolio(),
      { id: 2, name: 'İzleme', portfolioType: 'WATCHLIST' },
    ]);
    getWatchlistItems.mockResolvedValue([
      { id: 7, symbol: 'BTC', assetType: 'CRYPTO', lastPrice: 100, changePercent: 1.2 },
    ]);
    renderPage();

    // Portföy kartı başlığı + Toplam Değer metriği (1500 → ₺1.500,00).
    // (Hem StatTiles "Toplam Değer" hem PortfoliosCard satırı aynı tutarı basar → getAllByText.)
    expect(await screen.findByText('Ana Portföy')).toBeInTheDocument();
    expect(screen.getAllByText('₺1.500,00').length).toBeGreaterThan(0);

    // Favori satırı izleme listesinden yüklenir (getWatchlistItems wls.id ile).
    await waitFor(() => expect(getWatchlistItems).toHaveBeenCalledWith(2));
    expect(await screen.findByText('BTC')).toBeInTheDocument();

    // Son İşlemler kartında alış işlemi (ASELS) görünür.
    expect(screen.getAllByText('ASELS').length).toBeGreaterThan(0);

    // Risk/Dağılım: tek tür (STOCK %100) → ASELS tek varlık türü.
    expect(screen.getByText(/varlık türü/)).toBeInTheDocument();
  });

  it('boş veri: portföy/favori yoksa kartlar "yok" mesajlarını gösterir', async () => {
    getMyPortfolios.mockResolvedValue([]);
    renderPage();

    expect(await screen.findByText('Görüntülenecek portföy yok.')).toBeInTheDocument();
    expect(screen.getByText('Henüz favoriniz yok.')).toBeInTheDocument();
    expect(screen.getByText('Henüz işlem yapmadınız.')).toBeInTheDocument();
  });

  it('ekonomi göstergeleri: TÜFE/politika faizi (indicators) + çekirdek/ABD CPI (economy yoy) EconomyCard\'a yansır', async () => {
    getEconomicIndicators.mockResolvedValue({ inflation: 38.1, policyRate: 45 });
    getEconomy.mockResolvedValue({
      groups: [{
        indicators: [
          { key: 'cekirdek', yoyChangePercent: 30.44 },
          { key: 'abdCpi', yoyChangePercent: 4.25 },
        ],
      }],
    });
    renderPage();

    // EconomyCard: TÜFE %38.10, politika faizi %45.00 (toFixed(2)).
    expect(await screen.findByText('%38.10')).toBeInTheDocument();
    expect(screen.getByText('%45.00')).toBeInTheDocument();
    // Çekirdek Enflasyon ve ABD CPI economy yoyChangePercent'ten gelir.
    expect(screen.getByText('%30.44')).toBeInTheDocument();
    expect(screen.getByText('%4.25')).toBeInTheDocument();
  });

  it('"Grafik Ekle" → arama modalı açılır (allowIndices=true); seçim grafiği GridBoard\'a ekler ve kaldırılır', async () => {
    renderPage();

    fireEvent.click(screen.getByRole('button', { name: /Grafik Ekle/ }));
    const modal = await screen.findByTestId('search-modal');
    // chart modunda endeksler seçilebilir.
    expect(modal).toHaveAttribute('data-indices', 'true');

    // Enstrüman seç → MiniAssetChart eklenir, modal kapanır, localStorage'a yazılır.
    fireEvent.click(within(modal).getByText('pick-instrument'));
    expect(await screen.findByTestId('mini-asset-chart')).toBeInTheDocument();
    expect(screen.getByText('chart:THYAO')).toBeInTheDocument();
    expect(screen.queryByTestId('search-modal')).not.toBeInTheDocument();
    await waitFor(() =>
      expect(JSON.parse(localStorage.getItem('fp-dashboard-charts') || '[]')).toHaveLength(1));

    // Grafiği kaldır → DOM'dan ve localStorage'dan çıkar.
    fireEvent.click(screen.getByText('remove-chart'));
    await waitFor(() => expect(screen.queryByTestId('mini-asset-chart')).not.toBeInTheDocument());
    expect(JSON.parse(localStorage.getItem('fp-dashboard-charts') || '[]')).toHaveLength(0);
  });

  it('"Favori Ekle" → addWatchlistItem çağrılır, başarı toast\'ı çıkar ve liste yenilenir', async () => {
    // İlk yükte 1 izleme listesi var → favori oraya eklenir (createPortfolio çağrılmaz).
    getMyPortfolios.mockResolvedValue([{ id: 2, name: 'İzleme', portfolioType: 'WATCHLIST' }]);
    renderPage();
    await screen.findByText('Favoriler');

    fireEvent.click(screen.getByRole('button', { name: /^Ekle$/ }));
    const modal = await screen.findByTestId('search-modal');
    // favori modunda endeksler seçilemez.
    expect(modal).toHaveAttribute('data-indices', 'false');

    fireEvent.click(within(modal).getByText('pick-instrument'));

    await waitFor(() => expect(addWatchlistItem).toHaveBeenCalledWith(
      2, { symbol: 'THYAO', assetType: 'STOCK', notes: '' }));
    expect(createPortfolio).not.toHaveBeenCalled();
    expect(toastSuccess).toHaveBeenCalledWith('Favorilere eklendi.');
    // Modal kapanır + liste yeniden çekilir (ilk yük + favori sonrası = 2).
    expect(screen.queryByTestId('search-modal')).not.toBeInTheDocument();
    await waitFor(() => expect(getMyPortfolios.mock.calls.length).toBeGreaterThanOrEqual(2));
  });

  it('favori ekleme "already" hatası verirse uygun hata toast\'ı gösterir', async () => {
    getMyPortfolios.mockResolvedValue([{ id: 2, name: 'İzleme', portfolioType: 'WATCHLIST' }]);
    addWatchlistItem.mockRejectedValue({ response: { data: { message: 'Asset already in watchlist' } } });
    renderPage();
    await screen.findByText('Favoriler');

    fireEvent.click(screen.getByRole('button', { name: /^Ekle$/ }));
    fireEvent.click(within(await screen.findByTestId('search-modal')).getByText('pick-instrument'));

    await waitFor(() => expect(toastError).toHaveBeenCalledWith('Bu varlık zaten favorilerinizde.'));
    expect(toastSuccess).not.toHaveBeenCalled();
  });

  it('favori çıkar → deleteWatchlistItem çağrılır ve satır listeden kalkar', async () => {
    getMyPortfolios.mockResolvedValue([{ id: 2, name: 'İzleme', portfolioType: 'WATCHLIST' }]);
    getWatchlistItems.mockResolvedValue([
      { id: 7, symbol: 'BTC', assetType: 'CRYPTO', lastPrice: 100, changePercent: 1.2 },
    ]);
    renderPage();

    expect(await screen.findByText('BTC')).toBeInTheDocument();
    // FavoritesCard satırındaki "Favorilerden çıkar" başlıklı buton.
    fireEvent.click(screen.getByTitle('Favorilerden çıkar'));

    await waitFor(() => expect(deleteWatchlistItem).toHaveBeenCalledWith(2, 7));
    await waitFor(() => expect(screen.queryByText('BTC')).not.toBeInTheDocument());
  });

  it('"Alarm Kur" → arama modalı, seçim AlarmCreateModal\'ı açar; kapatınca kalkar', async () => {
    renderPage();

    fireEvent.click(screen.getByRole('button', { name: /Alarm Kur/ }));
    const modal = await screen.findByTestId('search-modal');
    fireEvent.click(within(modal).getByText('pick-instrument'));

    // Arama modalı kapanır, alarm modalı seçilen enstrümanla açılır.
    const alarm = await screen.findByTestId('alarm-modal');
    expect(within(alarm).getByText('alarm:THYAO')).toBeInTheDocument();
    expect(screen.queryByTestId('search-modal')).not.toBeInTheDocument();

    fireEvent.click(within(alarm).getByText('close-alarm'));
    await waitFor(() => expect(screen.queryByTestId('alarm-modal')).not.toBeInTheDocument());
  });

  it('portföyü panodan gizle → satır kalkar, "gizli" sayacı görünür; arama modalı close ile kapanır', async () => {
    getMyPortfolios.mockResolvedValue([
      holdingsPortfolio({ id: 1, name: 'Ana Portföy' }),
      holdingsPortfolio({
        id: 3, name: 'İkinci Portföy',
        holdings: [{ assetType: 'FUND', symbol: 'TTE', currency: 'TRY', marketValue: 500, changePercent: 1 }],
        transactions: [{ id: 21, symbol: 'TTE', assetType: 'FUND', transactionType: 'SELL', quantity: 5, price: 50, transactionDate: '2026-05-02' }],
      }),
    ]);
    renderPage();
    await screen.findByText('Ana Portföy');

    // PortfoliosCard satırındaki "Panodan kaldır" butonu (ilk satır).
    const hideButtons = screen.getAllByTitle('Panodan kaldır');
    fireEvent.click(hideButtons[0]);

    await waitFor(() => expect(screen.queryByText('Ana Portföy')).not.toBeInTheDocument());
    // Diğer portföy hâlâ duruyor + gizli-geri-getir butonu (sayaç) belirir.
    expect(screen.getByText('İkinci Portföy')).toBeInTheDocument();
    expect(screen.getByTitle('Gizlenen portföyleri geri getir')).toBeInTheDocument();

    // localStorage'a gizli pid yazıldı.
    expect(JSON.parse(localStorage.getItem('fp-dashboard-hidden-portfolios') || '[]')).toContain(1);
  });

  it('arama modalı onClose ile (seçim yapmadan) kapanır', async () => {
    renderPage();
    fireEvent.click(screen.getByRole('button', { name: /Grafik Ekle/ }));
    const modal = await screen.findByTestId('search-modal');
    fireEvent.click(within(modal).getByText('close-search'));
    await waitFor(() => expect(screen.queryByTestId('search-modal')).not.toBeInTheDocument());
  });
});
