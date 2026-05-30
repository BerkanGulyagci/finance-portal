import { useRef } from 'react';
import AnalysisPdfExportButton from './AnalysisPdfExportButton';
import AddWatchlistChartsToDashboardMenu from './AddWatchlistChartsToDashboardMenu';
import GridBoard from '../../../components/common/GridBoard';
import { useTranslation } from '../../../context/LanguageContext';
import { calculateDailyStatus, hasAnyChangePercentData } from '../utils/watchlistChartHelpers';
import { ChartCard, WL_CHARTS } from './watchlistChartRegistry';

function SummaryCard({ label, value, tone, hint }) {
  const { t } = useTranslation();
  const toneCls =
    tone === 'up' ? 'border-emerald-200 bg-emerald-50/60'
      : tone === 'down' ? 'border-rose-200 bg-rose-50/60'
        : tone === 'flat' ? 'border-gray-200 bg-gray-50/80'
          : tone === 'muted' ? 'border-amber-200 bg-amber-50/50'
            : 'border-sky-200 bg-sky-50/50';
  return (
    <div className={`rounded-xl border p-4 ${toneCls}`}>
      <div className="flex items-start gap-1">
        <span className="text-xs font-semibold text-gray-500">{label}</span>
        {hint ? (
          <span className="group relative inline-flex shrink-0">
            <span className="inline-flex h-4 w-4 cursor-help items-center justify-center rounded-full border border-amber-300 bg-amber-100 text-[10px] font-bold text-amber-800" tabIndex={0} aria-label={t('Açıklama')}>?</span>
            <span role="tooltip" className="pointer-events-none invisible absolute left-1/2 top-full z-[60] mt-2 w-max max-w-[min(240px,calc(100vw-2rem))] -translate-x-1/2 rounded-lg border border-gray-200 bg-white px-3 py-2 text-left text-[11px] leading-snug text-gray-700 shadow-lg opacity-0 ring-1 ring-black/5 transition group-hover:visible group-hover:opacity-100 group-focus-within:visible group-focus-within:opacity-100">{hint}</span>
          </span>
        ) : null}
      </div>
      <div className="mt-1 text-xl sm:text-2xl font-bold tabular-nums leading-snug text-gray-900 break-words">{value}</div>
    </div>
  );
}

/**
 * İzleme listesi grafik analizi — GridBoard ile özelleştirilebilir (sürükle/boyut/gizle) + her grafik
 * Dashboard'a eklenebilir (kaynak izleme listesi adı yazılır). Düzen fp-wl-charts-{id}'de saklanır.
 */
export default function WatchlistCharts({ items = [], loading = false, resolveDetailPath, portfolioName = '', portfolioId }) {
  const { t } = useTranslation();
  const reportRef = useRef(null);

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
    return <div className="p-10 text-center text-sm text-gray-500">{t('İzleme listesinde henüz sembol yok.')}</div>;
  }

  const status = calculateDailyStatus(items);
  const hasPerf = hasAnyChangePercentData(items);

  const cards = [];
  cards.push({
    key: 'summary', w: 12, h: 6,
    node: (
      <ChartCard title={t('Özet')}>
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-3">
          <SummaryCard label={t('Toplam Enstrüman')} value={status.total} tone="default" />
          <SummaryCard label={t('Yükselen')} value={status.up} tone="up" />
          <SummaryCard label={t('Düşen')} value={status.down} tone="down" />
          <SummaryCard label={t('Yatay / Değişmeyen')} value={status.flat} tone="flat" />
          <SummaryCard label={t('Veri Yok')} value={status.nodata} tone="muted" hint={t('Fiyat/değişim verisi bulunmayan enstrümanlar')} />
        </div>
        {!hasPerf && (
          <div className="mt-3 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900">
            {t('Yeterli fiyat verisi yok; günlük performans grafikleri ve sıralamalar gösterilemiyor. Varlık türü dağılımı sembol adedine göre çalışmaya devam eder.')}
          </div>
        )}
      </ChartCard>
    ),
  });

  WL_CHARTS.forEach(c => {
    if (c.perf && !hasPerf) return;
    const Comp = c.Comp;
    cards.push({
      key: c.key, w: c.w, h: c.h,
      node: <Comp items={items} resolveDetailPath={resolveDetailPath} />,
    });
  });

  return (
    <div ref={reportRef} data-pdf-capture="watchlist-charts" className="p-4 md:p-6 space-y-4 min-w-0 w-full">
      <header className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm">
        <h2 className="text-lg font-bold text-gray-900">{t('İzleme Listesi Analiz Raporu')}</h2>
        <p className="mt-1 text-sm font-medium text-gray-800">{t('İzleme listesi:')} {portfolioName || '—'}</p>
        <p className="mt-1 text-xs text-gray-500">{t('Oluşturulma:')} {new Date().toLocaleString('tr-TR', { dateStyle: 'long', timeStyle: 'short' })}</p>
      </header>

      <GridBoard
        storageKey={`fp-wl-charts-${portfolioId ?? 'default'}`}
        items={cards}
        removable
        toolbar={(
          <>
            <AddWatchlistChartsToDashboardMenu watchlistId={portfolioId} watchlistName={portfolioName} />
            <AnalysisPdfExportButton getRootElement={() => reportRef.current} fileNameBase={`izleme-analiz-${portfolioName || 'liste'}`} />
          </>
        )}
      />

      <footer className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-xs text-amber-950 leading-relaxed">
        <strong className="font-semibold">{t('Yasal uyarı:')}</strong> {t('Bu rapor yalnızca bilgilendirme amaçlıdır.')}
        {' '}{t('Gösterilen tablolar ve grafikler')} <strong className="font-semibold">{t('yatırım tavsiyesi değildir')}</strong>.
        {' '}{t('Yatırım kararlarınızı kendi risk ve tercihlerinize göre veriniz.')}
      </footer>
    </div>
  );
}
