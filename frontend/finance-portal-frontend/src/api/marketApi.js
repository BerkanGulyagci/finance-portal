import client from './client';

export async function getStocks(page = 0, size = 20, index = '') {
  const params = { page, size };
  if (index) params.index = index;
  const { data: w } = await client.get('/api/market/stocks', { params });
  return w.data ?? {};
}

export async function getCryptos(page = 0, size = 100, currency = 'try') {
  const { data: w } = await client.get('/api/market/crypto', { params: { page, size, currency } });
  return w.data ?? [];
}

export async function getAllCryptos(currency = 'try') {
  // Sayfaları sırayla çek — paralel çekince CoinGecko rate-limit verir
  const results = [];
  for (let page = 0; page < 4; page++) {
    try {
      const { data: w } = await client.get('/api/market/crypto', { params: { page, size: 250, currency } });
      const items = w.data ?? [];
      if (items.length === 0) break;
      results.push(...items);
      // Rate-limit koruması: sayfalar arası 1.3s bekle
      if (page < 3) await new Promise(r => setTimeout(r, 1300));
    } catch {
      break;
    }
  }
  return results;
}

export async function getCryptoOhlc(coinId, days = 7, currency = 'try') {
  const { data: w } = await client.get(`/api/market/crypto/${encodeURIComponent(coinId)}/ohlc`, { params: { days, currency } });
  return w.data ?? [];
}

export async function getCryptoChart(coinId, days = 7, currency = 'try') {
  const { data: w } = await client.get(`/api/market/crypto/${encodeURIComponent(coinId)}/chart`, { params: { days, currency } });
  return w.data ?? {};
}

export async function getCryptoDetail(coinId) {
  const { data: w } = await client.get(`/api/market/crypto/${encodeURIComponent(coinId)}/detail`);
  return w.data ?? {};
}

export async function getStockMidasDetail(symbol) {
  const { data: w } = await client.get(`/api/market/stocks/${symbol}/midas`);
  return w.data ?? null;
}

export async function getStockChart(symbol, range = '1d', interval = '1m') {
  const { data: w } = await client.get(`/api/market/stocks/${symbol}/chart`, { params: { range, interval } });
  return w.data ?? null;
}

export async function getStockOhlc(symbol, range = '3mo', interval = '1d') {
  const { data: w } = await client.get(`/api/market/stocks/${encodeURIComponent(symbol)}/ohlc`, { params: { range, interval } });
  return w.data ?? [];
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

export async function getViopContracts() {
  const { data: w } = await client.get('/api/market/viop');
  return w.data ?? [];
}

export async function getFunds(page = 0, size = 20) {
  const { data: w } = await client.get('/api/market/funds', { params: { page, size } });
  return w.data ?? {};
}

export async function getTefasFunds(kind = 'YAT', page = 0, size = 50) {
  const { data: w } = await client.get('/api/market/funds/tefas', { params: { kind, page, size } });
  return w.data ?? {};
}

export async function getTefasFundDetail(code) {
  const { data: w } = await client.get(`/api/market/funds/tefas/${encodeURIComponent(code)}`);
  return w.data ?? null;
}

export async function getTefasFundHistory(code, range = '1M') {
  const { data: w } = await client.get(`/api/market/funds/tefas/${encodeURIComponent(code)}/history`, { params: { range } });
  return w.data ?? null;
}

export async function getFxTcmb() {
  const { data: w } = await client.get('/api/market/fx/tcmb/latest');
  return w.data ?? {};
}

export async function getFxOpen(base = 'USD') {
  const { data: w } = await client.get('/api/market/fx/open/latest', { params: { base } });
  return w.data ?? {};
}

export async function getBonds() {
  const { data: w } = await client.get('/api/market/bonds');
  return w.data ?? [];
}

export async function getEconomicIndicators() {
  const { data: w } = await client.get('/api/market/indicators');
  return w.data ?? {};
}

export async function getGoldSpot() {
  const { data: w } = await client.get('/api/gold/spot');
  return w.data ?? null;
}

export async function getGoldHistory(range = '1M', currency = 'USD') {
  const { data: w } = await client.get('/api/gold/history', { params: { range, currency } });
  return w.data ?? null;
}

export async function getFxHistory(symbol, range = '1M') {
  const { data: w } = await client.get('/api/market/fx/history', { params: { symbol, range } });
  return w.data ?? null;
}

export async function searchAssetSymbols(type, q = '') {
  const query = q.trim().toLowerCase();
  try {
    if (type === 'STOCK') {
      // Tüm hisse sembollerini cache'den al
      const { data: w } = await client.get('/api/market/stocks', { params: { page: 0, size: 100 } });
      const symbols = (w.data?.content ?? []).map(s => s.symbol);
      if (!query) return symbols.slice(0, 20);
      return symbols.filter(s => s.toLowerCase().includes(query)).slice(0, 15);
    }
    if (type === 'CRYPTO') {
      const { data: w } = await client.get('/api/market/crypto', { params: { page: 0, size: 100 } });
      const symbols = (w.data ?? []).map(c => c.symbol?.toUpperCase());
      if (!query) return symbols.slice(0, 20);
      return symbols.filter(s => s?.toLowerCase().includes(query)).slice(0, 15);
    }
    if (type === 'FX') {
      const { data: w } = await client.get('/api/market/fx/tcmb/latest');
      const symbols = (w.data?.rates ?? []).map(r => r.symbol);
      if (!query) return symbols.slice(0, 20);
      return symbols.filter(s => s?.toLowerCase().includes(query)).slice(0, 15);
    }
    if (type === 'FUND') {
      const funds = ['SPY', 'QQQ', 'IWM', 'GLD', 'TLT', 'VTI', 'VOO', 'EFA', 'EEM', 'AGG', 'XLF', 'XLK', 'XLE', 'XLV', 'USO'];
      if (!query) return funds;
      return funds.filter(s => s.toLowerCase().includes(query));
    }
    if (type === 'FUTURE') {
      const futures = ['ES=F', 'NQ=F', 'YM=F', 'RTY=F', 'GC=F', 'SI=F', 'CL=F', 'BZ=F', 'NG=F', 'HG=F', 'ZW=F', 'ZC=F', 'ZS=F', '6E=F', '6J=F', '6B=F'];
      if (!query) return futures;
      return futures.filter(s => s.toLowerCase().includes(query));
    }
    return [];
  } catch {
    return [];
  }
}

export async function getAssetPrice(type, symbol) {
  try {
    if (type === 'STOCK' || type === 'FUTURE') {
      const { data: w } = await client.get(`/api/market/stocks/${encodeURIComponent(symbol)}`);
      const d = w.data;
      if (d?.summary?.price) return { valid: true, symbol, price: d.summary.price, currency: d.summary.currency };
      return { valid: false };
    }
    if (type === 'CRYPTO') {
      const sym = symbol.toLowerCase();
      const { data: w } = await client.get('/api/market/crypto', { params: { page: 0, size: 250 } });
      const coin = (w.data ?? []).find(c => c.symbol?.toLowerCase() === sym);
      if (coin) return { valid: true, symbol, price: coin.currentPrice, currency: 'TRY' };
      return { valid: false };
    }
    if (type === 'FX') {
      const { data: w } = await client.get('/api/market/fx/tcmb/latest');
      const rate = (w.data?.rates ?? []).find(r => r.symbol === symbol.toUpperCase());
      if (rate) return { valid: true, symbol, price: rate.sell, currency: 'TRY' };
      return { valid: false };
    }
    if (type === 'FUND') {
      const { data: w } = await client.get(`/api/market/funds/${encodeURIComponent(symbol)}`);
      const d = w.data;
      if (d?.summary?.price) return { valid: true, symbol, price: d.summary.price, currency: d.summary.currency };
      return { valid: false };
    }
    return { valid: false };
  } catch {
    return { valid: false };
  }
}
