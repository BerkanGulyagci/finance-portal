import { useEffect, useState, useRef } from 'react';
import { RefreshCw, Globe, Newspaper } from 'lucide-react';
import { getBloombergHtNews, getNews, proxyImageUrl } from '../../api/newsApi';
import { useTranslation } from '../../i18n/LanguageContext';

function formatTime(raw, t) {
  if (!raw) return '';
  try {
    const diff = Math.floor((Date.now() - new Date(raw).getTime()) / 60000);
    if (diff < 60) return `${diff} ${t('dk önce')}`;
    if (diff < 1440) return `${Math.floor(diff / 60)} ${t('saat önce')}`;
    return new Date(raw).toLocaleDateString('tr-TR');
  } catch { return raw; }
}

const CATEGORY_COLORS = {
  'BloombergHT': 'text-[#093eaa] bg-blue-50',
  'default': 'text-emerald-600 bg-emerald-50',
};

const PAGE_SIZE = 8;

function ArticleList({ articles, loading }) {
  const { t } = useTranslation();
  const [page, setPage] = useState(0);

  // Reset page when articles change (tab switch)
  useEffect(() => { setPage(0); }, [articles]);

  const visible = articles.slice(0, (page + 1) * PAGE_SIZE);
  const hasMore = visible.length < articles.length;

  if (loading) return (
    <div className="space-y-4">
      {[1, 2, 3].map(i => (
        <div key={i} className="flex gap-6 animate-pulse">
          <div className="w-64 h-48 bg-gray-200 rounded-xl flex-shrink-0" />
          <div className="flex-1 space-y-3 py-2">
            <div className="h-4 bg-gray-200 rounded w-1/4" />
            <div className="h-6 bg-gray-200 rounded w-3/4" />
            <div className="h-4 bg-gray-200 rounded w-full" />
          </div>
        </div>
      ))}
    </div>
  );

  if (!articles.length) return <p className="text-gray-400 text-sm py-4">{t('Haber bulunamadı.')}</p>;

  return (
    <>
      <div className="space-y-8">
        {visible.map((article, idx) => (
          <article key={idx} className="flex flex-col md:flex-row gap-6 group cursor-pointer">
            {article.imageUrl && (
              <div className="w-full md:w-64 h-48 rounded-xl overflow-hidden flex-shrink-0 shadow-md">
                <div
                  className="w-full h-full bg-cover bg-center group-hover:scale-110 transition-transform duration-500"
                  style={{ backgroundImage: `url('${proxyImageUrl(article.imageUrl)}')` }}
                />
              </div>
            )}
            <div className="flex-1">
              <div className="flex items-center gap-2 mb-2">
                <span className={`text-xs font-bold px-2 py-0.5 rounded ${CATEGORY_COLORS[article.source] ?? CATEGORY_COLORS.default}`}>
                  {article.source ?? t('HABER')}
                </span>
                <span className="text-xs text-gray-500">{formatTime(article.publishedAt, t)}</span>
              </div>
              <h3 className="text-xl font-bold group-hover:text-[#093eaa] transition-colors mb-3 leading-tight">
                {article.url
                  ? <a href={article.url} target="_blank" rel="noopener noreferrer">{article.title}</a>
                  : article.title}
              </h3>
              {article.description && (
                <p className="text-gray-500 text-sm leading-relaxed line-clamp-2">{article.description}</p>
              )}
            </div>
          </article>
        ))}
      </div>

      {hasMore && (
        <div className="flex justify-center py-8">
          <button
            onClick={() => setPage(p => p + 1)}
            className="border border-gray-200 bg-white px-8 py-3 rounded-xl font-bold hover:bg-gray-50 transition-all flex items-center gap-2 group text-sm"
          >
            {t('Daha Fazla Yükle')}
            <RefreshCw className="w-4 h-4 group-hover:rotate-180 transition-transform duration-500" />
          </button>
        </div>
      )}
    </>
  );
}

export function NewsFeed() {
  const { t } = useTranslation();
  const [activeTab, setActiveTab] = useState('bloomberg');

  // Separate state per tab — lazy loaded, cached after first fetch
  const [bhtArticles, setBhtArticles] = useState([]);
  const [intlArticles, setIntlArticles] = useState([]);
  const [bhtLoading, setBhtLoading] = useState(false);
  const [intlLoading, setIntlLoading] = useState(false);

  const bhtFetched = useRef(false);
  const intlFetched = useRef(false);

  // Load Bloomberg on mount
  useEffect(() => {
    if (bhtFetched.current) return;
    bhtFetched.current = true;
    setBhtLoading(true);
    getBloombergHtNews()
      .then(setBhtArticles)
      .catch(() => setBhtArticles([]))
      .finally(() => setBhtLoading(false));
  }, []);

  // Load international only when tab is clicked for the first time
  function handleTabClick(tab) {
    setActiveTab(tab);
    if (tab === 'intl' && !intlFetched.current) {
      intlFetched.current = true;
      setIntlLoading(true);
      getNews({ category: 'business', country: 'us', pageSize: 50 })
        .then(setIntlArticles)
        .catch(() => setIntlArticles([]))
        .finally(() => setIntlLoading(false));
    }
  }

  const tabs = [
    { key: 'bloomberg', label: t('Gündem Haberleri'), icon: Newspaper },
    { key: 'intl',      label: t('Dünyadan Haberler'), icon: Globe },
  ];

  return (
    <div className="lg:col-span-3 space-y-6">
      {/* Tab bar */}
      <div className="flex gap-1 border-b border-gray-200">
        {tabs.map(({ key, label, icon: Icon }) => (
          <button
            key={key}
            onClick={() => handleTabClick(key)}
            className={`flex items-center gap-2 px-4 py-3 text-sm font-bold border-b-2 transition-colors -mb-px ${
              activeTab === key
                ? 'border-[#093eaa] text-[#093eaa]'
                : 'border-transparent text-gray-500 hover:text-gray-700'
            }`}
          >
            <Icon className="w-4 h-4" />
            {label}
          </button>
        ))}
      </div>

      {/* Content — no page reload, just show/hide */}
      <div className={activeTab === 'bloomberg' ? 'block' : 'hidden'}>
        <ArticleList articles={bhtArticles} loading={bhtLoading} />
      </div>
      <div className={activeTab === 'intl' ? 'block' : 'hidden'}>
        <ArticleList articles={intlArticles} loading={intlLoading} />
      </div>
    </div>
  );
}
