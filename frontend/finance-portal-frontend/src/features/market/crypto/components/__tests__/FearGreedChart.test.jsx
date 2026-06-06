import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { LanguageProvider } from '../../../../../context/LanguageContext';

// ─────────────────────────────────────────────────────────────────────────────
// MOCK STRATEJİSİ
// 1) marketApi.getCryptoFearGreed: ağ yok → stub (her test kendi verisini verir).
// 2) recharts: jsdom SVG ölçemediğinden gerçek grafik throw eder/0 boyut verir.
//    Test ettiğimiz şey grafik İÇİ değil; rozet, başlık, loading/empty DÜZ DOM ve
//    merge'in çizgilere doğru `dataKey` verdiği. Bu yüzden recharts passthrough stub.
//    <Line> stub'ı dataKey/name'i data-testid'e yansıtır → merge sonucunu doğrulayabiliriz.
// 3) LanguageContext: GERÇEK (tr varsayılan → t(key) anahtarı döndürür) → TR etiket araması.
// ─────────────────────────────────────────────────────────────────────────────

vi.mock('../../../../../api/marketApi', () => ({
  getCryptoFearGreed: vi.fn(),
}));

vi.mock('recharts', () => {
  const Passthrough = ({ children }) => <div>{children}</div>;
  const Noop = () => null;
  return {
    ResponsiveContainer: Passthrough,
    ComposedChart: ({ children, data }) => (
      <div data-testid="composed-chart" data-points={data?.length ?? 0}>{children}</div>
    ),
    Line: ({ dataKey, name }) => <div data-testid={`line-${dataKey}`} data-name={name} />,
    XAxis: Noop,
    YAxis: ({ yAxisId, orientation, domain }) => (
      <div data-testid={`yaxis-${yAxisId}`} data-orientation={orientation ?? 'left'} data-domain={JSON.stringify(domain ?? null)} />
    ),
    CartesianGrid: Noop,
    Tooltip: Noop,
  };
});

import { getCryptoFearGreed } from '../../../../../api/marketApi';
import FearGreedChart from '../FearGreedChart';

// ── Test verisi ──────────────────────────────────────────────────────────────
const DAY = 86400000;
// 5 ardışık gün, UTC gün başlangıçları.
const D0 = Date.UTC(2026, 4, 1); // 2026-05-01

/** F&G satırları: backend formatı { timestamp(ms), value, classification }. */
function fngRows() {
  return [
    { timestamp: D0 + 0 * DAY, value: 12, classification: 'Extreme Fear' },
    { timestamp: D0 + 1 * DAY, value: 40, classification: 'Fear' },
    { timestamp: D0 + 2 * DAY, value: 55, classification: 'Neutral' },
    { timestamp: D0 + 3 * DAY, value: 72, classification: 'Greed' },
    { timestamp: D0 + 4 * DAY, value: 88, classification: 'Extreme Greed' },
  ];
}

/** Fiyat serisi (chartData şekli): { ts(ms), price }. Gün-ortası timestamp → gün eşleşmesi sınanır. */
function priceData() {
  return [
    { ts: D0 + 0 * DAY + 3600_000, price: 2_400_000 },
    { ts: D0 + 1 * DAY + 3600_000, price: 2_450_000 },
    // 3. gün (D0+2) için fiyat YOK → o noktada price null kalmalı (connectNulls)
    { ts: D0 + 3 * DAY + 3600_000, price: 2_600_000 },
    { ts: D0 + 4 * DAY + 3600_000, price: 2_700_000 },
  ];
}

function renderChart(props = {}) {
  return render(
    <LanguageProvider>
      <FearGreedChart priceData={priceData()} coinName="Bitcoin" days={90} {...props} />
    </LanguageProvider>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  localStorage.clear();
});

describe('FearGreedChart', () => {
  it('mount edilince getCryptoFearGreed verilen `days` ile çağrılır', async () => {
    getCryptoFearGreed.mockResolvedValue(fngRows());
    renderChart({ days: 90 });
    await waitFor(() => expect(getCryptoFearGreed).toHaveBeenCalledWith(90));
  });

  it('başlık coin adıyla "Fiyatı vs Korku & Açgözlülük" gösterir', async () => {
    getCryptoFearGreed.mockResolvedValue(fngRows());
    renderChart();
    expect(await screen.findByText(/Bitcoin Fiyatı vs Korku & Açgözlülük/)).toBeInTheDocument();
  });

  it('güncel (en yeni) F&G rozeti değer + TR sınıflandırma gösterir (88 — Aşırı Açgözlülük)', async () => {
    getCryptoFearGreed.mockResolvedValue(fngRows());
    renderChart();
    expect(await screen.findByText(/88 — Aşırı Açgözlülük/)).toBeInTheDocument();
  });

  it('çift eksen kurulur: sol "price" + sağ "fng" (domain 0-100, orientation right)', async () => {
    getCryptoFearGreed.mockResolvedValue(fngRows());
    renderChart();
    await screen.findByTestId('composed-chart');
    expect(screen.getByTestId('yaxis-price')).toBeInTheDocument();
    const fng = screen.getByTestId('yaxis-fng');
    expect(fng).toHaveAttribute('data-orientation', 'right');
    expect(fng).toHaveAttribute('data-domain', '[0,100]');
  });

  it('iki çizgi de (price + value) render edilir', async () => {
    getCryptoFearGreed.mockResolvedValue(fngRows());
    renderChart();
    await screen.findByTestId('composed-chart');
    expect(screen.getByTestId('line-price')).toBeInTheDocument();
    expect(screen.getByTestId('line-value')).toBeInTheDocument();
  });

  it('merge gün-bazında yapılır: F&G nokta sayısı = grafik nokta sayısı (5), fiyatsız gün null kalır', async () => {
    getCryptoFearGreed.mockResolvedValue(fngRows());
    renderChart();
    const chart = await screen.findByTestId('composed-chart');
    // 5 F&G günü taban → 5 nokta (fiyatı eksik 3. gün de noktada kalır, price=null).
    expect(chart).toHaveAttribute('data-points', '5');
  });

  it('boş F&G verisinde "Veri bulunamadı" fallback gösterir', async () => {
    getCryptoFearGreed.mockResolvedValue([]);
    renderChart();
    expect(await screen.findByText('Veri bulunamadı')).toBeInTheDocument();
    expect(screen.queryByTestId('composed-chart')).not.toBeInTheDocument();
  });

  it('API hatasında "Veri bulunamadı" fallback gösterir (throw etmez)', async () => {
    getCryptoFearGreed.mockRejectedValue(new Error('network'));
    renderChart();
    expect(await screen.findByText('Veri bulunamadı')).toBeInTheDocument();
  });
});
