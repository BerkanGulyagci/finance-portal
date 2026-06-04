import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';

// ── Mock'lar ────────────────────────────────────────────────────────────────
// WatchlistDetail; üç ağır alt komponenti (WatchlistTable / WatchlistCharts=GridBoard
// + grafik kayıtları / AddWatchlistItemModal) ve portfolioApi'yi (3 fonksiyon) çeker.
// jsdom'da canvas/ağ yoktur → alt komponentleri, ihtiyaç duyulan props'ları DOM'a
// köprüleyen basit stub'lara indirgeriz; böylece test SADECE WatchlistDetail'in
// kendi mantığını doğrular: sekme geçişi, tür/trend filtresi + varlık-türü grupları,
// CSV dışa aktarım, sembol-ekle modalı, silme akışı (confirm → deleteWatchlistItem)
// ve "portföye ekle" akışı (hedef portföy modalı → getMyPortfolios → navigate).
//
// computeTrend / getWatchlistDetailPath SAF yardımcılardır → mock'lanmaz (gerçek
// filtre mantığı çalışsın). useNavigate gerçek react-router-dom'dan gelir ama
// navigasyonu gözlemlemek için spy'lanır.
//
// Yol notu: test dosyası src/features/portfolio/components/__tests__ içinde →
//   ../X                                → komşu komponent (WatchlistTable …)
//   ../../utils/X                       → exportWatchlistCsv
//   ../../../../api/portfolioApi        → api modülü
//   ../../../../context/LanguageContext → gerçek LanguageProvider

const getWatchlistItems = vi.fn();
const deleteWatchlistItem = vi.fn();
const getMyPortfolios = vi.fn();
vi.mock('../../../../api/portfolioApi', () => ({
  getWatchlistItems: (...a) => getWatchlistItems(...a),
  deleteWatchlistItem: (...a) => deleteWatchlistItem(...a),
  getMyPortfolios: (...a) => getMyPortfolios(...a),
}));

const downloadWatchlistCsv = vi.fn();
vi.mock('../../utils/exportWatchlistCsv', () => ({
  downloadWatchlistCsv: (...a) => downloadWatchlistCsv(...a),
}));

// useNavigate spy — komponent navigate(`/portfolio/:id?...`) çağrısını gözlemleriz.
const navigateMock = vi.fn();
vi.mock('react-router-dom', async (orig) => ({
  ...(await orig()),
  useNavigate: () => navigateMock,
}));

// WatchlistTable: aldığı items + delete/addToPortfolio köprüleri + variant işareti.
vi.mock('../WatchlistTable', () => ({
  default: ({ variant, items, onDelete, deletingId, onAddToPortfolio }) => (
    <div data-testid={`wl-table-${variant}`} data-deleting={String(deletingId)}>
      rows:{(items ?? []).length}
      {(items ?? []).map((it) => (
        <div key={it.id} data-testid={`row-${it.id}`}>
          <span>{it.symbol}</span>
          <button type="button" onClick={() => onDelete(it.id)}>del-{it.id}</button>
          <button type="button" onClick={() => onAddToPortfolio(it, `${it.symbol} A.Ş.`)}>
            addpf-{it.id}
          </button>
        </div>
      ))}
    </div>
  ),
}));

vi.mock('../WatchlistCharts', () => ({
  default: ({ items, loading, portfolioName, portfolioId }) => (
    <div
      data-testid="wl-charts"
      data-loading={String(loading)}
      data-pid={String(portfolioId)}
      data-pname={portfolioName}
    >
      charts:{(items ?? []).length}
    </div>
  ),
}));

vi.mock('../AddWatchlistItemModal', () => ({
  default: ({ portfolioId, portfolioName, onClose, onAdded }) => (
    <div data-testid="add-wl-modal" data-pid={String(portfolioId)}>
      <span data-testid="add-wl-name">{portfolioName}</span>
      <button type="button" onClick={onClose}>close-add</button>
      <button
        type="button"
        onClick={() => onAdded({ id: 999, symbol: 'NEW', assetType: 'STOCK' })}
      >
        do-add
      </button>
    </div>
  ),
}));

import WatchlistDetail from '../WatchlistDetail';
import { LanguageProvider } from '../../../../context/LanguageContext';

// Varsayılan dil "tr" → t(key) anahtarın kendisini döndürür; gerçek Provider ile sar.
function renderDetail(props = {}) {
  const merged = { portfolio: watchlistPortfolio(), ...props };
  const utils = render(
    <MemoryRouter>
      <LanguageProvider>
        <WatchlistDetail {...merged} />
      </LanguageProvider>
    </MemoryRouter>,
  );
  return { ...utils, props: merged };
}

function watchlistPortfolio(over = {}) {
  return { id: 'wl-1', name: 'İzleme', portfolioType: 'WATCHLIST', ...over };
}

// İki STOCK + bir CRYPTO + bir FUND. Trend alanları sade tutuldu; gerçek computeTrend
// çalışır: explicit "trend" verince UP/DOWN deterministik döner.
function sampleItems() {
  return [
    { id: 1, symbol: 'THYAO', assetType: 'STOCK', lastPrice: 300, trend: 'UP' },
    { id: 2, symbol: 'ASELS', assetType: 'STOCK', lastPrice: 50, trend: 'DOWN' },
    { id: 3, symbol: 'BTC', assetType: 'CRYPTO', lastPrice: 2_000_000, trend: 'UP' },
    { id: 4, symbol: 'TTE', assetType: 'FUND', lastPrice: 12 }, // trend verisi yok → NO_DATA
  ];
}

beforeEach(() => {
  vi.clearAllMocks();
  localStorage.clear();
  // Varsayılan: dolu izleme listesi.
  getWatchlistItems.mockResolvedValue(sampleItems());
  getMyPortfolios.mockResolvedValue([
    { id: 'h-1', name: 'Ana Varlık', currency: 'TRY', portfolioType: 'HOLDINGS' },
    { id: 'wl-x', name: 'Başka İzleme', currency: 'USD', portfolioType: 'WATCHLIST' },
  ]);
  deleteWatchlistItem.mockResolvedValue({});
  vi.spyOn(window, 'confirm').mockReturnValue(true);
  vi.spyOn(window, 'alert').mockImplementation(() => {});
});

describe('WatchlistDetail — render ve yükleme (smoke)', () => {
  it('mount edince getWatchlistItems(portfolioId) çağrılır; dolu liste varlık-türü kartlarına bölünür', async () => {
    renderDetail();
    expect(getWatchlistItems).toHaveBeenCalledWith('wl-1');

    // Veri gelince Hisseler kartı (2 satır) + Kripto + Fon kartları görünür.
    const stockTable = (await screen.findAllByTestId('wl-table-default'))[0];
    expect(stockTable).toHaveTextContent('rows:2'); // ilk default = STOCK bölümü
    expect(screen.getByText('Hisseler', { selector: 'h3' })).toBeInTheDocument();
    expect(screen.getByText('Kripto', { selector: 'h3' })).toBeInTheDocument();
    expect(screen.getByText('Fon', { selector: 'h3' })).toBeInTheDocument();
  });

  it('üst bar sekme + aksiyon butonlarını gösterir (Özet/Grafikler/CSV/Sembol Ekle)', async () => {
    renderDetail();
    await screen.findAllByText('Hisseler');
    expect(screen.getByRole('button', { name: 'Özet' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Grafikler' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /CSV'ye Aktar/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Sembol Ekle/ })).toBeInTheDocument();
  });

  it('alt yazı izlenen sembol sayısını gösterir ({count} → 4)', async () => {
    renderDetail();
    expect(await screen.findByText('4 sembol takip ediliyor')).toBeInTheDocument();
  });

  it('boş liste → hiçbir varlık kartı çıkmaz, sayaç 0', async () => {
    getWatchlistItems.mockResolvedValue([]);
    renderDetail();
    expect(await screen.findByText('0 sembol takip ediliyor')).toBeInTheDocument();
    expect(screen.queryByText('Hisseler', { selector: 'h3' })).not.toBeInTheDocument();
    expect(screen.queryAllByTestId('wl-table-default')).toHaveLength(0);
  });

  it('API reddederse boş listeye düşer (throw etmez), sayaç 0', async () => {
    getWatchlistItems.mockRejectedValue(new Error('boom'));
    renderDetail();
    expect(await screen.findByText('0 sembol takip ediliyor')).toBeInTheDocument();
  });
});

describe('WatchlistDetail — sekmeler', () => {
  it('Grafikler sekmesi WatchlistCharts\'ı (tüm itemlarla) gösterir, özet kartını gizler', async () => {
    renderDetail();
    await screen.findAllByText('Hisseler');

    fireEvent.click(screen.getByRole('button', { name: 'Grafikler' }));

    const charts = screen.getByTestId('wl-charts');
    expect(charts).toHaveTextContent('charts:4'); // filtrelenmemiş tüm items
    expect(charts).toHaveAttribute('data-pid', 'wl-1');
    expect(charts).toHaveAttribute('data-pname', 'İzleme');
    // Özet sekmesine ait filtre kartı (Tür/Trend) artık görünmez.
    expect(screen.queryByText('Trend')).not.toBeInTheDocument();
    // Geri Özet'e dönülünce kartlar yine görünür.
    fireEvent.click(screen.getByRole('button', { name: 'Özet' }));
    expect(screen.getByText('Hisseler', { selector: 'h3' })).toBeInTheDocument();
  });
});

describe('WatchlistDetail — tür ve trend filtreleri', () => {
  it('Tür filtresi STOCK seçilince yalnız Hisseler kartı kalır', async () => {
    renderDetail();
    await screen.findByText('Kripto');

    // "Tür" select'i (ilk combobox) → STOCK.
    const selects = screen.getAllByRole('combobox');
    fireEvent.change(selects[0], { target: { value: 'STOCK' } });

    expect(screen.getByText('Hisseler', { selector: 'h3' })).toBeInTheDocument();
    expect(screen.queryByText('Kripto', { selector: 'h3' })).not.toBeInTheDocument();
    expect(screen.queryByText('Fon', { selector: 'h3' })).not.toBeInTheDocument();
  });

  it('Trend filtresi UP → yalnız yükseliş sinyalli semboller (THYAO + BTC) kalır', async () => {
    renderDetail();
    await screen.findByText('Kripto');

    // "Trend" select'i = "Veri yok" option'ı içeren combobox → UP. computeTrend explicit döner.
    const selects = screen.getAllByRole('combobox');
    const trendSelect = selects.find((s) => within(s).queryByText('Veri yok'));
    fireEvent.change(trendSelect, { target: { value: 'UP' } });

    // THYAO (STOCK, UP) + BTC (CRYPTO, UP) kalır; ASELS (DOWN) ve TTE (NO_DATA) düşer.
    expect(screen.getByText('Hisseler', { selector: 'h3' })).toBeInTheDocument();
    expect(screen.getAllByTestId('wl-table-default')[0]).toHaveTextContent('rows:1'); // STOCK: sadece THYAO
    expect(screen.getByText('Kripto', { selector: 'h3' })).toBeInTheDocument();
    expect(screen.getByText('THYAO')).toBeInTheDocument();
    expect(screen.queryByText('ASELS')).not.toBeInTheDocument();
    expect(screen.queryByText('Fon', { selector: 'h3' })).not.toBeInTheDocument();
  });

  it('Trend filtresi NO_DATA seçilince sinyalsiz FUND (TTE) görünür kalır', async () => {
    renderDetail();
    await screen.findByText('Kripto');

    // Trend select'ini değer atayarak NO_DATA'ya çek (option value="NO_DATA").
    const selects = screen.getAllByRole('combobox');
    const trendSelect = selects.find((s) => within(s).queryByText('Veri yok'));
    expect(trendSelect).toBeDefined();
    fireEvent.change(trendSelect, { target: { value: 'NO_DATA' } });

    // computeTrend(TTE)=null (sinyalsiz FUND) → NO_DATA filtresinde TTE her durumda görünür kalır.
    // (Fon kartı + TTE satırı = NO_DATA grubunun doğru elemanı.)
    expect(screen.getByText('Fon', { selector: 'h3' })).toBeInTheDocument();
    expect(screen.getByText('TTE')).toBeInTheDocument();
  });
});

describe('WatchlistDetail — CSV dışa aktarım', () => {
  it('"CSV\'ye Aktar" → downloadWatchlistCsv(portföy adı, items) çağrılır', async () => {
    renderDetail();
    await screen.findAllByText('Hisseler');

    fireEvent.click(screen.getByRole('button', { name: /CSV'ye Aktar/ }));
    expect(downloadWatchlistCsv).toHaveBeenCalledTimes(1);
    const [name, items] = downloadWatchlistCsv.mock.calls[0];
    expect(name).toBe('İzleme');
    expect(items).toHaveLength(4);
  });

  it('CSV üretimi hata atarsa alert ile uyarır', async () => {
    downloadWatchlistCsv.mockImplementation(() => { throw new Error('csv fail'); });
    renderDetail();
    await screen.findAllByText('Hisseler');

    fireEvent.click(screen.getByRole('button', { name: /CSV'ye Aktar/ }));
    expect(window.alert).toHaveBeenCalledWith('CSV oluşturulamadı.');
  });
});

describe('WatchlistDetail — sembol ekleme modalı', () => {
  it('"Sembol Ekle" → AddWatchlistItemModal açılır; portföy id + adını taşır', async () => {
    renderDetail();
    await screen.findAllByText('Hisseler');
    expect(screen.queryByTestId('add-wl-modal')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: /Sembol Ekle/ }));
    const modal = screen.getByTestId('add-wl-modal');
    expect(modal).toHaveAttribute('data-pid', 'wl-1');
    expect(screen.getByTestId('add-wl-name')).toHaveTextContent('İzleme');
  });

  it('modal "do-add" → yeni sembol listeye eklenir (sayaç 4→5) ve modal kapanır', async () => {
    renderDetail();
    await screen.findByText('4 sembol takip ediliyor');

    fireEvent.click(screen.getByRole('button', { name: /Sembol Ekle/ }));
    fireEvent.click(screen.getByText('do-add'));

    expect(await screen.findByText('5 sembol takip ediliyor')).toBeInTheDocument();
    expect(screen.queryByTestId('add-wl-modal')).not.toBeInTheDocument();
  });

  it('modal close-add → modal kapanır, liste değişmez', async () => {
    renderDetail();
    await screen.findAllByText('Hisseler');

    fireEvent.click(screen.getByRole('button', { name: /Sembol Ekle/ }));
    fireEvent.click(screen.getByText('close-add'));
    await waitFor(() => expect(screen.queryByTestId('add-wl-modal')).not.toBeInTheDocument());
    expect(screen.getByText('4 sembol takip ediliyor')).toBeInTheDocument();
  });
});

describe('WatchlistDetail — sembol silme akışı', () => {
  it('onay → deleteWatchlistItem(portfolioId, itemId) çağrılır ve satır listeden çıkar', async () => {
    renderDetail();
    await screen.findByText('THYAO');

    // İlk default tablodaki (STOCK) THYAO satırını sil (id=1).
    fireEvent.click(within(screen.getByTestId('row-1')).getByText('del-1'));

    expect(window.confirm).toHaveBeenCalled();
    await waitFor(() => expect(deleteWatchlistItem).toHaveBeenCalledWith('wl-1', 1));
    // Sayaç 4→3, THYAO satırı kayboldu.
    expect(await screen.findByText('3 sembol takip ediliyor')).toBeInTheDocument();
    expect(screen.queryByText('THYAO')).not.toBeInTheDocument();
  });

  it('onay reddedilirse deleteWatchlistItem hiç çağrılmaz', async () => {
    window.confirm.mockReturnValue(false);
    renderDetail();
    await screen.findByText('THYAO');

    fireEvent.click(within(screen.getByTestId('row-1')).getByText('del-1'));
    expect(window.confirm).toHaveBeenCalled();
    expect(deleteWatchlistItem).not.toHaveBeenCalled();
    expect(screen.getByText('THYAO')).toBeInTheDocument();
  });

  it('silme reddedince hata alert\'i gösterilir ve satır kalır', async () => {
    deleteWatchlistItem.mockRejectedValue(new Error('nope'));
    renderDetail();
    await screen.findByText('THYAO');

    fireEvent.click(within(screen.getByTestId('row-1')).getByText('del-1'));
    await waitFor(() => expect(deleteWatchlistItem).toHaveBeenCalled());
    await waitFor(() => expect(window.alert).toHaveBeenCalledWith('Sembol silinemedi.'));
    expect(screen.getByText('THYAO')).toBeInTheDocument();
  });
});

describe('WatchlistDetail — portföye ekle akışı', () => {
  it('addToPortfolio → hedef portföy modalı açılır; yalnız HOLDINGS portföyleri listelenir', async () => {
    renderDetail();
    await screen.findByText('THYAO');

    fireEvent.click(within(screen.getByTestId('row-1')).getByText('addpf-1'));

    // Modal başlığı + instrument adı (displayName "THYAO A.Ş.").
    expect(await screen.findByText('Varlık Portföyü Seç')).toBeInTheDocument();
    expect(screen.getByText('THYAO A.Ş. için alım/satım ekranı açılacak.')).toBeInTheDocument();

    // getMyPortfolios çağrıldı; sadece HOLDINGS olan "Ana Varlık" görünür, WATCHLIST gizli.
    await waitFor(() => expect(getMyPortfolios).toHaveBeenCalled());
    expect(await screen.findByText('Ana Varlık')).toBeInTheDocument();
    expect(screen.queryByText('Başka İzleme')).not.toBeInTheDocument();
  });

  it('hedef HOLDINGS portföyü seçilince navigate(/portfolio/:id?addTx=1&symbol=...&price=...)', async () => {
    renderDetail();
    await screen.findByText('THYAO');

    fireEvent.click(within(screen.getByTestId('row-1')).getByText('addpf-1'));
    const targetBtn = await screen.findByText('Ana Varlık');
    fireEvent.click(targetBtn);

    expect(navigateMock).toHaveBeenCalledTimes(1);
    const url = navigateMock.mock.calls[0][0];
    expect(url).toContain('/portfolio/h-1?');
    expect(url).toContain('addTx=1');
    expect(url).toContain('symbol=THYAO');
    expect(url).toContain('assetType=STOCK');
    expect(url).toContain('price=300'); // item.lastPrice=300
    // Modal navigate sonrası kapanır.
    await waitFor(() => expect(screen.queryByText('Varlık Portföyü Seç')).not.toBeInTheDocument());
  });

  it('hiç HOLDINGS portföyü yoksa "Varlık portföyü bulunamadı" mesajı gösterilir', async () => {
    getMyPortfolios.mockResolvedValue([
      { id: 'wl-x', name: 'Başka İzleme', currency: 'USD', portfolioType: 'WATCHLIST' },
    ]);
    renderDetail();
    await screen.findByText('THYAO');

    fireEvent.click(within(screen.getByTestId('row-1')).getByText('addpf-1'));
    expect(
      await screen.findByText('Varlık portföyü bulunamadı. Önce bir HOLDINGS portföyü oluşturun.'),
    ).toBeInTheDocument();
  });

  it('hedef modalı X ile kapatılınca navigate çağrılmaz', async () => {
    renderDetail();
    await screen.findByText('THYAO');

    fireEvent.click(within(screen.getByTestId('row-1')).getByText('addpf-1'));
    await screen.findByText('Varlık Portföyü Seç');

    // Modal başlığı satırındaki kapatma butonu (X) — başlığa en yakın button.
    const heading = screen.getByText('Varlık Portföyü Seç');
    const closeBtn = heading.closest('div').parentElement.querySelector('button');
    fireEvent.click(closeBtn);

    await waitFor(() => expect(screen.queryByText('Varlık Portföyü Seç')).not.toBeInTheDocument());
    expect(navigateMock).not.toHaveBeenCalled();
  });
});
