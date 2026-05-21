import { Link } from 'react-router-dom';
import { ShieldAlert } from 'lucide-react';
import { useTranslation } from '../../i18n/LanguageContext';

export default function UnauthorizedPage() {
  const { t } = useTranslation();
  return (
    <div className="min-h-screen bg-[#f5f6f8] flex items-center justify-center px-4">
      <div className="bg-white rounded-2xl shadow-sm border border-gray-200 p-8 max-w-md w-full text-center">
        <div className="bg-amber-50 w-14 h-14 rounded-xl flex items-center justify-center mx-auto mb-4">
          <ShieldAlert className="w-7 h-7 text-amber-600" />
        </div>
        <h1 className="text-xl font-bold text-gray-900 mb-2">{t('Erişim Reddedildi')}</h1>
        <p className="text-sm text-gray-500 mb-6">
          {t('Bu sayfaya yalnızca yönetici (ADMIN) hesapları erişebilir.')}
        </p>
        <div className="flex flex-col sm:flex-row gap-2 justify-center">
          <Link
            to="/news"
            className="bg-[#093eaa] text-white px-5 py-2.5 rounded-xl text-sm font-bold hover:bg-[#093eaa]/90 transition-all"
          >
            {t('Ana Sayfaya Dön')}
          </Link>
          <Link
            to="/portfolio"
            className="border border-gray-200 text-gray-700 px-5 py-2.5 rounded-xl text-sm font-bold hover:bg-gray-50 transition-all"
          >
            {t('Portföyüm')}
          </Link>
        </div>
      </div>
    </div>
  );
}
