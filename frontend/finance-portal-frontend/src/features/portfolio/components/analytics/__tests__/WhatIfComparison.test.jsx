import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import WhatIfComparison from '../WhatIfComparison';
import { LanguageProvider } from '../../../../../context/LanguageContext';
import { getPortfolioWhatIfSeries } from '../../../../../api/portfolioApi';

// ── Mock'lar ──────────────────────────────────────────────────────────────────
// 1) portfolioApi: ağ yok → getPortfolioWhatIfSeries stub'lanır (her testte ayrı veri verilir).
vi.mock('../../../../../api/portfolioApi', () => ({
  getPortfolioWhatIfSeries: vi.fn(),
}));

// 2) recharts: jsdom'da SVG ölçümü/boyut hesabı yok (ResponsiveContainer 0 verir, gerçek
//    LineChart boyut beklentisiyle uyarır). Test ettiğimiz şey grafik İÇİ değil; efsane,
//    başlık, hover-detay paneli gibi DÜZ DOM. Bu yüzden recharts'ı saf passthrough stub'larla
//    değiştiriyoruz — canvas/SVG mantığı koşmaz, throw riski sıfır.
vi.mock('recharts', () => {
  const Passthrough = ({ children }) => <div>{children}</div>;
  const Noop = () => null;
  return {
    ResponsiveContainer: Passthrough,
    LineChart: Passthrough,
    Line: Noop,
    XAxis: Noop,
    YAxis: Noop,
    CartesianGrid: Noop,
    Tooltip: Noop,
    ReferenceLine: Noop,
  };
});

// 3) InstrumentSearchModal: gerçeği mount'ta http ile ağ çağrısı yapar (useInstrumentSearch).
//    Testte basit bir stub: "varlık seç" düğmesi onSelect'i {assetType,symbol,name} ile çağırır,
//    "modal kapat" düğmesi onClose tetikler. Böylece kıyas-ekle ve sim-seçimi akışları test edilir.
vi.mock('../../../../../components/instrument/InstrumentSearchModal', () => ({
  default: ({ onSelect, onClose }) => (
    <div data-testid="instrument-search-modal">
      <button
        type="button"
        onClick={() => onSelect({ assetType: 'STOCK', symbol: 'THYAO', name: 'Türk Hava Yolları' })}
      >
        mock-select-thyao
      </button>
      <button type="button" onClick={onClose}>
        mock-close-modal
      </button>
    </div>
  ),
}));

// Varsayılan dil "tr" → gerçek LanguageProvider ile t(key) anahtarın kendisini döndürür.
function renderWithLang(ui) {
  return render(<LanguageProvider>{ui}</LanguageProvider>);
}

// Backend what-if-series yanıt şekli:
//   { points: [{ date, cost, actual, inflation, gold, usd, deposit, ... }],
//     availableScenarios: [...serisKey], availableBenchmarks: [...customId] }
function seriesPayload(overrides = {}) {
  return {
    points: [
      { date: '2025-06-01', cost: 100000, actual: 100000, inflation: 100000, gold: 100000, usd: 100000, deposit: 100000 },
      { date: '2025-12-01', cost: 100000, actual: 112000, inflation: 108000, gold: 120000, usd: 105000, deposit: 104000 },
      { date: '2026-06-01', cost: 100000, actual: 125000, inflation: 118000, gold: 140000, usd: 110000, deposit: 109000 },
    ],
    availableScenarios: ['actual', 'inflation', 'gold', 'usd', 'deposit'],
    availableBenchmarks: [],
    ...overrides,
  };
}

const HOLDINGS = [
  { symbol: 'ASELS', assetType: 'STOCK', name: 'Aselsan' },
  { symbol: 'BTC', assetType: 'CRYPTO', name: 'Bitcoin' },
];

describe('WhatIfComparison (Vitest + testing-library, recharts ResponsiveContainer mock)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    getPortfolioWhatIfSeries.mockResolvedValue(seriesPayload());
    // recharts ResponsiveContainer (mock'lanmasa da güvenli olsun) ve diğer ölçüm gözlemcileri.
    globalThis.ResizeObserver = class { observe() {} unobserve() {} disconnect() {} };
  });

  it('ilk render anında portföy için what-if serisini "ALL" kapsamıyla çeker (assetType/symbol null)', () => {
    renderWithLang(<WhatIfComparison portfolioId={42} holdings={HOLDINGS} />);
    // Varsayılan scope "ALL" → assetOptions[0] (Tüm Portföy) → assetType=null, symbol=null, benchmark=[].
    expect(getPortfolioWhatIfSeries).toHaveBeenCalledWith(42, null, null, []);
  });

  it('başlık ve mod düğmelerini (Portföyüm / Özel) her zaman render eder', () => {
    renderWithLang(<WhatIfComparison portfolioId={42} holdings={HOLDINGS} />);
    expect(screen.getByText('Ne Olurdu?')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Portföyüm' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Özel' })).toBeInTheDocument();
  });

  it('PORTFOLIO modunda varlık seçici (select) holdings\'ten seçenekler üretir', () => {
    renderWithLang(<WhatIfComparison portfolioId={42} holdings={HOLDINGS} />);
    const select = screen.getByLabelText('Varlık seç');
    expect(select).toBeInTheDocument();
    // İlk seçenek "Tüm Portföy" + her holding bir satır.
    expect(screen.getByRole('option', { name: 'Tüm Portföy' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'Aselsan · Hisse' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'Bitcoin · Kripto' })).toBeInTheDocument();
  });

  it('veri gelince efsane çizgileri (Gerçek + senaryolar) ve son % getiriyi gösterir', async () => {
    renderWithLang(<WhatIfComparison portfolioId={42} holdings={HOLDINGS} />);
    // Veri gelince çizilen "Kıyas ekle" düğmesi tek+benzersiz → render bekleme çıpası.
    await screen.findByRole('button', { name: /Kıyas ekle/ });
    // availableScenarios içindeki seriler efsanede (button) görünür. Aynı etiket alt
    // detay panelinde de (div) yer aldığından button-rolüyle daraltıyoruz.
    expect(screen.getByRole('button', { name: /Gerçek/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Altın \(gram\)/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Mevduat/ })).toBeInTheDocument();
    // Son satırda actual 125000/100000 → +25.0% (efsanede + alt detay panelinde, 2 yerde).
    expect(screen.getAllByText('+25.0%').length).toBeGreaterThan(0);
    // gold 140000/100000 → +40.0%.
    expect(screen.getAllByText('+40.0%').length).toBeGreaterThan(0);
  });

  it('scope değişince (bir holding seçilince) API o varlığın assetType/symbol\'ü ile yeniden çağrılır', async () => {
    renderWithLang(<WhatIfComparison portfolioId={7} holdings={HOLDINGS} />);
    await waitFor(() => expect(getPortfolioWhatIfSeries).toHaveBeenCalledWith(7, null, null, []));

    fireEvent.change(screen.getByLabelText('Varlık seç'), { target: { value: 'STOCK|ASELS' } });
    await waitFor(() =>
      expect(getPortfolioWhatIfSeries).toHaveBeenCalledWith(7, 'STOCK', 'ASELS', []),
    );
  });

  it('boş "points" gelince "yeterli veri yok" mesajını gösterir', async () => {
    getPortfolioWhatIfSeries.mockResolvedValue(seriesPayload({ points: [], availableScenarios: [] }));
    renderWithLang(<WhatIfComparison portfolioId={42} holdings={HOLDINGS} />);
    expect(
      await screen.findByText('Senaryo için yeterli veri yok (pozisyon çok yeni ya da fiyat geçmişi bulunamadı).'),
    ).toBeInTheDocument();
  });

  it('API hata verince "Senaryo hesaplanamadı." hata mesajını gösterir', async () => {
    getPortfolioWhatIfSeries.mockRejectedValue(new Error('boom'));
    renderWithLang(<WhatIfComparison portfolioId={42} holdings={HOLDINGS} />);
    expect(await screen.findByText('Senaryo hesaplanamadı.')).toBeInTheDocument();
  });

  it('portfolioId yoksa API çağrılmaz ve yüklenme (bounce) durumunda kalır', () => {
    const { container } = renderWithLang(<WhatIfComparison portfolioId={null} holdings={HOLDINGS} />);
    expect(getPortfolioWhatIfSeries).not.toHaveBeenCalled();
    // loading=true → bounce noktaları (animate-bounce) DOM'da, hata/veri yok.
    expect(container.querySelector('.animate-bounce')).toBeTruthy();
  });

  it('"Özel" moduna geçince API çağrılmaz ve özel-mod yönlendirme metni gösterilir', async () => {
    renderWithLang(<WhatIfComparison portfolioId={42} holdings={HOLDINGS} />);
    await waitFor(() => expect(getPortfolioWhatIfSeries).toHaveBeenCalledTimes(1));

    fireEvent.click(screen.getByRole('button', { name: 'Özel' }));
    // SIM moduna geçti ama simReady=false → veri sıfırlanır, yönlendirme metni çıkar, yeni API çağrısı yok.
    expect(
      await screen.findByText('Özel mod: bir varlık seç, tutar ve tarih gir — o tarihte o varlığı alsaydın ne olurdu görelim.'),
    ).toBeInTheDocument();
    expect(getPortfolioWhatIfSeries).toHaveBeenCalledTimes(1);
  });

  it('Özel modda varlık + tutar + tarih hazır olunca SIM parametreleriyle API çağrılır', async () => {
    renderWithLang(<WhatIfComparison portfolioId={9} holdings={HOLDINGS} />);
    await waitFor(() => expect(getPortfolioWhatIfSeries).toHaveBeenCalled());
    getPortfolioWhatIfSeries.mockClear();

    // Özel moda geç.
    fireEvent.click(screen.getByRole('button', { name: 'Özel' }));
    // "Varlık seç" düğmesi → modal aç (searchFor='sim').
    fireEvent.click(screen.getByRole('button', { name: /\+ Varlık seç/ }));
    // Stub modal ile bir enstrüman seç → simInstrument set olur, modal kapanır.
    fireEvent.click(await screen.findByText('mock-select-thyao'));

    // Varsayılan simAmount '10000', simDate (bir yıl önce) zaten dolu → simReady true.
    await waitFor(() => {
      expect(getPortfolioWhatIfSeries).toHaveBeenCalledWith(
        9, null, null, [],
        expect.objectContaining({ assetType: 'STOCK', symbol: 'THYAO', amount: 10000 }),
      );
    });
  });

  it('"Kıyas ekle" ile özel bir benchmark eklenir ve efsanede chip görünür (veri yoksa "veri yok")', async () => {
    renderWithLang(<WhatIfComparison portfolioId={42} holdings={HOLDINGS} />);
    // Efsane (ve "Kıyas ekle") çıkana kadar bekle.
    fireEvent.click(await screen.findByRole('button', { name: /Kıyas ekle/ }));
    fireEvent.click(await screen.findByText('mock-select-thyao'));

    // custom eklendi → THYAO etiketli chip; availableBenchmarks boş olduğundan "(veri yok)" rozeti.
    expect(await screen.findByText('THYAO')).toBeInTheDocument();
    expect(screen.getByText('(veri yok)')).toBeInTheDocument();
    // Eklenen benchmark'ın id'si sonraki API isteğine geçer (benchmark listesi ['STOCK|THYAO']).
    await waitFor(() =>
      expect(getPortfolioWhatIfSeries).toHaveBeenCalledWith(42, null, null, ['STOCK|THYAO']),
    );
  });

  it('eklenen kıyas chip\'inin (X) düğmesiyle kaldırılır', async () => {
    renderWithLang(<WhatIfComparison portfolioId={42} holdings={HOLDINGS} />);
    fireEvent.click(await screen.findByRole('button', { name: /Kıyas ekle/ }));
    fireEvent.click(await screen.findByText('mock-select-thyao'));
    const chip = await screen.findByText('THYAO');

    // Chip kapsayıcısındaki "Kaldır" başlıklı düğmeye bas.
    const removeBtn = screen.getByTitle('Kaldır');
    fireEvent.click(removeBtn);
    await waitFor(() => expect(screen.queryByText('THYAO')).not.toBeInTheDocument());
    expect(chip).not.toBeInTheDocument();
  });

  it('hover detay paneli son satırı (Bugün) tarih + getirilerle gösterir', async () => {
    renderWithLang(<WhatIfComparison portfolioId={42} holdings={HOLDINGS} />);
    // activeRow yok → lastRow gösterilir, etiket "Bugün".
    expect(await screen.findByText('Bugün')).toBeInTheDocument();
    // Yatırılan maliyet (cost=100000) panelde görünür (valuesHidden=false).
    expect(screen.getByText(/Yatırılan:/)).toBeInTheDocument();
  });

  it('valuesHidden=true iken para tutarları gizlenir (Yatırılan satırı çıkmaz)', async () => {
    renderWithLang(<WhatIfComparison portfolioId={42} holdings={HOLDINGS} valuesHidden />);
    await screen.findByText('Bugün');
    // Yüzdeler hâlâ var ama TL "Yatırılan:" satırı render edilmez.
    expect(screen.queryByText(/Yatırılan:/)).not.toBeInTheDocument();
    expect(screen.getAllByText('+25.0%').length).toBeGreaterThan(0);
  });

  it('senaryo serisini efsaneden tıklayınca gizlenir (üstü çizili + opaklık düşer)', async () => {
    renderWithLang(<WhatIfComparison portfolioId={42} holdings={HOLDINGS} />);
    // Efsane düğmesi (button-rolü; aynı etiket hover panelinde div olarak da var).
    const goldBtn = await screen.findByRole('button', { name: /Altın \(gram\)/ });
    // Başlangıçta gizli değil.
    expect(goldBtn.className).not.toContain('opacity-35');

    fireEvent.click(goldBtn);
    // toggle sonrası: button opacity-35, label line-through.
    await waitFor(() => expect(goldBtn.className).toContain('opacity-35'));
    const goldLabelAfter = within(goldBtn).getByText('Altın (gram)');
    expect(goldLabelAfter.className).toContain('line-through');
  });
});
