import { useEffect, useState } from 'react';
import { FX_META } from '../../features/market/fx/utils/fxMeta';
import { getStockMidasDetail } from '../../api/marketApi';

// Sembol → Midas logo URL (oturum boyu önbellek; her sembol en fazla bir kez çekilir, eşzamanlı istekler tekilleşir).
const cache = new Map();
const inflight = new Map();

// Harf rozeti renkleri (StockLogo ile aynı palet).
const COLORS = ['#093eaa', '#0ea5e9', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#ec4899', '#14b8a6'];
function colorFor(s) {
  let h = 0;
  for (const ch of String(s || '')) h = (h * 31 + ch.charCodeAt(0)) >>> 0;
  return COLORS[h % COLORS.length];
}

// "THYAO.IS" → "THYAO" ; "AEFES (25 May 26) Vadeli…" → "AEFES" ; "USDTRY (…)" → "USDTRY"
function rootOf(s) {
  if (!s) return '';
  const up = String(s).trim().toUpperCase().replace(/\.IS$/, '');
  const m = up.match(/^[A-Z0-9]+/);
  return m ? m[0] : up;
}

// Para birimi dayanağı mı? "USD" / "USDTRY" / "EURTRY" → temel para birimi kodu (yoksa null).
function currencyOf(root) {
  if (!root) return null;
  if (FX_META[root]) return root;
  if (root.length === 6) {
    const base = root.slice(0, 3);
    if (FX_META[base]) return base;
  }
  return null;
}

/**
 * Liste satırları için küçük enstrüman logosu (hisse + VİOP):
 *  - Para birimi dayanağı (USD, USDTRY, EURTRY…) → yuvarlak bayrak (flag-icons, ağ isteği yok)
 *  - Aksi halde Midas şirket logosu (lazy + önbellekli, `/api/proxy/image` üzerinden) — yoksa harf rozeti
 *
 * Logo isteği lazy + önbellekli + eşzamanlı-tekilleştirmeli olduğundan listeyi yavaşlatmaz; aynı
 * dayanağa sahip birden çok VİOP sözleşmesi tek istek paylaşır.
 */
export default function InstrumentLogo({ symbol, name, size = 24, className = '' }) {
  const root = rootOf(symbol);
  const ccy = currencyOf(root);
  const [url, setUrl] = useState(() => cache.get(root) ?? null);

  useEffect(() => {
    if (ccy || !root) return undefined;       // para birimi → bayrak; logo çekme
    let on = true;
    if (cache.has(root)) { setUrl(cache.get(root)); return undefined; }
    let p = inflight.get(root);
    if (!p) {
      p = getStockMidasDetail(root)
        .then(d => {
          const u = d?.logoUrl ? `/api/proxy/image?url=${encodeURIComponent(d.logoUrl)}` : null;
          cache.set(root, u); inflight.delete(root); return u;
        })
        .catch(() => { cache.set(root, null); inflight.delete(root); return null; });
      inflight.set(root, p);
    }
    p.then(u => { if (on) setUrl(u); });
    return () => { on = false; };
  }, [root, ccy]);

  const px = { width: size, height: size };
  const box = `rounded-full shrink-0 ${className}`;

  // Para birimi → yuvarlak bayrak
  if (ccy) {
    return (
      <span style={px} className={`${box} ring-1 ring-gray-100 overflow-hidden inline-flex items-center justify-center bg-white`} title={FX_META[ccy].ad}>
        <span className={`fi fi-${FX_META[ccy].cc}`} style={{ width: size, height: size, backgroundSize: 'cover', backgroundPosition: 'center', display: 'inline-block' }} />
      </span>
    );
  }

  // Şirket logosu
  if (url) {
    return (
      <img
        src={url}
        alt=""
        loading="lazy"
        style={px}
        className={`${box} object-contain bg-white border border-gray-100`}
        onError={() => { cache.set(root, null); setUrl(null); }}
      />
    );
  }

  // Harf rozeti (logo yok / yükleniyor)
  const letter = (String(root || name || '?').trim().charAt(0) || '?').toUpperCase();
  return (
    <span style={{ ...px, backgroundColor: colorFor(root || name) }} className={`${box} inline-flex items-center justify-center text-white text-[11px] font-bold`}>
      {letter}
    </span>
  );
}
