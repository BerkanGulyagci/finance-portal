import { describe, it, expect, beforeEach, vi } from 'vitest';

// '../../lib/http' default export'u (axios client) MOCK'lanır — gerçek ağ isteği atılmasın.
// Her API fonksiyonu yanıtı `{ data: wrapper }` olarak yıkıp `wrapper.data` döndürür.
vi.mock('../../lib/http', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
    patch: vi.fn(),
  },
}));

import client from '../../lib/http';
import {
  getNotifications,
  getUnreadCount,
  markNotificationRead,
  markAllNotificationsRead,
  deleteNotification,
} from '../notificationApi';

beforeEach(() => {
  vi.clearAllMocks();
});

// ─────────────────────────────────────────────────────────────────────────────
// getNotifications — GET /api/notifications, wrapper.data ?? []
// ─────────────────────────────────────────────────────────────────────────────
describe('getNotifications', () => {
  it('doğru URL ile GET çağırır ve wrapper.data dizisini döndürür', async () => {
    const list = [{ id: 1, message: 'Alarm tetiklendi' }, { id: 2, message: 'Bildirim' }];
    client.get.mockResolvedValue({ data: { data: list } });

    const result = await getNotifications();

    expect(client.get).toHaveBeenCalledTimes(1);
    expect(client.get).toHaveBeenCalledWith('/api/notifications');
    expect(result).toEqual(list);
  });

  it('wrapper.data null ise boş diziye düşer (?? [] dalı)', async () => {
    client.get.mockResolvedValue({ data: { data: null } });
    const result = await getNotifications();
    expect(result).toEqual([]);
  });

  it('wrapper.data undefined ise (alan yok) boş diziye düşer', async () => {
    client.get.mockResolvedValue({ data: {} });
    const result = await getNotifications();
    expect(result).toEqual([]);
  });

  it('hata durumunda reddi yukarı taşır (mockRejectedValue)', async () => {
    const err = new Error('ağ hatası');
    client.get.mockRejectedValue(err);
    await expect(getNotifications()).rejects.toThrow('ağ hatası');
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// getUnreadCount — GET /api/notifications/unread-count, wrapper.data ?? 0
// ─────────────────────────────────────────────────────────────────────────────
describe('getUnreadCount', () => {
  it('doğru URL ile GET çağırır ve sayıyı döndürür', async () => {
    client.get.mockResolvedValue({ data: { data: 7 } });

    const result = await getUnreadCount();

    expect(client.get).toHaveBeenCalledTimes(1);
    expect(client.get).toHaveBeenCalledWith('/api/notifications/unread-count');
    expect(result).toBe(7);
  });

  it('sayı 0 ise 0 döndürür (?? sıfırı yutmaz, nullish)', async () => {
    client.get.mockResolvedValue({ data: { data: 0 } });
    const result = await getUnreadCount();
    expect(result).toBe(0);
  });

  it('wrapper.data null ise 0’a düşer (?? 0 dalı)', async () => {
    client.get.mockResolvedValue({ data: { data: null } });
    const result = await getUnreadCount();
    expect(result).toBe(0);
  });

  it('wrapper.data undefined ise (alan yok) 0’a düşer', async () => {
    client.get.mockResolvedValue({ data: {} });
    const result = await getUnreadCount();
    expect(result).toBe(0);
  });

  it('hata durumunda reddi yukarı taşır', async () => {
    client.get.mockRejectedValue(new Error('500'));
    await expect(getUnreadCount()).rejects.toThrow('500');
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// markNotificationRead — POST /api/notifications/{id}/read
// ─────────────────────────────────────────────────────────────────────────────
describe('markNotificationRead', () => {
  it('id’yi URL’e gömerek POST çağırır ve wrapper.data döndürür', async () => {
    client.post.mockResolvedValue({ data: { data: { id: 42, read: true } } });

    const result = await markNotificationRead(42);

    expect(client.post).toHaveBeenCalledTimes(1);
    expect(client.post).toHaveBeenCalledWith('/api/notifications/42/read');
    expect(result).toEqual({ id: 42, read: true });
  });

  it('wrapper.data null ise null döndürür (?? YOK, ham döner)', async () => {
    client.post.mockResolvedValue({ data: { data: null } });
    const result = await markNotificationRead(5);
    expect(result).toBeNull();
  });

  it('hata durumunda reddi yukarı taşır', async () => {
    client.post.mockRejectedValue(new Error('404'));
    await expect(markNotificationRead(99)).rejects.toThrow('404');
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// markAllNotificationsRead — POST /api/notifications/read-all
// ─────────────────────────────────────────────────────────────────────────────
describe('markAllNotificationsRead', () => {
  it('sabit URL ile POST çağırır ve wrapper.data döndürür', async () => {
    client.post.mockResolvedValue({ data: { data: { updated: 3 } } });

    const result = await markAllNotificationsRead();

    expect(client.post).toHaveBeenCalledTimes(1);
    expect(client.post).toHaveBeenCalledWith('/api/notifications/read-all');
    expect(result).toEqual({ updated: 3 });
  });

  it('hata durumunda reddi yukarı taşır', async () => {
    client.post.mockRejectedValue(new Error('boom'));
    await expect(markAllNotificationsRead()).rejects.toThrow('boom');
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// deleteNotification — DELETE /api/notifications/{id}
// ─────────────────────────────────────────────────────────────────────────────
describe('deleteNotification', () => {
  it('id’yi URL’e gömerek DELETE çağırır ve wrapper.data döndürür', async () => {
    client.delete.mockResolvedValue({ data: { data: { id: 13, deleted: true } } });

    const result = await deleteNotification(13);

    expect(client.delete).toHaveBeenCalledTimes(1);
    expect(client.delete).toHaveBeenCalledWith('/api/notifications/13');
    expect(result).toEqual({ id: 13, deleted: true });
  });

  it('wrapper.data null ise null döndürür', async () => {
    client.delete.mockResolvedValue({ data: { data: null } });
    const result = await deleteNotification(1);
    expect(result).toBeNull();
  });

  it('hata durumunda reddi yukarı taşır', async () => {
    client.delete.mockRejectedValue(new Error('forbidden'));
    await expect(deleteNotification(7)).rejects.toThrow('forbidden');
  });
});
