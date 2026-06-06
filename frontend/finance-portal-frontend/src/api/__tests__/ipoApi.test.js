import { describe, it, expect, beforeEach, vi } from 'vitest';

// HTTP istemcisini MOCK'la — gerçek ağ YOK.
// Kaynak paylaşılan `client` (lib/http) kullanır → relative path (hardcoded localhost YOK).
vi.mock('../../lib/http', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn(), patch: vi.fn() },
}));

import client from '../../lib/http';
import { getIpos } from '../ipoApi';

beforeEach(() => {
  vi.clearAllMocks();
});

describe('getIpos', () => {
  it('doğru IPO uç noktasını argümansız (params YOK) GET ile çağırır', async () => {
    client.get.mockResolvedValue({ data: { data: [] } });

    await getIpos();

    expect(client.get).toHaveBeenCalledTimes(1);
    expect(client.get).toHaveBeenCalledWith(`/api/v1/market/ipo`);
    // Bu fonksiyon ikinci bir options/params argümanı GEÇİRMEZ.
    expect(client.get.mock.calls[0]).toHaveLength(1);
  });

  it('wrapper.data dizisini olduğu gibi döndürür (happy-path)', async () => {
    const payload = [
      { symbol: 'ABCD', company: 'ABC A.Ş.', date: '2026-06-10' },
      { symbol: 'EFGH', company: 'EFG A.Ş.', date: '2026-07-01' },
    ];
    client.get.mockResolvedValue({ data: { data: payload } });

    const res = await getIpos();

    expect(res).toEqual(payload);
    expect(res).toBe(payload); // dönüşüm yok, aynı referans
  });

  it('wrapper.data undefined ise boş dizi döndürür (?? [] fallback)', async () => {
    client.get.mockResolvedValue({ data: {} }); // wrapper.data === undefined

    const res = await getIpos();

    expect(res).toEqual([]);
  });

  it('wrapper.data null ise boş dizi döndürür (?? [] fallback)', async () => {
    client.get.mockResolvedValue({ data: { data: null } });

    const res = await getIpos();

    expect(res).toEqual([]);
  });

  it('wrapper.data boş dizi ise boş diziyi korur (falsy DEĞİL, ?? tetiklenmez)', async () => {
    const empty = [];
    client.get.mockResolvedValue({ data: { data: empty } });

    const res = await getIpos();

    expect(res).toBe(empty); // [] null/undefined olmadığından ?? devreye girmez
  });

  it('axios reddederse hatayı yukarı fırlatır', async () => {
    client.get.mockRejectedValue(new Error('network down'));

    await expect(getIpos()).rejects.toThrow('network down');
  });
});
