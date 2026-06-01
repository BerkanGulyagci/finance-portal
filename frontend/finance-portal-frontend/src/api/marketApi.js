import client from '../lib/http';

function normalizeBistSymbol(symbol) {
  const raw = String(symbol ?? '').trim().toUpperCase();
  if (!raw) return raw;
  // Yahoo kripto: BTC-USD, ETH-EUR — BIST .IS ekleme
  if (raw.includes('-')) return raw;
  return raw.includes('.') ? raw : `${raw}.IS`;
}

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

/** Backend'in (cache'li) top ~1000 coin listesi — arama/modal "herhangi bir coin" için. */
export async function getAllCryptoCoins(currency = 'try') {
  const { data } = await client.get('/api/market/crypto/all', { params: { currency } });
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

export async function getCryptoChart(coinId, days = 7, currency = 'try', interval, aggregate) {
  const params = { days, currency };
  if (interval) params.interval = interval;
  if (aggregate) params.aggregate = aggregate;
  const { data } = await client.get(`/api/market/crypto/${encodeURIComponent(coinId)}/chart`, { params });
  return data.data ?? {};
}

/** TRY 5Y / Tüm — Binance Spot klines (boşsa Yahoo fallback) */
export async function getCryptoBinanceCandles(symbol, range, currency = 'try') {
  const { data } = await client.get(
    `/api/market/crypto/${encodeURIComponent(String(symbol).toLowerCase())}/candles`,
    { params: { range, currency: (currency ?? 'try').toLowerCase() } },
  );
  if (data?.success === false) {
    throw new Error(data?.message ?? 'Binance candles request failed');
  }
  return data?.data ?? [];
}

/** USD/EUR 5Y / Tüm — Yahoo Finance OHLC (BTC-USD, BTC-EUR) */
export async function getCryptoYahooOhlc(symbol, range, currency) {
  const { data } = await client.get(
    `/api/market/crypto/${encodeURIComponent(symbol)}/yahoo/ohlc`,
    { params: { range, currency: (currency ?? 'usd').toLowerCase() } },
  );
  return data.data ?? [];
}

/** USD/EUR 5Y / Tüm — Yahoo Finance çizgi grafik */
export async function getCryptoYahooChart(symbol, range, currency) {
  const { data } = await client.get(
    `/api/market/crypto/${encodeURIComponent(symbol)}/yahoo/chart`,
    { params: { range, currency: (currency ?? 'usd').toLowerCase() } },
  );
  return data.data ?? null;
}

/** TL 5Y / Tüm — Yahoo USD kapanışı × USD/TRY (TRY=X) ile TL'ye çevrilmiş çizgi grafik */
export async function getCryptoYahooTryChart(symbol, range) {
  const { data } = await client.get(
    `/api/market/crypto/${encodeURIComponent(symbol)}/yahoo/chart-try`,
    { params: { range } },
  );
  return data.data ?? null;
}

export async function getCryptoDetail(coinId) {
  const { data } = await client.get(`/api/market/crypto/${encodeURIComponent(coinId)}/detail`);
  return data.data ?? {};
}

export async function getStockMidasDetail(symbol) {
  const normalized = normalizeBistSymbol(symbol);
  const { data } = await client.get(`/api/market/stocks/${encodeURIComponent(normalized)}/midas`);
  return data.data ?? null;
}

export async function getStockChart(symbol, range = '1d', interval = '1m') {
  const normalized = normalizeBistSymbol(symbol);
  const { data } = await client.get(`/api/market/stocks/${encodeURIComponent(normalized)}/chart`, { params: { range, interval } });
  return data.data ?? null;
}

/** Tek sembolün güncel fiyatı + günlük % değişimi (endeksler dahil: XU100.IS, XU030.IS, XU050.IS). */
export async function getStockQuote(symbol) {
  const normalized = normalizeBistSymbol(symbol);
  const { data } = await client.get(`/api/market/stocks/${encodeURIComponent(normalized)}`);
  const d = data.data ?? null;
  if (!d) return null;
  const s = d.summary ?? {};
  return { symbol: d.symbol, name: d.name, price: s.price, changePercent: s.changePercent, currency: d.currency };
}

/** Tür-bağımsız tarihsel TL fiyat serisi (hisse karşılaştırmada cross-enstrüman için). */
export async function getMarketPriceHistory(assetType, symbol, range = '1Y') {
  const { data } = await client.get('/api/market/price-history', {
    params: { assetType, symbol, range },
  });
  return data.data ?? { timestamps: [], closePrices: [] };
}

export async function getStockOhlc(symbol, range = '3mo', interval = '1d') {
  const normalized = normalizeBistSymbol(symbol);
  const { data } = await client.get(`/api/market/stocks/${encodeURIComponent(normalized)}/ohlc`, { params: { range, interval } });
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

/**
 * VİOP kontrat spec'i — multiplier, marginRate, currency.
 * AddTransactionModal canlı önizleme kartı için kullanılır.
 * Sembol formatları: "F_AKBNK0626" veya Akbank gösterim "USDTRY (30 HAZ 26) VADELI".
 * Spec bulunamazsa backend güvenli fallback döner (multiplier=1, marginRate=0.15) + found=false.
 */
export async function getViopContractSpec(symbol) {
  const { data } = await client.get('/api/market/viop/spec', { params: { symbol } });
  return data?.data ?? null;
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

/**
 * Fon birim pay fiyatı tarihsel serisi — TEFAS resmi API (grafik için, 5 yıla kadar).
 * type: YAT (TEFAS yatırım fonu) | EMK (emeklilik). points boşsa frontend Rasyonet'e fallback eder.
 */
export async function getFundPriceHistory(code, range = '1Y', type = 'YAT') {
  const { data } = await client.get(
    `/api/market/funds/${encodeURIComponent(code)}/price-history`,
    { params: { range, type } },
  );
  return data.data ?? { code, range, points: [] };
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

// ── Banka Döviz Kurları (Hesapkurdu) ─────────────────────────────────────────

/**
 * Tüm banka döviz kurlarını döndürür.
 * GET /api/market/currency/banks
 */
export async function getBankCurrencyRates() {
  const { data } = await client.get('/api/market/currency/banks');
  return data.data ?? [];
}

/**
 * Belirli bir döviz koduna göre banka kurlarını döndürür.
 * GET /api/market/currency/banks?currency=USD
 */
export async function getBankCurrencyRatesByCurrency(currency) {
  const { data } = await client.get('/api/market/currency/banks', { params: { currency } });
  return data.data ?? [];
}

/**
 * Belirli bir bankanın kurlarını döndürür.
 * GET /api/market/currency/banks/{bankName}
 */
export async function getBankCurrencyRatesByBank(bankName) {
  const { data } = await client.get(`/api/market/currency/banks/${encodeURIComponent(bankName)}`);
  return data.data ?? [];
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
  category = '',
  minRemainingDays = null,
  maxRemainingDays = null,
  sortBy = 'maturityDate',
  sortDir = 'asc',
} = {}) {
  const params = { page, size, sortBy, sortDir };
  if (search)              params.search = search;
  if (type)                params.type   = type;
  if (category)            params.category = category;
  if (minRemainingDays != null) params.minRemainingDays = minRemainingDays;
  if (maxRemainingDays != null) params.maxRemainingDays = maxRemainingDays;
  const { data } = await client.get('/api/market/bonds/evds', { params, timeout: 240_000 });
  return data.data ?? { items: [], totalItems: 0, totalPages: 0, page: 0, size, hasNext: false, hasPrevious: false };
}

/** Backend'den her kategorinin enstrüman sayısını döner (dropdown sayım için). */
export async function getEvdsBondCategoryCounts() {
  const { data } = await client.get('/api/market/bonds/evds/categories', { timeout: 120_000 });
  return data.data ?? {};
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
 * period: ONE_WEEK | ONE_MONTH | THREE_MONTHS | SIX_MONTHS | ONE_YEAR | FIVE_YEARS
 * period: ONE_WEEK | ONE_MONTH | THREE_MONTHS | SIX_MONTHS | ONE_YEAR
 */
export async function getEvdsBondHistory(instrumentCode, period = 'ONE_MONTH') {
  const { data } = await client.get(
    `/api/market/bonds/evds/${encodeURIComponent(instrumentCode)}/history`,
    { params: { period } }
  );
  return data.data ?? [];
}

// ── Eurobond (Hazine dış borç tahvilleri — HMB ISIN + Business Insider canlı) ──
export async function getGlobalBonds() {
  const { data } = await client.get('/api/market/bonds/global', { timeout: 240_000 });
  return data.data ?? [];
}

export async function getGlobalBondDetail(isin) {
  const { data } = await client.get(`/api/market/bonds/global/${encodeURIComponent(isin)}`, { timeout: 120_000 });
  return data.data ?? null;
}

export async function getGlobalBondChart(isin, range = '1Y') {
  const { data } = await client.get(
    `/api/market/bonds/global/${encodeURIComponent(isin)}/chart`,
    { params: { range }, timeout: 120_000 }
  );
  return data.data ?? [];
}

export async function getEconomicIndicators() {
  const { data } = await client.get('/api/market/indicators');
  return data.data ?? {};
}

/**
 * Türkiye ekonomisi özet göstergeleri — TÜFE/ÜFE enflasyon, faizler, GSYİH,
 * cari denge, rezerv, kur, işsizlik, bütçe vb. kategoriye göre gruplanmış.
 * GET /api/market/economy
 */
export async function getEconomy() {
  const { data } = await client.get('/api/market/economy');
  return data.data ?? { source: '', groups: [] };
}

/**
 * Tek bir ekonomi göstergesinin grafik zaman serisi.
 * GET /api/market/economy/series?key=tufe[&full=true]
 * full=true → kaynaktaki en eski tarihten bugüne TÜM geçmiş (günlük seriler gün-gün).
 */
export async function getEconomySeries(key, full = false) {
  const { data } = await client.get('/api/market/economy/series', { params: { key, full } });
  return data.data ?? { key, label: '', unit: '', frequency: '', transform: 'raw', source: '', points: [] };
}

/**
 * Tüm ekonomi göstergelerinin grafik zaman serileri (tek çağrı).
 * GET /api/market/economy/charts
 */
export async function getEconomyCharts() {
  const { data } = await client.get('/api/market/economy/charts');
  return data.data ?? [];
}

/**
 * Piyasanın hareketlileri — günün en çok yükselen/düşen enstrümanları (tüm piyasa, portföyden bağımsız).
 * GET /api/market/movers?limit=5 → [{ key, label, gainers:[mover], losers:[mover] }]
 */
export async function getMarketMovers(limit = 5) {
  const { data } = await client.get('/api/market/movers', { params: { limit } });
  return data.data ?? [];
}

/**
 * Güncel kredi faiz oranları (ihtiyaç/taşıt/konut/ticari) — kredi taksit hesaplayıcısı için.
 * GET /api/market/economy/loan-rates
 */
export async function getLoanRates() {
  const { data } = await client.get('/api/market/economy/loan-rates');
  return data.data ?? { personal: null, vehicle: null, housing: null, commercial: null, period: null, source: '' };
}

/**
 * Güncel TL mevduat faiz oranları (vadeye göre) + yıllık enflasyon — mevduat getiri hesaplayıcısı için.
 * GET /api/market/economy/deposit-rates
 */
export async function getDepositRates() {
  const { data } = await client.get('/api/market/economy/deposit-rates');
  return data.data ?? { upTo1Month: null, upTo3Months: null, upTo6Months: null, upTo1Year: null, inflationYoy: null, period: null, source: '' };
}

/**
 * Ekonomik takvim olayları (Finnhub). Tarih aralığı ISO YYYY-MM-DD; backend max 400 gün.
 * GET /api/market/economy/calendar?from=2025-05-31&to=2026-05-31
 *
 * Dönen liste: [{ time, country, currency, event, impact, actual, estimate, prev, unit }, ...]
 *  - time: "YYYY-MM-DD HH:mm:ss" UTC
 *  - impact: "low" | "medium" | "high" | "holiday" | null
 */
export async function getEconomicCalendar(from, to) {
  const { data } = await client.get('/api/market/economy/calendar', { params: { from, to } });
  return data.data ?? [];
}

export async function getGoldSpot() {
  const { data } = await client.get('/api/gold/spot');
  return data.data ?? null;
}

export async function getGoldHistory(range = '1M', currency = 'USD') {
  const { data } = await client.get('/api/gold/history', { params: { range, currency } });
  return data.data ?? null;
}

export async function getSilverSpot() {
  const { data } = await client.get('/api/silver/spot');
  return data.data ?? null;
}

export async function getSilverHistory(range = '1M', currency = 'TRY') {
  const { data } = await client.get('/api/silver/history', { params: { range, currency } });
  return data.data ?? null;
}

export async function getPreciousMetalSpot(metal) {
  const { data } = await client.get(`/api/precious-metals/${metal}/spot`);
  return data.data ?? null;
}

export async function getPreciousMetalHistory(metal, range = '1M', currency = 'TRY') {
  const { data } = await client.get(`/api/precious-metals/${metal}/history`, { params: { range, currency } });
  return data.data ?? null;
}

// ── Global Emtia (Yahoo Finance Futures) ─────────────────────────────────────

export async function getCommodityList() {
  const { data } = await client.get('/api/commodities/list');
  return data.data ?? [];
}

export async function getCommoditySpot(symbol) {
  const { data } = await client.get('/api/commodities/spot', { params: { symbol } });
  return data.data ?? null;
}

export async function getCommodityHistory(symbol, range = '1M', interval = null) {
  const params = { symbol, range };
  if (interval) params.interval = interval;
  const { data } = await client.get('/api/commodities/history', { params });
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
