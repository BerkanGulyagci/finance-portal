import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';

// ── SearchBox testleri (Vitest + @testing-library/react) ─────────────────────────
//
// SearchBox global bir arama kutusu. İlk odaklanmada (onFocus) marketApi'den 12 ayrı
// kaynağı KADEMELİ çeker (stoklar/kripto/döviz/emtia/fon/vadeli/DİBS/eurobond/endeks +
// statik altın). Sonuçları MODÜL SEVİYESİNDE biriken bir DATASET'te tutar ve açık
// kutulara abonelik (subscribe) ile haber verir. useNavigate ile detay sayfasına gider,
// son aramaları localStorage'a yazar.
//
// MOCK STRATEJİSİ:
//  - marketApi: 12 fonksiyonun TAMAMI vi.fn() ile stub'lanır (jsdom'da ağ yok).
//  - react-router-dom: gerçek kalır, sadece useNavigate paylaşılan bir spy'a bağlanır.
//  - LanguageContext: GERÇEK Provider; "tr"de t(key) → anahtarın kendisini döndürür,
//    böylece Türkçe metinleri (placeholder/başlık) doğrudan arayabiliriz.
//
// İZOLASYON (önemli): Komponent modül-seviyesi state (DATASET/started/subscribers)
// tuttuğundan, ilk loadDataset() çağrısı "started"ı kalıcı latch eder ve DATASET
// testler arasında birikir. Bunu önlemek için her testte vi.resetModules() ile TEMİZ
// bir SearchBox modülü yükleriz. resetModules mock factory'lerini de sıfırladığından,
// SearchBox'ın kullandığı marketApi örneğine ulaşmak için api'yi RESET SONRASI dinamik
// import ederiz (aynı fn örnekleri → spy'lar SearchBox'ınkilerle aynı).

const navigateSpy = vi.fn();
vi.mock('react-router-dom', async (orig) => ({
  ...(await orig()),
  useNavigate: () => navigateSpy,
}));

// marketApi — 12 named export'un hepsi mock (factory reset sonrası da yeniden çalışır).
vi.mock('../../../api/marketApi', () => ({
  getStocks: vi.fn(),
  getAllCryptoCoins: vi.fn(),
  getFxTcmb: vi.fn(),
  getCommodityList: vi.fn(),
  getAllTefasFunds: vi.fn(),
  getAllBesFunds: vi.fn(),
  getAllOksFunds: vi.fn(),
  getOsmanliFundBulletin: vi.fn(),
  getViopContracts: vi.fn(),
  getEvdsBonds: vi.fn(),
  getGlobalBonds: vi.fn(),
  getIndices: vi.fn(),
}));

// LanguageProvider, resetModules tutarlılığı için loadModule içinde dinamik import edilir.

// Tüm API fonksiyonlarını "boş" varsayılana çek (verilen taze api modülü üzerinde).
function resetApiDefaults(api) {
  api.getStocks.mockResolvedValue({ content: [], totalPages: 1, size: 200 });
  api.getAllCryptoCoins.mockResolvedValue([]);
  api.getFxTcmb.mockResolvedValue({ rates: [] });
  api.getCommodityList.mockResolvedValue([]);
  api.getAllTefasFunds.mockResolvedValue([]);
  api.getAllBesFunds.mockResolvedValue([]);
  api.getAllOksFunds.mockResolvedValue([]);
  api.getOsmanliFundBulletin.mockResolvedValue([]);
  api.getViopContracts.mockResolvedValue([]);
  api.getEvdsBonds.mockResolvedValue({ items: [], totalPages: 1 });
  api.getGlobalBonds.mockResolvedValue([]);
  api.getIndices.mockResolvedValue([]);
}

// "Dolu" tipik veri seti: popüler sembolleri (THYAO/ASELS/GARAN/BTC/ETH/USD) içerir,
// aramaların eşleşebilmesi için stok/kripto/döviz örnekleri sağlar.
function seedFullDataset(api) {
  api.getStocks.mockResolvedValue({
    content: [
      { symbol: 'THYAO', name: 'Türk Hava Yolları' },
      { symbol: 'ASELS', name: 'Aselsan' },
      { symbol: 'GARAN', name: 'Garanti Bankası' },
    ],
    totalPages: 1,
    size: 200,
  });
  api.getAllCryptoCoins.mockResolvedValue([
    { symbol: 'BTC', id: 'bitcoin', name: 'Bitcoin' },
    { symbol: 'ETH', id: 'ethereum', name: 'Ethereum' },
  ]);
  api.getFxTcmb.mockResolvedValue({ rates: [{ symbol: 'USD' }, { symbol: 'EUR' }] });
}

// resetModules + taze import → her test temiz DATASET ile SearchBox + AYNI api örneğini alır.
// ÖNEMLİ: resetModules sonrası LanguageContext de YENİDEN yüklenir; SearchBox'ın gördüğü
// context örneğiyle eşleşmesi için LanguageProvider'ı da reset SONRASI dinamik import ederiz
// (statik üst-import farklı örnek → "useTranslation must be used within LanguageProvider").
async function loadModule() {
  vi.resetModules();
  const api = await import('../../../api/marketApi');
  resetApiDefaults(api);
  const { LanguageProvider: LP } = await import('../../../context/LanguageContext');
  const mod = await import('../SearchBox');
  return { SearchBox: mod.default, api, LanguageProvider: LP };
}

// Tek adımda: temiz modül + render + arama input'u + taze api'yi döndürür.
async function setup() {
  const { SearchBox, api, LanguageProvider: LP } = await loadModule();
  const utils = render(
    <LP>
      <MemoryRouter>
        <SearchBox />
      </MemoryRouter>
    </LP>
  );
  const input = screen.getByPlaceholderText('Sitede ara...');
  return { input, api, ...utils };
}

beforeEach(() => {
  vi.clearAllMocks();
  localStorage.clear();
});

describe('SearchBox (Vitest + @testing-library/react)', () => {
  describe('smoke / temel render', () => {
    it("mock'larla hata fırlatmadan render eder ve arama input'unu gösterir", async () => {
      const { input } = await setup();
      expect(input).toBeInTheDocument();
      expect(input).toHaveAttribute('type', 'text');
    });

    it('başlangıçta açılır panel kapalıdır (odak yokken)', async () => {
      await setup();
      expect(screen.queryByText('Popüler Aramalar')).not.toBeInTheDocument();
      expect(screen.queryByText('Son Aramalarım')).not.toBeInTheDocument();
    });

    it('odaklanmadan önce hiçbir marketApi çağrısı yapılmaz (tembel yükleme)', async () => {
      const { api } = await setup();
      expect(api.getStocks).not.toHaveBeenCalled();
      expect(api.getAllCryptoCoins).not.toHaveBeenCalled();
      expect(api.getIndices).not.toHaveBeenCalled();
    });
  });

  describe('odaklanma → veri yükleme (kademeli)', () => {
    it('odaklanınca panel açılır ve marketApi kaynakları çağrılır', async () => {
      const { input, api } = await setup();

      fireEvent.focus(input);

      // Panel açıldı: sorgu yokken "Popüler Aramalar" başlığı görünür
      expect(screen.getByText('Popüler Aramalar')).toBeInTheDocument();
      // Kademeli yükleme tetiklendi
      expect(api.getStocks).toHaveBeenCalledTimes(1);
      expect(api.getAllCryptoCoins).toHaveBeenCalledTimes(1);
      expect(api.getFxTcmb).toHaveBeenCalledTimes(1);
      expect(api.getIndices).toHaveBeenCalledTimes(1);
      expect(api.getGlobalBonds).toHaveBeenCalledTimes(1);
    });

    it('veri henüz gelmemişken popüler bölümünde "Yükleniyor..." gösterir', async () => {
      const { input, api } = await setup();
      // getStocks çözülmesini geciktir → popüler liste (THYAO/...) boş kalır
      let resolveStocks;
      api.getStocks.mockReturnValue(new Promise((res) => { resolveStocks = res; }));

      fireEvent.focus(input);

      // Statik altın hemen eklenir ama popüler semboller henüz yok → "Yükleniyor..."
      expect(screen.getByText('Popüler Aramalar')).toBeInTheDocument();
      expect(screen.getByText('Yükleniyor...')).toBeInTheDocument();

      // Açık promise sızıntısı bırakma
      resolveStocks?.({ content: [], totalPages: 1, size: 200 });
    });

    it('veri gelince popüler semboller (THYAO/BTC/USD) listelenir', async () => {
      const { input, api } = await setup();
      seedFullDataset(api);

      fireEvent.focus(input);

      // async kaynaklar çözülünce buildPopular eşleşen sembolleri gösterir
      expect(await screen.findByText('THYAO')).toBeInTheDocument();
      expect(screen.getByText('BTC')).toBeInTheDocument();
      expect(screen.getByText('USD')).toBeInTheDocument();
      expect(screen.queryByText('Yükleniyor...')).not.toBeInTheDocument();
    });
  });

  describe('arama (filtreleme)', () => {
    it('sembol başına göre yazınca eşleşen sonuçları gösterir (THY → THYAO)', async () => {
      const { input, api } = await setup();
      seedFullDataset(api);
      fireEvent.focus(input);
      await screen.findByText('THYAO'); // dataset hazır

      fireEvent.change(input, { target: { value: 'THY' } });

      expect(await screen.findByText('THYAO')).toBeInTheDocument();
      expect(screen.getByText('Türk Hava Yolları')).toBeInTheDocument();
      // Eşleşmeyen başka stok listede olmamalı
      expect(screen.queryByText('Aselsan')).not.toBeInTheDocument();
    });

    it('isim başına göre yazınca eşleşir (bitcoin → BTC / Bitcoin)', async () => {
      const { input, api } = await setup();
      seedFullDataset(api);
      fireEvent.focus(input);
      await screen.findByText('BTC');

      fireEvent.change(input, { target: { value: 'bitcoin' } });

      expect(await screen.findByText('Bitcoin')).toBeInTheDocument();
      expect(screen.getAllByText('BTC').length).toBeGreaterThan(0);
    });

    it('hiçbir şeyle eşleşmeyen sorguda "Sonuç bulunamadı." gösterir (dataset doluyken)', async () => {
      const { input, api } = await setup();
      seedFullDataset(api);
      fireEvent.focus(input);
      await screen.findByText('THYAO'); // dataset dolu

      fireEvent.change(input, { target: { value: 'zzzzzzzzz' } });

      expect(await screen.findByText('Sonuç bulunamadı.')).toBeInTheDocument();
      // Sorgu varken popüler/son aramalar başlıkları gösterilmez
      expect(screen.queryByText('Popüler Aramalar')).not.toBeInTheDocument();
    });
  });

  describe('navigasyon + son aramalar (etkileşim)', () => {
    it('sonuç satırına tıklayınca item.path adresine yönlendirir', async () => {
      const { input, api } = await setup();
      seedFullDataset(api);
      fireEvent.focus(input);
      await screen.findByText('THYAO');

      fireEvent.change(input, { target: { value: 'THY' } });
      const row = await screen.findByText('THYAO');

      // ResultRow onMouseDown ile çalışır (onClick→go)
      fireEvent.mouseDown(row);

      expect(navigateSpy).toHaveBeenCalledTimes(1);
      expect(navigateSpy).toHaveBeenCalledWith('/market/stocks/THYAO', undefined);
    });

    it('seçilen öğe localStorage son aramalara yazılır', async () => {
      const { input, api } = await setup();
      seedFullDataset(api);
      fireEvent.focus(input);
      await screen.findByText('THYAO');

      fireEvent.change(input, { target: { value: 'THY' } });
      fireEvent.mouseDown(await screen.findByText('THYAO'));

      const saved = JSON.parse(localStorage.getItem('site_recent_searches') || '[]');
      expect(saved.length).toBe(1);
      expect(saved[0]).toMatchObject({ type: 'STOCK', symbol: 'THYAO', path: '/market/stocks/THYAO' });
    });

    it('mevcut son aramalar varsa odaklanınca "Son Aramalarım" bölümünde gösterilir', async () => {
      localStorage.setItem('site_recent_searches', JSON.stringify([
        { type: 'STOCK', symbol: 'GARAN', name: 'Garanti Bankası', exchange: 'İstanbul', path: '/market/stocks/GARAN' },
      ]));
      const { input } = await setup();

      fireEvent.focus(input);

      expect(screen.getByText('Son Aramalarım')).toBeInTheDocument();
      expect(screen.getByText('GARAN')).toBeInTheDocument();
    });
  });

  describe('klavye gezinmesi', () => {
    it('Escape tuşu açık paneli kapatır', async () => {
      const { input } = await setup();
      fireEvent.focus(input);
      expect(screen.getByText('Popüler Aramalar')).toBeInTheDocument();

      fireEvent.keyDown(input, { key: 'Escape' });

      await waitFor(() => {
        expect(screen.queryByText('Popüler Aramalar')).not.toBeInTheDocument();
      });
    });

    it('ArrowDown + Enter ile vurgulanan sonuca yönlendirir', async () => {
      const { input, api } = await setup();
      seedFullDataset(api);
      fireEvent.focus(input);
      await screen.findByText('THYAO');

      fireEvent.change(input, { target: { value: 'a' } }); // birden çok eşleşme (Aselsan/Garanti...)
      await waitFor(() => {
        expect(screen.queryByText('Sonuç bulunamadı.')).not.toBeInTheDocument();
      });

      fireEvent.keyDown(input, { key: 'ArrowDown' });
      fireEvent.keyDown(input, { key: 'Enter' });

      expect(navigateSpy).toHaveBeenCalledTimes(1);
      const calledPath = navigateSpy.mock.calls[0][0];
      expect(String(calledPath).startsWith('/market/')).toBe(true);
    });
  });
});
