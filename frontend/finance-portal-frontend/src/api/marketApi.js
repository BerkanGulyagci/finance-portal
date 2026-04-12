import client from './client';

export async function getCryptos(page = 0, size = 100) {
  const { data: w } = await client.get('/api/market/crypto', { params: { page, size } });
  return w.data ?? [];
}

export async function getAllCryptos() {
  // CoinGecko free tier: max 250 per page, rate limited
  // Fetch first 500 (pages 0 and 1 with size=250)
  const [p1, p2] = await Promise.all([
    client.get('/api/market/crypto', { params: { page: 0, size: 250 } }).then(r => r.data?.data ?? []),
    client.get('/api/market/crypto', { params: { page: 1, size: 250 } }).then(r => r.data?.data ?? []).catch(() => []),
  ]);
  return [...p1, ...p2];
}

export async function getStockMidasDetail(symbol) {
  const { data: w } = await client.get(`/api/market/stocks/${symbol}/midas`);
  return w.data ?? null;
}

export async function getStockChart(symbol, range = '1d', interval = '1m') {
  const { data: w } = await client.get(`/api/market/stocks/${symbol}/chart`, { params: { range, interval } });
  return w.data ?? null;
}

export async function getAllStocks() {
  const firstPage = await client.get('/api/market/stocks', { params: { page: 0, size: 20 } });
  const totalPages = firstPage.data?.data?.totalPages ?? 1;
  const results = [...(firstPage.data?.data?.content ?? [])];
  for (let p = 1; p < totalPages; p++) {
    const { data: w } = await client.get('/api/market/stocks', { params: { page: p, size: 20 } });
    results.push(...(w.data?.content ?? []));
  }
  return results;
}

export async function getFutures(page = 0, size = 20) {
  const { data: w } = await client.get('/api/market/futures', { params: { page, size } });
  return w.data ?? {};
}

export async function getFunds(page = 0, size = 20) {
  const { data: w } = await client.get('/api/market/funds', { params: { page, size } });
  return w.data ?? {};
}

export async function getTefasFunds(kind = 'YAT', page = 0, size = 50) {
  const { data: w } = await client.get('/api/market/funds/tefas', { params: { kind, page, size } });
  return w.data ?? {};
}

export async function getFxTcmb() {
  const { data: w } = await client.get('/api/market/fx/tcmb/latest');
  return w.data ?? {};
}
