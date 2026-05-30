import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { ArrowLeft, ExternalLink, Newspaper, Clock, User, Link2 } from 'lucide-react';
import { getNewsDetail, proxyImageUrl } from '../../api/newsApi';
import { Sidebar } from './components/Sidebar';
import { useTranslation } from '../../i18n/LanguageContext';
import { categoryBadgeClass, formatNewsDate, formatNewsTime } from '../../utils/newsUtils';

function ShareButtons({ url, title }) {
  const enc = encodeURIComponent(url || window.location.href);
  const encT = encodeURIComponent(title || '');
  const links = [
    { label: 'X', text: '𝕏', href: `https://twitter.com/intent/tweet?url=${enc}&text=${encT}` },
    { label: 'Facebook', text: 'f', href: `https://www.facebook.com/sharer/sharer.php?u=${enc}` },
    { label: 'LinkedIn', text: 'in', href: `https://www.linkedin.com/sharing/share-offsite/?url=${enc}` },
  ];
  return (
    <div className="flex items-center gap-2">
      {links.map(({ label, text, href }) => (
        <a
          key={label}
          href={href}
          target="_blank"
          rel="noopener noreferrer"
          title={label}
          className="flex h-9 w-9 items-center justify-center rounded-md bg-gray-100 text-gray-700 text-sm font-bold hover:bg-gray-200 transition-colors"
        >
          {text}
        </a>
      ))}
      <button
        onClick={() => navigator.clipboard?.writeText(url || window.location.href)}
        title="Linki kopyala"
        className="flex h-9 w-9 items-center justify-center rounded-md bg-gray-100 text-gray-600 hover:bg-gray-200 transition-colors"
      >
        <Link2 className="w-4 h-4" />
      </button>
    </div>
  );
}

function RelatedCard({ item, t }) {
  return (
    <Link
      to={`/news/${item.id}`}
      className="group flex gap-3 rounded-md border border-gray-100 bg-white p-2.5 shadow-sm hover:shadow-md transition-shadow"
    >
      <div className="w-20 h-16 rounded-md overflow-hidden shrink-0 bg-gray-100">
        {item.imageUrl ? (
          <div className="w-full h-full bg-cover bg-center" style={{ backgroundImage: `url('${proxyImageUrl(item.imageUrl)}')` }} />
        ) : (
          <div className={`w-full h-full flex items-center justify-center ${categoryBadgeClass(item.category)}`}>
            <Newspaper className="w-5 h-5 opacity-50" />
          </div>
        )}
      </div>
      <div className="min-w-0">
        <h4 className="text-sm font-semibold text-gray-900 leading-snug line-clamp-2 group-hover:text-[#093eaa] transition-colors">
          {item.title}
        </h4>
        <p className="mt-1 text-xs text-gray-400">{item.source} · {formatNewsTime(item.publishedAt, t)}</p>
      </div>
    </Link>
  );
}

export default function NewsDetailPage() {
  const { id } = useParams();
  const { t, language } = useTranslation();
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(false);
    window.scrollTo(0, 0);
    getNewsDetail(id, language)
      .then(d => { if (!cancelled) setData(d); })
      .catch(() => { if (!cancelled) setError(true); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [id, language]);

  const article = data?.article;
  const related = data?.related ?? [];
  const content = data?.content;
  const paragraphs = content ? content.split(/\n\n+/).filter(p => p.trim()) : [];

  return (
    <div className="max-w-[1180px] mx-auto grid grid-cols-1 lg:grid-cols-4 gap-8">
      <div className="lg:col-span-3">
        <Link to="/news" className="inline-flex items-center gap-1.5 text-sm font-medium text-gray-500 hover:text-[#093eaa] mb-4">
          <ArrowLeft className="w-4 h-4" /> {t('Haberlere dön')}
        </Link>

        {loading && (
          <div className="animate-pulse space-y-4">
            <div className="h-8 bg-gray-200 rounded-md w-3/4" />
            <div className="h-4 bg-gray-200 rounded-md w-1/3" />
            <div className="h-72 bg-gray-200 rounded-md" />
            <div className="h-4 bg-gray-200 rounded-md w-full" />
            <div className="h-4 bg-gray-200 rounded-md w-5/6" />
          </div>
        )}

        {!loading && (error || !article) && (
          <div className="text-center py-20 text-gray-400">
            <Newspaper className="w-12 h-12 mx-auto mb-4 opacity-40" />
            <p className="text-sm">{t('Haber bulunamadı veya artık güncel listede değil.')}</p>
            <Link to="/news" className="inline-block mt-4 text-sm font-semibold text-[#093eaa]">{t('Haberlere dön')}</Link>
          </div>
        )}

        {!loading && article && (
          <article className="rounded-md border border-gray-100 bg-white p-5 sm:p-7 shadow-sm">
            {article.categoryLabel && (
              <span className={`inline-block text-xs font-semibold px-2.5 py-1 rounded-md mb-3 ${categoryBadgeClass(article.category)}`}>
                {article.categoryLabel}
              </span>
            )}
            <h1 className="text-2xl sm:text-3xl font-extrabold text-gray-900 leading-tight">{article.title}</h1>

            <div className="flex flex-wrap items-center gap-x-4 gap-y-1 mt-4 text-sm text-gray-500">
              <span className="inline-flex items-center gap-1.5 font-semibold text-gray-700">
                <Newspaper className="w-4 h-4 text-[#093eaa]" /> {article.source}
              </span>
              {article.author && (
                <span className="inline-flex items-center gap-1.5"><User className="w-4 h-4" /> {article.author}</span>
              )}
              <span className="inline-flex items-center gap-1.5"><Clock className="w-4 h-4" /> {formatNewsDate(article.publishedAt)}</span>
            </div>

            <div className="mt-5 rounded-md overflow-hidden bg-gray-100">
              {article.imageUrl ? (
                <img src={proxyImageUrl(article.imageUrl)} alt={article.title} className="w-full max-h-[440px] object-cover" loading="lazy" />
              ) : (
                <div className={`w-full h-56 flex flex-col items-center justify-center gap-2 ${categoryBadgeClass(article.category)}`}>
                  <Newspaper className="w-10 h-10 opacity-50" />
                  <span className="text-sm font-semibold opacity-70">{article.source}</span>
                </div>
              )}
            </div>

            {/* Özet (lead) */}
            {article.description && (
              <p className="mt-6 text-[16px] leading-8 font-medium text-gray-800">{article.description}</p>
            )}

            {/* Tam metin (best-effort) */}
            {paragraphs.length > 0 && (
              <div className="mt-4 space-y-4">
                {paragraphs.map((para, i) => (
                  <p key={i} className="text-[15px] leading-8 text-gray-700">{para}</p>
                ))}
              </div>
            )}

            <div className="mt-7 flex flex-wrap items-center justify-between gap-4 border-t border-gray-100 pt-5">
              {article.url && (
                <a
                  href={article.url}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="inline-flex items-center gap-2 rounded-md bg-[#093eaa] px-5 py-2.5 text-sm font-bold text-white hover:bg-[#072f80] transition-colors"
                >
                  {t('Haberin devamını kaynakta oku')} <ExternalLink className="w-4 h-4" />
                </a>
              )}
              <ShareButtons url={article.url} title={article.title} />
            </div>
          </article>
        )}

        {!loading && related.length > 0 && (
          <section className="mt-8">
            <h2 className="text-lg font-bold text-gray-900 mb-3">{t('Bunlar da İlginizi Çekebilir')}</h2>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              {related.map(item => <RelatedCard key={item.id} item={item} t={t} />)}
            </div>
          </section>
        )}
      </div>

      <Sidebar />
    </div>
  );
}
