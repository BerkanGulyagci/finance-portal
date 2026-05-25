import client from './client';

// ── Kullanıcı (kendi destek talepleri) ────────────────────────────────────────
export async function getMyTickets() {
  const { data } = await client.get('/api/support/tickets');
  return data.data ?? [];
}

export async function createTicket({ subject, message }) {
  const { data } = await client.post('/api/support/tickets', { subject, message });
  return data.data;
}

export async function updateTicket(id, { subject, message }) {
  const { data } = await client.put(`/api/support/tickets/${id}`, { subject, message });
  return data.data;
}

export async function deleteTicket(id) {
  await client.delete(`/api/support/tickets/${id}`);
}

// ── Admin ─────────────────────────────────────────────────────────────────────
export async function getUserTickets(userId) {
  const { data } = await client.get(`/api/admin/users/${userId}/tickets`);
  return data.data ?? [];
}

export async function updateTicketStatus(id, status, adminNote) {
  const { data } = await client.patch(`/api/admin/support/tickets/${id}/status`, { status, adminNote });
  return data.data;
}
