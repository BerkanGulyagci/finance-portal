import { CATEGORY_META } from './commodityConstants';
import CommodityCard from './CommodityCard';
import { useTranslation } from '../../../../i18n/LanguageContext';

export default function CommodityCategorySection({ category, items, spots, loadingSpots, usdTryRate, onCardClick }) {
  const { t } = useTranslation();
  const catMeta = CATEGORY_META[category];
  if (!catMeta) return null;

  const Icon = catMeta.icon;

  return (
    <section>
      {/* Kategori başlığı */}
      <div className="flex items-center gap-2 mb-4">
        <div className={`p-1.5 rounded-lg ${catMeta.bg}`}>
          <Icon className={`w-4 h-4 ${catMeta.color}`} />
        </div>
        <h2 className="text-base font-bold text-gray-800">{t(catMeta.label)}</h2>
        <span className="text-xs text-gray-400 font-medium">({items.length} {t('emtia')})</span>
      </div>

      {/* Kartlar */}
      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 gap-5">
        {items.map(meta => (
          <CommodityCard
            key={meta.symbol}
            meta={meta}
            spot={spots[meta.symbol] ?? null}
            loading={loadingSpots && !spots[meta.symbol]}
            usdTryRate={usdTryRate}
            onClick={() => onCardClick(meta.symbol)}
          />
        ))}
      </div>
    </section>
  );
}
