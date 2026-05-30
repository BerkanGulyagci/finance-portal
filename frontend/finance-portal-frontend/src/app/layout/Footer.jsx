import { Mail, MessageCircle, Sparkles } from 'lucide-react';
import { Link } from 'react-router-dom';
import { useTranslation } from '../../i18n/LanguageContext';

const footerLinks = {
  piyasalar: [
    { label: 'Hisse Senetleri', path: '/market/stocks' },
    { label: 'Kripto Paralar', path: '/market/crypto' },
    { label: 'Döviz Kurları', path: '/market/fx' },
    { label: 'TEFAS Fonları', path: '/market/tefas' },
    { label: 'Altın & Emtia', path: '/market/commodities' },
    { label: 'Eurobond & DİBS', path: '/market/bonds' },
  ],
  hesabim: [
    { label: 'Dashboard', path: '/dashboard' },
    { label: 'Portföylerim', path: '/portfolio' },
    { label: 'Alarmlar', path: '/alarms' },
    { label: 'Bildirimler', path: '/notifications' },
    { label: 'Profilim', path: '/profile' },
  ],
  kesfet: [
    { label: 'Haberler', path: '/news' },
    { label: 'Ekonomi', path: '/market/economy' },
    { label: 'Karşılaştır', path: '/market/stocks/compare' },
    { label: 'Kredi Hesaplama', path: '/market/kredi-hesaplama' },
    { label: 'Mevduat Hesaplama', path: '/market/mevduat-hesaplama' },
  ],
};

export function Footer() {
  const { t } = useTranslation();
  return (
    <footer className="bg-white border-t border-gray-200 mt-20 py-12">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="grid grid-cols-1 md:grid-cols-4 gap-8 mb-10">
          <div className="md:col-span-1">
            <div className="flex items-center gap-3 mb-5">
              <img
                src="/brand-logo.png"
                alt=""
                role="presentation"
                className="h-11 w-auto sm:h-12 object-contain object-center shrink-0"
                width={57}
                height={48}
                decoding="async"
              />
              <div className="text-lg font-bold tracking-tight leading-tight flex items-center">
                <span className="text-[#093eaa]">Port</span>
                <span className="text-gray-900">iva</span>
              </div>
            </div>
            <p className="text-sm text-gray-500 leading-relaxed mb-5">
              {t('Portföy takibi, anlık piyasa verileri, alarmlar ve Porti yapay zeka asistanıyla yatırımlarını tek yerden yönet.')}
            </p>
            <div className="flex items-center gap-2 text-xs text-gray-500 bg-blue-50/60 border border-blue-100 rounded-lg px-2.5 py-1.5 w-fit">
              <Sparkles className="w-3.5 h-3.5 text-[#093eaa]" />
              <span><span className="font-semibold text-[#093eaa]">Porti</span> {t('— yapay zeka destekli finans asistanı')}</span>
            </div>
          </div>

          <div>
            <h4 className="font-bold mb-4 text-gray-900">{t('Piyasalar')}</h4>
            <ul className="space-y-2 text-sm text-gray-500">
              {footerLinks.piyasalar.map(l => (
                <li key={l.label}>
                  <Link to={l.path} className="hover:text-[#093eaa] transition-colors">{t(l.label)}</Link>
                </li>
              ))}
            </ul>
          </div>

          <div>
            <h4 className="font-bold mb-4 text-gray-900">{t('Hesabım')}</h4>
            <ul className="space-y-2 text-sm text-gray-500">
              {footerLinks.hesabim.map(l => (
                <li key={l.label}>
                  <Link to={l.path} className="hover:text-[#093eaa] transition-colors">{t(l.label)}</Link>
                </li>
              ))}
            </ul>
          </div>

          <div>
            <h4 className="font-bold mb-4 text-gray-900">{t('Keşfet')}</h4>
            <ul className="space-y-2 text-sm text-gray-500">
              {footerLinks.kesfet.map(l => (
                <li key={l.label}>
                  <Link to={l.path} className="hover:text-[#093eaa] transition-colors">{t(l.label)}</Link>
                </li>
              ))}
            </ul>
          </div>
        </div>

        <div className="border-t border-gray-200 pt-6 flex flex-col md:flex-row items-center justify-between gap-3">
          <p className="text-xs text-gray-400">
            {t('© 2026 Portiva. Tüm hakları saklıdır. Veriler gecikmeli olabilir; yatırım tavsiyesi değildir.')}
          </p>
          <div className="flex items-center gap-3 text-xs text-gray-400">
            <Link to="/news" className="inline-flex items-center gap-1 hover:text-[#093eaa] transition-colors">
              <MessageCircle className="w-3.5 h-3.5" />
              {t('Haber Akışı')}
            </Link>
            <span className="text-gray-300">·</span>
            <a
              href="mailto:bgulyaci@gmail.com"
              className="inline-flex items-center gap-1 hover:text-[#093eaa] transition-colors"
            >
              <Mail className="w-3.5 h-3.5" />
              {t('İletişim')}
            </a>
          </div>
        </div>
      </div>
    </footer>
  );
}
