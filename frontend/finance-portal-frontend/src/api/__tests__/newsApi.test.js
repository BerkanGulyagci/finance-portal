import { describe, it, expect, beforeEach, vi } from 'vitest';

// HTTP istemcilerini MOCK'la — gerçek ağ YOK.
// Kaynak iki yol kullanır:
//   * `import axios from 'axios'` → axios.get (getNews / getNewsDetail / getBloombergHtNews / getGoldNews)
//   * `import client from '../lib/http'` → client.get (getForMeNews)
// Test src/api/__tests__/ içinde olduğundan lib yolu '../../lib/http'.
vi.mock('axios', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn(), patch: vi.fn() },
}));
vi.mock('../../lib/http', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn(), patch: vi.fn() },
}));

import axios from 'axios';
import client from '../../lib/http';
import {
  proxyImageUrl,
  getNews,
  getForMeNews,
  getNewsDetail,
  getBloombergHtNews,
  getGoldNews,
} from '../newsApi';

const BASE_URL = 'http://localhost:8080';

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
    expect(out).toBe(`${BASE_URL}/api/v1/proxy/image?url=${encodeURIComponent(src)}`);
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
  it('argümansız çağrıldığında varsayılan page=1 & pageSize=12 ile axios.get çağırır', async () => {
    axios.get.mockResolvedValue({ data: { data: { items: [1], totalPages: 3 } } });

    const res = await getNews();

    expect(axios.get).toHaveBeenCalledTimes(1);
    expect(axios.get).toHaveBeenCalledWith(`${BASE_URL}/api/v1/news`, {
      params: { page: 1, pageSize: 12 },
    });
    expect(res).toEqual({ items: [1], totalPages: 3 });
  });

  it('verilen tüm filtreleri (category/source/q/range/region/lang + page/pageSize) params’a ekler', async () => {
    axios.get.mockResolvedValue({ data: { data: { items: [] } } });

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

    expect(axios.get).toHaveBeenCalledWith(`${BASE_URL}/api/v1/news`, {
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
    axios.get.mockResolvedValue({ data: { data: {} } });

    // category='' falsy → eklenmemeli; page=0 falsy → ?? sadece null/undefined’a baktığı için 0 KORUNUR.
    await getNews({ category: '', source: undefined, q: null, page: 0, pageSize: 0 });

    expect(axios.get).toHaveBeenCalledWith(`${BASE_URL}/api/v1/news`, {
      params: { page: 0, pageSize: 0 },
    });
    const sentParams = axios.get.mock.calls[0][1].params;
    expect(sentParams).not.toHaveProperty('category');
    expect(sentParams).not.toHaveProperty('source');
    expect(sentParams).not.toHaveProperty('q');
  });

  it('wrapper.data yoksa varsayılan boş haber zarfını döndürür', async () => {
    axios.get.mockResolvedValue({ data: {} }); // wrapper.data === undefined

    const res = await getNews();

    expect(res).toEqual({
      items: [], categories: [], sources: [], page: 1, pageSize: 12, totalElements: 0, totalPages: 1,
    });
  });

  it('axios reddederse hatayı yukarı fırlatır', async () => {
    axios.get.mockRejectedValue(new Error('network down'));
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
    // axios DEĞİL, korumalı client kullanılmalı (token gerektirir)
    expect(axios.get).not.toHaveBeenCalled();
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
    axios.get.mockResolvedValue({ data: { data: { id: 42, title: 'X' } } });

    const res = await getNewsDetail(42, 'en');

    expect(axios.get).toHaveBeenCalledWith(`${BASE_URL}/api/v1/news/detail/42`, {
      params: { lang: 'en' },
    });
    expect(res).toEqual({ id: 42, title: 'X' });
  });

  it('lang verilmediğinde boş params nesnesi ({}) ile çağırır', async () => {
    axios.get.mockResolvedValue({ data: { data: { id: 7 } } });

    await getNewsDetail(7);

    expect(axios.get).toHaveBeenCalledWith(`${BASE_URL}/api/v1/news/detail/7`, { params: {} });
  });

  it('wrapper.data yoksa null döndürür', async () => {
    axios.get.mockResolvedValue({ data: {} });
    const res = await getNewsDetail(1, 'tr');
    expect(res).toBeNull();
  });

  it('hata durumunda reddi yukarı taşır', async () => {
    axios.get.mockRejectedValue(new Error('404'));
    await expect(getNewsDetail(99)).rejects.toThrow('404');
  });
});

describe('getBloombergHtNews', () => {
  it('bloomberg-ht uç noktasını çağırır ve wrapper.data’yı döndürür', async () => {
    axios.get.mockResolvedValue({ data: { data: [{ t: 'a' }] } });

    const res = await getBloombergHtNews();

    expect(axios.get).toHaveBeenCalledWith(`${BASE_URL}/api/v1/news/bloomberg-ht`);
    expect(res).toEqual([{ t: 'a' }]);
  });

  it('wrapper.data yoksa boş dizi döndürür', async () => {
    axios.get.mockResolvedValue({ data: {} });
    const res = await getBloombergHtNews();
    expect(res).toEqual([]);
  });

  it('hata durumunda reddi yukarı taşır', async () => {
    axios.get.mockRejectedValue(new Error('boom'));
    await expect(getBloombergHtNews()).rejects.toThrow('boom');
  });
});

describe('getGoldNews', () => {
  it('gold uç noktasını çağırır ve wrapper.data’yı döndürür', async () => {
    const payload = { items: [{ id: 1 }], isFiltered: true, label: 'Altın' };
    axios.get.mockResolvedValue({ data: { data: payload } });

    const res = await getGoldNews();

    expect(axios.get).toHaveBeenCalledWith(`${BASE_URL}/api/v1/news/gold`);
    expect(res).toEqual(payload);
  });

  it('wrapper.data yoksa varsayılan altın zarfını döndürür', async () => {
    axios.get.mockResolvedValue({ data: {} });
    const res = await getGoldNews();
    expect(res).toEqual({ items: [], isFiltered: false, label: 'Son Haberler' });
  });

  it('hata durumunda reddi yukarı taşır', async () => {
    axios.get.mockRejectedValue(new Error('fail'));
    await expect(getGoldNews()).rejects.toThrow('fail');
  });
});
