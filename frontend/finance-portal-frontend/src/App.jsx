import { AuthProvider } from './context/AuthContext';
import { ToastProvider } from './context/ToastContext';
import { LanguageProvider } from './i18n/LanguageContext';
import { WatchlistProvider } from './context/WatchlistContext';
import AppRouter from './router/AppRouter';

export default function App() {
  return (
    <LanguageProvider>
      <AuthProvider>
        <ToastProvider>
          <WatchlistProvider>
            <AppRouter />
          </WatchlistProvider>
        </ToastProvider>
      </AuthProvider>
    </LanguageProvider>
  );
}
