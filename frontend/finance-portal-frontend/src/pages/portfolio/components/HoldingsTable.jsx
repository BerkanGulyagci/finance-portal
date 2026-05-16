import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Settings2, ChevronDown, ChevronUp } from 'lucide-react';
import TrendBadge from '../../../components/common/TrendBadge';
import { getWatchlistDetailPath } from '../constants/watchlistMarketRoutes';
import { CommodityDualPrice } from './CommodityPriceHint';
import { isYahooCommoditySymbol } from '../../../utils/commodityPriceUtils';

// ── Sabitler ─────────────────────────────────────────────────────────────────

const MAX_COLS = 10;

/** Varsayılan 10 kolon — tabloda bu sırayla gösterilir; Sıfırla aynı seti verir. */
const DEFAULT_DISPLAY_ORDER = [
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
];

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
const ALL_COLS = [
  { key: 'name',          label: 'İsim',                  def: true,  group: 'Temel'   },
  { key: 'symbol',        label: 'Sembol',                def: true,  group: 'Temel'   },
  { key: 'assetType',     label: 'Tür',                   def: true,  group: 'Temel'   },
  { key: 'qty',           label: 'Miktar',                def: true,  group: 'Temel'   },
  { key: 'avgCost',       label: 'Ortalama Alış',         def: true,  group: 'Temel'   },
  { key: 'currentPrice',  label: 'Mevcut Fiyat',          def: true,  group: 'Fiyat'   },
  { key: 'marketValue',   label: 'Piyasa Değeri',         def: true,  group: 'Fiyat'   },
  { key: 'totalCost',     label: 'Toplam Maliyet',        def: true,  group: 'Temel'   },
  { key: 'unrealizedPnl', label: 'Gerçekleşmemiş K/Z',   def: true,  group: 'K/Z'     },
  { key: 'unrealizedPct', label: 'Gerçekleşmemiş K/Z %', def: true,  group: 'K/Z'     },
  { key: 'dailyPnl',      label: 'Günlük K/Z',           def: false, group: 'K/Z'     },
  { key: 'dailyPct',      label: 'Günlük K/Z %',         def: false, group: 'K/Z'     },
  { key: 'realizedPnl',   label: 'Gerçekleşmiş K/Z',     def: false, group: 'K/Z'     },
  { key: 'realizedPct',   label: 'Gerçekleşmiş K/Z %',   def: false, group: 'K/Z'     },
  { key: 'currency',      label: 'Para Birimi',           def: false, group: 'Temel'   },
  { key: 'firstBuyDate',  label: 'İlk Alış Tarihi',      def: false, group: 'Tarih'   },
  { key: 'lastTxDate',    label: 'Son İşlem Tarihi',      def: false, group: 'Tarih'   },
  { key: 'volume',        label: 'Hacim',                 def: false, group: 'Piyasa'  },
  { key: 'week52',        label: '52 Hafta Aralığı',      def: false, group: 'Teknik'  },
  { key: 'trend',         label: 'Trend',                 def: false, group: 'Teknik'  },
];

const DEFAULT_KEYS = [...DEFAULT_DISPLAY_ORDER];

// Grup sırası popover'da
const GROUP_ORDER = ['Temel', 'Fiyat', 'K/Z', 'Tarih', 'Piyasa', 'Teknik'];

/** Seçili kolonları varsayılan sıra + ekstra kolonlar (ALL_COLS sırası) ile döndürür. */
function buildVisibleCols(selectedKeys) {
  const set = new Set(selectedKeys);
  const primary = DEFAULT_DISPLAY_ORDER
    .filter(k => set.has(k))
    .map(k => ALL_COLS.find(c => c.key === k))
    .filter(Boolean);
  const extra = ALL_COLS.filter(c => set.has(c.key) && !DEFAULT_DISPLAY_ORDER.includes(c.key));
  return [...primary, ...extra];
}

/** Tabloda DİBS ve diğer türler birlikteyken sütun başlıklarını ayarlar. */
function holdingsBondMix(holdings) {
  if (!holdings?.length) {
    return { hasBond: false, onlyBond: false, mixed: false };
  }
  const hasBond = holdings.some(h => String(h.assetType ?? '').toUpperCase() === 'BOND');
  const onlyBond = hasBond && holdings.every(h => String(h.assetType ?? '').toUpperCase() === 'BOND');
  const mixed = hasBond && !onlyBond;
  return { hasBond, onlyBond, mixed };
}

function columnHeaderLabel(col, mix) {
  if (!mix.hasBond) return col.label;
  if (col.key === 'avgCost') {
    if (mix.onlyBond) return 'Ort. Gösterge (maliyet)';
    if (mix.mixed) return 'Ortalama alış / gösterge';
    return col.label;
  }
  if (col.key === 'currentPrice') {
    if (mix.onlyBond) return 'Güncel gösterge';
    if (mix.mixed) return 'Mevcut fiyat / gösterge';
    return col.label;
  }
  return col.label;
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

/** Pozisyon günlük K/Z (TL): miktar × hisse başı günlük değişim; yoksa miktar × (fiyat − önceki kapanış). */
function positionDailyGainLoss(h) {
  const qty = num(h, 'totalQuantity');
  const perShare = num(h, 'dailyChangeAmount', 'change');
  if (qty != null && perShare != null) return qty * perShare;

  const pc = num(h, 'previousClose', 'regularMarketPreviousClose', 'prevClose');
  const cp = num(h, 'currentPrice');
  if (qty != null && cp != null && pc != null) return qty * (cp - pc);
  return null;
}

// ── Küçük UI parçaları ────────────────────────────────────────────────────────

function Dash() {
  return <span className="text-gray-300">-</span>;
}

function PnlAmt({ value, currency }) {
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

function PnlPct({ value }) {
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

function renderCell(key, h, commoditySpots) {
  const cur = h.currency;
  const isFund = String(h.assetType ?? '').toUpperCase() === 'FUND';
  const isYahooCommodity =
    String(h.assetType ?? '').toUpperCase() === 'COMMODITY' && isYahooCommoditySymbol(h.symbol);
  const commoditySpot = isYahooCommodity ? commoditySpots?.[h.symbol] : null;

  switch (key) {
    case 'name': {
      const name = h.name ?? h.displayName ?? h.instrumentName ?? h.symbol;
      const detailPath = getWatchlistDetailPath(h.assetType, h.symbol);
      if (detailPath) {
        return (
          <Link
            to={detailPath}
            className="text-sm text-gray-800 block max-w-[180px] truncate font-medium hover:text-[#093eaa] hover:underline focus:outline-none focus-visible:ring-2 focus-visible:ring-[#093eaa]/40 rounded"
            title={`${name} — Piyasa detayı`}
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

    case 'assetType':
      return (
        <span className="text-xs bg-gray-100 text-gray-600 px-2 py-0.5 rounded-full font-semibold whitespace-nowrap">
          {ASSET_LABELS[h.assetType] ?? h.assetType}
        </span>
      );

    case 'qty':
      return (
        <span className="text-sm font-mono">
          {(isFund ? fmtQtyFund(h.totalQuantity) : fmtQty(h.totalQuantity)) ?? <Dash />}
        </span>
      );

    case 'avgCost':
      return (
        <span className="text-sm">
          {(isFund ? fmtFundNavPrice(h.averageCost, cur) : fmtPrice(h.averageCost, cur)) ?? <Dash />}
        </span>
      );

    case 'currentPrice':
      if (isYahooCommodity && commoditySpot) {
        return <CommodityDualPrice tryAmount={h.currentPrice} spot={commoditySpot} />;
      }
      return (
        <span className="text-sm font-semibold">
          {(isFund ? fmtFundNavPrice(h.currentPrice, cur) : fmtPrice(h.currentPrice, cur)) ?? <Dash />}
        </span>
      );

    case 'marketValue': {
      const mv = positionMarketValue(h);
      const s = fmtMoneyTwoDecimals(mv, cur);
      return <span className="text-sm font-semibold">{s ?? <Dash />}</span>;
    }

    case 'totalCost':
      return <span className="text-sm">{fmtMoneyTwoDecimals(h.totalCost, cur) ?? <Dash />}</span>;

    case 'unrealizedPnl': {
      const pnl = unrealizedGainLoss(h);
      return <PnlAmt value={pnl} currency={cur} />;
    }

    case 'unrealizedPct': {
      const pnl  = unrealizedGainLoss(h);
      const cost = num(h, 'totalCost');
      const pct  = cost != null && cost > 0 && pnl != null ? (pnl / cost) * 100 : null;
      return <PnlPct value={pct} />;
    }

    case 'dailyPnl': {
      const v = positionDailyGainLoss(h);
      return <PnlAmt value={v} currency={cur} />;
    }

    case 'dailyPct': {
      const v =
        h.dailyGainLossPercent ??
        h.dailyChangePercent ??
        h.changePercent ??
        h.returnOneDay ??
        null;
      return <PnlPct value={v} />;
    }

    case 'realizedPnl':
      return <PnlAmt value={h.realizedGainLoss ?? null} currency={cur} />;

    case 'realizedPct':
      return <PnlPct value={h.realizedGainLossPercent ?? null} />;

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
              ? 'DİBS için borsa işlem hacmi verisi yok (EVDS gösterge serisi).'
              : isFuture
                ? 'Toplam açık pozisyon (kontrat)'
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
          {selected.length} sütun görüntüleniyor
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
          Düzenle
          {open ? <ChevronUp className="w-3 h-3" /> : <ChevronDown className="w-3 h-3" />}
        </button>
      </div>

      {/* Inline panel — taşmaz, overflow-hidden'dan etkilenmez */}
      {open && (
        <div className="border-b border-gray-200 bg-gray-50 px-4 py-4">
          <div className="flex items-center justify-between mb-3">
            <span className="text-sm font-bold text-gray-800">Sütunları Düzenle</span>
            <div className="flex items-center gap-3">
              <span className="text-xs text-gray-400">
                Seçili:{' '}
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
                Sıfırla
              </button>
            </div>
          </div>

          {warn && (
            <p className="text-xs text-amber-700 bg-amber-50 border border-amber-200 rounded-lg px-3 py-2 mb-3">
              En fazla {MAX_COLS} sütun seçilebilir.
            </p>
          )}

          {/* Gruplar — yatayda kaydırılabilir grid */}
          <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-4">
            {GROUP_ORDER.map(group => {
              const cols = byGroup[group];
              if (!cols?.length) return null;
              return (
                <div key={group}>
                  <p className="text-xs font-bold text-gray-400 uppercase tracking-wider mb-2">{group}</p>
                  <div className="space-y-1">
                    {cols.map(col => {
                      const checked  = selected.includes(col.key);
                      const disabled = !checked && selected.length >= MAX_COLS;
                      return (
                        <label
                          key={col.key}
                          className={`flex items-center gap-2 rounded-md px-2 py-1 cursor-pointer transition-colors select-none
                            ${checked  ? 'bg-blue-50 text-blue-800' : 'hover:bg-white text-gray-700'}
                            ${disabled ? 'opacity-40 cursor-not-allowed' : ''}`}
                        >
                          <input
                            type="checkbox"
                            checked={checked}
                            disabled={disabled}
                            onChange={() => toggle(col.key)}
                            className="w-3.5 h-3.5 rounded accent-[#093eaa] shrink-0"
                          />
                          <span className="text-xs font-medium leading-tight">{col.label}</span>
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
export default function HoldingsTable({ holdings = [], commoditySpots = {} }) {
  const [selectedKeys, setSelectedKeys] = useState(DEFAULT_KEYS);
  const [editorOpen, setEditorOpen]     = useState(false);

  const visibleCols = buildVisibleCols(selectedKeys);
  const bondMix = holdingsBondMix(holdings);

  if (!holdings.length) {
    return (
      <div className="p-12 text-center text-gray-400 text-sm">
        Henüz varlık yok. "Pozisyon Ekle" butonuna basarak başlayın.
      </div>
    );
  }

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
              {visibleCols.map(col => (
                <th
                  key={col.key}
                  className="text-left px-4 py-3 text-xs font-bold text-gray-500 uppercase tracking-wider border-b border-gray-200 whitespace-nowrap"
                >
                  {columnHeaderLabel(col, bondMix)}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {holdings.map(h => (
              <tr key={`${h.assetType}-${h.symbol}`} className="border-t border-gray-100 hover:bg-gray-50 transition-colors">
                {visibleCols.map(col => (
                  <td key={col.key} className="px-4 py-3">
                    {renderCell(col.key, h, commoditySpots)}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
