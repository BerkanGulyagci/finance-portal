import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../../../context/AuthContext';

export default function AdminRoute({ children }) {
  const { isAuthenticated, isAdmin, emailVerified } = useAuth();
  const location = useLocation();

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location.pathname }} replace />;
  }

  if (!emailVerified) {
    return <Navigate to="/verify-email" state={{ from: location.pathname }} replace />;
  }

  if (!isAdmin) {
    return <Navigate to="/unauthorized" replace />;
  }

  return children;
}
