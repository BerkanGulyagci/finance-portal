import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { LanguageProvider } from '../../../../context/LanguageContext';

// ─────────────────────────────────────────────────────────────────────────────
// MOCK'LAR
// FxPage 4 marketApi çağrısı yapar (getFxTcmb / getFxOpen / getBankCurrencyRates /
// getBankCurrencyRatesByCurrency). jsdom'da ağ yoktur → hepsi stub'lanır.
// DİKKAT: FxPage api'yi `.js` uzantısıyla import eder ('../../../api/marketApi.js'),
// ComparePage uzantısız eder. Her iki specifier da AYNI fabrikaya bağlanır ki
// hangi yoldan resolve edilirse edilsin mock devreye girsin.
//
// WatchlistStar AuthContext + WatchlistContext + InstrumentTargetModal çeker;
// FxPage'in kendi mantığını test etmek için onu tanınabilir bir işaretle stub'larız.
// SortableTh / FlagImg saf bileşenler → gerçeği kullanılır (LanguageProvider yeter).
// ─────────────────────────────────────────────────────────────────────────────

// vi.hoisted: mock fonksiyonları vi.mock hoisting'inden ÖNCE tanımlanır
// (aksi halde "Cannot access 'marketApiFactory' before initialization").
const { getFxTcmb, getFxOpen, getBankCurrencyRates, getBankCurrencyRatesByCurrency } = vi.hoisted(() => ({
  getFxTcmb: vi.fn(),
  getFxOpen: vi.fn(),
  getBankCurrencyRates: vi.fn(),
  getBankCurrencyRatesByCurrency: vi.fn(),
}));
// Inline factory (değişken referansı YOK → hoisting güvenli). İçindeki mock fn'ler hoisted.
vi.mock('../../../../api/marketApi.js', () => ({
  getFxTcmb: (...a) => getFxTcmb(...a),
  getFxOpen: (...a) => getFxOpen(...a),
  getBankCurrencyRates: (...a) => getBankCurrencyRates(...a),
  getBankCurrencyRatesByCurrency: (...a) => getBankCurrencyRatesByCurrency(...a),
}));
vi.mock('../../../../api/marketApi', () => ({
  getFxTcmb: (...a) => getFxTcmb(...a),
  getFxOpen: (...a) => getFxOpen(...a),
  getBankCurrencyRates: (...a) => getBankCurrencyRates(...a),
  getBankCurrencyRatesByCurrency: (...a) => getBankCurrencyRatesByCurrency(...a),
}));

vi.mock('../../../../components/instrument/WatchlistStar', () => ({
  default: ({ symbol, assetType }) => (
    <button data-testid="watchlist-star" data-symbol={symbol} data-type={assetType}>
      ★
    </button>
  ),
}));

import FxPage from '../FxPage';

// ── Sahte veri üreticileri (komponentin okuduğu alanlara birebir uygun) ───────
function tcmbPayload() {
  return {
    asOf: '2026-06-04',
    rates: [
      { symbol: 'USD', unit: 1, buy: 32.1, sell: 32.2, effectiveBuy: 32.05, effectiveSell: 32.25 },
      { symbol: 'EUR', unit: 1, buy: 34.5, sell: 34.6, effectiveBuy: 34.45, effectiveSell: 34.65 },
      { symbol: 'GBP', unit: 1, buy: 40.1, sell: 40.2, effectiveBuy: 40.0, effectiveSell: 40.3 },
      { symbol: 'JPY', unit: 100, buy: 0.21, sell: 0.22, effectiveBuy: null, effectiveSell: null },
      { symbol: 'CHF', unit: 1, buy: 36.0, sell: 36.1, effectiveBuy: 35.9, effectiveSell: 36.2 },
      { symbol: 'SAR', unit: 1, buy: 8.5, sell: 8.6, effectiveBuy: null, effectiveSell: null },
    ],
  };
}

function openPayload(base = 'USD') {
  return {
    base,
    asOf: '2026-06-04T10:00:00Z',
    rates: [
      { symbol: 'EUR', buy: 0.92, sell: 0.925 },
      { symbol: 'GBP', buy: 0.78, sell: 0.79 },
    ],
  };
}

function bankRows() {
  return [
    {
      bankName: 'A Bankası', currencyCode: 'USD', currencyName: 'ABD Doları',
      buyRate: 32.0, sellRate: 32.3, spread: 0.3, dailyChangePercent: 1.25,
      sourceUpdatedAt: '2026-06-04T09:30:00Z',
    },
    {
      bankName: 'B Bankası', currencyCode: 'JPY', currencyName: 'Japon Yeni',
      buyRate: 0.21, sellRate: 0.23, spread: 0.02, dailyChangePercent: -0.5,
      sourceUpdatedAt: '2026-06-04T09:31:00Z',
    },
  ];
}

// LanguageProvider varsayılan dili "tr" → t(key) anahtarın kendisini döndürür.
// MemoryRouter useSearchParams/useNavigate/Link için gerekli; ?tab=... verilebilir.
function renderPage({ route = '/market/fx' } = {}) {
  return render(
    <MemoryRouter initialEntries={[route]}>
      <LanguageProvider>
        <FxPage />
      </LanguageProvider>
    </MemoryRouter>
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  localStorage.clear();
  // Varsayılan başarılı yanıtlar — gerektiğinde testler override eder.
  getFxTcmb.mockResolvedValue(tcmbPayload());
  getFxOpen.mockImplementation((base) => Promise.resolve(openPayload(base)));
  getBankCurrencyRates.mockResolvedValue(bankRows());
  getBankCurrencyRatesByCurrency.mockImplementation((c) =>
    Promise.resolve(bankRows().filter((r) => r.currencyCode === c))
  );
});

describe('FxPage (Döviz Kurları) — jsdom + testing-library', () => {
  it('smoke: throw etmeden render olur, başlık ve TCMB açıklaması görünür', async () => {
    renderPage();
    expect(screen.getByRole('heading', { name: 'Döviz Kurları' })).toBeInTheDocument();
    expect(
      screen.getByText('TCMB resmi döviz kurları (günlük güncellenir) — Türk Lirası bazlı')
    ).toBeInTheDocument();
    // Açılışta TCMB yüklenir.
    await waitFor(() => expect(getFxTcmb).toHaveBeenCalledTimes(1));
  });

  it('açılışta sekmeler (TCMB / Open / Banka) render edilir', () => {
    renderPage();
    expect(screen.getByRole('button', { name: /TCMB Resmi Kurlar/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Open Exchange Rates/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Banka Kurları/ })).toBeInTheDocument();
  });

  it('TCMB verisi gelince popüler döviz kartları (USD/EUR...) ve tablo satırları görünür', async () => {
    renderPage();
    // Popüler kart başlığı + en az bir döviz kodu (USD popüler listede).
    expect(await screen.findByText('Popüler Dövizler')).toBeInTheDocument();
    // Tablo satırlarında USD/TRY link metni (Link bileşeni) bulunur.
    const usdLinks = await screen.findAllByText('USD/TRY');
    expect(usdLinks.length).toBeGreaterThan(0);
    // Para birimi Türkçe adı meta'dan gelir.
    expect(screen.getAllByText('ABD Doları')[0]).toBeInTheDocument();
    // Meta satırı asOf tarihini gösterir (kaynak satırı parçalı metin olduğundan
    // tarih daha sağlam bir kanıt — "TCMB" hem sekme hem meta'da geçtiği için belirsiz).
    expect(screen.getByText('2026-06-04')).toBeInTheDocument();
  });

  it('TCMB yüklenirken yükleme noktaları, ardından tablo gösterilir', async () => {
    let resolve;
    getFxTcmb.mockReturnValue(new Promise((r) => { resolve = r; }));
    const { container } = renderPage();
    // Çözülmeden önce animate-bounce noktaları (Dots) DOM'da.
    expect(container.querySelector('.animate-bounce')).toBeInTheDocument();
    resolve(tcmbPayload());
    // Çözülünce tablo satırı belirir.
    expect((await screen.findAllByText('ABD Doları'))[0]).toBeInTheDocument();
    expect(container.querySelector('.animate-bounce')).not.toBeInTheDocument();
  });

  it('ağ hatası (err.response yok) → "Sunucuya ulaşılamıyor." gösterir', async () => {
    getFxTcmb.mockRejectedValue(new Error('network down'));
    renderPage();
    expect(await screen.findByText('Sunucuya ulaşılamıyor.')).toBeInTheDocument();
  });

  it('HTTP hata (response.status) → "Hata (500)" gösterir', async () => {
    getFxTcmb.mockRejectedValue({ response: { status: 500 } });
    renderPage();
    expect(await screen.findByText('Hata (500)')).toBeInTheDocument();
  });

  it('arama kutusu döviz koduna/adına göre filtreler; eşleşme yoksa boş durum', async () => {
    renderPage();
    await screen.findAllByText('ABD Doları');
    const input = screen.getByPlaceholderText('Ara');
    // "EUR" → sadece Euro satırı kalır, USD adı kaybolur.
    fireEvent.change(input, { target: { value: 'EUR' } });
    expect(screen.getByText('Euro')).toBeInTheDocument();
    expect(screen.queryAllByText('ABD Doları')).toHaveLength(0);
    // Eşleşmeyen sorgu → "Sonuç bulunamadı".
    fireEvent.change(input, { target: { value: 'ZZZZ' } });
    expect(screen.getByText('Sonuç bulunamadı')).toBeInTheDocument();
  });

  it('arama yapınca popüler kartlar gizlenir (q varken şerit kapanır)', async () => {
    renderPage();
    expect(await screen.findByText('Popüler Dövizler')).toBeInTheDocument();
    fireEvent.change(screen.getByPlaceholderText('Ara'), { target: { value: 'USD' } });
    expect(screen.queryByText('Popüler Dövizler')).not.toBeInTheDocument();
  });

  it('arama temizle (X) butonu sorguyu sıfırlar', async () => {
    renderPage();
    await screen.findAllByText('ABD Doları');
    const input = screen.getByPlaceholderText('Ara');
    fireEvent.change(input, { target: { value: 'GBP' } });
    expect(input).toHaveValue('GBP');
    // Temizle butonu (aria-label="Temizle") görünür → tıkla → input boşalır.
    fireEvent.click(screen.getByLabelText('Temizle'));
    expect(input).toHaveValue('');
    // Tüm satırlar geri gelir.
    expect((await screen.findAllByText('ABD Doları'))[0]).toBeInTheDocument();
  });

  it('TCMB satırına tıklanınca detay sayfasına yönlendirir (navigate)', async () => {
    renderPage();
    const usdLinks = await screen.findAllByText('USD/TRY');
    // Tablo satırındaki link bir <a href="/market/fx/USD"> üretir.
    const rowLink = usdLinks.find((el) => el.closest('a'));
    expect(rowLink.closest('a')).toHaveAttribute('href', '/market/fx/USD');
  });

  it('"Open Exchange Rates" sekmesine geçince getFxOpen çağrılır ve tablo yüklenir', async () => {
    renderPage();
    await screen.findAllByText('ABD Doları'); // TCMB önce yüklensin
    fireEvent.click(screen.getByRole('button', { name: /Open Exchange Rates/ }));

    await waitFor(() => expect(getFxOpen).toHaveBeenCalledWith('USD'));
    // Açıklama Open sekmesine döner.
    expect(
      await screen.findByText('Open Exchange Rates — gerçek zamanlıya yakın kurlar')
    ).toBeInTheDocument();
    // Open tablosunda EUR satırı (payload'dan).
    expect(await screen.findByText('Euro')).toBeInTheDocument();
    // Baz para birimi seçici görünür.
    expect(screen.getByText('Baz Para Birimi:')).toBeInTheDocument();
  });

  it('Open sekmesinde baz para birimi değişince getFxOpen yeni bazla yeniden çağrılır', async () => {
    renderPage();
    await screen.findAllByText('ABD Doları');
    fireEvent.click(screen.getByRole('button', { name: /Open Exchange Rates/ }));
    await waitFor(() => expect(getFxOpen).toHaveBeenCalledWith('USD'));

    // Baz seçicide EUR butonuna tıkla → getFxOpen('EUR').
    const baseRow = screen.getByText('Baz Para Birimi:').closest('div');
    fireEvent.click(within(baseRow).getByRole('button', { name: 'EUR' }));
    await waitFor(() => expect(getFxOpen).toHaveBeenCalledWith('EUR'));
  });

  it('"Banka Kurları" sekmesine geçince getBankCurrencyRates çağrılır ve banka satırları yüklenir', async () => {
    renderPage();
    await screen.findAllByText('ABD Doları');
    fireEvent.click(screen.getByRole('button', { name: /Banka Kurları/ }));

    await waitFor(() => expect(getBankCurrencyRates).toHaveBeenCalledTimes(1));
    expect(
      await screen.findByText('Türk bankalarının anlık döviz alış/satış kurları — Hesapkurdu')
    ).toBeInTheDocument();
    expect(await screen.findByText('A Bankası')).toBeInTheDocument();
    // Döviz filtresi etiketi.
    expect(screen.getByText('Döviz:')).toBeInTheDocument();
  });

  it('Banka sekmesinde döviz filtresi seçilince getBankCurrencyRatesByCurrency çağrılır', async () => {
    renderPage();
    await screen.findAllByText('ABD Doları');
    fireEvent.click(screen.getByRole('button', { name: /Banka Kurları/ }));
    await screen.findByText('A Bankası');

    // Filtre satırında "USD" butonuna tıkla.
    const filterRow = screen.getByText('Döviz:').closest('div');
    fireEvent.click(within(filterRow).getByRole('button', { name: 'USD' }));
    await waitFor(() => expect(getBankCurrencyRatesByCurrency).toHaveBeenCalledWith('USD'));
  });

  it('URL ?tab=banks ile açılınca doğrudan Banka sekmesi aktif olur', async () => {
    renderPage({ route: '/market/fx?tab=banks' });
    // Banka açıklaması + banka verisi yüklenmesi → sekme aktif kanıtı.
    expect(
      await screen.findByText('Türk bankalarının anlık döviz alış/satış kurları — Hesapkurdu')
    ).toBeInTheDocument();
    await waitFor(() => expect(getBankCurrencyRates).toHaveBeenCalled());
  });

  it('boş TCMB listesi (rates=[]) gelince "Sonuç bulunamadı" boş durumu gösterir', async () => {
    getFxTcmb.mockResolvedValue({ asOf: '2026-06-04', rates: [] });
    renderPage();
    expect(await screen.findByText('Sonuç bulunamadı')).toBeInTheDocument();
    // Popüler kartlar da olmamalı.
    expect(screen.queryByText('Popüler Dövizler')).not.toBeInTheDocument();
  });

  it('WatchlistStar her TCMB satırına eklenir (assetType=FX, doğru sembol)', async () => {
    renderPage();
    await screen.findAllByText('ABD Doları');
    const stars = screen.getAllByTestId('watchlist-star');
    // 6 satır → 6 yıldız.
    expect(stars.length).toBe(6);
    expect(stars[0]).toHaveAttribute('data-type', 'FX');
    // İlk satır sıralı (symbol asc) → CHF en başta gelir.
    expect(stars.map((s) => s.getAttribute('data-symbol'))).toContain('USD');
  });
});
