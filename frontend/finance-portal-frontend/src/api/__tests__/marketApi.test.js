import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';

// '../../lib/http' default export'u (axios client) MOCK'lanır — gerçek ağ isteği atılmasın.
vi.mock('../../lib/http', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
    patch: vi.fn(),
  },
}));

import client from '../../lib/http';
import * as api from '../marketApi';

// Yardımcı: client.get'i tek seferlik bir axios-benzeri yanıtla çözer ({ data: ... }).
const resolveGet = (data) => client.get.mockResolvedValueOnce({ data });

beforeEach(() => {
  vi.clearAllMocks();
});

// ── getStocks ────────────────────────────────────────────────────────────────
describe('getStocks', () => {
  it('index verilmediğinde sadece page/size ile çağırır ve data.data döner', async () => {
    resolveGet({ data: { content: [{ symbol: 'AKBNK.IS' }], totalPages: 3 } });
    const result = await api.getStocks();
    expect(client.get).toHaveBeenCalledWith('/api/market/stocks', { params: { page: 0, size: 20 } });
    expect(result).toEqual({ content: [{ symbol: 'AKBNK.IS' }], totalPages: 3 });
  });

  it('index verilince params.index eklenir', async () => {
    resolveGet({ data: {} });
    await api.getStocks(2, 50, 'XU030');
    expect(client.get).toHaveBeenCalledWith('/api/market/stocks', {
      params: { page: 2, size: 50, index: 'XU030' },
    });
  });

  it('data.data yoksa boş nesne döner', async () => {
    resolveGet({});
    expect(await api.getStocks()).toEqual({});
  });
});

// ── getCryptos / getAllCryptoCoins ───────────────────────────────────────────
describe('getCryptos ve getAllCryptoCoins', () => {
  it('getCryptos doğru URL/params ile çağırır ve diziyi döner', async () => {
    resolveGet({ data: [{ symbol: 'btc' }] });
    const r = await api.getCryptos(1, 50, 'usd');
    expect(client.get).toHaveBeenCalledWith('/api/market/crypto', {
      params: { page: 1, size: 50, currency: 'usd' },
    });
    expect(r).toEqual([{ symbol: 'btc' }]);
  });

  it('getCryptos data.data yoksa boş dizi döner', async () => {
    resolveGet({});
    expect(await api.getCryptos()).toEqual([]);
  });

  it('getAllCryptoCoins /all endpoint + currency ile çağırır', async () => {
    resolveGet({ data: [{ symbol: 'eth' }] });
    const r = await api.getAllCryptoCoins('eur');
    expect(client.get).toHaveBeenCalledWith('/api/market/crypto/all', { params: { currency: 'eur' } });
    expect(r).toEqual([{ symbol: 'eth' }]);
  });

  it('getAllCryptoCoins boş yanıtta boş dizi döner', async () => {
    resolveGet({});
    expect(await api.getAllCryptoCoins()).toEqual([]);
  });
});

// ── getAllCryptos (sayfalı döngü + rate-limit beklemesi) ─────────────────────
describe('getAllCryptos', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it('boş sayfa gelince döngüyü kırar ve toplanan öğeleri döner', async () => {
    // 1. sayfa dolu, 2. sayfa boş → break.
    resolveGet({ data: [{ symbol: 'a' }] });
    resolveGet({ data: [] });
    const promise = api.getAllCryptos('try');
    await vi.runAllTimersAsync();
    const r = await promise;
    expect(r).toEqual([{ symbol: 'a' }]);
    expect(client.get).toHaveBeenCalledTimes(2);
    expect(client.get).toHaveBeenNthCalledWith(1, '/api/market/crypto', {
      params: { page: 0, size: 250, currency: 'try' },
    });
  });

  it('hata fırlatınca döngü break eder (catch dalı)', async () => {
    client.get.mockRejectedValueOnce(new Error('rate limit'));
    const promise = api.getAllCryptos();
    await vi.runAllTimersAsync();
    const r = await promise;
    expect(r).toEqual([]);
    expect(client.get).toHaveBeenCalledTimes(1);
  });

  it('4 sayfa da dolu gelirse hepsini biriktirir', async () => {
    for (let i = 0; i < 4; i++) resolveGet({ data: [{ symbol: `c${i}` }] });
    const promise = api.getAllCryptos();
    await vi.runAllTimersAsync();
    const r = await promise;
    expect(r).toHaveLength(4);
    expect(client.get).toHaveBeenCalledTimes(4);
  });
});

// ── Kripto OHLC / chart yolları ──────────────────────────────────────────────
describe('getCryptoOhlc ve getCryptoChart', () => {
  it('getCryptoOhlc coinId encode edilir, params geçer, dizi döner', async () => {
    resolveGet({ data: [[1, 2, 3]] });
    const r = await api.getCryptoOhlc('bit coin', 30, 'usd');
    expect(client.get).toHaveBeenCalledWith('/api/market/crypto/bit%20coin/ohlc', {
      params: { days: 30, currency: 'usd' },
    });
    expect(r).toEqual([[1, 2, 3]]);
  });

  it('getCryptoOhlc boş yanıtta boş dizi döner', async () => {
    resolveGet({});
    expect(await api.getCryptoOhlc('btc')).toEqual([]);
  });

  it('getCryptoChart interval/aggregate verilince params ekler', async () => {
    resolveGet({ data: { points: [] } });
    await api.getCryptoChart('btc', 90, 'try', '1d', 2);
    expect(client.get).toHaveBeenCalledWith('/api/market/crypto/btc/chart', {
      params: { days: 90, currency: 'try', interval: '1d', aggregate: 2 },
    });
  });

  it('getCryptoChart interval/aggregate yoksa eklemez ve boş nesne döner', async () => {
    resolveGet({});
    const r = await api.getCryptoChart('btc');
    expect(client.get).toHaveBeenCalledWith('/api/market/crypto/btc/chart', {
      params: { days: 7, currency: 'try' },
    });
    expect(r).toEqual({});
  });
});

// ── Binance candles (success=false → throw) ──────────────────────────────────
describe('getCryptoBinanceCandles', () => {
  it('symbol lowercase + range/currency lowercase ile çağırır, dizi döner', async () => {
    resolveGet({ data: [{ t: 1 }] });
    const r = await api.getCryptoBinanceCandles('BTC', '5Y', 'USD');
    expect(client.get).toHaveBeenCalledWith('/api/market/crypto/btc/candles', {
      params: { range: '5Y', currency: 'usd' },
    });
    expect(r).toEqual([{ t: 1 }]);
  });

  it('success=false ise message ile hata fırlatır', async () => {
    resolveGet({ success: false, message: 'Borsa kapalı' });
    await expect(api.getCryptoBinanceCandles('eth', '1Y')).rejects.toThrow('Borsa kapalı');
  });

  it('success=false + message yoksa varsayılan mesajla hata fırlatır', async () => {
    resolveGet({ success: false });
    await expect(api.getCryptoBinanceCandles('eth', '1Y')).rejects.toThrow('Binance candles request failed');
  });

  it('data.data yoksa boş dizi döner', async () => {
    resolveGet({ success: true });
    expect(await api.getCryptoBinanceCandles('eth', '1Y')).toEqual([]);
  });

  it('currency null geldiğinde varsayılan try kullanır', async () => {
    resolveGet({ data: [] });
    await api.getCryptoBinanceCandles('btc', '1Y', null);
    expect(client.get).toHaveBeenCalledWith('/api/market/crypto/btc/candles', {
      params: { range: '1Y', currency: 'try' },
    });
  });
});

// ── Yahoo OHLC/chart yolları ─────────────────────────────────────────────────
describe('Yahoo kripto grafik fonksiyonları', () => {
  it('getCryptoYahooOhlc default currency usd, dizi döner', async () => {
    resolveGet({ data: [{ o: 1 }] });
    const r = await api.getCryptoYahooOhlc('BTC-USD', '5Y');
    expect(client.get).toHaveBeenCalledWith('/api/market/crypto/BTC-USD/yahoo/ohlc', {
      params: { range: '5Y', currency: 'usd' },
    });
    expect(r).toEqual([{ o: 1 }]);
  });

  it('getCryptoYahooOhlc boş yanıtta boş dizi', async () => {
    resolveGet({});
    expect(await api.getCryptoYahooOhlc('BTC-USD', '1Y', 'EUR')).toEqual([]);
  });

  it('getCryptoYahooChart currency verilince lowercase geçer, null fallback döner', async () => {
    resolveGet({});
    const r = await api.getCryptoYahooChart('ETH-EUR', '1Y', 'EUR');
    expect(client.get).toHaveBeenCalledWith('/api/market/crypto/ETH-EUR/yahoo/chart', {
      params: { range: '1Y', currency: 'eur' },
    });
    expect(r).toBeNull();
  });

  it('getCryptoYahooTryChart sadece range ile çağırır, data döner', async () => {
    resolveGet({ data: { points: [1] } });
    const r = await api.getCryptoYahooTryChart('BTC', '5Y');
    expect(client.get).toHaveBeenCalledWith('/api/market/crypto/BTC/yahoo/chart-try', {
      params: { range: '5Y' },
    });
    expect(r).toEqual({ points: [1] });
  });

  it('getCryptoDetail coinId encode + boş nesne fallback', async () => {
    resolveGet({});
    const r = await api.getCryptoDetail('btc usd');
    expect(client.get).toHaveBeenCalledWith('/api/market/crypto/btc%20usd/detail');
    expect(r).toEqual({});
  });
});

// ── normalizeBistSymbol (dolaylı: getStockMidasDetail / getStockChart …) ─────
describe('BIST sembol normalizasyonu (dolaylı)', () => {
  it('düz sembole .IS ekler', async () => {
    resolveGet({ data: { foo: 1 } });
    const r = await api.getStockMidasDetail('akbnk');
    expect(client.get).toHaveBeenCalledWith('/api/market/stocks/AKBNK.IS/midas');
    expect(r).toEqual({ foo: 1 });
  });

  it('zaten nokta içeren sembole .IS eklemez', async () => {
    resolveGet({ data: null });
    const r = await api.getStockMidasDetail('XU100.IS');
    expect(client.get).toHaveBeenCalledWith('/api/market/stocks/XU100.IS/midas');
    expect(r).toBeNull();
  });

  it('tire içeren (kripto) sembole .IS eklemez', async () => {
    resolveGet({ data: {} });
    await api.getStockChart('BTC-USD', '1d', '1m');
    expect(client.get).toHaveBeenCalledWith('/api/market/stocks/BTC-USD/chart', {
      params: { range: '1d', interval: '1m' },
    });
  });

  it('boş/null sembol boş string olarak normalize edilir', async () => {
    resolveGet({ data: {} });
    await api.getStockMidasDetail(null);
    expect(client.get).toHaveBeenCalledWith('/api/market/stocks//midas');
  });
});

// ── getStockChart / getStockOhlc ─────────────────────────────────────────────
describe('getStockChart ve getStockOhlc', () => {
  it('getStockChart varsayılan range/interval ile null fallback', async () => {
    resolveGet({});
    const r = await api.getStockChart('thyao');
    expect(client.get).toHaveBeenCalledWith('/api/market/stocks/THYAO.IS/chart', {
      params: { range: '1d', interval: '1m' },
    });
    expect(r).toBeNull();
  });

  it('getStockOhlc verilen range/interval ile dizi döner', async () => {
    resolveGet({ data: [{ c: 10 }] });
    const r = await api.getStockOhlc('garan', '3mo', '1d');
    expect(client.get).toHaveBeenCalledWith('/api/market/stocks/GARAN.IS/ohlc', {
      params: { range: '3mo', interval: '1d' },
    });
    expect(r).toEqual([{ c: 10 }]);
  });

  it('getStockOhlc boş yanıtta boş dizi döner', async () => {
    resolveGet({});
    expect(await api.getStockOhlc('garan')).toEqual([]);
  });
});

// ── getStockQuote (null + summary çıkarımı) ──────────────────────────────────
describe('getStockQuote', () => {
  it('data.data null ise null döner', async () => {
    resolveGet({ data: null });
    expect(await api.getStockQuote('akbnk')).toBeNull();
  });

  it('summary alanlarını seçip düzleştirilmiş nesne döner', async () => {
    resolveGet({
      data: {
        symbol: 'AKBNK.IS',
        name: 'Akbank',
        currency: 'TRY',
        summary: { price: 50.5, changePercent: 1.2 },
      },
    });
    const r = await api.getStockQuote('akbnk');
    expect(r).toEqual({
      symbol: 'AKBNK.IS',
      name: 'Akbank',
      price: 50.5,
      changePercent: 1.2,
      currency: 'TRY',
    });
  });

  it('summary yoksa price/changePercent undefined olur', async () => {
    resolveGet({ data: { symbol: 'X.IS', name: 'X', currency: 'TRY' } });
    const r = await api.getStockQuote('x');
    expect(r.price).toBeUndefined();
    expect(r.changePercent).toBeUndefined();
    expect(r.symbol).toBe('X.IS');
  });
});

// ── getMarketPriceHistory ────────────────────────────────────────────────────
describe('getMarketPriceHistory', () => {
  it('assetType/symbol/range params ile çağırır, data döner', async () => {
    resolveGet({ data: { timestamps: [1], closePrices: [2] } });
    const r = await api.getMarketPriceHistory('CRYPTO', 'btc', '5Y');
    expect(client.get).toHaveBeenCalledWith('/api/market/price-history', {
      params: { assetType: 'CRYPTO', symbol: 'btc', range: '5Y' },
    });
    expect(r).toEqual({ timestamps: [1], closePrices: [2] });
  });

  it('boş yanıtta {timestamps:[],closePrices:[]} döner', async () => {
    resolveGet({});
    expect(await api.getMarketPriceHistory('STOCK', 'akbnk')).toEqual({ timestamps: [], closePrices: [] });
  });
});

// ── getAllStocks (sayfalama döngüsü) ─────────────────────────────────────────
describe('getAllStocks', () => {
  it('totalPages>1 ise tüm sayfaları çeker ve birleştirir', async () => {
    // İlk istek (firstPage) — toHaveBeenCalled ile değil mockResolvedValueOnce ile.
    client.get
      .mockResolvedValueOnce({ data: { data: { content: [{ symbol: 'a' }], totalPages: 2 } } })
      .mockResolvedValueOnce({ data: { data: { content: [{ symbol: 'b' }] } } });
    const r = await api.getAllStocks();
    expect(r).toEqual([{ symbol: 'a' }, { symbol: 'b' }]);
    expect(client.get).toHaveBeenCalledTimes(2);
    expect(client.get).toHaveBeenNthCalledWith(2, '/api/market/stocks', { params: { page: 1, size: 20 } });
  });

  it('totalPages yoksa (1) sadece ilk sayfa döner', async () => {
    client.get.mockResolvedValueOnce({ data: { data: { content: [{ symbol: 'x' }] } } });
    const r = await api.getAllStocks();
    expect(r).toEqual([{ symbol: 'x' }]);
    expect(client.get).toHaveBeenCalledTimes(1);
  });

  it('content yoksa boş dizi döner', async () => {
    client.get.mockResolvedValueOnce({ data: {} });
    expect(await api.getAllStocks()).toEqual([]);
  });
});

// ── Endeksler ────────────────────────────────────────────────────────────────
describe('Endeks fonksiyonları', () => {
  it('getIndices liste döner, boşta boş dizi', async () => {
    resolveGet({ data: [{ code: 'XU100' }] });
    expect(await api.getIndices()).toEqual([{ code: 'XU100' }]);
    expect(client.get).toHaveBeenCalledWith('/api/market/indices');
  });

  it('getIndices boş yanıtta boş dizi', async () => {
    resolveGet({});
    expect(await api.getIndices()).toEqual([]);
  });

  it('getIndex code encode eder, null fallback', async () => {
    resolveGet({});
    const r = await api.getIndex('XU 100');
    expect(client.get).toHaveBeenCalledWith('/api/market/indices/XU%20100');
    expect(r).toBeNull();
  });

  it('getIndexConstituents diziyi döner', async () => {
    resolveGet({ data: ['AKBNK'] });
    const r = await api.getIndexConstituents('XBANK');
    expect(client.get).toHaveBeenCalledWith('/api/market/indices/XBANK/constituents');
    expect(r).toEqual(['AKBNK']);
  });

  it('getIndexConstituents boş yanıtta boş dizi', async () => {
    resolveGet({});
    expect(await api.getIndexConstituents('XBANK')).toEqual([]);
  });
});

// ── Futures / VİOP ───────────────────────────────────────────────────────────
describe('Futures ve VİOP fonksiyonları', () => {
  it('getFutures page/size ile çağırır, boş nesne fallback', async () => {
    resolveGet({});
    const r = await api.getFutures(1, 30);
    expect(client.get).toHaveBeenCalledWith('/api/market/futures', { params: { page: 1, size: 30 } });
    expect(r).toEqual({});
  });

  it('getViopContractDetail name param ile çağırır, null fallback', async () => {
    resolveGet({});
    const r = await api.getViopContractDetail('F_AKBNK0626');
    expect(client.get).toHaveBeenCalledWith('/api/market/futures/viop', {
      params: { name: 'F_AKBNK0626' },
    });
    expect(r).toBeNull();
  });

  it('getViopContracts dizi döner, boşta boş dizi', async () => {
    resolveGet({});
    expect(await api.getViopContracts()).toEqual([]);
    expect(client.get).toHaveBeenCalledWith('/api/market/futures/viop/contracts');
  });

  it('getViopContractsByUnderlying symbol ile URL kurar', async () => {
    resolveGet({ data: [{ name: 'x' }] });
    const r = await api.getViopContractsByUnderlying('AKBNK');
    expect(client.get).toHaveBeenCalledWith('/api/market/futures/viop/contracts/by-underlying/AKBNK');
    expect(r).toEqual([{ name: 'x' }]);
  });

  it('getViopChart ham response döner (success=false dahil)', async () => {
    const raw = { success: false, message: 'desteklenmiyor' };
    resolveGet(raw);
    const r = await api.getViopChart('F_X', 'ONE_MONTH');
    expect(client.get).toHaveBeenCalledWith('/api/market/futures/viop/chart', {
      params: { name: 'F_X', period: 'ONE_MONTH' },
    });
    expect(r).toBe(raw);
  });

  it('getViopContractSpec spec döner, yoksa null', async () => {
    resolveGet({ data: { multiplier: 100 } });
    const r = await api.getViopContractSpec('F_AKBNK0626');
    expect(client.get).toHaveBeenCalledWith('/api/market/viop/spec', { params: { symbol: 'F_AKBNK0626' } });
    expect(r).toEqual({ multiplier: 100 });
  });

  it('getViopContractSpec boş yanıtta null', async () => {
    resolveGet({});
    expect(await api.getViopContractSpec('X')).toBeNull();
  });
});

// ── Fonlar ──────────────────────────────────────────────────────────────────
describe('Fon fonksiyonları', () => {
  it('getFunds page/size ile çağırır, boş nesne fallback', async () => {
    resolveGet({});
    const r = await api.getFunds(2, 10);
    expect(client.get).toHaveBeenCalledWith('/api/market/funds', { params: { page: 2, size: 10 } });
    expect(r).toEqual({});
  });

  it('getAllTefasFunds / getAllBesFunds / getAllOksFunds / getOsmanliFundBulletin doğru URL + boş dizi fallback', async () => {
    resolveGet({});
    expect(await api.getAllTefasFunds()).toEqual([]);
    expect(client.get).toHaveBeenCalledWith('/api/market/funds/tefas/all');

    resolveGet({ data: [1] });
    expect(await api.getAllBesFunds()).toEqual([1]);
    expect(client.get).toHaveBeenCalledWith('/api/market/funds/bes/all');

    resolveGet({});
    expect(await api.getAllOksFunds()).toEqual([]);
    expect(client.get).toHaveBeenCalledWith('/api/market/funds/oks/all');

    resolveGet({});
    expect(await api.getOsmanliFundBulletin()).toEqual([]);
    expect(client.get).toHaveBeenCalledWith('/api/market/funds/osmanli/bulletin');
  });

  it('getRasyonetFundDetail code encode + sourceCode param', async () => {
    resolveGet({ data: { code: 'AAK' } });
    const r = await api.getRasyonetFundDetail('A A K', 'TPF');
    expect(client.get).toHaveBeenCalledWith('/api/market/funds/tefas/A%20A%20K', {
      params: { sourceCode: 'TPF' },
    });
    expect(r).toEqual({ code: 'AAK' });
  });

  it('getRasyonetFundDetail varsayılan sourceCode TMF + null fallback', async () => {
    resolveGet({});
    const r = await api.getRasyonetFundDetail('AAK');
    expect(client.get).toHaveBeenCalledWith('/api/market/funds/tefas/AAK', { params: { sourceCode: 'TMF' } });
    expect(r).toBeNull();
  });

  it('getTefasFundDetail, getRasyonetFundDetail (TMF) çağrısına delege eder', async () => {
    resolveGet({ data: { code: 'B' } });
    const r = await api.getTefasFundDetail('B');
    expect(client.get).toHaveBeenCalledWith('/api/market/funds/tefas/B', { params: { sourceCode: 'TMF' } });
    expect(r).toEqual({ code: 'B' });
  });

  it('getFundPriceHistory range/type params + fallback nesne', async () => {
    resolveGet({});
    const r = await api.getFundPriceHistory('AAK', '5Y', 'EMK');
    expect(client.get).toHaveBeenCalledWith('/api/market/funds/AAK/price-history', {
      params: { range: '5Y', type: 'EMK' },
    });
    expect(r).toEqual({ code: 'AAK', range: '5Y', points: [] });
  });

  it('getTefasFundHistory ve getFundHistory ağ çağrısı yapmadan {points:[]} döner', async () => {
    const r1 = await api.getTefasFundHistory('AAK');
    const r2 = await api.getFundHistory('AAK');
    expect(r1).toEqual({ points: [] });
    expect(r2).toEqual({ points: [] });
    expect(client.get).not.toHaveBeenCalled();
  });
});

// ── getFundCompareHistory (tarih filtre + dönüşüm) ───────────────────────────
describe('getFundCompareHistory', () => {
  it('priceHistory boşsa boş seri döner', async () => {
    resolveGet({ data: { priceHistory: [] } });
    const r = await api.getFundCompareHistory('AAK', '1Y');
    expect(r).toEqual({ timestamps: [], closePrices: [] });
  });

  it('detail null ise boş seri döner', async () => {
    resolveGet({});
    const r = await api.getFundCompareHistory('AAK');
    expect(r).toEqual({ timestamps: [], closePrices: [] });
  });

  it('geçerli noktaları saniye-timestamp + closePrice olarak sıralı döner', async () => {
    resolveGet({
      data: {
        priceHistory: [
          { date: '2025-01-02', price: '12.5' },
          { date: '2025-01-01', price: '10' },
        ],
      },
    });
    const r = await api.getFundCompareHistory('AAK', '5Y');
    // Tarihe göre artan sıralama.
    expect(r.closePrices).toEqual([10, 12.5]);
    expect(r.timestamps).toHaveLength(2);
    expect(r.timestamps[0]).toBeLessThan(r.timestamps[1]);
    expect(r.timestamps[0]).toBe(Math.floor(new Date('2025-01-01').getTime() / 1000));
  });

  it('geçersiz tarih/fiyat noktalarını eler', async () => {
    resolveGet({
      data: {
        priceHistory: [
          { date: 'gecersiz', price: '5' },
          { date: '2025-03-01', price: 'NaN' },
          { date: '2025-03-02', price: '7.5' },
        ],
      },
    });
    // ALL → cutoff uygulanmaz (tarihe bağımlı kırılmasın); sadece geçersiz tarih/fiyat elenir.
    const r = await api.getFundCompareHistory('AAK', 'ALL');
    expect(r.closePrices).toEqual([7.5]);
  });

  it('ALL/MAX aralığında cutoff uygulanmaz (tüm noktalar kalır)', async () => {
    resolveGet({
      data: {
        priceHistory: [
          { date: '2000-01-01', price: '1' },
          { date: '2001-01-01', price: '2' },
        ],
      },
    });
    const r = await api.getFundCompareHistory('AAK', 'ALL');
    expect(r.closePrices).toEqual([1, 2]);
  });

  it('aralık cutoff eski noktaları kırpar', async () => {
    // 1M = 31 gün; çok eski tarih elenir, bugünkü tarih kalır.
    const today = new Date().toISOString().slice(0, 10);
    resolveGet({
      data: {
        priceHistory: [
          { date: '1990-01-01', price: '1' },
          { date: today, price: '99' },
        ],
      },
    });
    const r = await api.getFundCompareHistory('AAK', '1M');
    expect(r.closePrices).toEqual([99]);
  });
});

// ── Banka döviz kurları ──────────────────────────────────────────────────────
describe('Banka döviz kuru fonksiyonları', () => {
  it('getBankCurrencyRates dizi döner, boşta boş dizi', async () => {
    resolveGet({});
    expect(await api.getBankCurrencyRates()).toEqual([]);
    expect(client.get).toHaveBeenCalledWith('/api/market/currency/banks');
  });

  it('getBankCurrencyRatesByCurrency currency param ile çağırır', async () => {
    resolveGet({ data: [{ bank: 'X' }] });
    const r = await api.getBankCurrencyRatesByCurrency('USD');
    expect(client.get).toHaveBeenCalledWith('/api/market/currency/banks', { params: { currency: 'USD' } });
    expect(r).toEqual([{ bank: 'X' }]);
  });

  it('getBankCurrencyRatesByBank bankName encode eder', async () => {
    resolveGet({});
    await api.getBankCurrencyRatesByBank('Ziraat Bankası');
    expect(client.get).toHaveBeenCalledWith('/api/market/currency/banks/Ziraat%20Bankas%C4%B1');
  });
});

// ── FX ───────────────────────────────────────────────────────────────────────
describe('FX fonksiyonları', () => {
  it('getFxTcmb boş nesne fallback', async () => {
    resolveGet({});
    expect(await api.getFxTcmb()).toEqual({});
    expect(client.get).toHaveBeenCalledWith('/api/market/fx/tcmb/latest');
  });

  it('getFxOpen base param ile çağırır', async () => {
    resolveGet({ data: { rate: 1 } });
    const r = await api.getFxOpen('EUR');
    expect(client.get).toHaveBeenCalledWith('/api/market/fx/open/latest', { params: { base: 'EUR' } });
    expect(r).toEqual({ rate: 1 });
  });

  it('getFxHistory symbol/range params + null fallback', async () => {
    resolveGet({});
    const r = await api.getFxHistory('USDTRY', '3M');
    expect(client.get).toHaveBeenCalledWith('/api/market/fx/history', {
      params: { symbol: 'USDTRY', range: '3M' },
    });
    expect(r).toBeNull();
  });
});

// ── EVDS Tahvil ───────────────────────────────────────────────────────────────
describe('EVDS tahvil fonksiyonları', () => {
  it('getEvdsBonds varsayılan params (opsiyoneller eklenmez) + timeout', async () => {
    resolveGet({ data: { items: [{ code: 'TRT' }], totalItems: 1 } });
    const r = await api.getEvdsBonds();
    expect(client.get).toHaveBeenCalledWith('/api/market/bonds/evds', {
      params: { page: 0, size: 50, sortBy: 'maturityDate', sortDir: 'asc' },
      timeout: 240_000,
    });
    expect(r).toEqual({ items: [{ code: 'TRT' }], totalItems: 1 });
  });

  it('getEvdsBonds tüm opsiyonel filtreler verilince params ekler', async () => {
    resolveGet({ data: {} });
    await api.getEvdsBonds({
      page: 1,
      size: 25,
      search: 'TRT',
      type: 'KUPONLU',
      category: 'TUFE',
      minRemainingDays: 30,
      maxRemainingDays: 365,
      sortBy: 'code',
      sortDir: 'desc',
    });
    expect(client.get).toHaveBeenCalledWith('/api/market/bonds/evds', {
      params: {
        page: 1,
        size: 25,
        sortBy: 'code',
        sortDir: 'desc',
        search: 'TRT',
        type: 'KUPONLU',
        category: 'TUFE',
        minRemainingDays: 30,
        maxRemainingDays: 365,
      },
      timeout: 240_000,
    });
  });

  it('getEvdsBonds minRemainingDays=0 (null değil) eklenir', async () => {
    resolveGet({ data: {} });
    await api.getEvdsBonds({ minRemainingDays: 0, maxRemainingDays: 0 });
    const callArgs = client.get.mock.calls[0][1].params;
    expect(callArgs.minRemainingDays).toBe(0);
    expect(callArgs.maxRemainingDays).toBe(0);
  });

  it('getEvdsBonds boş yanıtta varsayılan sayfalama nesnesi döner', async () => {
    resolveGet({});
    const r = await api.getEvdsBonds({ size: 33 });
    expect(r).toEqual({
      items: [], totalItems: 0, totalPages: 0, page: 0, size: 33, hasNext: false, hasPrevious: false,
    });
  });

  it('getEvdsBondCategoryCounts timeout ile çağırır, boş nesne fallback', async () => {
    resolveGet({});
    const r = await api.getEvdsBondCategoryCounts();
    expect(client.get).toHaveBeenCalledWith('/api/market/bonds/evds/categories', { timeout: 120_000 });
    expect(r).toEqual({});
  });

  it('getEvdsBondDetail code encode + null fallback', async () => {
    resolveGet({});
    const r = await api.getEvdsBondDetail('TRT 123');
    expect(client.get).toHaveBeenCalledWith('/api/market/bonds/evds/TRT%20123');
    expect(r).toBeNull();
  });

  it('getEvdsBondHistory period param + boş dizi fallback', async () => {
    resolveGet({});
    const r = await api.getEvdsBondHistory('TRT', 'ONE_YEAR');
    expect(client.get).toHaveBeenCalledWith('/api/market/bonds/evds/TRT/history', {
      params: { period: 'ONE_YEAR' },
    });
    expect(r).toEqual([]);
  });
});

// ── Eurobond / Global tahvil ─────────────────────────────────────────────────
describe('Global tahvil fonksiyonları', () => {
  it('getGlobalBonds timeout ile çağırır, boş dizi fallback', async () => {
    resolveGet({});
    expect(await api.getGlobalBonds()).toEqual([]);
    expect(client.get).toHaveBeenCalledWith('/api/market/bonds/global', { timeout: 240_000 });
  });

  it('getGlobalBondDetail isin encode + timeout + null fallback', async () => {
    resolveGet({});
    const r = await api.getGlobalBondDetail('XS 1');
    expect(client.get).toHaveBeenCalledWith('/api/market/bonds/global/XS%201', { timeout: 120_000 });
    expect(r).toBeNull();
  });

  it('getGlobalBondChart range param + timeout + boş dizi fallback', async () => {
    resolveGet({ data: [{ p: 1 }] });
    const r = await api.getGlobalBondChart('XS123', '5Y');
    expect(client.get).toHaveBeenCalledWith('/api/market/bonds/global/XS123/chart', {
      params: { range: '5Y' },
      timeout: 120_000,
    });
    expect(r).toEqual([{ p: 1 }]);
  });
});

// ── Ekonomi göstergeleri ─────────────────────────────────────────────────────
describe('Ekonomi fonksiyonları', () => {
  it('getEconomicIndicators boş nesne fallback', async () => {
    resolveGet({});
    expect(await api.getEconomicIndicators()).toEqual({});
    expect(client.get).toHaveBeenCalledWith('/api/market/indicators');
  });

  it('getEconomy boş yanıtta {source:"",groups:[]} döner', async () => {
    resolveGet({});
    expect(await api.getEconomy()).toEqual({ source: '', groups: [] });
  });

  it('getEconomySeries key/full params + fallback nesne', async () => {
    resolveGet({});
    const r = await api.getEconomySeries('tufe', true);
    expect(client.get).toHaveBeenCalledWith('/api/market/economy/series', {
      params: { key: 'tufe', full: true },
    });
    expect(r).toEqual({
      key: 'tufe', label: '', unit: '', frequency: '', transform: 'raw', source: '', points: [],
    });
  });

  it('getEconomySeries varsayılan full=false', async () => {
    resolveGet({ data: { key: 'ufe', points: [1] } });
    const r = await api.getEconomySeries('ufe');
    expect(client.get).toHaveBeenCalledWith('/api/market/economy/series', {
      params: { key: 'ufe', full: false },
    });
    expect(r).toEqual({ key: 'ufe', points: [1] });
  });

  it('getEconomyCharts boş dizi fallback', async () => {
    resolveGet({});
    expect(await api.getEconomyCharts()).toEqual([]);
    expect(client.get).toHaveBeenCalledWith('/api/market/economy/charts');
  });

  it('getMarketMovers limit param + boş dizi fallback', async () => {
    resolveGet({});
    const r = await api.getMarketMovers(10);
    expect(client.get).toHaveBeenCalledWith('/api/market/movers', { params: { limit: 10 } });
    expect(r).toEqual([]);
  });

  it('getLoanRates boş yanıtta varsayılan nesne döner', async () => {
    resolveGet({});
    expect(await api.getLoanRates()).toEqual({
      personal: null, vehicle: null, housing: null, commercial: null, period: null, source: '',
    });
  });

  it('getDepositRates boş yanıtta varsayılan nesne döner', async () => {
    resolveGet({});
    expect(await api.getDepositRates()).toEqual({
      upTo1Month: null, upTo3Months: null, upTo6Months: null, upTo1Year: null,
      inflationYoy: null, period: null, source: '',
    });
  });

  it('getEconomicCalendar from/to params + boş dizi fallback', async () => {
    resolveGet({ data: [{ event: 'CPI' }] });
    const r = await api.getEconomicCalendar('2025-01-01', '2025-12-31');
    expect(client.get).toHaveBeenCalledWith('/api/market/economy/calendar', {
      params: { from: '2025-01-01', to: '2025-12-31' },
    });
    expect(r).toEqual([{ event: 'CPI' }]);
  });
});

// ── Altın / Gümüş / Kıymetli madenler ────────────────────────────────────────
describe('Değerli maden fonksiyonları', () => {
  it('getGoldSpot null fallback', async () => {
    resolveGet({});
    expect(await api.getGoldSpot()).toBeNull();
    expect(client.get).toHaveBeenCalledWith('/api/gold/spot');
  });

  it('getGoldHistory range/currency params', async () => {
    resolveGet({ data: { points: [] } });
    const r = await api.getGoldHistory('3M', 'TRY');
    expect(client.get).toHaveBeenCalledWith('/api/gold/history', { params: { range: '3M', currency: 'TRY' } });
    expect(r).toEqual({ points: [] });
  });

  it('getSilverSpot null fallback', async () => {
    resolveGet({});
    expect(await api.getSilverSpot()).toBeNull();
    expect(client.get).toHaveBeenCalledWith('/api/silver/spot');
  });

  it('getSilverHistory varsayılan range/currency', async () => {
    resolveGet({});
    await api.getSilverHistory();
    expect(client.get).toHaveBeenCalledWith('/api/silver/history', { params: { range: '1M', currency: 'TRY' } });
  });

  it('getPreciousMetalSpot metal URL kurar, null fallback', async () => {
    resolveGet({});
    const r = await api.getPreciousMetalSpot('platinum');
    expect(client.get).toHaveBeenCalledWith('/api/precious-metals/platinum/spot');
    expect(r).toBeNull();
  });

  it('getPreciousMetalHistory metal URL + params', async () => {
    resolveGet({ data: { x: 1 } });
    const r = await api.getPreciousMetalHistory('palladium', '6M', 'USD');
    expect(client.get).toHaveBeenCalledWith('/api/precious-metals/palladium/history', {
      params: { range: '6M', currency: 'USD' },
    });
    expect(r).toEqual({ x: 1 });
  });
});

// ── Emtia ────────────────────────────────────────────────────────────────────
describe('Emtia fonksiyonları', () => {
  it('getCommodityList boş dizi fallback', async () => {
    resolveGet({});
    expect(await api.getCommodityList()).toEqual([]);
    expect(client.get).toHaveBeenCalledWith('/api/commodities/list');
  });

  it('getCommoditySpot symbol param + null fallback', async () => {
    resolveGet({});
    const r = await api.getCommoditySpot('GC=F');
    expect(client.get).toHaveBeenCalledWith('/api/commodities/spot', { params: { symbol: 'GC=F' } });
    expect(r).toBeNull();
  });

  it('getCommodityHistory interval verilince params ekler', async () => {
    resolveGet({ data: { points: [] } });
    await api.getCommodityHistory('CL=F', '1Y', '1d');
    expect(client.get).toHaveBeenCalledWith('/api/commodities/history', {
      params: { symbol: 'CL=F', range: '1Y', interval: '1d' },
    });
  });

  it('getCommodityHistory interval yoksa eklemez (null branch)', async () => {
    resolveGet({});
    const r = await api.getCommodityHistory('CL=F');
    expect(client.get).toHaveBeenCalledWith('/api/commodities/history', {
      params: { symbol: 'CL=F', range: '1M' },
    });
    expect(r).toBeNull();
  });
});

// ── searchAssetSymbols (tip switch + filtre + catch) ─────────────────────────
describe('searchAssetSymbols', () => {
  it('STOCK: query yokken ilk 20 sembolü döner', async () => {
    const content = Array.from({ length: 30 }, (_, i) => ({ symbol: `S${i}.IS` }));
    resolveGet({ data: { content } });
    const r = await api.searchAssetSymbols('STOCK');
    expect(client.get).toHaveBeenCalledWith('/api/market/stocks', { params: { page: 0, size: 100 } });
    expect(r).toHaveLength(20);
    expect(r[0]).toBe('S0.IS');
  });

  it('STOCK: query verilince filtreler ve ilk 15 döner', async () => {
    const content = [{ symbol: 'AKBNK.IS' }, { symbol: 'GARAN.IS' }, { symbol: 'AKSA.IS' }];
    resolveGet({ data: { content } });
    const r = await api.searchAssetSymbols('STOCK', 'ak');
    expect(r).toEqual(['AKBNK.IS', 'AKSA.IS']);
  });

  it('CRYPTO: sembolleri uppercase eder ve filtreler', async () => {
    resolveGet({ data: [{ symbol: 'btc' }, { symbol: 'eth' }, { symbol: 'bnb' }] });
    const r = await api.searchAssetSymbols('CRYPTO', 'b');
    expect(client.get).toHaveBeenCalledWith('/api/market/crypto', { params: { page: 0, size: 100 } });
    expect(r).toEqual(['BTC', 'BNB']);
  });

  it('CRYPTO: query yokken ilk 20 uppercase sembol', async () => {
    resolveGet({ data: [{ symbol: 'btc' }] });
    const r = await api.searchAssetSymbols('CRYPTO');
    expect(r).toEqual(['BTC']);
  });

  it('FX: rates sembollerini filtreler', async () => {
    resolveGet({ data: { rates: [{ symbol: 'USD' }, { symbol: 'EUR' }] } });
    const r = await api.searchAssetSymbols('FX', 'us');
    expect(client.get).toHaveBeenCalledWith('/api/market/fx/tcmb/latest');
    expect(r).toEqual(['USD']);
  });

  it('FX: query yokken ilk 20 döner', async () => {
    resolveGet({ data: { rates: [{ symbol: 'USD' }, { symbol: 'EUR' }] } });
    const r = await api.searchAssetSymbols('FX');
    expect(r).toEqual(['USD', 'EUR']);
  });

  it('FUND: statik liste, query yokken hepsi', async () => {
    const r = await api.searchAssetSymbols('FUND');
    expect(r).toContain('SPY');
    expect(client.get).not.toHaveBeenCalled();
  });

  it('FUND: query ile filtreler', async () => {
    const r = await api.searchAssetSymbols('FUND', 'xl');
    expect(r).toEqual(['XLF', 'XLK', 'XLE', 'XLV']);
  });

  it('FUTURE: statik liste, query ile filtreler', async () => {
    const r = await api.searchAssetSymbols('FUTURE', 'gc');
    expect(r).toEqual(['GC=F']);
  });

  it('FUTURE: query yokken tüm liste', async () => {
    const r = await api.searchAssetSymbols('FUTURE');
    expect(r).toContain('ES=F');
  });

  it('bilinmeyen tip boş dizi döner', async () => {
    const r = await api.searchAssetSymbols('UNKNOWN');
    expect(r).toEqual([]);
  });

  it('hata fırlatınca catch ile boş dizi döner', async () => {
    client.get.mockRejectedValueOnce(new Error('boom'));
    const r = await api.searchAssetSymbols('STOCK', 'ak');
    expect(r).toEqual([]);
  });
});

// ── getAssetPrice (tip switch + valid/invalid + catch) ───────────────────────
describe('getAssetPrice', () => {
  it('STOCK: summary.price varsa valid:true döner', async () => {
    resolveGet({ data: { summary: { price: 50, currency: 'TRY' } } });
    const r = await api.getAssetPrice('STOCK', 'AKBNK.IS');
    expect(client.get).toHaveBeenCalledWith('/api/market/stocks/AKBNK.IS');
    expect(r).toEqual({ valid: true, symbol: 'AKBNK.IS', price: 50, currency: 'TRY' });
  });

  it('FUTURE: STOCK ile aynı yolu kullanır', async () => {
    resolveGet({ data: { summary: { price: 12, currency: 'TRY' } } });
    const r = await api.getAssetPrice('FUTURE', 'F_X');
    expect(r.valid).toBe(true);
    expect(r.price).toBe(12);
  });

  it('STOCK: summary yok/price yoksa valid:false', async () => {
    resolveGet({ data: { summary: {} } });
    expect(await api.getAssetPrice('STOCK', 'X')).toEqual({ valid: false });
  });

  it('CRYPTO: eşleşen coin bulunca valid:true (TRY)', async () => {
    resolveGet({ data: [{ symbol: 'BTC', currentPrice: 100 }] });
    const r = await api.getAssetPrice('CRYPTO', 'btc');
    expect(client.get).toHaveBeenCalledWith('/api/market/crypto', { params: { page: 0, size: 250 } });
    expect(r).toEqual({ valid: true, symbol: 'btc', price: 100, currency: 'TRY' });
  });

  it('CRYPTO: coin bulunamazsa valid:false', async () => {
    resolveGet({ data: [{ symbol: 'ETH', currentPrice: 5 }] });
    expect(await api.getAssetPrice('CRYPTO', 'btc')).toEqual({ valid: false });
  });

  it('FX: eşleşen rate bulunca sell fiyatıyla valid:true', async () => {
    resolveGet({ data: { rates: [{ symbol: 'USD', sell: 32 }] } });
    const r = await api.getAssetPrice('FX', 'usd');
    expect(r).toEqual({ valid: true, symbol: 'usd', price: 32, currency: 'TRY' });
  });

  it('FX: rate bulunamazsa valid:false', async () => {
    resolveGet({ data: { rates: [] } });
    expect(await api.getAssetPrice('FX', 'gbp')).toEqual({ valid: false });
  });

  it('FUND: summary.price varsa valid:true', async () => {
    resolveGet({ data: { summary: { price: 9.5, currency: 'TRY' } } });
    const r = await api.getAssetPrice('FUND', 'AAK');
    expect(client.get).toHaveBeenCalledWith('/api/market/funds/AAK');
    expect(r).toEqual({ valid: true, symbol: 'AAK', price: 9.5, currency: 'TRY' });
  });

  it('FUND: price yoksa valid:false', async () => {
    resolveGet({ data: {} });
    expect(await api.getAssetPrice('FUND', 'AAK')).toEqual({ valid: false });
  });

  it('bilinmeyen tip valid:false döner', async () => {
    expect(await api.getAssetPrice('OTHER', 'X')).toEqual({ valid: false });
  });

  it('hata fırlatınca catch ile valid:false döner', async () => {
    client.get.mockRejectedValueOnce(new Error('boom'));
    expect(await api.getAssetPrice('STOCK', 'X')).toEqual({ valid: false });
  });
});
