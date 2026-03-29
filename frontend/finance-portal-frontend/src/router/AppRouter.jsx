import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import AppLayout from '../components/layout/AppLayout';
import ProtectedRoute from '../components/common/ProtectedRoute';
import LoginPage from '../pages/LoginPage';
import DashboardPage from '../pages/DashboardPage';
import PortfolioPage from '../pages/PortfolioPage';
import PortfolioDetailPage from '../pages/PortfolioDetailPage';
import MarketPage from '../pages/MarketPage';
import NewsPage from '../pages/NewsPage';
import NotFoundPage from '../pages/NotFoundPage';

export default function AppRouter() {
  return (
    <BrowserRouter>
      <Routes>
        {/* "/" → News (public landing page) */}
        <Route path="/" element={<Navigate to="/news" replace />} />

        {/* Login — standalone, no layout */}
        <Route path="/login" element={<LoginPage />} />

        {/* Public — layout var, login gerekmez */}
        <Route element={<AppLayout />}>
          <Route path="/news"      element={<NewsPage />} />
          <Route path="/market"    element={<MarketPage />} />
          <Route path="/dashboard" element={<DashboardPage />} />
        </Route>

        {/* Protected — login gerekli */}
        <Route element={<ProtectedRoute><AppLayout /></ProtectedRoute>}>
          <Route path="/portfolio"     element={<PortfolioPage />} />
          <Route path="/portfolio/:id" element={<PortfolioDetailPage />} />
        </Route>

        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </BrowserRouter>
  );
}
