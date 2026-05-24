import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Clock, TrendingUp, Send } from 'lucide-react';
import { getBloombergHtNews, proxyImageUrl } from '../../api/newsApi';
import { useAuth } from '../../context/AuthContext';
import { useTranslation } from '../../i18n/LanguageContext';
import NewsletterModal from './NewsletterModal';

function formatTime(raw, t) {
  if (!raw) return '';
  try {
    const diff = Math.floor((Date.now() - new Date(raw).getTime()) / 60000);
    if (diff < 60) return `${diff} ${t('dk önce')}`;
    if (diff < 1440) return `${Math.floor(diff / 60)} ${t('saat önce')}`;
    return new Date(raw).toLocaleDateString('tr-TR');
  } catch { return ''; }
}

export function HeroSection() {
  const { t } = useTranslation();
  const { isAuthenticated, email } = useAuth();
  const navigate = useNavigate();
  const [newsletterOpen, setNewsletterOpen] = useState(false);
  const [featured, setFeatured] = useState(null);
  const [popular, setPopular] = useState([]);

  function handleNewsletter() {
    if (isAuthenticated) setNewsletterOpen(true);
    else navigate('/register');
  }

  useEffect(() => {
    getBloombergHtNews().then(news => {
      if (news.length > 0) setFeatured(news[0]);
      setPopular(news.slice(1, 3));
    }).catch(() => {});
  }, []);

  return (
    <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 mb-12">
      {/* Main Hero Card */}
      <div className="lg:col-span-2 relative group cursor-pointer overflow-hidden rounded-xl shadow-lg h-[450px] bg-gray-800">
        <div className="absolute inset-0 bg-gradient-to-t from-black/90 via-black/40 to-transparent z-10" />
        {featured?.imageUrl ? (
          <div
            className="absolute inset-0 bg-cover bg-center group-hover:scale-105 transition-transform duration-500"
            style={{ backgroundImage: `url('${proxyImageUrl(featured.imageUrl)}')` }}
          />
        ) : (
          <div
            className="absolute inset-0 bg-cover bg-center group-hover:scale-105 transition-transform duration-500"
            style={{ backgroundImage: "url('https://images.unsplash.com/photo-1611974789855-9c2a0a7236a3?w=1200&h=800&fit=crop')" }}
          />
        )}
        <div className="absolute bottom-0 left-0 p-8 z-20">
          <span className="bg-[#093eaa] text-white text-xs font-bold px-3 py-1 rounded mb-4 inline-block">
            {t('SON DAKİKA')}
          </span>
          <h2 className="text-3xl md:text-4xl font-bold text-white mb-4 leading-tight">
            {featured?.title ?? t('Piyasalarda son gelişmeler')}
          </h2>
          {featured?.description && (
            <p className="text-slate-200 text-base mb-6 line-clamp-2">{featured.description}</p>
          )}
          <div className="flex items-center gap-4 text-white text-sm">
            <span className="flex items-center gap-1">
              <Clock className="w-4 h-4" /> {formatTime(featured?.publishedAt, t)}
            </span>
          </div>
        </div>
      </div>

      {/* Right Panel */}
      <div className="space-y-6">
        {/* Popular */}
        <div className="bg-white p-6 rounded-xl shadow-sm border border-gray-200">
          <h3 className="font-bold text-lg mb-4 flex items-center gap-2">
            <TrendingUp className="w-5 h-5 text-[#093eaa]" /> {t('Öne Çıkan Haberler')}
          </h3>
          <div className="space-y-4">
            {popular.length === 0 && <p className="text-sm text-gray-400">{t('Yükleniyor...')}</p>}
            {popular.map((item, i) => (
              <div key={i} className={`flex gap-3 group cursor-pointer ${i > 0 ? 'border-t border-gray-100 pt-4' : ''}`}>
                {item.imageUrl && (
                  <div className="w-20 h-20 rounded-lg overflow-hidden flex-shrink-0">
                    <div className="w-full h-full bg-cover bg-center group-hover:scale-110 transition-transform"
                      style={{ backgroundImage: `url('${proxyImageUrl(item.imageUrl)}')` }} />
                  </div>
                )}
                <div>
                  <h4 className="text-sm font-bold group-hover:text-[#093eaa] transition-colors leading-snug">
                    {item.url
                      ? <a href={item.url} target="_blank" rel="noopener noreferrer">{item.title}</a>
                      : item.title}
                  </h4>
                  <span className="text-[10px] text-gray-400 uppercase mt-1 inline-block">
                    {item.source} • {formatTime(item.publishedAt, t)}
                  </span>
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Newsletter */}
        <div className="bg-blue-50 p-6 rounded-xl border border-blue-100">
          <h3 className="font-bold text-[#093eaa] mb-2">{t('Finans Bültenine Katılın')}</h3>
          <p className="text-xs text-gray-500 mb-4">
            {isAuthenticated
              ? t('Panonuzun özetini istediğiniz sıklıkta e-posta ile alın.')
              : t('Her sabah piyasa açılışından önce en kritik veriler e-postanızda olsun.')}
          </p>
          {isAuthenticated ? (
            <button
              onClick={handleNewsletter}
              className="w-full inline-flex items-center justify-center gap-2 bg-[#093eaa] text-white text-sm font-bold py-2.5 rounded-lg hover:bg-[#093eaa]/90 transition-colors"
            >
              <Send className="w-4 h-4" /> {t('Bülten Aboneliği')}
            </button>
          ) : (
            <div className="flex gap-2">
              <input
                type="email"
                placeholder={t('E-posta adresiniz')}
                onFocus={handleNewsletter}
                className="bg-white border border-gray-200 text-sm rounded-lg flex-1 px-3 py-2 focus:outline-none focus:ring-2 focus:ring-[#093eaa]"
              />
              <button onClick={handleNewsletter} className="bg-[#093eaa] text-white p-2 rounded-lg hover:bg-[#093eaa]/90 transition-colors">
                <Send className="w-4 h-4" />
              </button>
            </div>
          )}
        </div>
      </div>

      {newsletterOpen && <NewsletterModal email={email} onClose={() => setNewsletterOpen(false)} />}
    </div>
  );
}
