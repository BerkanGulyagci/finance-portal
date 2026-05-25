import { AuthProvider } from './context/AuthContext';
import { ToastProvider } from './context/ToastContext';
import { LanguageProvider } from './i18n/LanguageContext';
import { WatchlistProvider } from './context/WatchlistContext';
import { PreferencesProvider } from './context/PreferencesContext';
import AppRouter from './router/AppRouter';

export default function App() {
  return (
    <LanguageProvider>
      <AuthProvider>
        <ToastProvider>
          <WatchlistProvider>
            <PreferencesProvider>
              <AppRouter />
            </PreferencesProvider>
          </WatchlistProvider>
        </ToastProvider>
      </AuthProvider>
    </LanguageProvider>
  );
}
