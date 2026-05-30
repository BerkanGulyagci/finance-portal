import {
  getCryptoBinanceCandles,
  getCryptoChart,
  getCryptoOhlc,
  getCryptoYahooChart,
  getCryptoYahooOhlc,
  getCryptoYahooTryChart,
} from '../../../api/marketApi';

/**
 * Kripto grafikleri — CoinGecko days + interval eşlemeleri.
 */
export const CRYPTO_CHART_RANGES = [
  { label: '1G', days: 1, ohlcDays: 1, interval: null, timeGranularity: 'minute' },
  { label: '1H', days: 7, ohlcDays: 7, interval: 'hourly', timeGranularity: 'hour' },
  { label: '1A', days: 30, ohlcDays: 30, interval: 'hourly', timeGranularity: 'hour' },
  { label: '3A', days: 90, ohlcDays: 90, interval: 'hourly', timeGranularity: 'hour' },
  { label: '6A', days: 180, ohlcDays: 180, interval: 'hourly', timeGranularity: 'hour' },
  { label: '1Y', days: 365, ohlcDays: 365, interval: 'daily', timeGranularity: 'day' },
  {
    label: '5Y',
    yahoo: true,
    yahooRange: '5y',
    yahooInterval: '1wk',
    timeGranularity: 'week',
  },
  {
    label: 'Tüm',
    yahoo: true,
    yahooRange: 'max',
    yahooInterval: '1mo',
    timeGranularity: 'month',
  },
];

/** 5Y / Tüm aralık etiketini backend Binance range parametresine çevirir */
export function yahooRangeToBinanceRange(yahooRange) {
  if (yahooRange === '5y') return '5y';
  if (yahooRange === 'max') return 'max';
  return null;
}

function binanceCandlesToOhlcPoints(candles) {
  if (!candles?.length) return [];
  return candles
    .map((c) => {
      const open = parseFloat(c.open);
      const high = parseFloat(c.high);
      const low = parseFloat(c.low);
      const close = parseFloat(c.close);
      const t = Number(c.timestamp);
      if (![open, high, low, close, t].every(Number.isFinite) || close <= 0) return null;
      const tsMs = t < 1e11 ? t * 1000 : t;
      return toChartPoint(tsMs, open, high, low, close, Number(c.volume ?? 0));
    })
    .filter(Boolean)
    .sort((a, b) => a.timestamp - b.timestamp);
}

function binanceCandlesToLinePrices(candles) {
  if (!candles?.length) return [];
  return candles
    .map((c) => {
      const t = Number(c.timestamp);
      const p = parseFloat(c.close);
      if (!Number.isFinite(t) || !Number.isFinite(p) || p <= 0) return null;
      return [t < 1e11 ? t * 1000 : t, p];
    })
    .filter(Boolean)
    .sort((a, b) => a[0] - b[0]);
}

/** TRY 5Y / Tüm — Binance BTCTRY (backend); boşsa null (Yahoo fallback) */
export async function fetchBinanceTryCryptoChart(symbol, uiCurrency, yahooRange) {
  const ui = String(uiCurrency ?? '').trim().toUpperCase();
  if (ui !== 'TRY') return null;
  const range = yahooRangeToBinanceRange(yahooRange);
  if (!range || !symbol) return null;
  try {
    const candles = await getCryptoBinanceCandles(symbol, range, 'try');
    if (!candles?.length) {
      console.warn('Binance TRY candles empty', { symbol, range });
      return null;
    }
    return {
      candles,
      prices: binanceCandlesToLinePrices(candles),
      points: binanceCandlesToOhlcPoints(candles),
      sourceNote: `Binance Spot (${String(symbol).toUpperCase()}TRY)`,
    };
  } catch (e) {
    console.warn('Binance TRY chart request failed', symbol, range, e?.response?.data ?? e?.message);
    return null;
  }
}

/**
 * Yahoo'da kripto-TRY çifti yok (BTC-TRY vb.).
 * 5Y / Tüm: EUR seçiliyse BTC-EUR, aksi halde BTC-USD.
 */
export function resolveYahooQuoteCurrencies(uiCurrency) {
  const ui = String(uiCurrency ?? 'usd').trim().toUpperCase();
  if (ui === 'EUR') return ['EUR', 'USD'];
  return ['USD'];
}

/** Yahoo Finance sembolü: BTC-USD, ETH-EUR */
export function toYahooCryptoSymbol(symbol, quoteCurrency = 'USD') {
  const base = String(symbol ?? '').trim().toUpperCase();
  if (!base) return null;
  const cur = String(quoteCurrency ?? 'USD').trim().toUpperCase();
  return `${base}-${cur}`;
}

function yahooOhlcRowsToPoints(rows) {
  if (!rows?.length) return [];
  return rows
    .map((d) => {
      const open = parseFloat(d.open);
      const high = parseFloat(d.high);
      const low = parseFloat(d.low);
      const close = parseFloat(d.close);
      const t = Number(d.time);
      if (![open, high, low, close, t].every(Number.isFinite) || close <= 0) return null;
      const sec = t < 1e11 ? t : Math.floor(t / 1000);
      return {
        timestamp: sec,
        displayOpen: open,
        displayHigh: high,
        displayLow: low,
        displayClose: close,
        rawOpen: open,
        rawHigh: high,
        rawLow: low,
        rawClose: close,
        volume: Number(d.volume ?? 0),
      };
    })
    .filter(Boolean)
    .sort((a, b) => a.timestamp - b.timestamp);
}

export function formatYahooChartSourceNote(yahooSymbol, uiCurrency, quoteCurrency) {
  if (!yahooSymbol) return 'Yahoo Finance — veri yok';
  const ui = String(uiCurrency ?? '').trim().toUpperCase();
  const quote = String(quoteCurrency ?? '').trim().toUpperCase();
  if (ui === 'TRY' || (quote && ui !== quote)) {
    return `Yahoo Finance (${yahooSymbol}) · grafik ${quote} bazlı (Yahoo'da ${ui}-çifti yok)`;
  }
  return `Yahoo Finance (${yahooSymbol})`;
}

/** TRY seçiliyken grafik verisi USD/EUR ise alt uyarı metni */
export function buildTryFallbackWarning(uiCurrency, quoteCurrency, rangeLabel) {
  const ui = String(uiCurrency ?? '').trim().toUpperCase();
  const quote = String(quoteCurrency ?? '').trim().toUpperCase();
  if (ui !== 'TRY' || !quote || quote === 'TRY') return null;
  const label = rangeLabel ? String(rangeLabel) : 'bu aralık';
  return `Bu aralık (${label}) için TRY verisi bulunamadı; şu an ${quote} bazlı grafik görüntülüyorsunuz.`;
}

/** StockChartResponse {timestamps(saniye), closePrices} → [[ms, price], …] */
function stockChartToLinePrices(res) {
  const ts = res?.timestamps ?? [];
  const closes = res?.closePrices ?? [];
  const prices = [];
  for (let i = 0; i < Math.min(ts.length, closes.length); i++) {
    const t = Number(ts[i]);
    const p = Number(closes[i]);
    if (!Number.isFinite(t) || !Number.isFinite(p) || p <= 0) continue;
    prices.push([t < 1e11 ? t * 1000 : t, p]);
  }
  return prices;
}

/**
 * Yahoo türevli serinin son değeri coin'in gerçek güncel fiyatından çok uzaksa (ör. HYPE-USD =
 * Hyperliquid değil, ölü bir 2021 token'ı) → yanlış token; reddet. Referans yoksa kabul eder.
 */
function lastPriceWithinTolerance(prices, currentPrice) {
  const cur = Number(currentPrice);
  if (!Number.isFinite(cur) || cur <= 0) return true;
  if (!prices?.length) return true;
  const last = Number(prices[prices.length - 1]?.[1]);
  if (!Number.isFinite(last) || last <= 0) return true;
  const ratio = last / cur;
  return ratio >= 0.2 && ratio <= 5;
}

/** 5Y / Tüm — Yahoo çizgi grafik (+ yetersiz Yahoo'da CoinGecko) */
export async function fetchYahooCryptoLineChart(symbol, uiCurrency, yahooRange, yahooInterval, coinId, currentPrice) {
  const ui = String(uiCurrency ?? '').trim().toUpperCase();

  // "Tüm" (max) + TL: Yahoo USD × USD/TRY çevrimi ÖNCE denenir — Binance {SYMBOL}TRY ~2019,
  // CoinGecko TRY ~1 yıl ile sınırlıyken Yahoo tam geçmişi (2014+) TL'ye çevirebilir.
  let yahooTokenSuspect = false; // Yahoo ticker çakışması tespit edilirse Yahoo'yu tamamen atla
  if (ui === 'TRY' && yahooRange === 'max') {
    try {
      const res = await getCryptoYahooTryChart(symbol, 'max');
      const prices = stockChartToLinePrices(res);
      if (isAdequateRemoteLinePrices(prices)) {
        // currentPrice (TL) ile tutarlılık kontrolü — Yahoo ticker çakışmasını ayıklar.
        if (lastPriceWithinTolerance(prices, currentPrice)) {
          return {
            prices,
            total_volumes: [],
            yahooSymbol: res?.symbol ?? null,
            quoteCurrency: 'TRY',
            sourceNote: "Yahoo Finance · USD fiyat × USD/TRY ile TL'ye çevrildi",
            tryFallbackWarning: null,
          };
        }
        yahooTokenSuspect = true; // yanlış token → USD/EUR Yahoo'yu da atla, CoinGecko'ya git
      }
    } catch {
      /* aşağıdaki mevcut akışa düş */
    }
  }

  // Binance native TL — 5Y için ÖNCELİKLİ (gerçek TL piyasası, FX çevrimi DEĞİL; günlük 5y veri).
  // {SYMBOL}TRY pair'i olan coinler (BTC/ETH/…) burada doğru/canlı TL serisini alır.
  const binance = await fetchBinanceTryCryptoChart(symbol, uiCurrency, yahooRange);
  if (binance?.prices?.length && isAdequateRemoteLinePrices(binance.prices)) {
    return {
      prices: binance.prices,
      total_volumes: [],
      yahooSymbol: null,
      quoteCurrency: 'TRY',
      sourceNote: binance.sourceNote,
      tryFallbackWarning: null,
    };
  }

  // "5Y" + TL: Binance'te {SYMBOL}TRY pair'i YOK (ör. stablecoin DAI) → USD'ye düşmeden önce
  // Yahoo USD × USD/TRY çevrimine düş. "Tüm"de TL varken "5Y"de olmaması tutarsızlığını giderir;
  // Binance pair'i olan coinler yukarıda zaten native TL aldığından (BTC/ETH) bu yalnız pair'siz
  // coinlere çalışır — native 5Y TL serisini BOZMAZ.
  if (ui === 'TRY' && yahooRange === '5y' && !yahooTokenSuspect) {
    try {
      const res = await getCryptoYahooTryChart(symbol, '5y');
      const prices = stockChartToLinePrices(res);
      if (isAdequateRemoteLinePrices(prices) && lastPriceWithinTolerance(prices, currentPrice)) {
        return {
          prices,
          total_volumes: [],
          yahooSymbol: res?.symbol ?? null,
          quoteCurrency: 'TRY',
          sourceNote: "Yahoo Finance · USD fiyat × USD/TRY ile TL'ye çevrildi",
          tryFallbackWarning: null,
        };
      }
    } catch {
      /* Yahoo USD akışına düş */
    }
  }

  const range = yahooRangeToBinanceRange(yahooRange) ?? yahooRange;
  const rangeLabel = yahooRange === '5y' ? '5Y' : yahooRange === 'max' ? 'Tüm' : yahooRange;
  let sparseYahoo = null;
  for (const cur of resolveYahooQuoteCurrencies(uiCurrency)) {
    if (yahooTokenSuspect) break; // yanlış Yahoo token'ı (çakışma) → Yahoo'yu atla, CoinGecko'ya düş
    const yahooSym = toYahooCryptoSymbol(symbol, cur);
    if (!yahooSym) continue;
    try {
      const res = await getCryptoYahooChart(symbol, range, cur.toLowerCase());
      const ts = res?.timestamps ?? [];
      const closes = res?.closePrices ?? [];
      const prices = [];
      for (let i = 0; i < Math.min(ts.length, closes.length); i++) {
        const t = Number(ts[i]);
        const p = Number(closes[i]);
        if (!Number.isFinite(t) || !Number.isFinite(p) || p <= 0) continue;
        prices.push([t < 1e11 ? t * 1000 : t, p]);
      }
      if (!prices.length) continue;
      const payload = {
        prices,
        total_volumes: [],
        yahooSymbol: res?.symbol ?? yahooSym,
        quoteCurrency: cur,
        sourceNote: formatYahooChartSourceNote(res?.symbol ?? yahooSym, uiCurrency, cur),
        tryFallbackWarning: buildTryFallbackWarning(uiCurrency, cur, rangeLabel),
      };
      if (isAdequateRemoteLinePrices(prices)) return payload;
      sparseYahoo = payload;
    } catch {
      /* sonraki para birimi */
    }
  }

  if (coinId) {
    const cg = await fetchCoinGeckoLongLine(coinId, uiCurrency, yahooRange);
    if (isAdequateRemoteLinePrices(cg.prices)) {
      const cur = String(uiCurrency ?? 'usd').trim().toUpperCase();
      return {
        prices: cg.prices,
        total_volumes: cg.total_volumes ?? [],
        yahooSymbol: null,
        quoteCurrency: cur,
        sourceNote: formatCoinGeckoFallbackSource(uiCurrency),
        tryFallbackWarning: null,
      };
    }
  }

  return sparseYahoo ?? { prices: [], total_volumes: [], yahooSymbol: null, quoteCurrency: null, tryFallbackWarning: null };
}

/** 5Y / Tüm — Yahoo mum (+ yetersiz Yahoo'da CoinGecko) */
export async function fetchYahooCryptoOhlc(symbol, uiCurrency, yahooRange, yahooInterval, coinId) {
  const binance = await fetchBinanceTryCryptoChart(symbol, uiCurrency, yahooRange);
  if (binance?.points?.length && isAdequateRemoteChartPoints(binance.points)) {
    return {
      points: binance.points,
      yahooSymbol: null,
      quoteCurrency: 'TRY',
      sourceNote: binance.sourceNote,
      tryFallbackWarning: null,
    };
  }

  const range = yahooRangeToBinanceRange(yahooRange) ?? yahooRange;
  const rangeLabel = yahooRange === '5y' ? '5Y' : yahooRange === 'max' ? 'Tüm' : yahooRange;
  let sparseYahoo = null;
  for (const cur of resolveYahooQuoteCurrencies(uiCurrency)) {
    const yahooSym = toYahooCryptoSymbol(symbol, cur);
    if (!yahooSym) continue;
    try {
      const rows = await getCryptoYahooOhlc(symbol, range, cur.toLowerCase());
      const points = yahooOhlcRowsToPoints(rows);
      if (!points.length) continue;
      const payload = {
        points,
        yahooSymbol: yahooSym,
        quoteCurrency: cur,
        sourceNote: formatYahooChartSourceNote(yahooSym, uiCurrency, cur),
        tryFallbackWarning: buildTryFallbackWarning(uiCurrency, cur, rangeLabel),
      };
      if (isAdequateRemoteChartPoints(points)) return payload;
      sparseYahoo = payload;
    } catch {
      /* sonraki para birimi */
    }
  }

  if (coinId) {
    const cgPoints = await fetchCoinGeckoLongOhlc(coinId, uiCurrency, yahooRange);
    if (isAdequateRemoteChartPoints(cgPoints)) {
      const cur = String(uiCurrency ?? 'usd').trim().toUpperCase();
      return {
        points: cgPoints,
        yahooSymbol: null,
        quoteCurrency: cur,
        sourceNote: formatCoinGeckoFallbackSource(uiCurrency),
        tryFallbackWarning: null,
      };
    }
  }

  return sparseYahoo ?? { points: [], yahooSymbol: null, quoteCurrency: null, tryFallbackWarning: null };
}

/** CoinGecko market_chart — 5Y vb. için yedekli çekim */
export async function fetchReliableMarketChart(coinId, currency) {
  const cur = (currency ?? 'try').toLowerCase();
  const tries = [
    () => getCryptoChart(coinId, 'max', cur),
    () => getCryptoChart(coinId, 365, cur),
    () => getCryptoChart(coinId, 'max', cur, 'daily'),
    () => getCryptoChart(coinId, 365, cur, 'daily'),
  ];
  for (const fn of tries) {
    try {
      const data = await fn();
      if (data?.prices?.length) return data;
    } catch {
      /* sonraki deneme */
    }
  }
  return { prices: [], total_volumes: [] };
}

/** saniye (10 hane) / ms (13 hane) otomatik algıla */
export function normalizeTimestampMs(ts) {
  const n = Number(ts);
  if (!Number.isFinite(n)) return NaN;
  return n < 1e11 ? n * 1000 : n;
}

/** CoinGecko market_chart prices dizisini normalize et */
export function parseMarketChartPrices(data) {
  const raw = data?.prices;
  if (!Array.isArray(raw)) return [];
  return raw
    .map((p) => {
      if (Array.isArray(p)) {
        return [normalizeTimestampMs(p[0]), Number(p[1])];
      }
      if (p && typeof p === 'object') {
        const ts = p[0] ?? p.timestamp ?? p.t;
        const price = p[1] ?? p.price ?? p.value;
        return [normalizeTimestampMs(ts), Number(price)];
      }
      return null;
    })
    .filter((p) => Number.isFinite(p[0]) && Number.isFinite(p[1]) && p[1] > 0)
    .sort((a, b) => a[0] - b[0]);
}

export function alignVolumesToPrices(volumes, prices) {
  const volMap = buildVolumeMap(volumes);
  return prices.map((p) => [p[0], volMap.get(p[0]) ?? 0]);
}

const MIN_FULL_HISTORY_SPAN_MS = 500 * 86400000;
const MIN_FULL_HISTORY_POINTS = 400;

/** Yahoo/Binance sonrası CoinGecko'ya düşme eşiği (ör. KISHU-USD: 266 ay, tek geçerli fiyat) */
const MIN_REMOTE_CHART_POINTS = 24;
const MIN_REMOTE_CHART_SPAN_MS = 90 * 86400000;

export function isAdequateRemoteChartPoints(points) {
  if (!points?.length || points.length < MIN_REMOTE_CHART_POINTS) return false;
  const first = Number(points[0]?.timestamp);
  const last = Number(points[points.length - 1]?.timestamp);
  if (!Number.isFinite(first) || !Number.isFinite(last) || last <= first) return false;
  return (last - first) * 1000 >= MIN_REMOTE_CHART_SPAN_MS;
}

export function isAdequateRemoteLinePrices(prices) {
  if (!prices?.length || prices.length < MIN_REMOTE_CHART_POINTS) return false;
  const from = earliestTimestampMs(prices);
  return from != null && Date.now() - from >= MIN_REMOTE_CHART_SPAN_MS;
}

async function fetchCoinGeckoLongOhlc(coinId, uiCurrency, yahooRange) {
  const cur = (uiCurrency ?? 'usd').toLowerCase();
  if (yahooRange === 'max') {
    const full = await fetchFullHistoryOhlc(coinId, cur);
    if (full.length >= MIN_REMOTE_CHART_POINTS) return full;
  }
  const days = yahooRange === 'max' ? 'max' : 1825;
  try {
    const rows = await getCryptoOhlc(coinId, days, cur);
    return coingeckoOhlcRowsToPoints(rows);
  } catch {
    return [];
  }
}

async function fetchCoinGeckoLongLine(coinId, uiCurrency, yahooRange) {
  const cur = (uiCurrency ?? 'usd').toLowerCase();
  let data;
  if (yahooRange === 'max') {
    data = await fetchFullHistoryMarketChart(coinId, cur);
    if (data.prices?.length >= MIN_REMOTE_CHART_POINTS) return data;
  }
  data = await fetchReliableMarketChart(coinId, cur);
  const prices = parseMarketChartPrices(data);
  return {
    prices,
    total_volumes: alignVolumesToPrices(data?.total_volumes ?? [], prices),
  };
}

function formatCoinGeckoFallbackSource(currency) {
  const cur = String(currency ?? 'usd').trim().toUpperCase();
  return `CoinGecko (${cur}) — Yahoo'da yetersiz geçmiş veri`;
}

function isAdequateFullHistory(prices) {
  if (!prices?.length || prices.length < MIN_FULL_HISTORY_POINTS) return false;
  const from = earliestTimestampMs(prices);
  return from != null && Date.now() - from >= MIN_FULL_HISTORY_SPAN_MS;
}

/** Tüm — backend days=max + gerekirse genesis→bugün range (interval yok) */
export async function fetchFullHistoryMarketChart(coinId, currency) {
  const cur = (currency ?? 'try').toLowerCase();
  try {
    const data = await getCryptoChart(coinId, 'max', cur);
    const prices = parseMarketChartPrices(data);
    if (isAdequateFullHistory(prices)) {
      return {
        prices,
        total_volumes: alignVolumesToPrices(data?.total_volumes ?? [], prices),
      };
    }
  } catch (e) {
    const msg = e?.response?.data?.message ?? e?.message ?? 'Bilinmeyen hata';
    console.warn('Tüm geçmiş chart isteği başarısız:', msg);
  }
  return { prices: [], total_volumes: [] };
}

/** Tüm mum — yalnızca gerçek CoinGecko OHLC (fitil); sentetik mum yok */
export async function fetchFullHistoryOhlc(coinId, currency) {
  const cur = (currency ?? 'try').toLowerCase();
  try {
    const rows = await getCryptoOhlc(coinId, 'max', cur);
    const points = coingeckoOhlcRowsToPoints(rows);
    const from = points[0]?.timestamp != null ? points[0].timestamp * 1000 : null;
    const spanOk = from != null && Date.now() - from >= MIN_FULL_HISTORY_SPAN_MS;
    if (points.length >= 80 && spanOk) return points;
  } catch (e) {
    console.warn('Tüm geçmiş OHLC isteği başarısız', e);
  }
  return [];
}

export function buildVolumeMap(volumes) {
  const map = new Map();
  if (!volumes?.length) return map;
  for (const v of volumes) {
    const ts = Number(v[0]);
    if (Number.isFinite(ts)) {
      map.set(ts, (map.get(ts) ?? 0) + (Number(v[1]) || 0));
    }
  }
  return map;
}

export function earliestTimestampMs(prices) {
  if (!prices?.length) return null;
  let min = Infinity;
  for (const p of prices) {
    const ts = normalizeTimestampMs(p[0]);
    if (Number.isFinite(ts) && ts < min) min = ts;
  }
  return Number.isFinite(min) ? min : null;
}

/** Grafik eksen etiketi */
export function formatCryptoChartTimeLabel(timestampMs, granularity) {
  const d = new Date(Number(timestampMs));
  switch (granularity) {
    case 'minute':
    case 'hour':
      return d.toLocaleString('tr-TR', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' });
    case 'day':
      return d.toLocaleDateString('tr-TR', { day: '2-digit', month: 'short', year: '2-digit' });
    case 'week':
    case 'month':
      return d.toLocaleDateString('tr-TR', { month: 'short', year: '2-digit' });
    default:
      return d.toLocaleDateString('tr-TR');
  }
}

function weekCalendarKey(ts) {
  const d = new Date(ts);
  const day = d.getUTCDay() || 7;
  const thursday = new Date(d);
  thursday.setUTCDate(d.getUTCDate() + 4 - day);
  const year = thursday.getUTCFullYear();
  const yearStart = new Date(Date.UTC(year, 0, 1));
  const week = Math.ceil((((thursday - yearStart) / 86400000) + 1) / 7);
  return `${year}-W${week}`;
}

function resolveBucketKey(ts, bucketCalendar) {
  if (bucketCalendar === 'week') return weekCalendarKey(ts);
  return null;
}

/** 5Y çizgi — haftalık örnekleme */
export function downsamplePricesForLine(prices, volumes, bucketCalendar) {
  if (!prices?.length) return { prices: [], volumes: [] };

  const sorted = prices
    .map(p => ({
      ts: normalizeTimestampMs(p[0]),
      price: Number(p[1]),
    }))
    .filter(p => Number.isFinite(p.ts) && Number.isFinite(p.price) && p.price > 0)
    .sort((a, b) => a.ts - b.ts);

  const volMap = buildVolumeMap(volumes);

  if (!bucketCalendar) {
    return {
      prices: sorted.map(p => [p.ts, p.price]),
      volumes: sorted.map(p => [p.ts, volMap.get(p.ts) ?? 0]),
    };
  }

  const buckets = new Map();
  for (const p of sorted) {
    buckets.set(resolveBucketKey(p.ts, bucketCalendar), p);
  }

  const out = [...buckets.values()].sort((a, b) => a.ts - b.ts);
  return {
    prices: out.map(p => [p.ts, p.price]),
    volumes: out.map(p => [p.ts, volMap.get(p.ts) ?? 0]),
  };
}

function toChartPoint(tsMs, open, high, low, close, volume = 0) {
  const sec = Math.floor(tsMs / 1000);
  return {
    timestamp: sec,
    displayOpen: open,
    displayHigh: high,
    displayLow: low,
    displayClose: close,
    rawOpen: open,
    rawHigh: high,
    rawLow: low,
    rawClose: close,
    volume,
  };
}

/** CoinGecko /ohlc → [ts, o, h, l, c] */
export function coingeckoOhlcRowsToPoints(rows) {
  if (!rows?.length) return [];
  return rows
    .map(row => {
      const ts = normalizeTimestampMs(row[0]);
      const open = Number(row[1]);
      const high = Number(row[2]);
      const low = Number(row[3]);
      const close = Number(row[4]);
      if (![ts, open, high, low, close].every(Number.isFinite) || close <= 0) return null;
      return toChartPoint(ts, open, high, low, close, 0);
    })
    .filter(Boolean)
    .sort((a, b) => a.timestamp - b.timestamp);
}

/**
 * market_chart → mum OHLC (haftalık kova veya ham günlük nokta).
 */
export function pricesToOhlcPoints(prices, volumes, bucketCalendar) {
  if (!prices?.length) return [];

  const volByTs = buildVolumeMap(volumes);

  const sorted = prices
    .map(p => ({ ts: normalizeTimestampMs(p[0]), price: Number(p[1]) }))
    .filter(p => Number.isFinite(p.ts) && Number.isFinite(p.price) && p.price > 0)
    .sort((a, b) => a.ts - b.ts);

  if (!sorted.length) return [];

  if (!bucketCalendar) {
    const out = [];
    let prevClose = sorted[0].price;
    for (let i = 0; i < sorted.length; i++) {
      const { ts, price } = sorted[i];
      const open = i === 0 ? price : prevClose;
      out.push(toChartPoint(ts, open, Math.max(open, price), Math.min(open, price), price, volByTs.get(ts) ?? 0));
      prevClose = price;
    }
    return out;
  }

  const buckets = new Map();
  for (const { ts, price } of sorted) {
    const key = resolveBucketKey(ts, bucketCalendar);
    let b = buckets.get(key);
    if (!b) {
      b = { ts, open: price, high: price, low: price, close: price, volume: 0 };
      buckets.set(key, b);
    } else {
      b.ts = ts;
      b.close = price;
      b.high = Math.max(b.high, price);
      b.low = Math.min(b.low, price);
      b.volume += volByTs.get(ts) ?? 0;
    }
  }

  return [...buckets.values()]
    .sort((a, b) => a.ts - b.ts)
    .map(b => toChartPoint(b.ts, b.open, b.high, b.low, b.close, b.volume));
}
