import { BarChart2 } from 'lucide-react';
import { useTranslation } from '../../../../../i18n/LanguageContext';

export default function CompareEmptyState() {
  const { t } = useTranslation();
  return (
    <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-14 text-center">
      <BarChart2 className="w-12 h-12 text-gray-300 mx-auto mb-3" />
      <p className="text-gray-600 font-semibold text-base">{t('Fon seçin ve karşılaştırın')}</p>
      <p className="text-gray-400 text-sm mt-1.5">
        {t('En az 2, en fazla 4 fon seçip')} <span className="font-semibold text-[#093eaa]">{t('Karşılaştır')}</span> {t('butonuna tıklayın')}
      </p>
    </div>
  );
}
