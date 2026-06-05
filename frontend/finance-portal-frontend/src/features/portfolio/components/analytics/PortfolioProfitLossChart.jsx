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
  calculateProfitLossByHolding,
  truncateChartLabel,
} from '../../utils/portfolioAnalyticsHelpers';
import {
  BAR_HORIZONTAL_BAR,
  BAR_HORIZONTAL_CHART,
  CHART_GRID,
  COLOR_NEG,
  COLOR_POS,
  TICK,
  signedNumericDomain,
} from './portfolioChartStyles';
import { formatMoney } from '../../utils/portfolioFormatUtils';
import { useTranslation } from '../../../../context/LanguageContext';

export default function PortfolioProfitLossChart({ holdings, valuesHidden, currency }) {
  const { t } = useTranslation();
  const { rows, truncated } = calculateProfitLossByHolding(holdings);

  const chartData = rows.map(r => ({
    ...r,
    shortName: truncateChartLabel(r.name, 18),
  }));

  const xDomain = signedNumericDomain(chartData.map(d => d.profitLoss));
  const chartHeight = Math.min(380, Math.max(220, chartData.length * 38 + 48));

  return (
    <PortfolioChartCard
      title={t('Varlık Bazlı Kar/Zarar')}
      subtitle={
        truncated
          ? t('Satılmamış pozisyonların anlık K/Z (en yüksek mutlak değerler, max 12).')
          : t('Satılmamış pozisyonların anlık kar/zarar dağılımı.')
      }
    >
      {!chartData.length ? (
        <p className="text-center text-sm text-gray-400 py-10">{t('Kar/zarar verisi bulunamadı.')}</p>
      ) : (
        <div className="w-full min-w-0 flex-1 min-h-0" style={{ minHeight: chartHeight }}>
          <ResponsiveContainer width="100%" height="100%">
            <BarChart
              layout="vertical"
              data={chartData}
              margin={{ top: 4, right: valuesHidden ? 12 : 88, left: 4, bottom: 4 }}
              {...BAR_HORIZONTAL_CHART}
            >
              <CartesianGrid {...CHART_GRID} horizontal={false} />
              <XAxis
                type="number"
                domain={xDomain}
                tick={TICK}
                tickFormatter={v =>
                  valuesHidden ? '' : Number(v).toLocaleString('tr-TR', { notation: 'compact' })
                }
              />
              <YAxis type="category" dataKey="shortName" width={112} tick={TICK} />
              <ReferenceLine x={0} stroke="#cbd5e1" strokeWidth={1} />
              <Tooltip
                content={
                  <PortfolioAnalyticsTooltip
                    valuesHidden={valuesHidden}
                    currency={currency}
                    valueIsMoney
                  />
                }
              />
              <Bar
                dataKey="profitLoss"
                name={t('K/Z')}
                radius={[0, 3, 3, 0]}
                {...BAR_HORIZONTAL_BAR}
              >
                {chartData.map(entry => (
                  <Cell
                    key={entry.key}
                    fill={entry.profitLoss >= 0 ? COLOR_POS : COLOR_NEG}
                  />
                ))}
                {!valuesHidden && (
                  <LabelList
                    dataKey="profitLoss"
                    position="right"
                    formatter={v => formatMoney(v, currency, false)}
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
