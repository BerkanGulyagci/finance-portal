import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, within } from '@testing-library/react';

// DateTimeField bağımlılıkları sade: lucide ikonları (SVG), react-dom createPortal
// (jsdom destekler, popover document.body'ye gider) ve LanguageContext.useTranslation.
// Ağ/chart/router YOK → mock gerekmez; dil 'tr' olduğundan t(key) anahtarın kendisini
// döndürür ve gerçek LanguageProvider ile sarmalarız.
import DateTimeField from '../DateTimeField';
import { LanguageProvider } from '../../../../context/LanguageContext';

// Varsayılan: value verili (15 Mart 2024, 14:30). onChange casuslanır.
function renderField(props = {}) {
  const defaults = {
    value: '2024-03-15T14:30',
    onChange: vi.fn(),
    min: undefined,
    max: undefined,
  };
  const merged = { ...defaults, ...props };
  const utils = render(
    <LanguageProvider>
      <DateTimeField {...merged} />
    </LanguageProvider>,
  );
  return { ...utils, props: merged };
}

// Popover document.body'ye portal ile basılır; tek bir tane vardır.
// Saat input'u (type=time) popover'ın işaretidir → onu kapsayan div'i ararız.
function getPopover() {
  const timeInput = document.querySelector('input[type="time"]');
  // popover kökü: time input'tan yukarı doğru fixed z-[1000] kapsayıcı
  return timeInput ? timeInput.closest('div.fixed') : null;
}

beforeEach(() => {
  vi.clearAllMocks();
  localStorage.clear();
});

describe('DateTimeField — tetikleyici (kapalı durum)', () => {
  it('value verilince formatlı tarih+saati gösterir (smoke)', () => {
    renderField({ value: '2024-03-15T14:30' });
    // displayText: "15.03.2024 · 14:30"
    expect(screen.getByText('15.03.2024 · 14:30')).toBeInTheDocument();
    // Başta popover kapalı → saat input'u yok.
    expect(document.querySelector('input[type="time"]')).toBeNull();
  });

  it('value boşken "Tarih seçin" placeholder metnini gösterir', () => {
    // Dil 'tr' → t('Tarih seçin') === 'Tarih seçin'.
    renderField({ value: '' });
    expect(screen.getByText('Tarih seçin')).toBeInTheDocument();
  });

  it('geçersiz value verilince yine placeholder gösterilir (parseValue null)', () => {
    renderField({ value: 'not-a-date' });
    expect(screen.getByText('Tarih seçin')).toBeInTheDocument();
  });
});

describe('DateTimeField — popover açma/kapama', () => {
  it('tetikleyiciye tıklayınca takvim popover\'ı açılır', () => {
    renderField({ value: '2024-03-15T14:30' });
    fireEvent.click(screen.getByText('15.03.2024 · 14:30'));
    // Açılınca: ay/yıl başlığı + saat input + "Bugün"/"Tamam".
    expect(screen.getByText('Mart 2024')).toBeInTheDocument();
    expect(document.querySelector('input[type="time"]')).not.toBeNull();
    expect(screen.getByText('Bugün')).toBeInTheDocument();
    expect(screen.getByText('Tamam')).toBeInTheDocument();
  });

  it('açıkken tetikleyiciye tekrar tıklayınca popover kapanır', () => {
    renderField({ value: '2024-03-15T14:30' });
    const trigger = screen.getByText('15.03.2024 · 14:30');
    fireEvent.click(trigger); // aç
    expect(document.querySelector('input[type="time"]')).not.toBeNull();
    fireEvent.click(trigger); // kapat
    expect(document.querySelector('input[type="time"]')).toBeNull();
  });

  it('"Tamam" butonu popover\'ı kapatır', () => {
    renderField({ value: '2024-03-15T14:30' });
    fireEvent.click(screen.getByText('15.03.2024 · 14:30'));
    fireEvent.click(screen.getByText('Tamam'));
    expect(document.querySelector('input[type="time"]')).toBeNull();
  });

  it('Escape tuşu popover\'ı kapatır', () => {
    renderField({ value: '2024-03-15T14:30' });
    fireEvent.click(screen.getByText('15.03.2024 · 14:30'));
    expect(document.querySelector('input[type="time"]')).not.toBeNull();
    fireEvent.keyDown(document, { key: 'Escape' });
    expect(document.querySelector('input[type="time"]')).toBeNull();
  });

  it('dışarı (mousedown) tıklayınca popover kapanır', () => {
    renderField({ value: '2024-03-15T14:30' });
    fireEvent.click(screen.getByText('15.03.2024 · 14:30'));
    expect(document.querySelector('input[type="time"]')).not.toBeNull();
    // body üzerinde mousedown → onDown handler kapatır.
    fireEvent.mouseDown(document.body);
    expect(document.querySelector('input[type="time"]')).toBeNull();
  });
});

describe('DateTimeField — gün seçimi ve onChange', () => {
  it('bir güne tıklayınca onChange "YYYY-MM-DDTHH:mm" formatında çağrılır (saat korunur)', () => {
    const { props } = renderField({ value: '2024-03-15T14:30' });
    fireEvent.click(screen.getByText('15.03.2024 · 14:30'));
    const pop = getPopover();
    // Gün ızgarasında "20" butonuna tıkla (1-31 metinleri benzersiz değil; popover içinde ara).
    const day20 = within(pop).getByRole('button', { name: '20' });
    fireEvent.click(day20);
    expect(props.onChange).toHaveBeenCalledTimes(1);
    // Saat seçili değerden (14:30) korunur, gün 20 olur.
    expect(props.onChange).toHaveBeenCalledWith('2024-03-20T14:30');
  });

  it('saat input\'u değişince onChange aynı gün + yeni saat ile çağrılır', () => {
    const { props } = renderField({ value: '2024-03-15T14:30' });
    fireEvent.click(screen.getByText('15.03.2024 · 14:30'));
    const timeInput = document.querySelector('input[type="time"]');
    fireEvent.change(timeInput, { target: { value: '09:05' } });
    expect(props.onChange).toHaveBeenCalledWith('2024-03-15T09:05');
  });
});

describe('DateTimeField — mod döngüsü (gün → ay → yıl)', () => {
  it('başlığa tıklayınca ay seçimine, tekrar tıklayınca yıl seçimine geçer', () => {
    renderField({ value: '2024-03-15T14:30' });
    fireEvent.click(screen.getByText('15.03.2024 · 14:30'));

    // days modu: başlık "Mart 2024"
    fireEvent.click(screen.getByText('Mart 2024'));
    // months modu: başlık sadece yıl "2024" + ay kısaltmaları (Oca, Şub, Mar...)
    expect(screen.getByText('Oca')).toBeInTheDocument();
    expect(screen.getByText('Ara')).toBeInTheDocument();

    // başlık artık "2024" → tıkla → years modu
    fireEvent.click(screen.getByText('2024'));
    // years modu: ondalık başlangıcı floor(2024/12)*12 = 2016 → 2016..2027
    expect(screen.getByText('2016 - 2027')).toBeInTheDocument();
    const pop = getPopover();
    expect(within(pop).getByRole('button', { name: '2016' })).toBeInTheDocument();
    expect(within(pop).getByRole('button', { name: '2027' })).toBeInTheDocument();
  });

  it('yıl → ay → gün seçimi başlığı günceller (onChange tetiklemez)', () => {
    const { props } = renderField({ value: '2024-03-15T14:30' });
    fireEvent.click(screen.getByText('15.03.2024 · 14:30'));
    fireEvent.click(screen.getByText('Mart 2024')); // → months
    fireEvent.click(screen.getByText('2024')); // → years

    const pop = getPopover();
    fireEvent.click(within(pop).getByRole('button', { name: '2020' })); // yıl seç → months
    fireEvent.click(within(getPopover()).getByRole('button', { name: 'Tem' })); // Temmuz → days
    // Başlık artık "Temmuz 2020".
    expect(screen.getByText('Temmuz 2020')).toBeInTheDocument();
    // Sadece görünüm değişti; gün seçilmedi → onChange çağrılmadı.
    expect(props.onChange).not.toHaveBeenCalled();
  });
});

describe('DateTimeField — navigasyon okları', () => {
  it('ileri/geri oklar gün modunda ay başlığını değiştirir', () => {
    renderField({ value: '2024-03-15T14:30' });
    fireEvent.click(screen.getByText('15.03.2024 · 14:30'));
    const pop = getPopover();
    // İki ikon-butonu: önce ChevronLeft sonra ChevronRight (DOM sırası). Başlık ortada.
    const navButtons = within(pop).getAllByRole('button');
    // navButtons[0] = prev (ChevronLeft), navButtons[1] = başlık, navButtons[2] = next.
    fireEvent.click(navButtons[0]); // prev → Şubat 2024
    expect(screen.getByText('Şubat 2024')).toBeInTheDocument();
    fireEvent.click(within(getPopover()).getAllByRole('button')[2]); // next → Mart 2024
    expect(screen.getByText('Mart 2024')).toBeInTheDocument();
  });

  it('Ocak\'ta geri gidince önceki yılın Aralık ayına geçer (yıl sınırı)', () => {
    renderField({ value: '2024-01-10T08:00' });
    fireEvent.click(screen.getByText('10.01.2024 · 08:00'));
    const navButtons = within(getPopover()).getAllByRole('button');
    fireEvent.click(navButtons[0]); // prev → Aralık 2023
    expect(screen.getByText('Aralık 2023')).toBeInTheDocument();
  });
});

describe('DateTimeField — min/max sınırları (disabled günler)', () => {
  it('min sınırından önceki günler devre dışı olur ve tıklayınca onChange çağrılmaz', () => {
    // min = 2024-03-10 → 1..9 disabled, 10+ aktif.
    const { props } = renderField({ value: '2024-03-15T14:30', min: '2024-03-10' });
    fireEvent.click(screen.getByText('15.03.2024 · 14:30'));
    const pop = getPopover();
    const day5 = within(pop).getByRole('button', { name: '5' });
    expect(day5).toBeDisabled();
    fireEvent.click(day5);
    expect(props.onChange).not.toHaveBeenCalled();
    // Sınır içi gün (12) aktif.
    expect(within(pop).getByRole('button', { name: '12' })).not.toBeDisabled();
  });

  it('max sınırından sonraki günler devre dışı olur', () => {
    // max = 2024-03-20 → 21+ disabled.
    renderField({ value: '2024-03-15T14:30', max: '2024-03-20' });
    fireEvent.click(screen.getByText('15.03.2024 · 14:30'));
    const pop = getPopover();
    expect(within(pop).getByRole('button', { name: '25' })).toBeDisabled();
    expect(within(pop).getByRole('button', { name: '18' })).not.toBeDisabled();
  });
});

describe('DateTimeField — "Bugün" butonu', () => {
  it('"Bugün" butonu bugünün tarihiyle onChange\'i çağırır', () => {
    const { props } = renderField({ value: '2024-03-15T14:30' });
    fireEvent.click(screen.getByText('15.03.2024 · 14:30'));
    fireEvent.click(screen.getByText('Bugün'));
    expect(props.onChange).toHaveBeenCalledTimes(1);
    // onChange argümanı bugünün YYYY-MM-DD ön ekiyle başlamalı (saat dakikaya göre değişir).
    const now = new Date();
    const ymd = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
    expect(props.onChange.mock.calls[0][0].startsWith(ymd)).toBe(true);
  });
});
