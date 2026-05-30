import { computeTrend, TREND_SIGNAL, TREND_METHOD_LABEL } from '../../utils/trendUtils';
import { useTranslation } from '../../context/LanguageContext';

/**
 * TrendBadge
 * ─────────────────────────────────────────────────────────────────────────────
 * Herhangi bir varlık nesnesi için evrensel trend rozeti.
 * computeTrend(item) fonksiyonunu kullanır — hisse, kripto, emtia, fon, döviz,
 * tahvil gibi tüm varlık tiplerinde çalışır.
 *
 * Kullanım:
 *   <TrendBadge item={holding} />
 *   <TrendBadge item={commoditySpot} size="xs" />
 *   <TrendBadge item={fund} showMethod />
 *
 * Props:
 *   item       {object}  - Herhangi bir piyasa/varlık nesnesi
 *   size       {'xs'|'sm'|'md'}  - Yazı boyutu (varsayılan: 'sm')
 *   showMethod {boolean} - Hesaplama yöntemini badge'in yanında göster (debug)
 */
export default function TrendBadge({ item, size = 'sm', showMethod = false }) {
  const { t } = useTranslation();
  const { signal, method } = computeTrend(item);
  const tooltip = t(TREND_METHOD_LABEL[method] ?? method);

  if (!signal) {
    return (
      <span className="text-gray-300 text-sm select-none" title={tooltip}>
        —
      </span>
    );
  }

  const SIZE_CLS = {
    xs: 'text-xs gap-0.5 px-1.5 py-0.5',
    sm: 'text-sm gap-1   px-2   py-0.5',
    md: 'text-sm gap-1   px-2.5 py-1',
  };

  const CFG = {
    [TREND_SIGNAL.UP]: {
      icon:   '▲',
      label:  'Yükseliş',
      text:   'text-emerald-700',
      bg:     'bg-emerald-50',
      border: 'border-emerald-200',
    },
    [TREND_SIGNAL.DOWN]: {
      icon:   '▼',
      label:  'Düşüş',
      text:   'text-rose-700',
      bg:     'bg-rose-50',
      border: 'border-rose-200',
    },
    [TREND_SIGNAL.SIDEWAYS]: {
      icon:   '→',
      label:  'Yatay',
      text:   'text-amber-700',
      bg:     'bg-amber-50',
      border: 'border-amber-200',
    },
  }[signal];

  return (
    <span className="inline-flex items-center gap-1.5" title={tooltip}>
      <span
        className={`inline-flex items-center font-semibold rounded-full border
          ${SIZE_CLS[size] ?? SIZE_CLS.sm}
          ${CFG.text} ${CFG.bg} ${CFG.border}`}
      >
        <span aria-hidden="true">{CFG.icon}</span>
        {t(CFG.label)}
      </span>
      {showMethod && (
        <span className="text-[10px] text-gray-400 font-mono">[{method}]</span>
      )}
    </span>
  );
}
