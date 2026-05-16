import { Zap, Wheat, Factory } from 'lucide-react';

// ── Kategori meta ─────────────────────────────────────────────────────────────

export const CATEGORY_META = {
  ENERGY: {
    label: 'Enerji',
    icon: Zap,
    color: 'text-orange-500',
    bg: 'bg-orange-50',
    border: 'border-orange-200',
  },
  AGRICULTURE: {
    label: 'Tarım',
    icon: Wheat,
    color: 'text-green-600',
    bg: 'bg-green-50',
    border: 'border-green-200',
  },
  INDUSTRIAL_METALS: {
    label: 'Sanayi Metalleri',
    icon: Factory,
    color: 'text-blue-600',
    bg: 'bg-blue-50',
    border: 'border-blue-200',
  },
};

export const CATEGORY_ORDER = ['ENERGY', 'INDUSTRIAL_METALS', 'AGRICULTURE'];

// ── Yardımcılar ───────────────────────────────────────────────────────────────

export function fmt(v, dec = 2) {
  if (v == null || isNaN(parseFloat(v))) return '-';
  return parseFloat(v).toLocaleString('en-US', {
    minimumFractionDigits: dec,
    maximumFractionDigits: dec,
  });
}

export function fmtPct(v) {
  if (v == null || isNaN(parseFloat(v))) return null;
  const n = parseFloat(v);
  return { value: n, text: `${n >= 0 ? '+' : ''}${fmt(n)}%` };
}

export {
  fmtTry,
  fmtUsd,
  getPrimarySpotPrice,
  getTrySpotPrice,
  canToggleTry,
  pickCommoditySpotPriceTry,
  pickCommoditySpotPriceUsd,
  isYahooCommoditySymbol,
} from '../../../../utils/commodityPriceUtils';
