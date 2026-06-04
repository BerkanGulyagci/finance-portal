import { describe, it, expect, beforeEach, vi } from 'vitest';

// HTTP istemcisini MOCK'la — gerçek ağ YOK.
// Kaynak `import axios from 'axios'` kullanır → axios.get çağrılır.
// axios bare-modül adıyla import edildiği için mock yolu testin konumundan BAĞIMSIZ ('axios').
vi.mock('axios', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn(), patch: vi.fn() },
}));

import axios from 'axios';
import { getIpos } from '../ipoApi';

const BASE_URL = 'http://localhost:8080';

beforeEach(() => {
  vi.clearAllMocks();
});

describe('getIpos', () => {
  it('doğru IPO uç noktasını argümansız (params YOK) GET ile çağırır', async () => {
    axios.get.mockResolvedValue({ data: { data: [] } });

    await getIpos();

    expect(axios.get).toHaveBeenCalledTimes(1);
    expect(axios.get).toHaveBeenCalledWith(`${BASE_URL}/api/market/ipo`);
    // Bu fonksiyon ikinci bir options/params argümanı GEÇİRMEZ.
    expect(axios.get.mock.calls[0]).toHaveLength(1);
  });

  it('wrapper.data dizisini olduğu gibi döndürür (happy-path)', async () => {
    const payload = [
      { symbol: 'ABCD', company: 'ABC A.Ş.', date: '2026-06-10' },
      { symbol: 'EFGH', company: 'EFG A.Ş.', date: '2026-07-01' },
    ];
    axios.get.mockResolvedValue({ data: { data: payload } });

    const res = await getIpos();

    expect(res).toEqual(payload);
    expect(res).toBe(payload); // dönüşüm yok, aynı referans
  });

  it('wrapper.data undefined ise boş dizi döndürür (?? [] fallback)', async () => {
    axios.get.mockResolvedValue({ data: {} }); // wrapper.data === undefined

    const res = await getIpos();

    expect(res).toEqual([]);
  });

  it('wrapper.data null ise boş dizi döndürür (?? [] fallback)', async () => {
    axios.get.mockResolvedValue({ data: { data: null } });

    const res = await getIpos();

    expect(res).toEqual([]);
  });

  it('wrapper.data boş dizi ise boş diziyi korur (falsy DEĞİL, ?? tetiklenmez)', async () => {
    const empty = [];
    axios.get.mockResolvedValue({ data: { data: empty } });

    const res = await getIpos();

    expect(res).toBe(empty); // [] null/undefined olmadığından ?? devreye girmez
  });

  it('axios reddederse hatayı yukarı fırlatır', async () => {
    axios.get.mockRejectedValue(new Error('network down'));

    await expect(getIpos()).rejects.toThrow('network down');
  });
});
