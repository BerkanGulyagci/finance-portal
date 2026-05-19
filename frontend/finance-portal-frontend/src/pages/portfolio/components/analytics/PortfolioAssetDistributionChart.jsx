import { useState } from 'react';
import PortfolioChartCard from './PortfolioChartCard';
import PortfolioDonutChart from './PortfolioDonutChart';
import {
  calculateTopHoldingsDistribution,
  CHART_DONUT_COLORS,
  formatSharePercent,
} from '../../utils/portfolioAnalyticsHelpers';

export default function PortfolioAssetDistributionChart({ holdings, valuesHidden, currency }) {
  const { rows, total } = calculateTopHoldingsDistribution(holdings, 5);
  const [selectedName, setSelectedName] = useState(null);

  if (!rows.length) {
    return (
      <PortfolioChartCard
        title="Varlık Bazlı Dağılım"
        subtitle="En büyük pozisyonlar piyasa değerine göre; kalanlar “Diğer”."
      >
        <p className="text-center text-sm text-gray-400 py-10">Dağılım için yeterli veri bulunamadı.</p>
      </PortfolioChartCard>
    );
  }

  return (
    <PortfolioChartCard
      title="Varlık Bazlı Dağılım"
      subtitle="En büyük 5 pozisyon + Diğer; dilime tıklayın."
    >
      <PortfolioDonutChart
        data={rows}
        valuesHidden={valuesHidden}
        currency={currency}
        height={268}
        getCellKey={(entry, i) => `${entry.name}-${i}`}
        onSelect={item => setSelectedName(item?.name ?? null)}
      />
      <div className="mt-3 overflow-x-auto rounded-lg border border-gray-100">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-gray-200 bg-gray-50 text-left text-xs font-semibold text-gray-600">
              <th className="px-3 py-2">Varlık</th>
              <th className="px-3 py-2 text-right">Değer</th>
              <th className="px-3 py-2 text-right">Oran</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row, i) => (
              <tr
                key={`${row.name}-${i}`}
                className={`border-b border-gray-50 last:border-0 transition-colors ${
                  selectedName === row.name ? 'bg-sky-50/80' : ''
                }`}
              >
                <td className="px-3 py-2 font-medium text-gray-900 max-w-[180px]">
                  <span className="inline-flex items-center gap-2 min-w-0">
                    <span
                      className="inline-block h-2.5 w-2.5 shrink-0 rounded-sm"
                      style={{ backgroundColor: CHART_DONUT_COLORS[i % CHART_DONUT_COLORS.length] }}
                      aria-hidden
                    />
                    <span className="truncate" title={row.name}>
                      {row.name}
                    </span>
                  </span>
                </td>
                <td className="px-3 py-2 text-right tabular-nums text-gray-800 whitespace-nowrap">
                  {valuesHidden
                    ? '•••••'
                    : `${row.value.toLocaleString('tr-TR', { maximumFractionDigits: 2 })} ${currency}`}
                </td>
                <td className="px-3 py-2 text-right tabular-nums font-medium text-gray-800">
                  {formatSharePercent(row.sharePct, valuesHidden)}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {!valuesHidden && total > 0 && (
          <p className="px-3 py-2 text-xs text-gray-400 border-t border-gray-100">
            Toplam: {total.toLocaleString('tr-TR', { maximumFractionDigits: 2 })} {currency}
          </p>
        )}
      </div>
    </PortfolioChartCard>
  );
}
