import { Fragment, useState, useEffect, useMemo } from 'react';
import { Link } from 'react-router-dom';
import { Settings2, ChevronDown, ChevronUp, TrendingUp, TrendingDown, Plus, Coins } from 'lucide-react';
import TrendBadge from '../../../components/common/TrendBadge';
import { computeTrend } from '../../../utils/trendUtils';
import { getWatchlistDetailPath } from '../constants/watchlistMarketRoutes';
import { MASK_MONEY, MASK_PERCENT, MASK_QTY } from '../utils/portfolioFormatUtils';
import { getCommodityUnit } from '../utils/commodityUnit';
import { useTranslation } from '../../../context/LanguageContext';
import CouponIncomeModal from './CouponIncomeModal';

// ── Sabitler ─────────────────────────────────────────────────────────────────

const MAX_COLS = 12;

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

/** Kullanıcının seçtiği kolonlar tarayıcıda saklanır (sonraki girişte korunur). */
const COLS_STORAGE_KEY = 'fp.holdings.columns.v1';

const ASSET_LABELS = {
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
];

const DEFAULT_KEYS = [...DEFAULT_DISPLAY_ORDER];

/** Kayıtlı kolon seçimini oku (geçersiz/eski anahtarları temizler). Yoksa null. */
function loadSavedColumns() {
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
const GROUP_ORDER = ['Temel', 'Fiyat', 'K/Z', 'Enflasyona Göre', 'Tarih', 'Piyasa', 'Teknik'];

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

function fmtNum(v, dec = 2) {
  if (v == null) return null;
  const n = parseFloat(v);
  if (isNaN(n)) return null;
  return n.toLocaleString('tr-TR', { minimumFractionDigits: dec, maximumFractionDigits: dec });
}

function fmtQty(v) {
  if (v == null) return null;
  const n = parseFloat(v);
  if (isNaN(n)) return null;
  return n.toLocaleString('tr-TR', { minimumFractionDigits: 0, maximumFractionDigits: 8 });
}

/** FUND pay miktarı: en fazla 8 ondalık, gereksiz sondaki sıfırlar kırpılır. */
function fmtQtyFund(v) {
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
function fmtFundNavPrice(v, currency) {
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

function fmtPrice(v, currency) {
  if (v == null) return null;
  const n = parseFloat(v);
  if (isNaN(n)) return null;
  const dec = Math.abs(n) < 1 ? 4 : 2;
  const s = n.toLocaleString('tr-TR', { minimumFractionDigits: dec, maximumFractionDigits: dec });
  return currency ? `${s} ${currency}` : s;
}

/** Para toplamları / K-Z tutarları: 2 ondalık (locale, yuvarlama — tablo para kolonları). */
function fmtMoneyTwoDecimals(v, currency) {
  if (v == null) return null;
  const n = parseFloat(v);
  if (!Number.isFinite(n)) return null;
  const s = n.toLocaleString('tr-TR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  return currency ? `${s} ${currency}` : s;
}

/** -0,00% gibi yanıltıcı yüzdeyi önler; çok küçük değerde daha fazla ondalık gösterir. */
function formatPercentWithSuffix(n) {
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

function fmtDate(v) {
  if (!v) return null;
  try { return new Date(v).toLocaleDateString('tr-TR'); } catch { return String(v); }
}

function fmtVol(v) {
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
function resolveHoldingName(h) {
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

function num(h, ...keys) {
  for (const k of keys) {
    const v = h[k];
    if (v == null || v === '') continue;
    const n = parseFloat(v);
    if (Number.isFinite(n)) return n;
  }
  return null;
}

/** Pozisyon piyasa değeri: backend marketValue veya qty × currentPrice. */
function positionMarketValue(h) {
  const mv = num(h, 'marketValue');
  if (mv != null) return mv;
  const q = num(h, 'totalQuantity');
  const p = num(h, 'currentPrice');
  if (q != null && p != null) return q * p;
  return null;
}

/** Gerçekleşmemiş K/Z: profitLoss veya (piyasa değeri − toplam maliyet). */
function unrealizedGainLoss(h) {
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
function positionDailyGainLoss(h) {
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
function columnHasValueForHolding(key, h) {
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
    default:               return true; // temel kolonlar
  }
}

// ── Küçük UI parçaları ────────────────────────────────────────────────────────

function Dash() {
  return <span className="text-gray-300">-</span>;
}

/** Reel/enflasyon kolonları boşken nedenini açıklayan tire (örn. bu ay alınmış pozisyon). */
function ReelDash({ t }) {
  return (
    <span
      className="text-gray-300 cursor-help"
      title={t('Enflasyon faktörü hesaplanamadı: pozisyon çok yeni ya da bu dönemin enflasyonu (TÜFE) henüz açıklanmadı.')}
    >
      -
    </span>
  );
}

function PnlAmt({ value, currency, valuesHidden }) {
  if (valuesHidden) {
    return <span className="font-bold text-gray-500 tracking-widest">{MASK_MONEY}</span>;
  }
  if (value == null) return <Dash />;
  const n = parseFloat(value);
  if (!Number.isFinite(n)) return <Dash />;
  if (Math.abs(n) < 1e-14) {
    return <span className="font-bold text-gray-500">{fmtMoneyTwoDecimals(0, currency)}</span>;
  }
  const pos = n > 0;
  return (
    <span className={`font-bold ${pos ? 'text-emerald-600' : 'text-rose-600'}`}>
      {pos ? '+' : ''}{fmtMoneyTwoDecimals(n, currency) ?? String(n)}
    </span>
  );
}

/** Enflasyonu yendi mi? rozeti — reel K/Z işaretine göre. */
function BeatChip({ beat, t }) {
  return beat ? (
    <span className="inline-flex items-center gap-1 text-xs font-bold text-emerald-700 bg-emerald-50 border border-emerald-200 px-2 py-0.5 rounded-md whitespace-nowrap">
      <TrendingUp className="w-3 h-3" /> {t('Yendi')}
    </span>
  ) : (
    <span className="inline-flex items-center gap-1 text-xs font-bold text-rose-700 bg-rose-50 border border-rose-200 px-2 py-0.5 rounded-md whitespace-nowrap">
      <TrendingDown className="w-3 h-3" /> {t('Yenildi')}
    </span>
  );
}

function PnlPct({ value, valuesHidden }) {
  if (valuesHidden) {
    return <span className="font-semibold text-gray-500 tracking-widest">{MASK_PERCENT}</span>;
  }
  if (value == null) return <Dash />;
  const n = parseFloat(value);
  if (!Number.isFinite(n)) return <Dash />;
  const display = formatPercentWithSuffix(n);
  if (Math.abs(n) < 1e-14) {
    return <span className="font-semibold text-gray-500">{display}</span>;
  }
  const pos = n > 0;
  return (
    <span className={`font-semibold ${pos ? 'text-emerald-600' : 'text-rose-600'}`}>
      {pos ? '+' : ''}{display}
    </span>
  );
}

// ── Hücre render ─────────────────────────────────────────────────────────────

function renderCell(key, h, commoditySpots, valuesHidden, t) {
  const cur = h.currency;
  const isFund = String(h.assetType ?? '').toUpperCase() === 'FUND';

  switch (key) {
    case 'name': {
      const name = resolveHoldingName(h);
      const detailPath = getWatchlistDetailPath(h.assetType, h.symbol);
      if (detailPath) {
        return (
          <Link
            to={detailPath}
            className="text-sm text-gray-800 block max-w-[180px] truncate font-medium hover:text-[#093eaa] hover:underline focus:outline-none focus-visible:ring-2 focus-visible:ring-[#093eaa]/40 rounded"
            title={`${name} — ${t('Piyasa detayı')}`}
          >
            {name}
          </Link>
        );
      }
      return (
        <span className="text-sm text-gray-800 block max-w-[180px] truncate" title={name}>
          {name}
        </span>
      );
    }

    case 'symbol':
      return <span className="font-bold text-[#093eaa] text-sm whitespace-nowrap">{h.symbol}</span>;

    case 'assetType': {
      const cat = String(h.category ?? '');
      const isInfBond = h.assetType === 'BOND' && cat.startsWith('INFLATION_');
      const isFxBond = h.assetType === 'BOND' && (cat === 'FX_DENOMINATED_BOND' || cat === 'FX_LEASE_CERTIFICATE');
      const isGoldBond = h.assetType === 'BOND' && (cat === 'GOLD_INDEXED_BOND' || cat === 'GOLD_INDEXED_LEASE_CERTIFICATE');
      const isSukuk = h.assetType === 'BOND' && cat.includes('LEASE_CERTIFICATE');
      return (
        <div className="inline-flex items-center gap-1 whitespace-nowrap">
          <span className="text-xs bg-gray-100 text-gray-600 px-2 py-0.5 rounded-md font-semibold">
            {ASSET_LABELS[h.assetType] ? t(ASSET_LABELS[h.assetType]) : h.assetType}
          </span>
          {isInfBond && (
            <span
              className="text-[10px] bg-amber-100 text-amber-700 px-1.5 py-0.5 rounded font-semibold"
              title={t('TÜFE-endeksli — EVDS gösterge değeri zaten enflasyona endeksli; piyasa değeri kendiliğinden TÜFE ile büyür')}
            >
              {t('TÜFE')}
            </span>
          )}
          {isFxBond && (
            <span
              className="text-[10px] bg-blue-100 text-blue-700 px-1.5 py-0.5 rounded font-semibold"
              title={t('Yabancı para cinsli — piyasa değeri TCMB satış kuruyla TL\'ye çevrilir')}
            >
              {h.currency ?? 'FX'}
            </span>
          )}
          {isGoldBond && (
            <span
              className="text-[10px] bg-yellow-100 text-yellow-800 px-1.5 py-0.5 rounded font-semibold"
              title={t('Altına dayalı — piyasa değeri gram has altın TL fiyatı ile hesaplanır')}
            >
              {t('Altın')}
            </span>
          )}
          {isSukuk && (
            <span
              className="text-[10px] bg-emerald-100 text-emerald-700 px-1.5 py-0.5 rounded font-semibold"
              title={t('Kira Sertifikası (Sukuk) — kira getirisi öder')}
            >
              {t('Sukuk')}
            </span>
          )}
        </div>
      );
    }

    case 'qty': {
      if (valuesHidden) {
        return <span className="text-sm text-gray-500 tracking-widest">{MASK_QTY}</span>;
      }
      const qtyText = isFund ? fmtQtyFund(h.totalQuantity) : fmtQty(h.totalQuantity);
      if (qtyText == null) return <Dash />;
      const commodityUnit = h.assetType === 'COMMODITY'
        ? getCommodityUnit(h.symbol, null)
        : null;
      return (
        <span className="text-sm font-mono">
          {qtyText}
          {commodityUnit && (
            <span className="ml-1 text-[11px] text-gray-500 font-sans">{commodityUnit}</span>
          )}
        </span>
      );
    }

    case 'avgCost': {
      if (valuesHidden) {
        return <span className="text-sm text-gray-500 tracking-widest">{MASK_MONEY}</span>;
      }
      // BOND: averageCost = totalCost/qty (1 nominal başına). Mevcut Fiyat ve işlem "Birim Fiyat (/100)"
      // 100 nominal başına kote olduğundan, tutarlılık için non-gold bondlarda ×100 gösterilir
      // (altın bond adet/gram bazlı → /100 yok, olduğu gibi).
      const isGoldBondRow = h.assetType === 'BOND'
        && (h.category === 'GOLD_INDEXED_BOND' || h.category === 'GOLD_INDEXED_LEASE_CERTIFICATE');
      const avgCostPar = (h.assetType === 'BOND' && !isGoldBondRow) ? 100 : 1;
      const avgCostDisplay = h.averageCost != null ? h.averageCost * avgCostPar : h.averageCost;
      return (
        <span className="text-sm">
          {(isFund ? fmtFundNavPrice(h.averageCost, cur) : fmtPrice(avgCostDisplay, cur)) ?? <Dash />}
        </span>
      );
    }

    case 'currentPrice':
      if (valuesHidden) {
        return <span className="text-sm text-gray-500 tracking-widest">{MASK_MONEY}</span>;
      }
      return (
        <span className="text-sm font-semibold">
          {(isFund ? fmtFundNavPrice(h.currentPrice, cur) : fmtPrice(h.currentPrice, cur)) ?? <Dash />}
        </span>
      );

    case 'marketValue': {
      if (valuesHidden) {
        return <span className="text-sm text-gray-500 tracking-widest">{MASK_MONEY}</span>;
      }
      const mv = positionMarketValue(h);
      const s = fmtMoneyTwoDecimals(mv, cur);
      const isViop = h.assetType === 'FUTURE';
      return (
        <span className="text-sm font-semibold inline-flex items-center gap-1">
          {s ?? <Dash />}
          {isViop && s != null && (
            <span
              title={t('VİOP değerleri kontrat adedi × fiyat mantığıyla basitleştirilmiş olarak hesaplanır. Gerçek nominal değer sözleşme büyüklüğüne göre farklı olabilir.')}
              className="inline-flex items-center justify-center w-3.5 h-3.5 rounded-full bg-amber-100 text-amber-700 text-[9px] font-bold cursor-help"
              aria-label="VIOP bilgi"
            >i</span>
          )}
        </span>
      );
    }

    case 'totalCost':
      if (valuesHidden) {
        return <span className="text-sm text-gray-500 tracking-widest">{MASK_MONEY}</span>;
      }
      return <span className="text-sm">{fmtMoneyTwoDecimals(h.totalCost, cur) ?? <Dash />}</span>;

    case 'unrealizedPnl': {
      const pnl = unrealizedGainLoss(h);
      return <PnlAmt value={pnl} currency={cur} valuesHidden={valuesHidden} />;
    }

    case 'unrealizedPct': {
      const pnl  = unrealizedGainLoss(h);
      const cost = num(h, 'totalCost');
      const pct  = cost != null && cost > 0 && pnl != null ? (pnl / cost) * 100 : null;
      return <PnlPct value={pct} valuesHidden={valuesHidden} />;
    }

    case 'dailyPnl': {
      const v = positionDailyGainLoss(h);
      return <PnlAmt value={v} currency={cur} valuesHidden={valuesHidden} />;
    }

    case 'dailyPct': {
      const v =
        h.dailyGainLossPercent ??
        h.dailyChangePercent ??
        h.changePercent ??
        h.returnOneDay ??
        null;
      return <PnlPct value={v} valuesHidden={valuesHidden} />;
    }

    case 'realizedPnl':
      return <PnlAmt value={h.realizedGainLoss ?? null} currency={cur} valuesHidden={valuesHidden} />;

    case 'realizedPct':
      return <PnlPct value={h.realizedGainLossPercent ?? null} valuesHidden={valuesHidden} />;

    case 'realPnl':
      if (!valuesHidden && h.realProfitLoss == null) return <ReelDash t={t} />;
      return <PnlAmt value={h.realProfitLoss ?? null} currency={cur} valuesHidden={valuesHidden} />;

    case 'realPct':
      if (!valuesHidden && h.realProfitLossPercent == null) return <ReelDash t={t} />;
      return <PnlPct value={h.realProfitLossPercent ?? null} valuesHidden={valuesHidden} />;

    case 'realPnlTry':
      if (!valuesHidden && h.realProfitLossTry == null) return <ReelDash t={t} />;
      return <PnlAmt value={h.realProfitLossTry ?? null} currency={'TL'} valuesHidden={valuesHidden} />;

    case 'realPctTry':
      if (!valuesHidden && h.realProfitLossPercentTry == null) return <ReelDash t={t} />;
      return <PnlPct value={h.realProfitLossPercentTry ?? null} valuesHidden={valuesHidden} />;

    case 'beatInflation': {
      if (valuesHidden) return <Dash />;
      const rv = h.realProfitLoss ?? h.realProfitLossTry;
      if (rv == null) return <ReelDash t={t} />;
      const n = parseFloat(rv);
      if (!Number.isFinite(n)) return <ReelDash t={t} />;
      return <BeatChip beat={n > 0} t={t} />;
    }

    case 'inflationSince': {
      if (valuesHidden) {
        return <span className="text-sm text-gray-500 tracking-widest">{MASK_PERCENT}</span>;
      }
      const v = h.inflationSincePercent;
      if (v == null) return <ReelDash t={t} />;
      const n = parseFloat(v);
      if (!Number.isFinite(n)) return <Dash />;
      const src = h.inflationSource || t('TÜFE');
      // Enflasyon nötr bir büyüklük (iyi/kötü değil) → amber, +/- renklendirme yok
      return (
        <span
          className="text-sm font-medium text-amber-600 whitespace-nowrap"
          title={t('İlk alış tarihinden bugüne birikimli enflasyon ({src})', { src })}
        >
          {formatPercentWithSuffix(n)}
          {h.inflationSource && (
            <span className="ml-1 text-[10px] font-semibold text-gray-400">{src}</span>
          )}
        </span>
      );
    }

    case 'currency':
      return <span className="text-sm text-gray-700">{h.currency ?? <Dash />}</span>;

    case 'firstBuyDate': {
      const d = h.firstBuyDate ?? h.firstTransactionDate ?? null;
      const s = fmtDate(d);
      return <span className="text-sm text-gray-700">{s ?? <Dash />}</span>;
    }

    case 'lastTxDate': {
      const s = fmtDate(h.lastTransactionDate ?? null);
      return <span className="text-sm text-gray-700">{s ?? <Dash />}</span>;
    }

    case 'volume': {
      const isFuture = String(h.assetType ?? '').toUpperCase() === 'FUTURE';
      const isBond = String(h.assetType ?? '').toUpperCase() === 'BOND';
      const s = fmtVol(h.volume ?? null);
      return (
        <span
          className="text-sm font-mono text-gray-700"
          title={
            isBond
              ? t('DİBS için borsa işlem hacmi verisi yok (EVDS gösterge serisi).')
              : isFuture
                ? t('Toplam açık pozisyon (kontrat)')
                : undefined
          }
        >
          {s ?? <Dash />}
        </span>
      );
    }

    case 'week52': {
      // Backend STOCK → StockDetail.fiftyTwoWeekHigh/Low, önce bunlara bak
      const lo = h.fiftyTwoWeekLow  ?? h.week52Low  ?? h.fiftyTwoWeekRange?.split?.('–')?.[0] ?? null;
      const hi = h.fiftyTwoWeekHigh ?? h.week52High ?? h.fiftyTwoWeekRange?.split?.('–')?.[1] ?? null;
      if (h.fiftyTwoWeekRange) return <span className="text-sm text-gray-700 whitespace-nowrap">{h.fiftyTwoWeekRange}</span>;
      if (h.week52Range)       return <span className="text-sm text-gray-700 whitespace-nowrap">{h.week52Range}</span>;
      if (lo != null && hi != null) {
        if (isFund) {
          return (
            <span className="text-sm text-gray-700 whitespace-nowrap">
              {fmtFundNavPrice(lo, null)} – {fmtFundNavPrice(hi, null)}
            </span>
          );
        }
        return (
          <span className="text-sm text-gray-700 whitespace-nowrap">
            {fmtNum(lo)} – {fmtNum(hi)}
          </span>
        );
      }
      return <Dash />;
    }


    case 'trend':
      return <TrendBadge item={h} />;

    default:
      return <Dash />;
  }
}

// ── Sütun düzenleyici (inline panel) ─────────────────────────────────────────

function ColumnEditor({ open, onToggle, selected, onChange }) {
  const { t } = useTranslation();
  const [warn, setWarn] = useState(false);

  function toggle(key) {
    if (selected.includes(key)) {
      if (selected.length <= 1) return;
      onChange(selected.filter(k => k !== key));
      setWarn(false);
    } else {
      if (selected.length >= MAX_COLS) {
        setWarn(true);
        return;
      }
      onChange([...selected, key]);
      setWarn(false);
    }
  }

  const byGroup = {};
  for (const col of ALL_COLS) {
    (byGroup[col.group] ??= []).push(col);
  }

  return (
    <>
      {/* Toolbar satırı */}
      <div className="flex items-center justify-between px-4 py-2 border-b border-gray-100">
        <span className="text-xs text-gray-400">
          {t('{count} sütun görüntüleniyor', { count: selected.length })}
        </span>
        <button
          type="button"
          onClick={() => { onToggle(); setWarn(false); }}
          className={`flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold border rounded-lg transition-colors
            ${open
              ? 'bg-[#093eaa] text-white border-[#093eaa]'
              : 'text-gray-500 border-gray-200 hover:border-gray-300 hover:bg-gray-50'}`}
        >
          <Settings2 className="w-3.5 h-3.5" />
          {t('Düzenle')}
          {open ? <ChevronUp className="w-3 h-3" /> : <ChevronDown className="w-3 h-3" />}
        </button>
      </div>

      {/* Inline panel — taşmaz, overflow-hidden'dan etkilenmez */}
      {open && (
        <div className="border-b border-gray-200 bg-gray-50 px-4 py-4">
          <div className="flex items-center justify-between mb-3">
            <span className="text-sm font-bold text-gray-800">{t('Sütunları Düzenle')}</span>
            <div className="flex items-center gap-3">
              <span className="text-xs text-gray-400">
                {t('Seçili:')}{' '}
                <span className={selected.length >= MAX_COLS ? 'text-amber-600 font-bold' : 'font-semibold'}>
                  {selected.length}
                </span>{' '}
                / {MAX_COLS}
              </span>
              <button
                type="button"
                onClick={() => { onChange(DEFAULT_KEYS); setWarn(false); }}
                className="text-xs text-[#093eaa] hover:underline font-medium"
              >
                {t('Sıfırla')}
              </button>
            </div>
          </div>

          {warn && (
            <p className="text-xs text-amber-700 bg-amber-50 border border-amber-200 rounded-lg px-3 py-2 mb-3">
              {t('En fazla {max} sütun seçilebilir.', { max: MAX_COLS })}
            </p>
          )}

          {/* Gruplar — hepsi yan yana; dar ekranda az öğeli gruplar alt satıra kayar */}
          <div className="grid grid-cols-2 sm:grid-cols-4 lg:grid-cols-7 gap-x-4 gap-y-5 items-start">
            {GROUP_ORDER.map(group => {
              const cols = byGroup[group];
              if (!cols?.length) return null;
              return (
                <div key={group}>
                  <p className="text-xs font-bold text-gray-400 uppercase tracking-wider mb-2">{t(group)}</p>
                  <div className="space-y-1">
                    {cols.map(col => {
                      const checked  = selected.includes(col.key);
                      const disabled = !checked && selected.length >= MAX_COLS;
                      return (
                        <label
                          key={col.key}
                          title={col.hint ? t(col.hint) : undefined}
                          className={`flex items-start gap-2 rounded-md px-2 py-1 cursor-pointer transition-colors select-none
                            ${checked  ? 'bg-blue-50 text-blue-800' : 'hover:bg-white text-gray-700'}
                            ${disabled ? 'opacity-40 cursor-not-allowed' : ''}`}
                        >
                          <input
                            type="checkbox"
                            checked={checked}
                            disabled={disabled}
                            onChange={() => toggle(col.key)}
                            className="w-3.5 h-3.5 rounded accent-[#093eaa] shrink-0 mt-0.5"
                          />
                          <span className="leading-tight">
                            <span className="block text-xs font-medium">{t(col.label)}</span>
                            {col.hint && (
                              <span className="block text-[10px] font-normal text-gray-400 leading-tight">
                                {t(col.hint)}
                              </span>
                            )}
                          </span>
                        </label>
                      );
                    })}
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}
    </>
  );
}

// ── Ana bileşen ───────────────────────────────────────────────────────────────

/**
 * Props:
 *   holdings: PortfolioHoldingResponse[]
 */
export default function HoldingsTable({
  holdings = [],
  commoditySpots = {},
  valuesHidden = false,
  // Yeni props (opsiyonel — geriye uyumlu):
  transactions = [],          // BOND expand'inde kupon listesi için
  portfolioId = null,         // Kupon Ekle modalı için
  onPortfolioChanged = null,  // Kupon eklendikten sonra çağrılır (parent'tan portfolio'yu reload eder)
  onSelectedKeysChange = null, // Excel/PDF export "ekranda seçili kolonlar" — parent'a scalar selectedKeys yansıtır
}) {
  const { t } = useTranslation();
  const [selectedKeys, setSelectedKeys] = useState(() => loadSavedColumns() ?? DEFAULT_KEYS);
  const [editorOpen, setEditorOpen]     = useState(false);
  const [expandedKeys, setExpandedKeys] = useState(() => new Set());
  const [couponModalSymbol, setCouponModalSymbol] = useState(null);

  // Selected keys değişince parent'a yansıt (export butonlarının visibleCols'u bilmesi için).
  useEffect(() => {
    onSelectedKeysChange?.(selectedKeys);
  }, [selectedKeys, onSelectedKeysChange]);

  // Seçim değişince tarayıcıya kaydet (sonraki girişte korunur).
  useEffect(() => {
    try {
      localStorage.setItem(COLS_STORAGE_KEY, JSON.stringify(selectedKeys));
    } catch {
      /* localStorage yoksa yoksay */
    }
  }, [selectedKeys]);

  const visibleCols = buildVisibleCols(selectedKeys);

  // BOND için sembol başına COUPON_INCOME tx listesi — expand'de gösterilir
  const couponsBySymbol = useMemo(() => {
    const map = new Map();
    (transactions || []).forEach(tx => {
      if (tx?.transactionType === 'COUPON_INCOME' && tx?.symbol) {
        const list = map.get(tx.symbol) || [];
        list.push(tx);
        map.set(tx.symbol, list);
      }
    });
    // Tarihe göre yeni → eski
    for (const list of map.values()) {
      list.sort((a, b) => (b.transactionDate || '').localeCompare(a.transactionDate || ''));
    }
    return map;
  }, [transactions]);

  // Mevcut varlıkların hiçbirinde verisi olmayan seçili kolonlar — başlıkta işaretlenir
  const emptyColKeys = new Set(
    visibleCols
      .filter(c => !holdings.some(h => columnHasValueForHolding(c.key, h)))
      .map(c => c.key),
  );

  // Holding listesinde herhangi bir BOND varsa expand kolonu render edilir
  const hasAnyBond = holdings.some(h => String(h.assetType ?? '').toUpperCase() === 'BOND');

  if (!holdings.length) {
    return (
      <div className="p-12 text-center text-gray-400 text-sm">
        {t('Henüz varlık yok. "Pozisyon Ekle" butonuna basarak başlayın.')}
      </div>
    );
  }

  function toggleExpand(key) {
    setExpandedKeys(prev => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });
  }

  const couponHoldingForModal = couponModalSymbol
    ? holdings.find(h => h.symbol === couponModalSymbol && String(h.assetType).toUpperCase() === 'BOND')
    : null;

  return (
    <div>
      <ColumnEditor
        open={editorOpen}
        onToggle={() => setEditorOpen(v => !v)}
        selected={selectedKeys}
        onChange={setSelectedKeys}
      />

      {/* Tablo */}
      <div className="overflow-x-auto">
        <table className="w-full">
          <thead className="bg-gray-50">
            <tr>
              {hasAnyBond && (
                <th className="w-8 px-2 py-3 border-b border-gray-200" aria-label="expand" />
              )}
              {visibleCols.map(col => {
                const isEmpty = emptyColKeys.has(col.key);
                return (
                  <th
                    key={col.key}
                    title={isEmpty
                      ? t('Mevcut varlıklarda bu sütun için veri yok')
                      : (col.hint ? t(col.hint) : undefined)}
                    className={`text-left px-4 py-3 text-xs font-bold uppercase tracking-wider border-b border-gray-200 whitespace-nowrap ${isEmpty ? 'text-gray-300' : 'text-gray-500'}`}
                  >
                    {t(col.label)}
                    {isEmpty && (
                      <span className="ml-1 normal-case font-normal text-[10px] text-gray-300">
                        ({t('veri yok')})
                      </span>
                    )}
                  </th>
                );
              })}
            </tr>
          </thead>
          <tbody>
            {holdings.map(h => {
              const rowKey = `${h.assetType}-${h.symbol}`;
              const isBond = String(h.assetType ?? '').toUpperCase() === 'BOND';
              const isClosed = !!h.closed;
              const coupons = isBond ? (couponsBySymbol.get(h.symbol) || []) : [];
              // Sadece kupon ödeyen kategorilerde expand + Kupon Ekle göster.
              const COUPON_PAYING = new Set([
                'FIXED_COUPON_BOND','TLREF_INDEXED_BOND','INFLATION_INDEXED_BOND',
                'GOLD_INDEXED_BOND','FX_DENOMINATED_BOND',
                'LEASE_CERTIFICATE','INFLATION_INDEXED_LEASE_CERTIFICATE',
                'GOLD_INDEXED_LEASE_CERTIFICATE','FX_LEASE_CERTIFICATE',
              ]);
              const isCouponPaying = isBond && COUPON_PAYING.has(String(h.category ?? ''));
              const canExpand = isBond && (coupons.length > 0 || (portfolioId && !isClosed && isCouponPaying));
              const expanded = expandedKeys.has(rowKey);
              const rowClass = isClosed
                ? 'border-t border-gray-100 bg-gray-50/60 text-gray-500'
                : 'border-t border-gray-100 hover:bg-gray-50 transition-colors';
              return (
                <Fragment key={rowKey}>
                  <tr className={rowClass}>
                    {hasAnyBond && (
                      <td className="w-8 px-2 py-3 align-middle">
                        {canExpand ? (
                          <button
                            type="button"
                            onClick={() => toggleExpand(rowKey)}
                            className="p-1 rounded hover:bg-gray-200 text-gray-500 hover:text-gray-700"
                            title={expanded ? t('Daralt') : t('Genişlet — kupon ödemelerini gör')}
                          >
                            {expanded
                              ? <ChevronUp className="w-3.5 h-3.5" />
                              : <ChevronDown className="w-3.5 h-3.5" />}
                          </button>
                        ) : null}
                      </td>
                    )}
                    {visibleCols.map(col => (
                      <td key={col.key} className="px-4 py-3">
                        {isClosed
                          ? renderClosedBondCell(col.key, h, t)
                          : renderCell(col.key, h, commoditySpots, valuesHidden, t)}
                      </td>
                    ))}
                  </tr>
                  {isBond && expanded && (() => {
                    // Sadece periyodik kupon ÖDEYEN kategorilerde "Kupon Ekle" butonu görünür.
                    // Kuponsuz bondlar (Hazine Bonosu, kuponsuz DT, stripler) periyodik ödeme yapmaz.
                    const couponPayingCategories = new Set([
                      'FIXED_COUPON_BOND',
                      'TLREF_INDEXED_BOND',
                      'INFLATION_INDEXED_BOND',
                      'GOLD_INDEXED_BOND',
                      'FX_DENOMINATED_BOND',
                      'LEASE_CERTIFICATE',
                      'INFLATION_INDEXED_LEASE_CERTIFICATE',
                      'GOLD_INDEXED_LEASE_CERTIFICATE',
                      'FX_LEASE_CERTIFICATE',
                    ]);
                    const isCouponPaying = couponPayingCategories.has(String(h.category ?? ''));
                    return (
                      <tr className="bg-gray-50/40">
                        <td colSpan={(hasAnyBond ? 1 : 0) + visibleCols.length} className="px-4 py-3">
                          <BondExpandPanel
                            coupons={coupons}
                            canAddCoupon={!!portfolioId && !isClosed && isCouponPaying}
                            onAddCoupon={() => setCouponModalSymbol(h.symbol)}
                            t={t}
                          />
                        </td>
                      </tr>
                    );
                  })()}
                </Fragment>
              );
            })}
          </tbody>
        </table>
      </div>

      {couponHoldingForModal && (
        <CouponIncomeModal
          portfolioId={portfolioId}
          holding={couponHoldingForModal}
          onClose={() => setCouponModalSymbol(null)}
          onAdded={(updated) => {
            setCouponModalSymbol(null);
            if (onPortfolioChanged) onPortfolioChanged(updated);
          }}
        />
      )}
    </div>
  );
}

// ── BOND expand paneli (kupon ödemeleri + Kupon Ekle butonu) ──────────────────

function BondExpandPanel({ coupons, canAddCoupon, onAddCoupon, t }) {
  const total = coupons.reduce((acc, c) => acc + parseFloat(c.quantity || 0), 0);
  return (
    <div className="space-y-2">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2 text-xs font-semibold text-[#434653]">
          <Coins className="w-4 h-4 text-[#093eaa]" />
          {t('Kupon Ödemeleri')}
          {coupons.length > 0 && (
            <span className="text-[#747684] font-normal">
              · {coupons.length} {t('kayıt')}
              {' · '}
              {t('Toplam')}: {total.toLocaleString('tr-TR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} TL
            </span>
          )}
        </div>
        {canAddCoupon && (
          <button
            type="button"
            onClick={onAddCoupon}
            className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md bg-[#093eaa] hover:bg-[#072e80] text-white text-[11px] font-bold transition-colors"
          >
            <Plus className="w-3 h-3" /> {t('Kupon Ekle')}
          </button>
        )}
      </div>
      {coupons.length === 0 ? (
        <p className="text-[11px] text-[#747684] italic">
          {t('Henüz kupon ödemesi kaydedilmedi.')}
          {canAddCoupon && ' ' + t('Bankanızdan kupon geldiğinde yukarıdaki butonla ekleyebilirsiniz.')}
        </p>
      ) : (
        <div className="bg-white border border-gray-200 rounded-md overflow-hidden">
          <table className="w-full text-xs">
            <thead className="bg-gray-50 text-[10px] uppercase text-gray-500">
              <tr>
                <th className="text-left px-3 py-1.5">{t('Tarih')}</th>
                <th className="text-right px-3 py-1.5">{t('Tutar (TL)')}</th>
              </tr>
            </thead>
            <tbody>
              {coupons.map((c, idx) => (
                <tr key={c.id ?? idx} className="border-t border-gray-100">
                  <td className="px-3 py-1.5 text-gray-700">
                    {(c.transactionDate || '').split('T')[0] || '-'}
                  </td>
                  <td className="px-3 py-1.5 text-right font-mono text-gray-900">
                    {parseFloat(c.quantity || 0).toLocaleString('tr-TR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

// ── Kapalı (vade itfası) BOND için sütun render ──────────────────────────────

function renderClosedBondCell(colKey, h, t) {
  const Dash = () => <span className="text-gray-300">—</span>;
  switch (colKey) {
    case 'name':
      return (
        <div className="flex items-center gap-2">
          <span className="text-sm font-medium text-gray-500 line-through">
            {h.name ?? h.symbol}
          </span>
          <span className="text-[10px] bg-amber-100 text-amber-700 px-1.5 py-0.5 rounded font-semibold whitespace-nowrap">
            {t('İtfa edildi')}
          </span>
        </div>
      );
    case 'symbol':
      return <span className="text-sm font-mono text-gray-500">{h.symbol}</span>;
    case 'assetType':
      return (
        <span className="text-xs bg-gray-100 text-gray-600 px-2 py-0.5 rounded-md font-semibold">
          {t('DİBS')}
        </span>
      );
    case 'totalCost':
      // Orijinal yatırılan toplam (initialCost) — kullanıcı "ne yatırdım"ı görür
      if (h.initialCost == null) return <Dash />;
      return (
        <span className="text-sm text-gray-600">
          {parseFloat(h.initialCost).toLocaleString('tr-TR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} TL
        </span>
      );
    case 'realizedPnl': {
      if (h.realizedGainLoss == null) return <Dash />;
      const n = parseFloat(h.realizedGainLoss);
      const cls = n > 0 ? 'text-emerald-600' : n < 0 ? 'text-rose-600' : 'text-gray-600';
      return (
        <span className={`text-sm font-semibold ${cls}`}>
          {n > 0 ? '+' : ''}{n.toLocaleString('tr-TR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} TL
        </span>
      );
    }
    case 'realizedPct': {
      if (h.realizedGainLossPercent == null) return <Dash />;
      const n = parseFloat(h.realizedGainLossPercent);
      const cls = n > 0 ? 'text-emerald-600' : n < 0 ? 'text-rose-600' : 'text-gray-600';
      return (
        <span className={`text-sm font-semibold ${cls}`}>
          {n > 0 ? '+' : ''}{n.toFixed(2).replace('.', ',')}%
        </span>
      );
    }
    default:
      return <Dash />;
  }
}
