// Piyasa şeridinde (ticker) hangi öğelerin gösterileceği — kullanıcı tercihi (localStorage).

export const TICKER_PREFS_KEY = 'fp-ticker-items';
export const TICKER_PREFS_EVENT = 'fp-ticker-prefs-changed';

// Şeritte gösterilebilecek tüm öğeler (gruplu katalog). key'ler MarketTicker ile eşleşir.
export const TICKER_CATALOG = [
  {
    group: 'TCMB Döviz',
    items: [
      { key: 'fx:USD', label: 'USD/TRY' },
      { key: 'fx:EUR', label: 'EUR/TRY' },
      { key: 'fx:GBP', label: 'GBP/TRY' },
    ],
  },
  {
    group: 'Banka Kurları',
    items: [
      { key: 'bank:USD', label: 'USD/TRY (Banka)' },
      { key: 'bank:EUR', label: 'EUR/TRY (Banka)' },
      { key: 'bank:GBP', label: 'GBP/TRY (Banka)' },
    ],
  },
  {
    group: 'Altın',
    items: [{ key: 'gold:ons', label: 'ALTIN/ONS' }],
  },
  {
    group: 'Kripto',
    items: [
      { key: 'crypto:btc', label: 'BTC' },
      { key: 'crypto:eth', label: 'ETH' },
      { key: 'crypto:bnb', label: 'BNB' },
      { key: 'crypto:sol', label: 'SOL' },
    ],
  },
];

export const ALL_TICKER_KEYS = TICKER_CATALOG.flatMap(g => g.items.map(i => i.key));

/** Etkin öğe anahtarları (Set). Kayıt yoksa varsayılan: hepsi açık. */
export function readTickerPrefs() {
  try {
    const saved = JSON.parse(localStorage.getItem(TICKER_PREFS_KEY) || 'null');
    if (Array.isArray(saved)) {
      return new Set(saved.filter(k => ALL_TICKER_KEYS.includes(k)));
    }
  } catch { /* yoksay */ }
  return new Set(ALL_TICKER_KEYS);
}

/** Tercihleri kaydet + aynı sekmede MarketTicker'ı haberdar et. */
export function saveTickerPrefs(keys) {
  try {
    localStorage.setItem(TICKER_PREFS_KEY, JSON.stringify([...keys]));
    window.dispatchEvent(new Event(TICKER_PREFS_EVENT));
  } catch { /* yoksay */ }
}

// ── Kullanıcının şeride eklediği özel varlıklar (herhangi bir enstrüman) ───────
export const TICKER_CUSTOM_KEY = 'fp-ticker-custom';

/** [{ assetType, symbol, name }] */
export function readCustomTickerItems() {
  try {
    const v = JSON.parse(localStorage.getItem(TICKER_CUSTOM_KEY) || 'null');
    return Array.isArray(v) ? v : [];
  } catch {
    return [];
  }
}

export function saveCustomTickerItems(list) {
  try {
    localStorage.setItem(TICKER_CUSTOM_KEY, JSON.stringify(list));
    window.dispatchEvent(new Event(TICKER_PREFS_EVENT));
  } catch { /* yoksay */ }
}
