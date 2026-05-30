import PortfolioChartCard from './PortfolioChartCard';
import {
  calculateConcentrationMetrics,
  formatSharePercent,
} from '../../utils/portfolioAnalyticsHelpers';
import { useTranslation } from '../../../../context/LanguageContext';

const RISK_STYLES = {
  high: 'border-rose-200 bg-rose-50/70 text-rose-800',
  medium: 'border-amber-200 bg-amber-50/70 text-amber-900',
  low: 'border-emerald-200 bg-emerald-50/70 text-emerald-800',
};

function MetricTile({ label, value, sub }) {
  return (
    <div className="rounded-lg border border-gray-100 bg-gray-50/60 px-3 py-2.5 min-w-0">
      <p className="text-[10px] font-semibold uppercase tracking-wide text-gray-500">{label}</p>
      <p className="mt-1 text-sm font-bold text-gray-900 break-words">{value}</p>
      {sub && <p className="mt-0.5 text-[11px] text-gray-500">{sub}</p>}
    </div>
  );
}

export default function PortfolioConcentrationCard({ holdings, valuesHidden }) {
  const { t } = useTranslation();
  const m = calculateConcentrationMetrics(holdings);

  return (
    <PortfolioChartCard
      title={t('Portföy Yoğunlaşması')}
      subtitle={t('Tek pozisyon veya kategoriye bağımlılık özeti.')}
      className="lg:col-span-2"
    >
      {!m.hasData ? (
        <p className="text-center text-sm text-gray-400 py-8">{t('Dağılım için yeterli veri bulunamadı.')}</p>
      ) : (
        <>
          {m.risk && (
            <div
              className={`mb-4 inline-flex items-center rounded-full border px-3 py-1 text-xs font-semibold ${
                RISK_STYLES[m.risk.level] ?? RISK_STYLES.low
              }`}
            >
              {t(m.risk.label)}
            </div>
          )}

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <MetricTile
              label={t('En büyük pozisyon')}
              value={m.topPosition?.name ?? '—'}
              sub={
                m.topPosition
                  ? formatSharePercent(m.topPosition.sharePct, valuesHidden)
                  : null
              }
            />
            <MetricTile
              label={t('En büyük kategori')}
              value={m.topCategory ? t(m.topCategory.name) : '—'}
              sub={
                m.topCategory
                  ? formatSharePercent(m.topCategory.sharePct, valuesHidden)
                  : null
              }
            />
            <MetricTile label={t('Pozisyon sayısı')} value={String(m.positionCount)} />
            <MetricTile label={t('Varlık türü sayısı')} value={String(m.typeCount)} />
          </div>
        </>
      )}
    </PortfolioChartCard>
  );
}
