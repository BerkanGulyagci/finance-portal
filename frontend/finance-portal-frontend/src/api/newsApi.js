import axios from 'axios';

const BASE_URL = 'http://localhost:8080';

/**
 * Public endpoint — no auth token sent intentionally.
 * @param {{ category?: string, country?: string, keyword?: string, pageSize?: number }} filters
 * @returns {Promise<NewsItemDto[]>}
 */
export async function getNews(filters = {}) {
  const params = {};
  if (filters.category) params.category = filters.category;
  if (filters.country)  params.country  = filters.country;
  if (filters.keyword)  params.keyword  = filters.keyword;
  if (filters.pageSize) params.pageSize = filters.pageSize;

  const { data: wrapper } = await axios.get(`${BASE_URL}/api/news`, { params });
  return wrapper.data?.items ?? [];
}

export async function getBloombergHtNews() {
  const { data: wrapper } = await axios.get(`${BASE_URL}/api/news/bloomberg-ht`);
  // ApiResponse<List<NewsItemDto>>
  return wrapper.data ?? [];
}
