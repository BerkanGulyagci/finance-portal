import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { LanguageProvider } from '../../../../context/LanguageContext';

// ─────────────────────────────────────────────────────────────────────────────
// MOCK STRATEJİSİ
// TefasFundDetailPage ağır çocuk komponentler (TefasPriceChart → klinecharts canvas
// + çizim hook'u, MonthlyReturnChart, AssetAllocationChart), AuthContext'e bağlı
// InstrumentActionButtons ve marketApi (ağ) içerir. jsdom'da canvas/ağ olmadığından
// bunların hepsi stub'lanır; LanguageProvider GERÇEK (t anahtarı aynen döndürür) ve
// react-router-dom (useParams=code, MemoryRouter/Link gerçek) kullanılır.
// ─────────────────────────────────────────────────────────────────────────────

// klinecharts — TefasPriceChart dolaylı import eder; canvas jsdom'da çalışmaz.
// (TefasPriceChart zaten aşağıda stub'landı ama dolaylı import'a karşı güvenlik ağı.)
vi.mock('klinecharts', () => ({
  init: vi.fn(() => ({
    applyNewData: vi.fn(), createIndicator: vi.fn(), removeIndicator: vi.fn(),
    resize: vi.fn(), setStyles: vi.fn(), subscribeAction: vi.fn(),
    convertFromPixel: vi.fn(), createOverlay: vi.fn(), removeOverlay: vi.fn(),
  })),
  dispose: vi.fn(), registerOverlay: vi.fn(), registerIndicator: vi.fn(),
}));

// Grafik çocuk komponentleri — render edildiklerini + aldıkları props'u görmek için sade stub.
vi.mock('../components/TefasPriceChart', () => ({
  default: ({ code, fonTipi, priceHistory }) => (
    <div data-testid="price-chart" data-code={code} data-fontipi={fonTipi}
      data-points={priceHistory?.length ?? 0} />
  ),
}));
vi.mock('../components/MonthlyReturnChart', () => ({
  default: ({ monthlyReturns }) => (
    <div data-testid="monthly-chart" data-points={monthlyReturns?.length ?? 0} />
  ),
}));
vi.mock('../components/AssetAllocationChart', () => ({
  default: ({ assetAllocation }) => (
    <div data-testid="allocation-chart" data-points={assetAllocation?.length ?? 0} />
  ),
}));

// AuthContext'e + portfolioApi'ye bağlı buton kümeleri — sade stub.
vi.mock('../../../../components/instrument/InstrumentActionButtons', () => ({
  default: ({ symbol }) => <div data-testid="action-buttons">{symbol}</div>,
}));
vi.mock('../../../../components/common/UniversalCompareButton', () => ({
  default: ({ symbol }) => <button data-testid="universal-compare">{symbol}</button>,
}));

// marketApi — tek ağ çağrısı (getRasyonetFundDetail) stub. Her test dönüş değerini ayarlar.
vi.mock('../../../../api/marketApi', () => ({
  getRasyonetFundDetail: vi.fn(),
}));

// react-router-dom — useParams (code) override; MemoryRouter/Link/useLocation gerçek.
vi.mock('react-router-dom', async (orig) => {
  const actual = await orig();
  return { ...actual, useParams: () => ({ code: 'AFA' }) };
});

import { getRasyonetFundDetail } from '../../../../api/marketApi';
import TefasFundDetailPage from '../TefasFundDetailPage';

// ── Test verisi ──────────────────────────────────────────────────────────────
// TefasFundDetailPage'in (effect → getRasyonetFundDetail) beklediği detail şekli.
const FUND_DETAIL = {
  code: 'AFA',
  name: 'Ak Portföy Hisse Senedi Fonu',
  fundType: 'Hisse Senedi Şemsiye Fonu',
  managerName: 'Ak Portföy',
  founderName: 'Ak Portföy Yönetimi A.Ş.',
  price: 12.3456,
  riskLevel: 5,
  kapLink: 'https://kap.org.tr/fon/AFA',
  returnOneDay: 0.5, returnOneWeek: 1.2, returnOneMonth: 3.4,
  returnThreeMonths: 8.1, returnSixMonths: 15.0, returnYearToDate: 22.5,
  returnOneYear: 40.2, returnThreeYears: 120.0, returnFiveYears: 300.0,
  riskBest: 12.5, riskWorst: -8.3, riskPositiveRateOfReturn: '65%',
  marketCap: 1_500_000_000, marketCapUsd: 50_000_000,
  buySettlement: 1, sellSettlement: 2, minimumQuantitySales: '1',
  managementFeeAnnual: '1.91', commission: '0.50',
  strategy: 'Fon, ağırlıklı olarak BIST hisse senetlerine yatırım yapar.',
  monthlyReturns: [
    { year: 2025, month: 1, returnValue: 2.1 },
    { year: 2025, month: 2, returnValue: -1.3 },
  ],
  priceHistory: Array.from({ length: 30 }, (_, i) => ({ date: `2025-01-${i + 1}`, price: 12 + i * 0.01 })),
  assetAllocation: [
    { name: 'Hisse Senedi', percentage: 80 },
    { name: 'Para Piyasası', percentage: 20 },
  ],
};

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/market/tefas/AFA']}>
      <LanguageProvider>
        <TefasFundDetailPage />
      </LanguageProvider>
    </MemoryRouter>,
  );
}

describe('TefasFundDetailPage (Vitest + @testing-library/react)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    // Varsayılan happy-path: detay tam döner.
    getRasyonetFundDetail.mockResolvedValue(FUND_DETAIL);
  });

  it("mock'larla hata fırlatmadan render olur; geri linki ve fon kodunu gösterir (smoke)", async () => {
    renderPage();
    // Geri linki ("TEFAS Fonları") loading'ten bağımsız her zaman render edilir.
    expect(screen.getByText('TEFAS Fonları')).toBeInTheDocument();
    // Fon kodu rozeti (useParams=code) anında DOM'da.
    expect(screen.getAllByText('AFA')[0]).toBeInTheDocument();
    // Async dolum sonrası fon adı başlığı gelir.
    expect(await screen.findByRole('heading', { name: FUND_DETAIL.name })).toBeInTheDocument();
  });

  it('açılışta getRasyonetFundDetail(code, "TMF") çağrılır (listItem yok → varsayılan kaynak kodu)', async () => {
    renderPage();
    await screen.findByRole('heading', { name: FUND_DETAIL.name });
    // location.state yok → sourceCode 'TMF' (TEFAS) varsayılanına düşer. 3. arg = aktif dil
    // (strateji çevirisi için backend'e geçilir; testte varsayılan 'tr').
    expect(getRasyonetFundDetail).toHaveBeenCalledWith('AFA', 'TMF', 'tr');
  });

  it('yükleme sırasında skeleton gösterir, veri gelince gizler (loading dalı)', async () => {
    // Çözülmeyen promise → kalıcı loading.
    let resolveFn;
    getRasyonetFundDetail.mockReturnValue(new Promise((res) => { resolveFn = res; }));
    const { container } = renderPage();
    // Loading sırasında skeleton placeholder (animate-pulse) içerir.
    expect(container.querySelector('.animate-pulse')).toBeTruthy();
    // Sekme butonları henüz yok (içerik !loading && d'ye bağlı).
    expect(screen.queryByRole('button', { name: 'Performans' })).not.toBeInTheDocument();
    // Veriyi çöz → loading biter, sekmeler gelir.
    resolveFn(FUND_DETAIL);
    expect(await screen.findByRole('button', { name: 'Performans' })).toBeInTheDocument();
    await waitFor(() => expect(container.querySelector('.animate-pulse')).toBeFalsy());
  });

  it('varsayılan Performans sekmesi: getiri kartları + risk/performans bölümü', async () => {
    renderPage();
    // Sekmeler async içerikle gelir; varsayılan aktif sekme 'performance'.
    expect(await screen.findByRole('button', { name: 'Performans' })).toBeInTheDocument();
    // Getiri kartı etiketleri (t anahtarı aynen) görünür.
    expect(screen.getByText('Günlük')).toBeInTheDocument();
    expect(screen.getByText('YBG')).toBeInTheDocument();
    expect(screen.getByText('5 Yıl')).toBeInTheDocument();
    // Aylık getiri grafiği (monthlyReturns dolu) stub'ı render olur.
    expect(screen.getByTestId('monthly-chart')).toBeInTheDocument();
    // Risk ve Performans bölümü (riskBest/riskWorst dolu).
    expect(screen.getByText('Risk ve Performans')).toBeInTheDocument();
    expect(screen.getByText('En İyi Ay Getirisi')).toBeInTheDocument();
  });

  it('Grafik sekmesine geçilince fiyat grafiği + varlık dağılımı render olur', async () => {
    renderPage();
    const chartTab = await screen.findByRole('button', { name: 'Grafik' });
    fireEvent.click(chartTab);
    // TefasPriceChart stub'ı code + priceHistory ile gelir.
    const priceChart = await screen.findByTestId('price-chart');
    expect(priceChart).toBeInTheDocument();
    expect(priceChart).toHaveAttribute('data-code', 'AFA');
    // sourceCode 'TMF' → fonTipi 'YAT'.
    expect(priceChart).toHaveAttribute('data-fontipi', 'YAT');
    expect(priceChart.getAttribute('data-points')).toBe(String(FUND_DETAIL.priceHistory.length));
    // Varlık dağılımı (assetAllocation dolu) stub'ı.
    expect(screen.getByTestId('allocation-chart')).toBeInTheDocument();
    // "Fiyat Grafiği" başlığı.
    expect(screen.getByText('Fiyat Grafiği')).toBeInTheDocument();
  });

  it('Fon Bilgileri sekmesi temel bilgi satırlarını + stratejiyi gösterir', async () => {
    renderPage();
    const infoTab = await screen.findByRole('button', { name: 'Fon Bilgileri' });
    fireEvent.click(infoTab);
    expect(await screen.findByText('Fon Temel Bilgileri')).toBeInTheDocument();
    // Bilgi tablosu satır etiketleri.
    expect(screen.getByText('Fon Kodu')).toBeInTheDocument();
    expect(screen.getByText('Fon Türü')).toBeInTheDocument();
    expect(screen.getByText('Kurucu')).toBeInTheDocument();
    // Strateji metni (d.strategy dolu).
    expect(screen.getByText('Yatırım Stratejisi')).toBeInTheDocument();
    expect(screen.getByText(FUND_DETAIL.strategy)).toBeInTheDocument();
  });

  it('detail null dönerse "bulunamadı" hatasını gösterir, sekme içeriğini göstermez (error dalı)', async () => {
    getRasyonetFundDetail.mockResolvedValue(null);
    renderPage();
    expect(await screen.findByText('Fon bilgisi bulunamadı.')).toBeInTheDocument();
    // Hata varken sekmeler render edilmez.
    expect(screen.queryByRole('button', { name: 'Performans' })).not.toBeInTheDocument();
    // Geri linki yine de durur.
    expect(screen.getByText('TEFAS Fonları')).toBeInTheDocument();
  });

  it('API reddederse "yüklenemedi" hatasını gösterir (catch dalı)', async () => {
    getRasyonetFundDetail.mockRejectedValue(new Error('network'));
    renderPage();
    expect(await screen.findByText('Veriler yüklenemedi.')).toBeInTheDocument();
    expect(screen.queryByTestId('monthly-chart')).not.toBeInTheDocument();
  });

  it('aylık getiri / varlık dağılımı boşsa ilgili grafik kartları gizlenir (boş dal)', async () => {
    getRasyonetFundDetail.mockResolvedValue({
      ...FUND_DETAIL,
      monthlyReturns: [],
      assetAllocation: [],
      strategy: '',
      riskBest: null, riskWorst: null, riskPositiveRateOfReturn: null,
    });
    renderPage();
    // Performans sekmesi yüklenir ama aylık grafik + risk bölümü yok.
    expect(await screen.findByRole('button', { name: 'Performans' })).toBeInTheDocument();
    expect(screen.queryByTestId('monthly-chart')).not.toBeInTheDocument();
    expect(screen.queryByText('Risk ve Performans')).not.toBeInTheDocument();
    // Grafik sekmesinde fiyat grafiği var ama varlık dağılımı yok.
    fireEvent.click(screen.getByRole('button', { name: 'Grafik' }));
    expect(await screen.findByTestId('price-chart')).toBeInTheDocument();
    expect(screen.queryByTestId('allocation-chart')).not.toBeInTheDocument();
  });
});
