import { AuthProvider } from './context/AuthContext';
import { ToastProvider } from './context/ToastContext';
import { LanguageProvider } from './context/LanguageContext';
import { WatchlistProvider } from './context/WatchlistContext';
import { PreferencesProvider } from './context/PreferencesContext';
import { ThemeProvider } from './context/ThemeContext';
import AppRouter from './router/AppRouter';

export default function App() {
  return (
    <LanguageProvider>
      <AuthProvider>
        <ToastProvider>
          <WatchlistProvider>
            <PreferencesProvider>
              <ThemeProvider>
                <AppRouter />
              </ThemeProvider>
            </PreferencesProvider>
          </WatchlistProvider>
        </ToastProvider>
      </AuthProvider>
    </LanguageProvider>
  );
}
