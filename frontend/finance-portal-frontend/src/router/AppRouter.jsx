import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import AppLayout from '../app/layout/AppLayout';
import ProtectedRoute from '../app/layout/ProtectedRoute';
import AdminRoute from '../features/admin/components/AdminRoute';
import LoginPage from '../features/auth/LoginPage';
import RegisterPage from '../features/auth/RegisterPage';
import AuthCallbackPage from '../features/auth/AuthCallbackPage';
import VerifyEmailPage from '../features/auth/VerifyEmailPage';
import DashboardPage from '../features/dashboard/DashboardPage';
import PortfolioPage from '../features/portfolio/PortfolioPage';
import PortfolioDetailPage from '../features/portfolio/PortfolioDetailPage';
import AiAnalysisPage from '../features/portfolio/AiAnalysisPage';
import StocksPage from '../features/market/stock/StocksPage';
import StockDetailPage from '../features/market/stock/StockDetailPage';
import IndexDetailPage from '../features/market/stock/IndexDetailPage';
import CryptoPage from '../features/market/crypto/CryptoPage';
import CryptoDetailPage from '../features/market/crypto/CryptoDetailPage';
import FuturesPage from '../features/market/futures/FuturesPage';
import FuturesDetailPage from '../features/market/futures/FuturesDetailPage';
import FundsPage from '../features/market/funds/FundsPage';
import TefasPage from '../features/market/funds/TefasPage';
import TefasFundDetailPage from '../features/market/funds/TefasFundDetailPage';
import TefasComparePage from '../features/market/funds/TefasComparePage';
import FxPage from '../features/market/fx/FxPage';
import FxDetailPage from '../features/market/fx/FxDetailPage';
import BondsPage from '../features/market/bonds/BondsPage';
import BondDetailPage from '../features/market/bonds/BondDetailPage';
import EurobondDetailPage from '../features/market/bonds/EurobondDetailPage';
import EconomyPage from '../features/market/economy/EconomyPage';
import EconomicCalendarPage from '../features/market/economy/EconomicCalendarPage';
import LoanCalculatorPage from '../features/market/economy/LoanCalculatorPage';
import DepositCalculatorPage from '../features/market/economy/DepositCalculatorPage';
import GoldPage from '../features/market/gold/GoldPage';
import SilverPage from '../features/market/silver/SilverPage';
import PlatinumPage from '../features/market/precious/PlatinumPage';
import PalladiumPage from '../features/market/precious/PalladiumPage';
import CommodityComparePage from '../features/market/precious/CommodityComparePage';
import CommoditiesPage from '../features/market/commodities/CommoditiesPage';
import CommodityDetailPage from '../features/market/commodities/CommodityDetailPage';
import StockComparePage from '../features/market/stock/StockComparePage';
import ComparePage from '../features/market/fx/ComparePage';
import NewsPage from '../features/news/NewsPage';
import NewsDetailPage from '../features/news/NewsDetailPage';
import NotFoundPage from '../app/NotFoundPage';
import UnauthorizedPage from '../features/admin/UnauthorizedPage';
import AdminUsersPage from '../features/admin/AdminUsersPage';
import EurobondAdminPage from '../features/admin/EurobondAdminPage';
import ProfilePage from '../features/profile/ProfilePage';
import AlarmsPage from '../features/alarms/AlarmsPage';
import NotificationsPage from '../features/notifications/NotificationsPage';

export default function AppRouter() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Navigate to="/news" replace />} />
        <Route path="/auth/callback" element={<AuthCallbackPage />} />
        <Route path="/unauthorized" element={<UnauthorizedPage />} />

        {/* Public routes with layout */}
        <Route element={<AppLayout />}>
          <Route path="/login"             element={<LoginPage />} />
          <Route path="/register"          element={<RegisterPage />} />
          <Route path="/verify-email"      element={<VerifyEmailPage />} />
          <Route path="/news"              element={<NewsPage />} />
          <Route path="/news/:id"          element={<NewsDetailPage />} />
          <Route path="/market/stocks"     element={<StocksPage />} />
          <Route path="/market/stocks/compare" element={<StockComparePage />} />
          <Route path="/market/indices/:code"  element={<IndexDetailPage />} />
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
          <Route path="/market/bonds/global/:isin" element={<EurobondDetailPage />} />
          <Route path="/market/bonds/:symbol"  element={<BondDetailPage />} />
          <Route path="/market/economy"       element={<EconomyPage />} />
          <Route path="/market/economic-calendar" element={<EconomicCalendarPage />} />
          <Route path="/market/kredi-hesaplama" element={<LoanCalculatorPage />} />
          <Route path="/market/mevduat-hesaplama" element={<DepositCalculatorPage />} />
          <Route path="/market/gold"       element={<GoldPage />} />
          <Route path="/market/silver"     element={<SilverPage />} />
          <Route path="/market/platinum"   element={<PlatinumPage />} />
          <Route path="/market/palladium"  element={<PalladiumPage />} />
          <Route path="/market/commodities/compare" element={<CommodityComparePage />} />
          <Route path="/market/commodities" element={<CommoditiesPage />} />
          <Route path="/market/commodities/:symbol" element={<CommodityDetailPage />} />
          <Route path="/market/compare"    element={<ComparePage />} />

          {/* 404 fallback (public layout içinde — header/footer ile sarılır) */}
          <Route path="*" element={<NotFoundPage />} />
        </Route>

        {/* Authenticated (email verification optional) */}
        <Route element={<ProtectedRoute requireEmailVerified={false}><AppLayout /></ProtectedRoute>}>
          <Route path="/profile" element={<ProfilePage />} />
        </Route>

        {/* Protected routes */}
        <Route element={<ProtectedRoute><AppLayout /></ProtectedRoute>}>
          <Route path="/dashboard"         element={<DashboardPage />} />
          <Route path="/portfolio"         element={<PortfolioPage />} />
          <Route path="/portfolio/:id"     element={<PortfolioDetailPage />} />
          <Route path="/portfolio/:id/ai-analysis" element={<AiAnalysisPage />} />
          <Route path="/alarms"            element={<AlarmsPage />} />
          <Route path="/notifications"     element={<NotificationsPage />} />
        </Route>

        {/* Admin routes */}
        <Route element={<AdminRoute><AppLayout /></AdminRoute>}>
          <Route path="/admin" element={<Navigate to="/admin/users" replace />} />
          <Route path="/admin/users" element={<AdminUsersPage />} />
          <Route path="/admin/eurobonds" element={<EurobondAdminPage />} />
        </Route>

      </Routes>
    </BrowserRouter>
  );
}
