import { describe, it, expect, beforeEach, vi } from 'vitest';

// '../../lib/http' default export'u (axios benzeri client) MOCK'lanır — gerçek ağ isteği atılmasın.
// meApi.js TEK bağımlılığı bu client'tır; her fonksiyon client.get/patch ile sunucuya gider.
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
  getMe,
  updateMeProfile,
  updateMeEmail,
  changeMePassword,
} from '../meApi';

beforeEach(() => {
  vi.clearAllMocks();
});

describe('getMe', () => {
  it('GET /api/v1/me çağırır ve gövdedeki data.data alanını döndürür (sarmalayıcıyı soyar)', async () => {
    const profile = { id: 7, username: 'berkan', email: 'b@example.com' };
    client.get.mockResolvedValue({ data: { data: profile } });

    const result = await getMe();

    expect(client.get).toHaveBeenCalledTimes(1);
    expect(client.get).toHaveBeenCalledWith('/api/v1/me');
    expect(result).toBe(profile);
    expect(result).toEqual({ id: 7, username: 'berkan', email: 'b@example.com' });
  });

  it('iç data alanı null ise null döndürür (edge: boş profil)', async () => {
    client.get.mockResolvedValue({ data: { data: null } });

    const result = await getMe();

    expect(client.get).toHaveBeenCalledWith('/api/v1/me');
    expect(result).toBeNull();
  });

  it('client.get reddedilirse hata yukarı fırlatılır (yutulmaz)', async () => {
    const err = new Error('ağ hatası');
    client.get.mockRejectedValue(err);

    await expect(getMe()).rejects.toThrow('ağ hatası');
    expect(client.get).toHaveBeenCalledWith('/api/v1/me');
  });
});

describe('updateMeProfile', () => {
  it('PATCH /api/v1/me/profile çağırır, payload gövdeyi geçirir ve tüm data nesnesini döndürür', async () => {
    const payload = { firstName: 'Berkan', lastName: 'Gulyagci' };
    const body = { ok: true, message: 'güncellendi' };
    client.patch.mockResolvedValue({ data: body });

    const result = await updateMeProfile(payload);

    expect(client.patch).toHaveBeenCalledTimes(1);
    expect(client.patch).toHaveBeenCalledWith('/api/v1/me/profile', payload);
    expect(result).toBe(body);
  });

  it('payload undefined olsa bile aynen iletir (edge: boş gövde)', async () => {
    client.patch.mockResolvedValue({ data: { ok: true } });

    const result = await updateMeProfile(undefined);

    expect(client.patch).toHaveBeenCalledWith('/api/v1/me/profile', undefined);
    expect(result).toEqual({ ok: true });
  });

  it('client.patch reddedilirse hata fırlatılır', async () => {
    client.patch.mockRejectedValue(new Error('400 geçersiz'));

    await expect(updateMeProfile({ firstName: '' })).rejects.toThrow('400 geçersiz');
    expect(client.patch).toHaveBeenCalledWith('/api/v1/me/profile', { firstName: '' });
  });
});

describe('updateMeEmail', () => {
  it('PATCH /api/v1/me/email çağırır, payload geçirir ve data nesnesini döndürür', async () => {
    const payload = { email: 'yeni@example.com' };
    const body = { ok: true, verificationSent: true };
    client.patch.mockResolvedValue({ data: body });

    const result = await updateMeEmail(payload);

    expect(client.patch).toHaveBeenCalledTimes(1);
    expect(client.patch).toHaveBeenCalledWith('/api/v1/me/email', payload);
    expect(result).toBe(body);
  });

  it('client.patch reddedilirse hata fırlatılır (edge: zaten kullanımda)', async () => {
    client.patch.mockRejectedValue(new Error('email zaten kullanımda'));

    await expect(updateMeEmail({ email: 'dolu@example.com' })).rejects.toThrow(
      'email zaten kullanımda',
    );
    expect(client.patch).toHaveBeenCalledWith('/api/v1/me/email', { email: 'dolu@example.com' });
  });
});

describe('changeMePassword', () => {
  it('PATCH /api/v1/me/password çağırır, payload geçirir ve data nesnesini döndürür', async () => {
    const payload = { currentPassword: 'eski', newPassword: 'yeni12345' };
    const body = { ok: true };
    client.patch.mockResolvedValue({ data: body });

    const result = await changeMePassword(payload);

    expect(client.patch).toHaveBeenCalledTimes(1);
    expect(client.patch).toHaveBeenCalledWith('/api/v1/me/password', payload);
    expect(result).toBe(body);
  });

  it('client.patch reddedilirse hata fırlatılır (edge: yanlış mevcut şifre)', async () => {
    client.patch.mockRejectedValue(new Error('mevcut şifre hatalı'));

    await expect(
      changeMePassword({ currentPassword: 'x', newPassword: 'y' }),
    ).rejects.toThrow('mevcut şifre hatalı');
    expect(client.patch).toHaveBeenCalledWith('/api/v1/me/password', {
      currentPassword: 'x',
      newPassword: 'y',
    });
  });
});
