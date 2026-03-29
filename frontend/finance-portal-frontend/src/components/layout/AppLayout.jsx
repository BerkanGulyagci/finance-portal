import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';

const navItems = [
  { to: '/dashboard', label: 'Dashboard' },
  { to: '/portfolio', label: 'Portfolio' },
  { to: '/market', label: 'Market' },
  { to: '/news', label: 'News' },
];

export default function AppLayout() {
  const { logout } = useAuth();
  const navigate = useNavigate();

  function handleLogout() {
    logout();
    navigate('/');
  }

  return (
    <div>
      <nav style={{ display: 'flex', gap: '1rem', padding: '1rem', borderBottom: '1px solid #ccc', alignItems: 'center' }}>
        {navItems.map(({ to, label }) => (
          <NavLink
            key={to}
            to={to}
            style={({ isActive }) => ({ fontWeight: isActive ? 'bold' : 'normal' })}
          >
            {label}
          </NavLink>
        ))}
        <button onClick={handleLogout} style={{ marginLeft: 'auto' }}>
          Çıkış
        </button>
      </nav>
      <main style={{ padding: '1.5rem' }}>
        <Outlet />
      </main>
    </div>
  );
}
