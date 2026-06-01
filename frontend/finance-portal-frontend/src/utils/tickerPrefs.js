// Piyasa şeridinde (ticker) hangi öğelerin gösterileceği — kullanıcı tercihi (cihazlar arası senkron: prefs).
import { prefGet, prefSet, prefSetSync } from '../api/prefs';

export const TICKER_PREFS_KEY = 'fp-ticker-items';
export const TICKER_PREFS_EVENT = 'fp-ticker-prefs-changed';

// Akış hızı tercihi (kayan şerit). Backend allow-list yok; istenen key 'market_ticker_speed'.
export const TICKER_SPEED_KEY = 'market_ticker_speed';
export const TICKER_SPEED_VALUES = ['slow', 'normal', 'fast'];
// MarketTicker'daki requestAnimationFrame döngüsünün px/sn katsayısı.
// 40 px/sn mevcut varsayılan; slow=0.5×, fast=2×. Hız ve animasyon süresi orantılıdır
// (track genişliği sabit kabul edilince), yani slow≈120s/loop, normal≈60s, fast≈30s gibi
// algılanır — ürün şartıyla uyumlu.
export const TICKER_SPEED_PX = { slow: 20, normal: 40, fast: 80 };

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

// ── Akış hızı oku/yaz ──────────────────────────────────────────────────────
/** Geçerli akış hızını döner: 'slow' | 'normal' | 'fast' (varsayılan: 'normal'). */
export function readTickerSpeed() {
  const v = prefGet(TICKER_SPEED_KEY, null);
  return TICKER_SPEED_VALUES.includes(v) ? v : 'normal';
}

/**
 * Hız tercihini kaydet — prefSetSync ile (anında PUT) ve diğer açık sekmeleri haberdar et.
 * prefSetSync 8d1fb99'daki JSON Content-Type yaklaşımını kullanır; debounce yok.
 */
export function saveTickerSpeed(value) {
  const safe = TICKER_SPEED_VALUES.includes(value) ? value : 'normal';
  prefSetSync(TICKER_SPEED_KEY, safe).catch(() => { /* best-effort */ });
  window.dispatchEvent(new Event(TICKER_PREFS_EVENT));
}
