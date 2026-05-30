import { useEffect, useState } from 'react';
import { getStockMidasDetail } from '../../../api/marketApi';

// Sembol → logo URL (oturum boyu önbellek; her sembol en fazla bir kez çekilir).
const cache = new Map();
const inflight = new Map();

const COLORS = ['#093eaa', '#0ea5e9', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#ec4899', '#14b8a6'];
function colorFor(s) {
  let h = 0;
  for (const ch of String(s || '')) h = (h * 31 + ch.charCodeAt(0)) >>> 0;
  return COLORS[h % COLORS.length];
}

/**
 * Hisse şirket logosu (Midas) — yüklenene kadar harf rozeti gösterir, hata olursa rozette kalır.
 * Logo isteği lazy + önbellekli olduğundan listeyi yavaşlatmaz.
 */
export default function StockLogo({ symbol, name, size = 28 }) {
  const [url, setUrl] = useState(() => cache.get(symbol) ?? null);

  useEffect(() => {
    let on = true;
    if (cache.has(symbol)) { setUrl(cache.get(symbol)); return undefined; }
    let p = inflight.get(symbol);
    if (!p) {
      p = getStockMidasDetail(symbol)
        .then(d => {
          const u = d?.logoUrl ? `/api/proxy/image?url=${encodeURIComponent(d.logoUrl)}` : null;
          cache.set(symbol, u); inflight.delete(symbol); return u;
        })
        .catch(() => { cache.set(symbol, null); inflight.delete(symbol); return null; });
      inflight.set(symbol, p);
    }
    p.then(u => { if (on) setUrl(u); });
    return () => { on = false; };
  }, [symbol]);

  const px = { width: size, height: size };
  if (url) {
    return (
      <img
        src={url}
        alt=""
        style={px}
        className="rounded-full object-contain bg-white border border-gray-100 shrink-0"
        onError={() => { cache.set(symbol, null); setUrl(null); }}
      />
    );
  }
  const letter = String(symbol || name || '?').trim().charAt(0).toUpperCase();
  return (
    <span
      style={{ ...px, backgroundColor: colorFor(symbol) }}
      className="rounded-full flex items-center justify-center text-white text-[11px] font-bold shrink-0"
    >
      {letter}
    </span>
  );
}
