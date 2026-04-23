import { useState, useRef, useEffect } from 'react';
import { Wallet, Search, ChevronDown, Menu, X } from 'lucide-react';
import { Link, NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';

// ── Nav item definitions ──────────────────────────────────────────────────────
const navItems = [
  { name: 'Haberler', path: '/news', auth: false },
  { name: 'Dashboard', path: '/dashboard', auth: true },
  { name: 'Portföy', path: '/portfolio', auth: true },
];

// ── Dropdown menus (Bloomberg style) ─────────────────────────────────────────
const dropdownMenus = [
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
      { label: 'Karşılaştır', path: '/market/compare', desc: 'Dövizleri karşılaştır' },
    ],
  },
  {
    label: 'Altın',
    items: [
      { label: 'Altın Detay', path: '/market/gold', desc: 'Ons, gram, çeyrek altın fiyatları' },
      { label: 'Küresel Fonlar', path: '/market/funds', desc: 'GLD, SLV ETF\'leri' },
    ],
  },
  {
    label: 'Yatırım Fonları',
    items: [
      { label: 'TEFAS Fonları', path: '/market/tefas', desc: 'Türkiye yatırım fonları' },
      { label: 'Global Fonlar', path: '/market/funds', desc: 'ETF ve yatırım fonları' },
    ],
  },
];

// ── Generic Dropdown ──────────────────────────────────────────────────────────
function NavDropdown({ menu, onClose }) {
  return (
    <div className="absolute top-full left-0 mt-1 w-64 bg-white rounded-xl shadow-xl border border-gray-200 py-2 z-50">
      {menu.items.map(item => (
        <Link key={item.path + item.label} to={item.path} onClick={onClose}
          className="flex flex-col px-4 py-3 hover:bg-gray-50 transition-colors group">
          <span className="text-sm font-semibold text-gray-900 group-hover:text-[#093eaa]">{item.label}</span>
          <span className="text-xs text-gray-400 mt-0.5">{item.desc}</span>
        </Link>
      ))}
    </div>
  );
}

// ── Header ────────────────────────────────────────────────────────────────────
export function Header() {
  const [mobileOpen, setMobileOpen] = useState(false);
  const [openMenu, setOpenMenu] = useState(null); // index of open dropdown
  const { isAuthenticated, logout } = useAuth();
  const navigate = useNavigate();
  const navRef = useRef(null);

  useEffect(() => {
    function handleClick(e) {
      if (navRef.current && !navRef.current.contains(e.target)) {
        setOpenMenu(null);
      }
    }
    document.addEventListener('mousedown', handleClick);
    return () => document.removeEventListener('mousedown', handleClick);
  }, []);

  function handleLogout() { logout(); navigate('/'); }

  return (
    <header className="sticky top-0 z-50 bg-white border-b border-gray-200 shadow-sm">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex items-center justify-between h-14">

          {/* Logo */}
          <Link to="/" className="flex items-center gap-2.5 shrink-0">
            <div className="bg-[#093eaa] p-1.5 rounded-lg">
              <Wallet className="w-5 h-5 text-white" />
            </div>
            <h1 className="text-lg font-bold tracking-tight">
              <span className="text-[#093eaa]">Finans</span>
              <span className="text-gray-900">Portalı</span>
            </h1>
          </Link>

          {/* Desktop Nav */}
          <nav className="hidden md:flex items-center gap-0.5" ref={navRef}>
            {/* Static nav items */}
            {navItems.filter(item => !item.auth || isAuthenticated).map(item => (
              <NavLink key={item.path} to={item.path}
                className={({ isActive }) =>
                  `px-3 py-2 rounded-lg text-sm font-semibold transition-colors whitespace-nowrap ${isActive ? 'text-[#093eaa] bg-blue-50' : 'text-gray-700 hover:text-[#093eaa] hover:bg-gray-50'}`
                }>
                {item.name}
              </NavLink>
            ))}

            {/* Dropdown menus */}
            {dropdownMenus.map((menu, idx) => (
              <div key={menu.label} className="relative">
                <button
                  onClick={() => setOpenMenu(openMenu === idx ? null : idx)}
                  className={`flex items-center gap-1 px-3 py-2 rounded-lg text-sm font-semibold transition-colors whitespace-nowrap ${openMenu === idx ? 'text-[#093eaa] bg-blue-50' : 'text-gray-700 hover:text-[#093eaa] hover:bg-gray-50'}`}>
                  {menu.label}
                  <ChevronDown className={`w-3 h-3 transition-transform ${openMenu === idx ? 'rotate-180' : ''}`} />
                </button>
                {openMenu === idx && <NavDropdown menu={menu} onClose={() => setOpenMenu(null)} />}
              </div>
            ))}
          </nav>

          {/* Right side */}
          <div className="flex items-center gap-2">
            <div className="relative hidden lg:block">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 w-4 h-4" />
              <input type="text" placeholder="Ara..."
                className="pl-9 pr-4 py-1.5 bg-gray-100 border-none rounded-full text-sm w-40 focus:outline-none focus:ring-2 focus:ring-[#093eaa] focus:w-56 transition-all" />
            </div>

            {isAuthenticated ? (
              <button onClick={handleLogout}
                className="bg-gray-100 text-gray-700 px-3 py-1.5 rounded-lg text-sm font-bold hover:bg-gray-200 transition-all">
                Çıkış
              </button>
            ) : (
              <div className="flex items-center gap-2">
                <Link to="/login"
                  className="text-gray-700 px-3 py-1.5 rounded-lg text-sm font-bold hover:bg-gray-50 transition-all border border-gray-200">
                  Giriş Yap
                </Link>
                <Link to="/register"
                  className="bg-[#093eaa] text-white px-3 py-1.5 rounded-lg text-sm font-bold hover:bg-[#093eaa]/90 transition-all">
                  Kayıt Ol
                </Link>
              </div>
            )}

            <button className="md:hidden p-2" onClick={() => setMobileOpen(o => !o)}>
              {mobileOpen ? <X className="w-5 h-5" /> : <Menu className="w-5 h-5" />}
            </button>
          </div>
        </div>

        {/* Mobile Menu */}
        {mobileOpen && (
          <div className="md:hidden py-3 border-t border-gray-200 space-y-1">
            {navItems.filter(item => !item.auth || isAuthenticated).map(item => (
              <Link key={item.path} to={item.path} onClick={() => setMobileOpen(false)}
                className="block px-4 py-2.5 text-sm font-semibold hover:bg-gray-50 rounded-lg transition-colors">
                {item.name}
              </Link>
            ))}
            {dropdownMenus.map(menu => (
              <div key={menu.label} className="px-4 py-2">
                <p className="text-xs font-bold text-gray-400 uppercase tracking-wider mb-1">{menu.label}</p>
                {menu.items.map(item => (
                  <Link key={item.path + item.label} to={item.path} onClick={() => setMobileOpen(false)}
                    className="block py-1.5 text-sm font-semibold text-gray-700 hover:text-[#093eaa] transition-colors">
                    {item.label}
                  </Link>
                ))}
              </div>
            ))}
          </div>
        )}
      </div>
    </header>
  );
}
