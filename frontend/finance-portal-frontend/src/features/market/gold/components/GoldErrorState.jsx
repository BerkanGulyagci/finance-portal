import { useTranslation } from '../../../../context/LanguageContext';

export default function GoldErrorState({ message }) {
  const { t } = useTranslation();
  return (
    <div className="flex items-center justify-center py-12">
      <p className="text-rose-500 text-sm">{message ?? t('Altın verisi alınamadı.')}</p>
    </div>
  );
}
