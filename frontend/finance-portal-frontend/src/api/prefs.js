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

// Backend @RequestBody JsonNode bekler → Content-Type: application/json gerek.
// Axios plain object/array için kendisi JSON serialize eder ve doğru header'ı set eder;
// ancak primitive değerlerde (number/string/boolean) Content-Type'ı atlar ve tarayıcı
// application/x-www-form-urlencoded'a düşer → 500 HttpMediaTypeNotSupportedException.
// Bu yüzden gövdeyi her zaman JSON string'e çeviriyor ve header'ı açıkça veriyoruz.
const JSON_PUT_CONFIG = { headers: { 'Content-Type': 'application/json' } };

/** localStorage'a yaz + (giriş yapılmışsa) debounce'lu olarak sunucuya senkronla. */
export function prefSet(key, value) {
  try { localStorage.setItem(key, JSON.stringify(value)); } catch { /* yoksay */ }
  if (!isAuthed()) return;
  if (timers.has(key)) clearTimeout(timers.get(key));
  timers.set(key, setTimeout(() => {
    timers.delete(key);
    client.put(`/api/me/preferences/${encodeURIComponent(key)}`, JSON.stringify(value), JSON_PUT_CONFIG)
      .catch(() => { /* best-effort */ });
  }, DEBOUNCE_MS));
}

/**
 * Kritik ayarlar için: bekleyen debounce'u iptal eder, PUT'u hemen gönderir ve sunucu
 * cevabına ait promise'i döndürür. Anonim kullanıcı için çözümlenmiş promise döner
 * (sadece localStorage yazımı yapılır). Hata durumunda promise reject olur ki çağıran
 * UI feedback verebilsin (toast.error, optimistic rollback, vb.).
 */
export function prefSetSync(key, value) {
  try { localStorage.setItem(key, JSON.stringify(value)); } catch { /* yoksay */ }
  if (!isAuthed()) return Promise.resolve();
  if (timers.has(key)) {
    clearTimeout(timers.get(key));
    timers.delete(key);
  }
  return client.put(`/api/me/preferences/${encodeURIComponent(key)}`, JSON.stringify(value), JSON_PUT_CONFIG);
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
