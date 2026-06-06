import client from '../lib/http';

// Relative path kullanılır (BASE_URL hardcoded localhost DEĞİL) → prod'da nginx proxy,
// dev'de vite proxy üzerinden backend'e gider. Hem local hem cloud'da çalışır.

export function proxyImageUrl(url) {
  if (!url) return null;
  // Only proxy bloomberght images, others load directly
  if (url.includes('bloomberght.com')) {
    return `/api/v1/proxy/image?url=${encodeURIComponent(url)}`;
  }
  return url;
}

/**
 * Çoklu-kaynak, filtreli + sayfalı haber listesi (aggregator).
 * @param {{ category?: string, source?: string, q?: string, range?: string, page?: number, pageSize?: number }} filters
 * @returns {Promise<{ items, categories, sources, page, pageSize, totalElements, totalPages }>}
 */
export async function getNews(filters = {}) {
  const params = { page: filters.page ?? 1, pageSize: filters.pageSize ?? 12 };
  if (filters.category) params.category = filters.category;
  if (filters.source) params.source = filters.source;
  if (filters.q) params.q = filters.q;
  if (filters.range) params.range = filters.range;
  if (filters.region) params.region = filters.region;
  if (filters.lang) params.lang = filters.lang;

  const { data: wrapper } = await client.get('/api/v1/news', { params });
  return wrapper.data ?? {
    items: [], categories: [], sources: [], page: 1, pageSize: 12, totalElements: 0, totalPages: 1,
  };
}

/** Giriş yapan kullanıcının portföyüne göre "Size Özel" haberler (token gerektirir; client ile gönderilir). */
export async function getForMeNews({ lang, limit = 9 } = {}) {
  const params = { limit };
  if (lang) params.lang = lang;
  const { data: wrapper } = await client.get('/api/v1/news/for-me', { params });
  return wrapper.data ?? { items: [] };
}

/** Tek haber detayı + ilgili haberler. lang verilirse içerik o dile çevrilir (best-effort). */
export async function getNewsDetail(id, lang) {
  const params = lang ? { lang } : {};
  const { data: wrapper } = await client.get(`/api/v1/news/detail/${id}`, { params });
  return wrapper.data ?? null;
}

export async function getBloombergHtNews() {
  const { data: wrapper } = await client.get('/api/v1/news/bloomberg-ht');
  return wrapper.data ?? [];
}

export async function getGoldNews() {
  const { data: wrapper } = await client.get('/api/v1/news/gold');
  return wrapper.data ?? { items: [], isFiltered: false, label: 'Son Haberler' };
}
