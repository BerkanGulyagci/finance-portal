import { describe, it, expect, beforeEach, vi } from 'vitest';

// '../../lib/http' default export'u (axios benzeri client) MOCK'lanır — gerçek ağ isteği atılmasın.
// Tüm portfolioApi fonksiyonları bu client üzerinden istek atar; yalnız çağrı şekli + dönüş işleme doğrulanır.
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
import {
  bustMyPortfoliosCache,
  getMyPortfolios,
  getPortfolioById,
  getPortfolioAiAnalysis,
  getPortfolioAiAnalysisShared,
  getPortfolioRebalance,
  createPortfolio,
  updatePortfolio,
  deletePortfolio,
  addTransaction,
  getPriceAtDate,
  deleteTransaction,
  addCouponIncome,
  getPortfolioPerformance,
  getPortfolioWhatIf,
  getPortfolioWhatIfSeries,
  getWatchlistItems,
  addWatchlistItem,
  deleteWatchlistItem,
} from '../portfolioApi';

// `{ data: wrapper }` çıkarımı için yardımcı: client çoğu yerde { data: { data: X } } döndürür.
const wrap = (payload) => ({ data: { data: payload } });

beforeEach(() => {
  vi.clearAllMocks();
  // Modül-düzeyi cache'i (getMyPortfolios TTL + AI analiz cache + in-flight) testler arası temizle.
  bustMyPortfoliosCache();
});

describe('getMyPortfolios', () => {
  it("/api/v1/portfolios GET atar ve wrapper.data'yı döndürür", async () => {
    const items = [{ id: 1 }, { id: 2 }];
    client.get.mockResolvedValue(wrap(items));

    const out = await getMyPortfolios();

    expect(client.get).toHaveBeenCalledWith('/api/v1/portfolios');
    expect(out).toEqual(items);
  });

  it('TTL içinde ikinci çağrıda cache döndürür, backend yeniden çağrılmaz', async () => {
    const items = [{ id: 'a' }];
    client.get.mockResolvedValue(wrap(items));

    const first = await getMyPortfolios();
    const second = await getMyPortfolios();

    expect(first).toEqual(items);
    expect(second).toEqual(items);
    expect(client.get).toHaveBeenCalledTimes(1); // ikinci çağrı cache'ten
  });

  it('force=true verilince cache atlanır ve backend tekrar çağrılır', async () => {
    const items = [{ id: 'x' }];
    client.get.mockResolvedValue(wrap(items));

    await getMyPortfolios();
    await getMyPortfolios({ force: true });

    expect(client.get).toHaveBeenCalledTimes(2);
  });

  it('aktif (in-flight) bir istek varken eşzamanlı çağrılar aynı Promise\'ı paylaşır', async () => {
    let resolveFn;
    const deferred = new Promise((resolve) => { resolveFn = resolve; });
    client.get.mockReturnValue(deferred);

    const p1 = getMyPortfolios();
    const p2 = getMyPortfolios(); // henüz cache yok → in-flight Promise paylaşılmalı

    resolveFn(wrap([{ id: 'shared' }]));
    const [r1, r2] = await Promise.all([p1, p2]);

    expect(client.get).toHaveBeenCalledTimes(1);
    expect(r1).toEqual([{ id: 'shared' }]);
    expect(r2).toEqual([{ id: 'shared' }]);
  });

  it('istek bittikten sonra in-flight temizlenir (sonraki çağrı cache miss\'te yeniden atar)', async () => {
    client.get.mockResolvedValueOnce(wrap([{ id: 1 }]));
    await getMyPortfolios();
    // Cache'i busta → in-flight de null olmalı, yeni istek gider.
    bustMyPortfoliosCache();
    client.get.mockResolvedValueOnce(wrap([{ id: 2 }]));
    const out = await getMyPortfolios();
    expect(out).toEqual([{ id: 2 }]);
    expect(client.get).toHaveBeenCalledTimes(2);
  });
});

describe('bustMyPortfoliosCache', () => {
  it('cache\'i sıfırlar → sonraki getMyPortfolios backend\'e gider', async () => {
    client.get.mockResolvedValue(wrap([{ id: 1 }]));
    await getMyPortfolios();               // cache dolar
    bustMyPortfoliosCache();               // sıfırla
    await getMyPortfolios();               // tekrar backend
    expect(client.get).toHaveBeenCalledTimes(2);
  });
});

describe('getPortfolioById', () => {
  it('doğru URL ile GET atar ve wrapper.data döndürür', async () => {
    client.get.mockResolvedValue(wrap({ id: 7, name: 'Ana' }));
    const out = await getPortfolioById(7);
    expect(client.get).toHaveBeenCalledWith('/api/v1/portfolios/7');
    expect(out).toEqual({ id: 7, name: 'Ana' });
  });
});

describe('getPortfolioAiAnalysis', () => {
  it('/ai-analysis GET atar (lang param ile) ve wrapper.data döndürür', async () => {
    client.get.mockResolvedValue(wrap({ riskScore: 42 }));
    const out = await getPortfolioAiAnalysis(3, 'en');
    expect(client.get).toHaveBeenCalledWith(
      '/api/v1/portfolios/3/ai-analysis',
      { params: { lang: 'en' } },
    );
    expect(out).toEqual({ riskScore: 42 });
  });
});

describe('getPortfolioAiAnalysisShared', () => {
  it('light=true paramıyla GET atar ve wrapper.data döndürür', async () => {
    client.get.mockResolvedValue(wrap({ healthScore: 80 }));
    const out = await getPortfolioAiAnalysisShared(9);
    expect(client.get).toHaveBeenCalledWith('/api/v1/portfolios/9/ai-analysis', {
      params: { light: true },
    });
    expect(out).toEqual({ healthScore: 80 });
  });

  it('TTL içinde ikinci çağrı aynı Promise\'ı döndürür (tek istek)', async () => {
    client.get.mockResolvedValue(wrap({ healthScore: 1 }));
    const p1 = getPortfolioAiAnalysisShared(11);
    const p2 = getPortfolioAiAnalysisShared(11);
    expect(p1).toBe(p2); // aynı promise referansı (dedupe)
    await Promise.all([p1, p2]);
    expect(client.get).toHaveBeenCalledTimes(1);
  });

  it('farklı portföy id\'leri ayrı istek atar', async () => {
    client.get.mockResolvedValue(wrap({}));
    getPortfolioAiAnalysisShared(1);
    getPortfolioAiAnalysisShared(2);
    expect(client.get).toHaveBeenCalledTimes(2);
  });

  it('hata olursa cache silinir → sonraki çağrı yeniden istek atar', async () => {
    client.get.mockRejectedValueOnce(new Error('boom'));
    await expect(getPortfolioAiAnalysisShared(5)).rejects.toThrow('boom');
    // cache silindi → yeni çağrı tekrar backend'e gider
    client.get.mockResolvedValueOnce(wrap({ ok: true }));
    const out = await getPortfolioAiAnalysisShared(5);
    expect(out).toEqual({ ok: true });
    expect(client.get).toHaveBeenCalledTimes(2);
  });
});

describe('getPortfolioRebalance', () => {
  it('profile paramıyla /rebalance GET atar ve wrapper.data döndürür', async () => {
    client.get.mockResolvedValue(wrap({ target: [] }));
    const out = await getPortfolioRebalance(4, 'BALANCED');
    expect(client.get).toHaveBeenCalledWith('/api/v1/portfolios/4/rebalance', {
      params: { profile: 'BALANCED' },
    });
    expect(out).toEqual({ target: [] });
  });
});

describe('createPortfolio', () => {
  it('payload ile POST atar, cache\'i bustlar ve wrapper.data döndürür', async () => {
    const payload = { name: 'Yeni', description: 'd', currency: 'TRY', portfolioType: 'X' };
    client.post.mockResolvedValue(wrap({ id: 100 }));
    // Önce cache'i doldur ki bust gözlemlenebilsin.
    client.get.mockResolvedValue(wrap([{ id: 1 }]));
    await getMyPortfolios();

    const out = await createPortfolio(payload);

    expect(client.post).toHaveBeenCalledWith('/api/v1/portfolios', payload);
    expect(out).toEqual({ id: 100 });

    // bust sonrası getMyPortfolios tekrar backend'e gitmeli.
    await getMyPortfolios();
    expect(client.get).toHaveBeenCalledTimes(2);
  });
});

describe('updatePortfolio', () => {
  it('PATCH atar (doğru URL+body), cache bustlar, wrapper.data döndürür', async () => {
    const payload = { name: 'Düzeltildi', description: 'yeni' };
    client.patch.mockResolvedValue(wrap({ id: 6 }));
    client.get.mockResolvedValue(wrap([{ id: 1 }]));
    await getMyPortfolios();

    const out = await updatePortfolio(6, payload);

    expect(client.patch).toHaveBeenCalledWith('/api/v1/portfolios/6', payload);
    expect(out).toEqual({ id: 6 });
    await getMyPortfolios();
    expect(client.get).toHaveBeenCalledTimes(2);
  });
});

describe('deletePortfolio', () => {
  it('DELETE atar, cache bustlar, wrapper.data döndürür', async () => {
    client.delete.mockResolvedValue(wrap({ deleted: true }));
    client.get.mockResolvedValue(wrap([{ id: 1 }]));
    await getMyPortfolios();

    const out = await deletePortfolio(8);

    expect(client.delete).toHaveBeenCalledWith('/api/v1/portfolios/8');
    expect(out).toEqual({ deleted: true });
    await getMyPortfolios();
    expect(client.get).toHaveBeenCalledTimes(2);
  });
});

describe('addTransaction', () => {
  it('/transactions POST atar (body=payload), cache bustlar, wrapper.data döndürür', async () => {
    const payload = { symbol: 'AAPL', quantity: 5 };
    client.post.mockResolvedValue(wrap({ txId: 1 }));
    client.get.mockResolvedValue(wrap([{ id: 1 }]));
    await getMyPortfolios();

    const out = await addTransaction(2, payload);

    expect(client.post).toHaveBeenCalledWith('/api/v1/portfolios/2/transactions', payload);
    expect(out).toEqual({ txId: 1 });
    await getMyPortfolios();
    expect(client.get).toHaveBeenCalledTimes(2);
  });
});

describe('getPriceAtDate', () => {
  it('price-at GET atar (assetType/symbol/date params) ve data.data döndürür', async () => {
    client.get.mockResolvedValue({ data: { data: { price: 12.5, date: '2024-01-01', found: true } } });
    const out = await getPriceAtDate('STOCK', 'AAPL', '2024-01-01');
    expect(client.get).toHaveBeenCalledWith('/api/v1/portfolios/price-at', {
      params: { assetType: 'STOCK', symbol: 'AAPL', date: '2024-01-01' },
    });
    expect(out).toEqual({ price: 12.5, date: '2024-01-01', found: true });
  });

  it('data.data yoksa fallback { price:null, date:null, found:false } döndürür', async () => {
    client.get.mockResolvedValue({ data: { data: null } });
    const out = await getPriceAtDate('GOLD', 'XAU', '2020-05-05');
    expect(out).toEqual({ price: null, date: null, found: false });
  });

  it('data.data undefined (alan yok) iken de fallback döndürür', async () => {
    client.get.mockResolvedValue({ data: {} });
    const out = await getPriceAtDate('FX', 'USD', '2019-01-01');
    expect(out).toEqual({ price: null, date: null, found: false });
  });
});

describe('deleteTransaction', () => {
  it('doğru iç-içe URL ile DELETE atar, cache bustlar, wrapper.data döndürür', async () => {
    client.delete.mockResolvedValue(wrap({ ok: 1 }));
    client.get.mockResolvedValue(wrap([{ id: 1 }]));
    await getMyPortfolios();

    const out = await deleteTransaction(3, 77);

    expect(client.delete).toHaveBeenCalledWith('/api/v1/portfolios/3/transactions/77');
    expect(out).toEqual({ ok: 1 });
    await getMyPortfolios();
    expect(client.get).toHaveBeenCalledTimes(2);
  });
});

describe('addCouponIncome', () => {
  it('/coupon-income POST atar (body=payload), cache bustlar, wrapper.data döndürür', async () => {
    const payload = { symbol: 'TRT', amount: 100, paymentDate: '2024-03-03' };
    client.post.mockResolvedValue(wrap({ income: 100 }));
    client.get.mockResolvedValue(wrap([{ id: 1 }]));
    await getMyPortfolios();

    const out = await addCouponIncome(12, payload);

    expect(client.post).toHaveBeenCalledWith('/api/v1/portfolios/12/coupon-income', payload);
    expect(out).toEqual({ income: 100 });
    await getMyPortfolios();
    expect(client.get).toHaveBeenCalledTimes(2);
  });
});

describe('getPortfolioPerformance', () => {
  it('range+metric paramlarıyla /performance GET atar ve wrapper.data döndürür', async () => {
    client.get.mockResolvedValue(wrap([{ t: 1, v: 2 }]));
    const out = await getPortfolioPerformance(5, '1M', 'VALUE');
    expect(client.get).toHaveBeenCalledWith('/api/v1/portfolios/5/performance', {
      params: { range: '1M', metric: 'VALUE' },
    });
    expect(out).toEqual([{ t: 1, v: 2 }]);
  });
});

describe('getPortfolioWhatIf', () => {
  it('/what-if GET atar ve wrapper.data döndürür', async () => {
    client.get.mockResolvedValue(wrap({ gold: 10 }));
    const out = await getPortfolioWhatIf(6);
    expect(client.get).toHaveBeenCalledWith('/api/v1/portfolios/6/what-if');
    expect(out).toEqual({ gold: 10 });
  });
});

describe('getPortfolioWhatIfSeries', () => {
  it('sim dolu (assetType+symbol+amount+date) ise sim* paramları kullanılır', async () => {
    client.get.mockResolvedValue(wrap([{ d: 1 }]));
    const sim = { assetType: 'STOCK', symbol: 'AAPL', amount: 1000, date: '2024-01-01' };
    const out = await getPortfolioWhatIfSeries(7, 'IGNORED', 'IGNORED', [], sim);

    expect(client.get).toHaveBeenCalledWith('/api/v1/portfolios/7/what-if-series', {
      params: {
        simAssetType: 'STOCK',
        simSymbol: 'AAPL',
        simAmount: 1000,
        simDate: '2024-01-01',
      },
      paramsSerializer: { indexes: null },
    });
    expect(out).toEqual([{ d: 1 }]);
  });

  it('sim yokken assetType+symbol verilirse onlar param olur', async () => {
    client.get.mockResolvedValue(wrap([]));
    await getPortfolioWhatIfSeries(8, 'GOLD', 'XAU');
    expect(client.get).toHaveBeenCalledWith('/api/v1/portfolios/8/what-if-series', {
      params: { assetType: 'GOLD', symbol: 'XAU' },
      paramsSerializer: { indexes: null },
    });
  });

  it('benchmarks dolu ise benchmark dizisi param\'a eklenir', async () => {
    client.get.mockResolvedValue(wrap([]));
    await getPortfolioWhatIfSeries(9, 'STOCK', 'AAPL', ['XU100', 'GOLD']);
    expect(client.get).toHaveBeenCalledWith('/api/v1/portfolios/9/what-if-series', {
      params: { assetType: 'STOCK', symbol: 'AAPL', benchmark: ['XU100', 'GOLD'] },
      paramsSerializer: { indexes: null },
    });
  });

  it('ne sim ne assetType/symbol verilmezse params boş olur (varsayılan argümanlar)', async () => {
    client.get.mockResolvedValue(wrap([]));
    await getPortfolioWhatIfSeries(10);
    expect(client.get).toHaveBeenCalledWith('/api/v1/portfolios/10/what-if-series', {
      params: {},
      paramsSerializer: { indexes: null },
    });
  });

  it('eksik alanlı sim (amount/date yok) sim dalına girmez, assetType/symbol\'e düşer', async () => {
    client.get.mockResolvedValue(wrap([]));
    const sim = { assetType: 'STOCK', symbol: 'AAPL' }; // amount & date eksik
    await getPortfolioWhatIfSeries(11, 'GOLD', 'XAU', [], sim);
    expect(client.get).toHaveBeenCalledWith('/api/v1/portfolios/11/what-if-series', {
      params: { assetType: 'GOLD', symbol: 'XAU' },
      paramsSerializer: { indexes: null },
    });
  });
});

describe('getWatchlistItems', () => {
  it('/watchlist GET atar ve wrapper.data döndürür', async () => {
    client.get.mockResolvedValue(wrap([{ id: 1 }]));
    const out = await getWatchlistItems(3);
    expect(client.get).toHaveBeenCalledWith('/api/v1/portfolios/3/watchlist');
    expect(out).toEqual([{ id: 1 }]);
  });

  it('wrapper.data null/undefined ise boş dizi döndürür', async () => {
    client.get.mockResolvedValue({ data: { data: null } });
    const out = await getWatchlistItems(4);
    expect(out).toEqual([]);
  });
});

describe('addWatchlistItem', () => {
  it('/watchlist POST atar (body=payload), cache bustlar, wrapper.data döndürür', async () => {
    const payload = { symbol: 'AAPL', assetType: 'STOCK', notes: 'izle' };
    client.post.mockResolvedValue(wrap({ id: 55 }));
    client.get.mockResolvedValue(wrap([{ id: 1 }]));
    await getMyPortfolios();

    const out = await addWatchlistItem(2, payload);

    expect(client.post).toHaveBeenCalledWith('/api/v1/portfolios/2/watchlist', payload);
    expect(out).toEqual({ id: 55 });
    await getMyPortfolios();
    expect(client.get).toHaveBeenCalledTimes(2);
  });
});

describe('deleteWatchlistItem', () => {
  it('doğru iç-içe URL ile DELETE atar, cache bustlar, wrapper.data döndürür', async () => {
    client.delete.mockResolvedValue(wrap({ ok: true }));
    client.get.mockResolvedValue(wrap([{ id: 1 }]));
    await getMyPortfolios();

    const out = await deleteWatchlistItem(3, 99);

    expect(client.delete).toHaveBeenCalledWith('/api/v1/portfolios/3/watchlist/99');
    expect(out).toEqual({ ok: true });
    await getMyPortfolios();
    expect(client.get).toHaveBeenCalledTimes(2);
  });
});

describe('hata yolu (rejected)', () => {
  it('getPortfolioById backend hatasını yukarı fırlatır', async () => {
    client.get.mockRejectedValue(new Error('500'));
    await expect(getPortfolioById(1)).rejects.toThrow('500');
  });

  it('createPortfolio hata atarsa hata fırlatılır (bust çağrılmaz, await reject)', async () => {
    client.post.mockRejectedValue(new Error('bad'));
    await expect(createPortfolio({ name: 'x' })).rejects.toThrow('bad');
  });
});
