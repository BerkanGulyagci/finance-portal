import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { RefreshCw, Newspaper } from 'lucide-react';
import { getNews, proxyImageUrl } from '../../api/newsApi';
import { useTranslation } from '../../i18n/LanguageContext';
import { categoryBadgeClass, formatNewsTime } from '../../utils/newsUtils';

const PAGE_SIZE = 12;

function ImageBox({ item, className }) {
  const src = item.imageUrl ? proxyImageUrl(item.imageUrl) : null;
  return (
    <div className={`overflow-hidden bg-gray-100 ${className}`}>
      {src ? (
        <div
          className="w-full h-full bg-cover bg-center group-hover:scale-105 transition-transform duration-500"
          style={{ backgroundImage: `url('${src}')` }}
        />
      ) : (
        <div className={`w-full h-full flex flex-col items-center justify-center gap-1 ${categoryBadgeClass(item.category)}`}>
          <Newspaper className="w-7 h-7 opacity-50" />
          <span className="text-[10px] font-semibold opacity-70">{item.source}</span>
        </div>
      )}
    </div>
  );
}

function NewsCard({ item, t }) {
  return (
    <Link
      to={`/news/${item.id}`}
      className="group flex flex-col sm:flex-row gap-4 rounded-md border border-gray-100 bg-white p-3 shadow-sm hover:shadow-md transition-shadow"
    >
      <ImageBox item={item} className="w-full sm:w-52 h-40 sm:h-32 rounded-md shrink-0" />
      <div className="flex-1 min-w-0 py-0.5">
        <div className="flex items-center gap-2 mb-1.5 flex-wrap">
          {item.categoryLabel && (
            <span className={`text-[11px] font-semibold px-2 py-0.5 rounded-md ${categoryBadgeClass(item.category)}`}>
              {item.categoryLabel}
            </span>
          )}
          <span className="text-xs text-gray-400">{item.source}</span>
          <span className="text-xs text-gray-300">·</span>
          <span className="text-xs text-gray-400">{formatNewsTime(item.publishedAt, t)}</span>
        </div>
        <h3 className="text-base font-bold text-gray-900 leading-snug line-clamp-2 group-hover:text-[#093eaa] transition-colors">
          {item.title}
        </h3>
        {item.description && (
          <p className="mt-1.5 text-sm text-gray-500 leading-relaxed line-clamp-2">{item.description}</p>
        )}
      </div>
    </Link>
  );
}

/** Haber listesi. Filtreler (bölge/tür/kaynak/tarih/arama) NewsPage'den prop ile gelir; facet'leri onFacets ile geri bildirir. */
export function NewsFeed({ filters, onFacets }) {
  const { t, language } = useTranslation();
  const [items, setItems] = useState([]);
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    getNews({ ...filters, page: 1, pageSize: PAGE_SIZE, lang: language })
      .then(data => {
        if (cancelled) return;
        setItems(data.items ?? []);
        setPage(data.page ?? 1);
        setTotalPages(data.totalPages ?? 1);
        setTotal(data.totalElements ?? 0);
        onFacets?.({ categories: data.categories ?? [], sources: data.sources ?? [] });
      })
      .catch(() => { if (!cancelled) setItems([]); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [filters, onFacets, language]);

  function loadMore() {
    const next = page + 1;
    setLoading(true);
    getNews({ ...filters, page: next, pageSize: PAGE_SIZE, lang: language })
      .then(data => {
        setItems(prev => [...prev, ...(data.items ?? [])]);
        setPage(data.page ?? next);
      })
      .catch(() => {})
      .finally(() => setLoading(false));
  }

  return (
    <div className="space-y-4">
      <p className="text-xs text-gray-400 px-1">{total} {t('haber')}</p>

      {loading && items.length === 0 ? (
        <div className="space-y-3">
          {[1, 2, 3, 4].map(i => (
            <div key={i} className="flex gap-4 animate-pulse rounded-md border border-gray-100 bg-white p-3">
              <div className="w-52 h-32 bg-gray-200 rounded-md shrink-0" />
              <div className="flex-1 space-y-3 py-2">
                <div className="h-3 bg-gray-200 rounded-md w-1/4" />
                <div className="h-5 bg-gray-200 rounded-md w-3/4" />
                <div className="h-4 bg-gray-200 rounded-md w-full" />
              </div>
            </div>
          ))}
        </div>
      ) : items.length === 0 ? (
        <div className="text-center py-16 text-gray-400">
          <Newspaper className="w-10 h-10 mx-auto mb-3 opacity-40" />
          <p className="text-sm">{t('Bu filtrelerde haber bulunamadı.')}</p>
        </div>
      ) : (
        <div className="space-y-3">
          {items.map(item => <NewsCard key={item.id} item={item} t={t} />)}
        </div>
      )}

      {!loading && page < totalPages && items.length > 0 && (
        <div className="flex justify-center pt-2">
          <button
            onClick={loadMore}
            className="inline-flex items-center gap-2 rounded-md border border-gray-200 bg-white px-6 py-2.5 text-sm font-semibold text-gray-700 hover:bg-gray-50 transition-colors"
          >
            {t('Daha Fazla Yükle')}
            <RefreshCw className="w-4 h-4" />
          </button>
        </div>
      )}
    </div>
  );
}
