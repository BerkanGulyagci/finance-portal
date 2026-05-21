import { Link } from 'react-router-dom';
import { Wallet } from 'lucide-react';
import { useTranslation } from '../i18n/LanguageContext';

export default function NotFoundPage() {
  const { t } = useTranslation();
  return (
    <div className="min-h-screen bg-[#f5f6f8] flex items-center justify-center px-4">
      <div className="text-center">
        <div className="flex items-center justify-center gap-3 mb-8">
          <div className="bg-[#093eaa] p-2 rounded-xl">
            <Wallet className="w-7 h-7 text-white" />
          </div>
          <h1 className="text-2xl font-bold">
            <span className="text-[#093eaa]">{t('Finans')}</span>
            <span className="text-gray-900">{t('Portalı')}</span>
          </h1>
        </div>
        <p className="text-8xl font-black text-gray-200 mb-4">404</p>
        <h2 className="text-2xl font-bold text-gray-900 mb-2">{t('Sayfa Bulunamadı')}</h2>
        <p className="text-gray-500 mb-8">{t('Aradığınız sayfa mevcut değil veya taşınmış olabilir.')}</p>
        <Link to="/news"
          className="bg-[#093eaa] text-white px-6 py-3 rounded-xl font-bold text-sm hover:bg-[#093eaa]/90 transition-all inline-block">
          {t('Ana Sayfaya Dön')}
        </Link>
      </div>
    </div>
  );
}
