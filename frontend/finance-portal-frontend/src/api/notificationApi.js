import client from '../lib/http';

// ── Bildirimler ───────────────────────────────────────────────────────────────
// Alarm tetiklendiğinde üretilen uygulama-içi bildirimler + gönderilen e-posta kaydı.

export async function getNotifications() {
  const { data: wrapper } = await client.get('/api/v1/notifications');
  return wrapper.data ?? [];
}

export async function getUnreadCount() {
  const { data: wrapper } = await client.get('/api/v1/notifications/unread-count');
  return wrapper.data ?? 0;
}

export async function markNotificationRead(id) {
  const { data: wrapper } = await client.post(`/api/v1/notifications/${id}/read`);
  return wrapper.data;
}

export async function markAllNotificationsRead() {
  const { data: wrapper } = await client.post('/api/v1/notifications/read-all');
  return wrapper.data;
}

export async function deleteNotification(id) {
  const { data: wrapper } = await client.delete(`/api/v1/notifications/${id}`);
  return wrapper.data;
}
