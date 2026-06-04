import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';

// ── Mock'lar ────────────────────────────────────────────────────────────────
// AddTransactionModal iki API modülünü useEffect/handleSubmit içinde çağırır.
// jsdom'da ağ yok → modülleri stub'la. Yollar TEST dosyasının konumuna görelidir
// (src/features/portfolio/components/__tests__ → src/api : ../../../../api).

vi.mock('../../../../api/portfolioApi', () => ({
  addTransaction: vi.fn(),
  // STOCK + bugün + spot fiyat>0 olduğunda bu effect erken çıkar; yine de tanımlı olsun.
  getPriceAtDate: vi.fn(() => Promise.resolve({ found: false, price: null })),
}));

vi.mock('../../../../api/marketApi', () => ({
  // VİOP/tahvil "ilk işlem günü / ihraç tarihi" effect'leri — STOCK için tetiklenmez
  // ama import edildikleri için stub gerekir.
  getViopChart: vi.fn(() => Promise.resolve({ data: [] })),
  getViopContractSpec: vi.fn(() => Promise.resolve(null)),
  getEvdsBondDetail: vi.fn(() => Promise.resolve(null)),
  getGlobalBondDetail: vi.fn(() => Promise.resolve(null)),
}));

// InstrumentSearchModal gerçek hâli useInstrumentSearch hook'u üzerinden lib/http ile
// ağ isteği atar (jsdom'da çalışmaz). Arama adımını basit bir stub ile temsil et:
//  - portfolioName + initialType prop'larını DOM'a yazar (geçirildiğini doğrulamak için),
//  - "mock-select" butonu onSelect'i sahte bir enstrümanla çağırır (forma geçiş testi),
//  - "mock-close" butonu onClose'u çağırır.
vi.mock('../../../../components/instrument/InstrumentSearchModal', () => ({
  default: ({ portfolioName, onSelect, onClose, initialType }) => (
    <div data-testid="search-modal">
      <span data-testid="search-portfolio-name">{portfolioName}</span>
      <span data-testid="search-initial-type">{initialType}</span>
      <button
        type="button"
        onClick={() => onSelect({ symbol: 'THYAO.IS', assetType: 'STOCK', name: 'Türk Hava Yolları', price: 250 })}
      >
        mock-select
      </button>
      <button type="button" onClick={onClose}>mock-close</button>
    </div>
  ),
}));

import { addTransaction, getPriceAtDate } from '../../../../api/portfolioApi';
import AddTransactionModal from '../AddTransactionModal';
import { LanguageProvider } from '../../../../context/LanguageContext';

// Varsayılan dil "tr" → t(key) anahtarın kendisini döndürür; gerçek Provider ile sar.
function renderModal(props = {}) {
  const defaults = {
    portfolioId: 'pf-1',
    portfolioName: 'Test Portföyü',
    holdings: [],
    onClose: vi.fn(),
    onAdded: vi.fn(),
    initialInstrument: null,
  };
  const merged = { ...defaults, ...props };
  const utils = render(
    <LanguageProvider>
      <AddTransactionModal {...merged} />
    </LanguageProvider>,
  );
  return { ...utils, props: merged };
}

// Doğrudan form adımında açılması için tipik bir STOCK enstrümanı.
const STOCK_INSTRUMENT = {
  symbol: 'THYAO.IS',
  assetType: 'STOCK',
  name: 'Türk Hava Yolları',
  price: 100,
};

beforeEach(() => {
  vi.clearAllMocks();
  localStorage.clear();
  // Effect içindeki getPriceAtDate çağrısı (geçmiş/eurobond yolu) güvenli sonuçlansın.
  getPriceAtDate.mockResolvedValue({ found: false, price: null });
});

describe('AddTransactionModal — arama adımı (initialInstrument yok)', () => {
  it('initialInstrument verilmeyince arama modalını render eder', () => {
    renderModal({ initialInstrument: null });
    expect(screen.getByTestId('search-modal')).toBeInTheDocument();
    // İşlem formu (BUY/SELL segmenti) henüz görünmemeli.
    expect(screen.queryByText('İşlem Ekle')).not.toBeInTheDocument();
  });

  it('portfolioName ve initialType prop\'larını arama modalına geçirir', () => {
    renderModal({ initialInstrument: null, portfolioName: 'Emeklilik' });
    expect(screen.getByTestId('search-portfolio-name')).toHaveTextContent('Emeklilik');
    // initialInstrument yok → lastUsedAssetType default 'STOCK'.
    expect(screen.getByTestId('search-initial-type')).toHaveTextContent('STOCK');
  });

  it('arama modalındaki kapat butonu onClose\'u çağırır', () => {
    const { props } = renderModal({ initialInstrument: null });
    fireEvent.click(screen.getByText('mock-close'));
    expect(props.onClose).toHaveBeenCalledTimes(1);
  });

  it('arama modalından enstrüman seçilince forma geçer ve sembolü gösterir', () => {
    renderModal({ initialInstrument: null });
    fireEvent.click(screen.getByText('mock-select'));
    // Artık form adımı: başlık + seçilen sembol görünür.
    expect(screen.getByText('İşlem Ekle')).toBeInTheDocument();
    expect(screen.getByText('THYAO.IS')).toBeInTheDocument();
    expect(screen.queryByTestId('search-modal')).not.toBeInTheDocument();
  });
});

describe('AddTransactionModal — form adımı (initialInstrument ile)', () => {
  it('initialInstrument ile doğrudan formu render eder (smoke)', () => {
    renderModal({ initialInstrument: STOCK_INSTRUMENT });
    expect(screen.getByText('İşlem Ekle')).toBeInTheDocument();
    // Header'da sembol ve isim.
    expect(screen.getByText('THYAO.IS')).toBeInTheDocument();
    // BUY/SELL segmenti.
    expect(screen.getByText('Alış')).toBeInTheDocument();
    expect(screen.getByText('Satış')).toBeInTheDocument();
    // Varsayılan BUY → submit butonu "Alış Ekle".
    expect(screen.getByText('Alış Ekle')).toBeInTheDocument();
  });

  it('initialInstrument.price otomatik doldurulur ve "otomatik" rozeti gösterilir', () => {
    renderModal({ initialInstrument: STOCK_INSTRUMENT });
    // priceAuto initialInstrument.price != null olduğunda true.
    expect(screen.getByText('otomatik')).toBeInTheDocument();
  });

  it('Satış sekmesine tıklanınca submit butonu "Satış Ekle" olur', () => {
    renderModal({ initialInstrument: STOCK_INSTRUMENT });
    expect(screen.getByText('Alış Ekle')).toBeInTheDocument();
    fireEvent.click(screen.getByText('Satış'));
    expect(screen.getByText('Satış Ekle')).toBeInTheDocument();
    expect(screen.queryByText('Alış Ekle')).not.toBeInTheDocument();
  });

  it('"Değiştir" butonu arama adımına geri döndürür', () => {
    renderModal({ initialInstrument: STOCK_INSTRUMENT });
    expect(screen.queryByTestId('search-modal')).not.toBeInTheDocument();
    fireEvent.click(screen.getByText('Değiştir'));
    expect(screen.getByTestId('search-modal')).toBeInTheDocument();
  });

  it('header\'daki kapat (X) butonu onClose\'u çağırır', () => {
    const { props } = renderModal({ initialInstrument: STOCK_INSTRUMENT });
    fireEvent.click(screen.getByLabelText('Kapat'));
    expect(props.onClose).toHaveBeenCalledTimes(1);
  });

  it('İptal butonu onClose\'u çağırır', () => {
    const { props } = renderModal({ initialInstrument: STOCK_INSTRUMENT });
    fireEvent.click(screen.getByText('İptal'));
    expect(props.onClose).toHaveBeenCalledTimes(1);
  });
});

describe('AddTransactionModal — gönderim (submit) davranışı', () => {
  it('miktar boşken gönderince hata gösterir, addTransaction çağrılmaz', async () => {
    renderModal({ initialInstrument: STOCK_INSTRUMENT });
    // Fiyat otomatik dolu (100) ama miktar boş → "Miktar 0'dan büyük olmalıdır."
    fireEvent.click(screen.getByText('Alış Ekle'));
    expect(await screen.findByText("Miktar 0'dan büyük olmalıdır.")).toBeInTheDocument();
    expect(addTransaction).not.toHaveBeenCalled();
  });

  it('fiyat 0 iken gönderince fiyat hatası gösterir, addTransaction çağrılmaz', async () => {
    // price yoksa priceAuto effect'i de tetiklenir ama STOCK+bugün+price yok → /price-at çağrılır
    // ve found:false döner → fiyat boş kalır.
    renderModal({ initialInstrument: { ...STOCK_INSTRUMENT, price: null } });
    fireEvent.click(screen.getByText('Alış Ekle'));
    expect(await screen.findByText("Fiyat 0'dan büyük olmalıdır.")).toBeInTheDocument();
    expect(addTransaction).not.toHaveBeenCalled();
  });

  it('geçerli miktar + fiyat ile gönderince addTransaction beklenen payload ile çağrılır', async () => {
    addTransaction.mockResolvedValue({ id: 'pf-1', updated: true });
    const { props } = renderModal({ initialInstrument: STOCK_INSTRUMENT });

    // Miktar gir (STOCK floor: tam adet). Komisyon placeholder'ı farklı metin
    // ('0,00 (isteğe bağlı)') olduğundan exact-match yalnız [Miktar, Fiyat] döner → [0]=Miktar.
    const decimalInputs = screen.getAllByPlaceholderText('0,00');
    fireEvent.change(decimalInputs[0], { target: { value: '10' } });

    fireEvent.click(screen.getByText('Alış Ekle'));

    await waitFor(() => expect(addTransaction).toHaveBeenCalledTimes(1));
    const [portfolioId, payload] = addTransaction.mock.calls[0];
    expect(portfolioId).toBe('pf-1');
    expect(payload).toMatchObject({
      symbol: 'THYAO.IS',
      assetType: 'STOCK',
      transactionType: 'BUY',
      quantity: 10,
      price: 100,
    });
    // Başarıdan sonra onAdded(updated) + onClose çağrılır.
    await waitFor(() => expect(props.onAdded).toHaveBeenCalledWith({ id: 'pf-1', updated: true }));
    expect(props.onClose).toHaveBeenCalledTimes(1);
  });

  it('addTransaction reddedince hata mesajı gösterilir, onClose çağrılmaz', async () => {
    addTransaction.mockRejectedValue({});
    const { props } = renderModal({ initialInstrument: STOCK_INSTRUMENT });

    const decimalInputs = screen.getAllByPlaceholderText('0,00');
    fireEvent.change(decimalInputs[0], { target: { value: '5' } });
    fireEvent.click(screen.getByText('Alış Ekle'));

    // Hata yakalanır → "İşlem eklenemedi." (extractApiErrorMessage boş → fallback).
    expect(await screen.findByText('İşlem eklenemedi.')).toBeInTheDocument();
    expect(props.onClose).not.toHaveBeenCalled();
  });
});

describe('AddTransactionModal — koşullu render (asset tipine göre)', () => {
  it('FUND için "Birim Pay Değeri" etiketi ve varsayılan "Tutar ile" modu görünür', () => {
    renderModal({
      initialInstrument: { symbol: 'AFA', assetType: 'FUND', name: 'Ak Portföy Fonu', price: 12.5 },
    });
    // FUND fiyat etiketi (aynı <label> içinde "otomatik" rozeti de olabilir → regex).
    expect(screen.getByText(/Birim Pay Değeri/)).toBeInTheDocument();
    // defaultInputMode(FUND)='amount' → "Yatırım Tutarı" etiketi (miktar değil).
    expect(screen.getByText(/Yatırım Tutarı/)).toBeInTheDocument();
  });

  it('BOND için nominal/birim fiyat etiketleri ve ihraç bilgi notu görünür', () => {
    renderModal({
      initialInstrument: {
        symbol: 'TRT123456789',
        assetType: 'BOND',
        name: 'Devlet Tahvili',
        price: 95,
        category: 'ZERO_COUPON_BOND',
      },
    });
    expect(screen.getByText('Nominal Değer *')).toBeInTheDocument();
    // Fiyat etiketi aynı <label> içinde "otomatik" rozetiyle birleşebilir → regex.
    expect(screen.getByText(/Birim Fiyat \(100 TL nominal başına\)/)).toBeInTheDocument();
  });

  it('FUTURE için "Kontrat Adedi" ve "Pozisyon Yönü" (LONG/SHORT) alanları görünür', () => {
    renderModal({
      initialInstrument: { symbol: 'F_XU0300625', assetType: 'FUTURE', name: 'XU030 Vadeli', price: 11000 },
    });
    expect(screen.getByText('Kontrat Adedi *')).toBeInTheDocument();
    // "Pozisyon Yönü" + " *" ayrı text node'ları aynı <label> içinde → regex ile eşle.
    expect(screen.getByText(/Pozisyon Yönü/)).toBeInTheDocument();
    expect(screen.getByText('Uzun (LONG)')).toBeInTheDocument();
    expect(screen.getByText('Kısa (SHORT)')).toBeInTheDocument();
  });
});
