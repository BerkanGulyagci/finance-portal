import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import AppLayout from '../components/layout/AppLayout';
import ProtectedRoute from '../components/common/ProtectedRoute';
import LoginPage from '../pages/LoginPage';
import RegisterPage from '../pages/RegisterPage';
import AuthCallbackPage from '../pages/AuthCallbackPage';
import DashboardPage from '../pages/DashboardPage';
import PortfolioPage from '../pages/PortfolioPage';
import PortfolioDetailPage from '../pages/PortfolioDetailPage';
import MarketPage from '../pages/MarketPage';
import StocksPage from '../pages/market/StocksPage';
import StockDetailPage from '../pages/market/StockDetailPage';
import CryptoPage from '../pages/market/CryptoPage';
import FuturesPage from '../pages/market/FuturesPage';
import FundsPage from '../pages/market/FundsPage';
import TefasPage from '../pages/market/TefasPage';
import FxPage from '../pages/market/FxPage';
import FxDetailPage from '../pages/market/FxDetailPage';
import BondsPage from '../pages/market/BondsPage';
import GoldPage from '../pages/market/GoldPage';
import ComparePage from '../pages/market/ComparePage';
import NewsPage from '../pages/NewsPage';
import NotFoundPage from '../pages/NotFoundPage';

export default function AppRouter() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Navigate to="/news" replace />} />
        <Route path="/auth/callback" element={<AuthCallbackPage />} />

        {/* Public routes with layout */}
        <Route element={<AppLayout />}>
          <Route path="/login"             element={<LoginPage />} />
          <Route path="/register"          element={<RegisterPage />} />
          <Route path="/news"              element={<NewsPage />} />
          <Route path="/dashboard"         element={<DashboardPage />} />
          <Route path="/market"            element={<MarketPage />} />
          <Route path="/market/stocks"     element={<StocksPage />} />
          <Route path="/market/stocks/:symbol" element={<StockDetailPage />} />
          <Route path="/market/crypto"     element={<CryptoPage />} />
          <Route path="/market/futures"    element={<FuturesPage />} />
          <Route path="/market/funds"      element={<FundsPage />} />
          <Route path="/market/tefas"      element={<TefasPage />} />
          <Route path="/market/fx"         element={<FxPage />} />
          <Route path="/market/fx/:symbol" element={<FxDetailPage />} />
          <Route path="/market/bonds"      element={<BondsPage />} />
          <Route path="/market/gold"       element={<GoldPage />} />
          <Route path="/market/compare"    element={<ComparePage />} />
        </Route>

        {/* Protected routes */}
        <Route element={<ProtectedRoute><AppLayout /></ProtectedRoute>}>
          <Route path="/portfolio"         element={<PortfolioPage />} />
          <Route path="/portfolio/:id"     element={<PortfolioDetailPage />} />
        </Route>

        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </BrowserRouter>
  );
}
