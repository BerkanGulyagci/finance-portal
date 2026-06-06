import client from '../lib/http';

// Relative path → prod'da nginx, dev'de vite proxy üzerinden backend'e gider (hardcoded localhost DEĞİL).
export async function getIpos() {
  const { data: wrapper } = await client.get('/api/v1/market/ipo');
  return wrapper.data ?? [];
}
