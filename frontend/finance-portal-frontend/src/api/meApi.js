import client from './client';

export async function getMe() {
  const { data } = await client.get('/api/me');
  return data.data;
}
