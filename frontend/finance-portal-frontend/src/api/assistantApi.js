import client from '../lib/http';

/**
 * Warren AI sohbet — geçmişi gönderir, asistan yanıtını döner.
 * @param {{role:'user'|'assistant', content:string}[]} messages
 * @returns {Promise<{status:string, reply:string|null}>}
 */
export async function sendAssistantChat(messages) {
  // Savunma: yalnızca geçerli {role, content} kayıtlarını gönder. Boş/null/biçimsiz
  // girdi backend'de "boş veya null messages" → INVALID kırılmasına yol açıyordu.
  const safe = Array.isArray(messages)
    ? messages.filter(m => m && typeof m.content === 'string' && m.content.trim().length > 0)
    : [];
  const { data: wrapper } = await client.post('/api/v1/assistant/chat', { messages: safe });
  return wrapper.data;
}
