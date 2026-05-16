import { fmtTry, fmtUsd, getPrimarySpotPrice } from '../../../utils/commodityPriceUtils';

/**
 * Yahoo emtia için TRY işlem fiyatı altında USD referans satırı.
 */
export default function CommodityPriceHint({ spot, unit, className = '' }) {
  const primary = spot ? getPrimarySpotPrice(spot) : null;
  if (!primary) return null;

  return (
    <p className={`text-[11px] text-gray-500 leading-snug ${className}`}>
      Referans: ≈ {fmtUsd(primary.value, primary.decimals)} {primary.currency}
      {unit ? ` / ${unit}` : ''}
    </p>
  );
}

/** Portföy tablosu: TRY tutar + USD referans (spot DTO ile). */
export function CommodityDualPrice({ tryAmount, spot, className = '' }) {
  const primary = spot ? getPrimarySpotPrice(spot) : null;
  const tryVal = tryAmount != null ? parseFloat(tryAmount) : null;

  if (tryVal == null || !Number.isFinite(tryVal)) {
    return <span className={className}>-</span>;
  }

  return (
    <div className={className}>
      <span className="text-sm font-semibold">{fmtTry(tryVal)} TRY</span>
      {primary && (
        <span className="block text-[11px] text-gray-400 font-normal mt-0.5">
          ≈ {fmtUsd(primary.value, primary.decimals)} {primary.currency}
        </span>
      )}
    </div>
  );
}
