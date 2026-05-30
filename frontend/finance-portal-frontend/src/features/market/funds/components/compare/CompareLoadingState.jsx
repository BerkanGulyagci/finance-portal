import { useTranslation } from '../../../../../context/LanguageContext';

export default function CompareLoadingState({ count = 2 }) {
  const { t } = useTranslation();
  return (
    <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-12 text-center">
      <div className="flex items-center justify-center gap-2 mb-3">
        {[0, 1, 2].map(i => (
          <div key={i} className="w-2.5 h-2.5 bg-[#093eaa] rounded-full animate-bounce"
            style={{ animationDelay: `${i * 100}ms` }} />
        ))}
      </div>
      <p className="text-gray-500 font-semibold">{count} {t('fon için veriler yükleniyor...')}</p>
      <p className="text-gray-400 text-sm mt-1">{t('Rasyonet\'ten detay çekiliyor')}</p>
    </div>
  );
}
