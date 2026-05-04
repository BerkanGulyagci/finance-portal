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
import StocksPage from '../pages/market/stock/StocksPage';
import StockDetailPage from '../pages/market/stock/StockDetailPage';
import CryptoPage from '../pages/market/crypto/CryptoPage';
import CryptoDetailPage from '../pages/market/crypto/CryptoDetailPage';
import FuturesPage from '../pages/market/futures/FuturesPage';
import FuturesDetailPage from '../pages/market/futures/FuturesDetailPage';
import FundsPage from '../pages/market/funds/FundsPage';
import TefasPage from '../pages/market/funds/TefasPage';
import TefasFundDetailPage from '../pages/market/funds/TefasFundDetailPage';
import TefasComparePage from '../pages/market/funds/TefasComparePage';
import FxPage from '../pages/market/fx/FxPage';
import FxDetailPage from '../pages/market/fx/FxDetailPage';
import BondsPage from '../pages/market/bonds/BondsPage';
import BondDetailPage from '../pages/market/bonds/BondDetailPage';
import GoldPage from '../pages/market/gold/GoldPage';
import StockComparePage from '../pages/market/stock/StockComparePage';
import ComparePage from '../pages/market/fx/ComparePage';
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
          <Route path="/market/stocks/compare" element={<StockComparePage />} />
          <Route path="/market/stocks/:symbol" element={<StockDetailPage />} />
          <Route path="/market/crypto"     element={<CryptoPage />} />
          <Route path="/market/crypto/:coinId" element={<CryptoDetailPage />} />
          <Route path="/market/futures"    element={<FuturesPage />} />
          <Route path="/market/futures/:symbol" element={<FuturesDetailPage />} />
          <Route path="/market/funds"      element={<FundsPage />} />
          <Route path="/market/tefas"         element={<TefasPage />} />
          <Route path="/market/tefas/compare" element={<TefasComparePage />} />
          <Route path="/market/tefas/:code"   element={<TefasFundDetailPage />} />
          <Route path="/market/fx"         element={<FxPage />} />
          <Route path="/market/fx/:symbol" element={<FxDetailPage />} />
          <Route path="/market/bonds"         element={<BondsPage />} />
          <Route path="/market/bonds/:symbol"  element={<BondDetailPage />} />
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
