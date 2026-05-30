import client from '../lib/http';

export async function getMe() {
  const { data } = await client.get('/api/me');
  return data.data;
}

export async function updateMeProfile(payload) {
  const { data } = await client.patch('/api/me/profile', payload);
  return data;
}

export async function updateMeEmail(payload) {
  const { data } = await client.patch('/api/me/email', payload);
  return data;
}

export async function changeMePassword(payload) {
  const { data } = await client.patch('/api/me/password', payload);
  return data;
}
