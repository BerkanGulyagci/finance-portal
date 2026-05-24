import { Link } from 'react-router-dom';
import { Star, Plus, X, Mail } from 'lucide-react';
import DashCard from './DashCard';
import { fmtMoney, fmtPct, pctClass, num, ASSET_LABEL } from './dashUtils';
import { getWatchlistDetailPath } from '../portfolio/constants/watchlistMarketRoutes';
import { useTranslation } from '../../i18n/LanguageContext';

/**
 * Favoriler — izleme listesindeki varlıklar; ekleme/çıkarma yapılabilir.
 */
export default function FavoritesCard({ items, onAdd, onRemove }) {
  const { t } = useTranslation();

  const action = (
    <button onClick={onAdd} className="inline-flex items-center gap-1 text-xs font-semibold text-[#093eaa] hover:underline">
      <Plus className="w-3.5 h-3.5" /> {t('Ekle')}
    </button>
  );

  return (
    <DashCard title={t('Favoriler')} icon={Star} action={action} accent="#f59e0b" scroll>
      <p className="text-[11px] text-gray-400 mb-2 flex items-start gap-1">
        <Mail className="w-3 h-3 mt-0.5 shrink-0" />
        {t('Bu listedeki varlıklar bülten özetinizde de gösterilir.')}
      </p>
      {items.length === 0 ? (
        <div className="py-6 text-center">
          <p className="text-sm text-gray-400">{t('Henüz favoriniz yok.')}</p>
          <button onClick={onAdd} className="mt-2 inline-flex items-center gap-1 text-xs font-semibold text-[#093eaa] hover:underline">
            <Plus className="w-3.5 h-3.5" /> {t('Favori ekle')}
          </button>
        </div>
      ) : (
        <ul className="divide-y divide-gray-50">
          {items.map(it => {
            const pct = it.changePercent != null ? num(it.changePercent) : null;
            const path = getWatchlistDetailPath(it.assetType, it.symbol);
            const row = (
              <span className="flex items-center justify-between gap-2 flex-1 min-w-0 -mx-1 px-1 py-0.5 rounded-lg hover:bg-gray-50 transition-colors">
                <span className="flex items-center gap-2 min-w-0">
                  <Star className="w-3.5 h-3.5 text-amber-400 fill-amber-400 shrink-0" />
                  <span className="min-w-0">
                    <span className="block text-sm font-semibold text-gray-900 truncate">{it.symbol}</span>
                    <span className="block text-[10px] text-gray-400">{t(ASSET_LABEL[it.assetType] ?? it.assetType)}</span>
                  </span>
                </span>
                <span className="text-right shrink-0">
                  {it.lastPrice != null && <span className="block text-sm font-bold text-gray-800 tabular-nums">₺{fmtMoney(it.lastPrice)}</span>}
                  {pct != null && <span className={`block text-[11px] font-semibold tabular-nums ${pctClass(pct)}`}>{fmtPct(pct)}</span>}
                </span>
              </span>
            );
            return (
              <li key={`${it.portfolioId}:${it.id}`} className="group flex items-center gap-1.5 py-1.5">
                {path ? <Link to={path} className="flex-1 min-w-0">{row}</Link> : <div className="flex-1 min-w-0">{row}</div>}
                <button
                  onClick={() => onRemove(it)}
                  title={t('Favorilerden çıkar')}
                  className="opacity-0 group-hover:opacity-100 transition-opacity p-1 rounded-md text-gray-300 hover:text-rose-500 hover:bg-rose-50 shrink-0"
                >
                  <X className="w-4 h-4" />
                </button>
              </li>
            );
          })}
        </ul>
      )}
    </DashCard>
  );
}
