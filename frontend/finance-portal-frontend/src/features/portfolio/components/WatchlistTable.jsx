import { Link } from 'react-router-dom';
import { Trash2 } from 'lucide-react';
import { useTranslation } from '../../../context/LanguageContext';

const COMMODITY_NAMES = {
  'CL=F': 'WTI Ham Petrol',
  'BZ=F': 'Brent Ham Petrol',
  'NG=F': 'Doğal Gaz',
  'HG=F': 'Bakır',
  'ZW=F': 'Buğday',
  'ZC=F': 'Mısır',
  'KC=F': 'Kahve',
  'CC=F': 'Kakao',
  'CT=F': 'Pamuk',
};

const GOLD_NAMES = {
  GRAM: 'Gram Altın',
  CEYREK: 'Çeyrek Altın',
  YARIM: 'Yarım Altın',
  ATA: 'Ata Lira (Cumhuriyet)',
  CUMHUR: 'Ata Lira (Cumhuriyet)',
  TAM: 'Tam Altın (Ziynet)',
  ZIYNET: 'Tam Altın (Ziynet)',
  GOLD: 'Ons Altın',
  '14AYAR': '14 Ayar Bilezik',
  '22AYAR': '22 Ayar Bilezik',
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

function signed(v, dec = 2) {
  if (v === null || v === undefined) return '-';
  const n = typeof v === 'string' ? parseFloat(v) : v;
  if (Number.isNaN(n)) return '-';
  return `${n >= 0 ? '+' : ''}${fmt(n, dec)}`;
}

function fmtPct(v) {
  if (v === null || v === undefined) return '-';
  const n = typeof v === 'string' ? parseFloat(v) : v;
  if (Number.isNaN(n)) return '-';
  return `${n >= 0 ? '+' : ''}${fmt(n, 2)}%`;
}

function fmtVolume(v) {
  if (v === null || v === undefined) return '-';
  const n = typeof v === 'string' ? parseFloat(v) : v;
  if (Number.isNaN(n)) return '-';
  return Math.round(n).toLocaleString('tr-TR');
}

function formatAsOf(v) {
  if (!v) return '-';
  if (typeof v === 'string') return v.replace('T', ' ').slice(0, 16);
  try { return new Date(v).toISOString().slice(0, 16).replace('T', ' '); } catch { return '-'; }
}

function deltaClass(v) {
  const n = parseFloat(v ?? NaN);
  if (Number.isNaN(n)) return 'text-gray-400';
  if (n > 0) return 'text-emerald-600 font-semibold';
  if (n < 0) return 'text-rose-600 font-semibold';
  return 'text-gray-500';
}

/** Fark % hücresi: pozitif/negatif renk + hafif zemin (mockup gibi). */
function pctChip(v) {
  const n = parseFloat(v ?? NaN);
  const base = 'inline-block px-2 py-0.5 rounded-md tabular-nums font-bold';
  if (Number.isNaN(n)) return <span className="text-gray-400 tabular-nums">-</span>;
  if (n > 0) return <span className={`${base} text-emerald-700 bg-emerald-50`}>{fmtPct(n)}</span>;
  if (n < 0) return <span className={`${base} text-rose-700 bg-rose-50`}>{fmtPct(n)}</span>;
  return <span className="text-gray-500 tabular-nums">{fmtPct(n)}</span>;
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

/** thead başlık hücresi (büyük harf, sağa/sola/ortaya hizalı). */
function Th({ children, align = 'right' }) {
  const a = align === 'left' ? 'text-left' : align === 'center' ? 'text-center' : 'text-right';
  return (
    <th className={`px-5 py-3 text-[10px] font-bold text-gray-500 uppercase tracking-wider whitespace-nowrap ${a}`}>
      {children}
    </th>
  );
}

const TD = 'px-5 py-3.5 text-sm whitespace-nowrap';

/**
 * İzleme listesi tablosu (mockup tarzı: sağa hizalı sayılar, birleşik Yüksek/Düşük,
 * renkli Fark/Fark %, gruplu hacim, dış-çizgili "Varlıklarıma Ekle" butonu).
 */
export default function WatchlistTable({
  variant = 'default',
  items = [],
  onDelete,
  deletingId,
  onAddToPortfolio,
  resolveDetailPath,
}) {
  const { t } = useTranslation();
  if (!items.length) {
    return (
      <div className="p-10 text-center text-gray-400 text-sm">
        {t('İzleme listesi boş. "Sembol Ekle" butonuna basarak başlayın.')}
      </div>
    );
  }

  function actionCell(item) {
    return (
      <td className={`${TD} text-center`}>
        <div className="inline-flex items-center justify-center gap-1.5">
          {onAddToPortfolio && (
            <button
              onClick={() => onAddToPortfolio(item, getDisplayName(item).title)}
              className="text-[11px] font-semibold border border-[#093eaa]/40 text-[#093eaa] hover:bg-[#093eaa] hover:text-white px-3 py-1 rounded-md transition-colors"
            >
              {t('Varlıklarıma Ekle')}
            </button>
          )}
          {onDelete && (
            <button
              onClick={() => onDelete(item.id)}
              disabled={deletingId === item.id}
              className="h-7 w-7 inline-flex items-center justify-center rounded-md text-gray-400 hover:text-rose-500 hover:bg-rose-50 transition-colors disabled:opacity-40"
              title={t('Listeden çıkar')}
            >
              <Trash2 className="w-4 h-4" />
            </button>
          )}
        </div>
      </td>
    );
  }

  function symbolCell(item) {
    const { title, subtitle } = getDisplayName(item);
    return (
      <td className={`${TD} text-left`}>
        <DetailLink item={item} resolveDetailPath={resolveDetailPath} className="font-bold text-[#093eaa]">
          {title}
        </DetailLink>
        {subtitle && <div className="text-xs text-gray-400 font-normal">{subtitle}</div>}
      </td>
    );
  }

  function noteCell(item) {
    return (
      <td className="px-5 py-3.5 text-sm text-gray-600 text-left max-w-[200px] truncate" title={item.notes || ''}>
        {item.notes?.trim() || '-'}
      </td>
    );
  }

  function dateCell(item) {
    return <td className={`${TD} text-right text-gray-400`}>{formatAsOf(item.addedAt)}</td>;
  }

  return (
    <div className="overflow-x-auto">
      <table className={`w-full ${variant === 'fund' ? 'min-w-[900px]' : ''}`}>
        <thead className="bg-gray-50 border-b border-gray-200">
          <tr>
            {variant === 'fund' ? (
              <>
                <Th align="left">{t('Fon Kodu')}</Th>
                <Th align="left">{t('Fon Adı')}</Th>
                <Th>{t('Son Fiyat')}</Th>
                <Th>{t('Eklendiği Fiyat')}</Th>
                <Th>{t('Günlük')}</Th>
                <Th>{t('1 Ay')}</Th>
                <Th>{t('3 Ay')}</Th>
                <Th>{t('YBG')}</Th>
                <Th>{t('1 Yıl')}</Th>
                <Th align="center">{t('Risk')}</Th>
                <Th>{t('Eklenme Tarihi')}</Th>
                <Th align="center">{t('İşlem')}</Th>
              </>
            ) : variant === 'fx' ? (
              <>
                <Th align="left">{t('Sembol')}</Th>
                <Th>{t('Alış')}</Th>
                <Th>{t('Satış')}</Th>
                <Th>{t('Eklendiği Fiyat')}</Th>
                <Th align="left">{t('Not')}</Th>
                <Th>{t('Eklenme Tarihi')}</Th>
                <Th align="center">{t('İşlem')}</Th>
              </>
            ) : variant === 'bond' ? (
              <>
                <Th align="left">{t('Sembol')}</Th>
                <Th>{t('Değer')}</Th>
                <Th>{t('Eklendiği Fiyat')}</Th>
                <Th>{t('Günlük Fark')}</Th>
                <Th>{t('Günlük Fark %')}</Th>
                <Th>{t('Kalan Gün')}</Th>
                <Th>{t('Kupon %')}</Th>
                <Th align="left">{t('Not')}</Th>
                <Th>{t('Eklenme Tarihi')}</Th>
                <Th align="center">{t('İşlem')}</Th>
              </>
            ) : (
              <>
                <Th align="left">{t('Sembol')}</Th>
                <Th>{t('Son Fiyat')}</Th>
                <Th>{t('Eklendiği Fiyat')}</Th>
                <Th>{t('Açılış')}</Th>
                <Th>{t('Yüksek / Düşük')}</Th>
                <Th>{t('Fark')}</Th>
                <Th>{t('Fark %')}</Th>
                <Th>{t('Hacim')}</Th>
                <Th align="left">{t('Not')}</Th>
                <Th>{t('Eklenme Tarihi')}</Th>
                <Th align="center">{t('İşlem')}</Th>
              </>
            )}
          </tr>
        </thead>
        <tbody className="divide-y divide-gray-100">
          {items.map((item, i) => (
            <tr key={item.id ?? i} className="hover:bg-gray-50 transition-colors">
              {variant === 'fund' ? (
                <>
                  <td className={`${TD} text-left`}>
                    <DetailLink item={item} resolveDetailPath={resolveDetailPath} className="font-bold text-[#093eaa]">
                      {item.symbol}
                    </DetailLink>
                  </td>
                  <td className="px-5 py-3.5 text-sm text-gray-800 max-w-[220px] truncate" title={item.fundName || ''}>
                    <DetailLink item={item} resolveDetailPath={resolveDetailPath} className="text-gray-800">
                      {item.fundName?.trim() || '-'}
                    </DetailLink>
                  </td>
                  <td className={`${TD} text-right font-semibold tabular-nums`}>{fmt(item.lastPrice)}</td>
                  <td className={`${TD} text-right text-gray-500 tabular-nums`}>{fmt(item.startPrice)}</td>
                  <td className={`${TD} text-right tabular-nums ${deltaClass(item.changePercent)}`}>{fmtPct(item.changePercent)}</td>
                  <td className={`${TD} text-right tabular-nums ${deltaClass(item.fundReturnOneMonth)}`}>{fmtPct(item.fundReturnOneMonth)}</td>
                  <td className={`${TD} text-right tabular-nums ${deltaClass(item.fundReturnThreeMonths)}`}>{fmtPct(item.fundReturnThreeMonths)}</td>
                  <td className={`${TD} text-right tabular-nums ${deltaClass(item.fundReturnYtd)}`}>{fmtPct(item.fundReturnYtd)}</td>
                  <td className={`${TD} text-right tabular-nums ${deltaClass(item.fundReturnOneYear)}`}>{fmtPct(item.fundReturnOneYear)}</td>
                  <td className={`${TD} text-center text-gray-600`}>{item.fundRiskLevel != null ? item.fundRiskLevel : '-'}</td>
                  {dateCell(item)}
                  {actionCell(item)}
                </>
              ) : variant === 'fx' ? (
                <>
                  {symbolCell(item)}
                  <td className={`${TD} text-right font-semibold text-emerald-700 tabular-nums`}>{fmt(item.buy)}</td>
                  <td className={`${TD} text-right font-semibold text-rose-700 tabular-nums`}>{fmt(item.sell)}</td>
                  <td className={`${TD} text-right text-gray-500 tabular-nums`}>{fmt(item.startPrice)}</td>
                  {noteCell(item)}
                  {dateCell(item)}
                  {actionCell(item)}
                </>
              ) : variant === 'bond' ? (
                <>
                  {symbolCell(item)}
                  <td className={`${TD} text-right font-semibold tabular-nums`}>{fmt(item.lastPrice)}</td>
                  <td className={`${TD} text-right text-gray-500 tabular-nums`}>{fmt(item.startPrice)}</td>
                  <td className={`${TD} text-right tabular-nums ${deltaClass(item.change)}`}>{signed(item.change)}</td>
                  <td className={`${TD} text-right`}>{pctChip(item.changePercent)}</td>
                  <td className={`${TD} text-right text-gray-500 tabular-nums`}>{item.remainingDays ?? '-'}</td>
                  <td className={`${TD} text-right text-gray-500 tabular-nums`}>{fmt(item.couponRate)}</td>
                  {noteCell(item)}
                  {dateCell(item)}
                  {actionCell(item)}
                </>
              ) : (
                <>
                  {symbolCell(item)}
                  <td className={`${TD} text-right font-semibold tabular-nums`}>{fmt(item.lastPrice)}</td>
                  <td className={`${TD} text-right text-gray-500 tabular-nums`}>{fmt(item.startPrice)}</td>
                  <td className={`${TD} text-right text-gray-500 tabular-nums`}>{fmt(item.open)}</td>
                  <td className={`${TD} text-right text-gray-500 tabular-nums`}>{fmt(item.high)} / {fmt(item.low)}</td>
                  <td className={`${TD} text-right tabular-nums ${deltaClass(item.change)}`}>{signed(item.change)}</td>
                  <td className={`${TD} text-right`}>{pctChip(item.changePercent)}</td>
                  <td className={`${TD} text-right text-gray-500 tabular-nums`}>{fmtVolume(item.volume)}</td>
                  {noteCell(item)}
                  {dateCell(item)}
                  {actionCell(item)}
                </>
              )}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
