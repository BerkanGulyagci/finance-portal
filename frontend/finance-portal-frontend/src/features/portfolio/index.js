/**
 * Portfolio feature — public API.
 *
 * Bu dosya feature'ın dışarıdan görülebilen tüm export'larını tek bir noktadan açar
 * (barrel pattern). Diğer feature'lar (dashboard, market, profile, alarms vb.)
 * portfolio'nun iç dosya yapısına bağımlı olmadan import yapabilir.
 *
 * Kural: Portfolio'nun iç yapısı değişse bile bu dosyadan expose edilenler aynı
 * import path'iyle (`from '.../features/portfolio'`) erişilebilir kalır.
 *
 * Kullanım (dış feature'lardan):
 *   import { calculateAllocationByType, WL_CHART_BY_KEY } from '../portfolio';
 *
 * NOT: InstrumentSearchModal 5+ feature kullandığı için global'e terfi etti —
 * artık 'src/components/instrument/InstrumentSearchModal' üzerinden import edilir.
 */

// ── Sayfa-level component'ler (cross-feature kullanılıyor) ──────────────────
export { default as AddTransactionModal }   from './components/AddTransactionModal';

// ── Helper fonksiyonlar ─────────────────────────────────────────────────────
export {
  calculateAllocationByType,
  CHART_DONUT_COLORS,
  formatSharePercent,
} from './utils/portfolioAnalyticsHelpers';
export { getWatchlistDetailPath } from './constants/watchlistMarketRoutes';

// ── Registry'ler (dashboard sürükle-bırak grid'i bunları okur) ──────────────
export { ANALYTICS_BY_KEY } from './components/analytics/analyticsRegistry';
export { WL_CHART_BY_KEY }  from './components/watchlistChartRegistry';
