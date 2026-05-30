/**
 * Crypto detay sayfası — paylaşılan sabitler (chart + compare dropdown + ana sayfa).
 */

/** Karşılaştırma çizgi renkleri — ilk renk ana coin, sonrakiler karşılaştırılanlar. */
export const COMPARE_COLORS = ['#093eaa', '#f97316', '#8b5cf6', '#10b981', '#ef4444'];

/** Karşılaştırma dropdown'da arama yapılmazken gösterilen popüler coin'ler. */
export const POPULAR_COINS = [
  { id: 'bitcoin',  symbol: 'BTC', name: 'Bitcoin' },
  { id: 'ethereum', symbol: 'ETH', name: 'Ethereum' },
  { id: 'tether',   symbol: 'USDT', name: 'Tether' },
  { id: 'binancecoin', symbol: 'BNB', name: 'BNB' },
  { id: 'solana',   symbol: 'SOL', name: 'Solana' },
  { id: 'ripple',   symbol: 'XRP', name: 'XRP' },
  { id: 'dogecoin', symbol: 'DOGE', name: 'Dogecoin' },
  { id: 'cardano',  symbol: 'ADA', name: 'Cardano' },
];

/** Hareketli ortalama seçenekleri (chart MA butonlarında). */
export const MA_OPTIONS = [
  { period: 20,  label: 'MA20',  color: '#f59e0b' },
  { period: 50,  label: 'MA50',  color: '#8b5cf6' },
  { period: 200, label: 'MA200', color: '#ef4444' },
];
