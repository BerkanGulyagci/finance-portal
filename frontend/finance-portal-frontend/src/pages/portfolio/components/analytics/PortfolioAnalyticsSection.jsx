import PortfolioAllocationChart from './PortfolioAllocationChart';
import PortfolioAssetDistributionChart from './PortfolioAssetDistributionChart';
import PortfolioCostValueChart from './PortfolioCostValueChart';
import PortfolioProfitLossChart from './PortfolioProfitLossChart';
import PortfolioTypeProfitLossChart from './PortfolioTypeProfitLossChart';
import PortfolioDailyStatusChart from './PortfolioDailyStatusChart';
import PortfolioCategoryChangeChart from './PortfolioCategoryChangeChart';
import PortfolioDailyContributionChart from './PortfolioDailyContributionChart';
import PortfolioGainersLosersCard from './PortfolioGainersLosersCard';

export default function PortfolioAnalyticsSection({ holdings, valuesHidden, currency }) {
  return (
    <div className="p-4 sm:p-5 min-w-0 overflow-x-hidden">
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4 min-w-0">
        <PortfolioAllocationChart
          holdings={holdings}
          valuesHidden={valuesHidden}
          currency={currency}
        />
        <PortfolioAssetDistributionChart
          holdings={holdings}
          valuesHidden={valuesHidden}
          currency={currency}
        />
        <PortfolioCostValueChart
          holdings={holdings}
          valuesHidden={valuesHidden}
          currency={currency}
        />
        <PortfolioProfitLossChart
          holdings={holdings}
          valuesHidden={valuesHidden}
          currency={currency}
        />
        <PortfolioTypeProfitLossChart
          holdings={holdings}
          valuesHidden={valuesHidden}
          currency={currency}
        />
        <PortfolioDailyStatusChart holdings={holdings} />
        <PortfolioCategoryChangeChart holdings={holdings} valuesHidden={valuesHidden} />
        <PortfolioDailyContributionChart
          holdings={holdings}
          valuesHidden={valuesHidden}
          currency={currency}
        />
        <PortfolioGainersLosersCard
          holdings={holdings}
          valuesHidden={valuesHidden}
          currency={currency}
          limit={5}
        />
      </div>
    </div>
  );
}
