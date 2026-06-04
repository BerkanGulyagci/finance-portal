import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import WatchlistTable from '../WatchlistTable';
import { LanguageProvider } from '../../../../context/LanguageContext';

// WatchlistTable, SAF bir sunum (tablo) komponentidir. Grafik / ağ / canvas / async YOK.
// Bağımlılıkları yalnızca:
//   - react-router-dom <Link>  → DetailLink hücresi (resolveDetailPath dolu olunca anchor üretir)
//   - useTranslation (LanguageContext) → t(key) varsayılan "tr" dilinde anahtarın KENDİSİNİ döndürür
// Bu yüzden MOCK yerine GERÇEK sağlayıcılarla (MemoryRouter + LanguageProvider) sarmalıyoruz;
// böylece beklenen Türkçe etiketler (örn. "Son Fiyat", "Varlıklarıma Ekle") ekrana basılır.
//
// Sayı biçimlendirme tr-TR locale: binlik ayıracı "." , ondalık ayıracı "," (örn. 12345.67 → "12.345,67").

/** WatchlistTable'ı gerçek router + dil sağlayıcısıyla render eden yardımcı. */
function renderTable(props = {}) {
  return render(
    <MemoryRouter>
      <LanguageProvider>
        <WatchlistTable {...props} />
      </LanguageProvider>
    </MemoryRouter>
  );
}

// Örnek satırlar — alanlar komponentin GERÇEKTEN okuduğu isimlerle birebir.
const stockItem = {
  id: 1,
  symbol: 'THYAO',
  assetType: 'STOCK',
  lastPrice: 250.5,
  startPrice: 200,
  open: 245,
  high: 260,
  low: 240,
  change: 5.5,
  changePercent: 2.25,
  volume: 1234567,
  notes: 'uzun vade',
  addedAt: '2026-01-15T10:30:00',
};

const fundItem = {
  id: 10,
  symbol: 'AFA',
  assetType: 'FUND',
  fundName: 'Ak Portföy Hisse Fonu',
  lastPrice: 12.3456,
  startPrice: 10,
  changePercent: 1.5,
  fundReturnOneMonth: 3.2,
  fundReturnThreeMonths: -2.1,
  fundReturnYtd: 8.4,
  fundReturnOneYear: 25.7,
  fundRiskLevel: 5,
  addedAt: '2026-02-01T09:00:00',
};

const fxItem = {
  id: 20,
  symbol: 'USDTRY',
  assetType: 'FX',
  buy: 32.1,
  sell: 32.3,
  startPrice: 30,
  notes: 'dolar takip',
  addedAt: '2026-03-01T08:00:00',
};

const bondItem = {
  id: 30,
  symbol: 'TRT080126',
  assetType: 'BOND',
  lastPrice: 98.5,
  startPrice: 95,
  change: 1.25,
  changePercent: 1.28,
  remainingDays: 365,
  couponRate: 12.5,
  notes: 'tahvil',
  addedAt: '2026-04-01T07:00:00',
};

describe('WatchlistTable', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
  });

  describe('boş durum (empty state)', () => {
    it('items boş dizi iken tablo değil, "izleme listesi boş" mesajı basar', () => {
      renderTable({ items: [] });
      expect(
        screen.getByText(/İzleme listesi boş\. "Sembol Ekle" butonuna basarak başlayın\./)
      ).toBeInTheDocument();
      // tablo (ve dolayısıyla satırlar) render EDİLMEZ
      expect(screen.queryByRole('table')).not.toBeInTheDocument();
    });

    it('items hiç verilmezse (varsayılan []) yine boş mesajı basar, throw ETMEZ', () => {
      renderTable();
      expect(screen.getByText(/İzleme listesi boş/)).toBeInTheDocument();
    });
  });

  describe('default variant (hisse/genel)', () => {
    it('dolu veriyle smoke: tablo ve default başlıklar render edilir', () => {
      renderTable({ items: [stockItem] });
      expect(screen.getByRole('table')).toBeInTheDocument();
      // default varyant başlıkları
      expect(screen.getByText('Son Fiyat')).toBeInTheDocument();
      expect(screen.getByText('Açılış')).toBeInTheDocument();
      expect(screen.getByText('Yüksek / Düşük')).toBeInTheDocument();
      expect(screen.getByText('Hacim')).toBeInTheDocument();
    });

    it('sembol, fiyatlar ve hacim tr-TR formatında basılır', () => {
      renderTable({ items: [stockItem] });
      expect(screen.getByText('THYAO')).toBeInTheDocument();
      // lastPrice 250.5 → "250,50"
      expect(screen.getByText('250,50')).toBeInTheDocument();
      // startPrice 200 → "200,00"
      expect(screen.getByText('200,00')).toBeInTheDocument();
      // Yüksek / Düşük birleşik hücre: "260,00 / 240,00"
      expect(screen.getByText(/260,00\s*\/\s*240,00/)).toBeInTheDocument();
      // volume 1234567 → tr binlik ayıracıyla "1.234.567"
      expect(screen.getByText('1.234.567')).toBeInTheDocument();
    });

    it('pozitif Fark "+" işaretiyle (signed) ve Fark % chip olarak gösterilir', () => {
      renderTable({ items: [stockItem] });
      // change 5.5 → "+5,50"
      expect(screen.getByText('+5,50')).toBeInTheDocument();
      // changePercent 2.25 → "+2,25%"
      expect(screen.getByText('+2,25%')).toBeInTheDocument();
    });

    it('not (notes) hücresi varsa metni, yoksa "-" basar', () => {
      const { rerender } = renderTable({ items: [stockItem] });
      expect(screen.getByText('uzun vade')).toBeInTheDocument();

      rerender(
        <MemoryRouter>
          <LanguageProvider>
            <WatchlistTable items={[{ ...stockItem, notes: undefined }]} />
          </LanguageProvider>
        </MemoryRouter>
      );
      // notes yok → noteCell "-" (eski "uzun vade" metni kalkar)
      expect(screen.queryByText('uzun vade')).not.toBeInTheDocument();
      const row = screen.getByText('THYAO').closest('tr');
      expect(within(row).getAllByText('-').length).toBeGreaterThan(0);
    });

    it('birden çok satır verilince her biri için bir <tr> render edilir', () => {
      renderTable({
        items: [stockItem, { ...stockItem, id: 2, symbol: 'GARAN' }],
      });
      expect(screen.getByText('THYAO')).toBeInTheDocument();
      expect(screen.getByText('GARAN')).toBeInTheDocument();
      // thead(1) + tbody(2) = 3 satır
      expect(screen.getAllByRole('row')).toHaveLength(3);
    });
  });

  describe('null/undefined ve geçersiz sayı biçimlendirme', () => {
    it('eksik/null sayısal alanlar "-" olarak gösterilir (throw etmez)', () => {
      renderTable({
        items: [
          {
            id: 99,
            symbol: 'EMPTY',
            assetType: 'STOCK',
            lastPrice: null,
            startPrice: undefined,
            change: null,
            changePercent: null,
            volume: null,
          },
        ],
      });
      expect(screen.getByText('EMPTY')).toBeInTheDocument();
      // hiçbiri çökmeden "-" üretir; en az bir tane DOM'da olmalı
      expect(screen.getAllByText('-').length).toBeGreaterThan(0);
    });
  });

  describe('fund variant', () => {
    it('fon başlıkları (Fon Kodu / Fon Adı / Risk) ve getiri yüzdeleri basılır', () => {
      renderTable({ variant: 'fund', items: [fundItem] });
      expect(screen.getByText('Fon Kodu')).toBeInTheDocument();
      expect(screen.getByText('Fon Adı')).toBeInTheDocument();
      expect(screen.getByText('Risk')).toBeInTheDocument();
      // fon adı ve kodu
      expect(screen.getByText('Ak Portföy Hisse Fonu')).toBeInTheDocument();
      // negatif 3 aylık getiri -2.1 → "-2,10%"
      expect(screen.getByText('-2,10%')).toBeInTheDocument();
      // pozitif 1 yıllık 25.7 → "+25,70%"
      expect(screen.getByText('+25,70%')).toBeInTheDocument();
      // risk seviyesi
      expect(screen.getByText('5')).toBeInTheDocument();
    });
  });

  describe('fx variant', () => {
    it('Alış / Satış başlıkları ve değerleri basılır', () => {
      renderTable({ variant: 'fx', items: [fxItem] });
      expect(screen.getByText('Alış')).toBeInTheDocument();
      expect(screen.getByText('Satış')).toBeInTheDocument();
      // buy 32.1 → "32,10", sell 32.3 → "32,30"
      expect(screen.getByText('32,10')).toBeInTheDocument();
      expect(screen.getByText('32,30')).toBeInTheDocument();
      expect(screen.getByText('USDTRY')).toBeInTheDocument();
    });
  });

  describe('bond variant', () => {
    it('Değer / Kalan Gün / Kupon % başlıkları ve değerleri basılır', () => {
      renderTable({ variant: 'bond', items: [bondItem] });
      expect(screen.getByText('Değer')).toBeInTheDocument();
      expect(screen.getByText('Kalan Gün')).toBeInTheDocument();
      expect(screen.getByText('Kupon %')).toBeInTheDocument();
      // remainingDays 365 ve couponRate 12.5 → "12,50"
      expect(screen.getByText('365')).toBeInTheDocument();
      expect(screen.getByText('12,50')).toBeInTheDocument();
    });
  });

  describe('getDisplayName — varlık tipine göre görünen ad', () => {
    it('COMMODITY kıymetli maden sembolünü okunur ada çevirir + alt başlık', () => {
      renderTable({
        items: [{ id: 1, symbol: 'SILVER:GRAM_TRY', assetType: 'COMMODITY' }],
      });
      expect(screen.getByText('Gram Gümüş (₺)')).toBeInTheDocument();
      // ":" içeren sembol alt başlıkta " · " ile gösterilir
      expect(screen.getByText('SILVER · GRAM_TRY')).toBeInTheDocument();
    });

    it('GOLD sembolünü (GRAM) "Gram Altın" olarak gösterir', () => {
      renderTable({ items: [{ id: 1, symbol: 'GRAM', assetType: 'GOLD' }] });
      expect(screen.getByText('Gram Altın')).toBeInTheDocument();
    });

    it('CRYPTO sembolünü büyük harfe çevirir', () => {
      renderTable({ items: [{ id: 1, symbol: 'btc', assetType: 'CRYPTO' }] });
      expect(screen.getByText('BTC')).toBeInTheDocument();
    });
  });

  describe('DetailLink — resolveDetailPath davranışı', () => {
    it('resolveDetailPath bir yol döndürünce sembol <a> (link) olur', () => {
      renderTable({
        items: [stockItem],
        resolveDetailPath: (it) => `/market/stocks/${it.symbol}`,
      });
      const link = screen.getByRole('link', { name: 'THYAO' });
      expect(link).toBeInTheDocument();
      expect(link).toHaveAttribute('href', '/market/stocks/THYAO');
    });

    it('resolveDetailPath verilmezse sembol düz metin span olur, link OLMAZ', () => {
      renderTable({ items: [stockItem] });
      expect(screen.getByText('THYAO')).toBeInTheDocument();
      expect(screen.queryByRole('link')).not.toBeInTheDocument();
    });
  });

  describe('aksiyon hücresi — etkileşimler', () => {
    it('"Varlıklarıma Ekle" tıklanınca onAddToPortfolio(item, title) ile çağrılır', () => {
      const onAddToPortfolio = vi.fn();
      renderTable({ items: [stockItem], onAddToPortfolio });
      const btn = screen.getByRole('button', { name: 'Varlıklarıma Ekle' });
      fireEvent.click(btn);
      expect(onAddToPortfolio).toHaveBeenCalledTimes(1);
      // ikinci argüman getDisplayName(item).title → STOCK için sembolün kendisi
      expect(onAddToPortfolio).toHaveBeenCalledWith(stockItem, 'THYAO');
    });

    it('çöp (sil) butonu tıklanınca onDelete(item.id) ile çağrılır', () => {
      const onDelete = vi.fn();
      renderTable({ items: [stockItem], onDelete });
      // sil butonu title="Listeden çıkar"
      const delBtn = screen.getByRole('button', { name: 'Listeden çıkar' });
      fireEvent.click(delBtn);
      expect(onDelete).toHaveBeenCalledTimes(1);
      expect(onDelete).toHaveBeenCalledWith(stockItem.id);
    });

    it('deletingId satırın id\'sine eşitse sil butonu disabled olur', () => {
      const onDelete = vi.fn();
      renderTable({ items: [stockItem], onDelete, deletingId: stockItem.id });
      const delBtn = screen.getByRole('button', { name: 'Listeden çıkar' });
      expect(delBtn).toBeDisabled();
    });

    it('onAddToPortfolio/onDelete verilmezse aksiyon hücresinde buton render EDİLMEZ', () => {
      renderTable({ items: [stockItem] });
      expect(screen.queryByRole('button')).not.toBeInTheDocument();
    });

    it('her iki callback de varsa satırda iki buton olur', () => {
      renderTable({ items: [stockItem], onDelete: vi.fn(), onAddToPortfolio: vi.fn() });
      const row = screen.getByText('THYAO').closest('tr');
      expect(within(row).getAllByRole('button')).toHaveLength(2);
    });
  });
});
