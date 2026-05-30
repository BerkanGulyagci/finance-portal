import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Sparkles } from 'lucide-react';
import { getForMeNews, proxyImageUrl } from '../../../api/newsApi';
import { useTranslation } from '../../../i18n/LanguageContext';
import { formatNewsTime } from '../../../utils/newsUtils';

/** Dashboard kartı: portföye göre "Size Özel Haberler" (ilk birkaç başlık). */
export default function PersonalNewsCard() {
  const { t, language } = useTranslation();
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    getForMeNews({ lang: language, limit: 6 })
      .then(d => { if (!cancelled) setItems(d.items ?? []); })
      .catch(() => { if (!cancelled) setItems([]); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [language]);

  return (
    <div className="bg-white rounded-2xl border border-gray-100 shadow-sm p-3.5 h-full flex flex-col min-w-0">
      <div className="flex items-center justify-between mb-2 shrink-0">
        <h3 className="font-bold text-gray-900 flex items-center gap-2 text-sm">
          <Sparkles className="w-4 h-4 text-[#093eaa]" /> {t('Size Özel Haberler')}
        </h3>
        <Link to="/news" className="text-[11px] font-semibold text-[#093eaa] hover:underline">{t('Tümü')}</Link>
      </div>
      <div className="flex-1 min-h-0 overflow-auto -mx-1 px-1">
        {loading && <p className="text-sm text-gray-400 p-2">{t('Yükleniyor...')}</p>}
        {!loading && items.length === 0 && (
          <p className="text-xs text-gray-400 p-2 leading-relaxed">
            {t('Portföyünüze uygun haber bulunamadı. Portföyünüze varlık ekleyince burada görünür.')}
          </p>
        )}
        {items.map((item, i) => (
          <Link
            key={item.id}
            to={`/news/${item.id}`}
            className={`flex gap-3 py-2 group ${i > 0 ? 'border-t border-gray-100' : ''}`}
          >
            <div className="w-14 h-14 rounded-lg overflow-hidden shrink-0 bg-gray-100">
              {item.imageUrl ? (
                <div className="w-full h-full bg-cover bg-center" style={{ backgroundImage: `url('${proxyImageUrl(item.imageUrl)}')` }} />
              ) : (
                <div className="w-full h-full flex items-center justify-center text-gray-300"><Sparkles className="w-5 h-5" /></div>
              )}
            </div>
            <div className="min-w-0">
              <h4 className="text-xs font-bold text-gray-800 leading-snug line-clamp-2 group-hover:text-[#093eaa] transition-colors">
                {item.title}
              </h4>
              <span className="text-[10px] text-gray-400 mt-0.5 inline-block">{item.source} · {formatNewsTime(item.publishedAt, t)}</span>
            </div>
          </Link>
        ))}
      </div>
    </div>
  );
}
