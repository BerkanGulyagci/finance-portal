import client from '../lib/http';

export async function getMe() {
  const { data } = await client.get('/api/v1/me');
  return data.data;
}

export async function updateMeProfile(payload) {
  const { data } = await client.patch('/api/v1/me/profile', payload);
  return data;
}

export async function updateMeEmail(payload) {
  const { data } = await client.patch('/api/v1/me/email', payload);
  return data;
}

export async function changeMePassword(payload) {
  const { data } = await client.patch('/api/v1/me/password', payload);
  return data;
}
