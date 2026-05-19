import {
  calculateAllocationByType,
  calculateDailyStatus,
  PORTFOLIO_ASSET_LABELS,
  formatSharePercent,
} from '../../utils/portfolioAnalyticsHelpers';
import { formatMoney } from '../../utils/portfolioFormatUtils';

function StatTile({ label, value, hint }) {
  return (
    <div className="rounded-xl border border-gray-200 bg-gray-50/50 p-4 min-w-0">
      <p className="text-xs font-semibold text-gray-500">{label}</p>
      <p className="mt-1 text-lg font-bold text-gray-900 tabular-nums break-words">{value}</p>
      {hint && <p className="mt-1 text-xs text-gray-400">{hint}</p>}
    </div>
  );
}

export default function PortfolioStatsSummary({ holdings, valuesHidden, currency }) {
  const count = holdings?.length ?? 0;
  const status = calculateDailyStatus(holdings);
  const { rows, total } = calculateAllocationByType(holdings);

  if (!count) {
    return (
      <div className="p-12 text-center text-sm text-gray-400">Portföyde pozisyon bulunamadı.</div>
    );
  }

  const topType = rows[0];

  return (
    <div className="p-4 sm:p-5 space-y-4 min-w-0">
      <p className="text-sm text-gray-500">
        Özet metrikler. Detaylı grafikler için{' '}
        <span className="font-semibold text-[#093eaa]">Grafikler</span> sekmesine geçin.
      </p>
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
        <StatTile label="Pozisyon sayısı" value={String(count)} />
        <StatTile
          label="Yükselen / Düşen"
          value={`${status.up} / ${status.down}`}
          hint={`Yatay: ${status.flat} · Veri yok: ${status.nodata}`}
        />
        <StatTile
          label="Toplam piyasa değeri"
          value={formatMoney(total > 0 ? total : null, currency, valuesHidden)}
        />
        <StatTile
          label="En büyük kategori"
          value={
            topType
              ? `${topType.name} (${formatSharePercent(topType.sharePct, valuesHidden)})`
              : '—'
          }
        />
      </div>

      {rows.length > 0 && (
        <div className="rounded-xl border border-gray-200 overflow-hidden">
          <table className="w-full text-sm">
            <thead>
              <tr className="bg-gray-50 border-b border-gray-200 text-left text-xs font-semibold text-gray-600">
                <th className="px-4 py-2">Varlık türü</th>
                <th className="px-4 py-2 text-right">Piyasa değeri</th>
                <th className="px-4 py-2 text-right">Oran</th>
              </tr>
            </thead>
            <tbody>
              {rows.map(row => (
                <tr key={row.type} className="border-b border-gray-50 last:border-0">
                  <td className="px-4 py-2 font-medium text-gray-900">
                    {PORTFOLIO_ASSET_LABELS[row.type] ?? row.name}
                  </td>
                  <td className="px-4 py-2 text-right tabular-nums">
                    {formatMoney(row.value, currency, valuesHidden)}
                  </td>
                  <td className="px-4 py-2 text-right tabular-nums font-medium">
                    {formatSharePercent(row.sharePct, valuesHidden)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
