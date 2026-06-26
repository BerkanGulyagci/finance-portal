import { describe, it, expect, beforeEach, vi } from 'vitest';

// HTTP istemcisini MOCK'la — gerçek ağ YOK.
// Tüm news çağrıları paylaşılan `client` (lib/http) üzerinden RELATIVE path ile gider
// (hardcoded localhost YOK → prod nginx / dev vite proxy). Test src/api/__tests__/ içinde
// olduğundan lib yolu '../../lib/http'.
vi.mock('../../lib/http', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn(), patch: vi.fn() },
}));

import client from '../../lib/http';
import {
  proxyImageUrl,
  getNews,
  getForMeNews,
  getNewsDetail,
} from '../newsApi';

beforeEach(() => {
  vi.clearAllMocks();
});

describe('proxyImageUrl', () => {
  it('url null/undefined/boş ise null döndürür', () => {
    expect(proxyImageUrl(null)).toBeNull();
    expect(proxyImageUrl(undefined)).toBeNull();
    expect(proxyImageUrl('')).toBeNull();
  });

  it('bloomberght.com görselini proxy URL’ine sarar (encode ederek)', () => {
    const src = 'https://www.bloomberght.com/img/foo bar.jpg?x=1';
    const out = proxyImageUrl(src);
    expect(out).toBe(`/api/v1/proxy/image?url=${encodeURIComponent(src)}`);
    // boşluk ve & encode edilmeli (ham URL gömülmemeli)
    expect(out).toContain('%20');
    expect(out).not.toContain('foo bar');
  });

  it('bloomberght dışındaki görseli olduğu gibi (doğrudan) döndürür', () => {
    const src = 'https://cdn.example.com/pic.png';
    expect(proxyImageUrl(src)).toBe(src);
  });
});

describe('getNews', () => {
  it('argümansız çağrıldığında varsayılan page=1 & pageSize=12 ile client.get çağırır', async () => {
    client.get.mockResolvedValue({ data: { data: { items: [1], totalPages: 3 } } });

    const res = await getNews();

    expect(client.get).toHaveBeenCalledTimes(1);
    expect(client.get).toHaveBeenCalledWith(`/api/v1/news`, {
      params: { page: 1, pageSize: 12 },
    });
    expect(res).toEqual({ items: [1], totalPages: 3 });
  });

  it('verilen tüm filtreleri (category/source/q/range/region/lang + page/pageSize) params’a ekler', async () => {
    client.get.mockResolvedValue({ data: { data: { items: [] } } });

    await getNews({
      category: 'CRYPTO',
      source: 'Bloomberg',
      q: 'btc',
      range: '7d',
      region: 'TR',
      lang: 'en',
      page: 2,
      pageSize: 24,
    });

    expect(client.get).toHaveBeenCalledWith(`/api/v1/news`, {
      params: {
        page: 2,
        pageSize: 24,
        category: 'CRYPTO',
        source: 'Bloomberg',
        q: 'btc',
        range: '7d',
        region: 'TR',
        lang: 'en',
      },
    });
  });

  it('boş/eksik opsiyonel filtreler params’a EKLENMEZ (sadece page & pageSize kalır)', async () => {
    client.get.mockResolvedValue({ data: { data: {} } });

    // category='' falsy → eklenmemeli; page=0 falsy → ?? sadece null/undefined’a baktığı için 0 KORUNUR.
    await getNews({ category: '', source: undefined, q: null, page: 0, pageSize: 0 });

    expect(client.get).toHaveBeenCalledWith(`/api/v1/news`, {
      params: { page: 0, pageSize: 0 },
    });
    const sentParams = client.get.mock.calls[0][1].params;
    expect(sentParams).not.toHaveProperty('category');
    expect(sentParams).not.toHaveProperty('source');
    expect(sentParams).not.toHaveProperty('q');
  });

  it('wrapper.data yoksa varsayılan boş haber zarfını döndürür', async () => {
    client.get.mockResolvedValue({ data: {} }); // wrapper.data === undefined

    const res = await getNews();

    expect(res).toEqual({
      items: [], categories: [], sources: [], page: 1, pageSize: 12, totalElements: 0, totalPages: 1,
    });
  });

  it('axios reddederse hatayı yukarı fırlatır', async () => {
    client.get.mockRejectedValue(new Error('network down'));
    await expect(getNews()).rejects.toThrow('network down');
  });
});

describe('getForMeNews', () => {
  it('lib/http client ile /api/v1/news/for-me adresine varsayılan limit=9 gönderir', async () => {
    client.get.mockResolvedValue({ data: { data: { items: [{ id: 1 }] } } });

    const res = await getForMeNews();

    expect(client.get).toHaveBeenCalledTimes(1);
    expect(client.get).toHaveBeenCalledWith('/api/v1/news/for-me', { params: { limit: 9 } });
    expect(res).toEqual({ items: [{ id: 1 }] });
  });

  it('lang verildiğinde params’a lang ekler ve özel limit’i geçirir', async () => {
    client.get.mockResolvedValue({ data: { data: { items: [] } } });

    await getForMeNews({ lang: 'tr', limit: 5 });

    expect(client.get).toHaveBeenCalledWith('/api/v1/news/for-me', {
      params: { limit: 5, lang: 'tr' },
    });
  });

  it('lang falsy ise params’a lang EKLENMEZ', async () => {
    client.get.mockResolvedValue({ data: { data: { items: [] } } });

    await getForMeNews({ lang: '', limit: 3 });

    expect(client.get.mock.calls[0][1].params).not.toHaveProperty('lang');
  });

  it('wrapper.data yoksa { items: [] } döndürür', async () => {
    client.get.mockResolvedValue({ data: {} });
    const res = await getForMeNews();
    expect(res).toEqual({ items: [] });
  });

  it('client reddederse hata yukarı fırlatılır', async () => {
    client.get.mockRejectedValue(new Error('401'));
    await expect(getForMeNews()).rejects.toThrow('401');
  });
});

describe('getNewsDetail', () => {
  it('lang verildiğinde id’li detay URL’ine { lang } params ile gider', async () => {
    client.get.mockResolvedValue({ data: { data: { id: 42, title: 'X' } } });

    const res = await getNewsDetail(42, 'en');

    expect(client.get).toHaveBeenCalledWith(`/api/v1/news/detail/42`, {
      params: { lang: 'en' },
    });
    expect(res).toEqual({ id: 42, title: 'X' });
  });

  it('lang verilmediğinde boş params nesnesi ({}) ile çağırır', async () => {
    client.get.mockResolvedValue({ data: { data: { id: 7 } } });

    await getNewsDetail(7);

    expect(client.get).toHaveBeenCalledWith(`/api/v1/news/detail/7`, { params: {} });
  });

  it('wrapper.data yoksa null döndürür', async () => {
    client.get.mockResolvedValue({ data: {} });
    const res = await getNewsDetail(1, 'tr');
    expect(res).toBeNull();
  });

  it('hata durumunda reddi yukarı taşır', async () => {
    client.get.mockRejectedValue(new Error('404'));
    await expect(getNewsDetail(99)).rejects.toThrow('404');
  });
});

