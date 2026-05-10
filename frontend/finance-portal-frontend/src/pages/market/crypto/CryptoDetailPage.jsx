import { useEffect, useState, useCallback, useRef, useMemo } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import { ArrowLeft, TrendingUp, TrendingDown, Globe, ChevronDown, ChevronUp, ExternalLink, Plus, X } from 'lucide-react';
import {
  XAxis, YAxis, CartesianGrid, Tooltip,
  ResponsiveContainer, LineChart, Line, Brush,
  ComposedChart, Area, Bar,
} from 'recharts';
import { getCryptoChart, getCryptoDetail, getAllCryptos, getCryptoOhlc } from '../../../api/marketApi';
import { useAuth } from '../../../context/AuthContext';
import { init as klineInit, dispose as klineDispose } from 'klinecharts';
import CommodityDetailChart   from '../commodities/components/CommodityDetailChart';
import CommodityDetailToolbar from '../commodities/components/CommodityDetailToolbar';

const RANGES = [
  { label: '1G', days: 1 },
  { label: '7G', days: 7 },
  { label: '1A', days: 30 },
  { label: '3A', days: 90 },
  { label: '1Y', days: 365 },
];

const CURRENCIES = ['TRY', 'USD', 'EUR'];
const CURRENCY_SYMBOLS = { TRY: '₺', USD: '$', EUR: '€' };

// Karşılaştırma renkleri
const COMPARE_COLORS = ['#093eaa', '#f97316', '#8b5cf6', '#10b981', '#ef4444'];

// Popüler coinler (karşılaştırma dropdown'u için)
const POPULAR_COINS = [
  { id: 'bitcoin',  symbol: 'BTC', name: 'Bitcoin' },
  { id: 'ethereum', symbol: 'ETH', name: 'Ethereum' },
  { id: 'tether',   symbol: 'USDT', name: 'Tether' },
  { id: 'binancecoin', symbol: 'BNB', name: 'BNB' },
  { id: 'solana',   symbol: 'SOL', name: 'Solana' },
  { id: 'ripple',   symbol: 'XRP', name: 'XRP' },
  { id: 'dogecoin', symbol: 'DOGE', name: 'Dogecoin' },
  { id: 'cardano',  symbol: 'ADA', name: 'Cardano' },
];

function fmtPrice(v, currency) {
  if (v == null) return '-';
  const sym = CURRENCY_SYMBOLS[currency] ?? currency;
  const n = parseFloat(v);
  let dec;
  if (n >= 1000)        dec = 2;
  else if (n >= 1)      dec = 4;
  else if (n >= 0.01)   dec = 6;
  else if (n >= 0.0001) dec = 8;
  else                  dec = 10;
  return `${sym}${n.toLocaleString('tr-TR', { minimumFractionDigits: dec, maximumFractionDigits: dec })}`;
}

function fmt(v, dec = 2) {
  if (v == null) return '-';
  return parseFloat(v).toLocaleString('tr-TR', { minimumFractionDigits: dec, maximumFractionDigits: dec });
}

function fmtDate(ts, days) {
  const d = new Date(ts);
  if (days <= 1) return d.toLocaleTimeString('tr-TR', { hour: '2-digit', minute: '2-digit' });
  if (days <= 30) return d.toLocaleDateString('tr-TR', { day: '2-digit', month: '2-digit' });
  return d.toLocaleDateString('tr-TR', { month: '2-digit', year: '2-digit' });
}

function pct(v) {
  if (v == null) return <span className="text-gray-400">-</span>;
  const pos = v >= 0;
  return (
    <span className={`flex items-center gap-0.5 font-bold text-xs ${pos ? 'text-emerald-600' : 'text-rose-600'}`}>
      {pos ? <TrendingUp className="w-3 h-3" /> : <TrendingDown className="w-3 h-3" />}
      {pos ? '+' : ''}{fmt(v)}%
    </span>
  );
}

// MA hesaplama yardımcısı
function calcMA(data, period) {
  return data.map((_, i) => {
    if (i < period - 1) return null;
    const slice = data.slice(i - period + 1, i + 1);
    return slice.reduce((s, d) => s + (d.price ?? 0), 0) / period;
  });
}

// RSI hesaplama (14 periyot)
function calcRSI(data, period = 14) {
  if (data.length < period + 1) return null;
  const prices = data.map(d => d.price ?? 0);
  let gains = 0, losses = 0;
  for (let i = 1; i <= period; i++) {
    const diff = prices[i] - prices[i - 1];
    if (diff >= 0) gains += diff; else losses -= diff;
  }
  let avgGain = gains / period;
  let avgLoss = losses / period;
  for (let i = period + 1; i < prices.length; i++) {
    const diff = prices[i] - prices[i - 1];
    avgGain = (avgGain * (period - 1) + Math.max(diff, 0)) / period;
    avgLoss = (avgLoss * (period - 1) + Math.max(-diff, 0)) / period;
  }
  if (avgLoss === 0) return 100;
  const rs = avgGain / avgLoss;
  return 100 - (100 / (1 + rs));
}

function RSIBadge({ rsi }) {
  if (rsi == null) return null;
  const val = parseFloat(rsi.toFixed(1));
  let label, bg, text;
  if (val >= 70)      { label = 'Aşırı Alım'; bg = 'bg-rose-100';    text = 'text-rose-700'; }
  else if (val <= 30) { label = 'Aşırı Satım'; bg = 'bg-emerald-100'; text = 'text-emerald-700'; }
  else                { label = 'Normal';       bg = 'bg-gray-100';    text = 'text-gray-600'; }

  // Gauge yüzdesi (0-100 → 0%-100%)
  const pct = Math.min(100, Math.max(0, val));

  return (
    <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-5">
      <div className="flex items-center justify-between mb-3">
        <p className="text-xs font-bold text-gray-500 uppercase tracking-wider">RSI (14)</p>
        <span className={`text-xs font-bold px-2 py-0.5 rounded-full ${bg} ${text}`}>{label}</span>
      </div>
      <p className="text-2xl font-black text-gray-900 mb-3">{val}</p>
      {/* Gauge bar */}
      <div className="relative h-2 rounded-full overflow-hidden mb-1" style={{
        background: 'linear-gradient(to right, #10b981 0%, #10b981 30%, #f59e0b 30%, #f59e0b 70%, #ef4444 70%, #ef4444 100%)'
      }}>
        <div className="absolute top-1/2 -translate-y-1/2 w-3 h-3 rounded-full bg-white border-2 border-gray-700 shadow"
          style={{ left: `calc(${pct}% - 6px)` }} />
      </div>
      <div className="flex justify-between text-xs text-gray-400 mt-1">
        <span>0</span><span className="text-emerald-600 font-semibold">30</span>
        <span className="text-rose-600 font-semibold">70</span><span>100</span>
      </div>
    </div>
  );
}

const MA_OPTIONS = [
  { period: 7,  label: 'MA7',  color: '#f59e0b' },
  { period: 25, label: 'MA25', color: '#8b5cf6' },
  { period: 99, label: 'MA99', color: '#ef4444' },
];

// Range'e göre anlamlı MA periyotları
const MA_FOR_RANGE = {
  1:   [7],
  7:   [7],
  30:  [7, 25],
  90:  [7, 25, 99],
  365: [25, 99],
};

const EXCHANGE_OVERRIDES = {
  'whitebit': 'BYBIT:WBTUSDT', 'uniswap': 'COINBASE:UNIUSD',
  'chainlink': 'COINBASE:LINKUSD', 'stellar': 'COINBASE:XLMUSD',
  'vechain': 'BYBIT:VETUSDT', 'filecoin': 'COINBASE:FILUSD',
  'the-graph': 'COINBASE:GRTUSD', 'helium': 'COINBASE:HNTUSD',
  'kucoin-shares': 'KUCOIN:KCSUSDT', 'okb': 'OKX:OKBUSDT',
  'mog-coin': 'BYBIT:MOGUSDT', 'pepe': 'BYBIT:PEPEUSDT',
  'floki': 'BYBIT:FLOKIUSDT', 'bonk': 'BYBIT:BONKUSDT',
  'dogwifcoin': 'BYBIT:WIFUSDT', 'brett': 'BYBIT:BRETTUSDT',
  'popcat': 'BYBIT:POPCATUSDT', 'neiro': 'BYBIT:NEIROUSDT',
  'turbo': 'BYBIT:TURBOUSDT',
};

function resolveTvSymbol(coinId, symbol) {
  if (EXCHANGE_OVERRIDES[coinId]) return EXCHANGE_OVERRIDES[coinId];
  return `BINANCE:${(symbol || coinId).toUpperCase()}USDT`;
}

function TradingViewChart({ coinId, symbol }) {
  const tvSymbol = resolveTvSymbol(coinId, symbol);
  const exchange = tvSymbol.split(':')[0];
  const src = `https://www.tradingview.com/widgetembed/?frameElementId=tv_chart&symbol=${encodeURIComponent(tvSymbol)}&interval=60&hidesidetoolbar=0&hidetoptoolbar=0&symboledit=1&saveimage=0&toolbarbg=f1f3f6&studies=[]&theme=light&style=1&timezone=Europe%2FIstanbul&withdateranges=1&showpopupbutton=1&locale=tr`;
  return (
    <div>
      <iframe src={src} style={{ width: '100%', height: '620px', border: 'none' }}
        allowTransparency allowFullScreen title="TradingView Chart" />
      <p className="text-xs text-gray-400 px-6 pb-3">Grafik Kaynağı: {exchange}</p>
    </div>
  );
}

/* ─── KlineCharts Çizgi Grafiği (Crypto için) ─── */
function CryptoLineChart({ chartData, currency, range, compareCoins, compareData, coinId, mainCoinSymbol, activeMAs }) {
  const chartId = useRef(`kline_crypto_${Date.now()}`);
  const chartRef = useRef(null);
  const isComparing = compareCoins && compareCoins.length > 0;

  // Hover tooltip state
  const [hoverData, setHoverData] = useState(null);

  // Karşılaştırma için normalize edilmiş veri ref (tooltip'te kullanmak için)
  const normalizedDataRef = useRef([]);
  const compareOverlayRef = useRef({}); // {coinId: [{ts, value}]}

  useEffect(() => {
    if (!chartData || chartData.length === 0) return;

    const id = chartId.current;
    const chart = klineInit(id);
    chartRef.current = chart;

    if (isComparing) {
      // Karşılaştırma modu: % bazlı normalize edilmiş veriler
      const base0 = chartData[0]?.price;
      if (!base0) return;

      const normalizedData = chartData.map((point) => {
        const pctChange = base0 > 0 ? ((point.price - base0) / base0) * 100 : 0;
        
        return {
          timestamp: point.ts,
          open: pctChange,
          high: pctChange,
          low: pctChange,
          close: pctChange,
          volume: 0,
          turnover: 0,
        };
      }).filter(d => !isNaN(d.close)).sort((a, b) => a.timestamp - b.timestamp);

      if (normalizedData.length === 0) return;

      // Normalize data'yı ref'e kaydet (tooltip için)
      normalizedDataRef.current = normalizedData;

      // Ana coin için line style
      chart.setStyles({
        candle: {
          type: 'area',
          tooltip: {
            showRule: 'none',   // OHLC satırını kapat
          },
          priceMark: {
            last: { show: false },  // Sağdaki son fiyat etiketini kapat
          },
        },
        indicator: {
          lastValueMark: { show: false },
          tooltip: { showRule: 'none' },
        },
        crosshair: {
          horizontal: {
            text: { show: false },
          },
        },
      });

      chart.applyNewData(normalizedData);

      // Ana coin çizgisi (candle olarak)
      chart.setStyles({
        candle: {
          type: 'area',
          area: {
            lineColor: COMPARE_COLORS[0],
            lineSize: 2,
            value: 'close',
            smooth: true,
            backgroundColor: [
              { offset: 0, color: COMPARE_COLORS[0] + '15' },
              { offset: 1, color: COMPARE_COLORS[0] + '00' },
            ],
          },
        },
      });

      // Karşılaştırma coinleri için custom indicator ekle
      compareCoins.forEach((c, i) => {
        const prices = compareData[c.id] ?? [];
        const base = prices[0]?.[1];
        if (!base || base === 0) return;

        // Her karşılaştırma coini için overlay line ekle
        const overlayData = normalizedData.map(point => {
          // Timestamp bazlı en yakın noktayı bul
          let closest = prices[0];
          let minDiff = Math.abs(prices[0][0] - point.timestamp);
          for (let j = 1; j < prices.length; j++) {
            const diff = Math.abs(prices[j][0] - point.timestamp);
            if (diff < minDiff) { minDiff = diff; closest = prices[j]; }
            else break;
          }
          
          const pctChange = ((closest[1] - base) / base) * 100;
          return { value: pctChange };
        });

        // Custom overlay olarak ekle
        try {
          chart.createIndicator({
            name: 'MA',
            calcParams: [1], // Dummy MA, sadece çizgi çizmek için
            figures: [{
              key: 'ma',
              title: '',   // Grafik üzerindeki coin adı yazısını kapat
              type: 'line',
              baseValue: 0,
              styles: (data, indicator, defaultStyles) => {
                return {
                  line: {
                    style: 'solid',
                    smooth: true,
                    size: 2,
                    color: COMPARE_COLORS[i + 1] || '#6b7280',
                  }
                };
              }
            }],
            calc: (dataList) => {
              return dataList.map((kLineData, i) => {
                return { ma: overlayData[i]?.value ?? null };
              });
            },
          }, false, { id: 'candle_pane' });
        } catch (e) {
          console.error('Error adding compare line:', e);
        }

        // Overlay data'yı ref'e kaydet (tooltip için)
        compareOverlayRef.current[c.id] = overlayData;
      });

      // Crosshair hover event — floating tooltip için
      try {
        chart.subscribeAction('onCrosshairChange', (data) => {
          if (!data || data.dataIndex == null || data.dataIndex < 0) {
            setHoverData(null);
            return;
          }
          const idx = data.dataIndex;
          const mainPoint = normalizedDataRef.current[idx];
          if (!mainPoint) { setHoverData(null); return; }

          const date = new Date(mainPoint.timestamp).toLocaleString('tr-TR', {
            month: '2-digit', day: '2-digit',
            hour: '2-digit', minute: '2-digit',
          });

          const entries = [
            {
              symbol: mainCoinSymbol?.toUpperCase() ?? coinId,
              value: mainPoint.close,
              color: COMPARE_COLORS[0],
            },
            ...compareCoins.map((c, ci) => {
              const overlay = compareOverlayRef.current[c.id] ?? [];
              const pt = overlay[idx];
              return {
                symbol: c.symbol?.toUpperCase() ?? c.id,
                value: pt?.value ?? null,
                color: COMPARE_COLORS[ci + 1] || '#6b7280',
              };
            }),
          ];

          setHoverData({ date, entries });
        });
      } catch (_) {}

    } else {
      // Tek coin modu: Normal area chart + MA
      chart.setStyles({ candle: { type: 'area' } });

      const klineData = chartData
        .map(d => ({
          timestamp: d.ts,
          open:  d.price ?? 0,
          high:  d.price ?? 0,
          low:   d.price ?? 0,
          close: d.price ?? 0,
          volume: d.volume ?? 0,
          turnover: 0,
        }))
        .filter(d => !isNaN(d.close) && d.close > 0)
        .sort((a, b) => a.timestamp - b.timestamp);

      if (klineData.length === 0) return;

      const isUp = klineData[klineData.length - 1].close >= klineData[0].close;
      const color = isUp ? '#10b981' : '#ef4444';

      chart.setStyles({
        candle: {
          type: 'area',
          area: {
            lineColor: color,
            lineSize: 2,
            value: 'close',
            smooth: true,
            backgroundColor: [
              { offset: 0, color: color + '33' },
              { offset: 1, color: color + '00' },
            ],
          },
        },
      });

      chart.applyNewData(klineData);

      // MA ekle (varsa) - önce eski MA'yı temizle
      chart.removeIndicator('candle_pane', 'MA');
      if (activeMAs && activeMAs.length > 0) {
        chart.createIndicator(
          { name: 'MA', calcParams: activeMAs },
          false,
          { id: 'candle_pane' }
        );
      }
    }

    return () => { klineDispose(id); };
  }, [chartData, currency, range, isComparing, compareCoins, compareData, coinId, activeMAs]);

  if (!chartData || chartData.length === 0) {
    return (
      <div className="flex items-center justify-center h-[520px] text-gray-400 text-sm">
        Grafik verisi yüklenemedi.
      </div>
    );
  }

  return (
    <div
      className="relative"
      onMouseLeave={() => setHoverData(null)}
    >
      <div id={chartId.current} style={{ width: '100%', height: '520px' }} />

      {/* Hover tooltip — sadece karşılaştırma modunda */}
      {isComparing && hoverData && (
        <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 bg-gray-900/95 text-white rounded-xl px-4 py-3 shadow-xl text-xs min-w-[180px] pointer-events-none z-10">
          <p className="text-gray-400 mb-2 font-medium">{hoverData.date}</p>
          {hoverData.entries.map(entry => (
            <div key={entry.symbol} className="flex justify-between gap-4 mb-1">
              <span style={{ color: entry.color }} className="font-semibold">{entry.symbol}</span>
              <span className={`font-bold ${
                entry.value == null ? 'text-gray-400' :
                entry.value >= 0 ? 'text-emerald-400' : 'text-rose-400'
              }`}>
                {entry.value == null ? '-' : `${entry.value >= 0 ? '+' : ''}${entry.value.toFixed(2)}%`}
              </span>
            </div>
          ))}
        </div>
      )}

      <p className="text-xs text-gray-400 mt-2">
        Kaynak: CoinGecko · {currency} bazlı{isComparing ? ' · % değişim bazlı karşılaştırma' : ''}
      </p>
    </div>
  );
}

// Karşılaştırma dropdown bileşeni
function CompareDropdown({ compareCoins, onAdd, onRemove, allCoins }) {
  const [open, setOpen] = useState(false);
  const [search, setSearch] = useState('');
  const ref = useRef(null);

  useEffect(() => {
    function handleClick(e) { if (ref.current && !ref.current.contains(e.target)) setOpen(false); }
    document.addEventListener('mousedown', handleClick);
    return () => document.removeEventListener('mousedown', handleClick);
  }, []);

  const suggestions = search.trim()
    ? (allCoins || POPULAR_COINS).filter(c =>
        c.name?.toLowerCase().includes(search.toLowerCase()) ||
        c.symbol?.toLowerCase().includes(search.toLowerCase())
      ).slice(0, 8)
    : POPULAR_COINS;

  return (
    <div className="relative" ref={ref}>
      <button
        onClick={() => setOpen(o => !o)}
        className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-bold transition-all border ${
          compareCoins.length > 0
            ? 'bg-[#093eaa] text-white border-[#093eaa]'
            : 'bg-gray-100 text-gray-600 border-gray-200 hover:bg-gray-200'
        }`}
      >
        <Plus className="w-3 h-3" />
        Karşılaştır
        {compareCoins.length > 0 && <span className="bg-white/30 rounded-full px-1">{compareCoins.length}</span>}
        <ChevronDown className={`w-3 h-3 transition-transform ${open ? 'rotate-180' : ''}`} />
      </button>

      {open && (
        <div className="absolute top-full left-0 mt-1 w-64 bg-white border border-gray-200 rounded-xl shadow-xl z-50">
          <div className="p-3 border-b border-gray-100">
            <input
              autoFocus
              type="text"
              placeholder="Coin ara..."
              value={search}
              onChange={e => setSearch(e.target.value)}
              className="w-full px-3 py-1.5 bg-gray-50 border border-gray-200 rounded-lg text-xs focus:outline-none focus:ring-2 focus:ring-[#093eaa]"
            />
          </div>

          {compareCoins.length > 0 && (
            <div className="px-3 pt-2 pb-1">
              <p className="text-xs text-gray-400 mb-1.5">Seçili</p>
              <div className="flex flex-wrap gap-1">
                {compareCoins.map((c, i) => (
                  <span key={c.id} className="flex items-center gap-1 text-xs font-bold px-2 py-0.5 rounded-full text-white"
                    style={{ backgroundColor: COMPARE_COLORS[i + 1] ?? '#6b7280' }}>
                    {c.symbol?.toUpperCase()}
                    <button onClick={() => onRemove(c.id)}><X className="w-2.5 h-2.5" /></button>
                  </span>
                ))}
              </div>
            </div>
          )}

          <div className="max-h-48 overflow-y-auto">
            {!search && <p className="text-xs text-gray-400 px-3 pt-2 pb-1">Popüler</p>}
            {suggestions.map(c => {
              const selected = compareCoins.some(x => x.id === c.id);
              return (
                <button key={c.id} onClick={() => selected ? onRemove(c.id) : onAdd(c)}
                  className={`w-full flex items-center gap-2 px-3 py-2 text-left hover:bg-gray-50 transition-colors ${selected ? 'bg-blue-50' : ''}`}>
                  {c.image && <img src={c.image} alt="" className="w-5 h-5 rounded-full" />}
                  <span className="text-sm font-semibold text-gray-800">{c.name}</span>
                  <span className="text-xs text-gray-400 uppercase ml-auto">{c.symbol}</span>
                  {selected && <span className="text-[#093eaa] text-xs">✓</span>}
                </button>
              );
            })}
          </div>

          <div className="p-2 border-t border-gray-100">
            <button onClick={() => setOpen(false)}
              className="w-full text-xs text-gray-500 py-1 hover:text-gray-700">Kapat</button>
          </div>
        </div>
      )}
    </div>
  );
}

export default function CryptoDetailPage() {
  const { coinId } = useParams();
  const navigate = useNavigate();
  const { isAuthenticated } = useAuth();

  const [coin, setCoin]           = useState(null);
  const [detail, setDetail]       = useState(null);
  const [chartData, setChartData] = useState([]);
  const [ohlcData,  setOhlcData]  = useState([]);   // mum grafik için
  const [range, setRange]         = useState(7);
  const [currency, setCurrency]   = useState('TRY');
  const [chartMode, setChartMode] = useState('line');
  const [chartLoading, setChartLoading] = useState(false);
  const [loading, setLoading]     = useState(true);
  const [showDesc, setShowDesc]   = useState(false);
  const [allCoins, setAllCoins]   = useState([]);
  const [activeMA, setActiveMA]   = useState([]); // aktif MA periyotları

  // Karşılaştırma state'i
  const [compareCoins, setCompareCoins]     = useState([]); // [{id, symbol, name, image}]
  const [compareData, setCompareData]       = useState({}); // {coinId: [{ts, price}]}
  const [compareLoading, setCompareLoading] = useState(false);
  const isComparing = compareCoins.length > 0;

  useEffect(() => {
    setLoading(true);
    getAllCryptos(currency.toLowerCase())
      .then(items => {
        setAllCoins(items);
        const found = items.find(c => c.id === coinId);
        if (found) { setCoin(found); setLoading(false); }
        else {
          getCryptoDetail(coinId).then(d => {
            const md = d?.market_data;
            const cur = currency.toLowerCase();
            if (md) setCoin({
              id: coinId, symbol: d.symbol, name: d.name, image: d.image?.small,
              currentPrice: md.current_price?.[cur], marketCap: md.market_cap?.[cur],
              marketCapRank: d.market_cap_rank, totalVolume: md.total_volume?.[cur],
              high24h: md.high_24h?.[cur], low24h: md.low_24h?.[cur],
              priceChange24h: md.price_change_24h,
              priceChangePercentage24h: md.price_change_percentage_24h,
              priceChangePercentage1h: md.price_change_percentage_1h_in_currency?.[cur],
              priceChangePercentage7d: md.price_change_percentage_7d_in_currency?.[cur],
            });
          }).catch(() => {}).finally(() => setLoading(false));
        }
      }).catch(() => setLoading(false));
  }, [coinId, currency]);

  useEffect(() => {
    getCryptoDetail(coinId).then(setDetail).catch(() => {});
  }, [coinId]);

  // Ana coin chart verisi
  const loadLineChart = useCallback(async () => {
    setChartLoading(true);
    try {
      const data = await getCryptoChart(coinId, range, currency.toLowerCase());
      const prices = data?.prices ?? [];
      const volumes = data?.total_volumes ?? [];
      setChartData(prices.map((p, i) => ({
        ts: p[0],
        date: fmtDate(p[0], range),
        price: p[1],
        volume: volumes[i]?.[1] ?? null,
      })));
    } catch (e) { console.error(e); }
    finally { setChartLoading(false); }
  }, [coinId, range, currency]);

  // Mum grafik verisi
  const loadOhlcChart = useCallback(async () => {
    setChartLoading(true);
    try {
      const raw = await getCryptoOhlc(coinId, range, currency.toLowerCase());
      // Format: [timestamp_ms, open, high, low, close]
      const points = (raw ?? []).map(d => ({
        timestamp: Math.floor(d[0] / 1000), // ms → saniye (CommodityDetailChart saniye bekliyor)
        displayOpen:  d[1],
        displayHigh:  d[2],
        displayLow:   d[3],
        displayClose: d[4],
        rawOpen:  d[1],
        rawHigh:  d[2],
        rawLow:   d[3],
        rawClose: d[4],
        volume: 0,
      })).filter(p => p.displayClose > 0);
      setOhlcData(points);
    } catch (e) { console.error(e); }
    finally { setChartLoading(false); }
  }, [coinId, range, currency]);

  useEffect(() => {
    if (chartMode === 'line') loadLineChart();
    else if (chartMode === 'candle') loadOhlcChart();
    setActiveMA([]); // range değişince MA seçimini sıfırla
  }, [chartMode, loadLineChart, loadOhlcChart]);

  // Karşılaştırma coin'lerinin chart verilerini çek
  useEffect(() => {
    if (chartMode !== 'line' || compareCoins.length === 0) return;
    setCompareLoading(true);
    Promise.all(
      compareCoins.map(c =>
        getCryptoChart(c.id, range, currency.toLowerCase())
          .then(d => ({ id: c.id, prices: d?.prices ?? [] }))
          .catch(() => ({ id: c.id, prices: [] }))
      )
    ).then(results => {
      const map = {};
      results.forEach(r => { map[r.id] = r.prices; });
      setCompareData(map);
    }).finally(() => setCompareLoading(false));
  }, [compareCoins, range, currency, chartMode]);

  // Karşılaştırma modunda normalize edilmiş veri (% değişim bazlı)
  const normalizedChartData = useCallback(() => {
    if (!isComparing || chartData.length === 0) return chartData;
    const base0 = chartData[0]?.price;
    if (!base0) return chartData;

    return chartData.map((point) => {
      const row = { date: point.date, ts: point.ts };
      row[coinId] = base0 > 0 ? ((point.price - base0) / base0) * 100 : 0;
      row[`${coinId}_price`] = point.price;

      compareCoins.forEach(c => {
        const prices = compareData[c.id] ?? [];
        const base = prices[0]?.[1];
        if (!base || base === 0) { row[c.id] = null; row[`${c.id}_price`] = null; return; }

        // Timestamp bazlı en yakın noktayı bul
        let closest = prices[0];
        let minDiff = Math.abs(prices[0][0] - point.ts);
        for (let j = 1; j < prices.length; j++) {
          const diff = Math.abs(prices[j][0] - point.ts);
          if (diff < minDiff) { minDiff = diff; closest = prices[j]; }
          else break; // Sıralı olduğu için erken çık
        }

        row[c.id] = ((closest[1] - base) / base) * 100;
        row[`${c.id}_price`] = closest[1];
      });
      return row;
    });
  }, [isComparing, chartData, compareCoins, compareData, coinId]);

  const addCompare = useCallback((c) => {
    if (compareCoins.length >= 4 || compareCoins.some(x => x.id === c.id) || c.id === coinId) return;
    setCompareCoins(prev => [...prev, c]);
  }, [compareCoins, coinId]);

  const removeCompare = useCallback((id) => {
    setCompareCoins(prev => prev.filter(c => c.id !== id));
    setCompareData(prev => { const n = { ...prev }; delete n[id]; return n; });
  }, []);

  const change24h = coin?.priceChangePercentage24h;
  const pos24h = (change24h ?? 0) >= 0;
  const mainColor = COMPARE_COLORS[0];

  // MA verilerini chart datasına ekle
  const chartDataWithMA = useMemo(() => {
    if (chartData.length === 0) return chartData;
    const maArrays = {};
    MA_OPTIONS.forEach(({ period, label }) => {
      maArrays[label] = calcMA(chartData, period);
    });
    return chartData.map((d, i) => {
      const row = { ...d };
      MA_OPTIONS.forEach(({ label }) => { row[label] = maArrays[label][i]; });
      return row;
    });
  }, [chartData]);

  // RSI hesapla (chart verisi yeterince uzunsa)
  const rsiValue = useMemo(() => calcRSI(chartData), [chartData]);

  // Benzer coinler — aynı kategorideki coinler
  const similarCoins = useMemo(() => {
    if (!detail?.categories?.length || !allCoins.length) return [];
    const cats = new Set(detail.categories.map(c => c.toLowerCase()));
    return allCoins
      .filter(c => c.id !== coinId && detail.categories.some(cat =>
        // allCoins'de kategori bilgisi yok, market cap rank ile yakın olanları al
        true
      ))
      // Fallback: market cap rank'e göre yakın coinleri göster
      .filter(c => c.id !== coinId && c.marketCapRank != null && coin?.marketCapRank != null &&
        Math.abs(c.marketCapRank - coin.marketCapRank) <= 10)
      .sort((a, b) => (a.marketCapRank ?? 999) - (b.marketCapRank ?? 999))
      .slice(0, 6);
  }, [detail, allCoins, coinId, coin]);

  const [convAmount, setConvAmount] = useState('1');
  const convResult = coin?.currentPrice
    ? fmtPrice(parseFloat(convAmount || 0) * parseFloat(coin.currentPrice), currency)
    : '-';

  const md = detail?.market_data;
  const cur = currency.toLowerCase();
  const ath = md?.ath?.[cur], atl = md?.atl?.[cur];
  const athPct = md?.ath_change_percentage?.[cur], atlPct = md?.atl_change_percentage?.[cur];
  const circSupply = md?.circulating_supply, totalSupply = md?.total_supply, maxSupply = md?.max_supply;
  const description = detail?.description?.tr || detail?.description?.en || '';
  const shortDesc = description.replace(/<[^>]+>/g, '').slice(0, 300);
  const homepage = detail?.links?.homepage?.[0];
  const twitter = detail?.links?.twitter_screen_name;
  const github = detail?.links?.repos_url?.github?.[0];
  const categories = detail?.categories?.slice(0, 3) ?? [];

  const displayData = isComparing ? normalizedChartData() : chartData;
  const isLoading = chartLoading || compareLoading;

  // CommodityDetailChart için points formatına çevir (çizgi grafik)
  const linePoints = useMemo(() => {
    if (chartMode !== 'line') return [];
    return chartData.map(d => ({
      timestamp: Math.floor(d.ts / 1000),
      displayClose: d.price,
      rawClose: d.price,
      displayOpen: d.price,
      displayHigh: d.price,
      displayLow: d.price,
      volume: d.volume ?? 0,
    })).filter(p => p.displayClose > 0);
  }, [chartData, chartMode]);

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <Link to="/market/crypto" className="inline-flex items-center gap-1.5 text-sm text-[#093eaa] font-semibold hover:underline">
          <ArrowLeft className="w-4 h-4" /> Kripto Para
        </Link>
        <button
          onClick={() => isAuthenticated ? navigate('/portfolio') : navigate('/login', { state: { from: '/portfolio' } })}
          className="px-4 py-2 bg-emerald-500 text-white text-sm font-bold rounded-xl hover:bg-emerald-600 transition-all">
          Satın Al
        </button>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-4 gap-6">
        {/* Sol kolon */}
        <div className="space-y-4">
          {loading ? (
            <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6 flex gap-1.5">
              <div className="w-2 h-2 bg-[#093eaa] rounded-full animate-bounce" />
              <div className="w-2 h-2 bg-[#093eaa]/60 rounded-full animate-bounce [animation-delay:100ms]" />
              <div className="w-2 h-2 bg-[#093eaa]/30 rounded-full animate-bounce [animation-delay:200ms]" />
            </div>
          ) : coin && (
            <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-5">
              <div className="flex items-center gap-3 mb-4">
                {coin.image && <img src={coin.image} alt={coin.name} className="w-10 h-10 rounded-full" />}
                <div>
                  <h1 className="text-xl font-black text-gray-900">{coin.name}</h1>
                  <span className="text-xs text-gray-400 uppercase font-semibold">{coin.symbol}</span>
                </div>
                {coin.marketCapRank && (
                  <span className="ml-auto text-xs bg-gray-100 text-gray-500 font-bold px-2 py-1 rounded-full">#{coin.marketCapRank}</span>
                )}
              </div>
              <div className="flex gap-1 mb-3">
                {CURRENCIES.map(c => (
                  <button key={c} onClick={() => setCurrency(c)}
                    className={`px-3 py-1 rounded-lg text-xs font-bold transition-all ${currency === c ? 'bg-[#093eaa] text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'}`}>
                    {c}
                  </button>
                ))}
              </div>
              <p className="text-3xl font-black text-gray-900 mb-1">{fmtPrice(coin.currentPrice, currency)}</p>
              <span className={`flex items-center gap-1 text-sm font-bold ${pos24h ? 'text-emerald-600' : 'text-rose-600'}`}>
                {pos24h ? <TrendingUp className="w-4 h-4" /> : <TrendingDown className="w-4 h-4" />}
                {pos24h ? '+' : ''}{fmt(change24h)}% (24s)
              </span>
              {coin.low24h != null && coin.high24h != null && (
                <div className="mt-3">
                  <div className="flex justify-between text-xs text-gray-400 mb-1">
                    <span>{fmtPrice(coin.low24h, currency)}</span>
                    <span className="text-gray-500 font-semibold">24s Aralık</span>
                    <span>{fmtPrice(coin.high24h, currency)}</span>
                  </div>
                  <div className="h-1.5 bg-gray-100 rounded-full overflow-hidden">
                    <div className="h-full bg-gradient-to-r from-rose-400 to-emerald-400 rounded-full"
                      style={{ width: `${Math.min(100, ((coin.currentPrice - coin.low24h) / (coin.high24h - coin.low24h)) * 100)}%` }} />
                  </div>
                </div>
              )}
            </div>
          )}

          {coin && (
            <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-5">
              <p className="text-xs font-bold text-gray-500 uppercase tracking-wider mb-3">Dönemsel Getiri</p>
              <div className="grid grid-cols-3 gap-2">
                {[{ label: '1sa', val: coin.priceChangePercentage1h }, { label: '24sa', val: coin.priceChangePercentage24h }, { label: '7g', val: coin.priceChangePercentage7d }].map(item => (
                  <div key={item.label} className="text-center bg-gray-50 rounded-xl p-2">
                    <p className="text-xs text-gray-400 mb-1">{item.label}</p>
                    {pct(item.val)}
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* RSI — sadece çizgi modunda ve veri varsa */}
          {chartMode === 'line' && <RSIBadge rsi={rsiValue} />}

          {coin && (
            <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-5">
              <p className="text-xs font-bold text-gray-500 uppercase tracking-wider mb-3">Piyasa Verileri</p>
              <div className="space-y-2.5">
                {[
                  { label: 'Piyasa Değeri', value: coin.marketCap != null ? fmtPrice(coin.marketCap, currency) : '-' },
                  { label: '24s Hacim', value: coin.totalVolume != null ? fmtPrice(coin.totalVolume, currency) : '-' },
                  { label: 'Dolaşım Arzı', value: circSupply != null ? `${parseFloat(circSupply).toLocaleString('tr-TR', { maximumFractionDigits: 0 })} ${coin.symbol?.toUpperCase()}` : '-' },
                  { label: 'Toplam Arz', value: totalSupply != null ? `${parseFloat(totalSupply).toLocaleString('tr-TR', { maximumFractionDigits: 0 })} ${coin.symbol?.toUpperCase()}` : '-' },
                  { label: 'Maksimum Arz', value: maxSupply != null ? `${parseFloat(maxSupply).toLocaleString('tr-TR', { maximumFractionDigits: 0 })} ${coin.symbol?.toUpperCase()}` : '∞' },
                ].map(item => (
                  <div key={item.label} className="flex justify-between items-center text-sm">
                    <span className="text-gray-500">{item.label}</span>
                    <span className="font-semibold text-gray-900 text-right">{item.value}</span>
                  </div>
                ))}
              </div>
            </div>
          )}

          {(ath != null || atl != null) && (
            <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-5">
              <p className="text-xs font-bold text-gray-500 uppercase tracking-wider mb-3">Tüm Zamanlar</p>
              <div className="space-y-3">
                {ath != null && (
                  <div>
                    <div className="flex justify-between text-sm mb-0.5">
                      <span className="text-gray-500">En Yüksek</span>
                      <span className="font-bold text-emerald-600">{fmtPrice(ath, currency)}</span>
                    </div>
                    <div className="text-xs text-gray-400 text-right">{athPct != null ? `${fmt(athPct)}% şu anki fiyattan` : ''}</div>
                  </div>
                )}
                {atl != null && (
                  <div>
                    <div className="flex justify-between text-sm mb-0.5">
                      <span className="text-gray-500">En Düşük</span>
                      <span className="font-bold text-rose-600">{fmtPrice(atl, currency)}</span>
                    </div>
                    <div className="text-xs text-gray-400 text-right">{atlPct != null ? `+${fmt(atlPct)}% şu anki fiyattan` : ''}</div>
                  </div>
                )}
              </div>
            </div>
          )}

          {coin && (
            <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-5">
              <p className="text-xs font-bold text-gray-500 uppercase tracking-wider mb-3">{coin.symbol?.toUpperCase()} Çevirici</p>
              <div className="space-y-2">
                <div className="flex items-center gap-2 bg-gray-50 rounded-xl px-3 py-2">
                  <input type="number" value={convAmount} onChange={e => setConvAmount(e.target.value)}
                    className="flex-1 bg-transparent text-sm font-bold text-gray-900 focus:outline-none min-w-0" placeholder="1" />
                  <span className="text-xs font-bold text-gray-500 uppercase">{coin.symbol}</span>
                </div>
                <div className="flex items-center gap-2 bg-gray-50 rounded-xl px-3 py-2">
                  <span className="flex-1 text-sm font-bold text-gray-900">{convResult}</span>
                  <span className="text-xs font-bold text-gray-500">{currency}</span>
                </div>
              </div>
            </div>
          )}

          {detail && (
            <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-5">
              <p className="text-xs font-bold text-gray-500 uppercase tracking-wider mb-3">Bilgi</p>
              <div className="space-y-2">
                {homepage && (
                  <a href={homepage} target="_blank" rel="noopener noreferrer" className="flex items-center gap-2 text-sm text-[#093eaa] hover:underline">
                    <Globe className="w-4 h-4" /> {new URL(homepage).hostname}
                  </a>
                )}
                {twitter && (
                  <a href={`https://twitter.com/${twitter}`} target="_blank" rel="noopener noreferrer" className="flex items-center gap-2 text-sm text-[#093eaa] hover:underline">
                    <ExternalLink className="w-4 h-4" /> @{twitter}
                  </a>
                )}
                {github && (
                  <a href={github} target="_blank" rel="noopener noreferrer" className="flex items-center gap-2 text-sm text-[#093eaa] hover:underline">
                    <ExternalLink className="w-4 h-4" /> GitHub
                  </a>
                )}
                {categories.length > 0 && (
                  <div className="flex flex-wrap gap-1 mt-2">
                    {categories.map(c => (
                      <span key={c} className="text-xs bg-blue-50 text-[#093eaa] font-semibold px-2 py-0.5 rounded-full">{c}</span>
                    ))}
                  </div>
                )}
              </div>
            </div>
          )}
        </div>

        {/* Sağ kolon */}
        <div className="lg:col-span-3 space-y-4">
          <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden">
            {/* Grafik toolbar */}
            <div className="flex items-center gap-2 px-6 pt-5 pb-3 flex-wrap">
              {/* Grafik modu */}
              <div className="flex gap-1">
                {[{ key: 'line', label: '〰 Çizgi' }, { key: 'candle', label: '🕯 Mum' }].map(m => (
                  <button key={m.key} onClick={() => setChartMode(m.key)}
                    className={`px-3 py-1.5 rounded-lg text-xs font-bold transition-all ${chartMode === m.key ? 'bg-[#093eaa] text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'}`}>
                    {m.label}
                  </button>
                ))}
              </div>

              {/* Karşılaştır butonu — sadece çizgi modunda */}
              {chartMode === 'line' && (
                <CompareDropdown
                  compareCoins={compareCoins}
                  onAdd={addCompare}
                  onRemove={removeCompare}
                  allCoins={allCoins}
                />
              )}

              {/* Range butonları */}
              <div className="flex gap-1 ml-auto">
                {RANGES.map(r => (
                  <button key={r.days} onClick={() => setRange(r.days)}
                    className={`px-3 py-1.5 rounded-lg text-xs font-bold transition-all ${range === r.days ? 'bg-[#093eaa] text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'}`}>
                    {r.label}
                  </button>
                ))}
              </div>
            </div>

            {/* Karşılaştırma legend */}
            {chartMode === 'line' && isComparing && (
              <div className="flex flex-wrap gap-3 px-6 pb-2">
                <div className="flex items-center gap-1.5">
                  <div className="w-3 h-0.5 rounded" style={{ backgroundColor: mainColor }} />
                  <span className="text-xs font-semibold text-gray-700">{coin?.symbol?.toUpperCase()}</span>
                </div>
                {compareCoins.map((c, i) => (
                  <div key={c.id} className="flex items-center gap-1.5">
                    <div className="w-3 h-0.5 rounded" style={{ backgroundColor: COMPARE_COLORS[i + 1] }} />
                    <span className="text-xs font-semibold text-gray-700">{c.symbol?.toUpperCase()}</span>
                    <button onClick={() => removeCompare(c.id)} className="text-gray-400 hover:text-gray-600">
                      <X className="w-3 h-3" />
                    </button>
                  </div>
                ))}
              </div>
            )}

            {/* Grafik */}
            <div className="px-6 pb-6">
              {/* Karşılaştırma modu: CryptoLineChart ile normalize % grafik */}
              {chartMode === 'line' && isComparing ? (
                <>
                  {(chartLoading || compareLoading) && (
                    <div className="flex items-center justify-center h-[520px] bg-white/70">
                      <div className="flex gap-1.5">
                        <div className="w-2 h-2 bg-[#093eaa] rounded-full animate-bounce" />
                        <div className="w-2 h-2 bg-[#093eaa]/60 rounded-full animate-bounce [animation-delay:100ms]" />
                        <div className="w-2 h-2 bg-[#093eaa]/30 rounded-full animate-bounce [animation-delay:200ms]" />
                      </div>
                    </div>
                  )}
                  {!(chartLoading || compareLoading) && (
                    <CryptoLineChart
                      chartData={chartData}
                      currency={currency}
                      range={range}
                      compareCoins={compareCoins}
                      compareData={compareData}
                      coinId={coinId}
                      mainCoinSymbol={coin?.symbol}
                      activeMAs={activeMA}
                    />
                  )}
                </>
              ) : (
                /* Normal mod: CommodityDetailChart */
                <CommodityDetailChart
                  key={`${coinId}-${range}-${chartMode}-${currency}`}
                  points={chartMode === 'candle' ? ohlcData : linePoints}
                  chartMode={chartMode}
                  loading={chartLoading || compareLoading}
                />
              )}
              <p className="text-xs text-gray-400 mt-1">Kaynak: CoinGecko · {currency} bazlı</p>
            </div>
          </div>

          {shortDesc && (
            <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-5">
              <p className="text-xs font-bold text-gray-500 uppercase tracking-wider mb-3">{coin?.name} Hakkında</p>
              <p className="text-sm text-gray-700 leading-relaxed">
                {showDesc ? description.replace(/<[^>]+>/g, '') : shortDesc}
                {description.length > 300 && (
                  <button onClick={() => setShowDesc(s => !s)}
                    className="ml-1 text-[#093eaa] font-semibold hover:underline inline-flex items-center gap-0.5">
                    {showDesc ? <><ChevronUp className="w-3 h-3" /> Daha az</> : <><ChevronDown className="w-3 h-3" /> Devamını oku</>}
                  </button>
                )}
              </p>
            </div>
          )}

          {/* Benzer Coinler */}
          {similarCoins.length > 0 && (
            <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-5">
              <p className="text-xs font-bold text-gray-500 uppercase tracking-wider mb-4">Benzer Coinler</p>
              <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
                {similarCoins.map(c => {
                  const pos = (c.priceChangePercentage24h ?? 0) >= 0;
                  return (
                    <button key={c.id} onClick={() => navigate(`/market/crypto/${c.id}`)}
                      className="flex items-center gap-2.5 p-3 rounded-xl border border-gray-100 hover:border-[#093eaa]/30 hover:bg-blue-50 transition-all text-left">
                      {c.image && <img src={c.image} alt={c.name} className="w-8 h-8 rounded-full flex-shrink-0" />}
                      <div className="min-w-0">
                        <p className="text-xs font-bold text-gray-900 truncate">{c.name}</p>
                        <p className="text-xs text-gray-400 uppercase">{c.symbol}</p>
                        {c.priceChangePercentage24h != null && (
                          <p className={`text-xs font-semibold ${pos ? 'text-emerald-600' : 'text-rose-600'}`}>
                            {pos ? '+' : ''}{parseFloat(c.priceChangePercentage24h).toFixed(2)}%
                          </p>
                        )}
                      </div>
                    </button>
                  );
                })}
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
