import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { LanguageProvider } from '../../../../../context/LanguageContext';

// getCryptoFearGreedSummary + getCryptoChart stub; recharts passthrough (jsdom SVG ölçemez).
// Gauge raw-SVG'dir (recharts değil) → sayı/etiket düz DOM olarak doğrulanabilir.

vi.mock('../../../../../api/marketApi', () => ({
  getCryptoFearGreedSummary: vi.fn(),
  getCryptoChart: vi.fn(),
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
    ReferenceArea: Noop,
  };
});

import { getCryptoFearGreedSummary, getCryptoChart } from '../../../../../api/marketApi';
import FearGreedChart from '../FearGreedChart';

const DAY = 86400000;
const D0 = Date.UTC(2026, 4, 1); // 2026-05-01

function series() {
  return [
    { timestamp: D0 + 0 * DAY, value: 12, classification: 'Extreme Fear' },
    { timestamp: D0 + 1 * DAY, value: 40, classification: 'Fear' },
    { timestamp: D0 + 2 * DAY, value: 55, classification: 'Neutral' },
    { timestamp: D0 + 3 * DAY, value: 72, classification: 'Greed' },
    { timestamp: D0 + 4 * DAY, value: 88, classification: 'Extreme Greed' },
  ];
}

function summary() {
  return {
    current: { value: 13, classification: 'Extreme Fear', timestamp: D0 + 4 * DAY },
    yesterday: { value: 16, classification: 'Extreme Fear', timestamp: null },
    lastWeek: { value: 35, classification: 'Fear', timestamp: null },
    lastMonth: { value: 50, classification: 'Neutral', timestamp: null },
    yearlyHigh: { value: 71, classification: 'Greed', timestamp: D0 - 100 * DAY },
    yearlyLow: { value: 5, classification: 'Extreme Fear', timestamp: D0 - 60 * DAY },
    series: series(),
  };
}

function priceChart() {
  return {
    prices: [
      [D0 + 0 * DAY + 3600_000, 2_400_000],
      [D0 + 1 * DAY + 3600_000, 2_450_000],
      // 3. gün (D0+2) fiyat YOK → o noktada price null (connectNulls)
      [D0 + 3 * DAY + 3600_000, 2_600_000],
      [D0 + 4 * DAY + 3600_000, 2_700_000],
    ],
  };
}

function renderChart(props = {}) {
  return render(
    <LanguageProvider>
      <FearGreedChart coinId="bitcoin" coinName="Bitcoin" currency="try" days={90} {...props} />
    </LanguageProvider>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  localStorage.clear();
  getCryptoChart.mockResolvedValue(priceChart());
});

describe('FearGreedChart', () => {
  it('mount edilince getCryptoFearGreedSummary verilen `days` ile çağrılır', async () => {
    getCryptoFearGreedSummary.mockResolvedValue(summary());
    renderChart({ days: 90 });
    await waitFor(() => expect(getCryptoFearGreedSummary).toHaveBeenCalledWith(90));
  });

  it('sağ panel başlığı "Korku ve Hırs Endeksi Grafiği" gösterir', async () => {
    getCryptoFearGreedSummary.mockResolvedValue(summary());
    renderChart();
    expect(await screen.findByText('Korku ve Hırs Endeksi Grafiği')).toBeInTheDocument();
  });

  it('gauge güncel değeri (13) + TR sınıflandırma (Aşırı Korku) gösterir', async () => {
    getCryptoFearGreedSummary.mockResolvedValue(summary());
    renderChart();
    expect(await screen.findByText('13')).toBeInTheDocument();
    expect(screen.getAllByText('Aşırı Korku').length).toBeGreaterThan(0);
  });

  it('geçmiş rozetler Dün/Geçen Hafta/Geçen Ay değerlerini gösterir', async () => {
    getCryptoFearGreedSummary.mockResolvedValue(summary());
    renderChart();
    expect(await screen.findByText('Dün')).toBeInTheDocument();
    expect(screen.getByText('Geçen Hafta')).toBeInTheDocument();
    expect(screen.getByText('Geçen Ay')).toBeInTheDocument();
    expect(screen.getByText(/16 · Aşırı Korku/)).toBeInTheDocument();
    expect(screen.getByText(/35 · Korku/)).toBeInTheDocument();
  });

  it('yıllık en yüksek/düşük rozetleri gösterir (71 Hırs, 5 Aşırı Korku)', async () => {
    getCryptoFearGreedSummary.mockResolvedValue(summary());
    renderChart();
    expect(await screen.findByText('Yıllık En Yüksek')).toBeInTheDocument();
    expect(screen.getByText('Yıllık En Düşük')).toBeInTheDocument();
    expect(screen.getByText(/71 · Hırs/)).toBeInTheDocument();
    expect(screen.getByText(/5 · Aşırı Korku/)).toBeInTheDocument();
  });

  it('çift eksen kurulur: sol "price" + sağ "fng" (domain 0-100, orientation right)', async () => {
    getCryptoFearGreedSummary.mockResolvedValue(summary());
    renderChart();
    await screen.findByTestId('composed-chart');
    expect(screen.getByTestId('yaxis-price')).toBeInTheDocument();
    const fng = screen.getByTestId('yaxis-fng');
    expect(fng).toHaveAttribute('data-orientation', 'right');
    expect(fng).toHaveAttribute('data-domain', '[0,100]');
  });

  it('iki çizgi de (price + value) render edilir', async () => {
    getCryptoFearGreedSummary.mockResolvedValue(summary());
    renderChart();
    await screen.findByTestId('composed-chart');
    expect(screen.getByTestId('line-price')).toBeInTheDocument();
    expect(screen.getByTestId('line-value')).toBeInTheDocument();
  });

  it('merge gün-bazında yapılır: seri nokta sayısı = grafik nokta sayısı (5)', async () => {
    getCryptoFearGreedSummary.mockResolvedValue(summary());
    renderChart();
    const chart = await screen.findByTestId('composed-chart');
    expect(chart).toHaveAttribute('data-points', '5');
  });

  it('summary null gelince "Veri bulunamadı" fallback gösterir', async () => {
    getCryptoFearGreedSummary.mockResolvedValue(null);
    renderChart();
    expect(await screen.findByText('Veri bulunamadı')).toBeInTheDocument();
    expect(screen.queryByTestId('composed-chart')).not.toBeInTheDocument();
  });

  it('API hatasında "Veri bulunamadı" fallback gösterir (throw etmez)', async () => {
    getCryptoFearGreedSummary.mockRejectedValue(new Error('network'));
    renderChart();
    expect(await screen.findByText('Veri bulunamadı')).toBeInTheDocument();
  });
});
