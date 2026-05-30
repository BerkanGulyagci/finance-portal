import { MASK_MONEY } from '../../utils/portfolioFormatUtils';

export default function PortfolioMoneyTooltip({
  active,
  payload,
  label,
  valuesHidden,
  currency = 'TRY',
}) {
  if (!active || !payload?.length) return null;
  const name = label ?? payload[0]?.payload?.name;

  return (
    <div className="rounded-lg border border-gray-200 bg-white px-3 py-2 text-xs shadow-md max-w-[220px]">
      <div className="font-semibold text-gray-800 mb-1">{name}</div>
      <ul className="space-y-0.5">
        {payload.map(p => (
          <li key={p.dataKey ?? p.name} className="flex justify-between gap-3 text-gray-600">
            <span>{p.name}</span>
            <span className="tabular-nums font-medium text-gray-800">
              {valuesHidden
                ? MASK_MONEY
                : typeof p.value === 'number'
                  ? `${p.value.toLocaleString('tr-TR', { maximumFractionDigits: 2 })} ${currency}`
                  : '—'}
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
}
