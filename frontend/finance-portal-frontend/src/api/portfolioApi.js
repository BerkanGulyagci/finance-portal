import client from '../lib/http';

// ── Portfolio CRUD ────────────────────────────────────────────────────────────

/**
 * `getMyPortfolios()` 6 ayrı yerden çağrılıyor (PortfolioPage, DashboardPage,
 * Header PortfolioNavMenu, InstrumentActionButtons, InstrumentTargetModal,
 * WatchlistContext) — paylaşılan cache olmadığı için her route geçişinde tüm
 * tüketiciler eşzamanlı /api/v1/portfolios çağırıyor → backend liste cache HIT olsa
 * bile network round-trip + JSON parse tekrarlanır, cache MISS'te ise N paralel
 * cold-start yarar.
 *
 * Bu modül-düzeyi memoizer iki şey yapar:
 *   1) IN-FLIGHT dedup: aktif bir Promise varsa hepsi aynı Promise'ı paylaşır.
 *   2) 15s TTL cache: son sonucu döndürür → eşzamanlı sayfa render'larında
 *      backend'e dokunmaz.
 *
 * Her mutasyon (create/update/delete portföy, add/delete tx, kupon, watchlist
 * write) `bustMyPortfoliosCache()` çağırır — anlık tazelik. TTL yalnızca
 * salt-okunur navigasyonda gevşek bir tampondur.
 */
let _portfoliosCache = null;     // { at: number, data: Array }
let _portfoliosInFlight = null;  // Promise<Array> | null
const PORTFOLIOS_TTL_MS = 15_000;

export function bustMyPortfoliosCache() {
  _portfoliosCache = null;
  _portfoliosInFlight = null;
}

export async function getMyPortfolios({ force = false } = {}) {
  if (!force && _portfoliosCache && (Date.now() - _portfoliosCache.at) < PORTFOLIOS_TTL_MS) {
    return _portfoliosCache.data;
  }
  if (_portfoliosInFlight) {
    return _portfoliosInFlight;
  }
  _portfoliosInFlight = client.get('/api/v1/portfolios')
    .then(({ data: wrapper }) => {
      const data = wrapper.data;
      _portfoliosCache = { at: Date.now(), data };
      return data;
    })
    .finally(() => {
      _portfoliosInFlight = null;
    });
  return _portfoliosInFlight;
}

export async function getPortfolioById(portfolioId) {
  const { data: wrapper } = await client.get(`/api/v1/portfolios/${portfolioId}`);
  return wrapper.data;
}

/**
 * AI Portföy Analizi: backend metrikleri deterministik hesaplar (risk/sağlık skoru, volatilite,
 * Sharpe, drawdown, beta, yoğunlaşma, benchmark, reel getiri) + AI yorum raporu (varsa).
 */
export async function getPortfolioAiAnalysis(portfolioId) {
  const { data: wrapper } = await client.get(`/api/v1/portfolios/${portfolioId}/ai-analysis`);
  return wrapper.data;
}

// Paylaşımlı AI analiz cache — aynı portföyün birden çok kartı (Risk/Sağlık/Monte Carlo/Kimlik widget'ları)
// tek istek atsın diye (TTL 5 dk; portföy bazında promise dedupe).
const _aiAnalysisCache = new Map(); // portfolioId -> { at, promise }
const AI_ANALYSIS_TTL_MS = 5 * 60 * 1000;

export function getPortfolioAiAnalysisShared(portfolioId) {
  const hit = _aiAnalysisCache.get(portfolioId);
  if (hit && Date.now() - hit.at < AI_ANALYSIS_TTL_MS) return hit.promise;
  // light=true: yalnız deterministik skorlar/sınıflandırma/Monte Carlo — AI narrator'ı BEKLEMEZ (hızlı).
  const promise = client
    .get(`/api/v1/portfolios/${portfolioId}/ai-analysis`, { params: { light: true } })
    .then(({ data: wrapper }) => wrapper.data)
    .catch((e) => {
      _aiAnalysisCache.delete(portfolioId);
      throw e;
    });
  _aiAnalysisCache.set(portfolioId, { at: Date.now(), promise });
  return promise;
}

/**
 * Yeniden dengeleme: seçilen risk profiline göre örnek hedef dağılım vs mevcut (eğitsel).
 * profile: CONSERVATIVE | BALANCED | AGGRESSIVE.
 */
export async function getPortfolioRebalance(portfolioId, profile) {
  const { data: wrapper } = await client.get(`/api/v1/portfolios/${portfolioId}/rebalance`, {
    params: { profile },
  });
  return wrapper.data;
}

export async function createPortfolio(payload) {
  // payload: { name, description, currency, portfolioType }
  const { data: wrapper } = await client.post('/api/v1/portfolios', payload);
  bustMyPortfoliosCache();
  return wrapper.data;
}

export async function updatePortfolio(portfolioId, payload) {
  // payload: { name, description }
  const { data: wrapper } = await client.patch(`/api/v1/portfolios/${portfolioId}`, payload);
  bustMyPortfoliosCache();
  return wrapper.data;
}

export async function deletePortfolio(portfolioId) {
  const { data: wrapper } = await client.delete(`/api/v1/portfolios/${portfolioId}`);
  bustMyPortfoliosCache();
  return wrapper.data;
}

// ── Transactions (HOLDINGS) ───────────────────────────────────────────────────

export async function addTransaction(portfolioId, payload) {
  const { data: wrapper } = await client.post(
    `/api/v1/portfolios/${portfolioId}/transactions`,
    payload
  );
  bustMyPortfoliosCache();
  return wrapper.data;
}

/**
 * Bir varlığın belirli bir tarihteki kapanış fiyatı — işlem ekleme modalında otomatik
 * doldurma için. { price, date, found } döner; veri yoksa found=false.
 */
export async function getPriceAtDate(assetType, symbol, date) {
  const { data } = await client.get('/api/v1/portfolios/price-at', {
    params: { assetType, symbol, date },
  });
  return data.data ?? { price: null, date: null, found: false };
}

export async function deleteTransaction(portfolioId, transactionId) {
  const { data: wrapper } = await client.delete(
    `/api/v1/portfolios/${portfolioId}/transactions/${transactionId}`
  );
  bustMyPortfoliosCache();
  return wrapper.data;
}

/**
 * Manuel DİBS/Kira Sertifikası kupon ödeme kaydı.
 * payload: { symbol, amount, paymentDate (ISO string) }
 * Realized gelir olarak portföye yansır; açık pozisyon (nominal/cost) dokunulmaz.
 */
export async function addCouponIncome(portfolioId, payload) {
  const { data: wrapper } = await client.post(
    `/api/v1/portfolios/${portfolioId}/coupon-income`,
    payload
  );
  bustMyPortfoliosCache();
  return wrapper.data;
}

// ── Performance chart ───────────────────────────────────────────────────────

export async function getPortfolioPerformance(portfolioId, range, metric) {
  const { data: wrapper } = await client.get(
    `/api/v1/portfolios/${portfolioId}/performance`,
    { params: { range, metric } },
  );
  return wrapper.data;
}

export async function getPortfolioWhatIf(portfolioId) {
  const { data: wrapper } = await client.get(`/api/v1/portfolios/${portfolioId}/what-if`);
  return wrapper.data;
}

export async function getPortfolioWhatIfSeries(portfolioId, assetType, symbol, benchmarks = [], sim = null) {
  const params = {};
  if (sim && sim.assetType && sim.symbol && sim.amount && sim.date) {
    params.simAssetType = sim.assetType;
    params.simSymbol = sim.symbol;
    params.simAmount = sim.amount;
    params.simDate = sim.date;
  } else if (assetType && symbol) {
    params.assetType = assetType;
    params.symbol = symbol;
  }
  if (benchmarks.length) params.benchmark = benchmarks;
  const { data: wrapper } = await client.get(
    `/api/v1/portfolios/${portfolioId}/what-if-series`,
    { params, paramsSerializer: { indexes: null } }, // benchmark=a&benchmark=b (Spring List)
  );
  return wrapper.data;
}

// ── Watchlist ─────────────────────────────────────────────────────────────────

export async function getWatchlistItems(portfolioId) {
  const { data: wrapper } = await client.get(
    `/api/v1/portfolios/${portfolioId}/watchlist`
  );
  return wrapper.data ?? [];
}

export async function addWatchlistItem(portfolioId, payload) {
  // payload: { symbol, assetType, notes }
  const { data: wrapper } = await client.post(
    `/api/v1/portfolios/${portfolioId}/watchlist`,
    payload
  );
  bustMyPortfoliosCache();
  return wrapper.data;
}

export async function deleteWatchlistItem(portfolioId, itemId) {
  const { data: wrapper } = await client.delete(
    `/api/v1/portfolios/${portfolioId}/watchlist/${itemId}`
  );
  bustMyPortfoliosCache();
  return wrapper.data;
}
