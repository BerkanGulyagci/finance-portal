import client from './client';

export async function getMyPortfolios() {
  const { data: wrapper } = await client.get('/api/portfolios');
  return wrapper.data; // ApiResponse<List<PortfolioResponse>>
}

export async function getPortfolioById(portfolioId) {
  const { data: wrapper } = await client.get(`/api/portfolios/${portfolioId}`);
  return wrapper.data;
}

export async function createPortfolio(payload) {
  const { data: wrapper } = await client.post('/api/portfolios', payload);
  return wrapper.data;
}

export async function addTransaction(portfolioId, payload) {
  const { data: wrapper } = await client.post(`/api/portfolios/${portfolioId}/transactions`, payload);
  return wrapper.data;
}

export async function deletePortfolio(portfolioId) {
  const { data: wrapper } = await client.delete(`/api/portfolios/${portfolioId}`);
  return wrapper.data;
}

export async function deleteTransaction(portfolioId, transactionId) {
  const { data: wrapper } = await client.delete(`/api/portfolios/${portfolioId}/transactions/${transactionId}`);
  return wrapper.data;
}
