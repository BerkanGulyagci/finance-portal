import client from '../lib/http';

// ── Alarmlar ──────────────────────────────────────────────────────────────────
// Alarm: kullanıcının bir enstrüman için kurduğu fiyat/değişim/hacim koşulu.
// Koşul sağlanınca uygulama-içi bildirim + onaylı e-postaya mail gönderilir.

export async function getAlarms() {
  const { data: wrapper } = await client.get('/api/alarms');
  return wrapper.data ?? [];
}

export async function getAlarm(alarmId) {
  const { data: wrapper } = await client.get(`/api/alarms/${alarmId}`);
  return wrapper.data;
}

export async function createAlarm(payload) {
  // payload: { assetType, symbol, instrumentName, metric, direction, threshold, frequency, note }
  const { data: wrapper } = await client.post('/api/alarms', payload);
  return wrapper.data;
}

export async function updateAlarm(alarmId, payload) {
  // payload: { metric?, direction?, threshold?, frequency?, status?, note? }
  const { data: wrapper } = await client.patch(`/api/alarms/${alarmId}`, payload);
  return wrapper.data;
}

export async function deleteAlarm(alarmId) {
  const { data: wrapper } = await client.delete(`/api/alarms/${alarmId}`);
  return wrapper.data;
}
