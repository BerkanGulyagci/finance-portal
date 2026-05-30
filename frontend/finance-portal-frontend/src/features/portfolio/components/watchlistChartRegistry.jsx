import { useNavigate } from 'react-router-dom';
import {
  ResponsiveContainer, PieChart, Pie, Cell, BarChart, Bar,
  XAxis, YAxis, CartesianGrid, Tooltip, Legend, ReferenceLine,
} from 'recharts';
import { useTranslation } from '../../../context/LanguageContext';
import {
  WATCHLIST_ASSET_LABELS, groupByAssetType, calculateDailyStatus, calculateAverageChangeByType,
  getTopGainers, getTopLosers, formatPctForChart, formatSharePercent, countSharePercent,
  formatPctAxis, formatWatchlistTablePrice, formatWatchlistSymbolDisplay,
  getWatchlistChartDisplayTitle, getWatchlistTopListSubtitle,
} from '../utils/watchlistChartHelpers';

export const DONUT_COLORS = ['#0ea5e9', '#8b5cf6', '#10b981', '#f59e0b', '#eab308', '#64748b', '#06b6d4', '#f43f5e'];

function assetTypeBadgeClass(t) {
  switch (t) {
    case 'STOCK': return 'bg-sky-100 text-sky-900';
    case 'CRYPTO': return 'bg-violet-100 text-violet-900';
    case 'FUND': return 'bg-indigo-100 text-indigo-900';
    case 'COMMODITY': return 'bg-amber-100 text-amber-950';
    case 'GOLD': return 'bg-yellow-100 text-yellow-950';
    case 'FX': return 'bg-teal-100 text-teal-900';
    case 'BOND': return 'bg-slate-100 text-slate-800';
    case 'FUTURE': return 'bg-orange-100 text-orange-950';
    default: return 'bg-gray-100 text-gray-800';
  }
}

function ChartTooltip({ active, payload, label }) {
  const { t } = useTranslation();
  if (!active || !payload?.length) return null;
  const p = payload[0];
  const v = p?.value;
  const name = label ?? p?.name ?? p?.payload?.name;
  const raw = p?.payload;
  if (raw?.sharePct != null && typeof v === 'number' && p.dataKey === 'value') {
    return (
      <div className="rounded-lg border border-gray-200 bg-white px-3 py-2 text-xs shadow-md">
        <div className="font-semibold text-gray-800">{name}</div>
        <div className="text-gray-600">{t('Adet:')} {v.toLocaleString('tr-TR')} · {t('Oran:')} {formatSharePercent(raw.sharePct)}</div>
      </div>
    );
  }
  return (
    <div className="rounded-lg border border-gray-200 bg-white px-3 py-2 text-xs shadow-md">
      <div className="font-semibold text-gray-800">{name}</div>
      <div className="text-gray-600">
        {typeof v === 'number' && p.dataKey === 'avg' ? formatPctForChart(v)
          : typeof v === 'number' ? v.toLocaleString('tr-TR') : String(v ?? '')}
      </div>
    </div>
  );
}

/**
 * Ortak kart kabuğu: başlık + (opsiyonel) kaynak etiketi + (opsiyonel) aksiyon; gövde kart içinde kayar.
 * Hem izleme "Grafikler" sekmesi hem de Dashboard aynı kabuğu kullanır.
 */
export function ChartCard({ title, subtitle, source, action, children }) {
  return (
    <div className="h-full rounded-2xl border border-gray-200 bg-white shadow-sm flex flex-col overflow-hidden">
      <div className="px-4 pt-3.5 shrink-0 flex items-start justify-between gap-2">
        <div className="min-w-0">
          <h3 className="text-base font-bold text-gray-900 truncate">{title}</h3>
          {subtitle ? <p className="mt-0.5 text-xs text-gray-500">{subtitle}</p> : null}
          {source ? (
            <span className="mt-1 inline-flex items-center gap-1 text-[11px] font-semibold text-[#093eaa] bg-[#093eaa]/5 px-2 py-0.5 rounded-full">
              {source}
            </span>
          ) : null}
        </div>
        {action ? <div className="shrink-0">{action}</div> : null}
      </div>
      <div className="flex-1 min-h-0 overflow-auto px-4 pb-4 pt-3">{children}</div>
    </div>
  );
}

function MiniTableBody({ rows, resolveDetailPath }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const pctClass = n => (n == null || Number.isNaN(n)) ? 'text-gray-400' : n > 0 ? 'text-emerald-600' : n < 0 ? 'text-rose-600' : 'text-gray-500';
  if (!rows?.length) return <p className="mt-4 text-center text-sm text-gray-400">{t('Veri yok')}</p>;
  return (
    <table className="w-full text-sm">
      <thead>
        <tr className="border-b border-gray-100 text-left text-xs font-semibold text-gray-500">
          <th className="py-2 pr-3 min-w-[140px]">{t('Varlık')}</th>
          <th className="py-2 pr-2 text-right">{t('Fiyat')}</th>
          <th className="py-2 text-right">{t('Fark %')}</th>
        </tr>
      </thead>
      <tbody>
        {rows.map((row, idx) => {
          const item = row.item;
          const to = typeof resolveDetailPath === 'function' ? resolveDetailPath(item) : null;
          const go = () => { if (to) navigate(to); };
          const typeLabel = WATCHLIST_ASSET_LABELS[item?.assetType] ? t(WATCHLIST_ASSET_LABELS[item?.assetType]) : (item?.assetType ?? '—');
          return (
            <tr key={item?.id ?? `${formatWatchlistSymbolDisplay(item)}-${idx}`}
              onClick={go}
              className={`border-b border-gray-50 last:border-0 ${to ? 'cursor-pointer hover:bg-sky-50/60' : ''}`}>
              <td className="py-2 pr-3 align-top">
                <div className="flex min-w-0 max-w-[220px] sm:max-w-none flex-col gap-1">
                  <span className={`inline-flex w-fit shrink-0 rounded-md px-1.5 py-0.5 text-[10px] font-semibold leading-none ${assetTypeBadgeClass(item?.assetType)}`}>{typeLabel}</span>
                  <span className="text-xs font-semibold leading-snug text-[#093eaa] break-words">{getWatchlistChartDisplayTitle(item)}</span>
                  {getWatchlistTopListSubtitle(item) ? <span className="font-mono text-[10px] leading-tight text-gray-400">{getWatchlistTopListSubtitle(item)}</span> : null}
                </div>
              </td>
              <td className="py-2 pr-2 text-right tabular-nums text-gray-800">{formatWatchlistTablePrice(item)}</td>
              <td className={`py-2 text-right font-semibold tabular-nums ${pctClass(row.changePercent)}`}>{formatPctForChart(row.changePercent)}</td>
            </tr>
          );
        })}
      </tbody>
    </table>
  );
}

// ── Grafik bileşenleri (her biri watchlist items'tan hesaplar) ────────────────
function WlDistribution({ items, source, action }) {
  const { t } = useTranslation();
  const status = calculateDailyStatus(items);
  const grouped = groupByAssetType(items);
  const rows = Object.entries(grouped).map(([type, list]) => ({
    name: WATCHLIST_ASSET_LABELS[type] ? t(WATCHLIST_ASSET_LABELS[type]) : type,
    type, value: list.length, sharePct: countSharePercent(list.length, status.total),
  })).sort((a, b) => a.name.localeCompare(b.name, 'tr'));
  return (
    <ChartCard title={t('Varlık Türü Dağılımı')} subtitle={t('Dağılım takip edilen sembol adedine göre hesaplanır.')} source={source} action={action}>
      <div className="h-[260px] w-full min-w-0">
        <ResponsiveContainer width="100%" height="100%">
          <PieChart>
            <Pie data={rows} dataKey="value" nameKey="name" cx="50%" cy="50%" innerRadius={52} outerRadius={92} paddingAngle={2}
              label={({ name, percent }) => `${name} ${formatSharePercent(percent * 100)}`}>
              {rows.map((e, i) => <Cell key={e.type} fill={DONUT_COLORS[i % DONUT_COLORS.length]} stroke="#fff" />)}
            </Pie>
            <Tooltip content={<ChartTooltip />} />
            <Legend wrapperStyle={{ fontSize: 12 }} />
          </PieChart>
        </ResponsiveContainer>
      </div>
      <div className="mt-4 overflow-x-auto rounded-lg border border-gray-100">
        <table className="w-full min-w-[280px] text-sm">
          <thead>
            <tr className="border-b border-gray-200 bg-gray-50 text-left text-xs font-semibold text-gray-600">
              <th className="px-3 py-2">{t('Kategori')}</th><th className="px-3 py-2 text-right">{t('Adet')}</th><th className="px-3 py-2 text-right">{t('Oran')}</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row, i) => (
              <tr key={row.type} className="border-b border-gray-50 last:border-0">
                <td className="px-3 py-2 font-medium text-gray-900">
                  <span className="inline-flex items-center gap-2"><span className="inline-block h-2.5 w-2.5 shrink-0 rounded-sm" style={{ backgroundColor: DONUT_COLORS[i % DONUT_COLORS.length] }} />{row.name}</span>
                </td>
                <td className="px-3 py-2 text-right tabular-nums text-gray-800">{row.value.toLocaleString('tr-TR')}</td>
                <td className="px-3 py-2 text-right tabular-nums font-medium text-gray-800">{row.sharePct != null ? formatSharePercent(row.sharePct) : '-'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </ChartCard>
  );
}

function WlDailyStatus({ items, source, action }) {
  const { t } = useTranslation();
  const status = calculateDailyStatus(items);
  const data = [
    { name: t('Yükselen'), count: status.up, fill: '#10b981' },
    { name: t('Düşen'), count: status.down, fill: '#f43f5e' },
    { name: t('Yatay'), count: status.flat, fill: '#94a3b8' },
    { name: t('Veri Yok'), count: status.nodata, fill: '#fbbf24' },
  ];
  return (
    <ChartCard title={t('Günlük Durum')} subtitle={t('Yükselen / düşen / yatay / veri yok adetleri')} source={source} action={action}>
      <div className="h-[240px] w-full min-w-0">
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={data} margin={{ top: 8, right: 8, left: 0, bottom: 8 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="#e5e7eb" />
            <XAxis dataKey="name" tick={{ fontSize: 11 }} /><YAxis allowDecimals={false} tick={{ fontSize: 11 }} />
            <Tooltip content={<ChartTooltip />} />
            <Bar dataKey="count" radius={[6, 6, 0, 0]}>{data.map(e => <Cell key={e.name} fill={e.fill} />)}</Bar>
          </BarChart>
        </ResponsiveContainer>
      </div>
    </ChartCard>
  );
}

function WlAvgByType({ items, source, action }) {
  const { t } = useTranslation();
  const avg = calculateAverageChangeByType(items);
  return (
    <ChartCard title={t('Kategori Bazlı Ortalama Değişim')} subtitle={t('Her varlık türü için ortalama günlük değişim; günlük % verisi olmayan enstrümanlar ve boş kategoriler dahil edilmez.')} source={source} action={action}>
      {!avg.length ? <p className="mt-8 text-center text-sm text-gray-500">{t('Yeterli fiyat verisi yok')}</p> : (
        <div className="h-[240px] w-full min-w-0">
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={avg.map(e => ({ ...e, label: t(e.label) }))} margin={{ top: 8, right: 8, left: 0, bottom: 8 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="#e5e7eb" />
              <XAxis dataKey="label" tick={{ fontSize: 11 }} interval={0} angle={-18} textAnchor="end" height={56} />
              <YAxis tick={{ fontSize: 11 }} tickFormatter={formatPctAxis} />
              <ReferenceLine y={0} stroke="#cbd5e1" /><Tooltip content={<ChartTooltip />} />
              <Bar dataKey="avg" name={t('Ortalama %')} radius={[6, 6, 0, 0]}>{avg.map(e => <Cell key={e.type} fill={e.avg >= 0 ? '#10b981' : '#f43f5e'} />)}</Bar>
            </BarChart>
          </ResponsiveContainer>
        </div>
      )}
    </ChartCard>
  );
}

function WlGainers({ items, resolveDetailPath, source, action }) {
  const { t } = useTranslation();
  return (
    <ChartCard title={t('En Çok Yükselenler')} source={source} action={action}>
      <MiniTableBody rows={getTopGainers(items, 5)} resolveDetailPath={resolveDetailPath} />
    </ChartCard>
  );
}

function WlLosers({ items, resolveDetailPath, source, action }) {
  const { t } = useTranslation();
  return (
    <ChartCard title={t('En Çok Düşenler')} source={source} action={action}>
      <MiniTableBody rows={getTopLosers(items, 5)} resolveDetailPath={resolveDetailPath} />
    </ChartCard>
  );
}

/** İzleme listesi grafik kaydı — hem "Grafikler" sekmesi hem dashboard kullanır. perf: günlük % verisi gerektirir. */
export const WL_CHARTS = [
  { key: 'distribution', label: 'Varlık Türü Dağılımı', Comp: WlDistribution, w: 6, h: 16, perf: false },
  { key: 'dailyStatus', label: 'Günlük Durum', Comp: WlDailyStatus, w: 6, h: 10, perf: true },
  { key: 'avgByType', label: 'Kategori Bazlı Ortalama Değişim', Comp: WlAvgByType, w: 6, h: 10, perf: true },
  { key: 'gainers', label: 'En Çok Yükselenler', Comp: WlGainers, w: 6, h: 10, perf: true },
  { key: 'losers', label: 'En Çok Düşenler', Comp: WlLosers, w: 6, h: 10, perf: true },
];

export const WL_CHART_BY_KEY = Object.fromEntries(WL_CHARTS.map(c => [c.key, c]));
