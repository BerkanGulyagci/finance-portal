import client from './client';

// ── Portfolio CRUD ────────────────────────────────────────────────────────────

export async function getMyPortfolios() {
  const { data: wrapper } = await client.get('/api/portfolios');
  return wrapper.data;
}

export async function getPortfolioById(portfolioId) {
  const { data: wrapper } = await client.get(`/api/portfolios/${portfolioId}`);
  return wrapper.data;
}

export async function createPortfolio(payload) {
  // payload: { name, description, currency, portfolioType }
  const { data: wrapper } = await client.post('/api/portfolios', payload);
  return wrapper.data;
}

export async function updatePortfolio(portfolioId, payload) {
  // payload: { name, description }
  const { data: wrapper } = await client.patch(`/api/portfolios/${portfolioId}`, payload);
  return wrapper.data;
}

export async function deletePortfolio(portfolioId) {
  const { data: wrapper } = await client.delete(`/api/portfolios/${portfolioId}`);
  return wrapper.data;
}

// ── Transactions (HOLDINGS) ───────────────────────────────────────────────────

export async function addTransaction(portfolioId, payload) {
  const { data: wrapper } = await client.post(
    `/api/portfolios/${portfolioId}/transactions`,
    payload
  );
  return wrapper.data;
}

/**
 * Bir varlığın belirli bir tarihteki kapanış fiyatı — işlem ekleme modalında otomatik
 * doldurma için. { price, date, found } döner; veri yoksa found=false.
 */
export async function getPriceAtDate(assetType, symbol, date) {
  const { data } = await client.get('/api/portfolios/price-at', {
    params: { assetType, symbol, date },
  });
  return data.data ?? { price: null, date: null, found: false };
}

export async function deleteTransaction(portfolioId, transactionId) {
  const { data: wrapper } = await client.delete(
    `/api/portfolios/${portfolioId}/transactions/${transactionId}`
  );
  return wrapper.data;
}

/**
 * Manuel DİBS/Kira Sertifikası kupon ödeme kaydı.
 * payload: { symbol, amount, paymentDate (ISO string) }
 * Realized gelir olarak portföye yansır; açık pozisyon (nominal/cost) dokunulmaz.
 */
export async function addCouponIncome(portfolioId, payload) {
  const { data: wrapper } = await client.post(
    `/api/portfolios/${portfolioId}/coupon-income`,
    payload
  );
  return wrapper.data;
}

// ── Performance chart ───────────────────────────────────────────────────────

export async function getPortfolioPerformance(portfolioId, range, metric) {
  const { data: wrapper } = await client.get(
    `/api/portfolios/${portfolioId}/performance`,
    { params: { range, metric } },
  );
  return wrapper.data;
}

export async function getPortfolioWhatIf(portfolioId) {
  const { data: wrapper } = await client.get(`/api/portfolios/${portfolioId}/what-if`);
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
    `/api/portfolios/${portfolioId}/what-if-series`,
    { params, paramsSerializer: { indexes: null } }, // benchmark=a&benchmark=b (Spring List)
  );
  return wrapper.data;
}

// ── Watchlist ─────────────────────────────────────────────────────────────────

export async function getWatchlistItems(portfolioId) {
  const { data: wrapper } = await client.get(
    `/api/portfolios/${portfolioId}/watchlist`
  );
  return wrapper.data ?? [];
}

export async function addWatchlistItem(portfolioId, payload) {
  // payload: { symbol, assetType, notes }
  const { data: wrapper } = await client.post(
    `/api/portfolios/${portfolioId}/watchlist`,
    payload
  );
  return wrapper.data;
}

export async function deleteWatchlistItem(portfolioId, itemId) {
  const { data: wrapper } = await client.delete(
    `/api/portfolios/${portfolioId}/watchlist/${itemId}`
  );
  return wrapper.data;
}
