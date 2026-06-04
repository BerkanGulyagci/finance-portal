import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';

// ── Mock'lar ────────────────────────────────────────────────────────────────
// AlarmCreateModal, alarm API'sini (createAlarm/updateAlarm) handleSubmit içinde
// çağırır. jsdom'da ağ yok → modülü stub'la. Yol TEST dosyasının konumuna görelidir
// (src/components/instrument/__tests__ → src/api : ../../../api).
vi.mock('../../../api/alarmApi', () => ({
  createAlarm: vi.fn(() => Promise.resolve({ id: 'al-1' })),
  updateAlarm: vi.fn(() => Promise.resolve({ id: 'al-1' })),
}));

// Toast: gerçek ToastProvider yerine basit stub — success/error çağrılarını doğrula.
const toastSuccess = vi.fn();
const toastError = vi.fn();
vi.mock('../../../context/ToastContext', () => ({
  useToast: () => ({ success: toastSuccess, error: toastError, push: vi.fn() }),
}));

import { createAlarm, updateAlarm } from '../../../api/alarmApi';
import AlarmCreateModal from '../AlarmCreateModal';
// LanguageContext GERÇEK Provider ile kullanılır: varsayılan dil "tr" → t(key) anahtarın
// kendisini döndürür, yani DOM'da Türkçe metin anahtarları görünür.
import { LanguageProvider } from '../../../context/LanguageContext';

// Tipik bir BIST hisse enstrümanı (.IS → ₺ sembolü).
const STOCK_INSTRUMENT = {
  assetType: 'STOCK',
  symbol: 'THYAO.IS',
  name: 'Türk Hava Yolları',
  price: 250.5,
};

function renderModal(props = {}) {
  const defaults = {
    instrument: STOCK_INSTRUMENT,
    alarm: null,
    onClose: vi.fn(),
    onSaved: vi.fn(),
    onCreated: vi.fn(),
  };
  const merged = { ...defaults, ...props };
  const utils = render(
    <MemoryRouter>
      <LanguageProvider>
        <AlarmCreateModal {...merged} />
      </LanguageProvider>
    </MemoryRouter>,
  );
  return { ...utils, props: merged };
}

beforeEach(() => {
  vi.clearAllMocks();
  localStorage.clear();
});

describe('AlarmCreateModal — oluşturma modu (instrument ile)', () => {
  it('enstrüman adıyla başlığı ve "İçin Alarm" metnini render eder (smoke)', () => {
    renderModal();
    // Başlık: "{name} İçin Alarm" — ayrı text node'ları aynı <span> içinde → regex.
    expect(screen.getByText(/Türk Hava Yolları/)).toBeInTheDocument();
    expect(screen.getByText(/İçin Alarm/)).toBeInTheDocument();
    // Düzenleme moduna ait metin görünmemeli.
    expect(screen.queryByText(/— Alarmı Düzenle/)).not.toBeInTheDocument();
  });

  it('temel form alanlarını ve "Oluştur" gönder butonunu gösterir', () => {
    renderModal();
    expect(screen.getByText('Koşul')).toBeInTheDocument();
    expect(screen.getByText('Değer')).toBeInTheDocument();
    expect(screen.getByText('Sıklık')).toBeInTheDocument();
    // Oluşturma modunda submit butonu "Oluştur" (edit'te "Kaydet").
    expect(screen.getByRole('button', { name: 'Oluştur' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Kaydet' })).not.toBeInTheDocument();
  });

  it('eşik (threshold) girişini mevcut fiyatla TR biçiminde ön-doldurur', () => {
    renderModal();
    // currentPrice=250.5 → fmtTr → "250,5" (tr-TR). Değer alanı tek text input.
    const input = screen.getByPlaceholderText('0,00');
    expect(input).toHaveValue('250,5');
  });

  it('hisse (.IS) için para birimi sembolü ₺ ve "Şu anki fiyat" satırını gösterir', () => {
    renderModal();
    // suffix ₺ (input'un yanındaki) + "Şu anki fiyat:" etiketi.
    expect(screen.getByText('Şu anki fiyat:')).toBeInTheDocument();
    // 250,5 ₺ birden çok yerde olabilir; en az bir ₺ görünür.
    expect(screen.getAllByText('₺').length).toBeGreaterThan(0);
  });
});

describe('AlarmCreateModal — koşullu render dalları', () => {
  it('FUTURE enstrümanda (oluşturma) teminat uyarısını ve Profil linkini gösterir', () => {
    renderModal({
      instrument: { assetType: 'FUTURE', symbol: 'F_XU0300625', name: 'XU030 Vadeli', price: 11000 },
    });
    expect(
      screen.getByText('Teminat uyarıları artık Profil sayfasından tek noktadan yönetilir.'),
    ).toBeInTheDocument();
    // Profil linki (Link to="/profile").
    const profileLink = screen.getByRole('link', { name: 'Profil' });
    expect(profileLink).toHaveAttribute('href', '/profile');
  });

  it('hisse (FUTURE değil) enstrümanda teminat uyarısı görünmez', () => {
    renderModal();
    expect(
      screen.queryByText('Teminat uyarıları artık Profil sayfasından tek noktadan yönetilir.'),
    ).not.toBeInTheDocument();
  });

  it('düzenleme modunda (MARGIN_RATIO alarm) "Alarm Türü" başlığı ve eski-sistem notunu gösterir', () => {
    renderModal({
      instrument: null,
      alarm: {
        id: 'al-9',
        assetType: 'FUTURE',
        symbol: 'F_XU0300625',
        instrumentName: 'XU030 Vadeli',
        metric: 'MARGIN_RATIO',
        direction: 'BELOW',
        threshold: 0.25, // 0-1 ondalık → UI'da %25
        frequency: 'RECURRING',
        note: '',
      },
    });
    // Edit modu başlığı.
    expect(screen.getByText(/— Alarmı Düzenle/)).toBeInTheDocument();
    // MARGIN_RATIO edit → "Alarm Türü" disabled select + açıklama.
    expect(screen.getByText('Alarm Türü')).toBeInTheDocument();
    expect(
      screen.getByText('Teminat uyarıları artık Profil sayfasından tek noktadan yönetilir.'),
    ).toBeInTheDocument();
    // Submit "Kaydet".
    expect(screen.getByRole('button', { name: 'Kaydet' })).toBeInTheDocument();
  });
});

describe('AlarmCreateModal — etkileşim', () => {
  it('X (kapat) butonu onClose callback\'ini çağırır', () => {
    const { props } = renderModal();
    // Header'daki kapat butonu erişilebilir ad taşımıyor → ilk button'ı X kabul et:
    // güvenli yol: tüm buttonlar içinde X ikonlu olan. Burada başlıktaki tek "icon-only"
    // buton header'daki kapat; "Hızlı seç"/preset yok (PRICE). Frekans butonları metinli.
    // En sağlam: onClose'u tetikleyen "Alarmlarımı Düzenle" yerine X'i bul.
    // X butonunun erişilebilir adı yok; bu yüzden container'dan ilk <button>'ı al.
    const buttons = screen.getAllByRole('button');
    // İlk button = header kapat (X). Tıkla → onClose.
    fireEvent.click(buttons[0]);
    expect(props.onClose).toHaveBeenCalledTimes(1);
  });

  it('sıklık "Sürekli" butonuna tıklayınca açıklama metni değişir', () => {
    renderModal();
    // Başlangıç ONCE → "Koşul bir kez sağlandığında tetiklenir ve durur."
    expect(
      screen.getByText('Koşul bir kez sağlandığında tetiklenir ve durur.'),
    ).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Sürekli' }));
    // RECURRING açıklaması.
    expect(
      screen.getByText('Koşul her sağlandığında tetiklenir (saatte en fazla bir kez).'),
    ).toBeInTheDocument();
  });

  it('"Alarmlarımı Düzenle" linki onClose tetikler ve /alarms\'a gider', () => {
    const { props } = renderModal();
    const link = screen.getByRole('link', { name: 'Alarmlarımı Düzenle' });
    expect(link).toHaveAttribute('href', '/alarms');
    fireEvent.click(link);
    expect(props.onClose).toHaveBeenCalledTimes(1);
  });
});

describe('AlarmCreateModal — gönderim (submit) davranışı', () => {
  it('geçerli eşikle gönderince createAlarm beklenen payload ile çağrılır ve callback\'ler tetiklenir', async () => {
    createAlarm.mockResolvedValue({ id: 'al-1' });
    const { props } = renderModal();

    // Eşik zaten "250,5" ön-dolu; yön ABOVE varsayılan. Doğrudan gönder.
    fireEvent.click(screen.getByRole('button', { name: 'Oluştur' }));

    await waitFor(() => expect(createAlarm).toHaveBeenCalledTimes(1));
    const payload = createAlarm.mock.calls[0][0];
    expect(payload).toMatchObject({
      assetType: 'STOCK',
      symbol: 'THYAO.IS',
      instrumentName: 'Türk Hava Yolları',
      metric: 'PRICE',
      direction: 'ABOVE',
      threshold: 250.5, // parseNumeric("250,5") → 250.5
      frequency: 'ONCE',
    });
    // Başarı: toast + onSaved + onCreated + onClose.
    expect(toastSuccess).toHaveBeenCalledWith('Alarm oluşturuldu.');
    expect(props.onSaved).toHaveBeenCalledTimes(1);
    expect(props.onCreated).toHaveBeenCalledTimes(1);
    expect(props.onClose).toHaveBeenCalledTimes(1);
  });

  it('"Altına İnerse" yönü seçilince direction BELOW olarak gönderilir', async () => {
    createAlarm.mockResolvedValue({ id: 'al-2' });
    renderModal();

    // Koşul select'i: ABOVE/BELOW. "Altına İnerse" = BELOW.
    const conditionSelect = screen.getByRole('combobox');
    fireEvent.change(conditionSelect, { target: { value: 'BELOW' } });

    fireEvent.click(screen.getByRole('button', { name: 'Oluştur' }));
    await waitFor(() => expect(createAlarm).toHaveBeenCalledTimes(1));
    expect(createAlarm.mock.calls[0][0]).toMatchObject({ direction: 'BELOW' });
  });

  it('eşik geçersiz (boş) olunca hata gösterir ve createAlarm çağrılmaz', async () => {
    renderModal();
    const input = screen.getByPlaceholderText('0,00');
    // Eşiği temizle → parseNumeric(null/"") → null.
    fireEvent.change(input, { target: { value: '' } });
    fireEvent.click(screen.getByRole('button', { name: 'Oluştur' }));

    expect(await screen.findByText('Lütfen geçerli bir değer girin.')).toBeInTheDocument();
    expect(createAlarm).not.toHaveBeenCalled();
  });

  it('createAlarm reddedince API hata mesajını gösterir, onClose çağrılmaz', async () => {
    createAlarm.mockRejectedValue({ response: { data: { message: 'Bu enstrüman için zaten alarm var.' } } });
    const { props } = renderModal();

    fireEvent.click(screen.getByRole('button', { name: 'Oluştur' }));

    expect(await screen.findByText('Bu enstrüman için zaten alarm var.')).toBeInTheDocument();
    expect(props.onClose).not.toHaveBeenCalled();
    expect(toastSuccess).not.toHaveBeenCalled();
  });

  it('düzenleme modunda gönderince updateAlarm doğru id + payload ile çağrılır', async () => {
    updateAlarm.mockResolvedValue({ id: 'al-7' });
    const { props } = renderModal({
      instrument: null,
      alarm: {
        id: 'al-7',
        assetType: 'STOCK',
        symbol: 'THYAO.IS',
        instrumentName: 'Türk Hava Yolları',
        metric: 'PRICE',
        direction: 'BELOW',
        threshold: 200,
        frequency: 'ONCE',
        note: 'hedef',
      },
    });

    fireEvent.click(screen.getByRole('button', { name: 'Kaydet' }));

    await waitFor(() => expect(updateAlarm).toHaveBeenCalledTimes(1));
    const [id, payload] = updateAlarm.mock.calls[0];
    expect(id).toBe('al-7');
    expect(payload).toMatchObject({
      metric: 'PRICE',
      direction: 'BELOW',
      threshold: 200,
      frequency: 'ONCE',
      note: 'hedef',
    });
    expect(toastSuccess).toHaveBeenCalledWith('Alarm güncellendi.');
    expect(props.onClose).toHaveBeenCalledTimes(1);
  });

  it('MARGIN_RATIO düzenlemede eşik 100 ile çarpılıp gösterilir ve /100 ondalık gönderilir', async () => {
    updateAlarm.mockResolvedValue({ id: 'al-9' });
    renderModal({
      instrument: null,
      alarm: {
        id: 'al-9',
        assetType: 'FUTURE',
        symbol: 'F_XU0300625',
        instrumentName: 'XU030 Vadeli',
        metric: 'MARGIN_RATIO',
        direction: 'BELOW',
        threshold: 0.25, // UI: %25
        frequency: 'ONCE',
        note: '',
      },
    });
    // Threshold input ön-dolu: 0.25*100 = 25 → fmtTr → "25".
    const input = screen.getByPlaceholderText('50'); // MARGIN metric placeholder "50"
    expect(input).toHaveValue('25');

    fireEvent.click(screen.getByRole('button', { name: 'Kaydet' }));
    await waitFor(() => expect(updateAlarm).toHaveBeenCalledTimes(1));
    // payloadThreshold = 25/100 = 0.25; direction MARGIN → BELOW.
    expect(updateAlarm.mock.calls[0][1]).toMatchObject({
      metric: 'MARGIN_RATIO',
      direction: 'BELOW',
      threshold: 0.25,
    });
  });
});
