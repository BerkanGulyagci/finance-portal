import { useState, useRef, useEffect } from 'react';
import { Wallet, Search, ChevronDown, Menu, X } from 'lucide-react';
import { Link, NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';

const marketItems = [
  { label: 'Hisse Senetleri', path: '/market/stocks', desc: 'BIST hisse fiyatları' },
  { label: 'Kripto Para',     path: '/market/crypto',  desc: 'CoinGecko TRY bazlı' },
  { label: 'Vadeli İşlemler', path: '/market/futures', desc: 'Küresel vadeli kontratlar' },
  { label: 'Global Fonlar',   path: '/market/funds',   desc: 'ETF ve yatırım fonları' },
  { label: 'TEFAS Fonları',   path: '/market/tefas',   desc: 'Türkiye yatırım fonları' },
  { label: 'Döviz Kurları',   path: '/market/fx',      desc: 'TCMB resmi kurlar' },
];

const navItems = [
  { name: 'Haberler',  path: '/news' },
  { name: 'Dashboard', path: '/dashboard' },
  { name: 'Portföy',   path: '/portfolio' },
];

function Dropdown({ items, onClose }) {
  return (
    <div className="absolute top-full left-0 mt-1 w-72 bg-white rounded-xl shadow-xl border border-gray-200 py-2 z-50">
      {items.map(item => (
        <Link key={item.path} to={item.path} onClick={onClose}
          className="flex flex-col px-4 py-3 hover:bg-gray-50 transition-colors group">
          <span className="text-sm font-semibold text-gray-900 group-hover:text-[#093eaa]">{item.label}</span>
          <span className="text-xs text-gray-400 mt-0.5">{item.desc}</span>
        </Link>
      ))}
    </div>
  );
}

export function Header() {
  const [mobileOpen, setMobileOpen] = useState(false);
  const [marketOpen, setMarketOpen] = useState(false);
  const { isAuthenticated, logout } = useAuth();
  const navigate = useNavigate();
  const dropdownRef = useRef(null);

  // Close dropdown on outside click
  useEffect(() => {
    function handleClick(e) {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target)) {
        setMarketOpen(false);
      }
    }
    document.addEventListener('mousedown', handleClick);
    return () => document.removeEventListener('mousedown', handleClick);
  }, []);

  function handleLogout() {
    logout();
    navigate('/');
  }

  return (
    <header className="sticky top-0 z-50 bg-white border-b border-gray-200 shadow-sm">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex items-center justify-between h-16">

          {/* Logo */}
          <Link to="/" className="flex items-center gap-3 shrink-0">
            <div className="bg-[#093eaa] p-1.5 rounded-lg">
              <Wallet className="w-6 h-6 text-white" />
            </div>
            <h1 className="text-xl font-bold tracking-tight">
              <span className="text-[#093eaa]">Finans</span>
              <span className="text-gray-900">Portalı</span>
            </h1>
          </Link>

          {/* Desktop Nav */}
          <nav className="hidden md:flex items-center gap-1">
            {navItems.map(item => (
              <NavLink key={item.path} to={item.path}
                className={({ isActive }) =>
                  `px-3 py-2 rounded-lg text-sm font-semibold transition-colors ${isActive ? 'text-[#093eaa] bg-blue-50' : 'text-gray-700 hover:text-[#093eaa] hover:bg-gray-50'}`
                }>
                {item.name}
              </NavLink>
            ))}

            {/* Piyasalar dropdown */}
            <div className="relative" ref={dropdownRef}>
              <button
                onClick={() => setMarketOpen(o => !o)}
                className={`flex items-center gap-1 px-3 py-2 rounded-lg text-sm font-semibold transition-colors ${marketOpen ? 'text-[#093eaa] bg-blue-50' : 'text-gray-700 hover:text-[#093eaa] hover:bg-gray-50'}`}
              >
                Piyasalar <ChevronDown className={`w-3.5 h-3.5 transition-transform ${marketOpen ? 'rotate-180' : ''}`} />
              </button>
              {marketOpen && <Dropdown items={marketItems} onClose={() => setMarketOpen(false)} />}
            </div>
          </nav>

          {/* Right side */}
          <div className="flex items-center gap-3">
            <div className="relative hidden sm:block">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 w-4 h-4" />
              <input type="text" placeholder="Ara..."
                className="pl-9 pr-4 py-2 bg-gray-100 border-none rounded-full text-sm w-48 focus:outline-none focus:ring-2 focus:ring-[#093eaa] focus:w-64 transition-all" />
            </div>

            {isAuthenticated ? (
              <button onClick={handleLogout}
                className="bg-gray-100 text-gray-700 px-4 py-2 rounded-lg text-sm font-bold hover:bg-gray-200 transition-all">
                Çıkış
              </button>
            ) : (
              <Link to="/login"
                className="bg-[#093eaa] text-white px-4 py-2 rounded-lg text-sm font-bold hover:bg-[#093eaa]/90 transition-all">
                Giriş Yap
              </Link>
            )}

            <button className="md:hidden p-2" onClick={() => setMobileOpen(o => !o)}>
              {mobileOpen ? <X className="w-6 h-6" /> : <Menu className="w-6 h-6" />}
            </button>
          </div>
        </div>

        {/* Mobile Menu */}
        {mobileOpen && (
          <div className="md:hidden py-4 border-t border-gray-200 space-y-1">
            {navItems.map(item => (
              <Link key={item.path} to={item.path} onClick={() => setMobileOpen(false)}
                className="block px-4 py-3 text-sm font-semibold hover:bg-gray-50 rounded-lg transition-colors">
                {item.name}
              </Link>
            ))}
            <div className="px-4 py-2">
              <p className="text-xs font-bold text-gray-400 uppercase tracking-wider mb-2">Piyasalar</p>
              {marketItems.map(item => (
                <Link key={item.path} to={item.path} onClick={() => setMobileOpen(false)}
                  className="block py-2 text-sm font-semibold text-gray-700 hover:text-[#093eaa] transition-colors">
                  {item.label}
                </Link>
              ))}
            </div>
          </div>
        )}
      </div>
    </header>
  );
}
