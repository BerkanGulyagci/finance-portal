import { useEffect, useState, useRef, useId } from 'react';
import { ChevronDown, ChevronUp, LineChart } from 'lucide-react';
import {
  getFxTcmb,
  getCryptos,
  getGoldSpot,
  getBankCurrencyRatesByCurrency,
  getFxHistory,
  getGoldHistory,
  getCryptoChart,
} from '../../api/marketApi';
import { useTranslation } from '../../i18n/LanguageContext';

const TICKER_OPEN_STORAGE_KEY = 'finance-portal-market-ticker-open';

const COLOR_UP = '#10b981';
const COLOR_DOWN = '#ef4444';
const COLOR_NEUTRAL = '#64748b';

function Sparkline({ data, color }) {
  const gradId = useId().replace(/:/g, '');
  if (!data || data.length < 2) return null;
  const w = 64, h = 28;
  const min = Math.min(...data);
  const max = Math.max(...data);
  const range = max - min || 1;
  const pts = data.map((v, i) => {
    const x = (i / (data.length - 1)) * w;
    const y = h - ((v - min) / range) * (h - 4) - 2;
    return `${x},${y}`;
  }).join(' ');
  const fillPts = `0,${h} ${pts} ${w},${h}`;
  const fillUrl = `url(#spark-grad-${gradId})`;
  return (
    <svg width={w} height={h} className="shrink-0">
      <defs>
        <linearGradient id={`spark-grad-${gradId}`} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor={color} stopOpacity="0.3" />
          <stop offset="100%" stopColor={color} stopOpacity="0" />
        </linearGradient>
      </defs>
      <polygon points={fillPts} fill={fillUrl} />
      <polyline points={pts} fill="none" stroke={color} strokeWidth="1.5" strokeLinejoin="round" />
    </svg>
  );
}

/** Son değer ve yüzde değişime göre monoton eğri (rastgele değil). */
function trendSparkline(endValue, changePercent, points = 14) {
  if (endValue == null || Number.isNaN(endValue) || endValue <= 0) return [];
  if (changePercent == null || Number.isNaN(changePercent) || Math.abs(changePercent) < 1e-6) {
    return Array.from({ length: Math.max(2, points) }, () => endValue);
  }
  const startValue = endValue / (1 + changePercent / 100);
  const n = Math.max(2, points);
  return Array.from({ length: n }, (_, i) => {
    const t = i / (n - 1);
    return startValue + (endValue - startValue) * t;
  });
}

function dirFromChangePct(pct) {
  if (pct == null || Number.isNaN(pct)) return null;
  if (pct > 0.0001) return 'up';
  if (pct < -0.0001) return 'down';
  return null;
}

/** TCMB günlük kapanışlardan gerçek mini-seri ve pencere içi % değişim. */
function sparkFromFxHistory(hist, maxPoints = 16) {
  const raw = hist?.points;
  if (!Array.isArray(raw) || raw.length < 2) return { spark: [], changePct: null, dir: null };
  const closes = raw.map(p => parseFloat(p.close)).filter(v => !Number.isNaN(v) && v > 0);
  if (closes.length < 2) return { spark: [], changePct: null, dir: null };
  const spark = closes.slice(-maxPoints);
  const first = spark[0];
  const last = spark[spark.length - 1];
  const changePct = first !== 0 ? ((last - first) / first) * 100 : 0;
  return { spark, changePct, dir: dirFromChangePct(changePct) };
}

function downsample(series, maxPoints) {
  if (!series?.length) return [];
  if (series.length <= maxPoints) return [...series];
  const out = [];
  const last = series.length - 1;
  for (let i = 0; i < maxPoints; i++) {
    const idx = Math.round((i / (maxPoints - 1)) * last);
    out.push(series[idx]);
  }
  return out;
}

/** Seriyi ölçekle: son nokta endValue olsun (şekil korunur, % değişim aynı kalır). */
function scaleSparkToEnd(spark, endValue) {
  if (!spark?.length || endValue == null || endValue <= 0) return spark ?? [];
  const last = spark[spark.length - 1];
  if (!last || last <= 0) return spark;
  const k = endValue / last;
  return spark.map(v => v * k);
}

function closesFromFxHist(hist) {
  const raw = hist?.points;
  if (!Array.isArray(raw)) return [];
  return raw.map(p => parseFloat(p.close)).filter(v => !Number.isNaN(v) && v > 0);
}

function changeDirFromSeries(spark) {
  if (!spark?.length || spark.length < 2) return { changePct: null, dir: null };
  const first = spark[0];
  const last = spark[spark.length - 1];
  if (first == null || first <= 0) return { changePct: null, dir: null };
  const changePct = ((last - first) / first) * 100;
  return { changePct, dir: dirFromChangePct(changePct) };
}

/** CoinGecko market_chart.prices → mini seri (grafik sayfalarıyla aynı kaynak). */
function sparkFromGeckoChart(chart, maxPoints = 36) {
  const raw = chart?.prices;
  if (!Array.isArray(raw) || raw.length < 2) return { spark: [], changePct: null, dir: null };
  const vals = raw.map(p => parseFloat(p[1])).filter(v => !Number.isNaN(v) && v > 0);
  if (vals.length < 2) return { spark: [], changePct: null, dir: null };
  const spark = downsample(vals, maxPoints);
  const { changePct, dir } = changeDirFromSeries(spark);
  return { spark, changePct, dir };
}

/** Altın tarihsel (ONS USD) — detay grafikleriyle aynı endpoint. */
function sparkFromGoldHist(hist, maxPoints = 36) {
  const raw = hist?.points;
  if (!Array.isArray(raw) || raw.length < 2) return { spark: [], changePct: null, dir: null };
  const vals = raw.map(p => parseFloat(p.close)).filter(v => !Number.isNaN(v) && v > 0);
  if (vals.length < 2) return { spark: [], changePct: null, dir: null };
  const spark = downsample(vals, maxPoints);
  const { changePct, dir } = changeDirFromSeries(spark);
  return { spark, changePct, dir };
}

export function MarketTicker() {
  const { t } = useTranslation();
  const [items, setItems] = useState([]);
  const [tickerOpen, setTickerOpen] = useState(() => {
    try {
      return localStorage.getItem(TICKER_OPEN_STORAGE_KEY) !== '0';
    } catch {
      return true;
    }
  });
  const scrollRef = useRef(null);
  const [isPaused, setIsPaused] = useState(false);
  const posRef = useRef(0);
  const animRef = useRef(null);

  useEffect(() => {
    try {
      localStorage.setItem(TICKER_OPEN_STORAGE_KEY, tickerOpen ? '1' : '0');
    } catch {
      /* ignore */
    }
  }, [tickerOpen]);

  useEffect(() => {
    async function load() {
      try {
        const [fx, cryptos, goldSpot, bankUsd, bankEur, bankGbp, histUsd, histEur, histGbp, goldHist] =
          await Promise.all([
            getFxTcmb().catch(() => null),
            getCryptos(0, 50).catch(() => []),
            getGoldSpot().catch(() => null),
            getBankCurrencyRatesByCurrency('USD').catch(() => []),
            getBankCurrencyRatesByCurrency('EUR').catch(() => []),
            getBankCurrencyRatesByCurrency('GBP').catch(() => []),
            getFxHistory('USD', '1M').catch(() => null),
            getFxHistory('EUR', '1M').catch(() => null),
            getFxHistory('GBP', '1M').catch(() => null),
            getGoldHistory('1M', 'USD').catch(() => null),
          ]);

        const tickerCryptoDays = 14;
        const cryptoChartEntries = await Promise.all(
          ['btc', 'eth', 'bnb', 'sol'].map(sym => {
            const c = cryptos.find(x => x.symbol?.toLowerCase() === sym);
            if (!c?.id) return Promise.resolve([sym, null]);
            return getCryptoChart(c.id, tickerCryptoDays, 'try').then(d => [sym, d]).catch(() => [sym, null]);
          })
        );
        const cryptoCharts = Object.fromEntries(cryptoChartEntries);

        const result = [];
        const rates = fx?.rates ?? [];

        const pushTcmbPair = (sym, hist, rateRow) => {
          if (!rateRow) return;
          const val = parseFloat(rateRow.sell);
          const { spark, changePct, dir } = sparkFromFxHistory(hist);
          const sparkline = spark.length >= 2 ? spark : trendSparkline(val, 0);
          const ch = changePct != null && Number.isFinite(changePct) ? changePct : null;
          result.push({
            label: `${sym}/TRY`,
            value: val.toLocaleString('tr-TR', { minimumFractionDigits: 3 }),
            change: ch != null && Math.abs(ch) > 1e-6 ? ch : null,
            dir: ch != null ? dir : null,
            spark: sparkline,
          });
        };

        pushTcmbPair('USD', histUsd, rates.find(x => x.symbol === 'USD'));
        pushTcmbPair('EUR', histEur, rates.find(x => x.symbol === 'EUR'));
        pushTcmbPair('GBP', histGbp, rates.find(x => x.symbol === 'GBP'));

        // Banka kurları — ortalama satış; mini grafik: aynı para TCMB günlük şekli, son nokta banka ortalamasına ölçekli
        const addBankRate = (bankRates, sym, tcmbHist) => {
          if (!bankRates || bankRates.length === 0) return;
          const sells = bankRates.map(r => r.sellRate).filter(v => v != null && v > 0);
          if (sells.length === 0) return;
          const avg = sells.reduce((a, b) => a + b, 0) / sells.length;
          const changes = bankRates.map(r => r.dailyChangePercent).filter(v => v != null);
          const avgChg = changes.length > 0 ? changes.reduce((a, b) => a + b, 0) / changes.length : 0;
          const closes = closesFromFxHist(tcmbHist);
          let spark = [];
          let change = avgChg !== 0 ? avgChg : null;
          let dir = dirFromChangePct(avgChg);
          if (closes.length >= 2) {
            const slice = closes.slice(-28);
            spark = scaleSparkToEnd(downsample(slice, 24), avg);
            const { changePct, dir: dHist } = changeDirFromSeries(spark);
            if (changePct != null && Number.isFinite(changePct)) {
              change = Math.abs(changePct) > 1e-6 ? changePct : null;
              dir = dHist;
            }
          } else {
            spark = trendSparkline(avg, avgChg);
          }
          result.push({
            label: `${sym}/TRY ${t('Banka')}`,
            value: avg.toLocaleString('tr-TR', { minimumFractionDigits: 4, maximumFractionDigits: 4 }),
            change,
            dir,
            spark: spark.length >= 2 ? spark : trendSparkline(avg, avgChg),
          });
        };
        addBankRate(bankUsd, 'USD', histUsd);
        addBankRate(bankEur, 'EUR', histEur);
        addBankRate(bankGbp, 'GBP', histGbp);

        // Altın/ONS — spot + /api/gold/history (USD ONS) ile büyük grafikle aynı seri
        if (goldSpot?.price) {
          const val = parseFloat(goldSpot.price);
          const spotChg = parseFloat(goldSpot.changePercent ?? 0);
          const g = sparkFromGoldHist(goldHist);
          let spark = g.spark;
          if (spark.length >= 2) spark = scaleSparkToEnd(spark, val);
          else spark = trendSparkline(val, spotChg);
          const { changePct, dir } = spark.length >= 2 ? changeDirFromSeries(spark) : { changePct: spotChg, dir: dirFromChangePct(spotChg) };
          const ch = changePct != null && Number.isFinite(changePct) && Math.abs(changePct) > 1e-6 ? changePct : spotChg !== 0 ? spotChg : null;
          const d = dir ?? dirFromChangePct(spotChg);
          result.push({
            label: t('ALTIN/ONS'),
            value: val.toLocaleString('tr-TR', { minimumFractionDigits: 2 }),
            change: ch,
            dir: d,
            spark,
          });
        }

        // Kripto — CoinGecko market_chart (detay sayfasındaki çizgi grafik verisi)
        ['btc', 'eth', 'bnb', 'sol'].forEach(sym => {
          const c = cryptos.find(x => x.symbol?.toLowerCase() === sym);
          if (!c) return;
          const val = parseFloat(c.currentPrice ?? 0);
          const chgApi = parseFloat(c.priceChangePercentage24h ?? 0);
          const chart = cryptoCharts[sym];
          const g = sparkFromGeckoChart(chart);
          const spark = g.spark.length >= 2 ? g.spark : trendSparkline(val, chgApi);
          const { changePct, dir } = g.spark.length >= 2
            ? { changePct: g.changePct, dir: g.dir }
            : { changePct: chgApi, dir: dirFromChangePct(chgApi) };
          const ch = changePct != null && Number.isFinite(changePct) && Math.abs(changePct) > 1e-6 ? changePct : chgApi !== 0 ? chgApi : null;
          const d = dir ?? dirFromChangePct(chgApi);
          result.push({
            label: c.symbol?.toUpperCase(),
            value: val.toLocaleString('tr-TR', { minimumFractionDigits: 0 }),
            change: ch,
            dir: d,
            spark,
          });
        });

        setItems(result);
      } catch {
        setItems([]);
      }
    }
    load();
  }, []);

  useEffect(() => {
    if (!tickerOpen) return;
    const el = scrollRef.current;
    if (!el || !items.length) return;
    const speed = 0.5;
    const animate = () => {
      if (!isPaused && el) {
        posRef.current += speed;
        const half = el.scrollWidth / 2;
        if (posRef.current >= half) posRef.current = 0;
        el.scrollLeft = posRef.current;
      }
      animRef.current = requestAnimationFrame(animate);
    };
    animRef.current = requestAnimationFrame(animate);
    return () => cancelAnimationFrame(animRef.current);
  }, [isPaused, items, tickerOpen]);

  if (!items.length) return null;

  if (!tickerOpen) {
    return (
      <div className="bg-gray-50 border-b border-gray-200">
        <button
          type="button"
          onClick={() => setTickerOpen(true)}
          className="w-full px-4 sm:px-6 lg:px-8 flex items-center justify-between h-9 text-left hover:bg-gray-100 transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-[#093eaa]/30 group"
          aria-expanded="false"
          aria-label={t('Piyasa şeridini göster')}
        >
          <span className="flex items-center gap-2 text-xs font-medium text-gray-500">
            <LineChart className="w-3.5 h-3.5 text-gray-400 shrink-0" aria-hidden />
            {t('Piyasa özeti gizli')}
          </span>
          <span className="flex items-center gap-1 text-xs font-semibold text-[#093eaa] group-hover:underline">
            {t('Göster')}
            <ChevronDown className="w-4 h-4 shrink-0" aria-hidden />
          </span>
        </button>
      </div>
    );
  }

  const TickerCard = ({ item }) => {
    const isUp = item.dir === 'up';
    const isDown = item.dir === 'down';
    const valueColor = isUp ? COLOR_UP : isDown ? COLOR_DOWN : COLOR_NEUTRAL;
    const sparkColor = isUp ? COLOR_UP : isDown ? COLOR_DOWN : COLOR_NEUTRAL;
    return (
      <div className="flex items-center gap-3 shrink-0 px-5 border-r border-gray-200 last:border-r-0 min-w-[160px]">
        <div className="flex-1 min-w-0">
          <p className="text-[10px] font-bold text-gray-400 uppercase tracking-wider mb-0.5">{item.label}</p>
          <p className="text-sm font-bold leading-tight" style={{ color: valueColor }}>{item.value}</p>
          {item.change != null && (
            <p className={`text-[10px] font-semibold mt-0.5 ${isUp ? 'text-emerald-600' : isDown ? 'text-rose-600' : 'text-slate-500'}`}>
              {isUp ? '▲ ' : isDown ? '▼ ' : ''}{Math.abs(item.change).toFixed(2)}%
            </p>
          )}
        </div>
        <Sparkline data={item.spark} color={sparkColor} />
      </div>
    );
  };

  return (
    <div className="bg-white border-b border-gray-200 flex">
      <button
        type="button"
        onClick={() => setTickerOpen(false)}
        className="shrink-0 w-10 flex items-center justify-center border-r border-gray-100 bg-gray-50 hover:bg-gray-100 text-gray-600 transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-[#093eaa]/40"
        title={t('Piyasa şeridini gizle')}
        aria-expanded="true"
        aria-label={t('Piyasa şeridini gizle')}
      >
        <ChevronUp className="w-4 h-4" aria-hidden />
      </button>
      <div
        className="flex-1 min-w-0 overflow-hidden"
        onMouseEnter={() => setIsPaused(true)}
        onMouseLeave={() => setIsPaused(false)}
      >
        <div ref={scrollRef} className="flex items-stretch overflow-x-hidden select-none py-2"
          style={{ scrollBehavior: 'auto' }}>
          {[...items, ...items].map((item, i) => (
            <TickerCard key={`${item.label}-${i}`} item={item} />
          ))}
        </div>
      </div>
    </div>
  );
}
