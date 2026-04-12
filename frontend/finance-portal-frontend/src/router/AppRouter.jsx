import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import AppLayout from '../components/layout/AppLayout';
import ProtectedRoute from '../components/common/ProtectedRoute';
import LoginPage from '../pages/LoginPage';
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
import NewsPage from '../pages/NewsPage';
import NotFoundPage from '../pages/NotFoundPage';

export default function AppRouter() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Navigate to="/news" replace />} />
        <Route path="/login" element={<LoginPage />} />

        {/* Public routes with layout */}
        <Route element={<AppLayout />}>
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
