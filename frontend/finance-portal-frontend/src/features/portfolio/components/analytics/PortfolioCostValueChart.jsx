import {
  ResponsiveContainer,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
} from 'recharts';
import PortfolioChartCard from './PortfolioChartCard';
import PortfolioMoneyTooltip from './PortfolioMoneyTooltip';
import {
  calculateCostVsMarketRows,
  truncateChartLabel,
} from '../../utils/portfolioAnalyticsHelpers';
import { MASK_MONEY } from '../../utils/portfolioFormatUtils';
import {
  BAR_GROUPED_BAR,
  BAR_GROUPED_CHART,
  CHART_GRID,
  COLOR_COST,
  COLOR_MV,
  TICK,
  isSkewedMoneyScale,
  positiveMoneyDomain,
} from './portfolioChartStyles';
import { useTranslation } from '../../../../context/LanguageContext';

export default function PortfolioCostValueChart({ holdings, valuesHidden, currency }) {
  const { t } = useTranslation();
  const { rows, truncated } = calculateCostVsMarketRows(holdings);

  const chartData = rows.map(r => ({
    ...r,
    shortName: truncateChartLabel(r.name, 14),
  }));

  const allValues = chartData.flatMap(d => [d.cost, d.marketValue]);
  const skewed = isSkewedMoneyScale(allValues);
  const yDomain = positiveMoneyDomain(allValues);

  const chartHeight = Math.min(340, Math.max(220, chartData.length * 48 + 56));

  return (
    <PortfolioChartCard
      title={t('Maliyet / Piyasa Değeri')}
      subtitle={
        skewed
          ? t('Karşılaştırma için ölçek genişletildi; tam tutarlar için çubuğun üzerine gelin.')
          : truncated
            ? t('Her pozisyonun maliyeti ile güncel piyasa değeri (en büyük 12 pozisyon).')
            : t('Her pozisyonun maliyeti ile güncel piyasa değeri karşılaştırılır.')
      }
    >
      {!chartData.length ? (
        <p className="text-center text-sm text-gray-400 py-10">{t('Dağılım için yeterli veri bulunamadı.')}</p>
      ) : (
        <div className="w-full min-w-0" style={{ height: chartHeight }}>
          <ResponsiveContainer width="100%" height="100%">
            <BarChart
              data={chartData}
              margin={{ top: 8, right: 8, left: 0, bottom: 4 }}
              {...BAR_GROUPED_CHART}
            >
              <CartesianGrid {...CHART_GRID} vertical={false} />
              <XAxis
                dataKey="shortName"
                tick={TICK}
                interval={0}
                angle={chartData.length > 4 ? -22 : 0}
                textAnchor={chartData.length > 4 ? 'end' : 'middle'}
                height={chartData.length > 4 ? 56 : 32}
              />
              <YAxis
                domain={yDomain}
                tick={TICK}
                tickFormatter={v =>
                  valuesHidden ? '' : Number(v).toLocaleString('tr-TR', { notation: 'compact' })
                }
                width={52}
              />
              <Tooltip
                content={
                  <PortfolioMoneyTooltip valuesHidden={valuesHidden} currency={currency} />
                }
              />
              <Legend wrapperStyle={{ fontSize: 11 }} />
              <Bar
                dataKey="cost"
                name={t('Toplam Maliyet')}
                fill={COLOR_COST}
                radius={[3, 3, 0, 0]}
                {...BAR_GROUPED_BAR}
              />
              <Bar
                dataKey="marketValue"
                name={t('Piyasa Değeri')}
                fill={COLOR_MV}
                radius={[3, 3, 0, 0]}
                {...BAR_GROUPED_BAR}
              />
            </BarChart>
          </ResponsiveContainer>
        </div>
      )}
      {valuesHidden && chartData.length > 0 && (
        <p className="mt-2 text-center text-[10px] text-gray-400">{t('Değerler gizli')} ({MASK_MONEY})</p>
      )}
    </PortfolioChartCard>
  );
}
