import { useState, useRef, useEffect, useId } from 'react';
import { ChevronDown, Menu, X, User, Settings, Shield, LogOut, Briefcase, Bell, BellRing, LayoutGrid } from 'lucide-react';
import { Link, NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { useTranslation } from '../../i18n/LanguageContext';
import { getMyPortfolios } from '../../api/portfolioApi';
import SearchBox from './SearchBox';
import NotificationBell from './NotificationBell';

// ── Generic Dropdown ──────────────────────────────────────────────────────────
function NavDropdown({ menu, onClose, t }) {
  // groups varsa gruplu render, yoksa düz liste
  if (menu.groups) {
    return (
      <div className="absolute top-full left-0 mt-1 w-64 bg-white rounded-xl shadow-xl border border-gray-200 py-2 z-50">
        {menu.groups.map(group => (
          <div key={group.title}>
            {/* Grup başlığı */}
            <div className="px-4 pt-2 pb-1">
              <span className="text-[10px] font-bold text-gray-400 uppercase tracking-widest">
                {t(group.title)}
              </span>
            </div>
            {group.items.map(item => (
              <Link key={item.path + item.label} to={item.path} onClick={onClose}
                className="flex flex-col px-4 py-2.5 hover:bg-gray-50 transition-colors group pl-6">
                <span className="text-sm font-semibold text-gray-900 group-hover:text-[#093eaa]">{t(item.label)}</span>
                <span className="text-xs text-gray-400 mt-0.5">{t(item.desc)}</span>
              </Link>
            ))}
          </div>
        ))}
      </div>
    );
  }

  return (
    <div className="absolute top-full left-0 mt-1 w-64 bg-white rounded-xl shadow-xl border border-gray-200 py-2 z-50">
      {menu.items.map(item => (
        <Link key={item.path + item.label} to={item.path} onClick={onClose}
          className="flex flex-col px-4 py-3 hover:bg-gray-50 transition-colors group">
          <span className="text-sm font-semibold text-gray-900 group-hover:text-[#093eaa]">{t(item.label)}</span>
          <span className="text-xs text-gray-400 mt-0.5">{t(item.desc)}</span>
        </Link>
      ))}
    </div>
  );
}

// ── Profile menu ──────────────────────────────────────────────────────────────
function ProfileMenu({ onClose, t }) {
  const { logout } = useAuth();
  const navigate = useNavigate();

  function handleLogout() {
    onClose();
    logout();
    navigate('/');
  }

  function openProfileModal(modal) {
    onClose();
    navigate(modal ? `/profile?modal=${modal}` : '/profile');
  }

  const itemClass =
    'flex items-center gap-2 px-4 py-2.5 text-sm font-semibold text-gray-700 hover:bg-gray-50 hover:text-[#093eaa] transition-colors w-full text-left';

  return (
    <div className="absolute right-0 top-full mt-1 w-56 bg-white rounded-xl shadow-xl border border-gray-200 py-2 z-50">
      <Link to="/portfolio" onClick={onClose} className={itemClass}>
        <Briefcase className="w-4 h-4 shrink-0" />
        {t('Portföyüm')}
      </Link>
      <Link to="/alarms" onClick={onClose} className={itemClass}>
        <Bell className="w-4 h-4 shrink-0" />
        {t('Alarmlarım')}
      </Link>
      <div className="my-1 border-t border-gray-100" />
      <Link to="/profile" onClick={onClose} className={itemClass}>
        <User className="w-4 h-4 shrink-0" />
        {t('Profilim')}
      </Link>
      <button type="button" onClick={() => openProfileModal('name')} className={itemClass}>
        <Settings className="w-4 h-4 shrink-0" />
        {t('Hesap Ayarları')}
      </button>
      <button type="button" onClick={() => openProfileModal('password')} className={itemClass}>
        <Shield className="w-4 h-4 shrink-0" />
        {t('Şifre Değiştir')}
      </button>
      <button type="button" onClick={handleLogout} className={`${itemClass} text-red-700 hover:text-red-800`}>
        <LogOut className="w-4 h-4 shrink-0" />
        {t('Çıkış Yap')}
      </button>
    </div>
  );
}

// ── Portföy dropdown (en değerli portföyler + Alarmlarım/Bildirimler) ─────────
function PortfolioNavMenu({ onClose, t }) {
  const [portfolios, setPortfolios] = useState([]);

  useEffect(() => {
    getMyPortfolios()
      .then(list => {
        const holdings = (list ?? [])
          .filter(p => p.portfolioType !== 'WATCHLIST')
          .sort((a, b) => (parseFloat(b.totalMarketValue) || 0) - (parseFloat(a.totalMarketValue) || 0));
        setPortfolios(holdings);
      })
      .catch(() => {});
  }, []);

  const top = portfolios.slice(0, 3);
  const fmtVal = (v) => {
    const n = parseFloat(v);
    return Number.isFinite(n) ? `₺${n.toLocaleString('tr-TR', { maximumFractionDigits: 0 })}` : '-';
  };
  const itemClass =
    'flex items-center gap-2 px-4 py-2.5 text-sm font-semibold text-gray-700 hover:bg-gray-50 hover:text-[#093eaa] transition-colors w-full text-left';

  return (
    <div className="absolute top-full left-0 mt-1 w-72 bg-white rounded-xl shadow-xl border border-gray-200 py-2 z-50">
      <div className="px-4 pt-1 pb-1">
        <span className="text-[10px] font-bold text-gray-400 uppercase tracking-widest">{t('Portföylerim')}</span>
      </div>
      {top.length === 0 ? (
        <p className="px-4 py-2 text-xs text-gray-400">{t('Henüz portföyünüz yok.')}</p>
      ) : top.map(p => (
        <Link key={p.id} to={`/portfolio/${p.id}`} onClick={onClose}
          className="flex items-center justify-between gap-2 px-4 py-2.5 hover:bg-gray-50 group">
          <span className="flex items-center gap-2 min-w-0">
            <Briefcase className="w-4 h-4 text-[#093eaa] shrink-0" />
            <span className="text-sm font-semibold text-gray-900 group-hover:text-[#093eaa] truncate">{p.name}</span>
          </span>
          <span className="text-xs font-bold text-gray-500 tabular-nums shrink-0">{fmtVal(p.totalMarketValue)}</span>
        </Link>
      ))}
      <Link to="/portfolio" onClick={onClose} className={itemClass}>
        <LayoutGrid className="w-4 h-4 shrink-0" /> {t('Tüm Portföyler')}
      </Link>
      <div className="my-1 border-t border-gray-100" />
      <Link to="/alarms" onClick={onClose} className={itemClass}>
        <Bell className="w-4 h-4 shrink-0" /> {t('Alarmlarım')}
      </Link>
      <Link to="/notifications" onClick={onClose} className={itemClass}>
        <BellRing className="w-4 h-4 shrink-0" /> {t('Bildirimler')}
      </Link>
    </div>
  );
}

// ── Bayrak ikonları (inline SVG — Windows emoji bayrak desteklemediği için) ────
function FlagTR({ className = '' }) {
  return (
    <svg viewBox="0 0 1200 800" preserveAspectRatio="xMidYMid slice" className={className} aria-hidden="true">
      <rect width="1200" height="800" fill="#E30A17" />
      <circle cx="425" cy="400" r="200" fill="#fff" />
      <circle cx="475" cy="400" r="160" fill="#E30A17" />
      <path fill="#fff" d="M583.334 400l180.901 58.779-111.804-153.885v190.212l111.804-153.885z" />
    </svg>
  );
}

function FlagGB({ className = '' }) {
  const id = useId();
  return (
    <svg viewBox="0 0 60 30" preserveAspectRatio="xMidYMid slice" className={className} aria-hidden="true">
      <clipPath id={`${id}-s`}><path d="M0,0 v30 h60 v-30 z" /></clipPath>
      <clipPath id={`${id}-t`}><path d="M30,15 h30 v15 z v15 h-30 z h-30 v-15 z v-15 h30 z" /></clipPath>
      <g clipPath={`url(#${id}-s)`}>
        <path d="M0,0 v30 h60 v-30 z" fill="#012169" />
        <path d="M0,0 L60,30 M60,0 L0,30" stroke="#fff" strokeWidth="6" />
        <path d="M0,0 L60,30 M60,0 L0,30" clipPath={`url(#${id}-t)`} stroke="#C8102E" strokeWidth="4" />
        <path d="M30,0 v30 M0,15 h60" stroke="#fff" strokeWidth="10" />
        <path d="M30,0 v30 M0,15 h60" stroke="#C8102E" strokeWidth="6" />
      </g>
    </svg>
  );
}

// ── Language toggle — bayraklı tek tıkla geçiş ────────────────────────────────
function LanguageToggle({ className = '' }) {
  const { language, setLanguage } = useTranslation();
  const isEn = language === 'en';
  return (
    <button
      type="button"
      onClick={() => setLanguage(isEn ? 'tr' : 'en')}
      title={isEn ? 'Türkçe’ye geç' : 'Switch to English'}
      aria-label={isEn ? 'Türkçe’ye geç' : 'Switch to English'}
      className={`inline-flex items-center gap-1.5 rounded-full pl-1 pr-2.5 py-1 hover:bg-gray-100 transition-colors ${className}`}
    >
      <span className="w-6 h-6 rounded-full overflow-hidden ring-1 ring-gray-200 shrink-0">
        {isEn ? <FlagGB className="w-full h-full" /> : <FlagTR className="w-full h-full" />}
      </span>
      <span className="text-xs font-bold text-gray-600">{isEn ? 'EN' : 'TR'}</span>
    </button>
  );
}

// ── Header ────────────────────────────────────────────────────────────────────
export function Header() {
  const [mobileOpen, setMobileOpen] = useState(false);
  const [openMenu, setOpenMenu] = useState(null); // index of open dropdown
  const [profileOpen, setProfileOpen] = useState(false);
  const { isAuthenticated, isAdmin, logout, username } = useAuth();
  const { t } = useTranslation();
  const navigate = useNavigate();
  const navRef = useRef(null);
  const profileRef = useRef(null);

  // ── Nav item definitions ────────────────────────────────────────────────────
  const navItems = [
    { name: 'Haberler', path: '/news', auth: false },
    { name: 'Dashboard', path: '/dashboard', auth: true },
  ];

  // ── Dropdown menus (Bloomberg style) ────────────────────────────────────────
  const dropdownMenus = [
    {
      label: 'Ekonomi',
      groups: [
        {
          title: 'Genel',
          items: [
            { label: 'Türkiye Ekonomisi', path: '/market/economy', desc: 'Makro göstergeler: enflasyon, faiz, büyüme' },
          ],
        },
        {
          title: 'Hesaplama Araçları',
          items: [
            { label: 'Kredi Hesaplama', path: '/market/kredi-hesaplama', desc: 'Taksit, toplam geri ödeme, faiz hesabı' },
            { label: 'Mevduat Hesaplama', path: '/market/mevduat-hesaplama', desc: 'Vade sonu net + enflasyona göre reel getiri' },
          ],
        },
      ],
    },
    {
      label: 'Piyasalar',
      items: [
        { label: 'Hisse Senetleri', path: '/market/stocks', desc: 'BIST hisse fiyatları' },
        { label: 'Kripto Para', path: '/market/crypto', desc: 'CoinGecko TRY bazlı' },
        { label: 'Vadeli İşlemler', path: '/market/futures', desc: 'VİOP ve küresel vadeli' },
        { label: 'Tahvil / Bono', path: '/market/bonds', desc: 'Devlet İç Borçlanma Senetleri' },
      ],
    },
    {
      label: 'Döviz',
      items: [
        { label: 'TCMB Kurları', path: '/market/fx', desc: 'Resmi döviz kurları' },
        { label: 'Open Exchange Rates', path: '/market/fx', desc: 'Gerçek zamanlı kurlar' },
        { label: 'Banka Kurları', path: '/market/fx?tab=banks', desc: 'Türk bankalarının alış/satış kurları' },
        { label: 'Karşılaştır', path: '/market/compare', desc: 'Dövizleri karşılaştır' },
      ],
    },
    {
      label: 'Emtia',
      groups: [
        {
          title: 'Kıymetli Madenler',
          items: [
            { label: 'Altın',    path: '/market/gold',      desc: 'Ons, gram, çeyrek altın' },
            { label: 'Gümüş',   path: '/market/silver',    desc: 'Gram, kg, ons gümüş' },
            { label: 'Platin',  path: '/market/platinum',  desc: 'TL/Gram, USD/Ons' },
            { label: 'Paladyum',path: '/market/palladium', desc: 'TL/Gram, USD/Ons' },
          ],
        },
        {
          title: 'Analiz',
          items: [
            { label: 'Emtia Karşılaştırma', path: '/market/commodities/compare', desc: 'Normalize performans karşılaştırması' },
            { label: 'Diğer Emtialar', path: '/market/commodities', desc: 'Enerji, tarım, sanayi metalleri' },
          ],
        },
      ],
    },
    {
      label: 'Fonlar',
      items: [
        { label: 'Tüm Fonlar',      path: '/market/tefas',         desc: 'TEFAS · BES · OKS · Osmanlı Portföy' },
        { label: 'Fon Karşılaştır', path: '/market/tefas/compare', desc: 'Fonları karşılaştır' },
      ],
    },
  ];

  useEffect(() => {
    function handleClick(e) {
      if (navRef.current && !navRef.current.contains(e.target)) {
        setOpenMenu(null);
      }
      if (profileRef.current && !profileRef.current.contains(e.target)) {
        setProfileOpen(false);
      }
    }
    document.addEventListener('mousedown', handleClick);
    return () => document.removeEventListener('mousedown', handleClick);
  }, []);

  function handleLogout() { logout(); navigate('/'); }

  return (
    <header className="sticky top-0 z-50 bg-white border-b border-gray-200 shadow-sm">
      <div className="px-4 sm:px-6 lg:px-8">
        <div className="flex items-center h-14 gap-3">

          {/* Logo + marka adı */}
          <Link
            to="/"
            className="flex items-center gap-2.5 sm:gap-3 shrink-0 min-w-0 focus:outline-none focus-visible:ring-2 focus-visible:ring-[#093eaa]/40 rounded-lg"
            aria-label={t('FinansPortalı — ana sayfa')}
          >
            <img
              src="/brand-logo.png"
              alt=""
              role="presentation"
              className="h-10 w-auto sm:h-11 max-w-[min(190px,42vw)] sm:max-w-[210px] object-contain object-center shrink-0"
              width={210}
              height={44}
              decoding="async"
            />
            <span className="text-lg font-bold tracking-tight truncate leading-tight flex items-center">
              <span className="text-[#093eaa]">{t('Finans')}</span>
              <span className="text-gray-900">{t('Portalı')}</span>
            </span>
          </Link>

          {/* Desktop Nav — logo ile sağ grup arasında ortalı */}
          <nav className="hidden lg:flex items-center gap-1 flex-1 justify-center" ref={navRef}>
            {/* Static nav items */}
            {navItems.filter(item => !item.auth || isAuthenticated).map(item => (
              <NavLink key={item.path} to={item.path}
                className={({ isActive }) =>
                  `px-3 py-2 rounded-lg text-sm font-semibold transition-colors whitespace-nowrap ${isActive ? 'text-[#093eaa] bg-blue-50' : 'text-gray-700 hover:text-[#093eaa] hover:bg-gray-50'}`
                }>
                {t(item.name)}
              </NavLink>
            ))}

            {/* Portföy dropdown (giriş yapan kullanıcı) */}
            {isAuthenticated && (
              <div className="relative">
                <button
                  onClick={() => setOpenMenu(openMenu === 'portfolio' ? null : 'portfolio')}
                  className={`flex items-center gap-1 px-3 py-2 rounded-lg text-sm font-semibold transition-colors whitespace-nowrap ${openMenu === 'portfolio' ? 'text-[#093eaa] bg-blue-50' : 'text-gray-700 hover:text-[#093eaa] hover:bg-gray-50'}`}>
                  {t('Portföy')}
                  <ChevronDown className={`w-3 h-3 transition-transform ${openMenu === 'portfolio' ? 'rotate-180' : ''}`} />
                </button>
                {openMenu === 'portfolio' && <PortfolioNavMenu onClose={() => setOpenMenu(null)} t={t} />}
              </div>
            )}

            {/* Dropdown menus */}
            {isAdmin && (
              <NavLink to="/admin/users"
                className={({ isActive }) =>
                  `px-3 py-2 rounded-lg text-sm font-semibold transition-colors whitespace-nowrap ${isActive ? 'text-[#093eaa] bg-blue-50' : 'text-gray-700 hover:text-[#093eaa] hover:bg-gray-50'}`
                }>
                {t('Admin Panel')}
              </NavLink>
            )}

            {dropdownMenus.map((menu, idx) => (
              <div key={menu.label} className="relative">
                <button
                  onClick={() => setOpenMenu(openMenu === idx ? null : idx)}
                  className={`flex items-center gap-1 px-3 py-2 rounded-lg text-sm font-semibold transition-colors whitespace-nowrap ${openMenu === idx ? 'text-[#093eaa] bg-blue-50' : 'text-gray-700 hover:text-[#093eaa] hover:bg-gray-50'}`}>
                  {t(menu.label)}
                  <ChevronDown className={`w-3 h-3 transition-transform ${openMenu === idx ? 'rotate-180' : ''}`} />
                </button>
                {openMenu === idx && <NavDropdown menu={menu} onClose={() => setOpenMenu(null)} t={t} />}
              </div>
            ))}
          </nav>

          {/* Right side — en sağa yaslı */}
          <div className="flex items-center gap-1.5 ml-auto">
            <SearchBox />

            {/* Language toggle — bayraklı geçiş */}
            <LanguageToggle className="hidden sm:inline-flex" />

            {isAuthenticated && (
              <div className="hidden sm:block">
                <NotificationBell />
              </div>
            )}

            {isAuthenticated ? (
              <div className="relative hidden sm:block" ref={profileRef}>
                <button
                  type="button"
                  onClick={() => setProfileOpen((open) => !open)}
                  className={`flex items-center gap-2 rounded-full pl-1 pr-2.5 py-1 hover:bg-gray-100 transition-all ${profileOpen ? 'bg-gray-100 ring-2 ring-[#093eaa]/20' : ''}`}
                >
                  <span className="w-7 h-7 rounded-full bg-[#093eaa] text-white flex items-center justify-center text-xs font-bold shrink-0">
                    {(username?.[0] || 'U').toUpperCase()}
                  </span>
                  <span className="max-w-[120px] truncate text-sm font-semibold text-gray-700">{username || t('Hesabım')}</span>
                  <ChevronDown className={`w-3.5 h-3.5 text-gray-400 transition-transform ${profileOpen ? 'rotate-180' : ''}`} />
                </button>
                {profileOpen && <ProfileMenu onClose={() => setProfileOpen(false)} t={t} />}
              </div>
            ) : (
              <div className="flex items-center gap-2">
                <Link to="/login"
                  className="text-gray-700 px-3 py-1.5 rounded-lg text-sm font-bold hover:bg-gray-50 transition-all border border-gray-200">
                  {t('Giriş Yap')}
                </Link>
                <Link to="/register"
                  className="bg-[#093eaa] text-white px-3 py-1.5 rounded-lg text-sm font-bold hover:bg-[#093eaa]/90 transition-all">
                  {t('Kayıt Ol')}
                </Link>
              </div>
            )}

            <button className="lg:hidden p-2" onClick={() => setMobileOpen(o => !o)}>
              {mobileOpen ? <X className="w-5 h-5" /> : <Menu className="w-5 h-5" />}
            </button>
          </div>
        </div>

        {/* Mobile Menu */}
        {mobileOpen && (
          <div className="lg:hidden py-3 border-t border-gray-200 space-y-1">
            {/* Mobile language toggle */}
            <div className="px-4 py-2 flex items-center justify-between border-b border-gray-100 mb-2">
              <span className="text-xs font-bold text-gray-400 uppercase tracking-wider">{t('Dil')}</span>
              <LanguageToggle />
            </div>

            {navItems.filter(item => !item.auth || isAuthenticated).map(item => (
              <Link key={item.path} to={item.path} onClick={() => setMobileOpen(false)}
                className="block px-4 py-2.5 text-sm font-semibold hover:bg-gray-50 rounded-lg transition-colors">
                {t(item.name)}
              </Link>
            ))}
            {isAdmin && (
              <Link to="/admin/users" onClick={() => setMobileOpen(false)}
                className="block px-4 py-2.5 text-sm font-semibold text-[#093eaa] hover:bg-gray-50 rounded-lg transition-colors">
                {t('Admin Panel')}
              </Link>
            )}
            {isAuthenticated && (
              <div className="px-4 py-2 border-b border-gray-100 mb-2 space-y-1">
                <p className="text-xs font-bold text-gray-400 uppercase tracking-wider mb-1">{t('Hesap')}</p>
                <Link to="/portfolio" onClick={() => setMobileOpen(false)}
                  className="block py-2 text-sm font-semibold text-gray-700 hover:text-[#093eaa]">
                  {t('Portföyüm')}
                </Link>
                <Link to="/profile" onClick={() => setMobileOpen(false)}
                  className="block py-2 text-sm font-semibold text-gray-700 hover:text-[#093eaa]">
                  {t('Profilim')}
                </Link>
                <Link to="/alarms" onClick={() => setMobileOpen(false)}
                  className="block py-2 text-sm font-semibold text-gray-700 hover:text-[#093eaa]">
                  {t('Alarmlarım')}
                </Link>
                <Link to="/notifications" onClick={() => setMobileOpen(false)}
                  className="block py-2 text-sm font-semibold text-gray-700 hover:text-[#093eaa]">
                  {t('Bildirimler')}
                </Link>
                <Link to="/profile?modal=name" onClick={() => setMobileOpen(false)}
                  className="block py-2 text-sm font-semibold text-gray-700 hover:text-[#093eaa]">
                  {t('Hesap Ayarları')}
                </Link>
                <Link to="/profile?modal=password" onClick={() => setMobileOpen(false)}
                  className="block py-2 text-sm font-semibold text-gray-700 hover:text-[#093eaa]">
                  {t('Şifre Değiştir')}
                </Link>
                <button type="button" onClick={() => { setMobileOpen(false); handleLogout(); }}
                  className="block w-full text-left py-2 text-sm font-semibold text-red-700">
                  {t('Çıkış Yap')}
                </button>
              </div>
            )}
            {dropdownMenus.map(menu => (
              <div key={menu.label} className="px-4 py-2">
                <p className="text-xs font-bold text-gray-400 uppercase tracking-wider mb-1">{t(menu.label)}</p>
                {menu.groups
                  ? menu.groups.map(group => (
                      <div key={group.title} className="mb-2">
                        <p className="text-[10px] font-bold text-gray-300 uppercase tracking-widest mb-1 pl-1">{t(group.title)}</p>
                        {group.items.map(item => (
                          <Link key={item.path + item.label} to={item.path} onClick={() => setMobileOpen(false)}
                            className="block py-1.5 pl-3 text-sm font-semibold text-gray-700 hover:text-[#093eaa] transition-colors">
                            {t(item.label)}
                          </Link>
                        ))}
                      </div>
                    ))
                  : menu.items.map(item => (
                      <Link key={item.path + item.label} to={item.path} onClick={() => setMobileOpen(false)}
                        className="block py-1.5 text-sm font-semibold text-gray-700 hover:text-[#093eaa] transition-colors">
                        {t(item.label)}
                      </Link>
                    ))
                }
              </div>
            ))}
            {!isAuthenticated && (
              <div className="px-4 pt-2 flex gap-2">
                <Link to="/login" onClick={() => setMobileOpen(false)}
                  className="flex-1 text-center text-gray-700 px-3 py-2 rounded-lg text-sm font-bold border border-gray-200">
                  {t('Giriş Yap')}
                </Link>
                <Link to="/register" onClick={() => setMobileOpen(false)}
                  className="flex-1 text-center bg-[#093eaa] text-white px-3 py-2 rounded-lg text-sm font-bold">
                  {t('Kayıt Ol')}
                </Link>
              </div>
            )}
          </div>
        )}
      </div>
    </header>
  );
}
