import {
  ResponsiveContainer,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Cell,
  ReferenceLine,
  LabelList,
} from 'recharts';
import PortfolioChartCard from './PortfolioChartCard';
import PortfolioAnalyticsTooltip from './PortfolioAnalyticsTooltip';
import {
  calculateAverageChangeByType,
  hasAnyDailyChangeData,
  formatPctAxis,
} from '../../utils/portfolioAnalyticsHelpers';
import {
  BAR_VERTICAL_BAR,
  BAR_VERTICAL_CHART,
  CHART_GRID,
  COLOR_NEG,
  COLOR_POS,
  TICK,
  formatCompactPct,
  positivePctDomain,
} from './portfolioChartStyles';
import { useTranslation } from '../../../../i18n/LanguageContext';

export default function PortfolioCategoryChangeChart({ holdings, valuesHidden }) {
  const { t } = useTranslation();
  const hasDaily = hasAnyDailyChangeData(holdings);
  const avgByType = calculateAverageChangeByType(holdings).map(e => ({ ...e, label: t(e.label) }));
  const yDomain = positivePctDomain(avgByType.map(e => e.avg));

  return (
    <PortfolioChartCard
      title={t('Kategori Bazlı Ortalama Değişim')}
      subtitle={t('Her varlık türü için ortalama günlük değişim; günlük % verisi olmayanlar dahil edilmez.')}
    >
      {!hasDaily ? (
        <p className="text-center text-sm text-gray-400 py-10">{t('Günlük değişim verisi bulunamadı.')}</p>
      ) : !avgByType.length ? (
        <p className="text-center text-sm text-gray-400 py-10">{t('Yeterli kategori verisi yok.')}</p>
      ) : (
        <div className="h-[240px] w-full min-w-0">
          <ResponsiveContainer width="100%" height="100%">
            <BarChart
              data={avgByType}
              margin={{ top: 8, right: valuesHidden ? 8 : 36, left: 0, bottom: 8 }}
              {...BAR_VERTICAL_CHART}
            >
              <CartesianGrid {...CHART_GRID} vertical={false} />
              <XAxis
                dataKey="label"
                tick={TICK}
                interval={0}
                angle={-16}
                textAnchor="end"
                height={52}
              />
              <YAxis
                domain={yDomain}
                tick={TICK}
                tickFormatter={v => (valuesHidden ? '' : formatPctAxis(v))}
              />
              <ReferenceLine y={0} stroke="#cbd5e1" strokeWidth={1} />
              <Tooltip content={<PortfolioAnalyticsTooltip valuesHidden={valuesHidden} />} />
              <Bar dataKey="avg" name={t('Ortalama %')} radius={[3, 3, 0, 0]} {...BAR_VERTICAL_BAR}>
                {avgByType.map(e => (
                  <Cell key={e.type} fill={e.avg >= 0 ? COLOR_POS : COLOR_NEG} />
                ))}
                {!valuesHidden && (
                  <LabelList
                    dataKey="avg"
                    position="top"
                    formatter={v => formatCompactPct(v, false)}
                    style={{ fontSize: 9, fill: '#64748b' }}
                  />
                )}
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        </div>
      )}
    </PortfolioChartCard>
  );
}
