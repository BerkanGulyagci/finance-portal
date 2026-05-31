import {
  Coins, Gem, Flame, Droplet, Wheat, Coffee, Sprout, Cloud, Bean, Pickaxe,
  Landmark, PiggyBank, CandlestickChart,
} from 'lucide-react';
import InstrumentLogo from './InstrumentLogo';

// Harf rozeti renkleri (StockLogo/InstrumentLogo ile aynı palet).
const COLORS = ['#093eaa', '#0ea5e9', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#ec4899', '#14b8a6'];
function colorFor(s) {
  let h = 0;
  for (const ch of String(s || '')) h = (h * 31 + ch.charCodeAt(0)) >>> 0;
  return COLORS[h % COLORS.length];
}

function LetterBadge({ text, size }) {
  const letter = (String(text || '?').trim().charAt(0) || '?').toUpperCase();
  return (
    <span
      style={{ width: size, height: size, backgroundColor: colorFor(text), fontSize: Math.round(size * 0.4) }}
      className="rounded-full flex items-center justify-center text-white font-bold shrink-0"
    >
      {letter}
    </span>
  );
}

// Emtia / kıymetli metal sembolü → temsili lucide ikon + tematik renk rozeti.
function commodityVisual(symbol) {
  const s = String(symbol || '').toUpperCase();
  if (s.startsWith('SILVER'))    return { Icon: Coins, bg: 'bg-slate-100',   fg: 'text-slate-500' };
  if (s.startsWith('PLATINUM'))  return { Icon: Gem,   bg: 'bg-sky-100',     fg: 'text-sky-600' };
  if (s.startsWith('PALLADIUM')) return { Icon: Gem,   bg: 'bg-indigo-100',  fg: 'text-indigo-500' };
  const MAP = {
    'CL=F': { Icon: Droplet, bg: 'bg-zinc-200',   fg: 'text-zinc-700' },   // WTI petrol
    'BZ=F': { Icon: Droplet, bg: 'bg-zinc-200',   fg: 'text-zinc-700' },   // Brent petrol
    'NG=F': { Icon: Flame,   bg: 'bg-orange-100',  fg: 'text-orange-600' }, // doğal gaz
    'HG=F': { Icon: Pickaxe, bg: 'bg-amber-100',   fg: 'text-amber-700' },  // bakır
    'ZW=F': { Icon: Wheat,   bg: 'bg-yellow-100',  fg: 'text-yellow-700' }, // buğday
    'ZC=F': { Icon: Sprout,  bg: 'bg-lime-100',    fg: 'text-lime-700' },   // mısır
    'KC=F': { Icon: Coffee,  bg: 'bg-amber-100',   fg: 'text-amber-800' },  // kahve
    'CC=F': { Icon: Bean,    bg: 'bg-amber-100',   fg: 'text-amber-900' },  // kakao
    'CT=F': { Icon: Cloud,   bg: 'bg-slate-100',   fg: 'text-slate-500' },  // pamuk
  };
  return MAP[s] || { Icon: Droplet, bg: 'bg-emerald-50', fg: 'text-emerald-600' };
}

/**
 * Tüm enstrüman türleri için tek, kaliteli ikon bileşeni:
 *  - Gerçek görsel (kripto/CoinGecko) varsa → görsel
 *  - Hisse → Midas şirket logosu, Döviz → ülke bayrağı (InstrumentLogo üzerinden)
 *  - Altın / Kıymetli metal / Emtia / Tahvil / Fon / Vadeli → temalı lucide ikon rozeti
 *  - Aksi halde → renkli harf rozeti
 */
export default function AssetIcon({ assetType, symbol, name, image, size = 28 }) {
  if (image) {
    return (
      <img src={image} alt="" loading="lazy" style={{ width: size, height: size }}
        className="rounded-full object-cover bg-white shrink-0" />
    );
  }

  const t = String(assetType || '').toUpperCase();

  if (t === 'STOCK' || t === 'FX') {
    return <InstrumentLogo symbol={symbol} name={name} size={size} />;
  }
  if (t === 'CRYPTO') {
    return <LetterBadge text={symbol || name} size={size} />;
  }

  let vis;
  if (t === 'GOLD')           vis = { Icon: Coins, bg: 'bg-amber-100', fg: 'text-amber-600' };
  else if (t === 'COMMODITY') vis = commodityVisual(symbol);
  else if (t === 'BOND')      vis = { Icon: Landmark, bg: 'bg-blue-50', fg: 'text-blue-600' };
  else if (t === 'FUND')      vis = { Icon: PiggyBank, bg: 'bg-emerald-50', fg: 'text-emerald-600' };
  else if (t === 'FUTURE')    vis = { Icon: CandlestickChart, bg: 'bg-violet-50', fg: 'text-violet-600' };
  else return <LetterBadge text={symbol || name} size={size} />;

  const { Icon, bg, fg } = vis;
  return (
    <span style={{ width: size, height: size }} className={`rounded-full flex items-center justify-center shrink-0 ${bg} ${fg}`}>
      <Icon style={{ width: Math.round(size * 0.55), height: Math.round(size * 0.55) }} strokeWidth={2.2} />
    </span>
  );
}
