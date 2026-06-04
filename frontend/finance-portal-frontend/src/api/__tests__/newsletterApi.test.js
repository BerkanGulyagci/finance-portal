import { describe, it, expect, beforeEach, vi } from 'vitest';

// '../../lib/http' default export'u (axios benzeri client) MOCK'lanır — gerçek ağ isteği atılmasın.
// newsletterApi.js TEK bağımlılığı bu client'tır; her fonksiyon client.get/put ile sunucuya gider
// ve dönen gövdeden { data } destructure edip data.data alanını döndürür.
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
import { getNewsletter, updateNewsletter } from '../newsletterApi';

beforeEach(() => {
  vi.clearAllMocks();
});

// ─────────────────────────────────────────────────────────────────────────────
// getNewsletter — GET /api/newsletter/me
// ─────────────────────────────────────────────────────────────────────────────
describe('getNewsletter', () => {
  it('GET /api/newsletter/me çağırır ve gövdedeki data.data alanını döndürür (sarmalayıcıyı soyar)', async () => {
    const subscription = { subscribed: true, frequency: 'WEEKLY', email: 'b@example.com' };
    client.get.mockResolvedValue({ data: { data: subscription } });

    const result = await getNewsletter();

    expect(client.get).toHaveBeenCalledTimes(1);
    expect(client.get).toHaveBeenCalledWith('/api/newsletter/me');
    expect(result).toBe(subscription);
    expect(result).toEqual({ subscribed: true, frequency: 'WEEKLY', email: 'b@example.com' });
  });

  it('iç data alanı null ise null döndürür (edge: abonelik yok — fallback yok)', async () => {
    client.get.mockResolvedValue({ data: { data: null } });

    const result = await getNewsletter();

    expect(client.get).toHaveBeenCalledWith('/api/newsletter/me');
    expect(result).toBeNull();
  });

  it('iç data alanı undefined ise undefined döndürür (edge: alan hiç gelmedi)', async () => {
    client.get.mockResolvedValue({ data: {} });

    const result = await getNewsletter();

    expect(client.get).toHaveBeenCalledWith('/api/newsletter/me');
    expect(result).toBeUndefined();
  });

  it('client.get reddedilirse hata yukarı fırlatılır (yutulmaz)', async () => {
    client.get.mockRejectedValue(new Error('ağ hatası'));

    await expect(getNewsletter()).rejects.toThrow('ağ hatası');
    expect(client.get).toHaveBeenCalledWith('/api/newsletter/me');
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// updateNewsletter — PUT /api/newsletter/me   body: { frequency, enabled }
// ─────────────────────────────────────────────────────────────────────────────
describe('updateNewsletter', () => {
  it('PUT /api/newsletter/me çağırır, frequency+enabled gövdesini geçirir ve data.data döndürür', async () => {
    const updated = { subscribed: true, frequency: 'DAILY', email: 'b@example.com' };
    client.put.mockResolvedValue({ data: { data: updated } });

    const result = await updateNewsletter('DAILY', true);

    expect(client.put).toHaveBeenCalledTimes(1);
    expect(client.put).toHaveBeenCalledWith('/api/newsletter/me', {
      frequency: 'DAILY',
      enabled: true,
    });
    expect(result).toBe(updated);
    expect(result).toEqual({ subscribed: true, frequency: 'DAILY', email: 'b@example.com' });
  });

  it('enabled=false geçirildiğinde gövdede aynen iletilir (edge: aboneliği kapatma)', async () => {
    const updated = { subscribed: false, frequency: 'MONTHLY', email: 'b@example.com' };
    client.put.mockResolvedValue({ data: { data: updated } });

    const result = await updateNewsletter('MONTHLY', false);

    expect(client.put).toHaveBeenCalledWith('/api/newsletter/me', {
      frequency: 'MONTHLY',
      enabled: false,
    });
    expect(result).toEqual(updated);
  });

  it('argümanlar undefined olsa bile gövdeye aynen yansır (edge: boş çağrı)', async () => {
    client.put.mockResolvedValue({ data: { data: null } });

    const result = await updateNewsletter(undefined, undefined);

    expect(client.put).toHaveBeenCalledWith('/api/newsletter/me', {
      frequency: undefined,
      enabled: undefined,
    });
    expect(result).toBeNull();
  });

  it('iç data alanı undefined ise undefined döndürür (fallback yok)', async () => {
    client.put.mockResolvedValue({ data: {} });

    expect(await updateNewsletter('WEEKLY', true)).toBeUndefined();
    expect(client.put).toHaveBeenCalledWith('/api/newsletter/me', {
      frequency: 'WEEKLY',
      enabled: true,
    });
  });

  it('client.put reddedilirse hata yukarı fırlatılır (edge: 400 geçersiz frekans)', async () => {
    client.put.mockRejectedValue(new Error('400 geçersiz'));

    await expect(updateNewsletter('HOURLY', true)).rejects.toThrow('400 geçersiz');
    expect(client.put).toHaveBeenCalledWith('/api/newsletter/me', {
      frequency: 'HOURLY',
      enabled: true,
    });
  });
});
