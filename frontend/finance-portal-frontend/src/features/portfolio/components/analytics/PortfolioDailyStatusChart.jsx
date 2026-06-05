import {
  ResponsiveContainer,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Cell,
  LabelList,
} from 'recharts';
import PortfolioChartCard from './PortfolioChartCard';
import PortfolioAnalyticsTooltip from './PortfolioAnalyticsTooltip';
import { calculateDailyStatus } from '../../utils/portfolioAnalyticsHelpers';
import { BAR_VERTICAL_BAR, BAR_VERTICAL_CHART, CHART_GRID, TICK } from './portfolioChartStyles';
import { useTranslation } from '../../../../context/LanguageContext';

export default function PortfolioDailyStatusChart({ holdings }) {
  const { t } = useTranslation();
  const status = calculateDailyStatus(holdings);
  const data = [
    { name: t('Yükselen'), count: status.up, fill: '#22c55e' },
    { name: t('Düşen'), count: status.down, fill: '#ef4444' },
    { name: t('Yatay'), count: status.flat, fill: '#94a3b8' },
    { name: t('Veri Yok'), count: status.nodata, fill: '#fbbf24' },
  ];

  if (!status.total) {
    return (
      <PortfolioChartCard title={t('Günlük Durum')} subtitle={t('Günlük değişim durumuna göre pozisyon adedi.')}>
        <p className="text-center text-sm text-gray-400 py-10">{t('Pozisyon bulunamadı.')}</p>
      </PortfolioChartCard>
    );
  }

  return (
    <PortfolioChartCard
      title={t('Günlük Durum')}
      subtitle={t('Yükselen / düşen / yatay / veri yok — pozisyon adedi (günlük % verisine göre).')}
    >
      <div className="flex-1 min-h-0 w-full min-w-0" style={{ minHeight: 200 }}>
        <ResponsiveContainer width="100%" height="100%">
          <BarChart
            data={data}
            margin={{ top: 16, right: 8, left: 0, bottom: 8 }}
            {...BAR_VERTICAL_CHART}
          >
            <CartesianGrid {...CHART_GRID} vertical={false} />
            <XAxis dataKey="name" tick={TICK} />
            <YAxis
              allowDecimals={false}
              tick={TICK}
              domain={[0, dataMax => dataMax + Math.max(1, Math.ceil(dataMax * 0.15))]}
            />
            <Tooltip content={<PortfolioAnalyticsTooltip valuesHidden={false} />} />
            <Bar dataKey="count" radius={[3, 3, 0, 0]} {...BAR_VERTICAL_BAR}>
              {data.map(e => (
                <Cell key={e.name} fill={e.fill} />
              ))}
              <LabelList
                dataKey="count"
                position="top"
                style={{ fontSize: 10, fill: '#475569', fontWeight: 600 }}
              />
            </Bar>
          </BarChart>
        </ResponsiveContainer>
      </div>
    </PortfolioChartCard>
  );
}
