import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { LanguageProvider } from '../../../../context/LanguageContext';

// ─────────────────────────────────────────────────────────────────────────────
// MOCK'LAR
// ComparePage canvas çizen ECharts'ı dinamik import('echarts') ile yükler →
// jsdom'da canvas yok, init/setOption/resize/dispose stub'lanır.
// new ResizeObserver da chart effect'i içinde kullanılır → global stub gerekir.
// getFxHistory ağ çağrısı → sahte { points: [...] } döner.
// ─────────────────────────────────────────────────────────────────────────────

vi.mock('echarts', () => ({
  init: vi.fn(() => ({
    setOption: vi.fn(),
    resize: vi.fn(),
    dispose: vi.fn(),
    on: vi.fn(),
    off: vi.fn(),
  })),
  use: vi.fn(),
  graphic: {},
}));

// marketApi — test __tests__/ içinde, kaynak fx/ klasöründe, api 4 seviye yukarıda.
vi.mock('../../../../api/marketApi', () => ({
  getFxHistory: vi.fn(),
}));

import * as marketApi from '../../../../api/marketApi';
import ComparePage from '../ComparePage';

// ── Sahte FX serisi üretici (getMetrics ≥2 nokta + date/close ister) ──────────
function makeSeries(start, end) {
  return {
    points: [
      { date: '2024-01-01', close: String(start) },
      { date: '2024-01-15', close: String((start + end) / 2) },
      { date: '2024-02-01', close: String(end) },
    ],
  };
}

// LanguageProvider varsayılan dil "tr" → t(key) anahtarın kendisini döndürür.
// MemoryRouter, useSearchParams/useNavigate için gerekli; initialEntries ile ?symbols verilebilir.
function renderPage({ route = '/market/fx/compare' } = {}) {
  return render(
    <MemoryRouter initialEntries={[route]}>
      <LanguageProvider>
        <ComparePage />
      </LanguageProvider>
    </MemoryRouter>
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  localStorage.clear();
  // ResizeObserver jsdom'da yok — chart effect'i patlamasın diye class stub'la.
  // (clearAllMocks vi.fn implementation'ını bozup "not a constructor" yapabildiğinden
  // düz class kullanıyoruz; new ResizeObserver(...) her zaman çalışır.)
  globalThis.ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  };
  // Varsayılan: her enstrüman için dolu seri.
  marketApi.getFxHistory.mockResolvedValue(makeSeries(30, 33));
});

describe('ComparePage (FX enstrüman karşılaştırma) — jsdom + testing-library', () => {
  it('smoke: throw etmeden render olur ve başlık + açıklama görünür', () => {
    renderPage();
    expect(screen.getByText('Enstrüman Karşılaştırma')).toBeInTheDocument();
    expect(
      screen.getByText(/Birden fazla dövizi aynı grafikte karşılaştır/)
    ).toBeInTheDocument();
    expect(screen.getByText('Enstrüman Ekle')).toBeInTheDocument();
  });

  it('URL ?symbols yokken varsayılan olarak USD/TRY ve EUR/TRY çiplerini gösterir', () => {
    renderPage();
    expect(screen.getByText('USD/TRY')).toBeInTheDocument();
    expect(screen.getByText('EUR/TRY')).toBeInTheDocument();
  });

  it('URL ?symbols=GBP,CHF,JPY verilince o enstrümanların çiplerini gösterir', () => {
    renderPage({ route: '/market/fx/compare?symbols=GBP,CHF,JPY' });
    expect(screen.getByText('GBP/TRY')).toBeInTheDocument();
    expect(screen.getByText('CHF/TRY')).toBeInTheDocument();
    expect(screen.getByText('JPY/TRY')).toBeInTheDocument();
  });

  it('yüklenmemiş + yükleniyor değilken boş durum mesajını gösterir', () => {
    renderPage();
    expect(
      screen.getByText('Enstrümanları seçip "Karşılaştır" butonuna tıkla')
    ).toBeInTheDocument();
    expect(
      screen.getByText('En fazla 7 enstrüman karşılaştırabilirsin')
    ).toBeInTheDocument();
  });

  it('range butonları (1H/1A/3A/6A/1Y/5Y/10Y) ve "Karşılaştır" butonu render edilir', () => {
    renderPage();
    expect(screen.getByRole('button', { name: 'Karşılaştır' })).toBeInTheDocument();
    // Range etiketleri RANGE_LABELS'tan
    ['1H', '1A', '3A', '6A', '1Y', '5Y', '10Y'].forEach((label) => {
      expect(screen.getByRole('button', { name: label })).toBeInTheDocument();
    });
  });

  it('"Ekle" butonuna tıklayınca öneri paneli açılır ve arama kutusu çıkar', () => {
    renderPage();
    fireEvent.click(screen.getByText('Ekle'));
    expect(screen.getByPlaceholderText('Ara... (USD, EUR...)')).toBeInTheDocument();
    // USD/EUR zaten ekli → önerilerde GBP/TRY gibi başka bir döviz olmalı
    expect(screen.getByText('GBP/TRY')).toBeInTheDocument();
  });

  it('öneri panelinde aramayla filtreler; eşleşme yoksa "Sonuç bulunamadı" gösterir', () => {
    renderPage();
    fireEvent.click(screen.getByText('Ekle'));
    const input = screen.getByPlaceholderText('Ara... (USD, EUR...)');
    fireEvent.change(input, { target: { value: 'ZZZZ' } });
    expect(screen.getByText('Sonuç bulunamadı')).toBeInTheDocument();
  });

  it('öneriden enstrüman eklenince yeni çip listeye gelir (GBP/TRY)', () => {
    renderPage();
    fireEvent.click(screen.getByText('Ekle'));
    const input = screen.getByPlaceholderText('Ara... (USD, EUR...)');
    fireEvent.change(input, { target: { value: 'GBP' } });
    // Panel içindeki GBP/TRY önerisine tıkla
    fireEvent.click(screen.getByText('GBP/TRY'));
    // Eklendikten sonra çip olarak görünür (panel kapanır)
    expect(screen.getByText('GBP/TRY')).toBeInTheDocument();
    expect(screen.queryByPlaceholderText('Ara... (USD, EUR...)')).not.toBeInTheDocument();
  });

  it('"Karşılaştır" tıklanınca her enstrüman için getFxHistory çağrılır ve sonuç tabloları yüklenir', async () => {
    renderPage();
    fireEvent.click(screen.getByRole('button', { name: 'Karşılaştır' }));

    // İki enstrüman (USD, EUR) için seçili range (1M) ile çağrı
    await waitFor(() => {
      expect(marketApi.getFxHistory).toHaveBeenCalledTimes(2);
    });
    expect(marketApi.getFxHistory).toHaveBeenCalledWith('USD', '1M');
    expect(marketApi.getFxHistory).toHaveBeenCalledWith('EUR', '1M');

    // Yüklenince metrik ve simülasyon başlıkları belirir
    expect(await screen.findByText('Performans Metrikleri')).toBeInTheDocument();
    expect(screen.getByText('TL Yatırım Simülasyonu')).toBeInTheDocument();
    expect(screen.getByText('Göreceli Performans (%)')).toBeInTheDocument();

    // Boş durum mesajı artık görünmemeli
    expect(
      screen.queryByText('Enstrümanları seçip "Karşılaştır" butonuna tıkla')
    ).not.toBeInTheDocument();
  });

  it('yüklenince ECharts grafik konteyneri (dinamik import) hata fırlatmadan kurulur', async () => {
    const echarts = await import('echarts');
    renderPage();
    fireEvent.click(screen.getByRole('button', { name: 'Karşılaştır' }));
    await screen.findByText('Performans Metrikleri');
    // Dinamik import('echarts').init mikro-task'ta çağrılır
    await waitFor(() => {
      expect(echarts.init).toHaveBeenCalled();
    });
  });

  it('metrik tablosunda "En iyi" performans rozeti gösterilir', async () => {
    // USD artışı (30→40, +%33) EUR'dan (30→31) daha iyi → topPerformer USD olmalı
    marketApi.getFxHistory.mockImplementation((key) =>
      Promise.resolve(key === 'USD' ? makeSeries(30, 40) : makeSeries(30, 31))
    );
    renderPage();
    fireEvent.click(screen.getByRole('button', { name: 'Karşılaştır' }));

    expect(await screen.findByText('En iyi:')).toBeInTheDocument();
    // Tablo başlıkları
    expect(screen.getByText('Dönem Getirisi')).toBeInTheDocument();
    expect(screen.getByText('Volatilite')).toBeInTheDocument();
    expect(screen.getByText('Drawdown')).toBeInTheDocument();
  });

  it('boş seri (points yok) gelince yükler ama metrik hücreleri "-" gösterir', async () => {
    marketApi.getFxHistory.mockResolvedValue({ points: [] });
    renderPage();
    fireEvent.click(screen.getByRole('button', { name: 'Karşılaştır' }));

    // Metrik bölümü yine de render olur (loaded=true)
    expect(await screen.findByText('Performans Metrikleri')).toBeInTheDocument();
    // points boş → getMetrics null → chartData boş → grafik bloğu render edilmez
    expect(screen.queryByText('Göreceli Performans (%)')).not.toBeInTheDocument();
  });

  it('getFxHistory reddedilse bile (catch) çökme olmaz; yine yükler', async () => {
    marketApi.getFxHistory.mockRejectedValue(new Error('ağ hatası'));
    renderPage();
    fireEvent.click(screen.getByRole('button', { name: 'Karşılaştır' }));
    // Hata yutulur → loaded=true → tablolar render olur, throw etmez
    expect(await screen.findByText('Performans Metrikleri')).toBeInTheDocument();
  });

  it('çipteki kaldır (X) butonuna basınca enstrüman listeden çıkar', () => {
    renderPage({ route: '/market/fx/compare?symbols=USD,EUR,GBP' });
    // GBP/TRY çipini ve onun kaldır butonunu bul
    const gbpChip = screen.getByText('GBP/TRY').closest('span');
    const removeBtn = within(gbpChip).getByRole('button');
    fireEvent.click(removeBtn);
    expect(screen.queryByText('GBP/TRY')).not.toBeInTheDocument();
    // Diğerleri kalır
    expect(screen.getByText('USD/TRY')).toBeInTheDocument();
    expect(screen.getByText('EUR/TRY')).toBeInTheDocument();
  });

  it('yatırım tutarı input değeri değiştirilebilir (TL simülasyonu)', async () => {
    renderPage();
    fireEvent.click(screen.getByRole('button', { name: 'Karşılaştır' }));
    await screen.findByText('TL Yatırım Simülasyonu');

    const investInput = screen.getByPlaceholderText('1000');
    fireEvent.change(investInput, { target: { value: '5000' } });
    expect(investInput).toHaveValue(5000);
  });
});
