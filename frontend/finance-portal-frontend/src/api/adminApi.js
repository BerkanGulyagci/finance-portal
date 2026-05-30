import client from '../lib/http';

export async function getUsers({ search, first = 0, max = 20, status = 'ALL', withTickets = false } = {}) {
  const params = { first, max, status };
  if (search?.trim()) {
    params.search = search.trim();
  }
  if (withTickets) {
    params.withTickets = true;
  }
  const { data: wrapper } = await client.get('/api/admin/users', { params });
  return wrapper.data;
}

export async function getUser(userId) {
  const { data: wrapper } = await client.get(`/api/admin/users/${userId}`);
  return wrapper.data;
}

export async function banUser(userId, body) {
  const { data: wrapper } = await client.post(`/api/admin/users/${userId}/ban`, body);
  return wrapper;
}

export async function unbanUser(userId) {
  const { data: wrapper } = await client.post(`/api/admin/users/${userId}/unban`);
  return wrapper;
}

// ── Eurobond ISIN listesi yönetimi ──
export async function getEurobondIsins() {
  const { data: wrapper } = await client.get('/api/admin/eurobonds/isins');
  return wrapper.data;
}

export async function refreshEurobondIsins(xlsxUrl) {
  const { data: wrapper } = await client.post('/api/admin/eurobonds/refresh', null, { params: { xlsxUrl } });
  return wrapper.data;
}
