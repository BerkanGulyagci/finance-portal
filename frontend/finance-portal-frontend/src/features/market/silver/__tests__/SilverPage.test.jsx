import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor} from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { LanguageProvider } from '../../../../context/LanguageContext';

// ─────────────────────────────────────────────────────────────────────────────
// MOCK'LAR
// SilverPage iki marketApi çağrısı yapar: getSilverSpot (açılışta) + getSilverHistory
// (range/currency değişince). jsdom'da ağ yoktur → ikisi de stub'lanır.
// Mock YOLU testin konumuna göre: __tests__/ → kaynak SilverPage 'api/marketApi'yi
// '../../../api/marketApi' ile import eder, test bir seviye daha derin olduğundan
// '../../../../api/marketApi'.
//
// AĞIR ÇOCUK BİLEŞENLER stub'lanır (SilverPage'in KENDİ mantığını izole etmek için):
//   • GoldChart      → klinecharts/canvas çeker (jsdom'da canvas YOK)
//   • InstrumentActionButtons → AuthContext + portfolioApi çeker (giriş yoksa null)
//   • Precious/Universal CompareButton → sadece Link; tanınır işaretle stub'lanır
// klinecharts ayrıca savunma amaçlı doğrudan da mock'lanır (GoldChart stub'lı olsa da
// transitif bir import init() çağırırsa canvas patlamasın).
// Saf sunum bileşenleri (GoldLoadingState/GoldErrorState/GoldChartToolbar/
// GoldSourceNotice) GERÇEK kullanılır → yalnız LanguageProvider yeter.
// ─────────────────────────────────────────────────────────────────────────────

const { getSilverSpot, getSilverHistory } = vi.hoisted(() => ({
  getSilverSpot: vi.fn(),
  getSilverHistory: vi.fn(),
}));
vi.mock('../../../../api/marketApi', () => ({
  getSilverSpot: (...a) => getSilverSpot(...a),
  getSilverHistory: (...a) => getSilverHistory(...a),
}));
vi.mock('../../../../api/marketApi.js', () => ({
  getSilverSpot: (...a) => getSilverSpot(...a),
  getSilverHistory: (...a) => getSilverHistory(...a),
}));

// klinecharts — canvas jsdom'da çalışmaz, init/dispose vb. no-op stub.
vi.mock('klinecharts', () => ({
  init: vi.fn(() => ({
    applyNewData: vi.fn(),
    createIndicator: vi.fn(),
    removeIndicator: vi.fn(),
    resize: vi.fn(),
    setStyles: vi.fn(),
    subscribeAction: vi.fn(),
    convertFromPixel: vi.fn(),
    createOverlay: vi.fn(),
    removeOverlay: vi.fn(),
  })),
  dispose: vi.fn(),
  registerOverlay: vi.fn(),
  registerIndicator: vi.fn(),
}));

// GoldChart stub — aldığı prop'ları data-attribute olarak yansıtır ki SilverPage'in
// hesapladığı displayPoints sayısı / chartMode / isDown / currency doğrulanabilsin.
vi.mock('../../gold/components/GoldChart', () => ({
  default: ({ points, chartMode, isDown, currency, loading }) => (
    <div
      data-testid="gold-chart"
      data-points={points?.length ?? 0}
      data-mode={chartMode}
      data-down={String(!!isDown)}
      data-currency={currency}
      data-loading={String(!!loading)}
    />
  ),
}));

// InstrumentActionButtons stub — gerçek bileşen AuthContext + portfolioApi çeker,
// giriş yoksa null döner; SilverPage'in onu doğru prop'larla çağırdığını işaretle doğrula.
vi.mock('../../../../components/instrument/InstrumentActionButtons', () => ({
  default: ({ assetType, symbol, name, price }) => (
    <div
      data-testid="instrument-actions"
      data-asset={assetType}
      data-symbol={symbol}
      data-name={name}
      data-price={price ?? ''}
    />
  ),
}));

vi.mock('../../../../components/common/PreciousCompareButton', () => ({
  default: () => <div data-testid="precious-compare" />,
}));

vi.mock('../../../../components/common/UniversalCompareButton', () => ({
  default: ({ assetType, symbol }) => (
    <div data-testid="universal-compare" data-asset={assetType} data-symbol={symbol} />
  ),
}));

import SilverPage from '../SilverPage';

// ── Sahte veri üreticileri (komponentin okuduğu alanlara birebir uygun) ───────
function spotPayload(overrides = {}) {
  return {
    official: true,
    stale: false,
    lastValidDate: '2026-06-04',
    source: 'BIST',
    fallback: false,
    disclaimer: null,
    silverGramCloseTry: 38.5,
    silverGramTry: 38.7,
    closeTryKg: 38500,
    weightedAverageTryKg: 38600,
    silverUsdOns: 31.25,
    ...overrides,
  };
}

// İki günlük basit gram-TRY çizgi serisi (close artıyor → isDown=false).
// SilverPage çizgi modunda `close` (yoksa weightedAverage) okur; mult try_gram'da 1.
function gramHistory() {
  return {
    source: 'BIST',
    official: true,
    fallback: false,
    disclaimer: null,
    points: [
      { date: '2026-05-01', close: 36.0, weightedAverage: 36.1, high: 36.5, low: 35.8 },
      { date: '2026-05-02', close: 38.0, weightedAverage: 38.2, high: 38.6, low: 37.7 },
    ],
  };
}

// LanguageProvider varsayılan dili "tr" → t(key) anahtarın kendisini döndürür.
// MemoryRouter useSearchParams için gerekli; ?tab=... ile derin link verilebilir.
function renderPage({ route = '/market/silver' } = {}) {
  return render(
    <MemoryRouter initialEntries={[route]}>
      <LanguageProvider>
        <SilverPage />
      </LanguageProvider>
    </MemoryRouter>
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  localStorage.clear();
  // Varsayılan başarılı yanıtlar — gerektiğinde testler override eder.
  getSilverSpot.mockResolvedValue(spotPayload());
  getSilverHistory.mockResolvedValue(gramHistory());
});

describe('SilverPage (Gümüş) — jsdom + testing-library', () => {
  it('smoke: throw etmeden render olur; başlık ve BİST açıklaması görünür', async () => {
    renderPage();
    expect(screen.getByRole('heading', { name: 'Gümüş' })).toBeInTheDocument();
    // Başlık altı açıklama parçası (parçalı metin → kısmi eşleşme).
    expect(screen.getByText(/kıymetli maden referanslarına dayanır\./)).toBeInTheDocument();
    // Açılışta spot + history yüklenir.
    await waitFor(() => expect(getSilverSpot).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(getSilverHistory).toHaveBeenCalled());
  });

  it('üç sekme (Gram ₺ / Kg ₺ / Ons $) render edilir', async () => {
    renderPage();
    await screen.findByTestId('gold-chart');
    expect(screen.getByRole('button', { name: 'Gram Gümüş (₺)' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Kg Gümüş (₺)' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Ons Gümüş ($)' })).toBeInTheDocument();
  });

  it('açılışta yükleme iskeleti (SkeletonMetalDetail) gösterilir, spot gelince kaybolur', async () => {
    let resolveSpot;
    getSilverSpot.mockReturnValue(new Promise((r) => { resolveSpot = r; }));
    const { container } = renderPage();
    // Çözülmeden önce skeleton placeholder'ları (animate-pulse) DOM'da.
    expect(container.querySelector('.animate-pulse')).toBeInTheDocument();
    resolveSpot(spotPayload());
    // Çözülünce istatistik bölümü (örn. grafik stub) belirir, skeleton gider.
    await screen.findByTestId('gold-chart');
    expect(container.querySelector('.animate-pulse')).not.toBeInTheDocument();
  });

  it('spot çağrısı başarısız olunca hata mesajı gösterilir', async () => {
    getSilverSpot.mockRejectedValue(new Error('boom'));
    renderPage();
    expect(await screen.findByText('Gümüş verisi alınamadı.')).toBeInTheDocument();
    // Hata durumunda grafik render edilmez.
    expect(screen.queryByTestId('gold-chart')).not.toBeInTheDocument();
  });

  it('history gelince istatistikler hesaplanır (yükseliş → emerald + + işaretli değişim)', async () => {
    renderPage();
    await screen.findByTestId('gold-chart');
    // İstatistik etiketleri (StatRow label anahtarları t ile aynen render).
    expect(screen.getByText('En Yüksek')).toBeInTheDocument();
    expect(screen.getByText('En Düşük')).toBeInTheDocument();
    expect(screen.getByText('Dönem Kapanış')).toBeInTheDocument();
    // Dönem Değişimi: (38 - 36) / 36 * 100 ≈ +5,56% → "+" ile başlar.
    const changeRows = screen.getAllByText(/^\+\d/);
    expect(changeRows.length).toBeGreaterThan(0);
    // En yüksek = 38,60 (high'lar arası max) → çizgi modunda high yok, displayClose kullanılır
    // → en yüksek close 38,00. Sembol ₺ ile birlikte görünür.
    expect(screen.getAllByText(/₺38,00/).length).toBeGreaterThan(0);
  });

  it('düşüş serisinde fiyat rose (kırmızı) renkte ve grafik isDown=true alır', async () => {
    getSilverHistory.mockResolvedValue({
      points: [
        { date: '2026-05-01', close: 40.0 },
        { date: '2026-05-02', close: 36.0 },
      ],
    });
    const { container } = renderPage();
    const chart = await screen.findByTestId('gold-chart');
    expect(chart).toHaveAttribute('data-down', 'true');
    // Büyük fiyat metni rose-600 sınıfını taşır.
    expect(container.querySelector('.text-rose-600')).toBeInTheDocument();
  });

  it('grafik stub doğru prop\'ları alır (points sayısı, line modu, TRY currency)', async () => {
    renderPage();
    const chart = await screen.findByTestId('gold-chart');
    expect(chart).toHaveAttribute('data-points', '2');
    expect(chart).toHaveAttribute('data-mode', 'line');
    expect(chart).toHaveAttribute('data-currency', 'TRY');
  });

  it('Ons ($) sekmesine geçince getSilverHistory USD para birimiyle çağrılır; currency=USD', async () => {
    renderPage();
    await screen.findByTestId('gold-chart');
    // Ons sekmesine tıkla.
    fireEvent.click(screen.getByRole('button', { name: 'Ons Gümüş ($)' }));
    await waitFor(() => expect(getSilverHistory).toHaveBeenCalledWith(expect.anything(), 'USD'));
    // Grafik currency prop'u USD'ye döner.
    await waitFor(() => {
      expect(screen.getByTestId('gold-chart')).toHaveAttribute('data-currency', 'USD');
    });
    // USD sekmesinde USD/Ons istatistiği görünür.
    expect(screen.getByText('USD/Ons')).toBeInTheDocument();
  });

  it('aralık değişince (3M) getSilverHistory yeni range ile yeniden çağrılır', async () => {
    renderPage();
    await screen.findByTestId('gold-chart');
    getSilverHistory.mockClear();
    // Toolbar range butonu "3A" (RANGE_LABELS['3M']).
    fireEvent.click(screen.getByRole('button', { name: '3A' }));
    await waitFor(() => expect(getSilverHistory).toHaveBeenCalledWith('3M', 'TRY'));
  });

  it('yenile butonu loadHistory\'yi yeniden tetikler', async () => {
    renderPage();
    await screen.findByTestId('gold-chart');
    getSilverHistory.mockClear();
    fireEvent.click(screen.getByTitle('Yenile'));
    await waitFor(() => expect(getSilverHistory).toHaveBeenCalledTimes(1));
  });

  it('mum (candle) moduna geçince grafik data-mode=candle olur ve sentetik açılış uyarısı çıkar', async () => {
    renderPage();
    await screen.findByTestId('gold-chart');
    // Toolbar'da "Mum" butonu (canCandle && kısa range).
    fireEvent.click(screen.getByRole('button', { name: 'Mum' }));
    await waitFor(() => {
      expect(screen.getByTestId('gold-chart')).toHaveAttribute('data-mode', 'candle');
    });
    // SilverPage'in kendi mum uyarısı metni (sentetik açılış).
    expect(
      screen.getByText(/Gümüş mum grafiğinde açılış değeri BIST tarafından verilmediği için/)
    ).toBeInTheDocument();
  });

  it('history null dönerse (hata) grafik 0 nokta alır ama sayfa çökmez', async () => {
    getSilverHistory.mockResolvedValue(null);
    renderPage();
    const chart = await screen.findByTestId('gold-chart');
    expect(chart).toHaveAttribute('data-points', '0');
    // Spot geldiği için sayfa gövdesi yine render → başlık durur.
    expect(screen.getByRole('heading', { name: 'Gümüş' })).toBeInTheDocument();
  });

  it('URL ?tab=usd_ons ile açılınca doğrudan Ons sekmesi aktif olur (USD history)', async () => {
    renderPage({ route: '/market/silver?tab=usd_ons' });
    await screen.findByTestId('gold-chart');
    await waitFor(() => expect(getSilverHistory).toHaveBeenCalledWith(expect.anything(), 'USD'));
    expect(screen.getByText('USD/Ons')).toBeInTheDocument();
  });

  it('aksiyon ve karşılaştır butonları doğru COMMODITY sembolüyle render edilir', async () => {
    renderPage();
    await screen.findByTestId('gold-chart');
    // try_gram → PRECIOUS_CAT GRAM_TRY → "SILVER:GRAM_TRY".
    const actions = screen.getByTestId('instrument-actions');
    expect(actions).toHaveAttribute('data-asset', 'COMMODITY');
    expect(actions).toHaveAttribute('data-symbol', 'SILVER:GRAM_TRY');
    const universal = screen.getByTestId('universal-compare');
    expect(universal).toHaveAttribute('data-symbol', 'SILVER:GRAM_TRY');
    expect(screen.getByTestId('precious-compare')).toBeInTheDocument();
  });

  it('alt spot özet kartları (Gram/Kg/Ons) spot değerleriyle render edilir', async () => {
    renderPage();
    await screen.findByTestId('gold-chart');
    // Kartlardaki "Borsa İstanbul" etiketi birden çok kez geçer.
    expect(screen.getAllByText('Borsa İstanbul').length).toBeGreaterThan(0);
    // Gram kapanış 38,50 (silverGramCloseTry) kart değeri olarak görünür.
    expect(screen.getAllByText(/₺38,50/).length).toBeGreaterThan(0);
  });

  it('stale (eski veri) bayrağı işaretliyse "Eski Veri" rozeti gösterilir', async () => {
    getSilverSpot.mockResolvedValue(spotPayload({ stale: true }));
    renderPage();
    await screen.findByTestId('gold-chart');
    expect(screen.getByText('Eski Veri')).toBeInTheDocument();
  });
});
