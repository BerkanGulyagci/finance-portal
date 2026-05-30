import { useEffect, useState, useCallback, useRef, useMemo } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import { ArrowLeft, TrendingUp, TrendingDown, Globe, ChevronDown, ChevronUp, ExternalLink, X } from 'lucide-react';
import UniversalCompareButton from '../../../components/common/UniversalCompareButton';
import TrendBadge from '../../../components/common/TrendBadge';
import InstrumentActionButtons from '../../../components/instrument/InstrumentActionButtons';
import { buildTrendItem } from '../../../utils/trendUtils';
import {
  XAxis, YAxis, CartesianGrid, Tooltip,
  ResponsiveContainer, LineChart, Line, Brush,
  ComposedChart, Area, Bar,
} from 'recharts';
import { getCryptoDetail, getAllCryptos, getCryptoChart, getCryptoOhlc } from '../../../api/marketApi';
import { useAuth } from '../../../context/AuthContext';
import CommodityDetailChart   from '../commodities/components/CommodityDetailChart';
import CommodityDetailToolbar from '../commodities/components/CommodityDetailToolbar';
import {
  CRYPTO_CHART_RANGES,
  coingeckoOhlcRowsToPoints,
  earliestTimestampMs,
  buildTryFallbackWarning,
  fetchYahooCryptoLineChart,
  fetchYahooCryptoOhlc,
  formatCryptoChartTimeLabel,
  buildVolumeMap,
  parseMarketChartPrices,
  alignVolumesToPrices,
  formatYahooChartSourceNote,
} from './utils/cryptoChartRanges';
import { COMPARE_COLORS, MA_OPTIONS } from './utils/cryptoChartConfig';
import CryptoLineChart from './components/CryptoLineChart';
import CompareDropdown from './components/CompareDropdown';
import { useTranslation } from '../../../context/LanguageContext';

const CURRENCIES = ['TRY', 'USD', 'EUR'];
const CURRENCY_SYMBOLS = { TRY: '₺', USD: '$', EUR: '€' };

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
  const { t } = useTranslation();
  if (rsi == null) return null;
  const val = parseFloat(rsi.toFixed(1));
  let label, bg, text;
  if (val >= 70)      { label = t('Aşırı Alım'); bg = 'bg-rose-100';    text = 'text-rose-700'; }
  else if (val <= 30) { label = t('Aşırı Satım'); bg = 'bg-emerald-100'; text = 'text-emerald-700'; }
  else                { label = t('Normal');       bg = 'bg-gray-100';    text = 'text-gray-600'; }

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
  const { t } = useTranslation();
  const tvSymbol = resolveTvSymbol(coinId, symbol);
  const exchange = tvSymbol.split(':')[0];
  const src = `https://www.tradingview.com/widgetembed/?frameElementId=tv_chart&symbol=${encodeURIComponent(tvSymbol)}&interval=60&hidesidetoolbar=0&hidetoptoolbar=0&symboledit=1&saveimage=0&toolbarbg=f1f3f6&studies=[]&theme=light&style=1&timezone=Europe%2FIstanbul&withdateranges=1&showpopupbutton=1&locale=tr`;
  return (
    <div>
      <iframe src={src} style={{ width: '100%', height: '620px', border: 'none' }}
        allowTransparency allowFullScreen title="TradingView Chart" />
      <p className="text-xs text-gray-400 px-6 pb-3">{t('Grafik Kaynağı:')} {exchange}</p>
    </div>
  );
}

/* ─── KlineCharts Çizgi Grafiği (Crypto için) ─── */

export default function CryptoDetailPage() {
  const { t } = useTranslation();
  const { coinId } = useParams();
  const navigate = useNavigate();
  const { isAuthenticated } = useAuth();

  const [coin, setCoin]           = useState(null);
  const [detail, setDetail]       = useState(null);
  const [chartData, setChartData] = useState([]);
  const [ohlcData,  setOhlcData]  = useState([]);   // mum grafik için
  const [rangeIdx, setRangeIdx]   = useState(1);
  const [currency, setCurrency]   = useState('TRY');
  const [chartMode, setChartMode] = useState('line');
  const [chartLoading, setChartLoading] = useState(false);
  const [loading, setLoading]     = useState(true);
  const [showDesc, setShowDesc]   = useState(false);
  const [allCoins, setAllCoins]   = useState([]);
  const [activeMA, setActiveMA]   = useState([]); // aktif MA periyotları
  const [historyFrom, setHistoryFrom] = useState(null);
  const [chartSource, setChartSource] = useState('CoinGecko');
  const [chartTryFallbackWarning, setChartTryFallbackWarning] = useState(null);

  // Karşılaştırma state'i
  const [compareCoins, setCompareCoins]     = useState([]); // [{id, symbol, name, image}]
  const [compareData, setCompareData]       = useState({}); // {coinId: [{ts, price}]}
  const [compareLoading, setCompareLoading] = useState(false);
  const isComparing = compareCoins.length > 0;
  const rangeConfig = CRYPTO_CHART_RANGES[rangeIdx];

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
    getCryptoDetail(coinId).then(d => {
      setDetail(d);
      // getAllCryptos TÜM coin listesini çektiği için yavaş; tek-coin detail çağrısı genelde
      // daha erken döner ve market_data kartın ihtiyacı olan her şeyi içerir. coin henüz yoksa
      // başlık kartını HEMEN detail'den doldur (••• beklemesin); liste gelince refine eder.
      setCoin(prev => {
        if (prev) return prev;
        const md = d?.market_data;
        if (!md) return prev;
        const c = currency.toLowerCase();
        return {
          id: coinId, symbol: d.symbol, name: d.name, image: d.image?.small || d.image?.thumb || d.image?.large,
          currentPrice: md.current_price?.[c], marketCap: md.market_cap?.[c],
          marketCapRank: d.market_cap_rank, totalVolume: md.total_volume?.[c],
          high24h: md.high_24h?.[c], low24h: md.low_24h?.[c],
          priceChange24h: md.price_change_24h,
          priceChangePercentage24h: md.price_change_percentage_24h,
          priceChangePercentage1h: md.price_change_percentage_1h_in_currency?.[c],
          priceChangePercentage7d: md.price_change_percentage_7d_in_currency?.[c],
        };
      });
      setLoading(false);
    }).catch(() => {});
    // currency kasıtlı dışarıda: ilk-yükleme dolumu içindir; para birimi değişimini getAllCryptos yönetir
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [coinId]);

  // Ana coin chart verisi
  const loadLineChart = useCallback(async () => {
    setChartLoading(true);
    setChartData([]);
    try {
      let prices;
      let volumes;
      if (rangeConfig.yahoo) {
        if (!coin?.symbol) {
          setChartData([]);
          setChartLoading(false);
          return;
        }
        const raw = await fetchYahooCryptoLineChart(
          coin.symbol,
          currency,
          rangeConfig.yahooRange,
          rangeConfig.yahooInterval,
          coinId,
          coin?.currentPrice, // güncel fiyat (seçili para birimi) — yanlış Yahoo token'ını ayıklamak için
        );
        prices = raw.prices ?? [];
        volumes = raw.total_volumes ?? [];
        const fromMs = earliestTimestampMs(prices);
        setHistoryFrom(fromMs ? new Date(fromMs) : null);
        setChartSource(
          raw.sourceNote ?? formatYahooChartSourceNote(raw.yahooSymbol, currency, raw.quoteCurrency),
        );
        setChartTryFallbackWarning(
          raw.tryFallbackWarning ?? buildTryFallbackWarning(currency, raw.quoteCurrency, rangeConfig.label),
        );
      } else {
        setHistoryFrom(null);
        setChartSource('CoinGecko');
        setChartTryFallbackWarning(null);
        const data = await getCryptoChart(
          coinId,
          rangeConfig.days,
          currency.toLowerCase(),
          rangeConfig.interval ?? undefined,
        );
        prices = parseMarketChartPrices(data);
        volumes = alignVolumesToPrices(data?.total_volumes ?? [], prices);
      }
      const gran = rangeConfig.timeGranularity;
      const volMap = buildVolumeMap(volumes);
      setChartData(prices.map((p) => ({
        ts: p[0],
        date: formatCryptoChartTimeLabel(p[0], gran),
        price: p[1],
        volume: volMap.get(Number(p[0])) ?? null,
      })));
    } catch (e) {
      console.error(e);
      setChartData([]);
      setChartTryFallbackWarning(null);
    } finally {
      setChartLoading(false);
    }
  }, [coinId, rangeConfig, currency, coin?.symbol]);

  const loadOhlcChart = useCallback(async () => {
    setChartLoading(true);
    setOhlcData([]);
    try {
      if (rangeConfig.yahoo) {
        if (!coin?.symbol) {
          setOhlcData([]);
          setChartLoading(false);
          return;
        }
        const { points, yahooSymbol, quoteCurrency, sourceNote, tryFallbackWarning } = await fetchYahooCryptoOhlc(
          coin.symbol,
          currency,
          rangeConfig.yahooRange,
          rangeConfig.yahooInterval,
          coinId,
        );
        setOhlcData(points);
        const fromMs = points[0]?.timestamp != null ? points[0].timestamp * 1000 : null;
        setHistoryFrom(fromMs ? new Date(fromMs) : null);
        setChartSource(
          sourceNote ?? formatYahooChartSourceNote(yahooSymbol, currency, quoteCurrency),
        );
        setChartTryFallbackWarning(
          tryFallbackWarning ?? buildTryFallbackWarning(currency, quoteCurrency, rangeConfig.label),
        );
      } else {
        setHistoryFrom(null);
        setChartSource('CoinGecko');
        setChartTryFallbackWarning(null);
        const days = rangeConfig.ohlcDays ?? rangeConfig.days;
        const rows = await getCryptoOhlc(coinId, days, currency.toLowerCase());
        setOhlcData(coingeckoOhlcRowsToPoints(rows));
      }
    } catch (e) {
      console.error(e);
      setOhlcData([]);
      setChartTryFallbackWarning(null);
    } finally {
      setChartLoading(false);
    }
  }, [coinId, rangeConfig, currency, coin?.symbol]);

  useEffect(() => {
    setChartData([]);
    setOhlcData([]);
    setHistoryFrom(null);
    setChartTryFallbackWarning(null);
    if (chartMode === 'line') loadLineChart();
    else if (chartMode === 'candle') loadOhlcChart();
    setActiveMA([]);
  }, [chartMode, loadLineChart, loadOhlcChart, rangeIdx, currency, coinId]);

  // Karşılaştırma coin'lerinin chart verilerini çek
  useEffect(() => {
    if (chartMode !== 'line' || compareCoins.length === 0) return;
    setCompareLoading(true);
    Promise.all(
      compareCoins.map(c => {
        const load = rangeConfig.yahoo
          ? fetchYahooCryptoLineChart(c.symbol, currency, rangeConfig.yahooRange, rangeConfig.yahooInterval, c.id)
              .then(raw => ({ id: c.id, prices: raw.prices ?? [] }))
          : getCryptoChart(
              c.id,
              rangeConfig.days,
              currency.toLowerCase(),
              rangeConfig.interval ?? undefined,
            ).then(d => ({ id: c.id, prices: parseMarketChartPrices(d) }));
        return load.catch(() => ({ id: c.id, prices: [] }));
      })
    ).then(results => {
      const map = {};
      results.forEach(r => { map[r.id] = r.prices; });
      setCompareData(map);
    }).finally(() => setCompareLoading(false));
  }, [compareCoins, rangeConfig, currency, chartMode]);

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

  // Trend rozeti — fiyat serisinden (MA20/50 + 52h konumu + 7g momentum)
  const trendItem = useMemo(
    () => buildTrendItem(chartData.map(d => d.price), 'CRYPTO', { priceChangePercentage7d: coin?.priceChangePercentage7d }),
    [chartData, coin],
  );

  const displayData = isComparing ? normalizedChartData() : chartData;
  const isLoading = chartLoading || compareLoading;

  // CommodityDetailChart için points formatına çevir (çizgi grafik)
  const linePoints = useMemo(() => {
    if (chartMode !== 'line') return [];
    return chartData.map(d => ({
      timestamp: Math.floor((d.ts < 1e11 ? d.ts * 1000 : d.ts) / 1000),
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
          <ArrowLeft className="w-4 h-4" /> {t('Kripto Para')}
        </Link>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-4 gap-6">
        {/* Sol kolon */}
        <div className="space-y-4">
          {/* Skeleton SADECE ilk yüklemede (henüz coin yokken). coin varsa kart kalır,
              para birimi (TRY/USD/EUR) değiştirilince rakamlar YERİNDE güncellenir — kart kaybolmaz. */}
          {loading && !coin ? (
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
              {/* Para birimi değişiminde kart durur; bu rakamlar güncellenirken hafif soluklaşır */}
              <div className={`transition-opacity duration-300 ${loading ? 'opacity-40' : 'opacity-100'}`}>
                <p className="text-3xl font-black text-gray-900 mb-1">{fmtPrice(coin.currentPrice, currency)}</p>
                <span className={`flex items-center gap-1 text-sm font-bold ${pos24h ? 'text-emerald-600' : 'text-rose-600'}`}>
                  {pos24h ? <TrendingUp className="w-4 h-4" /> : <TrendingDown className="w-4 h-4" />}
                  {pos24h ? '+' : ''}{fmt(change24h)}% ({t('24s')})
                </span>
              </div>
              {trendItem && <div className="mt-2"><TrendBadge item={trendItem} size="sm" /></div>}
              <div className="mt-3">
                <InstrumentActionButtons
                  assetType="CRYPTO"
                  symbol={(coin.symbol || '').toUpperCase()}
                  name={coin.name}
                  price={coin.currentPrice}
                />
              </div>
              {coin.low24h != null && coin.high24h != null && (
                <div className="mt-3">
                  <div className="flex justify-between text-xs text-gray-400 mb-1">
                    <span>{fmtPrice(coin.low24h, currency)}</span>
                    <span className="text-gray-500 font-semibold">{t('24s Aralık')}</span>
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
              <p className="text-xs font-bold text-gray-500 uppercase tracking-wider mb-3">{t('Dönemsel Getiri')}</p>
              <div className="grid grid-cols-3 gap-2">
                {[{ label: '1sa', val: coin.priceChangePercentage1h }, { label: '24sa', val: coin.priceChangePercentage24h }, { label: '7g', val: coin.priceChangePercentage7d }].map(item => (
                  <div key={item.label} className="text-center bg-gray-50 rounded-xl p-2">
                    <p className="text-xs text-gray-400 mb-1">{t(item.label)}</p>
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
              <p className="text-xs font-bold text-gray-500 uppercase tracking-wider mb-3">{t('Piyasa Verileri')}</p>
              <div className="space-y-2.5">
                {[
                  { label: 'Piyasa Değeri', value: coin.marketCap != null ? fmtPrice(coin.marketCap, currency) : '-' },
                  { label: '24s Hacim', value: coin.totalVolume != null ? fmtPrice(coin.totalVolume, currency) : '-' },
                  { label: 'Dolaşım Arzı', value: circSupply != null ? `${parseFloat(circSupply).toLocaleString('tr-TR', { maximumFractionDigits: 0 })} ${coin.symbol?.toUpperCase()}` : '-' },
                  { label: 'Toplam Arz', value: totalSupply != null ? `${parseFloat(totalSupply).toLocaleString('tr-TR', { maximumFractionDigits: 0 })} ${coin.symbol?.toUpperCase()}` : '-' },
                  { label: 'Maksimum Arz', value: maxSupply != null ? `${parseFloat(maxSupply).toLocaleString('tr-TR', { maximumFractionDigits: 0 })} ${coin.symbol?.toUpperCase()}` : '∞' },
                ].map(item => (
                  <div key={item.label} className="flex justify-between items-center text-sm">
                    <span className="text-gray-500">{t(item.label)}</span>
                    <span className="font-semibold text-gray-900 text-right">{item.value}</span>
                  </div>
                ))}
              </div>
            </div>
          )}

          {(ath != null || atl != null) && (
            <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-5">
              <p className="text-xs font-bold text-gray-500 uppercase tracking-wider mb-3">{t('Tüm Zamanlar')}</p>
              <div className="space-y-3">
                {ath != null && (
                  <div>
                    <div className="flex justify-between text-sm mb-0.5">
                      <span className="text-gray-500">{t('En Yüksek')}</span>
                      <span className="font-bold text-emerald-600">{fmtPrice(ath, currency)}</span>
                    </div>
                    <div className="text-xs text-gray-400 text-right">{athPct != null ? `${fmt(athPct)}% ${t('şu anki fiyattan')}` : ''}</div>
                  </div>
                )}
                {atl != null && (
                  <div>
                    <div className="flex justify-between text-sm mb-0.5">
                      <span className="text-gray-500">{t('En Düşük')}</span>
                      <span className="font-bold text-rose-600">{fmtPrice(atl, currency)}</span>
                    </div>
                    <div className="text-xs text-gray-400 text-right">{atlPct != null ? `+${fmt(atlPct)}% ${t('şu anki fiyattan')}` : ''}</div>
                  </div>
                )}
              </div>
            </div>
          )}

          {coin && (
            <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-5">
              <p className="text-xs font-bold text-gray-500 uppercase tracking-wider mb-3">{coin.symbol?.toUpperCase()} {t('Çevirici')}</p>
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
              <p className="text-xs font-bold text-gray-500 uppercase tracking-wider mb-3">{t('Bilgi')}</p>
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
                    {t(m.label)}
                  </button>
                ))}
              </div>

              {/* Karşılaştır kümesi — inline (aynı grafikte coin) + Tümüyle (her şeyle, ayrı sayfa) */}
              {chartMode === 'line' && (
                <div title={t('Aynı grafikte başka kripto paralarla kıyasla')}>
                  <CompareDropdown
                    compareCoins={compareCoins}
                    onAdd={addCompare}
                    onRemove={removeCompare}
                    allCoins={allCoins}
                  />
                </div>
              )}
              {coin && (
                <UniversalCompareButton
                  assetType="CRYPTO"
                  symbol={(coin.symbol || '').toUpperCase()}
                  name={coin.name}
                />
              )}

              {/* Not: İndikatör menüsü grafiğin (CommodityDetailChart) çizim toolbar'ında — burada tekrarı kaldırıldı */}

              {/* Range butonları — segmented */}
              <div className="inline-flex items-center gap-0.5 rounded-lg border border-gray-200 bg-gray-50 p-0.5 ml-auto">
                {CRYPTO_CHART_RANGES.map((r, i) => (
                  <button key={r.label} onClick={() => setRangeIdx(i)}
                    className={`px-3 py-1 rounded-md text-xs font-semibold whitespace-nowrap transition-all ${rangeIdx === i ? 'bg-white text-[#093eaa] shadow-sm' : 'text-gray-500 hover:text-gray-800'}`}>
                    {t(r.label)}
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
                  key={`${coinId}-${rangeIdx}-${chartMode}-${currency}`}
                  points={chartMode === 'candle' ? ohlcData : linePoints}
                  chartMode={chartMode}
                  valueFormatter={(v) => fmtPrice(v, currency)}
                  loading={chartLoading || compareLoading}
                  sourceNote={(() => {
                    const n = chartMode === 'candle' ? ohlcData.length : linePoints.length;
                    const range = historyFrom ? ` · ${historyFrom.toLocaleDateString('tr-TR')} – ${t('bugün')}` : '';
                    const pts = n ? ` · ${n} ${t('nokta')}` : '';
                    if (rangeConfig.yahoo) {
                      if (!n) return `${t('Kaynak:')} ${chartSource || 'Yahoo Finance'} — ${t('veri yok')}`;
                      return `${t('Kaynak:')} ${chartSource}${range}${pts}`;
                    }
                    return `${t('Kaynak:')} CoinGecko · ${currency} ${t('bazlı')}${pts}`;
                  })()}
                  sourceWarning={chartTryFallbackWarning}
                />
              )}
            </div>
          </div>

          {shortDesc && (
            <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-5">
              <p className="text-xs font-bold text-gray-500 uppercase tracking-wider mb-3">{coin?.name} {t('Hakkında')}</p>
              <p className="text-sm text-gray-700 leading-relaxed">
                {showDesc ? description.replace(/<[^>]+>/g, '') : shortDesc}
                {description.length > 300 && (
                  <button onClick={() => setShowDesc(s => !s)}
                    className="ml-1 text-[#093eaa] font-semibold hover:underline inline-flex items-center gap-0.5">
                    {showDesc ? <><ChevronUp className="w-3 h-3" /> {t('Daha az')}</> : <><ChevronDown className="w-3 h-3" /> {t('Devamını oku')}</>}
                  </button>
                )}
              </p>
            </div>
          )}

          {/* Benzer Coinler */}
          {similarCoins.length > 0 && (
            <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-5">
              <p className="text-xs font-bold text-gray-500 uppercase tracking-wider mb-4">{t('Benzer Coinler')}</p>
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
