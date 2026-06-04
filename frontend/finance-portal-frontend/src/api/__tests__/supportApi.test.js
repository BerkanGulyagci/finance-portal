import { describe, it, expect, beforeEach, vi } from 'vitest';

// '../../lib/http' default export'u (axios client) MOCK'lanır — gerçek ağ isteği atılmasın.
// supportApi sadece client.get/post/put/delete/patch çağırır ve { data } destructure eder.
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
  getMyTickets,
  createTicket,
  updateTicket,
  deleteTicket,
  getUserTickets,
  updateTicketStatus,
} from '../supportApi';

beforeEach(() => {
  vi.clearAllMocks();
});

// ─────────────────────────────────────────────────────────────────────────────
// getMyTickets — GET /api/support/tickets
// ─────────────────────────────────────────────────────────────────────────────
describe('getMyTickets', () => {
  it('doğru URL ile GET çağırır ve data.data dizisini döner', async () => {
    const tickets = [{ id: 1, subject: 'Sorun' }];
    client.get.mockResolvedValue({ data: { data: tickets } });

    const result = await getMyTickets();

    expect(client.get).toHaveBeenCalledTimes(1);
    expect(client.get).toHaveBeenCalledWith('/api/support/tickets');
    expect(result).toEqual(tickets);
  });

  it('data.data null/undefined ise boş diziye düşer (?? [])', async () => {
    client.get.mockResolvedValue({ data: { data: null } });
    expect(await getMyTickets()).toEqual([]);

    client.get.mockResolvedValue({ data: {} }); // data.data undefined
    expect(await getMyTickets()).toEqual([]);
  });

  it('hata fırlatırsa (reject) çağırana yansıtır', async () => {
    client.get.mockRejectedValue(new Error('ağ hatası'));
    await expect(getMyTickets()).rejects.toThrow('ağ hatası');
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// createTicket — POST /api/support/tickets
// ─────────────────────────────────────────────────────────────────────────────
describe('createTicket', () => {
  it('doğru URL ve body ile POST çağırır ve data.data döner', async () => {
    const created = { id: 7, subject: 'Konu', message: 'Mesaj' };
    client.post.mockResolvedValue({ data: { data: created } });

    const result = await createTicket({ subject: 'Konu', message: 'Mesaj' });

    expect(client.post).toHaveBeenCalledTimes(1);
    expect(client.post).toHaveBeenCalledWith('/api/support/tickets', {
      subject: 'Konu',
      message: 'Mesaj',
    });
    expect(result).toEqual(created);
  });

  it('data.data undefined ise undefined döner (fallback yok)', async () => {
    client.post.mockResolvedValue({ data: {} });
    expect(await createTicket({ subject: 'a', message: 'b' })).toBeUndefined();
  });

  it('hata fırlatırsa (reject) çağırana yansıtır', async () => {
    client.post.mockRejectedValue(new Error('400'));
    await expect(createTicket({ subject: 'a', message: 'b' })).rejects.toThrow('400');
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// updateTicket — PUT /api/support/tickets/:id
// ─────────────────────────────────────────────────────────────────────────────
describe('updateTicket', () => {
  it('id’yi URL’ye gömer, body ile PUT çağırır ve data.data döner', async () => {
    const updated = { id: 42, subject: 'Yeni', message: 'Güncel' };
    client.put.mockResolvedValue({ data: { data: updated } });

    const result = await updateTicket(42, { subject: 'Yeni', message: 'Güncel' });

    expect(client.put).toHaveBeenCalledTimes(1);
    expect(client.put).toHaveBeenCalledWith('/api/support/tickets/42', {
      subject: 'Yeni',
      message: 'Güncel',
    });
    expect(result).toEqual(updated);
  });

  it('data.data undefined ise undefined döner', async () => {
    client.put.mockResolvedValue({ data: {} });
    expect(await updateTicket(1, { subject: 'x', message: 'y' })).toBeUndefined();
  });

  it('hata fırlatırsa (reject) çağırana yansıtır', async () => {
    client.put.mockRejectedValue(new Error('403'));
    await expect(updateTicket(1, { subject: 'x', message: 'y' })).rejects.toThrow('403');
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// deleteTicket — DELETE /api/support/tickets/:id
// ─────────────────────────────────────────────────────────────────────────────
describe('deleteTicket', () => {
  it('id’yi URL’ye gömerek DELETE çağırır ve undefined döner (değer döndürmez)', async () => {
    client.delete.mockResolvedValue({ data: { data: { ignored: true } } });

    const result = await deleteTicket(9);

    expect(client.delete).toHaveBeenCalledTimes(1);
    expect(client.delete).toHaveBeenCalledWith('/api/support/tickets/9');
    expect(result).toBeUndefined();
  });

  it('hata fırlatırsa (reject) çağırana yansıtır', async () => {
    client.delete.mockRejectedValue(new Error('404'));
    await expect(deleteTicket(9)).rejects.toThrow('404');
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// getUserTickets (admin) — GET /api/admin/users/:userId/tickets
// ─────────────────────────────────────────────────────────────────────────────
describe('getUserTickets', () => {
  it('userId’yi URL’ye gömerek GET çağırır ve data.data dizisini döner', async () => {
    const tickets = [{ id: 1 }, { id: 2 }];
    client.get.mockResolvedValue({ data: { data: tickets } });

    const result = await getUserTickets(123);

    expect(client.get).toHaveBeenCalledTimes(1);
    expect(client.get).toHaveBeenCalledWith('/api/admin/users/123/tickets');
    expect(result).toEqual(tickets);
  });

  it('data.data null/undefined ise boş diziye düşer (?? [])', async () => {
    client.get.mockResolvedValue({ data: { data: null } });
    expect(await getUserTickets(5)).toEqual([]);

    client.get.mockResolvedValue({ data: {} });
    expect(await getUserTickets(5)).toEqual([]);
  });

  it('hata fırlatırsa (reject) çağırana yansıtır', async () => {
    client.get.mockRejectedValue(new Error('500'));
    await expect(getUserTickets(5)).rejects.toThrow('500');
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// updateTicketStatus (admin) — PATCH /api/admin/support/tickets/:id/status
// ─────────────────────────────────────────────────────────────────────────────
describe('updateTicketStatus', () => {
  it('id’yi URL’ye gömer, status+adminNote body ile PATCH çağırır ve data.data döner', async () => {
    const updated = { id: 3, status: 'RESOLVED' };
    client.patch.mockResolvedValue({ data: { data: updated } });

    const result = await updateTicketStatus(3, 'RESOLVED', 'çözüldü');

    expect(client.patch).toHaveBeenCalledTimes(1);
    expect(client.patch).toHaveBeenCalledWith('/api/admin/support/tickets/3/status', {
      status: 'RESOLVED',
      adminNote: 'çözüldü',
    });
    expect(result).toEqual(updated);
  });

  it('adminNote verilmezse body’de undefined geçer', async () => {
    client.patch.mockResolvedValue({ data: { data: { id: 4 } } });

    await updateTicketStatus(4, 'IN_PROGRESS');

    expect(client.patch).toHaveBeenCalledWith('/api/admin/support/tickets/4/status', {
      status: 'IN_PROGRESS',
      adminNote: undefined,
    });
  });

  it('data.data undefined ise undefined döner', async () => {
    client.patch.mockResolvedValue({ data: {} });
    expect(await updateTicketStatus(1, 'OPEN', 'n')).toBeUndefined();
  });

  it('hata fırlatırsa (reject) çağırana yansıtır', async () => {
    client.patch.mockRejectedValue(new Error('409'));
    await expect(updateTicketStatus(1, 'OPEN', 'n')).rejects.toThrow('409');
  });
});
