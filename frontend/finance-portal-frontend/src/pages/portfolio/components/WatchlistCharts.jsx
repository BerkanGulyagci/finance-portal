import { useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import AnalysisPdfExportButton from './AnalysisPdfExportButton';
import { useTranslation } from '../../../i18n/LanguageContext';
import {
  ResponsiveContainer,
  PieChart,
  Pie,
  Cell,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ReferenceLine,
} from 'recharts';
import {
  WATCHLIST_ASSET_LABELS,
  groupByAssetType,
  calculateDailyStatus,
  calculateAverageChangeByType,
  getTopGainers,
  getTopLosers,
  hasAnyChangePercentData,
  formatPctForChart,
  formatSharePercent,
  countSharePercent,
  formatPctAxis,
  formatWatchlistTablePrice,
  formatWatchlistSymbolDisplay,
  getWatchlistChartDisplayTitle,
  getWatchlistTopListSubtitle,
} from '../utils/watchlistChartHelpers';


function assetTypeBadgeClass(t) {
  switch (t) {
    case 'STOCK':
      return 'bg-sky-100 text-sky-900';
    case 'CRYPTO':
      return 'bg-violet-100 text-violet-900';
    case 'FUND':
      return 'bg-indigo-100 text-indigo-900';
    case 'COMMODITY':
      return 'bg-amber-100 text-amber-950';
    case 'GOLD':
      return 'bg-yellow-100 text-yellow-950';
    case 'FX':
      return 'bg-teal-100 text-teal-900';
    case 'BOND':
      return 'bg-slate-100 text-slate-800';
    case 'FUTURE':
      return 'bg-orange-100 text-orange-950';
    default:
      return 'bg-gray-100 text-gray-800';
  }
}


const DONUT_COLORS = [
  '#0ea5e9',
  '#8b5cf6',
  '#10b981',
  '#f59e0b',
  '#eab308',
  '#64748b',
  '#06b6d4',
  '#f43f5e',
];

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
        <div className="text-gray-600">
          {t('Adet:')} {v.toLocaleString('tr-TR')} · {t('Oran:')} {formatSharePercent(raw.sharePct)}
        </div>
      </div>
    );
  }

  return (
    <div className="rounded-lg border border-gray-200 bg-white px-3 py-2 text-xs shadow-md">
      <div className="font-semibold text-gray-800">{name}</div>
      <div className="text-gray-600">
        {typeof v === 'number' && p.dataKey === 'avg'
          ? formatPctForChart(v)
          : typeof v === 'number'
            ? v.toLocaleString('tr-TR')
            : String(v ?? '')}
      </div>
    </div>
  );
}

function SummaryCard({ label, value, tone, hint }) {
  const { t } = useTranslation();
  const toneCls =
    tone === 'up'
      ? 'border-emerald-200 bg-emerald-50/60'
      : tone === 'down'
        ? 'border-rose-200 bg-rose-50/60'
        : tone === 'flat'
          ? 'border-gray-200 bg-gray-50/80'
          : tone === 'muted'
            ? 'border-amber-200 bg-amber-50/50'
            : 'border-sky-200 bg-sky-50/50';
  return (
    <div className={`rounded-xl border p-4 ${toneCls}`}>
      <div className="flex items-start gap-1">
        <span className="text-xs font-semibold text-gray-500">{label}</span>
        {hint ? (
          <span className="group relative inline-flex shrink-0">
            <span
              className="inline-flex h-4 w-4 cursor-help items-center justify-center rounded-full border border-amber-300 bg-amber-100 text-[10px] font-bold text-amber-800 outline-none ring-sky-400 transition hover:bg-amber-200 focus-visible:ring-2"
              tabIndex={0}
              aria-label={t('Açıklama')}
            >
              ?
            </span>
            <span
              role="tooltip"
              className="pointer-events-none invisible absolute left-1/2 top-full z-[60] mt-2 w-max max-w-[min(240px,calc(100vw-2rem))] -translate-x-1/2 rounded-lg border border-gray-200 bg-white px-3 py-2 text-left text-[11px] leading-snug text-gray-700 shadow-lg opacity-0 ring-1 ring-black/5 transition duration-150 ease-out group-hover:visible group-hover:opacity-100 group-focus-within:visible group-focus-within:opacity-100"
            >
              {hint}
            </span>
          </span>
        ) : null}
      </div>
      <div className="mt-1 text-xl sm:text-2xl font-bold tabular-nums leading-snug text-gray-900 break-words">
        {value}
      </div>
    </div>
  );
}

function MiniTable({ title, rows, valueClass, resolveDetailPath }) {
  const { t } = useTranslation();
  const navigate = useNavigate();

  return (
    <div className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm">
      <h4 className="text-sm font-bold text-gray-900">{title}</h4>
      {!rows?.length ? (
        <p className="mt-4 text-center text-sm text-gray-400">{t('Veri yok')}</p>
      ) : (
        <div className="mt-3 overflow-x-auto">
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
                const go = () => {
                  if (to) navigate(to);
                };
                const typeLabel = WATCHLIST_ASSET_LABELS[item?.assetType]
                  ? t(WATCHLIST_ASSET_LABELS[item?.assetType])
                  : (item?.assetType ?? '—');
                const primaryName = getWatchlistChartDisplayTitle(item);
                const sub = getWatchlistTopListSubtitle(item);
                return (
                  <tr
                    key={item?.id ?? `${formatWatchlistSymbolDisplay(item)}-${idx}`}
                    role={to ? 'link' : undefined}
                    tabIndex={to ? 0 : undefined}
                    onClick={go}
                    onKeyDown={e => {
                      if (!to) return;
                      if (e.key === 'Enter' || e.key === ' ') {
                        e.preventDefault();
                        go();
                      }
                    }}
                    title={to ? t('Detaya git') : undefined}
                    className={`border-b border-gray-50 last:border-0 transition-colors ${
                      to ? 'cursor-pointer hover:bg-sky-50/60' : ''
                    }`}
                  >
                    <td className="py-2 pr-3 align-top">
                      <div className="flex min-w-0 max-w-[220px] sm:max-w-none flex-col gap-1">
                        <span
                          className={`inline-flex w-fit shrink-0 rounded-md px-1.5 py-0.5 text-[10px] font-semibold leading-none ${assetTypeBadgeClass(item?.assetType)}`}
                        >
                          {typeLabel}
                        </span>
                        <span className="text-xs font-semibold leading-snug text-[#093eaa] break-words">
                          {primaryName}
                        </span>
                        {sub ? (
                          <span className="font-mono text-[10px] leading-tight text-gray-400">{sub}</span>
                        ) : null}
                      </div>
                    </td>
                    <td className="py-2 pr-2 text-right tabular-nums text-gray-800">
                      {formatWatchlistTablePrice(item)}
                    </td>
                    <td className={`py-2 text-right font-semibold tabular-nums ${valueClass(row.changePercent)}`}>
                      {formatPctForChart(row.changePercent)}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

/**
 * İzleme listesi — miktar / maliyet yok; dağılım adet + günlük % (changePercent).
 * @param {(item: object) => string | null} [resolveDetailPath]
 * @param {string} [portfolioName] — PDF raporunda gösterilen izleme listesi adı
 */
export default function WatchlistCharts({ items = [], loading = false, resolveDetailPath, portfolioName = '' }) {
  const { t } = useTranslation();
  if (loading) {
    return (
      <div className="p-8 flex justify-center gap-1.5">
        <div className="w-2 h-2 bg-sky-500 rounded-full animate-bounce" />
        <div className="w-2 h-2 bg-sky-500/60 rounded-full animate-bounce [animation-delay:100ms]" />
        <div className="w-2 h-2 bg-sky-500/30 rounded-full animate-bounce [animation-delay:200ms]" />
      </div>
    );
  }

  if (!items.length) {
    return (
      <div className="p-10 text-center text-sm text-gray-500">
        {t('İzleme listesinde henüz sembol yok.')}
      </div>
    );
  }

  const status = calculateDailyStatus(items);
  const hasPerf = hasAnyChangePercentData(items);
  const grouped = groupByAssetType(items);
  const totalForShare = status.total;

  const donutRows = Object.entries(grouped)
    .map(([type, list]) => {
      const name = WATCHLIST_ASSET_LABELS[type] ? t(WATCHLIST_ASSET_LABELS[type]) : type;
      const count = list.length;
      const sharePct = countSharePercent(count, totalForShare);
      return {
        name,
        type,
        value: count,
        sharePct,
      };
    })
    .sort((a, b) => a.name.localeCompare(b.name, 'tr'));

  const donutData = donutRows.map(r => ({ ...r }));

  const dailyBarData = [
    { name: t('Yükselen'), count: status.up, fill: '#10b981' },
    { name: t('Düşen'), count: status.down, fill: '#f43f5e' },
    { name: t('Yatay'), count: status.flat, fill: '#94a3b8' },
    { name: t('Veri Yok'), count: status.nodata, fill: '#fbbf24' },
  ];

  const avgByType = calculateAverageChangeByType(items);
  const gainers = getTopGainers(items, 5);
  const losers = getTopLosers(items, 5);

  const pctClass = n => {
    if (n === null || n === undefined || Number.isNaN(n)) return 'text-gray-400';
    if (n > 0) return 'text-emerald-600';
    if (n < 0) return 'text-rose-600';
    return 'text-gray-500';
  };

  const reportRef = useRef(null);

  return (
    <div className="flex flex-col">
      <div className="flex justify-end px-4 pt-4 md:px-6 md:pt-6">
        <AnalysisPdfExportButton
          getRootElement={() => reportRef.current}
          fileNameBase={`izleme-analiz-${portfolioName || 'liste'}`}
        />
      </div>

      <div
        ref={reportRef}
        data-pdf-capture="watchlist-charts"
        className="p-4 md:p-6 pt-2 md:pt-3 space-y-6 min-w-0 w-full"
      >
        <header className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm">
          <h2 className="text-lg font-bold text-gray-900">{t('İzleme Listesi Analiz Raporu')}</h2>
          <p className="mt-1 text-sm font-medium text-gray-800">
            {t('İzleme listesi:')} {portfolioName || '—'}
          </p>
          <p className="mt-1 text-xs text-gray-500">
            {t('Oluşturulma:')}{' '}
            {new Date().toLocaleString('tr-TR', { dateStyle: 'long', timeStyle: 'short' })}
          </p>
        </header>

      {/* Özet kartları */}
      <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-3">
        <SummaryCard label={t('Toplam Enstrüman')} value={status.total} tone="default" />
        <SummaryCard label={t('Yükselen')} value={status.up} tone="up" />
        <SummaryCard label={t('Düşen')} value={status.down} tone="down" />
        <SummaryCard label={t('Yatay / Değişmeyen')} value={status.flat} tone="flat" />
        <SummaryCard
          label={t('Veri Yok')}
          value={status.nodata}
          tone="muted"
          hint={t('Fiyat/değişim verisi bulunmayan enstrümanlar')}
        />
      </div>

      {!hasPerf && (
        <div className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900">
          {t('Yeterli fiyat verisi yok; günlük performans grafikleri ve sıralamalar gösterilemiyor. Varlık türü dağılımı sembol adedine göre çalışmaya devam eder.')}
        </div>
      )}

      {/* Varlık türü — donut (önceki görünüm: etiket + legend) + özet tablo */}
      <div className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm">
        <h3 className="text-base font-bold text-gray-900">{t('Varlık Türü Dağılımı')}</h3>
        <p className="mt-1 text-xs text-gray-500">
          {t('Dağılım takip edilen sembol adedine göre hesaplanır.')}
        </p>

        <div className="mt-4 h-[280px] w-full min-w-0">
          <ResponsiveContainer width="100%" height="100%">
            <PieChart>
              <Pie
                data={donutData}
                dataKey="value"
                nameKey="name"
                cx="50%"
                cy="50%"
                innerRadius={56}
                outerRadius={96}
                paddingAngle={2}
                label={({ name, percent }) =>
                  `${name} ${formatSharePercent(percent * 100)}`
                }
              >
                {donutData.map((entry, i) => (
                  <Cell key={entry.type} fill={DONUT_COLORS[i % DONUT_COLORS.length]} stroke="#fff" />
                ))}
              </Pie>
              <Tooltip content={<ChartTooltip />} />
              <Legend wrapperStyle={{ fontSize: 12 }} />
            </PieChart>
          </ResponsiveContainer>
        </div>

        <div className="mt-6 overflow-x-auto rounded-lg border border-gray-100">
          <table className="w-full min-w-[280px] text-sm">
            <thead>
              <tr className="border-b border-gray-200 bg-gray-50 text-left text-xs font-semibold text-gray-600">
                <th className="px-3 py-2">{t('Kategori')}</th>
                <th className="px-3 py-2 text-right">{t('Adet')}</th>
                <th className="px-3 py-2 text-right">{t('Oran')}</th>
              </tr>
            </thead>
            <tbody>
              {donutRows.map((row, i) => (
                <tr key={row.type} className="border-b border-gray-50 last:border-0">
                  <td className="px-3 py-2 font-medium text-gray-900">
                    <span className="inline-flex items-center gap-2">
                      <span
                        className="inline-block h-2.5 w-2.5 shrink-0 rounded-sm"
                        style={{ backgroundColor: DONUT_COLORS[i % DONUT_COLORS.length] }}
                        aria-hidden
                      />
                      {row.name}
                    </span>
                  </td>
                  <td className="px-3 py-2 text-right tabular-nums text-gray-800">
                    {row.value.toLocaleString('tr-TR')}
                  </td>
                  <td className="px-3 py-2 text-right tabular-nums font-medium text-gray-800">
                    {row.sharePct != null ? formatSharePercent(row.sharePct) : '-'}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* Günlük durum + ortalama — sadece changePercent varsa anlamlı */}
      {hasPerf && (
        <>
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            <div className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm min-w-0">
              <h3 className="text-base font-bold text-gray-900">{t('Günlük Durum')}</h3>
              <p className="mt-1 text-xs text-gray-500">{t('Yükselen / düşen / yatay / veri yok adetleri')}</p>
              <div className="mt-4 h-[260px] w-full min-w-0">
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={dailyBarData} margin={{ top: 8, right: 8, left: 0, bottom: 8 }}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#e5e7eb" />
                    <XAxis dataKey="name" tick={{ fontSize: 11 }} />
                    <YAxis allowDecimals={false} tick={{ fontSize: 11 }} />
                    <Tooltip content={<ChartTooltip />} />
                    <Bar dataKey="count" radius={[6, 6, 0, 0]}>
                      {dailyBarData.map(e => (
                        <Cell key={e.name} fill={e.fill} />
                      ))}
                    </Bar>
                  </BarChart>
                </ResponsiveContainer>
              </div>
            </div>

            <div className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm min-w-0">
              <h3 className="text-base font-bold text-gray-900">{t('Kategori Bazlı Ortalama Değişim')}</h3>
              <p className="mt-1 text-xs text-gray-500">
                {t('Her varlık türü için ortalama günlük değişim; günlük % verisi olmayan enstrümanlar ve boş kategoriler dahil edilmez.')}
              </p>
              {!avgByType.length ? (
                <p className="mt-8 text-center text-sm text-gray-500">{t('Yeterli fiyat verisi yok')}</p>
              ) : (
                <div className="mt-4 h-[260px] w-full min-w-0">
                  <ResponsiveContainer width="100%" height="100%">
                    <BarChart data={avgByType.map(e => ({ ...e, label: t(e.label) }))} margin={{ top: 8, right: 8, left: 0, bottom: 8 }}>
                      <CartesianGrid strokeDasharray="3 3" stroke="#e5e7eb" />
                      <XAxis dataKey="label" tick={{ fontSize: 11 }} interval={0} angle={-18} textAnchor="end" height={56} />
                      <YAxis tick={{ fontSize: 11 }} tickFormatter={formatPctAxis} />
                      <ReferenceLine y={0} stroke="#cbd5e1" />
                      <Tooltip content={<ChartTooltip />} />
                      <Bar dataKey="avg" name={t('Ortalama %')} radius={[6, 6, 0, 0]}>
                        {avgByType.map(e => (
                          <Cell key={e.type} fill={e.avg >= 0 ? '#10b981' : '#f43f5e'} />
                        ))}
                      </Bar>
                    </BarChart>
                  </ResponsiveContainer>
                </div>
              )}
            </div>
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            <MiniTable
              title={t('En Çok Yükselenler')}
              rows={gainers}
              valueClass={pctClass}
              resolveDetailPath={resolveDetailPath}
            />
            <MiniTable
              title={t('En Çok Düşenler')}
              rows={losers}
              valueClass={pctClass}
              resolveDetailPath={resolveDetailPath}
            />
          </div>
        </>
      )}

        <footer className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-xs text-amber-950 leading-relaxed">
          <strong className="font-semibold">{t('Yasal uyarı:')}</strong> {t('Bu rapor yalnızca bilgilendirme amaçlıdır.')}
          {' '}{t('Gösterilen tablolar ve grafikler')} <strong className="font-semibold">{t('yatırım tavsiyesi değildir')}</strong>.
          {' '}{t('Yatırım kararlarınızı kendi risk ve tercihlerinize göre veriniz.')}
        </footer>
      </div>
    </div>
  );
}
