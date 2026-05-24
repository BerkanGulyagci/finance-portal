import { createContext, useContext, useCallback, useRef, useState } from 'react';
import { useAuth } from './AuthContext';
import {
  getMyPortfolios, getWatchlistItems, addWatchlistItem, deleteWatchlistItem, createPortfolio,
} from '../api/portfolioApi';

const WatchlistContext = createContext(null);

const keyOf = (assetType, symbol) => `${assetType}:${String(symbol).toUpperCase()}`;

/**
 * Kullanıcının tüm izleme listesi (WATCHLIST) öğelerini bir kez yükler ve
 * (assetType, symbol) → {portfolioId, itemId} eşlemesini tutar. Liste sayfalarındaki
 * yıldız butonları bunu kullanarak "ekli mi?" kontrolü ve ekle/çıkar yapar.
 */
export function WatchlistProvider({ children }) {
  const { isAuthenticated } = useAuth();
  const [map, setMap] = useState({});           // key → {portfolioId, itemId}
  const [version, setVersion] = useState(0);     // re-render tetikleyici
  const loadedRef = useRef(false);
  const loadingRef = useRef(false);
  const watchlistPidRef = useRef(null);

  const ensureLoaded = useCallback(async () => {
    if (!isAuthenticated || loadedRef.current || loadingRef.current) return;
    loadingRef.current = true;
    try {
      const portfolios = await getMyPortfolios();
      const watchlists = (portfolios ?? []).filter(p => p.portfolioType === 'WATCHLIST');
      if (watchlists.length > 0) watchlistPidRef.current = watchlists[0].id;
      const next = {};
      for (const wl of watchlists) {
        const items = await getWatchlistItems(wl.id).catch(() => []);
        for (const it of items) {
          next[keyOf(it.assetType, it.symbol)] = { portfolioId: wl.id, itemId: it.id };
        }
      }
      setMap(next);
      loadedRef.current = true;
      setVersion(v => v + 1);
    } finally {
      loadingRef.current = false;
    }
  }, [isAuthenticated]);

  const reload = useCallback(() => {
    loadedRef.current = false;
    return ensureLoaded();
  }, [ensureLoaded]);

  const isWatched = useCallback((assetType, symbol) => !!map[keyOf(assetType, symbol)], [map]);

  const toggle = useCallback(async (assetType, symbol, notes) => {
    const key = keyOf(assetType, symbol);
    const existing = map[key];
    if (existing) {
      await deleteWatchlistItem(existing.portfolioId, existing.itemId);
      setMap(prev => { const n = { ...prev }; delete n[key]; return n; });
      return false;
    }
    // Ekle — izleme listesi portföyü yoksa oluştur
    let pid = watchlistPidRef.current;
    if (!pid) {
      const created = await createPortfolio({
        name: 'İzleme Listem', description: '', currency: 'TRY', portfolioType: 'WATCHLIST',
      });
      pid = created.id;
      watchlistPidRef.current = pid;
    }
    const item = await addWatchlistItem(pid, { symbol, assetType, notes: notes ?? '' });
    setMap(prev => ({ ...prev, [key]: { portfolioId: pid, itemId: item.id } }));
    return true;
  }, [map]);

  return (
    <WatchlistContext.Provider value={{ ensureLoaded, reload, isWatched, toggle, version }}>
      {children}
    </WatchlistContext.Provider>
  );
}

export function useWatchlist() {
  const ctx = useContext(WatchlistContext);
  if (!ctx) throw new Error('useWatchlist must be used within WatchlistProvider');
  return ctx;
}
