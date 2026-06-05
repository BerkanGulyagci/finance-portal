import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Sparkles } from 'lucide-react';
import { getForMeNews, proxyImageUrl } from '../../../api/newsApi';
import { useAuth } from '../../../context/AuthContext';
import { useTranslation } from '../../../context/LanguageContext';
import { categoryBadgeClass, formatNewsTime } from '../../../utils/newsUtils';

/** Giriş yapan kullanıcının portföyüne göre "Size Özel" haber şeridi (feed üstünde). Boşsa/anonimse gizli. */
export function PersonalNewsStrip() {
  const { isAuthenticated } = useAuth();
  const { t, language } = useTranslation();
  const [items, setItems] = useState([]);

  useEffect(() => {
    if (!isAuthenticated) { setItems([]); return undefined; }
    let cancelled = false;
    getForMeNews({ lang: language, limit: 9 })
      .then(d => { if (!cancelled) setItems(d.items ?? []); })
      .catch(() => { if (!cancelled) setItems([]); });
    return () => { cancelled = true; };
  }, [isAuthenticated, language]);

  if (!isAuthenticated || items.length === 0) return null;

  return (
    <div className="mb-4 rounded-md border border-[#093eaa]/20 bg-[#093eaa]/[0.03] p-4">
      <h3 className="text-sm font-bold text-gray-800 flex items-center gap-2 mb-3">
        <Sparkles className="w-4 h-4 text-[#093eaa]" /> {t('Size Özel')}
        <span className="text-xs font-normal text-gray-400">· {t('portföyünüze göre')}</span>
      </h3>
      <div className="flex gap-3 overflow-x-auto m3-scroll pb-1">
        {items.map(item => (
          <Link
            key={item.id}
            to={`/news/${item.id}`}
            className="group shrink-0 w-60 rounded-md border border-gray-100 bg-white shadow-sm hover:shadow-md transition-shadow overflow-hidden"
          >
            <div className="h-28 bg-gray-100 overflow-hidden">
              {item.imageUrl ? (
                <div
                  className="w-full h-full bg-cover bg-center group-hover:scale-105 transition-transform duration-500"
                  style={{ backgroundImage: `url('${proxyImageUrl(item.imageUrl)}')` }}
                />
              ) : (
                <div className={`w-full h-full flex items-center justify-center ${categoryBadgeClass(item.category)}`}>
                  <span className="text-xs font-semibold opacity-70">{item.source}</span>
                </div>
              )}
            </div>
            <div className="p-2.5">
              <div className="flex items-center gap-1.5 mb-1 flex-wrap">
                {item.categoryLabel && (
                  <span className={`text-[10px] font-semibold px-1.5 py-0.5 rounded ${categoryBadgeClass(item.category)}`}>
                    {item.categoryLabel}
                  </span>
                )}
                <span className="text-[10px] text-gray-400">{formatNewsTime(item.publishedAt, t)}</span>
              </div>
              <h4 className="text-sm font-bold text-gray-900 leading-snug line-clamp-2 group-hover:text-[#093eaa] transition-colors">
                {item.title}
              </h4>
            </div>
          </Link>
        ))}
      </div>
    </div>
  );
}
