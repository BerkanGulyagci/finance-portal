import { ChevronLeft, ChevronRight } from 'lucide-react';
import { useTranslation } from '../../../context/LanguageContext';

export default function AdminUsersPagination({ page, hasMore, loading, onPrev, onNext }) {
  const { t } = useTranslation();
  const pageNumber = page + 1;

  return (
    <section className="flex items-center justify-between gap-3 px-4 py-3 border-t border-gray-100 bg-gray-50/50">
      <p className="text-xs text-gray-500 font-medium">{t('Sayfa')} {pageNumber}</p>
      <div className="flex items-center gap-2">
        <button
          type="button"
          onClick={onPrev}
          disabled={loading || page === 0}
          className="inline-flex items-center gap-1 px-3 py-1.5 rounded-lg text-xs font-bold border border-gray-200 bg-white text-gray-700 hover:bg-gray-50 disabled:opacity-40"
        >
          <ChevronLeft className="w-4 h-4" />
          {t('Önceki')}
        </button>
        <button
          type="button"
          onClick={onNext}
          disabled={loading || !hasMore}
          className="inline-flex items-center gap-1 px-3 py-1.5 rounded-lg text-xs font-bold border border-gray-200 bg-white text-gray-700 hover:bg-gray-50 disabled:opacity-40"
        >
          {t('Sonraki')}
          <ChevronRight className="w-4 h-4" />
        </button>
      </div>
    </section>
  );
}
