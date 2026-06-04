import client from '../lib/http';

/** Mevcut bülten aboneliği: { subscribed, frequency, email } */
export async function getNewsletter() {
  const { data } = await client.get('/api/v1/newsletter/me');
  return data.data;
}

/** Aboneliği oluştur/güncelle. frequency: DAILY | WEEKLY | MONTHLY */
export async function updateNewsletter(frequency, enabled) {
  const { data } = await client.put('/api/v1/newsletter/me', { frequency, enabled });
  return data.data;
}
