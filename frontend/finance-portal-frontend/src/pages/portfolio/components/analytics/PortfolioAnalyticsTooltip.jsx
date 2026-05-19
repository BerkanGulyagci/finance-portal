import { formatSharePercent } from '../../utils/portfolioAnalyticsHelpers';

export default function PortfolioAnalyticsTooltip({
  active,
  payload,
  label,
  valuesHidden,
  currency = 'TRY',
  valueIsMoney = false,
}) {
  if (!active || !payload?.length) return null;
  const p = payload[0];
  const raw = p?.payload;
  const name = label ?? p?.name ?? raw?.name;

  let valueLine = '';
  if (valueIsMoney && typeof p?.value === 'number') {
    valueLine = valuesHidden
      ? '•••••'
      : `${p.value.toLocaleString('tr-TR', { maximumFractionDigits: 2 })} ${currency}`;
  } else if (raw?.sharePct != null && typeof p?.value === 'number') {
    valueLine = valuesHidden
      ? '••••'
      : `${p.value.toLocaleString('tr-TR', { maximumFractionDigits: 2 })} ${currency} · ${formatSharePercent(raw.sharePct, false)}`;
  } else if (p?.dataKey === 'avg') {
    valueLine = valuesHidden
      ? '••••'
      : `${Number(p.value).toLocaleString('tr-TR', { maximumFractionDigits: 2 })}%`;
  } else if (
    (p?.dataKey === 'profitLoss' || p?.dataKey === 'daily') &&
    typeof p?.value === 'number'
  ) {
    valueLine = valuesHidden
      ? '•••••'
      : `${p.value.toLocaleString('tr-TR', { maximumFractionDigits: 2 })} ${currency}`;
  } else if (typeof p?.value === 'number') {
    valueLine = valuesHidden ? '••••' : p.value.toLocaleString('tr-TR');
  }

  return (
    <div className="rounded-lg border border-gray-200 bg-white px-3 py-2 text-xs shadow-md">
      <div className="font-semibold text-gray-800">{name}</div>
      {valueLine && <div className="text-gray-600 mt-0.5">{valueLine}</div>}
    </div>
  );
}
