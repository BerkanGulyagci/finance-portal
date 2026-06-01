// Piyasa şeridinde (ticker) hangi öğelerin gösterileceği — kullanıcı tercihi (cihazlar arası senkron: prefs).
import { prefGet, prefSet } from '../api/prefs';

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
    group: 'Endeksler (BIST)',
    items: [
      { key: 'bist:XU100', label: 'BIST 100' },
      { key: 'bist:XU030', label: 'BIST 30' },
      { key: 'bist:XU050', label: 'BIST 50' },
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
  {
    group: 'Ekonomi',
    items: [
      { key: 'eco:inflation', label: 'TÜFE (Enflasyon)' },
      { key: 'eco:policyRate', label: 'Politika Faizi' },
      { key: 'eco:ppi', label: 'ÜFE' },
      { key: 'eco:deposit', label: 'Mevduat Faizi' },
    ],
  },
];

export const ALL_TICKER_KEYS = TICKER_CATALOG.flatMap(g => g.items.map(i => i.key));

/** Etkin öğe anahtarları (Set). Kayıt yoksa varsayılan: hepsi açık. */
export function readTickerPrefs() {
  const saved = prefGet(TICKER_PREFS_KEY, null);
  if (Array.isArray(saved)) {
    return new Set(saved.filter(k => ALL_TICKER_KEYS.includes(k)));
  }
  return new Set(ALL_TICKER_KEYS);
}

/** Tercihleri kaydet + aynı sekmede MarketTicker'ı haberdar et. */
export function saveTickerPrefs(keys) {
  prefSet(TICKER_PREFS_KEY, [...keys]);
  window.dispatchEvent(new Event(TICKER_PREFS_EVENT));
}

// ── Kullanıcının şeride eklediği özel varlıklar (herhangi bir enstrüman) ───────
export const TICKER_CUSTOM_KEY = 'fp-ticker-custom';

/** [{ assetType, symbol, name }] */
export function readCustomTickerItems() {
  const v = prefGet(TICKER_CUSTOM_KEY, []);
  return Array.isArray(v) ? v : [];
}

export function saveCustomTickerItems(list) {
  prefSet(TICKER_CUSTOM_KEY, list);
  window.dispatchEvent(new Event(TICKER_PREFS_EVENT));
}
