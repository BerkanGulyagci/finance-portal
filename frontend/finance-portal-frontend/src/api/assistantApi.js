import client from '../lib/http';

/**
 * Warren AI sohbet — geçmişi gönderir, asistan yanıtını döner.
 * @param {{role:'user'|'assistant', content:string}[]} messages
 * @returns {Promise<{status:string, reply:string|null}>}
 */
export async function sendAssistantChat(messages) {
  const { data: wrapper } = await client.post('/api/assistant/chat', { messages });
  return wrapper.data;
}
