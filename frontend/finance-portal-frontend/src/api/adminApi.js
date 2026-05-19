import client from './client';

export async function getUsers({ search, first = 0, max = 20 } = {}) {
  const params = { first, max };
  if (search?.trim()) {
    params.search = search.trim();
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
