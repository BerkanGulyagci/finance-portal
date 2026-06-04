import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';

// ─────────────────────────────────────────────────────────────────────────────
// MOCK'lar — kaynağı GERÇEKTEN okuyarak belirlendi.
//
// 1) klinecharts: jsdom'da canvas yok → init/dispose/registerIndicator stub'lanır.
//    init() chart instance döndürür; BondKlineChart üzerinde setStyles/applyNewData/
//    createIndicator/removeIndicator çağırır. Hepsini vi.fn ile karşıla.
// 2) marketApi: history/detail/bonds HTTP çağrılarını stub'la (ağ yok).
//    Yol: components/__tests__ → src/api  =  ../../../../../api/marketApi
// 3) react-router-dom: UniversalCompareButton <Link> kullanır → MemoryRouter ile sar.
// 4) LanguageContext: GERÇEK LanguageProvider (localStorage okur, tr varsayılan,
//    t(key) anahtarın kendisini döndürür) — bu yüzden Türkçe metinleri aratabiliriz.
// ─────────────────────────────────────────────────────────────────────────────

vi.mock('klinecharts', () => {
  const makeChart = () => ({
    applyNewData: vi.fn(),
    createIndicator: vi.fn(),
    removeIndicator: vi.fn(),
    resize: vi.fn(),
    setStyles: vi.fn(),
    subscribeAction: vi.fn(),
    convertFromPixel: vi.fn(),
    createOverlay: vi.fn(),
    removeOverlay: vi.fn(),
  });
  return {
    init: vi.fn(() => makeChart()),
    dispose: vi.fn(),
    registerIndicator: vi.fn(),
    registerOverlay: vi.fn(),
  };
});

vi.mock('../../../../../api/marketApi', () => ({
  getEvdsBondHistory: vi.fn(),
  getEvdsBondDetail: vi.fn(),
  getEvdsBonds: vi.fn(),
}));

import { init as klineInit } from 'klinecharts';
import {
  getEvdsBondHistory,
  getEvdsBondDetail,
  getEvdsBonds,
} from '../../../../../api/marketApi';
import { LanguageProvider } from '../../../../../context/LanguageContext';
import BondEvdsHistoryChart from '../BondEvdsHistoryChart';

// ─── Yardımcılar ──────────────────────────────────────────────────────────────

/** Son N gün için, bugüne kadar uzanan, artan gösterge değerli history noktaları üretir.
 *  Komponentin filterBondHistoryByDays cutoff'unu (son X gün) GEÇSİN diye tarihler GÜNCEL. */
function makePoints(count = 40, startValue = 100) {
  const pts = [];
  for (let i = count - 1; i >= 0; i--) {
    const d = new Date();
    d.setDate(d.getDate() - i);
    const date = d.toISOString().slice(0, 10);
    pts.push({
      date,
      dateText: date,
      indicatorValue: startValue + (count - 1 - i), // monoton artan → +%, "Yükseliş"
    });
  }
  return pts;
}

function renderChart(props = {}) {
  return render(
    <MemoryRouter>
      <LanguageProvider>
        <BondEvdsHistoryChart instrumentCode="TRT123456" {...props} />
      </LanguageProvider>
    </MemoryRouter>,
  );
}

// jsdom'da ResizeObserver yok; klinecharts mock'lansa da güvenli olsun diye global stub.
beforeEach(() => {
  localStorage.clear();
  vi.clearAllMocks();
  globalThis.ResizeObserver = class { observe() {} unobserve() {} disconnect() {} };
  // Varsayılan API cevapları — gerekirse test içinde override edilir.
  getEvdsBondHistory.mockResolvedValue([]);
  getEvdsBondDetail.mockResolvedValue(null);
  getEvdsBonds.mockResolvedValue({ items: [] });
});

// ─────────────────────────────────────────────────────────────────────────────
// 1) Smoke + temel iskelet
// ─────────────────────────────────────────────────────────────────────────────
describe('BondEvdsHistoryChart — render (smoke)', () => {
  it('basePoints ile render olur ve ana başlık + kaynak notu DOM\'da olur (throw etmez)', () => {
    renderChart({ basePoints: makePoints() });
    expect(
      screen.getByText('TCMB EVDS Gösterge Değeri Grafiği'),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/Kaynak: TCMB EVDS/),
    ).toBeInTheDocument();
  });

  it('tüm periyot butonlarını (1 Hafta … 5Y) render eder', () => {
    renderChart({ basePoints: makePoints() });
    ['1 Hafta', '1 Ay', '3 Ay', '6 Ay', '1 Yıl', '5Y'].forEach((label) => {
      expect(screen.getByRole('button', { name: label })).toBeInTheDocument();
    });
  });

  it('karşılaştırma yokken "Değer Grafiği" modu ve İndikatör + Karşılaştır kontrolleri görünür', () => {
    renderChart({ basePoints: makePoints() });
    expect(screen.getByText('Değer Grafiği')).toBeInTheDocument();
    expect(screen.queryByText('Performans Grafiği')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /İndikatör/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Karşılaştır/ })).toBeInTheDocument();
    // UniversalCompareButton (Link) — "Tümüyle Karşılaştır"
    expect(screen.getByRole('link', { name: /Tümüyle Karşılaştır/ })).toBeInTheDocument();
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// 2) Koşullu render dalları: veri var / yok
// ─────────────────────────────────────────────────────────────────────────────
describe('BondEvdsHistoryChart — koşullu render', () => {
  it('basePoints DOLU ise periyot değişim metni ("değişim:") gösterilir', () => {
    renderChart({ basePoints: makePoints(40) });
    // mainPeriodPct != null → "1 Ay değişim: +%..." metni
    expect(screen.getByText(/değişim:/)).toBeInTheDocument();
  });

  it('hiç nokta yoksa (boş veri) "grafik verisi bulunamadı" boş-durum mesajı gösterilir', async () => {
    // basePoints boş → mount'ta history çekilir (default mock []) → yükleme bitince
    // filteredPoints boş → BondKlineChart empty state. Async: yükleme tamamlanmasını bekle.
    renderChart({ basePoints: [] });
    expect(
      await screen.findByText('Bu dönem için grafik verisi bulunamadı.'),
    ).toBeInTheDocument();
    // Boş veride değişim yüzdesi de hesaplanamaz
    expect(screen.queryByText(/değişim:/)).not.toBeInTheDocument();
  });

  it('grafik instance (klineInit) dolu veriyle init edilir', () => {
    // klinecharts mock'lı init — dolu veride çağrılmalı (BondKlineChart useEffect)
    renderChart({ basePoints: makePoints(40) });
    expect(klineInit).toHaveBeenCalled();
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// 3) Period etkileşimi → API çağrısı
// ─────────────────────────────────────────────────────────────────────────────
describe('BondEvdsHistoryChart — periyot seçimi', () => {
  it('basePoints kısa periyotları (<=365g) karşıladığından mount\'ta history çekilmez', () => {
    renderChart({ basePoints: makePoints(40) });
    // Varsayılan period ONE_MONTH (30g <= 365) + basePoints dolu → loadMainHistory erken döner
    expect(getEvdsBondHistory).not.toHaveBeenCalled();
  });

  it('5Y (FIVE_YEARS, 1825g > 365) seçilince getEvdsBondHistory çağrılır', async () => {
    getEvdsBondHistory.mockResolvedValue(makePoints(40));
    renderChart({ basePoints: makePoints(40) });

    fireEvent.click(screen.getByRole('button', { name: '5Y' }));

    await waitFor(() => {
      expect(getEvdsBondHistory).toHaveBeenCalledWith('TRT123456', 'FIVE_YEARS');
    });
  });

  it('basePoints YOKKEN mount\'ta history çekilir (ONE_MONTH)', async () => {
    getEvdsBondHistory.mockResolvedValue(makePoints(40));
    renderChart({ basePoints: [] });
    await waitFor(() => {
      expect(getEvdsBondHistory).toHaveBeenCalledWith('TRT123456', 'ONE_MONTH');
    });
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// 4) Yenile butonu
// ─────────────────────────────────────────────────────────────────────────────
describe('BondEvdsHistoryChart — yenile', () => {
  it('Yenile butonuna tıklayınca loadMainHistory tekrar tetiklenir (5Y\'de API çağrısı tekrarlanır)', async () => {
    getEvdsBondHistory.mockResolvedValue(makePoints(40));
    renderChart({ basePoints: makePoints(40) });

    // 5Y'ye geç → ilk API çağrısı
    fireEvent.click(screen.getByRole('button', { name: '5Y' }));
    await waitFor(() => expect(getEvdsBondHistory).toHaveBeenCalledTimes(1));

    // Yenile (title="Yenile") → aynı period için tekrar çek
    fireEvent.click(screen.getByTitle('Yenile'));
    await waitFor(() => expect(getEvdsBondHistory).toHaveBeenCalledTimes(2));
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// 5) Hata dalı
// ─────────────────────────────────────────────────────────────────────────────
describe('BondEvdsHistoryChart — hata durumu', () => {
  it('history reject olursa "Grafik verisi şu an alınamadı." hata mesajı gösterilir', async () => {
    getEvdsBondHistory.mockRejectedValue(new Error('network'));
    // basePoints boş → mount'ta history çekilir → reject → error=true
    renderChart({ basePoints: [] });
    expect(
      await screen.findByText('Grafik verisi şu an alınamadı.'),
    ).toBeInTheDocument();
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// 6) İndikatör (MA) menüsü etkileşimi
// ─────────────────────────────────────────────────────────────────────────────
describe('BondEvdsHistoryChart — İndikatör menüsü', () => {
  it('İndikatör butonuna tıklayınca MA20/MA50/MA200 seçenekleri açılır', () => {
    renderChart({ basePoints: makePoints(40) });
    fireEvent.click(screen.getByRole('button', { name: /İndikatör/ }));
    expect(screen.getByRole('button', { name: 'MA20' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'MA50' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'MA200' })).toBeInTheDocument();
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// 7) Karşılaştırma akışı → performans modu + legend
// ─────────────────────────────────────────────────────────────────────────────
describe('BondEvdsHistoryChart — karşılaştırma seçimi', () => {
  it('Karşılaştır dropdown\'u açılınca getEvdsBonds ile kıymet listesi çekilir ve seçince performans moduna geçilir', async () => {
    getEvdsBondHistory.mockResolvedValue(makePoints(40));
    getEvdsBondDetail.mockResolvedValue({ instrumentCode: 'TRT999999' });
    getEvdsBonds.mockResolvedValue({
      items: [
        { instrumentCode: 'TRT999999', type: 'Kuponlu', maturityDate: '2030-01-01', indicatorValue: 95, remainingDays: 1500 },
      ],
    });

    renderChart({ basePoints: makePoints(40) });

    // BondCompareSelector trigger ("Karşılaştır")
    fireEvent.click(screen.getByRole('button', { name: /Karşılaştır/ }));

    // Dropdown açılınca liste çekilir
    await waitFor(() => expect(getEvdsBonds).toHaveBeenCalled());

    // Listedeki kıymeti seç → compareCode set → performans moduna geç + history+detail çek
    const option = await screen.findByText('TRT999999');
    fireEvent.click(option);

    await waitFor(() => {
      expect(screen.getByText('Performans Grafiği')).toBeInTheDocument();
    });
    // Karşılaştırma kıymeti için history + detail çekilmeli
    await waitFor(() => {
      expect(getEvdsBondHistory).toHaveBeenCalledWith('TRT999999', expect.any(String));
      expect(getEvdsBondDetail).toHaveBeenCalledWith('TRT999999');
    });
  });
});
