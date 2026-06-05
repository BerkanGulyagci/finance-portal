import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { LanguageProvider } from '../../../../context/LanguageContext';

// ─────────────────────────────────────────────────────────────────────────────
// MOCK'LAR
// BondsPage iki marketApi çağrısı yapar: getEvdsBonds (sayfalı liste) +
// getEvdsBondCategoryCounts (kategori sayımları). jsdom'da ağ yok → ikisi de stub.
// BondsPage api'yi uzantısız import eder ('../../../api/marketApi'); FxPage örneğiyle
// tutarlı olsun diye hem '.js'li hem uzantısız specifier'ı AYNI fabrikaya bağlarız.
//
// WatchlistStar AuthContext + WatchlistContext + InstrumentTargetModal çeker;
// BondsPage'in kendi mantığını izole test etmek için tanınabilir bir işaretle stub'lanır.
// EurobondList kendi getGlobalBonds çağrısını + Dropdown/SortableTh ağacını getirir;
// "Eurobond" sekmesi davranışını sembolik olarak doğrulamak için onu da stub'larız.
// Pagination saf bileşendir → gerçeği kullanılır (LanguageProvider yeterli).
// ─────────────────────────────────────────────────────────────────────────────

// vi.hoisted: mock fn'ler vi.mock hoisting'inden önce tanımlı olmalı.
// Factory INLINE (değişken referansı yok → TDZ hatası olmaz).
const { getEvdsBonds, getEvdsBondCategoryCounts } = vi.hoisted(() => ({
  getEvdsBonds: vi.fn(),
  getEvdsBondCategoryCounts: vi.fn(),
}));
vi.mock('../../../../api/marketApi.js', () => ({
  getEvdsBonds: (...a) => getEvdsBonds(...a),
  getEvdsBondCategoryCounts: (...a) => getEvdsBondCategoryCounts(...a),
}));
vi.mock('../../../../api/marketApi', () => ({
  getEvdsBonds: (...a) => getEvdsBonds(...a),
  getEvdsBondCategoryCounts: (...a) => getEvdsBondCategoryCounts(...a),
}));

vi.mock('../../../../components/instrument/WatchlistStar', () => ({
  default: ({ symbol, assetType, name }) => (
    <button data-testid="watchlist-star" data-symbol={symbol} data-type={assetType} data-name={name}>
      ★
    </button>
  ),
}));

vi.mock('../components/EurobondList', () => ({
  default: () => <div data-testid="eurobond-list">EUROBOND_LIST</div>,
}));

import BondsPage from '../BondsPage';

// ── Sahte veri üreticileri (komponentin okuduğu alanlara birebir uygun) ───────
// BondsPage satırlarda b.instrumentCode / b.category / b.maturityDate / b.remainingDays /
// b.indicatorValue / b.dailyChangePercent / b.couponRate / b.source / b.type okur.
function bondRow(over = {}) {
  return {
    instrumentCode: 'TRD070725T18',
    category: 'FIXED_COUPON_BOND',
    type: 'Devlet Tahvili',
    cbrtCode: 'TRT070725F18',
    maturityDate: '2025-07-07',
    remainingDays: 45,
    indicatorValue: 98.42,
    dailyChangePercent: 0.35,
    couponRate: 12.5,
    source: 'TCMB EVDS',
    ...over,
  };
}

function listPayload(items = [bondRow()], over = {}) {
  return {
    items,
    totalItems: items.length,
    totalPages: 1,
    ...over,
  };
}

const COUNTS = {
  FIXED_COUPON_BOND: 12,
  ZERO_COUPON_BILL: 5,
  INFLATION_INDEXED_BOND: 3,
};

// LanguageProvider varsayılan dili "tr" → t(key) anahtarın kendisini döndürür,
// dolayısıyla testlerde Türkçe anahtar metinleri aranır.
// MemoryRouter, satırlardaki <Link>'ler için gereklidir.
function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/market/bonds']}>
      <LanguageProvider>
        <BondsPage />
      </LanguageProvider>
    </MemoryRouter>
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  localStorage.clear();
  // Varsayılan başarılı yanıtlar — gerektiğinde testler override eder.
  getEvdsBonds.mockResolvedValue(listPayload());
  getEvdsBondCategoryCounts.mockResolvedValue(COUNTS);
});

describe('BondsPage (Tahvil / Bono) — jsdom + testing-library', () => {
  it('smoke: throw etmeden render olur, başlık ve DİBS açıklaması görünür', async () => {
    renderPage();
    expect(screen.getByRole('heading', { name: 'Tahvil / Bono' })).toBeInTheDocument();
    expect(
      screen.getByText(
        'Devlet İç Borçlanma Senetleri (DİBS) · TCMB EVDS Gösterge Değerleri · Kaynak: TCMB EVDS'
      )
    ).toBeInTheDocument();
    // Açılışta liste + kategori sayımları çekilir.
    await waitFor(() => expect(getEvdsBonds).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(getEvdsBondCategoryCounts).toHaveBeenCalledTimes(1));
  });

  it('açılışta iki sekme (DİBS / Eurobond) render edilir', () => {
    renderPage();
    expect(screen.getByRole('button', { name: 'Devlet İç Borç (DİBS)' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Eurobond (Dış Borç)' })).toBeInTheDocument();
  });

  it('veri gelince tablo satırı (kıymet kodu link) + stat sayıları görünür', async () => {
    renderPage();
    // Kıymet kodu bir <a href="/market/bonds/TRD070725T18"> link'i üretir.
    const codeLink = await screen.findByText('TRD070725T18');
    expect(codeLink.closest('a')).toHaveAttribute(
      'href',
      '/market/bonds/TRD070725T18'
    );
    // Kategori etiketi CATEGORY_LABELS[b.category] üzerinden gösterilir.
    expect(screen.getByText('Kuponlu Devlet Tahvili')).toBeInTheDocument();
    // Vade tarihi + kupon oranı.
    expect(screen.getByText('2025-07-07')).toBeInTheDocument();
    // Stat kartları: "Toplam Aktif Kıymet" etiketi.
    expect(screen.getByText('Toplam Aktif Kıymet')).toBeInTheDocument();
  });

  it('yüklenirken skeleton (animate-pulse) gösterilir, ardından tablo gelir', async () => {
    let resolve;
    getEvdsBonds.mockReturnValue(new Promise((r) => { resolve = r; }));
    const { container } = renderPage();
    // Çözülmeden önce skeleton placeholder (animate-pulse) DOM'da.
    expect(container.querySelector('.animate-pulse')).toBeInTheDocument();
    resolve(listPayload());
    // Çözülünce satır belirir, skeleton kaybolur.
    expect(await screen.findByText('TRD070725T18')).toBeInTheDocument();
    expect(container.querySelector('.animate-pulse')).not.toBeInTheDocument();
  });

  it('boş liste (items=[]) gelince "veri bulunamadı" boş durumu gösterir', async () => {
    getEvdsBonds.mockResolvedValue(listPayload([], { totalItems: 0, totalPages: 0 }));
    renderPage();
    expect(
      await screen.findByText('Gösterilecek EVDS tahvil/bono verisi bulunamadı.')
    ).toBeInTheDocument();
  });

  it('ağ hatası (err.response yok) → TCMB EVDS hata mesajı gösterir', async () => {
    getEvdsBonds.mockRejectedValue(new Error('network down'));
    renderPage();
    expect(
      await screen.findByText('TCMB EVDS verileri şu anda alınamadı.')
    ).toBeInTheDocument();
  });

  it('HTTP hata (response.status) → "Hata (500)" gösterir', async () => {
    getEvdsBonds.mockRejectedValue({ response: { status: 500 } });
    renderPage();
    expect(await screen.findByText('Hata (500)')).toBeInTheDocument();
  });

  it('"Eurobond" sekmesine geçince EurobondList render edilir, DİBS tablosu kaybolur', async () => {
    renderPage();
    await screen.findByText('TRD070725T18'); // DİBS önce yüklensin
    fireEvent.click(screen.getByRole('button', { name: 'Eurobond (Dış Borç)' }));

    expect(await screen.findByTestId('eurobond-list')).toBeInTheDocument();
    // Eurobond açıklaması görünür.
    expect(
      screen.getByText(
        'Hazine dış borç tahvilleri (Eurobond) · Kaynak: HMB ISIN listesi + Business Insider'
      )
    ).toBeInTheDocument();
    // DİBS tablosundaki satır artık görünmemeli.
    expect(screen.queryByText('TRD070725T18')).not.toBeInTheDocument();
  });

  it('Kıymet Kodu sıralama başlığına tıklayınca getEvdsBonds yeni sortBy ile çağrılır', async () => {
    renderPage();
    await waitFor(() => expect(getEvdsBonds).toHaveBeenCalledTimes(1));
    // Sıralanabilir başlık "Kıymet Kodu" → handleSort('instrumentCode').
    fireEvent.click(screen.getByText('Kıymet Kodu'));
    await waitFor(() =>
      expect(getEvdsBonds).toHaveBeenLastCalledWith(
        expect.objectContaining({ sortBy: 'instrumentCode', sortDir: 'asc' })
      )
    );
  });

  it('aynı kolona ikinci tıklama sıralama yönünü desc yapar', async () => {
    renderPage();
    await waitFor(() => expect(getEvdsBonds).toHaveBeenCalledTimes(1));
    const header = screen.getByText('Vade Tarihi'); // varsayılan sortBy='maturityDate'/asc
    fireEvent.click(header); // toggle → desc
    await waitFor(() =>
      expect(getEvdsBonds).toHaveBeenLastCalledWith(
        expect.objectContaining({ sortBy: 'maturityDate', sortDir: 'desc' })
      )
    );
  });

  it('vade filtresi "0–90 gün" seçilince getEvdsBonds min=0/max=90 ile çağrılır', async () => {
    renderPage();
    await waitFor(() => expect(getEvdsBonds).toHaveBeenCalledTimes(1));
    fireEvent.click(screen.getByRole('button', { name: '0–90 gün' }));
    await waitFor(() =>
      expect(getEvdsBonds).toHaveBeenLastCalledWith(
        expect.objectContaining({ minRemainingDays: 0, maxRemainingDays: 90 })
      )
    );
  });

  it('kategori seçici değişince getEvdsBonds yeni category ile çağrılır', async () => {
    renderPage();
    await waitFor(() => expect(getEvdsBonds).toHaveBeenCalledTimes(1));
    // İlk <select> = kategori filtresi.
    const categorySelect = screen.getAllByRole('combobox')[0];
    fireEvent.change(categorySelect, { target: { value: 'FIXED_COUPON_BOND' } });
    await waitFor(() =>
      expect(getEvdsBonds).toHaveBeenLastCalledWith(
        expect.objectContaining({ category: 'FIXED_COUPON_BOND' })
      )
    );
  });

  it('sayfa boyutu değişince getEvdsBonds yeni size ile çağrılır', async () => {
    renderPage();
    await waitFor(() => expect(getEvdsBonds).toHaveBeenCalledTimes(1));
    // İkinci <select> = sayfa boyutu (SIZE_OPTIONS: 25/50/100).
    const sizeSelect = screen.getAllByRole('combobox')[1];
    fireEvent.change(sizeSelect, { target: { value: '100' } });
    await waitFor(() =>
      expect(getEvdsBonds).toHaveBeenLastCalledWith(
        expect.objectContaining({ size: 100 })
      )
    );
  });

  it('arama kutusuna yazınca input değeri anında güncellenir, debounce sonrası fetch tetiklenir', async () => {
    renderPage();
    // İlk yükleme otursun.
    await waitFor(() => expect(getEvdsBonds).toHaveBeenCalled());

    const input = screen.getByPlaceholderText('TRD07...');
    fireEvent.change(input, { target: { value: 'TRD05' } });
    // Kontrollü input → değer anında yansır (setSearch senkron).
    expect(input).toHaveValue('TRD05');

    // 400ms gerçek debounce dolunca debouncedSearch güncellenir → yeni fetch (search='TRD05').
    await waitFor(() =>
      expect(getEvdsBonds).toHaveBeenLastCalledWith(
        expect.objectContaining({ search: 'TRD05' })
      )
    );
  });

  it('WatchlistStar her satıra eklenir (assetType=BOND, doğru sembol)', async () => {
    getEvdsBonds.mockResolvedValue(
      listPayload([
        bondRow({ instrumentCode: 'TRD070725T18' }),
        bondRow({ instrumentCode: 'TRT140728T15', category: 'ZERO_COUPON_BILL' }),
      ])
    );
    renderPage();
    await screen.findByText('TRD070725T18');
    const stars = screen.getAllByTestId('watchlist-star');
    expect(stars.length).toBe(2);
    expect(stars[0]).toHaveAttribute('data-type', 'BOND');
    const symbols = stars.map((s) => s.getAttribute('data-symbol'));
    expect(symbols).toContain('TRD070725T18');
    expect(symbols).toContain('TRT140728T15');
  });

  it('kategorisi olmayan satırda b.type fallback gösterilir, değişim badge işareti doğru', async () => {
    getEvdsBonds.mockResolvedValue(
      listPayload([
        bondRow({
          instrumentCode: 'TRX999999X99',
          category: null,
          type: 'Hazine Bonosu',
          dailyChangePercent: -1.2,
          couponRate: null,
        }),
      ])
    );
    renderPage();
    await screen.findByText('TRX999999X99');
    // category yok → type metni etiket olarak görünür.
    expect(screen.getByText('Hazine Bonosu')).toBeInTheDocument();
    // Negatif günlük değişim → fmtPct "-%1,20" (tr-TR ondalık virgül).
    const row = screen.getByText('TRX999999X99').closest('tr');
    expect(within(row).getByText('-%1,20')).toBeInTheDocument();
  });
});
