import { describe, it, expect, beforeEach, vi } from 'vitest';

// '../../lib/http' default export'u (axios benzeri client) MOCK'lanır — gerçek ağ
// isteği atılmasın. alarmApi.js her çağrıda { data: wrapper } destructure edip
// wrapper.data döndürdüğü için mock'lar { data: { data: ... } } şekline resolve eder.
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
  getAlarms,
  getAlarm,
  createAlarm,
  updateAlarm,
  deleteAlarm,
} from '../alarmApi';

beforeEach(() => {
  vi.clearAllMocks();
});

// ── getAlarms ───────────────────────────────────────────────────────────────

describe('getAlarms', () => {
  it("DOĞRU URL ile client.get'i çağırır ve wrapper.data'yı döndürür", async () => {
    const alarms = [{ id: 1, symbol: 'AKBNK' }, { id: 2, symbol: 'THYAO' }];
    client.get.mockResolvedValue({ data: { data: alarms } });

    const result = await getAlarms();

    expect(client.get).toHaveBeenCalledTimes(1);
    expect(client.get).toHaveBeenCalledWith('/api/v1/alarms');
    expect(result).toEqual(alarms);
  });

  it("wrapper.data null ise boş diziye (?? []) düşer", async () => {
    client.get.mockResolvedValue({ data: { data: null } });

    const result = await getAlarms();

    expect(result).toEqual([]);
  });

  it("wrapper.data undefined ise (alan yoksa) boş diziye düşer", async () => {
    client.get.mockResolvedValue({ data: {} });

    const result = await getAlarms();

    expect(result).toEqual([]);
  });

  it('istek reddedilirse hata yukarı fırlatılır', async () => {
    client.get.mockRejectedValue(new Error('ağ hatası'));

    await expect(getAlarms()).rejects.toThrow('ağ hatası');
  });
});

// ── getAlarm ────────────────────────────────────────────────────────────────

describe('getAlarm', () => {
  it("alarmId'yi URL'e gömerek client.get'i çağırır ve wrapper.data'yı döndürür", async () => {
    const alarm = { id: 42, symbol: 'GARAN', metric: 'PRICE' };
    client.get.mockResolvedValue({ data: { data: alarm } });

    const result = await getAlarm(42);

    expect(client.get).toHaveBeenCalledTimes(1);
    expect(client.get).toHaveBeenCalledWith('/api/v1/alarms/42');
    expect(result).toEqual(alarm);
  });

  it("wrapper.data ne ise (null dahil) aynen döndürür", async () => {
    client.get.mockResolvedValue({ data: { data: null } });

    const result = await getAlarm('abc');

    expect(client.get).toHaveBeenCalledWith('/api/v1/alarms/abc');
    expect(result).toBeNull();
  });

  it('istek reddedilirse hata yukarı fırlatılır', async () => {
    client.get.mockRejectedValue(new Error('404'));

    await expect(getAlarm(7)).rejects.toThrow('404');
  });
});

// ── createAlarm ───────────────────────────────────────────────────────────────

describe('createAlarm', () => {
  it("payload'ı body olarak client.post'a geçirir ve wrapper.data'yı döndürür", async () => {
    const payload = {
      assetType: 'STOCK',
      symbol: 'AKBNK',
      instrumentName: 'Akbank',
      metric: 'PRICE',
      direction: 'ABOVE',
      threshold: 50,
      frequency: 'ONCE',
      note: 'hedef',
    };
    const created = { id: 99, ...payload, status: 'ACTIVE' };
    client.post.mockResolvedValue({ data: { data: created } });

    const result = await createAlarm(payload);

    expect(client.post).toHaveBeenCalledTimes(1);
    expect(client.post).toHaveBeenCalledWith('/api/v1/alarms', payload);
    expect(result).toEqual(created);
  });

  it('istek reddedilirse hata yukarı fırlatılır', async () => {
    client.post.mockRejectedValue(new Error('400 doğrulama'));

    await expect(createAlarm({ symbol: 'X' })).rejects.toThrow('400 doğrulama');
  });
});

// ── updateAlarm ───────────────────────────────────────────────────────────────

describe('updateAlarm', () => {
  it("alarmId'yi URL'e gömer, payload'ı body olarak client.patch'e geçirir ve wrapper.data'yı döndürür", async () => {
    const payload = { threshold: 75, status: 'PAUSED' };
    const updated = { id: 5, threshold: 75, status: 'PAUSED' };
    client.patch.mockResolvedValue({ data: { data: updated } });

    const result = await updateAlarm(5, payload);

    expect(client.patch).toHaveBeenCalledTimes(1);
    expect(client.patch).toHaveBeenCalledWith('/api/v1/alarms/5', payload);
    expect(result).toEqual(updated);
  });

  it('istek reddedilirse hata yukarı fırlatılır', async () => {
    client.patch.mockRejectedValue(new Error('409'));

    await expect(updateAlarm(1, {})).rejects.toThrow('409');
  });
});

// ── deleteAlarm ───────────────────────────────────────────────────────────────

describe('deleteAlarm', () => {
  it("alarmId'yi URL'e gömerek client.delete'i çağırır ve wrapper.data'yı döndürür", async () => {
    client.delete.mockResolvedValue({ data: { data: { deleted: true } } });

    const result = await deleteAlarm(13);

    expect(client.delete).toHaveBeenCalledTimes(1);
    expect(client.delete).toHaveBeenCalledWith('/api/v1/alarms/13');
    expect(result).toEqual({ deleted: true });
  });

  it("wrapper.data null ise null döndürür", async () => {
    client.delete.mockResolvedValue({ data: { data: null } });

    const result = await deleteAlarm(2);

    expect(client.delete).toHaveBeenCalledWith('/api/v1/alarms/2');
    expect(result).toBeNull();
  });

  it('istek reddedilirse hata yukarı fırlatılır', async () => {
    client.delete.mockRejectedValue(new Error('403'));

    await expect(deleteAlarm(9)).rejects.toThrow('403');
  });
});
