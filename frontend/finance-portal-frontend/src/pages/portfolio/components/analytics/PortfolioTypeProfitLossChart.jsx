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
import { calculateProfitLossByType } from '../../utils/portfolioAnalyticsHelpers';
import {
  BAR_VERTICAL_BAR,
  BAR_VERTICAL_CHART,
  CHART_GRID,
  COLOR_NEG,
  COLOR_POS,
  TICK,
  signedNumericDomain,
} from './portfolioChartStyles';
import { formatMoney } from '../../utils/portfolioFormatUtils';

export default function PortfolioTypeProfitLossChart({ holdings, valuesHidden, currency }) {
  const rows = calculateProfitLossByType(holdings);
  const yDomain = signedNumericDomain(rows.map(r => r.profitLoss));

  return (
    <PortfolioChartCard
      title="Varlık Türü Bazlı Kar/Zarar"
      subtitle="Kategorilere göre toplam açık kar/zarar (marketValue − totalCost)."
    >
      {!rows.length ? (
        <p className="text-center text-sm text-gray-400 py-10">Kar/zarar verisi bulunamadı.</p>
      ) : (
        <div className="h-[240px] w-full min-w-0">
          <ResponsiveContainer width="100%" height="100%">
            <BarChart
              data={rows}
              margin={{ top: 8, right: valuesHidden ? 8 : 72, left: 0, bottom: 8 }}
              {...BAR_VERTICAL_CHART}
            >
              <CartesianGrid {...CHART_GRID} vertical={false} />
              <XAxis
                dataKey="label"
                tick={TICK}
                interval={0}
                angle={rows.length > 4 ? -18 : 0}
                textAnchor={rows.length > 4 ? 'end' : 'middle'}
                height={rows.length > 4 ? 52 : 32}
              />
              <YAxis
                domain={yDomain}
                tick={TICK}
                tickFormatter={v =>
                  valuesHidden ? '' : Number(v).toLocaleString('tr-TR', { notation: 'compact' })
                }
                width={48}
              />
              <ReferenceLine y={0} stroke="#cbd5e1" strokeWidth={1} />
              <Tooltip
                content={
                  <PortfolioAnalyticsTooltip
                    valuesHidden={valuesHidden}
                    currency={currency}
                    valueIsMoney
                  />
                }
              />
              <Bar dataKey="profitLoss" name="K/Z" radius={[3, 3, 0, 0]} {...BAR_VERTICAL_BAR}>
                {rows.map(e => (
                  <Cell key={e.type} fill={e.profitLoss >= 0 ? COLOR_POS : COLOR_NEG} />
                ))}
                {!valuesHidden && (
                  <LabelList
                    dataKey="profitLoss"
                    position="top"
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
