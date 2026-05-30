import { LayoutDashboard } from 'lucide-react';
import { addPfChart } from '../../../../utils/dashboardCharts';
import { useToast } from '../../../../context/ToastContext';
import { useTranslation } from '../../../../context/LanguageContext';

/**
 * Portföy analiz kartını Dashboard'a ekler. Dashboard'da hangi portföyden geldiği yazılır.
 */
export default function AddToDashboardButton({ portfolioId, portfolioName, chartKey }) {
  const toast = useToast();
  const { t } = useTranslation();

  function onClick(e) {
    e.preventDefault();
    e.stopPropagation();
    if (!portfolioId) return;
    const added = addPfChart({ portfolioId, portfolioName, chartKey });
    toast[added ? 'success' : 'error'](added ? t('Dashboard\'a eklendi.') : t('Bu kart zaten dashboard\'da.'));
  }

  return (
    <button
      type="button"
      onClick={onClick}
      title={t('Dashboard\'a ekle')}
      className="p-1 rounded-md text-gray-300 hover:text-[#093eaa] hover:bg-[#093eaa]/5 transition-colors"
    >
      <LayoutDashboard className="w-4 h-4" />
    </button>
  );
}
