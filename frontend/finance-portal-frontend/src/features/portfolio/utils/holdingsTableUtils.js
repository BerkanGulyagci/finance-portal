// HoldingsTable'dan çıkarılan SAF sabitler + formatter'lar + data-helper'lar + kolon yardımcıları.
// React/JSX yok. Davranış orijinaliyle birebir aynıdır (taşıma; tek satır mantık değişmedi).

import { computeTrend } from '../../../utils/trendUtils';

// ── Sabitler ─────────────────────────────────────────────────────────────────

export const MAX_COLS = 12;

/** Varsayılan kolonlar — tabloda bu sırayla gösterilir; Sıfırla aynı seti verir. */
export const DEFAULT_DISPLAY_ORDER = [
  'name',
  'symbol',
  'assetType',
  'qty',
  'avgCost',
  'currentPrice',
  'marketValue',
  'totalCost',
  'unrealizedPnl',
  'unrealizedPct',
  'dailyPct',
];

/** VİOP holding'i bulunan kullanıcılarda varsayılan olarak eklenen ek kolonlar
 *  ("teminat durumu"). Saf hisse/fon portföylerinde header kalabalık olmasın diye
 *  yalnız FUTURE varsa otomatik açılır; kullanıcı sonra column editor'dan kalıcı
 *  seçim yapabilir (localStorage saklar). */
export const FUTURE_DEFAULT_EXTRAS = ['marginStatus'];

/** Kullanıcının seçtiği kolonlar tarayıcıda saklanır (sonraki girişte korunur). */
export const COLS_STORAGE_KEY = 'fp.holdings.columns.v1';

export const ASSET_LABELS = {
  STOCK: 'Hisse', CRYPTO: 'Kripto', FX: 'Döviz',
  FUND: 'Fon', FUTURE: 'Vadeli', GOLD: 'Altın',
  COMMODITY: 'Emtia', BOND: 'DİBS',
};

/**
 * Tüm kolon tanımları.
 * def: true → sayfa ilk açıldığında seçili gelir.
 * group: popover'daki grup başlığı.
 */
export const ALL_COLS = [
  { key: 'name',          label: 'İsim',                  def: true,  group: 'Temel',   type: 'string' },
  { key: 'symbol',        label: 'Sembol',                def: true,  group: 'Temel',   type: 'string' },
  { key: 'assetType',     label: 'Tür',                   def: true,  group: 'Temel',   type: 'string' },
  { key: 'qty',           label: 'Miktar',                def: true,  group: 'Temel',   type: 'number' },
  { key: 'avgCost',       label: 'Ortalama Alış',         def: true,  group: 'Temel',   type: 'number' },
  { key: 'currentPrice',  label: 'Mevcut Fiyat',          def: true,  group: 'Fiyat',   type: 'number' },
  { key: 'marketValue',   label: 'Piyasa Değeri',         def: true,  group: 'Fiyat',   type: 'number' },
  { key: 'totalCost',     label: 'Toplam Maliyet',        def: true,  group: 'Temel',   type: 'number' },
  { key: 'unrealizedPnl', label: 'Gerçekleşmemiş K/Z',   def: true,  group: 'K/Z',     type: 'number', hint: 'Nominal — enflasyon hariç' },
  { key: 'unrealizedPct', label: 'Gerçekleşmemiş K/Z %', def: true,  group: 'K/Z',     type: 'percent', hint: 'Nominal — enflasyon hariç' },
  { key: 'dailyPnl',      label: 'Günlük K/Z',           def: false, group: 'K/Z',     type: 'number', hint: 'Bugün düne göre' },
  { key: 'dailyPct',      label: 'Günlük K/Z %',         def: false, group: 'K/Z',     type: 'percent', hint: 'Bugün düne göre' },
  { key: 'realizedPnl',   label: 'Gerçekleşmiş K/Z',     def: false, group: 'K/Z',     type: 'number', hint: 'Satıştan kesinleşen' },
  { key: 'realizedPct',   label: 'Gerçekleşmiş K/Z %',   def: false, group: 'K/Z',     type: 'percent', hint: 'Satıştan kesinleşen' },
  { key: 'realPnl',       label: 'Reel K/Z',             def: false, group: 'Enflasyona Göre', type: 'number', hint: 'Kendi para birimi' },
  { key: 'realPct',       label: 'Reel K/Z %',           def: false, group: 'Enflasyona Göre', type: 'percent', hint: 'Kendi para birimi' },
  { key: 'realPnlTry',    label: 'Reel K/Z (TL)',        def: false, group: 'Enflasyona Göre', type: 'number', hint: 'TL alım gücü (TÜFE)' },
  { key: 'realPctTry',    label: 'Reel K/Z % (TL)',      def: false, group: 'Enflasyona Göre', type: 'percent', hint: 'TL alım gücü (TÜFE)' },
  { key: 'beatInflation', label: 'Enf. Durumu',          def: false, group: 'Enflasyona Göre', type: 'string', hint: 'Yendi / Yenildi' },
  { key: 'inflationSince',label: 'Enflasyon (alıştan)',  def: false, group: 'Enflasyona Göre', type: 'percent', hint: 'Birikimli enflasyon %' },
  { key: 'currency',      label: 'Para Birimi',           def: false, group: 'Temel',   type: 'string' },
  { key: 'firstBuyDate',  label: 'İlk Alış Tarihi',      def: false, group: 'Tarih',   type: 'date' },
  { key: 'lastTxDate',    label: 'Son İşlem Tarihi',      def: false, group: 'Tarih',   type: 'date' },
  { key: 'volume',        label: 'Hacim',                 def: false, group: 'Piyasa',  type: 'number' },
  { key: 'week52',        label: '52 Hafta Aralığı',      def: false, group: 'Teknik',  type: 'string' },
  { key: 'trend',         label: 'Trend',                 def: false, group: 'Teknik',  type: 'string' },
  // VİOP — sadece FUTURE satırlarda anlamlıdır; diğer satırlarda boş gösterilir.
  { key: 'viopDirection', label: 'VİOP Yön',             def: false, group: 'VİOP',    type: 'string', hint: 'LONG / SHORT' },
  { key: 'viopMargin',    label: 'VİOP Teminat',          def: false, group: 'VİOP',    type: 'number', hint: 'Yatırılan başlangıç teminatı' },
  { key: 'viopNominal',   label: 'VİOP Nominal',          def: false, group: 'VİOP',    type: 'number', hint: 'Sözleşmenin tam piyasa büyüklüğü qty × fiyat × çarpan; toplama EKLENMEZ — referans için' },
  { key: 'viopLeverage',  label: 'VİOP Kaldıraç',         def: false, group: 'VİOP',    type: 'number', hint: 'Notional / Teminat' },
  { key: 'marginStatus',  label: 'Teminat Durumu',        def: true,  group: 'VİOP',    type: 'string', hint: 'Sağlıklı / Uyarı / Kritik — başlangıç teminatına göre kalan özsermaye oranı (Equity / InitialMargin)' },
];

export const DEFAULT_KEYS = [...DEFAULT_DISPLAY_ORDER];

/** Kayıtlı kolon seçimini oku (geçersiz/eski anahtarları temizler). Yoksa null. */
export function loadSavedColumns() {
  try {
    const raw = localStorage.getItem(COLS_STORAGE_KEY);
    if (!raw) return null;
    const arr = JSON.parse(raw);
    if (!Array.isArray(arr)) return null;
    const valid = arr.filter(k => ALL_COLS.some(c => c.key === k));
    return valid.length ? valid.slice(0, MAX_COLS) : null;
  } catch {
    return null;
  }
}

// Grup sırası popover'da
export const GROUP_ORDER = ['Temel', 'Fiyat', 'K/Z', 'Enflasyona Göre', 'Tarih', 'Piyasa', 'Teknik', 'VİOP'];

/** Seçili kolonları varsayılan sıra + ekstra kolonlar (ALL_COLS sırası) ile döndürür. */
export function buildVisibleCols(selectedKeys) {
  const set = new Set(selectedKeys);
  const primary = DEFAULT_DISPLAY_ORDER
    .filter(k => set.has(k))
    .map(k => ALL_COLS.find(c => c.key === k))
    .filter(Boolean);
  const extra = ALL_COLS.filter(c => set.has(c.key) && !DEFAULT_DISPLAY_ORDER.includes(c.key));
  return [...primary, ...extra];
}

// ── Format yardımcıları ───────────────────────────────────────────────────────

export function fmtNum(v, dec = 2) {
  if (v == null) return null;
  const n = parseFloat(v);
  if (isNaN(n)) return null;
  return n.toLocaleString('tr-TR', { minimumFractionDigits: dec, maximumFractionDigits: dec });
}

export function fmtQty(v) {
  if (v == null) return null;
  const n = parseFloat(v);
  if (isNaN(n)) return null;
  return n.toLocaleString('tr-TR', { minimumFractionDigits: 0, maximumFractionDigits: 8 });
}

/** FUND pay miktarı: en fazla 8 ondalık, gereksiz sondaki sıfırlar kırpılır. */
export function fmtQtyFund(v) {
  if (v == null) return null;
  const n = parseFloat(v);
  if (!Number.isFinite(n)) return null;
  const trimmed = n.toFixed(8).replace(/\.?0+$/, '');
  if (!trimmed.includes('.')) {
    return Number(trimmed).toLocaleString('tr-TR', { maximumFractionDigits: 0 });
  }
  const [intg, frac] = trimmed.split('.');
  const intLoc = Number(intg).toLocaleString('tr-TR', { useGrouping: true });
  return `${intLoc},${frac}`;
}

/**
 * Sıfıra doğru kesim (yuvarlama yok) — FUND NAV / birim pay gösterimi.
 * @param {number} maxFrac kesilecek ondalık basamak (FUND için 6)
 */
function truncateTowardZero(n, maxFrac) {
  if (!Number.isFinite(n)) return NaN;
  const f = 10 ** maxFrac;
  return n >= 0 ? Math.floor(n * f + 1e-9) / f : Math.ceil(n * f - 1e-9) / f;
}

const FUND_NAV_DISPLAY_FRAC = 6;

/** FUND: averageCost / currentPrice — 6 ondalığa truncate, gösterimde tam 6 hane (yuvarlama yok). */
export function fmtFundNavPrice(v, currency) {
  if (v == null) return null;
  const n = parseFloat(v);
  if (!Number.isFinite(n)) return null;
  const t = truncateTowardZero(n, FUND_NAV_DISPLAY_FRAC);
  const s = t.toLocaleString('tr-TR', {
    minimumFractionDigits: FUND_NAV_DISPLAY_FRAC,
    maximumFractionDigits: FUND_NAV_DISPLAY_FRAC,
  });
  return currency ? `${s} ${currency}` : s;
}

export function fmtPrice(v, currency) {
  if (v == null) return null;
  const n = parseFloat(v);
  if (isNaN(n)) return null;
  const dec = Math.abs(n) < 1 ? 4 : 2;
  const s = n.toLocaleString('tr-TR', { minimumFractionDigits: dec, maximumFractionDigits: dec });
  return currency ? `${s} ${currency}` : s;
}

/** Para toplamları / K-Z tutarları: 2 ondalık (locale, yuvarlama — tablo para kolonları). */
export function fmtMoneyTwoDecimals(v, currency) {
  if (v == null) return null;
  const n = parseFloat(v);
  if (!Number.isFinite(n)) return null;
  const s = n.toLocaleString('tr-TR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  return currency ? `${s} ${currency}` : s;
}

/** -0,00% gibi yanıltıcı yüzdeyi önler; çok küçük değerde daha fazla ondalık gösterir. */
export function formatPercentWithSuffix(n) {
  if (!Number.isFinite(n)) return null;
  if (Math.abs(n) < 1e-14) return '0,00%';

  const abs = Math.abs(n);
  const rounded2 = parseFloat(n.toFixed(2));
  const misleadingZero = abs > 1e-12 && Math.abs(rounded2) < 1e-10;

  let decimals = 2;
  if (misleadingZero || (abs > 0 && abs < 0.005)) {
    for (let d = 4; d <= 10; d += 1) {
      decimals = d;
      if (Math.abs(parseFloat(n.toFixed(d))) > 1e-12) break;
    }
  }

  let s = n.toFixed(decimals);
  if (decimals > 2 && abs < 0.005) {
    s = s.replace(/\.?0+$/, '');
  }
  return `${s.replace('.', ',')}%`;
}

export function fmtDate(v) {
  if (!v) return null;
  try { return new Date(v).toLocaleDateString('tr-TR'); } catch { return String(v); }
}

export function fmtVol(v) {
  if (v == null) return null;
  const n = parseFloat(v);
  if (isNaN(n)) return null;
  if (n >= 1_000_000_000) return (n / 1_000_000_000).toFixed(2) + 'B';
  if (n >= 1_000_000)     return (n / 1_000_000).toFixed(2) + 'M';
  if (n >= 1_000)         return (n / 1_000).toFixed(1) + 'K';
  return fmtNum(n, 0) ?? String(n);
}

const METAL_TR = { SILVER: 'Gümüş', PLATINUM: 'Platin', PALLADIUM: 'Paladyum', GOLD: 'Altın' };
const UNIT_TR = { GRAM_TRY: 'Gram', KG_TRY: 'Kg', USD_ONS: 'Ons', EUR_ONS: 'Ons', GRAM: 'Gram' };

/**
 * Görünen isim — kıymetli maden (SILVER:GRAM_TRY gibi) sembollerini birim+metal olarak açar
 * (ör. "Gram Gümüş"); diğerlerinde backend adını kullanır.
 */
export function resolveHoldingName(h) {
  const base = h.name ?? h.displayName ?? h.instrumentName ?? h.symbol;
  const sym = String(h.symbol ?? '');
  if (sym.includes(':')) {
    const [metal, cat] = sym.toUpperCase().split(':');
    const m = METAL_TR[metal];
    if (m) {
      const u = UNIT_TR[cat];
      return u ? `${u} ${m}` : m;
    }
  }
  return base;
}

export function num(h, ...keys) {
  for (const k of keys) {
    const v = h[k];
    if (v == null || v === '') continue;
    const n = parseFloat(v);
    if (Number.isFinite(n)) return n;
  }
  return null;
}

/** Pozisyon piyasa değeri: backend marketValue veya qty × currentPrice. */
export function positionMarketValue(h) {
  const mv = num(h, 'marketValue');
  if (mv != null) return mv;
  const q = num(h, 'totalQuantity');
  const p = num(h, 'currentPrice');
  if (q != null && p != null) return q * p;
  return null;
}

/** Gerçekleşmemiş K/Z: profitLoss veya (piyasa değeri − toplam maliyet). */
export function unrealizedGainLoss(h) {
  const pl = num(h, 'profitLoss');
  if (pl != null) return pl;
  const mv = positionMarketValue(h);
  const cost = num(h, 'totalCost');
  if (mv != null && cost != null) return mv - cost;
  return null;
}

/**
 * Pozisyon günlük K/Z (TL): miktar × hisse başı günlük değişim; yoksa miktar × (fiyat − önceki kapanış).
 * BOND için TCMB konvansiyonu: fiyat 100 nominal başına kote → /100 uygulanır.
 * Altın bond (adet/gram bazlı) istisna: /100 YOK.
 */
export function positionDailyGainLoss(h) {
  const qty = num(h, 'totalQuantity');
  const isBond = h?.assetType === 'BOND';
  const isGoldBond = h?.category === 'GOLD_INDEXED_BOND'
    || h?.category === 'GOLD_INDEXED_LEASE_CERTIFICATE';
  const scale = (isBond && !isGoldBond) ? 100 : 1;
  const perShare = num(h, 'dailyChangeAmount', 'change');
  if (qty != null && perShare != null) return (qty * perShare) / scale;

  const pc = num(h, 'previousClose', 'regularMarketPreviousClose', 'prevClose');
  const cp = num(h, 'currentPrice');
  if (qty != null && cp != null && pc != null) return (qty * (cp - pc)) / scale;
  return null;
}

/**
 * Excel/PDF export için bir kolonun RAW değerini döndürür (formatsız).
 * - number/percent kolonlarda → Number veya null
 * - date kolonlarda → ISO YYYY-MM-DD string veya ''
 * - string kolonlarda → string
 *
 * Excel tarafı bu raw değeri ALL_COLS[key].type'a göre tipleyip formatlar.
 * Bilinmeyen key → throw (yeni kolon eklenince fark edilir).
 */
export function renderCellForExport(key, h) {
  const numProp = (...keys) => {
    for (const k of keys) {
      const v = h?.[k];
      if (v == null || v === '') continue;
      const n = typeof v === 'number' ? v : parseFloat(v);
      if (Number.isFinite(n)) return n;
    }
    return null;
  };

  switch (key) {
    case 'name':         return h.name ?? h.symbol ?? '';
    case 'symbol':       return h.symbol ?? '';
    case 'assetType':    return ASSET_LABELS[h.assetType] ?? String(h.assetType ?? '');
    case 'qty':          return numProp('totalQuantity');
    case 'avgCost':      return numProp('averageCost');
    case 'currentPrice': return numProp('currentPrice');
    case 'marketValue':  return positionMarketValue(h);
    case 'totalCost':    return numProp('totalCost');
    case 'unrealizedPnl': return unrealizedGainLoss(h);
    case 'unrealizedPct': {
      const pl = unrealizedGainLoss(h);
      const cost = numProp('totalCost');
      return cost && pl != null ? (pl / cost) * 100 : null;
    }
    case 'dailyPnl':     return positionDailyGainLoss(h);
    case 'dailyPct':     return numProp('dailyGainLossPercent', 'dailyChangePercent', 'changePercent', 'returnOneDay');
    case 'realizedPnl':  return numProp('realizedGainLoss');
    case 'realizedPct':  return numProp('realizedGainLossPercent');
    case 'realPnl':      return numProp('realProfitLoss');
    case 'realPct':      return numProp('realProfitLossPercent');
    case 'realPnlTry':   return numProp('realProfitLossTry');
    case 'realPctTry':   return numProp('realProfitLossPercentTry');
    case 'beatInflation': {
      const pct = numProp('realProfitLossPercent');
      if (pct == null) return '';
      return pct >= 0 ? 'Enflasyonu Yendi' : 'Enflasyona Yenildi';
    }
    case 'inflationSince': return numProp('inflationSincePercent');
    case 'currency':     return h.currency ?? 'TRY';
    case 'firstBuyDate': return h.firstBuyDate ? String(h.firstBuyDate).slice(0, 10) : '';
    case 'lastTxDate':   return h.lastTransactionDate ? String(h.lastTransactionDate).slice(0, 10) : '';
    case 'volume':       return numProp('volume');
    case 'week52': {
      const low = numProp('fiftyTwoWeekLow');
      const high = numProp('fiftyTwoWeekHigh');
      if (low == null && high == null) return '';
      const fmt = (n) => n == null ? '-' : n.toLocaleString('tr-TR', { maximumFractionDigits: 2 });
      return `${fmt(low)} – ${fmt(high)}`;
    }
    case 'trend': {
      const trend = computeTrend(h);
      if (!trend) return '';
      if (trend === 'UP') return 'Yükseliş';
      if (trend === 'DOWN') return 'Düşüş';
      return 'Yatay';
    }
    case 'viopDirection':
      return h.assetType === 'FUTURE' ? (h.viopDirection ?? 'LONG') : '';
    case 'viopMargin':
      return h.assetType === 'FUTURE' ? numProp('viopMarginPosted') : null;
    case 'viopNominal':
      return h.assetType === 'FUTURE' ? numProp('viopNotional') : null;
    case 'viopLeverage':
      return h.assetType === 'FUTURE' ? numProp('viopLeverage') : null;
    case 'marginStatus':
      return h.assetType === 'FUTURE' ? (h.marginStatus ?? '') : '';
    default:
      throw new Error(`renderCellForExport: bilinmeyen kolon "${key}"`);
  }
}

/**
 * Bir kolonun belirli bir varlıkta gerçek verisi var mı?
 * Temel kolonlar (isim, sembol, miktar vb.) her zaman dolu kabul edilir; veri kaynağına
 * bağlı kolonlar (günlük K/Z, hacim, 52 hafta, trend, reel/gerçekleşmiş K/Z) gerçek
 * değere göre değerlendirilir. Tüm satırlarda boşsa başlıkta "(veri yok)" ipucu gösterilir.
 */
export function columnHasValueForHolding(key, h) {
  switch (key) {
    case 'marketValue':    return positionMarketValue(h) != null;
    case 'unrealizedPnl':
    case 'unrealizedPct':  return unrealizedGainLoss(h) != null;
    case 'dailyPnl':       return positionDailyGainLoss(h) != null;
    case 'dailyPct':       return (h.dailyGainLossPercent ?? h.dailyChangePercent ?? h.changePercent ?? h.returnOneDay) != null;
    case 'realizedPnl':    return h.realizedGainLoss != null;
    case 'realizedPct':    return h.realizedGainLossPercent != null;
    case 'realPnl':        return h.realProfitLoss != null;
    case 'realPct':        return h.realProfitLossPercent != null;
    case 'realPnlTry':     return h.realProfitLossTry != null;
    case 'realPctTry':     return h.realProfitLossPercentTry != null;
    case 'beatInflation':  return (h.realProfitLoss ?? h.realProfitLossTry) != null;
    case 'inflationSince': return h.inflationSincePercent != null;
    case 'volume':         return h.volume != null;
    case 'week52':
      return (
        h.fiftyTwoWeekRange != null || h.week52Range != null ||
        ((h.fiftyTwoWeekLow ?? h.week52Low) != null && (h.fiftyTwoWeekHigh ?? h.week52High) != null)
      );
    case 'trend':          return !!computeTrend(h).signal;
    case 'viopDirection':  return h.assetType === 'FUTURE';
    case 'viopMargin':     return h.assetType === 'FUTURE' && h.viopMarginPosted != null;
    case 'viopNominal':    return h.assetType === 'FUTURE' && h.viopNotional != null;
    case 'viopLeverage':   return h.assetType === 'FUTURE' && h.viopLeverage != null;
    case 'marginStatus':   return h.assetType === 'FUTURE' && h.marginStatus != null;
    default:               return true; // temel kolonlar
  }
}
