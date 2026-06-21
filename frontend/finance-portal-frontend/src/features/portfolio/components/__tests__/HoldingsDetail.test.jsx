import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';

// ── Mock'lar ────────────────────────────────────────────────────────────────
// HoldingsDetail; ağır alt komponentleri (HoldingsTable / TransactionsTable /
// AddTransactionModal / PortfolioPerformanceChart=klinecharts / analitik bölümler /
// MarginAlertSettings) ve iki API modülünü çeker. jsdom'da canvas/ağ yoktur →
// hepsini props'larını DOM'a yazan basit stub'lara indirgeriz; böylece test
// SADECE HoldingsDetail'in kendi mantığını doğrular: özet hesapları (maliyet/
// günlük/açık/reel/gerçekleşen K/Z), değer-gizle maskesi, para-birimi döngüsü,
// sekme geçişi, VİOP'a bağlı "Teminat Uyarısı" sekmesi, pozisyon-ekle modalı ve
// işlem-silme akışı (confirm → deleteTransaction → onPortfolioUpdate).
//
// Yol notu: test dosyası src/features/portfolio/components/__tests__ içinde →
//   ../X                              → komşu komponent (HoldingsTable …)
//   ../analytics/X                    → analitik alt komponentler
//   ../../../profile/components/X     → MarginAlertSettings
//   ../../../../api/X                 → api modülleri
//   ../../../../context/LanguageContext → gerçek LanguageProvider

const deleteTransaction = vi.fn();
const getPortfolioById = vi.fn();
vi.mock('../../../../api/portfolioApi', () => ({
  deleteTransaction: (...a) => deleteTransaction(...a),
  getPortfolioById: (...a) => getPortfolioById(...a),
}));

const getCommoditySpot = vi.fn();
const getFxTcmb = vi.fn();
const getCryptos = vi.fn();
const getGoldSpot = vi.fn();
vi.mock('../../../../api/marketApi', () => ({
  getCommoditySpot: (...a) => getCommoditySpot(...a),
  getFxTcmb: (...a) => getFxTcmb(...a),
  getCryptos: (...a) => getCryptos(...a),
  getGoldSpot: (...a) => getGoldSpot(...a),
}));

// Alt komponentler: tanınabilir işaret + ihtiyaç duyulan props köprüleri.
vi.mock('../HoldingsTable', () => ({
  default: ({ holdings, valuesHidden }) => (
    <div data-testid="holdings-table" data-hidden={String(valuesHidden)}>
      holdings:{(holdings ?? []).length}
    </div>
  ),
}));
vi.mock('../TransactionsTable', () => ({
  default: ({ transactions, onDelete, deletingId }) => (
    <div data-testid="transactions-table" data-deleting={String(deletingId)}>
      tx:{(transactions ?? []).length}
      <button type="button" onClick={() => onDelete(77)}>del-tx</button>
      <button type="button" onClick={() => onDelete(null)}>del-tx-null</button>
    </div>
  ),
}));
vi.mock('../AddTransactionModal', () => ({
  default: ({ portfolioName, onClose, onAdded }) => (
    <div data-testid="add-tx-modal">
      <span data-testid="add-tx-portfolio">{portfolioName}</span>
      <button type="button" onClick={onClose}>close-add</button>
      <button type="button" onClick={() => onAdded({ id: 'pf-added' })}>do-add</button>
    </div>
  ),
}));
vi.mock('../PortfolioPerformanceChart', () => ({
  default: ({ portfolioId, valuesHidden }) => (
    <div data-testid="perf-chart" data-pid={String(portfolioId)} data-hidden={String(valuesHidden)}>
      perf-chart
    </div>
  ),
}));
vi.mock('../analytics/PortfolioAnalyticsSection', () => ({
  default: ({ holdings }) => (
    <div data-testid="analytics-section">analytics:{(holdings ?? []).length}</div>
  ),
}));
vi.mock('../analytics/PortfolioStatsSummary', () => ({
  default: ({ portfolioId }) => (
    <div data-testid="stats-summary" data-pid={String(portfolioId)}>stats</div>
  ),
}));
vi.mock('../../../profile/components/MarginAlertSettings', () => ({
  default: () => <div data-testid="margin-alert">margin-alert</div>,
}));

import HoldingsDetail from '../HoldingsDetail';
import { LanguageProvider } from '../../../../context/LanguageContext';

// Varsayılan dil "tr" → t(key) anahtarın kendisini döndürür; gerçek Provider ile sar.
function renderDetail(props = {}) {
  const defaults = {
    portfolio: holdingsPortfolio(),
    onPortfolioUpdate: vi.fn(),
    onInitialInstrumentConsumed: vi.fn(),
    onActiveTabChange: vi.fn(),
    onHoldingsSelectedKeysChange: vi.fn(),
    initialInstrument: null,
  };
  const merged = { ...defaults, ...props };
  const utils = render(
    <LanguageProvider>
      <HoldingsDetail {...merged} />
    </LanguageProvider>,
  );
  return { ...utils, props: merged };
}

// Tipik bir HOLDINGS portföyü — özet/günlük/reel/gerçekleşen K/Z için yeterli alanlar.
//  mv=1500, cost=1000, pnl=500 (+%50); günlük: STOCK qty=10 × change=2 = +20;
//  realized: 30; reel: +40.
function holdingsPortfolio(over = {}) {
  return {
    id: 'pf-1',
    name: 'Ana Portföy',
    currency: 'TRY',
    totalMarketValue: 1500,
    totalCost: 1000,
    totalProfitLoss: 500,
    totalRealProfitLoss: 40,
    totalRealProfitLossPercent: 4,
    holdings: [
      {
        assetType: 'STOCK',
        symbol: 'THYAO.IS',
        totalQuantity: 10,
        change: 2,
        marketValue: 1500,
        realizedGainLoss: 30,
      },
    ],
    transactions: [{ id: 11 }, { id: 12 }, { id: 13 }],
    ...over,
  };
}

beforeEach(() => {
  vi.clearAllMocks();
  localStorage.clear();
  getFxTcmb.mockResolvedValue({ rates: [{ symbol: 'USD', sell: 30, unit: 1 }, { symbol: 'EUR', sell: 35, unit: 1 }] });
  getCryptos.mockResolvedValue([{ symbol: 'BTC', currentPrice: 3_000_000 }]);
  getGoldSpot.mockResolvedValue({ gramGoldTry: 2500 });
  getCommoditySpot.mockResolvedValue(null);
  // confirm/alert jsdom'da var ama testte deterministik olsun.
  vi.spyOn(window, 'confirm').mockReturnValue(true);
  vi.spyOn(window, 'alert').mockImplementation(() => {});
});

describe('HoldingsDetail — özet kartı ve render (smoke)', () => {
  it('throw etmeden render eder; özet başlığı + ana sekme içeriği görünür', () => {
    renderDetail();
    expect(screen.getByRole('heading', { name: 'Portföy Özeti' })).toBeInTheDocument();
    expect(screen.getByText('Güncel Portföy Değeri')).toBeInTheDocument();
    // Varsayılan aktif sekme "holdings" → HoldingsTable görünür.
    expect(screen.getByTestId('holdings-table')).toHaveTextContent('holdings:1');
    // Sağdaki performans grafiği her zaman özet kartında.
    expect(screen.getByTestId('perf-chart')).toHaveAttribute('data-pid', 'pf-1');
  });

  it('güncel değer (TL), maliyet ve açık K/Z rakamlarını tr-TR formatında gösterir', () => {
    renderDetail();
    // formatMoney → formatTrNumber(n,2) (minimumFractionDigits:0) → tam sayılar
    // ondalıksız. mv=1500 → "1.500 TRY" (TL rozeti), maliyet=1000 → "1.000 TRY".
    expect(screen.getByText('1.500 TRY')).toBeInTheDocument();
    expect(screen.getByText('TL')).toBeInTheDocument();
    expect(screen.getByText('1.000 TRY')).toBeInTheDocument(); // cost satırı (önek yok)
    // pnl=500 → "+500 TRY" (pozitif → "+"); pnlPct=%50 → "+50,00%" (yüzde 2 ondalık).
    expect(screen.getByText('+500 TRY')).toBeInTheDocument();
    expect(screen.getByText('+50,00%')).toBeInTheDocument();
  });

  it('günlük K/Z pozisyonlardan hesaplanır (STOCK qty×change = +20)', () => {
    renderDetail();
    // dailyPnl = 10 × 2 = 20 (pozitif → "+20 TRY").
    expect(screen.getByText('+20 TRY')).toBeInTheDocument();
  });

  it('gerçekleşen K/Z holdings.realizedGainLoss toplamından gelir (30)', () => {
    renderDetail();
    // realizedPnl = 30 (pozitif, "+" öneki YOK — formatMoney signed değil).
    expect(screen.getByText('30 TRY')).toBeInTheDocument();
  });

  it('Reel K/Z satırı yalnız totalRealProfitLoss != null ise render edilir', () => {
    const { unmount } = renderDetail();
    expect(screen.getByText('Reel K/Z')).toBeInTheDocument();
    unmount();

    // totalRealProfitLoss yoksa Reel K/Z satırı çıkmamalı.
    renderDetail({ portfolio: holdingsPortfolio({ totalRealProfitLoss: null, totalRealProfitLossPercent: null }) });
    expect(screen.queryByText('Reel K/Z')).not.toBeInTheDocument();
  });

  it('tüm özet satır etiketleri (Maliyet / Günlük / Gerçekleşmemiş / Gerçekleşmiş K/Z) görünür', () => {
    renderDetail();
    expect(screen.getByText('Toplam Maliyet')).toBeInTheDocument();
    expect(screen.getByText('Günlük K/Z')).toBeInTheDocument();
    // Grafik solu etiketleri tablo sütun terimleriyle birleştirildi (Açık→Gerçekleşmemiş, Gerçekleşen→Gerçekleşmiş)
    expect(screen.getByText('Gerçekleşmemiş K/Z')).toBeInTheDocument();
    expect(screen.getByText('Gerçekleşmiş K/Z')).toBeInTheDocument();
  });
});

describe('HoldingsDetail — değerleri gizle/göster maskesi', () => {
  it('"Değerleri gizle" → rakamlar •••••; tekrar tıkla geri gelir', async () => {
    renderDetail();
    expect(screen.getByText('1.500 TRY')).toBeInTheDocument();

    fireEvent.click(screen.getByLabelText('Değerleri gizle'));
    await waitFor(() => expect(screen.queryByText('1.500 TRY')).not.toBeInTheDocument());
    expect(screen.getAllByText('•••••').length).toBeGreaterThan(0);
    // Maske alt komponentlere de geçer.
    expect(screen.getByTestId('holdings-table')).toHaveAttribute('data-hidden', 'true');

    fireEvent.click(screen.getByLabelText('Değerleri göster'));
    expect(await screen.findByText('1.500 TRY')).toBeInTheDocument();
  });
});

describe('HoldingsDetail — para birimi döngüsü', () => {
  it('değer tıklanınca TL → USD geçer; kur servisleri çağrılır, değer $\'a çevrilir', async () => {
    renderDetail();
    // Başta TL rozeti.
    expect(screen.getByText('TL')).toBeInTheDocument();

    // Değer butonuna tıkla → loadRates() + cycleCurrency().
    fireEvent.click(screen.getByText('1.500 TRY'));

    // Kur kaynakları çağrıldı.
    await waitFor(() => expect(getFxTcmb).toHaveBeenCalled());
    expect(getCryptos).toHaveBeenCalled();
    expect(getGoldSpot).toHaveBeenCalled();

    // USD rozeti + dolar değeri (1500 / 30 = 50 → "$50").
    expect(await screen.findByText('USD')).toBeInTheDocument();
    expect(screen.getByText('$50')).toBeInTheDocument();
  });
});

describe('HoldingsDetail — sekmeler', () => {
  it('İşlemler sekmesi TransactionsTable\'ı, Grafikler analitik bölümü, Fırsat Maliyeti stats\'ı gösterir', () => {
    renderDetail();
    // holdings (varsayılan) açık.
    expect(screen.getByTestId('holdings-table')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'İşlemler' }));
    expect(screen.getByTestId('transactions-table')).toHaveTextContent('tx:3');
    expect(screen.queryByTestId('holdings-table')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Grafikler' }));
    expect(screen.getByTestId('analytics-section')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Fırsat Maliyeti' }));
    expect(screen.getByTestId('stats-summary')).toHaveAttribute('data-pid', 'pf-1');
  });

  it('sekme değişimi onActiveTabChange ile parent\'a bildirilir', async () => {
    const onActiveTabChange = vi.fn();
    renderDetail({ onActiveTabChange });
    // Mount effect'i ilk aktif sekmeyi ("holdings") bildirir.
    await waitFor(() => expect(onActiveTabChange).toHaveBeenCalledWith('holdings'));

    fireEvent.click(screen.getByRole('button', { name: 'İşlemler' }));
    expect(onActiveTabChange).toHaveBeenCalledWith('transactions');
  });

  it('VİOP pozisyonu YOKSA "Teminat Uyarısı" sekmesi listede olmaz', () => {
    renderDetail();
    expect(screen.queryByRole('button', { name: 'Teminat Uyarısı' })).not.toBeInTheDocument();
  });

  it('VİOP (FUTURE) pozisyonu VARSA "Teminat Uyarısı" sekmesi eklenir ve MarginAlertSettings render eder', () => {
    renderDetail({
      portfolio: holdingsPortfolio({
        holdings: [{ assetType: 'FUTURE', symbol: 'F_XU0300625', totalQuantity: 1, marketValue: 1000 }],
      }),
    });
    const marginTab = screen.getByRole('button', { name: 'Teminat Uyarısı' });
    expect(marginTab).toBeInTheDocument();

    fireEvent.click(marginTab);
    expect(screen.getByTestId('margin-alert')).toBeInTheDocument();
    expect(
      screen.getByText('Bu eşik tüm portföylerinizdeki tüm VİOP pozisyonları için geçerlidir.'),
    ).toBeInTheDocument();
  });
});

describe('HoldingsDetail — pozisyon ekleme modalı', () => {
  it('"Pozisyon Ekle" → AddTransactionModal açılır; portföy adını taşır', () => {
    renderDetail();
    expect(screen.queryByTestId('add-tx-modal')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: /Pozisyon Ekle/ }));
    expect(screen.getByTestId('add-tx-modal')).toBeInTheDocument();
    expect(screen.getByTestId('add-tx-portfolio')).toHaveTextContent('Ana Portföy');
  });

  it('modal "do-add" → onPortfolioUpdate(updated) çağrılır ve modal kapanır', async () => {
    const { props } = renderDetail();
    fireEvent.click(screen.getByRole('button', { name: /Pozisyon Ekle/ }));

    fireEvent.click(screen.getByText('do-add'));
    expect(props.onPortfolioUpdate).toHaveBeenCalledWith({ id: 'pf-added' });
    await waitFor(() => expect(screen.queryByTestId('add-tx-modal')).not.toBeInTheDocument());
  });

  it('initialInstrument verilince modal otomatik açılır + onInitialInstrumentConsumed çağrılır', () => {
    const onInitialInstrumentConsumed = vi.fn();
    renderDetail({
      initialInstrument: { symbol: 'THYAO.IS', assetType: 'STOCK', name: 'THY' },
      onInitialInstrumentConsumed,
    });
    expect(screen.getByTestId('add-tx-modal')).toBeInTheDocument();
    expect(onInitialInstrumentConsumed).toHaveBeenCalledTimes(1);
  });
});

describe('HoldingsDetail — işlem silme akışı', () => {
  it('onay → deleteTransaction(portfolioId, txId) çağrılır, sonuç onPortfolioUpdate\'e geçer', async () => {
    deleteTransaction.mockResolvedValue({ id: 'pf-1', updated: true });
    const { props } = renderDetail();

    fireEvent.click(screen.getByRole('button', { name: 'İşlemler' }));
    const table = screen.getByTestId('transactions-table');
    fireEvent.click(within(table).getByText('del-tx'));

    expect(window.confirm).toHaveBeenCalled();
    await waitFor(() => expect(deleteTransaction).toHaveBeenCalledWith('pf-1', 77));
    await waitFor(() => expect(props.onPortfolioUpdate).toHaveBeenCalledWith({ id: 'pf-1', updated: true }));
  });

  it('onay reddedilirse deleteTransaction hiç çağrılmaz', () => {
    window.confirm.mockReturnValue(false);
    renderDetail();

    fireEvent.click(screen.getByRole('button', { name: 'İşlemler' }));
    fireEvent.click(within(screen.getByTestId('transactions-table')).getByText('del-tx'));

    expect(window.confirm).toHaveBeenCalled();
    expect(deleteTransaction).not.toHaveBeenCalled();
  });

  it('txId yoksa uyarı (alert) gösterir, confirm/deleteTransaction çağrılmaz', () => {
    renderDetail();
    fireEvent.click(screen.getByRole('button', { name: 'İşlemler' }));
    fireEvent.click(within(screen.getByTestId('transactions-table')).getByText('del-tx-null'));

    expect(window.alert).toHaveBeenCalled();
    expect(window.confirm).not.toHaveBeenCalled();
    expect(deleteTransaction).not.toHaveBeenCalled();
  });

  it('deleteTransaction reddedince hata alert\'i gösterilir', async () => {
    deleteTransaction.mockRejectedValue(new Error('boom'));
    renderDetail();

    fireEvent.click(screen.getByRole('button', { name: 'İşlemler' }));
    fireEvent.click(within(screen.getByTestId('transactions-table')).getByText('del-tx'));

    await waitFor(() => expect(deleteTransaction).toHaveBeenCalled());
    await waitFor(() => expect(window.alert).toHaveBeenCalled());
  });
});

describe('HoldingsDetail — boş/eksik veri dalları', () => {
  it('holdings boş + backend toplamları yoksa değer 0,00 ve günlük K/Z "-" gösterir', () => {
    renderDetail({
      portfolio: {
        id: 'pf-empty',
        name: 'Boş',
        currency: 'TRY',
        holdings: [],
        transactions: [],
      },
    });
    // mv/pnl/realized hepsi 0 → "0 TRY" (formatTrNumber 0 → "0"); birden çok kez geçer.
    expect(screen.getAllByText('0 TRY').length).toBeGreaterThan(0);
    // dailyPnl null → "-" (cost=0 → maliyet de "-"; ikisi de tire → en az bir tane var).
    expect(screen.getAllByText('-').length).toBeGreaterThan(0);
    // Holdings tablosu boş.
    expect(screen.getByTestId('holdings-table')).toHaveTextContent('holdings:0');
  });
});
