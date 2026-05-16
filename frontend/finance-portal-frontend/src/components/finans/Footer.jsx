import { Globe, Share2 } from 'lucide-react';
import { Link } from 'react-router-dom';

const footerLinks = {
  haberler: [
    { label: 'Borsa Haberleri', path: '/news' },
    { label: 'Döviz Haberleri', path: '/news' },
    { label: 'Şirket Haberleri', path: '/news' },
    { label: 'Teknoloji', path: '/news' },
  ],
  piyasalar: [
    { label: 'Dolar Ne Kadar?', path: '/market' },
    { label: 'Euro Ne Kadar?', path: '/market' },
    { label: 'Canlı Borsa', path: '/market' },
    { label: 'Altın Fiyatları', path: '/market' },
  ],
  kurumsal: [
    { label: 'Künye', path: '#' },
    { label: 'İletişim', path: '#' },
    { label: 'Gizlilik İlkeleri', path: '#' },
    { label: 'Kullanım Şartları', path: '#' },
  ],
};

export function Footer() {
  return (
    <footer className="bg-white border-t border-gray-200 mt-20 py-12">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="grid grid-cols-1 md:grid-cols-4 gap-8 mb-12">
          <div className="md:col-span-1">
            <div className="flex items-center gap-3 mb-6">
              <img
                src="/brand-logo.png"
                alt=""
                role="presentation"
                className="h-10 w-auto sm:h-11 max-w-[190px] sm:max-w-[210px] object-contain object-center shrink-0"
                width={210}
                height={44}
                decoding="async"
              />
              <div className="text-lg font-bold tracking-tight leading-tight flex items-center">
                <span className="text-[#093eaa]">Finans</span>
                <span className="text-gray-900">Portalı</span>
              </div>
            </div>
            <p className="text-sm text-gray-500 mb-6">
              Türkiye'nin ve dünyanın en güncel finans haberlerini, verilerini ve analizlerini sunan bağımsız platform.
            </p>
            <div className="flex gap-4">
              <a href="#" className="w-8 h-8 rounded-full bg-gray-100 flex items-center justify-center text-gray-400 hover:text-[#093eaa] transition-colors">
                <Globe className="w-4 h-4" />
              </a>
              <a href="#" className="w-8 h-8 rounded-full bg-gray-100 flex items-center justify-center text-gray-400 hover:text-[#093eaa] transition-colors">
                <Share2 className="w-4 h-4" />
              </a>
            </div>
          </div>

          <div>
            <h4 className="font-bold mb-4 text-gray-900">Haberler</h4>
            <ul className="space-y-2 text-sm text-gray-500">
              {footerLinks.haberler.map(l => (
                <li key={l.label}><Link to={l.path} className="hover:text-[#093eaa] transition-colors">{l.label}</Link></li>
              ))}
            </ul>
          </div>

          <div>
            <h4 className="font-bold mb-4 text-gray-900">Piyasalar</h4>
            <ul className="space-y-2 text-sm text-gray-500">
              {footerLinks.piyasalar.map(l => (
                <li key={l.label}><Link to={l.path} className="hover:text-[#093eaa] transition-colors">{l.label}</Link></li>
              ))}
            </ul>
          </div>

          <div>
            <h4 className="font-bold mb-4 text-gray-900">Kurumsal</h4>
            <ul className="space-y-2 text-sm text-gray-500">
              {footerLinks.kurumsal.map(l => (
                <li key={l.label}><a href={l.path} className="hover:text-[#093eaa] transition-colors">{l.label}</a></li>
              ))}
            </ul>
          </div>
        </div>

        <div className="border-t border-gray-200 pt-8 flex flex-col md:flex-row items-center justify-between gap-4">
          <p className="text-xs text-gray-400">
            © 2025 Finans Portalı. Tüm hakları saklıdır. Veriler gecikmeli olarak yansıtılabilir.
          </p>
        </div>
      </div>
    </footer>
  );
}
