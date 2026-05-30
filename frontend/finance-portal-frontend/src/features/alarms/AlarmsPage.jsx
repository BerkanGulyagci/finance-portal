import { Link } from 'react-router-dom';
import { ArrowLeft, Bell } from 'lucide-react';
import AlarmsManager from '../../components/instrument/AlarmsManager';
import { useTranslation } from '../../context/LanguageContext';

/**
 * "Alarmlarımı Düzenle" sayfası — tüm alarmların listesi + yönetimi.
 */
export default function AlarmsPage() {
  const { t } = useTranslation();
  return (
    <div className="max-w-3xl mx-auto space-y-5">
      <div>
        <Link to="/portfolio" className="inline-flex items-center gap-1.5 text-sm text-[#093eaa] font-semibold hover:underline mb-3">
          <ArrowLeft className="w-4 h-4" /> {t('Portföyüm')}
        </Link>
        <h1 className="text-2xl font-bold text-gray-900 border-l-4 border-[#093eaa] pl-4 flex items-center gap-2">
          {t('Alarmlarımı Düzenle')}
        </h1>
        <p className="text-sm text-gray-500 mt-2 pl-5">
          {t('Fiyat, değişim veya hacim koşulu sağlandığında uygulama-içi bildirim ve onaylı e-postanıza mail alırsınız.')}
        </p>
      </div>

      <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-3 sm:p-6">
        <AlarmsManager />
      </div>
    </div>
  );
}
