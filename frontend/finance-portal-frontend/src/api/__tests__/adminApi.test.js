import { describe, it, expect, beforeEach, vi } from 'vitest';

// '../../lib/http' default export'u (axios client) MOCK'lanır — gerçek ağ isteği atılmasın.
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
  getUsers,
  getUser,
  banUser,
  unbanUser,
  getEurobondIsins,
  refreshEurobondIsins,
} from '../adminApi';

beforeEach(() => {
  vi.clearAllMocks();
});

// ── getUsers ────────────────────────────────────────────────────────────────
describe('getUsers', () => {
  it('argümansız çağrıda varsayılan parametrelerle GET atar ve wrapper.data döner', async () => {
    const payload = [{ id: 1 }, { id: 2 }];
    client.get.mockResolvedValue({ data: { data: payload } });

    const result = await getUsers();

    expect(client.get).toHaveBeenCalledTimes(1);
    expect(client.get).toHaveBeenCalledWith('/api/admin/users', {
      params: { first: 0, max: 20, status: 'ALL' },
    });
    // search/withTickets eklenmemeli
    expect(client.get.mock.calls[0][1].params).not.toHaveProperty('search');
    expect(client.get.mock.calls[0][1].params).not.toHaveProperty('withTickets');
    expect(result).toBe(payload);
  });

  it('verilen first/max/status değerlerini params olarak iletir', async () => {
    client.get.mockResolvedValue({ data: { data: [] } });

    await getUsers({ first: 40, max: 10, status: 'BANNED' });

    expect(client.get).toHaveBeenCalledWith('/api/admin/users', {
      params: { first: 40, max: 10, status: 'BANNED' },
    });
  });

  it('dolu search trim edilerek params.search olarak eklenir', async () => {
    client.get.mockResolvedValue({ data: { data: [] } });

    await getUsers({ search: '  ahmet  ' });

    expect(client.get).toHaveBeenCalledWith('/api/admin/users', {
      params: { first: 0, max: 20, status: 'ALL', search: 'ahmet' },
    });
  });

  it('sadece boşluktan oluşan search → eklenmez (trim sonrası boş)', async () => {
    client.get.mockResolvedValue({ data: { data: [] } });

    await getUsers({ search: '   ' });

    expect(client.get.mock.calls[0][1].params).not.toHaveProperty('search');
  });

  it('boş string search → eklenmez (optional chaining/trim falsy dalı)', async () => {
    client.get.mockResolvedValue({ data: { data: [] } });

    await getUsers({ search: '' });

    expect(client.get.mock.calls[0][1].params).not.toHaveProperty('search');
  });

  it('withTickets=true → params.withTickets true olarak eklenir', async () => {
    client.get.mockResolvedValue({ data: { data: [] } });

    await getUsers({ withTickets: true });

    expect(client.get.mock.calls[0][1].params).toMatchObject({ withTickets: true });
  });

  it('withTickets=false → eklenmez', async () => {
    client.get.mockResolvedValue({ data: { data: [] } });

    await getUsers({ withTickets: false });

    expect(client.get.mock.calls[0][1].params).not.toHaveProperty('withTickets');
  });

  it('search + withTickets birlikte verilince ikisi de iletilir', async () => {
    client.get.mockResolvedValue({ data: { data: [] } });

    await getUsers({ search: 'x', withTickets: true, first: 5, max: 3, status: 'ACTIVE' });

    expect(client.get).toHaveBeenCalledWith('/api/admin/users', {
      params: { first: 5, max: 3, status: 'ACTIVE', search: 'x', withTickets: true },
    });
  });

  it('hata yolunda (reject) promise reddedilir', async () => {
    const err = new Error('ağ hatası');
    client.get.mockRejectedValue(err);

    await expect(getUsers()).rejects.toThrow('ağ hatası');
  });
});

// ── getUser ─────────────────────────────────────────────────────────────────
describe('getUser', () => {
  it('userId ile doğru URL\'e GET atar ve wrapper.data döner', async () => {
    const user = { id: 7, username: 'foo' };
    client.get.mockResolvedValue({ data: { data: user } });

    const result = await getUser(7);

    expect(client.get).toHaveBeenCalledWith('/api/admin/users/7');
    expect(result).toBe(user);
  });

  it('string userId de URL\'e gömülür', async () => {
    client.get.mockResolvedValue({ data: { data: null } });

    await getUser('abc-123');

    expect(client.get).toHaveBeenCalledWith('/api/admin/users/abc-123');
  });

  it('hata yolunda promise reddedilir', async () => {
    client.get.mockRejectedValue(new Error('404'));

    await expect(getUser(1)).rejects.toThrow('404');
  });
});

// ── banUser ─────────────────────────────────────────────────────────────────
describe('banUser', () => {
  it('body ile ban URL\'ine POST atar ve TÜM wrapper\'ı döner', async () => {
    const wrapper = { success: true, message: 'banned', data: { id: 3 } };
    client.post.mockResolvedValue({ data: wrapper });
    const body = { reason: 'spam', durationDays: 7 };

    const result = await banUser(3, body);

    expect(client.post).toHaveBeenCalledWith('/api/admin/users/3/ban', body);
    expect(result).toBe(wrapper);
  });

  it('hata yolunda promise reddedilir', async () => {
    client.post.mockRejectedValue(new Error('yetkisiz'));

    await expect(banUser(3, {})).rejects.toThrow('yetkisiz');
  });
});

// ── unbanUser ───────────────────────────────────────────────────────────────
describe('unbanUser', () => {
  it('unban URL\'ine (body\'siz) POST atar ve TÜM wrapper\'ı döner', async () => {
    const wrapper = { success: true, message: 'unbanned' };
    client.post.mockResolvedValue({ data: wrapper });

    const result = await unbanUser(9);

    expect(client.post).toHaveBeenCalledWith('/api/admin/users/9/unban');
    // ikinci argüman (body) verilmemeli
    expect(client.post.mock.calls[0]).toHaveLength(1);
    expect(result).toBe(wrapper);
  });

  it('hata yolunda promise reddedilir', async () => {
    client.post.mockRejectedValue(new Error('hata'));

    await expect(unbanUser(9)).rejects.toThrow('hata');
  });
});

// ── getEurobondIsins ─────────────────────────────────────────────────────────
describe('getEurobondIsins', () => {
  it('eurobond ISIN listesine GET atar ve wrapper.data döner', async () => {
    const isins = ['XS123', 'US456'];
    client.get.mockResolvedValue({ data: { data: isins } });

    const result = await getEurobondIsins();

    expect(client.get).toHaveBeenCalledWith('/api/admin/eurobonds/isins');
    expect(result).toBe(isins);
  });

  it('hata yolunda promise reddedilir', async () => {
    client.get.mockRejectedValue(new Error('hata'));

    await expect(getEurobondIsins()).rejects.toThrow('hata');
  });
});

// ── refreshEurobondIsins ─────────────────────────────────────────────────────
describe('refreshEurobondIsins', () => {
  it('xlsxUrl\'i params olarak, body null ile refresh URL\'ine POST atar ve wrapper.data döner', async () => {
    const summary = { added: 5, total: 100 };
    client.post.mockResolvedValue({ data: { data: summary } });
    const url = 'https://example.com/list.xlsx';

    const result = await refreshEurobondIsins(url);

    expect(client.post).toHaveBeenCalledWith(
      '/api/admin/eurobonds/refresh',
      null,
      { params: { xlsxUrl: url } },
    );
    expect(result).toBe(summary);
  });

  it('undefined xlsxUrl ile çağrılınca params.xlsxUrl undefined olur', async () => {
    client.post.mockResolvedValue({ data: { data: null } });

    await refreshEurobondIsins(undefined);

    expect(client.post).toHaveBeenCalledWith(
      '/api/admin/eurobonds/refresh',
      null,
      { params: { xlsxUrl: undefined } },
    );
  });

  it('hata yolunda promise reddedilir', async () => {
    client.post.mockRejectedValue(new Error('refresh patladı'));

    await expect(refreshEurobondIsins('x')).rejects.toThrow('refresh patladı');
  });
});
