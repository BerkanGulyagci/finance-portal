import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';

export default function ProtectedRoute({ children, requireEmailVerified = true }) {
  const { isAuthenticated, emailVerified } = useAuth();
  const location = useLocation();

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location.pathname }} replace />;
  }

  if (requireEmailVerified && !emailVerified) {
    return <Navigate to="/verify-email" state={{ from: location.pathname }} replace />;
  }

  return children;
}
