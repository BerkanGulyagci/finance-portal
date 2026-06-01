import { createRoot } from 'react-dom/client';
import './index.css';
import App from './App';
import { applyTheme, readTheme } from './context/ThemeContext';

// Temayı React mount'tan ÖNCE uygula → koyu modda açılışta beyaz flash (FOUC) olmaz.
applyTheme(readTheme());

createRoot(document.getElementById('root')).render(
  <App />
);
