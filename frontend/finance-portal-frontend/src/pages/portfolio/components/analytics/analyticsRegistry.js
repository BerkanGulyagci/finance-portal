import PortfolioAllocationChart from './PortfolioAllocationChart';
import PortfolioAssetDistributionChart from './PortfolioAssetDistributionChart';
import PortfolioCostValueChart from './PortfolioCostValueChart';
import PortfolioProfitLossChart from './PortfolioProfitLossChart';
import PortfolioTypeProfitLossChart from './PortfolioTypeProfitLossChart';
import PortfolioDailyStatusChart from './PortfolioDailyStatusChart';
import PortfolioCategoryChangeChart from './PortfolioCategoryChangeChart';
import PortfolioDailyContributionChart from './PortfolioDailyContributionChart';
import PortfolioGainersLosersCard from './PortfolioGainersLosersCard';

/**
 * Portföy analiz kartlarının ortak kaydı. Hem portföy "Grafikler" sekmesi hem de
 * Dashboard (panoya eklenen kartlar) aynı bileşeni anahtarla render edebilsin diye.
 * Tüm bileşenler { holdings, valuesHidden, currency } props'unu kabul eder (fazlası yok sayılır).
 * w/h: GridBoard başlangıç boyutu (12 sütunlu grid; satır yüksekliği ~30px) — içerik sığsın diye.
 */
export const ANALYTICS_CHARTS = [
  { key: 'allocation', label: 'Varlık Türü Dağılımı', Comp: PortfolioAllocationChart, w: 4, h: 18 },
  { key: 'assetDist', label: 'Varlık Bazlı Dağılım', Comp: PortfolioAssetDistributionChart, w: 4, h: 18 },
  { key: 'costValue', label: 'Maliyet / Piyasa Değeri', Comp: PortfolioCostValueChart, w: 4, h: 13 },
  { key: 'profitLoss', label: 'Varlık Bazlı Kar/Zarar', Comp: PortfolioProfitLossChart, w: 4, h: 13 },
  { key: 'typePL', label: 'Varlık Türü Bazlı Kar/Zarar', Comp: PortfolioTypeProfitLossChart, w: 4, h: 13 },
  { key: 'dailyStatus', label: 'Günlük Durum', Comp: PortfolioDailyStatusChart, w: 4, h: 12 },
  { key: 'categoryChange', label: 'Kategori Bazlı Ortalama Değişim', Comp: PortfolioCategoryChangeChart, w: 4, h: 13 },
  { key: 'dailyContribution', label: 'Günlük K/Z Katkısı', Comp: PortfolioDailyContributionChart, w: 4, h: 13 },
  { key: 'gainersLosers', label: 'En Çok Kazandıran / Kaybettiren', Comp: PortfolioGainersLosersCard, w: 8, h: 14 },
];

export const ANALYTICS_BY_KEY = Object.fromEntries(ANALYTICS_CHARTS.map(c => [c.key, c]));
