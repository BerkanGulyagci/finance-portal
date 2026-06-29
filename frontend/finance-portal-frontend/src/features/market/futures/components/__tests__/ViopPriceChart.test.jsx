import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';

// ─────────────────────────────────────────────────────────────────────────────
// MOCK'lar (kritik): jsdom'da canvas/ağ yok. klinecharts (canvas) ve marketApi
// (HTTP) mock'lanır; LanguageContext'in useTranslation'ı t:(k)=>k döndürür ki
// tüm alt bileşenler (DrawingToolbar, IndicatorMenu, ViopCompareSelector,
// TrendBadge, UniversalCompareButton) Provider olmadan anahtar metinle render olsun.
// ─────────────────────────────────────────────────────────────────────────────

// klinecharts — gerçek init canvas'a dokunur; tüm çağrılan metotları no-op mock'la.
vi.mock('klinecharts', () => ({
  init: vi.fn(() => ({
    applyNewData: vi.fn(),
    createIndicator: vi.fn(() => 'pane_x'),
    removeIndicator: vi.fn(),
    resize: vi.fn(),
    setStyles: vi.fn(),
    subscribeAction: vi.fn(),
    convertFromPixel: vi.fn(),
    createOverlay: vi.fn(),
    removeOverlay: vi.fn(),
    setOffsetRightDistance: vi.fn(),
    setBarSpace: vi.fn(),
  })),
  dispose: vi.fn(),
  registerOverlay: vi.fn(),
  registerIndicator: vi.fn(),
}));

// API — depth: __tests__ → components → futures → market → features → src → api
vi.mock('../../../../../api/marketApi', () => ({
  getViopChart: vi.fn(),
  getViopContracts: vi.fn(),
}));

// LanguageContext — t anahtarı aynen döndürür; vars ({n}) basit interpolasyon.
vi.mock('../../../../../context/LanguageContext', () => ({
  useTranslation: () => ({
    t: (key, vars) => {
      if (key == null) return '';
      let out = String(key);
      if (vars && typeof vars === 'object') {
        out = out.replace(/\{(\w+)\}/g, (m, name) =>
          Object.prototype.hasOwnProperty.call(vars, name) ? String(vars[name]) : m
        );
      }
      return out;
    },
    language: 'tr',
  }),
}));

import ViopPriceChart from '../ViopPriceChart';
import { getViopChart, getViopContracts } from '../../../../../api/marketApi';

// ── Test yardımcıları ────────────────────────────────────────────────────────

/** Komponentin beklediği point şekli: { timestamp, value } */
function makePoints(values, startTs = 1700000000000) {
  return values.map((v, i) => ({ timestamp: startTs + i * 86_400_000, value: v }));
}

const OK_POINTS = makePoints([100, 102, 101, 105, 110, 108, 112]); // +12% civarı

function renderChart(props = {}) {
  return render(
    <MemoryRouter>
      <ViopPriceChart contractName="XU030 0625" {...props} />
    </MemoryRouter>
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  localStorage.clear();

  // jsdom'da olmayan global'ler — komponent/altları kullanıyor olabilir.
  globalThis.ResizeObserver = class { observe() {} unobserve() {} disconnect() {} };
  // fitChartToWidth requestAnimationFrame kullanır → senkron çalıştır.
  vi.stubGlobal('requestAnimationFrame', (cb) => { cb(0); return 0; });
  vi.stubGlobal('cancelAnimationFrame', vi.fn());

  // Varsayılan: başarılı veri (her test override edebilir).
  getViopChart.mockResolvedValue({ success: true, data: OK_POINTS });
  getViopContracts.mockResolvedValue([]);
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('ViopPriceChart (Vitest + @testing-library/react, jsdom)', () => {
  describe('temel render (smoke)', () => {
    it('mock\'larla throw etmeden render olur ve başlığı gösterir', async () => {
      renderChart();
      expect(
        await screen.findByText('VİOP Sözleşme Grafiği')
      ).toBeInTheDocument();
    });

    it('mount\'ta ana grafik verisini contractName + varsayılan periyot (ONE_DAY) ile çeker', async () => {
      renderChart();
      await waitFor(() =>
        expect(getViopChart).toHaveBeenCalledWith('XU030 0625', 'ONE_DAY')
      );
    });

    it('tüm periyot butonlarını ve yenile kontrolünü render eder', async () => {
      renderChart();
      await screen.findByText('VİOP Sözleşme Grafiği');
      // PERIODS etiketleri
      ['1 Gün', '1 Hafta', '1 Ay', '3 Ay', '6 Ay', '1 Yıl'].forEach(lbl =>
        expect(screen.getByText(lbl)).toBeInTheDocument()
      );
      expect(screen.getByTitle('Yenile')).toBeInTheDocument();
    });
  });

  describe('koşullu render dalları (status)', () => {
    it('veri dolu (success+data) → "Değer Grafiği" gösterir, "veri bulunamadı" göstermez', async () => {
      renderChart();
      expect(await screen.findByText('Değer Grafiği')).toBeInTheDocument();
      // Grafik canvas konteyneri (id viop_kline_*) mount edilir; boş/empty mesajı YOK.
      expect(
        screen.queryByText('Bu dönem için VİOP grafik verisi bulunamadı.')
      ).not.toBeInTheDocument();
      expect(
        screen.queryByText('Bu sözleşme türü için grafik desteği henüz eklenmedi.')
      ).not.toBeInTheDocument();
    });

    it('boş veri (success:true, data:[]) → empty mesajı gösterir', async () => {
      getViopChart.mockResolvedValue({ success: true, data: [] });
      renderChart();
      expect(
        await screen.findByText('Bu dönem için VİOP grafik verisi bulunamadı.')
      ).toBeInTheDocument();
    });

    it('desteklenmeyen sözleşme (success:false) → "grafik desteği henüz eklenmedi" gösterir', async () => {
      getViopChart.mockResolvedValue({ success: false, data: [] });
      renderChart();
      expect(
        await screen.findByText('Bu sözleşme türü için grafik desteği henüz eklenmedi.')
      ).toBeInTheDocument();
    });

    it('genel hata (reject, status≠422) → "şu anda alınamadı" gösterir', async () => {
      getViopChart.mockRejectedValue(new Error('network down'));
      renderChart();
      expect(
        await screen.findByText('VİOP grafik verisi şu anda alınamadı.')
      ).toBeInTheDocument();
    });

    it('422 hatası → unsupported dalına düşer ("grafik desteği henüz eklenmedi")', async () => {
      getViopChart.mockRejectedValue({ response: { status: 422 } });
      renderChart();
      expect(
        await screen.findByText('Bu sözleşme türü için grafik desteği henüz eklenmedi.')
      ).toBeInTheDocument();
    });
  });

  describe('periyot değişimi (etkileşim)', () => {
    it('farklı periyot butonuna tıklayınca o periyotla yeniden veri çeker', async () => {
      renderChart();
      await waitFor(() =>
        expect(getViopChart).toHaveBeenCalledWith('XU030 0625', 'ONE_DAY')
      );

      fireEvent.click(screen.getByText('1 Ay'));

      await waitFor(() =>
        expect(getViopChart).toHaveBeenCalledWith('XU030 0625', 'ONE_MONTH')
      );
    });

    it('Yenile butonu mevcut periyotla tekrar fetch tetikler', async () => {
      renderChart();
      await waitFor(() => expect(getViopChart).toHaveBeenCalledTimes(1));

      fireEvent.click(screen.getByTitle('Yenile'));

      await waitFor(() =>
        expect(getViopChart).toHaveBeenCalledTimes(2)
      );
      // Hâlâ aynı (varsayılan) periyot ile çağrılmış olmalı
      expect(getViopChart).toHaveBeenLastCalledWith('XU030 0625', 'ONE_DAY');
    });
  });

  describe('karşılaştırma seçici (ViopCompareSelector)', () => {
    it('"Karşılaştır" butonu açılınca sözleşme listesini çeker', async () => {
      getViopContracts.mockResolvedValue([
        { name: 'XU100 0625', lastPrice: '9.999', changePercent: '+1,2%' },
      ]);
      renderChart();
      await screen.findByText('VİOP Sözleşme Grafiği');

      // Sayfa-içi "Karşılaştır" (UniversalCompareButton "Tümüyle Karşılaştır"dan ayrı)
      fireEvent.click(screen.getByText('Karşılaştır'));

      await waitFor(() => expect(getViopContracts).toHaveBeenCalledTimes(1));
      // Açılan dropdown'da arama input'u görünür
      expect(
        await screen.findByPlaceholderText('Sözleşme ara...')
      ).toBeInTheDocument();
      // Ana sözleşme dışındaki kontrat listede çıkar
      expect(await screen.findByText('XU100 0625')).toBeInTheDocument();
    });
  });

  describe('özet / trend göstergeleri', () => {
    it('dolu veride periyot değişim yüzdesini (pozitif → yeşil, +%) gösterir', async () => {
      renderChart();
      await screen.findByText('Değer Grafiği');
      // OK_POINTS 100→112 yükseliş → "değişim:" satırı + pozitif yüzde (+%...)
      const changeRow = await screen.findByText(/değişim:/);
      expect(changeRow).toBeInTheDocument();
      // "değişim:" ile aynı span içinde +%... yer alır (sibling text node'lar).
      expect(changeRow.closest('span')?.textContent).toMatch(/\+%/);
    });

    it('İndikatör menüsü butonu render edilir (RSI/MA toggle giriş noktası)', async () => {
      renderChart();
      await screen.findByText('Değer Grafiği');
      expect(screen.getByText('İndikatör')).toBeInTheDocument();
    });

    it('"Tümüyle Karşılaştır" (FUTURE) linki contractName ile oluşturulur', async () => {
      renderChart();
      await screen.findByText('VİOP Sözleşme Grafiği');
      const link = screen.getByText('Tümüyle Karşılaştır').closest('a');
      expect(link).toBeTruthy();
      expect(link.getAttribute('href')).toContain('FUTURE');
      expect(link.getAttribute('href')).toContain(encodeURIComponent('XU030 0625'));
    });
  });
});
