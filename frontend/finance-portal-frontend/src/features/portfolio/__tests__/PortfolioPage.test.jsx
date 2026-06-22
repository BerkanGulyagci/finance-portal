import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';

// ── Mock'lar ─────────────────────────────────────────────────────────────────
// PortfolioPage; api (portfolioApi/marketApi), recharts tabanlı donut, alarm
// yöneticisi ve modalları çeker. jsdom'da canvas/ağ/recharts-boyut yoktur →
// veri kaynaklarını ve ağır alt komponentleri stub'larız; böylece test SADECE
// PortfolioPage'in kendi mantığını (özet kartları, kart grid, sil/düzenle,
// değer-gizle, yükleniyor/hata/boş dalları) doğrular.

const getMyPortfolios = vi.fn();
const deletePortfolio = vi.fn();
vi.mock('../../../api/portfolioApi', () => ({
  getMyPortfolios: (...a) => getMyPortfolios(...a),
  deletePortfolio: (...a) => deletePortfolio(...a),
}));

const getFxTcmb = vi.fn();
vi.mock('../../../api/marketApi', () => ({
  getFxTcmb: (...a) => getFxTcmb(...a),
}));

// Alt modal/komponentler: yalnızca tanınabilir bir işaret + props köprüsü.
vi.mock('../components/CreatePortfolioModal', () => ({
  default: ({ onClose, onCreated }) => (
    <div data-testid="create-modal">
      <button onClick={onClose}>close-create</button>
      <button onClick={() => onCreated([{ id: 999, name: 'Yeni', portfolioType: 'HOLDINGS', currency: 'TRY' }])}>
        do-create
      </button>
    </div>
  ),
}));
vi.mock('../components/EditPortfolioModal', () => ({
  default: ({ portfolio, onClose, onUpdated }) => (
    <div data-testid="edit-modal">
      <span>edit:{portfolio?.name}</span>
      <button onClick={onClose}>close-edit</button>
      <button onClick={() => onUpdated({ id: portfolio.id, name: 'Güncellenmiş Ad' })}>do-update</button>
    </div>
  ),
}));
vi.mock('../components/analytics/PortfolioOverviewDonut', () => ({
  default: ({ totalLabel, data }) => (
    <div data-testid="donut" data-label={totalLabel}>
      donut:{(data ?? []).length}
    </div>
  ),
}));
vi.mock('../../../components/instrument/AlarmsManager', () => ({
  default: () => <div data-testid="alarms-manager">alarms</div>,
}));

import PortfolioPage, { computeSummary } from '../PortfolioPage';
import { LanguageProvider } from '../../../context/LanguageContext';

// Varsayılan dil "tr" → t(key) anahtarın kendisini döndürür; {count} gibi
// değişkenler LanguageContext tarafından gerçekten enterpole edilir.
function renderPage() {
  return render(
    <MemoryRouter>
      <LanguageProvider>
        <PortfolioPage />
      </LanguageProvider>
    </MemoryRouter>
  );
}

// Tipik bir holdings portföyü (özet/kart hesapları için yeterli alanlar).
function holdingsPortfolio(over = {}) {
  return {
    id: 1,
    name: 'Ana Portföy',
    description: 'açıklama',
    portfolioType: 'HOLDINGS',
    currency: 'TRY',
    totalMarketValue: 1500,
    totalCost: 1000,
    totalProfitLoss: 500,
    holdings: [
      { assetType: 'STOCK', currency: 'TRY', totalQuantity: 10, change: 2, marketValue: 1500 },
    ],
    transactions: [{ id: 11 }, { id: 12 }],
    ...over,
  };
}

function watchlistPortfolio(over = {}) {
  return {
    id: 2,
    name: 'İzleme',
    portfolioType: 'WATCHLIST',
    currency: 'TRY',
    watchlistItemCount: 3,
    ...over,
  };
}

beforeEach(() => {
  vi.clearAllMocks();
  localStorage.clear();
  // Varsayılan: boş liste → çoğu test gerektiğinde override eder.
  getMyPortfolios.mockResolvedValue([]);
  getFxTcmb.mockResolvedValue({ rates: [{ symbol: 'USD', sell: 30, unit: 1 }, { symbol: 'EUR', sell: 35, unit: 1 }] });
  // window.confirm / alert jsdom'da var ama testte deterministik olsun.
  vi.spyOn(window, 'confirm').mockReturnValue(true);
  vi.spyOn(window, 'alert').mockImplementation(() => {});
});

describe('PortfolioPage (jsdom + testing-library + React 19)', () => {
  it('yükleme sırasında skeleton gösterir, başlık her zaman görünür', async () => {
    // getMyPortfolios çözülmeden loading dalını yakala.
    let resolve;
    getMyPortfolios.mockReturnValue(new Promise(r => { resolve = r; }));
    const { container } = renderPage();

    expect(screen.getByRole('heading', { name: 'Portföylerim' })).toBeInTheDocument();
    // Loading sırasında skeleton placeholder (animate-pulse).
    expect(container.querySelector('.animate-pulse')).toBeInTheDocument();

    resolve([]);
    await waitFor(() => expect(container.querySelector('.animate-pulse')).not.toBeInTheDocument());
  });

  it('boş liste dönünce boş-durum mesajını ve "Portföy Oluştur" butonunu gösterir', async () => {
    getMyPortfolios.mockResolvedValue([]);
    renderPage();

    expect(await screen.findByText('Henüz portföy yok')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Portföy Oluştur' })).toBeInTheDocument();
    // Boşta donut/metrik kartı olmamalı.
    expect(screen.queryByTestId('donut')).not.toBeInTheDocument();
  });

  it('ağ hatası (err.response yok) → "Sunucuya ulaşılamıyor." gösterir', async () => {
    getMyPortfolios.mockRejectedValue(new Error('network down'));
    renderPage();

    expect(await screen.findByText('Sunucuya ulaşılamıyor.')).toBeInTheDocument();
    expect(screen.queryByText('Henüz portföy yok')).not.toBeInTheDocument();
  });

  it('401 yanıtı → "Giriş yapmanız gerekiyor." gösterir', async () => {
    getMyPortfolios.mockRejectedValue({ response: { status: 401 } });
    renderPage();
    expect(await screen.findByText('Giriş yapmanız gerekiyor.')).toBeInTheDocument();
  });

  it('500 yanıtı → status enterpole edilmiş hata mesajı gösterir', async () => {
    getMyPortfolios.mockRejectedValue({ response: { status: 500 } });
    renderPage();
    expect(await screen.findByText('Portföyler yüklenemedi (500).')).toBeInTheDocument();
  });

  it('dolu liste → kart başlığı, maliyet/piyasa değeri ve metrik kartlarını render eder', async () => {
    getMyPortfolios.mockResolvedValue([holdingsPortfolio()]);
    renderPage();

    // Kart başlığı (heading) görünür.
    expect(await screen.findByRole('heading', { name: 'Ana Portföy' })).toBeInTheDocument();
    expect(screen.getByText('açıklama')).toBeInTheDocument();

    // HOLDINGS kart içeriği: maliyet (1000) tr-TR → "1.000,00" (yalnız kartta),
    // değer (1500) → "1.500,00" (hem kart hem "Toplam Değer" metriğinde → getAllByText).
    expect(screen.getByText('1.000,00')).toBeInTheDocument();
    expect(screen.getAllByText('1.500,00').length).toBeGreaterThan(0);

    // Özet metrik kartları başlıkları.
    expect(screen.getByText('Toplam Değer')).toBeInTheDocument();
    expect(screen.getByText('Toplam K/Z')).toBeInTheDocument();
    // İki donut (portföy + varlık türü dağılımı).
    expect(screen.getAllByTestId('donut').length).toBe(2);
    // Alarm yöneticisi her zaman altta.
    expect(screen.getByTestId('alarms-manager')).toBeInTheDocument();
  });

  it('WATCHLIST portföyü için "sembol takip ediliyor" metnini gösterir', async () => {
    getMyPortfolios.mockResolvedValue([watchlistPortfolio({ watchlistItemCount: 7 })]);
    renderPage();

    await screen.findByRole('heading', { name: 'İzleme' });
    expect(screen.getByText('sembol takip ediliyor')).toBeInTheDocument();
    expect(screen.getByText('7')).toBeInTheDocument();
    // İzleme listesi varlık portföyü olmadığından özet kartları (Toplam Değer) çıkmaz.
    expect(screen.queryByText('Toplam Değer')).not.toBeInTheDocument();
  });

  it('"Yeni Portföy" → modal açılır; onCreated listeyi günceller', async () => {
    getMyPortfolios.mockResolvedValue([]);
    renderPage();
    await screen.findByText('Henüz portföy yok');

    fireEvent.click(screen.getByRole('button', { name: 'Yeni Portföy' }));
    expect(screen.getByTestId('create-modal')).toBeInTheDocument();

    // Modal "do-create" → onCreated ile yeni portföy listeye girer, modal kapanır.
    fireEvent.click(screen.getByText('do-create'));
    expect(await screen.findByRole('heading', { name: 'Yeni' })).toBeInTheDocument();
    expect(screen.queryByTestId('create-modal')).not.toBeInTheDocument();
  });

  it('kalem kalem silme: onay → deletePortfolio çağrılır ve kart listeden kalkar', async () => {
    getMyPortfolios.mockResolvedValue([holdingsPortfolio()]);
    deletePortfolio.mockResolvedValue({});
    renderPage();
    await screen.findByRole('heading', { name: 'Ana Portföy' });

    // Kartın içindeki "Portföyü sil" başlıklı buton.
    const delBtn = screen.getByTitle('Portföyü sil');
    fireEvent.click(delBtn);

    expect(window.confirm).toHaveBeenCalled();
    await waitFor(() => expect(deletePortfolio).toHaveBeenCalledWith(1));
    await waitFor(() =>
      expect(screen.queryByRole('heading', { name: 'Ana Portföy' })).not.toBeInTheDocument()
    );
  });

  it('silme onayı reddedilirse deletePortfolio hiç çağrılmaz', async () => {
    window.confirm.mockReturnValue(false);
    getMyPortfolios.mockResolvedValue([holdingsPortfolio()]);
    renderPage();
    await screen.findByRole('heading', { name: 'Ana Portföy' });

    fireEvent.click(screen.getByTitle('Portföyü sil'));
    expect(window.confirm).toHaveBeenCalled();
    expect(deletePortfolio).not.toHaveBeenCalled();
    // Kart hâlâ duruyor.
    expect(screen.getByRole('heading', { name: 'Ana Portföy' })).toBeInTheDocument();
  });

  it('düzenle → EditPortfolioModal açılır; onUpdated kart başlığını günceller', async () => {
    getMyPortfolios.mockResolvedValue([holdingsPortfolio()]);
    renderPage();
    await screen.findByRole('heading', { name: 'Ana Portföy' });

    fireEvent.click(screen.getByTitle('Portföyü düzenle'));
    const modal = await screen.findByTestId('edit-modal');
    expect(within(modal).getByText('edit:Ana Portföy')).toBeInTheDocument();

    // do-update → onUpdated, kart başlığı yeni isimle değişir.
    fireEvent.click(within(modal).getByText('do-update'));
    expect(await screen.findByRole('heading', { name: 'Güncellenmiş Ad' })).toBeInTheDocument();
  });

  it('değerleri gizle butonu maskeleme yapar (•••••) ve geri açar', async () => {
    getMyPortfolios.mockResolvedValue([holdingsPortfolio()]);
    renderPage();
    await screen.findByRole('heading', { name: 'Ana Portföy' });

    // Başta maliyet rakamı görünür.
    expect(screen.getByText('1.000,00')).toBeInTheDocument();

    // "Değerleri gizle" (aria-label) → maskelenir.
    fireEvent.click(screen.getByLabelText('Değerleri gizle'));
    await waitFor(() => expect(screen.queryByText('1.000,00')).not.toBeInTheDocument());
    expect(screen.getAllByText('•••••').length).toBeGreaterThan(0);

    // Tekrar tıkla → değerler geri gelir.
    fireEvent.click(screen.getByLabelText('Değerleri göster'));
    expect(await screen.findByText('1.000,00')).toBeInTheDocument();
  });

  it('portföy sayısı alt başlıkta gösterilir ({count} enterpolasyonu)', async () => {
    getMyPortfolios.mockResolvedValue([holdingsPortfolio(), watchlistPortfolio()]);
    renderPage();
    await screen.findByRole('heading', { name: 'Ana Portföy' });
    // Başlık altındaki "2 portföy" metni.
    expect(screen.getByText('2 portföy')).toBeInTheDocument();
  });
});

describe('computeSummary — Günlük K/Z ölçekleme', () => {
  const p = (holdings) => [{
    id: 1, portfolioType: 'HOLDINGS', currency: 'TRY',
    totalMarketValue: 0, totalCost: 0, totalProfitLoss: 0, holdings,
  }];

  it('hisse: qty × change (değişmedi — /100 uygulanmaz)', () => {
    // STOCK 10 adet, günlük birim değişim 2 → katkı 20 (tam birim, par yok).
    const s = computeSummary(p([
      { assetType: 'STOCK', currency: 'TRY', totalQuantity: 10, change: 2 },
    ]));
    expect(s.totalDaily).toBeCloseTo(20, 4);
  });

  it('tahvil (BOND): qty × change / 100 (par-kote — 100 kat şişme düzeltildi)', () => {
    // BOND 10000 nominal, change 0.087 (100 nominal başına) → katkı 8.70, 870 değil.
    const s = computeSummary(p([
      { assetType: 'BOND', currency: 'TRY', totalQuantity: 10000, change: 0.087 },
    ]));
    expect(s.totalDaily).toBeCloseTo(8.7, 4);
  });

  it('altın bonosu: per-unit kote → /100 UYGULANMAZ (tam birim)', () => {
    const s = computeSummary(p([
      { assetType: 'BOND', category: 'GOLD_INDEXED_BOND', currency: 'TRY',
        totalQuantity: 5, change: 3 },
    ]));
    expect(s.totalDaily).toBeCloseTo(15, 4); // 5 × 3, /100 yok
  });

  it('kripto/fx: BOND değil → qty × change (dokunulmaz)', () => {
    const s = computeSummary(p([
      { assetType: 'CRYPTO', currency: 'TRY', totalQuantity: 2, change: 100 },
      { assetType: 'FX', currency: 'TRY', totalQuantity: 1000, change: 0.5 },
    ]));
    expect(s.totalDaily).toBeCloseTo(2 * 100 + 1000 * 0.5, 4); // 200 + 500 = 700
  });

  it('USD pozisyon (TRY değil): günlük K/Z dışında bırakılır', () => {
    const s = computeSummary(p([
      { assetType: 'STOCK', currency: 'USD', totalQuantity: 10, change: 2 },
    ]));
    expect(s.hasDaily).toBe(false);
  });
});
