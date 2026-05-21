import { Eye, Briefcase } from 'lucide-react';
import { useTranslation } from '../../../i18n/LanguageContext';

/**
 * portfolioType değerine göre renkli badge gösterir.
 * WATCHLIST → mavi/gökyüzü
 * HOLDINGS  → yeşil
 */
export default function PortfolioTypeBadge({ type }) {
  const { t } = useTranslation();
  if (type === 'WATCHLIST') {
    return (
      <span className="inline-flex items-center gap-1 text-xs font-bold bg-sky-50 text-sky-600 border border-sky-200 px-2 py-0.5 rounded-full">
        <Eye className="w-3 h-3" />
        {t('İzleme Listesi')}
      </span>
    );
  }
  return (
    <span className="inline-flex items-center gap-1 text-xs font-bold bg-emerald-50 text-emerald-700 border border-emerald-200 px-2 py-0.5 rounded-full">
      <Briefcase className="w-3 h-3" />
      {t('Varlık Portföyü')}
    </span>
  );
}
