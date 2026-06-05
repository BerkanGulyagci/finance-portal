import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { LanguageProvider } from '../../../../context/LanguageContext';

// ─────────────────────────────────────────────────────────────────────────────
// MOCK'LAR
// TefasPage 4 marketApi çağrısı yapar — sekmeye göre biri:
//   getAllTefasFunds / getAllBesFunds / getAllOksFunds / getOsmanliFundBulletin.
// jsdom'da ağ YOKTUR → hepsi stub'lanır. Mock yolu __tests__/ konumuna göre:
//   __tests__ → funds → market → features → src  ⇒ ../../../../api/marketApi
// FxPage testindeki gibi hem uzantılı hem uzantısız specifier'ı bağlarız ki
// hangi yoldan resolve edilirse edilsin mock devreye girsin.
//
// WatchlistStar AuthContext + WatchlistContext + InstrumentTargetModal çeker;
// TefasPage'in KENDİ mantığını izole etmek için onu tanınabilir bir işaretle stub'larız.
// SortableTh / Pagination / Dropdown saf bileşenler → gerçeği kullanılır
// (LanguageProvider yeter; Dropdown'ın ChevronDown'ı jsdom'da SVG olarak render olur).
// ─────────────────────────────────────────────────────────────────────────────

const { getAllTefasFunds, getAllBesFunds, getAllOksFunds, getOsmanliFundBulletin } = vi.hoisted(() => ({
  getAllTefasFunds: vi.fn(),
  getAllBesFunds: vi.fn(),
  getAllOksFunds: vi.fn(),
  getOsmanliFundBulletin: vi.fn(),
}));
vi.mock('../../../../api/marketApi.js', () => ({
  getAllTefasFunds: (...a) => getAllTefasFunds(...a),
  getAllBesFunds: (...a) => getAllBesFunds(...a),
  getAllOksFunds: (...a) => getAllOksFunds(...a),
  getOsmanliFundBulletin: (...a) => getOsmanliFundBulletin(...a),
}));
vi.mock('../../../../api/marketApi', () => ({
  getAllTefasFunds: (...a) => getAllTefasFunds(...a),
  getAllBesFunds: (...a) => getAllBesFunds(...a),
  getAllOksFunds: (...a) => getAllOksFunds(...a),
  getOsmanliFundBulletin: (...a) => getOsmanliFundBulletin(...a),
}));

vi.mock('../../../../components/instrument/WatchlistStar', () => ({
  default: ({ symbol, assetType, name }) => (
    <button data-testid="watchlist-star" data-symbol={symbol} data-type={assetType} data-name={name}>
      ★
    </button>
  ),
}));

import TefasPage from '../TefasPage';

// ── Sahte veri üreticileri (komponentin OKUDUĞU alanlara birebir uygun) ───────
// FundTable r.code / r.name / r.managerName / r.founderName / r.fundType /
//   r.riskLevel / r.price / r.returnOneMonth/ThreeMonths/OneYear/ThreeYears okur.
function tefasFund(over = {}) {
  return {
    code: 'AAA', name: 'A Fonu', managerName: 'A Yönetim', founderName: 'A Portföy',
    fundType: 'Hisse Senedi Fonu', riskLevel: 5, price: '12.345678',
    returnOneMonth: '2.50', returnThreeMonths: '5.00',
    returnOneYear: '20.00', returnThreeYears: '-3.00',
    ...over,
  };
}

function tefasFunds() {
  return [
    tefasFund({ code: 'AAA', name: 'Alfa Fonu',  fundType: 'Hisse Senedi Fonu', founderName: 'Alfa Portföy', returnOneMonth: '1.10' }),
    tefasFund({ code: 'BBB', name: 'Beta Fonu',  fundType: 'Borçlanma Araçları Fonu', founderName: 'Beta Portföy', returnOneMonth: '-2.20' }),
    tefasFund({ code: 'CCC', name: 'Ceta Fonu',  fundType: 'Hisse Senedi Fonu', founderName: 'Alfa Portföy', riskLevel: null, price: null }),
  ];
}

// Osmanlı bülteni FARKLI alanlar: r.type / r.group / r.dailyReturn / r.weeklyReturn /
//   r.monthlyReturn / r.yearlyReturn.
function osmanliFunds() {
  return [
    { code: 'OZA', name: 'Osmanlı A', group: 'Hisse', type: 'Yatirim', riskLevel: 4,
      dailyReturn: '0.50', weeklyReturn: '1.20', monthlyReturn: '3.30', yearlyReturn: '25.00', uniqueCode: 'OZA1' },
    { code: 'OZB', name: 'Osmanlı B', group: 'Para Piyasası', type: 'Serbest', riskLevel: 2,
      dailyReturn: '-0.10', weeklyReturn: '0.40', monthlyReturn: '1.10', yearlyReturn: '18.00', uniqueCode: 'OZB1' },
  ];
}

// 20'den fazla fon → ikinci sayfa / pagination kontrolü için.
function manyTefasFunds(n = 25) {
  return Array.from({ length: n }, (_, i) => {
    const num = String(i + 1).padStart(2, '0');
    return tefasFund({ code: `F${num}`, name: `Fon ${num}`, founderName: 'Tek Portföy', fundType: 'Karma Fon' });
  });
}

// LanguageProvider varsayılan dil "tr" → t(key) anahtarın KENDİSİNİ döndürür.
// MemoryRouter Link bileşeni için gerekli.
function renderPage() {
  return render(
    <MemoryRouter>
      <LanguageProvider>
        <TefasPage />
      </LanguageProvider>
    </MemoryRouter>
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  localStorage.clear();
  // Varsayılan başarılı yanıtlar — gerektiğinde testler override eder.
  getAllTefasFunds.mockResolvedValue(tefasFunds());
  getAllBesFunds.mockResolvedValue(tefasFunds());
  getAllOksFunds.mockResolvedValue(tefasFunds());
  getOsmanliFundBulletin.mockResolvedValue(osmanliFunds());
});

describe('TefasPage (Yatırım Fonları) — jsdom + testing-library', () => {
  it('smoke: throw etmeden render olur, başlık ve kaynak satırı görünür', async () => {
    renderPage();
    expect(screen.getByRole('heading', { name: 'Yatırım Fonları' })).toBeInTheDocument();
    expect(screen.getByText('Kaynak: Rasyonet / YatırımDirekt')).toBeInTheDocument();
    // Açılışta yalnız TEFAS sekmesi yüklenir.
    await waitFor(() => expect(getAllTefasFunds).toHaveBeenCalledTimes(1));
    expect(getAllBesFunds).not.toHaveBeenCalled();
    expect(getAllOksFunds).not.toHaveBeenCalled();
    expect(getOsmanliFundBulletin).not.toHaveBeenCalled();
  });

  it('dört sekme butonu (TEFAS / BES / OKS / Osmanlı Portföy) render edilir', () => {
    renderPage();
    expect(screen.getByRole('button', { name: /TEFAS/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /BES/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /OKS/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Osmanlı Portföy/ })).toBeInTheDocument();
  });

  it('TEFAS verisi gelince fon satırları (kod + ad) tabloda görünür', async () => {
    renderPage();
    // Fon kodu Link metni olarak gelir.
    expect(await screen.findByText('AAA')).toBeInTheDocument();
    expect(screen.getByText('Alfa Fonu')).toBeInTheDocument();
    expect(screen.getByText('Beta Fonu')).toBeInTheDocument();
    // Yönetici adı alt satırda gösterilir.
    expect(screen.getAllByText('A Yönetim').length).toBeGreaterThan(0);
  });

  it('fon kodu /market/tefas/:code detay linkine bağlanır', async () => {
    renderPage();
    const codeLink = await screen.findByText('AAA');
    expect(codeLink.closest('a')).toHaveAttribute('href', '/market/tefas/AAA');
  });

  it('yüklenirken skeleton (animate-pulse), ardından tablo gösterilir', async () => {
    let resolve;
    getAllTefasFunds.mockReturnValue(new Promise((r) => { resolve = r; }));
    const { container } = renderPage();
    // Çözülmeden önce skeleton placeholder (animate-pulse).
    expect(container.querySelector('.animate-pulse')).toBeInTheDocument();
    resolve(tefasFunds());
    // Çözülünce satır belirir, skeleton kaybolur.
    expect(await screen.findByText('Alfa Fonu')).toBeInTheDocument();
    expect(container.querySelector('.animate-pulse')).not.toBeInTheDocument();
  });

  it('ağ hatası (err.response yok) → "Sunucuya ulaşılamıyor." gösterir', async () => {
    getAllTefasFunds.mockRejectedValue(new Error('network down'));
    renderPage();
    expect(await screen.findByText('Sunucuya ulaşılamıyor.')).toBeInTheDocument();
  });

  it('HTTP hata (response.status) → "Hata (500)" gösterir', async () => {
    getAllTefasFunds.mockRejectedValue({ response: { status: 500 } });
    renderPage();
    expect(await screen.findByText('Hata (500)')).toBeInTheDocument();
  });

  it('boş liste ([]) gelince "Sonuç bulunamadı." boş durumu gösterir', async () => {
    getAllTefasFunds.mockResolvedValue([]);
    renderPage();
    expect(await screen.findByText('Sonuç bulunamadı.')).toBeInTheDocument();
  });

  it('API dizi DÖNMEZSE (null) komponent çökmez, boş duruma düşer', async () => {
    getAllTefasFunds.mockResolvedValue(null);
    renderPage();
    // Array.isArray(funds) ? funds : [] → boş tablo.
    expect(await screen.findByText('Sonuç bulunamadı.')).toBeInTheDocument();
  });

  it('arama kutusu fon koduna/adına göre filtreler; eşleşme yoksa boş durum', async () => {
    renderPage();
    await screen.findByText('Alfa Fonu');
    const input = screen.getByPlaceholderText('Fon kodu, adı veya yönetici ara...');
    // "Beta" → sadece Beta satırı kalır, Alfa kaybolur.
    fireEvent.change(input, { target: { value: 'Beta' } });
    expect(screen.getByText('Beta Fonu')).toBeInTheDocument();
    expect(screen.queryByText('Alfa Fonu')).not.toBeInTheDocument();
    // Eşleşmeyen sorgu → "Sonuç bulunamadı."
    fireEvent.change(input, { target: { value: 'ZZZZ' } });
    expect(screen.getByText('Sonuç bulunamadı.')).toBeInTheDocument();
  });

  it('her satıra WatchlistStar eklenir (assetType=FUND, doğru sembol)', async () => {
    renderPage();
    await screen.findByText('Alfa Fonu');
    const stars = screen.getAllByTestId('watchlist-star');
    expect(stars.length).toBe(3); // 3 fon → 3 yıldız
    expect(stars[0]).toHaveAttribute('data-type', 'FUND');
    const symbols = stars.map((s) => s.getAttribute('data-symbol'));
    expect(symbols).toContain('AAA');
    expect(symbols).toContain('BBB');
  });

  it('her satırda karşılaştırma (compare) linki bulunur', async () => {
    renderPage();
    await screen.findByText('AAA');
    // /market/tefas/compare?codes=AAA bağlantısı.
    const compareLink = document.querySelector('a[href="/market/tefas/compare?codes=AAA"]');
    expect(compareLink).toBeInTheDocument();
  });

  it('20\'den fazla fonda sayfalama: ikinci sayfaya geçilince sonraki satırlar görünür', async () => {
    getAllTefasFunds.mockResolvedValue(manyTefasFunds(25));
    renderPage();
    // İlk sayfa: F01 var, F25 yok (PAGE_SIZE=20).
    expect(await screen.findByText('Fon 01')).toBeInTheDocument();
    expect(screen.queryByText('Fon 25')).not.toBeInTheDocument();
    // "Sonraki sayfa" → 2. sayfada F25 görünür, F01 kaybolur.
    fireEvent.click(screen.getByLabelText('Sonraki sayfa'));
    expect(await screen.findByText('Fon 25')).toBeInTheDocument();
    expect(screen.queryByText('Fon 01')).not.toBeInTheDocument();
  });

  it('sütun başlığına tıklayınca sıralama yönü değişir (kod desc → ilk satır son fon)', async () => {
    getAllTefasFunds.mockResolvedValue(manyTefasFunds(25));
    renderPage();
    await screen.findByText('Fon 01');
    // "Fon Kodu" başlığına tıkla → asc zaten varsayılan, tekrar tıkla desc.
    // Varsayılan key 'code' asc; başlığa bir kez tıklayınca desc olur → F25 ilk sayfada.
    const codeHeader = screen.getByText('Fon Kodu');
    fireEvent.click(codeHeader);
    // desc: en büyük kod (F25) ilk sayfada görünür.
    expect(await screen.findByText('Fon 25')).toBeInTheDocument();
    // F01 artık ilk 20 dışına düşer.
    expect(screen.queryByText('Fon 01')).not.toBeInTheDocument();
  });

  it('BES sekmesine geçince getAllBesFunds çağrılır ve "Şirket" sütunu eklenir', async () => {
    renderPage();
    await screen.findByText('Alfa Fonu'); // TEFAS önce yüklensin
    fireEvent.click(screen.getByRole('button', { name: /BES/ }));

    await waitFor(() => expect(getAllBesFunds).toHaveBeenCalledTimes(1));
    // showFounder=true → "Şirket" başlığı görünür.
    expect(await screen.findByText('Şirket')).toBeInTheDocument();
  });

  it('OKS sekmesine geçince getAllOksFunds çağrılır', async () => {
    renderPage();
    await screen.findByText('Alfa Fonu');
    fireEvent.click(screen.getByRole('button', { name: /OKS/ }));
    await waitFor(() => expect(getAllOksFunds).toHaveBeenCalledTimes(1));
  });

  it('sekme verisi cache\'lenir: aynı sekmeye geri dönünce API tekrar çağrılmaz', async () => {
    renderPage();
    await screen.findByText('Alfa Fonu');
    // BES'e geç (1. çağrı) — veri RENDER olana dek bekle (data.bes set edildi kanıtı).
    fireEvent.click(screen.getByRole('button', { name: /BES/ }));
    await waitFor(() => expect(getAllBesFunds).toHaveBeenCalledTimes(1));
    await screen.findByText('Şirket'); // showFounder=true → BES verisi yerleşti
    // Osmanlı'ya geç, bülten yüklensin.
    fireEvent.click(screen.getByRole('button', { name: /Osmanlı Portföy/ }));
    await waitFor(() => expect(getOsmanliFundBulletin).toHaveBeenCalledTimes(1));
    await screen.findByText('Günlük %');
    // Tekrar BES'e dön.
    fireEvent.click(screen.getByRole('button', { name: /BES/ }));
    await screen.findByText('Şirket');
    // data[bes] dolu → fetchTab erken return → 2. çağrı OLMAZ.
    expect(getAllBesFunds).toHaveBeenCalledTimes(1);
    // TEFAS da hiç yeniden çağrılmadı (açılıştaki tek çağrı).
    expect(getAllTefasFunds).toHaveBeenCalledTimes(1);
  });

  it('Osmanlı Portföy sekmesi getOsmanliFundBulletin çağırır ve bülten tablosu (Günlük/Haftalık) gösterir', async () => {
    renderPage();
    await screen.findByText('Alfa Fonu');
    fireEvent.click(screen.getByRole('button', { name: /Osmanlı Portföy/ }));

    await waitFor(() => expect(getOsmanliFundBulletin).toHaveBeenCalledTimes(1));
    // Bülten tablosuna özgü başlıklar.
    expect(await screen.findByText('Günlük %')).toBeInTheDocument();
    expect(screen.getByText('Haftalık %')).toBeInTheDocument();
    expect(screen.getByText('Grup')).toBeInTheDocument();
    // Bülten satırları (kod + ad).
    expect(screen.getByText('OZA')).toBeInTheDocument();
    expect(screen.getByText('Osmanlı A')).toBeInTheDocument();
  });

  it('Osmanlı bülteninde arama grup/koda göre filtreler', async () => {
    renderPage();
    await screen.findByText('Alfa Fonu');
    fireEvent.click(screen.getByRole('button', { name: /Osmanlı Portföy/ }));
    await screen.findByText('Osmanlı A');

    const input = screen.getByPlaceholderText('Fon kodu, adı veya grup ara...');
    fireEvent.change(input, { target: { value: 'OZB' } });
    expect(screen.getByText('Osmanlı B')).toBeInTheDocument();
    expect(screen.queryByText('Osmanlı A')).not.toBeInTheDocument();
  });

  it('Osmanlı bülteni boş gelince "Sonuç bulunamadı." gösterir', async () => {
    getOsmanliFundBulletin.mockResolvedValue([]);
    renderPage();
    await screen.findByText('Alfa Fonu');
    fireEvent.click(screen.getByRole('button', { name: /Osmanlı Portföy/ }));
    expect(await screen.findByText('Sonuç bulunamadı.')).toBeInTheDocument();
  });

  it('Osmanlı bülteni HTTP hatası → "Hata (404)" gösterir', async () => {
    getOsmanliFundBulletin.mockRejectedValue({ response: { status: 404 } });
    renderPage();
    await screen.findByText('Alfa Fonu');
    fireEvent.click(screen.getByRole('button', { name: /Osmanlı Portföy/ }));
    expect(await screen.findByText('Hata (404)')).toBeInTheDocument();
  });

  it('veri yüklenince sekme butonu fon sayısını gösterir ("· N fon")', async () => {
    renderPage();
    await screen.findByText('Alfa Fonu');
    // TEFAS sekmesi 3 fon → alt etikette "· 3 fon" parçası.
    const tefasBtn = screen.getByRole('button', { name: /TEFAS/ });
    expect(within(tefasBtn).getByText(/· 3 fon/)).toBeInTheDocument();
  });
});
