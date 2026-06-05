import PortfolioAllocationChart from './PortfolioAllocationChart';
import PortfolioAssetDistributionChart from './PortfolioAssetDistributionChart';
import PortfolioCostValueChart from './PortfolioCostValueChart';
import PortfolioProfitLossChart from './PortfolioProfitLossChart';
import PortfolioTypeProfitLossChart from './PortfolioTypeProfitLossChart';
import PortfolioDailyStatusChart from './PortfolioDailyStatusChart';
import PortfolioCategoryChangeChart from './PortfolioCategoryChangeChart';
import PortfolioDailyContributionChart from './PortfolioDailyContributionChart';
import PortfolioGainersLosersCard from './PortfolioGainersLosersCard';
import {
  PortfolioRiskScoreCard,
  PortfolioHealthScoreCard,
  PortfolioIdentityCard,
  PortfolioMonteCarloCard,
} from './PortfolioAiCards';

/**
 * Portföy analiz kartlarının ortak kaydı. Hem portföy "Grafikler" sekmesi hem de
 * Dashboard (panoya eklenen kartlar) aynı bileşeni anahtarla render edebilsin diye.
 * Çoğu bileşen { holdings, valuesHidden, currency } props'unu kabul eder; AI kartları (ai*)
 * { portfolioId, valuesHidden } kullanır (holdings yok sayılır) — veriyi /ai-analysis'ten çeker.
 * w/h: GridBoard başlangıç boyutu (12 sütunlu grid; satır yüksekliği ~30px) — içerik sığsın diye.
 */
export const ANALYTICS_CHARTS = [
  // h: GridBoard satır sayısı (~30px/satır) — içeriğe göre ayarlı; fazla boşluk bırakmaz.
  { key: 'allocation', label: 'Varlık Türü Dağılımı', Comp: PortfolioAllocationChart, w: 4, h: 15 },
  { key: 'assetDist', label: 'Varlık Bazlı Dağılım', Comp: PortfolioAssetDistributionChart, w: 4, h: 15 },
  { key: 'costValue', label: 'Maliyet / Piyasa Değeri', Comp: PortfolioCostValueChart, w: 4, h: 13 },
  { key: 'profitLoss', label: 'Varlık Bazlı Kar/Zarar', Comp: PortfolioProfitLossChart, w: 4, h: 13 },
  { key: 'typePL', label: 'Varlık Türü Bazlı Kar/Zarar', Comp: PortfolioTypeProfitLossChart, w: 4, h: 13 },
  { key: 'dailyStatus', label: 'Günlük Durum', Comp: PortfolioDailyStatusChart, w: 4, h: 12 },
  { key: 'categoryChange', label: 'Kategori Bazlı Ortalama Değişim', Comp: PortfolioCategoryChangeChart, w: 4, h: 13 },
  { key: 'dailyContribution', label: 'Günlük K/Z Katkısı', Comp: PortfolioDailyContributionChart, w: 4, h: 13 },
  // Risk + Sağlık skorları, Günlük K/Z Katkısı'nın yanına gelsin (4+3+3=10 → aynı satır).
  { key: 'aiRisk', label: 'Risk Skoru (AI)', Comp: PortfolioRiskScoreCard, w: 3, h: 11 },
  { key: 'aiHealth', label: 'Sağlık Skoru (AI)', Comp: PortfolioHealthScoreCard, w: 3, h: 11 },
  { key: 'gainersLosers', label: 'En Çok Kazandıran / Kaybettiren', Comp: PortfolioGainersLosersCard, w: 8, h: 10 },
  // ── Diğer AI Portföy Analizi kartları (veri: /ai-analysis) ────────────────────
  { key: 'aiIdentity', label: 'Portföy Kimliği (AI)', Comp: PortfolioIdentityCard, w: 3, h: 9 },
  { key: 'aiMonteCarlo', label: 'Monte Carlo Projeksiyon (AI)', Comp: PortfolioMonteCarloCard, w: 4, h: 13 },
];

export const ANALYTICS_BY_KEY = Object.fromEntries(ANALYTICS_CHARTS.map(c => [c.key, c]));
