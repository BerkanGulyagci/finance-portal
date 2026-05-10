import { Link } from 'react-router-dom';
import { Trash2 } from 'lucide-react';

const COMMODITY_NAMES = {
  // Enerji
  'CL=F': 'WTI Ham Petrol',
  'BZ=F': 'Brent Ham Petrol',
  'NG=F': 'Doğal Gaz',
  // Sanayi
  'HG=F': 'Bakır',
  // Tarım
  'ZW=F': 'Buğday',
  'ZC=F': 'Mısır',
  'KC=F': 'Kahve',
  'CC=F': 'Kakao',
  'CT=F': 'Pamuk',
};

const GOLD_NAMES = {
  GOLD: 'Altın (Ons)',
  GRAM: 'Gram Altın',
  CEYREK: 'Çeyrek Altın',
  YARIM: 'Yarım Altın',
  TAM: 'Tam Altın',
  CUMHUR: 'Cumhuriyet Altını',
  ATA: 'Ata Altın',
};

const PRECIOUS_NAMES = {
  'SILVER:GRAM_TRY': 'Gram Gümüş (₺)',
  'SILVER:KG_TRY': 'Kg Gümüş (₺)',
  'SILVER:USD_ONS': 'Ons Gümüş ($)',
  'PLATINUM:GRAM_TRY': 'Gram Platin (₺)',
  'PLATINUM:KG_TRY': 'Kg Platin (₺)',
  'PLATINUM:USD_ONS': 'Ons Platin ($)',
  'PLATINUM:EUR_ONS': 'Ons Platin (€)',
  'PALLADIUM:GRAM_TRY': 'Gram Paladyum (₺)',
  'PALLADIUM:KG_TRY': 'Kg Paladyum (₺)',
  'PALLADIUM:USD_ONS': 'Ons Paladyum ($)',
  'PALLADIUM:EUR_ONS': 'Ons Paladyum (€)',
};

function fmt(v, dec = 2) {
  if (v === null || v === undefined) return '-';
  const n = typeof v === 'string' ? parseFloat(v) : v;
  if (Number.isNaN(n)) return '-';
  return n.toLocaleString('tr-TR', { minimumFractionDigits: dec, maximumFractionDigits: dec });
}

function fmtPct(v) {
  if (v === null || v === undefined) return '-';
  const n = typeof v === 'string' ? parseFloat(v) : v;
  if (Number.isNaN(n)) return '-';
  return `${n >= 0 ? '+' : ''}${fmt(n, 2)}%`;
}

function deltaClass(v) {
  const n = parseFloat(v ?? NaN);
  if (Number.isNaN(n)) return 'text-gray-400';
  if (n > 0) return 'text-emerald-600 font-semibold';
  if (n < 0) return 'text-rose-600 font-semibold';
  return 'text-gray-500';
}

function formatAsOf(v) {
  if (!v) return '-';
  // backend LocalDateTime → "2026-05-10T12:34:56"
  if (typeof v === 'string') return v.replace('T', ' ').slice(0, 16);
  try { return new Date(v).toISOString().slice(0, 16).replace('T', ' '); } catch { return '-'; }
}

function getDisplayName(item) {
  if (!item) return { title: '-', subtitle: '' };
  const symbol = item.symbol ?? '';
  const type = item.assetType;

  if (type === 'COMMODITY') {
    const name = PRECIOUS_NAMES[symbol] || COMMODITY_NAMES[symbol];
    if (name) return { title: name, subtitle: symbol.includes(':') ? symbol.replace(':', ' · ') : symbol };
    return { title: symbol, subtitle: '' };
  }

  if (type === 'GOLD') {
    const name = GOLD_NAMES[symbol];
    if (name) return { title: name, subtitle: symbol };
    return { title: symbol, subtitle: '' };
  }

  if (type === 'FUND') {
    const fn = item.fundName?.trim();
    if (fn) return { title: fn, subtitle: symbol };
    return { title: symbol, subtitle: '' };
  }

  if (type === 'CRYPTO') {
    const s = symbol.trim();
    return { title: s ? s.toUpperCase() : '-', subtitle: '' };
  }

  return { title: symbol, subtitle: '' };
}

/** Varlık adı / sembol → detay sayfası linki (resolveDetailPath ile) */
function DetailLink({ item, resolveDetailPath, className, children }) {
  const to = typeof resolveDetailPath === 'function' ? resolveDetailPath(item) : null;
  if (!to) {
    return <span className={className}>{children}</span>;
  }
  return (
    <Link to={to} className={`${className} hover:underline`} onClick={e => e.stopPropagation()}>
      {children}
    </Link>
  );
}

/**
 * İzleme listesi tablosu.
 * Props:
 *   items: WatchlistItemResponse[]
 *   onDelete(itemId): void
 *   deletingId: string | null
 *   resolveDetailPath(item): string | null — verilirse sembolden detaya gidilir
 */
export default function WatchlistTable({
  variant = 'default',
  items = [],
  onDelete,
  deletingId,
  onAddToPortfolio,
  resolveDetailPath,
}) {
  if (!items.length) {
    return (
      <div className="p-12 text-center text-gray-400 text-sm">
        İzleme listesi boş. "Sembol Ekle" butonuna basarak başlayın.
      </div>
    );
  }

  return (
    <div className="px-3 pb-2 overflow-x-auto">
      <table className={`w-full ${variant === 'fund' ? 'min-w-[1000px]' : 'table-fixed'}`}>
        <thead className="bg-gray-50">
          <tr>
            {(variant === 'bond'
              ? ['Sembol', 'Değer', 'Eklendiği Fiyat', 'Günlük Fark', 'Günlük Fark %', 'Kalan Gün', 'Kupon %', 'Not', 'Eklenme Tarihi', 'İşlem']
              : variant === 'fx'
                ? ['Sembol', 'Alış', 'Satış', 'Eklendiği Fiyat', 'Not', 'Eklenme Tarihi', 'İşlem']
                : variant === 'fund'
                  ? ['Fon Kodu', 'Fon Adı', 'Son Fiyat', 'Günlük Getiri', '1 Ay', '3 Ay', 'YBG', '1 Yıl', 'Risk', 'Eklenme Tarihi', 'İşlem']
                  : ['Sembol', 'Son Fiyat', 'Eklendiği Fiyat', 'Açılış', 'Yüksek', 'Düşük', 'Fark', 'Fark %', 'Hacim', 'Not', 'Eklenme Tarihi', 'İşlem']
            ).map(h => (
              <th
                key={h}
                className={`text-left px-2 py-2 text-[10px] font-bold text-gray-500 uppercase tracking-wider border-b border-gray-200 ${
                  h === 'Eklenme Tarihi' ? 'w-[110px]' : ''
                } ${h === 'İşlem' ? 'w-[150px]' : ''} ${variant === 'fund' && h === 'Fon Adı' ? 'min-w-[140px]' : ''}`}
              >
                {h}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {items.map((item, i) => (
            <tr key={item.id ?? i} className="border-t border-gray-100 hover:bg-gray-50 transition-colors">
              {variant === 'fund' ? (
                <>
                  <td className="px-2 py-2.5 font-bold text-[#093eaa] text-xs whitespace-nowrap">
                    <DetailLink item={item} resolveDetailPath={resolveDetailPath} className="font-bold text-[#093eaa] text-xs">
                      {item.symbol}
                    </DetailLink>
                  </td>
                  <td
                    className="px-2 py-2.5 text-xs text-gray-800 max-w-[200px] truncate"
                    title={item.fundName || ''}
                  >
                    <DetailLink item={item} resolveDetailPath={resolveDetailPath} className="text-xs text-gray-800">
                      {item.fundName?.trim() || '-'}
                    </DetailLink>
                  </td>
                  <td className="px-2 py-2.5 text-xs font-semibold whitespace-nowrap tabular-nums">{fmt(item.lastPrice)}</td>
                  <td className={`px-2 py-2.5 text-xs whitespace-nowrap ${deltaClass(item.changePercent)}`}>{fmtPct(item.changePercent)}</td>
                  <td className={`px-2 py-2.5 text-xs whitespace-nowrap ${deltaClass(item.fundReturnOneMonth)}`}>{fmtPct(item.fundReturnOneMonth)}</td>
                  <td className={`px-2 py-2.5 text-xs whitespace-nowrap ${deltaClass(item.fundReturnThreeMonths)}`}>{fmtPct(item.fundReturnThreeMonths)}</td>
                  <td className={`px-2 py-2.5 text-xs whitespace-nowrap ${deltaClass(item.fundReturnYtd)}`}>{fmtPct(item.fundReturnYtd)}</td>
                  <td className={`px-2 py-2.5 text-xs whitespace-nowrap ${deltaClass(item.fundReturnOneYear)}`}>{fmtPct(item.fundReturnOneYear)}</td>
                  <td className="px-2 py-2.5 text-xs text-gray-600 text-center">{item.fundRiskLevel != null ? item.fundRiskLevel : '-'}</td>
                  <td className="px-2 py-2.5 text-xs text-gray-400 whitespace-nowrap">{formatAsOf(item.addedAt)}</td>
                </>
              ) : (
                <td className="px-2 py-2.5">
                  <div className="flex flex-col">
                    <DetailLink item={item} resolveDetailPath={resolveDetailPath} className="font-bold text-[#093eaa] text-xs">
                      {getDisplayName(item).title}
                    </DetailLink>
                    {getDisplayName(item).subtitle && (
                      <span className="text-xs text-gray-400">{getDisplayName(item).subtitle}</span>
                    )}
                  </div>
                </td>
              )}

              {variant === 'fund' ? null : variant === 'fx' ? (
                <>
                  <td className="px-2 py-2.5 text-xs font-semibold text-emerald-700">{fmt(item.buy)}</td>
                  <td className="px-2 py-2.5 text-xs font-semibold text-rose-700">{fmt(item.sell)}</td>
                  <td className="px-2 py-2.5 text-xs text-gray-600">{fmt(item.startPrice)}</td>
                  <td className="px-2 py-2.5 text-xs text-gray-600 whitespace-nowrap max-w-[220px] overflow-hidden text-ellipsis" title={item.notes || ''}>{item.notes?.trim() || '-'}</td>
                  <td className="px-2 py-2.5 text-xs text-gray-400 whitespace-nowrap">{formatAsOf(item.addedAt)}</td>
                </>
              ) : variant === 'bond' ? (
                <>
                  <td className="px-2 py-2.5 text-xs font-semibold">{fmt(item.lastPrice)}</td>
                  <td className="px-2 py-2.5 text-xs text-gray-600">{fmt(item.startPrice)}</td>
                  <td className={`px-2 py-2.5 text-xs ${deltaClass(item.change)}`}>{fmt(item.change)}</td>
                  <td className={`px-2 py-2.5 text-xs ${deltaClass(item.changePercent)}`}>{fmtPct(item.changePercent)}</td>
                  <td className="px-2 py-2.5 text-xs text-gray-500">{item.remainingDays ?? '-'}</td>
                  <td className="px-2 py-2.5 text-xs text-gray-500">{fmt(item.couponRate)}</td>
                  <td className="px-2 py-2.5 text-xs text-gray-600 whitespace-nowrap max-w-[220px] overflow-hidden text-ellipsis" title={item.notes || ''}>{item.notes?.trim() || '-'}</td>
                  <td className="px-2 py-2.5 text-xs text-gray-400 whitespace-nowrap">{formatAsOf(item.addedAt)}</td>
                </>
              ) : (
                <>
                  <td className="px-2 py-2.5 text-xs font-semibold">{fmt(item.lastPrice)}</td>
                  <td className="px-2 py-2.5 text-xs text-gray-600">{fmt(item.startPrice)}</td>
                  <td className="px-2 py-2.5 text-xs text-gray-500">{fmt(item.open)}</td>
                  <td className="px-2 py-2.5 text-xs text-gray-500">{fmt(item.high)}</td>
                  <td className="px-2 py-2.5 text-xs text-gray-500">{fmt(item.low)}</td>
                  <td className={`px-2 py-2.5 text-xs ${deltaClass(item.change)}`}>{fmt(item.change)}</td>
                  <td className={`px-2 py-2.5 text-xs ${deltaClass(item.changePercent)}`}>{fmtPct(item.changePercent)}</td>
                  <td className="px-2 py-2.5 text-xs text-gray-500">{item.volume ?? '-'}</td>
                  <td className="px-2 py-2.5 text-xs text-gray-600 whitespace-nowrap max-w-[220px] overflow-hidden text-ellipsis" title={item.notes || ''}>{item.notes?.trim() || '-'}</td>
                  <td className="px-2 py-2.5 text-xs text-gray-400 whitespace-nowrap">{formatAsOf(item.addedAt)}</td>
                </>
              )}
              <td className="px-2 py-2.5 border-l border-gray-100">
                <div className="flex items-center gap-1 whitespace-nowrap">
                  {onAddToPortfolio && (
                    <button
                      onClick={() => onAddToPortfolio(item, getDisplayName(item).title)}
                      className="h-7 px-2 rounded-md border border-gray-200 bg-white text-[#093eaa] hover:bg-gray-50 transition-colors text-[11px] font-semibold whitespace-nowrap"
                    >
                      Varlıklarıma Ekle
                    </button>
                  )}
                  {onDelete && (
                    <button
                      onClick={() => onDelete(item.id)}
                      disabled={deletingId === item.id}
                      className="h-7 w-7 inline-flex items-center justify-center rounded-md border border-gray-200 text-gray-400 hover:text-rose-500 hover:border-rose-200 hover:bg-rose-50 transition-colors disabled:opacity-40"
                      title="Listeden çıkar"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  )}
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
