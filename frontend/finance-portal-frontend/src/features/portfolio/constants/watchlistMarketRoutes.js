/**
 * İzleme listesi kalemi → piyasa detay route'u (React Router path).
 * Bilinmeyen / eşleşmeyen durumlarda liste sayfasına veya genel altın/kripto sayfasına düşer.
 */

/** CoinGecko benzersiz id — sembol eşlemesi (watchlist sembolü genelde büyük harf BTC, XRP) */
const CRYPTO_SYMBOL_TO_ID = {
  BTC: 'bitcoin',
  ETH: 'ethereum',
  USDT: 'tether',
  BNB: 'binancecoin',
  SOL: 'solana',
  XRP: 'ripple',
  DOGE: 'dogecoin',
  ADA: 'cardano',
  AVAX: 'avalanche-2',
  MATIC: 'matic-network',
  DOT: 'polkadot',
  LINK: 'chainlink',
  LTC: 'litecoin',
  TRX: 'tron',
  ATOM: 'cosmos',
  UNI: 'uniswap',
  XLM: 'stellar',
  BCH: 'bitcoin-cash',
  NEAR: 'near',
  APT: 'aptos',
  ARB: 'arbitrum',
  OP: 'optimism',
  FIL: 'filecoin',
  INJ: 'injective-protocol',
  PEPE: 'pepe',
  SHIB: 'shiba-inu',
  FTM: 'fantom',
  HBAR: 'hedera-hashgraph',
  VET: 'vechain',
  ALGO: 'algorand',
  ETC: 'ethereum-classic',
  XMR: 'monero',
  AAVE: 'aave',
  MKR: 'maker',
  GRT: 'the-graph',
  SAND: 'the-sandbox',
  MANA: 'decentraland',
  AXS: 'axie-infinity',
  CHZ: 'chiliz',
  ENJ: 'enjin-coin',
  BAT: 'basic-attention-token',
  ZEC: 'zcash',
  EOS: 'eos',
  THETA: 'theta-token',
  ICP: 'internet-computer',
  SEI: 'sei-network',
  TIA: 'celestia',
  WLD: 'worldcoin-wld',
  RENDER: 'render-token',
  IMX: 'immutable-x',
  SNX: 'havven',
  CRV: 'curve-dao-token',
  LDO: 'lido-dao',
};

/**
 * BIST endeks kodları — watchlist'te endeks STOCK+{KOD}.IS olarak saklanır (AssetType enum'unda INDEX yok),
 * dashboard'da ise INDICATOR+{KOD}. İkisinde de detay /market/indices/:code'a gitsin diye burada tanınır.
 * Katalog ile eşle (BistIndexCatalog). Endeks kodları stabildir.
 */
const BIST_INDEX_CODES = new Set([
  'XU100', 'XU030', 'XU050', 'XU500', 'XUTUM', 'XBANA', 'X10XB',
  'XUSIN', 'XUHIZ', 'XUMAL', 'XUTEK',
  'XBANK', 'XHOLD', 'XGIDA', 'XKMYA', 'XMESY', 'XMANA', 'XTAST', 'XELKT', 'XILTM', 'XBLSM',
  'XTCRT', 'XULAS', 'XSGRT', 'XFINK', 'XGMYO', 'XMADN', 'XTEKS', 'XKAGT', 'XINSA', 'XTRZM', 'XSPOR',
  'XK100', 'XK050', 'XK030', 'XKTUM',
  'XTMTU', 'XTM25', 'XKURY', 'XHARZ', 'XYORT',
]);

/** Sembol bir BIST endeksi mi? (.IS soneki olsa da olmasa da). → endeks kodu ya da null. */
export function indexCodeOf(symbol) {
  const code = String(symbol ?? '').trim().toUpperCase().replace(/\.IS$/, '');
  return BIST_INDEX_CODES.has(code) ? code : null;
}

/** Statik altın ürün kodu → GoldPage sekme anahtarı (goldConstants GOLD_TABS.key) */
const GOLD_WATCHLIST_TO_TAB = {
  GOLD: 'ons',
  GRAM: 'gram',
  CEYREK: 'ceyrek',
  YARIM: 'yarim',
  CUMHUR: 'cumhuriyet',
};

function cryptoSymbolToCoinId(symbol) {
  if (!symbol) return null;
  const u = String(symbol).trim().toUpperCase();
  return CRYPTO_SYMBOL_TO_ID[u] ?? null;
}

/** BIST kıymetli maden kodu METAL:CATEGORY — veriler BİST’ten gelir; Yahoo emtia detayına yönlendirilmez */
const BIST_PRECIOUS_CATEGORY_TO_TAB = {
  GRAM_TRY: 'try_gram',
  KG_TRY: 'try_kg',
  USD_ONS: 'usd_ons',
  EUR_ONS: 'eur_ons',
};

function preciousBistSymbolToPath(symbol) {
  const s = String(symbol).trim();
  if (!s.includes(':')) return null;
  const idx = s.indexOf(':');
  const metal = s.slice(0, idx).toUpperCase();
  const cat = s.slice(idx + 1).toUpperCase();
  let tab = BIST_PRECIOUS_CATEGORY_TO_TAB[cat];
  if (!tab) return null;

  if (metal === 'SILVER') {
    // Gümüş sayfasında EUR sekmesi yok
    if (tab === 'eur_ons') tab = 'usd_ons';
    return { path: '/market/silver', tab };
  }
  if (metal === 'PLATINUM') return { path: '/market/platinum', tab };
  if (metal === 'PALLADIUM') return { path: '/market/palladium', tab };
  return null;
}

/**
 * BIST kıymetli maden (`SILVER:GRAM_TRY` vb.) → gümüş/platin/paladyum sayfası yolu.
 * @returns {{ path: string, tab: string } | null}
 */
export function getPreciousMetalMarketPath(symbol) {
  return preciousBistSymbolToPath(symbol);
}

/**
 * @param {string} assetType — STOCK | CRYPTO | …
 * @param {string} symbol
 * @returns {string|null} — null ise detay linki üretilmez
 */
export function getWatchlistDetailPath(assetType, symbol) {
  if (!symbol || symbol === '') return null;
  const sym = String(symbol).trim();

  // Endeksler her tipte (STOCK+.IS veya INDICATOR+kod) endeks detayına gider.
  const idxCode = indexCodeOf(sym);
  if (idxCode) return `/market/indices/${encodeURIComponent(idxCode)}`;

  switch (assetType) {
    case 'STOCK':
      return `/market/stocks/${encodeURIComponent(sym)}`;
    case 'CRYPTO': {
      const id = cryptoSymbolToCoinId(sym);
      return id ? `/market/crypto/${encodeURIComponent(id)}` : '/market/crypto';
    }
    case 'FUND':
      return `/market/tefas/${encodeURIComponent(sym)}`;
    case 'COMMODITY': {
      const precious = preciousBistSymbolToPath(sym);
      if (precious) {
        return `${precious.path}?tab=${encodeURIComponent(precious.tab)}`;
      }
      return `/market/commodities/${encodeURIComponent(sym)}`;
    }
    case 'GOLD': {
      const tab = GOLD_WATCHLIST_TO_TAB[sym.toUpperCase()];
      return tab ? `/market/gold?tab=${encodeURIComponent(tab)}` : '/market/gold';
    }
    case 'FUTURE':
      return `/market/futures/${encodeURIComponent(sym)}`;
    case 'FX':
      return `/market/fx/${encodeURIComponent(sym)}`;
    case 'BOND': {
      // Eurobond (Hazine dış borç) ISIN'i (TR ile başlamayan tam ISIN) → global detay sayfası;
      // yurt içi DİBS → EVDS detay sayfası.
      const isEurobond = /^[A-Z]{2}[A-Z0-9]{9}[0-9]$/.test(sym.toUpperCase()) && !sym.toUpperCase().startsWith('TR');
      return isEurobond
        ? `/market/bonds/global/${encodeURIComponent(sym)}`
        : `/market/bonds/${encodeURIComponent(sym)}`;
    }
    default:
      return null;
  }
}
