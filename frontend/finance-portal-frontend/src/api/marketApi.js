import client from './client';

export async function getStocks(page = 0, size = 20, index = '') {
  const params = { page, size };
  if (index) params.index = index;
  const { data } = await client.get('/api/market/stocks', { params });
  return data.data ?? {};
}

export async function getCryptos(page = 0, size = 100, currency = 'try') {
  const { data } = await client.get('/api/market/crypto', { params: { page, size, currency } });
  return data.data ?? [];
}

export async function getAllCryptos(currency = 'try') {
  // Sayfaları sırayla çek — paralel çekince CoinGecko rate-limit verir
  const results = [];
  for (let page = 0; page < 4; page++) {
    try {
      const { data } = await client.get('/api/market/crypto', { params: { page, size: 250, currency } });
      const items = data.data ?? [];
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
  const { data } = await client.get(`/api/market/crypto/${encodeURIComponent(coinId)}/ohlc`, { params: { days, currency } });
  return data.data ?? [];
}

export async function getCryptoChart(coinId, days = 7, currency = 'try') {
  const { data } = await client.get(`/api/market/crypto/${encodeURIComponent(coinId)}/chart`, { params: { days, currency } });
  return data.data ?? {};
}

export async function getCryptoDetail(coinId) {
  const { data } = await client.get(`/api/market/crypto/${encodeURIComponent(coinId)}/detail`);
  return data.data ?? {};
}

export async function getStockMidasDetail(symbol) {
  const { data } = await client.get(`/api/market/stocks/${symbol}/midas`);
  return data.data ?? null;
}

export async function getStockChart(symbol, range = '1d', interval = '1m') {
  const { data } = await client.get(`/api/market/stocks/${symbol}/chart`, { params: { range, interval } });
  return data.data ?? null;
}

export async function getStockOhlc(symbol, range = '3mo', interval = '1d') {
  const { data } = await client.get(`/api/market/stocks/${encodeURIComponent(symbol)}/ohlc`, { params: { range, interval } });
  return data.data ?? [];
}

export async function getAllStocks() {
  const firstPage = await client.get('/api/market/stocks', { params: { page: 0, size: 20 } });
  const totalPages = firstPage.data?.data?.totalPages ?? 1;
  const results = [...(firstPage.data?.data?.content ?? [])];
  for (let p = 1; p < totalPages; p++) {
    const { data } = await client.get('/api/market/stocks', { params: { page: p, size: 20 } });
    results.push(...(data.data?.content ?? []));
  }
  return results;
}

export async function getFutures(page = 0, size = 20) {
  const { data } = await client.get('/api/market/futures', { params: { page, size } });
  return data.data ?? {};
}

export async function getViopContractDetail(contractName) {
  const { data } = await client.get('/api/market/futures/viop', { 
    params: { name: contractName } 
  });
  return data.data ?? null;
}

export async function getViopContracts() {
  const { data } = await client.get('/api/market/futures/viop/contracts');
  return data.data ?? [];
}

export async function getViopContractsByUnderlying(symbol) {
  const { data } = await client.get(`/api/market/futures/viop/contracts/by-underlying/${symbol}`);
  return data.data ?? [];
}

/**
 * VİOP sözleşmesi grafik verisi (İş Yatırım'dan).
 * period: ONE_WEEK | ONE_MONTH | THREE_MONTHS | SIX_MONTHS | ONE_YEAR
 * Desteklenmeyen sözleşmeler için backend success=false döner.
 */
export async function getViopChart(contractName, period = 'ONE_WEEK') {
  const { data } = await client.get('/api/market/futures/viop/chart', {
    params: { name: contractName, period },
  });
  // Backend success=false dönebilir (desteklenmeyen sözleşme) — ham response'u döndür
  return data;
}

export async function getFunds(page = 0, size = 20) {
  const { data } = await client.get('/api/market/funds', { params: { page, size } });
  return data.data ?? {};
}

/**
 * Tüm TEFAS fonlarını Rasyonet'ten çeker.
 * GET /api/market/funds/tefas/all
 */
export async function getAllTefasFunds() {
  const { data } = await client.get('/api/market/funds/tefas/all');
  return data.data ?? [];
}

/**
 * Tüm BES (Bireysel Emeklilik) fonlarını çeker — SourceCode: TPF
 * GET /api/market/funds/bes/all
 */
export async function getAllBesFunds() {
  const { data } = await client.get('/api/market/funds/bes/all');
  return data.data ?? [];
}

/**
 * Tüm OKS (Otomatik Katılım) fonlarını çeker — SourceCode: TAF
 * GET /api/market/funds/oks/all
 */
export async function getAllOksFunds() {
  const { data } = await client.get('/api/market/funds/oks/all');
  return data.data ?? [];
}

/**
 * Osmanlı Portföy fon bültenini çeker.
 * GET /api/market/funds/osmanli/bulletin
 */
export async function getOsmanliFundBulletin() {
  const { data } = await client.get('/api/market/funds/osmanli/bulletin');
  return data.data ?? [];
}

/**
 * Rasyonet card endpoint'inden zengin fon detayı çeker.
 * GET /api/market/funds/tefas/{code}?sourceCode={sourceCode}
 * sourceCode: TMF (default/TEFAS) | TPF (BES) | TAF (OKS)
 */
export async function getRasyonetFundDetail(code, sourceCode = 'TMF') {
  const { data } = await client.get(`/api/market/funds/tefas/${encodeURIComponent(code)}`, {
    params: { sourceCode },
  });
  return data.data ?? null;
}

/** Alias — TefasComparePage uyumluluğu için */
export async function getTefasFundDetail(code) {
  return getRasyonetFundDetail(code);
}

/** TefasComparePage'de kullanılıyor — şimdilik boş dizi döndür (HangiKredi history kaldırıldı) */
export async function getTefasFundHistory(code, range = '1M') {
  // HangiKredi history endpoint'i kaldırıldı. Rasyonet'te günlük fiyat geçmişi
  // fon detay sayfasında priceHistory olarak geliyor.
  return { points: [] };
}

/** FundPriceChart.jsx uyumluluğu için — HangiKredi chart kaldırıldı */
export async function getFundHistory(code, period = 'ONE_MONTH') {
  return { points: [] };
}

export async function getFxTcmb() {
  const { data } = await client.get('/api/market/fx/tcmb/latest');
  return data.data ?? {};
}

export async function getFxOpen(base = 'USD') {
  const { data } = await client.get('/api/market/fx/open/latest', { params: { base } });
  return data.data ?? {};
}

// ── EVDS Tahvil/Bono API ──────────────────────────────────────────────────────

/**
 * EVDS tabanlı DİBS kıymet listesini sayfalı, filtrelenebilir ve sıralanabilir şekilde döndürür.
 * GET /api/market/bonds/evds
 */
export async function getEvdsBonds({
  page = 0,
  size = 50,
  search = '',
  type = '',
  minRemainingDays = null,
  maxRemainingDays = null,
  sortBy = 'maturityDate',
  sortDir = 'asc',
} = {}) {
  const params = { page, size, sortBy, sortDir };
  if (search)              params.search = search;
  if (type)                params.type   = type;
  if (minRemainingDays != null) params.minRemainingDays = minRemainingDays;
  if (maxRemainingDays != null) params.maxRemainingDays = maxRemainingDays;
  const { data } = await client.get('/api/market/bonds/evds', { params });
  return data.data ?? { items: [], totalItems: 0, totalPages: 0, page: 0, size, hasNext: false, hasPrevious: false };
}

/**
 * Tekil kıymetin EVDS detayını döndürür.
 * GET /api/market/bonds/evds/{instrumentCode}
 */
export async function getEvdsBondDetail(instrumentCode) {
  const { data } = await client.get(`/api/market/bonds/evds/${encodeURIComponent(instrumentCode)}`);
  return data.data ?? null;
}

/**
 * Kıymetin tarihsel EVDS gösterge değerlerini döndürür.
 * GET /api/market/bonds/evds/{instrumentCode}/history?period={period}
 * period: ONE_WEEK | ONE_MONTH | THREE_MONTHS | SIX_MONTHS | ONE_YEAR
 */
export async function getEvdsBondHistory(instrumentCode, period = 'ONE_MONTH') {
  const { data } = await client.get(
    `/api/market/bonds/evds/${encodeURIComponent(instrumentCode)}/history`,
    { params: { period } }
  );
  return data.data ?? [];
}

export async function getEconomicIndicators() {
  const { data } = await client.get('/api/market/indicators');
  return data.data ?? {};
}

export async function getGoldSpot() {
  const { data } = await client.get('/api/gold/spot');
  return data.data ?? null;
}

export async function getGoldHistory(range = '1M', currency = 'USD') {
  const { data } = await client.get('/api/gold/history', { params: { range, currency } });
  return data.data ?? null;
}

export async function getFxHistory(symbol, range = '1M') {
  const { data } = await client.get('/api/market/fx/history', { params: { symbol, range } });
  return data.data ?? null;
}

export async function searchAssetSymbols(type, q = '') {
  const query = q.trim().toLowerCase();
  try {
    if (type === 'STOCK') {
      // Tüm hisse sembollerini cache'den al
      const { data } = await client.get('/api/market/stocks', { params: { page: 0, size: 100 } });
      const symbols = (data.data?.content ?? []).map(s => s.symbol);
      if (!query) return symbols.slice(0, 20);
      return symbols.filter(s => s.toLowerCase().includes(query)).slice(0, 15);
    }
    if (type === 'CRYPTO') {
      const { data } = await client.get('/api/market/crypto', { params: { page: 0, size: 100 } });
      const symbols = (data.data ?? []).map(c => c.symbol?.toUpperCase());
      if (!query) return symbols.slice(0, 20);
      return symbols.filter(s => s?.toLowerCase().includes(query)).slice(0, 15);
    }
    if (type === 'FX') {
      const { data } = await client.get('/api/market/fx/tcmb/latest');
      const symbols = (data.data?.rates ?? []).map(r => r.symbol);
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
      const { data } = await client.get(`/api/market/stocks/${encodeURIComponent(symbol)}`);
      const d = data.data;
      if (d?.summary?.price) return { valid: true, symbol, price: d.summary.price, currency: d.summary.currency };
      return { valid: false };
    }
    if (type === 'CRYPTO') {
      const sym = symbol.toLowerCase();
      const { data } = await client.get('/api/market/crypto', { params: { page: 0, size: 250 } });
      const coin = (data.data ?? []).find(c => c.symbol?.toLowerCase() === sym);
      if (coin) return { valid: true, symbol, price: coin.currentPrice, currency: 'TRY' };
      return { valid: false };
    }
    if (type === 'FX') {
      const { data } = await client.get('/api/market/fx/tcmb/latest');
      const rate = (data.data?.rates ?? []).find(r => r.symbol === symbol.toUpperCase());
      if (rate) return { valid: true, symbol, price: rate.sell, currency: 'TRY' };
      return { valid: false };
    }
    if (type === 'FUND') {
      const { data } = await client.get(`/api/market/funds/${encodeURIComponent(symbol)}`);
      const d = data.data;
      if (d?.summary?.price) return { valid: true, symbol, price: d.summary.price, currency: d.summary.currency };
      return { valid: false };
    }
    return { valid: false };
  } catch {
    return { valid: false };
  }
}
