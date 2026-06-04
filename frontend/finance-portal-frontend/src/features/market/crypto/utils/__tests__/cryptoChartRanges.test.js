import { describe, it, expect, beforeEach, vi } from 'vitest';

// marketApi MOCK'lanır — gerçek ağ isteği atılmasın. Async fetch* fonksiyonlarının
// SAF koordinasyon/dallanma mantığı bu stub'larla test edilir. Geri kalan export'lar
// (parse/format/downsample/yeterlilik) tamamen SAF olduğundan API'ye dokunmaz.
vi.mock('../../../../../api/marketApi', () => ({
  getCryptoBinanceCandles: vi.fn(),
  getCryptoChart: vi.fn(),
  getCryptoOhlc: vi.fn(),
  getCryptoYahooChart: vi.fn(),
  getCryptoYahooOhlc: vi.fn(),
  getCryptoYahooTryChart: vi.fn(),
}));

import {
  getCryptoBinanceCandles,
  getCryptoChart,
  getCryptoOhlc,
  getCryptoYahooChart,
  getCryptoYahooOhlc,
  getCryptoYahooTryChart,
} from '../../../../../api/marketApi';

import {
  CRYPTO_CHART_RANGES,
  yahooRangeToBinanceRange,
  resolveYahooQuoteCurrencies,
  toYahooCryptoSymbol,
  formatYahooChartSourceNote,
  buildTryFallbackWarning,
  normalizeTimestampMs,
  parseMarketChartPrices,
  alignVolumesToPrices,
  isAdequateRemoteChartPoints,
  isAdequateRemoteLinePrices,
  buildVolumeMap,
  earliestTimestampMs,
  formatCryptoChartTimeLabel,
  downsamplePricesForLine,
  coingeckoOhlcRowsToPoints,
  pricesToOhlcPoints,
  fetchBinanceTryCryptoChart,
  fetchReliableMarketChart,
  fetchFullHistoryMarketChart,
  fetchFullHistoryOhlc,
  fetchYahooCryptoLineChart,
  fetchYahooCryptoOhlc,
} from '../cryptoChartRanges';

// Yardımcılar -------------------------------------------------------------
const DAY_MS = 86400000;
const nowSec = () => Math.floor(Date.now() / 1000);
// Yeterlilik eşiğini geçen (>=24 nokta, >=90 gün span) ms-tabanlı çizgi serisi üretir.
const adequateLine = (n = 30, spanDays = 120) => {
  const start = Date.now() - spanDays * DAY_MS;
  const step = (spanDays * DAY_MS) / (n - 1);
  return Array.from({ length: n }, (_, i) => [Math.round(start + i * step), 100 + i]);
};
// Yeterli OHLC nokta listesi (timestamp SANİYE cinsinden — kaynak öyle bekler).
const adequatePoints = (n = 30, spanDays = 120) => {
  const startSec = nowSec() - spanDays * 86400;
  const step = (spanDays * 86400) / (n - 1);
  return Array.from({ length: n }, (_, i) => ({
    timestamp: Math.round(startSec + i * step),
    displayClose: 100 + i,
  }));
};

beforeEach(() => {
  // resetAllMocks: çağrı geçmişi + İMPLEMENTASYONU (mockResolvedValue) sıfırlar.
  // clearAllMocks yalnız geçmişi siler → bir testin kalıcı mockResolvedValue(null)'ı
  // sonraki testlere sızıp async fetch'leri boş döndürüyordu.
  vi.resetAllMocks();
});

// =========================================================================
describe('CRYPTO_CHART_RANGES (sabit)', () => {
  it('8 aralık tanımlı ve etiketler beklenen sırada', () => {
    expect(CRYPTO_CHART_RANGES).toHaveLength(8);
    expect(CRYPTO_CHART_RANGES.map((r) => r.label)).toEqual([
      '1G', '1H', '1A', '3A', '6A', '1Y', '5Y', 'Tüm',
    ]);
  });

  it('CoinGecko aralıkları days/timeGranularity taşır, yahoo bayrağı taşımaz', () => {
    const cg = CRYPTO_CHART_RANGES.filter((r) => !r.yahoo);
    expect(cg).toHaveLength(6);
    cg.forEach((r) => {
      expect(typeof r.days).toBe('number');
      expect(typeof r.timeGranularity).toBe('string');
    });
    expect(cg.map((r) => r.days)).toEqual([1, 7, 30, 90, 180, 365]);
  });

  it('Yahoo aralıkları (5Y/Tüm) yahooRange + yahooInterval taşır', () => {
    const y = CRYPTO_CHART_RANGES.filter((r) => r.yahoo);
    expect(y.map((r) => r.yahooRange)).toEqual(['5y', 'max']);
    expect(y.map((r) => r.yahooInterval)).toEqual(['1wk', '1mo']);
  });
});

describe('yahooRangeToBinanceRange', () => {
  it('5y → "5y", max → "max"', () => {
    expect(yahooRangeToBinanceRange('5y')).toBe('5y');
    expect(yahooRangeToBinanceRange('max')).toBe('max');
  });

  it('bilinmeyen/null/undefined → null', () => {
    expect(yahooRangeToBinanceRange('1y')).toBeNull();
    expect(yahooRangeToBinanceRange(null)).toBeNull();
    expect(yahooRangeToBinanceRange(undefined)).toBeNull();
    expect(yahooRangeToBinanceRange('')).toBeNull();
  });
});

describe('resolveYahooQuoteCurrencies', () => {
  it('EUR → [EUR, USD] (büyük/küçük harf duyarsız)', () => {
    expect(resolveYahooQuoteCurrencies('EUR')).toEqual(['EUR', 'USD']);
    expect(resolveYahooQuoteCurrencies('eur')).toEqual(['EUR', 'USD']);
  });

  it('USD/TRY/diğer → [USD]', () => {
    expect(resolveYahooQuoteCurrencies('USD')).toEqual(['USD']);
    expect(resolveYahooQuoteCurrencies('TRY')).toEqual(['USD']);
    expect(resolveYahooQuoteCurrencies('gbp')).toEqual(['USD']);
  });

  it('null/undefined → varsayılan usd → [USD]', () => {
    expect(resolveYahooQuoteCurrencies(null)).toEqual(['USD']);
    expect(resolveYahooQuoteCurrencies(undefined)).toEqual(['USD']);
  });
});

describe('toYahooCryptoSymbol', () => {
  it('sembol + para birimi büyük harfe çevrilir', () => {
    expect(toYahooCryptoSymbol('btc', 'usd')).toBe('BTC-USD');
    expect(toYahooCryptoSymbol('  eth  ', 'eur')).toBe('ETH-EUR');
  });

  it('varsayılan para birimi USD', () => {
    expect(toYahooCryptoSymbol('sol')).toBe('SOL-USD');
  });

  it('boş/null/undefined sembol → null', () => {
    expect(toYahooCryptoSymbol('')).toBeNull();
    expect(toYahooCryptoSymbol('   ')).toBeNull();
    expect(toYahooCryptoSymbol(null)).toBeNull();
    expect(toYahooCryptoSymbol(undefined)).toBeNull();
  });

  it('para birimi null ise USD fallback', () => {
    expect(toYahooCryptoSymbol('btc', null)).toBe('BTC-USD');
  });
});

describe('formatYahooChartSourceNote', () => {
  it('sembol yoksa "veri yok" notu', () => {
    expect(formatYahooChartSourceNote(null)).toBe('Yahoo Finance — veri yok');
    expect(formatYahooChartSourceNote('')).toBe('Yahoo Finance — veri yok');
  });

  it('ui===quote → düz sembol notu', () => {
    expect(formatYahooChartSourceNote('BTC-USD', 'USD', 'USD')).toBe('Yahoo Finance (BTC-USD)');
  });

  it('TRY seçili → para-birimi-bazlı uyarı içeren not', () => {
    const note = formatYahooChartSourceNote('BTC-USD', 'TRY', 'USD');
    expect(note).toContain('grafik USD bazlı');
    expect(note).toContain('TRY-çifti yok');
    expect(note).toContain('BTC-USD');
  });

  it('ui !== quote (ör. EUR ui, USD quote) → bazlı uyarı', () => {
    const note = formatYahooChartSourceNote('ETH-USD', 'EUR', 'USD');
    expect(note).toContain('grafik USD bazlı');
    expect(note).toContain('EUR-çifti yok');
  });

  it('quote boş ise (ui!==quote dalı tetiklenmez) düz not döner', () => {
    expect(formatYahooChartSourceNote('BTC-USD', 'EUR', '')).toBe('Yahoo Finance (BTC-USD)');
  });
});

describe('buildTryFallbackWarning', () => {
  it('TRY ui + USD quote → uyarı metni (etiket gömülü)', () => {
    const w = buildTryFallbackWarning('TRY', 'USD', '5Y');
    expect(w).toContain('(5Y)');
    expect(w).toContain('USD bazlı');
  });

  it('rangeLabel yoksa "bu aralık" kullanılır', () => {
    expect(buildTryFallbackWarning('TRY', 'EUR')).toContain('(bu aralık)');
  });

  it('ui TRY değilse → null', () => {
    expect(buildTryFallbackWarning('USD', 'USD', '5Y')).toBeNull();
  });

  it('quote yok ya da quote===TRY → null', () => {
    expect(buildTryFallbackWarning('TRY', '', '5Y')).toBeNull();
    expect(buildTryFallbackWarning('TRY', 'TRY', '5Y')).toBeNull();
    expect(buildTryFallbackWarning('TRY', null, '5Y')).toBeNull();
  });
});

describe('normalizeTimestampMs', () => {
  it('saniye (<1e11) → ms (×1000)', () => {
    expect(normalizeTimestampMs(1_700_000_000)).toBe(1_700_000_000_000);
  });

  it('zaten ms (>=1e11) ise aynen', () => {
    expect(normalizeTimestampMs(1_700_000_000_000)).toBe(1_700_000_000_000);
  });

  it('sayısal string parse edilir', () => {
    expect(normalizeTimestampMs('1700000000')).toBe(1_700_000_000_000);
  });

  it('geçersiz → NaN', () => {
    expect(normalizeTimestampMs('abc')).toBeNaN();
    expect(normalizeTimestampMs(undefined)).toBeNaN();
    expect(normalizeTimestampMs(null)).toBe(0); // Number(null)=0 → finite → 0*1000
  });
});

describe('parseMarketChartPrices', () => {
  it('dizi-tipi noktaları normalize eder, saniye→ms', () => {
    const out = parseMarketChartPrices({ prices: [[1_700_000_000, 50]] });
    expect(out).toEqual([[1_700_000_000_000, 50]]);
  });

  it('obje-tipi noktaları (timestamp/price alanları) destekler', () => {
    const out = parseMarketChartPrices({
      prices: [{ timestamp: 1_700_000_000, price: 12 }],
    });
    expect(out).toEqual([[1_700_000_000_000, 12]]);
  });

  it('obje indeksli [0]/[1] alanları da okunur', () => {
    const obj = { 0: 1_700_000_000_000, 1: 7 };
    const out = parseMarketChartPrices({ prices: [obj] });
    expect(out).toEqual([[1_700_000_000_000, 7]]);
  });

  it('fiyat<=0 / NaN olanlar elenir, zamana göre sıralanır', () => {
    const out = parseMarketChartPrices({
      prices: [
        [2_000_000_000_000, 5],
        [1_000_000_000_000, 10],
        [1_500_000_000_000, 0], // 0 → elenir
        [1_600_000_000_000, -3], // negatif → elenir
      ],
    });
    expect(out).toEqual([
      [1_000_000_000_000, 10],
      [2_000_000_000_000, 5],
    ]);
  });

  it('prices dizi değilse / yoksa → boş dizi', () => {
    expect(parseMarketChartPrices({})).toEqual([]);
    expect(parseMarketChartPrices(null)).toEqual([]);
    expect(parseMarketChartPrices({ prices: 'x' })).toEqual([]);
  });
});

describe('alignVolumesToPrices', () => {
  it('zaman damgasına göre hacmi eşler; eşleşmeyen → 0', () => {
    const volumes = [[1000, 5], [2000, 9]];
    const prices = [[1000, 10], [3000, 20]];
    expect(alignVolumesToPrices(volumes, prices)).toEqual([[1000, 5], [3000, 0]]);
  });

  it('boş hacim → tüm fiyatlar 0 hacim', () => {
    expect(alignVolumesToPrices([], [[1000, 1]])).toEqual([[1000, 0]]);
  });
});

describe('isAdequateRemoteChartPoints', () => {
  it('yeterli nokta + yeterli span → true', () => {
    expect(isAdequateRemoteChartPoints(adequatePoints())).toBe(true);
  });

  it('boş/null/24 altı nokta → false', () => {
    expect(isAdequateRemoteChartPoints([])).toBe(false);
    expect(isAdequateRemoteChartPoints(null)).toBe(false);
    expect(isAdequateRemoteChartPoints(adequatePoints(10))).toBe(false);
  });

  it('yeterli nokta ama kısa span (<90 gün) → false', () => {
    expect(isAdequateRemoteChartPoints(adequatePoints(30, 10))).toBe(false);
  });

  it('last <= first (sıralı değil/eşit) → false', () => {
    const pts = Array.from({ length: 30 }, () => ({ timestamp: 1000 }));
    expect(isAdequateRemoteChartPoints(pts)).toBe(false);
  });
});

describe('isAdequateRemoteLinePrices', () => {
  it('yeterli nokta + yeterli geçmiş → true', () => {
    expect(isAdequateRemoteLinePrices(adequateLine())).toBe(true);
  });

  it('boş/24 altı → false', () => {
    expect(isAdequateRemoteLinePrices([])).toBe(false);
    expect(isAdequateRemoteLinePrices(adequateLine(5))).toBe(false);
  });

  it('yeterli nokta ama hepsi yakın geçmiş (<90 gün) → false', () => {
    expect(isAdequateRemoteLinePrices(adequateLine(30, 5))).toBe(false);
  });
});

describe('buildVolumeMap', () => {
  it('aynı zaman damgasındaki hacimleri toplar', () => {
    const map = buildVolumeMap([[1000, 5], [1000, 3], [2000, 7]]);
    expect(map.get(1000)).toBe(8);
    expect(map.get(2000)).toBe(7);
  });

  it('geçersiz ts atlanır, NaN hacim 0 sayılır', () => {
    const map = buildVolumeMap([['abc', 5], [3000, NaN]]);
    expect(map.has(NaN)).toBe(false);
    expect(map.get(3000)).toBe(0);
  });

  it('boş/null → boş Map', () => {
    expect(buildVolumeMap([]).size).toBe(0);
    expect(buildVolumeMap(null).size).toBe(0);
  });
});

describe('earliestTimestampMs', () => {
  it('en erken (normalize edilmiş ms) damgayı döner', () => {
    // 2000(sn)→2_000_000ms, 1_700_000_000(sn)→1.7e12ms, 3000(sn)→3_000_000ms → min 2_000_000
    expect(earliestTimestampMs([[2000, 1], [1_700_000_000, 2], [3000, 3]]))
      .toBe(2_000_000);
  });

  it('saniye girişleri ms\'e çevrilerek karşılaştırılır', () => {
    // normalizeTimestampMs: <1e11 olan değer SANİYE sayılıp ×1000 yapılır.
    // 1_700_000_000 → 1_700_000_000_000; 5000 → 5_000_000. Min = 5_000_000.
    expect(earliestTimestampMs([[1_700_000_000, 1], [5000, 2]])).toBe(5_000_000);
  });

  it('boş/null → null; hepsi geçersiz → null', () => {
    expect(earliestTimestampMs([])).toBeNull();
    expect(earliestTimestampMs(null)).toBeNull();
    expect(earliestTimestampMs([['x', 1]])).toBeNull();
  });
});

describe('formatCryptoChartTimeLabel', () => {
  const TS = Date.UTC(2024, 0, 15, 10, 30); // 15 Oca 2024

  it('minute/hour → gün+ay+saat içerir (string döner)', () => {
    const s = formatCryptoChartTimeLabel(TS, 'minute');
    expect(typeof s).toBe('string');
    expect(s.length).toBeGreaterThan(0);
    expect(formatCryptoChartTimeLabel(TS, 'hour').length).toBeGreaterThan(0);
  });

  it('day / week / month granülerlikleri string döner', () => {
    expect(typeof formatCryptoChartTimeLabel(TS, 'day')).toBe('string');
    expect(typeof formatCryptoChartTimeLabel(TS, 'week')).toBe('string');
    expect(typeof formatCryptoChartTimeLabel(TS, 'month')).toBe('string');
  });

  it('bilinmeyen granülerlik → varsayılan tarih biçimi', () => {
    expect(typeof formatCryptoChartTimeLabel(TS, 'bilinmeyen')).toBe('string');
    expect(typeof formatCryptoChartTimeLabel(TS)).toBe('string');
  });
});

describe('downsamplePricesForLine', () => {
  it('boş/null fiyat → boş prices+volumes', () => {
    expect(downsamplePricesForLine([])).toEqual({ prices: [], volumes: [] });
    expect(downsamplePricesForLine(null)).toEqual({ prices: [], volumes: [] });
  });

  it('bucketCalendar yok → ham (sıralı, normalize) + hacim eşleştirme', () => {
    // Gerçekçi ms timestamp (>1e11) → normalize değiştirmez; fiyat+hacim ts'i eşleşir.
    const t1 = Date.UTC(2024, 0, 1);
    const t2 = Date.UTC(2024, 0, 2);
    const out = downsamplePricesForLine(
      [[t2, 20], [t1, 10]],
      [[t1, 5]],
    );
    expect(out.prices).toEqual([[t1, 10], [t2, 20]]);
    expect(out.volumes).toEqual([[t1, 5], [t2, 0]]);
  });

  it('geçersiz (fiyat<=0/NaN) noktalar elenir', () => {
    const t1 = Date.UTC(2024, 0, 1), t2 = Date.UTC(2024, 0, 2), t3 = Date.UTC(2024, 0, 3);
    const out = downsamplePricesForLine([[t1, 0], [t2, -1], [t3, 9]], []);
    expect(out.prices).toEqual([[t3, 9]]);
  });

  it('week kovası → her ISO haftada SON nokta', () => {
    // 2024-01-01 Pzt, 02 Sal → aynı hafta; 08 Pzt → sonraki hafta.
    const monday = Date.UTC(2024, 0, 1);
    const tuesday = Date.UTC(2024, 0, 2);
    const nextMonday = Date.UTC(2024, 0, 8);
    const out = downsamplePricesForLine(
      [[monday, 10], [tuesday, 11], [nextMonday, 20]],
      [],
      'week',
    );
    expect(out.prices).toHaveLength(2);
    expect(out.prices[0]).toEqual([tuesday, 11]); // 1. haftanın SON noktası
    expect(out.prices[1]).toEqual([nextMonday, 20]);
  });
});

describe('coingeckoOhlcRowsToPoints', () => {
  it('[ts,o,h,l,c] satırlarını chart noktasına çevirir (ts saniyeye)', () => {
    const out = coingeckoOhlcRowsToPoints([[1_700_000_000_000, 10, 12, 9, 11]]);
    expect(out).toHaveLength(1);
    expect(out[0]).toMatchObject({
      timestamp: 1_700_000_000, // ms → sn (floor)
      displayOpen: 10,
      displayHigh: 12,
      displayLow: 9,
      displayClose: 11,
      rawClose: 11,
      volume: 0,
    });
  });

  it('close<=0 veya NaN alan → eler; zamana göre sıralar', () => {
    const out = coingeckoOhlcRowsToPoints([
      [3000, 1, 2, 0.5, 5],
      [1000, 1, 2, 0.5, 4],
      [2000, 1, 2, 0.5, 0], // close 0 → eler
      [4000, 1, 2, 0.5, NaN], // NaN → eler
    ]);
    expect(out.map((p) => p.displayClose)).toEqual([4, 5]);
  });

  it('boş/null → boş dizi', () => {
    expect(coingeckoOhlcRowsToPoints([])).toEqual([]);
    expect(coingeckoOhlcRowsToPoints(null)).toEqual([]);
  });
});

describe('pricesToOhlcPoints', () => {
  it('boş fiyat / hepsi geçersiz → boş dizi', () => {
    expect(pricesToOhlcPoints([])).toEqual([]);
    expect(pricesToOhlcPoints([[1000, 0], [2000, -5]])).toEqual([]);
  });

  it('kova yok → ardışık close\'ları open yapan günlük mumlar', () => {
    const out = pricesToOhlcPoints([[1000, 10], [2000, 12], [3000, 8]], []);
    expect(out).toHaveLength(3);
    // İlk: open=close=price
    expect(out[0]).toMatchObject({ displayOpen: 10, displayClose: 10, displayHigh: 10, displayLow: 10 });
    // İkinci: open=önceki close (10), close=12 → high 12, low 10
    expect(out[1]).toMatchObject({ displayOpen: 10, displayClose: 12, displayHigh: 12, displayLow: 10 });
    // Üçüncü: open=12, close=8 → high 12, low 8
    expect(out[2]).toMatchObject({ displayOpen: 12, displayClose: 8, displayHigh: 12, displayLow: 8 });
  });

  it('hacim eşleştirilir (kova yok dalı)', () => {
    // Gerçekçi ms timestamp (>1e11) → normalize değiştirmez, fiyat ve hacim ts'i eşleşir.
    const ts = Date.UTC(2024, 0, 15);
    const out = pricesToOhlcPoints([[ts, 10]], [[ts, 42]]);
    expect(out[0].volume).toBe(42);
  });

  it('week kovası → haftalık open/high/low/close + hacim toplamı', () => {
    const monday = Date.UTC(2024, 0, 1);
    const tuesday = Date.UTC(2024, 0, 2);
    const nextMonday = Date.UTC(2024, 0, 8);
    const out = pricesToOhlcPoints(
      [[monday, 10], [tuesday, 15], [nextMonday, 20]],
      [[monday, 1], [tuesday, 2]],
      'week',
    );
    expect(out).toHaveLength(2);
    // 1. hafta: open=10 (ilk), close=15 (son), high=15, low=10.
    // NOT: kovanın İLK noktasının hacmi sayılmaz (volume:0 ile başlar, yalnız sonraki
    // noktalar else dalında eklenir) → volume = 0 + 2 = 2 (monday'in 1'i sayılmaz).
    expect(out[0]).toMatchObject({
      displayOpen: 10, displayClose: 15, displayHigh: 15, displayLow: 10, volume: 2,
    });
    // 2. hafta tek nokta
    expect(out[1]).toMatchObject({ displayOpen: 20, displayClose: 20 });
  });
});

// =========================================================================
// Async fetch* — marketApi mock'lanarak dallanma mantığı doğrulanır.
// =========================================================================
describe('fetchBinanceTryCryptoChart', () => {
  it('uiCurrency TRY değil → null (API çağrılmaz)', async () => {
    expect(await fetchBinanceTryCryptoChart('BTC', 'USD', '5y')).toBeNull();
    expect(getCryptoBinanceCandles).not.toHaveBeenCalled();
  });

  it('range çözülemez (bilinmeyen yahooRange) → null', async () => {
    expect(await fetchBinanceTryCryptoChart('BTC', 'TRY', '1y')).toBeNull();
  });

  it('symbol yoksa → null', async () => {
    expect(await fetchBinanceTryCryptoChart('', 'TRY', '5y')).toBeNull();
  });

  it('boş candles → null (uyarı loglanır)', async () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
    getCryptoBinanceCandles.mockResolvedValueOnce([]);
    expect(await fetchBinanceTryCryptoChart('BTC', 'TRY', '5y')).toBeNull();
    warn.mockRestore();
  });

  it('geçerli candles → prices/points/sourceNote üretir', async () => {
    getCryptoBinanceCandles.mockResolvedValueOnce([
      { timestamp: 1_700_000_000, open: '1', high: '2', low: '0.5', close: '1.5', volume: '100' },
      { timestamp: 1_700_086_400, open: '1.5', high: '3', low: '1', close: '2.5', volume: '200' },
    ]);
    const res = await fetchBinanceTryCryptoChart('btc', 'try', '5y');
    expect(res.prices).toHaveLength(2);
    expect(res.points).toHaveLength(2);
    expect(res.sourceNote).toBe('Binance Spot (BTCTRY)');
    // saniye damgası ms'e çevrildi
    expect(res.prices[0][0]).toBe(1_700_000_000_000);
  });

  it('API hata fırlatırsa → null (yakalanır)', async () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
    getCryptoBinanceCandles.mockRejectedValueOnce(new Error('boom'));
    expect(await fetchBinanceTryCryptoChart('BTC', 'TRY', 'max')).toBeNull();
    warn.mockRestore();
  });
});

describe('fetchReliableMarketChart', () => {
  it('ilk başarılı deneme prices döndürür', async () => {
    getCryptoChart.mockResolvedValueOnce({ prices: [[1000, 1]], total_volumes: [] });
    const data = await fetchReliableMarketChart('bitcoin', 'usd');
    expect(data.prices).toHaveLength(1);
    expect(getCryptoChart).toHaveBeenCalledTimes(1);
  });

  it('ilk denemeler boş/hata → sonraki denemeye geçer', async () => {
    getCryptoChart
      .mockRejectedValueOnce(new Error('x')) // 1. dene: hata
      .mockResolvedValueOnce({ prices: [] }) // 2. dene: boş
      .mockResolvedValueOnce({ prices: [[1, 9]] }); // 3. dene: dolu
    const data = await fetchReliableMarketChart('eth');
    expect(data.prices).toEqual([[1, 9]]);
    expect(getCryptoChart).toHaveBeenCalledTimes(3);
  });

  it('tüm denemeler başarısız → boş yapı', async () => {
    getCryptoChart.mockResolvedValue({ prices: [] });
    const data = await fetchReliableMarketChart('doge');
    expect(data).toEqual({ prices: [], total_volumes: [] });
    expect(getCryptoChart).toHaveBeenCalledTimes(4);
  });
});

describe('fetchFullHistoryMarketChart', () => {
  it('yeterli tam geçmiş → prices + hizalı hacim', async () => {
    const prices = adequateLine(420, 600).map(([t, p]) => [t, p]);
    getCryptoChart.mockResolvedValueOnce({ prices, total_volumes: [] });
    const out = await fetchFullHistoryMarketChart('bitcoin', 'try');
    expect(out.prices.length).toBe(420);
    expect(out.total_volumes.length).toBe(420);
  });

  it('yetersiz geçmiş → boş yapı', async () => {
    getCryptoChart.mockResolvedValueOnce({ prices: adequateLine(30, 120), total_volumes: [] });
    const out = await fetchFullHistoryMarketChart('bitcoin', 'usd');
    expect(out).toEqual({ prices: [], total_volumes: [] });
  });

  it('API hata → boş yapı (uyarı loglanır)', async () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
    getCryptoChart.mockRejectedValueOnce(new Error('down'));
    expect(await fetchFullHistoryMarketChart('x')).toEqual({ prices: [], total_volumes: [] });
    warn.mockRestore();
  });
});

describe('fetchFullHistoryOhlc', () => {
  it('>=80 nokta + yeterli span → points', async () => {
    const startMs = Date.now() - 600 * DAY_MS;
    const rows = Array.from({ length: 90 }, (_, i) => {
      const ms = startMs + i * (600 * DAY_MS / 89);
      return [ms, 10, 12, 9, 11];
    });
    getCryptoOhlc.mockResolvedValueOnce(rows);
    const pts = await fetchFullHistoryOhlc('bitcoin', 'usd');
    expect(pts.length).toBe(90);
  });

  it('az nokta → boş dizi', async () => {
    getCryptoOhlc.mockResolvedValueOnce([[Date.now(), 1, 2, 0.5, 1]]);
    expect(await fetchFullHistoryOhlc('x')).toEqual([]);
  });

  it('API hata → boş dizi (uyarı loglanır)', async () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
    getCryptoOhlc.mockRejectedValueOnce(new Error('nope'));
    expect(await fetchFullHistoryOhlc('x')).toEqual([]);
    warn.mockRestore();
  });
});

describe('fetchYahooCryptoLineChart', () => {
  it('Binance native TL yeterli → TRY quote sonucu (Yahoo denenmez)', async () => {
    // ui=TRY + 5y → önce Binance; yeterli seri dön.
    getCryptoBinanceCandles.mockResolvedValueOnce(
      adequateLine(30, 120).map(([t, p]) => ({
        timestamp: Math.floor(t / 1000), open: p, high: p, low: p, close: p, volume: 0,
      })),
    );
    const res = await fetchYahooCryptoLineChart('BTC', 'TRY', '5y');
    expect(res.quoteCurrency).toBe('TRY');
    expect(res.prices.length).toBeGreaterThanOrEqual(24);
    expect(getCryptoYahooChart).not.toHaveBeenCalled();
  });

  it('USD: Yahoo yeterli seri → USD quote payload', async () => {
    getCryptoBinanceCandles.mockResolvedValueOnce(null); // TRY yolu yok
    const line = adequateLine(30, 120);
    getCryptoYahooChart.mockResolvedValueOnce({
      symbol: 'BTC-USD',
      timestamps: line.map(([t]) => Math.floor(t / 1000)),
      closePrices: line.map(([, p]) => p),
    });
    const res = await fetchYahooCryptoLineChart('BTC', 'USD', '5y');
    expect(res.quoteCurrency).toBe('USD');
    expect(res.yahooSymbol).toBe('BTC-USD');
    expect(res.sourceNote).toContain('Yahoo Finance');
  });

  it('Yahoo yetersiz + coinId var → CoinGecko fallback', async () => {
    getCryptoBinanceCandles.mockResolvedValue(null);
    const sparse = adequateLine(30, 5); // yeterli nokta ama kısa span → yetersiz
    getCryptoYahooChart.mockResolvedValueOnce({
      symbol: 'XYZ-USD',
      timestamps: sparse.map(([t]) => Math.floor(t / 1000)),
      closePrices: sparse.map(([, p]) => p),
    });
    // CoinGecko market_chart (max yolu): fetchFullHistoryMarketChart yetersiz → fetchReliableMarketChart
    getCryptoChart.mockResolvedValue({ prices: adequateLine(30, 120), total_volumes: [] });
    const res = await fetchYahooCryptoLineChart('XYZ', 'USD', '5y', '1wk', 'xyz-coin');
    expect(res.quoteCurrency).toBe('USD');
    expect(res.sourceNote).toContain('CoinGecko');
  });

  it('hiçbir kaynak yok → boş payload (prices boş)', async () => {
    getCryptoBinanceCandles.mockResolvedValue(null);
    getCryptoYahooChart.mockResolvedValue({ timestamps: [], closePrices: [] });
    const res = await fetchYahooCryptoLineChart('NONE', 'USD', '5y');
    expect(res.prices).toEqual([]);
    expect(res.quoteCurrency).toBeNull();
  });

  it('TRY max + Yahoo-TRY yeterli ve tolerans içi → TL çevrim sonucu', async () => {
    const line = adequateLine(30, 600);
    getCryptoYahooTryChart.mockResolvedValueOnce({
      symbol: 'BTC',
      timestamps: line.map(([t]) => Math.floor(t / 1000)),
      closePrices: line.map(([, p]) => p),
    });
    // currentPrice serinin son değerine yakın → tolerans içi
    const res = await fetchYahooCryptoLineChart('BTC', 'TRY', 'max', '1mo', null, 129);
    expect(res.quoteCurrency).toBe('TRY');
    expect(res.sourceNote).toContain("TL'ye çevrildi");
    expect(getCryptoBinanceCandles).not.toHaveBeenCalled();
  });
});

describe('fetchYahooCryptoOhlc', () => {
  it('Binance native TL points yeterli → TRY sonucu', async () => {
    // mockResolvedValue (kalıcı) — Once başka bir çağrıyla tükenip boş dönmesin.
    getCryptoBinanceCandles.mockResolvedValue(
      adequatePoints(30, 120).map((p) => ({
        timestamp: p.timestamp, open: 1, high: 1, low: 1, close: p.displayClose, volume: 0,
      })),
    );
    const res = await fetchYahooCryptoOhlc('BTC', 'TRY', '5y');
    expect(res.quoteCurrency).toBe('TRY');
    expect(res.points.length).toBeGreaterThanOrEqual(24);
    expect(getCryptoYahooOhlc).not.toHaveBeenCalled();
  });

  it('USD: Yahoo OHLC yeterli → USD payload', async () => {
    getCryptoBinanceCandles.mockResolvedValue(null);
    const startSec = nowSec() - 120 * 86400;
    const rows = Array.from({ length: 30 }, (_, i) => ({
      time: Math.round(startSec + i * (120 * 86400 / 29)),
      open: 1, high: 2, low: 0.5, close: 1.5, volume: 10,
    }));
    getCryptoYahooOhlc.mockResolvedValueOnce(rows);
    const res = await fetchYahooCryptoOhlc('BTC', 'USD', '5y');
    expect(res.quoteCurrency).toBe('USD');
    expect(res.points.length).toBe(30);
    expect(res.sourceNote).toContain('Yahoo Finance');
  });

  it('Yahoo yetersiz + coinId → CoinGecko OHLC fallback', async () => {
    getCryptoBinanceCandles.mockResolvedValue(null);
    getCryptoYahooOhlc.mockResolvedValueOnce([]); // boş → points yok
    const startMs = Date.now() - 600 * DAY_MS;
    const cgRows = Array.from({ length: 90 }, (_, i) => [
      startMs + i * (600 * DAY_MS / 89), 10, 12, 9, 11,
    ]);
    // yahooRange '5y' → fetchCoinGeckoLongOhlc days=1825 yolu (getCryptoOhlc)
    getCryptoOhlc.mockResolvedValueOnce(cgRows);
    const res = await fetchYahooCryptoOhlc('XYZ', 'USD', '5y', '1wk', 'xyz-coin');
    expect(res.quoteCurrency).toBe('USD');
    expect(res.sourceNote).toContain('CoinGecko');
    expect(res.points.length).toBe(90);
  });

  it('hiçbir kaynak yok → boş points payload', async () => {
    getCryptoBinanceCandles.mockResolvedValue(null);
    getCryptoYahooOhlc.mockResolvedValue([]);
    const res = await fetchYahooCryptoOhlc('NONE', 'USD', '5y');
    expect(res.points).toEqual([]);
    expect(res.quoteCurrency).toBeNull();
  });
});
