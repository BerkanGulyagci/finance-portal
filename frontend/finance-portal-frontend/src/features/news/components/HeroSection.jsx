import { useEffect, useState, useRef } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Clock, TrendingUp, Send } from 'lucide-react';
import { getNews, proxyImageUrl } from '../../../api/newsApi';
import { useAuth } from '../../../context/AuthContext';
import { SkeletonHero } from '../../../components/common/Skeleton';
import { useTranslation } from '../../../context/LanguageContext';
import { formatNewsTime } from '../../../utils/newsUtils';
import NewsletterModal from '../../../components/shared/NewsletterModal';

const SLIDE_COUNT = 10;
const AUTO_MS = 6000;

function NewsletterCard() {
  const { t } = useTranslation();
  const { isAuthenticated, email } = useAuth();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);

  function handleNewsletter() {
    if (isAuthenticated) setOpen(true);
    else navigate('/register');
  }

  return (
    <div className="bg-blue-50 p-5 rounded-md border border-blue-100">
      <h3 className="font-bold text-[#093eaa] mb-2">{t('Finans Bültenine Katılın')}</h3>
      <p className="text-xs text-gray-500 mb-4">
        {isAuthenticated
          ? t('Panonuzun özetini istediğiniz sıklıkta e-posta ile alın.')
          : t('Her sabah piyasa açılışından önce en kritik veriler e-postanızda olsun.')}
      </p>
      {isAuthenticated ? (
        <button
          onClick={handleNewsletter}
          className="w-full inline-flex items-center justify-center gap-2 bg-[#093eaa] text-white text-sm font-bold py-2.5 rounded-md hover:bg-[#072f80] transition-colors"
        >
          <Send className="w-4 h-4" /> {t('Bülten Aboneliği')}
        </button>
      ) : (
        <div className="flex gap-2">
          <input
            type="email"
            placeholder={t('E-posta adresiniz')}
            onFocus={handleNewsletter}
            className="bg-white border border-gray-200 text-sm rounded-md flex-1 px-3 py-2 focus:outline-none focus:ring-2 focus:ring-[#093eaa]/30"
          />
          <button onClick={handleNewsletter} className="bg-[#093eaa] text-white p-2 rounded-md hover:bg-[#072f80] transition-colors">
            <Send className="w-4 h-4" />
          </button>
        </div>
      )}
      {open && <NewsletterModal email={email} onClose={() => setOpen(false)} />}
    </div>
  );
}

export function HeroSection({ filterSlot }) {
  const { t, language } = useTranslation();
  const [items, setItems] = useState([]);
  const [current, setCurrent] = useState(0);
  const timer = useRef(null);

  useEffect(() => {
    getNews({ region: 'TR', pageSize: 24, lang: language })
      .then(data => setItems(data.items ?? []))
      .catch(() => {});
  }, [language]);

  // Slider'da YALNIZCA gerçek görseli olan haberler
  const slides = items.filter(i => i.imageUrl && i.imageUrl.startsWith('http')).slice(0, SLIDE_COUNT);
  const important = items.slice(0, 4);

  useEffect(() => {
    if (slides.length <= 1) return undefined;
    timer.current = setInterval(() => setCurrent(c => (c + 1) % slides.length), AUTO_MS);
    return () => clearInterval(timer.current);
  }, [slides.length]);

  const active = slides[current];

  // Henüz haber gelmediyse (ilk yükleme) iskelet göster — boş siyah banner + "Yükleniyor"
  // yerine. filterSlot (filtre çubuğu) varsa altında korunur.
  if (items.length === 0) {
    return (
      <div>
        <SkeletonHero />
        {filterSlot && <div className="mb-6">{filterSlot}</div>}
      </div>
    );
  }

  return (
    <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 mb-6">
      {/* Sol: büyük carousel (pager içeride) + altında filtre (boş alanı doldurur) */}
      <div className="lg:col-span-2 flex flex-col gap-4">
        <div className="relative h-[300px] sm:h-[420px] overflow-hidden rounded-md bg-gray-900 group">
          <Link to={active?.id ? `/news/${active.id}` : '/news'} className="block absolute inset-0 z-10">
            <div
              key={active?.id}
              className="absolute inset-0 bg-cover bg-center group-hover:scale-[1.03] transition-transform duration-700"
              style={{ backgroundImage: active ? `url('${proxyImageUrl(active.imageUrl)}')` : 'none' }}
            />
            <div className="absolute inset-0 bg-gradient-to-t from-black/90 via-black/45 to-transparent" />
            <div className="absolute bottom-0 left-0 right-0 p-6 sm:p-7">
              {active?.categoryLabel && (
                <span className="inline-block bg-[#093eaa] text-white text-[11px] font-bold tracking-wide uppercase px-3 py-1 mb-3 rounded-md">
                  {active.categoryLabel}
                </span>
              )}
              <h2 className="text-2xl sm:text-3xl font-extrabold text-white leading-tight max-w-2xl drop-shadow pr-24">
                {active?.title ?? t('Yükleniyor...')}
              </h2>
              <div className="flex items-center gap-3 text-white/80 text-sm mt-3">
                {active?.source && <span className="font-semibold">{active.source}</span>}
                {active?.publishedAt && (
                  <span className="flex items-center gap-1"><Clock className="w-4 h-4" /> {formatNewsTime(active.publishedAt, t)}</span>
                )}
              </div>
            </div>
          </Link>

          {/* Pager — slider İÇİNDE, sağ altta, beyaz/şeffaf */}
          {slides.length > 1 && (
            <div className="absolute bottom-3 right-3 z-20 flex flex-wrap gap-1 justify-end max-w-[60%]">
              {slides.map((s, i) => (
                <button
                  key={s.id ?? i}
                  onClick={() => setCurrent(i)}
                  aria-label={`Haber ${i + 1}`}
                  className={`w-6 h-6 text-[11px] font-bold rounded-md backdrop-blur-sm transition-colors ${
                    i === current ? 'bg-white text-gray-900' : 'bg-white/25 text-white hover:bg-white/45'
                  }`}
                >
                  {i + 1}
                </button>
              ))}
            </div>
          )}
        </div>
        {filterSlot && <div className="flex-1 min-h-0">{filterSlot}</div>}
      </div>

      {/* Sağ: Öne Çıkan Haberler + Bülten */}
      <div className="space-y-4">
        <div className="bg-white rounded-md border border-gray-200 p-5 shadow-sm">
          <h3 className="font-bold text-base mb-4 flex items-center gap-2">
            <TrendingUp className="w-5 h-5 text-[#093eaa]" /> {t('Öne Çıkan Haberler')}
          </h3>
          <div className="space-y-3">
            {important.length === 0 && <p className="text-sm text-gray-400">{t('Yükleniyor...')}</p>}
            {important.map((item, i) => (
              <Link
                key={item.id}
                to={`/news/${item.id}`}
                className={`flex gap-3 group ${i > 0 ? 'border-t border-gray-100 pt-3' : ''}`}
              >
                <div className="w-16 h-16 rounded-md overflow-hidden shrink-0 bg-gray-100">
                  {item.imageUrl ? (
                    <div className="w-full h-full bg-cover bg-center group-hover:scale-110 transition-transform"
                      style={{ backgroundImage: `url('${proxyImageUrl(item.imageUrl)}')` }} />
                  ) : (
                    <div className="w-full h-full flex items-center justify-center text-gray-300">
                      <TrendingUp className="w-5 h-5" />
                    </div>
                  )}
                </div>
                <div className="min-w-0">
                  <h4 className="text-sm font-bold leading-snug line-clamp-2 group-hover:text-[#093eaa] transition-colors">
                    {item.title}
                  </h4>
                  <span className="text-[10px] text-gray-400 uppercase mt-1 inline-block">
                    {item.source} • {formatNewsTime(item.publishedAt, t)}
                  </span>
                </div>
              </Link>
            ))}
          </div>
        </div>

        <NewsletterCard />
      </div>
    </div>
  );
}
