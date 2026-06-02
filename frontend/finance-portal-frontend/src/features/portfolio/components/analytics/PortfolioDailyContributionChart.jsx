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
  calculateDailyContributionRows,
  hasAnyDailyProfitLossData,
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

export default function PortfolioDailyContributionChart({ holdings, valuesHidden, currency }) {
  const { t } = useTranslation();
  const hasData = hasAnyDailyProfitLossData(holdings);
  const { rows, truncated } = calculateDailyContributionRows(holdings);

  const chartData = rows.map(r => ({
    ...r,
    shortName: truncateChartLabel(r.name, 18),
  }));

  const xDomain = signedNumericDomain(chartData.map(d => d.daily));
  const chartHeight = Math.min(380, Math.max(220, chartData.length * 38 + 48));

  // İşaret-duyarlı değer etiketi: uzun bar → tepe içine (beyaz), kısa bar → tepe dışına (gri).
  // Negatif barda etiketi sola taşırıp kategori adına bindirmeyi önler.
  const renderValueLabel = props => {
    const { x, y, width, height, value } = props;
    if (value == null || !Number.isFinite(value)) return null;
    const txt = formatMoney(value, currency, false);
    const positive = value >= 0;
    const barLen = Math.abs(width);
    const tipX = positive ? x + width : x;
    const labelW = txt.length * 5.4 + 8;
    const inside = barLen > labelW + 10;
    const lx = inside ? (positive ? tipX - 5 : tipX + 5) : positive ? tipX + 5 : tipX - 5;
    const anchor = inside === positive ? 'end' : 'start';
    return (
      <text x={lx} y={y + height / 2} dy={3} textAnchor={anchor} fontSize={9}
        fill={inside ? '#ffffff' : '#64748b'} fontWeight={inside ? 600 : 400}>
        {txt}
      </text>
    );
  };

  return (
    <PortfolioChartCard
      title={t('Günlük K/Z Katkısı')}
      subtitle={
        truncated
          ? t('Bugünkü günlük K/Z’ye en çok katkı eden pozisyonlar (max 12).')
          : t('Bugünkü günlük kar/zarara pozisyon bazlı katkı.')
      }
    >
      {!hasData ? (
        <p className="text-center text-sm text-gray-400 py-10">{t('Günlük katkı verisi bulunamadı.')}</p>
      ) : !chartData.length ? (
        <p className="text-center text-sm text-gray-400 py-10">{t('Günlük katkı verisi bulunamadı.')}</p>
      ) : (
        <div className="w-full min-w-0" style={{ height: chartHeight }}>
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
              <Bar dataKey="daily" name={t('Günlük K/Z')} radius={[0, 3, 3, 0]} {...BAR_HORIZONTAL_BAR}>
                {chartData.map(entry => (
                  <Cell key={entry.key} fill={entry.daily >= 0 ? COLOR_POS : COLOR_NEG} />
                ))}
                {!valuesHidden && <LabelList dataKey="daily" content={renderValueLabel} />}
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        </div>
      )}
    </PortfolioChartCard>
  );
}
