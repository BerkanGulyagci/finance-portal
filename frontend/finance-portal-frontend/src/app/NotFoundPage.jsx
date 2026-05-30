import { Link, useNavigate } from 'react-router-dom';
import { Home, ArrowLeft, TrendingUp, Newspaper, Wallet, BarChart3 } from 'lucide-react';
import { useTranslation } from '../context/LanguageContext';

const QUICK_LINKS = [
  { to: '/news',      icon: Newspaper,  labelKey: 'Haberler' },
  { to: '/dashboard', icon: BarChart3,  labelKey: 'Panel' },
  { to: '/portfolio', icon: Wallet,     labelKey: 'Portföyüm' },
  { to: '/market/stocks',    icon: TrendingUp, labelKey: 'Hisse Senetleri' },
];

export default function NotFoundPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();

  return (
    <div className="max-w-3xl mx-auto px-4 py-12 sm:py-16">
      <div className="text-center">
        {/* Büyük 404 */}
        <p className="text-[120px] sm:text-[160px] font-black text-gray-100 leading-none select-none">404</p>

        <h1 className="-mt-6 sm:-mt-10 text-2xl sm:text-3xl font-bold text-gray-900 mb-3">
          {t('Sayfa Bulunamadı')}
        </h1>
        <p className="text-gray-500 mb-8 max-w-md mx-auto">
          {t('Aradığınız sayfa mevcut değil veya taşınmış olabilir. Aşağıdaki bağlantılarla devam edebilirsiniz.')}
        </p>

        {/* Aksiyon butonları */}
        <div className="flex flex-wrap items-center justify-center gap-3 mb-12">
          <button
            type="button"
            onClick={() => navigate(-1)}
            className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl border border-gray-200 bg-white text-gray-700 text-sm font-semibold hover:border-[#093eaa]/40 hover:bg-[#093eaa]/5 transition-all"
          >
            <ArrowLeft className="w-4 h-4" />
            {t('Geri Dön')}
          </button>
          <Link
            to="/news"
            className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl bg-[#093eaa] text-white text-sm font-bold hover:bg-[#093eaa]/90 transition-all"
          >
            <Home className="w-4 h-4" />
            {t('Ana Sayfa')}
          </Link>
        </div>

        {/* Popüler sayfalar */}
        <div className="border-t border-gray-100 pt-8">
          <p className="text-xs font-bold text-gray-400 uppercase tracking-wider mb-4">
            {t('Popüler Sayfalar')}
          </p>
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 max-w-xl mx-auto">
            {QUICK_LINKS.map(({ to, icon: Icon, labelKey }) => (
              <Link
                key={to}
                to={to}
                className="group flex flex-col items-center gap-2 px-3 py-4 rounded-xl border border-gray-100 bg-white hover:border-[#093eaa]/40 hover:bg-[#093eaa]/5 transition-all"
              >
                <span className="w-10 h-10 rounded-lg bg-[#093eaa]/10 text-[#093eaa] flex items-center justify-center group-hover:scale-110 transition-transform">
                  <Icon className="w-5 h-5" />
                </span>
                <span className="text-xs font-semibold text-gray-700 group-hover:text-[#093eaa] transition-colors">
                  {t(labelKey)}
                </span>
              </Link>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
