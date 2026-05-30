import { RefreshCw, Shield } from 'lucide-react';
import { useTranslation } from '../../../context/LanguageContext';

export default function AdminPageHeader({ title, description, loading, onRefresh }) {
  const { t } = useTranslation();
  return (
    <header className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 mb-6">
      <section>
        <p className="flex items-center gap-2 text-[#093eaa] mb-1">
          <Shield className="w-5 h-5" />
          <span className="text-xs font-bold uppercase tracking-wider">{t('Yönetim')}</span>
        </p>
        <h1 className="text-2xl font-bold text-gray-900">{title}</h1>
        {description && <p className="text-sm text-gray-500 mt-1">{description}</p>}
      </section>
      <button
        type="button"
        onClick={onRefresh}
        disabled={loading}
        className="inline-flex items-center gap-2 border border-gray-200 bg-white text-gray-700 px-4 py-2 rounded-xl text-sm font-bold hover:bg-gray-50 disabled:opacity-50"
      >
        <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
        {t('Yenile')}
      </button>
    </header>
  );
}
