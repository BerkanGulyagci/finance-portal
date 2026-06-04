import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent} from '@testing-library/react';
import DateField from '../DateField';
import { LanguageProvider } from '../../../context/LanguageContext';

// DateField, useTranslation() hook'unu kullanır → gerçek LanguageProvider ile sarmalanır.
// Varsayılan dil "tr" olduğundan t(key) anahtarın kendisini döndürür ("Bugün" → "Bugün").
// Popover, createPortal ile document.body'ye basılır; testing-library `screen` body'yi
// sorguladığı için portal içeriği normal şekilde bulunur.
// Not: jsdom'da canvas/ağ yok ama DateField bunları kullanmaz (sadece SVG ikon + DOM),
// bu yüzden chart/api/router mock'una gerek yoktur.
function renderWithLang(ui) {
  return render(<LanguageProvider>{ui}</LanguageProvider>);
}

// Popover header butonu (ay/yıl etiketi) + okları dışındaki gün ızgarasını verir.
// Trigger metniyle karışmaması için aria yerine rol+metin kombinasyonu kullanılır.
function openPicker() {
  // Trigger, value yoksa "Tarih seçin" placeholder'ını içerir.
  const trigger = screen.getAllByRole('button')[0];
  fireEvent.click(trigger);
}

describe('DateField (jsdom + testing-library + React 19 pipeline)', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
  });

  it('value yokken varsayılan placeholder (Tarih seçin) ile render olur', () => {
    renderWithLang(<DateField value={null} onChange={() => {}} />);
    expect(screen.getByText('Tarih seçin')).toBeInTheDocument();
  });

  it('ozel placeholder prop verilince onu gosterir', () => {
    renderWithLang(
      <DateField value={null} onChange={() => {}} placeholder="İşlem tarihi" />
    );
    expect(screen.getByText('İşlem tarihi')).toBeInTheDocument();
  });

  it('value verilince trigger DD.MM.YYYY formatında gösterir (YYYY-MM-DD girdisi)', () => {
    renderWithLang(<DateField value="2024-03-15" onChange={() => {}} />);
    expect(screen.getByText('15.03.2024')).toBeInTheDocument();
  });

  it('Date instance value\'ını da DD.MM.YYYY olarak gösterir', () => {
    renderWithLang(
      <DateField value={new Date(2023, 0, 5)} onChange={() => {}} />
    );
    expect(screen.getByText('05.01.2023')).toBeInTheDocument();
  });

  it('başlangıçta popover kapalıdır (Bugün/Tamam görünmez)', () => {
    renderWithLang(<DateField value={null} onChange={() => {}} />);
    expect(screen.queryByText('Bugün')).not.toBeInTheDocument();
    expect(screen.queryByText('Tamam')).not.toBeInTheDocument();
  });

  it('trigger\'a tıklayınca takvim popover açılır (gün başlıkları + Bugün/Tamam)', () => {
    renderWithLang(<DateField value="2024-03-15" onChange={() => {}} />);
    openPicker();
    // Pazartesi-başlangıçlı TR gün-of-week başlıkları
    expect(screen.getByText('Pt')).toBeInTheDocument();
    expect(screen.getByText('Pz')).toBeInTheDocument();
    // Alt aksiyon butonları
    expect(screen.getByText('Bugün')).toBeInTheDocument();
    expect(screen.getByText('Tamam')).toBeInTheDocument();
  });

  it('açık popover header\'ı görüntülenen ayın TR adını + yılı gösterir', () => {
    // value = Mart 2024 → header "Mart 2024"
    renderWithLang(<DateField value="2024-03-15" onChange={() => {}} />);
    openPicker();
    expect(screen.getByText('Mart 2024')).toBeInTheDocument();
  });

  it('bir güne tıklayınca onChange Date ile çağrılır ve popover kapanır', () => {
    const onChange = vi.fn();
    renderWithLang(<DateField value="2024-03-15" onChange={onChange} />);
    openPicker();
    // Gün ızgarasındaki "20" butonuna tıkla (Mart 2024).
    fireEvent.click(screen.getByRole('button', { name: '20' }));

    expect(onChange).toHaveBeenCalledTimes(1);
    const arg = onChange.mock.calls[0][0];
    expect(arg).toBeInstanceOf(Date);
    expect(arg.getFullYear()).toBe(2024);
    expect(arg.getMonth()).toBe(2); // 0-tabanlı: Mart
    expect(arg.getDate()).toBe(20);
    // Seçim sonrası popover kapanır
    expect(screen.queryByText('Bugün')).not.toBeInTheDocument();
  });

  it('"Bugün" butonu onChange\'i bugünün tarihiyle çağırır ve kapatır', () => {
    const onChange = vi.fn();
    renderWithLang(<DateField value={null} onChange={onChange} />);
    openPicker();
    fireEvent.click(screen.getByText('Bugün'));

    expect(onChange).toHaveBeenCalledTimes(1);
    const arg = onChange.mock.calls[0][0];
    expect(arg).toBeInstanceOf(Date);
    const now = new Date();
    expect(arg.getFullYear()).toBe(now.getFullYear());
    expect(arg.getMonth()).toBe(now.getMonth());
    expect(arg.getDate()).toBe(now.getDate());
    expect(screen.queryByText('Tamam')).not.toBeInTheDocument();
  });

  it('max sınırını aşan gün disabled olur ve tıklanınca onChange çağrılmaz', () => {
    const onChange = vi.fn();
    // Görünüm Mart 2024; max = 2024-03-10 → "20" günü devre dışı.
    renderWithLang(
      <DateField value="2024-03-05" max="2024-03-10" onChange={onChange} />
    );
    openPicker();
    const day20 = screen.getByRole('button', { name: '20' });
    expect(day20).toBeDisabled();
    fireEvent.click(day20);
    expect(onChange).not.toHaveBeenCalled();
    // Sınır içindeki bir gün (8) etkin olmalı
    expect(screen.getByRole('button', { name: '8' })).not.toBeDisabled();
  });

  it('min sınırından önceki gün disabled olur', () => {
    // Görünüm Mart 2024; min = 2024-03-15 → "5" günü devre dışı, "25" etkin.
    renderWithLang(
      <DateField value="2024-03-20" min="2024-03-15" onChange={() => {}} />
    );
    openPicker();
    expect(screen.getByRole('button', { name: '5' })).toBeDisabled();
    expect(screen.getByRole('button', { name: '25' })).not.toBeDisabled();
  });

  it('header etiketine tıklayınca ay-seçim moduna geçer (ay kısaltmaları görünür)', () => {
    renderWithLang(<DateField value="2024-03-15" onChange={() => {}} />);
    openPicker();
    // Header label = "Mart 2024" → days→months moduna geçer.
    fireEvent.click(screen.getByText('Mart 2024'));
    // Aylar 3-harf kısaltma ile: Oca, Şub, Mar ...
    expect(screen.getByRole('button', { name: 'Oca' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Ara' })).toBeInTheDocument();
    // Gün başlıkları artık yok
    expect(screen.queryByText('Pt')).not.toBeInTheDocument();
  });

  it('ay → yıl modu: bir ay seçince günlere döner ve görünen ay değişir', () => {
    renderWithLang(<DateField value="2024-03-15" onChange={() => {}} />);
    openPicker();
    fireEvent.click(screen.getByText('Mart 2024')); // months moduna
    fireEvent.click(screen.getByRole('button', { name: 'Tem' })); // Temmuz seç → days moduna
    // Header artık Temmuz 2024'i göstermeli
    expect(screen.getByText('Temmuz 2024')).toBeInTheDocument();
  });

  it('açıkken trigger\'a tekrar tıklayınca popover kapanır (toggle)', () => {
    renderWithLang(<DateField value="2024-03-15" onChange={() => {}} />);
    const trigger = screen.getAllByRole('button')[0];
    fireEvent.click(trigger); // aç
    expect(screen.getByText('Bugün')).toBeInTheDocument();
    fireEvent.click(trigger); // kapat
    expect(screen.queryByText('Bugün')).not.toBeInTheDocument();
  });

  it('"Tamam" butonu popover\'ı onChange çağırmadan kapatır', () => {
    const onChange = vi.fn();
    renderWithLang(<DateField value="2024-03-15" onChange={onChange} />);
    openPicker();
    fireEvent.click(screen.getByText('Tamam'));
    expect(screen.queryByText('Bugün')).not.toBeInTheDocument();
    expect(onChange).not.toHaveBeenCalled();
  });

  it('ileri ok ile bir sonraki aya geçer (Mart → Nisan)', () => {
    renderWithLang(<DateField value="2024-03-15" onChange={() => {}} />);
    openPicker();
    const popoverButtons = screen.getAllByRole('button');
    // Header satırı: [prev(ChevronLeft), label, next(ChevronRight)] portal içinde.
    // İleri ok, label("Mart 2024")'tan hemen sonraki butondur.
    const label = screen.getByText('Mart 2024');
    const nextBtn = label.closest('button').nextElementSibling;
    fireEvent.click(nextBtn);
    expect(screen.getByText('Nisan 2024')).toBeInTheDocument();
    expect(popoverButtons.length).toBeGreaterThan(0);
  });
});
