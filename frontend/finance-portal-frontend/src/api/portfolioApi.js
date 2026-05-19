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

export async function deleteTransaction(portfolioId, transactionId) {
  const { data: wrapper } = await client.delete(
    `/api/portfolios/${portfolioId}/transactions/${transactionId}`
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
