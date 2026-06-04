import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import PortfolioPerformanceChart from '../PortfolioPerformanceChart';
import { LanguageProvider } from '../../../../context/LanguageContext';
import { getPortfolioPerformance } from '../../../../api/portfolioApi';

// ── Mock'lar ────────────────────────────────────────────────────────────────
// 1) klinecharts: jsdom'da canvas yok → init() bir sahte grafik nesnesi döndürmeli.
//    Komponentin inner KLine bileşeni effect içinde bu nesnenin metotlarını çağırır
//    (setStyles, applyNewData, subscribeAction, createIndicator, …). Tümü no-op.
const klineChartInstance = {
  setPriceVolumePrecision: vi.fn(),
  setStyles: vi.fn(),
  applyNewData: vi.fn(),
  createIndicator: vi.fn(),
  removeIndicator: vi.fn(),
  subscribeAction: vi.fn(),
  scrollToTimestamp: vi.fn(),
  resize: vi.fn(),
  convertFromPixel: vi.fn(),
  createOverlay: vi.fn(),
  removeOverlay: vi.fn(),
};
vi.mock('klinecharts', () => ({
  init: vi.fn(() => klineChartInstance),
  dispose: vi.fn(),
  registerIndicator: vi.fn(),
}));

// 2) portfolioApi: ağ yok → getPortfolioPerformance stub'lanır (her testte ayrı veri verilir).
vi.mock('../../../../api/portfolioApi', () => ({
  getPortfolioPerformance: vi.fn(),
}));

// Varsayılan dil "tr" olduğundan gerçek LanguageProvider ile t(key) anahtarın kendisini döndürür.
function renderWithLang(ui) {
  return render(<LanguageProvider>{ui}</LanguageProvider>);
}

// Backend performans yanıtı şekli: { points: [{ date, marketValue, periodGrowthPercent, inflationValue }], excludedAssets: [...] }
function perfPayload(overrides = {}) {
  return {
    points: [
      { date: '2026-05-01', marketValue: 100000, periodGrowthPercent: 0, inflationValue: 100000 },
      { date: '2026-05-15', marketValue: 110000, periodGrowthPercent: 10, inflationValue: 102000 },
      { date: '2026-06-01', marketValue: 121000, periodGrowthPercent: 21, inflationValue: 104000 },
    ],
    excludedAssets: [],
    ...overrides,
  };
}

describe('PortfolioPerformanceChart (Vitest + testing-library, klinecharts mock)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    getPortfolioPerformance.mockResolvedValue(perfPayload());
  });

  it('ilk render anında "Yükleniyor…" gösterir ve veri çekmeyi tetikler', () => {
    renderWithLang(<PortfolioPerformanceChart portfolioId={42} />);
    // loading başlangıç state'i true
    expect(screen.getByText('Yükleniyor…')).toBeInTheDocument();
    // mount effect'i veriyi çeker (1M / VALUE varsayılanlarıyla)
    expect(getPortfolioPerformance).toHaveBeenCalledWith(42, '1M', 'VALUE');
  });

  it('aralık ve metrik kontrollerini (butonlar + select) render eder', () => {
    renderWithLang(<PortfolioPerformanceChart portfolioId={42} />);
    // RANGES butonları
    ['5D', '1M', '6M', 'YTD', '1Y'].forEach(label => {
      expect(screen.getByRole('button', { name: label })).toBeInTheDocument();
    });
    // Metrik seçici (aria-label çevrilmemiş tr anahtarı)
    expect(screen.getByLabelText('Grafik metriği')).toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'Portföy Değeri' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'Dönem Getirisi' })).toBeInTheDocument();
  });

  it('veri gelince yüklenme kaybolur ve KLine grafiği başlatılır (init çağrılır)', async () => {
    const klinecharts = await import('klinecharts');
    renderWithLang(<PortfolioPerformanceChart portfolioId={42} />);

    await waitFor(() => {
      expect(screen.queryByText('Yükleniyor…')).not.toBeInTheDocument();
    });
    // Grafik bileşeni mount oldu → klinecharts.init çağrıldı, veri uygulandı.
    expect(klinecharts.init).toHaveBeenCalled();
    expect(klineChartInstance.applyNewData).toHaveBeenCalled();
  });

  it('aralık butonuna tıklanınca yeni aralık ile API tekrar çağrılır', async () => {
    renderWithLang(<PortfolioPerformanceChart portfolioId={7} />);
    await waitFor(() => expect(getPortfolioPerformance).toHaveBeenCalledWith(7, '1M', 'VALUE'));

    fireEvent.click(screen.getByRole('button', { name: '6M' }));
    await waitFor(() => expect(getPortfolioPerformance).toHaveBeenCalledWith(7, '6M', 'VALUE'));
  });

  it('metrik "Dönem Getirisi" seçilince GROWTH ile API tekrar çağrılır', async () => {
    renderWithLang(<PortfolioPerformanceChart portfolioId={7} />);
    await waitFor(() => expect(getPortfolioPerformance).toHaveBeenCalledWith(7, '1M', 'VALUE'));

    fireEvent.change(screen.getByLabelText('Grafik metriği'), { target: { value: 'GROWTH' } });
    await waitFor(() => expect(getPortfolioPerformance).toHaveBeenCalledWith(7, '1M', 'GROWTH'));
  });

  it('boş "points" gelince "Bu aralık için grafik verisi yok." mesajını gösterir', async () => {
    getPortfolioPerformance.mockResolvedValue(perfPayload({ points: [] }));
    renderWithLang(<PortfolioPerformanceChart portfolioId={42} />);

    expect(await screen.findByText('Bu aralık için grafik verisi yok.')).toBeInTheDocument();
    // Veri olmadığı için grafik başlatılmaz.
    const klinecharts = await import('klinecharts');
    expect(klinecharts.init).not.toHaveBeenCalled();
  });

  it('API hata verince hata mesajı gösterir', async () => {
    getPortfolioPerformance.mockRejectedValue({ response: { status: 500 } });
    renderWithLang(<PortfolioPerformanceChart portfolioId={42} />);

    // status !== 400 → genel "Performans verisi yüklenemedi" anahtarı.
    expect(await screen.findByText('Performans verisi yüklenemedi')).toBeInTheDocument();
  });

  it('400 hata verince "Geçersiz istek" mesajı gösterir', async () => {
    getPortfolioPerformance.mockRejectedValue({ response: { status: 400 } });
    renderWithLang(<PortfolioPerformanceChart portfolioId={42} />);

    expect(await screen.findByText('Geçersiz istek')).toBeInTheDocument();
  });

  it('backend "message" alanı varsa onu hata olarak gösterir', async () => {
    getPortfolioPerformance.mockRejectedValue({
      response: { status: 422, data: { message: 'Özel hata metni' } },
    });
    renderWithLang(<PortfolioPerformanceChart portfolioId={42} />);

    expect(await screen.findByText('Özel hata metni')).toBeInTheDocument();
  });

  it('dışlanan varlık varsa uyarı bandını adıyla birlikte gösterir', async () => {
    getPortfolioPerformance.mockResolvedValue(
      perfPayload({ excludedAssets: [{ symbol: 'THYAO' }] }),
    );
    renderWithLang(<PortfolioPerformanceChart portfolioId={42} />);

    // "{count} varlık grafik hesaplamasına dahil edilemedi." → tr'de count yerine konur.
    expect(
      await screen.findByText('1 varlık grafik hesaplamasına dahil edilemedi.'),
    ).toBeInTheDocument();
    // Tekil dahil-edilmeyen etiketi + sembol.
    expect(screen.getByText(/Dahil edilmeyen:/)).toBeInTheDocument();
    expect(screen.getByText(/THYAO/)).toBeInTheDocument();
  });

  it('portfolioId yoksa API çağrılmaz ve yüklenme durumunda kalır', () => {
    renderWithLang(<PortfolioPerformanceChart portfolioId={null} />);
    expect(getPortfolioPerformance).not.toHaveBeenCalled();
    expect(screen.getByText('Yükleniyor…')).toBeInTheDocument();
  });

  it('refreshKey değişince veri yeniden çekilir', async () => {
    const { rerender } = renderWithLang(
      <PortfolioPerformanceChart portfolioId={9} refreshKey={0} />,
    );
    await waitFor(() => expect(getPortfolioPerformance).toHaveBeenCalledTimes(1));

    rerender(
      <LanguageProvider>
        <PortfolioPerformanceChart portfolioId={9} refreshKey={1} />
      </LanguageProvider>,
    );
    await waitFor(() => expect(getPortfolioPerformance).toHaveBeenCalledTimes(2));
  });
});
