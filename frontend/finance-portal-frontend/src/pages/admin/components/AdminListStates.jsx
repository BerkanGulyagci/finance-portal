import { Users } from 'lucide-react';
import { useTranslation } from '../../../i18n/LanguageContext';

export function AdminLoadingState() {
  const { t } = useTranslation();
  return <p className="p-12 text-center text-gray-500 text-sm">{t('Yükleniyor...')}</p>;
}

export function AdminErrorState({ message, onRetry }) {
  const { t } = useTranslation();
  return (
    <section className="p-12 text-center">
      <p className="text-rose-600 text-sm font-semibold mb-3">{message}</p>
      <button
        type="button"
        onClick={onRetry}
        className="text-[#093eaa] text-sm font-bold hover:underline"
      >
        {t('Tekrar dene')}
      </button>
    </section>
  );
}

export function AdminEmptyState({ message }) {
  const { t } = useTranslation();
  const text = message ?? t('Kullanıcı bulunamadı.');
  return (
    <section className="p-12 text-center">
      <Users className="w-10 h-10 text-gray-300 mx-auto mb-3" />
      <p className="text-gray-500 text-sm">{text}</p>
    </section>
  );
}
