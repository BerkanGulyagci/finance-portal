import client from '../lib/http';

// Kullanıcı arayüz tercihleri: localStorage (anında + anonim/çevrimdışı) + giriş yapılmışsa sunucu senkronu.
// Yazma: localStorage'a hemen, sunucuya debounce'lu PUT. Okuma: localStorage (giriş sonrası hydrate ile dolu).

const TOKEN_KEY = 'auth_token';
const DEBOUNCE_MS = 600;
const timers = new Map();

function isAuthed() {
  return !!localStorage.getItem(TOKEN_KEY);
}

/** localStorage'dan JSON oku. */
export function prefGet(key, fallback = null) {
  try {
    const v = localStorage.getItem(key);
    return v != null ? JSON.parse(v) : fallback;
  } catch {
    return fallback;
  }
}

/** localStorage'a yaz + (giriş yapılmışsa) debounce'lu olarak sunucuya senkronla. */
export function prefSet(key, value) {
  try { localStorage.setItem(key, JSON.stringify(value)); } catch { /* yoksay */ }
  if (!isAuthed()) return;
  if (timers.has(key)) clearTimeout(timers.get(key));
  timers.set(key, setTimeout(() => {
    timers.delete(key);
    client.put(`/api/me/preferences/${encodeURIComponent(key)}`, value).catch(() => { /* best-effort */ });
  }, DEBOUNCE_MS));
}

/** Sunucudaki tüm tercihleri çekip localStorage'a yazar (giriş sonrası bir kez). */
export async function hydratePrefs() {
  if (!isAuthed()) return;
  try {
    const { data: wrapper } = await client.get('/api/me/preferences');
    const prefs = wrapper?.data ?? {};
    Object.entries(prefs).forEach(([k, v]) => {
      try { localStorage.setItem(k, JSON.stringify(v)); } catch { /* yoksay */ }
    });
  } catch { /* best-effort: yerel değerler kalır */ }
}
