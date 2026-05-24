import client from './client';

// ── Bildirimler ───────────────────────────────────────────────────────────────
// Alarm tetiklendiğinde üretilen uygulama-içi bildirimler + gönderilen e-posta kaydı.

export async function getNotifications() {
  const { data: wrapper } = await client.get('/api/notifications');
  return wrapper.data ?? [];
}

export async function getUnreadCount() {
  const { data: wrapper } = await client.get('/api/notifications/unread-count');
  return wrapper.data ?? 0;
}

export async function markNotificationRead(id) {
  const { data: wrapper } = await client.post(`/api/notifications/${id}/read`);
  return wrapper.data;
}

export async function markAllNotificationsRead() {
  const { data: wrapper } = await client.post('/api/notifications/read-all');
  return wrapper.data;
}

export async function deleteNotification(id) {
  const { data: wrapper } = await client.delete(`/api/notifications/${id}`);
  return wrapper.data;
}
