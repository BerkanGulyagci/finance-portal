import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { LanguageProvider } from '../../../../context/LanguageContext';

// ─────────────────────────────────────────────────────────────────────────────
// MOCK'LAR
// PreciousMetalPage (Platin/Paladyum) iki marketApi çağrısı yapar:
//   • getPreciousMetalSpot(metal)                       → açılışta
//   • getPreciousMetalHistory(metal, range, currency)   → range/currency/sekme değişince
// jsdom'da ağ yoktur → ikisi de stub'lanır.
// Mock YOLU testin konumuna göre: __tests__/ → kaynak 'api/marketApi'yi
// '../../../api/marketApi' ile import eder; test bir seviye daha derin → '../../../../api/marketApi'.
//
// AĞIR ÇOCUK BİLEŞENLER stub'lanır (PreciousMetalPage'in KENDİ mantığını izole etmek için):
//   • GoldChart                → klinecharts/canvas çeker (jsdom'da canvas YOK)
//   • InstrumentActionButtons  → AuthContext + portfolioApi çeker (giriş yoksa null)
//   • Precious/Universal CompareButton → sadece Link; tanınır işaretle stub'lanır
// klinecharts ayrıca savunma amaçlı doğrudan mock'lanır (GoldChart stub'lı olsa da
// transitif bir import init() çağırırsa canvas patlamasın).
// Saf sunum bileşenleri (GoldLoadingState/GoldErrorState/GoldChartToolbar/
// GoldSourceNotice) GERÇEK kullanılır → yalnız LanguageProvider yeter.
// ─────────────────────────────────────────────────────────────────────────────

const { getPreciousMetalSpot, getPreciousMetalHistory } = vi.hoisted(() => ({
  getPreciousMetalSpot: vi.fn(),
  getPreciousMetalHistory: vi.fn(),
}));
vi.mock('../../../../api/marketApi', () => ({
  getPreciousMetalSpot: (...a) => getPreciousMetalSpot(...a),
  getPreciousMetalHistory: (...a) => getPreciousMetalHistory(...a),
}));
vi.mock('../../../../api/marketApi.js', () => ({
  getPreciousMetalSpot: (...a) => getPreciousMetalSpot(...a),
  getPreciousMetalHistory: (...a) => getPreciousMetalHistory(...a),
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

// GoldChart stub — aldığı prop'ları data-attribute olarak yansıtır ki sayfanın
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
// giriş yoksa null döner; sayfanın onu doğru prop'larla çağırdığını işaretle doğrula.
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
  default: ({ assetType, symbol, name }) => (
    <div data-testid="universal-compare" data-asset={assetType} data-symbol={symbol} data-name={name} />
  ),
}));

import PreciousMetalPage from '../PreciousMetalPage';

// ── Sahte veri üreticileri (komponentin okuduğu alanlara birebir uygun) ───────
function spotPayload(overrides = {}) {
  return {
    official: true,
    stale: false,
    lastValidDate: '2026-06-04',
    source: 'BIST',
    fallback: false,
    disclaimer: null,
    tryGram: 1100.5,   // try_gram sekmesi currentPrice
    tryKg: 1100500,    // try_kg
    usdOns: 950.25,    // usd_ons
    eurOns: 880.4,     // eur_ons
    ...overrides,
  };
}

// İki günlük basit gram-TRY çizgi serisi (close artıyor → isDown=false).
// buildDisplayPoints try_gram'da p.tryGram (yoksa p.value) okur.
function gramHistory() {
  return {
    source: 'BIST',
    official: true,
    fallback: false,
    disclaimer: null,
    points: [
      { date: '2026-05-01', tryGram: 1000.0, value: 1000.0 },
      { date: '2026-05-02', tryGram: 1100.0, value: 1100.0 },
    ],
  };
}

// USD ons serisi — usd_ons sekmesi p.usdOns okur.
function usdHistory() {
  return {
    source: 'BIST',
    official: true,
    points: [
      { date: '2026-05-01', usdOns: 900.0, value: 900.0 },
      { date: '2026-05-02', usdOns: 950.0, value: 950.0 },
    ],
  };
}

// LanguageProvider varsayılan dili "tr" → t(key) anahtarın kendisini döndürür.
// MemoryRouter useSearchParams için gerekli; ?tab=... ile derin link verilebilir.
function renderPage({ route = '/market/platinum', metal = 'platinum', metalName = 'Platin' } = {}) {
  return render(
    <MemoryRouter initialEntries={[route]}>
      <LanguageProvider>
        <PreciousMetalPage metal={metal} metalName={metalName} />
      </LanguageProvider>
    </MemoryRouter>
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  localStorage.clear();
  // Varsayılan başarılı yanıtlar — gerektiğinde testler override eder.
  getPreciousMetalSpot.mockResolvedValue(spotPayload());
  getPreciousMetalHistory.mockResolvedValue(gramHistory());
});

describe('PreciousMetalPage (Platin/Paladyum) — jsdom + testing-library', () => {
  it('smoke: throw etmeden render olur; metalName başlığı ve BİST açıklaması görünür', async () => {
    renderPage();
    expect(screen.getByRole('heading', { name: 'Platin' })).toBeInTheDocument();
    // Başlık altı açıklama parçası (parçalı metin → kısmi eşleşme).
    expect(screen.getByText(/kıymetli maden referanslarına dayanır\./)).toBeInTheDocument();
    // Açılışta spot + history yüklenir.
    await waitFor(() => expect(getPreciousMetalSpot).toHaveBeenCalledWith('platinum'));
    await waitFor(() => expect(getPreciousMetalHistory).toHaveBeenCalled());
  });

  it('metalName prop\'u değişince (Paladyum) başlık ve sekme etiketleri ona göre render edilir', async () => {
    renderPage({ metal: 'palladium', metalName: 'Paladyum' });
    await screen.findByTestId('gold-chart');
    expect(screen.getByRole('heading', { name: 'Paladyum' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Gram Paladyum (₺)' })).toBeInTheDocument();
    expect(getPreciousMetalSpot).toHaveBeenCalledWith('palladium');
  });

  it('dört sekme (Gram ₺ / Kg ₺ / Ons $ / Ons €) render edilir', async () => {
    renderPage();
    await screen.findByTestId('gold-chart');
    expect(screen.getByRole('button', { name: 'Gram Platin (₺)' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Kg Platin (₺)' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Ons Platin ($)' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Ons Platin (€)' })).toBeInTheDocument();
  });

  it('açılışta yükleme noktaları (GoldLoadingState) gösterilir, spot gelince kaybolur', async () => {
    let resolveSpot;
    getPreciousMetalSpot.mockReturnValue(new Promise((r) => { resolveSpot = r; }));
    const { container } = renderPage();
    // Çözülmeden önce animate-bounce noktaları DOM'da.
    expect(container.querySelector('.animate-bounce')).toBeInTheDocument();
    resolveSpot(spotPayload());
    // Çözülünce grafik stub'ı belirir.
    await screen.findByTestId('gold-chart');
    expect(container.querySelector('.animate-bounce')).not.toBeInTheDocument();
  });

  it('spot çağrısı başarısız olunca metalName ile hata mesajı gösterilir, grafik render edilmez', async () => {
    getPreciousMetalSpot.mockRejectedValue(new Error('boom'));
    renderPage();
    expect(await screen.findByText('Platin verisi alınamadı.')).toBeInTheDocument();
    expect(screen.queryByTestId('gold-chart')).not.toBeInTheDocument();
  });

  it('history gelince istatistikler hesaplanır (yükseliş → + işaretli değişim ve ₺ değerler)', async () => {
    renderPage();
    await screen.findByTestId('gold-chart');
    // İstatistik etiketleri (StatRow label anahtarları t ile aynen render).
    expect(screen.getByText('En Yüksek')).toBeInTheDocument();
    expect(screen.getByText('En Düşük')).toBeInTheDocument();
    expect(screen.getByText('Kapanış')).toBeInTheDocument();
    expect(screen.getByText('Dönem Değişimi')).toBeInTheDocument();
    // Dönem Değişimi: (1100 - 1000) / 1000 * 100 = +10,00% → "+" ile başlayan metin.
    expect(screen.getAllByText(/^\+\d/).length).toBeGreaterThan(0);
    // En yüksek close 1.100,00 → ₺ sembolü ile görünür (StatRow + başlık fiyatı).
    expect(screen.getAllByText(/₺1\.100,00/).length).toBeGreaterThan(0);
  });

  it('düşüş serisinde büyük fiyat rose (kırmızı) renkte ve grafik isDown=true alır', async () => {
    getPreciousMetalHistory.mockResolvedValue({
      points: [
        { date: '2026-05-01', tryGram: 1200.0 },
        { date: '2026-05-02', tryGram: 1000.0 },
      ],
    });
    const { container } = renderPage();
    const chart = await screen.findByTestId('gold-chart');
    expect(chart).toHaveAttribute('data-down', 'true');
    expect(container.querySelector('.text-rose-600')).toBeInTheDocument();
  });

  it('grafik stub doğru prop\'ları alır: 2 nokta, line modu (canCandle:false), TRY currency', async () => {
    renderPage();
    const chart = await screen.findByTestId('gold-chart');
    expect(chart).toHaveAttribute('data-points', '2');
    expect(chart).toHaveAttribute('data-mode', 'line');
    expect(chart).toHaveAttribute('data-currency', 'TRY');
  });

  it('canCandle:false → toolbar\'da "Mum" grafik butonu render edilmez', async () => {
    renderPage();
    await screen.findByTestId('gold-chart');
    expect(screen.queryByRole('button', { name: 'Mum' })).not.toBeInTheDocument();
  });

  it('Ons ($) sekmesine geçince getPreciousMetalHistory USD currency ile çağrılır; grafik currency=USD', async () => {
    renderPage();
    await screen.findByTestId('gold-chart');
    getPreciousMetalHistory.mockResolvedValue(usdHistory());
    fireEvent.click(screen.getByRole('button', { name: 'Ons Platin ($)' }));
    await waitFor(() =>
      expect(getPreciousMetalHistory).toHaveBeenCalledWith('platinum', '1M', 'USD')
    );
    await waitFor(() =>
      expect(screen.getByTestId('gold-chart')).toHaveAttribute('data-currency', 'USD')
    );
  });

  it('Ons (€) sekmesine geçince getPreciousMetalHistory EUR currency ile çağrılır; grafik currency=EUR', async () => {
    renderPage();
    await screen.findByTestId('gold-chart');
    getPreciousMetalHistory.mockResolvedValue({
      points: [
        { date: '2026-05-01', eurOns: 800.0 },
        { date: '2026-05-02', eurOns: 880.0 },
      ],
    });
    fireEvent.click(screen.getByRole('button', { name: 'Ons Platin (€)' }));
    await waitFor(() =>
      expect(getPreciousMetalHistory).toHaveBeenCalledWith('platinum', '1M', 'EUR')
    );
    await waitFor(() =>
      expect(screen.getByTestId('gold-chart')).toHaveAttribute('data-currency', 'EUR')
    );
  });

  it('aralık değişince (3M → "3A") getPreciousMetalHistory yeni range ile yeniden çağrılır', async () => {
    renderPage();
    await screen.findByTestId('gold-chart');
    getPreciousMetalHistory.mockClear();
    // Toolbar range butonu "3A" (RANGE_LABELS['3M']).
    fireEvent.click(screen.getByRole('button', { name: '3A' }));
    await waitFor(() =>
      expect(getPreciousMetalHistory).toHaveBeenCalledWith('platinum', '3M', 'TRY')
    );
  });

  it('yenile butonu loadHistory\'yi yeniden tetikler', async () => {
    renderPage();
    await screen.findByTestId('gold-chart');
    getPreciousMetalHistory.mockClear();
    fireEvent.click(screen.getByTitle('Yenile'));
    await waitFor(() => expect(getPreciousMetalHistory).toHaveBeenCalledTimes(1));
  });

  it('history null dönerse (hata) grafik 0 nokta alır ama sayfa çökmez', async () => {
    getPreciousMetalHistory.mockResolvedValue(null);
    renderPage();
    const chart = await screen.findByTestId('gold-chart');
    expect(chart).toHaveAttribute('data-points', '0');
    // Spot geldiği için sayfa gövdesi yine render → başlık durur.
    expect(screen.getByRole('heading', { name: 'Platin' })).toBeInTheDocument();
  });

  it('URL ?tab=usd_ons ile açılınca doğrudan Ons sekmesi aktif olur (USD history)', async () => {
    getPreciousMetalHistory.mockResolvedValue(usdHistory());
    renderPage({ route: '/market/platinum?tab=usd_ons' });
    await screen.findByTestId('gold-chart');
    await waitFor(() =>
      expect(getPreciousMetalHistory).toHaveBeenCalledWith('platinum', '1M', 'USD')
    );
    // USD sekmesinde grafik currency USD'dir.
    await waitFor(() =>
      expect(screen.getByTestId('gold-chart')).toHaveAttribute('data-currency', 'USD')
    );
  });

  it('aksiyon ve karşılaştır butonları doğru COMMODITY sembolüyle (PLATINUM:GRAM_TRY) render edilir', async () => {
    renderPage();
    await screen.findByTestId('gold-chart');
    // try_gram → PRECIOUS_CAT GRAM_TRY → "PLATINUM:GRAM_TRY".
    const actions = screen.getByTestId('instrument-actions');
    expect(actions).toHaveAttribute('data-asset', 'COMMODITY');
    expect(actions).toHaveAttribute('data-symbol', 'PLATINUM:GRAM_TRY');
    const universal = screen.getByTestId('universal-compare');
    expect(universal).toHaveAttribute('data-symbol', 'PLATINUM:GRAM_TRY');
    expect(screen.getByTestId('precious-compare')).toBeInTheDocument();
  });

  it('Ons ($) sekmesinde COMMODITY sembolü USD_ONS kategorisine döner (PLATINUM:USD_ONS)', async () => {
    renderPage();
    await screen.findByTestId('gold-chart');
    getPreciousMetalHistory.mockResolvedValue(usdHistory());
    fireEvent.click(screen.getByRole('button', { name: 'Ons Platin ($)' }));
    await waitFor(() =>
      expect(screen.getByTestId('instrument-actions')).toHaveAttribute('data-symbol', 'PLATINUM:USD_ONS')
    );
  });

  it('alt spot özet kartları (Gram/Kg/Ons$/Ons€) spot değerleriyle render edilir', async () => {
    renderPage();
    await screen.findByTestId('gold-chart');
    // Kartlardaki "Borsa İstanbul" etiketi birden çok kez geçer.
    expect(screen.getAllByText('Borsa İstanbul').length).toBeGreaterThan(0);
    // Gram değeri 1.100,50 (spot.tryGram) kart + StatRow + başlık olarak görünür.
    expect(screen.getAllByText(/₺1\.100,50/).length).toBeGreaterThan(0);
    // USD/Ons istatistiği spot.usdOns → $950,25.
    expect(screen.getAllByText(/\$950,25/).length).toBeGreaterThan(0);
  });

  it('stale (eski veri) bayrağı işaretliyse "Eski Veri" rozeti gösterilir', async () => {
    getPreciousMetalSpot.mockResolvedValue(spotPayload({ stale: true }));
    renderPage();
    await screen.findByTestId('gold-chart');
    expect(screen.getByText('Eski Veri')).toBeInTheDocument();
  });

  it('official bayrağı ile "Borsa İstanbul" rozeti başlıkta gösterilir', async () => {
    renderPage();
    await screen.findByTestId('gold-chart');
    // official:true → en az bir "Borsa İstanbul" rozeti/etiketi (başlık + kartlar).
    expect(screen.getAllByText('Borsa İstanbul').length).toBeGreaterThan(0);
    // lastValidDate satırı render edilir.
    expect(screen.getByText(/2026-06-04/)).toBeInTheDocument();
  });
});
