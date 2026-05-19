import { useNavigate } from 'react-router-dom';
import PortfolioChartCard from './PortfolioChartCard';
import { getWatchlistDetailPath } from '../../constants/watchlistMarketRoutes';
import { formatMoney } from '../../utils/portfolioFormatUtils';
import {
  getTopGainers,
  getTopLosers,
  holdingDisplayName,
  assetTypeBadgeClass,
  formatPctSigned,
  parseMarketValue,
  PORTFOLIO_ASSET_LABELS,
} from '../../utils/portfolioAnalyticsHelpers';

function parsePrice(h) {
  const cp = h?.currentPrice;
  if (cp != null && cp !== '') {
    const n = typeof cp === 'number' ? cp : parseFloat(cp);
    if (Number.isFinite(n)) return n;
  }
  return parseMarketValue(h);
}

function MoverRow({ row, valuesHidden, currency, onNavigate }) {
  const { holding: h, changePercent } = row;
  const to = getWatchlistDetailPath(h.assetType, h.symbol);
  const typeLabel = PORTFOLIO_ASSET_LABELS[h.assetType] ?? h.assetType ?? '—';
  const price = parsePrice(h);
  const pctCls =
    changePercent > 0 ? 'text-emerald-600' : changePercent < 0 ? 'text-rose-600' : 'text-gray-500';

  return (
    <li>
      <button
        type="button"
        onClick={() => to && onNavigate(to)}
        disabled={!to}
        className={`w-full text-left rounded-lg border border-gray-100 px-2.5 py-2 transition-colors ${
          to ? 'hover:bg-sky-50/50 cursor-pointer' : 'cursor-default'
        }`}
      >
        <div className="flex items-start justify-between gap-2">
          <div className="min-w-0 flex-1">
            <span
              className={`inline-flex rounded-md px-1.5 py-0.5 text-[10px] font-semibold ${assetTypeBadgeClass(h.assetType)}`}
            >
              {typeLabel}
            </span>
            <p className="mt-1 text-sm font-semibold text-[#093eaa] truncate" title={holdingDisplayName(h)}>
              {holdingDisplayName(h)}
            </p>
          </div>
          <div className="shrink-0 text-right">
            <p className="text-xs tabular-nums text-gray-700">
              {formatMoney(price, currency, valuesHidden)}
            </p>
            <p className={`text-xs font-semibold tabular-nums ${pctCls}`}>
              {formatPctSigned(changePercent, valuesHidden)}
            </p>
          </div>
        </div>
      </button>
    </li>
  );
}

function MoverColumn({ title, rows, emptyMsg, valuesHidden, currency, onNavigate, tone }) {
  const borderCls = tone === 'up' ? 'border-emerald-100' : 'border-rose-100';
  const titleCls = tone === 'up' ? 'text-emerald-800' : 'text-rose-800';

  return (
    <div className={`rounded-lg border ${borderCls} bg-gray-50/40 p-3 min-w-0`}>
      <h4 className={`text-sm font-bold ${titleCls}`}>{title}</h4>
      {!rows.length ? (
        <p className="mt-4 text-center text-xs text-gray-400 py-6">{emptyMsg}</p>
      ) : (
        <ul className="mt-2 space-y-2">
          {rows.map((row, idx) => (
            <MoverRow
              key={row.holding.id ?? `${row.holding.symbol}-${idx}`}
              row={row}
              valuesHidden={valuesHidden}
              currency={currency}
              onNavigate={onNavigate}
            />
          ))}
        </ul>
      )}
    </div>
  );
}

export default function PortfolioGainersLosersCard({
  holdings,
  valuesHidden,
  currency = 'TRY',
  limit = 5,
}) {
  const navigate = useNavigate();
  const gainers = getTopGainers(holdings, limit);
  const losers = getTopLosers(holdings, limit);

  return (
    <PortfolioChartCard
      title="Yükselenler ve Düşenler"
      subtitle="Günlük veya açık K/Z yüzdesine göre; her iki liste her zaman gösterilir."
      className="lg:col-span-2"
    >
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4 min-w-0">
        <MoverColumn
          title="Yükselenler"
          rows={gainers}
          emptyMsg="Yükselen varlık bulunamadı."
          valuesHidden={valuesHidden}
          currency={currency}
          onNavigate={to => navigate(to)}
          tone="up"
        />
        <MoverColumn
          title="Düşenler"
          rows={losers}
          emptyMsg="Düşen varlık bulunamadı."
          valuesHidden={valuesHidden}
          currency={currency}
          onNavigate={to => navigate(to)}
          tone="down"
        />
      </div>
    </PortfolioChartCard>
  );
}
