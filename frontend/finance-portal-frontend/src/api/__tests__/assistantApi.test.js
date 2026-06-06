import { describe, it, expect, beforeEach, vi } from 'vitest';

// '../../lib/http' default export'u (axios benzeri client) MOCK'lanır — gerçek ağ isteği atılmasın.
// assistantApi.js TEK bağımlılığı bu client'tır; sendAssistantChat client.post ile sunucuya gider.
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
import { sendAssistantChat } from '../assistantApi';

beforeEach(() => {
  vi.clearAllMocks();
});

// ─────────────────────────────────────────────────────────────────────────────
// sendAssistantChat — POST /api/assistant/chat
// Kaynak: const { data: wrapper } = await client.post(...); return wrapper.data;
// Yani dış sarmalayıcı { data: { ... } } soyulur, içteki .data döndürülür.
// ─────────────────────────────────────────────────────────────────────────────
describe('sendAssistantChat', () => {
  it('doğru URL ve { messages } gövdesi ile POST çağırır, içteki wrapper.data alanını döndürür', async () => {
    const messages = [
      { role: 'user', content: 'Merhaba' },
      { role: 'assistant', content: 'Selam' },
      { role: 'user', content: 'Portföyüm nasıl?' },
    ];
    const inner = { status: 'OK', reply: 'Portföyün gayet iyi.' };
    // Çift katmanlı: dıştaki data sarmalayıcı, içindeki data asıl yük.
    client.post.mockResolvedValue({ data: { data: inner } });

    const result = await sendAssistantChat(messages);

    expect(client.post).toHaveBeenCalledTimes(1);
    expect(client.post).toHaveBeenCalledWith('/api/v1/assistant/chat', { messages });
    // Dönen değer içteki .data nesnesinin TA KENDİSİ olmalı (soyma doğru).
    expect(result).toBe(inner);
    expect(result).toEqual({ status: 'OK', reply: 'Portföyün gayet iyi.' });
  });

  it('içteki data null ise null döndürür (edge: boş yanıt)', async () => {
    client.post.mockResolvedValue({ data: { data: null } });

    const result = await sendAssistantChat([{ role: 'user', content: 'x' }]);

    expect(client.post).toHaveBeenCalledWith('/api/v1/assistant/chat', {
      messages: [{ role: 'user', content: 'x' }],
    });
    expect(result).toBeNull();
  });

  it('boş/biçimsiz kayıtları eler (savunma: backend boş messages → INVALID kırılmasın)', async () => {
    const inner = { status: 'OK', reply: 'ok' };
    client.post.mockResolvedValue({ data: { data: inner } });

    // null, content olmayan, boş-string, sadece-boşluk → hepsi elenir; geçerli olan kalır.
    await sendAssistantChat([
      null,
      { role: 'user' },
      { role: 'user', content: '   ' },
      { role: 'user', content: '' },
      { role: 'user', content: 'Merhaba' },
    ]);

    expect(client.post).toHaveBeenCalledWith('/api/v1/assistant/chat', {
      messages: [{ role: 'user', content: 'Merhaba' }],
    });
  });

  it('içteki data alanı yoksa undefined döndürür (edge: wrapper.data tanımsız)', async () => {
    client.post.mockResolvedValue({ data: {} });

    const result = await sendAssistantChat([{ role: 'user', content: 'merhaba' }]);

    expect(result).toBeUndefined();
  });

  it('boş messages dizisi boş gönderilir (edge: geçmiş yok)', async () => {
    const inner = { status: 'OK', reply: null };
    client.post.mockResolvedValue({ data: { data: inner } });

    const result = await sendAssistantChat([]);

    expect(client.post).toHaveBeenCalledWith('/api/v1/assistant/chat', { messages: [] });
    expect(result).toBe(inner);
  });

  it('messages undefined ise boş diziye normalize edilir (savunma)', async () => {
    client.post.mockResolvedValue({ data: { data: { status: 'OK', reply: '' } } });

    await sendAssistantChat(undefined);

    expect(client.post).toHaveBeenCalledWith('/api/v1/assistant/chat', { messages: [] });
  });

  it('client.post reddedilirse hata yukarı fırlatılır (yutulmaz)', async () => {
    client.post.mockRejectedValue(new Error('ağ hatası'));

    await expect(
      sendAssistantChat([{ role: 'user', content: 'x' }]),
    ).rejects.toThrow('ağ hatası');
    expect(client.post).toHaveBeenCalledWith('/api/v1/assistant/chat', {
      messages: [{ role: 'user', content: 'x' }],
    });
  });
});
