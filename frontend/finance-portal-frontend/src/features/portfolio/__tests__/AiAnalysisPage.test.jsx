import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';

// ── Mock'lar ─────────────────────────────────────────────────────────────────
// AiAnalysisPage; tek API çağrısı (getPortfolioAiAnalysis), recharts (Monte Carlo
// yelpazesi) ve iki ağır alt-kart (RebalanceCard/ForecastCard) çeker. jsdom'da
// ağ + recharts-boyut yoktur → veri kaynağını ve alt-kartları stub'larız; böylece
// test SADECE AiAnalysisPage'in kendi mantığını (yükleniyor/hata/boş + skor/metrik/
// yoğunlaşma/stres/sinyal kartları + AI rapor biçimleme + koşullu render) doğrular.

const getPortfolioAiAnalysis = vi.fn();
vi.mock('../../../api/portfolioApi', () => ({
  getPortfolioAiAnalysis: (...a) => getPortfolioAiAnalysis(...a),
}));

// recharts: ResponsiveContainer jsdom'da 0-boyut → çocukları hiç render etmez.
// Sadece onu, içeriği geçiren bir sarmalayıcıya indirgiyoruz; SVG kalanını jsdom
// çizebilir (zaten DOM varlığını test etmiyoruz, kartın render OLMASINI test ediyoruz).
vi.mock('recharts', async (orig) => {
  const actual = await orig();
  return { ...actual, ResponsiveContainer: ({ children }) => <div>{children}</div> };
});

// Alt kartlar: yalnızca tanınabilir bir işaret + aldıkları prop'un köprüsü.
// (Kendi testleri ayrı; burada AiAnalysisPage onları doğru koşulda gösteriyor mu bakıyoruz.)
vi.mock('../analysis/RebalanceCard', () => ({
  default: ({ rebalance, portfolioId }) => (
    <div data-testid="rebalance-card">rebalance:{portfolioId}:{rebalance?.driftPercent}</div>
  ),
}));
vi.mock('../analysis/ForecastCard', () => ({
  default: ({ forecast }) => (
    <div data-testid="forecast-card">forecast:{String(forecast?.available)}</div>
  ),
}));

import AiAnalysisPage from '../AiAnalysisPage';
import { LanguageProvider } from '../../../context/LanguageContext';

// useParams().id = "7" gelsin diye gerçek route ile sarıyoruz. Dil "tr" →
// t(key) anahtarın kendisini döndürür, {n}/{x} gibi yer-tutucular gerçekten
// enterpole edilir (LanguageContext.t).
function renderPage(id = '7') {
  return render(
    <MemoryRouter initialEntries={[`/portfolio/${id}/ai-analysis`]}>
      <LanguageProvider>
        <Routes>
          <Route path="/portfolio/:id/ai-analysis" element={<AiAnalysisPage />} />
        </Routes>
      </LanguageProvider>
    </MemoryRouter>
  );
}

// Komponentin okuduğu tüm alanları kapsayan dolu bir analiz nesnesi.
// Alt nesneler (riskMetrics.available, monteCarlo.available, stressTests[].available,
// assetSignals, forecast, rebalance, aiReportAvailable) açık → tüm koşullu kartlar çıkar.
function fullAnalysis(over = {}) {
  return {
    name: 'Ana Portföy',
    holdingsCount: 5,
    totalValueTry: 125000,
    totalProfitLossPercent: 12.5,
    riskScore: 48,
    riskLabel: 'Orta Risk',
    healthScore: 71,
    healthLabel: 'Sağlıklı',
    riskFactors: [{ label: 'Volatilite', contribution: 20, detail: 'oynaklık' }],
    healthFactors: [{ label: 'Çeşitlendirme', contribution: 15, detail: 'dağılım' }],
    classification: {
      profile: 'BALANCED',
      label: 'Dengeli',
      detail: 'Büyüme ve koruma dengeli.',
      growthWeightPercent: 55,
      defensiveWeightPercent: 45,
    },
    riskMetrics: {
      available: true,
      annualReturnPercent: 18.2,
      annualVolatilityPercent: 22.4,
      sharpe: 0.81,
      sortino: 1.12,
      maxDrawdownPercent: -14.3,
      beta: 0.93,
    },
    concentration: {
      topHoldingPercent: 34.5,
      topHoldingLabel: 'THYAO',
      top3Percent: 61.2,
      herfindahl: 0.21,
      label: 'Orta yoğunlaşma',
    },
    monteCarlo: {
      available: true,
      horizonMonths: 12,
      medianEndValue: 140000,
      expectedReturnPercent: 12,
      p95EndValue: 180000,
      p5EndValue: 100000,
      probLossPercent: 18,
      note: 'Geçmiş oynaklık varsayımı.',
      bands: [
        { month: 1, p5: 120000, p50: 126000, p95: 132000 },
        { month: 6, p5: 110000, p50: 133000, p95: 156000 },
        { month: 12, p5: 100000, p50: 140000, p95: 180000 },
      ],
    },
    forecast: { available: true, portfolioHorizons: [], assetForecasts: [] },
    stressTests: [
      { key: '2008', label: '2008 Krizi', period: '2008', available: true, impactPercent: -28.4, note: 'proxy' },
      { key: '2020', label: 'Covid', period: '2020', available: false, impactPercent: null },
    ],
    assetSignals: [
      {
        symbol: 'GARAN',
        name: 'Türk Hava Yolları',
        weightPercent: 34.5,
        trend: 'UP',
        trendLabel: 'Yükseliş',
        range52wPercent: 72,
        profitLossPercent: 21.3,
        momentum1mPercent: 4.1,
        momentum3mPercent: 9.7,
        maState: 'MA20 > MA50',
      },
    ],
    rebalance: { driftPercent: 9.4, basedOnProfile: 'BALANCED', profileLabel: 'Dengeli', items: [], note: 'n' },
    aiReportAvailable: true,
    aiReport: '**Teşhis**\nPortföy dengeli görünüyor.\n- Birinci madde\n- İkinci madde',
    notes: ['Bu bir yatırım tavsiyesi değildir.'],
    ...over,
  };
}

beforeEach(() => {
  vi.clearAllMocks();
  localStorage.clear();
  // jsdom'da olmayanlar — recharts/ölçüm güvenliği için zararsız stub'lar.
  globalThis.ResizeObserver = class { observe() {} unobserve() {} disconnect() {} };
  // Varsayılan: dolu analiz; testler gerektiğinde override eder.
  getPortfolioAiAnalysis.mockResolvedValue(fullAnalysis());
});

describe('AiAnalysisPage (jsdom + testing-library + React 19)', () => {
  it('başlangıçta yükleniyor durumunu + ilerleme çubuğunu gösterir', async () => {
    // Promise çözülmeden loading dalını yakala (interval'a bağlı kalmadan, anlık assert).
    let resolve;
    getPortfolioAiAnalysis.mockReturnValue(new Promise((r) => { resolve = r; }));
    renderPage();

    expect(screen.getByText('Portföyün AI ile analiz ediliyor…')).toBeInTheDocument();
    // Başlangıç ilerlemesi %8 → metin DOM'da.
    expect(screen.getByText('%8')).toBeInTheDocument();

    resolve(fullAnalysis());
    await waitFor(() =>
      expect(screen.queryByText('Portföyün AI ile analiz ediliyor…')).not.toBeInTheDocument()
    );
  });

  it('id ile getPortfolioAiAnalysis çağrılır (useParams köprüsü)', async () => {
    renderPage('42');
    await screen.findByRole('heading', { name: 'AI Portföy Analizi' });
    // Artık UI dili de geçilir (id, lang) → ikinci argüman dil ('tr' default).
    expect(getPortfolioAiAnalysis).toHaveBeenCalledWith('42', expect.any(String));
  });

  it('hata → mesajı ve "Portföye dön" bağlantısını gösterir', async () => {
    getPortfolioAiAnalysis.mockRejectedValue({ response: { data: { message: 'Sunucu hatası' } } });
    renderPage();

    expect(await screen.findByText('Sunucu hatası')).toBeInTheDocument();
    expect(screen.getByText(/Portföye dön/)).toBeInTheDocument();
    // Hata dalında başlık/skor kartları olmamalı.
    expect(screen.queryByRole('heading', { name: 'AI Portföy Analizi' })).not.toBeInTheDocument();
  });

  it('hata mesajı yoksa varsayılan "Analiz alınamadı." gösterilir', async () => {
    getPortfolioAiAnalysis.mockRejectedValue(new Error('network'));
    renderPage();
    expect(await screen.findByText('Analiz alınamadı.')).toBeInTheDocument();
  });

  it('data null çözülürse hiçbir şey render etmez (loading biter, içerik yok)', async () => {
    getPortfolioAiAnalysis.mockResolvedValue(null);
    const { container } = renderPage();
    await waitFor(() =>
      expect(screen.queryByText('Portföyün AI ile analiz ediliyor…')).not.toBeInTheDocument()
    );
    // if (!data) return null → başlık çıkmaz, kök boş kalır.
    expect(screen.queryByRole('heading', { name: 'AI Portföy Analizi' })).not.toBeInTheDocument();
    expect(container).toBeEmptyDOMElement();
  });

  it('dolu veri → başlık, profil rozeti, portföy adı/pozisyon ve toplam değer render eder', async () => {
    renderPage();

    expect(await screen.findByRole('heading', { name: 'AI Portföy Analizi' })).toBeInTheDocument();
    // Sınıflandırma etiketi rozeti (başlık satırında) + Portföy Kimliği kartında → en az bir kez.
    expect(screen.getAllByText('Dengeli').length).toBeGreaterThan(0);
    // "Ana Portföy · 5 pozisyon" — ad/sayı/t('pozisyon') aynı <p> içinde birden
    // çok metin düğümü; regex ile <p>'nin tüm içeriğini eşle (alt eleman yok).
    expect(screen.getByText(/Ana Portföy · 5 pozisyon/)).toBeInTheDocument();
    // Toplam değer tr-TR para biçimi (mock'ta benzersiz).
    expect(screen.getByText('125.000 ₺')).toBeInTheDocument();
  });

  it('skor halkaları (risk/sağlık) ve skor değerlerini gösterir', async () => {
    renderPage();
    await screen.findByRole('heading', { name: 'AI Portföy Analizi' });

    expect(screen.getByText('Risk Skoru')).toBeInTheDocument();
    expect(screen.getByText('Sağlık Skoru')).toBeInTheDocument();
    // ScoreGauge ortasındaki sayı (riskScore=48, healthScore=71).
    expect(screen.getByText('48')).toBeInTheDocument();
    expect(screen.getByText('71')).toBeInTheDocument();
    // Etiketler t(key) ile.
    expect(screen.getByText('Orta Risk')).toBeInTheDocument();
    expect(screen.getByText('Sağlıklı')).toBeInTheDocument();
  });

  it('risk-ayarlı metrikleri (available=true) Sharpe/Sortino/Beta ile gösterir', async () => {
    renderPage();
    await screen.findByRole('heading', { name: 'AI Portföy Analizi' });

    expect(screen.getByText('Risk-Ayarlı Metrikler')).toBeInTheDocument();
    expect(screen.getByText('Sharpe')).toBeInTheDocument();
    expect(screen.getByText('Sortino')).toBeInTheDocument();
    expect(screen.getByText('Beta (BIST100)')).toBeInTheDocument();
    // fmtNum(0.81) tr-TR → "0,81".
    expect(screen.getByText('0,81')).toBeInTheDocument();
  });

  it('risk metrikleri available=false → "geçmiş çok kısa" mesajı, Sharpe satırı çıkmaz', async () => {
    getPortfolioAiAnalysis.mockResolvedValue(
      fullAnalysis({ riskMetrics: { available: false, note: 'yetersiz veri' } })
    );
    renderPage();
    await screen.findByRole('heading', { name: 'AI Portföy Analizi' });

    expect(screen.getByText(/geçmiş çok kısa/)).toBeInTheDocument();
    expect(screen.queryByText('Sharpe')).not.toBeInTheDocument();
  });

  it('yoğunlaşma kartı: en büyük pozisyon yüzdesi + Herfindahl + etiket', async () => {
    renderPage();
    await screen.findByRole('heading', { name: 'AI Portföy Analizi' });

    expect(screen.getByText('Yoğunlaşma Riski')).toBeInTheDocument();
    // topHoldingLabel etiketi yoğunlaşma kartına özgü (": THYAO" ekiyle).
    expect(screen.getByText(/THYAO/)).toBeInTheDocument();
    // top3Percent=61.2 → "%61,2" (mock'ta benzersiz).
    expect(screen.getByText('%61,2')).toBeInTheDocument();
    expect(screen.getByText('Herfindahl')).toBeInTheDocument();
    expect(screen.getByText('Orta yoğunlaşma')).toBeInTheDocument();
  });

  it('Monte Carlo kartı (available=true) başlık + özet metrikleri ve notu gösterir', async () => {
    renderPage();
    await screen.findByRole('heading', { name: 'AI Portföy Analizi' });

    expect(screen.getByText(/Monte Carlo Projeksiyon/)).toBeInTheDocument();
    expect(screen.getByText('İyimser (%95)')).toBeInTheDocument();
    expect(screen.getByText('Kötümser (%5)')).toBeInTheDocument();
    expect(screen.getByText('Geçmiş oynaklık varsayımı.')).toBeInTheDocument();
  });

  it('Monte Carlo available=false → kart hiç render edilmez', async () => {
    getPortfolioAiAnalysis.mockResolvedValue(fullAnalysis({ monteCarlo: { available: false } }));
    renderPage();
    await screen.findByRole('heading', { name: 'AI Portföy Analizi' });
    expect(screen.queryByText(/Monte Carlo Projeksiyon/)).not.toBeInTheDocument();
  });

  it('tarihsel stres testleri: yalnız available=true olan kayıt görünür', async () => {
    renderPage();
    await screen.findByRole('heading', { name: 'AI Portföy Analizi' });

    expect(screen.getByText(/Tarihsel Stres Testleri/)).toBeInTheDocument();
    // available olan 2008 görünür, available=false Covid görünmez.
    expect(screen.getByText('2008 Krizi')).toBeInTheDocument();
    expect(screen.queryByText('Covid')).not.toBeInTheDocument();
    // impactPercent -28.4 → "%-28,4"
    expect(screen.getByText('%-28,4')).toBeInTheDocument();
  });

  it('varlık bazlı sinyal kartı: ad, ağırlık ve momentum metrikleri', async () => {
    renderPage();
    await screen.findByRole('heading', { name: 'AI Portföy Analizi' });

    expect(screen.getByText(/Varlık Bazlı Sinyaller/)).toBeInTheDocument();
    expect(screen.getByText('Türk Hava Yolları')).toBeInTheDocument();
    expect(screen.getByText('MA20 > MA50')).toBeInTheDocument();
    // momentum1mPercent=4.1 → "%4,1" (Mini değer)
    expect(screen.getByText('%4,1')).toBeInTheDocument();
  });

  it('alt kartlar (ForecastCard/RebalanceCard) doğru prop ile mount edilir', async () => {
    renderPage('7');
    await screen.findByRole('heading', { name: 'AI Portföy Analizi' });

    // ForecastCard forecast.available köprüsü.
    expect(screen.getByTestId('forecast-card')).toHaveTextContent('forecast:true');
    // RebalanceCard portfolioId (route id) + drift köprüsü.
    expect(screen.getByTestId('rebalance-card')).toHaveTextContent('rebalance:7:9.4');
  });

  it('AI rapor (available=true) başlık + madde olarak biçimlenir', async () => {
    renderPage();
    await screen.findByRole('heading', { name: 'AI Portföy Analizi' });

    expect(screen.getByText('AI Yorum Raporu')).toBeInTheDocument();
    // **Teşhis** → h4 başlık (yıldızlar soyulmuş).
    expect(screen.getByRole('heading', { name: 'Teşhis' })).toBeInTheDocument();
    // "- Birinci madde" → liste öğesi.
    expect(screen.getByText('Birinci madde')).toBeInTheDocument();
    expect(screen.getByText('İkinci madde')).toBeInTheDocument();
  });

  it('AI rapor yoksa ama portföy DOLU (holdingsCount>0) → "model meşgul/kota" mesajı', async () => {
    getPortfolioAiAnalysis.mockResolvedValue(
      fullAnalysis({ aiReportAvailable: false, aiReport: null }) // holdingsCount=5 (dolu)
    );
    renderPage();
    await screen.findByRole('heading', { name: 'AI Portföy Analizi' });

    expect(screen.getByText(/AI yorumu şu an kullanılamıyor/)).toBeInTheDocument();
    // Boş-portföy mesajı bu durumda ÇIKMAMALI.
    expect(screen.queryByText(/Portföyünüzde işlem\/pozisyon bulunamadı/)).not.toBeInTheDocument();
  });

  it('AI rapor yoksa ve portföy BOŞ (holdingsCount=0) → "işlem bulunamadı" mesajı (meşgul/kota DEĞİL)', async () => {
    getPortfolioAiAnalysis.mockResolvedValue(
      fullAnalysis({ aiReportAvailable: false, aiReport: null, holdingsCount: 0 })
    );
    renderPage();
    await screen.findByRole('heading', { name: 'AI Portföy Analizi' });

    expect(screen.getByText(/Portföyünüzde işlem\/pozisyon bulunamadı/)).toBeInTheDocument();
    // Yanlış "model meşgul/kota" mesajı boş portföyde GÖSTERİLMEMELİ.
    expect(screen.queryByText(/AI yorumu şu an kullanılamıyor/)).not.toBeInTheDocument();
  });

  it('notlar (disclaimer) listesi render edilir', async () => {
    renderPage();
    await screen.findByRole('heading', { name: 'AI Portföy Analizi' });
    expect(screen.getByText('Bu bir yatırım tavsiyesi değildir.')).toBeInTheDocument();
  });

  it('classification null → "Sınıflandırma için veri yok." ve fallback profil', async () => {
    getPortfolioAiAnalysis.mockResolvedValue(fullAnalysis({ classification: null }));
    renderPage();
    await screen.findByRole('heading', { name: 'AI Portföy Analizi' });

    expect(screen.getByText('Sınıflandırma için veri yok.')).toBeInTheDocument();
    // Başlık satırındaki rozet (cls) çıkmaz; Portföy Kimliği kartı yine de başlıkta var.
    expect(screen.getByText('Portföy Kimliği')).toBeInTheDocument();
  });

  it('boş diziler → sinyal/stres kartları render edilmez (koşullu render)', async () => {
    getPortfolioAiAnalysis.mockResolvedValue(
      fullAnalysis({ assetSignals: [], stressTests: [], forecast: null, rebalance: null })
    );
    renderPage();
    await screen.findByRole('heading', { name: 'AI Portföy Analizi' });

    expect(screen.queryByText(/Varlık Bazlı Sinyaller/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Tarihsel Stres Testleri/)).not.toBeInTheDocument();
    expect(screen.queryByTestId('forecast-card')).not.toBeInTheDocument();
    expect(screen.queryByTestId('rebalance-card')).not.toBeInTheDocument();
  });
});
